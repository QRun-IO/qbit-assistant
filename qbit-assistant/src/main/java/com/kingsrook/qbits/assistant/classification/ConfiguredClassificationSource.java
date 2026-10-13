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
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Default classification source for RCs without core classification metadata.
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
      return (table == null || table.startsWith("assistant_") ? DataClassification.PUBLIC : DataClassification.max(null, tables.get(table)));
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
      return (value == null ? ofTable(table) : value);
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
            result = DataClassification.max(result, ofField(table, field));
         }
      }
      for(DataClassification value : fields.getOrDefault(table, Map.of()).values())
      {
         result = DataClassification.max(result, value);
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
         result = DataClassification.max(result, highest(table));
      }
      for(String table : fields.keySet())
      {
         result = DataClassification.max(result, highest(table));
      }
      return (result);
   }
}
