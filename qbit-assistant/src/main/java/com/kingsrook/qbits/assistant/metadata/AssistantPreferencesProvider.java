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


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.model.AssistantUserPreference;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceChange;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceProviderInterface;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceRequestContext;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceSectionMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceValues;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferencesMetaData;


/*******************************************************************************
 ** Core preferences over the standard assistant_user_preference table. Core
 ** supplies the authenticated user and trusted scope, retaining audit identity.
 *******************************************************************************/
public class AssistantPreferencesProvider implements UserPreferenceProviderInterface
{
   public static final String SECTION = "assistant";
   public static final String AUTO_CONFIRM_REASON = "Auto-confirm is not allowed by the assistant policy.";



   /*******************************************************************************
    ** The admin app is intentionally not the scope: ordinary assistant users
    ** must see their own preferences without assistant.admin.
    *******************************************************************************/
   public static void register(QInstance qInstance)
   {
      UserPreferencesMetaData.ofOrWithNew(qInstance).withSection(new Section()
         .withName(SECTION).withLabel("Assistant").withIcon(new QIcon("smart_toy"))
         .withDescription("Defaults for new conversations and confirmation of changes.")
         .withPermissionName(AssistantQBitConfig.ACCESS_PERMISSION)
         .withProvider(new QCodeReference(AssistantPreferencesProvider.class)));
   }



   /*******************************************************************************
    ** Core only applies channel rules to app-scoped sections. Explicitly deny
    ** this unbound configuration section before loading or changing any rows.
    *******************************************************************************/
   private AssistantRuntime runtime(UserPreferenceRequestContext context) throws QException
   {
      if("assistant".equals(context.getSession().getClientChannelName()))
      {
         throw (new QNotFoundException("Preference section not found"));
      }
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      AssistantSecurity.ownerKey(context.getSession(), runtime.getConfig());
      return (runtime);
   }



   /*******************************************************************************
    ** Read only the context user's row, even if they hold an admin owner key.
    *******************************************************************************/
   @Override
   public UserPreferenceValues load(UserPreferenceRequestContext context) throws QException
   {
      AssistantUserPreference preference = runtime(context).getStore().getPreference(context.getUserId());
      return (new UserPreferenceValues().withValues(Map.of(
         "defaultWriteMode", preference != null && Boolean.TRUE.equals(preference.getDefaultWriteMode()),
         "autoConfirmLowRisk", preference != null && Boolean.TRUE.equals(preference.getAutoConfirmLowRisk()))));
   }



   /*******************************************************************************
    ** Re-read policy at the write boundary; the submitted defaults grant nothing.
    *******************************************************************************/
   @Override
   public List<String> validate(UserPreferenceRequestContext context, UserPreferenceChange change) throws QException
   {
      PolicyService.ToolPolicy policy = runtime(context).getPolicyService().current(true).tools();
      List<String> errors = new ArrayList<>();
      if(change.getValues().containsKey("autoConfirmLowRisk") && !policy.autoConfirmAllowed())
      {
         errors.add("Auto-confirm low-risk changes: " + AUTO_CONFIRM_REASON);
      }
      if(Boolean.TRUE.equals(change.getValues().get("defaultWriteMode")) && !policy.writeModeAvailableTo(context.getSession()))
      {
         errors.add("Start conversations in Write mode: Write mode is not available to you under the assistant policy.");
      }
      return (errors);
   }



   /*******************************************************************************
    ** Apply only supplied fields; all ownership comes from the context.
    *******************************************************************************/
   @Override
   public void store(UserPreferenceRequestContext context, UserPreferenceChange change) throws QException
   {
      AssistantRuntime runtime = runtime(context);
      AssistantUserPreference preference = runtime.getStore().getPreference(context.getUserId());
      if(preference == null)
      {
         preference = runtime.getStore().insert(new AssistantUserPreference().withUserId(context.getUserId())
            .withDefaultWriteMode(false).withAutoConfirmLowRisk(false));
      }
      QRecord update = new QRecord().withValue("id", preference.getId());
      change.getValues().forEach(update::setValue);
      runtime.getStore().updateFields(AssistantUserPreference.TABLE_NAME, update);
   }



   /*******************************************************************************
    ** Return fresh field metadata for each request, so policy editability never
    ** mutates the shared instance metadata or leaks between concurrent users.
    *******************************************************************************/
   public static class Section extends UserPreferenceSectionMetaData
   {
      /*******************************************************************************
       ** Scalar read-only metadata uses the standard field help text for its reason.
       *******************************************************************************/
      @Override
      public List<QFieldMetaData> getFields()
      {
         boolean autoConfirmAllowed = false;
         AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
         if(runtime != null && QContext.getQSession() != null)
         {
            try
            {
               autoConfirmAllowed = runtime.getPolicyService().current(false).tools().autoConfirmAllowed();
            }
            catch(QException e)
            {
               // Fail closed when policy cannot be read; provider validation also rechecks it.
            }
         }
         QFieldMetaData autoConfirm = new QFieldMetaData("autoConfirmLowRisk", QFieldType.BOOLEAN)
            .withLabel("Auto-confirm low-risk changes").withIsEditable(autoConfirmAllowed);
         if(!autoConfirmAllowed)
         {
            autoConfirm.withHelpContent(new QHelpContent(AUTO_CONFIRM_REASON));
         }
         return (List.of(new QFieldMetaData("defaultWriteMode", QFieldType.BOOLEAN).withLabel("Start conversations in Write mode"), autoConfirm));
      }
   }
}
