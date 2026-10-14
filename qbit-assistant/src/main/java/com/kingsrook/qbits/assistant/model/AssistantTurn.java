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
 ** One turn: a user message and the assistant's answer, with the model calls
 ** and tool calls in between. Holds the accounting (tokens, cost).
 *******************************************************************************/
public class AssistantTurn extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_turn";

   /*******************************************************************************
    ** The table name before 0.1.0-RC.3 (camelCase). See UPGRADE.md.
    *******************************************************************************/
   @Deprecated(since = "0.1.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "assistantTurn";

   @QField(isEditable = false, maxLength = 16)
   private String classification;

   @QField(isEditable = false, maxLength = 500)
   private String classificationTables;

   @QField(isEditable = false, maxLength = 16)
   private String routeDecision;

   @QField(isEditable = false, maxLength = 16)
   private String routeSource;

   @QField(isEditable = false)
   private Integer fallbackCount;

   @QField(isEditable = false)
   private Integer providerId;

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false, possibleValueSourceName = AssistantConversation.TABLE_NAME)
   private Integer conversationId;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField(isEditable = false)
   private Integer userMessageId;

   @QField(isEditable = false)
   private Integer assistantMessageId;

   @QField(isEditable = false, maxLength = 16)
   private String status;

   @QField(isEditable = false, maxLength = 64)
   private String stopReason;

   @QField(isEditable = false, maxLength = 32)
   private String errorCode;

   @QField(isEditable = false, maxLength = 64)
   private String cancelReason;

   @QField(isEditable = false, maxLength = 200)
   private String model;

   @QField(isEditable = false)
   private Instant startedAt;

   @QField(isEditable = false)
   private Instant endedAt;

   @QField(isEditable = false)
   private Instant heartbeatAt;

   @QField(isEditable = false, maxLength = 100)
   private String leaseOwner;

   @QField(isEditable = false)
   private Instant cancelRequestedAt;

   @QField(isEditable = false, maxLength = 64)
   private String phase;

   @QField(isEditable = false, maxLength = 16)
   private String concurrencyMode;

   @QField(isEditable = false)
   private Integer toolCallCount;

   @QField(isEditable = false)
   private Integer modelCallCount;

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

   @QField(isEditable = false)
   private String providerRequestIds;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantTurn()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantTurn(QRecord record) throws QException
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
   public AssistantTurn withId(Integer id)
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
   public AssistantTurn withConversationId(Integer conversationId)
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
   public AssistantTurn withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userMessageId
    *******************************************************************************/
   public Integer getUserMessageId()
   {
      return (this.userMessageId);
   }



   /*******************************************************************************
    ** Setter for userMessageId
    *******************************************************************************/
   public void setUserMessageId(Integer userMessageId)
   {
      this.userMessageId = userMessageId;
   }



   /*******************************************************************************
    ** Fluent setter for userMessageId
    *******************************************************************************/
   public AssistantTurn withUserMessageId(Integer userMessageId)
   {
      this.userMessageId = userMessageId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for assistantMessageId
    *******************************************************************************/
   public Integer getAssistantMessageId()
   {
      return (this.assistantMessageId);
   }



   /*******************************************************************************
    ** Setter for assistantMessageId
    *******************************************************************************/
   public void setAssistantMessageId(Integer assistantMessageId)
   {
      this.assistantMessageId = assistantMessageId;
   }



   /*******************************************************************************
    ** Fluent setter for assistantMessageId
    *******************************************************************************/
   public AssistantTurn withAssistantMessageId(Integer assistantMessageId)
   {
      this.assistantMessageId = assistantMessageId;
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
   public AssistantTurn withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for stopReason
    *******************************************************************************/
   public String getStopReason()
   {
      return (this.stopReason);
   }



   /*******************************************************************************
    ** Setter for stopReason
    *******************************************************************************/
   public void setStopReason(String stopReason)
   {
      this.stopReason = stopReason;
   }



   /*******************************************************************************
    ** Fluent setter for stopReason
    *******************************************************************************/
   public AssistantTurn withStopReason(String stopReason)
   {
      this.stopReason = stopReason;
      return (this);
   }



   /*******************************************************************************
    ** Getter for errorCode
    *******************************************************************************/
   public String getErrorCode()
   {
      return (this.errorCode);
   }



   /*******************************************************************************
    ** Setter for errorCode
    *******************************************************************************/
   public void setErrorCode(String errorCode)
   {
      this.errorCode = errorCode;
   }



   /*******************************************************************************
    ** Fluent setter for errorCode
    *******************************************************************************/
   public AssistantTurn withErrorCode(String errorCode)
   {
      this.errorCode = errorCode;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cancelReason
    *******************************************************************************/
   public String getCancelReason()
   {
      return (this.cancelReason);
   }



   /*******************************************************************************
    ** Setter for cancelReason
    *******************************************************************************/
   public void setCancelReason(String cancelReason)
   {
      this.cancelReason = cancelReason;
   }



   /*******************************************************************************
    ** Fluent setter for cancelReason
    *******************************************************************************/
   public AssistantTurn withCancelReason(String cancelReason)
   {
      this.cancelReason = cancelReason;
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
   public AssistantTurn withModel(String model)
   {
      this.model = model;
      return (this);
   }



   /*******************************************************************************
    ** Getter for startedAt
    *******************************************************************************/
   public Instant getStartedAt()
   {
      return (this.startedAt);
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
   public AssistantTurn withStartedAt(Instant startedAt)
   {
      this.startedAt = startedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for endedAt
    *******************************************************************************/
   public Instant getEndedAt()
   {
      return (this.endedAt);
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
   public AssistantTurn withEndedAt(Instant endedAt)
   {
      this.endedAt = endedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for toolCallCount
    *******************************************************************************/
   public Integer getToolCallCount()
   {
      return (this.toolCallCount);
   }



   /*******************************************************************************
    ** Setter for toolCallCount
    *******************************************************************************/
   public void setToolCallCount(Integer toolCallCount)
   {
      this.toolCallCount = toolCallCount;
   }



   /*******************************************************************************
    ** Fluent setter for toolCallCount
    *******************************************************************************/
   public AssistantTurn withToolCallCount(Integer toolCallCount)
   {
      this.toolCallCount = toolCallCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modelCallCount
    *******************************************************************************/
   public Integer getModelCallCount()
   {
      return (this.modelCallCount);
   }



   /*******************************************************************************
    ** Setter for modelCallCount
    *******************************************************************************/
   public void setModelCallCount(Integer modelCallCount)
   {
      this.modelCallCount = modelCallCount;
   }



   /*******************************************************************************
    ** Fluent setter for modelCallCount
    *******************************************************************************/
   public AssistantTurn withModelCallCount(Integer modelCallCount)
   {
      this.modelCallCount = modelCallCount;
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
   public AssistantTurn withInputTokens(Long inputTokens)
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
   public AssistantTurn withOutputTokens(Long outputTokens)
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
   public AssistantTurn withCacheReadTokens(Long cacheReadTokens)
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
   public AssistantTurn withCacheWriteTokens(Long cacheWriteTokens)
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
   public AssistantTurn withCostMicros(Long costMicros)
   {
      this.costMicros = costMicros;
      return (this);
   }



   /*******************************************************************************
    ** Getter for providerRequestIds
    *******************************************************************************/
   public String getProviderRequestIds()
   {
      return (this.providerRequestIds);
   }



   /*******************************************************************************
    ** Setter for providerRequestIds
    *******************************************************************************/
   public void setProviderRequestIds(String providerRequestIds)
   {
      this.providerRequestIds = providerRequestIds;
   }



   /*******************************************************************************
    ** Fluent setter for providerRequestIds
    *******************************************************************************/
   public AssistantTurn withProviderRequestIds(String providerRequestIds)
   {
      this.providerRequestIds = providerRequestIds;
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
   public AssistantTurn withCreateDate(Instant createDate)
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
   public AssistantTurn withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for classification
    *******************************************************************************/
   public String getClassification()
   {
      return (classification);
   }



   /*******************************************************************************
    ** Setter for classification
    *******************************************************************************/
   public void setClassification(String classification)
   {
      this.classification = classification;
   }



   /*******************************************************************************
    ** Fluent setter for classification
    *******************************************************************************/
   public AssistantTurn withClassification(String classification)
   {
      this.classification = classification;
      return (this);
   }



   /*******************************************************************************
    ** Getter for classificationTables
    *******************************************************************************/
   public String getClassificationTables()
   {
      return (classificationTables);
   }



   /*******************************************************************************
    ** Setter for classificationTables
    *******************************************************************************/
   public void setClassificationTables(String classificationTables)
   {
      this.classificationTables = classificationTables;
   }



   /*******************************************************************************
    ** Fluent setter for classificationTables
    *******************************************************************************/
   public AssistantTurn withClassificationTables(String classificationTables)
   {
      this.classificationTables = classificationTables;
      return (this);
   }






   /*******************************************************************************
    ** Get routeDecision.
    *******************************************************************************/
   public String getRouteDecision()
   {
      return (this.routeDecision);
   }



   /*******************************************************************************
    ** Set routeDecision.
    *******************************************************************************/
   public void setRouteDecision(String routeDecision)
   {
      this.routeDecision = routeDecision;
   }



   /*******************************************************************************
    ** With routeDecision.
    *******************************************************************************/
   public AssistantTurn withRouteDecision(String routeDecision)
   {
      this.routeDecision = routeDecision;
      return (this);
   }



   /*******************************************************************************
    ** Get routeSource.
    *******************************************************************************/
   public String getRouteSource()
   {
      return (this.routeSource);
   }



   /*******************************************************************************
    ** Set routeSource.
    *******************************************************************************/
   public void setRouteSource(String routeSource)
   {
      this.routeSource = routeSource;
   }



   /*******************************************************************************
    ** With routeSource.
    *******************************************************************************/
   public AssistantTurn withRouteSource(String routeSource)
   {
      this.routeSource = routeSource;
      return (this);
   }



   /*******************************************************************************
    ** Get fallbackCount.
    *******************************************************************************/
   public Integer getFallbackCount()
   {
      return (this.fallbackCount);
   }



   /*******************************************************************************
    ** Set fallbackCount.
    *******************************************************************************/
   public void setFallbackCount(Integer fallbackCount)
   {
      this.fallbackCount = fallbackCount;
   }



   /*******************************************************************************
    ** With fallbackCount.
    *******************************************************************************/
   public AssistantTurn withFallbackCount(Integer fallbackCount)
   {
      this.fallbackCount = fallbackCount;
      return (this);
   }



   /*******************************************************************************
    ** Get providerId.
    *******************************************************************************/
   public Integer getProviderId()
   {
      return (this.providerId);
   }



   /*******************************************************************************
    ** Set providerId.
    *******************************************************************************/
   public void setProviderId(Integer providerId)
   {
      this.providerId = providerId;
   }



   /*******************************************************************************
    ** With providerId.
    *******************************************************************************/
   public AssistantTurn withProviderId(Integer providerId)
   {
      this.providerId = providerId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for heartbeatAt
    *******************************************************************************/
   public Instant getHeartbeatAt()
   {
      return (this.heartbeatAt);
   }



   /*******************************************************************************
    ** Setter for heartbeatAt
    *******************************************************************************/
   public void setHeartbeatAt(Instant heartbeatAt)
   {
      this.heartbeatAt = heartbeatAt;
   }



   /*******************************************************************************
    ** Fluent setter for heartbeatAt
    *******************************************************************************/
   public AssistantTurn withHeartbeatAt(Instant heartbeatAt)
   {
      this.heartbeatAt = heartbeatAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for leaseOwner
    *******************************************************************************/
   public String getLeaseOwner()
   {
      return (this.leaseOwner);
   }



   /*******************************************************************************
    ** Setter for leaseOwner
    *******************************************************************************/
   public void setLeaseOwner(String leaseOwner)
   {
      this.leaseOwner = leaseOwner;
   }



   /*******************************************************************************
    ** Fluent setter for leaseOwner
    *******************************************************************************/
   public AssistantTurn withLeaseOwner(String leaseOwner)
   {
      this.leaseOwner = leaseOwner;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cancelRequestedAt
    *******************************************************************************/
   public Instant getCancelRequestedAt()
   {
      return (this.cancelRequestedAt);
   }



   /*******************************************************************************
    ** Setter for cancelRequestedAt
    *******************************************************************************/
   public void setCancelRequestedAt(Instant cancelRequestedAt)
   {
      this.cancelRequestedAt = cancelRequestedAt;
   }



   /*******************************************************************************
    ** Fluent setter for cancelRequestedAt
    *******************************************************************************/
   public AssistantTurn withCancelRequestedAt(Instant cancelRequestedAt)
   {
      this.cancelRequestedAt = cancelRequestedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for phase
    *******************************************************************************/
   public String getPhase()
   {
      return (this.phase);
   }



   /*******************************************************************************
    ** Setter for phase
    *******************************************************************************/
   public void setPhase(String phase)
   {
      this.phase = phase;
   }



   /*******************************************************************************
    ** Fluent setter for phase
    *******************************************************************************/
   public AssistantTurn withPhase(String phase)
   {
      this.phase = phase;
      return (this);
   }



   /*******************************************************************************
    ** Getter for concurrencyMode
    *******************************************************************************/
   public String getConcurrencyMode()
   {
      return (this.concurrencyMode);
   }



   /*******************************************************************************
    ** Setter for concurrencyMode
    *******************************************************************************/
   public void setConcurrencyMode(String concurrencyMode)
   {
      this.concurrencyMode = concurrencyMode;
   }



   /*******************************************************************************
    ** Fluent setter for concurrencyMode
    *******************************************************************************/
   public AssistantTurn withConcurrencyMode(String concurrencyMode)
   {
      this.concurrencyMode = concurrencyMode;
      return (this);
   }

}
