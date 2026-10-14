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


import java.util.stream.Collectors;
import java.util.Objects;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.provider.CacheHint;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelResponse;
import com.kingsrook.qbits.assistant.provider.ModelUsage;
import com.kingsrook.qbits.assistant.provider.StreamHandle;
import com.kingsrook.qbits.assistant.provider.SystemBlock;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** Selects the initial model from the effective profile. Classifier content is
 ** strictly the fixed instruction plus the question; no context is attached.
 *******************************************************************************/
public class ModelRouter
{
   public static final String CLASSIFIER_PROMPT = "Classify the user's request as SIMPLE or COMPLEX. Reply with one word.";
   private static final Pattern COMPLEX_WORDS = Pattern.compile("\\b(analyze|compare|why|trend|explain|summarize\\s+across|plan)\\b", Pattern.CASE_INSENSITIVE);
   private final AssistantRuntime runtime;
   private final ModelUsage usage = new ModelUsage();
   private Long costMicros = 0L;
   private Integer calls = 0;



   /*******************************************************************************
    ** Routing returns accounting as well as the selected model.
    *******************************************************************************/
   public record Route(ProviderService.Active active, String effort, ModelUsage usage, Long costMicros, Integer calls)
   {
   }



   /*******************************************************************************
    ** One router per turn; accounting is never shared between users.
    *******************************************************************************/
   public ModelRouter(AssistantRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Preserve classifier accounting even when later route resolution fails.
    *******************************************************************************/
   public Route accounting()
   {
      return (new Route(null, null, usage, costMicros, calls));
   }



   /*******************************************************************************
    ** Each rule is independently sufficient. Boundaries are >600 characters,
    ** at least two distinct table names, and >8 previous turns. Match words,
    ** not substrings (for example, planet does not imply plan).
    *******************************************************************************/
   public static String heuristic(String question, Set<String> tableNames, Boolean writeContext, Integer priorTurns, Boolean previousToolLimit)
   {
      String text = question == null ? "" : question;
      Long mentioned = tableNames.stream().filter(name -> Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE).matcher(text).find()).count();
      return (Boolean.TRUE.equals(writeContext) || text.length() > 600 || mentioned >= 2 || COMPLEX_WORDS.matcher(text).find()
         || priorTurns > 8 || Boolean.TRUE.equals(previousToolLimit) ? "COMPLEX" : "SIMPLE");
   }



   /*******************************************************************************
    ** OFF preserves row/default/policy selection. With no profile, configured
    ** escalation supplies the complex slot and its effort when enabled.
    *******************************************************************************/
   public Route route(QSession session, AssistantConversation conversation, AssistantTurn turn, String question, Map<String, Object> pageContext,
      PolicyService.EffectivePolicy policy, TurnClassification classification, RunningTurn running, Instant deadline, Consumer<String> notice) throws QException, InterruptedException
   {
      AssistantProfile profile = runtime.getProfileService().pick(session, runtime.appNameFor(pageContext));
      String mode = profile == null || profile.getRoutingMode() == null ? "OFF" : profile.getRoutingMode();
      Boolean escalation = profile == null && Boolean.TRUE.equals(runtime.getConfig().getModels().getEscalationEnabled());
      String decision = "OFF";
      String source = null;
      Integer selectedId = null;
      String effort = policy.mainEffort();
      if(!"OFF".equals(mode) || escalation)
      {
         List<AssistantTurn> previous = runtime.getStore().query(AssistantTurn.TABLE_NAME, new QQueryFilter()
            .withCriteria("userId", QCriteriaOperator.EQUALS, turn.getUserId()).withCriteria("conversationId", QCriteriaOperator.EQUALS, conversation.getId())
            .withCriteria("id", QCriteriaOperator.LESS_THAN, turn.getId()).withOrderBy(new QFilterOrderBy("id", false)).withLimit(9), AssistantTurn.class);
         Boolean pending = !runtime.getStore().query(AssistantPendingAction.TABLE_NAME, new QQueryFilter()
            .withCriteria("userId", QCriteriaOperator.EQUALS, turn.getUserId()).withCriteria("conversationId", QCriteriaOperator.EQUALS, conversation.getId())
            .withCriteria("status", QCriteriaOperator.EQUALS, AssistantPendingAction.STATUS_PENDING).withLimit(1), AssistantPendingAction.class).isEmpty();
         String digest = runtime.getContextBuilder().appDigest();
         Set<String> tables = runtime.getQInstance().getTables().keySet().stream().filter(digest::contains).collect(Collectors.toSet());
         decision = heuristic(question, tables, pending, previous.size(), !previous.isEmpty() && "tool_limit".equals(previous.get(0).getStopReason()));
         source = "HEURISTIC";
         if("CLASSIFIER".equals(mode) && profile.getClassifierModelId() != null)
         {
            String classified = classify(profile.getClassifierModelId(), question, turn, policy, classification, running, deadline, notice);
            if(classified != null)
            {
               decision = classified;
               source = "CLASSIFIER";
            }
         }
         selectedId = profile == null ? null : ("COMPLEX".equals(decision) ? profile.getComplexModelId() : profile.getSimpleModelId());
      }
      turn.withRouteDecision(decision).withRouteSource(source);
      ProviderService.Active active = selectedId == null ? null : runtime.getProviderService().resolveModel(selectedId, classification.getClassification() == DataClassification.PHI);
      if(selectedId != null && active == null)
      {
         throw (new ProviderService.ProviderSetupException("The routed model is disabled, unavailable or not eligible for this conversation."));
      }
      if(active == null)
      {
         TurnDataRouting.beforeResolve(runtime, classification, turn, policy.mainModel());
         active = runtime.getProviderService().resolve(policy.mainModel());
         if(active != null && escalation && "COMPLEX".equals(decision))
         {
            String model = runtime.getConfig().getModels().getEscalationModel();
            if(active.row() == null)
            {
               active = new ProviderService.Active(null, active.providerType(), model, active.provider());
            }
            else
            {
               active = runtime.getProviderService().forRow(new AssistantProvider(active.row().toQRecord()).withModel(model), model);
            }
            effort = runtime.getConfig().getModels().getEscalationEffort();
         }
      }
      return (new Route(active, effort, usage, costMicros, calls));
   }



