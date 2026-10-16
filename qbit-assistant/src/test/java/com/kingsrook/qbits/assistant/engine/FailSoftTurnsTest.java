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


import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.processes.AssistantRetentionStep;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qbits.assistant.spi.ModelEventListener;
import com.kingsrook.qbits.assistant.spi.ModelMessage;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.spi.StreamHandle;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiGateway;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.EDITOR;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Fail soft: tool failures and timeouts become ERROR (is_error) results plus
 ** an error notice; any exception in the loop still ends the turn (ERROR,
 ** message.end, terminal row, unregistered); abandoned runs and stuck
 ** messages are finalized when read or swept.
 *******************************************************************************/
class FailSoftTurnsTest extends BaseAssistantTest
{
   private static final String INTERNAL_DETAIL = "INTERNAL-DETAIL-7731";

   /** what the UI API gateway does: THROW (an unexpected exception) or SLEEP (hang) */
   private final AtomicReference<String> gatewayMode = new AtomicReference<>("THROW");

   /** whether the provider's stream() throws */
   private volatile boolean providerThrows = false;



   /*******************************************************************************
    ** A gateway that fails or hangs, a short tool limit, and a provider whose
    ** stream() can throw outright (not a ModelProviderException).
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      config.withToolCallTimeoutSeconds(1);
      config.withUiApiGateway(new UiApiGateway()
      {
         @Override
         public boolean isInvocable(String method, String pathTemplate)
         {
            return (true);
         }



         @Override
         public UiApiResult invoke(UiApiCall call) throws QException
         {
            if("SLEEP".equals(gatewayMode.get()))
            {
               try
               {
                  Thread.sleep(30_000);
               }
               catch(InterruptedException e)
               {
                  Thread.currentThread().interrupt();
                  throw (new QException("interrupted"));
               }
            }
            throw (new IllegalStateException(INTERNAL_DETAIL));
         }
      });
      provider = new ScriptedModelProvider()
      {
         @Override
         public StreamHandle stream(ModelRequest request, ModelEventListener listener)
         {
            if(providerThrows)
            {
               throw (new IllegalStateException(INTERNAL_DETAIL));
            }
            return (super.stream(request, listener));
         }
      };
      config.withModelProvider(provider);
   }



   /*******************************************************************************
    ** The tool results the model got back in its second request.
    *******************************************************************************/
   private List<ModelPart> toolResultsSentBack()
   {
      ModelRequest second = provider.getRequests().get(1);
      ModelMessage results = second.getMessages().get(second.getMessages().size() - 1);
      return (results.getParts());
   }



   /*******************************************************************************
    ** A tool that throws: an ERROR is_error result for the model, an ERROR tool
    ** part and an error notice for the user, no internal detail in either.
    *******************************************************************************/
   @Test
   void toolExceptionBecomesErrorPart() throws Exception
   {
      provider.thenToolUse("count_records", input("table", "blog_post", "filter", filter("AND"))).thenText("Sorry, I could not count them.");
      TurnRunner.Outcome outcome = runner.run(EDITOR, "how many posts?");

      assertThat(outcome.end()).containsEntry("status", "COMPLETE");
      assertThat(outcome.events(TurnEvent.TOOL_RESULT)).singleElement().satisfies(result -> assertThat(result).containsEntry("outcome", "ERROR").containsEntry("summary", "Failed"));
      assertThat(outcome.events(TurnEvent.NOTICE)).anySatisfy(notice -> assertThat(notice).containsEntry("level", "error"));
      assertThat(toolResultsSentBack()).singleElement().satisfies(part -> assertThat(part.getIsError()).isTrue());

      List<Map<String, Object>> parts = assistantParts(outcome.turnId());
      assertThat(parts).anySatisfy(part -> assertThat(part).containsEntry("type", "tool").containsEntry("outcome", "ERROR"));
      assertThat(parts).anySatisfy(part -> assertThat(part).containsEntry("type", "notice").containsEntry("level", "error"));
      assertThat(String.valueOf(parts)).doesNotContain(INTERNAL_DETAIL);
      outcome.events().forEach(event -> assertThat(event.toJson()).doesNotContain(INTERNAL_DETAIL));
   }



