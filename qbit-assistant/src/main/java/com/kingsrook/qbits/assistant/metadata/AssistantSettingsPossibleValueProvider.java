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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.spi.AssistantProviderType;
import com.kingsrook.qqq.backend.core.actions.values.QCustomPossibleValueProvider;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.values.SearchPossibleValueSourceInput;
import com.kingsrook.qqq.backend.core.model.metadata.code.InitializableViaCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReferenceWithProperties;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;


/*******************************************************************************
 ** Provider settings choices, narrowed by the selected provider type.
 *******************************************************************************/
public class AssistantSettingsPossibleValueProvider implements QCustomPossibleValueProvider<String>, InitializableViaCodeReference
{
   private final Map<String, QPossibleValue<String>> union = new LinkedHashMap<>();
   private Boolean models;
   private Boolean providerRow;



   /*******************************************************************************
    ** Price rows share model choices but their id is never a provider id.
    *******************************************************************************/
   @Override
   public void initialize(QCodeReference codeReference)
   {
      Map<String, Serializable> properties = ((QCodeReferenceWithProperties) codeReference).getProperties();
      models = (Boolean) properties.get("models");
      providerRow = (Boolean) properties.get("providerRow");
      for(Object object : (List<?>) properties.get("union"))
      {
         QPossibleValue<?> value = (QPossibleValue<?>) object;
         union.put(String.valueOf(value.getId()), new QPossibleValue<>(String.valueOf(value.getId()), value.getLabel()));
      }
   }



   /*******************************************************************************
    ** Saved values resolve across types, including disabled registry models.
    *******************************************************************************/
   @Override
   public QPossibleValue<String> getPossibleValue(Serializable idValue) throws QException
   {
      String id = String.valueOf(idValue);
      if(union.containsKey(id))
      {
         return (union.get(id));
      }
      if(models && idValue != null)
      {
         for(AssistantModel model : new AssistantStore().listModels())
         {
            if(id.equals(model.getModelId()))
            {
               return (new QPossibleValue<>(id, id));
            }
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** Keep union fallback for callers that do not supply a known provider type.
    *******************************************************************************/
   @Override
   public List<QPossibleValue<String>> search(SearchPossibleValueSourceInput input) throws QException
   {
      Map<String, Serializable> otherValues = input.getOtherValues();
      AssistantProviderType type = otherValues == null ? null : AssistantRuntime.of(QContext.getQInstance()).getConfig().getProviderTypes().get(otherValues.get("providerType"));
      Map<String, QPossibleValue<String>> values = new LinkedHashMap<>();
      if(type == null)
      {
         values.putAll(union);
      }
      else
      {
         for(String id : models ? type.getModels() : type.getKinds())
         {
            values.put(id, new QPossibleValue<>(id, models ? id : AssistantRecordScreens.providerKindLabel(id)));
         }
         if(models && providerRow && otherValues.get("id") != null)
         {
            AssistantStore store = new AssistantStore();
            String providerId = String.valueOf(otherValues.get("id"));
            AssistantProvider provider = store.listProviders().stream()
               .filter(row -> providerId.equals(String.valueOf(row.getId())) && type.getType().equals(row.getProviderType())).findFirst().orElse(null);
            if(provider != null)
            {
               for(AssistantModel model : store.listModels())
               {
                  if(provider.getId().equals(model.getProviderId()) && Boolean.TRUE.equals(model.getEnabled()) && "CHAT".equals(model.getModelKind()))
                  {
                     values.put(model.getModelId(), new QPossibleValue<>(model.getModelId(), model.getModelId()));
                  }
               }
            }
         }
      }
      return (completeCustomPVSSearch(input, new ArrayList<>(values.values())));
   }
}
