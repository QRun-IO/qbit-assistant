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
   public static final int MAX_PROVIDER_MESSAGE_CHARS = 300;

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
   private String        providerErrorType;
   private String        providerMessage;
   private String        requestId;
   private boolean       strictSchemaRejected;



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




   /*******************************************************************************
    ** The provider's own error type (e.g. invalid_request_error), when it sent one.
    *******************************************************************************/
   public String getProviderErrorType()
   {
      return (providerErrorType);
   }



   /*******************************************************************************
    ** The provider's own error message: one line, capped, with anything that looks
    ** like a key removed (see {@link #sanitizeProviderMessage}). Safe to log and
    ** to show to administrators; never contains prompt content.
    *******************************************************************************/
   public String getProviderMessage()
   {
      return (providerMessage);
   }



   /*******************************************************************************
    ** The provider's request id, for its support.
    *******************************************************************************/
   public String getRequestId()
   {
      return (requestId);
   }



   /*******************************************************************************
    ** Fluent setter for the provider's error details.
    *******************************************************************************/
   public ModelProviderException withProviderDetails(String providerErrorType, String providerMessage, String requestId)
   {
      this.providerErrorType = sanitizeProviderMessage(providerErrorType, 64);
      this.providerMessage = sanitizeProviderMessage(providerMessage, MAX_PROVIDER_MESSAGE_CHARS);
      this.requestId = sanitizeProviderMessage(requestId, 100);
      return (this);
   }



   /*******************************************************************************
    ** One line, at most max characters, with key-like tokens (sk-..., long
    ** base64/hex runs) replaced by [redacted]. Null for blank input.
    *******************************************************************************/
   public static String sanitizeProviderMessage(String text, int max)
   {
      if(text == null || text.isBlank())
      {
         return (null);
      }
      String line = text.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
      line = line.replaceAll("(?i)\\bsk-[a-z0-9_\\-]{6,}", "[redacted]");
      line = line.replaceAll("[A-Za-z0-9+/_\\-]{40,}", "[redacted]");
      return (line.length() <= max ? line : line.substring(0, max - 1) + "…");
   }




   /*******************************************************************************
    ** Whether the provider refused the request because of its strict tool
    ** schemas (e.g. Anthropic's "compiled grammar is too large"): the same
    ** request without strict tools can succeed.
    *******************************************************************************/
   public boolean isStrictSchemaRejected()
   {
      return (strictSchemaRejected);
   }



   /*******************************************************************************
    ** Fluent setter for strictSchemaRejected
    *******************************************************************************/
   public ModelProviderException withStrictSchemaRejected(boolean strictSchemaRejected)
   {
      this.strictSchemaRejected = strictSchemaRejected;
      return (this);
   }

}
