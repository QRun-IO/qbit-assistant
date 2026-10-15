/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
 ** Scheduled assistant bookkeeping; credentials are references only.
 *******************************************************************************/
public class AssistantScheduledRun extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_scheduled_run";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true)
   private Integer taskId;

   @QField(isRequired = true, maxLength = 200)
   private String userId;

   @QField()
   private Instant scheduledFor;

   @QField()
   private Instant startedAt;

   @QField()
   private Instant endedAt;

   @QField(maxLength = 20)
   private String status;

   @QField()
   private Integer conversationId;

   @QField()
   private Integer turnId;

   @QField()
   private String summary;

   @QField()
   private Long costMicros;

   @QField()
   private String error;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Empty entity for framework mapping.
    *******************************************************************************/
   public AssistantScheduledRun()
   {
   }



   /*******************************************************************************
    ** Map a bookkeeping record.
    *******************************************************************************/
   public AssistantScheduledRun(QRecord record) throws QException
   {
      populateFromQRecord(record);
   }



   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (id);
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
   public AssistantScheduledRun withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for taskId
    *******************************************************************************/
   public Integer getTaskId()
   {
      return (taskId);
   }



   /*******************************************************************************
    ** Setter for taskId
    *******************************************************************************/
   public void setTaskId(Integer taskId)
   {
      this.taskId = taskId;
   }



   /*******************************************************************************
    ** Fluent setter for taskId
    *******************************************************************************/
   public AssistantScheduledRun withTaskId(Integer taskId)
   {
      this.taskId = taskId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userId
    *******************************************************************************/
   public String getUserId()
   {
      return (userId);
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
   public AssistantScheduledRun withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for scheduledFor
    *******************************************************************************/
   public Instant getScheduledFor()
   {
      return (scheduledFor);
   }



   /*******************************************************************************
    ** Setter for scheduledFor
    *******************************************************************************/
   public void setScheduledFor(Instant scheduledFor)
   {
      this.scheduledFor = scheduledFor;
   }



   /*******************************************************************************
    ** Fluent setter for scheduledFor
    *******************************************************************************/
   public AssistantScheduledRun withScheduledFor(Instant scheduledFor)
   {
      this.scheduledFor = scheduledFor;
      return (this);
   }



   /*******************************************************************************
    ** Getter for startedAt
    *******************************************************************************/
   public Instant getStartedAt()
   {
      return (startedAt);
   }



   /*******************************************************************************
    ** Setter for startedAt
    *******************************************************************************/
   public void setStartedAt(Instant startedAt)
   {
      this.startedAt = startedAt;
   }



   /*******************************************************************************
    ** Fluent setter for startedAt
    *******************************************************************************/
   public AssistantScheduledRun withStartedAt(Instant startedAt)
   {
      this.startedAt = startedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for endedAt
    *******************************************************************************/
   public Instant getEndedAt()
   {
      return (endedAt);
   }



   /*******************************************************************************
    ** Setter for endedAt
    *******************************************************************************/
   public void setEndedAt(Instant endedAt)
   {
      this.endedAt = endedAt;
   }



   /*******************************************************************************
    ** Fluent setter for endedAt
    *******************************************************************************/
   public AssistantScheduledRun withEndedAt(Instant endedAt)
   {
      this.endedAt = endedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for status
    *******************************************************************************/
   public String getStatus()
   {
      return (status);
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
   public AssistantScheduledRun withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for conversationId
    *******************************************************************************/
   public Integer getConversationId()
   {
      return (conversationId);
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
   public AssistantScheduledRun withConversationId(Integer conversationId)
   {
      this.conversationId = conversationId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for turnId
    *******************************************************************************/
   public Integer getTurnId()
   {
      return (turnId);
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
   public AssistantScheduledRun withTurnId(Integer turnId)
   {
      this.turnId = turnId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for summary
    *******************************************************************************/
   public String getSummary()
   {
      return (summary);
   }



   /*******************************************************************************
    ** Setter for summary
    *******************************************************************************/
   public void setSummary(String summary)
   {
      this.summary = summary;
   }



   /*******************************************************************************
    ** Fluent setter for summary
    *******************************************************************************/
   public AssistantScheduledRun withSummary(String summary)
   {
      this.summary = summary;
      return (this);
   }



   /*******************************************************************************
    ** Getter for costMicros
    *******************************************************************************/
   public Long getCostMicros()
   {
      return (costMicros);
   }



   /*******************************************************************************
    ** Setter for costMicros
    *******************************************************************************/
   public void setCostMicros(Long costMicros)
   {
      this.costMicros = costMicros;
   }



   /*******************************************************************************
    ** Fluent setter for costMicros
    *******************************************************************************/
   public AssistantScheduledRun withCostMicros(Long costMicros)
   {
      this.costMicros = costMicros;
      return (this);
   }



   /*******************************************************************************
    ** Getter for error
    *******************************************************************************/
   public String getError()
   {
      return (error);
   }



   /*******************************************************************************
    ** Setter for error
    *******************************************************************************/
   public void setError(String error)
   {
      this.error = error;
   }



   /*******************************************************************************
    ** Fluent setter for error
    *******************************************************************************/
   public AssistantScheduledRun withError(String error)
   {
      this.error = error;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (createDate);
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
   public AssistantScheduledRun withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (modifyDate);
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
   public AssistantScheduledRun withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
