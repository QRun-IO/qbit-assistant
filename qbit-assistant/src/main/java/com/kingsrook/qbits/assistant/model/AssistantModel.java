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


import java.math.BigDecimal;
import java.time.Instant;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;


/*******************************************************************************
 ** A provider-scoped model, its capabilities, prices and last connection test.
 *******************************************************************************/
public class AssistantModel extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_model";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true)
   private Integer providerId;

   @QField(isRequired = true, maxLength = 200)
   private String modelId;

   @QField(maxLength = 200)
   private String displayName;

   @QField()
   private Boolean enabled;

   @QField()
   private Boolean supportsTools;

   @QField()
   private Boolean supportsVision;

   @QField()
   private Boolean supportsStreaming;

   @QField()
   private Boolean supportsReasoningEffort;

   @QField()
   private Integer contextWindowTokens;

   @QField()
   private Integer maxOutputTokens;

   @QField()
   private BigDecimal inputUsdPerMTok;

   @QField()
   private BigDecimal outputUsdPerMTok;

   @QField(maxLength = 16)
   private String source;

   @QField(isEditable = false)
   private Instant discoveredAt;

   @QField(isEditable = false)
   private Instant lastTestAt;

   @QField(isEditable = false, maxLength = 16)
   private String lastTestStatus;

   @QField(isEditable = false)
   private Integer lastTestLatencyMs;

   @QField(isEditable = false, maxLength = 500)
   private String lastTestError;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;


   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantModel()
   {
   }



   /*******************************************************************************
    ** Populate from a record.
    *******************************************************************************/
   public AssistantModel(QRecord record) throws QException
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
   public AssistantModel withId(Integer id)
   {
      this.id = id;
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
   public AssistantModel withProviderId(Integer providerId)
   {
      this.providerId = providerId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modelId
    *******************************************************************************/
   public String getModelId()
   {
      return (this.modelId);
   }



   /*******************************************************************************
    ** Setter for modelId
    *******************************************************************************/
   public void setModelId(String modelId)
   {
      this.modelId = modelId;
   }



   /*******************************************************************************
    ** Fluent setter for modelId
    *******************************************************************************/
   public AssistantModel withModelId(String modelId)
   {
      this.modelId = modelId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for displayName
    *******************************************************************************/
   public String getDisplayName()
   {
      return (this.displayName);
   }



   /*******************************************************************************
    ** Setter for displayName
    *******************************************************************************/
   public void setDisplayName(String displayName)
   {
      this.displayName = displayName;
   }



   /*******************************************************************************
    ** Fluent setter for displayName
    *******************************************************************************/
   public AssistantModel withDisplayName(String displayName)
   {
      this.displayName = displayName;
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
   public AssistantModel withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for supportsTools
    *******************************************************************************/
   public Boolean getSupportsTools()
   {
      return (this.supportsTools);
   }



   /*******************************************************************************
    ** Setter for supportsTools
    *******************************************************************************/
   public void setSupportsTools(Boolean supportsTools)
   {
      this.supportsTools = supportsTools;
   }



   /*******************************************************************************
    ** Fluent setter for supportsTools
    *******************************************************************************/
   public AssistantModel withSupportsTools(Boolean supportsTools)
   {
      this.supportsTools = supportsTools;
      return (this);
   }



   /*******************************************************************************
    ** Getter for supportsVision
    *******************************************************************************/
   public Boolean getSupportsVision()
   {
      return (this.supportsVision);
   }



   /*******************************************************************************
    ** Setter for supportsVision
    *******************************************************************************/
   public void setSupportsVision(Boolean supportsVision)
   {
      this.supportsVision = supportsVision;
   }



   /*******************************************************************************
    ** Fluent setter for supportsVision
    *******************************************************************************/
   public AssistantModel withSupportsVision(Boolean supportsVision)
   {
      this.supportsVision = supportsVision;
      return (this);
   }



   /*******************************************************************************
    ** Getter for supportsStreaming
    *******************************************************************************/
   public Boolean getSupportsStreaming()
   {
      return (this.supportsStreaming);
   }



   /*******************************************************************************
    ** Setter for supportsStreaming
    *******************************************************************************/
   public void setSupportsStreaming(Boolean supportsStreaming)
   {
      this.supportsStreaming = supportsStreaming;
   }



   /*******************************************************************************
    ** Fluent setter for supportsStreaming
    *******************************************************************************/
   public AssistantModel withSupportsStreaming(Boolean supportsStreaming)
   {
      this.supportsStreaming = supportsStreaming;
      return (this);
   }



   /*******************************************************************************
    ** Getter for supportsReasoningEffort
    *******************************************************************************/
   public Boolean getSupportsReasoningEffort()
   {
      return (this.supportsReasoningEffort);
   }



   /*******************************************************************************
    ** Setter for supportsReasoningEffort
    *******************************************************************************/
   public void setSupportsReasoningEffort(Boolean supportsReasoningEffort)
   {
      this.supportsReasoningEffort = supportsReasoningEffort;
   }



   /*******************************************************************************
    ** Fluent setter for supportsReasoningEffort
    *******************************************************************************/
   public AssistantModel withSupportsReasoningEffort(Boolean supportsReasoningEffort)
   {
      this.supportsReasoningEffort = supportsReasoningEffort;
      return (this);
   }



   /*******************************************************************************
    ** Getter for contextWindowTokens
    *******************************************************************************/
   public Integer getContextWindowTokens()
   {
      return (this.contextWindowTokens);
   }



   /*******************************************************************************
    ** Setter for contextWindowTokens
    *******************************************************************************/
   public void setContextWindowTokens(Integer contextWindowTokens)
   {
      this.contextWindowTokens = contextWindowTokens;
   }



   /*******************************************************************************
    ** Fluent setter for contextWindowTokens
    *******************************************************************************/
   public AssistantModel withContextWindowTokens(Integer contextWindowTokens)
   {
      this.contextWindowTokens = contextWindowTokens;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxOutputTokens
    *******************************************************************************/
   public Integer getMaxOutputTokens()
   {
      return (this.maxOutputTokens);
   }



   /*******************************************************************************
    ** Setter for maxOutputTokens
    *******************************************************************************/
   public void setMaxOutputTokens(Integer maxOutputTokens)
   {
      this.maxOutputTokens = maxOutputTokens;
   }



   /*******************************************************************************
    ** Fluent setter for maxOutputTokens
    *******************************************************************************/
   public AssistantModel withMaxOutputTokens(Integer maxOutputTokens)
   {
      this.maxOutputTokens = maxOutputTokens;
      return (this);
   }



   /*******************************************************************************
    ** Getter for inputUsdPerMTok
    *******************************************************************************/
   public BigDecimal getInputUsdPerMTok()
   {
      return (this.inputUsdPerMTok);
   }



   /*******************************************************************************
    ** Setter for inputUsdPerMTok
    *******************************************************************************/
   public void setInputUsdPerMTok(BigDecimal inputUsdPerMTok)
   {
      this.inputUsdPerMTok = inputUsdPerMTok;
   }



   /*******************************************************************************
    ** Fluent setter for inputUsdPerMTok
    *******************************************************************************/
   public AssistantModel withInputUsdPerMTok(BigDecimal inputUsdPerMTok)
   {
      this.inputUsdPerMTok = inputUsdPerMTok;
      return (this);
   }



   /*******************************************************************************
    ** Getter for outputUsdPerMTok
    *******************************************************************************/
   public BigDecimal getOutputUsdPerMTok()
   {
      return (this.outputUsdPerMTok);
   }



   /*******************************************************************************
    ** Setter for outputUsdPerMTok
    *******************************************************************************/
   public void setOutputUsdPerMTok(BigDecimal outputUsdPerMTok)
   {
      this.outputUsdPerMTok = outputUsdPerMTok;
   }



   /*******************************************************************************
    ** Fluent setter for outputUsdPerMTok
    *******************************************************************************/
   public AssistantModel withOutputUsdPerMTok(BigDecimal outputUsdPerMTok)
   {
      this.outputUsdPerMTok = outputUsdPerMTok;
      return (this);
   }



   /*******************************************************************************
    ** Getter for source
    *******************************************************************************/
   public String getSource()
   {
      return (this.source);
   }



   /*******************************************************************************
    ** Setter for source
    *******************************************************************************/
   public void setSource(String source)
   {
      this.source = source;
   }



   /*******************************************************************************
    ** Fluent setter for source
    *******************************************************************************/
   public AssistantModel withSource(String source)
   {
      this.source = source;
      return (this);
   }



   /*******************************************************************************
    ** Getter for discoveredAt
    *******************************************************************************/
   public Instant getDiscoveredAt()
   {
      return (this.discoveredAt);
   }



   /*******************************************************************************
    ** Setter for discoveredAt
    *******************************************************************************/
   public void setDiscoveredAt(Instant discoveredAt)
   {
      this.discoveredAt = discoveredAt;
   }



   /*******************************************************************************
    ** Fluent setter for discoveredAt
    *******************************************************************************/
   public AssistantModel withDiscoveredAt(Instant discoveredAt)
   {
      this.discoveredAt = discoveredAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastTestAt
    *******************************************************************************/
   public Instant getLastTestAt()
   {
      return (this.lastTestAt);
   }



   /*******************************************************************************
    ** Setter for lastTestAt
    *******************************************************************************/
   public void setLastTestAt(Instant lastTestAt)
   {
      this.lastTestAt = lastTestAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastTestAt
    *******************************************************************************/
   public AssistantModel withLastTestAt(Instant lastTestAt)
   {
      this.lastTestAt = lastTestAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastTestStatus
    *******************************************************************************/
   public String getLastTestStatus()
   {
      return (this.lastTestStatus);
   }



   /*******************************************************************************
    ** Setter for lastTestStatus
    *******************************************************************************/
   public void setLastTestStatus(String lastTestStatus)
   {
      this.lastTestStatus = lastTestStatus;
   }



   /*******************************************************************************
    ** Fluent setter for lastTestStatus
    *******************************************************************************/
   public AssistantModel withLastTestStatus(String lastTestStatus)
   {
      this.lastTestStatus = lastTestStatus;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastTestLatencyMs
    *******************************************************************************/
   public Integer getLastTestLatencyMs()
   {
      return (this.lastTestLatencyMs);
   }



   /*******************************************************************************
    ** Setter for lastTestLatencyMs
    *******************************************************************************/
   public void setLastTestLatencyMs(Integer lastTestLatencyMs)
   {
      this.lastTestLatencyMs = lastTestLatencyMs;
   }



   /*******************************************************************************
    ** Fluent setter for lastTestLatencyMs
    *******************************************************************************/
   public AssistantModel withLastTestLatencyMs(Integer lastTestLatencyMs)
   {
      this.lastTestLatencyMs = lastTestLatencyMs;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastTestError
    *******************************************************************************/
   public String getLastTestError()
   {
      return (this.lastTestError);
   }



   /*******************************************************************************
    ** Setter for lastTestError
    *******************************************************************************/
   public void setLastTestError(String lastTestError)
   {
      this.lastTestError = lastTestError;
   }



   /*******************************************************************************
    ** Fluent setter for lastTestError
    *******************************************************************************/
   public AssistantModel withLastTestError(String lastTestError)
   {
      this.lastTestError = lastTestError;
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
   public AssistantModel withCreateDate(Instant createDate)
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
   public AssistantModel withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
