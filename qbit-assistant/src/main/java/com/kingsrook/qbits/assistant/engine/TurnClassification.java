/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2026.  Kingsrook, LLC
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

package com.kingsrook.qbits.assistant.engine;


import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.classification.ClassificationSource;
import com.kingsrook.qbits.assistant.classification.Classifications;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qqq.backend.core.actions.ai.AiRecordRef;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolResult;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** Monotonic classification of one turn, including inherited conversation history. Contains table names only.
 *******************************************************************************/
public class TurnClassification
{
   private static final Set<String> METADATA_TOOLS = Set.of("list_tables", "describe_table", "navigate", "search_help", "present_results");
   private final ClassificationSource source;
   private final Set<String> tables = new LinkedHashSet<>();
   private DataClassification classification;



   /*******************************************************************************
    ** Start with history, then the page table or complete record.
    *******************************************************************************/
   public TurnClassification(ClassificationSource source, AssistantConversation conversation, Map<String, Object> pageContext)
   {
      this.source = source;
      classification = Classifications.stored(conversation.getMaxClassification());
      if(pageContext != null && pageContext.get("tableName") instanceof String table)
      {
         touch(table, pageContext.get("recordId") == null ? source.ofTable(table) : source.highest(table));
      }
   }



   /*******************************************************************************
    ** Observe before appending results. Metadata reads no records; presentation
    ** only reuses values whose originating data tools were already classified.
    *******************************************************************************/
   public void observe(ModelPart use, AiToolResult result)
   {
      if(METADATA_TOOLS.contains(use.getToolName()))
      {
         return;
      }
      Map<String, Object> input = use.getInput() == null ? Map.of() : use.getInput();
      boolean crossTable = "search_knowledge".equals(use.getToolName()) || "search_records".equals(use.getToolName()) || "get_record_history".equals(use.getToolName());
      String table = result.getTableName() != null ? result.getTableName()
         : (input.get("table") instanceof String name ? name : (input.get("tableName") instanceof String name ? name : null));
      if(crossTable)
      {
         Set<String> touched = new LinkedHashSet<>();
         if(table != null)
         {
            touched.add(table);
         }
         if(result.getTableName() != null)
         {
            touched.add(result.getTableName());
         }
         for(AiRecordRef ref : result.getRecordRefs() == null ? List.<AiRecordRef>of() : result.getRecordRefs())
         {
            if(ref.getTableName() != null)
            {
               touched.add(ref.getTableName());
            }
         }
         collectTables(result.getRender(), touched);
         collectResultTables(result.getModelContent(), touched);
         if("search_knowledge".equals(use.getToolName()) && touched.isEmpty())
         {
            return;
         }
         if(touched.isEmpty() || touched.stream().anyMatch(name -> QContext.getQInstance().getTable(name) == null))
         {
            touchUnknown();
         }
         touched.forEach(name -> touch(name, source.highest(name)));
      }
      else if(table != null)
      {
         Object selection = "query_records".equals(use.getToolName()) ? (input.containsKey("fields") ? input.get("fields") : input.get("select")) : null;
         Collection<String> fields = selection instanceof Collection<?> selected && selected.stream().allMatch(String.class::isInstance)
            ? selected.stream().map(String.class::cast).toList() : null;
         touch(table, source.highest(table, fields));
         observeReturnedFields(table, result);
      }
      else
      {
         touchUnknown();
      }
   }



   /*******************************************************************************
    ** Inputs cannot claim a subset that the tool ignored. Include returned columns
    ** and record-label fields that are sent even alongside explicit subsets.
    *******************************************************************************/
   private void observeReturnedFields(String table, AiToolResult result)
   {
      if(result.getRender() instanceof Map<?, ?> render && render.get("columns") instanceof Collection<?> columns)
      {
         for(Object column : columns)
         {
            if(column instanceof Map<?, ?> field && field.get("name") instanceof String name)
            {
               touch(table, source.ofField(table, name));
            }
         }
      }
      QTableMetaData metadata = QContext.getQInstance().getTable(table);
      if(metadata != null && metadata.getRecordLabelFields() != null && result.getRecordRefs() != null && !result.getRecordRefs().isEmpty())
      {
         for(String field : metadata.getRecordLabelFields())
         {
            touch(table, source.ofField(table, field));
         }
      }
   }



   /*******************************************************************************
    ** Inspect structured data inside the untrusted envelope without retaining values.
    *******************************************************************************/
   private void collectResultTables(String content, Set<String> names)
   {
      if(content == null)
      {
         return;
      }
      Integer start = content.indexOf('{');
      Integer end = content.lastIndexOf('}');
      if(start >= 0 && end >= start)
      {
         try
         {
            collectTables(JsonUtils.toObject(content.substring(start, end + 1), Map.class), names);
         }
         catch(Exception e)
         {
            touchUnknown();
         }
      }
   }



   /*******************************************************************************
    ** Structured render metadata can name additional tables; never retain record values.
    *******************************************************************************/
   private void collectTables(Object value, Set<String> names)
   {
      if(value instanceof Map<?, ?> map)
      {
         map.forEach((key, child) ->
         {
            if(("table".equals(key) || "tableName".equals(key)) && child instanceof String name)
            {
               names.add(name);
            }
            else
            {
               collectTables(child, names);
            }
         });
      }
      else if(value instanceof Collection<?> list)
      {
         list.forEach(child -> collectTables(child, names));
      }
   }



   /*******************************************************************************
    ** Unknown cross-table results inherit the highest level anywhere in the host.
    *******************************************************************************/
   private void touchUnknown()
   {
      classification = Classifications.max(classification, source.highestAnywhere());
      for(String table : QContext.getQInstance().getTables().keySet())
      {
         if(!table.startsWith("assistant_") && source.highest(table) != DataClassification.PUBLIC)
         {
            tables.add(table);
         }
      }
   }



   /*******************************************************************************
    ** Keep the maximum and the contributing application tables.
    *******************************************************************************/
   private void touch(String table, DataClassification value)
   {
      if(table != null && !table.startsWith("assistant_"))
      {
         classification = Classifications.max(classification, value);
         if(QContext.getQInstance().getTable(table) != null)
         {
            tables.add(table);
         }
      }
   }



   /*******************************************************************************
    ** Files attached to the question are user content of unknown
    ** classification. In HIPAA mode they are treated as PHI (so they only ever
    ** reach HIPAA-eligible providers, and the conversation stays PHI); otherwise
    ** they follow the rules for the user's own text, which raises nothing.
    *******************************************************************************/
   public void attachedFiles(boolean hipaaMode)
   {
      if(hipaaMode)
      {
         classification = Classifications.max(classification, DataClassification.PHI);
      }
   }



   /*******************************************************************************
    ** The maximum classification so far.
    *******************************************************************************/
   public DataClassification getClassification()
   {
      return (classification);
   }



   /*******************************************************************************
    ** Reconcile a newer persisted conversation snapshot without lowering the turn.
    *******************************************************************************/
   public void inherit(AssistantConversation conversation)
   {
      if(conversation != null)
      {
         classification = Classifications.max(classification, Classifications.stored(conversation.getMaxClassification()));
      }
   }



   /*******************************************************************************
    ** Bounded table names for audit and reasons; no record labels or values.
    *******************************************************************************/
   public String getTables()
   {
      String names = String.join(", ", tables);
      return (names.length() <= 500 ? names : names.substring(0, 497) + "...");
   }
}
