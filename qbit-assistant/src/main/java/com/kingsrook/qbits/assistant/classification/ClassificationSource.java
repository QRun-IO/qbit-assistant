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


import java.util.Collection;
import com.kingsrook.qqq.backend.core.context.QContext;


/*******************************************************************************
 ** Host seam for table and field classifications; a core metadata adapter can replace the configured source.
 *******************************************************************************/
public interface ClassificationSource
{
   /*******************************************************************************
    ** Classification of the table; unconfigured tables are PUBLIC.
    *******************************************************************************/
   DataClassification ofTable(String table);



   /*******************************************************************************
    ** Classification of a field, falling back to the table.
    *******************************************************************************/
   DataClassification ofField(String table, String field);



   /*******************************************************************************
    ** Highest classification of the table and all its fields.
    *******************************************************************************/
   DataClassification highest(String table);



   /*******************************************************************************
    ** Highest classification of selected fields; absent or empty selection means all fields.
    *******************************************************************************/
   default DataClassification highest(String table, Collection<String> fields)
   {
      if(fields == null || fields.isEmpty())
      {
         return (highest(table));
      }
      DataClassification result = DataClassification.PUBLIC;
      for(String field : fields)
      {
         result = DataClassification.max(result, field == null || "*".equals(field) ? highest(table) : ofField(table, field));
      }
      return (result);
   }



   /*******************************************************************************
    ** Conservative bound for results whose application tables cannot be identified.
    *******************************************************************************/
   default DataClassification highestAnywhere()
   {
      DataClassification result = DataClassification.PUBLIC;
      for(String table : QContext.getQInstance().getTables().keySet())
      {
         if(!table.startsWith("assistant_"))
         {
            result = DataClassification.max(result, highest(table));
         }
      }
      return (result);
   }
}
