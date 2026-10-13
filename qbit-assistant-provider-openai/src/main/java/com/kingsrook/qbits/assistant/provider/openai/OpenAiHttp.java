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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLHandshakeException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.assistant.provider.DiscoveredModel;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ProviderSettings;


/*******************************************************************************
 ** Shared HTTP/authentication/error mapping. ProviderService guards URLs before
 ** constructing this transport; redirects must not move keys to another host.
 *******************************************************************************/
final class OpenAiHttp
{
   static final ObjectMapper JSON = new ObjectMapper();
   final HttpClient client;
   final ProviderSettings settings;
   private final String base;



   /*******************************************************************************
    ** Copy settings so callers cannot mutate a cached client's credentials.
    *******************************************************************************/
   OpenAiHttp(ProviderSettings source)
   {
      settings = new ProviderSettings().withKind(source.getKind() == null ? "OPENAI_COMPATIBLE" : source.getKind())
         .withTlsCaBundle(source.getTlsCaBundle()).withSslContext(source.getSslContext())
         .withApiKey(source.getApiKey()).withBaseUrl(source.getBaseUrl()).withApiVersion(source.getApiVersion()).withRegion(source.getRegion())
         .withConnectTimeoutSeconds(source.getConnectTimeoutSeconds()).withRequestTimeoutSeconds(source.getRequestTimeoutSeconds());
      String url = settings.getBaseUrl() == null ? new OpenAiCompatibleProviderType().getDefaultBaseUrl(settings.getKind()) : settings.getBaseUrl();
      if(url == null)
      {
         throw (new IllegalArgumentException("A base URL is required for this provider kind."));
      }
      base = url.replaceAll("/+$", "");
      HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(settings.getConnectTimeoutSeconds())).followRedirects(HttpClient.Redirect.NEVER);
      if(settings.getSslContext() != null)
      {
         builder.sslContext(settings.getSslContext());
      }
      client = builder.build();
   }



   /*******************************************************************************
    ** Azure uses deployment paths and a version query parameter.
    *******************************************************************************/
   HttpRequest.Builder request(String model, Integer seconds) throws ModelProviderException
   {
      try
      {
         String path = model == null ? "/models" : "/chat/completions";
         if("AZURE_OPENAI".equals(settings.getKind()))
         {
            path = model == null ? "/openai/models" : "/openai/deployments/" + encode(model) + "/chat/completions";
            path += "?api-version=" + encode(settings.getApiVersion() == null || settings.getApiVersion().isBlank() ? "2024-10-21" : settings.getApiVersion());
         }
         HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(seconds)).header("Content-Type", "application/json");
         if(settings.getApiKey() != null && !settings.getApiKey().isBlank())
         {
            if("AZURE_OPENAI".equals(settings.getKind()))
            {
               builder.header("api-key", settings.getApiKey());
            }
            else
            {
               builder.header("Authorization", "Bearer " + settings.getApiKey());
            }
         }
         return (builder);
      }
      catch(IllegalArgumentException e)
      {
         throw (new ModelProviderException(ModelProviderException.Kind.INVALID_REQUEST, null, "The provider connection settings are invalid."));
      }
   }



   /*******************************************************************************
    ** Models with optional server-specific context information.
    *******************************************************************************/
   List<DiscoveredModel> models(Integer seconds) throws ModelProviderException
   {
      CompletableFuture<HttpResponse<String>> pending = client.sendAsync(request(null, seconds).GET().build(), HttpResponse.BodyHandlers.ofString());
      try
      {
         HttpResponse<String> response = pending.get(seconds, TimeUnit.SECONDS);
         if(response.statusCode() != 200)
         {
            throw (error(response.statusCode(), response.body(), requestId(response), false));
         }
         JsonNode body = JSON.readTree(response.body());
         if(body.has("error"))
         {
            throw (error(500, response.body(), requestId(response), false));
         }
         List<DiscoveredModel> result = new ArrayList<>();
         for(JsonNode model : body.path("data"))
         {
            String id = model.path("id").asText();
            if(id.isBlank())
            {
               continue;
            }
            int context = model.path("context_length").asInt(model.path("max_model_len").asInt(model.path("meta").path("n_ctx_train").asInt()));
            result.add(new DiscoveredModel(id, model.path("name").asText(id), context > 0 ? context : null, model.path("owned_by").asText(null)));
         }
         return (result);
      }
      catch(TimeoutException e)
      {
         throw (new ModelProviderException(ModelProviderException.Kind.TIMEOUT, null, "The provider did not answer in time."));
      }
      catch(ExecutionException e)
      {
         throw (transportError(e));
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         throw (new ModelProviderException(ModelProviderException.Kind.NETWORK, null, "The provider request was interrupted."));
      }
      catch(IOException e)
      {
         throw (transportError(e));
      }
      finally
      {
         pending.cancel(true);
         client.close();
      }
   }



   /*******************************************************************************
    ** Sanitize errors, explicitly removing even short or unusual API keys.
    *******************************************************************************/
   ModelProviderException error(int status, String body, String requestId, boolean strict)
   {
      String message = "The provider refused the request.";
      String type = "openai-compatible";
      try
      {
         JsonNode error = JSON.readTree(body).path("error");
         message = error.path("message").asText(message);
         type = error.path("type").asText(type);
      }
      catch(Exception e)
      {
         // Do not expose unstructured proxy pages or request bodies.
      }
      message = safe(message);
      type = safe(type);
      ModelProviderException.Kind kind = errorKind(status);
      String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
      return (new ModelProviderException(kind, status, message).withProviderDetails(type, message, safe(requestId))
         .withStrictSchemaRejected(strict && status == 400 && (lower.contains("strict") || lower.contains("schema") || lower.contains("grammar"))));
   }



   /*******************************************************************************
    ** Credential-free diagnostics.
    *******************************************************************************/
   private String safe(String value)
   {
      String key = settings.getApiKey();
      return (ModelProviderException.sanitizeProviderMessage(value == null || key == null || key.isEmpty() ? value : value.replace(key, "[redacted]"), 300));
   }



   /*******************************************************************************
    ** Common request-id headers.
    *******************************************************************************/
   static String requestId(HttpResponse<?> response)
   {
      return (response.headers().firstValue("x-request-id").orElse(response.headers().firstValue("apim-request-id").orElse(null)));
   }



   /*******************************************************************************
    ** Encode a single path segment or query value.
    *******************************************************************************/
   private static String encode(String value)
   {
      return (URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
   }



   /*******************************************************************************
    ** Map the provider error category.
    *******************************************************************************/
   private static ModelProviderException.Kind errorKind(int status)
   {
      return (switch(status)
      {
         case 401, 403 -> ModelProviderException.Kind.AUTHENTICATION;
         case 429 -> ModelProviderException.Kind.RATE_LIMITED;
         case 503, 529 -> ModelProviderException.Kind.OVERLOADED;
         case 400, 404, 422 -> ModelProviderException.Kind.INVALID_REQUEST;
         default -> status >= 500 ? ModelProviderException.Kind.SERVER : ModelProviderException.Kind.INVALID_REQUEST;
      });
   }


   /*******************************************************************************
    ** Fixed diagnostics omit remote certificate details, URLs and credentials.
    *******************************************************************************/
   static ModelProviderException transportError(Throwable error)
   {
      for(Throwable cause = error; cause != null; cause = cause.getCause())
      {
         if(cause instanceof SSLHandshakeException)
         {
            return (new ModelProviderException(ModelProviderException.Kind.NETWORK, null,
               "The provider TLS certificate is not trusted or does not match the host. Check tlsCaBundle and the HTTPS hostname."));
         }
         if(cause instanceof HttpTimeoutException)
         {
            return (new ModelProviderException(ModelProviderException.Kind.TIMEOUT, null, "The provider did not answer in time."));
         }
      }
      return (new ModelProviderException(ModelProviderException.Kind.NETWORK, null, "Could not read the provider response."));
   }
}
