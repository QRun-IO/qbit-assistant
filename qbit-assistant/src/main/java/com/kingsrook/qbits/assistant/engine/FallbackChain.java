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


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.provider.ModelEventListener;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelResponse;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** At most four fallback edges per call, never after that call emitted output.
 ** Explicit chains may cross providers. Implicit alternatives stay local and in
 ** the same adapter family; PHI, enablement, health and budgets always narrow them.
 *******************************************************************************/
public class FallbackChain
{
   public static final Integer MAX_HOPS = 4;
   private final AssistantRuntime runtime;
   private final TurnClassification classification;
   private final AssistantTurn turn;
   private final Consumer<String> notice;
   private final AtomicBoolean output = new AtomicBoolean();
   private final ProviderHealth health;



   /*******************************************************************************
    ** The engine owns dispatch, usage, cancellation and strict-schema retries.
    *******************************************************************************/
   @FunctionalInterface
   public interface Dispatch
   {
      /*******************************************************************************
       ** Dispatch one selected model through the existing guards.
       *******************************************************************************/
      ModelResponse call(ProviderService.Active selected) throws QException, ModelProviderException, InterruptedException;
   }



   /*******************************************************************************
    ** One chain tracker per turn.
    *******************************************************************************/
   public FallbackChain(AssistantRuntime runtime, TurnClassification classification, AssistantTurn turn, Consumer<String> notice)
   {
      this.runtime = runtime;
      this.classification = classification;
      this.turn = turn;
      this.notice = notice;
      this.health = new ProviderHealth(runtime);
      turn.setFallbackCount(0);
   }



   /*******************************************************************************
    ** Start a distinct attempt, including a strict-schema retry.
    *******************************************************************************/
   public void beforeCall(ProviderService.Active active) throws QException, ModelProviderException, InterruptedException
   {
      output.set(false);
      health.beforeCall(active);
   }



   /*******************************************************************************
    ** Count text and tool starts conservatively, even when text is still buffered.
    *******************************************************************************/
   public ModelEventListener listener(ModelEventListener delegate)
   {
      return (new ModelEventListener()
      {
         /*******************************************************************************
          ** Visible text prevents another provider from replacing this attempt.
          *******************************************************************************/
         @Override
         public void onTextDelta(String text)
         {
            if(text != null && !text.isEmpty())
            {
               output.set(true);
            }
            delegate.onTextDelta(text);
         }



         /*******************************************************************************
          ** A partial tool call also prevents retry.
          *******************************************************************************/
         @Override
         public void onToolUseStart(String id, String name)
         {
            output.set(true);
            delegate.onToolUseStart(id, name);
         }
      });
   }



   /*******************************************************************************
    ** Used by strict retries as well as provider fallback.
    *******************************************************************************/
   public Boolean hasOutput()
   {
      return (output.get());
   }



   /*******************************************************************************
    ** Retain the original route's budget scopes across downgrades and retries.
    ** A fallback may change the destination, never evade an original STOP.
    *******************************************************************************/
   public BudgetService.Decision budget(ProviderService.Active origin, ProviderService.Active candidate) throws QException
   {
      Boolean phi = classification.getClassification() == DataClassification.PHI;
      BudgetService.Decision original = runtime.getBudgetService().evaluate(turn.getUserId(), origin, phi, false);
      if(original.stopped() || (Objects.equals(origin.providerId(), candidate.providerId()) && Objects.equals(origin.model(), candidate.model())))
      {
         return (original);
      }
      BudgetService.Decision destination = runtime.getBudgetService().evaluate(turn.getUserId(), candidate, phi, false);
      List<String> notices = new ArrayList<>(original.notices());
      notices.addAll(destination.notices());
      return (new BudgetService.Decision(destination.stopped(), destination.reason(), destination.active(), notices));
   }



