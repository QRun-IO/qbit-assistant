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

package com.kingsrook.qbits.assistant.model;


import java.time.Instant;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.fields.ValueTooLongBehavior;


/*******************************************************************************
 ** How the assistant presents itself: display name, icon, personality (tone instructions appended to the system prompt, bounded), greeting and starter prompts. One profile is the default; others can be scoped to an app or to users holding a permission. A profile only changes how the assistant talks; it never grants tools, data access or permissions.
 *******************************************************************************/
public class AssistantProfile extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_profile";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 64)
   private String name;

   @QField(isRequired = true, maxLength = 64)
   private String displayName;

   @QField(maxLength = 64)
   private String icon;

   @QField()
   private String personality;

   @QField(maxLength = 500, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String greeting;

   @QField()
   private String starterPrompts;

   @QField()
   private Boolean isDefault;

   @QField()
   private Boolean enabled;

   @QField(maxLength = 128)
   private String appName;

   @QField(maxLength = 128)
   private String permissionName;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantProfile()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantProfile(QRecord record) throws QException
   {
      populateFromQRecord(record);
   }



   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (this.id);
   }



   /*******************************************************************************
    ** Setter for id
    *******************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /*******************************************************************************
    ** Fluent setter for id
    *******************************************************************************/
   public AssistantProfile withId(Integer id)
   {
      this.id = id;
      return (this);
   }


   /*******************************************************************************
    ** Getter for name
    *******************************************************************************/
   public String getName()
   {
      return (this.name);
   }



   /*******************************************************************************
    ** Setter for name
    *******************************************************************************/
   public void setName(String name)
   {
      this.name = name;
   }



   /*******************************************************************************
    ** Fluent setter for name
    *******************************************************************************/
   public AssistantProfile withName(String name)
   {
      this.name = name;
      return (this);
   }


   /*******************************************************************************
    ** Getter for displayName
    *******************************************************************************/
   public String getDisplayName()
   {
      return (this.displayName);
   }



   /*******************************************************************************
    ** Setter for displayName
    *******************************************************************************/
   public void setDisplayName(String displayName)
   {
      this.displayName = displayName;
   }



   /*******************************************************************************
    ** Fluent setter for displayName
    *******************************************************************************/
   public AssistantProfile withDisplayName(String displayName)
   {
      this.displayName = displayName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for icon
    *******************************************************************************/
   public String getIcon()
   {
      return (this.icon);
   }



   /*******************************************************************************
    ** Setter for icon
    *******************************************************************************/
   public void setIcon(String icon)
   {
      this.icon = icon;
   }



   /*******************************************************************************
    ** Fluent setter for icon
    *******************************************************************************/
   public AssistantProfile withIcon(String icon)
   {
      this.icon = icon;
      return (this);
   }


   /*******************************************************************************
    ** Getter for personality
    *******************************************************************************/
   public String getPersonality()
   {
      return (this.personality);
   }



   /*******************************************************************************
    ** Setter for personality
    *******************************************************************************/
   public void setPersonality(String personality)
   {
      this.personality = personality;
   }



   /*******************************************************************************
    ** Fluent setter for personality
    *******************************************************************************/
   public AssistantProfile withPersonality(String personality)
   {
      this.personality = personality;
      return (this);
   }


   /*******************************************************************************
    ** Getter for greeting
    *******************************************************************************/
   public String getGreeting()
   {
      return (this.greeting);
   }



   /*******************************************************************************
    ** Setter for greeting
    *******************************************************************************/
   public void setGreeting(String greeting)
   {
      this.greeting = greeting;
   }



   /*******************************************************************************
    ** Fluent setter for greeting
    *******************************************************************************/
   public AssistantProfile withGreeting(String greeting)
   {
      this.greeting = greeting;
      return (this);
   }


   /*******************************************************************************
    ** Getter for starterPrompts
    *******************************************************************************/
   public String getStarterPrompts()
   {
      return (this.starterPrompts);
   }



   /*******************************************************************************
    ** Setter for starterPrompts
    *******************************************************************************/
   public void setStarterPrompts(String starterPrompts)
   {
      this.starterPrompts = starterPrompts;
   }



   /*******************************************************************************
    ** Fluent setter for starterPrompts
    *******************************************************************************/
   public AssistantProfile withStarterPrompts(String starterPrompts)
   {
      this.starterPrompts = starterPrompts;
      return (this);
   }


   /*******************************************************************************
    ** Getter for isDefault
    *******************************************************************************/
   public Boolean getIsDefault()
   {
      return (this.isDefault);
   }



   /*******************************************************************************
    ** Setter for isDefault
    *******************************************************************************/
   public void setIsDefault(Boolean isDefault)
   {
      this.isDefault = isDefault;
   }



   /*******************************************************************************
    ** Fluent setter for isDefault
    *******************************************************************************/
   public AssistantProfile withIsDefault(Boolean isDefault)
   {
      this.isDefault = isDefault;
      return (this);
   }


   /*******************************************************************************
    ** Getter for enabled
    *******************************************************************************/
   public Boolean getEnabled()
   {
      return (this.enabled);
   }



   /*******************************************************************************
    ** Setter for enabled
    *******************************************************************************/
   public void setEnabled(Boolean enabled)
   {
      this.enabled = enabled;
   }



   /*******************************************************************************
    ** Fluent setter for enabled
    *******************************************************************************/
   public AssistantProfile withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }


   /*******************************************************************************
    ** Getter for appName
    *******************************************************************************/
   public String getAppName()
   {
      return (this.appName);
   }



   /*******************************************************************************
    ** Setter for appName
    *******************************************************************************/
   public void setAppName(String appName)
   {
      this.appName = appName;
   }



   /*******************************************************************************
    ** Fluent setter for appName
    *******************************************************************************/
   public AssistantProfile withAppName(String appName)
   {
      this.appName = appName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for permissionName
    *******************************************************************************/
   public String getPermissionName()
   {
      return (this.permissionName);
   }



   /*******************************************************************************
    ** Setter for permissionName
    *******************************************************************************/
   public void setPermissionName(String permissionName)
   {
      this.permissionName = permissionName;
   }



   /*******************************************************************************
    ** Fluent setter for permissionName
    *******************************************************************************/
   public AssistantProfile withPermissionName(String permissionName)
   {
      this.permissionName = permissionName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (this.createDate);
   }



   /*******************************************************************************
    ** Setter for createDate
    *******************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /*******************************************************************************
    ** Fluent setter for createDate
    *******************************************************************************/
   public AssistantProfile withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }


   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (this.modifyDate);
   }



   /*******************************************************************************
    ** Setter for modifyDate
    *******************************************************************************/
   public void setModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
   }



   /*******************************************************************************
    ** Fluent setter for modifyDate
    *******************************************************************************/
   public AssistantProfile withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
