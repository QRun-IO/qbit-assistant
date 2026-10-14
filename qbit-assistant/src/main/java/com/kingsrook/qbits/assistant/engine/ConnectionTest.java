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
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.provider.RegistryModelProvider;
import com.kingsrook.qbits.assistant.rag.EmbeddingService;
import com.kingsrook.qbits.assistant.spi.AssistantProviderType;
import com.kingsrook.qbits.assistant.spi.HealthResult;
import com.kingsrook.qbits.assistant.spi.ModelMessage;
import com.kingsrook.qbits.assistant.spi.ModelPricing;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.spi.ModelResponse;
import com.kingsrook.qbits.assistant.spi.StreamHandle;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
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

      return (call(runtime, active, userId));
   }



   /*******************************************************************************
    ** Probe health, then make and account for a minimal completion.
    *******************************************************************************/
   private static Result call(AssistantRuntime runtime, ProviderService.Active active, String userId)
   {
      List<ModelMessage> messages = new ArrayList<>();
      messages.add(ModelMessage.user(PING_PROMPT));
      ModelRequest request = new ModelRequest()
         .withModel(active.model())
         .withEffort("low")
         .withMaxTokens(PING_MAX_TOKENS)
         .withMessages(messages);

      UsageRecorder.Call usageCall = new UsageRecorder.Call(userId, null, null, active, UsageRecorder.PURPOSE_TEST);
      ModelPricing       pricing   = runtime.getProviderService().pricing(active, active.model());
      long               started   = System.nanoTime();
      try
      {
         AssistantProviderType type = runtime.getConfig().getProviderTypes().get(active.providerType());
         HealthResult health = type.checkHealth(runtime.getProviderService().settings(active.row()));
         if(Boolean.FALSE.equals(health.healthy()))
         {
            throw (new ModelProviderException(ModelProviderException.Kind.NETWORK, null, health.message()));
         }
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
         return (new Result(STATUS_ERROR, (int) latency, null, Boolean.TRUE.equals(runtime.getConfig().getHipaaMode()) ? String.valueOf(e.getKind()) : describe(e)));
      }
      catch(ProviderService.ProviderSetupException e)
      {
         return (new Result(STATUS_ERROR, null, null, e.getMessage()));
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
      String hint = hint(e.getKind());
      return (hint + " (" + e.getKind() + (e.getHttpStatus() == null ? "" : ", HTTP " + e.getHttpStatus()) + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String value, int max)
   {
      return (value == null || value.length() <= max ? value : value.substring(0, max));
   }




   /*******************************************************************************
    ** Test even a disabled registry model before an admin enables it.
    *******************************************************************************/
   public static Result runModel(AssistantRuntime runtime, AssistantModel model, String userId) throws QException
   {
      AssistantProvider row = runtime.getStore().getProvider(model.getProviderId());
      Result result;
      try
      {
         if(row == null)
         {
            throw (new ProviderService.ProviderSetupException("The model's provider no longer exists."));
         }
         if(!model.isChat())
         {
            result = testEmbedding(runtime, model);
         }
         else
         {
            AssistantProviderType type = runtime.getConfig().getProviderTypes().get(row.getProviderType());
            var settings = runtime.getProviderService().settings(row);
            var provider = new RegistryModelProvider(type.create(settings), model);
            result = call(runtime, new ProviderService.Active(row, row.getProviderType(), model.getModelId(), provider), userId);
         }
      }
      catch(ProviderService.ProviderSetupException e)
      {
         result = new Result(STATUS_ERROR, null, model.getModelId(), e.getMessage());
      }
      runtime.getStore().updateFields(AssistantModel.TABLE_NAME, new QRecord().withValue("id", model.getId())
         .withValue("lastTestAt", runtime.getClock().instant()).withValue("lastTestStatus", result.status())
         .withValue("lastTestLatencyMs", result.latencyMs()).withValue("lastTestError", truncate(result.error(), 500)));
      return (result);
   }



   /*******************************************************************************
    ** An embedding model is tested with embeddings, never a chat completion.
    ** The normal embedding budget, eligibility and usage rules still apply.
    *******************************************************************************/
   private static Result testEmbedding(AssistantRuntime runtime, AssistantModel model)
   {
      Long started = System.nanoTime();
      try
      {
         new EmbeddingService(runtime).embed(model.getId(), List.of("Connection test."),
            DataClassification.PUBLIC, "", null, null);
         return (new Result(STATUS_OK, (int) ((System.nanoTime() - started) / 1_000_000), model.getModelId(), null));
      }
      catch(QException e)
      {
         return (new Result(STATUS_ERROR, null, model.getModelId(), e.getMessage()));
      }
   }




   /*******************************************************************************
    ** Map the provider error category.
    *******************************************************************************/
   private static String hint(ModelProviderException.Kind kind)
   {
      return (switch(kind)
      {
         case AUTHENTICATION -> "The API key was refused. Check the key in the secret store.";
         case RATE_LIMITED -> "Rate limited by the provider. Try again shortly.";
         case OVERLOADED -> "The provider is overloaded. Try again shortly.";
         case INVALID_REQUEST -> "The provider refused the request. Check the model id.";
         case TIMEOUT -> "No answer in time.";
         case NETWORK -> "Could not reach the provider. Check the base URL and the network.";
         case SERVER -> "The provider had a server error.";
         default -> "The call failed.";
      });
   }

}
