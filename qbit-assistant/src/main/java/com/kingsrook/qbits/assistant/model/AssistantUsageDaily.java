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
import java.time.LocalDate;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;


/*******************************************************************************
 ** Daily usage rollup for budgets and dashboards: one USER row per user and
 ** day, and one INSTANCE row (userId null) per day.
 *******************************************************************************/
public class AssistantUsageDaily extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_usage_daily";

   /*******************************************************************************
    ** The table name before 0.1.0-RC.3 (camelCase). See UPGRADE.md.
    *******************************************************************************/
   @Deprecated(since = "0.1.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "assistantUsageDaily";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false, maxLength = 16)
   private String scope;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField(isEditable = false)
   private LocalDate usageDate;

   @QField(isEditable = false)
   private Integer turnCount;

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
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantUsageDaily()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantUsageDaily(QRecord record) throws QException
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
   public AssistantUsageDaily withId(Integer id)
   {
      this.id = id;
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
   public AssistantUsageDaily withScope(String scope)
   {
      this.scope = scope;
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
   public AssistantUsageDaily withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for usageDate
    *******************************************************************************/
   public LocalDate getUsageDate()
   {
      return (this.usageDate);
   }



   /*******************************************************************************
    ** Setter for usageDate
    *******************************************************************************/
   public void setUsageDate(LocalDate usageDate)
   {
      this.usageDate = usageDate;
   }



   /*******************************************************************************
    ** Fluent setter for usageDate
    *******************************************************************************/
   public AssistantUsageDaily withUsageDate(LocalDate usageDate)
   {
      this.usageDate = usageDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for turnCount
    *******************************************************************************/
   public Integer getTurnCount()
   {
      return (this.turnCount);
   }



   /*******************************************************************************
    ** Setter for turnCount
    *******************************************************************************/
   public void setTurnCount(Integer turnCount)
   {
      this.turnCount = turnCount;
   }



   /*******************************************************************************
    ** Fluent setter for turnCount
    *******************************************************************************/
   public AssistantUsageDaily withTurnCount(Integer turnCount)
   {
      this.turnCount = turnCount;
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
   public AssistantUsageDaily withInputTokens(Long inputTokens)
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
   public AssistantUsageDaily withOutputTokens(Long outputTokens)
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
   public AssistantUsageDaily withCacheReadTokens(Long cacheReadTokens)
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
   public AssistantUsageDaily withCacheWriteTokens(Long cacheWriteTokens)
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
   public AssistantUsageDaily withCostMicros(Long costMicros)
   {
      this.costMicros = costMicros;
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
   public AssistantUsageDaily withCreateDate(Instant createDate)
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
   public AssistantUsageDaily withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
