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


import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.model.AssistantRagSource;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;


/*******************************************************************************
 ** Validate source configuration; invalidate vectors whenever their meaning changes.
 *******************************************************************************/
public class AssistantRagSourceCustomizer implements TableCustomizerInterface
{
   private static final List<String> SEMANTIC_FIELDS = List.of("tableName", "fieldNames", "filterJson", "embeddingModelId", "chunkChars", "chunkOverlapChars");



   /*******************************************************************************
    ** Validate merged edits, not partial update payloads. Status writes remain
    ** possible even after a host removes a table or disables a model.
    *******************************************************************************/
   @Override
   public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      Optional<Map<Serializable, QRecord>> old = oldRecordListToMap("id", oldRecordList);
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      for(QRecord record : records)
      {
         if(record.getValues().keySet().stream().allMatch(name -> name.equals("id") || name.startsWith("lastIndex") || name.startsWith("indexed") || name.endsWith("Date")))
         {
            continue;
         }
         QRecord merged = new QRecord();
         old.map(m -> m.get(record.getValue("id"))).ifPresent(o -> merged.setValues(new LinkedHashMap<>(o.getValues())));
         record.getValues().forEach(merged::setValue);
         for(var entry : Map.<String, Serializable>of("enabled", true, "chunkChars", 1500, "chunkOverlapChars", 200).entrySet())
         {
            if(merged.getValue(entry.getKey()) == null)
            {
               record.setValue(entry.getKey(), entry.getValue());
               merged.setValue(entry.getKey(), entry.getValue());
            }
         }
         if(runtime != null)
         {
            for(String error : runtime.getRagService().validate(new AssistantRagSource(merged)))
            {
               record.addError(new BadInputStatusMessage(error));
            }
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** Old vectors cannot survive a model, filter or chunk-layout change.
    *******************************************************************************/
   @Override
   public List<QRecord> postInsertOrUpdate(AbstractActionInput input, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      Optional<Map<Serializable, QRecord>> old = oldRecordListToMap("id", oldRecordList);
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      if(runtime != null && old.isPresent())
      {
         for(QRecord record : records)
         {
            QRecord previous = old.get().get(record.getValue("id"));
            if(previous != null && SEMANTIC_FIELDS.stream().anyMatch(name -> record.getValues().containsKey(name) && !Objects.equals(previous.getValue(name), record.getValue(name))))
            {
               runtime.getRagService().clear(new AssistantRagSource(previous));
            }
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** Deleted sources no longer have searchable vectors.
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput input, List<QRecord> records) throws QException
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      if(runtime != null)
      {
         for(QRecord record : records)
         {
            runtime.getRagService().remove(new AssistantRagSource(record));
         }
      }
      return (records);
   }
}
