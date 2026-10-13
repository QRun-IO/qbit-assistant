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
 ** An extra skill: a named, admin-written instruction pack (Markdown) the assistant reads when it is relevant (its trigger keywords appear in the question, or the user is in one of its apps; with neither set, always). Skills are instructions ONLY: they can never grant tools, endpoints, data access or permissions.
 *******************************************************************************/
public class AssistantSkill extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_skill";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 64)
   private String name;

   @QField(maxLength = 500, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String description;

   @QField(isRequired = true)
   private String instructions;

   @QField()
   private Boolean enabled;

   @QField(maxLength = 500)
   private String triggerKeywords;

   @QField(maxLength = 500)
   private String appNames;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantSkill()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantSkill(QRecord record) throws QException
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
   public AssistantSkill withId(Integer id)
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
   public AssistantSkill withName(String name)
   {
      this.name = name;
      return (this);
   }


   /*******************************************************************************
    ** Getter for description
    *******************************************************************************/
   public String getDescription()
   {
      return (this.description);
   }



   /*******************************************************************************
    ** Setter for description
    *******************************************************************************/
   public void setDescription(String description)
   {
      this.description = description;
   }



   /*******************************************************************************
    ** Fluent setter for description
    *******************************************************************************/
   public AssistantSkill withDescription(String description)
   {
      this.description = description;
      return (this);
   }


   /*******************************************************************************
    ** Getter for instructions
    *******************************************************************************/
   public String getInstructions()
   {
      return (this.instructions);
   }



   /*******************************************************************************
    ** Setter for instructions
    *******************************************************************************/
   public void setInstructions(String instructions)
   {
      this.instructions = instructions;
   }



   /*******************************************************************************
    ** Fluent setter for instructions
    *******************************************************************************/
   public AssistantSkill withInstructions(String instructions)
   {
      this.instructions = instructions;
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
   public AssistantSkill withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }


   /*******************************************************************************
    ** Getter for triggerKeywords
    *******************************************************************************/
   public String getTriggerKeywords()
   {
      return (this.triggerKeywords);
   }



   /*******************************************************************************
    ** Setter for triggerKeywords
    *******************************************************************************/
   public void setTriggerKeywords(String triggerKeywords)
   {
      this.triggerKeywords = triggerKeywords;
   }



   /*******************************************************************************
    ** Fluent setter for triggerKeywords
    *******************************************************************************/
   public AssistantSkill withTriggerKeywords(String triggerKeywords)
   {
      this.triggerKeywords = triggerKeywords;
      return (this);
   }


   /*******************************************************************************
    ** Getter for appNames
    *******************************************************************************/
   public String getAppNames()
   {
      return (this.appNames);
   }



   /*******************************************************************************
    ** Setter for appNames
    *******************************************************************************/
   public void setAppNames(String appNames)
   {
      this.appNames = appNames;
   }



   /*******************************************************************************
    ** Fluent setter for appNames
    *******************************************************************************/
   public AssistantSkill withAppNames(String appNames)
   {
      this.appNames = appNames;
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
   public AssistantSkill withCreateDate(Instant createDate)
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
   public AssistantSkill withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
