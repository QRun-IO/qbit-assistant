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

package com.kingsrook.qbits.assistant.jobs;


import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.engine.RunningTurn;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qbits.assistant.spi.ModelEventListener;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.spi.ModelResponse;
import com.kingsrook.qbits.assistant.spi.StreamHandle;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.uiapi.CoreUiApiGateway;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.jobs.DurableJobsMetaData;
import com.kingsrook.qqq.backend.core.jobs.DurableJobsMetaDataProvider;
import com.kingsrook.qqq.backend.core.jobs.model.JobStatus;
import com.kingsrook.qqq.backend.core.jobs.progress.JobPhase;
import com.kingsrook.qqq.backend.core.jobs.progress.JobProgressSnapshot;
import com.kingsrook.qqq.backend.core.jobs.runtime.DurableJobsRuntime;
import com.kingsrook.qqq.backend.core.jobs.store.DurableJobStore;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.EDITOR;
import static com.kingsrook.qqq.backend.core.jobs.DurableJobsTables.TABLE_JOB;
import static com.kingsrook.qqq.backend.core.jobs.DurableJobsTables.TABLE_LOG;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Real Jobs tray rows, progress and cancellation for assistant answers.
 *******************************************************************************/
class JobTrackingRunTrackerTest extends BaseAssistantTest
{
   private static final String PROMPT = "PRIVATE-PROMPT-JOBS-CANARY";
   private static final String ANSWER = "PRIVATE-ANSWER-JOBS-CANARY";

   private final CountDownLatch toolEntered = new CountDownLatch(1);
   private final CountDownLatch toolRelease = new CountDownLatch(1);
   private DurableJobStore jobs;
   private AssistantApi api;
   private ControlledProvider controlled;



   /*******************************************************************************
    ** Add jobs only to this fixture, leaving runtime startup explicit per test.
    *******************************************************************************/
   @BeforeEach
   void initializeJobs() throws Exception
   {
      new DurableJobsMetaDataProvider().defineAll(qInstance, AssistantTestFixture.BACKEND, null);
      DurableJobsMetaData settings = new DurableJobsMetaData().withWorkerThreads(1).withLeaseTtlSeconds(3)
         .withReaperIntervalSeconds(1).withPollIntervalMillis(50).withPodId("test-pod");
      qInstance.withSupplementalMetaData(settings);
      new QInstanceEnricher(qInstance).enrich();
      jobs = new DurableJobStore(settings);

      config.getRuntime().close();
      config.withUiApiGateway(new CoreUiApiGateway(config::getUiApiBaseUrl, qInstance)
      {
         /*******************************************************************************
          ** Hold the real count call so the tool phase can be inspected in storage.
          *******************************************************************************/
         @Override
         public UiApiResult invoke(UiApiCall call) throws QException
         {
            if("/table/{tableName}/count".equals(call.getPathTemplate()))
            {
               toolEntered.countDown();
               try
               {
                  assertThat(toolRelease.await(15, TimeUnit.SECONDS)).as("tool released").isTrue();
               }
               catch(InterruptedException e)
               {
                  Thread.currentThread().interrupt();
                  throw (new QException("Test tool interrupted", e));
               }
            }
            return (super.invoke(call));
         }
      });
      config.setRuntime(new AssistantRuntime(qInstance, config));
      var session = AssistantTestFixture.ownerSession(EDITOR, config);
      QContext.init(qInstance, session);
      api = new AssistantApi(config.getRuntime(), session);
      assertThat(config.getRunTracker()).isExactlyInstanceOf(JobTrackingRunTracker.class);
   }



   /*******************************************************************************
    ** Release every gate before draining both runtimes, even after an assertion.
    *******************************************************************************/
   @AfterEach
   void stopJobs()
   {
      toolRelease.countDown();
      if(controlled != null)
      {
         controlled.handles.forEach(handle -> handle.release.countDown());
      }
      config.getRuntime().close();
      DurableJobsRuntime.stop(qInstance, Duration.ofSeconds(5));
   }



