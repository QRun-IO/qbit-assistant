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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.api.AssistantApiException;
import com.kingsrook.qbits.assistant.jobs.AssistantRunTracker;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.persistence.TranscriptRedactor;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qbits.assistant.spi.ModelEventListener;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.spi.ModelResponse;
import com.kingsrook.qbits.assistant.spi.ModelUsage;
import com.kingsrook.qbits.assistant.spi.StreamHandle;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.EDITOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Durable run lifecycle, using latches instead of provider timing guesses.
 *******************************************************************************/
class DurableTurnsTest extends BaseAssistantTest
{
   private static final String CLASSIFIED_TEXT = "CLASSIFIED-LIFECYCLE-CANARY";

   private QSession session;
   private AssistantApi api;
   private AssistantStore store;



   /*******************************************************************************
    ** Short, valid lease settings keep watchdog tests bounded.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      config.withTurnHeartbeatSeconds(1).withTurnLeaseSeconds(3).withTurnForceFinalizeGraceSeconds(1)
         .withMaxTurnSeconds(10).withKillSwitchPollMillis(50);
   }



   /*******************************************************************************
    ** Every operation uses the user's own fixture session.
    *******************************************************************************/
   @BeforeEach
   void atomicBackend()
   {
      config.getRuntime().close();
      config.setRuntime(new AssistantRuntime(qInstance, config, Clock.systemUTC(), new AtomicCallsStore(config)));
      userContext();
   }



   /*******************************************************************************
    ** Establish the caller's context, also after a test replaces the runtime.
    *******************************************************************************/
   private void userContext()
   {
      session = AssistantTestFixture.ownerSession(EDITOR, config);
      QContext.init(qInstance, session);
      api = new AssistantApi(config.getRuntime(), session);
      store = config.getRuntime().getStore();
   }



   /*******************************************************************************
    ** Terminal paths on other threads flush the same provisional transcript scope.
    ** Live text stays intact; stored user/message/replay content has only tokens.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = { "SUPERSEDED", "TIMEOUT", "SHUTDOWN" })
   void terminalPathsRedactClassifiedContent(String reason) throws Exception
   {
      config.withDataClassification("blog_post", "title", DataClassification.PII);
      ControlledProvider controlled = controlled(true);
      controlled.classifiedText = CLASSIFIED_TEXT;
      if("TIMEOUT".equals(reason))
      {
         config.setMaxTurnSeconds(1);
      }
      Integer conversation = conversation();
      RunningTurn run = api.startTurn(conversation, Map.of("text", CLASSIFIED_TEXT));
      try
      {
         controlled.awaitCalls(1);
         if("SHUTDOWN".equals(reason))
         {
            config.getRuntime().shutdown(Duration.ZERO);
            QContext.init(qInstance, session);
         }
         else if("SUPERSEDED".equals(reason))
         {
            api.startTurn(conversation, Map.of("text", "next question"));
         }
         AssistantTurn row = completed(run);
         assertThat(row.getCancelReason()).isEqualTo(reason);
         assertThat(store.getMessage(EDITOR, row.getUserMessageId()).getPartsJson()).contains("changed:v1").doesNotContain(CLASSIFIED_TEXT);
         assertThat(store.getMessage(EDITOR, row.getAssistantMessageId()).getPartsJson()).contains("changed:v1").doesNotContain(CLASSIFIED_TEXT);
         assertThat(api.turnEvents(run.getTurnId(), 0).persisted()).anySatisfy(event -> assertThat(event.toJson()).contains("changed:v1"))
            .allSatisfy(event -> assertThat(event.toJson()).doesNotContain(CLASSIFIED_TEXT));
         assertThat(run.getLog().after(0, 1)).anySatisfy(event -> assertThat(event.toJson()).contains(CLASSIFIED_TEXT));
         assertThat(TranscriptRedactor.current()).isNull();
         assertThat(QLogger.redact(CLASSIFIED_TEXT)).isEqualTo("changed:v1");
      }
      finally
      {
         controlled.release();
      }
      await(() -> CLASSIFIED_TEXT.equals(QLogger.redact(CLASSIFIED_TEXT)));
   }



   /*******************************************************************************
    ** A rejected submission still persists its terminal notice and releases scope.
    *******************************************************************************/
   @Test
   void rejectedSubmissionFinalizesTranscript() throws Exception
   {
      Integer conversation = conversation();
      config.getRuntime().getExecutor().shutdown();
      assertThatThrownBy(() -> api.startTurn(conversation, Map.of("text", "Submission failed")))
         .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
      QContext.init(qInstance, session);
      assertThat(store.listRunningTurns(EDITOR)).isEmpty();
      assertThat(store.listMessages(EDITOR, conversation, null, 100)).anySatisfy(message ->
         assertThat(message.getPartsJson()).contains(TurnEngine.RESTART_TEXT));
      assertThat(TranscriptRedactor.current()).isNull();
   }



