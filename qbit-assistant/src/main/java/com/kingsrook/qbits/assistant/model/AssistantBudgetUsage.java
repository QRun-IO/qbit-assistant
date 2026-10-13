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


import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import java.time.Instant;


/*******************************************************************************
 ** Private accounting projection of usage, without conversation or request data.
 ** Admin-only in the UI; no owner lock so internal shared-budget sums can run
 ** under the current session. The original usage record remains owner-locked.
 *******************************************************************************/
public class AssistantBudgetUsage extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_budget_usage";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true)
   private Integer usageId;

   @QField(maxLength = 255)
   private String userId;

   @QField()
   private Integer providerId;

   @QField(maxLength = 200)
   private String model;

   @QField(isRequired = true)
   private Instant occurredAt;

   @QField()
   private Long costMicros;

   @QField()
   private Long inputTokens;

   @QField()
   private Long outputTokens;

   @QField()
   private Long cacheReadTokens;

   @QField()
   private Long cacheWriteTokens;


   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantBudgetUsage()
   {
   }



   /*******************************************************************************
    ** Populate from a record.
    *******************************************************************************/
   public AssistantBudgetUsage(QRecord record) throws QException
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
   public AssistantBudgetUsage withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for usageId
    *******************************************************************************/
   public Integer getUsageId()
   {
      return (this.usageId);
   }



   /*******************************************************************************
    ** Setter for usageId
    *******************************************************************************/
   public void setUsageId(Integer usageId)
   {
      this.usageId = usageId;
   }



   /*******************************************************************************
    ** Fluent setter for usageId
    *******************************************************************************/
   public AssistantBudgetUsage withUsageId(Integer usageId)
   {
      this.usageId = usageId;
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
   public AssistantBudgetUsage withUserId(String userId)
   {
      this.userId = userId;
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
   public AssistantBudgetUsage withProviderId(Integer providerId)
   {
      this.providerId = providerId;
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
   public AssistantBudgetUsage withModel(String model)
   {
      this.model = model;
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
   public AssistantBudgetUsage withOccurredAt(Instant occurredAt)
   {
      this.occurredAt = occurredAt;
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
   public AssistantBudgetUsage withCostMicros(Long costMicros)
   {
      this.costMicros = costMicros;
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
   public AssistantBudgetUsage withInputTokens(Long inputTokens)
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
   public AssistantBudgetUsage withOutputTokens(Long outputTokens)
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
   public AssistantBudgetUsage withCacheReadTokens(Long cacheReadTokens)
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
   public AssistantBudgetUsage withCacheWriteTokens(Long cacheWriteTokens)
   {
      this.cacheWriteTokens = cacheWriteTokens;
      return (this);
   }

}
