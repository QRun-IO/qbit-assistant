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


import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantBudgetEvent;
import com.kingsrook.qbits.assistant.model.AssistantBudgetOverride;
import com.kingsrook.qbits.assistant.model.AssistantBudgetUsage;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/*******************************************************************************
 ** Data-driven budgets layered on the existing policy and provider caps.
 ** Evaluation occurs at dispatch boundaries, never during streaming tokens.
 *******************************************************************************/
public class BudgetService
{
   private final AssistantStore store;
   private final ProviderService providers;
   private final Clock clock;



   /*******************************************************************************
    ** Read-only budget snapshot, also used by the app home.
    *******************************************************************************/
   public record Snapshot(AssistantBudget budget, String scopeKey, Instant periodStart, BigDecimal used, BigDecimal limit, BigDecimal percent, String state, List<AssistantBudgetOverride> overrides)
   {
   }



   /*******************************************************************************
    ** Strictest decision and newly raised notices.
    *******************************************************************************/
   public record Decision(boolean stopped, String reason, ProviderService.Active active, List<String> notices)
   {
   }



   /*******************************************************************************
    ** A safe terminal budget error, distinct from provider failures.
    *******************************************************************************/
   public static class Blocked extends QException
   {
      /*******************************************************************************
       ** Name the budget that refused this dispatch.
       *******************************************************************************/
      public Blocked(String reason)
      {
         super(reason);
      }
   }



   /*******************************************************************************
    ** Production constructor.
    *******************************************************************************/
   public BudgetService(AssistantStore store, ProviderService providers, Clock clock)
   {
      this.store = store;
      this.providers = providers;
      this.clock = clock;
   }



   /*******************************************************************************
    ** Evaluate all applicable budgets. STOP wins; a downgrade is checked against
    ** the original and target scopes so switching cannot evade another budget.
    *******************************************************************************/
   public Decision evaluate(String userId, ProviderService.Active active, boolean requiresHipaa, boolean preflight) throws QException
   {
      List<String> notices = new ArrayList<>();
      ProviderService.Active selected = active;
      List<AssistantBudget> budgets = store.budgets();
      List<Integer> applied = new ArrayList<>();
      List<Snapshot> pending = new ArrayList<>();
      List<String> messages = new ArrayList<>();
      for(int pass = 0; pass <= budgets.size(); pass++)
      {
         boolean changed = false;
         for(AssistantBudget budget : budgets)
         {
            if(applied.contains(budget.getId()) || (!applies(budget, userId, active) && !applies(budget, userId, selected)))
            {
               continue;
            }
            applied.add(budget.getId());
            Snapshot snapshot = snapshot(budget, userId);
            String state = snapshot.state();
            String message = null;
            ProviderService.Active target = null;
            if("DOWNGRADED".equals(state))
            {
               target = providers.budgetDowngrade(budget.getDowngradeModelId(), requiresHipaa, active);
               if(target == null)
               {
                  state = "STOPPED";
               }
               else
               {
                  message = "Budget reached: switched to " + target.model() + " for the rest of the period.";
               }
            }
            if("STOPPED".equals(state))
            {
               message = "Assistant budget '" + budget.getName() + "' has been reached. Ask an administrator for an override.";
            }
            if("WARNED".equals(state))
            {
               message = "You have used " + snapshot.percent().setScale(0, RoundingMode.DOWN) + "% of the "
                  + ("DAY".equals(budget.getPeriod()) ? "daily" : "monthly") + " assistant budget '" + budget.getName() + "'.";
            }
            if("STOPPED".equals(state))
            {
               if(transition(snapshot, state, userId, message))
               {
                  notices.add(message);
               }
               return (new Decision(true, message, selected, notices));
            }
            if(message != null && (!preflight || !"DOWNGRADED".equals(state)))
            {
               pending.add(snapshot);
               messages.add(message);
            }
            if(target != null)
            {
               selected = target;
               changed = true;
            }
         }
         if(!changed)
         {
            break;
         }
      }
      for(int i = 0; i < pending.size(); i++)
      {
         Snapshot snapshot = pending.get(i);
         String message = "DOWNGRADED".equals(snapshot.state()) ? "Budget reached: switched to " + selected.model() + " for the rest of the period." : messages.get(i);
         if(transition(snapshot, snapshot.state(), userId, message))
         {
            notices.add(message);
         }
      }
      return (new Decision(false, null, selected, notices));
   }



