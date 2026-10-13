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

package com.kingsrook.qbits.assistant.metadata;


import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantBudgetOverride;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/*******************************************************************************
 ** Validates budgets and attributes admin overrides to the current session.
 *******************************************************************************/
public class AssistantBudgetCustomizer implements TableCustomizerInterface
{
   /*******************************************************************************
    ** Validate the merged row so partial updates cannot bypass invariants.
    *******************************************************************************/
   @Override
   public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      Optional<Map<Serializable, QRecord>> old = oldRecordListToMap("id", oldRecordList);
      for(QRecord record : records)
      {
         QRecord merged = new QRecord();
         old.map(m -> m.get(record.getValue("id"))).ifPresent(o -> merged.setValues(new LinkedHashMap<>(o.getValues())));
         record.getValues().forEach(merged::setValue);
         if(merged.getValue("budgetId") != null)
         {
            checkOverride(record, merged, input instanceof InsertInput);
         }
         else
         {
            checkBudget(record, merged);
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** Defaults and scope-specific validation.
    *******************************************************************************/
   private static void checkBudget(QRecord record, QRecord merged) throws QException
   {
      defaults(record, merged, "enabled", true);
      defaults(record, merged, "warnPercent", 80);
      defaults(record, merged, "hardCeilingPercent", 120);
      defaults(record, merged, "atLimitAction", "DOWNGRADE");
      String scope = merged.getValueString("scopeType");
      String value = merged.getValueString("scopeValue");
      choice(record, merged, "scopeType", List.of("INSTANCE", "USER", "ROLE", "PROVIDER", "MODEL"));
      choice(record, merged, "period", List.of("DAY", "MONTH"));
      choice(record, merged, "metric", List.of("COST_USD", "TOKENS"));
      choice(record, merged, "atLimitAction", List.of("DOWNGRADE", "STOP"));
      if("INSTANCE".equals(scope) ? value != null && !value.isBlank() : value == null || value.isBlank())
      {
         error(record, "Scope value must be empty for INSTANCE and present for every other scope.");
      }
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      if("PROVIDER".equals(scope) || "MODEL".equals(scope))
      {
         try
         {
            Integer id = Integer.valueOf(value);
            boolean exists = "PROVIDER".equals(scope) ? runtime.getStore().getProvider(id) != null : runtime.getStore().listModels().stream().anyMatch(m -> id.equals(m.getId()));
            if(!exists)
            {
               error(record, "Scope value must reference an existing provider or model registry row.");
            }
         }
         catch(NumberFormatException e)
         {
            error(record, "Scope value must be a numeric record id.");
         }
      }
      Integer modelId = merged.getValueInteger("downgradeModelId");
      if(modelId != null && runtime.getStore().listModels().stream().noneMatch(m -> modelId.equals(m.getId())))
      {
         error(record, "Downgrade model must reference an existing model registry row.");
      }
      BigDecimal limit = merged.getValueBigDecimal("limitValue");
      if(limit == null || limit.signum() <= 0 || ("TOKENS".equals(merged.getValueString("metric")) && limit.stripTrailingZeros().scale() > 0))
      {
         error(record, "Limit must be positive; token limits must be whole numbers.");
      }
      int warn = merged.getValueInteger("warnPercent");
      if(warn < 0 || warn >= 100 || merged.getValueInteger("hardCeilingPercent") < 100)
      {
         error(record, "Warning percent must be 0 through 99 and hard ceiling must be at least 100.");
      }
   }



   /*******************************************************************************
    ** Only admins grant overrides; never accept a supplied grantor.
    *******************************************************************************/
   private static void checkOverride(QRecord record, QRecord merged, boolean insert) throws QException
   {
      if(!QContext.getQSession().hasPermission(AssistantQBitConfig.ADMIN_PERMISSION))
      {
         error(record, "Only assistant.admin may grant budget overrides.");
      }
      if(insert)
      {
         record.setValue("grantedBy", AssistantSecurity.userId(QContext.getQSession()));
      }
      else
      {
         record.getValues().remove("grantedBy");
      }
      AssistantBudgetOverride override = new AssistantBudgetOverride(merged);
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      AssistantBudget budget = runtime.getStore().budget(override.getBudgetId());
      if(budget == null)
      {
         error(record, "Budget must reference an existing budget.");
      }
      if(override.getReason() == null || override.getReason().isBlank())
      {
         error(record, "A reason is required.");
      }
      if(override.getExpiresAt() == null)
      {
         error(record, "An expiry is required.");
      }
      if((override.getExtraValue() == null) == (override.getUnlimitedUntil() == null))
      {
         error(record, "Set exactly one of extra value or unlimited until.");
      }
      if(override.getExtraValue() != null && (override.getExtraValue().signum() <= 0 || (budget != null && "TOKENS".equals(budget.getMetric()) && override.getExtraValue().stripTrailingZeros().scale() > 0)))
      {
         error(record, "Extra value must be positive; token values must be whole numbers.");
      }
      if(override.getUnlimitedUntil() != null && override.getExpiresAt() != null && override.getUnlimitedUntil().isAfter(override.getExpiresAt()))
      {
         error(record, "Unlimited until cannot be after expiry.");
      }
   }



   /*******************************************************************************
    ** Fill an omitted setting.
    *******************************************************************************/
   private static void defaults(QRecord record, QRecord merged, String field, Serializable value)
   {
      if(merged.getValue(field) == null)
      {
         merged.setValue(field, value);
         record.setValue(field, value);
      }
   }



   /*******************************************************************************
    ** Validate a possible value even for non-UI callers.
    *******************************************************************************/
   private static void choice(QRecord record, QRecord merged, String field, List<String> choices)
   {
      if(merged.getValueString(field) == null || !choices.contains(merged.getValueString(field)))
      {
         error(record, "Invalid " + field + ".");
      }
   }



   /*******************************************************************************
    ** Safe validation message.
    *******************************************************************************/
   private static void error(QRecord record, String message)
   {
      record.addError(new BadInputStatusMessage(message));
   }
}
