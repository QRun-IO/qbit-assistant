/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.assistant.scheduled;


import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.classification.DataClassification;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantScheduledRun;
import com.kingsrook.qbits.assistant.model.AssistantScheduledTask;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.processes.ScheduledTaskProcess;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.UiApiTestServer;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.implementations.MockAuthenticationModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;



/*******************************************************************************
 ** Scheduled runs use real manageSession and UI API requests on the fixture.
 *******************************************************************************/
class ScheduledTasksTest extends BaseAssistantTest
{
   private static final Instant NOW = Instant.parse("2026-03-08T06:55:00Z");
   private final Map<String, String> secrets = new HashMap<>();
   private final List<AssistantScheduledRun> notifications = new ArrayList<>();
   private AssistantRuntime runtime;



   /*******************************************************************************
    ** Dedicated environment references; the test resolver contains fake tokens.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      config.withAllowedScheduledCredentialEnvNames(Set.of("SCHEDULED_TOKEN")).withSecretResolver(secrets::get)
         .withDataClassification("patient", DataClassification.PHI);
   }



   /*******************************************************************************
    ** Fixed time for due selection, cron, budgets and persisted timestamps.
    *******************************************************************************/
   @BeforeEach
   void setupSchedule()
   {
      config.getRuntime().close();
      runtime = new AssistantRuntime(qInstance, config, Clock.fixed(NOW, ZoneOffset.UTC));
      config.setRuntime(runtime);
      qInstance.getAuthentication().setModuleCodeReference(new QCodeReference(TokenMock.class));
      secrets.put("SCHEDULED_TOKEN", AssistantTestFixture.EDITOR);
      admin();
   }



   /*******************************************************************************
    ** RC.5 MOCK maps sessionId but ignores accessToken. This test-only module
    ** maps login tokens to that same fixture identity, preserving all grants.
    *******************************************************************************/
   public static class TokenMock extends MockAuthenticationModule
   {
      /*******************************************************************************
       ** Login and subsequent UUID resolution use identical fixture sessions.
       *******************************************************************************/
      @Override
      public QSession createSession(QInstance instance, Map<String, String> context) throws QAuthenticationException
      {
         Map<String, String> mapped = new HashMap<>(context);
         if(context.get("accessToken") != null)
         {
            mapped.put("sessionId", context.get("accessToken"));
         }
         QSession session = super.createSession(instance, mapped);
         AssistantSecurity.applySessionKeys(session, AssistantRuntime.of(instance).getConfig());
         return (session);
      }
   }