   /*******************************************************************************
    ** Completion closes the lease, emits progress, and frees the next question.
    *******************************************************************************/
   @Test
   void completion() throws Exception
   {
      provider.thenText("Done").thenText("Again");
      Integer conversation = conversation();
      RunningTurn first = api.startTurn(conversation, Map.of("text", "hello"));
      AssistantTurn row = completed(first);
      assertThat(row.getStatus()).isEqualTo("COMPLETE");
      assertThat(row.getPhase()).isEqualTo("done");
      assertThat(row.getEndedAt()).isNotNull();
      assertThat(row.getHeartbeatAt()).isAfterOrEqualTo(row.getStartedAt());
      assertThat(row.getLeaseOwner()).isEqualTo(config.getRuntime().getInstanceId()).hasSizeLessThanOrEqualTo(100);
      assertThat(row.getConcurrencyMode()).isEqualTo("SUPERSEDE");
      assertThat(store.listRunningTurns(EDITOR)).isEmpty();
      assertThat(store.updateRunningTurn(EDITOR, new QRecord().withValue("id", first.getTurnId()).withValue("status", "CANCELLING"))).isFalse();
      assertThat(first.getLog().after(0, 1).getFirst().getData()).containsEntry("startedAt", row.getStartedAt().toString());
      assertThat(first.getLog().after(0, 1)).filteredOn(e -> TurnEvent.TURN_PHASE.equals(e.getType()))
         .extracting(e -> e.getData().get("phase")).containsExactly("thinking", "writing");
      assertThat(completed(api.startTurn(conversation, Map.of("text", "again"))).getStatus()).isEqualTo("COMPLETE");
   }



   /*******************************************************************************
    ** Streamed text arrives on the provider's own thread; the "writing" progress
    ** it triggers still runs in the turn's QContext (the job tracker writes
    ** through QQQ, which needs the instance).
    *******************************************************************************/
   @Test
   void streamedProgressRunsInTheTurnContext() throws Exception
   {
      List<String> contexts = new CopyOnWriteArrayList<>();
      config.setRunTracker(new AssistantRunTracker()
      {
         @Override
         public String start(String userId, Integer turnId, Integer conversationId, String label)
         {
            return ("job-" + turnId);
         }

         @Override
         public void progress(String trackingId, String phase, String message)
         {
            contexts.add(phase + ":" + (QContext.getQInstance() != null));
         }

         @Override
         public void finish(String trackingId, String status, String message)
         {
         }
      });
      provider.thenText("A streamed answer on the provider's thread");
      AssistantTurn row = completed(api.startTurn(conversation(), Map.of("text", "hello")));
      assertThat(row.getStatus()).isEqualTo("COMPLETE");
      assertThat(contexts).contains("writing:true").doesNotContain("writing:false");
   }



   /*******************************************************************************
    ** A throwing post-processing collaborator cannot strand a streamed answer.
    *******************************************************************************/
   @Test
   void accountingFailureStillFinalizes() throws Exception
   {
      AssistantStore failing = new AssistantStore(config)
      {
         /*******************************************************************************
          ** Inject a failure after streaming has finished.
          *******************************************************************************/
         @Override
         public void addUsage(String userId, LocalDate date, ModelUsage usage, long costMicros, boolean countTurn)
         {
            throw (new IllegalStateException("accounting unavailable"));
         }

         /*******************************************************************************
          ** Conversation totals fail independently of the turn update.
          *******************************************************************************/
         @Override
         public void updateFields(String table, QRecord record) throws QException
         {
            if(AssistantConversation.TABLE_NAME.equals(table) && record.getValue("totalTokensIn") != null)
            {
               throw (new IllegalStateException("totals unavailable"));
            }
            super.updateFields(table, record);
         }
      };
      config.getRuntime().close();
      AssistantRuntime runtime = new AssistantRuntime(qInstance, config, Clock.systemUTC())
      {
         /*******************************************************************************
          ** Substitute only the engine/API persistence collaborator.
          *******************************************************************************/
         @Override
         public AssistantStore getStore()
         {
            return (failing);
         }
      };
      config.setRuntime(runtime);
      userContext();
      provider.thenText("A streamed answer").thenText("Next answer");
      Integer conversation = conversation();
      RunningTurn run = api.startTurn(conversation, Map.of("text", "hello"));
      assertThat(completed(run).getStatus()).isEqualTo("COMPLETE");
      assertThat(api.turnEvents(run.getTurnId(), 0).persisted()).extracting(TurnEvent::getType).contains(TurnEvent.TEXT_DELTA, TurnEvent.MESSAGE_END);
      assertThat(completed(api.startTurn(conversation, Map.of("text", "next"))).getStatus()).isEqualTo("COMPLETE");
   }



