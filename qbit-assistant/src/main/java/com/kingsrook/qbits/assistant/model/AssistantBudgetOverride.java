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
 ** Persisted assistant budget budget override.
 *******************************************************************************/
public class AssistantBudgetOverride extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_budget_override";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true)
   private Integer budgetId;

   @QField(maxLength = 200)
   private String scopeValue;

   @QField()
   private BigDecimal extraValue;

   @QField()
   private Instant unlimitedUntil;

   @QField(isRequired = true)
   private String reason;

   @QField(isEditable = false, maxLength = 200)
   private String grantedBy;

   @QField(isRequired = true)
   private Instant expiresAt;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;


   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantBudgetOverride()
   {
   }



   /*******************************************************************************
    ** Populate from a record.
    *******************************************************************************/
   public AssistantBudgetOverride(QRecord record) throws QException
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
   public AssistantBudgetOverride withId(Integer id)
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
   public AssistantBudgetOverride withBudgetId(Integer budgetId)
   {
      this.budgetId = budgetId;
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
   public AssistantBudgetOverride withScopeValue(String scopeValue)
   {
      this.scopeValue = scopeValue;
      return (this);
   }



   /*******************************************************************************
    ** Getter for extraValue
    *******************************************************************************/
   public BigDecimal getExtraValue()
   {
      return (this.extraValue);
   }



   /*******************************************************************************
    ** Setter for extraValue
    *******************************************************************************/
   public void setExtraValue(BigDecimal extraValue)
   {
      this.extraValue = extraValue;
   }



   /*******************************************************************************
    ** Fluent setter for extraValue
    *******************************************************************************/
   public AssistantBudgetOverride withExtraValue(BigDecimal extraValue)
   {
      this.extraValue = extraValue;
      return (this);
   }



   /*******************************************************************************
    ** Getter for unlimitedUntil
    *******************************************************************************/
   public Instant getUnlimitedUntil()
   {
      return (this.unlimitedUntil);
   }



   /*******************************************************************************
    ** Setter for unlimitedUntil
    *******************************************************************************/
   public void setUnlimitedUntil(Instant unlimitedUntil)
   {
      this.unlimitedUntil = unlimitedUntil;
   }



   /*******************************************************************************
    ** Fluent setter for unlimitedUntil
    *******************************************************************************/
   public AssistantBudgetOverride withUnlimitedUntil(Instant unlimitedUntil)
   {
      this.unlimitedUntil = unlimitedUntil;
      return (this);
   }



   /*******************************************************************************
    ** Getter for reason
    *******************************************************************************/
   public String getReason()
   {
      return (this.reason);
   }



   /*******************************************************************************
    ** Setter for reason
    *******************************************************************************/
   public void setReason(String reason)
   {
      this.reason = reason;
   }



   /*******************************************************************************
    ** Fluent setter for reason
    *******************************************************************************/
   public AssistantBudgetOverride withReason(String reason)
   {
      this.reason = reason;
      return (this);
   }



   /*******************************************************************************
    ** Getter for grantedBy
    *******************************************************************************/
   public String getGrantedBy()
   {
      return (this.grantedBy);
   }



   /*******************************************************************************
    ** Setter for grantedBy
    *******************************************************************************/
   public void setGrantedBy(String grantedBy)
   {
      this.grantedBy = grantedBy;
   }



   /*******************************************************************************
    ** Fluent setter for grantedBy
    *******************************************************************************/
   public AssistantBudgetOverride withGrantedBy(String grantedBy)
   {
      this.grantedBy = grantedBy;
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
   public AssistantBudgetOverride withExpiresAt(Instant expiresAt)
   {
      this.expiresAt = expiresAt;
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
   public AssistantBudgetOverride withCreateDate(Instant createDate)
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
   public AssistantBudgetOverride withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
