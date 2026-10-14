/*
 * Copyright 2026 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.assistant.engine;


import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.api.AssistantApiException;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelUsage;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.ADMIN;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.EDITOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Failure handling: orphaned turns of a dead replica, concurrent usage
 ** increments, usage of failed / cancelled calls, the kill switch failing
 ** closed and its watcher, and list limits.
 *******************************************************************************/
class RobustnessTest extends BaseAssistantTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      config.setKillSwitchPollMillis(100);
   }



   /*******************************************************************************
    ** A turn left RUNNING by a crashed replica: the first replay / cancel /
    ** delete / new turn finishes it as ERROR with persisted error +
    ** message.end, and nothing waits on it anymore.
    *******************************************************************************/
   @Test
   void testOrphanedTurn() throws Exception
   {
      QSession session = AssistantTestFixture.ownerSession(EDITOR, config);
      QContext.init(qInstance, session);
      try
      {
         AssistantStore        store        = new AssistantStore();
         AssistantConversation conversation = store.createConversation(EDITOR, "crashed", null);
         AssistantMessage      message      = store.insert(new AssistantMessage().withConversationId(conversation.getId()).withUserId(EDITOR).withRole("ASSISTANT").withStatus("STREAMING").withSeq(2).withPartsJson("[{\"type\":\"text\",\"text\":\"Partial\"}]"));
         AssistantTurn         turn         = store.insert(new AssistantTurn().withConversationId(conversation.getId()).withUserId(EDITOR).withStatus("RUNNING")
            .withAssistantMessageId(message.getId()).withStartedAt(Instant.now().minus(Duration.ofHours(1))));
         store.insertEvent(turn.getId(), EDITOR, 1, TurnEvent.MESSAGE_START, "{\"seq\":1}");
         store.insertEvent(turn.getId(), EDITOR, 2, TurnEvent.TEXT_DELTA, "{\"seq\":2,\"text\":\"Partial\"}");

         ///////////////////////////////////////////////////////////////////////
         // a fresh RUNNING turn (inside the time limit) is NOT an orphan    //
         ///////////////////////////////////////////////////////////////////////
         AssistantTurn young = store.insert(new AssistantTurn().withConversationId(conversation.getId()).withUserId(EDITOR).withStatus("RUNNING").withStartedAt(Instant.now()));

         AssistantApi            api    = new AssistantApi(config.getRuntime(), session);
         AssistantApi.TurnEvents events = api.turnEvents(turn.getId(), 2);
         assertThat(events.finished()).isTrue();
         assertThat(events.persisted()).extracting(TurnEvent::getType).containsExactly(TurnEvent.ERROR, TurnEvent.MESSAGE_END);
         assertThat(events.persisted().get(0).getData()).containsEntry("code", "INTERNAL").containsEntry("seq", 3);
         assertThat(events.persisted().get(1).getData()).containsEntry("status", "ERROR").containsEntry("messageId", message.getId());

         assertThat(store.getTurn(EDITOR, turn.getId()).getStatus()).isEqualTo("ERROR");
         AssistantMessage reaped = store.getMessage(EDITOR, message.getId());
         assertThat(reaped.getStatus()).isEqualTo("ERROR");
         assertThat(reaped.getPartsJson()).contains("Partial").contains("interrupted");

         ////////////////////////////////////////////////////////
         // idempotent: a second look adds nothing             //
         ////////////////////////////////////////////////////////
         assertThat(api.turnEvents(turn.getId(), 0).persisted()).hasSize(4);
         assertThatThrownBy(() -> api.cancelTurn(turn.getId())).isInstanceOfSatisfying(AssistantApiException.class, e -> assertThat(e.getStatus()).isEqualTo(409));

         assertThat(api.turnEvents(young.getId(), 0).finished()).isFalse();
         assertThatThrownBy(() -> api.deleteConversation(conversation.getId())).isInstanceOfSatisfying(AssistantApiException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
         store.updateFields(AssistantTurn.TABLE_NAME, new com.kingsrook.qqq.backend.core.model.data.QRecord().withValue("id", young.getId()).withValue("startedAt", Instant.now().minus(Duration.ofHours(2))));
         api.deleteConversation(conversation.getId());
         assertThat(store.getConversation(EDITOR, conversation.getId())).isNull();
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** An orphan from another replica does not block the user's next turn.
    *******************************************************************************/
   @Test
   void testOrphanDoesNotBlockNewTurns() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(EDITOR, config));
      try
      {
         new AssistantStore().insert(new AssistantTurn().withConversationId(999).withUserId(EDITOR).withStatus("RUNNING").withStartedAt(Instant.now().minus(Duration.ofHours(1))));
      }
      finally
      {
         QContext.clear();
      }
      provider.thenText("ok");
      assertThat(runner.run(EDITOR, "hello").end()).containsEntry("status", "COMPLETE");
   }



   /*******************************************************************************
    ** Concurrent usage increments of one user and the instance lose nothing
    ** (one JVM).
    *******************************************************************************/
   @Test
   void testConcurrentUsage() throws Exception
   {
      LocalDate       today   = LocalDate.now(ZoneOffset.UTC);
      int             writers = 20;
      CountDownLatch  ready   = new CountDownLatch(writers);
      CountDownLatch  go      = new CountDownLatch(1);
      List<Future<?>> results = new ArrayList<>();
      try(ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
      {
         for(int i = 0; i < writers; i++)
         {
            String user = i % 2 == 0 ? EDITOR : AssistantTestFixture.OTHER_EDITOR;
            results.add(executor.submit(() ->
            {
               QContext.init(qInstance, AssistantTestFixture.ownerSession(user, config));
               try
               {
                  ready.countDown();
                  go.await();
                  new AtomicCallsStore().addUsage(user, today, new ModelUsage().withInputTokens(10L).withOutputTokens(1L), 7, true);
               }
               finally
               {
                  QContext.clear();
               }
               return (null);
            }));
         }

         ///////////////////////////////////////////////////////////////////
         // release every writer at once, then surface any writer failure //
         // (a worker exception must fail the test, not vanish)           //
         ///////////////////////////////////////////////////////////////////
         ready.await();
         go.countDown();
         for(Future<?> result : results)
         {
            result.get();
         }
      }

      QContext.init(qInstance, AssistantTestFixture.sessionFor(ADMIN));
      try
      {
         List<AssistantUsageDaily> rows = new AssistantStore().query(AssistantUsageDaily.TABLE_NAME, new QQueryFilter(), AssistantUsageDaily.class);
         assertThat(rows).hasSize(3);
         AssistantUsageDaily instance = rows.stream().filter(r -> "INSTANCE".equals(r.getScope())).findFirst().orElseThrow();
         assertThat(instance.getTurnCount()).isEqualTo(20);
         assertThat(instance.getInputTokens()).isEqualTo(200);
         assertThat(instance.getCostMicros()).isEqualTo(140);
         assertThat(rows.stream().filter(r -> EDITOR.equals(r.getUserId())).findFirst().orElseThrow().getTurnCount()).isEqualTo(10);
         assertThat(qInstance.getTable(AssistantUsageDaily.TABLE_NAME).getUniqueKeys()).isNotEmpty();
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** AssistantStore whose individual backend calls are atomic, as they are on a
    ** real database. QQQ's in-memory backend is not safe for concurrent callers:
    ** MemoryRecordStore.query iterates a table's synchronized map without holding
    ** its lock, so inserting a different usage row (another key, so another
    ** AssistantStore usage lock) can throw ConcurrentModificationException. Each
    ** call is made atomic here; the read-modify-write across calls is still only
    ** protected by AssistantStore's per-key usage locks, which is what the test
    ** checks.
    *******************************************************************************/
   private static class AtomicCallsStore extends AssistantStore
   {
      private static final Object BACKEND_LOCK = new Object();



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public <T extends QRecordEntity> List<T> query(String tableName, QQueryFilter filter, Class<T> entityClass) throws QException
      {
         synchronized(BACKEND_LOCK)
         {
            return (super.query(tableName, filter, entityClass));
         }
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public <T extends QRecordEntity> T insert(T entity) throws QException
      {
         synchronized(BACKEND_LOCK)
         {
            return (super.insert(entity));
         }
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void update(QRecordEntity entity) throws QException
      {
         synchronized(BACKEND_LOCK)
         {
            super.update(entity);
         }
      }
   }



   /*******************************************************************************
    ** Usage of a failed model call (partial) is still counted.
    *******************************************************************************/
   @Test
   void testFailedCallUsageCounted() throws Exception
   {
      provider.thenError(ModelProviderException.Kind.OVERLOADED, "overloaded");
      TurnRunner.Outcome outcome = runner.run(EDITOR, "hello");
      assertThat(outcome.errorCode()).isEqualTo("PROVIDER");
      Map<String, Object> usage = outcome.events(TurnEvent.USAGE).get(0);
      assertThat(((Number) usage.get("inputTokens")).longValue()).isEqualTo(1200);
      assertThat(((Number) usage.get("costMicros")).longValue()).isGreaterThan(0);
   }



   /*******************************************************************************
    ** The kill switch fails closed when the policy cannot be read.
    *******************************************************************************/
   @Test
   void testKillSwitchFailsClosed()
   {
      AssistantStore broken = new AssistantStore()
      {
         @Override
         public AssistantPolicy loadPolicy() throws QException
         {
            throw (new QException("database down"));
         }
      };
      assertThat(new PolicyService(config, broken, Clock.systemUTC()).isKillSwitchOn()).isTrue();
   }



   /*******************************************************************************
    ** The watcher cancels a turn that is waiting on the model as soon as the
    ** kill switch flips (well before the model would answer).
    *******************************************************************************/
   @Test
   void testKillSwitchWatcher() throws Exception
   {
      provider.withDelay(Duration.ofSeconds(20)).thenText("never");
      Thread.ofVirtual().start(() ->
      {
         try
         {
            Thread.sleep(400);
            QContext.init(qInstance, AssistantTestFixture.sessionFor(ADMIN));
            new AssistantStore().insert(new AssistantPolicy().withName("default").withKillSwitch(true));
         }
         catch(Exception e)
         {
            throw (new RuntimeException(e));
         }
         finally
         {
            QContext.clear();
         }
      });

      long               start   = System.currentTimeMillis();
      TurnRunner.Outcome outcome = runner.run(EDITOR, "slow");
      assertThat(System.currentTimeMillis() - start).isLessThan(5000);
      assertThat(outcome.errorCode()).isEqualTo("KILL_SWITCH");
      assertThat(outcome.end()).containsEntry("status", "CANCELLED");
   }



   /*******************************************************************************
    ** Replay pages through long event logs.
    *******************************************************************************/
   @Test
   void testEventPaging() throws Exception
   {
      QSession session = AssistantTestFixture.ownerSession(EDITOR, config);
      QContext.init(qInstance, session);
      try
      {
         AssistantStore store = new AssistantStore();
         AssistantTurn  turn  = store.insert(new AssistantTurn().withConversationId(1).withUserId(EDITOR).withStatus("COMPLETE").withStartedAt(Instant.now()));
         for(int i = 1; i <= AssistantStore.EVENT_PAGE_SIZE + 3; i++)
         {
            store.insertEvent(turn.getId(), EDITOR, i, TurnEvent.TEXT_DELTA, "{}");
         }
         AssistantApi            api   = new AssistantApi(config.getRuntime(), session);
         AssistantApi.TurnEvents first = api.turnEvents(turn.getId(), 0);
         assertThat(first.persisted()).hasSize(AssistantStore.EVENT_PAGE_SIZE);
         assertThat(first.more()).isTrue();
         AssistantApi.TurnEvents second = api.turnEvents(turn.getId(), AssistantStore.EVENT_PAGE_SIZE);
         assertThat(second.persisted()).hasSize(3);
         assertThat(second.more()).isFalse();
         assertThat(store.query(AssistantTurnEvent.TABLE_NAME, new QQueryFilter().withCriteria("turnId", QCriteriaOperator.EQUALS, turn.getId()), AssistantTurnEvent.class)).hasSize(AssistantStore.EVENT_PAGE_SIZE + 3);
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** Kernel settings come from the config.
    *******************************************************************************/
   @Test
   void testKernelSettings()
   {
      AssistantQBitConfig custom = new AssistantQBitConfig().withMaxRowsToModel(10).withHiddenWidgetNames(new java.util.LinkedHashSet<>(List.of("secretWidget")))
         .withCustomRedactionPatterns(new ArrayList<>(List.of("ACME-\\d{6}")));
      var settings = AssistantRuntime.kernelSettings(custom);
      assertThat(settings.getMaxRowsToModel()).isEqualTo(10);
      assertThat(settings.getHiddenWidgetNames()).contains("secretWidget");
      assertThat(settings.getCustomRedactionPatterns()).extracting(java.util.regex.Pattern::pattern).contains("ACME-\\d{6}");

      List<String> errors = new ArrayList<>();
      new AssistantQBitConfig().withBackendName("x").withCustomRedactionPatterns(new ArrayList<>(List.of("("))).validate(null, errors);
      assertThat(errors).anyMatch(e -> e.startsWith("customRedactionPatterns"));
      assertThat(new LinkedHashMap<>()).isEmpty();
   }

}