   /*******************************************************************************
    ** Explicit concurrent mode permits overlap within and across conversations.
    *******************************************************************************/
   @Test
   void concurrentQuestions() throws Exception
   {
      ControlledProvider controlled = controlled(false);
      Integer firstConversation = conversation();
      List<RunningTurn> runs = new ArrayList<>();
      runs.add(api.startTurn(firstConversation, Map.of("text", "one", "concurrency", "CoNcUrReNt")));
      controlled.awaitCalls(1);
      runs.add(api.startTurn(firstConversation, Map.of("text", "two", "concurrency", "concurrent")));
      controlled.awaitCalls(2);
      runs.add(api.startTurn(conversation(), Map.of("text", "three", "concurrency", "concurrent")));
      controlled.awaitCalls(3);
      assertThat(active()).hasSize(3);
      controlled.release();
      for(RunningTurn run : runs)
      {
         assertThat(completed(run).getStatus()).isEqualTo("COMPLETE");
      }
   }



   /*******************************************************************************
    ** Default supersede frees the slot immediately and records the explanation.
    *******************************************************************************/
   @Test
   void supersedeAndSoftCap() throws Exception
   {
      ControlledProvider controlled = controlled(false);
      Integer conversation = conversation();
      RunningTurn first = api.startTurn(conversation, Map.of("text", "one"));
      controlled.awaitCalls(1);
      RunningTurn second = api.startTurn(conversation, Map.of("text", "two"));
      controlled.awaitCalls(2);
      AssistantTurn superseded = completed(first);
      assertThat(superseded.getStatus()).isEqualTo("CANCELLED");
      assertThat(superseded.getCancelReason()).isEqualTo("SUPERSEDED");
      assertThat(store.getMessage(EDITOR, superseded.getAssistantMessageId()).getPartsJson()).contains(TurnEngine.SUPERSEDED_TEXT);
      config.setMaxConcurrentTurnsPerUser(2);
      RunningTurn third = api.startTurn(conversation(), Map.of("text", "three", "concurrency", "concurrent"));
      controlled.awaitCalls(3);
      RunningTurn fourth = api.startTurn(conversation(), Map.of("text", "four", "concurrency", "concurrent"));
      controlled.awaitCalls(4);
      assertThat(completed(second).getCancelReason()).isEqualTo("SUPERSEDED");
      controlled.release();
      assertThat(completed(third).getStatus()).isEqualTo("COMPLETE");
      assertThat(completed(fourth).getStatus()).isEqualTo("COMPLETE");
   }



   /*******************************************************************************
    ** A provider ignoring both cancel and interruption is forcibly finalized.
    *******************************************************************************/
   @Test
   void wedgedProviderTimeout() throws Exception
   {
      ControlledProvider controlled = controlled(true);
      controlled.emitText = false;
      RecordingTracker tracker = new RecordingTracker();
      config.setRunTracker(tracker);
      config.setMaxTurnSeconds(1);
      RunningTurn run = api.startTurn(conversation(), Map.of("text", "wedged"));
      try
      {
         controlled.awaitCalls(1);
         AssistantTurn row = completed(run);
         assertThat(row.getStatus()).isEqualTo("ERROR");
         assertThat(row.getErrorCode()).isEqualTo("TIMEOUT");
         assertThat(row.getCancelReason()).isEqualTo("TIMEOUT");
         assertThat(store.getMessage(EDITOR, row.getAssistantMessageId()).getPartsJson()).contains(TurnEngine.TIMEOUT_TEXT);
         assertThat(controlled.handles.getFirst().cancelled).isTrue();
         assertThat(tracker.finishes.get()).isEqualTo(1);
         int events = run.getLog().after(0, 1).size();
         controlled.release();
         await(() -> controlled.handles.getFirst().returned);
         assertThat(run.getLog().after(0, 1)).hasSize(events);
         assertThat(tracker.finishes.get()).isEqualTo(1);
      }
      finally
      {
         controlled.release();
      }
   }



