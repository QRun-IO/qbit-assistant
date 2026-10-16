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

package com.kingsrook.qbits.assistant.api;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.engine.ClientErrors;
import com.kingsrook.qbits.assistant.health.AssistantHealthChecks;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantClientError;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.processes.AssistantRetentionStep;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.UiApiTestServer;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.health.HealthChecksMetaData;
import com.kingsrook.qqq.backend.core.health.HealthStatus;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.EDITOR;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.OTHER_EDITOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** POST /assistant/client-errors: bounds and scrubbing, owned ids only, the
 ** per-user rate limit (202 accepted:false), owner security, the HTTP
 ** contract, the health check and retention.
 *******************************************************************************/
class ClientErrorsTest extends BaseAssistantTest
{
   /*******************************************************************************
    ** A small limit and warn threshold keep the tests short.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      config.withClientErrorsPerUserPerMinute(3).withClientErrorWarnPerHour(2);
   }



   /*******************************************************************************
    ** The API bound to a user's own session (in QContext).
    *******************************************************************************/
   private AssistantApi as(String userId)
   {
      QSession session = AssistantTestFixture.ownerSession(userId, config);
      QContext.init(qInstance, session);
      return (new AssistantApi(config.getRuntime(), session));
   }



   /*******************************************************************************
    ** The user's stored reports.
    *******************************************************************************/
   private List<AssistantClientError> rows(String userId) throws Exception
   {
      as(userId);
      return (new AssistantStore().query(AssistantClientError.TABLE_NAME, new QQueryFilter(), AssistantClientError.class));
   }



   /*******************************************************************************
    ** Every field is bounded and the free text scrubbed; ids are kept only when
    ** they are the user's own.
    *******************************************************************************/
   @Test
   void storesBoundedScrubbedReports() throws Exception
   {
      AssistantApi          api   = as(EDITOR);
      AssistantConversation mine  = config.getRuntime().getStore().createConversation(EDITOR, "mine", null);
      as(OTHER_EDITOR);
      AssistantConversation other = config.getRuntime().getStore().createConversation(OTHER_EDITOR, "other", null);
      api = as(EDITOR);

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("scope", "assistant-render" + "x".repeat(100));
      body.put("message", "Cannot read properties of undefined (reading 'length') for jane.doe@example.com card 4111 1111 1111 1111 " + "y".repeat(800));
      body.put("errorName", "TypeError");
      body.put("stack", "at render (https://app.example.com/_next/chunk.js?token=abc:1:2)\n" + "z".repeat(5000));
      body.put("componentStack", "in RecordsRender\n in AssistantPart key 9f8e7d6c5b4a3928170655e4d3c2b1a0AbCdEfQx");
      body.put("requestId", "req-123");
      body.put("route", "https://app.example.com/app/orders/12345678?filter=secret#top");
      body.put("conversationId", mine.getId());
      body.put("messageId", 999_999);
      body.put("turnId", "12");
      body.put("partType", "tool");
      body.put("renderType", "records");
      body.put("appVersion", "1.2.3");
      body.put("occurredAt", "2026-10-15T12:00:00Z");
      body.put("unknownField", "ignored");
      assertThat(api.reportClientError(body)).isEqualTo(Map.of("accepted", true));

      body.put("conversationId", other.getId());
      body.put("route", "javascript:alert(1)");
      body.put("occurredAt", "not a date");
      assertThat(as(EDITOR).reportClientError(body)).containsEntry("accepted", true);

      List<AssistantClientError> rows = rows(EDITOR);
      assertThat(rows).hasSize(2);
      AssistantClientError row = rows.get(0);
      assertThat(row.getUserId()).isEqualTo(EDITOR);
      assertThat(row.getScope()).hasSize(ClientErrors.SCOPE_MAX).startsWith("assistant-render");
      assertThat(row.getMessage()).hasSize(ClientErrors.MESSAGE_MAX).startsWith("Cannot read properties of undefined (reading 'length') for [email] card [")
         .doesNotContain("jane.doe").doesNotContain("4111");
      assertThat(ClientErrors.text("order 12345678 for 555 123 4567, version 1.2.3", 100)).isEqualTo("order [number] for [number], version 1.2.3");
      assertThat(row.getStack()).hasSize(ClientErrors.STACK_MAX).contains("chunk.js?[query]").doesNotContain("token=abc");
      assertThat(row.getComponentStack()).contains("[token]").doesNotContain("9f8e7d6c");
      assertThat(row.getRoute()).isEqualTo("/app/orders/[number]");
      assertThat(row.getConversationId()).isEqualTo(mine.getId());
      assertThat(row.getMessageId()).isNull();
      assertThat(row.getTurnId()).isNull();
      assertThat(row.getPartType()).isEqualTo("tool");
      assertThat(row.getRenderType()).isEqualTo("records");
      assertThat(row.getAppVersion()).isEqualTo("1.2.3");
      assertThat(row.getOccurredAt()).isEqualTo(Instant.parse("2026-10-15T12:00:00Z"));

      AssistantClientError second = rows.get(1);
      assertThat(second.getConversationId()).as("another user's conversation is not linked").isNull();
      assertThat(second.getRoute()).isNull();
      assertThat(second.getOccurredAt()).isNull();

      ///////////////////////////////////////////////////////////
      // owner-locked: another user never sees these reports   //
      ///////////////////////////////////////////////////////////
      assertThat(rows(OTHER_EDITOR)).isEmpty();
   }



