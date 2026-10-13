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
 ** A write the assistant proposed and the user must confirm. Confirming runs the stored UI API operation with the confirming user's own session; nothing runs before that.
 *******************************************************************************/
public class AssistantPendingAction extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_pending_action";
   public static final String STATUS_PENDING = "PENDING";
   public static final String STATUS_EXECUTED = "EXECUTED";
   public static final String STATUS_FAILED = "FAILED";
   public static final String STATUS_REJECTED = "REJECTED";
   public static final String STATUS_EXPIRED = "EXPIRED";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false, possibleValueSourceName = AssistantConversation.TABLE_NAME)
   private Integer conversationId;

   @QField(isEditable = false)
   private Integer turnId;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField(isEditable = false, maxLength = 128)
   private String toolCallId;

   @QField(isEditable = false, maxLength = 64)
   private String toolName;

   @QField(isEditable = false, maxLength = 255)
   private String uiApiOperation;

   @QField(isEditable = false, maxLength = 128)
   private String tableName;

   @QField(isEditable = false, maxLength = 128)
   private String recordId;

   @QField(isEditable = false)
   private String paramsJson;

   @QField(isEditable = false, maxLength = 500, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String summary;

   @QField(isEditable = false, maxLength = 16)
   private String status;

   @QField(isEditable = false)
   private Integer httpStatus;

   @QField(isEditable = false, maxLength = 500, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String resultSummary;

   @QField(isEditable = false)
   private Instant expiresAt;

   @QField(isEditable = false)
   private Instant decidedAt;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantPendingAction()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantPendingAction(QRecord record) throws QException
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
   public AssistantPendingAction withId(Integer id)
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
   public AssistantPendingAction withConversationId(Integer conversationId)
   {
      this.conversationId = conversationId;
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
   public AssistantPendingAction withTurnId(Integer turnId)
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
   public AssistantPendingAction withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }


   /*******************************************************************************
    ** Getter for toolCallId
    *******************************************************************************/
   public String getToolCallId()
   {
      return (this.toolCallId);
   }



   /*******************************************************************************
    ** Setter for toolCallId
    *******************************************************************************/
   public void setToolCallId(String toolCallId)
   {
      this.toolCallId = toolCallId;
   }



   /*******************************************************************************
    ** Fluent setter for toolCallId
    *******************************************************************************/
   public AssistantPendingAction withToolCallId(String toolCallId)
   {
      this.toolCallId = toolCallId;
      return (this);
   }


   /*******************************************************************************
    ** Getter for toolName
    *******************************************************************************/
   public String getToolName()
   {
      return (this.toolName);
   }



   /*******************************************************************************
    ** Setter for toolName
    *******************************************************************************/
   public void setToolName(String toolName)
   {
      this.toolName = toolName;
   }



   /*******************************************************************************
    ** Fluent setter for toolName
    *******************************************************************************/
   public AssistantPendingAction withToolName(String toolName)
   {
      this.toolName = toolName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for uiApiOperation
    *******************************************************************************/
   public String getUiApiOperation()
   {
      return (this.uiApiOperation);
   }



   /*******************************************************************************
    ** Setter for uiApiOperation
    *******************************************************************************/
   public void setUiApiOperation(String uiApiOperation)
   {
      this.uiApiOperation = uiApiOperation;
   }



   /*******************************************************************************
    ** Fluent setter for uiApiOperation
    *******************************************************************************/
   public AssistantPendingAction withUiApiOperation(String uiApiOperation)
   {
      this.uiApiOperation = uiApiOperation;
      return (this);
   }


   /*******************************************************************************
    ** Getter for tableName
    *******************************************************************************/
   public String getTableName()
   {
      return (this.tableName);
   }



   /*******************************************************************************
    ** Setter for tableName
    *******************************************************************************/
   public void setTableName(String tableName)
   {
      this.tableName = tableName;
   }



   /*******************************************************************************
    ** Fluent setter for tableName
    *******************************************************************************/
   public AssistantPendingAction withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for recordId
    *******************************************************************************/
   public String getRecordId()
   {
      return (this.recordId);
   }



   /*******************************************************************************
    ** Setter for recordId
    *******************************************************************************/
   public void setRecordId(String recordId)
   {
      this.recordId = recordId;
   }



   /*******************************************************************************
    ** Fluent setter for recordId
    *******************************************************************************/
   public AssistantPendingAction withRecordId(String recordId)
   {
      this.recordId = recordId;
      return (this);
   }


   /*******************************************************************************
    ** Getter for paramsJson
    *******************************************************************************/
   public String getParamsJson()
   {
      return (this.paramsJson);
   }



   /*******************************************************************************
    ** Setter for paramsJson
    *******************************************************************************/
   public void setParamsJson(String paramsJson)
   {
      this.paramsJson = paramsJson;
   }



   /*******************************************************************************
    ** Fluent setter for paramsJson
    *******************************************************************************/
   public AssistantPendingAction withParamsJson(String paramsJson)
   {
      this.paramsJson = paramsJson;
      return (this);
   }


   /*******************************************************************************
    ** Getter for summary
    *******************************************************************************/
   public String getSummary()
   {
      return (this.summary);
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
   public AssistantPendingAction withSummary(String summary)
   {
      this.summary = summary;
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
   public AssistantPendingAction withStatus(String status)
   {
      this.status = status;
      return (this);
   }


   /*******************************************************************************
    ** Getter for httpStatus
    *******************************************************************************/
   public Integer getHttpStatus()
   {
      return (this.httpStatus);
   }



   /*******************************************************************************
    ** Setter for httpStatus
    *******************************************************************************/
   public void setHttpStatus(Integer httpStatus)
   {
      this.httpStatus = httpStatus;
   }



   /*******************************************************************************
    ** Fluent setter for httpStatus
    *******************************************************************************/
   public AssistantPendingAction withHttpStatus(Integer httpStatus)
   {
      this.httpStatus = httpStatus;
      return (this);
   }


   /*******************************************************************************
    ** Getter for resultSummary
    *******************************************************************************/
   public String getResultSummary()
   {
      return (this.resultSummary);
   }



   /*******************************************************************************
    ** Setter for resultSummary
    *******************************************************************************/
   public void setResultSummary(String resultSummary)
   {
      this.resultSummary = resultSummary;
   }



   /*******************************************************************************
    ** Fluent setter for resultSummary
    *******************************************************************************/
   public AssistantPendingAction withResultSummary(String resultSummary)
   {
      this.resultSummary = resultSummary;
      return (this);
   }


   /*******************************************************************************
    ** Getter for expiresAt
    *******************************************************************************/
   public Instant getExpiresAt()
   {
      return (this.expiresAt);
   }



   /*******************************************************************************
    ** Setter for expiresAt
    *******************************************************************************/
   public void setExpiresAt(Instant expiresAt)
   {
      this.expiresAt = expiresAt;
   }



   /*******************************************************************************
    ** Fluent setter for expiresAt
    *******************************************************************************/
   public AssistantPendingAction withExpiresAt(Instant expiresAt)
   {
      this.expiresAt = expiresAt;
      return (this);
   }


   /*******************************************************************************
    ** Getter for decidedAt
    *******************************************************************************/
   public Instant getDecidedAt()
   {
      return (this.decidedAt);
   }



   /*******************************************************************************
    ** Setter for decidedAt
    *******************************************************************************/
   public void setDecidedAt(Instant decidedAt)
   {
      this.decidedAt = decidedAt;
   }



   /*******************************************************************************
    ** Fluent setter for decidedAt
    *******************************************************************************/
   public AssistantPendingAction withDecidedAt(Instant decidedAt)
   {
      this.decidedAt = decidedAt;
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
   public AssistantPendingAction withCreateDate(Instant createDate)
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
   public AssistantPendingAction withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
