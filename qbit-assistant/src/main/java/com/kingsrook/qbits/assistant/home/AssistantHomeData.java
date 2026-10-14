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

package com.kingsrook.qbits.assistant.home;


import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.engine.BudgetService;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.engine.UsageRecorder;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantBudgetOverride;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/*******************************************************************************
 ** Everything the Assistant home shows, computed from the qbit's own tables
 ** (assistant_provider, assistant_usage, assistant_conversation,
 ** assistant_tool_call, assistant_policy) as the viewing admin. Days are UTC.
 ** No sample data: an empty table gives an empty section.
 *******************************************************************************/
public class AssistantHomeData
{
   public static final int DAYS        = 30;
   public static final int TOP         = 10;
   public static final int RECENT      = 10;

   private static final Set<String> TOOL_FAILURES = Set.of("DENIED", "NOT_FOUND", "INVALID_INPUT", "ERROR");

   private final List<ProviderHealth>     providers     = new ArrayList<>();
   private final Map<LocalDate, DayUsage> days          = new LinkedHashMap<>();
   private final List<Totals>             byUser        = new ArrayList<>();
   private final List<Totals>             byModel       = new ArrayList<>();
   private final List<ToolTotals>         byTool        = new ArrayList<>();
   private final List<QRecord>            failedCalls   = new ArrayList<>();
   private final List<QRecord>            failedTools   = new ArrayList<>();
   private final List<QRecord>            deniedTools   = new ArrayList<>();

   private final List<BudgetService.Snapshot> budgets = new ArrayList<>();
   private final List<Totals> byProvider = new ArrayList<>();

   private Long fallbackCount = 0L;
   private Boolean hipaaMode;
   private Integer hipaaRetentionDays;
   private LocalDate today;
   private long      monthCostMicros;
   private long      monthRequests;
   private Long      instanceMonthlyCapMicros;
   private boolean   killSwitch;
   private int       calls7d;
   private int       errors7d;
   private boolean   truncated;



   /*******************************************************************************
    ** One provider's health.
    *******************************************************************************/
   public record ProviderHealth(AssistantProvider row, String typeLabel, String keyStatus, boolean keyPresent, List<String> problems, boolean active, long monthCostMicros, Long capMicros, String kind, String location, long enabledModels)
   {
   }



   /*******************************************************************************
    ** One day's usage.
    *******************************************************************************/
   public static class DayUsage
   {
      public int  conversations;
      public int  requests;
      public int  errors;
      public long tokensIn;
      public long tokensOut;
      public long costMicros;
   }



   /*******************************************************************************
    ** Totals for one key (a user, a model).
    *******************************************************************************/
   public static class Totals
   {
      public String key;
      public int    requests;
      public long   tokensIn;
      public long   tokensOut;
      public long   costMicros;
   }



   /*******************************************************************************
    ** Totals for one tool.
    *******************************************************************************/
   public static class ToolTotals
   {
      public String name;
      public int    calls;
      public int    failures;
      public long   totalMs;
   }