   /*******************************************************************************
    ** Buffer a classifier reply, with a three-second deadline and four output
    ** tokens. Errors and non-exact answers leave the heuristic decision intact.
    *******************************************************************************/
   private String classify(Integer modelId, String question, AssistantTurn turn, PolicyService.EffectivePolicy policy, TurnClassification classification,
      RunningTurn running, Instant deadline, Consumer<String> notice) throws QException, InterruptedException
   {
      classification.inherit(runtime.getStore().getConversation(turn.getUserId(), turn.getConversationId()));
      ProviderService.Active active = runtime.getProviderService().resolveModel(modelId, classification.getClassification() == DataClassification.PHI, false);
      if(active == null || PhiRoutingGuard.check(classification, active.row(), runtime.getConfig().getHipaaMode()).isPresent())
      {
         return (null);
      }
      BudgetService.Decision budget = runtime.getBudgetService().evaluate(turn.getUserId(), active, classification.getClassification() == DataClassification.PHI, false);
      budget.notices().forEach(notice);
      PolicyService.BudgetStatus configured = runtime.getPolicyService().checkConfiguredBudget(policy, turn.getUserId(), 0, 0);
      if(budget.stopped() || configured.hardExceeded() || !Objects.equals(budget.active().model(), active.model()) || !Objects.equals(budget.active().providerId(), active.providerId()))
      {
         return (null);
      }
      Long cap = ProviderService.capMicros(active.row());
      if((cap != null && runtime.getProviderService().monthToDateCostMicros(active.providerId()) >= cap) || running.isCancelled() || runtime.getPolicyService().isKillSwitchOn() || !runtime.getClock().instant().isBefore(deadline))
      {
         return (null);
      }
      try
      {
         TurnDataRouting.beforeModelCall(runtime, classification, turn, active);
      }
      catch(TurnDataRouting.Blocked e)
      {
         return (null);
      }
      ModelRequest request = new ModelRequest().withModel(active.model()).withMaxTokens(4).withStreaming(false)
         .withSystem(List.of(new SystemBlock(CLASSIFIER_PROMPT, CacheHint.NONE))).withMessages(List.of(ModelMessage.user(question)));
      ModelResponse response = null;
      ModelProviderException error = null;
      StreamHandle handle = null;
      Long started = System.nanoTime();
      ProviderHealth health = new ProviderHealth(runtime);
      Semaphore gate = runtime.getProviderService().gate(active.row());
      Boolean acquired = false;
      try
      {
         health.beforeCall(active);
         if(gate != null)
         {
            acquired = gate.tryAcquire();
            if(!acquired)
            {
               throw (new ModelProviderException(ModelProviderException.Kind.RATE_LIMITED, null, "Classifier concurrency limit reached."));
            }
         }
         calls++;
         handle = active.provider().stream(request, text ->
         {
         });
         running.setCurrentHandle(handle);
         Duration remaining = Duration.between(runtime.getClock().instant(), deadline);
         response = handle.await(remaining.compareTo(Duration.ofSeconds(3)) < 0 ? remaining : Duration.ofSeconds(3));
         if(!ModelResponse.STOP_CANCELLED.equals(response.getStopReason()))
         {
            health.succeeded(active);
            String answer = response.getText().strip().toUpperCase(Locale.ROOT);
            if(List.of("SIMPLE", "COMPLEX").contains(answer) && response.getToolUses().isEmpty())
            {
               return (answer);
            }
         }
         return (null);
      }
      catch(ModelProviderException e)
      {
         error = e;
         health.failed(active, e);
         return (null);
      }
      catch(RuntimeException e)
      {
         error = new ModelProviderException(ModelProviderException.Kind.OTHER, null, "Classifier did not return a usable answer.");
         return (null);
      }
      finally
      {
         if(acquired)
         {
            gate.release();
         }
         if(handle != null)
         {
            handle.cancel();
         }
         running.setCurrentHandle(null);
         ModelUsage measured = response == null ? (error == null ? null : error.getPartialUsage()) : response.getUsage();
         if(measured != null)
         {
            usage.add(measured);
            costMicros += runtime.getProviderService().pricing(active, active.model()).costMicros(measured);
         }
         UsageRecorder.save(runtime.getStore(), UsageRecorder.row(new UsageRecorder.Call(turn.getUserId(), turn.getConversationId(), turn.getId(), active, UsageRecorder.PURPOSE_ROUTE),
            response, error, (System.nanoTime() - started) / 1_000_000, runtime.getProviderService().pricing(active, active.model())));
      }
   }
}
