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

package com.kingsrook.qbits.assistant.tools.bulkload;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.access.DataAccessMode;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qbits.assistant.spi.ModelResponse;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolOutcome;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.savedbulkloadprofiles.SavedBulkLoadProfileMetaDataProvider;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.EvalSupport.firstResult;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The bulk-load tools: definitions, the UI API calls they make (as the
 ** user, with the route header from the provider, never the model), pending
 ** actions for writes, the confirmed save and run, and log hygiene. Plus the
 ** golden eval: a messy CSV -> profile -> dry run with an error -> fixed ->
 ** saved -> loaded.
 *******************************************************************************/
class BulkLoadToolsTest extends BaseAssistantTest
{
   private final FakeBulkLoadServer server  = new FakeBulkLoadServer(true);
   private final List<BulkLoadRunner.Request> runs = new ArrayList<>();



   /*******************************************************************************
    ** The fake server as the gateway, and a recording runner.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      config.withUiApiGateway(server).withBulkLoadRunner(request ->
      {
         runs.add(request);
         return (new BulkLoadRunner.RunHandle("AWAITING_CONFIRMATION", 42L, null, "Job 42 is waiting for your confirmation on its job record.", "POST /processes/{processName}/jobs", 201));
      });
   }



   /*******************************************************************************
    ** The saved-profile tables (so the tools are offered), and write access.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws QException
   {
      new SavedBulkLoadProfileMetaDataProvider().defineAll(qInstance, AssistantTestFixture.BACKEND, null);
      setAccess(BulkLoadTools.PROFILE_TABLE, DataAccessMode.READ_WRITE);
      setAccess("blog_post", DataAccessMode.READ_WRITE);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void clear()
   {
      QContext.clear();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void setAccess(String tableName, DataAccessMode mode) throws QException
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      try
      {
         config.getRuntime().getStore().insert(new AssistantDataAccess().withObjectType(AssistantDataAccess.TYPE_TABLE).withObjectName(tableName).withMode(mode.name()));
         config.getRuntime().getDataAccessService().invalidate();
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** One tool call as the turn engine makes it.
    *******************************************************************************/
   private UiApiToolbox.Outcome tool(String toolName, Map<String, Object> in, boolean hipaa)
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
      try
      {
         return (config.getRuntime().getBulkLoadTools().execute(new UiApiToolbox.Context("session-1", null, server, PolicyService.ToolPolicy.defaults(),
            write -> config.getRuntime().getStore().insert(new AssistantPendingAction().withUserId(AssistantTestFixture.ADMIN).withToolName(write.toolName())
               .withTableName(write.tableName()).withUiApiOperation(write.operation()).withParamsJson(JsonUtils.toJson(write.values()))
               .withSummary(write.summary()).withStatus(AssistantPendingAction.STATUS_PENDING).withExpiresAt(java.time.Instant.now().plusSeconds(600))).getId()),
            "call-1", toolName, in, hipaa));
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testDefinitionsAreOffered()
   {
      List<String> names = config.getRuntime().getContextBuilder().tools().stream().map(d -> d.getName()).toList();
      assertThat(names).contains(BulkLoadTools.LIST, BulkLoadTools.DESCRIBE, BulkLoadTools.ATTACH, BulkLoadTools.SUGGEST, BulkLoadTools.DRY_RUN);
      assertThat(config.getRuntime().getBulkLoadTools().definitions(PolicyService.ToolPolicy.defaults(), true))
         .extracting(d -> d.getName()).containsExactlyInAnyOrderElementsOf(BulkLoadTools.NAMES);
      assertThat(config.getRuntime().getBulkLoadTools().definitions(PolicyService.ToolPolicy.defaults(), false))
         .extracting(d -> d.getName()).doesNotContain(BulkLoadTools.SAVE, BulkLoadTools.RUN);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testReadToolsCallTheUiApiWithTheProvidersRoute()
   {
      UiApiToolbox.Outcome list = tool(BulkLoadTools.LIST, Map.of("tableName", "blog_post"), false);
      assertThat(list.getResult().getOutcome()).isEqualTo(AiToolOutcome.OK);
      assertThat(list.getResult().getModelContent()).contains("Posts from the CMS export");
      assertThat(list.getOperation()).isEqualTo("POST /table/{tableName}/query");

      UiApiToolbox.Outcome describe = tool(BulkLoadTools.DESCRIBE, Map.of("id", 3), false);
      assertThat(describe.getResult().getModelContent()).contains("fieldList");

      ////////////////////////////////////////////////////////////////////////
      // the route header comes from the provider flag, whatever the input //
      ////////////////////////////////////////////////////////////////////////
      tool(BulkLoadTools.ATTACH, Map.of("tableName", "blog_post", "uploadRef", "up_1", "X-QQQ-AI-Route", "hipaa", "route", "hipaa"), false);
      tool(BulkLoadTools.ATTACH, Map.of("tableName", "blog_post", "uploadRef", "up_1"), true);
      List<UiApiCall> attaches = server.calls("GET /bulkLoad/uploads/{uploadRef}/profile");
      assertThat(attaches.get(0).getHeaders()).containsEntry(BulkLoadTools.ROUTE_HEADER, "standard");
      assertThat(attaches.get(1).getHeaders()).containsEntry(BulkLoadTools.ROUTE_HEADER, "hipaa");
      assertThat(attaches.get(0).getSessionUUID()).isEqualTo("session-1");

      UiApiToolbox.Outcome missing = tool(BulkLoadTools.ATTACH, Map.of("tableName", "blog_post", "uploadRef", "nope"), false);
      assertThat(missing.getResult().getOutcome()).isEqualTo(AiToolOutcome.NOT_FOUND);

      UiApiToolbox.Outcome invalid = tool(BulkLoadTools.DRY_RUN, Map.of("tableName", "blog_post", "uploadRef", "up_1"), false);
      assertThat(invalid.getResult().getOutcome()).isEqualTo(AiToolOutcome.INVALID_INPUT);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testDataAccessAndAnOlderServer() throws QException
   {
      setAccess("page", DataAccessMode.NONE);
      UiApiToolbox.Outcome denied = tool(BulkLoadTools.SUGGEST, Map.of("tableName", "page", "uploadRef", "up_1"), false);
      assertThat(denied.getResult().getOutcome()).isEqualTo(AiToolOutcome.DENIED);
      assertThat(server.getCalls()).isEmpty();

      setAccess("media", DataAccessMode.READ);
      UiApiToolbox.Outcome readOnly = tool(BulkLoadTools.RUN, Map.of("tableName", "media", "uploadRef", "up_1", "profileId", 7), false);
      assertThat(readOnly.getResult().getOutcome()).isEqualTo(AiToolOutcome.DENIED);

      BulkLoadTools        older   = new BulkLoadTools(qInstance, config.getRuntime().getDataAccessService(), null);
      FakeBulkLoadServer   oldApi  = new FakeBulkLoadServer(false);
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
      UiApiToolbox.Outcome outcome = older.execute(new UiApiToolbox.Context("s", null, oldApi, null, null), "c", BulkLoadTools.SUGGEST,
         Map.of("tableName", "blog_post", "uploadRef", "up_1"), false);
      assertThat(outcome.getResult().getOutcome()).isEqualTo(AiToolOutcome.ERROR);
      assertThat(outcome.getResult().getModelContent()).contains("does not offer");
      assertThat(oldApi.getCalls()).isEmpty();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testJobsLaunchRunnerSaysWhenTheLaunchIsMissing() throws QException
   {
      BulkLoadRunner.RunHandle handle = new JobsLaunchBulkLoadRunner().start(new BulkLoadRunner.Request("s", null, server, "blog_post", "up_1", 7));
      assertThat(handle.status()).isEqualTo("NOT_AVAILABLE");
      assertThat(handle.summary()).contains("durable jobs launch");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testForLogDropsSamplesAndValues()
   {
      String logged = BulkLoadTools.forLog("<qqq_data source=\"x\">{\"columns\":[{\"header\":\"A\",\"sampleValues\":[\"" + FakeBulkLoadServer.SAMPLE_SENTINEL
         + "\"]}],\"summaryLines\":[{\"bulletsOfText\":[\"Row 3 [x]\"]}],\"profile\":{\"fieldList\":[{\"valueMappings\":{\"secret\":1}}]}}</qqq_data>");
      assertThat(logged).doesNotContain(FakeBulkLoadServer.SAMPLE_SENTINEL).doesNotContain("Row 3 [x]").doesNotContain("secret").contains("header");
      assertThat(BulkLoadTools.forLog(null)).isNull();
      assertThat(BulkLoadTools.forLog("plain")).isEqualTo("plain");
   }



   /*******************************************************************************
    ** Golden eval (owner request 2026-10-13): upload a CSV with messy headers;
    ** the assistant proposes a profile, validates it, fixes one error, saves
    ** it (shared with a group, after the user confirms) and runs the load
    ** (after the user confirms). The scripted model stands in for the LLM;
    ** its fix reads the dry run's error from the tool result.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testGoldenMessyCsvToSavedProfileAndLoad() throws Exception
   {
      Map<String, Object> attachments = Map.of("attachments", List.of(Map.of("uploadRef", "up_1", "fileName", "posts.csv")));
      AtomicReference<String> suggested = new AtomicReference<>();

      provider.thenToolUse(BulkLoadTools.ATTACH, Map.of("tableName", "blog_post", "uploadRef", "up_1"))
         .thenToolUse(BulkLoadTools.SUGGEST, Map.of("tableName", "blog_post", "uploadRef", "up_1"))
         .thenRespond(request ->
         {
            Map<String, Object> suggestion = firstResult(request);
            suggested.set(JsonUtils.toJson(suggestion.get("profile")));
            return (toolUse("dry1", BulkLoadTools.DRY_RUN, Map.of("tableName", "blog_post", "uploadRef", "up_1", "profile", suggestion.get("profile"))));
         })
         .thenRespond(request ->
         {
            ////////////////////////////////////////////////////////////////
            // read the error, fix it: default the missing required field //
            ////////////////////////////////////////////////////////////////
            Map<String, Object> dryRun = firstResult(request);
            assertThat(dryRun.get("errorCount")).isEqualTo(2);
            String missingField = String.valueOf(((Map<?, ?>) ((List<?>) dryRun.get("errors")).get(0)).get("fieldName"));
            Map<String, Object> profile = new JSONObject(suggested.get()).toMap();
            List<Map<String, Object>> fields = new ArrayList<>((List<Map<String, Object>>) profile.get("fieldList"));
            fields.add(new LinkedHashMap<>(Map.of("fieldName", missingField, "defaultValue", "DRAFT")));
            profile.put("fieldList", fields);
            return (toolUse("dry2", BulkLoadTools.DRY_RUN, Map.of("tableName", "blog_post", "uploadRef", "up_1", "profile", profile, "mode", "FULL")));
         })
         .thenRespond(request ->
         {
            Map<String, Object> profile = new JSONObject(suggested.get()).toMap();
            ((List<Map<String, Object>>) profile.get("fieldList")).add(new LinkedHashMap<>(Map.of("fieldName", "status", "defaultValue", "DRAFT")));
            return (toolUse("save", BulkLoadTools.SAVE, Map.of("tableName", "blog_post", "label", "CMS post export", "profile", profile,
               "shares", List.of(Map.of("audienceType", "group", "audienceId", "kof22-admins")))));
         })
         .thenText("The file maps cleanly once Status defaults to DRAFT (2 rows, no errors). Confirm to save the profile \"CMS post export\".");

      TurnRunner.Outcome first = runner.run(AssistantTestFixture.ADMIN, null, "Load the attached posts.csv into blog posts and save the mapping for next time.", attachments);
      List<String> tools = first.toolCalls().stream().map(AssistantToolCall::getToolName).toList();
      assertThat(tools).containsExactly(BulkLoadTools.ATTACH, BulkLoadTools.SUGGEST, BulkLoadTools.DRY_RUN, BulkLoadTools.DRY_RUN, BulkLoadTools.SAVE);

      List<UiApiCall> validates = server.calls("POST /bulkLoad/{tableName}/validate");
      assertThat(validates).hasSize(2);
      assertThat(validates).allSatisfy(c -> assertThat(c.getHeaders()).containsEntry(BulkLoadTools.ROUTE_HEADER, "standard"));
      assertThat(first.toolCalls().get(2).getResultSummary()).contains("2 with errors");
      assertThat(first.toolCalls().get(3).getResultSummary()).contains("0 with errors");
      assertThat(first.toolCalls()).allSatisfy(c -> assertThat(String.valueOf(c.getResultJson()) + c.getInputJson()).doesNotContain(FakeBulkLoadServer.SAMPLE_SENTINEL));
      assertThat(server.calls("POST /processes/{processName}/init")).as("nothing is saved before the user confirms").isEmpty();

      Integer saveActionId = first.toolCalls().get(4).getPendingActionId();
      assertThat(saveActionId).isNotNull();
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      Map<String, Object> saved = new AssistantApi(config.getRuntime(), QContext.getQSession()).confirmAction(saveActionId);
      QContext.clear();
      assertThat(saved.get("status")).isEqualTo(AssistantPendingAction.STATUS_EXECUTED);
      List<UiApiCall> processCalls = server.calls("POST /processes/{processName}/init");
      assertThat(processCalls).extracting(c -> c.getPathParams().get("processName")).containsExactly("storeSavedBulkLoadProfile", "insertSharedRecord");
      Map<String, Object> stored = FakeBulkLoadServer.formValues(processCalls.get(0));
      assertThat(stored).containsEntry("label", "CMS post export").containsEntry("tableName", "blog_post");
      assertThat(String.valueOf(stored.get("mappingJson"))).contains("DRAFT");
      assertThat(FakeBulkLoadServer.formValues(processCalls.get(1))).containsEntry("audienceType", "group").containsEntry("audienceId", "kof22-admins")
         .containsEntry("recordId", 7).containsEntry("scopeId", "READ_ONLY");

      ////////////////////////////////////////////////////////////////
      // second turn: run the load with the saved profile; confirm  //
      ////////////////////////////////////////////////////////////////
      provider.thenToolUse(BulkLoadTools.RUN, Map.of("tableName", "blog_post", "uploadRef", "up_1", "profileId", 7))
         .thenText("The load is queued as job 42; start it from its job record when you are ready.");
      TurnRunner.Outcome second = runner.run(AssistantTestFixture.ADMIN, first.conversationId(), "Now load it.", attachments);
      AssistantToolCall runCall = second.toolCalls().get(0);
      assertThat(runCall.getToolName()).isEqualTo(BulkLoadTools.RUN);
      assertThat(runs).as("nothing runs before the user confirms").isEmpty();

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      Map<String, Object> ran = new AssistantApi(config.getRuntime(), QContext.getQSession()).confirmAction(runCall.getPendingActionId());
      QContext.clear();
      assertThat(ran.get("outcome")).isEqualTo("OK");
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).savedBulkLoadProfileId()).isEqualTo(7);
      assertThat(runs.get(0).uploadRef()).isEqualTo("up_1");
      assertThat(runs.get(0).tableName()).isEqualTo("blog_post");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static ModelResponse toolUse(String id, String name, Map<String, Object> input)
   {
      return (new ModelResponse().withParts(List.of(ModelPart.toolUse("toolu_" + id, name, input))).withStopReason(ModelResponse.STOP_TOOL_USE));
   }
}
