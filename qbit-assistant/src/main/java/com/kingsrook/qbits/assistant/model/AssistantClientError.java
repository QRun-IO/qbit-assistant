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
 ** An error the panel (Next UI) hit while showing the assistant, reported by
 ** the user's browser through POST /assistant/client-errors. Only bounded,
 ** scrubbed fields are kept (no free text beyond them); the ids are kept only
 ** when they are the reporting user's own rows.
 *******************************************************************************/
public class AssistantClientError extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_client_error";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField(isEditable = false, maxLength = 64)
   private String scope;

   @QField(isEditable = false)
   private String message;

   @QField(isEditable = false, maxLength = 100)
   private String errorName;

   @QField(isEditable = false)
   private String stack;

   @QField(isEditable = false)
   private String componentStack;

   @QField(isEditable = false, maxLength = 128)
   private String requestId;

   @QField(isEditable = false, maxLength = 300)
   private String route;

   @QField(isEditable = false, possibleValueSourceName = AssistantConversation.TABLE_NAME)
   private Integer conversationId;

   @QField(isEditable = false)
   private Integer messageId;

   @QField(isEditable = false)
   private Integer turnId;

   @QField(isEditable = false, maxLength = 64)
   private String partType;

   @QField(isEditable = false, maxLength = 64)
   private String renderType;

   @QField(isEditable = false, maxLength = 64)
   private String appVersion;

   @QField(isEditable = false)
   private Instant occurredAt;

   @QField(isEditable = false)
   private Instant createDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantClientError()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantClientError(QRecord record) throws QException
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
   public AssistantClientError withId(Integer id)
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
   public AssistantClientError withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for scope
    *******************************************************************************/
   public String getScope()
   {
      return (this.scope);
   }



   /*******************************************************************************
    ** Setter for scope
    *******************************************************************************/
   public void setScope(String scope)
   {
      this.scope = scope;
   }



   /*******************************************************************************
    ** Fluent setter for scope
    *******************************************************************************/
   public AssistantClientError withScope(String scope)
   {
      this.scope = scope;
      return (this);
   }



   /*******************************************************************************
    ** Getter for message
    *******************************************************************************/
   public String getMessage()
   {
      return (this.message);
   }



   /*******************************************************************************
    ** Setter for message
    *******************************************************************************/
   public void setMessage(String message)
   {
      this.message = message;
   }



   /*******************************************************************************
    ** Fluent setter for message
    *******************************************************************************/
   public AssistantClientError withMessage(String message)
   {
      this.message = message;
      return (this);
   }



   /*******************************************************************************
    ** Getter for errorName
    *******************************************************************************/
   public String getErrorName()
   {
      return (this.errorName);
   }



   /*******************************************************************************
    ** Setter for errorName
    *******************************************************************************/
   public void setErrorName(String errorName)
   {
      this.errorName = errorName;
   }



   /*******************************************************************************
    ** Fluent setter for errorName
    *******************************************************************************/
   public AssistantClientError withErrorName(String errorName)
   {
      this.errorName = errorName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for stack
    *******************************************************************************/
   public String getStack()
   {
      return (this.stack);
   }



   /*******************************************************************************
    ** Setter for stack
    *******************************************************************************/
   public void setStack(String stack)
   {
      this.stack = stack;
   }



   /*******************************************************************************
    ** Fluent setter for stack
    *******************************************************************************/
   public AssistantClientError withStack(String stack)
   {
      this.stack = stack;
      return (this);
   }



   /*******************************************************************************
    ** Getter for componentStack
    *******************************************************************************/
   public String getComponentStack()
   {
      return (this.componentStack);
   }



   /*******************************************************************************
    ** Setter for componentStack
    *******************************************************************************/
   public void setComponentStack(String componentStack)
   {
      this.componentStack = componentStack;
   }



   /*******************************************************************************
    ** Fluent setter for componentStack
    *******************************************************************************/
   public AssistantClientError withComponentStack(String componentStack)
   {
      this.componentStack = componentStack;
      return (this);
   }



   /*******************************************************************************
    ** Getter for requestId
    *******************************************************************************/
   public String getRequestId()
   {
      return (this.requestId);
   }



   /*******************************************************************************
    ** Setter for requestId
    *******************************************************************************/
   public void setRequestId(String requestId)
   {
      this.requestId = requestId;
   }



   /*******************************************************************************
    ** Fluent setter for requestId
    *******************************************************************************/
   public AssistantClientError withRequestId(String requestId)
   {
      this.requestId = requestId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for route
    *******************************************************************************/
   public String getRoute()
   {
      return (this.route);
   }



   /*******************************************************************************
    ** Setter for route
    *******************************************************************************/
   public void setRoute(String route)
   {
      this.route = route;
   }



   /*******************************************************************************
    ** Fluent setter for route
    *******************************************************************************/
   public AssistantClientError withRoute(String route)
   {
      this.route = route;
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
   public AssistantClientError withConversationId(Integer conversationId)
   {
      this.conversationId = conversationId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for messageId
    *******************************************************************************/
   public Integer getMessageId()
   {
      return (this.messageId);
   }



   /*******************************************************************************
    ** Setter for messageId
    *******************************************************************************/
   public void setMessageId(Integer messageId)
   {
      this.messageId = messageId;
   }



   /*******************************************************************************
    ** Fluent setter for messageId
    *******************************************************************************/
   public AssistantClientError withMessageId(Integer messageId)
   {
      this.messageId = messageId;
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
   public AssistantClientError withTurnId(Integer turnId)
   {
      this.turnId = turnId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for partType
    *******************************************************************************/
   public String getPartType()
   {
      return (this.partType);
   }



   /*******************************************************************************
    ** Setter for partType
    *******************************************************************************/
   public void setPartType(String partType)
   {
      this.partType = partType;
   }



   /*******************************************************************************
    ** Fluent setter for partType
    *******************************************************************************/
   public AssistantClientError withPartType(String partType)
   {
      this.partType = partType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for renderType
    *******************************************************************************/
   public String getRenderType()
   {
      return (this.renderType);
   }



   /*******************************************************************************
    ** Setter for renderType
    *******************************************************************************/
   public void setRenderType(String renderType)
   {
      this.renderType = renderType;
   }



   /*******************************************************************************
    ** Fluent setter for renderType
    *******************************************************************************/
   public AssistantClientError withRenderType(String renderType)
   {
      this.renderType = renderType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for appVersion
    *******************************************************************************/
   public String getAppVersion()
   {
      return (this.appVersion);
   }



   /*******************************************************************************
    ** Setter for appVersion
    *******************************************************************************/
   public void setAppVersion(String appVersion)
   {
      this.appVersion = appVersion;
   }



   /*******************************************************************************
    ** Fluent setter for appVersion
    *******************************************************************************/
   public AssistantClientError withAppVersion(String appVersion)
   {
      this.appVersion = appVersion;
      return (this);
   }



   /*******************************************************************************
    ** Getter for occurredAt
    *******************************************************************************/
   public Instant getOccurredAt()
   {
      return (this.occurredAt);
   }



   /*******************************************************************************
    ** Setter for occurredAt
    *******************************************************************************/
   public void setOccurredAt(Instant occurredAt)
   {
      this.occurredAt = occurredAt;
   }



   /*******************************************************************************
    ** Fluent setter for occurredAt
    *******************************************************************************/
   public AssistantClientError withOccurredAt(Instant occurredAt)
   {
      this.occurredAt = occurredAt;
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
   public AssistantClientError withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }

}
