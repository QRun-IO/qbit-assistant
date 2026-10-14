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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.metadata.AssistantChannelWall;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.UiApiTestServer;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.ClientChannelsMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.reporting.QReportMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The real UI API enforces the channel wall even for assistant administrators.
 *******************************************************************************/
class AssistantChannelWallTest extends BaseAssistantTest
{
   /*******************************************************************************
    ** Every produced object is explicitly denied, with core security defaults intact.
    *******************************************************************************/
   @Test
   void registersAllObjectsIdempotently()
   {
      QTableMetaData renamed = new QTableMetaData().withName("custom_settings");
      renamed.setSourceQBitName(AssistantQBitProducer.GROUP_ID + ":" + AssistantQBitProducer.ARTIFACT_ID);
      qInstance.addTable(renamed);
      qInstance.addReport(new QReportMetaData().withName("assistant_future_report").withPermissionRules(new QPermissionRules().withDeniedClients(Set.of("cli"))));
      AssistantChannelWall.register(qInstance);
      AssistantChannelWall.register(qInstance);
      List<MetaDataWithPermissionRules> objects = objects();
      assertThat(objects).isNotEmpty().allSatisfy(object -> assertThat(object.getPermissionRules().getDeniedClients()).contains("assistant"));
      assertThat(qInstance.getReport("assistant_future_report").getPermissionRules().getDeniedClients()).contains("cli");
      assertThat(ClientChannelsMetaData.of(qInstance).getDeniedPatterns().get("assistant"))
         .contains("assistant_*", "authentik_*", "audit*", "*api_key*", "*credential*", "*secret*")
         .doesNotHaveDuplicates();
      assertThat(renamed.getPermissionRules().getDeniedClients()).contains("assistant");
      AssistantChannelWall.validate(qInstance);
   }



   /*******************************************************************************
    ** Removing both layers from one object fails runtime startup, for every kind.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"table", "process", "report", "widget"})
   void startupRejectsAnUndeniedObject(String kind)
   {
      qInstance.addReport(new QReportMetaData().withName("assistant_report"));
      AssistantChannelWall.register(qInstance);
      MetaDataWithPermissionRules object = switch(kind)
      {
         case "table" -> qInstance.getTables().values().stream().filter(value -> value.getName().startsWith("assistant")).findFirst().orElseThrow();
         case "process" -> qInstance.getProcesses().values().stream().filter(value -> value.getName().startsWith("assistant")).findFirst().orElseThrow();
         case "report" -> qInstance.getReport("assistant_report");
         default -> qInstance.getWidgets().values().stream().filter(value -> value.getName().startsWith("assistant")).findFirst().orElseThrow();
      };
      ClientChannelsMetaData.of(qInstance).getDeniedPatterns().get("assistant").remove("assistant_*");
      object.getPermissionRules().withDeniedClients(Set.of());
      assertThatThrownBy(() -> new AssistantRuntime(qInstance, config)).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Assistant channel wall").hasMessageContaining(object.getName());
   }



   /*******************************************************************************
    ** Direct invocations bypass tool exposure checks, proving the wall lives in core.
    *******************************************************************************/
   @Test
   void adminDeniedAcrossEveryTableAndProcess() throws Exception
   {
      String base = UiApiTestServer.serve(qInstance, config);
      UiApiResult metadata = invoke(new UiApiCall("GET", "/metaData"));
      assertThat(metadata.status()).isEqualTo(200);
      UiApiResult uiMetadata = ui(base, "GET", "/metaData", null);
      assertThat(uiMetadata.status()).isEqualTo(200);
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
      UiApiToolbox.Outcome listed = config.getRuntime().getToolbox().execute(new UiApiToolbox.Context(AssistantTestFixture.ADMIN, null,
         config.getRuntime().getUiApiGateway(), null, null, null, "42"), "list", UiApiToolbox.LIST_TABLES, Map.of());
      for(String table : qInstance.getTables().keySet().stream().filter(name -> name.startsWith("assistant")).toList())
      {
         assertThat(((Map<?, ?>) metadata.json().get("tables")).containsKey(table)).isFalse();
         assertThat(((Map<?, ?>) uiMetadata.json().get("tables")).containsKey(table)).isTrue();
         assertThat(listed.getResult().getModelContent()).doesNotContain("\"" + table + "\"");
         for(UiApiCall call : List.of(
            new UiApiCall("POST", "/table/{tableName}/query").withJsonBody(Map.of()),
            new UiApiCall("GET", "/table/{tableName}/{primaryKey}").withPathParam("primaryKey", "1"),
            new UiApiCall("POST", "/table/{tableName}").withJsonBody(Map.of()),
            new UiApiCall("PATCH", "/table/{tableName}/{primaryKey}").withPathParam("primaryKey", "1").withJsonBody(Map.of()),
            new UiApiCall("DELETE", "/table/{tableName}/{primaryKey}").withPathParam("primaryKey", "1")))
         {
            assertThat(invoke(call.withPathParam("tableName", table)).status()).as("%s %s", table, call.operation()).isEqualTo(403);
         }
         assertThat(ui(base, "POST", "/table/" + table + "/query", "{}").status()).as(table).isEqualTo(200);
      }
      for(String process : qInstance.getProcesses().keySet().stream().filter(name -> name.startsWith("assistant")).toList())
      {
         assertThat(((Map<?, ?>) metadata.json().get("processes")).containsKey(process)).isFalse();
         assertThat(((Map<?, ?>) uiMetadata.json().get("processes")).containsKey(process)).isTrue();
         assertThat(invoke(new UiApiCall("POST", "/processes/{processName}/init").withPathParam("processName", process).withJsonBody(Map.of())).status()).as(process).isEqualTo(403);
         assertThat(ui(base, "POST", "/processes/" + process + "/init", "{}").status()).as(process).isEqualTo(200);
      }
      assertThat(UiApiTestServer.SCHEDULED_REQUESTS).anySatisfy(request -> assertThat(request.get("client")).isEqualTo("assistant/42"));
   }



   /*******************************************************************************
    ** Spoofed headers cannot turn an assistant call into a Next UI request.
    *******************************************************************************/
   private UiApiResult invoke(UiApiCall call) throws Exception
   {
      return (config.getRuntime().getUiApiGateway().invoke(call.withSessionUUID(AssistantTestFixture.ADMIN).withClientDetail("42").withHeader("X-QQQ-Client", "next-ui")));
   }



   /*******************************************************************************
    ** The same administrator's direct browser request.
    *******************************************************************************/
   private UiApiResult ui(String base, String method, String path, String body) throws Exception
   {
      HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/qqq/v1" + path)).header("Cookie", "sessionId=" + AssistantTestFixture.ADMIN)
         .header("X-QQQ-Client", "next-ui").header("Content-Type", "application/json")
         .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
      HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
      return (new UiApiResult(response.statusCode(), response.body()));
   }



   /*******************************************************************************
    ** Enumerate rather than maintaining an allowlist that could miss new objects.
    *******************************************************************************/
   private List<MetaDataWithPermissionRules> objects()
   {
      List<MetaDataWithPermissionRules> objects = new ArrayList<>();
      objects.addAll(qInstance.getTables().values());
      objects.addAll(qInstance.getProcesses().values());
      objects.addAll(qInstance.getReports().values());
      objects.addAll(qInstance.getWidgets().values());
      return (objects.stream().filter(object -> object.getName().startsWith("assistant")).toList());
   }
}
