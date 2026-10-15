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
 ** Audit of one tool call, including denied ones: sanitized input, outcome,
 ** permission check, size, duration, redactions. resultJson (what the model
 ** saw) has a shorter retention than the row.
 *******************************************************************************/
public class AssistantToolCall extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_tool_call";

   /*******************************************************************************
    ** The table name before 0.1.0-RC.3 (camelCase). See UPGRADE.md.
    *******************************************************************************/
   @Deprecated(since = "0.1.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "assistantToolCall";

   @QField(isEditable = false, maxLength = 64)
   private String clientChannel;

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false)
   private Integer turnId;

   @QField(isEditable = false, possibleValueSourceName = AssistantConversation.TABLE_NAME)
   private Integer conversationId;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField(isEditable = false)
   private String toolCallId;

   @QField(isEditable = false, maxLength = 64)
   private String toolName;

   @QField(isEditable = false)
   private String inputJson;

   @QField(isEditable = false, maxLength = 16)
   private String outcome;

   @QField(isEditable = false, maxLength = 255, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String permissionCheck;

   @QField(isEditable = false, maxLength = 128)
   private String tableName;

   @QField(isEditable = false, maxLength = 128)
   private String processName;

   @QField(isEditable = false, maxLength = 128)
   private String widgetName;

   @QField(isEditable = false)
   private Integer rowCount;

   @QField(isEditable = false)
   private Long durationMs;

   @QField(isEditable = false)
   private String resultSummary;

   @QField(isEditable = false)
   private String resultJson;

   @QField(isEditable = false)
   private String redactionsJson;

   @QField(isEditable = false, maxLength = 255)
   private String uiApiOperation;

   @QField(isEditable = false)
   private Integer httpStatus;

   @QField(isEditable = false)
   private String auditHref;

   @QField(isEditable = false, possibleValueSourceName = AssistantPendingAction.TABLE_NAME)
   private Integer pendingActionId;

   @QField(isEditable = false)
   private Instant createDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantToolCall()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantToolCall(QRecord record) throws QException
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
   public AssistantToolCall withId(Integer id)
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
   public AssistantToolCall withTurnId(Integer turnId)
   {
      this.turnId = turnId;
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
   public AssistantToolCall withConversationId(Integer conversationId)
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
   public AssistantToolCall withUserId(String userId)
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
   public AssistantToolCall withToolCallId(String toolCallId)
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
   public AssistantToolCall withToolName(String toolName)
   {
      this.toolName = toolName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for inputJson
    *******************************************************************************/
   public String getInputJson()
   {
      return (this.inputJson);
   }



   /*******************************************************************************
    ** Setter for inputJson
    *******************************************************************************/
   public void setInputJson(String inputJson)
   {
      this.inputJson = inputJson;
   }



   /*******************************************************************************
    ** Fluent setter for inputJson
    *******************************************************************************/
   public AssistantToolCall withInputJson(String inputJson)
   {
      this.inputJson = inputJson;
      return (this);
   }



   /*******************************************************************************
    ** Getter for outcome
    *******************************************************************************/
   public String getOutcome()
   {
      return (this.outcome);
   }



   /*******************************************************************************
    ** Setter for outcome
    *******************************************************************************/
   public void setOutcome(String outcome)
   {
      this.outcome = outcome;
   }



   /*******************************************************************************
    ** Fluent setter for outcome
    *******************************************************************************/
   public AssistantToolCall withOutcome(String outcome)
   {
      this.outcome = outcome;
      return (this);
   }



   /*******************************************************************************
    ** Getter for permissionCheck
    *******************************************************************************/
   public String getPermissionCheck()
   {
      return (this.permissionCheck);
   }



   /*******************************************************************************
    ** Setter for permissionCheck
    *******************************************************************************/
   public void setPermissionCheck(String permissionCheck)
   {
      this.permissionCheck = permissionCheck;
   }



   /*******************************************************************************
    ** Fluent setter for permissionCheck
    *******************************************************************************/
   public AssistantToolCall withPermissionCheck(String permissionCheck)
   {
      this.permissionCheck = permissionCheck;
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
   public AssistantToolCall withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for processName
    *******************************************************************************/
   public String getProcessName()
   {
      return (this.processName);
   }



   /*******************************************************************************
    ** Setter for processName
    *******************************************************************************/
   public void setProcessName(String processName)
   {
      this.processName = processName;
   }



   /*******************************************************************************
    ** Fluent setter for processName
    *******************************************************************************/
   public AssistantToolCall withProcessName(String processName)
   {
      this.processName = processName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for widgetName
    *******************************************************************************/
   public String getWidgetName()
   {
      return (this.widgetName);
   }



   /*******************************************************************************
    ** Setter for widgetName
    *******************************************************************************/
   public void setWidgetName(String widgetName)
   {
      this.widgetName = widgetName;
   }



   /*******************************************************************************
    ** Fluent setter for widgetName
    *******************************************************************************/
   public AssistantToolCall withWidgetName(String widgetName)
   {
      this.widgetName = widgetName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for rowCount
    *******************************************************************************/
   public Integer getRowCount()
   {
      return (this.rowCount);
   }



   /*******************************************************************************
    ** Setter for rowCount
    *******************************************************************************/
   public void setRowCount(Integer rowCount)
   {
      this.rowCount = rowCount;
   }



   /*******************************************************************************
    ** Fluent setter for rowCount
    *******************************************************************************/
   public AssistantToolCall withRowCount(Integer rowCount)
   {
      this.rowCount = rowCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for durationMs
    *******************************************************************************/
   public Long getDurationMs()
   {
      return (this.durationMs);
   }



   /*******************************************************************************
    ** Setter for durationMs
    *******************************************************************************/
   public void setDurationMs(Long durationMs)
   {
      this.durationMs = durationMs;
   }



   /*******************************************************************************
    ** Fluent setter for durationMs
    *******************************************************************************/
   public AssistantToolCall withDurationMs(Long durationMs)
   {
      this.durationMs = durationMs;
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
   public AssistantToolCall withResultSummary(String resultSummary)
   {
      this.resultSummary = resultSummary;
      return (this);
   }



   /*******************************************************************************
    ** Getter for resultJson
    *******************************************************************************/
   public String getResultJson()
   {
      return (this.resultJson);
   }



   /*******************************************************************************
    ** Setter for resultJson
    *******************************************************************************/
   public void setResultJson(String resultJson)
   {
      this.resultJson = resultJson;
   }



   /*******************************************************************************
    ** Fluent setter for resultJson
    *******************************************************************************/
   public AssistantToolCall withResultJson(String resultJson)
   {
      this.resultJson = resultJson;
      return (this);
   }



   /*******************************************************************************
    ** Getter for redactionsJson
    *******************************************************************************/
   public String getRedactionsJson()
   {
      return (this.redactionsJson);
   }



   /*******************************************************************************
    ** Setter for redactionsJson
    *******************************************************************************/
   public void setRedactionsJson(String redactionsJson)
   {
      this.redactionsJson = redactionsJson;
   }



   /*******************************************************************************
    ** Fluent setter for redactionsJson
    *******************************************************************************/
   public AssistantToolCall withRedactionsJson(String redactionsJson)
   {
      this.redactionsJson = redactionsJson;
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
   public AssistantToolCall withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }


   /*******************************************************************************
    ** Getter for uiApiOperation (the /qqq/v1 operation the tool ran, e.g. POST /table/{tableName}/query)
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
   public AssistantToolCall withUiApiOperation(String uiApiOperation)
   {
      this.uiApiOperation = uiApiOperation;
      return (this);
   }


   /*******************************************************************************
    ** Getter for httpStatus (the operation's HTTP status, as the UI would have seen it)
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
   public AssistantToolCall withHttpStatus(Integer httpStatus)
   {
      this.httpStatus = httpStatus;
      return (this);
   }


   /*******************************************************************************
    ** Getter for auditHref (an in-app link to the audit entries of the records a write touched)
    *******************************************************************************/
   public String getAuditHref()
   {
      return (this.auditHref);
   }



   /*******************************************************************************
    ** Setter for auditHref
    *******************************************************************************/
   public void setAuditHref(String auditHref)
   {
      this.auditHref = auditHref;
   }



   /*******************************************************************************
    ** Fluent setter for auditHref
    *******************************************************************************/
   public AssistantToolCall withAuditHref(String auditHref)
   {
      this.auditHref = auditHref;
      return (this);
   }


   /*******************************************************************************
    ** Getter for pendingActionId
    *******************************************************************************/
   public Integer getPendingActionId()
   {
      return (this.pendingActionId);
   }



   /*******************************************************************************
    ** Setter for pendingActionId
    *******************************************************************************/
   public void setPendingActionId(Integer pendingActionId)
   {
      this.pendingActionId = pendingActionId;
   }



   /*******************************************************************************
    ** Fluent setter for pendingActionId
    *******************************************************************************/
   public AssistantToolCall withPendingActionId(Integer pendingActionId)
   {
      this.pendingActionId = pendingActionId;
      return (this);
   }




   /*******************************************************************************
    ** Getter for clientChannel
    *******************************************************************************/
   public String getClientChannel()
   {
      return (clientChannel);
   }



   /*******************************************************************************
    ** Setter for clientChannel
    *******************************************************************************/
   public void setClientChannel(String clientChannel)
   {
      this.clientChannel = clientChannel;
   }



   /*******************************************************************************
    ** Fluent setter for clientChannel
    *******************************************************************************/
   public AssistantToolCall withClientChannel(String clientChannel)
   {
      this.clientChannel = clientChannel;
      return (this);
   }
}
