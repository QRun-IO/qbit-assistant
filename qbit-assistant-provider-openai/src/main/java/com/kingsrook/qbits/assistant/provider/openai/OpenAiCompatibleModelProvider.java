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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kingsrook.qbits.assistant.spi.AssistantModelProvider;
import com.kingsrook.qbits.assistant.spi.CompletableStreamHandle;
import com.kingsrook.qbits.assistant.spi.EmbeddingRequest;
import com.kingsrook.qbits.assistant.spi.EmbeddingResponse;
import com.kingsrook.qbits.assistant.spi.ModelCapabilities;
import com.kingsrook.qbits.assistant.spi.ModelEventListener;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.spi.ModelResponse;
import com.kingsrook.qbits.assistant.spi.ModelUsage;
import com.kingsrook.qbits.assistant.spi.ProviderSettings;
import com.kingsrook.qbits.assistant.spi.StreamHandle;


/*******************************************************************************
 ** JDK HTTP Chat Completions adapter. Only visible content is emitted or stored;
 ** reasoning deltas are ignored. Each call owns its cancellation and read guard.
 *******************************************************************************/
public class OpenAiCompatibleModelProvider implements AssistantModelProvider
{
   private final OpenAiHttp http;
   private final boolean local;



   /*******************************************************************************
    ** Build a client with resolved settings.
    *******************************************************************************/
   public OpenAiCompatibleModelProvider(ProviderSettings settings)
   {
      http = new OpenAiHttp(settings);
      local = "LOCAL".equals(new OpenAiCompatibleProviderType().getDefaultLocation(http.settings.getKind()));
   }



   /*******************************************************************************
    ** Adapter identity.
    *******************************************************************************/
   @Override
   public String getName()
   {
      return ("openai-compatible");
   }



