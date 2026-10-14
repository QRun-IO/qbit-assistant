/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.assistant.metadata;


import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.model.AssistantScheduledTask;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.scheduled.ScheduledTasks;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.QueryOrGetInputInterface;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;


/*******************************************************************************
 ** Validate task edits and invalidate approvals before changed text is saved.
 *******************************************************************************/
public class AssistantScheduledTaskCustomizer implements TableCustomizerInterface
{
   /*******************************************************************************
    ** Internal status writes bypass this customizer only inside AssistantStore.
    *******************************************************************************/
   @Override
   public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      Optional<Map<Serializable, QRecord>> old = oldRecordListToMap("id", oldRecordList);
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      for(QRecord record : records)
      {
         record.getValues().remove("credentialStatus");
         if(input instanceof UpdateInput update
            && update.hasFlag(AssistantStore.ScheduledWrite.INTERNAL))
         {
            continue;
         }
         QRecord previous = old.map(rows -> rows.get(record.getValue("id"))).orElse(null);
         for(String field : List.of("writeModeApprovedBy", "writeModeApprovedAt", "writeModeApprovalHash", "nextRunAt", "lastRunAt", "lastRunStatus", "lastRunError"))
         {
            record.getValues().remove(field);
         }
         QRecord merged = new QRecord();
         merged.setValues(previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous.getValues()));
         record.getValues().forEach(merged::setValue);
         for(var entry : Map.<String, Serializable>of("enabled", false, "timezone", "UTC", "outputMode", "RECORD", "writeModeRequested", false).entrySet())
         {
            if(merged.getValue(entry.getKey()) == null)
            {
               merged.setValue(entry.getKey(), entry.getValue());
               record.setValue(entry.getKey(), entry.getValue());
            }
         }
         if(previous == null || ScheduledTasks.APPROVAL_FIELDS.stream().anyMatch(field -> !Objects.equals(previous.getValue(field), merged.getValue(field))))
         {
            record.setValue("writeModeApprovedBy", null);
            record.setValue("writeModeApprovedAt", null);
            record.setValue("writeModeApprovalHash", null);
         }
         try
         {
            AssistantScheduledTask task = new AssistantScheduledTask(merged);
            ScheduledTasks.checkReference(runtime.getConfig(), task.getCredentialEnvName());
            if(task.getPrompt() == null || task.getPrompt().isBlank() || task.getPrompt().length() > 4000)
            {
               throw (new QException("The prompt must contain 1 to 4000 characters."));
            }
            if(!List.of("RECORD", "NOTIFICATION", "BOTH").contains(task.getOutputMode()))
            {
               throw (new QException("Output mode must be RECORD, NOTIFICATION or BOTH."));
            }
            if(task.getMaxCostUsdPerRun() != null && task.getMaxCostUsdPerRun().signum() < 0)
            {
               throw (new QException("The per-run cost cap cannot be negative."));
            }
            var next = ScheduledTasks.next(task, runtime.getClock().instant());
            if(previous == null || List.of("enabled", "cronExpression", "timezone").stream().anyMatch(field -> !Objects.equals(previous.getValue(field), merged.getValue(field))))
            {
               record.setValue("nextRunAt", Boolean.TRUE.equals(task.getEnabled()) ? next : null);
            }
         }
         catch(QException e)
         {
            record.addError(new BadInputStatusMessage(e.getMessage()));
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** Computed status is never persisted.
    *******************************************************************************/
   @Override
   public List<QRecord> postQuery(QueryOrGetInputInterface input, List<QRecord> records) throws QException
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      for(QRecord record : records)
      {
         record.setValue("credentialStatus", ScheduledTasks.credentialStatus(runtime.getConfig(), new AssistantScheduledTask(record)));
      }
      return (records);
   }
}
