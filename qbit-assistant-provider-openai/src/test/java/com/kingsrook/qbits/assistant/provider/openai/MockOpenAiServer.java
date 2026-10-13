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

package com.kingsrook.qbits.assistant.provider.openai;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import javax.net.ssl.SSLContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;


/*******************************************************************************
 ** Loopback-only scripted Chat Completions server with captured requests.
 *******************************************************************************/
class MockOpenAiServer implements AutoCloseable
{
   final List<Captured> requests = new CopyOnWriteArrayList<>();
   private final LinkedBlockingQueue<Script> scripts = new LinkedBlockingQueue<>();
   private final HttpServer server;
   private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();



   /*******************************************************************************
    ** Captured wire data, only synthetic keys in default tests.
    *******************************************************************************/
   record Captured(String path, String authorization, String apiKey, String body)
   {
   }



   /*******************************************************************************
    ** Delay can be before headers or after an SSE keep-alive.
    *******************************************************************************/
   record Script(int status, String body, long delayMs, boolean afterHeaders, CountDownLatch started)
   {
   }



   /*******************************************************************************
    ** Bind only the loopback interface.
    *******************************************************************************/
   MockOpenAiServer() throws IOException
   {
      this(null);
   }



   /*******************************************************************************
    ** Optional TLS uses exactly the same scripted endpoints and request capture.
    *******************************************************************************/
   MockOpenAiServer(SSLContext tls) throws IOException
   {
      if(tls == null)
      {
         server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      }
      else
      {
         HttpsServer https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
         https.setHttpsConfigurator(new HttpsConfigurator(tls));
         server = https;
      }
      server.createContext("/", this::serve);
      server.setExecutor(executor);
      server.start();
   }



   /*******************************************************************************
    ** Base URL deliberately has the v1 prefix.
    *******************************************************************************/
   String url()
   {
      return ((server instanceof HttpsServer ? "https://localhost:" : "http://127.0.0.1:") + server.getAddress().getPort() + "/v1");
   }



   /*******************************************************************************
    ** Queue a reply.
    *******************************************************************************/
   void enqueue(int status, String body)
   {
      scripts.add(new Script(status, body, 0, false, new CountDownLatch(1)));
   }



   /*******************************************************************************
    ** Queue a stalled response.
    *******************************************************************************/
   CountDownLatch stall(boolean afterHeaders)
   {
      CountDownLatch started = new CountDownLatch(1);
      scripts.add(new Script(200, "data: [DONE]\n\n", 2500, afterHeaders, started));
      return (started);
   }



   /*******************************************************************************
    ** Capture and reply, tolerating client cancellation.
    *******************************************************************************/
   private void serve(HttpExchange exchange) throws IOException
   {
      try(exchange)
      {
         requests.add(new Captured(exchange.getRequestURI().toString(), exchange.getRequestHeaders().getFirst("Authorization"),
            exchange.getRequestHeaders().getFirst("api-key"), new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
         Script script = scripts.poll();
         if(script == null)
         {
            exchange.sendResponseHeaders(500, -1);
            return;
         }
         if(!script.afterHeaders())
         {
            script.started().countDown();
            Thread.sleep(script.delayMs());
         }
         exchange.getResponseHeaders().set("Content-Type", script.body().startsWith("{") ? "application/json" : "text/event-stream");
         exchange.getResponseHeaders().set("x-request-id", "mock-request");
         exchange.sendResponseHeaders(script.status(), 0);
         if(script.afterHeaders())
         {
            exchange.getResponseBody().write(": keep-alive\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            script.started().countDown();
            Thread.sleep(script.delayMs());
         }
         exchange.getResponseBody().write(script.body().getBytes(StandardCharsets.UTF_8));
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
      catch(IOException e)
      {
         // Cancellation closes the client socket while a script is writing.
      }
   }



   /*******************************************************************************
    ** Stop all handlers.
    *******************************************************************************/
   @Override
   public void close()
   {
      server.stop(0);
      executor.shutdownNow();
   }
}
