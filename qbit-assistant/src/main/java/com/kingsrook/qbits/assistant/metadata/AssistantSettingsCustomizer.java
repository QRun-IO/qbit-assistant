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


import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.engine.FallbackChain;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.QueryOrGetInputInterface;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;


/*******************************************************************************
 ** Customizer of the settings tables (assistant_provider, assistant_model_price).
 **
 ** assistant_provider:
 ** - Before a save, the row is checked (ProviderService.validate). Above all,
 **   apiKeyEnvName must be the NAME of an allowed environment variable, so a
 **   key pasted into it by mistake is refused and never stored.
 ** - After a save, a row marked default makes every other row non-default.
 ** - After a read, the virtual field apiKeyStatus says whether the key is set,
 **   with its last four characters at most. No read path returns the key.
 **
 ** Both tables: a change drops the cached provider rows and prices.
 *******************************************************************************/
public class AssistantSettingsCustomizer implements TableCustomizerInterface
{

   /*******************************************************************************
    ** Add the masked key status to provider rows.
    *******************************************************************************/
   @Override
   public List<QRecord> postQuery(QueryOrGetInputInterface queryInput, List<QRecord> records) throws QException
   {
      ProviderService service = service();
      if(service == null || !AssistantProvider.TABLE_NAME.equals(queryInput.getTableName()))
      {
         return (records);
      }
      for(QRecord record : records)
      {
         record.setValue(ProviderService.KEY_STATUS_FIELD, service.keyStatus(new AssistantProvider(record)));
      }
      return (records);
   }



   /*******************************************************************************
    ** Check provider rows before insert and update.
    *******************************************************************************/
   @Override
   public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      ProviderService service = service();
      if(AssistantModel.TABLE_NAME.equals(tableName(input)))
      {
         for(QRecord record : records)
         {
            if(record.getValue("id") == null)
            {
               if(record.getValue("source") == null)
               {
                  record.setValue("source", "MANUAL");
               }
               if(record.getValue("enabled") == null)
               {
                  record.setValue("enabled", true);
               }
            }
         }
         validateFallbacks(records, oldRecordList);
         return (records);
      }
      if(service == null || !AssistantProvider.TABLE_NAME.equals(tableName(input)))
      {
         return (records);
      }

      Optional<Map<Serializable, QRecord>> old = oldRecordListToMap("id", oldRecordList);
      for(QRecord record : records)
      {
         record.removeValue(ProviderService.KEY_STATUS_FIELD);
         if(isSystemUpdate(record))
         {
            continue;
         }

         QRecord merged = new QRecord();
         old.map(m -> m.get(record.getValue("id"))).ifPresent(o -> merged.setValues(new java.util.LinkedHashMap<>(o.getValues())));
         record.getValues().forEach(merged::setValue);

         AssistantProvider provider = new AssistantProvider(merged);
         if(merged.getValue("kind") == null)
         {
            record.setValue("kind", service.kind(provider));
         }
         if(merged.getValue("location") == null)
         {
            record.setValue("location", service.location(provider));
         }
         for(String field : List.of("hipaaEligible", "connectTimeoutSeconds", "requestTimeoutSeconds"))
         {
            if(merged.getValue(field) == null)
            {
               record.setValue(field, field.equals("hipaaEligible") ? Boolean.FALSE : (field.equals("connectTimeoutSeconds") ? 10 : 120));
            }
         }
         for(String error : service.validate(provider))
         {
            record.addError(new BadInputStatusMessage(error));
         }
         if(record.getValue("enabled") == null && merged.getValue("enabled") == null)
         {
            record.setValue("enabled", true);
         }
         if(record.getValue("isDefault") == null && merged.getValue("isDefault") == null)
         {
            record.setValue("isDefault", false);
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** Keep one default provider; drop caches.
    *******************************************************************************/
   @Override
   public List<QRecord> postInsertOrUpdate(AbstractActionInput input, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      if(runtime == null)
      {
         return (records);
      }
      if(AssistantProvider.TABLE_NAME.equals(tableName(input)))
      {
         for(QRecord record : records)
         {
            if(Boolean.TRUE.equals(record.getValueBoolean("isDefault")) && record.getValue("id") != null && record.getErrors().isEmpty())
            {
               runtime.getStore().clearOtherDefaultProviders(record.getValueInteger("id"));
            }
         }
      }
      runtime.getProviderService().invalidate();
      return (records);
   }



   /*******************************************************************************
    ** Drop caches after a delete.
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      ProviderService service = service();
      if(service != null)
      {
         service.invalidate();
      }
      return (records);
   }



   /*******************************************************************************
    ** An update by the qbit itself: only the last test result, or clearing the
    ** default flag. These are saved even for a row whose settings are invalid.
    *******************************************************************************/
   static boolean isSystemUpdate(QRecord record)
   {
      if(record.getValue("id") == null)
      {
         return (false);
      }
      for(Map.Entry<String, Serializable> entry : record.getValues().entrySet())
      {
         String name = entry.getKey();
         boolean system = name.equals("id") || name.equals("modifyDate") || name.startsWith("lastTest") || name.startsWith("lastDiscovery")
            || List.of("healthState", "healthCheckedAt", "downUntil").contains(name)
            || (name.equals("isDefault") && Boolean.FALSE.equals(entry.getValue()));
         if(!system)
         {
            return (false);
         }
      }
      return (true);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String tableName(AbstractActionInput input)
   {
      if(input instanceof com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput insert)
      {
         return (insert.getTableName());
      }
      if(input instanceof com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput update)
      {
         return (update.getTableName());
      }
      return (null);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static ProviderService service()
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      return (runtime == null ? null : runtime.getProviderService());
   }



   /*******************************************************************************
    ** Validate the resulting graph, including all changes in this save batch.
    *******************************************************************************/
   private void validateFallbacks(List<QRecord> records, Optional<List<QRecord>> oldRecords) throws QException
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      if(runtime == null)
      {
         return;
      }
      Map<Integer, AssistantModel> models = new java.util.LinkedHashMap<>();
      for(AssistantModel model : runtime.getStore().listModels())
      {
         models.put(model.getId(), model);
      }
      Optional<Map<Serializable, QRecord>> old = oldRecordListToMap("id", oldRecords);
      List<AssistantModel> proposed = new java.util.ArrayList<>();
      for(QRecord record : records)
      {
         QRecord merged = new QRecord();
         old.map(m -> m.get(record.getValue("id"))).ifPresent(o -> merged.setValues(new java.util.LinkedHashMap<>(o.getValues())));
         record.getValues().forEach(merged::setValue);
         AssistantModel model = new AssistantModel(merged);
         proposed.add(model);
         if(model.getId() != null)
         {
            models.put(model.getId(), model);
         }
      }
      for(Integer i = 0; i < records.size(); i++)
      {
         String error = FallbackChain.validate(proposed.get(i), models);
         if(error != null)
         {
            records.get(i).addError(new BadInputStatusMessage(error));
         }
      }
      for(AssistantModel model : models.values())
      {
         String error = FallbackChain.validate(model, models, false);
         if(error != null)
         {
            records.forEach(record -> record.addError(new BadInputStatusMessage(error)));
            break;
         }
      }
   }

}
