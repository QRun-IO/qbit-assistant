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
 ** A failed model call. The message is safe to show and log: providers must
 ** never put credentials, prompts or tool results in it.
 *******************************************************************************/
public class ModelProviderException extends Exception
{
   /*******************************************************************************
    ** Error classes the engine reacts to.
    *******************************************************************************/
   public enum Kind
   {
      AUTHENTICATION,
      RATE_LIMITED,
      OVERLOADED,
      INVALID_REQUEST,
      TIMEOUT,
      NETWORK,
      SERVER,
      OTHER
   }

   private final Kind    kind;
   private final Integer httpStatus;
   private ModelUsage    partialUsage;



   /*******************************************************************************
    **
    *******************************************************************************/
   public ModelProviderException(Kind kind, Integer httpStatus, String message)
   {
      super(message);
      this.kind = kind;
      this.httpStatus = httpStatus;
   }



   /*******************************************************************************
    ** Getter for kind
    *******************************************************************************/
   public Kind getKind()
   {
      return (kind);
   }



   /*******************************************************************************
    ** Getter for httpStatus (null when there was no HTTP response)
    *******************************************************************************/
   public Integer getHttpStatus()
   {
      return (httpStatus);
   }



   /*******************************************************************************
    ** Usage already incurred by the failed call (e.g. input tokens of a stream
    ** that broke off), or null. Counted toward budgets and cost.
    *******************************************************************************/
   public ModelUsage getPartialUsage()
   {
      return (partialUsage);
   }



   /*******************************************************************************
    ** Fluent setter for partialUsage
    *******************************************************************************/
   public ModelProviderException withPartialUsage(ModelUsage partialUsage)
   {
      this.partialUsage = partialUsage;
      return (this);
   }



   /*******************************************************************************
    ** Whether a retry later may succeed.
    *******************************************************************************/
   public boolean isRetryable()
   {
      return (kind == Kind.RATE_LIMITED || kind == Kind.OVERLOADED || kind == Kind.TIMEOUT || kind == Kind.NETWORK || kind == Kind.SERVER);
   }

}
