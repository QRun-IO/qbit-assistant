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
 ** One model call: who, which provider and model, what for (a TURN or a
 ** connection TEST), how it ended, how long it took, the tokens and the cost.
 ** Written by the qbit only.
 *******************************************************************************/
public class AssistantUsage extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_usage";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField(isEditable = false)
   private Integer conversationId;

   @QField(isEditable = false)
   private Integer turnId;

   @QField(isEditable = false)
   private Integer providerId;

   @QField(isEditable = false, maxLength = 50)
   private String providerType;

   @QField(isEditable = false, maxLength = 200)
   private String model;

   @QField(isEditable = false, maxLength = 16)
   private String purpose;

   @QField(isEditable = false, maxLength = 16)
   private String status;

   @QField(isEditable = false, maxLength = 32)
   private String errorKind;

   @QField(isEditable = false)
   private Integer httpStatus;

   @QField(isEditable = false)
   private Integer latencyMs;

   @QField(isEditable = false)
   private Long inputTokens;

   @QField(isEditable = false)
   private Long outputTokens;

   @QField(isEditable = false)
   private Long cacheReadTokens;

   @QField(isEditable = false)
   private Long cacheWriteTokens;

   @QField(isEditable = false)
   private Long costMicros;

   @QField(isEditable = false, maxLength = 100)
   private String requestId;

   @QField(isEditable = false)
   private Instant createDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantUsage()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantUsage(QRecord record) throws QException
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
   public AssistantUsage withId(Integer id)
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
   public AssistantUsage withUserId(String userId)
   {
      this.userId = userId;
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
   public AssistantUsage withConversationId(Integer conversationId)
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
   public AssistantUsage withTurnId(Integer turnId)
   {
      this.turnId = turnId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for providerId
    *******************************************************************************/
   public Integer getProviderId()
   {
      return (this.providerId);
   }



   /*******************************************************************************
    ** Setter for providerId
    *******************************************************************************/
   public void setProviderId(Integer providerId)
   {
      this.providerId = providerId;
   }



   /*******************************************************************************
    ** Fluent setter for providerId
    *******************************************************************************/
   public AssistantUsage withProviderId(Integer providerId)
   {
      this.providerId = providerId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for providerType
    *******************************************************************************/
   public String getProviderType()
   {
      return (this.providerType);
   }



   /*******************************************************************************
    ** Setter for providerType
    *******************************************************************************/
   public void setProviderType(String providerType)
   {
      this.providerType = providerType;
   }



   /*******************************************************************************
    ** Fluent setter for providerType
    *******************************************************************************/
   public AssistantUsage withProviderType(String providerType)
   {
      this.providerType = providerType;
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
   public AssistantUsage withModel(String model)
   {
      this.model = model;
      return (this);
   }



   /*******************************************************************************
    ** Getter for purpose
    *******************************************************************************/
   public String getPurpose()
   {
      return (this.purpose);
   }



   /*******************************************************************************
    ** Setter for purpose
    *******************************************************************************/
   public void setPurpose(String purpose)
   {
      this.purpose = purpose;
   }



   /*******************************************************************************
    ** Fluent setter for purpose
    *******************************************************************************/
   public AssistantUsage withPurpose(String purpose)
   {
      this.purpose = purpose;
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
   public AssistantUsage withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for errorKind
    *******************************************************************************/
   public String getErrorKind()
   {
      return (this.errorKind);
   }



   /*******************************************************************************
    ** Setter for errorKind
    *******************************************************************************/
   public void setErrorKind(String errorKind)
   {
      this.errorKind = errorKind;
   }



   /*******************************************************************************
    ** Fluent setter for errorKind
    *******************************************************************************/
   public AssistantUsage withErrorKind(String errorKind)
   {
      this.errorKind = errorKind;
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
   public AssistantUsage withHttpStatus(Integer httpStatus)
   {
      this.httpStatus = httpStatus;
      return (this);
   }



   /*******************************************************************************
    ** Getter for latencyMs
    *******************************************************************************/
   public Integer getLatencyMs()
   {
      return (this.latencyMs);
   }



   /*******************************************************************************
    ** Setter for latencyMs
    *******************************************************************************/
   public void setLatencyMs(Integer latencyMs)
   {
      this.latencyMs = latencyMs;
   }



   /*******************************************************************************
    ** Fluent setter for latencyMs
    *******************************************************************************/
   public AssistantUsage withLatencyMs(Integer latencyMs)
   {
      this.latencyMs = latencyMs;
      return (this);
   }



   /*******************************************************************************
    ** Getter for inputTokens
    *******************************************************************************/
   public Long getInputTokens()
   {
      return (this.inputTokens);
   }



   /*******************************************************************************
    ** Setter for inputTokens
    *******************************************************************************/
   public void setInputTokens(Long inputTokens)
   {
      this.inputTokens = inputTokens;
   }



   /*******************************************************************************
    ** Fluent setter for inputTokens
    *******************************************************************************/
   public AssistantUsage withInputTokens(Long inputTokens)
   {
      this.inputTokens = inputTokens;
      return (this);
   }



   /*******************************************************************************
    ** Getter for outputTokens
    *******************************************************************************/
   public Long getOutputTokens()
   {
      return (this.outputTokens);
   }



   /*******************************************************************************
    ** Setter for outputTokens
    *******************************************************************************/
   public void setOutputTokens(Long outputTokens)
   {
      this.outputTokens = outputTokens;
   }



   /*******************************************************************************
    ** Fluent setter for outputTokens
    *******************************************************************************/
   public AssistantUsage withOutputTokens(Long outputTokens)
   {
      this.outputTokens = outputTokens;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheReadTokens
    *******************************************************************************/
   public Long getCacheReadTokens()
   {
      return (this.cacheReadTokens);
   }



   /*******************************************************************************
    ** Setter for cacheReadTokens
    *******************************************************************************/
   public void setCacheReadTokens(Long cacheReadTokens)
   {
      this.cacheReadTokens = cacheReadTokens;
   }



   /*******************************************************************************
    ** Fluent setter for cacheReadTokens
    *******************************************************************************/
   public AssistantUsage withCacheReadTokens(Long cacheReadTokens)
   {
      this.cacheReadTokens = cacheReadTokens;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheWriteTokens
    *******************************************************************************/
   public Long getCacheWriteTokens()
   {
      return (this.cacheWriteTokens);
   }



   /*******************************************************************************
    ** Setter for cacheWriteTokens
    *******************************************************************************/
   public void setCacheWriteTokens(Long cacheWriteTokens)
   {
      this.cacheWriteTokens = cacheWriteTokens;
   }



   /*******************************************************************************
    ** Fluent setter for cacheWriteTokens
    *******************************************************************************/
   public AssistantUsage withCacheWriteTokens(Long cacheWriteTokens)
   {
      this.cacheWriteTokens = cacheWriteTokens;
      return (this);
   }



   /*******************************************************************************
    ** Getter for costMicros
    *******************************************************************************/
   public Long getCostMicros()
   {
      return (this.costMicros);
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
   public AssistantUsage withCostMicros(Long costMicros)
   {
      this.costMicros = costMicros;
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
   public AssistantUsage withRequestId(String requestId)
   {
      this.requestId = requestId;
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
   public AssistantUsage withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }

}
