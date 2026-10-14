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
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.UiApiTestServer;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.EDITOR;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.NO_ACCESS;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.OTHER_EDITOR;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The core preferences HTTP contract, user isolation, policy and channel wall.
 *******************************************************************************/
class AssistantPreferencesTest extends BaseAssistantTest
{
   private String base;
   private final HttpClient client = HttpClient.newHttpClient();



   /*******************************************************************************
    ** Serve the same core specs as a host application.
    *******************************************************************************/
   @BeforeEach
   void start()
   {
      QContext.clear();
      base = UiApiTestServer.serve(qInstance, config) + "/qqq/v1";
   }



   /*******************************************************************************
    ** Authenticate as a human user; no identity is accepted in the body.
    *******************************************************************************/
   private HttpResponse<String> send(String method, String path, String user, String channel, String body) throws Exception
   {
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
         .header("Cookie", "sessionId=" + user).header("X-QQQ-Client", channel);
      if(body != null)
      {
         request.header("Content-Type", "application/json");
      }
      return (client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
         HttpResponse.BodyHandlers.ofString()));
   }



   /*******************************************************************************
    ** A regular assistant user sees the section, without admin table permissions.
    *******************************************************************************/
   @Test
   void visibilityAndRemovedEndpoint() throws Exception
   {
      HttpResponse<String> visible = send("GET", "/preferences/assistant", EDITOR, "next-ui", null);
      assertThat(visible.statusCode()).as(visible.body()).isEqualTo(200);
      assertThat(new JSONObject(visible.body()).getString("name")).isEqualTo("assistant");
      assertThat(send("GET", "/preferences", EDITOR, "next-ui", null).body()).contains("Start conversations in Write mode");
      assertThat(send("GET", "/preferences/assistant", NO_ACCESS, "next-ui", null).statusCode()).isEqualTo(404);
      assertThat(send("GET", "/preferences", NO_ACCESS, "next-ui", null).body()).doesNotContain("Start conversations in Write mode");
      assertThat(send("PATCH", "/preferences/assistant", NO_ACCESS, "next-ui", "{\"values\":{\"defaultWriteMode\":true}}").statusCode()).isEqualTo(404);
      assertThat(send("GET", "/assistant/preferences", EDITOR, "next-ui", null).statusCode()).isEqualTo(404);
      assertThat(send("PATCH", "/assistant/preferences", EDITOR, "next-ui", "{}").statusCode()).isEqualTo(404);
   }



   /*******************************************************************************
    ** Partial patches and new conversations use only the authenticated user's row.
    *******************************************************************************/
   @Test
   void roundTripIsolationAndDefault() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      config.getRuntime().getStore().insert(new AssistantDataAccess().withObjectType("TABLE").withObjectName("page").withMode("READ_WRITE"));
      config.getRuntime().getDataAccessService().invalidate();
      config.getRuntime().getStore().insert(new AssistantPolicy().withName("default").withAutoConfirmAllowed(true));
      config.getRuntime().getPolicyService().invalidate();
      QContext.clear();
      assertThat(send("PATCH", "/preferences/assistant", OTHER_EDITOR, "next-ui", "{\"values\":{\"defaultWriteMode\":false,\"autoConfirmLowRisk\":true}}").statusCode()).isEqualTo(200);
      HttpResponse<String> patched = send("PATCH", "/preferences/assistant", EDITOR, "next-ui", "{\"values\":{\"defaultWriteMode\":true}}");
      assertThat(patched.statusCode()).as(patched.body()).isEqualTo(200);
      JSONObject values = new JSONObject(send("GET", "/preferences/assistant", EDITOR, "next-ui", null).body()).getJSONObject("values");
      assertThat(values.getBoolean("defaultWriteMode")).isTrue();
      assertThat(values.getBoolean("autoConfirmLowRisk")).isFalse();
      JSONObject other = new JSONObject(send("GET", "/preferences/assistant", OTHER_EDITOR, "next-ui", null).body()).getJSONObject("values");
      assertThat(other.getBoolean("defaultWriteMode")).isFalse();
      assertThat(other.getBoolean("autoConfirmLowRisk")).isTrue();
      JSONObject conversation = new JSONObject(send("POST", "/assistant/conversations", EDITOR, "next-ui", "{}").body());
      assertThat(conversation.getBoolean("writeMode")).isTrue();
      JSONObject probe = new JSONObject(send("GET", "/assistant/config", EDITOR, "next-ui", null).body());
      assertThat(probe.getJSONObject("preferences").getBoolean("defaultWriteMode")).isTrue();
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      AssistantPolicy policy = config.getRuntime().getStore().loadPolicy();
      config.getRuntime().getStore().updateFields(AssistantPolicy.TABLE_NAME, new QRecord().withValue("id", policy.getId()).withValue("writeModeAllowed", false));
      config.getRuntime().getPolicyService().invalidate();
      QContext.clear();
      assertThat(new JSONObject(send("GET", "/assistant/config", EDITOR, "next-ui", null).body()).getJSONObject("preferences").getBoolean("defaultWriteMode")).isFalse();
      assertThat(new JSONObject(send("POST", "/assistant/conversations", EDITOR, "next-ui", "{}").body()).getBoolean("writeMode")).isFalse();
      assertThat(send("PATCH", "/preferences/assistant", EDITOR, "next-ui", "{\"values\":{\"defaultWriteMode\":true}}").statusCode()).isEqualTo(400);
   }



   /*******************************************************************************
    ** The UI sees a read-only field with a reason; the server rejects a bypass.
    *******************************************************************************/
   @Test
   void policyDisablesAutoConfirm() throws Exception
   {
      String body = send("GET", "/preferences/assistant", EDITOR, "next-ui", null).body();
      assertThat(body).contains("Auto-confirm is not allowed by the assistant policy.");
      HttpResponse<String> denied = send("PATCH", "/preferences/assistant", EDITOR, "next-ui", "{\"values\":{\"autoConfirmLowRisk\":true}}");
      assertThat(denied.statusCode()).as(denied.body()).isEqualTo(400);
      assertThat(denied.body()).contains("can't be changed");
      assertThat(new JSONObject(send("GET", "/preferences/assistant", EDITOR, "next-ui", null).body()).getJSONObject("values").getBoolean("autoConfirmLowRisk")).isFalse();
   }



   /*******************************************************************************
    ** Core rejects identity injection and unknown fields before any provider write.
    *******************************************************************************/
   @Test
   void invalidPatchDoesNotWrite() throws Exception
   {
      for(String body : new String[] { "{\"userId\":\"other\",\"values\":{\"defaultWriteMode\":true}}",
         "{\"values\":{\"userId\":\"other\"}}", "{\"values\":{\"unknown\":true}}", "{\"values\":{\"defaultWriteMode\":[]}}" })
      {
         assertThat(send("PATCH", "/preferences/assistant", EDITOR, "next-ui", body).statusCode()).isEqualTo(400);
      }
      assertThat(new JSONObject(send("GET", "/preferences/assistant", EDITOR, "next-ui", null).body()).getJSONObject("values").getBoolean("defaultWriteMode")).isFalse();
   }



   /*******************************************************************************
    ** Even an administrator acting through the assistant cannot read or patch it.
    *******************************************************************************/
   @Test
   void assistantChannelCannotUsePreferences() throws Exception
   {
      for(String user : new String[] { EDITOR, AssistantTestFixture.ADMIN })
      {
         for(String channel : new String[] { "assistant/turn-42", "assistant/scheduled/12" })
         {
            assertThat(send("GET", "/preferences/assistant", user, channel, null).statusCode()).isEqualTo(404);
            assertThat(send("PATCH", "/preferences/assistant", user, channel, "{\"values\":{\"defaultWriteMode\":true}}").statusCode()).isEqualTo(404);
         }
         assertThat(send("GET", "/preferences/assistant", user, "next-ui", null).statusCode()).isEqualTo(200);
      }
   }
}