   /*******************************************************************************
    ** Stop has its own bounded force path even when the provider ignores it.
    *******************************************************************************/
   @Test
   void stopWedgedProvider() throws Exception
   {
      ControlledProvider controlled = controlled(true);
      RunningTurn run = api.startTurn(conversation(), Map.of("text", "wait"));
      try
      {
         controlled.awaitCalls(1);
         assertThat(api.cancelTurn(run.getTurnId())).containsEntry("status", "CANCELLING");
         AssistantTurn row = completed(run);
         assertThat(row.getStatus()).isEqualTo("CANCELLED");
         assertThat(row.getCancelReason()).isEqualTo("USER");
      }
      finally
      {
         controlled.release();
      }
   }



   /*******************************************************************************
    ** A failed heartbeat tick does not stop cancellation polling on later ticks.
    *******************************************************************************/
   @Test
   void heartbeatFailureDoesNotKillWatchdog() throws Exception
   {
      AtomicInteger heartbeats = new AtomicInteger();
      AssistantStore failingOnce = new AssistantStore(config)
      {
         /*******************************************************************************
          ** Fail only the first renewal, then behave like the real store.
          *******************************************************************************/
         @Override
         public void updateFields(String table, QRecord record) throws QException
         {
            if(AssistantTurn.TABLE_NAME.equals(table) && record.getValue("heartbeatAt") != null && heartbeats.incrementAndGet() == 1)
            {
               throw (new QException("temporary heartbeat failure"));
            }
            super.updateFields(table, record);
         }
      };
      config.getRuntime().close();
      config.setRuntime(new AssistantRuntime(qInstance, config)
      {
         /*******************************************************************************
          ** A transient persistence failure local to the owner.
          *******************************************************************************/
         @Override
         public AssistantStore getStore()
         {
            return (failingOnce);
         }
      });
      userContext();
      ControlledProvider controlled = controlled(false);
      RunningTurn run = api.startTurn(conversation(), Map.of("text", "wait"));
      controlled.awaitCalls(1);
      AssistantRuntime other = new AssistantRuntime(qInstance, config);
      try
      {
         new AssistantApi(other, session).cancelTurn(run.getTurnId());
         assertThat(completed(run).getStatus()).isEqualTo("CANCELLED");
         assertThat(heartbeats.get()).isGreaterThanOrEqualTo(2);
      }
      finally
      {
         controlled.release();
         other.close();
      }
   }



   /*******************************************************************************
    ** Stale leases, old cancel requests and absolute deadlines release remotely.
    *******************************************************************************/
   @Test
   void orphanRecoveryAndIdempotentStop() throws Exception
   {
      AssistantTurn stale = remote(Instant.now().minusSeconds(5));
      store.updateFields(AssistantMessage.TABLE_NAME, new QRecord().withValue("id", stale.getAssistantMessageId())
         .withValue("partsJson", new MessageParts().addText("changed:v1").toJson()));
      AssistantTurn fresh = remote(Instant.now());
      AssistantTurn deadline = remote(Instant.now());
      store.updateFields(AssistantTurn.TABLE_NAME, new QRecord().withValue("id", deadline.getId()).withValue("startedAt", Instant.now().minusSeconds(30)));
      assertThat(active()).extracting(r -> r.get("turnId")).containsExactly(fresh.getId());
      assertThat(store.getTurn(EDITOR, stale.getId()).getStatus()).isEqualTo("ERROR");
      assertThat(store.getMessage(EDITOR, stale.getAssistantMessageId()).getPartsJson()).contains("changed:v1", "server restarted");
      assertThat(store.getTurn(EDITOR, deadline.getId()).getStatus()).isEqualTo("ERROR");
      assertThat(api.turnEvents(stale.getId(), 0).persisted()).extracting(TurnEvent::getType).containsExactly(TurnEvent.ERROR, TurnEvent.MESSAGE_END);
      assertThat(api.turnEvents(stale.getId(), 0).persisted()).hasSize(2);
      assertThat(api.cancelTurn(stale.getId())).containsEntry("status", "ERROR");
      assertThat(api.cancelTurn(fresh.getId())).containsEntry("status", "CANCELLING");
      assertThat(store.getTurn(EDITOR, fresh.getId()).getCancelRequestedAt()).isNotNull();
      store.updateFields(AssistantTurn.TABLE_NAME, new QRecord().withValue("id", fresh.getId()).withValue("cancelRequestedAt", Instant.now().minusSeconds(5)));
      assertThat(active()).isEmpty();
      assertThat(store.getTurn(EDITOR, fresh.getId()).getStatus()).isEqualTo("CANCELLED");
      AssistantTurn stopped = remote(Instant.now().minusSeconds(5));
      assertThat(api.cancelTurn(stopped.getId())).containsEntry("status", "CANCELLED");
      assertThat(api.cancelTurn(stopped.getId())).containsEntry("status", "CANCELLED");
      provider.thenText("Recovered");
      assertThat(completed(api.startTurn(conversation(), Map.of("text", "next"))).getStatus()).isEqualTo("COMPLETE");
   }