   /*******************************************************************************
    ** A single linked owner job progresses through thinking, tool and writing.
    *******************************************************************************/
   @Test
   void answerTracksProgressAndCompletes() throws Exception
   {
      assertThat(DurableJobsRuntime.start(qInstance)).isNotNull();
      ControlledHandle thinking = new ControlledHandle(new ModelResponse().withParts(List.of(
         ModelPart.toolUse("count-1", "count_records", input("table", "blog_post", "filter", filter("AND")))))
         .withStopReason(ModelResponse.STOP_TOOL_USE), null, false);
      ControlledHandle writing = answer(false);
      useProvider(thinking, writing);
      Integer conversationId = (Integer) api.createConversation(Map.of()).get("id");
      RunningTurn run = api.startTurn(conversationId, Map.of("text", PROMPT));
      entered(thinking);
      QRecord job = onlyJob();
      Integer jobId = job.getValueInteger("id");
      assertThat(job.getValueString("processName")).isEqualTo("assistant.turn");
      assertThat(job.getValueString("label")).isEqualTo("Assistant answer");
      assertThat(job.getValueString("userId")).isEqualTo(EDITOR);
      assertThat(job.getValueString("tableName")).isEqualTo(AssistantConversation.TABLE_NAME);
      assertThat(JsonUtils.toObject(job.getValueString("sourceJson"), Map.class)).containsEntry("recordId", conversationId);
      assertPhase(jobId, "thinking", null);

      thinking.release.countDown();
      assertThat(toolEntered.await(15, TimeUnit.SECONDS)).as("tool entered").isTrue();
      assertPhase(jobId, "tool", "Counting records");
      toolRelease.countDown();
      entered(writing);
      assertPhase(jobId, "writing", null);
      writing.release.countDown();

      assertThat(completed(run).getStatus()).isEqualTo("COMPLETE");
      job = onlyJob();
      assertThat(job.getValueInteger("id")).isEqualTo(jobId);
      assertThat(job.getValueString("status")).isEqualTo("COMPLETED");
      assertThat(job.getValueString("progressMessage")).isEqualTo("Answered.");
      assertThat(job.getValueString("statusReason")).isEqualTo("Answered.");
      assertThat(run.getLog().after(0, 1)).filteredOn(event -> TurnEvent.TOOL_RESULT.equals(event.getType()))
         .singleElement().satisfies(event -> assertThat(event.getData()).containsEntry("outcome", "OK"));
      assertPrivateTextAbsent();
   }



   /*******************************************************************************
    ** The runtime's periodic tick observes the same row change as Jobs tray Stop.
    *******************************************************************************/
   @Test
   void jobsTrayStopCancelsAnswer() throws Exception
   {
      DurableJobsRuntime.start(qInstance);
      ControlledHandle writing = answer(false);
      useProvider(writing);
      RunningTurn run = startTurn();
      entered(writing);
      Integer jobId = onlyJob().getValueInteger("id");
      jobs.updateJob(jobId, Map.of("status", "CANCELLING"));

      assertThat(completed(run).getStatus()).isEqualTo("CANCELLED");
      assertThat(writing.isCancelled()).isTrue();
      assertThat(onlyJob().getValueString("status")).isEqualTo("CANCELLED");
      assertThat(onlyJob().getValueString("progressMessage")).isEqualTo("Stopped.");
      assertPrivateTextAbsent();
   }



   /*******************************************************************************
    ** Even partial output and provider errors containing private text stay out.
    *******************************************************************************/
   @Test
   void failedAnswerHasOnlyFixedJobMessages() throws Exception
   {
      DurableJobsRuntime.start(qInstance);
      ControlledHandle writing = answer(true);
      useProvider(writing);
      RunningTurn run = startTurn();
      entered(writing);
      writing.release.countDown();

      assertThat(completed(run).getStatus()).isEqualTo("ERROR");
      QRecord job = onlyJob();
      assertThat(job.getValueString("status")).isEqualTo("FAILED");
      assertThat(job.getValueString("progressMessage")).isEqualTo("The answer did not finish.");
      assertThat(job.getValueString("statusReason")).isEqualTo("The answer did not finish.");
      assertThat(jobs.query(TABLE_LOG, new QQueryFilter())).isNotEmpty();
      assertPrivateTextAbsent();
   }



