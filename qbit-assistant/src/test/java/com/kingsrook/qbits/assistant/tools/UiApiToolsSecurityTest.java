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

package com.kingsrook.qbits.assistant.tools;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.access.DataAccessMode;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.UiApiTestServer;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolOutcome;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The owner's rule (2026-10-13): the assistant goes through the same API the
 ** UI does, so it is held to the same security boundaries. For each case the
 ** UI's own request (a direct /qqq/v1 call with the user's session cookie) and
 ** the assistant's tool call are made side by side: the status and the error
 ** message must be identical, and the assistant may only ever be narrower
 ** (data access NONE / READ).
 *******************************************************************************/
class UiApiToolsSecurityTest extends BaseAssistantTest
{
   private final HttpClient client = HttpClient.newHttpClient();



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void clear()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** The UI's request: METHOD /qqq/v1{path} as the user's browser sends it.
    *******************************************************************************/
   private HttpResponse<String> ui(String method, String path, String user, String body) throws Exception
   {
      String              base    = UiApiTestServer.serve(qInstance, config);
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/qqq/v1" + path)).header("Cookie", "sessionId=" + user).timeout(Duration.ofSeconds(30));
      if(body != null)
      {
         builder.header("Content-Type", "application/json");
      }
      builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
      return (client.send(builder.build(), HttpResponse.BodyHandlers.ofString()));
   }