   /*******************************************************************************
    ** Two replicas reaping the same row produce only one terminal frame.
    *******************************************************************************/
   @Test
   void simultaneousReapers() throws Exception
   {
      AssistantTurn stale = remote(Instant.now().minusSeconds(5));
      AssistantRuntime other = new AssistantRuntime(qInstance, config);
      CountDownLatch start = new CountDownLatch(1);
      try(var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
      {
         List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
         for(AssistantRuntime runtime : List.of(config.getRuntime(), other))
         {
            futures.add(executor.submit(() ->
            {
               start.await();
               QContext.init(qInstance, session);
               try
               {
                  new AssistantApi(runtime, session).activeTurns();
               }
               finally
               {
                  QContext.clear();
               }
               return (null);
            }));
         }
         start.countDown();
         for(var future : futures)
         {
            future.get(5, java.util.concurrent.TimeUnit.SECONDS);
         }
         List<TurnEvent> events = api.turnEvents(stale.getId(), 0).persisted();
         assertThat(events).filteredOn(TurnEvent::isTerminal).hasSize(1);
         assertThat(store.getTurn(EDITOR, stale.getId()).getStatus()).isEqualTo("ERROR");
      }
      finally
      {
         other.close();
      }
   }



   /*******************************************************************************
    ** A terminal outcome written elsewhere wins over the local engine's finish.
    *******************************************************************************/
   @Test
   void remoteTerminalOutcomeIsPreserved() throws Exception
   {
      ControlledProvider controlled = controlled(false);
      RunningTurn run = api.startTurn(conversation(), Map.of("text", "wait"));
      controlled.awaitCalls(1);
      store.insertEvent(run.getTurnId(), EDITOR, 50, TurnEvent.MESSAGE_END,
         new TurnEvent(50, TurnEvent.MESSAGE_END, Map.of("status", "ERROR", "stopReason", "interrupted")).toJson());
      store.updateFields(AssistantTurn.TABLE_NAME, new QRecord().withValue("id", run.getTurnId()).withValue("status", "ERROR")
         .withValue("phase", "done").withValue("errorCode", "INTERNAL").withValue("stopReason", "interrupted").withValue("endedAt", Instant.now()));
      controlled.release();
      assertThat(completed(run).getStatus()).isEqualTo("ERROR");
      assertThat(api.turnEvents(run.getTurnId(), 0).persisted()).filteredOn(TurnEvent::isTerminal).hasSize(1);
      assertThat(run.getLog().after(0, 1)).last().extracting(e -> e.getData().get("status")).isEqualTo("ERROR");
   }



   /*******************************************************************************
    ** Configuration rejects invalid lease timing and concurrency modes.
    *******************************************************************************/
   @Test
   void configurationValidation()
   {
      config.withTurnHeartbeatSeconds(5).withTurnLeaseSeconds(4).withDefaultConcurrencyMode("invalid");
      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);
      assertThat(errors).contains("turnHeartbeatSeconds must be less than turnLeaseSeconds.", "defaultConcurrencyMode must be SUPERSEDE or CONCURRENT.");
      config.withRunTracker(null);
      assertThat(config.getRunTracker()).isSameAs(AssistantRunTracker.NOOP);
   }