   /*******************************************************************************
    ** Hosts without jobs keep ordinary answers and safe null tracker callbacks.
    *******************************************************************************/
   @Test
   void absentRuntimeDoesNotTrackOrDisruptAnswer() throws Exception
   {
      assertThat(DurableJobsRuntime.get(qInstance)).isNull();
      AssistantRunTracker tracker = config.getRunTracker();
      assertThat(tracker.start(EDITOR, 1, 1, "ignored")).isNull();
      tracker.progress(null, "thinking", PROMPT);
      tracker.onCancel(null, () ->
      {
         throw (new AssertionError("No job to cancel"));
      });
      tracker.finish(null, "COMPLETE", ANSWER);
      provider.thenText(ANSWER);
      RunningTurn run = startTurn();
      assertThat(completed(run).getStatus()).isEqualTo("COMPLETE");
      assertThat(jobs.query(TABLE_JOB, new QQueryFilter())).isEmpty();
      assertThat(jobs.query(TABLE_LOG, new QQueryFilter())).isEmpty();
   }



   /*******************************************************************************
    ** Core mutates phase objects; every new job must start with fresh phases.
    *******************************************************************************/
   @Test
   void jobsHaveIndependentPhaseState() throws Exception
   {
      DurableJobsRuntime.start(qInstance);
      AssistantRunTracker tracker = config.getRunTracker();
      Integer conversationId = (Integer) api.createConversation(Map.of()).get("id");
      String first = tracker.start(EDITOR, 1, conversationId, PROMPT);
      tracker.progress(first, "writing", ANSWER);
      String second = tracker.start(EDITOR, 2, conversationId, PROMPT);
      QRecord secondJob = jobs.getJob(Integer.valueOf(second));
      JobProgressSnapshot snapshot = JsonUtils.toObject(secondJob.getValueString("progressJson"), JobProgressSnapshot.class);
      assertThat(snapshot.getPhases()).extracting(JobPhase::getStatus)
         .containsExactly(JobPhase.Status.PENDING, JobPhase.Status.PENDING, JobPhase.Status.PENDING);
      tracker.progress(second, "thinking", PROMPT);
      tracker.progress(first, "unknown", PROMPT);
      tracker.progress("missing", "tool", PROMPT);
      assertPhase(Integer.valueOf(first), "writing", null);
      assertPhase(Integer.valueOf(second), "thinking", null);
      tracker.finish(first, "COMPLETE", ANSWER);
      tracker.finish(second, "COMPLETE", ANSWER);
      assertPrivateTextAbsent();
   }



   /*******************************************************************************
    ** Terminal mapping is fixed, including unknown and absent assistant statuses.
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource({ "COMPLETE,COMPLETED,Answered.", "CANCELLED,CANCELLED,Stopped.",
      "ERROR,FAILED,The answer did not finish.", "unknown,FAILED,The answer did not finish.",
      ",FAILED,The answer did not finish." })
   void terminalMapping(String status, JobStatus expected, String message)
   {
      assertThat(JobTrackingRunTracker.jobStatus(status)).isEqualTo(expected);
      assertThat(JobTrackingRunTracker.finishMessage(status)).isEqualTo(message);
   }



   /*******************************************************************************
    ** Use independently controlled model calls without changing the tracker.
    *******************************************************************************/
   private void useProvider(ControlledHandle... handles)
   {
      controlled = new ControlledProvider(List.of(handles));
      config.withModelProvider(controlled);
   }



   /*******************************************************************************
    ** Partial text is emitted before this response is released or fails.
    *******************************************************************************/
   private ControlledHandle answer(boolean fail)
   {
      return (new ControlledHandle(new ModelResponse().withParts(List.of(ModelPart.text(ANSWER)))
         .withStopReason(ModelResponse.STOP_END_TURN), ANSWER, fail));
   }



   /*******************************************************************************
    ** Create a conversation and answer as the fixture's editor.
    *******************************************************************************/
   private RunningTurn startTurn() throws Exception
   {
      Integer conversationId = (Integer) api.createConversation(Map.of()).get("id");
      return (api.startTurn(conversationId, Map.of("text", PROMPT)));
   }



   /*******************************************************************************
    ** Wait until a model call has emitted its optional text and is blocked.
    *******************************************************************************/
   private void entered(ControlledHandle handle) throws InterruptedException
   {
      assertThat(handle.entered.await(15, TimeUnit.SECONDS)).as("model call entered").isTrue();
   }



   /*******************************************************************************
    ** Check persisted progress while the answer is still running.
    *******************************************************************************/
   private void assertPhase(Integer jobId, String phase, String message) throws QException
   {
      QRecord job = jobs.getJob(jobId);
      assertThat(job.getValueString("status")).isEqualTo("RUNNING");
      assertThat(job.getValueString("currentPhase")).isEqualTo(phase);
      assertThat(job.getValueString("progressMessage")).isEqualTo(message);
   }



