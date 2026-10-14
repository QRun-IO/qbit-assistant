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
import java.math.BigDecimal;
import java.time.Instant;


/*******************************************************************************
 ** Persisted assistant budget budget.
 *******************************************************************************/
public class AssistantBudget extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_budget";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 200)
   private String name;

   @QField()
   private Boolean enabled;

   @QField(isRequired = true, maxLength = 16)
   private String scopeType;

   @QField(maxLength = 200)
   private String scopeValue;

   @QField(isRequired = true, maxLength = 16)
   private String period;

   @QField(isRequired = true, maxLength = 16)
   private String metric;

   @QField(isRequired = true)
   private BigDecimal limitValue;

   @QField()
   private Integer warnPercent;

   @QField(maxLength = 16)
   private String atLimitAction;

   @QField()
   private Integer downgradeModelId;

   @QField()
   private Integer hardCeilingPercent;

   @QField(maxLength = 2000)
   private String notes;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;


   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantBudget()
   {
   }



   /*******************************************************************************
    ** Populate from a record.
    *******************************************************************************/
   public AssistantBudget(QRecord record) throws QException
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
   public AssistantBudget withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for name
    *******************************************************************************/
   public String getName()
   {
      return (this.name);
   }



   /*******************************************************************************
    ** Setter for name
    *******************************************************************************/
   public void setName(String name)
   {
      this.name = name;
   }



   /*******************************************************************************
    ** Fluent setter for name
    *******************************************************************************/
   public AssistantBudget withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for enabled
    *******************************************************************************/
   public Boolean getEnabled()
   {
      return (this.enabled);
   }



   /*******************************************************************************
    ** Setter for enabled
    *******************************************************************************/
   public void setEnabled(Boolean enabled)
   {
      this.enabled = enabled;
   }



   /*******************************************************************************
    ** Fluent setter for enabled
    *******************************************************************************/
   public AssistantBudget withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for scopeType
    *******************************************************************************/
   public String getScopeType()
   {
      return (this.scopeType);
   }



   /*******************************************************************************
    ** Setter for scopeType
    *******************************************************************************/
   public void setScopeType(String scopeType)
   {
      this.scopeType = scopeType;
   }



   /*******************************************************************************
    ** Fluent setter for scopeType
    *******************************************************************************/
   public AssistantBudget withScopeType(String scopeType)
   {
      this.scopeType = scopeType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for scopeValue
    *******************************************************************************/
   public String getScopeValue()
   {
      return (this.scopeValue);
   }



   /*******************************************************************************
    ** Setter for scopeValue
    *******************************************************************************/
   public void setScopeValue(String scopeValue)
   {
      this.scopeValue = scopeValue;
   }



   /*******************************************************************************
    ** Fluent setter for scopeValue
    *******************************************************************************/
   public AssistantBudget withScopeValue(String scopeValue)
   {
      this.scopeValue = scopeValue;
      return (this);
   }



   /*******************************************************************************
    ** Getter for period
    *******************************************************************************/
   public String getPeriod()
   {
      return (this.period);
   }



   /*******************************************************************************
    ** Setter for period
    *******************************************************************************/
   public void setPeriod(String period)
   {
      this.period = period;
   }



   /*******************************************************************************
    ** Fluent setter for period
    *******************************************************************************/
   public AssistantBudget withPeriod(String period)
   {
      this.period = period;
      return (this);
   }



   /*******************************************************************************
    ** Getter for metric
    *******************************************************************************/
   public String getMetric()
   {
      return (this.metric);
   }



   /*******************************************************************************
    ** Setter for metric
    *******************************************************************************/
   public void setMetric(String metric)
   {
      this.metric = metric;
   }



   /*******************************************************************************
    ** Fluent setter for metric
    *******************************************************************************/
   public AssistantBudget withMetric(String metric)
   {
      this.metric = metric;
      return (this);
   }



   /*******************************************************************************
    ** Getter for limitValue
    *******************************************************************************/
   public BigDecimal getLimitValue()
   {
      return (this.limitValue);
   }



   /*******************************************************************************
    ** Setter for limitValue
    *******************************************************************************/
   public void setLimitValue(BigDecimal limitValue)
   {
      this.limitValue = limitValue;
   }



   /*******************************************************************************
    ** Fluent setter for limitValue
    *******************************************************************************/
   public AssistantBudget withLimitValue(BigDecimal limitValue)
   {
      this.limitValue = limitValue;
      return (this);
   }



   /*******************************************************************************
    ** Getter for warnPercent
    *******************************************************************************/
   public Integer getWarnPercent()
   {
      return (this.warnPercent);
   }



   /*******************************************************************************
    ** Setter for warnPercent
    *******************************************************************************/
   public void setWarnPercent(Integer warnPercent)
   {
      this.warnPercent = warnPercent;
   }



   /*******************************************************************************
    ** Fluent setter for warnPercent
    *******************************************************************************/
   public AssistantBudget withWarnPercent(Integer warnPercent)
   {
      this.warnPercent = warnPercent;
      return (this);
   }



   /*******************************************************************************
    ** Getter for atLimitAction
    *******************************************************************************/
   public String getAtLimitAction()
   {
      return (this.atLimitAction);
   }



   /*******************************************************************************
    ** Setter for atLimitAction
    *******************************************************************************/
   public void setAtLimitAction(String atLimitAction)
   {
      this.atLimitAction = atLimitAction;
   }



   /*******************************************************************************
    ** Fluent setter for atLimitAction
    *******************************************************************************/
   public AssistantBudget withAtLimitAction(String atLimitAction)
   {
      this.atLimitAction = atLimitAction;
      return (this);
   }



   /*******************************************************************************
    ** Getter for downgradeModelId
    *******************************************************************************/
   public Integer getDowngradeModelId()
   {
      return (this.downgradeModelId);
   }



   /*******************************************************************************
    ** Setter for downgradeModelId
    *******************************************************************************/
   public void setDowngradeModelId(Integer downgradeModelId)
   {
      this.downgradeModelId = downgradeModelId;
   }



   /*******************************************************************************
    ** Fluent setter for downgradeModelId
    *******************************************************************************/
   public AssistantBudget withDowngradeModelId(Integer downgradeModelId)
   {
      this.downgradeModelId = downgradeModelId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for hardCeilingPercent
    *******************************************************************************/
   public Integer getHardCeilingPercent()
   {
      return (this.hardCeilingPercent);
   }



   /*******************************************************************************
    ** Setter for hardCeilingPercent
    *******************************************************************************/
   public void setHardCeilingPercent(Integer hardCeilingPercent)
   {
      this.hardCeilingPercent = hardCeilingPercent;
   }



   /*******************************************************************************
    ** Fluent setter for hardCeilingPercent
    *******************************************************************************/
   public AssistantBudget withHardCeilingPercent(Integer hardCeilingPercent)
   {
      this.hardCeilingPercent = hardCeilingPercent;
      return (this);
   }



   /*******************************************************************************
    ** Getter for notes
    *******************************************************************************/
   public String getNotes()
   {
      return (this.notes);
   }



   /*******************************************************************************
    ** Setter for notes
    *******************************************************************************/
   public void setNotes(String notes)
   {
      this.notes = notes;
   }



   /*******************************************************************************
    ** Fluent setter for notes
    *******************************************************************************/
   public AssistantBudget withNotes(String notes)
   {
      this.notes = notes;
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
   public AssistantBudget withCreateDate(Instant createDate)
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
   public AssistantBudget withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
