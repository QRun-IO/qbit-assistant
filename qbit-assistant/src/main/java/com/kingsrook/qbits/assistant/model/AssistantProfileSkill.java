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


/*******************************************************************************
 ** Links a skill to a profile: the assistant may use the skill while that profile is active.
 *******************************************************************************/
public class AssistantProfileSkill extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_profile_skill";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, possibleValueSourceName = AssistantProfile.TABLE_NAME)
   private Integer profileId;

   @QField(isRequired = true, possibleValueSourceName = AssistantSkill.TABLE_NAME)
   private Integer skillId;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantProfileSkill()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantProfileSkill(QRecord record) throws QException
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
   public AssistantProfileSkill withId(Integer id)
   {
      this.id = id;
      return (this);
   }


   /*******************************************************************************
    ** Getter for profileId
    *******************************************************************************/
   public Integer getProfileId()
   {
      return (this.profileId);
   }



   /*******************************************************************************
    ** Setter for profileId
    *******************************************************************************/
   public void setProfileId(Integer profileId)
   {
      this.profileId = profileId;
   }



   /*******************************************************************************
    ** Fluent setter for profileId
    *******************************************************************************/
   public AssistantProfileSkill withProfileId(Integer profileId)
   {
      this.profileId = profileId;
      return (this);
   }


   /*******************************************************************************
    ** Getter for skillId
    *******************************************************************************/
   public Integer getSkillId()
   {
      return (this.skillId);
   }



   /*******************************************************************************
    ** Setter for skillId
    *******************************************************************************/
   public void setSkillId(Integer skillId)
   {
      this.skillId = skillId;
   }



   /*******************************************************************************
    ** Fluent setter for skillId
    *******************************************************************************/
   public AssistantProfileSkill withSkillId(Integer skillId)
   {
      this.skillId = skillId;
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
   public AssistantProfileSkill withCreateDate(Instant createDate)
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
   public AssistantProfileSkill withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
