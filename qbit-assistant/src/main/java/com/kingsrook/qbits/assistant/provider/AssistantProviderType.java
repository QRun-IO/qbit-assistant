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

}
