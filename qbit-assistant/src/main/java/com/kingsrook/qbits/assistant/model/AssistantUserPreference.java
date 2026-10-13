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
 ** A user's own assistant preferences: the default mode of new conversations and auto-confirm of low-risk writes (only where the policy allows it).
 *******************************************************************************/
public class AssistantUserPreference extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_user_preference";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField()
   private Boolean defaultWriteMode;

   @QField()
   private Boolean autoConfirmLowRisk;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantUserPreference()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantUserPreference(QRecord record) throws QException
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
   public AssistantUserPreference withId(Integer id)
   {
      this.id = id;
      return (this);
   }


   /*******************************************************************************
    ** Getter for userId
    *******************************************************************************/
   public String getUserId()
   {
      return (this.userId);
   }



   /*******************************************************************************
    ** Setter for userId
    *******************************************************************************/
   public void setUserId(String userId)
   {
      this.userId = userId;
   }



   /*******************************************************************************
    ** Fluent setter for userId
    *******************************************************************************/
   public AssistantUserPreference withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }


   /*******************************************************************************
    ** Getter for defaultWriteMode
    *******************************************************************************/
   public Boolean getDefaultWriteMode()
   {
      return (this.defaultWriteMode);
   }



   /*******************************************************************************
    ** Setter for defaultWriteMode
    *******************************************************************************/
   public void setDefaultWriteMode(Boolean defaultWriteMode)
   {
      this.defaultWriteMode = defaultWriteMode;
   }



   /*******************************************************************************
    ** Fluent setter for defaultWriteMode
    *******************************************************************************/
   public AssistantUserPreference withDefaultWriteMode(Boolean defaultWriteMode)
   {
      this.defaultWriteMode = defaultWriteMode;
      return (this);
   }


   /*******************************************************************************
    ** Getter for autoConfirmLowRisk
    *******************************************************************************/
   public Boolean getAutoConfirmLowRisk()
   {
      return (this.autoConfirmLowRisk);
   }



   /*******************************************************************************
    ** Setter for autoConfirmLowRisk
    *******************************************************************************/
   public void setAutoConfirmLowRisk(Boolean autoConfirmLowRisk)
   {
      this.autoConfirmLowRisk = autoConfirmLowRisk;
   }



   /*******************************************************************************
    ** Fluent setter for autoConfirmLowRisk
    *******************************************************************************/
   public AssistantUserPreference withAutoConfirmLowRisk(Boolean autoConfirmLowRisk)
   {
      this.autoConfirmLowRisk = autoConfirmLowRisk;
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
   public AssistantUserPreference withCreateDate(Instant createDate)
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
   public AssistantUserPreference withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
