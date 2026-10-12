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


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.EDITOR;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.NO_ACCESS;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.OTHER_EDITOR;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The routes over real HTTP (Javalin on a random port, MOCK authentication
 ** with the sessionId cookie naming the user): the contract's JSON shapes,
 ** the SSE stream (framing, seq, headers, end), replay with ?after=,
 ** cancel, feedback, and per-user isolation.
 *******************************************************************************/
class AssistantRouteProviderTest extends BaseAssistantTest
{
   private Javalin    javalin;
   private String     base;
   private HttpClient client = HttpClient.newHttpClient();



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void startServer()
   {
      QContext.clear();
      AssistantRouteProvider routes = new AssistantRouteProvider().withPingMillis(200);
      routes.setQInstance(qInstance);
      javalin = Javalin.create(routes::acceptJavalinConfig).start(0);
      base = "http://localhost:" + javalin.port() + AssistantRouteProvider.BASE_PATH;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void stopServer()
   {
      javalin.stop();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private HttpResponse<String> send(String method, String path, String user, String body) throws Exception
   {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", "sessionId=" + user).timeout(Duration.ofSeconds(30));
      if(body != null)
      {
         builder.header("Content-Type", "application/json");
      }
      builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
      return (client.send(builder.build(), HttpResponse.BodyHandlers.ofString()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Object> json(HttpResponse<String> response) throws Exception
   {
      return (JsonUtils.toObject(response.body(), new TypeReference<Map<String, Object>>() {}));
   }



   /*******************************************************************************
    ** One parsed SSE frame.
    *******************************************************************************/
   record Frame(String id, String event, Map<String, Object> data)
   {
   }



   /*******************************************************************************
    ** Read an SSE body into frames.
    *******************************************************************************/
   static List<Frame> frames(InputStream body) throws Exception
   {
      List<Frame> frames = new ArrayList<>();
      try(BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8)))
      {
         String id = null, event = null, data = null, line;
         while((line = reader.readLine()) != null)
         {
            if(line.isEmpty())
            {
               if(event != null)
               {
                  frames.add(new Frame(id, event, JsonUtils.toObject(data, new TypeReference<Map<String, Object>>() {})));
               }
               id = event = data = null;
            }
            else if(line.startsWith("id: "))
            {
               id = line.substring(4);
            }
            else if(line.startsWith("event: "))
            {
               event = line.substring(7);
            }
            else if(line.startsWith("data: "))
            {
               data = line.substring(6);
            }
         }
      }
      return (frames);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testConfigProbe() throws Exception
   {
      Map<String, Object> config = json(send("GET", "/config", EDITOR, null));
      assertThat(config).containsEntry("enabled", true).containsEntry("shortcut", "mod+j").containsEntry("model", "claude-sonnet-5-5");
      assertThat((Map<String, Object>) config.get("limits")).containsEntry("maxTurnSeconds", 300).containsEntry("maxToolCallsPerTurn", 25);
      assertThat((Map<String, Object>) config.get("features")).containsEntry("writes", false);

      assertThat(json(send("GET", "/config", NO_ACCESS, null))).containsEntry("enabled", false).containsEntry("reason", "NO_PERMISSION");
      assertThat(send("GET", "/conversations", NO_ACCESS, null).statusCode()).isEqualTo(403);

      setKillSwitch(true);
      assertThat(json(send("GET", "/config", EDITOR, null))).containsEntry("enabled", false).containsEntry("reason", "KILL_SWITCH");
      HttpResponse<String> blocked = send("POST", "/conversations/1/turns", EDITOR, "{\"text\":\"hi\"}");
      assertThat(blocked.statusCode()).isIn(404, 503);

      config.clear();
      this.config.setEnabled(false);
      assertThat(json(send("GET", "/config", EDITOR, null))).containsEntry("reason", "DISABLED");
      assertThat(send("GET", "/conversations", EDITOR, null).statusCode()).isEqualTo(503);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testConversationCrudAndIsolation() throws Exception
   {
      HttpResponse<String> created = send("POST", "/conversations", EDITOR, "{\"title\":\"Posts\",\"context\":{\"tableName\":\"blog_post\",\"junk\":1}}");
      assertThat(created.statusCode()).isEqualTo(201);
      Map<String, Object> conversation = json(created);
      Integer             id           = (Integer) conversation.get("id");
      assertThat(conversation).containsEntry("title", "Posts").containsEntry("status", "ACTIVE").containsEntry("pinned", false).containsEntry("unreadCount", 0);
      assertThat(conversation).containsKeys("lastMessageAt", "createDate");

      assertThat((List<?>) json(send("GET", "/conversations", EDITOR, null)).get("conversations")).hasSize(1);
      assertThat((List<?>) json(send("GET", "/conversations", OTHER_EDITOR, null)).get("conversations")).isEmpty();

      ///////////////////////////////////////////////////////////
      // another user's conversation id: 404, never the data   //
      ///////////////////////////////////////////////////////////
      HttpResponse<String> foreign = send("GET", "/conversations/" + id, OTHER_EDITOR, null);
      assertThat(foreign.statusCode()).isEqualTo(404);
      assertThat(foreign.body()).doesNotContain("Posts");
      assertThat(send("PATCH", "/conversations/" + id, OTHER_EDITOR, "{\"title\":\"x\"}").statusCode()).isEqualTo(404);
      assertThat(send("DELETE", "/conversations/" + id, OTHER_EDITOR, null).statusCode()).isEqualTo(404);
      assertThat(send("POST", "/conversations/" + id + "/turns", OTHER_EDITOR, "{\"text\":\"hi\"}").statusCode()).isEqualTo(404);

      Map<String, Object> patched = json(send("PATCH", "/conversations/" + id, EDITOR, "{\"title\":\"Renamed\",\"pinned\":true}"));
      assertThat(patched).containsEntry("title", "Renamed").containsEntry("pinned", true);
      assertThat(send("PATCH", "/conversations/" + id, EDITOR, "{\"status\":\"GONE\"}").statusCode()).isEqualTo(400);
      assertThat(json(send("PATCH", "/conversations/" + id, EDITOR, "{\"status\":\"ARCHIVED\"}"))).containsEntry("status", "ARCHIVED");
      assertThat((List<?>) json(send("GET", "/conversations", EDITOR, null)).get("conversations")).isEmpty();
      assertThat((List<?>) json(send("GET", "/conversations?status=ARCHIVED", EDITOR, null)).get("conversations")).hasSize(1);

      Map<String, Object> loaded = json(send("GET", "/conversations/" + id, EDITOR, null));
      assertThat((Map<String, Object>) loaded.get("conversation")).containsEntry("id", id);
      assertThat((List<?>) loaded.get("messages")).isEmpty();

      ////////////////////////////////////////
      // CSRF shape and bad input refused   //
      ////////////////////////////////////////
      HttpRequest form = HttpRequest.newBuilder(URI.create(base + "/conversations")).header("Cookie", "sessionId=" + EDITOR)
         .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("title=x")).build();
      assertThat(client.send(form, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
      assertThat(send("GET", "/conversations/abc", EDITOR, null).statusCode()).isEqualTo(400);
      assertThat(send("POST", "/conversations/" + id + "/turns", EDITOR, "{}").statusCode()).isEqualTo(400);

      assertThat(send("DELETE", "/conversations/" + id, EDITOR, null).statusCode()).isEqualTo(204);
      assertThat(send("GET", "/conversations/" + id, EDITOR, null).statusCode()).isEqualTo(404);
   }



   /*******************************************************************************
    ** The SSE stream end to end, then replay, feedback and the message list.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testTurnStreamReplayAndFeedback() throws Exception
   {
      provider.thenToolUse("count_records", input("table", "blog_post", "filter", filter("AND", "status", "EQUALS", List.of("PUBLISHED"))))
         .thenText("There are **4** published posts.");

      Integer id = (Integer) json(send("POST", "/conversations", EDITOR, "{}")).get("id");

      HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/conversations/" + id + "/turns")).header("Cookie", "sessionId=" + EDITOR)
         .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"how many published posts?\",\"pageContext\":{\"tableName\":\"blog_post\"}}")).build();
      HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
      assertThat(response.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-cache");
      assertThat(response.headers().firstValue("X-Accel-Buffering").orElse("")).isEqualTo("no");

      List<Frame> frames = frames(response.body());
      List<Frame> events = frames.stream().filter(f -> !"ping".equals(f.event())).toList();
      assertThat(events.get(0).event()).isEqualTo("message.start");
      assertThat(events.get(events.size() - 1).event()).isEqualTo("message.end");
      assertThat(events.get(events.size() - 1).data()).containsEntry("status", "COMPLETE");
      for(Frame frame : events)
      {
         assertThat(frame.data().get("seq")).isEqualTo(Integer.parseInt(frame.id()));
      }
      assertThat(events).extracting(Frame::event).contains("tool.start", "tool.result", "text.delta", "usage");

      Map<String, Object> start  = events.get(0).data();
      Integer             turnId = (Integer) start.get("turnId");
      Integer             msgId  = (Integer) start.get("messageId");
      assertThat(start).containsEntry("conversationId", id).containsKey("userMessageId");

      Map<String, Object> toolResult = events.stream().filter(f -> f.event().equals("tool.result")).findFirst().orElseThrow().data();
      assertThat(toolResult).containsEntry("name", "count_records").containsEntry("outcome", "OK").containsKey("summary");
      assertThat((Map<String, Object>) toolResult.get("render")).containsEntry("type", "count").containsEntry("count", 4);

      ///////////////////////////////////
      // replay after seq 2: the rest  //
      ///////////////////////////////////
      HttpRequest replay = HttpRequest.newBuilder(URI.create(base + "/turns/" + turnId + "/events?after=2")).header("Cookie", "sessionId=" + EDITOR).GET().build();
      List<Frame> replayed = frames(client.send(replay, HttpResponse.BodyHandlers.ofInputStream()).body());
      assertThat(replayed.get(0).id()).isEqualTo("3");
      assertThat(replayed).hasSize(events.size() - 2);
      assertThat(replayed.get(replayed.size() - 1).event()).isEqualTo("message.end");
      assertThat(send("GET", "/turns/" + turnId + "/events", OTHER_EDITOR, null).statusCode()).isEqualTo(404);

      ////////////////////////////////////////////
      // the conversation re-opens as it was   //
      ////////////////////////////////////////////
      Map<String, Object>       loaded   = json(send("GET", "/conversations/" + id, EDITOR, null));
      List<Map<String, Object>> messages = (List<Map<String, Object>>) loaded.get("messages");
      assertThat(messages).hasSize(2);
      assertThat(messages.get(0)).containsEntry("role", "USER").containsEntry("status", "COMPLETE").containsEntry("turnId", turnId);
      assertThat(messages.get(1)).containsEntry("role", "ASSISTANT").containsEntry("status", "COMPLETE").containsEntry("id", msgId);
      List<Map<String, Object>> parts = (List<Map<String, Object>>) messages.get(1).get("parts");
      assertThat(parts).extracting(p -> p.get("type")).containsExactly("tool", "text");
      assertThat(parts.get(1)).containsEntry("text", "There are **4** published posts.");
      assertThat((Map<String, Object>) loaded.get("conversation")).containsEntry("title", "how many published posts?").containsEntry("unreadCount", 0);

      ////////////////
      // feedback  //
      ////////////////
      assertThat(send("POST", "/messages/" + msgId + "/feedback", EDITOR, "{\"rating\":1,\"reason\":\"accurate\"}").statusCode()).isEqualTo(204);
      assertThat(send("POST", "/messages/" + msgId + "/feedback", EDITOR, "{\"rating\":5}").statusCode()).isEqualTo(400);
      assertThat(send("POST", "/messages/" + messages.get(0).get("id") + "/feedback", EDITOR, "{\"rating\":1}").statusCode()).isEqualTo(400);
      assertThat(send("POST", "/messages/" + msgId + "/feedback", OTHER_EDITOR, "{\"rating\":-1}").statusCode()).isEqualTo(404);

      assertThat(send("POST", "/turns/" + turnId + "/cancel", EDITOR, "{}").statusCode()).isEqualTo(409);
   }



   /*******************************************************************************
    ** Cancel a running turn over HTTP; the stream ends CANCELLED; a client
    ** that disconnects does not stop the turn (it is replayable).
    *******************************************************************************/
   @Test
   void testCancelAndPings() throws Exception
   {
      provider.withDelay(Duration.ofSeconds(20)).thenText("never");
      Integer id = (Integer) json(send("POST", "/conversations", EDITOR, "{}")).get("id");

      HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/conversations/" + id + "/turns")).header("Cookie", "sessionId=" + EDITOR)
         .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"slow\"}")).build();
      var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());

      Integer turnId = null;
      long    until  = System.currentTimeMillis() + 10_000;
      while(turnId == null && System.currentTimeMillis() < until)
      {
         Thread.sleep(50);
         QContext.init(qInstance, AssistantTestFixture.ownerSession(EDITOR, config));
         try
         {
            var running = new AssistantStore().listRunningTurns(EDITOR);
            turnId = running.isEmpty() ? null : running.get(0).getId();
         }
         finally
         {
            QContext.clear();
         }
      }
      assertThat(turnId).isNotNull();

      ///////////////////////////////////////////////////////////////
      // a second turn while one runs: refused (concurrency = 1)  //
      ///////////////////////////////////////////////////////////////
      assertThat(send("POST", "/conversations/" + id + "/turns", EDITOR, "{\"text\":\"again\"}").statusCode()).isEqualTo(429);

      Thread.sleep(500);
      assertThat(send("POST", "/turns/" + turnId + "/cancel", OTHER_EDITOR, "{}").statusCode()).isEqualTo(404);
      HttpResponse<String> cancel = send("POST", "/turns/" + turnId + "/cancel", EDITOR, "{}");
      assertThat(cancel.statusCode()).isEqualTo(202);
      assertThat(json(cancel)).containsEntry("status", "CANCELLING");

      List<Frame> frames = frames(future.get().body());
      assertThat(frames).extracting(Frame::event).contains("ping");
      Frame end = frames.get(frames.size() - 1);
      assertThat(end.event()).isEqualTo("message.end");
      assertThat(end.data()).containsEntry("status", "CANCELLED");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testDeriveTitle()
   {
      assertThat(AssistantApi.deriveTitle("  how many posts?\nmore lines")).isEqualTo("how many posts?");
      String title = AssistantApi.deriveTitle("find every published blog post that is missing a meta description and an excerpt please");
      assertThat(title).endsWith("\u2026").hasSizeLessThanOrEqualTo(AssistantApi.TITLE_MAX_CHARS);
      assertThat(AssistantApi.deriveTitle("x".repeat(100))).hasSize(AssistantApi.TITLE_MAX_CHARS);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testRegistration()
   {
      AssistantRoutes.register(qInstance);
      AssistantRoutes.register(qInstance);
      List<QCodeReference> references = QJavalinMetaData.of(qInstance).getAdditionalRouteProviderReferences();
      assertThat(references).filteredOn(r -> r.getName().equals(AssistantRouteProvider.class.getName())).hasSize(1);
   }



   /*******************************************************************************
    ** Insert / update the policy row (as an admin).
    *******************************************************************************/
   private void setKillSwitch(boolean on) throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      try
      {
         AssistantStore  store  = new AssistantStore();
         AssistantPolicy policy = store.loadPolicy();
         if(policy == null)
         {
            store.insert(new AssistantPolicy().withName("default").withKillSwitch(on).withKillSwitchReason("Maintenance."));
         }
         else
         {
            store.update(policy.withKillSwitch(on));
         }
         config.getRuntime().getPolicyService().invalidate();
      }
      finally
      {
         QContext.clear();
      }
   }

}
