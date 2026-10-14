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


/*******************************************************************************
 ** Model provider SPI (spec 2.8). A provider turns a provider-neutral
 ** ModelRequest into a streamed model call.
 **
 ** Contract:
 ** - stream() returns at once; the call runs in the background. Text deltas
 **   go to the listener as they arrive (never thinking text). The final
 **   response (all content parts, stop reason, usage) comes from
 **   StreamHandle.await().
 ** - StreamHandle.cancel() aborts the HTTP stream; await() then returns a
 **   response with stopReason "cancelled".
 ** - Provider errors surface as ModelProviderException from await(), with a
 **   message that never contains credentials or request bodies.
 ** - Implementations must never log prompts, tool results or keys.
 *******************************************************************************/
public interface AssistantModelProvider
{

   /*******************************************************************************
    ** The provider's name (what AssistantQBitConfig.providerName refers to).
    *******************************************************************************/
   String getName();

   /*******************************************************************************
    ** What a model supports (tools, parallel tools, caching, context size).
    *******************************************************************************/
   ModelCapabilities getCapabilities(String modelId);

   /*******************************************************************************
    ** Start a streamed call.
    *******************************************************************************/
   StreamHandle stream(ModelRequest request, ModelEventListener listener);

   /*******************************************************************************
    ** Optional token count of a request; null when unsupported.
    *******************************************************************************/
   default TokenCount countTokens(ModelRequest request)
   {
      return (null);
   }



   /*******************************************************************************
    ** Optional embedding capability. Unsupported adapters fail before transport.
    *******************************************************************************/
   default EmbeddingResponse embed(EmbeddingRequest request) throws ModelProviderException
   {
      throw (new ModelProviderException(ModelProviderException.Kind.INVALID_REQUEST, null, "Embeddings are unsupported by this provider."));
   }

}
