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

package com.kingsrook.qbits.assistant;


import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.access.DataAccessMode;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.UiApiTestServer;
import com.kingsrook.qbits.assistant.tools.FilterLinkBuilder;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Confirmed writes retain the human actor and link only to their channel's audits.
 *******************************************************************************/
class AssistantAuditAttributionTest extends BaseAssistantTest
{
   /*******************************************************************************
    ** Insert, update and delete all create usable audit links, including deletions.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"create_record", "update_record", "delete_record"})
   void confirmedWriteAttributesActorAndChannel(String tool) throws Exception
   {
      String base = UiApiTestServer.serve(qInstance, config);
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
      var store = config.getRuntime().getStore();
      store.insert(new AssistantDataAccess().withObjectType(AssistantDataAccess.TYPE_TABLE).withObjectName("page").withMode(DataAccessMode.READ_WRITE.name()));
      config.getRuntime().getDataAccessService().invalidate();
      AssistantTurn turn = store.insert(new AssistantTurn().withUserId(AssistantTestFixture.ADMIN));
      AssistantPendingAction action = store.insert(new AssistantPendingAction().withUserId(AssistantTestFixture.ADMIN).withTurnId(turn.getId())
         .withToolCallId("write-1").withToolName(tool).withTableName("page").withRecordId("12")
         .withParamsJson(JsonUtils.toJson(tool.equals("delete_record") ? Map.of() : Map.of("title", "Assistant changed this page")))
         .withStatus(AssistantPendingAction.STATUS_PENDING).withExpiresAt(Instant.now().plusSeconds(600)));
      Map<String, Object> result = new AssistantApi(config.getRuntime(), QContext.getQSession()).confirmAction(action.getId());
      assertThat(result.get("status")).isEqualTo(AssistantPendingAction.STATUS_EXECUTED);
      String channel = "assistant/" + turn.getId();
      List<QRecord> audits = records("audit", new QQueryFilter().withCriteria("clientChannel", QCriteriaOperator.EQUALS, channel));
      assertThat(audits).hasSize(1);
      QRecord audit = audits.getFirst();
      assertHumanActor(audit);
      assertThat(records("auditTable", new QQueryFilter().withCriteria("id", QCriteriaOperator.EQUALS, audit.getValue("auditTableId"))).getFirst().getValueString("name")).isEqualTo("page");
      List<AssistantToolCall> calls = store.query(AssistantToolCall.TABLE_NAME, new QQueryFilter().withCriteria("pendingActionId", QCriteriaOperator.EQUALS, action.getId()), AssistantToolCall.class);
      assertThat(calls).hasSize(1);
      assertThat(calls.getFirst().getClientChannel()).isEqualTo(channel);
      String href = calls.getFirst().getAuditHref();
      assertThat(href).startsWith("/app/audit?filter=");
      QQueryFilter filter = JsonUtils.toObject(URLDecoder.decode(URI.create(href).getRawQuery().substring("filter=".length()), StandardCharsets.UTF_8), QQueryFilter.class);
      assertThat(filter.getCriteria()).hasSize(3);
      assertThat(filter.getCriteria()).anySatisfy(criterion ->
      {
         assertThat(criterion.getFieldName()).isEqualTo("clientChannel");
         assertThat(criterion.getValues()).containsExactly(channel);
      });
      assertThat(filter.getCriteria()).anySatisfy(criterion ->
      {
         assertThat(criterion.getFieldName()).isEqualTo("auditTable.name");
         assertThat(criterion.getValues()).containsExactly("page");
      });
      assertThat(filter.getCriteria()).anySatisfy(criterion ->
      {
         assertThat(criterion.getFieldName()).isEqualTo("recordId");
         assertThat(criterion.getValues()).extracting(String::valueOf).containsExactly(audit.getValueString("recordId"));
      });
      UiApiResult linked = ui(base, "POST", "/table/audit/query", JsonUtils.toJson(Map.of("filter", filter)));
      assertThat(linked.status()).isEqualTo(200);
      assertThat((List<?>) linked.json().get("records")).hasSize(1);

      String directMethod = tool.equals("create_record") ? "POST" : tool.equals("update_record") ? "PATCH" : "DELETE";
      String directPath = "/table/page" + (tool.equals("create_record") ? "" : "/11");
      UiApiResult direct = ui(base, directMethod, directPath, tool.equals("delete_record") ? null : "{\"title\":\"UI changed this page\"}");
      assertThat(direct.status()).isBetween(200, 299);
      List<QRecord> directAudits = records("audit", new QQueryFilter().withCriteria("clientChannel", QCriteriaOperator.EQUALS, "next-ui"));
      assertThat(directAudits).hasSize(1).allSatisfy(this::assertHumanActor);
   }



   /*******************************************************************************
    ** Encoded filters for string keys must survive persistence without truncation.
    *******************************************************************************/
   @Test
   void storesLongAuditLinks() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
      String href = FilterLinkBuilder.href("audit", List.of(
         Map.of("fieldName", "recordId", "operator", "EQUALS", "values", List.of("opaque/record key ".repeat(12))),
         Map.of("fieldName", "auditTable.name", "operator", "EQUALS", "values", List.of("customer_external_document")),
         Map.of("fieldName", "clientChannel", "operator", "EQUALS", "values", List.of("assistant/42"))));
      assertThat(href.length()).isGreaterThan(500);
      assertThat(qInstance.getTable(AssistantToolCall.TABLE_NAME).getField("auditHref").getMaxLength()).isNull();
      AssistantToolCall call = config.getRuntime().getStore().insert(new AssistantToolCall().withUserId(AssistantTestFixture.ADMIN).withAuditHref(href));
      assertThat(config.getRuntime().getStore().query(AssistantToolCall.TABLE_NAME,
         new QQueryFilter().withCriteria("id", QCriteriaOperator.EQUALS, call.getId()), AssistantToolCall.class).getFirst().getAuditHref()).isEqualTo(href);
   }



   /*******************************************************************************
    ** Explain access carries an owned turn, or the bare channel outside a turn.
    *******************************************************************************/
   @Test
   void explainAccessUsesOwnedTurnOrBareChannel() throws Exception
   {
      UiApiTestServer.serve(qInstance, config);
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
      AssistantTurn turn = config.getRuntime().getStore().insert(new AssistantTurn().withUserId(AssistantTestFixture.ADMIN));
      AssistantApi api = new AssistantApi(config.getRuntime(), QContext.getQSession());
      api.access("page", null, turn.getId());
      assertThat(UiApiTestServer.SCHEDULED_REQUESTS.getLast().get("client")).isEqualTo("assistant/" + turn.getId());
      api.access("page", null);
      assertThat(UiApiTestServer.SCHEDULED_REQUESTS.getLast().get("client")).isEqualTo("assistant");
      AssistantTurn other = config.getRuntime().getStore().insert(new AssistantTurn().withUserId(AssistantTestFixture.EDITOR));
      assertThatThrownBy(() -> api.access("page", null, other.getId())).hasMessageContaining("not found");
   }



   /*******************************************************************************
    ** Read the fixture's audit evidence independently of the assistant's gateway.
    *******************************************************************************/
   private List<QRecord> records(String table, QQueryFilter filter) throws Exception
   {
      return (new QueryAction().execute(new QueryInput(table).withFilter(filter)).getRecords());
   }



   /*******************************************************************************
    ** Core's audit user is the authenticated human's display name.
    *******************************************************************************/
   private void assertHumanActor(QRecord audit) throws Exception
   {
      assertThat(records("auditUser", new QQueryFilter().withCriteria("id", QCriteriaOperator.EQUALS, audit.getValue("auditUserId"))).getFirst().getValueString("name"))
         .isEqualTo("Name of " + AssistantTestFixture.ADMIN);
   }



   /*******************************************************************************
    ** A direct Next UI request from the same administrator.
    *******************************************************************************/
   private UiApiResult ui(String base, String method, String path, String body) throws Exception
   {
      HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/qqq/v1" + path)).header("Cookie", "sessionId=" + AssistantTestFixture.ADMIN)
         .header("X-QQQ-Client", "next-ui").header("Content-Type", "application/json")
         .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
      HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
      return (new UiApiResult(response.statusCode(), response.body()));
   }
}
