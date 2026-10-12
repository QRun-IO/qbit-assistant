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

package com.kingsrook.qbits.assistant.persistence;


import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantFeedback;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.provider.ModelUsage;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;


/*******************************************************************************
 ** Reads and writes the QBit's own assistant* tables through QQQ actions,
 ** under whatever session is in QContext - always the requesting user's
 ** session (the record locks on userId then confine every call to the user's
 ** own rows). Queries also filter on userId explicitly, so an admin's
 ** all-access key never mixes other users' conversations into the panel.
 **
 ** This is the only package allowed to call QQQ data actions (ArchUnit).
 *******************************************************************************/
public class AssistantStore
{
   public static final String SCOPE_USER     = "USER";
   public static final String SCOPE_INSTANCE = "INSTANCE";



   /////////////////////////////////////////////////////////////////////////////
   // generic helpers                                                         //
   /////////////////////////////////////////////////////////////////////////////

   /*******************************************************************************
    ** Query into entities.
    *******************************************************************************/
   public <T extends QRecordEntity> List<T> query(String tableName, QQueryFilter filter, Class<T> entityClass) throws QException
   {
      List<QRecord> records = new QueryAction().execute(new QueryInput(tableName).withFilter(filter)).getRecords();
      List<T>       result  = new ArrayList<>();
      for(QRecord record : CollectionUtils.nonNullList(records))
      {
         result.add(QRecordEntity.fromQRecord(entityClass, record));
      }
      return (result);
   }



   /*******************************************************************************
    ** Insert one entity; returns it with its id.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public <T extends QRecordEntity> T insert(T entity) throws QException
   {
      InsertOutput output = new InsertAction().execute(new InsertInput(entity.tableName()).withRecordEntity(entity));
      QRecord      record = output.getRecords().get(0);
      if(CollectionUtils.nullSafeHasContents(record.getErrors()))
      {
         throw (new QException("Could not save to " + entity.tableName() + ": " + record.getErrors().get(0).getMessage()));
      }
      return ((T) QRecordEntity.fromQRecord(entity.getClass(), record));
   }



   /*******************************************************************************
    ** Update one entity (all its fields).
    *******************************************************************************/
   public void update(QRecordEntity entity) throws QException
   {
      UpdateOutput output = new UpdateAction().execute(new UpdateInput(entity.tableName()).withRecordEntity(entity));
      QRecord      record = output.getRecords().get(0);
      if(CollectionUtils.nullSafeHasContents(record.getErrors()))
      {
         throw (new QException("Could not update " + entity.tableName() + ": " + record.getErrors().get(0).getMessage()));
      }
   }



   /*******************************************************************************
    ** Update some fields of one record.
    *******************************************************************************/
   public void updateFields(String tableName, QRecord record) throws QException
   {
      UpdateOutput output = new UpdateAction().execute(new UpdateInput(tableName).withRecord(record));
      QRecord      result = output.getRecords().get(0);
      if(CollectionUtils.nullSafeHasContents(result.getErrors()))
      {
         throw (new QException("Could not update " + tableName + ": " + result.getErrors().get(0).getMessage()));
      }
   }



