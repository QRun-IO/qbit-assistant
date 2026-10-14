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

package com.kingsrook.qbits.assistant.provider.fake;


import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import com.kingsrook.qbits.assistant.spi.AssistantModelProvider;
import com.kingsrook.qbits.assistant.spi.CompletableStreamHandle;
import com.kingsrook.qbits.assistant.spi.ModelCapabilities;
import com.kingsrook.qbits.assistant.spi.ModelEventListener;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.spi.ModelResponse;
import com.kingsrook.qbits.assistant.spi.ModelUsage;
import com.kingsrook.qbits.assistant.spi.StreamHandle;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** A deterministic, recording model provider for tests, evals and demos.
 **
 ** Each model call consumes the next scripted step. A step sees the request
 ** (so it can read earlier tool results) and returns the response: text,
 ** tool calls, an error, or anything else. Text is streamed to the listener
 ** in small chunks. Every request is recorded, both as the object and as
 ** its JSON (what a real provider would send), so tests can assert that a
 ** value never left the server.
 **
 ** When the script runs out, the provider answers "(script exhausted)".
 *******************************************************************************/
public class ScriptedModelProvider implements AssistantModelProvider
{
   public static final String DEFAULT_NAME = "scripted";

   private final String                                   name;
   private final List<Function<ModelRequest, ModelResponse>> steps = Collections.synchronizedList(new ArrayList<>());
   private final List<ModelRequest>                        requests = Collections.synchronizedList(new ArrayList<>());
   private final List<String>                              requestJson = Collections.synchronizedList(new ArrayList<>());
   private final AtomicInteger                             nextStep = new AtomicInteger(0);

   private Duration          delay        = Duration.ZERO;
   private ModelUsage        usagePerCall = new ModelUsage().withInputTokens(1200L).withOutputTokens(150L).withCacheReadTokens(3000L).withCacheWriteTokens(0L);
   private ModelCapabilities capabilities = new ModelCapabilities().withPromptCaching(true).withThinking(false);



   /*******************************************************************************
    **
    *******************************************************************************/
   public ScriptedModelProvider()
   {
      this(DEFAULT_NAME);
   }



   /*******************************************************************************
    ** A scripted provider registered under another name (e.g. "anthropic").
    *******************************************************************************/
   public ScriptedModelProvider(String name)
   {
      this.name = name;
   }



   /*******************************************************************************
    ** Next call: answer with text and end the turn.
    *******************************************************************************/
   public ScriptedModelProvider thenText(String text)
   {
      return (thenRespond(request -> new ModelResponse().withParts(new ArrayList<>(List.of(ModelPart.text(text)))).withStopReason(ModelResponse.STOP_END_TURN)));
   }



   /*******************************************************************************
    ** Next call: ask for one tool call (optionally with a text preamble).
    *******************************************************************************/
   public ScriptedModelProvider thenToolUse(String toolName, Map<String, Object> input)
   {
      List<Object[]> calls = new ArrayList<>();
      calls.add(new Object[] { toolName, input });
      return (thenToolUses(null, calls));
   }



   /*******************************************************************************
    ** Next call: ask for several tool calls at once (parallel tool use). Each
    ** entry is {toolName, inputMap}.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public ScriptedModelProvider thenToolUses(String preamble, List<Object[]> calls)
   {
      return (thenRespond(request ->
      {
         int             step   = requests.size() - 1;
         List<ModelPart> parts  = new ArrayList<>();
         if(preamble != null)
         {
            parts.add(ModelPart.text(preamble));
         }
         int i = 0;
         for(Object[] call : calls)
         {
            parts.add(ModelPart.toolUse("toolu_" + step + "_" + (i++), (String) call[0], (Map<String, Object>) call[1]));
         }
         return (new ModelResponse().withParts(parts).withStopReason(ModelResponse.STOP_TOOL_USE));
      }));
   }



   /*******************************************************************************
    ** Next call: fail with a provider error.
    *******************************************************************************/
   public ScriptedModelProvider thenError(ModelProviderException.Kind kind, String message)
   {
      return (thenRespond(request ->
      {
         throw (new ScriptedFailure(new ModelProviderException(kind, null, message).withPartialUsage(new ModelUsage().add(usagePerCall))));
      }));
   }



