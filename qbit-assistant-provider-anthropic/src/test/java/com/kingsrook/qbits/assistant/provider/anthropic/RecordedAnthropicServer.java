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

package com.kingsrook.qbits.assistant.provider.anthropic;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;


/*******************************************************************************
 ** A local stand-in for the Anthropic API that replays recorded responses
 ** (SSE streams or JSON errors) for POST /v1/messages and records what it
 ** was sent. No network, no real key.
 *******************************************************************************/
public class RecordedAnthropicServer implements AutoCloseable
{
   private final HttpServer                         server;
   private final ConcurrentLinkedQueue<Reply>       replies  = new ConcurrentLinkedQueue<>();
   private final List<Map<String, String>>          requests = Collections.synchronizedList(new ArrayList<>());



   /*******************************************************************************
    ** One canned reply: status, content type, body, and an optional pause
    ** after the body before the stream is closed.
    *******************************************************************************/
   public record Reply(int status, String contentType, String body, long holdOpenMillis)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public RecordedAnthropicServer() throws IOException
   {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/v1/messages", this::handle);
      server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
      server.start();
   }



   /*******************************************************************************
    ** Queue a recorded SSE stream (from src/test/resources/fixtures).
    *******************************************************************************/
   public RecordedAnthropicServer thenStream(String fixture, long holdOpenMillis) throws IOException
   {
      replies.add(new Reply(200, "text/event-stream", fixture(fixture), holdOpenMillis));
      return (this);
   }



   /*******************************************************************************
    ** Queue a JSON error.
    *******************************************************************************/
   public RecordedAnthropicServer thenError(int status, String fixture) throws IOException
   {
      replies.add(new Reply(status, "application/json", fixture(fixture), 0));
      return (this);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void handle(HttpExchange exchange) throws IOException
   {
      String body;
      try(InputStream in = exchange.getRequestBody())
      {
         body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      requests.add(Map.of("body", body, "apiKey", String.valueOf(exchange.getRequestHeaders().getFirst("x-api-key")),
         "version", String.valueOf(exchange.getRequestHeaders().getFirst("anthropic-version"))));

      Reply reply = replies.poll();
      if(reply == null)
      {
         reply = new Reply(500, "application/json", "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"no recorded reply\"}}", 0);
      }

      byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", reply.contentType());
      exchange.getResponseHeaders().add("request-id", "req_recorded_" + requests.size());
      exchange.sendResponseHeaders(reply.status(), reply.holdOpenMillis() > 0 ? 0 : bytes.length);
      try(OutputStream out = exchange.getResponseBody())
      {
         out.write(bytes);
         out.flush();
         if(reply.holdOpenMillis() > 0)
         {
            try
            {
               Thread.sleep(reply.holdOpenMillis());
            }
            catch(InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
         }
      }
      catch(IOException e)
      {
         ///////////////////////////////////
         // client went away (cancelled) //
         ///////////////////////////////////
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static String fixture(String name) throws IOException
   {
      try(InputStream in = RecordedAnthropicServer.class.getResourceAsStream("/fixtures/" + name))
      {
         if(in == null)
         {
            throw (new IOException("missing fixture " + name));
         }
         return (new String(in.readAllBytes(), StandardCharsets.UTF_8));
      }
   }



   /*******************************************************************************
    ** The base URL to point the SDK at.
    *******************************************************************************/
   public String baseUrl()
   {
      return ("http://127.0.0.1:" + server.getAddress().getPort());
   }



   /*******************************************************************************
    ** The requests received (body, apiKey, version).
    *******************************************************************************/
   public List<Map<String, String>> getRequests()
   {
      return (new ArrayList<>(requests));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void close()
   {
      server.stop(0);
   }

}
