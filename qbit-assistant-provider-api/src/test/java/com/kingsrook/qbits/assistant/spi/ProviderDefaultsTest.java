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

package com.kingsrook.qbits.assistant.spi;


import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** A minimal external provider compiles and uses SPI defaults without the runtime.
 *******************************************************************************/
class ProviderDefaultsTest
{
   /*******************************************************************************
    ** Legacy two-argument factories and static model lists remain supported.
    *******************************************************************************/
   @Test
   void minimalProviderDefaults() throws Exception
   {
      ExampleProvider provider = new ExampleProvider();
      ProviderSettings settings = new ProviderSettings().withApiKey("test-key").withBaseUrl("https://example.invalid");
      assertThat(provider.create(settings)).isSameAs(provider);
      assertThat(provider.settings.getApiKey()).isEqualTo("test-key");
      assertThat(provider.settings.getBaseUrl()).isEqualTo("https://example.invalid");
      assertThat(provider.getKinds()).containsExactly("EXAMPLE_PROVIDER");
      assertThat(provider.getDefaultBaseUrl("EXAMPLE_PROVIDER")).isNull();
      assertThat(provider.getDefaultApiKeyEnvName("EXAMPLE_PROVIDER")).isEqualTo("EXAMPLE_KEY");
      assertThat(provider.getDefaultLocation("EXAMPLE_PROVIDER")).isEqualTo("CLOUD");
      assertThat(provider.requiresBaseUrl("EXAMPLE_PROVIDER")).isTrue();
      assertThat(provider.validate(settings)).isEmpty();
      assertThat(provider.discoverModels(settings)).containsExactly(new DiscoveredModel("example", "example", null, null));
      assertThat(provider.checkHealth(settings).healthy()).isNull();
      assertThat(provider.checkHealth(settings).message()).isEqualTo("Health check not supported");
      assertThat(new HealthResult(false, 20L, "bad sk-abcdefghijk").message()).isEqualTo("bad [redacted]");
      assertThat(provider.countTokens(new ModelRequest())).isNull();
      assertThatThrownBy(() -> provider.embed(new EmbeddingRequest("example", List.of("text"))))
         .isInstanceOfSatisfying(ModelProviderException.class, error -> assertThat(error.getKind()).isEqualTo(ModelProviderException.Kind.INVALID_REQUEST));
      StringBuilder deltas = new StringBuilder();
      ModelEventListener listener = deltas::append;
      listener.onToolUseStart("call", "lookup");
      assertThat(deltas).isEmpty();
      listener.onTextDelta("visible answer");
      assertThat(deltas).hasToString("visible answer");
   }



   /*******************************************************************************
    ** Only the original required SPI methods are implemented.
    *******************************************************************************/
   private static class ExampleProvider implements AssistantProviderType, AssistantModelProvider
   {
      private ProviderSettings settings;



      /*******************************************************************************
       ** Minimal provider contract.
       *******************************************************************************/
      @Override
      public String getType()
      {
         return ("example-provider");
      }


      /*******************************************************************************
       ** Minimal provider contract.
       *******************************************************************************/
      @Override
      public String getLabel()
      {
         return ("Example");
      }


      /*******************************************************************************
       ** Minimal provider contract.
       *******************************************************************************/
      @Override
      public List<String> getModels()
      {
         return (List.of("example"));
      }


      /*******************************************************************************
       ** Minimal provider contract.
       *******************************************************************************/
      @Override
      public String getDefaultApiKeyEnvName()
      {
         return ("EXAMPLE_KEY");
      }


      /*******************************************************************************
       ** Minimal provider contract.
       *******************************************************************************/
      @Override
      public String getDefaultBaseUrl()
      {
         return (null);
      }


      /*******************************************************************************
       ** Minimal provider contract.
       *******************************************************************************/
      @Override
      public AssistantModelProvider create(String apiKey, String baseUrl)
      {
         settings = new ProviderSettings().withApiKey(apiKey).withBaseUrl(baseUrl);
         return (this);
      }


      /*******************************************************************************
       ** Minimal provider contract.
       *******************************************************************************/
      @Override
      public String getName()
      {
         return ("example");
      }


      /*******************************************************************************
       ** Minimal provider contract.
       *******************************************************************************/
      @Override
      public ModelCapabilities getCapabilities(String modelId)
      {
         return (new ModelCapabilities());
      }


      /*******************************************************************************
       ** Minimal provider contract.
       *******************************************************************************/
      @Override
      public StreamHandle stream(ModelRequest request, ModelEventListener listener)
      {
         throw (new UnsupportedOperationException());
      }
   }
}