   /*******************************************************************************
    ** A tool that hangs past toolCallTimeoutSeconds gets an ERROR (is_error)
    ** result; the turn goes on and completes.
    *******************************************************************************/
   @Test
   void toolTimeoutBecomesErrorResult() throws Exception
   {
      gatewayMode.set("SLEEP");
      provider.thenToolUse("count_records", input("table", "blog_post", "filter", filter("AND"))).thenText("That took too long.");
      long started = System.currentTimeMillis();
      TurnRunner.Outcome outcome = runner.run(EDITOR, "how many posts?");

      assertThat(System.currentTimeMillis() - started).isLessThan(20_000);
      assertThat(outcome.end()).containsEntry("status", "COMPLETE");
      assertThat(outcome.events(TurnEvent.TOOL_RESULT)).singleElement().satisfies(result -> assertThat(result).containsEntry("outcome", "ERROR").containsEntry("summary", "Timed out"));
      assertThat(outcome.events(TurnEvent.NOTICE)).anySatisfy(notice -> assertThat(notice).containsEntry("level", "error"));
      assertThat(toolResultsSentBack()).singleElement().satisfies(part ->
      {
         assertThat(part.getIsError()).isTrue();
         assertThat(part.getText()).contains("did not finish in time");
      });
   }



   /*******************************************************************************
    ** The turn's own limit: status ERROR, an error event with code TIMEOUT,
    ** then message.end.
    *******************************************************************************/
   @Test
   void turnTimeoutEndsWithTimeoutError() throws Exception
   {
      config.setMaxTurnSeconds(1);
      provider.withDelay(Duration.ofSeconds(3)).thenText("too late");
      TurnRunner.Outcome outcome = runner.run(EDITOR, "slow");
      assertThat(outcome.end()).containsEntry("status", "ERROR");
      assertThat(outcome.events(TurnEvent.ERROR)).singleElement().satisfies(error -> assertThat(error).containsEntry("code", "TIMEOUT"));
      List<TurnEvent> events = outcome.events();
      assertThat(events.getLast().getType()).isEqualTo(TurnEvent.MESSAGE_END);
      assertThat(turnRow(outcome.turnId()).getStatus()).isEqualTo("ERROR");
   }



   /*******************************************************************************
    ** An unexpected exception anywhere in the loop: ERROR, message.end, the turn
    ** row and the message finalized, the run unregistered, and the next
    ** question is not locked out.
    *******************************************************************************/
   @Test
   void loopExceptionStillEndsTheTurn() throws Exception
   {
      providerThrows = true;
      TurnRunner.Outcome failed = runner.run(EDITOR, "hello");
      assertThat(failed.end()).containsEntry("status", "ERROR");
      assertThat(failed.errorCode()).isEqualTo("INTERNAL");
      assertThat(failed.events().getLast().getType()).isEqualTo(TurnEvent.MESSAGE_END);
      assertThat(String.valueOf(failed.events())).doesNotContain(INTERNAL_DETAIL);
      assertThat(runner.runtime().getRunningTurn(failed.turnId())).isNull();

      AssistantTurn row = turnRow(failed.turnId());
      assertThat(row.getStatus()).isEqualTo("ERROR");
      assertThat(row.getErrorCode()).isEqualTo("INTERNAL");
      assertThat(row.getEndedAt()).isNotNull();
      assertThat(message(row.getAssistantMessageId()).getStatus()).isEqualTo("ERROR");
      assertThat(assistantParts(failed.turnId())).anySatisfy(part -> assertThat(part).containsEntry("type", "notice").containsEntry("level", "error"));

      providerThrows = false;
      provider.thenText("Fine now.");
      assertThat(runner.run(EDITOR, failed.conversationId(), "again", null).end()).containsEntry("status", "COMPLETE");
   }



