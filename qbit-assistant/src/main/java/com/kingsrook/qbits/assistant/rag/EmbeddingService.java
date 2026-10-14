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

package com.kingsrook.qbits.assistant.rag;


import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.engine.BudgetService;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.engine.UsageRecorder;
import com.kingsrook.qbits.assistant.model.AssistantDataAudit;
import com.kingsrook.qbits.assistant.scheduled.ScheduledTurn;
import com.kingsrook.qbits.assistant.spi.EmbeddingRequest;
import com.kingsrook.qbits.assistant.spi.EmbeddingResponse;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelResponse;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Embedding dispatch uses fresh eligibility, kill-switch and budget checks per batch.
 *******************************************************************************/
public class EmbeddingService
{
   private final AssistantRuntime runtime;



   /*******************************************************************************
    ** Bind the per-instance accounting and provider services.
    *******************************************************************************/
   public EmbeddingService(AssistantRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Batch at most 64 inputs. Every dispatched batch records EMBED usage,
    ** including failures, and updates the existing daily budget rollups.
    *******************************************************************************/
   public List<float[]> embed(Integer modelId, List<String> texts, DataClassification classification, String tables, Integer conversationId, Integer turnId) throws QException
   {
      List<float[]> vectors = new ArrayList<>();
      for(Integer offset = 0; offset < texts.size(); offset += 64)
      {
         vectors.addAll(batch(modelId, texts.subList(offset, Math.min(offset + 64, texts.size())), classification, tables, conversationId, turnId));
      }
      return (vectors);
   }



   /*******************************************************************************
    ** Audit protected dispatch refusals without any text, before transport.
    *******************************************************************************/
   public ProviderService.Active resolve(Integer modelId, DataClassification classification, String tables, Integer conversationId, Integer turnId) throws QException
   {
      try
      {
         return (runtime.getProviderService().resolveEmbedding(modelId, classification == DataClassification.PHI));
      }
      catch(ProviderService.ProviderSetupException e)
      {
         if(classification == DataClassification.PHI || Boolean.TRUE.equals(runtime.getConfig().getHipaaMode()))
         {
            var model = runtime.getStore().listModels().stream().filter(m -> Objects.equals(m.getId(), modelId)).findFirst().orElse(null);
            var provider = model == null ? null : runtime.getStore().getProvider(model.getProviderId());
            runtime.getStore().insert(new AssistantDataAudit().withUserId(AssistantSecurity.userId(QContext.getQSession())).withConversationId(conversationId).withTurnId(turnId)
               .withProviderId(provider == null ? null : provider.getId()).withProviderName(provider == null ? null : provider.getName())
               .withModel(model == null ? null : model.getModelId()).withHipaaEligible(provider != null && Boolean.TRUE.equals(provider.getHipaaEligible()))
               .withClassification(classification.name()).withTables(tables).withDecision("BLOCKED").withReason(e.getMessage()).withCreateDate(runtime.getClock().instant()));
         }
         throw (e);
      }
   }



   /*******************************************************************************
    ** Never downgrade an embedding call into a different vector space.
    *******************************************************************************/
   private List<float[]> batch(Integer modelId, List<String> texts, DataClassification classification, String tables, Integer conversationId, Integer turnId) throws QException
   {
      String userId = AssistantSecurity.userId(QContext.getQSession());
      ScheduledTurn.checkCost(runtime, conversationId, turnId);
      ProviderService.Active active = resolve(modelId, classification, tables, conversationId, turnId);
      PolicyService.EffectivePolicy policy = runtime.getPolicyService().current(true);
      BudgetService.Decision budget = runtime.getBudgetService().evaluate(userId, active, classification == DataClassification.PHI, false);
      Long cap = ProviderService.capMicros(active.row());
      if(policy.killSwitch())
      {
         throw (new QException("The assistant kill switch is on; embeddings were not sent."));
      }
      if(budget.stopped() || !Objects.equals(active.model(), budget.active().model()) || !Objects.equals(active.providerId(), budget.active().providerId())
         || runtime.getPolicyService().checkConfiguredBudget(policy, userId, 0, 0).hardExceeded()
         || (cap != null && runtime.getProviderService().monthToDateCostMicros(active.providerId()) >= cap))
      {
         throw (new QException("The embedding budget has been reached; embeddings were not sent."));
      }
      if(classification.isAtLeast(DataClassification.PII))
      {
         runtime.getStore().insert(new AssistantDataAudit().withUserId(userId).withConversationId(conversationId).withTurnId(turnId)
            .withProviderId(active.providerId()).withProviderName(active.row().getName()).withModel(active.model())
            .withHipaaEligible(Boolean.TRUE.equals(active.row().getHipaaEligible())).withClassification(classification.name())
            .withTables(tables).withDecision("ALLOWED").withReason("Embedding provider permitted for this classification.").withCreateDate(runtime.getClock().instant()));
      }
      Semaphore gate = runtime.getProviderService().gate(active.row());
      Boolean acquired = gate == null || gate.tryAcquire();
      if(!acquired)
      {
         throw (new QException("Embedding provider concurrency limit reached."));
      }
      Long started = System.nanoTime();
      ModelResponse response = null;
      ModelProviderException error = null;
      try
      {
         EmbeddingResponse embedded = active.provider().embed(new EmbeddingRequest(active.model(), texts));
         response = new ModelResponse().withModel(active.model()).withRequestId(embedded.requestId()).withUsage(embedded.usage());
         if(embedded.vectors().size() != texts.size())
         {
            throw (new ModelProviderException(ModelProviderException.Kind.OTHER, null, "The embedding provider returned the wrong number of vectors."));
         }
         return (embedded.vectors());
      }
      catch(ModelProviderException e)
      {
         error = e;
         throw (new QException("Embedding request failed (" + e.getKind() + "). Check provider settings and retry."));
      }
      finally
      {
         if(gate != null)
         {
            gate.release();
         }
         var pricing = runtime.getProviderService().pricing(active, active.model());
         runtime.getStore().insert(UsageRecorder.row(new UsageRecorder.Call(userId, conversationId, turnId, active, UsageRecorder.PURPOSE_EMBED),
            response, error, (System.nanoTime() - started) / 1_000_000, pricing));
         var usage = response == null ? (error == null ? null : error.getPartialUsage()) : response.getUsage();
         if(usage != null)
         {
            runtime.getStore().addUsage(userId, LocalDate.now(runtime.getClock().withZone(ZoneOffset.UTC)), usage, pricing.costMicros(usage), false);
         }
      }
   }
}