   /*******************************************************************************
    ** Bookkeeping starts in the scheduler/admin context supplied by the host.
    *******************************************************************************/
   private void admin()
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
   }



   /*******************************************************************************
    ** A due daily task, crossing Chicago's spring daylight-saving transition.
    *******************************************************************************/
   private AssistantScheduledTask task() throws Exception
   {
      AssistantScheduledTask task = runtime.getStore().insert(new AssistantScheduledTask().withName("Morning report").withEnabled(true)
         .withPrompt("Summarize today's posts").withRunAsUserId(AssistantTestFixture.EDITOR).withCredentialEnvName("SCHEDULED_TOKEN")
         .withCronExpression("0 0 8 * * ?").withTimezone("America/Chicago").withOutputMode("RECORD").withWriteModeRequested(false));
      runtime.getStore().scheduledUpdate(new QRecord().withValue("id", task.getId()).withValue("nextRunAt", NOW.minusSeconds(60)));
      return (runtime.getStore().scheduledTask(task.getId()));
   }



   /*******************************************************************************
    ** Capture notification delivery without changing the runner.
    *******************************************************************************/
   private ScheduledTaskRunner scheduledRunner()
   {
      return (new ScheduledTaskRunner(runtime, new ScheduledLogin(), new ScheduledAlerts()
      {
         /*******************************************************************************
          ** Delegate using this instance's configured collaborator.
          *******************************************************************************/
         @Override
         public void raise(AssistantScheduledRun run)
         {
            notifications.add(run);
         }
      }));
   }



   /*******************************************************************************
    ** One due slot, one owned conversation; next wall-clock occurrence uses DST.
    *******************************************************************************/
   @Test
   void dueRunsOnceAndAdvancesAcrossDst() throws Exception
   {
      AssistantScheduledTask task = task();
      provider.thenText("Today's report");
      assertThat(scheduledRunner().runDue()).isEqualTo(1);
      assertThat(scheduledRunner().runDue()).isZero();
      var runs = runtime.getStore().query(AssistantScheduledRun.TABLE_NAME, new QQueryFilter(), AssistantScheduledRun.class);
      assertThat(runs).hasSize(1);
      AssistantScheduledRun run = runs.get(0);
      assertThat(run.getStatus()).as(run.getError()).isEqualTo("OK");
      assertThat(run.getSummary()).isEqualTo("Today's report");
      assertThat(run.getStartedAt()).isEqualTo(NOW);
      var conversation = runtime.getStore().getConversation(AssistantTestFixture.EDITOR, run.getConversationId());
      assertThat(conversation.getUserId()).isEqualTo(AssistantTestFixture.EDITOR);
      assertThat(conversation.getChannel()).isEqualTo("assistant/scheduled");
      assertThat(conversation.getScheduledTaskId()).isEqualTo(task.getId());
      assertThat(runtime.getStore().scheduledTask(task.getId()).getNextRunAt()).isEqualTo(Instant.parse("2026-03-08T13:00:00Z"));
      assertThat(QContext.getQSession().getUser().getIdReference()).isEqualTo(AssistantTestFixture.ADMIN);
      assertThat(notifications).isEmpty();
   }



   /*******************************************************************************
    ** Every tool HTTP call carries both the designated UUID and client channel.
    *******************************************************************************/
   @Test
   void designatedUserDenialAndClientHeaders() throws Exception
   {
      AssistantScheduledTask task = task();
      provider.thenToolUse("query_records", input("table", "analytics_daily_stat", "filter", filter("AND"))).thenText("Access denied");
      AssistantScheduledRun run = scheduledRunner().run(task, false);
      assertThat(run.getStatus()).as(run.getError()).isEqualTo("OK");
      var calls = runtime.getStore().query(AssistantToolCall.TABLE_NAME, new QQueryFilter().withCriteria("turnId", QCriteriaOperator.EQUALS, run.getTurnId()), AssistantToolCall.class);
      assertThat(calls).hasSize(1);
      assertThat(calls.get(0).getHttpStatus()).isEqualTo(403);
      assertThat(calls.get(0).getClientChannel()).isEqualTo("assistant/scheduled/" + task.getId());
      assertThat(UiApiTestServer.SCHEDULED_REQUESTS).isNotEmpty().allSatisfy(request ->
      {
         assertThat(request.get("client")).isEqualTo("assistant/scheduled/" + task.getId());
         assertThat(request.get("session")).isEqualTo(AssistantTestFixture.EDITOR);
      });
      QContext.init(qInstance, AssistantTestFixture.ownerSession("editor-other", config));
      assertThat(runtime.getStore().query(AssistantScheduledRun.TABLE_NAME, new QQueryFilter(), AssistantScheduledRun.class)).isEmpty();
      assertThat(runtime.getStore().getConversation("editor-other", run.getConversationId())).isNull();
   }



   /*******************************************************************************
    ** Credential failures occur before a model call and never expose a token.
    *******************************************************************************/
   @Test
   void credentialFailures() throws Exception
   {
      AssistantScheduledTask task = task();
      secrets.clear();
      assertThat(scheduledRunner().run(task, false).getError()).contains("not set");
      secrets.put("SCHEDULED_TOKEN", "analyst-other");
      task = task();
      assertThat(scheduledRunner().run(task, false).getError()).contains("another user");
      secrets.put("SCHEDULED_TOKEN", "Deny");
      task = task();
      assertThat(scheduledRunner().run(task, false).getError()).contains("HTTP");
      task = task();
      config.setAllowedScheduledCredentialEnvNames(Set.of());
      AssistantScheduledRun run = scheduledRunner().run(task, false);
      assertThat(run.getStatus()).as(run.getError()).isEqualTo("FAILED");
      assertThat(run.getError()).contains("not allowed");
      assertThat(provider.getRequests()).isEmpty();
   }



   /*******************************************************************************
    ** Independent runner instances compete for the same durable schedule slot.
    *******************************************************************************/
   @Test
   void racingRunnersClaimOnlyOnce() throws Exception
   {
      AssistantScheduledTask task = task();
      provider.thenText("Once");
      CyclicBarrier barrier = new CyclicBarrier(2);
      try(var executor = Executors.newVirtualThreadPerTaskExecutor())
      {
         var work = (Callable<AssistantScheduledRun>) () ->
         {
            admin();
            barrier.await();
            return (scheduledRunner().run(task, false));
         };
         var one = executor.submit(work);
         var two = executor.submit(work);
         assertThat(Stream.of(one.get(), two.get()).filter(Objects::nonNull)).hasSize(1);
      }
      assertThat(provider.getRequests()).hasSize(1);
   }



   /*******************************************************************************
    ** Per-run caps stop before further dispatch, including a zero-cost allowance.
    *******************************************************************************/
   @Test
   void costCapStopsRun() throws Exception
   {
      AssistantScheduledTask task = task();
      runtime.getStore().updateFields(AssistantScheduledTask.TABLE_NAME, new QRecord().withValue("id", task.getId()).withValue("maxCostUsdPerRun", BigDecimal.ZERO));
      AssistantScheduledRun run = scheduledRunner().run(task, false);
      assertThat(run.getStatus()).as(run.getError()).isEqualTo("BLOCKED");
      assertThat(run.getError()).contains("BUDGET");
      assertThat(provider.getRequests()).isEmpty();
      task = task();
      runtime.getStore().updateFields(AssistantScheduledTask.TABLE_NAME, new QRecord().withValue("id", task.getId()).withValue("maxCostUsdPerRun", new BigDecimal("0.000001")));
      provider.thenToolUse("count_records", input("table", "page", "filter", filter("AND"))).thenText("Must not run");
      assertThat(scheduledRunner().run(task, false).getStatus()).isEqualTo("BLOCKED");
      assertThat(provider.getRequests()).hasSize(1);
   }



   /*******************************************************************************
    ** Approved writes are pending proposals even when normal auto-confirm is on.
    *******************************************************************************/
   @Test
   void approvalAndPromptInvalidation() throws Exception
   {
      AssistantScheduledTask task = task();
      secrets.put("SCHEDULED_TOKEN", AssistantTestFixture.ADMIN);
      runtime.getStore().updateFields(AssistantScheduledTask.TABLE_NAME, new QRecord().withValue("id", task.getId()).withValue("runAsUserId", AssistantTestFixture.ADMIN).withValue("writeModeRequested", true));
      runtime.getStore().insert(new AssistantDataAccess().withObjectType("TABLE").withObjectName("page").withMode("READ_WRITE"));
      runtime.getDataAccessService().invalidate();
      provider.thenText("Read only");
      assertThat(scheduledRunner().run(task, false).getStatus()).isEqualTo("OK");
      assertThat(provider.getRequests().get(0).getTools()).noneMatch(tool -> tool.getName().equals("create_record"));
      task = runtime.getStore().scheduledTask(task.getId());
      action(task, "Approve");
      assertThat(ScheduledTasks.writesApproved(runtime.getStore().scheduledTask(task.getId()))).isTrue();
      provider.thenToolUse("create_record", input("table", "page", "values", Map.of("title", "Scheduled proposal"))).thenText("Please confirm");
      AssistantScheduledRun run = scheduledRunner().run(task, true);
      assertThat(run.getStatus()).as(run.getError()).isEqualTo("OK");
      assertThat(run.getSummary()).contains("Waiting for your confirmation");
      assertThat(provider.getRequests().get(1).getTools()).anyMatch(tool -> tool.getName().equals("create_record"));
      assertThat(runtime.getStore().query(AssistantPendingAction.TABLE_NAME, new QQueryFilter(), AssistantPendingAction.class)).hasSize(1);
      runtime.getStore().updateFields(AssistantScheduledTask.TABLE_NAME, new QRecord().withValue("id", task.getId()).withValue("prompt", "Changed prompt"));
      assertThat(runtime.getStore().scheduledTask(task.getId()).getWriteModeApprovedAt()).isNull();
      assertThat(runtime.getStore().scheduledTask(task.getId()).getWriteModeApprovalHash()).isNull();
      action(runtime.getStore().scheduledTask(task.getId()), "Approve");
      action(runtime.getStore().scheduledTask(task.getId()), "Revoke");
      assertThat(runtime.getStore().scheduledTask(task.getId()).getWriteModeApprovedAt()).isNull();
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.EDITOR, config));
      AssistantScheduledTask selected = task;
      assertThatThrownBy(() -> action(selected, "Approve")).hasMessageContaining("assistant.admin");
   }



   /*******************************************************************************
    ** Exercise the same Review/Result steps the record action uses.
    *******************************************************************************/
   private void action(AssistantScheduledTask task, String action) throws Exception
   {
      RunBackendStepInput input = new RunBackendStepInput();
      input.setRecords(List.of(task.toQRecord()));
      input.addValue("scheduledAction", action);
      RunBackendStepOutput reviewOutput = new RunBackendStepOutput();
      ScheduledTaskProcess.Review review = new ScheduledTaskProcess.Review();
      review.runOnePage(input, reviewOutput);
      assertThat(review.getProcessSummary(reviewOutput, false)).isNotEmpty();
      input.setRecords(reviewOutput.getRecords());
      RunBackendStepOutput resultOutput = new RunBackendStepOutput();
      ScheduledTaskProcess.Result result = new ScheduledTaskProcess.Result();
      result.runOnePage(input, resultOutput);
      assertThat(result.getProcessSummary(resultOutput, true)).isNotEmpty();
   }



   /*******************************************************************************
    ** Output modes retain minimal run history and route notifications by owner.
    *******************************************************************************/
   @Test
   void outputModesAndModelFailure() throws Exception
   {
      for(String mode : List.of("RECORD", "NOTIFICATION", "BOTH"))
      {
         AssistantScheduledTask task = task();
         runtime.getStore().updateFields(AssistantScheduledTask.TABLE_NAME, new QRecord().withValue("id", task.getId()).withValue("outputMode", mode));
         provider.thenText("Summary");
         assertThat(scheduledRunner().run(task, false).getStatus()).isEqualTo("OK");
      }
      assertThat(notifications).hasSize(2).allSatisfy(run -> assertThat(run.getUserId()).isEqualTo(AssistantTestFixture.EDITOR));
      provider.thenError(ModelProviderException.Kind.OTHER, "Private provider detail");
      AssistantScheduledRun failed = scheduledRunner().run(task(), false);
      assertThat(failed.getStatus()).isEqualTo("FAILED");
      assertThat(failed.getError()).doesNotContain("Private provider detail");
      new ScheduledAlerts().raise(failed);
   }



   /*******************************************************************************
    ** Validation rejects malformed schedules and secret-looking references.
    *******************************************************************************/
   @Test
   void validationAndManualProcess() throws Exception
   {
      AssistantScheduledTask task = task();
      assertThatThrownBy(() -> ScheduledTasks.next(task.withTimezone("No/SuchZone"), NOW)).hasMessageContaining("timezone");
      assertThatThrownBy(() -> ScheduledTasks.next(task.withTimezone("UTC").withCronExpression("invalid"), NOW)).hasMessageContaining("cron");
      assertThatThrownBy(() -> ScheduledTasks.checkReference(config, "token-SECRET")).hasMessageNotContaining("SECRET");
      assertThatThrownBy(() -> runtime.getStore().updateFields(AssistantScheduledTask.TABLE_NAME, new QRecord().withValue("id", task.getId()).withValue("prompt", " "))).hasMessageContaining("Prompt");
      AssistantScheduledTask saved = runtime.getStore().scheduledTask(task.getId());
      assertThat(saved.getCredentialStatus()).contains("Set").doesNotContain(AssistantTestFixture.EDITOR);
      provider.thenText("Manual");
      action(saved, "RunNow");
      config.setScheduledTasksSchedulerName("hostScheduler");
      assertThat(ScheduledTaskProcess.processes(config).get(0).getSchedule().getRepeatSeconds()).isEqualTo(60);
      new ScheduledTaskProcess.Runner().run(new RunBackendStepInput(), new RunBackendStepOutput());
   }



   /*******************************************************************************
    ** The scheduled profile supplies persona text and normal PHI refusal applies.
    *******************************************************************************/
   @Test
   void selectedProfileAndPhiBlock() throws Exception
   {
      AssistantProfile profile = runtime.getStore().insert(new AssistantProfile().withName("Scheduled").withDisplayName("Daily helper").withEnabled(true)
         .withPersonality("Use a concise daily briefing style."));
      AssistantScheduledTask task = task();
      runtime.getStore().updateFields(AssistantScheduledTask.TABLE_NAME, new QRecord().withValue("id", task.getId()).withValue("profileId", profile.getId()));
      provider.thenToolUse("get_record", input("table", "patient", "id", 1)).thenText("Must not reach a noneligible provider");
      AssistantScheduledRun run = scheduledRunner().run(task, false);
      assertThat(run.getStatus()).as(run.getError()).isEqualTo("BLOCKED");
      assertThat(provider.getRequests()).hasSize(1);
      assertThat(provider.getRequests().get(0).getSystem().get(1).getText()).contains("Daily helper");
   }



   /*******************************************************************************
    ** HIPAA retains the answer only in encrypted conversation storage.
    *******************************************************************************/
   @Test
   void hipaaSummaryAndEncryption() throws Exception
   {
      secrets.put("CONVERSATION_KEY", Base64.getEncoder().encodeToString(new byte[32]));
      config.withHipaaMode(true).withConversationEncryptionKeyEnvName("CONVERSATION_KEY");
      config.withProviderType(new AssistantProviderType()
      {
         /*******************************************************************************
          ** Delegate using this instance's configured collaborator.
          *******************************************************************************/
         @Override
         public String getType()
         {
            return ("scheduled-test");
         }

         /*******************************************************************************
          ** Delegate using this instance's configured collaborator.
          *******************************************************************************/
         @Override
         public String getLabel()
         {
            return ("Scheduled test");
         }

         /*******************************************************************************
          ** Delegate using this instance's configured collaborator.
          *******************************************************************************/
         @Override
         public List<String> getModels()
         {
            return (List.of("test-model"));
         }

         /*******************************************************************************
          ** Delegate using this instance's configured collaborator.
          *******************************************************************************/
         @Override
         public String getDefaultApiKeyEnvName()
         {
            return (null);
         }

         /*******************************************************************************
          ** Delegate using this instance's configured collaborator.
          *******************************************************************************/
         @Override
         public String getDefaultBaseUrl()
         {
            return ("http://localhost");
         }

         /*******************************************************************************
          ** Delegate using this instance's configured collaborator.
          *******************************************************************************/
         @Override
         public AssistantModelProvider create(String key, String base)
         {
            return (provider);
         }
      });
      runtime.close();
      runtime = new AssistantRuntime(qInstance, config, Clock.fixed(NOW, ZoneOffset.UTC));
      config.setRuntime(runtime);
      AssistantScheduledRun blockedRun = scheduledRunner().run(task(), false);
      assertThat(blockedRun.getStatus()).isEqualTo("BLOCKED");
      assertThat(blockedRun.getSummary()).isEqualTo("see conversation");
      assertThat(provider.getRequests()).isEmpty();
      runtime.getStore().insert(new AssistantProvider().withName("Eligible local")
         .withProviderType("scheduled-test").withModel("test-model").withEnabled(true).withIsDefault(true).withLocation("LOCAL").withHipaaEligible(true));
      provider.thenText("PHI answer sentinel");
      AssistantScheduledRun run = scheduledRunner().run(task(), false);
      assertThat(run.getStatus()).as(run.getError()).isEqualTo("OK");
      assertThat(run.getSummary()).isEqualTo("see conversation");
      var records = new QueryAction().execute(
         new QueryInput("assistant_message")).getRecords();
      assertThat(records).isNotEmpty().allSatisfy(record -> assertThat(record.getValueString("partsJson")).startsWith("enc:v1:").doesNotContain("sentinel"));
   }



   /*******************************************************************************
    ** Instance/user budgets and the kill switch also stop scheduled starts.
    *******************************************************************************/
   @Test
   void normalPolicyStillApplies() throws Exception
   {
      AssistantScheduledTask task = task();
      runtime.getStore().insert(new AssistantPolicy().withName("Stop").withKillSwitch(true));
      assertThat(scheduledRunner().run(task, false).getStatus()).isEqualTo("BLOCKED");
      assertThat(provider.getRequests()).isEmpty();
      runtime.getStore().delete("assistant_policy", new QQueryFilter());
      config.getBudgets().setUserDailyCostMicrosHard(0L);
      AssistantScheduledRun budgetRun = scheduledRunner().run(task(), false);
      assertThat(budgetRun.getStatus()).isEqualTo("BLOCKED");
      assertThat(budgetRun.getError()).contains("BUDGET");
      assertThat(provider.getRequests()).isEmpty();
   }



   /*******************************************************************************
    ** Application record locks stay effective with a designated-user login.
    *******************************************************************************/
   @Test
   void applicationRowsRemainOwnerLocked() throws Exception
   {
      qInstance.getTable("blog_post").withRecordSecurityLock(new RecordSecurityLock()
         .withFieldName("slug").withSecurityKeyType(config.getOwnerSecurityKeyType()));
      assertThat(runtime.getUiApiGateway().invoke(new UiApiCall("PATCH", "/table/{tableName}/{primaryKey}")
         .withPathParam("tableName", "blog_post").withPathParam("primaryKey", "1").withSessionUUID(AssistantTestFixture.ADMIN)
         .withJsonBody(Map.of("slug", AssistantTestFixture.EDITOR))).status()).isEqualTo(200);
      provider.thenToolUse("query_records", input("table", "blog_post", "filter", filter("AND"))).thenText("Your one post");
      AssistantScheduledRun run = scheduledRunner().run(task(), false);
      assertThat(run.getStatus()).as(run.getError()).isEqualTo("OK");
      var calls = runtime.getStore().query(AssistantToolCall.TABLE_NAME, new QQueryFilter().withCriteria("turnId", QCriteriaOperator.EQUALS, run.getTurnId()), AssistantToolCall.class);
      assertThat(calls).singleElement().satisfies(call -> assertThat(call.getRowCount()).isEqualTo(1));
   }
}
