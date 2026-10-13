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


import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.CompletableStreamHandle;
import com.kingsrook.qbits.assistant.provider.ModelCapabilities;
import com.kingsrook.qbits.assistant.provider.ModelEventListener;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelUsage;
import com.kingsrook.qbits.assistant.provider.StreamHandle;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The Anthropic (Claude) model provider, on com.anthropic:anthropic-java
 ** streaming (createStreaming + MessageAccumulator).
 **
 ** The API key comes ONLY from the environment (fromEnvironment(), variable
 ** ANTHROPIC_API_KEY) or from a value the host reads from its own secret
 ** store and passes to builder().apiKey(...) - which also accepts the
 ** reference form "${env.NAME}". The key is held only inside the SDK client:
 ** never logged, never in toString, never in exceptions, never in tables.
 *******************************************************************************/
public class AnthropicModelProvider implements AssistantModelProvider
{
   private static final QLogger LOG = QLogger.getLogger(AnthropicModelProvider.class);

   public static final String  NAME           = "anthropic";
   public static final String  API_KEY_ENV    = "ANTHROPIC_API_KEY";
   private static final Pattern ENV_REFERENCE = Pattern.compile("^\\$\\{env\\.([A-Za-z_][A-Za-z0-9_]*)}$");

   private final AnthropicClient client;
   private final String          baseUrl;
   private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();



   /*******************************************************************************
    **
    *******************************************************************************/
   private AnthropicModelProvider(AnthropicClient client, String baseUrl)
   {
      this.client = client;
      this.baseUrl = baseUrl;
   }



   /*******************************************************************************
    ** A provider with the key from ANTHROPIC_API_KEY.
    *******************************************************************************/
   public static AnthropicModelProvider fromEnvironment()
   {
      return (builder().apiKey("${env." + API_KEY_ENV + "}").build());
   }



   /*******************************************************************************
    ** A builder.
    *******************************************************************************/
   public static Builder builder()
   {
      return (new Builder());
   }



   /*******************************************************************************
    ** Builds the provider. Unless a client is supplied, apiKey is required
    ** (a literal from a secret store, or "${env.NAME}").
    *******************************************************************************/
   public static class Builder
   {
      private String          apiKey;
      private AnthropicClient client;
      private String          baseUrl;
      private Duration        timeout    = Duration.ofMinutes(10);
      private Integer         maxRetries = 2;



      /*******************************************************************************
       ** The API key, or "${env.NAME}" to read it from the environment.
       *******************************************************************************/
      public Builder apiKey(String apiKey)
      {
         this.apiKey = apiKey;
         return (this);
      }



      /*******************************************************************************
       ** Use an already configured client, including a cloud backend. The provider
       ** owns and closes this client; key, URL, timeout and retry builder options
       ** apply only when the builder creates its own client.
       *******************************************************************************/
      public Builder client(AnthropicClient client)
      {
         this.client = client;
         return (this);
      }



      /*******************************************************************************
       ** Another API base URL (a gateway, or a test server).
       *******************************************************************************/
      public Builder baseUrl(String baseUrl)
      {
         this.baseUrl = baseUrl;
         return (this);
      }



      /*******************************************************************************
       ** Request timeout (default 10 minutes; the turn engine has its own).
       *******************************************************************************/
      public Builder timeout(Duration timeout)
      {
         this.timeout = timeout;
         return (this);
      }



