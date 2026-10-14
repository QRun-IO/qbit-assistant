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
 ** Admin-owned indexing configuration. Source records never contain indexed text.
 *******************************************************************************/
public class AssistantRagSource extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_rag_source";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 200)
   private String name;

   @QField()
   private Boolean enabled;

   @QField(isRequired = true, maxLength = 200)
   private String tableName;

   @QField(isRequired = true, maxLength = 500)
   private String fieldNames;

   @QField(maxLength = 200)
   private String titleFieldName;

   @QField()
   private String filterJson;

   @QField(isRequired = true)
   private Integer embeddingModelId;

   @QField()
   private Integer chunkChars;

   @QField()
   private Integer chunkOverlapChars;

   @QField(isEditable = false)
   private Instant lastIndexedAt;

   @QField(isEditable = false, maxLength = 200)
   private String lastIndexStatus;

   @QField(isEditable = false, maxLength = 500)
   private String lastIndexError;

   @QField(isEditable = false)
   private Integer indexedRecordCount;

   @QField(isEditable = false)
   private Integer indexedChunkCount;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;


   /*******************************************************************************
    ** Empty entity for framework mapping.
    *******************************************************************************/
   public AssistantRagSource()
   {
   }



   /*******************************************************************************
    ** Map the source's own configuration record.
    *******************************************************************************/
   public AssistantRagSource(QRecord record) throws QException
   {
      populateFromQRecord(record);
   }



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
   public AssistantRagSource withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for name
    *******************************************************************************/
   public String getName()
   {
      return (name);
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
   public AssistantRagSource withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for enabled
    *******************************************************************************/
   public Boolean getEnabled()
   {
      return (enabled);
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
   public AssistantRagSource withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tableName
    *******************************************************************************/
   public String getTableName()
   {
      return (tableName);
   }



   /*******************************************************************************
    ** Setter for tableName
    *******************************************************************************/
   public void setTableName(String tableName)
   {
      this.tableName = tableName;
   }



   /*******************************************************************************
    ** Fluent setter for tableName
    *******************************************************************************/
   public AssistantRagSource withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for fieldNames
    *******************************************************************************/
   public String getFieldNames()
   {
      return (fieldNames);
   }



   /*******************************************************************************
    ** Setter for fieldNames
    *******************************************************************************/
   public void setFieldNames(String fieldNames)
   {
      this.fieldNames = fieldNames;
   }



   /*******************************************************************************
    ** Fluent setter for fieldNames
    *******************************************************************************/
   public AssistantRagSource withFieldNames(String fieldNames)
   {
      this.fieldNames = fieldNames;
      return (this);
   }



   /*******************************************************************************
    ** Getter for titleFieldName
    *******************************************************************************/
   public String getTitleFieldName()
   {
      return (titleFieldName);
   }



   /*******************************************************************************
    ** Setter for titleFieldName
    *******************************************************************************/
   public void setTitleFieldName(String titleFieldName)
   {
      this.titleFieldName = titleFieldName;
   }



   /*******************************************************************************
    ** Fluent setter for titleFieldName
    *******************************************************************************/
   public AssistantRagSource withTitleFieldName(String titleFieldName)
   {
      this.titleFieldName = titleFieldName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for filterJson
    *******************************************************************************/
   public String getFilterJson()
   {
      return (filterJson);
   }



   /*******************************************************************************
    ** Setter for filterJson
    *******************************************************************************/
   public void setFilterJson(String filterJson)
   {
      this.filterJson = filterJson;
   }



   /*******************************************************************************
    ** Fluent setter for filterJson
    *******************************************************************************/
   public AssistantRagSource withFilterJson(String filterJson)
   {
      this.filterJson = filterJson;
      return (this);
   }



   /*******************************************************************************
    ** Getter for embeddingModelId
    *******************************************************************************/
   public Integer getEmbeddingModelId()
   {
      return (embeddingModelId);
   }



   /*******************************************************************************
    ** Setter for embeddingModelId
    *******************************************************************************/
   public void setEmbeddingModelId(Integer embeddingModelId)
   {
      this.embeddingModelId = embeddingModelId;
   }



   /*******************************************************************************
    ** Fluent setter for embeddingModelId
    *******************************************************************************/
   public AssistantRagSource withEmbeddingModelId(Integer embeddingModelId)
   {
      this.embeddingModelId = embeddingModelId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for chunkChars
    *******************************************************************************/
   public Integer getChunkChars()
   {
      return (chunkChars);
   }



   /*******************************************************************************
    ** Setter for chunkChars
    *******************************************************************************/
   public void setChunkChars(Integer chunkChars)
   {
      this.chunkChars = chunkChars;
   }



   /*******************************************************************************
    ** Fluent setter for chunkChars
    *******************************************************************************/
   public AssistantRagSource withChunkChars(Integer chunkChars)
   {
      this.chunkChars = chunkChars;
      return (this);
   }



   /*******************************************************************************
    ** Getter for chunkOverlapChars
    *******************************************************************************/
   public Integer getChunkOverlapChars()
   {
      return (chunkOverlapChars);
   }



   /*******************************************************************************
    ** Setter for chunkOverlapChars
    *******************************************************************************/
   public void setChunkOverlapChars(Integer chunkOverlapChars)
   {
      this.chunkOverlapChars = chunkOverlapChars;
   }



   /*******************************************************************************
    ** Fluent setter for chunkOverlapChars
    *******************************************************************************/
   public AssistantRagSource withChunkOverlapChars(Integer chunkOverlapChars)
   {
      this.chunkOverlapChars = chunkOverlapChars;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastIndexedAt
    *******************************************************************************/
   public Instant getLastIndexedAt()
   {
      return (lastIndexedAt);
   }



   /*******************************************************************************
    ** Setter for lastIndexedAt
    *******************************************************************************/
   public void setLastIndexedAt(Instant lastIndexedAt)
   {
      this.lastIndexedAt = lastIndexedAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastIndexedAt
    *******************************************************************************/
   public AssistantRagSource withLastIndexedAt(Instant lastIndexedAt)
   {
      this.lastIndexedAt = lastIndexedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastIndexStatus
    *******************************************************************************/
   public String getLastIndexStatus()
   {
      return (lastIndexStatus);
   }



   /*******************************************************************************
    ** Setter for lastIndexStatus
    *******************************************************************************/
   public void setLastIndexStatus(String lastIndexStatus)
   {
      this.lastIndexStatus = lastIndexStatus;
   }



   /*******************************************************************************
    ** Fluent setter for lastIndexStatus
    *******************************************************************************/
   public AssistantRagSource withLastIndexStatus(String lastIndexStatus)
   {
      this.lastIndexStatus = lastIndexStatus;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastIndexError
    *******************************************************************************/
   public String getLastIndexError()
   {
      return (lastIndexError);
   }



   /*******************************************************************************
    ** Setter for lastIndexError
    *******************************************************************************/
   public void setLastIndexError(String lastIndexError)
   {
      this.lastIndexError = lastIndexError;
   }



   /*******************************************************************************
    ** Fluent setter for lastIndexError
    *******************************************************************************/
   public AssistantRagSource withLastIndexError(String lastIndexError)
   {
      this.lastIndexError = lastIndexError;
      return (this);
   }



   /*******************************************************************************
    ** Getter for indexedRecordCount
    *******************************************************************************/
   public Integer getIndexedRecordCount()
   {
      return (indexedRecordCount);
   }



   /*******************************************************************************
    ** Setter for indexedRecordCount
    *******************************************************************************/
   public void setIndexedRecordCount(Integer indexedRecordCount)
   {
      this.indexedRecordCount = indexedRecordCount;
   }



   /*******************************************************************************
    ** Fluent setter for indexedRecordCount
    *******************************************************************************/
   public AssistantRagSource withIndexedRecordCount(Integer indexedRecordCount)
   {
      this.indexedRecordCount = indexedRecordCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for indexedChunkCount
    *******************************************************************************/
   public Integer getIndexedChunkCount()
   {
      return (indexedChunkCount);
   }



   /*******************************************************************************
    ** Setter for indexedChunkCount
    *******************************************************************************/
   public void setIndexedChunkCount(Integer indexedChunkCount)
   {
      this.indexedChunkCount = indexedChunkCount;
   }



   /*******************************************************************************
    ** Fluent setter for indexedChunkCount
    *******************************************************************************/
   public AssistantRagSource withIndexedChunkCount(Integer indexedChunkCount)
   {
      this.indexedChunkCount = indexedChunkCount;
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
   public AssistantRagSource withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (modifyDate);
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
   public AssistantRagSource withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