   /*******************************************************************************
    ** Assert uniqueness across all jobs, not just those matching this turn.
    *******************************************************************************/
   private QRecord onlyJob() throws QException
   {
      List<QRecord> rows = jobs.query(TABLE_JOB, new QQueryFilter());
      assertThat(rows).hasSize(1);
      return (rows.getFirst());
   }



   /*******************************************************************************
    ** Inspect every persisted job and log field for both private canaries.
    *******************************************************************************/
   private void assertPrivateTextAbsent() throws QException
   {
      for(String table : List.of(TABLE_JOB, TABLE_LOG))
      {
         assertThat(jobs.query(table, new QQueryFilter())).allSatisfy(row ->
            assertThat(JsonUtils.toJson(row)).doesNotContain(PROMPT, ANSWER));
      }
   }



   /*******************************************************************************
    ** Wait for finalization and cleanup before reading the assistant turn.
    *******************************************************************************/
   private AssistantTurn completed(RunningTurn run) throws Exception
   {
      await(() -> config.getRuntime().getRunningTurn(run.getTurnId()) == null);
      assertThat(run.getLog().isClosed()).isTrue();
      return (config.getRuntime().getStore().getTurn(EDITOR, run.getTurnId()));
   }



   /*******************************************************************************
    ** Bound polling by observable completion rather than fixed timing guesses.
    *******************************************************************************/
   private static void await(Callable<Boolean> condition) throws Exception
   {
      long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      while(!condition.call() && System.nanoTime() < deadline)
      {
         Thread.sleep(10);
      }
      assertThat(condition.call()).as("condition reached within fifteen seconds").isTrue();
   }



   /*******************************************************************************
    ** Script model responses with separate gates for thinking and writing.
    *******************************************************************************/
   private static class ControlledProvider extends ScriptedModelProvider
   {
      private final List<ControlledHandle> handles;
      private final AtomicInteger next = new AtomicInteger();

      /*******************************************************************************
       ** Retain the scripted handles for teardown and assertions.
       *******************************************************************************/
      ControlledProvider(List<ControlledHandle> handles)
      {
         this.handles = handles;
      }

      /*******************************************************************************
       ** Publish partial text, then let the engine wait on the selected handle.
       *******************************************************************************/
      @Override
      public StreamHandle stream(ModelRequest request, ModelEventListener listener)
      {
         ControlledHandle handle = handles.get(next.getAndIncrement());
         if(handle.text != null)
         {
            listener.onTextDelta(handle.text);
         }
         return (handle);
      }
   }



   /*******************************************************************************
    ** A bounded, cancellable model call that can fail after streaming text.
    *******************************************************************************/
   private static class ControlledHandle implements StreamHandle
   {
      private final ModelResponse response;
      private final String text;
      private final boolean fail;
      private final CountDownLatch entered = new CountDownLatch(1);
      private final CountDownLatch release = new CountDownLatch(1);
      private volatile boolean cancelled;

      /*******************************************************************************
       ** Set the eventual response and optional partial-output failure.
       *******************************************************************************/
      ControlledHandle(ModelResponse response, String text, boolean fail)
      {
         this.response = response;
         this.text = text;
         this.fail = fail;
      }

      /*******************************************************************************
       ** Stop unblocks the engine without test intervention.
       *******************************************************************************/
      @Override
      public void cancel()
      {
         cancelled = true;
         release.countDown();
      }

      /*******************************************************************************
       ** Report whether the real cancellation callback reached the provider.
       *******************************************************************************/
      @Override
      public boolean isCancelled()
      {
         return (cancelled);
      }

      /*******************************************************************************
       ** Wait until the test or the Jobs tray releases this model call.
       *******************************************************************************/
      @Override
      public ModelResponse await(Duration timeout) throws ModelProviderException, InterruptedException
      {
         entered.countDown();
         if(!release.await(timeout.toMillis(), TimeUnit.MILLISECONDS))
         {
            throw (new ModelProviderException(ModelProviderException.Kind.TIMEOUT, null, "Test provider timed out"));
         }
         if(cancelled)
         {
            return (new ModelResponse().withStopReason(ModelResponse.STOP_CANCELLED));
         }
         if(fail)
         {
            throw (new ModelProviderException(ModelProviderException.Kind.OTHER, null, PROMPT + " " + ANSWER));
         }
         return (response);
      }
   }
}
