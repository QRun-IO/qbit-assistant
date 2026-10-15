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

package com.kingsrook.qbits.assistant.classification;


import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.ai.AiDataClassifications;
import com.kingsrook.qqq.backend.core.actions.ai.AiFieldMetaData;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** The default classification source: core's AI classification metadata
 ** (AiTableMetaData / AiFieldMetaData, via AiDataClassifications) merged with
 ** the classifications configured on the qbit. A configured value wins when
 ** both are set; a field falls back to its own core classification, then to
 ** its table's; anything unclassified (and the assistant's own tables) is
 ** PUBLIC.
 *******************************************************************************/
public class ConfiguredClassificationSource implements ClassificationSource
{
   private final Map<String, DataClassification> tables = new LinkedHashMap<>();
   private final Map<String, Map<String, DataClassification>> fields = new LinkedHashMap<>();



   /*******************************************************************************
    ** Configure a table; null removes its override.
    *******************************************************************************/
   public ConfiguredClassificationSource withClassification(String table, DataClassification classification)
   {
      if(table != null)
      {
         tables.put(table, classification);
      }
      return (this);
   }



   /*******************************************************************************
    ** Configure a field; null restores table fallback.
    *******************************************************************************/
   public ConfiguredClassificationSource withClassification(String table, String field, DataClassification classification)
   {
      if(table != null && field != null)
      {
         fields.computeIfAbsent(table, key -> new LinkedHashMap<>()).put(field, classification);
      }
      return (this);
   }



   /*******************************************************************************
    ** Unconfigured and internal tables are PUBLIC.
    *******************************************************************************/
   @Override
   public DataClassification ofTable(String table)
   {
      return (table == null || table.startsWith("assistant_") ? DataClassification.PUBLIC : Classifications.max(null, tables.get(table) == null ? AiDataClassifications.ofTable(coreTable(table)) : tables.get(table)));
   }



   /*******************************************************************************
    ** An explicit field classification overrides the table default.
    *******************************************************************************/
   @Override
   public DataClassification ofField(String table, String field)
   {
      if(table == null || table.startsWith("assistant_"))
      {
         return (DataClassification.PUBLIC);
      }
      DataClassification value = field == null ? null : fields.getOrDefault(table, Map.of()).get(field);
      if(value == null)
      {
         QTableMetaData metadata = coreTable(table);
         AiFieldMetaData coreField = metadata == null || metadata.getFields() == null || field == null ? null : AiFieldMetaData.of(metadata.getFields().get(field));
         value = coreField == null ? null : coreField.getClassification();
      }
      return (value == null ? ofTable(table) : value);
   }



   /*******************************************************************************
    ** The table's core metadata, or null (no instance in context, or unknown).
    *******************************************************************************/
   private static QTableMetaData coreTable(String table)
   {
      QInstance qInstance = QContext.getQInstance();
      return (qInstance == null ? null : qInstance.getTable(table));
   }



   /*******************************************************************************
    ** Resolve all field names from the current instance, also retaining configured overrides.
    *******************************************************************************/
   @Override
   public DataClassification highest(String table)
   {
      DataClassification result = ofTable(table);
      if(table == null || table.startsWith("assistant_"))
      {
         return (result);
      }
      QTableMetaData metadata = QContext.getQInstance().getTable(table);
      if(metadata != null)
      {
         for(String field : metadata.getFields().keySet())
         {
            result = Classifications.max(result, ofField(table, field));
         }
      }
      for(DataClassification value : fields.getOrDefault(table, Map.of()).values())
      {
         result = Classifications.max(result, value);
      }
      return (result);
   }



   /*******************************************************************************
    ** Include configured tables even when a host has not registered their metadata yet.
    *******************************************************************************/
   @Override
   public DataClassification highestAnywhere()
   {
      DataClassification result = ClassificationSource.super.highestAnywhere();
      for(String table : tables.keySet())
      {
         result = Classifications.max(result, highest(table));
      }
      for(String table : fields.keySet())
      {
         result = Classifications.max(result, highest(table));
      }
      return (result);
   }
}