   /*******************************************************************************
    ** A different runtime writes Stop; the owner sees it within one heartbeat.
    *******************************************************************************/
   @Test
   void crossPodStopAndHeartbeat() throws Exception
   {
      ControlledProvider controlled = controlled(false);
      RunningTurn run = api.startTurn(conversation(), Map.of("text", "wait"));
      controlled.awaitCalls(1);
      AssistantRuntime remote = new AssistantRuntime(qInstance, config);
      try
      {
         assertThat(remote.getInstanceId()).isNotEqualTo(config.getRuntime().getInstanceId());
         AssistantApi otherPod = new AssistantApi(remote, session);
         assertThat(((List<?>) otherPod.activeTurns().get("turns"))).hasSize(1);
         assertThat(active().getFirst()).containsEntry("live", true).containsEntry("phase", "writing").containsKey("elapsedMs");
         assertThat(otherPod.cancelTurn(run.getTurnId())).containsEntry("status", "CANCELLING");
         AssistantTurn row = completed(run);
         assertThat(row.getStatus()).isEqualTo("CANCELLED");
         assertThat(row.getCancelReason()).isEqualTo("USER");
         assertThat(row.getHeartbeatAt()).isAfter(row.getStartedAt());
         assertThat(otherPod.cancelTurn(run.getTurnId())).containsEntry("status", "CANCELLED");
      }
      finally
      {
         controlled.release();
         remote.close();
      }
   }



   /*******************************************************************************
    ** Shutdown force-finalizes a wedged engine and refuses new inserts with 503.
    *******************************************************************************/
   @Test
   void rollingDeploy() throws Exception
   {
      ControlledProvider controlled = controlled(true);
      RecordingTracker tracker = new RecordingTracker();
      config.setRunTracker(tracker);
      Integer conversation = conversation();
      RunningTurn run = api.startTurn(conversation, Map.of("text", "wait"));
      try
      {
         controlled.awaitCalls(1);
         config.getRuntime().shutdown(Duration.ZERO);
         QContext.init(qInstance, session);
         AssistantTurn row = completed(run);
         assertThat(row.getStatus()).isEqualTo("ERROR");
         assertThat(row.getErrorCode()).isEqualTo("INTERRUPTED");
         assertThat(store.getMessage(EDITOR, row.getAssistantMessageId()).getPartsJson()).contains(TurnEngine.RESTART_TEXT);
         assertThat(tracker.finishes.get()).isEqualTo(1);
         int messages = store.listMessages(EDITOR, conversation, null, 100).size();
         assertThatThrownBy(() -> api.startTurn(conversation, Map.of("text", "retry")))
            .isInstanceOfSatisfying(AssistantApiException.class, e ->
            {
               assertThat(e.getStatus()).isEqualTo(503);
               assertThat(e.getCode()).isEqualTo("SHUTTING_DOWN");
            });
         assertThat(store.listMessages(EDITOR, conversation, null, 100)).hasSize(messages);
      }
      finally
      {
         controlled.release();
      }
   }



   /*******************************************************************************
    ** Graceful cancellation also uses the restart outcome, not a user cancel.
    *******************************************************************************/
   @Test
   void gracefulShutdown() throws Exception
   {
      ControlledProvider controlled = controlled(false);
      RunningTurn run = api.startTurn(conversation(), Map.of("text", "wait"));
      controlled.awaitCalls(1);
      config.getRuntime().shutdown(Duration.ofSeconds(1));
      QContext.init(qInstance, session);
      assertThat(completed(run).getErrorCode()).isEqualTo("INTERRUPTED");
   }



   /*******************************************************************************
    ** Tracker progress is phase-based; its Stop callback cancels the local run.
    *******************************************************************************/
   @Test
   void trackerAndTrackerFailures() throws Exception
   {
      ControlledProvider controlled = controlled(false);
      RecordingTracker tracker = new RecordingTracker();
      config.setRunTracker(tracker);
      RunningTurn run = api.startTurn(conversation(), Map.of("text", "wait"));
      controlled.awaitCalls(1);
      await(() -> tracker.cancel != null);
      tracker.cancel.run();
      assertThat(completed(run).getStatus()).isEqualTo("CANCELLED");
      assertThat(tracker.starts.get()).isEqualTo(1);
      assertThat(tracker.phases).containsExactly("thinking", "writing");
      assertThat(tracker.finishes.get()).isEqualTo(1);
      assertThat(tracker.label).isEqualTo("Assistant answer");
      tracker.fail = true;
      config.withModelProvider(provider);
      config.getRuntime().getProviderService().invalidate();
      provider.thenText("Still works");
      RunningTurn throwing = api.startTurn(conversation(), Map.of("text", "hello"));
      assertThat(completed(throwing).getStatus()).isEqualTo("COMPLETE");
      assertThat(tracker.finishes.get()).isEqualTo(2);
   }