   /*******************************************************************************
    ** Whether the caller and selected provider/model are within this scope.
    *******************************************************************************/
   private boolean applies(AssistantBudget budget, String userId, ProviderService.Active active) throws QException
   {
      return (switch(budget.getScopeType())
      {
         case "INSTANCE" -> true;
         case "USER" -> Objects.equals(budget.getScopeValue(), userId);
         case "ROLE" -> QContext.getQSession().hasPermission(budget.getScopeValue());
         case "PROVIDER" -> active != null && Objects.equals(budget.getScopeValue(), String.valueOf(active.providerId()));
         case "MODEL" -> modelMatches(budget, active);
         default -> false;
      });
   }



   /*******************************************************************************
    ** Model scope is a registry row id, never an ambiguous cross-provider name.
    *******************************************************************************/
   private boolean modelMatches(AssistantBudget budget, ProviderService.Active active) throws QException
   {
      AssistantModel model = scopedModel(budget);
      return (model != null && active != null && Objects.equals(model.getProviderId(), active.providerId()) && Objects.equals(model.getModelId(), active.model()));
   }



   /*******************************************************************************
    ** Resolve the scoped registry model.
    *******************************************************************************/
   private AssistantModel scopedModel(AssistantBudget budget) throws QException
   {
      return (store.listModels().stream().filter(m -> String.valueOf(m.getId()).equals(budget.getScopeValue())).findFirst().orElse(null));
   }



