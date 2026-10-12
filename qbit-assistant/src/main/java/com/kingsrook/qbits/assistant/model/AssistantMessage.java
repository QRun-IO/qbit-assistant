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
 ** One message of a conversation. partsJson holds the UI parts (text, tool
 ** steps with render payloads, record refs, navigation, notices) so a
 ** conversation re-opens exactly as it was shown.
 *******************************************************************************/
public class AssistantMessage extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_message";

   /*******************************************************************************
    ** The table name before 0.1.0-RC.2 (camelCase). See UPGRADE.md.
    *******************************************************************************/
   @Deprecated(since = "0.1.0-RC.2")
   public static final String LEGACY_TABLE_NAME = "assistantMessage";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false, possibleValueSourceName = AssistantConversation.TABLE_NAME)
   private Integer conversationId;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField(isEditable = false)
   private Integer parentMessageId;

   @QField(isEditable = false, maxLength = 16)
   private String role;

   @QField(isEditable = false, maxLength = 16)
   private String status;

   @QField(isEditable = false)
   private Integer turnId;

   @QField(isEditable = false)
   private Integer seq;

   @QField(isEditable = false, maxLength = 64)
   private String model;

   @QField(isEditable = false)
   private String partsJson;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantMessage()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantMessage(QRecord record) throws QException
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
   public AssistantMessage withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for conversationId
    *******************************************************************************/
   public Integer getConversationId()
   {
      return (this.conversationId);
   }



   /*******************************************************************************
    ** Setter for conversationId
    *******************************************************************************/
   public void setConversationId(Integer conversationId)
   {
      this.conversationId = conversationId;
   }



   /*******************************************************************************
    ** Fluent setter for conversationId
    *******************************************************************************/
   public AssistantMessage withConversationId(Integer conversationId)
   {
      this.conversationId = conversationId;
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
   public AssistantMessage withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for parentMessageId
    *******************************************************************************/
   public Integer getParentMessageId()
   {
      return (this.parentMessageId);
   }



   /*******************************************************************************
    ** Setter for parentMessageId
    *******************************************************************************/
   public void setParentMessageId(Integer parentMessageId)
   {
      this.parentMessageId = parentMessageId;
   }



   /*******************************************************************************
    ** Fluent setter for parentMessageId
    *******************************************************************************/
   public AssistantMessage withParentMessageId(Integer parentMessageId)
   {
      this.parentMessageId = parentMessageId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for role
    *******************************************************************************/
   public String getRole()
   {
      return (this.role);
   }



   /*******************************************************************************
    ** Setter for role
    *******************************************************************************/
   public void setRole(String role)
   {
      this.role = role;
   }



   /*******************************************************************************
    ** Fluent setter for role
    *******************************************************************************/
   public AssistantMessage withRole(String role)
   {
      this.role = role;
      return (this);
   }



   /*******************************************************************************
    ** Getter for status
    *******************************************************************************/
   public String getStatus()
   {
      return (this.status);
   }



   /*******************************************************************************
    ** Setter for status
    *******************************************************************************/
   public void setStatus(String status)
   {
      this.status = status;
   }



   /*******************************************************************************
    ** Fluent setter for status
    *******************************************************************************/
   public AssistantMessage withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for turnId
    *******************************************************************************/
   public Integer getTurnId()
   {
      return (this.turnId);
   }



   /*******************************************************************************
    ** Setter for turnId
    *******************************************************************************/
   public void setTurnId(Integer turnId)
   {
      this.turnId = turnId;
   }



   /*******************************************************************************
    ** Fluent setter for turnId
    *******************************************************************************/
   public AssistantMessage withTurnId(Integer turnId)
   {
      this.turnId = turnId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for seq
    *******************************************************************************/
   public Integer getSeq()
   {
      return (this.seq);
   }



   /*******************************************************************************
    ** Setter for seq
    *******************************************************************************/
   public void setSeq(Integer seq)
   {
      this.seq = seq;
   }



   /*******************************************************************************
    ** Fluent setter for seq
    *******************************************************************************/
   public AssistantMessage withSeq(Integer seq)
   {
      this.seq = seq;
      return (this);
   }



   /*******************************************************************************
    ** Getter for model
    *******************************************************************************/
   public String getModel()
   {
      return (this.model);
   }



   /*******************************************************************************
    ** Setter for model
    *******************************************************************************/
   public void setModel(String model)
   {
      this.model = model;
   }



   /*******************************************************************************
    ** Fluent setter for model
    *******************************************************************************/
   public AssistantMessage withModel(String model)
   {
      this.model = model;
      return (this);
   }



   /*******************************************************************************
    ** Getter for partsJson
    *******************************************************************************/
   public String getPartsJson()
   {
      return (this.partsJson);
   }



   /*******************************************************************************
    ** Setter for partsJson
    *******************************************************************************/
   public void setPartsJson(String partsJson)
   {
      this.partsJson = partsJson;
   }



   /*******************************************************************************
    ** Fluent setter for partsJson
    *******************************************************************************/
   public AssistantMessage withPartsJson(String partsJson)
   {
      this.partsJson = partsJson;
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
   public AssistantMessage withCreateDate(Instant createDate)
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
   public AssistantMessage withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