   /*******************************************************************************
    ** Resolve candidates lazily after a failure; refresh rows before every attempt.
    *******************************************************************************/
   public ModelResponse call(ModelRequest request, Supplier<ProviderService.Active> current, Dispatch dispatch) throws QException, ModelProviderException, InterruptedException
   {
      ProviderService.Active selected = current.get();
      String unavailable = null;
      Set<Integer> visited = new HashSet<>();
      Integer hops = 0;
      Boolean explicitChain = null;
      while(true)
      {
         try
         {
            ModelResponse response = dispatch.call(selected);
            if(!ModelResponse.STOP_CANCELLED.equals(response.getStopReason()))
            {
               health.succeeded(current.get());
               if(unavailable != null)
               {
                  notice.accept("Provider " + unavailable + " is unavailable; answered with " + name(current.get()) + ".");
               }
            }
            return (response);
         }
         catch(ModelProviderException error)
         {
            ProviderService.Active failed = current.get();
            health.failed(failed, error);
            if(!error.isRetryable() || output.get() || hops >= MAX_HOPS)
            {
               throw (error);
            }
            List<AssistantModel> models = runtime.getStore().listModels();
            AssistantModel origin = models.stream().filter(m -> Objects.equals(m.getProviderId(), failed.providerId()) && Objects.equals(m.getModelId(), failed.model())).findFirst().orElse(null);
            if(origin != null)
            {
               visited.add(origin.getId());
            }
            if(explicitChain == null)
            {
               explicitChain = origin != null && origin.getFallbackModelId() != null;
            }
            selected = null;
            List<AssistantModel> candidates = explicitChain && (origin == null || origin.getFallbackModelId() == null) ? List.of() : candidates(origin, failed, models);
            for(AssistantModel candidate : candidates)
            {
               if(hops >= MAX_HOPS)
               {
                  break;
               }
               if(!visited.add(candidate.getId()))
               {
                  continue;
               }
               hops++;
               ProviderService.Active resolved = runtime.getProviderService().resolveModel(candidate.getId(), classification.getClassification() == DataClassification.PHI);
               if(resolved == null || health.isDown(resolved.row()) || PhiRoutingGuard.check(classification, resolved.row(), runtime.getConfig().getHipaaMode()).isPresent())
               {
                  continue;
               }
               BudgetService.Decision budget = runtime.getBudgetService().evaluate(turn.getUserId(), resolved, classification.getClassification() == DataClassification.PHI, false);
               if(budget.stopped())
               {
                  continue;
               }
               if(!explicitChain && !"LOCAL".equals(runtime.getProviderService().location(budget.active().row())))
               {
                  continue;
               }
               Long cap = ProviderService.capMicros(budget.active().row());
               if(health.isDown(budget.active().row()) || (cap != null && runtime.getProviderService().monthToDateCostMicros(budget.active().providerId()) >= cap))
               {
                  continue;
               }
               selected = budget.active();
               budget.notices().forEach(notice);
               break;
            }
            if(selected == null)
            {
               throw (error);
            }
            if(unavailable == null)
            {
               unavailable = name(failed);
            }
            turn.setFallbackCount(turn.getFallbackCount() + 1);
            neutralHistory(request);
         }
      }
   }



   /*******************************************************************************
    ** Explicit edges win. The automatic pool is LOCAL only, same adapter family.
    *******************************************************************************/
   private List<AssistantModel> candidates(AssistantModel origin, ProviderService.Active failed, List<AssistantModel> models) throws QException
   {
      List<AssistantModel> result = new ArrayList<>();
      if(origin != null && origin.getFallbackModelId() != null)
      {
         Integer next = origin.getFallbackModelId();
         Set<Integer> seen = new HashSet<>();
         while(next != null && seen.add(next) && result.size() < MAX_HOPS)
         {
            Integer id = next;
            AssistantModel row = models.stream().filter(m -> id.equals(m.getId())).findFirst().orElse(null);
            if(row == null)
            {
               break;
            }
            result.add(row);
            next = row.getFallbackModelId();
         }
      }
      else if(failed.row() != null && "LOCAL".equals(runtime.getProviderService().location(failed.row())))
      {
         for(AssistantProvider provider : runtime.getStore().listProviders())
         {
            if("LOCAL".equals(runtime.getProviderService().location(provider)) && Objects.equals(provider.getProviderType(), failed.providerType()))
            {
               models.stream().filter(m -> Objects.equals(m.getProviderId(), provider.getId())).forEach(result::add);
            }
         }
      }
      return (result);
   }



   /*******************************************************************************
    ** Rebuild messages from neutral parts; signatures never cross provider boundaries.
    *******************************************************************************/
   public static void neutralHistory(ModelRequest request)
   {
      List<ModelMessage> messages = new ArrayList<>();
      for(ModelMessage message : request.getMessages())
      {
         messages.add(new ModelMessage().withRole(message.getRole()).withParts(new ArrayList<>(message.getParts())).withCacheHint(message.getCacheHint()));
      }
      request.setMessages(messages);
      request.getTools().forEach(tool -> tool.setStrict(false));
   }



   /*******************************************************************************
    ** User-visible names only.
    *******************************************************************************/
   private static String name(ProviderService.Active active)
   {
      return (active.row() == null ? active.providerType() : active.row().getName());
   }



   /*******************************************************************************
    ** Validate the proposed graph (including every row of a batch update).
    *******************************************************************************/
   public static String validate(AssistantModel origin, Map<Integer, AssistantModel> models)
   {
      return (validate(origin, models, true));
   }



   /*******************************************************************************
    ** Recheck ancestor lengths without preventing an admin from disabling a
    ** destination. Runtime selection skips disabled models and follows the chain.
    *******************************************************************************/
   public static String validate(AssistantModel origin, Map<Integer, AssistantModel> models, Boolean requireEnabled)
   {
      Set<Integer> seen = new HashSet<>();
      seen.add(origin.getId());
      Integer next = origin.getFallbackModelId();
      Integer hops = 0;
      while(next != null)
      {
         if(!seen.add(next))
         {
            return ("Fallback chain must not contain a cycle.");
         }
         if(++hops > MAX_HOPS)
         {
            return ("Fallback chain must have at most four hops.");
         }
         AssistantModel candidate = models.get(next);
         if(candidate == null || (requireEnabled && !Boolean.TRUE.equals(candidate.getEnabled())))
         {
            return ("Fallback model must exist and be enabled.");
         }
         next = candidate.getFallbackModelId();
      }
      return (null);
   }
}
