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
import com.kingsrook.qqq.backend.core.model.metadata.fields.ValueTooLongBehavior;


/*******************************************************************************
 ** One assistant conversation, owned by a user (record lock on userId).
 *******************************************************************************/
public class AssistantConversation extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_conversation";

   /*******************************************************************************
    ** The table name before 0.1.0-RC.2 (camelCase). See UPGRADE.md.
    *******************************************************************************/
   @Deprecated(since = "0.1.0-RC.2")
   public static final String LEGACY_TABLE_NAME = "assistantConversation";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField(maxLength = 255, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String title;

   @QField(maxLength = 16)
   private String status;

   @QField()
   private Boolean pinned;

   @QField(isEditable = false)
   private Instant lastMessageAt;

   @QField(isEditable = false)
   private Integer unreadCount;

   @QField(isEditable = false)
   private String contextJson;

   @QField(isEditable = false)
   private Long totalTokensIn;

   @QField(isEditable = false)
   private Long totalTokensOut;

   @QField(isEditable = false)
   private Long totalCostMicros;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantConversation()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantConversation(QRecord record) throws QException
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
   public AssistantConversation withId(Integer id)
   {
      this.id = id;
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
   public AssistantConversation withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for title
    *******************************************************************************/
   public String getTitle()
   {
      return (this.title);
   }



   /*******************************************************************************
    ** Setter for title
    *******************************************************************************/
   public void setTitle(String title)
   {
      this.title = title;
   }



   /*******************************************************************************
    ** Fluent setter for title
    *******************************************************************************/
   public AssistantConversation withTitle(String title)
   {
      this.title = title;
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
   public AssistantConversation withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for pinned
    *******************************************************************************/
   public Boolean getPinned()
   {
      return (this.pinned);
   }



   /*******************************************************************************
    ** Setter for pinned
    *******************************************************************************/
   public void setPinned(Boolean pinned)
   {
      this.pinned = pinned;
   }



   /*******************************************************************************
    ** Fluent setter for pinned
    *******************************************************************************/
   public AssistantConversation withPinned(Boolean pinned)
   {
      this.pinned = pinned;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastMessageAt
    *******************************************************************************/
   public Instant getLastMessageAt()
   {
      return (this.lastMessageAt);
   }



   /*******************************************************************************
    ** Setter for lastMessageAt
    *******************************************************************************/
   public void setLastMessageAt(Instant lastMessageAt)
   {
      this.lastMessageAt = lastMessageAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastMessageAt
    *******************************************************************************/
   public AssistantConversation withLastMessageAt(Instant lastMessageAt)
   {
      this.lastMessageAt = lastMessageAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for unreadCount
    *******************************************************************************/
   public Integer getUnreadCount()
   {
      return (this.unreadCount);
   }



   /*******************************************************************************
    ** Setter for unreadCount
    *******************************************************************************/
   public void setUnreadCount(Integer unreadCount)
   {
      this.unreadCount = unreadCount;
   }



   /*******************************************************************************
    ** Fluent setter for unreadCount
    *******************************************************************************/
   public AssistantConversation withUnreadCount(Integer unreadCount)
   {
      this.unreadCount = unreadCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for contextJson
    *******************************************************************************/
   public String getContextJson()
   {
      return (this.contextJson);
   }



   /*******************************************************************************
    ** Setter for contextJson
    *******************************************************************************/
   public void setContextJson(String contextJson)
   {
      this.contextJson = contextJson;
   }



   /*******************************************************************************
    ** Fluent setter for contextJson
    *******************************************************************************/
   public AssistantConversation withContextJson(String contextJson)
   {
      this.contextJson = contextJson;
      return (this);
   }



   /*******************************************************************************
    ** Getter for totalTokensIn
    *******************************************************************************/
   public Long getTotalTokensIn()
   {
      return (this.totalTokensIn);
   }



   /*******************************************************************************
    ** Setter for totalTokensIn
    *******************************************************************************/
   public void setTotalTokensIn(Long totalTokensIn)
   {
      this.totalTokensIn = totalTokensIn;
   }



   /*******************************************************************************
    ** Fluent setter for totalTokensIn
    *******************************************************************************/
   public AssistantConversation withTotalTokensIn(Long totalTokensIn)
   {
      this.totalTokensIn = totalTokensIn;
      return (this);
   }



   /*******************************************************************************
    ** Getter for totalTokensOut
    *******************************************************************************/
   public Long getTotalTokensOut()
   {
      return (this.totalTokensOut);
   }



   /*******************************************************************************
    ** Setter for totalTokensOut
    *******************************************************************************/
   public void setTotalTokensOut(Long totalTokensOut)
   {
      this.totalTokensOut = totalTokensOut;
   }



   /*******************************************************************************
    ** Fluent setter for totalTokensOut
    *******************************************************************************/
   public AssistantConversation withTotalTokensOut(Long totalTokensOut)
   {
      this.totalTokensOut = totalTokensOut;
      return (this);
   }



   /*******************************************************************************
    ** Getter for totalCostMicros
    *******************************************************************************/
   public Long getTotalCostMicros()
   {
      return (this.totalCostMicros);
   }



   /*******************************************************************************
    ** Setter for totalCostMicros
    *******************************************************************************/
   public void setTotalCostMicros(Long totalCostMicros)
   {
      this.totalCostMicros = totalCostMicros;
   }



   /*******************************************************************************
    ** Fluent setter for totalCostMicros
    *******************************************************************************/
   public AssistantConversation withTotalCostMicros(Long totalCostMicros)
   {
      this.totalCostMicros = totalCostMicros;
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
   public AssistantConversation withCreateDate(Instant createDate)
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
   public AssistantConversation withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}
