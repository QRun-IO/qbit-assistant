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


import java.util.List;
import com.kingsrook.qbits.assistant.spi.AssistantModelProvider;
import com.kingsrook.qbits.assistant.spi.AssistantProviderType;
import com.kingsrook.qbits.assistant.spi.DiscoveredModel;
import com.kingsrook.qbits.assistant.spi.HealthResult;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ProviderSettings;


/*******************************************************************************
 ** One installed adapter for the Chat Completions dialects.
 *******************************************************************************/
public class OpenAiCompatibleProviderType implements AssistantProviderType
{
   /*******************************************************************************
    ** Installed adapter id.
    *******************************************************************************/
   @Override
   public String getType()
   {
      return ("openai-compatible");
   }



   /*******************************************************************************
    ** Settings label.
    *******************************************************************************/
   @Override
   public String getLabel()
   {
      return ("OpenAI-compatible");
   }



   /*******************************************************************************
    ** Discover endpoint-specific models instead of guessing ids.
    *******************************************************************************/
   @Override
   public List<String> getModels()
   {
      return (List.of());
   }



   /*******************************************************************************
    ** Extensible kind list.
    *******************************************************************************/
   @Override
   public List<String> getKinds()
   {
      return (List.of("OPENAI", "AZURE_OPENAI", "OPENROUTER", "OLLAMA", "LM_STUDIO", "VLLM", "LLAMA_CPP", "OPENAI_COMPATIBLE"));
   }



   /*******************************************************************************
    ** The legacy factory uses the generic dialect.
    *******************************************************************************/
   @Override
   public String getDefaultApiKeyEnvName()
   {
      return (null);
   }



   /*******************************************************************************
    ** The generic dialect requires an explicit URL.
    *******************************************************************************/
   @Override
   public String getDefaultBaseUrl()
   {
      return (null);
   }



   /*******************************************************************************
    ** Built-in endpoints. Redirects are never followed.
    *******************************************************************************/
   @Override
   public String getDefaultBaseUrl(String kind)
   {
      return (switch(kind)
      {
         case "OPENAI" -> "https://api.openai.com/v1";
         case "OPENROUTER" -> "https://openrouter.ai/api/v1";
         case "OLLAMA" -> "http://localhost:11434/v1";
         case "LM_STUDIO" -> "http://localhost:1234/v1";
         case "VLLM" -> "http://localhost:8000/v1";
         case "LLAMA_CPP" -> "http://localhost:8080/v1";
         default -> null;
      });
   }



   /*******************************************************************************
    ** Default key references, never key values.
    *******************************************************************************/
   @Override
   public String getDefaultApiKeyEnvName(String kind)
   {
      return (switch(kind)
      {
         case "OPENAI" -> "OPENAI_API_KEY";
         case "AZURE_OPENAI" -> "AZURE_OPENAI_API_KEY";
         case "OPENROUTER" -> "OPENROUTER_API_KEY";
         default -> null;
      });
   }



   /*******************************************************************************
    ** Location is an admin-editable fact, with a kind-based default.
    *******************************************************************************/
   @Override
   public String getDefaultLocation(String kind)
   {
      return (List.of("OLLAMA", "LM_STUDIO", "VLLM", "LLAMA_CPP").contains(kind) ? "LOCAL" : "CLOUD");
   }



   /*******************************************************************************
    ** Backward-compatible factory.
    *******************************************************************************/
   @Override
   public AssistantModelProvider create(String apiKey, String baseUrl)
   {
      return (create(new ProviderSettings().withKind("OPENAI_COMPATIBLE").withApiKey(apiKey).withBaseUrl(baseUrl)));
   }



   /*******************************************************************************
    ** Full connection settings.
    *******************************************************************************/
   @Override
   public AssistantModelProvider create(ProviderSettings settings)
   {
      return (new OpenAiCompatibleModelProvider(settings));
   }



   /*******************************************************************************
    ** Only a models request, never a completion.
    *******************************************************************************/
   @Override
   public List<DiscoveredModel> discoverModels(ProviderSettings settings) throws ModelProviderException
   {
      return (new OpenAiHttp(settings).models(settings.getRequestTimeoutSeconds()));
   }



   /*******************************************************************************
    ** A short models probe with latency and count.
    *******************************************************************************/
   @Override
   public HealthResult checkHealth(ProviderSettings settings)
   {
      long start = System.nanoTime();
      try
      {
         List<DiscoveredModel> models = new OpenAiHttp(settings).models(Math.min(10, settings.getRequestTimeoutSeconds()));
         return (new HealthResult(true, (System.nanoTime() - start) / 1_000_000, models.size() + " models available"));
      }
      catch(ModelProviderException e)
      {
         return (new HealthResult(false, (System.nanoTime() - start) / 1_000_000,
            e.getKind() + (e.getHttpStatus() == null ? "" : " HTTP " + e.getHttpStatus()) + ": " + e.getMessage()));
      }
   }
}
