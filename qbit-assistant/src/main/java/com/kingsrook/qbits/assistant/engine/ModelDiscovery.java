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


import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.spi.AssistantProviderType;
import com.kingsrook.qbits.assistant.spi.DiscoveredModel;
import com.kingsrook.qbits.assistant.spi.ModelCapabilities;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ProviderSettings;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Additive discovery: never overwrite an administrator's model configuration.
 *******************************************************************************/
public final class ModelDiscovery
{
   /*******************************************************************************
    ** Summary for the standard process result screen.
    *******************************************************************************/
   public record Result(boolean ok, String message)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ModelDiscovery()
   {
   }



   /*******************************************************************************
    ** Serialize local discovery runs; the table unique key also guards replicas.
    *******************************************************************************/
   public static Result run(AssistantRuntime runtime, AssistantProvider row) throws QException
   {
      ProviderService service = runtime.getProviderService();
      synchronized(service)
      {
         Result result;
         try
         {
            ProviderSettings settings = service.settings(row);
            AssistantProviderType type = runtime.getConfig().getProviderTypes().get(row.getProviderType());
            List<DiscoveredModel> discovered = type.discoverModels(settings);
            Set<String> existing = new HashSet<>();
            runtime.getStore().listModels().stream().filter(m -> row.getId().equals(m.getProviderId())).forEach(m -> existing.add(m.getModelId()));
            boolean local = "LOCAL".equals(service.location(row));
            var provider = type.create(settings);
            int added = 0;
            int present = 0;
            for(DiscoveredModel model : discovered)
            {
               if(existing.contains(model.modelId()))
               {
                  present++;
                  continue;
               }
               ModelCapabilities caps = provider.getCapabilities(model.modelId());
               runtime.getStore().insert(new AssistantModel().withProviderId(row.getId()).withModelId(model.modelId())
                  .withModelKind(embedding(model.modelId()) ? "EMBEDDING" : "CHAT").withEmbeddingDimensions(dimensions(model.modelId()))
                  .withDisplayName(model.displayName()).withEnabled(local || discovered.size() <= 20)
                  .withSource("DISCOVERED").withDiscoveredAt(runtime.getClock().instant())
                  .withSupportsTools(caps.getTools()).withSupportsVision(caps.getVision()).withSupportsStreaming(caps.getStreaming())
                  .withSupportsReasoningEffort(caps.getReasoningEffort())
                  .withContextWindowTokens(model.contextWindowTokens() == null ? caps.getMaxContextTokens() : model.contextWindowTokens())
                  .withInputUsdPerMTok(local ? BigDecimal.ZERO : null).withOutputUsdPerMTok(local ? BigDecimal.ZERO : null));
               existing.add(model.modelId());
               added++;
            }
            result = new Result(true, added + " models added, " + present + " already present");
         }
         catch(ProviderService.ProviderSetupException e)
         {
            result = new Result(false, e.getMessage());
         }
         catch(ModelProviderException e)
         {
            result = new Result(false, ConnectionTest.describe(e));
         }
         catch(QException | RuntimeException e)
         {
            result = new Result(false, "Could not save discovered models. Check the model registry and retry.");
         }
         runtime.getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", row.getId())
            .withValue("lastDiscoveryAt", runtime.getClock().instant()).withValue("lastDiscoveryStatus", result.ok() ? "OK" : "ERROR")
            .withValue("lastDiscoveryError", result.ok() ? null : result.message().substring(0, Math.min(500, result.message().length()))));
         service.invalidate();
         return (result);
      }
   }


   /*******************************************************************************
    ** Discovery is a suggestion; an administrator can correct the kind.
    *******************************************************************************/
   public static Boolean embedding(String id)
   {
      String lower = id.toLowerCase(java.util.Locale.ROOT);
      return (List.of("embed", "bge", "e5", "gte", "nomic").stream().anyMatch(lower::contains));
   }



   /*******************************************************************************
    ** Known local defaults; other models must declare their dimensions.
    *******************************************************************************/
   private static Integer dimensions(String id)
   {
      if(id.contains("bge-m3"))
      {
         return (1024);
      }
      if(id.contains("nomic-embed-text"))
      {
         return (768);
      }
      return (null);
   }

}
