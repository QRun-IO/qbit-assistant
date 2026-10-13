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
import java.util.Optional;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.access.DataAccessMode;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** `assistant_data_access` rows: the object must exist (a table or process of
 ** this instance) and the mode must be NONE, READ or READ_WRITE; READ_WRITE
 ** on a process means "may run". After any change the runtime's cached modes
 ** are dropped, so the next tool call uses the new setting.
 *******************************************************************************/
public class AssistantDataAccessCustomizer implements TableCustomizerInterface
{

   /*******************************************************************************
    ** Check each row (merged with its old values on update).
    *******************************************************************************/
   @Override
   public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      QInstance                            qInstance = QContext.getQInstance();
      Optional<Map<Serializable, QRecord>> old       = oldRecordListToMap("id", oldRecordList);
      for(QRecord record : records)
      {
         QRecord merged = new QRecord();
         old.map(m -> m.get(record.getValue("id"))).ifPresent(o -> merged.setValues(new LinkedHashMap<>(o.getValues())));
         record.getValues().forEach(merged::setValue);

         String type = merged.getValueString("objectType");
         String name = merged.getValueString("objectName");
         if(!StringUtils.hasContent(type))
         {
            record.setValue("objectType", AssistantDataAccess.TYPE_TABLE);
            type = AssistantDataAccess.TYPE_TABLE;
         }
         if(!AssistantDataAccess.TYPE_TABLE.equals(type) && !AssistantDataAccess.TYPE_PROCESS.equals(type))
         {
            record.addError(new BadInputStatusMessage("Type must be TABLE or PROCESS."));
         }
         else if(!StringUtils.hasContent(name))
         {
            record.addError(new BadInputStatusMessage("Name the table or process."));
         }
         else if(AssistantDataAccess.TYPE_TABLE.equals(type) ? qInstance.getTable(name.trim()) == null : qInstance.getProcess(name.trim()) == null)
         {
            record.addError(new BadInputStatusMessage("There is no " + type.toLowerCase() + " named " + name + " in this application."));
         }
         else if(!name.equals(name.trim()))
         {
            record.setValue("objectName", name.trim());
         }

         if(DataAccessMode.parse(merged.getValueString("mode")) == null)
         {
            record.addError(new BadInputStatusMessage("Assistant access must be NONE, READ or READ_WRITE."));
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** Drop the cached modes after a save.
    *******************************************************************************/
   @Override
   public List<QRecord> postInsertOrUpdate(AbstractActionInput input, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      invalidate();
      return (records);
   }



   /*******************************************************************************
    ** Drop the cached modes after a delete.
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      invalidate();
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void invalidate()
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      if(runtime != null)
      {
         runtime.getDataAccessService().invalidate();
      }
   }

}