   /*******************************************************************************
    ** Delete by filter.
    *******************************************************************************/
   public void delete(String tableName, QQueryFilter filter) throws QException
   {
      new DeleteAction().execute(new DeleteInput(tableName).withQueryFilter(filter));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QQueryFilter owned(String userId)
   {
      return (new QQueryFilter().withCriteria("userId", QCriteriaOperator.EQUALS, userId));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static <T> T first(List<T> list)
   {
      return (list.isEmpty() ? null : list.get(0));
   }



   /////////////////////////////////////////////////////////////////////////////
   // conversations                                                           //
   /////////////////////////////////////////////////////////////////////////////

   /*******************************************************************************
    **
    *******************************************************************************/
   public AssistantConversation createConversation(String userId, String title, String contextJson) throws QException
   {
      return (insert(new AssistantConversation()
         .withUserId(userId)
         .withTitle(title)
         .withStatus("ACTIVE")
         .withPinned(false)
         .withUnreadCount(0)
         .withContextJson(contextJson)
         .withTotalTokensIn(0L)
         .withTotalTokensOut(0L)
         .withTotalCostMicros(0L)));
   }



   /*******************************************************************************
    ** The user's conversations in a status, newest activity first.
    *******************************************************************************/
   public List<AssistantConversation> listConversations(String userId, String status) throws QException
   {
      List<AssistantConversation> list = query(AssistantConversation.TABLE_NAME, owned(userId)
         .withCriteria("status", QCriteriaOperator.EQUALS, status), AssistantConversation.class);

      /////////////////////////////////////////////////////////////////////
      // pinned first, then newest activity (or creation) first, then id //
      /////////////////////////////////////////////////////////////////////
      list.sort((a, b) ->
      {
         int pinned = Boolean.compare(Boolean.TRUE.equals(b.getPinned()), Boolean.TRUE.equals(a.getPinned()));
         if(pinned != 0)
         {
            return (pinned);
         }
         Instant ia = a.getLastMessageAt() != null ? a.getLastMessageAt() : a.getCreateDate();
         Instant ib = b.getLastMessageAt() != null ? b.getLastMessageAt() : b.getCreateDate();
         int     c  = (ib == null ? Instant.EPOCH : ib).compareTo(ia == null ? Instant.EPOCH : ia);
         return (c != 0 ? c : Integer.compare(b.getId(), a.getId()));
      });
      return (list);
   }



   /*******************************************************************************
    ** One of the user's conversations, or null.
    *******************************************************************************/
   public AssistantConversation getConversation(String userId, Integer id) throws QException
   {
      return (first(query(AssistantConversation.TABLE_NAME, owned(userId).withCriteria("id", QCriteriaOperator.EQUALS, id), AssistantConversation.class)));
   }



   /*******************************************************************************
    ** Delete a conversation and everything under it.
    *******************************************************************************/
   public void deleteConversation(String userId, Integer conversationId) throws QException
   {
      List<Serializable> turnIds = new ArrayList<>();
      for(AssistantTurn turn : query(AssistantTurn.TABLE_NAME, owned(userId).withCriteria("conversationId", QCriteriaOperator.EQUALS, conversationId), AssistantTurn.class))
      {
         turnIds.add(turn.getId());
      }
      if(!turnIds.isEmpty())
      {
         delete(AssistantTurnEvent.TABLE_NAME, owned(userId).withCriteria("turnId", QCriteriaOperator.IN, turnIds));
      }
      for(String table : List.of(AssistantToolCall.TABLE_NAME, AssistantFeedback.TABLE_NAME, AssistantMessage.TABLE_NAME, AssistantTurn.TABLE_NAME))
      {
         delete(table, owned(userId).withCriteria("conversationId", QCriteriaOperator.EQUALS, conversationId));
      }
      delete(AssistantConversation.TABLE_NAME, owned(userId).withCriteria("id", QCriteriaOperator.EQUALS, conversationId));
   }



   /////////////////////////////////////////////////////////////////////////////
   // messages                                                                //
   /////////////////////////////////////////////////////////////////////////////

   /*******************************************************************************
    ** A page of a conversation's messages, oldest first: the newest `limit`
    ** messages, or the ones before `beforeId`.
    *******************************************************************************/
   public List<AssistantMessage> listMessages(String userId, Integer conversationId, Integer beforeId, int limit) throws QException
   {
      QQueryFilter filter = owned(userId).withCriteria("conversationId", QCriteriaOperator.EQUALS, conversationId);
      if(beforeId != null)
      {
         filter.withCriteria("id", QCriteriaOperator.LESS_THAN, beforeId);
      }
      filter.withOrderBy(new QFilterOrderBy("id", false)).withLimit(limit);

      List<AssistantMessage> list = query(AssistantMessage.TABLE_NAME, filter, AssistantMessage.class);
      Collections.reverse(list);
      return (list);
   }



   /*******************************************************************************
    ** One of the user's messages, or null.
    *******************************************************************************/
   public AssistantMessage getMessage(String userId, Integer id) throws QException
   {
      return (first(query(AssistantMessage.TABLE_NAME, owned(userId).withCriteria("id", QCriteriaOperator.EQUALS, id), AssistantMessage.class)));
   }



   /*******************************************************************************
    ** Next message sequence number in a conversation.
    *******************************************************************************/
   public int nextMessageSeq(String userId, Integer conversationId) throws QException
   {
      List<AssistantMessage> last = query(AssistantMessage.TABLE_NAME, owned(userId)
         .withCriteria("conversationId", QCriteriaOperator.EQUALS, conversationId)
         .withOrderBy(new QFilterOrderBy("id", false))
         .withLimit(1), AssistantMessage.class);
      return (last.isEmpty() || last.get(0).getSeq() == null ? 1 : last.get(0).getSeq() + 1);
   }



   /////////////////////////////////////////////////////////////////////////////
   // turns and events                                                        //
   /////////////////////////////////////////////////////////////////////////////

   /*******************************************************************************
    ** One of the user's turns, or null.
    *******************************************************************************/
   public AssistantTurn getTurn(String userId, Integer id) throws QException
   {
      return (first(query(AssistantTurn.TABLE_NAME, owned(userId).withCriteria("id", QCriteriaOperator.EQUALS, id), AssistantTurn.class)));
   }



   /*******************************************************************************
    ** The user's turns still running (any replica).
    *******************************************************************************/
   public List<AssistantTurn> listRunningTurns(String userId) throws QException
   {
      return (query(AssistantTurn.TABLE_NAME, owned(userId).withCriteria("status", QCriteriaOperator.IN, List.of("RUNNING", "CANCELLING")), AssistantTurn.class));
   }



   /*******************************************************************************
    ** Persist one streamed event.
    *******************************************************************************/
   public void insertEvent(Integer turnId, String userId, int seq, String eventType, String dataJson) throws QException
   {
      insert(new AssistantTurnEvent()
         .withTurnId(turnId)
         .withUserId(userId)
         .withSeq(seq)
         .withEventType(eventType)
         .withDataJson(dataJson));
   }



   /*******************************************************************************
    ** A turn's persisted events after a sequence number, in order.
    *******************************************************************************/
   public List<AssistantTurnEvent> listEvents(String userId, Integer turnId, int afterSeq) throws QException
   {
      return (query(AssistantTurnEvent.TABLE_NAME, owned(userId)
         .withCriteria("turnId", QCriteriaOperator.EQUALS, turnId)
         .withCriteria("seq", QCriteriaOperator.GREATER_THAN, afterSeq)
         .withOrderBy(new QFilterOrderBy("seq", true)), AssistantTurnEvent.class));
   }



   /////////////////////////////////////////////////////////////////////////////
   // policy and usage                                                        //
   /////////////////////////////////////////////////////////////////////////////

   /*******************************************************************************
    ** The policy row (lowest id), or null.
    *******************************************************************************/
   public AssistantPolicy loadPolicy() throws QException
   {
      return (first(query(AssistantPolicy.TABLE_NAME, new QQueryFilter().withOrderBy(new QFilterOrderBy("id", true)).withLimit(1), AssistantPolicy.class)));
   }



   /*******************************************************************************
    ** A user's usage row for a day (or null).
    *******************************************************************************/
   public AssistantUsageDaily getUserUsage(String userId, LocalDate date) throws QException
   {
      return (first(query(AssistantUsageDaily.TABLE_NAME, owned(userId)
         .withCriteria("scope", QCriteriaOperator.EQUALS, SCOPE_USER)
         .withCriteria("usageDate", QCriteriaOperator.EQUALS, date), AssistantUsageDaily.class)));
   }



   /*******************************************************************************
    ** The instance usage rows of a date range (inclusive).
    *******************************************************************************/
   public List<AssistantUsageDaily> listInstanceUsage(LocalDate from, LocalDate to) throws QException
   {
      return (query(AssistantUsageDaily.TABLE_NAME, new QQueryFilter()
         .withCriteria("scope", QCriteriaOperator.EQUALS, SCOPE_INSTANCE)
         .withCriteria("usageDate", QCriteriaOperator.BETWEEN, from, to), AssistantUsageDaily.class));
   }



   /*******************************************************************************
    ** Add a turn's usage to the user's and the instance's rows of the day.
    ** (Read-modify-write: concurrent turns of different users can lose an
    ** increment on the instance row; budgets are approximate by design.)
    *******************************************************************************/
   public void addUsage(String userId, LocalDate date, ModelUsage usage, long costMicros, boolean countTurn) throws QException
   {
      AssistantUsageDaily userRow = getUserUsage(userId, date);
      if(userRow == null)
      {
         userRow = new AssistantUsageDaily().withScope(SCOPE_USER).withUserId(userId).withUsageDate(date);
      }
      AssistantUsageDaily instanceRow = first(query(AssistantUsageDaily.TABLE_NAME, new QQueryFilter()
         .withCriteria("scope", QCriteriaOperator.EQUALS, SCOPE_INSTANCE)
         .withCriteria("userId", QCriteriaOperator.IS_BLANK)
         .withCriteria("usageDate", QCriteriaOperator.EQUALS, date), AssistantUsageDaily.class));
      if(instanceRow == null)
      {
         instanceRow = new AssistantUsageDaily().withScope(SCOPE_INSTANCE).withUsageDate(date);
      }

      for(AssistantUsageDaily row : List.of(userRow, instanceRow))
      {
         row.setTurnCount(nz(row.getTurnCount()) + (countTurn ? 1 : 0));
         row.setInputTokens(nz(row.getInputTokens()) + nz(usage.getInputTokens()));
         row.setOutputTokens(nz(row.getOutputTokens()) + nz(usage.getOutputTokens()));
         row.setCacheReadTokens(nz(row.getCacheReadTokens()) + nz(usage.getCacheReadTokens()));
         row.setCacheWriteTokens(nz(row.getCacheWriteTokens()) + nz(usage.getCacheWriteTokens()));
         row.setCostMicros(nz(row.getCostMicros()) + costMicros);
         if(row.getId() == null)
         {
            insert(row);
         }
         else
         {
            update(row);
         }
      }
   }



   /////////////////////////////////////////////////////////////////////////////
   // retention                                                               //
   /////////////////////////////////////////////////////////////////////////////

   /*******************************************************************************
    ** Conversations (any owner visible to the session) not active since a time.
    *******************************************************************************/
   public List<AssistantConversation> listConversationsInactiveSince(Instant cutoff, int limit) throws QException
   {
      return (query(AssistantConversation.TABLE_NAME, new QQueryFilter()
         .withCriteria("modifyDate", QCriteriaOperator.LESS_THAN, cutoff)
         .withOrderBy(new QFilterOrderBy("id", true))
         .withLimit(limit), AssistantConversation.class));
   }



   /*******************************************************************************
    ** Null out resultJson of tool calls created before a time; returns count.
    *******************************************************************************/
   public int clearToolResultsBefore(Instant cutoff, int limit) throws QException
   {
      List<AssistantToolCall> calls = query(AssistantToolCall.TABLE_NAME, new QQueryFilter()
         .withCriteria("createDate", QCriteriaOperator.LESS_THAN, cutoff)
         .withCriteria("resultJson", QCriteriaOperator.IS_NOT_BLANK)
         .withLimit(limit), AssistantToolCall.class);
      if(calls.isEmpty())
      {
         return (0);
      }
      List<QRecord> records = new ArrayList<>();
      for(AssistantToolCall call : calls)
      {
         records.add(new QRecord().withValue("id", call.getId()).withValue("resultJson", null));
      }
      new UpdateAction().execute(new UpdateInput(AssistantToolCall.TABLE_NAME).withRecords(records));
      return (calls.size());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static long nz(Long value)
   {
      return (value == null ? 0 : value);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static int nz(Integer value)
   {
      return (value == null ? 0 : value);
   }

}