   /*******************************************************************************
    ** Build a remote persisted turn with an assistant message to recover.
    *******************************************************************************/
   private AssistantTurn remote(Instant heartbeat) throws Exception
   {
      Integer conversation = conversation();
      AssistantMessage message = store.insert(new AssistantMessage().withConversationId(conversation).withUserId(EDITOR)
         .withRole("ASSISTANT").withStatus("STREAMING").withSeq(1).withPartsJson("[]"));
      return (store.insert(new AssistantTurn().withConversationId(conversation).withUserId(EDITOR).withAssistantMessageId(message.getId())
         .withStatus("RUNNING").withStartedAt(heartbeat).withHeartbeatAt(heartbeat).withLeaseOwner("dead-pod").withPhase("thinking")));
   }



   /*******************************************************************************
    ** Create a user-owned conversation.
    *******************************************************************************/
   private Integer conversation() throws Exception
   {
      return ((Integer) api.createConversation(Map.of()).get("id"));
   }



   /*******************************************************************************
    ** Read the active-run response.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private List<Map<String, Object>> active() throws Exception
   {
      return ((List<Map<String, Object>>) api.activeTurns().get("turns"));
   }



   /*******************************************************************************
    ** Wait for both persisted completion and local cleanup, then check replay.
    *******************************************************************************/
   private AssistantTurn completed(RunningTurn run) throws Exception
   {
      await(() -> config.getRuntime().getRunningTurn(run.getTurnId()) == null);
      assertThat(run.getLog().isClosed()).isTrue();
      assertThat(run.getLog().after(0, 1)).filteredOn(TurnEvent::isTerminal).hasSize(1);
      AssistantTurn row = store.getTurn(EDITOR, run.getTurnId());
      assertThat(row.getStatus()).isNotIn("RUNNING", "CANCELLING");
      assertThat(api.turnEvents(run.getTurnId(), 0).persisted()).last().extracting(TurnEvent::getType).isEqualTo(TurnEvent.MESSAGE_END);
      return (row);
   }



   /*******************************************************************************
    ** Install a deterministic provider before the first call resolves it.
    *******************************************************************************/
   private ControlledProvider controlled(boolean stubborn)
   {
      ControlledProvider result = new ControlledProvider(stubborn);
      config.withModelProvider(result);
      return (result);
   }



   /*******************************************************************************
    ** Short polling with a hard bound, without timing-sensitive fixed waits.
    *******************************************************************************/
   private static void await(BooleanSupplier condition) throws InterruptedException
   {
      long until = System.nanoTime() + Duration.ofSeconds(6).toNanos();
      while(!condition.getAsBoolean() && System.nanoTime() < until)
      {
         Thread.sleep(5);
      }
      assertThat(condition.getAsBoolean()).as("condition reached within six seconds").isTrue();
   }



   /*******************************************************************************
    ** MemoryRecordStore iterates its synchronized maps without holding their
    ** lock. Serialize individual backend calls, as in RobustnessTest, so the
    ** fixture models atomic database operations while turns still overlap.
    *******************************************************************************/
   private static class AtomicCallsStore extends AssistantStore
   {
      private static final Object BACKEND_LOCK = new Object();

      /*******************************************************************************
       ** Preserve encryption and other configured persistence behavior.
       *******************************************************************************/
      AtomicCallsStore(AssistantQBitConfig config)
      {
         super(config);
      }

      /*******************************************************************************
       ** Make a memory query atomic with respect to insert/update operations.
       *******************************************************************************/
      @Override
      public <T extends QRecordEntity> List<T> query(String table, QQueryFilter filter, Class<T> type) throws QException
      {
         synchronized(BACKEND_LOCK)
         {
            return (super.query(table, filter, type));
         }
      }

      /*******************************************************************************
       ** Make an insert atomic with respect to memory queries.
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
       ** Make an update atomic with respect to memory queries.
       *******************************************************************************/
      @Override
      public void updateFields(String table, QRecord record) throws QException
      {
         synchronized(BACKEND_LOCK)
         {
            super.updateFields(table, record);
         }
      }
   }



   /*******************************************************************************
    ** Streams partial text, then blocks until explicitly released.
    *******************************************************************************/
   private static class ControlledProvider extends ScriptedModelProvider
   {
      private final boolean stubborn;
      private String classifiedText;
      private final List<ControlledHandle> handles = new CopyOnWriteArrayList<>();
      private boolean emitText = true;

