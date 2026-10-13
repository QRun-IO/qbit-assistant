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
 ** The price of one model, in USD per million tokens, used to compute the cost
 ** of every model call. Seeded with the Anthropic list prices; editable.
 *******************************************************************************/
public class AssistantModelPrice extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_model_price";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 50)
   private String providerType;

   @QField(isRequired = true, maxLength = 100)
   private String model;

   @QField(isRequired = true)
   private BigDecimal inputUsdPerMTok;

   @QField(isRequired = true)
   private BigDecimal outputUsdPerMTok;

   @QField()
   private BigDecimal cacheReadUsdPerMTok;

   @QField()
   private BigDecimal cacheWrite5mUsdPerMTok;

   @QField()
   private BigDecimal cacheWrite1hUsdPerMTok;

   @QField(maxLength = 255)
   private String notes;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantModelPrice()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantModelPrice(QRecord record) throws QException
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
   public AssistantModelPrice withId(Integer id)
   {
      this.id = id;
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
   public AssistantModelPrice withProviderType(String providerType)
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
   public AssistantModelPrice withModel(String model)
   {
      this.model = model;
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
   public AssistantModelPrice withInputUsdPerMTok(BigDecimal inputUsdPerMTok)
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
   public AssistantModelPrice withOutputUsdPerMTok(BigDecimal outputUsdPerMTok)
   {
      this.outputUsdPerMTok = outputUsdPerMTok;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheReadUsdPerMTok
    *******************************************************************************/
   public BigDecimal getCacheReadUsdPerMTok()
   {
      return (this.cacheReadUsdPerMTok);
   }



   /*******************************************************************************
    ** Setter for cacheReadUsdPerMTok
    *******************************************************************************/
   public void setCacheReadUsdPerMTok(BigDecimal cacheReadUsdPerMTok)
   {
      this.cacheReadUsdPerMTok = cacheReadUsdPerMTok;
   }



   /*******************************************************************************
    ** Fluent setter for cacheReadUsdPerMTok
    *******************************************************************************/
   public AssistantModelPrice withCacheReadUsdPerMTok(BigDecimal cacheReadUsdPerMTok)
   {
      this.cacheReadUsdPerMTok = cacheReadUsdPerMTok;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheWrite5mUsdPerMTok
    *******************************************************************************/
   public BigDecimal getCacheWrite5mUsdPerMTok()
   {
      return (this.cacheWrite5mUsdPerMTok);
   }



   /*******************************************************************************
    ** Setter for cacheWrite5mUsdPerMTok
    *******************************************************************************/
   public void setCacheWrite5mUsdPerMTok(BigDecimal cacheWrite5mUsdPerMTok)
   {
      this.cacheWrite5mUsdPerMTok = cacheWrite5mUsdPerMTok;
   }



   /*******************************************************************************
    ** Fluent setter for cacheWrite5mUsdPerMTok
    *******************************************************************************/
   public AssistantModelPrice withCacheWrite5mUsdPerMTok(BigDecimal cacheWrite5mUsdPerMTok)
   {
      this.cacheWrite5mUsdPerMTok = cacheWrite5mUsdPerMTok;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheWrite1hUsdPerMTok
    *******************************************************************************/
   public BigDecimal getCacheWrite1hUsdPerMTok()
   {
      return (this.cacheWrite1hUsdPerMTok);
   }



   /*******************************************************************************
    ** Setter for cacheWrite1hUsdPerMTok
    *******************************************************************************/
   public void setCacheWrite1hUsdPerMTok(BigDecimal cacheWrite1hUsdPerMTok)
   {
      this.cacheWrite1hUsdPerMTok = cacheWrite1hUsdPerMTok;
   }



   /*******************************************************************************
    ** Fluent setter for cacheWrite1hUsdPerMTok
    *******************************************************************************/
   public AssistantModelPrice withCacheWrite1hUsdPerMTok(BigDecimal cacheWrite1hUsdPerMTok)
   {
      this.cacheWrite1hUsdPerMTok = cacheWrite1hUsdPerMTok;
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
   public AssistantModelPrice withNotes(String notes)
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
   public AssistantModelPrice withCreateDate(Instant createDate)
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
   public AssistantModelPrice withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