   /*******************************************************************************
    ** Conversation detail repairs a message left STREAMING: by a run that
    ** already ended (its status), and by an abandoned run (reaped, ERROR).
    ** Legacy parts come back normalized.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void conversationDetailRepairsStuckMessages() throws Exception
   {
      QSession session = AssistantTestFixture.ownerSession(EDITOR, config);
      QContext.init(qInstance, session);
      AssistantStore        store        = config.getRuntime().getStore();
      AssistantApi          api          = new AssistantApi(config.getRuntime(), session);
      AssistantConversation conversation = store.createConversation(EDITOR, "stuck", null);

      AssistantTurn ended = store.insert(new AssistantTurn().withConversationId(conversation.getId()).withUserId(EDITOR).withStatus("COMPLETE").withStartedAt(Instant.now()));
      store.insert(new AssistantMessage().withConversationId(conversation.getId()).withUserId(EDITOR).withRole("ASSISTANT").withStatus("STREAMING").withSeq(2)
         .withTurnId(ended.getId()).withPartsJson("[{\"type\":\"tool\",\"name\":\"query_records\",\"render\":{\"type\":\"records\",\"tableName\":\"t\"}}]"));

      AssistantTurn abandoned = store.insert(new AssistantTurn().withConversationId(conversation.getId()).withUserId(EDITOR).withStatus("RUNNING")
         .withStartedAt(Instant.now().minus(Duration.ofHours(1))).withHeartbeatAt(Instant.now().minus(Duration.ofHours(1))));
      AssistantMessage stuck = store.insert(new AssistantMessage().withConversationId(conversation.getId()).withUserId(EDITOR).withRole("ASSISTANT").withStatus("STREAMING").withSeq(4)
         .withTurnId(abandoned.getId()).withPartsJson("[{\"type\":\"text\",\"text\":\"Partial\"}]"));
      store.updateFields(AssistantTurn.TABLE_NAME, new QRecord().withValue("id", abandoned.getId()).withValue("assistantMessageId", stuck.getId()));

      AssistantMessage orphan = store.insert(new AssistantMessage().withConversationId(conversation.getId()).withUserId(EDITOR).withRole("ASSISTANT").withStatus("STREAMING").withSeq(6)
         .withPartsJson("[]"));

      List<Map<String, Object>> messages = (List<Map<String, Object>>) api.getConversation(conversation.getId(), null, null).get("messages");
      assertThat(messages).extracting(m -> m.get("status")).containsExactly("COMPLETE", "ERROR", "ERROR");
      Map<String, Object> render = (Map<String, Object>) ((List<Map<String, Object>>) messages.get(0).get("parts")).get(0).get("render");
      assertThat(render).containsEntry("rows", List.of()).containsEntry("columns", List.of());
      assertThat(String.valueOf(messages.get(1).get("parts"))).contains(AbandonedTurns.INTERRUPTED_TEXT);
      assertThat(String.valueOf(messages.get(2).get("parts"))).contains(AbandonedTurns.UNFINISHED_TEXT);

      assertThat(store.getTurn(EDITOR, abandoned.getId()).getStatus()).isEqualTo("ERROR");
      assertThat(store.getMessage(EDITOR, orphan.getId()).getStatus()).isEqualTo("ERROR");
      assertThat(api.activeTurns().get("turns")).isEqualTo(List.of());
   }



   /*******************************************************************************
    ** The retention process finalizes runs nobody read again (e.g. after a pod
    ** restart), with their messages and a terminal replay.
    *******************************************************************************/
   @Test
   void retentionSweepsAbandonedRuns() throws Exception
   {
      QSession session = AssistantTestFixture.ownerSession(EDITOR, config);
      QContext.init(qInstance, session);
      AssistantStore        store        = config.getRuntime().getStore();
      AssistantConversation conversation = store.createConversation(EDITOR, "restarted", null);
      AssistantMessage message = store.insert(new AssistantMessage().withConversationId(conversation.getId()).withUserId(EDITOR).withRole("ASSISTANT").withStatus("STREAMING").withSeq(2)
         .withPartsJson("[]"));
      AssistantTurn old = store.insert(new AssistantTurn().withConversationId(conversation.getId()).withUserId(EDITOR).withStatus("RUNNING")
         .withAssistantMessageId(message.getId()).withStartedAt(Instant.now().minus(Duration.ofHours(2))));
      AssistantTurn fresh = store.insert(new AssistantTurn().withConversationId(conversation.getId()).withUserId(EDITOR).withStatus("RUNNING")
         .withStartedAt(Instant.now()).withHeartbeatAt(Instant.now()));

      AssistantRetentionStep.Result result = AssistantRetentionStep.purge(config, store, Instant.now());
      assertThat(result.turnsFinalized()).isEqualTo(1);
      assertThat(store.getTurn(EDITOR, old.getId()).getStatus()).isEqualTo("ERROR");
      assertThat(store.getTurn(EDITOR, fresh.getId()).getStatus()).isEqualTo("RUNNING");
      assertThat(store.getMessage(EDITOR, message.getId()).getStatus()).isEqualTo("ERROR");
      assertThat(store.listEvents(EDITOR, old.getId(), 0, 10)).extracting(e -> e.getEventType()).containsExactly(TurnEvent.ERROR, TurnEvent.MESSAGE_END);
   }



   /*******************************************************************************
    ** The turn row, read as its owner.
    *******************************************************************************/
   private AssistantTurn turnRow(Integer turnId) throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(EDITOR, config));
      try
      {
         return (config.getRuntime().getStore().getTurn(EDITOR, turnId));
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** A message, read as its owner.
    *******************************************************************************/
   private AssistantMessage message(Integer messageId) throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(EDITOR, config));
      try
      {
         return (config.getRuntime().getStore().getMessage(EDITOR, messageId));
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** The stored parts of a turn's assistant message.
    *******************************************************************************/
   private List<Map<String, Object>> assistantParts(Integer turnId) throws Exception
   {
      return (MessageParts.fromJson(message(turnRow(turnId).getAssistantMessageId()).getPartsJson()).list());
   }
}
