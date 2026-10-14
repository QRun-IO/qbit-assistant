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

package com.kingsrook.qbits.assistant.provider.vertex;


import java.io.IOException;
import java.time.Duration;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.Timeout;
import com.anthropic.vertex.backends.VertexBackend;
import com.google.auth.oauth2.GoogleCredentials;
import com.kingsrook.qbits.assistant.provider.anthropic.AnthropicModelProvider;
import com.kingsrook.qbits.assistant.provider.openai.OpenAiCompatibleModelProvider;
import com.kingsrook.qbits.assistant.spi.AssistantModelProvider;
import com.kingsrook.qbits.assistant.spi.ModelCapabilities;
import com.kingsrook.qbits.assistant.spi.ModelEventListener;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.spi.ProviderSettings;
import com.kingsrook.qbits.assistant.spi.StreamHandle;


/*******************************************************************************
 ** Route Claude to the Anthropic backend and other models to Vertex OpenAI.
 ** One scoped ADC credential supplies both clients; access tokens stay in memory.
 *******************************************************************************/
public class VertexModelProvider implements AssistantModelProvider
{
   private final AnthropicModelProvider claude;
   private final OpenAiCompatibleModelProvider openai;



   /*******************************************************************************
    ** Load Application Default Credentials without exposing credential failures.
    *******************************************************************************/
   public VertexModelProvider(ProviderSettings settings)
   {
      this(settings, applicationCredentials());
   }



   /*******************************************************************************
    ** Credential injection for deterministic, offline contract tests.
    *******************************************************************************/
   VertexModelProvider(ProviderSettings settings, GoogleCredentials credentials)
   {
      GoogleCredentials scoped = credentials.createScoped("https://www.googleapis.com/auth/cloud-platform");
      var backend = VertexBackend.builder().googleCredentials(scoped).region(settings.getRegion()).project(settings.getCloudProject());
      if(settings.getBaseUrl() != null)
      {
         backend.baseUrl(settings.getBaseUrl());
      }
      claude = AnthropicModelProvider.builder().client(AnthropicOkHttpClient.builder().backend(backend.build())
         .timeout(Timeout.builder().connect(Duration.ofSeconds(settings.getConnectTimeoutSeconds()))
            .request(Duration.ofSeconds(settings.getRequestTimeoutSeconds())).build()).build()).build();
      String base = settings.getBaseUrl() == null ? "https://" + settings.getRegion() + "-aiplatform.googleapis.com/v1/projects/"
         + settings.getCloudProject() + "/locations/" + settings.getRegion() + "/endpoints/openapi" : settings.getBaseUrl();
      openai = new OpenAiCompatibleModelProvider(new ProviderSettings().withKind("VERTEX").withBaseUrl(base)
         .withConnectTimeoutSeconds(settings.getConnectTimeoutSeconds()).withRequestTimeoutSeconds(settings.getRequestTimeoutSeconds())
         .withTlsCaBundle(settings.getTlsCaBundle()).withSslContext(settings.getSslContext())
         .withBearerTokenSupplier(() -> accessToken(scoped)));
   }



   /*******************************************************************************
    ** Resolve ADC lazily when a provider is created, not during registration.
    *******************************************************************************/
   private static GoogleCredentials applicationCredentials()
   {
      try
      {
         return (GoogleCredentials.getApplicationDefault());
      }
      catch(IOException e)
      {
         throw (new IllegalStateException("Google Application Default Credentials are unavailable. Configure GOOGLE_APPLICATION_CREDENTIALS or workload identity."));
      }
   }



   /*******************************************************************************
    ** Refresh expired credentials on every request; never retain a token in settings.
    *******************************************************************************/
   private static String accessToken(GoogleCredentials credentials)
   {
      try
      {
         credentials.refreshIfExpired();
         return (credentials.getAccessToken().getTokenValue());
      }
      catch(IOException e)
      {
         throw (new IllegalStateException("Could not refresh Google Application Default Credentials."));
      }
   }



   /*******************************************************************************
    ** Provider identity.
    *******************************************************************************/
   @Override
   public String getName()
   {
      return ("vertex");
   }



   /*******************************************************************************
    ** Route using the registry's model id; caller requests are never mutated.
    *******************************************************************************/
   private AssistantModelProvider route(String model)
   {
      return (model != null && model.startsWith("claude-") ? claude : openai);
   }



   /*******************************************************************************
    ** Capabilities follow the selected backend.
    *******************************************************************************/
   @Override
   public ModelCapabilities getCapabilities(String modelId)
   {
      return (route(modelId).getCapabilities(modelId));
   }



   /*******************************************************************************
    ** Stream through the existing adapters, preserving their error and tool mapping.
    *******************************************************************************/
   @Override
   public StreamHandle stream(ModelRequest request, ModelEventListener listener)
   {
      return (route(request.getModel()).stream(request, listener));
   }



   /*******************************************************************************
    ** Release the Anthropic client and its executor.
    *******************************************************************************/
   public void close()
   {
      claude.close();
   }
}
