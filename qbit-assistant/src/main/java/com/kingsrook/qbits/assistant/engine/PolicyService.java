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
import java.util.LinkedHashSet;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantBudgets;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.session.QSession;
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
   public record EffectivePolicy(boolean killSwitch, String killSwitchReason, String mainModel, String mainEffort, AssistantBudgets budgets, ToolPolicy tools)
   {
      /*******************************************************************************
       ** Without tool settings (the defaults).
       *******************************************************************************/
      public EffectivePolicy(boolean killSwitch, String killSwitchReason, String mainModel, String mainEffort, AssistantBudgets budgets)
      {
         this(killSwitch, killSwitchReason, mainModel, mainEffort, budgets, ToolPolicy.defaults());
      }
   }



   /*******************************************************************************
    ** The policy row's tool settings, with their defaults.
    **
    ** @param writesRequireConfirmation every write waits for the user's
    **        confirmation card (default true)
    ** @param allowedTools tool names allowed; empty allows every tool
    ** @param maxRowsPerCall most rows one tool call returns to the model
    ** @param redactPii redact secrets and personal identifiers from tool
    **        results (default true)
    *******************************************************************************/
   public record ToolPolicy(boolean writesRequireConfirmation, Set<String> allowedTools, int maxRowsPerCall, boolean redactPii,
                            boolean writeModeAllowed, String writeModePermission, boolean autoConfirmAllowed, boolean writeMode, boolean autoConfirm)
   {
      /*******************************************************************************
       ** Write mode on, no auto-confirm, write mode allowed for everyone (tests
       ** and hosts that build a policy by hand).
       *******************************************************************************/
      public ToolPolicy(boolean writesRequireConfirmation, Set<String> allowedTools, int maxRowsPerCall, boolean redactPii)
      {
         this(writesRequireConfirmation, allowedTools, maxRowsPerCall, redactPii, true, null, false, true, false);
      }



      /*******************************************************************************
       ** This policy for a conversation in Write (true) or Read-only (false) mode.
       *******************************************************************************/
      public ToolPolicy withWriteMode(boolean writeMode)
      {
         return (new ToolPolicy(writesRequireConfirmation, allowedTools, maxRowsPerCall, redactPii, writeModeAllowed, writeModePermission, autoConfirmAllowed, writeMode, autoConfirm));
      }



      /*******************************************************************************
       ** This policy with the user's auto-confirm (only effective when the policy
       ** allows it).
       *******************************************************************************/
      public ToolPolicy withAutoConfirm(boolean autoConfirm)
      {
         return (new ToolPolicy(writesRequireConfirmation, allowedTools, maxRowsPerCall, redactPii, writeModeAllowed, writeModePermission, autoConfirmAllowed, writeMode,
            autoConfirm && autoConfirmAllowed));
      }



      /*******************************************************************************
       ** Whether this user may switch a conversation to Write mode (the policy
       ** allows it, and they hold the write-mode permission when one is set).
       *******************************************************************************/
      public boolean writeModeAvailableTo(QSession session)
      {
         return (writeModeAllowed && (!StringUtils.hasContent(writeModePermission) || (session != null && session.hasPermission(writeModePermission.strip()))));
      }
      public static final int DEFAULT_MAX_ROWS_PER_CALL = 50;
      public static final int MAX_ROWS_PER_CALL_CEILING = 200;



      /*******************************************************************************
       ** The defaults.
       *******************************************************************************/
      public static ToolPolicy defaults()
      {
         return (new ToolPolicy(true, Set.of(), DEFAULT_MAX_ROWS_PER_CALL, true, true, null, false, false, false));
      }



      /*******************************************************************************
       ** Whether a tool is allowed by name.
       *******************************************************************************/
      public boolean allows(String toolName)
      {
         return (allowedTools == null || allowedTools.isEmpty() || allowedTools.contains(toolName));
      }



      /*******************************************************************************
       ** From a policy row (null: the defaults).
       *******************************************************************************/
      public static ToolPolicy of(AssistantPolicy row)
      {
         if(row == null)
         {
            return (defaults());
         }
         Set<String> allowed = new LinkedHashSet<>();
         if(StringUtils.hasContent(row.getAllowedTools()))
         {
            for(String name : row.getAllowedTools().split("[,\\s]+"))
            {
               if(StringUtils.hasContent(name))
               {
                  allowed.add(name.trim());
               }
            }
         }
         int maxRows = row.getMaxRowsPerCall() == null || row.getMaxRowsPerCall() < 1 ? DEFAULT_MAX_ROWS_PER_CALL : Math.min(MAX_ROWS_PER_CALL_CEILING, row.getMaxRowsPerCall());
         return (new ToolPolicy(!Boolean.FALSE.equals(row.getWritesRequireConfirmation()), allowed, maxRows, !Boolean.FALSE.equals(row.getRedactPii()),
            !Boolean.FALSE.equals(row.getWriteModeAllowed()), row.getWriteModePermission(), Boolean.TRUE.equals(row.getAutoConfirmAllowed()), false, false));
      }
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

      return (new EffectivePolicy(row != null && Boolean.TRUE.equals(row.getKillSwitch()), row == null ? null : row.getKillSwitchReason(), mainModel, mainEffort, budgets, ToolPolicy.of(row)));
   }



   /*******************************************************************************
    ** Whether the kill switch is on right now (fresh read). Fails CLOSED: if
    ** the policy cannot be read, the switch counts as on (logged).
    *******************************************************************************/
   public boolean isKillSwitchOn()
   {
      try
      {
         return (current(true).killSwitch());
      }
      catch(Exception e)
      {
         LOG.warn("Could not read the assistant policy; treating the kill switch as ON (fail closed)", e);
         return (true);
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
