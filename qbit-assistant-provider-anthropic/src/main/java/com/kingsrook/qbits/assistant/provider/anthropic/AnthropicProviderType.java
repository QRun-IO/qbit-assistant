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


import java.util.List;
import com.kingsrook.qbits.assistant.spi.AssistantModelProvider;
import com.kingsrook.qbits.assistant.spi.AssistantProviderType;


/*******************************************************************************
 ** The Anthropic (Claude) provider type for the assistant_provider settings
 ** table. Register it with AssistantQBitConfig.withProviderType(new
 ** AnthropicProviderType()). The key comes from the row's key variable
 ** (default ANTHROPIC_API_KEY).
 *******************************************************************************/
public class AnthropicProviderType implements AssistantProviderType
{
   public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

   /*******************************************************************************
    ** The current Claude models, the default (Sonnet 5.5) first.
    *******************************************************************************/
   public static final List<String> MODELS = List.of(
      "claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-5-5", "claude-fable-5-1",
      "claude-opus-5", "claude-sonnet-5", "claude-opus-4-8", "claude-sonnet-4-6", "claude-haiku-4-5");



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getType()
   {
      return (AnthropicModelProvider.NAME);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getLabel()
   {
      return ("Anthropic (Claude)");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<String> getModels()
   {
      return (MODELS);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getDefaultApiKeyEnvName()
   {
      return (AnthropicModelProvider.API_KEY_ENV);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getDefaultBaseUrl()
   {
      return (DEFAULT_BASE_URL);
   }



   /*******************************************************************************
    ** A provider with the resolved key (passed as a literal, never logged).
    *******************************************************************************/
   @Override
   public AssistantModelProvider create(String apiKey, String baseUrl)
   {
      AnthropicModelProvider.Builder builder = AnthropicModelProvider.builder().apiKey(apiKey);
      if(baseUrl != null)
      {
         builder.baseUrl(baseUrl);
      }
      return (builder.build());
   }

}
