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

package com.kingsrook.qbits.assistant.testing;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;


/*******************************************************************************
 ** Loopback-only cloud protocol fixture shared through the QBit test jar.
 *******************************************************************************/
public class CloudProviderTestServer implements AutoCloseable
{
   public static final ObjectMapper JSON = new ObjectMapper();
   public final List<Captured> requests = new CopyOnWriteArrayList<>();
   private final LinkedBlockingQueue<Reply> replies = new LinkedBlockingQueue<>();
   private final HttpServer server;



   /*******************************************************************************
    ** Only synthetic credentials are captured by cloud tests.
    *******************************************************************************/
   public record Captured(String path, String authorization, String body)
   {
   }



   /*******************************************************************************
    ** Binary event-stream and SSE use the same scripted HTTP transport.
    *******************************************************************************/
   private record Reply(Integer status, String contentType, byte[] body)
   {
   }



   /*******************************************************************************
    ** Listen exclusively on loopback with an ephemeral port.
    *******************************************************************************/
   public CloudProviderTestServer() throws IOException
   {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::serve);
      server.start();
   }



   /*******************************************************************************
    ** The root URL; each backend appends its own API path.
    *******************************************************************************/
   public String url()
   {
      return ("http://127.0.0.1:" + server.getAddress().getPort());
   }



   /*******************************************************************************
    ** Queue a response without contacting a real cloud service.
    *******************************************************************************/
   public void enqueue(Integer status, String contentType, byte[] body)
   {
      replies.add(new Reply(status, contentType, body));
   }



   /*******************************************************************************
    ** Queue a successful SSE stream.
    *******************************************************************************/
   public void sse(String events)
   {
      enqueue(200, "text/event-stream", events.getBytes(StandardCharsets.UTF_8));
   }



   /*******************************************************************************
    ** Capture the request before returning the scripted response.
    *******************************************************************************/
   private void serve(HttpExchange exchange) throws IOException
   {
      try(exchange)
      {
         requests.add(new Captured(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
         Reply reply = replies.poll();
         if(reply == null)
         {
            exchange.sendResponseHeaders(500, -1);
            return;
         }
         exchange.getResponseHeaders().set("Content-Type", reply.contentType());
         if("application/vnd.amazon.eventstream".equals(reply.contentType()))
         {
            exchange.getResponseHeaders().set("x-amzn-bedrock-content-type", "application/json");
         }
         exchange.sendResponseHeaders(reply.status(), reply.body().length);
         exchange.getResponseBody().write(reply.body());
      }
   }



   /*******************************************************************************
    ** Anthropic event JSON, including fragmented tool arguments and usage.
    *******************************************************************************/
   public static List<String> anthropicEvents(String model, String text, Map<String, Object> toolInput) throws IOException
   {
      List<Map<String, Object>> events = new ArrayList<>();
      events.add(Map.of("type", "message_start", "message", Map.of("id", "msg_mock", "type", "message", "role", "assistant", "model", model,
         "content", List.of(), "usage", Map.of("input_tokens", 12, "output_tokens", 1))));
      events.add(Map.of("type", "content_block_start", "index", 0, "content_block", Map.of("type", "text", "text", "")));
      events.add(Map.of("type", "content_block_delta", "index", 0, "delta", Map.of("type", "text_delta", "text", text)));
      events.add(Map.of("type", "content_block_stop", "index", 0));
      if(toolInput != null)
      {
         events.add(Map.of("type", "content_block_start", "index", 1, "content_block", Map.of("type", "tool_use", "id", "count", "name", "count_records", "input", Map.of())));
         String arguments = JSON.writeValueAsString(toolInput);
         Integer middle = arguments.length() / 2;
         for(String fragment : List.of(arguments.substring(0, middle), arguments.substring(middle)))
         {
            events.add(Map.of("type", "content_block_delta", "index", 1, "delta", Map.of("type", "input_json_delta", "partial_json", fragment)));
         }
         events.add(Map.of("type", "content_block_stop", "index", 1));
      }
      events.add(Map.of("type", "message_delta", "delta", Map.of("stop_reason", toolInput == null ? "end_turn" : "tool_use"), "usage", Map.of("output_tokens", 7)));
      events.add(Map.of("type", "message_stop"));
      List<String> result = new ArrayList<>();
      for(Map<String, Object> event : events)
      {
         result.add(JSON.writeValueAsString(event));
      }
      return (result);
   }



   /*******************************************************************************
    ** Wrap Anthropic JSON events in standard SSE framing for Vertex.
    *******************************************************************************/
   public static String anthropicSse(String model, String text, Map<String, Object> toolInput) throws IOException
   {
      StringBuilder result = new StringBuilder();
      for(String event : anthropicEvents(model, text, toolInput))
      {
         result.append("event: ").append(JSON.readTree(event).path("type").asText()).append("\ndata: ").append(event).append("\n\n");
      }
      return (result.toString());
   }



   /*******************************************************************************
    ** Published-post filter shared by the cloud golden turns.
    *******************************************************************************/
   public static Map<String, Object> countInput()
   {
      return (Map.of("table", "blog_post", "filter", Map.of("booleanOperator", "AND", "criteria",
         List.of(Map.of("field", "status", "operator", "EQUALS", "values", List.of("PUBLISHED"))))));
   }



   /*******************************************************************************
    ** Stop the mock listener.
    *******************************************************************************/
   @Override
   public void close()
   {
      server.stop(0);
   }
}
