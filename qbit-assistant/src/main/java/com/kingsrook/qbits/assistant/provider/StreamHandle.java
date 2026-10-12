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


import java.time.Duration;


/*******************************************************************************
 ** A running streamed model call.
 *******************************************************************************/
public interface StreamHandle
{

   /*******************************************************************************
    ** Abort the call (idempotent). await() then returns a response with stop
    ** reason "cancelled" (and whatever content had arrived).
    *******************************************************************************/
   void cancel();

   /*******************************************************************************
    ** Whether cancel() was called.
    *******************************************************************************/
   boolean isCancelled();

   /*******************************************************************************
    ** Wait for the complete response. Throws ModelProviderException (kind
    ** TIMEOUT) when the timeout passes; the call is then cancelled.
    *******************************************************************************/
   ModelResponse await(Duration timeout) throws ModelProviderException, InterruptedException;

}
