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
 ** The assistant's data access for one table or process: NONE (never), READ (read tools) or READ_WRITE (read and write tools). The assistant still acts with the user's own session, so this only ever narrows what the user may do. Tables and processes without a row use the default (NONE for sensitive tables, READ for other tables, NONE for processes).
 *******************************************************************************/
public class AssistantDataAccess extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_data_access";
   public static final String TYPE_TABLE = "TABLE";
   public static final String TYPE_PROCESS = "PROCESS";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 16, possibleValueSourceName = "assistant_object_type")
   private String objectType;

   @QField(isRequired = true, maxLength = 128)
   private String objectName;

   @QField(isRequired = true, maxLength = 16, possibleValueSourceName = "assistant_data_access_mode")
   private String mode;

   @QField(maxLength = 500, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String notes;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantDataAccess()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantDataAccess(QRecord record) throws QException
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
   public AssistantDataAccess withId(Integer id)
   {
      this.id = id;
      return (this);
   }


   /*******************************************************************************
    ** Getter for objectType
    *******************************************************************************/
   public String getObjectType()
   {
      return (this.objectType);
   }



   /*******************************************************************************
    ** Setter for objectType
    *******************************************************************************/
   public void setObjectType(String objectType)
   {
      this.objectType = objectType;
   }



   /*******************************************************************************
    ** Fluent setter for objectType
    *******************************************************************************/
   public AssistantDataAccess withObjectType(String objectType)
   {
      this.objectType = objectType;
      return (this);
   }


   /*******************************************************************************
    ** Getter for objectName
    *******************************************************************************/
   public String getObjectName()
   {
      return (this.objectName);
   }



   /*******************************************************************************
    ** Setter for objectName
    *******************************************************************************/
   public void setObjectName(String objectName)
   {
      this.objectName = objectName;
   }



   /*******************************************************************************
    ** Fluent setter for objectName
    *******************************************************************************/
   public AssistantDataAccess withObjectName(String objectName)
   {
      this.objectName = objectName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for mode
    *******************************************************************************/
   public String getMode()
   {
      return (this.mode);
   }



   /*******************************************************************************
    ** Setter for mode
    *******************************************************************************/
   public void setMode(String mode)
   {
      this.mode = mode;
   }



   /*******************************************************************************
    ** Fluent setter for mode
    *******************************************************************************/
   public AssistantDataAccess withMode(String mode)
   {
      this.mode = mode;
      return (this);
   }


   /*******************************************************************************
    ** Getter for notes
    *******************************************************************************/
   public String getNotes()
   {
      return (this.notes);
   }



   /*******************************************************************************
    ** Setter for notes
    *******************************************************************************/
   public void setNotes(String notes)
   {
      this.notes = notes;
   }



   /*******************************************************************************
    ** Fluent setter for notes
    *******************************************************************************/
   public AssistantDataAccess withNotes(String notes)
   {
      this.notes = notes;
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
   public AssistantDataAccess withCreateDate(Instant createDate)
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
   public AssistantDataAccess withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
