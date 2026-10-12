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
 ** A thumbs up / down on an assistant message.
 *******************************************************************************/
public class AssistantFeedback extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_feedback";

   /*******************************************************************************
    ** The table name before 0.1.0-RC.3 (camelCase). See UPGRADE.md.
    *******************************************************************************/
   @Deprecated(since = "0.1.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "assistantFeedback";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false)
   private Integer messageId;

   @QField(isEditable = false, possibleValueSourceName = AssistantConversation.TABLE_NAME)
   private Integer conversationId;

   @QField(isEditable = false, maxLength = 255)
   private String userId;

   @QField(isEditable = false)
   private Integer rating;

   @QField(isEditable = false, maxLength = 64, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String reason;

   @QField(isEditable = false)
   private String comment;

   @QField(isEditable = false)
   private Instant createDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantFeedback()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantFeedback(QRecord record) throws QException
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
   public AssistantFeedback withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for messageId
    *******************************************************************************/
   public Integer getMessageId()
   {
      return (this.messageId);
   }



   /*******************************************************************************
    ** Setter for messageId
    *******************************************************************************/
   public void setMessageId(Integer messageId)
   {
      this.messageId = messageId;
   }



   /*******************************************************************************
    ** Fluent setter for messageId
    *******************************************************************************/
   public AssistantFeedback withMessageId(Integer messageId)
   {
      this.messageId = messageId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for conversationId
    *******************************************************************************/
   public Integer getConversationId()
   {
      return (this.conversationId);
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
   public AssistantFeedback withConversationId(Integer conversationId)
   {
      this.conversationId = conversationId;
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
   public AssistantFeedback withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for rating
    *******************************************************************************/
   public Integer getRating()
   {
      return (this.rating);
   }



   /*******************************************************************************
    ** Setter for rating
    *******************************************************************************/
   public void setRating(Integer rating)
   {
      this.rating = rating;
   }



   /*******************************************************************************
    ** Fluent setter for rating
    *******************************************************************************/
   public AssistantFeedback withRating(Integer rating)
   {
      this.rating = rating;
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
   public AssistantFeedback withReason(String reason)
   {
      this.reason = reason;
      return (this);
   }



   /*******************************************************************************
    ** Getter for comment
    *******************************************************************************/
   public String getComment()
   {
      return (this.comment);
   }



   /*******************************************************************************
    ** Setter for comment
    *******************************************************************************/
   public void setComment(String comment)
   {
      this.comment = comment;
   }



   /*******************************************************************************
    ** Fluent setter for comment
    *******************************************************************************/
   public AssistantFeedback withComment(String comment)
   {
      this.comment = comment;
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
   public AssistantFeedback withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }

}
