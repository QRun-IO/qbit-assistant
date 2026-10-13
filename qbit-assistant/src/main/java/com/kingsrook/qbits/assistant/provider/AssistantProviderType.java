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

package com.kingsrook.qbits.assistant.provider;


import java.util.List;


/*******************************************************************************
 ** A kind of model provider an admin can configure in the assistant_provider
 ** table (Anthropic now; OpenAI-compatible, Bedrock and others plug in the
 ** same way). Hosts register types with AssistantQBitConfig.withProviderType;
 ** the provider settings screen offers the registered types and their models.
 **
 ** create() receives the API key already resolved from its reference; it must
 ** never log the key or put it in toString or exception messages.
 *******************************************************************************/
public interface AssistantProviderType
{

   /*******************************************************************************
    ** The type's id, stored in assistant_provider.providerType (e.g. "anthropic").
    *******************************************************************************/
   String getType();

   /*******************************************************************************
    ** A label for the settings screen.
    *******************************************************************************/
   String getLabel();

   /*******************************************************************************
    ** The models offered for this type, best default first.
    *******************************************************************************/
   List<String> getModels();

   /*******************************************************************************
    ** The environment variable that holds the key by default (e.g.
    ** ANTHROPIC_API_KEY).
    *******************************************************************************/
   String getDefaultApiKeyEnvName();

   /*******************************************************************************
    ** The provider's own API base URL (shown when no override is set).
    *******************************************************************************/
   String getDefaultBaseUrl();

   /*******************************************************************************
    ** Build a provider. apiKey is the resolved secret (never null or empty);
    ** baseUrl is null for the type's default.
    *******************************************************************************/
   AssistantModelProvider create(String apiKey, String baseUrl);




   /*******************************************************************************
    ** Backward-compatible provider registry extension.
    *******************************************************************************/
   default List<String> getKinds()
   {
      return (List.of(getType().toUpperCase(java.util.Locale.ROOT).replace('-', '_')));
   }



   /*******************************************************************************
    ** Backward-compatible provider registry extension.
    *******************************************************************************/
   default String getDefaultBaseUrl(String kind)
   {
      return (getDefaultBaseUrl());
   }



   /*******************************************************************************
    ** Backward-compatible provider registry extension.
    *******************************************************************************/
   default String getDefaultApiKeyEnvName(String kind)
   {
      return (getDefaultApiKeyEnvName());
   }



   /*******************************************************************************
    ** Backward-compatible provider registry extension.
    *******************************************************************************/
   default String getDefaultLocation(String kind)
   {
      return ("CLOUD");
   }



   /*******************************************************************************
    ** Backward-compatible provider registry extension.
    *******************************************************************************/
   default AssistantModelProvider create(ProviderSettings settings)
   {
      return (create(settings.getApiKey(), settings.getBaseUrl()));
   }



   /*******************************************************************************
    ** Backward-compatible provider registry extension.
    *******************************************************************************/
   default List<DiscoveredModel> discoverModels(ProviderSettings settings) throws ModelProviderException
   {
      return (getModels().stream().map(id -> new DiscoveredModel(id, id, null, null)).toList());
   }



   /*******************************************************************************
    ** Backward-compatible provider registry extension.
    *******************************************************************************/
   default HealthResult checkHealth(ProviderSettings settings)
   {
      return (new HealthResult(null, null, "Health check not supported"));
   }

}