      /*******************************************************************************
       ** SDK retries of connection errors, 408 / 409 / 429 / 5xx (default 2).
       *******************************************************************************/
      public Builder maxRetries(Integer maxRetries)
      {
         this.maxRetries = maxRetries;
         return (this);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      public AnthropicModelProvider build()
      {
         if(client != null)
         {
            return (new AnthropicModelProvider(client, baseUrl));
         }
         String key = resolveSecret(apiKey);
         if(!StringUtils.hasContent(key))
         {
            throw (new IllegalStateException("No Anthropic API key: set " + API_KEY_ENV + " or pass a key from the application's secret store."));
         }

         AnthropicOkHttpClient.Builder clientBuilder = AnthropicOkHttpClient.builder().apiKey(key);
         if(StringUtils.hasContent(baseUrl))
         {
            clientBuilder.baseUrl(baseUrl);
         }
         if(timeout != null)
         {
            clientBuilder.timeout(timeout);
         }
         if(maxRetries != null)
         {
            clientBuilder.maxRetries(maxRetries);
         }
         return (new AnthropicModelProvider(clientBuilder.build(), StringUtils.hasContent(baseUrl) ? baseUrl : "https://api.anthropic.com"));
      }



      /*******************************************************************************
       ** A key literal, or the value of "${env.NAME}".
       *******************************************************************************/
      static String resolveSecret(String value)
      {
         if(value == null)
         {
            return (null);
         }
         Matcher matcher = ENV_REFERENCE.matcher(value.trim());
         if(matcher.matches())
         {
            return (System.getenv(matcher.group(1)));
         }
         return (value.trim());
      }



      /*******************************************************************************
       ** No key in here.
       *******************************************************************************/
      @Override
      public String toString()
      {
         return ("AnthropicModelProvider.Builder{baseUrl=" + baseUrl + ", apiKey=" + (apiKey == null ? "unset" : "[set]") + "}");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getName()
   {
      return (NAME);
   }



   /*******************************************************************************
    ** Claude 5.5 models: tools, parallel tools, prompt caching, (adaptive)
    ** thinking.
    *******************************************************************************/
   @Override
   public ModelCapabilities getCapabilities(String modelId)
   {
      return (new ModelCapabilities()
         .withTools(true)
         .withParallelToolCalls(true)
         .withPromptCaching(true)
         .withThinking(true)
         .withMaxContextTokens(200_000));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public StreamHandle stream(ModelRequest request, ModelEventListener listener)
   {
      CompletableStreamHandle handle = new CompletableStreamHandle();
      MessageCreateParams     params;
      try
      {
         params = AnthropicMapping.toParams(request);
      }
      catch(RuntimeException e)
      {
         handle.fail(new ModelProviderException(ModelProviderException.Kind.INVALID_REQUEST, null, "Could not build the Anthropic request."));
         return (handle);
      }

      AtomicReference<ModelUsage> seen = new AtomicReference<>();
      handle.setPartialUsage(seen::get);

      executor.submit(() ->
      {
         try(StreamResponse<RawMessageStreamEvent> response = client.messages().createStreaming(params))
         {
            handle.setOnCancel(response::close);
            if(handle.isCancelled())
            {
               return;
            }

            MessageAccumulator accumulator = MessageAccumulator.create();
            response.stream().forEach(event ->
            {
               accumulator.accumulate(event);
               event.messageStart().ifPresent(start -> seen.set(AnthropicMapping.toUsage(start.message().usage())));
               event.messageDelta().ifPresent(delta ->
               {
                  ModelUsage current = seen.get() == null ? new ModelUsage() : seen.get();
                  seen.set(current.withOutputTokens(delta.usage().outputTokens()));
               });
               event.contentBlockDelta().flatMap(delta -> delta.delta().text()).ifPresent(text -> listener.onTextDelta(text.text()));
               event.contentBlockStart().flatMap(start -> start.contentBlock().toolUse()).ifPresent(use -> listener.onToolUseStart(use.id(), use.name()));
            });

            if(!handle.isCancelled())
            {
               handle.complete(AnthropicMapping.toResponse(accumulator.message()));
            }
         }
         catch(Throwable t)
         {
            if(!handle.isCancelled())
            {
               ModelProviderException exception = AnthropicMapping.toProviderException(t).withPartialUsage(seen.get());
               ///////////////////////////////////////////////////////////////////
               // a 4xx is a request or account problem an operator must see:    //
               // log the API's error type, its (sanitized) message and the      //
               // request id - never the prompt                                  //
               ///////////////////////////////////////////////////////////////////
               if(exception.getHttpStatus() != null && exception.getHttpStatus() >= 400 && exception.getHttpStatus() < 500)
               {
                  LOG.warn("Anthropic stream failed", logPair("kind", exception.getKind()), logPair("httpStatus", exception.getHttpStatus()), logPair("model", request.getModel()),
                     logPair("errorType", exception.getProviderErrorType()), logPair("errorMessage", exception.getProviderMessage()), logPair("requestId", exception.getRequestId()));
               }
               else
               {
                  LOG.info("Anthropic stream failed", logPair("kind", exception.getKind()), logPair("httpStatus", exception.getHttpStatus()), logPair("model", request.getModel()),
                     logPair("errorType", exception.getProviderErrorType()), logPair("requestId", exception.getRequestId()));
               }
               handle.fail(exception);
            }
         }
      });
      return (handle);
   }



   /*******************************************************************************
    ** Release the HTTP client and threads.
    *******************************************************************************/
   public void close()
   {
      executor.shutdownNow();
      client.close();
   }



   /*******************************************************************************
    ** No key in here.
    *******************************************************************************/
   @Override
   public String toString()
   {
      return ("AnthropicModelProvider{baseUrl=" + baseUrl + "}");
   }

}