      /*******************************************************************************
       ** Configure whether cancel and interruption are deliberately ignored.
       *******************************************************************************/
      ControlledProvider(boolean stubborn)
      {
         this.stubborn = stubborn;
      }

      /*******************************************************************************
       ** Start a controlled stream.
       *******************************************************************************/
      @Override
      public StreamHandle stream(ModelRequest request, ModelEventListener listener)
      {
         ControlledHandle handle = new ControlledHandle(stubborn);
         if(classifiedText != null)
         {
            TranscriptRedactor.current().observe("blog_post", "title", 1, classifiedText, classifiedText);
            listener.onTextDelta(classifiedText);
         }
         if(emitText)
         {
            listener.onTextDelta("A partial answer that has already begun streaming to the user. ");
         }
         handles.add(handle);
         return (handle);
      }

      /*******************************************************************************
       ** Await a known number of started calls.
       *******************************************************************************/
      void awaitCalls(int count) throws InterruptedException
      {
         await(() -> handles.size() >= count);
      }

      /*******************************************************************************
       ** Always release test threads, including deliberately wedged ones.
       *******************************************************************************/
      void release()
      {
         handles.forEach(h -> h.release.countDown());
      }
   }



   /*******************************************************************************
    ** A model that can deliberately violate the provider timeout contract.
    *******************************************************************************/
   private static class ControlledHandle implements StreamHandle
   {
      private final boolean stubborn;
      private final CountDownLatch release = new CountDownLatch(1);
      private volatile boolean cancelled;
      private volatile boolean returned;

      /*******************************************************************************
       ** Configure cancellation behavior.
       *******************************************************************************/
      ControlledHandle(boolean stubborn)
      {
         this.stubborn = stubborn;
      }

      /*******************************************************************************
       ** Abort unless this is the wedged-provider test.
       *******************************************************************************/
      @Override
      public void cancel()
      {
         cancelled = true;
         if(!stubborn)
         {
            release.countDown();
         }
      }

      /*******************************************************************************
       ** Observe cancellation.
       *******************************************************************************/
      @Override
      public boolean isCancelled()
      {
         return (cancelled);
      }

      /*******************************************************************************
       ** Block deterministically; stubborn mode ignores timeouts and interrupts.
       *******************************************************************************/
      @Override
      public ModelResponse await(Duration timeout) throws InterruptedException
      {
         while(release.getCount() != 0)
         {
            try
            {
               release.await();
            }
            catch(InterruptedException e)
            {
               if(!stubborn)
               {
                  throw (e);
               }
            }
         }
         returned = true;
         return (new ModelResponse().withParts(List.of(ModelPart.text("Done")))
            .withStopReason(cancelled ? ModelResponse.STOP_CANCELLED : ModelResponse.STOP_END_TURN));
      }
   }



   /*******************************************************************************
    ** Records the host job-tray contract and can throw from every callback.
    *******************************************************************************/
   private static class RecordingTracker implements AssistantRunTracker
   {
      private final AtomicInteger starts = new AtomicInteger();
      private final AtomicInteger finishes = new AtomicInteger();
      private final List<String> phases = new CopyOnWriteArrayList<>();
      private volatile Runnable cancel;
      private String label;
      private boolean fail;

      /*******************************************************************************
       ** Record publication.
       *******************************************************************************/
      @Override
      public String start(String userId, Integer turnId, Integer conversationId, String label)
      {
         starts.incrementAndGet();
         this.label = label;
         if(fail)
         {
            throw (new IllegalStateException("tracker start failed"));
         }
         return ("job-" + turnId);
      }

      /*******************************************************************************
       ** Record a phase transition.
       *******************************************************************************/
      @Override
      public void progress(String trackingId, String phase, String message)
      {
         phases.add(phase);
         if(fail)
         {
            throw (new IllegalStateException("tracker progress failed"));
         }
      }

      /*******************************************************************************
       ** Record terminal notification exactly once.
       *******************************************************************************/
      @Override
      public void finish(String trackingId, String status, String message)
      {
         finishes.incrementAndGet();
         if(fail)
         {
            throw (new IllegalStateException("tracker finish failed"));
         }
      }

      /*******************************************************************************
       ** Retain the host's Stop callback.
       *******************************************************************************/
      @Override
      public void onCancel(String trackingId, Runnable cancel)
      {
         this.cancel = cancel;
      }
   }
}
