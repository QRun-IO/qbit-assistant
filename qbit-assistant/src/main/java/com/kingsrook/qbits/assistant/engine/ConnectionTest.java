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


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelResponse;
import com.kingsrook.qbits.assistant.provider.StreamHandle;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Test connection: one minimal live call to a provider row's model (a short
 ** prompt, at most PING_MAX_TOKENS output tokens, low effort). The result
 ** (status, latency, the model that answered, error text) is saved on the
 ** row, and the call is recorded in assistant_usage with purpose TEST.
 ** Error text comes from the provider exception, which never carries the key
 ** or the request.
 *******************************************************************************/
public final class ConnectionTest
{
   public static final int    PING_MAX_TOKENS = 32;
   public static final String PING_PROMPT     = "Connection test. Reply with the single word OK.";

   public static final String STATUS_OK    = "OK";
   public static final String STATUS_ERROR = "ERROR";



   /*******************************************************************************
    ** What a test found.
    *******************************************************************************/
   public record Result(String status, Integer latencyMs, String model, String error)
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      public boolean ok()
      {
         return (STATUS_OK.equals(status));
      }
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ConnectionTest()
   {
   }



   /*******************************************************************************
    ** Test one provider row and save the result on it.
    *******************************************************************************/
   public static Result run(AssistantRuntime runtime, AssistantProvider row, String userId) throws QException
   {
      Result result = call(runtime, row, userId);
      runtime.getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord()
         .withValue("id", row.getId())
         .withValue("lastTestAt", runtime.getClock().instant())
         .withValue("lastTestStatus", result.status())
         .withValue("lastTestLatencyMs", result.latencyMs())
         .withValue("lastTestModel", result.model())
         .withValue("lastTestError", truncate(result.error(), 500)));
      runtime.getProviderService().invalidate();
      return (result);
   }



   /*******************************************************************************
    ** Make the call (and record its usage).
    *******************************************************************************/
   static Result call(AssistantRuntime runtime, AssistantProvider row, String userId)
   {
      ProviderService.Active active;
      try
      {
         active = runtime.getProviderService().forRow(row, runtime.getConfig().getModels().getMainModel());
      }
      catch(ProviderService.ProviderSetupException e)
      {
         return (new Result(STATUS_ERROR, null, null, e.getMessage()));
      }

      List<ModelMessage> messages = new ArrayList<>();
      messages.add(ModelMessage.user(PING_PROMPT));
      ModelRequest request = new ModelRequest()
         .withModel(active.model())
         .withEffort("low")
         .withMaxTokens(PING_MAX_TOKENS)
         .withMessages(messages);

      UsageRecorder.Call usageCall = new UsageRecorder.Call(userId, null, null, active, UsageRecorder.PURPOSE_TEST);
      ModelPricing       pricing   = runtime.getProviderService().pricing(active.providerType(), active.model());
      long               started   = System.nanoTime();
      try
      {
         StreamHandle  handle   = active.provider().stream(request, text ->
         {
            // the reply text is not used
         });
         ModelResponse response = handle.await(Duration.ofSeconds(runtime.getConfig().getProviderTestTimeoutSeconds()));
         long          latency  = (System.nanoTime() - started) / 1_000_000;
         UsageRecorder.save(runtime.getStore(), UsageRecorder.row(usageCall, response, null, latency, pricing));
         if(ModelResponse.STOP_CANCELLED.equals(response.getStopReason()))
         {
            return (new Result(STATUS_ERROR, (int) latency, null, "The call was cancelled."));
         }
         return (new Result(STATUS_OK, (int) latency, response.getModel() != null ? response.getModel() : active.model(), null));
      }
      catch(ModelProviderException e)
      {
         long latency = (System.nanoTime() - started) / 1_000_000;
         UsageRecorder.save(runtime.getStore(), UsageRecorder.row(usageCall, null, e, latency, pricing));
         return (new Result(STATUS_ERROR, (int) latency, null, describe(e)));
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         return (new Result(STATUS_ERROR, null, null, "The test was interrupted."));
      }
   }



   /*******************************************************************************
    ** An admin-readable error: the kind, the HTTP status and the provider's
    ** (credential-free) message.
    *******************************************************************************/
   static String describe(ModelProviderException e)
   {
      String hint = switch(e.getKind())
      {
         case AUTHENTICATION -> "The API key was refused. Check the key in the secret store.";
         case RATE_LIMITED -> "Rate limited by the provider. Try again shortly.";
         case OVERLOADED -> "The provider is overloaded. Try again shortly.";
         case INVALID_REQUEST -> "The provider refused the request. Check the model id.";
         case TIMEOUT -> "No answer in time.";
         case NETWORK -> "Could not reach the provider. Check the base URL and the network.";
         case SERVER -> "The provider had a server error.";
         default -> "The call failed.";
      };
      return (hint + " (" + e.getKind() + (e.getHttpStatus() == null ? "" : ", HTTP " + e.getHttpStatus()) + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String value, int max)
   {
      return (value == null || value.length() <= max ? value : value.substring(0, max));
   }

}