   /*******************************************************************************
    ** Load it all for the session in QContext.
    *******************************************************************************/
   public static AssistantHomeData load(AssistantRuntime runtime) throws QException
   {
      AssistantHomeData data = new AssistantHomeData();
      data.hipaaMode = runtime.getConfig().getHipaaMode();
      data.hipaaRetentionDays = runtime.getConfig().getHipaaRetentionDays();
      for(AssistantBudget budget : runtime.getStore().budgets())
      {
         BudgetService.Snapshot snapshot = runtime.getBudgetService().snapshot(budget, AssistantSecurity.userId(QContext.getQSession()));
         List<AssistantBudgetOverride> overrides = runtime.getStore().budgetOverrides(budget.getId()).stream()
            .filter(o -> o.getExpiresAt() != null && o.getExpiresAt().isAfter(runtime.getClock().instant()))
            .filter(o -> o.getUnlimitedUntil() == null || o.getUnlimitedUntil().isAfter(runtime.getClock().instant())).toList();
         data.budgets.add(new BudgetService.Snapshot(budget, snapshot.scopeKey(), snapshot.periodStart(), snapshot.used(), snapshot.limit(), snapshot.percent(), snapshot.state(), overrides));
      }
      data.today = LocalDate.now(runtime.getClock().withZone(ZoneOffset.UTC));
      LocalDate firstDay   = data.today.minusDays(DAYS - 1);
      LocalDate monthStart = data.today.withDayOfMonth(1);
      LocalDate from       = firstDay.isBefore(monthStart) ? firstDay : monthStart;
      Instant   fromAt     = from.atStartOfDay().toInstant(ZoneOffset.UTC);
      Instant   monthAt    = monthStart.atStartOfDay().toInstant(ZoneOffset.UTC);
      Instant   weekAt     = runtime.getClock().instant().minusSeconds(7L * 24 * 3600);
      int       max        = runtime.getConfig().getHomeMaxRows();

      for(LocalDate d = firstDay; !d.isAfter(data.today); d = d.plusDays(1))
      {
         data.days.put(d, new DayUsage());
      }

      PolicyService.EffectivePolicy policy = runtime.getPolicyService().current(false);
      data.killSwitch = policy.killSwitch();
      data.instanceMonthlyCapMicros = policy.budgets().getInstanceMonthlyCostMicrosHard();

      ////////////////////////////////////////
      // usage rows: by day, user, model    //
      ////////////////////////////////////////
      List<QRecord> usage = runtime.getStore().queryRecords(AssistantUsage.TABLE_NAME, new QQueryFilter()
         .withCriteria("createDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS, fromAt)
         .withOrderBy(new QFilterOrderBy("id", false)), max);
      data.truncated |= usage.size() >= max;

      Map<String, Totals> providerTotals = new LinkedHashMap<>();
      Map<String, Totals> users          = new LinkedHashMap<>();
      Map<String, Totals> models         = new LinkedHashMap<>();
      Map<Integer, Long>  providerMonth  = new LinkedHashMap<>();
      for(QRecord row : usage)
      {
         Instant at   = row.getValueInstant("createDate");
         long    in   = nz(row.getValueLong("inputTokens")) + nz(row.getValueLong("cacheReadTokens")) + nz(row.getValueLong("cacheWriteTokens"));
         long    out  = nz(row.getValueLong("outputTokens"));
         long    cost = nz(row.getValueLong("costMicros"));
         boolean err  = UsageRecorder.STATUS_ERROR.equals(row.getValueString("status"));
         boolean turn = UsageRecorder.PURPOSE_TURN.equals(row.getValueString("purpose"));
         if(at == null)
         {
            continue;
         }

         DayUsage day = data.days.get(LocalDate.ofInstant(at, ZoneOffset.UTC));
         if(day != null)
         {
            day.requests++;
            day.errors += err ? 1 : 0;
            day.tokensIn += in;
            day.tokensOut += out;
            day.costMicros += cost;
            add(users, row.getValueString("userId") == null ? "(none)" : row.getValueString("userId"), in, out, cost);
            add(models, row.getValueString("model") == null ? "(unknown)" : row.getValueString("model"), in, out, cost);
            add(providerTotals, row.getValueString("providerId") == null ? "Code-registered provider" : "Provider #" + row.getValueString("providerId"), in, out, cost);
         }

         if(!at.isBefore(weekAt) && turn)
         {
            data.calls7d++;
            data.errors7d += err ? 1 : 0;
         }

         if(err && data.failedCalls.size() < RECENT)
         {
            data.failedCalls.add(row);
         }

         if(!at.isBefore(monthAt))
         {
            data.monthCostMicros += cost;
            data.monthRequests++;
            Integer providerId = row.getValueInteger("providerId");
            if(providerId != null)
            {
               providerMonth.merge(providerId, cost, Long::sum);
            }
         }
      }
      data.byProvider.addAll(top(providerTotals));
      data.byUser.addAll(top(users));
      data.byModel.addAll(top(models));

      ////////////////////////////////////////
      // conversations started per day      //
      ////////////////////////////////////////
      Instant dayOneAt = firstDay.atStartOfDay().toInstant(ZoneOffset.UTC);
      List<QRecord> routedTurns = runtime.getStore().queryRecords(AssistantTurn.TABLE_NAME, new QQueryFilter()
         .withCriteria("startedAt", QCriteriaOperator.GREATER_THAN_OR_EQUALS, dayOneAt), max);
      data.truncated |= routedTurns.size() >= max;
      for(QRecord row : routedTurns)
      {
         if(row.getValueInteger("fallbackCount") != null)
         {
            data.fallbackCount += row.getValueInteger("fallbackCount");
         }
      }
      List<QRecord> conversations = runtime.getStore().queryRecords(AssistantConversation.TABLE_NAME, new QQueryFilter()
         .withCriteria("createDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS, dayOneAt), max);
      data.truncated |= conversations.size() >= max;
      for(QRecord row : conversations)
      {
         Instant at = row.getValueInstant("createDate");
         DayUsage day = at == null ? null : data.days.get(LocalDate.ofInstant(at, ZoneOffset.UTC));
         if(day != null)
         {
            day.conversations++;
         }
      }

      ////////////////////////////////////////
      // tools                              //
      ////////////////////////////////////////
      List<QRecord> tools = runtime.getStore().queryRecords(AssistantToolCall.TABLE_NAME, new QQueryFilter()
         .withCriteria("createDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS, dayOneAt)
         .withOrderBy(new QFilterOrderBy("id", false)), max);
      data.truncated |= tools.size() >= max;
      Map<String, ToolTotals> toolMap = new LinkedHashMap<>();
      for(QRecord row : tools)
      {
         String     name   = row.getValueString("toolName") == null ? "(unknown)" : row.getValueString("toolName");
         ToolTotals totals = toolMap.computeIfAbsent(name, k ->
         {
            ToolTotals t = new ToolTotals();
            t.name = k;
            return (t);
         });
         totals.calls++;
         totals.totalMs += nz(row.getValueLong("durationMs"));
         if(TOOL_FAILURES.contains(row.getValueString("outcome")))
         {
            totals.failures++;
            if("DENIED".equals(row.getValueString("outcome")) && data.deniedTools.size() < RECENT)
            {
               data.deniedTools.add(row);
            }
            if(data.failedTools.size() < RECENT)
            {
               data.failedTools.add(row);
            }
         }
      }
      toolMap.values().stream()
         .sorted(Comparator.comparingInt((ToolTotals t) -> t.calls).reversed().thenComparing(t -> t.name))
         .limit(TOP)
         .forEach(data.byTool::add);

      ////////////////////////////////////////
      // providers                          //
      ////////////////////////////////////////
      ProviderService   service = runtime.getProviderService();
      AssistantProvider active  = service.activeRow();
      for(AssistantProvider row : runtime.getStore().listProviders())
      {
         String status = service.keyStatus(row);
         AssistantProviderType type = row.getProviderType() == null ? null : runtime.getConfig().getProviderTypes().get(row.getProviderType());
         data.providers.add(new ProviderHealth(row, type == null ? row.getProviderType() : type.getLabel(), status, (status.startsWith("Set") || status.startsWith("Not needed")), service.validate(row), active != null && active.getId().equals(row.getId()),
            providerMonth.getOrDefault(row.getId(), 0L), ProviderService.capMicros(row), service.kind(row), service.location(row), service.models(row.getId()).stream().filter(m -> Boolean.TRUE.equals(m.getEnabled())).count()));
      }
      return (data);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void add(Map<String, Totals> map, String key, long in, long out, long cost)
   {
      Totals totals = map.computeIfAbsent(key, k ->
      {
         Totals t = new Totals();
         t.key = k;
         return (t);
      });
      totals.requests++;
      totals.tokensIn += in;
      totals.tokensOut += out;
      totals.costMicros += cost;
   }



   /*******************************************************************************
    ** The top entries by cost, then requests.
    *******************************************************************************/
   private static List<Totals> top(Map<String, Totals> map)
   {
      return (map.values().stream()
         .sorted(Comparator.comparingLong((Totals t) -> t.costMicros).reversed().thenComparing(Comparator.comparingInt((Totals t) -> t.requests).reversed()).thenComparing(t -> t.key))
         .limit(TOP)
         .toList());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static long nz(Long value)
   {
      return (value == null ? 0 : value);
   }



   /*******************************************************************************
    ** The error rate of turn model calls in the last 7 days (0-100), or null.
    *******************************************************************************/
   public Integer errorRatePercent7d()
   {
      return (calls7d == 0 ? null : (int) Math.round(errors7d * 100.0 / calls7d));
   }



   /*******************************************************************************
    ** Getter for providers
    *******************************************************************************/
   public List<ProviderHealth> getProviders()
   {
      return (providers);
   }



   /*******************************************************************************
    ** Getter for days (oldest first)
    *******************************************************************************/
   public Map<LocalDate, DayUsage> getDays()
   {
      return (days);
   }



   /*******************************************************************************
    ** Getter for byUser
    *******************************************************************************/
   public List<Totals> getByUser()
   {
      return (byUser);
   }



   /*******************************************************************************
    ** Getter for byModel
    *******************************************************************************/
   public List<Totals> getByModel()
   {
      return (byModel);
   }



   /*******************************************************************************
    ** Getter for byTool
    *******************************************************************************/
   public List<ToolTotals> getByTool()
   {
      return (byTool);
   }



   /*******************************************************************************
    ** Getter for failedCalls (newest first)
    *******************************************************************************/
   public List<QRecord> getFailedCalls()
   {
      return (failedCalls);
   }



   /*******************************************************************************
    ** Getter for deniedTools: the newest tool calls the UI API, data access or
    ** the policy refused
    *******************************************************************************/
   public List<QRecord> getDeniedTools()
   {
      return (deniedTools);
   }



   /*******************************************************************************
    ** Getter for failedTools (newest first)
    *******************************************************************************/
   public List<QRecord> getFailedTools()
   {
      return (failedTools);
   }



   /*******************************************************************************
    ** Getter for today
    *******************************************************************************/
   public LocalDate getToday()
   {
      return (today);
   }



   /*******************************************************************************
    ** Getter for monthCostMicros
    *******************************************************************************/
   public long getMonthCostMicros()
   {
      return (monthCostMicros);
   }



   /*******************************************************************************
    ** Getter for monthRequests
    *******************************************************************************/
   public long getMonthRequests()
   {
      return (monthRequests);
   }



   /*******************************************************************************
    ** Getter for instanceMonthlyCapMicros
    *******************************************************************************/
   public Long getInstanceMonthlyCapMicros()
   {
      return (instanceMonthlyCapMicros);
   }



   /*******************************************************************************
    ** Getter for killSwitch
    *******************************************************************************/
   public boolean isKillSwitch()
   {
      return (killSwitch);
   }



   /*******************************************************************************
    ** Getter for calls7d
    *******************************************************************************/
   public int getCalls7d()
   {
      return (calls7d);
   }



   /*******************************************************************************
    ** Getter for errors7d
    *******************************************************************************/
   public int getErrors7d()
   {
      return (errors7d);
   }



   /*******************************************************************************
    ** Getter for truncated (a table had more rows than the home reads)
    *******************************************************************************/
   public boolean isTruncated()
   {
      return (truncated);
   }



   /*******************************************************************************
    ** Read-only instance HIPAA status.
    *******************************************************************************/
   public Boolean getHipaaMode()
   {
      return (hipaaMode);
   }



   /*******************************************************************************
    ** Configured PHI retention in days.
    *******************************************************************************/
   public Integer getHipaaRetentionDays()
   {
      return (hipaaRetentionDays);
   }



   /*******************************************************************************
    ** Current enabled budgets and active overrides.
    *******************************************************************************/
   public List<BudgetService.Snapshot> getBudgets()
   {
      return (budgets);
   }



   /*******************************************************************************
    ** Provider usage over the same 30-day window as users and models.
    *******************************************************************************/
   public List<Totals> getByProvider()
   {
      return (byProvider);
   }



   /*******************************************************************************
    ** Provider switches in the displayed period, including recovered failures.
    *******************************************************************************/
   public Long getFallbackCount()
   {
      return (fallbackCount);
   }
}
