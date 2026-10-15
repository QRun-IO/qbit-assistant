/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.assistant.model;


import java.time.Instant;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;


/*******************************************************************************
 ** Routing decision before each PII/PHI model call and every blocked request; no record values.
 *******************************************************************************/
public class AssistantDataAudit extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_data_audit";

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

   @QField(isEditable = false, maxLength = 200)
   private String providerName;

   @QField(isEditable = false, maxLength = 200)
   private String model;

   @QField(isEditable = false)
   private Boolean hipaaEligible;

   @QField(isEditable = false, maxLength = 16)
   private String classification;

   @QField(isEditable = false, maxLength = 500)
   private String tables;

   @QField(isEditable = false, maxLength = 16)
   private String decision;

   @QField(isEditable = false)
   private String reason;

   @QField(isEditable = false)
   private Instant createDate;



   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (id);
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
   public AssistantDataAudit withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userId
    *******************************************************************************/
   public String getUserId()
   {
      return (userId);
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
   public AssistantDataAudit withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for conversationId
    *******************************************************************************/
   public Integer getConversationId()
   {
      return (conversationId);
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
   public AssistantDataAudit withConversationId(Integer conversationId)
   {
      this.conversationId = conversationId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for turnId
    *******************************************************************************/
   public Integer getTurnId()
   {
      return (turnId);
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
   public AssistantDataAudit withTurnId(Integer turnId)
   {
      this.turnId = turnId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for providerId
    *******************************************************************************/
   public Integer getProviderId()
   {
      return (providerId);
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
   public AssistantDataAudit withProviderId(Integer providerId)
   {
      this.providerId = providerId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for providerName
    *******************************************************************************/
   public String getProviderName()
   {
      return (providerName);
   }



   /*******************************************************************************
    ** Setter for providerName
    *******************************************************************************/
   public void setProviderName(String providerName)
   {
      this.providerName = providerName;
   }



   /*******************************************************************************
    ** Fluent setter for providerName
    *******************************************************************************/
   public AssistantDataAudit withProviderName(String providerName)
   {
      this.providerName = providerName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for model
    *******************************************************************************/
   public String getModel()
   {
      return (model);
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
   public AssistantDataAudit withModel(String model)
   {
      this.model = model;
      return (this);
   }



   /*******************************************************************************
    ** Getter for hipaaEligible
    *******************************************************************************/
   public Boolean getHipaaEligible()
   {
      return (hipaaEligible);
   }



   /*******************************************************************************
    ** Setter for hipaaEligible
    *******************************************************************************/
   public void setHipaaEligible(Boolean hipaaEligible)
   {
      this.hipaaEligible = hipaaEligible;
   }



   /*******************************************************************************
    ** Fluent setter for hipaaEligible
    *******************************************************************************/
   public AssistantDataAudit withHipaaEligible(Boolean hipaaEligible)
   {
      this.hipaaEligible = hipaaEligible;
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
   public AssistantDataAudit withClassification(String classification)
   {
      this.classification = classification;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tables
    *******************************************************************************/
   public String getTables()
   {
      return (tables);
   }



   /*******************************************************************************
    ** Setter for tables
    *******************************************************************************/
   public void setTables(String tables)
   {
      this.tables = tables;
   }



   /*******************************************************************************
    ** Fluent setter for tables
    *******************************************************************************/
   public AssistantDataAudit withTables(String tables)
   {
      this.tables = tables;
      return (this);
   }



   /*******************************************************************************
    ** Getter for decision
    *******************************************************************************/
   public String getDecision()
   {
      return (decision);
   }



   /*******************************************************************************
    ** Setter for decision
    *******************************************************************************/
   public void setDecision(String decision)
   {
      this.decision = decision;
   }



   /*******************************************************************************
    ** Fluent setter for decision
    *******************************************************************************/
   public AssistantDataAudit withDecision(String decision)
   {
      this.decision = decision;
      return (this);
   }



   /*******************************************************************************
    ** Getter for reason
    *******************************************************************************/
   public String getReason()
   {
      return (reason);
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
   public AssistantDataAudit withReason(String reason)
   {
      this.reason = reason;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (createDate);
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
   public AssistantDataAudit withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



}
