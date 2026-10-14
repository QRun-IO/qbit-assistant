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
 ** Persisted assistant budget budget event.
 *******************************************************************************/
public class AssistantBudgetEvent extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_budget_event";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true)
   private Integer budgetId;

   @QField(isRequired = true, maxLength = 400)
   private String scopeKey;

   @QField(isRequired = true)
   private Instant periodStart;

   @QField(isRequired = true, maxLength = 16)
   private String state;

   @QField()
   private BigDecimal usedValue;

   @QField()
   private BigDecimal limitValue;

   @QField(maxLength = 200)
   private String userId;

   @QField(isEditable = false)
   private Instant createDate;


   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantBudgetEvent()
   {
   }



   /*******************************************************************************
    ** Populate from a record.
    *******************************************************************************/
   public AssistantBudgetEvent(QRecord record) throws QException
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
   public AssistantBudgetEvent withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for budgetId
    *******************************************************************************/
   public Integer getBudgetId()
   {
      return (this.budgetId);
   }



   /*******************************************************************************
    ** Setter for budgetId
    *******************************************************************************/
   public void setBudgetId(Integer budgetId)
   {
      this.budgetId = budgetId;
   }



   /*******************************************************************************
    ** Fluent setter for budgetId
    *******************************************************************************/
   public AssistantBudgetEvent withBudgetId(Integer budgetId)
   {
      this.budgetId = budgetId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for scopeKey
    *******************************************************************************/
   public String getScopeKey()
   {
      return (this.scopeKey);
   }



   /*******************************************************************************
    ** Setter for scopeKey
    *******************************************************************************/
   public void setScopeKey(String scopeKey)
   {
      this.scopeKey = scopeKey;
   }



   /*******************************************************************************
    ** Fluent setter for scopeKey
    *******************************************************************************/
   public AssistantBudgetEvent withScopeKey(String scopeKey)
   {
      this.scopeKey = scopeKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for periodStart
    *******************************************************************************/
   public Instant getPeriodStart()
   {
      return (this.periodStart);
   }



   /*******************************************************************************
    ** Setter for periodStart
    *******************************************************************************/
   public void setPeriodStart(Instant periodStart)
   {
      this.periodStart = periodStart;
   }



   /*******************************************************************************
    ** Fluent setter for periodStart
    *******************************************************************************/
   public AssistantBudgetEvent withPeriodStart(Instant periodStart)
   {
      this.periodStart = periodStart;
      return (this);
   }



   /*******************************************************************************
    ** Getter for state
    *******************************************************************************/
   public String getState()
   {
      return (this.state);
   }



   /*******************************************************************************
    ** Setter for state
    *******************************************************************************/
   public void setState(String state)
   {
      this.state = state;
   }



   /*******************************************************************************
    ** Fluent setter for state
    *******************************************************************************/
   public AssistantBudgetEvent withState(String state)
   {
      this.state = state;
      return (this);
   }



   /*******************************************************************************
    ** Getter for usedValue
    *******************************************************************************/
   public BigDecimal getUsedValue()
   {
      return (this.usedValue);
   }



   /*******************************************************************************
    ** Setter for usedValue
    *******************************************************************************/
   public void setUsedValue(BigDecimal usedValue)
   {
      this.usedValue = usedValue;
   }



   /*******************************************************************************
    ** Fluent setter for usedValue
    *******************************************************************************/
   public AssistantBudgetEvent withUsedValue(BigDecimal usedValue)
   {
      this.usedValue = usedValue;
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
   public AssistantBudgetEvent withLimitValue(BigDecimal limitValue)
   {
      this.limitValue = limitValue;
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
   public AssistantBudgetEvent withUserId(String userId)
   {
      this.userId = userId;
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
   public AssistantBudgetEvent withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }

}
