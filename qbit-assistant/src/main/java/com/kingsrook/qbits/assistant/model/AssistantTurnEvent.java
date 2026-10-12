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
 ** One streamed event of a turn (the SSE stream), kept for
 ** GET /turns/{id}/events?after= replay.
 *******************************************************************************/
public class AssistantTurnEvent extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_turn_event";

   /*******************************************************************************
    ** The table name before 0.1.0-RC.2 (camelCase). See UPGRADE.md.
    *******************************************************************************/
   @Deprecated(since = "0.1.0-RC.2")
   public static final String LEGACY_TABLE_NAME = "assistantTurnEvent";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false)
   private Integer turnId;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField(isEditable = false)
   private Integer seq;

   @QField(isEditable = false, maxLength = 32)
   private String eventType;

   @QField(isEditable = false)
   private String dataJson;

   @QField(isEditable = false)
   private Instant createDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantTurnEvent()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantTurnEvent(QRecord record) throws QException
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
   public AssistantTurnEvent withId(Integer id)
   {
      this.id = id;
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
   public AssistantTurnEvent withTurnId(Integer turnId)
   {
      this.turnId = turnId;
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
   public AssistantTurnEvent withUserId(String userId)
   {
      this.userId = userId;
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
   public AssistantTurnEvent withSeq(Integer seq)
   {
      this.seq = seq;
      return (this);
   }



   /*******************************************************************************
    ** Getter for eventType
    *******************************************************************************/
   public String getEventType()
   {
      return (this.eventType);
   }



   /*******************************************************************************
    ** Setter for eventType
    *******************************************************************************/
   public void setEventType(String eventType)
   {
      this.eventType = eventType;
   }



   /*******************************************************************************
    ** Fluent setter for eventType
    *******************************************************************************/
   public AssistantTurnEvent withEventType(String eventType)
   {
      this.eventType = eventType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for dataJson
    *******************************************************************************/
   public String getDataJson()
   {
      return (this.dataJson);
   }



   /*******************************************************************************
    ** Setter for dataJson
    *******************************************************************************/
   public void setDataJson(String dataJson)
   {
      this.dataJson = dataJson;
   }



   /*******************************************************************************
    ** Fluent setter for dataJson
    *******************************************************************************/
   public AssistantTurnEvent withDataJson(String dataJson)
   {
      this.dataJson = dataJson;
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
   public AssistantTurnEvent withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }

}