   /*******************************************************************************
    ** One bounded batch, with the deadline covering headers and the entire body.
    ** Restore input order from the response indexes; reject malformed vectors.
    *******************************************************************************/
   @Override
   public EmbeddingResponse embed(EmbeddingRequest request) throws ModelProviderException
   {
      ObjectNode body = OpenAiHttp.JSON.createObjectNode();
      body.put("model", request.model());
      body.set("input", OpenAiHttp.JSON.valueToTree(request.input()));
      var pending = http.client.sendAsync(http.request(request.model(), http.settings.getRequestTimeoutSeconds(), "embeddings")
         .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
      try
      {
         HttpResponse<String> response = pending.get(http.settings.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
         if(response.statusCode() != 200)
         {
            throw (http.error(response.statusCode(), response.body(), OpenAiHttp.requestId(response), false));
         }
         JsonNode json = OpenAiHttp.JSON.readTree(response.body());
         if(json.has("error"))
         {
            throw (http.error(500, response.body(), OpenAiHttp.requestId(response), false));
         }
         float[][] vectors = new float[request.input().size()][];
         Integer dimensions = null;
         for(JsonNode item : json.path("data"))
         {
            Integer index = item.path("index").asInt(-1);
            JsonNode values = item.path("embedding");
            if(index < 0 || index >= vectors.length || vectors[index] != null || !values.isArray() || values.isEmpty()
               || (dimensions != null && dimensions != values.size()))
            {
               throw (new IOException("Invalid embedding response."));
            }
            dimensions = values.size();
            float[] vector = new float[dimensions];
            for(Integer i = 0; i < vector.length; i++)
            {
               if(!values.get(i).isNumber())
               {
                  throw (new IOException("Invalid embedding coordinate."));
               }
               vector[i] = (float) values.get(i).asDouble();
               if(!Float.isFinite(vector[i]))
               {
                  throw (new IOException("Non-finite embedding coordinate."));
               }
            }
            vectors[index] = vector;
         }
         if(java.util.Arrays.stream(vectors).anyMatch(java.util.Objects::isNull))
         {
            throw (new IOException("Missing embedding."));
         }
         return (new EmbeddingResponse(List.of(vectors), new ModelUsage().withInputTokens(json.path("usage").path("prompt_tokens").asLong()), OpenAiHttp.requestId(response)));
      }
      catch(java.util.concurrent.TimeoutException e)
      {
         throw (new ModelProviderException(ModelProviderException.Kind.TIMEOUT, null, "The provider did not answer in time."));
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         throw (new ModelProviderException(ModelProviderException.Kind.NETWORK, null, "The embedding request was interrupted."));
      }
      catch(java.util.concurrent.ExecutionException | IOException e)
      {
         throw (OpenAiHttp.transportError(e));
      }
      finally
      {
         pending.cancel(true);
      }
   }



   /*******************************************************************************
    ** Conservative defaults; registry rows can override model-specific facts.
    *******************************************************************************/
   @Override
   public ModelCapabilities getCapabilities(String modelId)
   {
      return (new ModelCapabilities().withTools(true).withParallelToolCalls(true).withPromptCaching(false).withThinking(false)
         .withMaxContextTokens(local ? 32_000 : 128_000).withStreaming(true).withVision(false).withReasoningEffort(false));
   }



   /*******************************************************************************
    ** Interrupting the worker aborts the HTTP call, including a blocked body read.
    *******************************************************************************/
   @Override
   public StreamHandle stream(ModelRequest request, ModelEventListener listener)
   {
      CompletableStreamHandle handle = new CompletableStreamHandle();
      AtomicReference<InputStream> input = new AtomicReference<>();
      Thread worker = Thread.ofVirtual().unstarted(() -> run(request, listener, handle, input));
      handle.setOnCancel(() ->
      {
         worker.interrupt();
         close(input.get());
      });
      worker.start();
      return (handle);
   }



   /*******************************************************************************
    ** Guard the entire request and idle reads, including after HTTP headers.
    *******************************************************************************/
   private void run(ModelRequest request, ModelEventListener listener, CompletableStreamHandle handle, AtomicReference<InputStream> input)
   {
      Thread worker = Thread.currentThread();
      AtomicLong lastRead = new AtomicLong(System.nanoTime());
      long started = lastRead.get();
      long timeout = TimeUnit.SECONDS.toNanos(http.settings.getRequestTimeoutSeconds());
      ScheduledExecutorService guard = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
      Accumulator result = new Accumulator(request.getModel(), listener);
      handle.setPartialUsage(() -> result.usage);
      guard.scheduleAtFixedRate(() ->
      {
         long now = System.nanoTime();
         if(now - lastRead.get() >= timeout || now - started >= timeout)
         {
            handle.fail(new ModelProviderException(ModelProviderException.Kind.TIMEOUT, null, "The provider did not answer in time.").withPartialUsage(result.usage));
            worker.interrupt();
            close(input.get());
         }
      }, 100, 100, TimeUnit.MILLISECONDS);
      try
      {
         ModelCapabilities caps = request.getCapabilities() == null ? getCapabilities(request.getModel()) : request.getCapabilities();
         ObjectNode body = OpenAiMapping.request(request, http.settings.getKind(), caps);
         boolean strict = false;
         for(JsonNode tool : body.path("tools"))
         {
            strict |= tool.path("function").path("strict").asBoolean();
         }
         HttpResponse<InputStream> response = http.client.send(http.request(request.getModel(), http.settings.getRequestTimeoutSeconds())
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofInputStream());
         input.set(response.body());
         result.requestId = OpenAiHttp.requestId(response);
         if(handle.isCancelled())
         {
            return;
         }
         if(response.statusCode() != 200)
         {
            throw (http.error(response.statusCode(), new String(response.body().readNBytes(64 * 1024), StandardCharsets.UTF_8), result.requestId, strict));
         }
         if(!Boolean.FALSE.equals(request.getStreaming()) && Boolean.TRUE.equals(caps.getStreaming()))
         {
            readEvents(response.body(), lastRead, result, strict, handle);
         }
         else
         {
            result.accept(OpenAiHttp.JSON.readTree(response.body()), false, http, strict);
         }
         handle.complete(result.response());
      }
      catch(ModelProviderException e)
      {
         handle.fail(e.withPartialUsage(result.usage));
      }
      catch(HttpTimeoutException e)
      {
         handle.fail(new ModelProviderException(ModelProviderException.Kind.TIMEOUT, null, "The provider did not answer in time.").withPartialUsage(result.usage));
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         if(!handle.isCancelled())
         {
            handle.fail(new ModelProviderException(ModelProviderException.Kind.NETWORK, null, "The provider request was interrupted."));
         }
      }
      catch(IOException | RuntimeException e)
      {
         ////////////////////////////////////////////////////////////////////////
         // a transport error at or past the deadline is the timeout itself    //
         // (the JDK client or the guard closed the call), whichever saw it    //
         // first; report it as TIMEOUT so the kind does not depend on a race. //
         ////////////////////////////////////////////////////////////////////////
         long now = System.nanoTime();
         long margin = TimeUnit.MILLISECONDS.toNanos(200);
         boolean late = now - started >= timeout - margin || now - lastRead.get() >= timeout - margin;
         handle.fail((late ? new ModelProviderException(ModelProviderException.Kind.TIMEOUT, null, "The provider did not answer in time.") : OpenAiHttp.transportError(e)).withPartialUsage(result.usage));
      }
      finally
      {
         guard.shutdownNow();
         close(input.get());
      }
   }



   /*******************************************************************************
    ** SSE data fields, CRLF, comments, multi-line data and an optional DONE marker.
    *******************************************************************************/
   private void readEvents(InputStream stream, AtomicLong lastRead, Accumulator result, boolean strict, CompletableStreamHandle handle) throws IOException, ModelProviderException
   {
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      StringBuilder data = new StringBuilder();
      String line;
      while(!handle.isCancelled() && (line = reader.readLine()) != null)
      {
         lastRead.set(System.nanoTime());
         if(line.isEmpty())
         {
            if(data.length() > 0)
            {
               if("[DONE]".equals(data.toString().trim()))
               {
                  return;
               }
               result.accept(OpenAiHttp.JSON.readTree(data.toString()), true, http, strict);
               data.setLength(0);
            }
         }
         else if(line.startsWith("data:"))
         {
            if(data.length() > 0)
            {
               data.append('\n');
            }
            data.append(line.substring(5).stripLeading());
         }
      }
      if(data.length() > 0 && !"[DONE]".equals(data.toString().trim()))
      {
         result.accept(OpenAiHttp.JSON.readTree(data.toString()), true, http, strict);
      }
   }



   /*******************************************************************************
    ** Closing an already cancelled stream is harmless.
    *******************************************************************************/
   private static void close(InputStream input)
   {
      if(input != null)
      {
         try
         {
            input.close();
         }
         catch(IOException e)
         {
            // The aborted HTTP call may already have closed the stream.
         }
      }
   }



   /*******************************************************************************
    ** Tool arguments arrive as JSON fragments, ordered by call index.
    *******************************************************************************/
   private static class Tool
   {
      private String id = "";
      private String name = "";
      private final StringBuilder arguments = new StringBuilder();
      private boolean announced;
   }



   /*******************************************************************************
    ** Shared mapping for stream chunks and one-shot responses.
    *******************************************************************************/
   private static class Accumulator
   {
      private final ModelEventListener listener;
      private final StringBuilder text = new StringBuilder();
      private final Map<Integer, Tool> tools = new TreeMap<>();
      private volatile ModelUsage usage = new ModelUsage();
      private String model;
      private String requestId;
      private String stop = ModelResponse.STOP_END_TURN;



      /*******************************************************************************
       ** One completion.
       *******************************************************************************/
      Accumulator(String model, ModelEventListener listener)
      {
         this.model = model;
         this.listener = listener;
      }



      /*******************************************************************************
       ** Ignore reasoning fields; retain only text, tools and billable usage.
       *******************************************************************************/
      void accept(JsonNode chunk, boolean streaming, OpenAiHttp http, boolean strict) throws ModelProviderException
      {
         if(chunk.has("error"))
         {
            JsonNode error = chunk.path("error");
            int status = error.path("status").asInt(error.path("code").asInt(500));
            String type = error.path("type").asText();
            if(status == 500)
            {
               status = switch(type)
               {
                  case "invalid_request_error" -> 400;
                  case "authentication_error" -> 401;
                  case "rate_limit_error" -> 429;
                  case "overloaded_error" -> 503;
                  default -> 500;
               };
            }
            throw (http.error(status, chunk.toString(), requestId, strict));
         }
         model = chunk.path("model").asText(model);
         if(chunk.hasNonNull("usage"))
         {
            JsonNode used = chunk.get("usage");
            long cached = used.path("prompt_tokens_details").path("cached_tokens").asLong();
            usage = new ModelUsage().withInputTokens(Math.max(0, used.path("prompt_tokens").asLong() - cached))
               .withOutputTokens(used.path("completion_tokens").asLong()).withCacheReadTokens(cached);
         }
         for(JsonNode choice : chunk.path("choices"))
         {
            if(choice.path("index").asInt() != 0)
            {
               continue;
            }
            JsonNode delta = choice.path(streaming ? "delta" : "message");
            String content = delta.path("content").asText("");
            if(!content.isEmpty())
            {
               text.append(content);
               listener.onTextDelta(content);
            }
            int position = 0;
            for(JsonNode call : delta.path("tool_calls"))
            {
               Tool tool = tools.computeIfAbsent(call.path("index").asInt(position++), k -> new Tool());
               if(tool.id.isEmpty())
               {
                  tool.id = call.path("id").asText("");
               }
               if(tool.name.isEmpty())
               {
                  tool.name = call.path("function").path("name").asText("");
               }
               tool.arguments.append(call.path("function").path("arguments").asText(""));
               if(!tool.announced && !tool.id.isEmpty() && !tool.name.isEmpty())
               {
                  listener.onToolUseStart(tool.id, tool.name);
                  tool.announced = true;
               }
            }
            if(choice.hasNonNull("finish_reason"))
            {
               stop = switch(choice.get("finish_reason").asText())
               {
                  case "tool_calls", "function_call" -> ModelResponse.STOP_TOOL_USE;
                  case "length" -> ModelResponse.STOP_MAX_TOKENS;
                  case "content_filter" -> ModelResponse.STOP_REFUSAL;
                  default -> ModelResponse.STOP_END_TURN;
               };
            }
         }
      }



      /*******************************************************************************
       ** Parse complete argument objects only, preserving parallel call order.
       *******************************************************************************/
      ModelResponse response()
      {
         List<ModelPart> parts = new ArrayList<>();
         if(!text.isEmpty())
         {
            parts.add(ModelPart.text(text.toString()));
         }
         for(Map.Entry<Integer, Tool> entry : tools.entrySet())
         {
            Tool tool = entry.getValue();
            if(tool.id.isEmpty())
            {
               tool.id = "call_" + entry.getKey();
            }
            if(!tool.announced)
            {
               listener.onToolUseStart(tool.id, tool.name);
               tool.announced = true;
            }
            Map<String, Object> arguments;
            try
            {
               arguments = tool.arguments.isEmpty() ? Map.of() : OpenAiHttp.JSON.readerFor(new TypeReference<Map<String, Object>>() {})
                  .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(tool.arguments.toString());
               if(arguments == null)
               {
                  throw (new IOException("Tool arguments must be an object."));
               }
            }
            catch(IOException e)
            {
               arguments = Map.of("_invalid_arguments", tool.arguments.substring(0, Math.min(200, tool.arguments.length())));
            }
            parts.add(ModelPart.toolUse(tool.id, tool.name, arguments));
         }
         if(!tools.isEmpty() && ModelResponse.STOP_END_TURN.equals(stop))
         {
            stop = ModelResponse.STOP_TOOL_USE;
         }
         return (new ModelResponse().withModel(model).withRequestId(requestId).withParts(parts).withUsage(usage).withStopReason(stop));
      }
   }
}
