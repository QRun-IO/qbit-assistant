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


import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import com.kingsrook.qbits.assistant.AssistantBudgets;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The effective policy: QBit config overlaid with the assistantPolicy row
 ** (kill switch, model routing, budgets), cached for policyCacheSeconds -
 ** except the kill switch checks inside a turn, which read fresh. Budget
 ** usage days are UTC.
 *******************************************************************************/
public class PolicyService
{
   private static final QLogger LOG = QLogger.getLogger(PolicyService.class);

   private final AssistantQBitConfig config;
   private final AssistantStore      store;
   private final Clock               clock;

   private volatile AssistantPolicy cached;
   private volatile Instant         cachedAt;



   /*******************************************************************************
    ** The resolved policy values.
    *******************************************************************************/
   public record EffectivePolicy(boolean killSwitch, String killSwitchReason, String mainModel, String mainEffort, AssistantBudgets budgets)
   {
   }



   /*******************************************************************************
    ** Where a user stands against the budgets.
    *******************************************************************************/
   public record BudgetStatus(boolean hardExceeded, boolean softExceeded, String message)
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      public static BudgetStatus ok()
      {
         return (new BudgetStatus(false, false, null));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public PolicyService(AssistantQBitConfig config, AssistantStore store, Clock clock)
   {
      this.config = config;
      this.store = store;
      this.clock = clock;
   }



   /*******************************************************************************
    ** The effective policy (fresh = bypass the cache).
    *******************************************************************************/
   public EffectivePolicy current(boolean fresh) throws QException
   {
      AssistantPolicy row = loadRow(fresh);

      AssistantBudgets base    = config.getBudgets() == null ? new AssistantBudgets() : config.getBudgets();
      AssistantBudgets budgets = new AssistantBudgets()
         .withUserDailyTokensSoft(pick(row == null ? null : row.getUserDailyTokensSoft(), base.getUserDailyTokensSoft()))
         .withUserDailyTokensHard(pick(row == null ? null : row.getUserDailyTokensHard(), base.getUserDailyTokensHard()))
         .withUserDailyCostMicrosSoft(pick(row == null ? null : row.getUserDailyCostMicrosSoft(), base.getUserDailyCostMicrosSoft()))
         .withUserDailyCostMicrosHard(pick(row == null ? null : row.getUserDailyCostMicrosHard(), base.getUserDailyCostMicrosHard()))
         .withInstanceMonthlyTokensSoft(pick(row == null ? null : row.getInstanceMonthlyTokensSoft(), base.getInstanceMonthlyTokensSoft()))
         .withInstanceMonthlyTokensHard(pick(row == null ? null : row.getInstanceMonthlyTokensHard(), base.getInstanceMonthlyTokensHard()))
         .withInstanceMonthlyCostMicrosSoft(pick(row == null ? null : row.getInstanceMonthlyCostMicrosSoft(), base.getInstanceMonthlyCostMicrosSoft()))
         .withInstanceMonthlyCostMicrosHard(pick(row == null ? null : row.getInstanceMonthlyCostMicrosHard(), base.getInstanceMonthlyCostMicrosHard()));

      String mainModel  = row != null && StringUtils.hasContent(row.getMainModel()) ? row.getMainModel() : config.getModels().getMainModel();
      String mainEffort = row != null && StringUtils.hasContent(row.getMainEffort()) ? row.getMainEffort() : config.getModels().getMainEffort();

      return (new EffectivePolicy(row != null && Boolean.TRUE.equals(row.getKillSwitch()), row == null ? null : row.getKillSwitchReason(), mainModel, mainEffort, budgets));
   }



   /*******************************************************************************
    ** Whether the kill switch is on right now (fresh read; errors count as
    ** "off" only if the policy table is unreadable - logged).
    *******************************************************************************/
   public boolean isKillSwitchOn()
   {
      try
      {
         return (current(true).killSwitch());
      }
      catch(QException e)
      {
         LOG.warn("Could not read the assistant policy; treating the kill switch as off", e);
         return (false);
      }
   }



   /*******************************************************************************
    ** Check a user against the budgets, counting `pendingTokens` /
    ** `pendingCostMicros` of a running turn not yet recorded.
    *******************************************************************************/
   public BudgetStatus checkBudget(EffectivePolicy policy, String userId, long pendingTokens, long pendingCostMicros) throws QException
   {
      AssistantBudgets b     = policy.budgets();
      LocalDate        today = LocalDate.now(clock.withZone(ZoneOffset.UTC));

      if(b.getUserDailyTokensSoft() == null && b.getUserDailyTokensHard() == null && b.getUserDailyCostMicrosSoft() == null && b.getUserDailyCostMicrosHard() == null
         && b.getInstanceMonthlyTokensSoft() == null && b.getInstanceMonthlyTokensHard() == null && b.getInstanceMonthlyCostMicrosSoft() == null && b.getInstanceMonthlyCostMicrosHard() == null)
      {
         return (BudgetStatus.ok());
      }

      AssistantUsageDaily user       = store.getUserUsage(userId, today);
      long                userTokens = pendingTokens + (user == null ? 0 : billable(user));
      long                userCost   = pendingCostMicros + (user == null ? 0 : nz(user.getCostMicros()));

      long instanceTokens = pendingTokens;
      long instanceCost   = pendingCostMicros;
      for(AssistantUsageDaily row : store.listInstanceUsage(today.withDayOfMonth(1), today))
      {
         instanceTokens += billable(row);
         instanceCost += nz(row.getCostMicros());
      }

      if(over(userTokens, b.getUserDailyTokensHard()) || over(userCost, b.getUserDailyCostMicrosHard()))
      {
         return (new BudgetStatus(true, true, "You have reached your daily assistant usage limit. It resets at midnight UTC."));
      }
      if(over(instanceTokens, b.getInstanceMonthlyTokensHard()) || over(instanceCost, b.getInstanceMonthlyCostMicrosHard()))
      {
         return (new BudgetStatus(true, true, "The assistant has reached this application's monthly usage limit. Ask an administrator."));
      }
      if(over(userTokens, b.getUserDailyTokensSoft()) || over(userCost, b.getUserDailyCostMicrosSoft()))
      {
         return (new BudgetStatus(false, true, "You are close to your daily assistant usage limit."));
      }
      if(over(instanceTokens, b.getInstanceMonthlyTokensSoft()) || over(instanceCost, b.getInstanceMonthlyCostMicrosSoft()))
      {
         return (new BudgetStatus(false, true, "The assistant is close to this application's monthly usage limit."));
      }
      return (BudgetStatus.ok());
   }



   /*******************************************************************************
    ** Drop the cached policy row.
    *******************************************************************************/
   public void invalidate()
   {
      cached = null;
      cachedAt = null;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private AssistantPolicy loadRow(boolean fresh) throws QException
   {
      Instant now = clock.instant();
      if(!fresh && cachedAt != null && cachedAt.plusSeconds(config.getPolicyCacheSeconds()).isAfter(now))
      {
         return (cached);
      }
      AssistantPolicy row = store.loadPolicy();
      cached = row;
      cachedAt = now;
      return (row);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static boolean over(long value, Long limit)
   {
      return (limit != null && value >= limit);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static long billable(AssistantUsageDaily row)
   {
      return (nz(row.getInputTokens()) + nz(row.getCacheWriteTokens()) + nz(row.getOutputTokens()));
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
   private static Long pick(Long override, Long base)
   {
      return (override != null ? override : base);
   }

}