   /*******************************************************************************
    ** Next call: any computed response.
    *******************************************************************************/
   public ScriptedModelProvider thenRespond(Function<ModelRequest, ModelResponse> step)
   {
      steps.add(step);
      return (this);
   }



   /*******************************************************************************
    ** Simulated latency of every call (cancel / kill-switch tests).
    *******************************************************************************/
   public ScriptedModelProvider withDelay(Duration delay)
   {
      this.delay = delay;
      return (this);
   }



   /*******************************************************************************
    ** Usage reported by every call.
    *******************************************************************************/
   public ScriptedModelProvider withUsagePerCall(ModelUsage usagePerCall)
   {
      this.usagePerCall = usagePerCall;
      return (this);
   }



   /*******************************************************************************
    ** Capabilities reported for every model.
    *******************************************************************************/
   public ScriptedModelProvider withCapabilities(ModelCapabilities capabilities)
   {
      this.capabilities = capabilities;
      return (this);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getName()
   {
      return (name);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public ModelCapabilities getCapabilities(String modelId)
   {
      return (capabilities);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public StreamHandle stream(ModelRequest request, ModelEventListener listener)
   {
      requests.add(request.copy());
      requestJson.add(JsonUtils.toJson(request));

      int                                  index  = nextStep.getAndIncrement();
      Function<ModelRequest, ModelResponse> step  = index < steps.size() ? steps.get(index) : null;
      CompletableStreamHandle              handle = new CompletableStreamHandle();

      Thread thread = Thread.ofVirtual().name("scripted-model-" + index).start(() ->
      {
         try
         {
            if(!delay.isZero())
            {
               Thread.sleep(delay.toMillis());
            }
            if(handle.isCancelled())
            {
               return;
            }

            ModelResponse response = step == null
               ? new ModelResponse().withParts(new ArrayList<>(List.of(ModelPart.text("(script exhausted)")))).withStopReason(ModelResponse.STOP_END_TURN)
               : step.apply(request);

            for(ModelPart part : response.getParts())
            {
               if(ModelPart.Type.TEXT.equals(part.getType()) && part.getText() != null)
               {
                  String text = part.getText();
                  for(int i = 0; i < text.length(); i += 12)
                  {
                     if(handle.isCancelled())
                     {
                        return;
                     }
                     listener.onTextDelta(text.substring(i, Math.min(text.length(), i + 12)));
                  }
               }
               else if(ModelPart.Type.TOOL_USE.equals(part.getType()))
               {
                  listener.onToolUseStart(part.getToolUseId(), part.getToolName());
               }
            }

            response.setUsage(new ModelUsage().add(usagePerCall));
            response.setRequestId("req_scripted_" + index);
            handle.complete(response);
         }
         catch(ScriptedFailure f)
         {
            handle.fail(f.exception);
         }
         catch(InterruptedException e)
         {
            Thread.currentThread().interrupt();
         }
         catch(RuntimeException e)
         {
            handle.fail(new ModelProviderException(ModelProviderException.Kind.OTHER, null, "scripted step failed: " + e.getClass().getSimpleName()));
         }
      });
      handle.setOnCancel(thread::interrupt);
      return (handle);
   }



   /*******************************************************************************
    ** The requests sent so far (copies).
    *******************************************************************************/
   public List<ModelRequest> getRequests()
   {
      return (new ArrayList<>(requests));
   }



   /*******************************************************************************
    ** The JSON of the requests sent so far.
    *******************************************************************************/
   public List<String> getRequestJson()
   {
      return (new ArrayList<>(requestJson));
   }



   /*******************************************************************************
    ** How many calls were made.
    *******************************************************************************/
   public int getCallCount()
   {
      return (requests.size());
   }



   /*******************************************************************************
    ** Carries a scripted provider error out of a step function.
    *******************************************************************************/
   private static class ScriptedFailure extends RuntimeException
   {
      private final ModelProviderException exception;



      /*******************************************************************************
       **
       *******************************************************************************/
      ScriptedFailure(ModelProviderException exception)
      {
         super(exception.getMessage());
         this.exception = exception;
      }
   }

}