   /*******************************************************************************
    ** scope and message are required; everything else is optional.
    *******************************************************************************/
   @Test
   void requiresScopeAndMessage()
   {
      AssistantApi api = as(EDITOR);
      assertThatThrownBy(() -> api.reportClientError(null)).isInstanceOf(AssistantApiException.class).extracting("status").isEqualTo(400);
      assertThatThrownBy(() -> api.reportClientError(Map.of("message", "x"))).extracting("status").isEqualTo(400);
      assertThatThrownBy(() -> api.reportClientError(Map.of("scope", "page", "message", "   "))).extracting("status").isEqualTo(400);
      assertThatThrownBy(() -> api.reportClientError(Map.of("scope", 7, "message", "x"))).extracting("status").isEqualTo(400);
   }



   /*******************************************************************************
    ** Over the per-user limit: still acknowledged (accepted:false), nothing
    ** stored; other users have their own limit.
    *******************************************************************************/
   @Test
   void rateLimitedPerUser() throws Exception
   {
      Map<String, Object> body = Map.of("scope", "page", "message", "boom");
      for(int i = 0; i < 3; i++)
      {
         assertThat(as(EDITOR).reportClientError(body)).containsEntry("accepted", true);
      }
      assertThat(as(EDITOR).reportClientError(body)).isEqualTo(Map.of("accepted", false));
      assertThat(as(OTHER_EDITOR).reportClientError(body)).containsEntry("accepted", true);
      assertThat(rows(EDITOR)).hasSize(3);
   }



   /*******************************************************************************
    ** The health check is UP below the threshold and DEGRADED at it.
    *******************************************************************************/
   @Test
   void healthCheckWarnsOnManyReports() throws Exception
   {
      assertThat(HealthChecksMetaData.ofOrWithNew(qInstance).getChecks()).anySatisfy(check -> assertThat(check.getName()).isEqualTo(AssistantHealthChecks.CLIENT_ERRORS));
      as(EDITOR);
      assertThat(AssistantHealthChecks.clientErrors(config.getRuntime()).getStatus()).isEqualTo(HealthStatus.UP);
      as(EDITOR).reportClientError(Map.of("scope", "page", "message", "one"));
      as(EDITOR).reportClientError(Map.of("scope", "page", "message", "two"));
      as(EDITOR);
      assertThat(AssistantHealthChecks.clientErrors(config.getRuntime()).getStatus()).isEqualTo(HealthStatus.DEGRADED);
      assertThat(AssistantHealthChecks.clientErrors(null).getStatus()).isEqualTo(HealthStatus.UNKNOWN);
   }



   /*******************************************************************************
    ** Retention deletes reports older than the tool result retention.
    *******************************************************************************/
   @Test
   void retentionDeletesOldReports() throws Exception
   {
      as(EDITOR).reportClientError(Map.of("scope", "page", "message", "old"));
      as(EDITOR);
      AssistantRetentionStep.Result kept = AssistantRetentionStep.purge(config, config.getRuntime().getStore(), Instant.now());
      assertThat(kept.clientErrorsDeleted()).isZero();
      AssistantRetentionStep.Result result = AssistantRetentionStep.purge(config, config.getRuntime().getStore(), Instant.now().plus(Duration.ofDays(config.getToolResultRetentionDays() + 1)));
      assertThat(result.clientErrorsDeleted()).isEqualTo(1);
      assertThat(rows(EDITOR)).isEmpty();
   }



   /*******************************************************************************
    ** Over HTTP: 202 {accepted}, a non-object body or a missing field is a 400;
    ** the table is in the Assistant nav. (The session comes from the standard
    ** UI API authentication; the fixture's MOCK module signs anyone in.)
    *******************************************************************************/
   @Test
   void httpContract() throws Exception
   {
      QContext.clear();
      String     base   = UiApiTestServer.serve(qInstance, config) + AssistantRouteProvider.BASE_PATH;
      HttpClient client = HttpClient.newHttpClient();

      HttpResponse<String> accepted = client.send(HttpRequest.newBuilder(URI.create(base + "/client-errors")).header("Cookie", "sessionId=" + EDITOR)
         .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"scope\":\"assistant-part\",\"message\":\"Cannot read properties of undefined\"}")).build(),
         HttpResponse.BodyHandlers.ofString());
      assertThat(accepted.statusCode()).isEqualTo(202);
      assertThat(JsonUtils.toObject(accepted.body(), new TypeReference<Map<String, Object>>() {})).isEqualTo(Map.of("accepted", true));

      HttpResponse<String> notObject = client.send(HttpRequest.newBuilder(URI.create(base + "/client-errors")).header("Cookie", "sessionId=" + EDITOR)
         .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("[1,2]")).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(notObject.statusCode()).isEqualTo(400);

      HttpResponse<String> missing = client.send(HttpRequest.newBuilder(URI.create(base + "/client-errors")).header("Cookie", "sessionId=" + EDITOR)
         .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"scope\":\"page\"}")).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(missing.statusCode()).isEqualTo(400);

      assertThat(rows(EDITOR)).hasSize(1);
      assertThat(AssistantMetaDataBuilder.NAV_TABLES).contains(AssistantClientError.TABLE_NAME);
   }
}
