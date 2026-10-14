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
import com.kingsrook.qbits.assistant.engine.ProfileService;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantSkill;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Profiles, skills and their links: bounded text (a personality of at most
 ** {@link ProfileService#MAX_PERSONALITY_CHARS} characters, skill
 ** instructions of at most {@link ProfileService#MAX_SKILL_CHARS}), at most
 ** {@link ProfileService#MAX_STARTER_PROMPTS} starter prompts, a real app
 ** name, and one default profile (marking one unmarks the others). After any
 ** change the runtime's cached profiles are dropped.
 *******************************************************************************/
public class AssistantProfileCustomizer implements TableCustomizerInterface
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      String                               table = tableName(input);
      Optional<Map<Serializable, QRecord>> old   = oldRecordListToMap("id", oldRecordList);
      for(QRecord record : records)
      {
         QRecord merged = new QRecord();
         old.map(m -> m.get(record.getValue("id"))).ifPresent(o -> merged.setValues(new LinkedHashMap<>(o.getValues())));
         record.getValues().forEach(merged::setValue);

         if(AssistantProfile.TABLE_NAME.equals(table))
         {
            checkProfile(record, merged);
         }
         else if(AssistantSkill.TABLE_NAME.equals(table))
         {
            String instructions = merged.getValueString("instructions");
            if(instructions != null && instructions.length() > ProfileService.MAX_SKILL_CHARS)
            {
               record.addError(new BadInputStatusMessage("Instructions can be at most " + ProfileService.MAX_SKILL_CHARS + " characters (" + instructions.length() + " given). Split it into smaller skills."));
            }
            if(record.getValue("enabled") == null && merged.getValue("enabled") == null)
            {
               record.setValue("enabled", true);
            }
         }
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void checkProfile(QRecord record, QRecord merged)
   {
      String routingMode = merged.getValueString("routingMode");
      if(routingMode == null)
      {
         record.setValue("routingMode", "OFF");
      }
      else if(!List.of("OFF", "HEURISTIC", "CLASSIFIER").contains(routingMode))
      {
         record.addError(new BadInputStatusMessage("Routing mode must be OFF, HEURISTIC or CLASSIFIER."));
      }
      String personality = merged.getValueString("personality");
      if(personality != null && personality.length() > ProfileService.MAX_PERSONALITY_CHARS)
      {
         record.addError(new BadInputStatusMessage("Personality can be at most " + ProfileService.MAX_PERSONALITY_CHARS + " characters (" + personality.length() + " given)."));
      }
      String starters = merged.getValueString("starterPrompts");
      if(starters != null && starters.lines().filter(StringUtils::hasContent).count() > ProfileService.MAX_STARTER_PROMPTS)
      {
         record.addError(new BadInputStatusMessage("Give at most " + ProfileService.MAX_STARTER_PROMPTS + " starter prompts, one per line."));
      }
      String appName = merged.getValueString("appName");
      if(StringUtils.hasContent(appName) && QContext.getQInstance().getApp(appName.strip()) == null)
      {
         record.addError(new BadInputStatusMessage("There is no app named " + appName + " in this application."));
      }
      if(record.getValue("enabled") == null && merged.getValue("enabled") == null)
      {
         record.setValue("enabled", true);
      }
   }



   /*******************************************************************************
    ** One default profile: saving one as default unmarks the others. Then drop
    ** the cache.
    *******************************************************************************/
   @Override
   public List<QRecord> postInsertOrUpdate(AbstractActionInput input, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      if(runtime != null && AssistantProfile.TABLE_NAME.equals(tableName(input)))
      {
         for(QRecord record : records)
         {
            if(Boolean.TRUE.equals(record.getValueBoolean("isDefault")) && record.getValue("id") != null && record.getErrors().isEmpty())
            {
               for(AssistantProfile other : runtime.getStore().query(AssistantProfile.TABLE_NAME,
                  new QQueryFilter().withCriteria("isDefault", QCriteriaOperator.EQUALS, true).withCriteria("id", QCriteriaOperator.NOT_EQUALS, record.getValue("id")), AssistantProfile.class))
               {
                  runtime.getStore().updateFields(AssistantProfile.TABLE_NAME, new QRecord().withValue("id", other.getId()).withValue("isDefault", false));
               }
            }
         }
      }
      invalidate(runtime);
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      invalidate(AssistantRuntime.of(QContext.getQInstance()));
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void invalidate(AssistantRuntime runtime)
   {
      if(runtime != null)
      {
         runtime.getProfileService().invalidate();
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String tableName(AbstractActionInput input)
   {
      if(input instanceof InsertInput insert)
      {
         return (insert.getTableName());
      }
      if(input instanceof UpdateInput update)
      {
         return (update.getTableName());
      }
      return (null);
   }

}
