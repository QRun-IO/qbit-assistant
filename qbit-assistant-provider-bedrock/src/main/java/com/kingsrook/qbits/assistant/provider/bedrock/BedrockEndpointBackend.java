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

package com.kingsrook.qbits.assistant.provider.bedrock;


import com.anthropic.backends.Backend;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpResponse;


/*******************************************************************************
 ** SDK 2.71 has no Bedrock endpoint setter. Override only the endpoint, keeping
 ** the SDK's request conversion, SigV4 signing of that endpoint, and decoding.
 ** The caller must validate the URL against the host's allowlist first.
 *******************************************************************************/
record BedrockEndpointBackend(Backend delegate, String baseUrl) implements Backend
{
   /*******************************************************************************
    ** Preserve the Bedrock model-path and body conversion.
    *******************************************************************************/
   @Override
   public HttpRequest prepareRequest(HttpRequest request)
   {
      return (delegate.prepareRequest(request));
   }



   /*******************************************************************************
    ** Sign the actual request URL, including the overridden host.
    *******************************************************************************/
   @Override
   public HttpRequest authorizeRequest(HttpRequest request)
   {
      return (delegate.authorizeRequest(request));
   }



   /*******************************************************************************
    ** Decode the AWS event stream through the SDK.
    *******************************************************************************/
   @Override
   public HttpResponse prepareResponse(HttpResponse response)
   {
      return (delegate.prepareResponse(response));
   }



   /*******************************************************************************
    ** Release the SDK's stream conversion resources.
    *******************************************************************************/
   @Override
   public void close()
   {
      delegate.close();
   }
}