   /*******************************************************************************
    ** The assistant's tool call, as the turn engine makes it.
    *******************************************************************************/
   private UiApiToolbox.Outcome tool(String user, PolicyService.ToolPolicy policy, String toolName, Map<String, Object> in)
   {
      UiApiTestServer.serve(qInstance, config);
      QContext.init(qInstance, AssistantTestFixture.ownerSession(user, config));
      try
      {
         return (config.getRuntime().getToolbox().execute(new UiApiToolbox.Context(user, null, config.getRuntime().getUiApiGateway(), policy,
            write -> config.getRuntime().getStore().insert(new AssistantPendingAction().withUserId(user).withToolName(write.toolName()).withTableName(write.tableName())
               .withRecordId(write.recordId()).withUiApiOperation(write.operation()).withParamsJson(com.kingsrook.qqq.backend.core.utils.JsonUtils.toJson(write.values()))
               .withSummary(write.summary()).withStatus(AssistantPendingAction.STATUS_PENDING)
               .withExpiresAt(java.time.Instant.now().plusSeconds(600))).getId()), "call-1", toolName, in));
      }
      finally
      {
         QContext.clear();
      }
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
    **
    *******************************************************************************/
   private static PolicyService.ToolPolicy noConfirmation()
   {
      return (new PolicyService.ToolPolicy(false, Set.of(), 50, true));
   }



   /*******************************************************************************
    ** No read permission: the UI gets 403; the assistant gets the same 403 and
    ** the same message, as a DENIED tool result.
    *******************************************************************************/
   @Test
   void testReadDeniedIsTheSameDenial() throws Exception
   {
      String               body = "{\"filter\":{\"criteria\":[],\"booleanOperator\":\"AND\"}}";
      HttpResponse<String> ui   = ui("POST", "/table/blog_post/query", AssistantTestFixture.ANALYST, body);
      assertThat(ui.statusCode()).isEqualTo(403);

      UiApiToolbox.Outcome outcome = tool(AssistantTestFixture.ANALYST, PolicyService.ToolPolicy.defaults(), UiApiToolbox.QUERY_RECORDS,
         input("table", "blog_post", "filter", filter("AND")));
      assertThat(outcome.getHttpStatus()).isEqualTo(ui.statusCode());
      assertThat(outcome.getResult().getOutcome()).isEqualTo(AiToolOutcome.DENIED);
      assertThat(outcome.getOperation()).isEqualTo("POST /table/{tableName}/query");
      assertThat(outcome.getResult().getModelContent()).contains(new UiApiResult(ui.statusCode(), ui.body()).errorMessage());

      ////////////////////////////////////////////
      // get and count: the same, by status    //
      ////////////////////////////////////////////
      assertThat(tool(AssistantTestFixture.ANALYST, null, UiApiToolbox.GET_RECORD, input("table", "blog_post", "id", "1")).getHttpStatus())
         .isEqualTo(ui("GET", "/table/blog_post/1", AssistantTestFixture.ANALYST, null).statusCode());
      assertThat(tool(AssistantTestFixture.ANALYST, null, UiApiToolbox.COUNT_RECORDS, input("table", "blog_post", "filter", filter("AND"))).getHttpStatus())
         .isEqualTo(ui("POST", "/table/blog_post/count", AssistantTestFixture.ANALYST, body).statusCode());

      ////////////////////////////////////////////////////////////
      // and the table the user may read works, with no change  //
      ////////////////////////////////////////////////////////////
      UiApiToolbox.Outcome ok = tool(AssistantTestFixture.ANALYST, null, UiApiToolbox.COUNT_RECORDS, input("table", "analytics_daily_stat", "filter", filter("AND")));
      assertThat(ok.getResult().getOutcome()).isEqualTo(AiToolOutcome.OK);
      assertThat(ok.getHttpStatus()).isEqualTo(200);
   }



   /*******************************************************************************
    ** No insert permission: even with data access READ_WRITE and no
    ** confirmation, the write gets the UI's 403.
    *******************************************************************************/
   @Test
   void testWriteDeniedIsTheSameDenial() throws Exception
   {
      setAccess("blog_post", DataAccessMode.READ_WRITE);
      HttpResponse<String> ui = ui("POST", "/table/blog_post", AssistantTestFixture.EDITOR, "{\"title\":\"From the UI\"}");
      assertThat(ui.statusCode()).isEqualTo(403);

      UiApiToolbox.Outcome outcome = tool(AssistantTestFixture.EDITOR, noConfirmation(), UiApiToolbox.CREATE_RECORD,
         input("table", "blog_post", "values", Map.of("title", "From the assistant")));
      assertThat(outcome.getHttpStatus()).isEqualTo(403);
      assertThat(outcome.getResult().getOutcome()).isEqualTo(AiToolOutcome.DENIED);
      assertThat(outcome.getResult().getModelContent()).contains(new UiApiResult(ui.statusCode(), ui.body()).errorMessage());

      assertThat(tool(AssistantTestFixture.EDITOR, noConfirmation(), UiApiToolbox.DELETE_RECORD, input("table", "blog_post", "id", "1")).getHttpStatus())
         .isEqualTo(ui("DELETE", "/table/blog_post/1", AssistantTestFixture.EDITOR, null).statusCode());
   }



   /*******************************************************************************
    ** Data access only narrows: NONE hides a table the user can read; READ
    ** refuses writes the user could make; nothing raises a permission.
    *******************************************************************************/
   @Test
   void testDataAccessOnlyNarrows() throws Exception
   {
      assertThat(ui("POST", "/table/page/count", AssistantTestFixture.EDITOR, "{}").statusCode()).isEqualTo(200);
      setAccess("page", DataAccessMode.NONE);
      UiApiToolbox.Outcome hidden = tool(AssistantTestFixture.EDITOR, null, UiApiToolbox.COUNT_RECORDS, input("table", "page", "filter", filter("AND")));
      assertThat(hidden.getResult().getOutcome()).isEqualTo(AiToolOutcome.DENIED);
      assertThat(hidden.getHttpStatus()).as("no request is made").isNull();

      UiApiToolbox.Outcome list = tool(AssistantTestFixture.EDITOR, null, UiApiToolbox.LIST_TABLES, Map.of());
      assertThat(list.getResult().getModelContent()).contains("\"blog_post\"").doesNotContain("\"page\"").doesNotContain("analytics_daily_stat").doesNotContain("assistant_");

      //////////////////////////////////////////////////////////////////
      // the admin may insert in the UI, but blog_post is READ (the    //
      // default): the assistant refuses before any request           //
      //////////////////////////////////////////////////////////////////
      UiApiToolbox.Outcome readOnly = tool(AssistantTestFixture.ADMIN, noConfirmation(), UiApiToolbox.CREATE_RECORD, input("table", "blog_post", "values", Map.of("title", "x")));
      assertThat(readOnly.getResult().getOutcome()).isEqualTo(AiToolOutcome.DENIED);
      assertThat(readOnly.getHttpStatus()).isNull();
      assertThat(readOnly.getResult().getModelContent()).contains("READ");

      ///////////////////////////////////////////////////////////////////
      // a sensitive table (AI exposure NONE) cannot be raised         //
      ///////////////////////////////////////////////////////////////////
      setAccess("assistant_policy", DataAccessMode.READ_WRITE);
      assertThat(config.getRuntime().getDataAccessService().forTable("assistant_policy").mode()).isEqualTo(DataAccessMode.NONE);
   }



   /*******************************************************************************
    ** With confirmation on (the default) a write only proposes; confirming runs
    ** it through the UI API with the confirming user's session, once, and logs
    ** the operation, status and audit link.
    *******************************************************************************/
   @Test
   void testConfirmedWriteRunsThroughTheUiApi() throws Exception
   {
      setAccess("blog_post", DataAccessMode.READ_WRITE);
      UiApiToolbox.Outcome proposed = tool(AssistantTestFixture.ADMIN, PolicyService.ToolPolicy.defaults(), UiApiToolbox.CREATE_RECORD,
         input("table", "blog_post", "values", Map.of("title", "Proposed by the assistant", "status", "DRAFT")));
      assertThat(proposed.getResult().getOutcome()).isEqualTo(AiToolOutcome.OK);
      assertThat(proposed.getHttpStatus()).as("nothing runs before confirmation").isNull();
      assertThat(proposed.getPendingActionId()).isNotNull();
      assertThat(String.valueOf(proposed.getResult().getRender())).contains("pending_action");

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      AssistantApi api = new AssistantApi(config.getRuntime(), QContext.getQSession());
      assertThat(config.getRuntime().getStore().queryRecords("blog_post", new QQueryFilter().withCriteria("title", QCriteriaOperator.EQUALS, "Proposed by the assistant"), 10)).isEmpty();

      Map<String, Object> confirmed = api.confirmAction(proposed.getPendingActionId());
      assertThat(confirmed.get("status")).isEqualTo(AssistantPendingAction.STATUS_EXECUTED);
      assertThat(confirmed.get("outcome")).isEqualTo("OK");
      List<QRecord> created = config.getRuntime().getStore().queryRecords("blog_post", new QQueryFilter().withCriteria("title", QCriteriaOperator.EQUALS, "Proposed by the assistant"), 10);
      assertThat(created).hasSize(1);

      assertThatThrownBy(() -> api.confirmAction(proposed.getPendingActionId())).hasMessageContaining("already");

      List<AssistantToolCall> log = config.getRuntime().getStore().query(AssistantToolCall.TABLE_NAME,
         new QQueryFilter().withCriteria("pendingActionId", QCriteriaOperator.EQUALS, proposed.getPendingActionId()), AssistantToolCall.class);
      assertThat(log).hasSize(1);
      assertThat(log.get(0).getUiApiOperation()).isEqualTo("POST /table/{tableName}");
      assertThat(log.get(0).getHttpStatus()).isBetween(200, 299);
      assertThat(log.get(0).getAuditHref()).startsWith("/app/blog_post/");
      QContext.clear();
   }



   /*******************************************************************************
    ** The user who did not propose an action cannot see or confirm it (the
    ** owner record lock), and a rejected action never runs.
    *******************************************************************************/
   @Test
   void testOnlyTheOwnerDecides() throws Exception
   {
      setAccess("blog_post", DataAccessMode.READ_WRITE);
      UiApiToolbox.Outcome proposed = tool(AssistantTestFixture.EDITOR, PolicyService.ToolPolicy.defaults(), UiApiToolbox.UPDATE_RECORD,
         input("table", "blog_post", "id", "1", "values", Map.of("title", "Changed")));
      Integer actionId = proposed.getPendingActionId();
      assertThat(actionId).isNotNull();

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.OTHER_EDITOR));
      AssistantApi other = new AssistantApi(config.getRuntime(), QContext.getQSession());
      assertThatThrownBy(() -> other.confirmAction(actionId)).hasMessageContaining("not found");
      QContext.clear();

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.EDITOR));
      AssistantApi owner = new AssistantApi(config.getRuntime(), QContext.getQSession());
      assertThat(owner.rejectAction(actionId).get("status")).isEqualTo(AssistantPendingAction.STATUS_REJECTED);
      assertThatThrownBy(() -> owner.confirmAction(actionId)).hasMessageContaining("already");
      QContext.clear();
   }



   /*******************************************************************************
    ** Only spec'd /qqq/v1 operations can be invoked (the assistant's own raw
    ** routes are not reachable), and never without a session.
    *******************************************************************************/
   @Test
   void testOnlySpecdOperationsWithASession()
   {
      UiApiTestServer.serve(qInstance, config);
      assertThatThrownBy(() -> config.getRuntime().getUiApiGateway().invoke(new UiApiCall("POST", "/assistant/conversations").withSessionUUID(AssistantTestFixture.ADMIN)))
         .hasMessageContaining("Not a spec'd");
      assertThatThrownBy(() -> config.getRuntime().getUiApiGateway().invoke(new UiApiCall("GET", "/metaData")))
         .hasMessageContaining("session");
      for(String[] operation : UiApiToolbox.OPERATIONS.values())
      {
         assertThat(config.getRuntime().getUiApiGateway().isInvocable(operation[0], operation[1])).as(operation[0] + " " + operation[1]).isTrue();
      }
   }



   /*******************************************************************************
    ** The policy narrows the tools, and caps rows.
    *******************************************************************************/
   @Test
   void testPolicy()
   {
      PolicyService.ToolPolicy onlyCount = new PolicyService.ToolPolicy(true, Set.of(UiApiToolbox.COUNT_RECORDS), 2, true);
      assertThat(tool(AssistantTestFixture.EDITOR, onlyCount, UiApiToolbox.QUERY_RECORDS, input("table", "blog_post", "filter", filter("AND"))).getResult().getOutcome())
         .isEqualTo(AiToolOutcome.DENIED);

      PolicyService.ToolPolicy twoRows = new PolicyService.ToolPolicy(true, Set.of(), 2, true);
      UiApiToolbox.Outcome     capped  = tool(AssistantTestFixture.EDITOR, twoRows, UiApiToolbox.QUERY_RECORDS, input("table", "blog_post", "filter", filter("AND"), "limit", 50));
      assertThat(capped.getResult().getRowCount()).isEqualTo(2);
      assertThat(capped.getResult().getModelContent()).contains("\"hasMore\":true").doesNotContain(AssistantTestFixture.HIDDEN_SENTINEL).doesNotContain(AssistantTestFixture.PASSWORD_SENTINEL);
   }

}
