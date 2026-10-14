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

package com.kingsrook.qbits.assistant.engine;


import com.kingsrook.qbits.assistant.persistence.TranscriptRedactor;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelResponse;
import com.kingsrook.qbits.assistant.provider.ModelUsage;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Writes one assistant_usage row per model call (a turn step or a
 ** connection test), with the tokens, the cost from the price table, the
 ** latency and how the call ended. A failed write is logged and never fails
 ** the call it describes.
 *******************************************************************************/
public final class UsageRecorder
{
   private static final QLogger LOG = QLogger.getLogger(UsageRecorder.class);

   public static final String PURPOSE_TURN = "TURN";
   public static final String PURPOSE_TEST = "TEST";
   public static final String PURPOSE_EMBED = "EMBED";
   public static final String PURPOSE_ROUTE = "ROUTE";

   public static final String STATUS_OK        = "OK";
   public static final String STATUS_ERROR     = "ERROR";
   public static final String STATUS_CANCELLED = "CANCELLED";



   /*******************************************************************************
    ** Who and what a call was for.
    *******************************************************************************/
   public record Call(String userId, Integer conversationId, Integer turnId, ProviderService.Active active, String purpose)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private UsageRecorder()
   {
   }



   /*******************************************************************************
    ** Build the row of a finished call (response or error, one of them set).
    *******************************************************************************/
   public static AssistantUsage row(Call call, ModelResponse response, ModelProviderException error, long latencyMs, ModelPricing pricing)
   {
      ModelUsage usage = response != null ? response.getUsage() : (error == null ? null : error.getPartialUsage());
      String status = error != null ? STATUS_ERROR
         : (ModelResponse.STOP_CANCELLED.equals(response == null ? null : response.getStopReason()) ? STATUS_CANCELLED : STATUS_OK);

      return (new AssistantUsage()
         .withUserId(call.userId())
         .withConversationId(call.conversationId())
         .withTurnId(call.turnId())
         .withProviderId(call.active() == null ? null : call.active().providerId())
         .withProviderType(call.active() == null ? null : call.active().providerType())
         .withModel(response != null && response.getModel() != null ? response.getModel() : (call.active() == null ? null : call.active().model()))
         .withPurpose(call.purpose())
         .withStatus(status)
         .withErrorKind(error == null ? null : String.valueOf(error.getKind()))
         .withHttpStatus(error == null ? null : error.getHttpStatus())
         .withLatencyMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, latencyMs)))
         .withInputTokens(usage == null ? 0L : nz(usage.getInputTokens()))
         .withOutputTokens(usage == null ? 0L : nz(usage.getOutputTokens()))
         .withCacheReadTokens(usage == null ? 0L : nz(usage.getCacheReadTokens()))
         .withCacheWriteTokens(usage == null ? 0L : nz(usage.getCacheWriteTokens()))
         .withCostMicros(pricing == null ? 0L : pricing.costMicros(usage))
         .withRequestId(response != null ? truncate(response.getRequestId(), 100) : (error == null ? null : truncate(error.getRequestId(), 100))));
   }



   /*******************************************************************************
    ** What an administrator needs to act on a failed call: the provider's error
    ** type and its own (sanitized) message when it sent one, else the summary.
    *******************************************************************************/
   public static String errorText(ModelProviderException error)
   {
      if(error.getProviderMessage() != null)
      {
         return ((error.getProviderErrorType() == null ? "" : error.getProviderErrorType() + ": ") + error.getProviderMessage());
      }
      return (error.getMessage());
   }



   /*******************************************************************************
    ** Insert a row (under the session in QContext); never throws.
    *******************************************************************************/
   public static void save(AssistantStore store, AssistantUsage row)
   {
      try
      {
         store.insert(row);
      }
      catch(QException | RuntimeException e)
      {
         LOG.warn("Could not record assistant model-call usage", e, logPair("turnId", row.getTurnId()), logPair("purpose", row.getPurpose()));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static long nz(Long value)
   {
      return (value == null ? 0 : value);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String value, int max)
   {
      if(value != null && value.length() > max && TranscriptRedactor.current() != null)
      {
         TranscriptRedactor.current().truncated(value, value.substring(0, max));
      }
      return (value == null || value.length() <= max ? value : value.substring(0, max));
   }

}