   /*******************************************************************************
    ** UTC period, usage, active overrides and effective state, without events.
    ** ROLE allowances are per permission holder, evaluated with their session.
    *******************************************************************************/
   public Snapshot snapshot(AssistantBudget budget, String userId) throws QException
   {
      userId = "USER".equals(budget.getScopeType()) ? budget.getScopeValue() : userId;
      Instant now = clock.instant();
      LocalDate day = LocalDate.ofInstant(now, ZoneOffset.UTC);
      LocalDate start = "MONTH".equals(budget.getPeriod()) ? day.withDayOfMonth(1) : day;
      Instant from = start.atStartOfDay().toInstant(ZoneOffset.UTC);
      Instant until = ("MONTH".equals(budget.getPeriod()) ? start.plusMonths(1) : start.plusDays(1)).atStartOfDay().toInstant(ZoneOffset.UTC);
      String scopeKey = budget.getScopeType() + ":" + ("ROLE".equals(budget.getScopeType()) ? budget.getScopeValue() + ":" + userId : Objects.toString(budget.getScopeValue(), ""));
      boolean shared = List.of("INSTANCE", "PROVIDER", "MODEL").contains(budget.getScopeType());
      String dateField = shared ? "occurredAt" : "createDate";
      QQueryFilter filter = new QQueryFilter().withCriteria(dateField, QCriteriaOperator.GREATER_THAN_OR_EQUALS, from)
         .withCriteria(dateField, QCriteriaOperator.LESS_THAN, until);
      switch(budget.getScopeType())
      {
         case "USER" -> filter.withCriteria("userId", QCriteriaOperator.EQUALS, budget.getScopeValue());
         case "ROLE" -> filter.withCriteria("userId", QCriteriaOperator.EQUALS, userId);
         case "PROVIDER" -> filter.withCriteria("providerId", QCriteriaOperator.EQUALS, Integer.valueOf(budget.getScopeValue()));
         case "MODEL" ->
         {
            AssistantModel model = scopedModel(budget);
            filter.withCriteria("providerId", QCriteriaOperator.EQUALS, model == null ? -1 : model.getProviderId());
            filter.withCriteria("model", QCriteriaOperator.EQUALS, model == null ? "" : model.getModelId());
         }
         default ->
         {
            // Shared accounting contains no conversation or request data.
         }
      }
      BigDecimal used = BigDecimal.ZERO;
      for(AssistantUsage usage : store.query(shared ? AssistantBudgetUsage.TABLE_NAME : AssistantUsage.TABLE_NAME, filter, AssistantUsage.class))
      {
         used = used.add("COST_USD".equals(budget.getMetric()) ? BigDecimal.valueOf(nz(usage.getCostMicros()), 6)
            : BigDecimal.valueOf(nz(usage.getInputTokens())).add(BigDecimal.valueOf(nz(usage.getOutputTokens())))
               .add(BigDecimal.valueOf(nz(usage.getCacheReadTokens()))).add(BigDecimal.valueOf(nz(usage.getCacheWriteTokens()))));
      }
      BigDecimal limit = budget.getLimitValue();
      boolean unlimited = false;
      List<AssistantBudgetOverride> overrides = new ArrayList<>();
      for(AssistantBudgetOverride override : store.budgetOverrides(budget.getId()))
      {
         if(override.getExpiresAt() == null || !override.getExpiresAt().isAfter(now)
            || (override.getScopeValue() != null && !override.getScopeValue().isBlank() && !override.getScopeValue().equals(userId)))
         {
            continue;
         }
         overrides.add(override);
         if(override.getUnlimitedUntil() != null && override.getUnlimitedUntil().isAfter(now))
         {
            unlimited = true;
         }
         if(override.getExtraValue() != null)
         {
            limit = limit.add(override.getExtraValue());
         }
      }
      BigDecimal percent = unlimited ? BigDecimal.ZERO : used.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.DOWN);
      String state = unlimited ? "UNLIMITED" : percent.compareTo(BigDecimal.valueOf(budget.getHardCeilingPercent())) >= 0 ? "STOPPED"
         : percent.compareTo(BigDecimal.valueOf(100)) >= 0 ? ("STOP".equals(budget.getAtLimitAction()) ? "STOPPED" : "DOWNGRADED")
         : percent.compareTo(BigDecimal.valueOf(budget.getWarnPercent())) >= 0 ? "WARNED" : "OK";
      return (new Snapshot(budget, scopeKey, from, used, unlimited ? null : limit, percent, state, overrides));
   }



   /*******************************************************************************
    ** The persisted unique key is also the warning/alert deduplication record.
    ** Resolution on period rollover or override recovery needs an active-alert
    ** lifecycle: these immutable events cannot re-raise a resolved state within
    ** the same period, and user-specific overrides must not resolve shared alerts.
    ** Track active keys and recurrence before calling QAlerts.resolve here.
    *******************************************************************************/
   private boolean transition(Snapshot snapshot, String state, String userId, String message) throws QException
   {
      if(!store.budgetEvent(new AssistantBudgetEvent().withBudgetId(snapshot.budget().getId()).withScopeKey(snapshot.scopeKey())
         .withPeriodStart(snapshot.periodStart()).withState(state).withUsedValue(snapshot.used()).withLimitValue(snapshot.limit())
         .withUserId(userId).withCreateDate(clock.instant())))
      {
         return (false);
      }
      String type = "assistant.budget." + switch(state)
      {
         case "WARNED" -> "warning";
         case "DOWNGRADED" -> "downgraded";
         default -> "stopped";
      };
      BudgetAlerts.raise(type, snapshot.budget().getId() + ":" + snapshot.scopeKey() + ":" + snapshot.periodStart(),
         "STOPPED".equals(state) ? AlertSeverity.ERROR : AlertSeverity.WARNING, "Assistant budget " + snapshot.budget().getName(), message);
      return (true);
   }



   /*******************************************************************************
    ** Null usage counters are zero.
    *******************************************************************************/
   private static long nz(Long value)
   {
      return (value == null ? 0 : value);
   }
}
