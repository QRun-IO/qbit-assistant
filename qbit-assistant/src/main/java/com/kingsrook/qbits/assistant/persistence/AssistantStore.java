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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.classification.Classifications;
import com.kingsrook.qbits.assistant.engine.TurnClassification;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantBudgetEvent;
import com.kingsrook.qbits.assistant.model.AssistantBudgetOverride;
import com.kingsrook.qbits.assistant.model.AssistantBudgetUsage;
import com.kingsrook.qbits.assistant.model.AssistantClientError;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantDataAudit;
import com.kingsrook.qbits.assistant.model.AssistantFeedback;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantModelPrice;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantScheduledRun;
import com.kingsrook.qbits.assistant.model.AssistantScheduledTask;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.scheduled.ScheduledTasks;
import com.kingsrook.qbits.assistant.spi.ModelUsage;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.ActionFlag;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
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
 ** Reads and writes the QBit's own assistant_* tables through QQQ actions,
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

   public static final String OWN_TABLE_PREFIX = "assistant_";

   public static final int MAX_CONVERSATIONS = 200;
   public static final int MAX_RUNNING_TURNS = 50;
   public static final int EVENT_PAGE_SIZE   = 500;
   public static final int MAX_PROVIDERS     = 100;
   public static final int MAX_PRICES        = 500;
   public static final int MAX_SUM_ROWS      = 100_000;

   private final ConversationEncryption encryption;
   private static final Map<String, List<String>> ENCRYPTED_FIELDS = Map.of(
      AssistantMessage.TABLE_NAME, List.of("partsJson"),
      AssistantConversation.TABLE_NAME, List.of("title", "contextJson"),
      AssistantToolCall.TABLE_NAME, List.of("inputJson", "resultJson", "redactionsJson", "resultSummary"),
      AssistantTurnEvent.TABLE_NAME, List.of("dataJson"),
      AssistantPendingAction.TABLE_NAME, List.of("paramsJson", "summary", "resultSummary"));

   private static final Map<String, Object> USAGE_LOCKS = new ConcurrentHashMap<>();



   /////////////////////////////////////////////////////////////////////////////
   // generic helpers                                                         //
   /////////////////////////////////////////////////////////////////////////////

   /*******************************************************************************
    ** The store only ever touches the QBit's own tables (assistant_*): the
    ** user's application data is reached only through the UI API.
    *******************************************************************************/
   public static void requireOwnTable(String tableName) throws QException
   {
      if(tableName == null || !tableName.startsWith(OWN_TABLE_PREFIX))
      {
         throw (new QException("The assistant store only touches its own tables, not " + tableName + "; application data goes through the UI API."));
      }
   }



   /*******************************************************************************
    ** Query into entities.
    *******************************************************************************/
   public <T extends QRecordEntity> List<T> query(String tableName, QQueryFilter filter, Class<T> entityClass) throws QException
   {
      requireOwnTable(tableName);
      List<QRecord> records = new QueryAction().execute(new QueryInput(tableName).withFilter(filter)).getRecords();
      List<T>       result  = new ArrayList<>();
      for(QRecord record : CollectionUtils.nonNullList(records))
      {
         result.add(QRecordEntity.fromQRecord(entityClass, cryptRecord(tableName, record, false)));
      }
      return (result);
   }



   /*******************************************************************************
    ** Insert one entity; returns it with its id.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public <T extends QRecordEntity> T insert(T entity) throws QException
   {
      requireOwnTable(entity.tableName());
      InsertOutput output = new InsertAction().execute(new InsertInput(entity.tableName()).withRecord(cryptRecord(entity.tableName(), entity.toQRecord(), true)));
      QRecord      record = output.getRecords().get(0);
      if(CollectionUtils.nullSafeHasContents(record.getErrors()))
      {
         throw (new QException("Could not save to " + entity.tableName() + ": " + record.getErrors().get(0).getMessage()));
      }
      if(entity instanceof AssistantUsage)
      {
         projectBudgetUsage(record);
      }
      rememberTranscript(entity.tableName(), entity.toQRecord(), record.getValue("id"));
      QRecord returned = cryptRecord(entity.tableName(), record, false);
      if(TranscriptRedactor.current() != null)
      {
         TranscriptRedactor.current().restore(returned, entity.toQRecord());
      }
      return ((T) QRecordEntity.fromQRecord(entity.getClass(), returned));
   }



   /*******************************************************************************
    ** Update one entity (all its fields).
    *******************************************************************************/
   public void update(QRecordEntity entity) throws QException
   {
      requireOwnTable(entity.tableName());
      rememberTranscript(entity.tableName(), entity.toQRecord(), entity.toQRecord().getValue("id"));
      UpdateOutput output = new UpdateAction().execute(new UpdateInput(entity.tableName()).withRecord(cryptRecord(entity.tableName(), entity.toQRecord(), true)));
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
      requireOwnTable(tableName);
      rememberTranscript(tableName, record, record.getValue("id"));
      UpdateOutput output = new UpdateAction().execute(new UpdateInput(tableName).withRecord(cryptRecord(tableName, record, true)));
      QRecord      result = output.getRecords().get(0);
      if(CollectionUtils.nullSafeHasContents(result.getErrors()))
      {
         throw (new QException("Could not update " + tableName + ": " + result.getErrors().get(0).getMessage()));
      }
      if(AssistantUsage.TABLE_NAME.equals(tableName))
      {
         AssistantUsage usage = first(query(tableName, new QQueryFilter().withCriteria("id", QCriteriaOperator.EQUALS, record.getValue("id")), AssistantUsage.class));
         projectBudgetUsage(usage.toQRecord());
      }
   }



   /*******************************************************************************
    ** Delete by filter.
    *******************************************************************************/
   public void delete(String tableName, QQueryFilter filter) throws QException
   {
      requireOwnTable(tableName);
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
         .withCriteria("status", QCriteriaOperator.EQUALS, status)
         .withOrderBy(new QFilterOrderBy("id", false))
         .withLimit(MAX_CONVERSATIONS), AssistantConversation.class);

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
      deleteConversationChildren(userId, conversationId);
      delete(AssistantConversation.TABLE_NAME, owned(userId).withCriteria("id", QCriteriaOperator.EQUALS, conversationId));
   }



   /*******************************************************************************
    ** Delete a conversation's child rows (turns and their events, messages,
    ** tool calls, feedback, data audits, pending actions), not the conversation
    ** itself. A table delete of the conversation calls this afterwards, so
    ** nothing is orphaned on a database without the cascading foreign keys.
    *******************************************************************************/
   public void deleteConversationChildren(String userId, Integer conversationId) throws QException
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
      delete(AssistantDataAudit.TABLE_NAME, owned(userId).withCriteria("conversationId", QCriteriaOperator.EQUALS, conversationId));
      delete(AssistantPendingAction.TABLE_NAME, owned(userId).withCriteria("conversationId", QCriteriaOperator.EQUALS, conversationId));
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
    ** Re-read before a lifecycle update. A completed row is never reopened by
    ** a late local Stop or overwritten by a second local finalizer. Every read
    ** and update remains under the caller's own session and owner filter.
    *******************************************************************************/
   public boolean updateRunningTurn(String userId, QRecord changes) throws QException
   {
      synchronized(QContext.getQInstance())
      {
         AssistantTurn current = getTurn(userId, changes.getValueInteger("id"));
         if(current == null || !("RUNNING".equals(current.getStatus()) || "CANCELLING".equals(current.getStatus())))
         {
            return (false);
         }
         updateFields(AssistantTurn.TABLE_NAME, changes);
         return (true);
      }
   }



   /*******************************************************************************
    ** One of the user's own pending actions, or null.
    *******************************************************************************/
   public AssistantPendingAction getPendingAction(String userId, Integer id) throws QException
   {
      return (first(query(AssistantPendingAction.TABLE_NAME, owned(userId).withCriteria("id", QCriteriaOperator.EQUALS, id), AssistantPendingAction.class)));
   }



   /*******************************************************************************
    ** The user's turns still running (any replica).
    *******************************************************************************/
   public List<AssistantTurn> listRunningTurns(String userId) throws QException
   {
      return (query(AssistantTurn.TABLE_NAME, owned(userId)
         .withCriteria("status", QCriteriaOperator.IN, List.of("RUNNING", "CANCELLING"))
         .withOrderBy(new QFilterOrderBy("id", false))
         .withLimit(MAX_RUNNING_TURNS), AssistantTurn.class));
   }



   /*******************************************************************************
    ** Runs of any user (that the running session can see) still RUNNING or
    ** CANCELLING although they started before a time, by id after afterId
    ** (the retention sweep of abandoned runs).
    *******************************************************************************/
   public List<AssistantTurn> listRunningTurnsStartedBefore(Instant cutoff, Integer afterId, int limit) throws QException
   {
      return (query(AssistantTurn.TABLE_NAME, new QQueryFilter()
         .withCriteria("status", QCriteriaOperator.IN, List.of("RUNNING", "CANCELLING"))
         .withCriteria("startedAt", QCriteriaOperator.LESS_THAN, cutoff)
         .withCriteria("id", QCriteriaOperator.GREATER_THAN, afterId == null ? 0 : afterId)
         .withOrderBy(new QFilterOrderBy("id", true))
         .withLimit(limit), AssistantTurn.class));
   }



   /*******************************************************************************
    ** Persist one streamed event.
    *******************************************************************************/
   public void insertEvent(Integer turnId, String userId, int seq, String eventType, String dataJson) throws QException
   {
      if(TurnEvent.MESSAGE_END.equals(eventType) || TurnEvent.ERROR.equals(eventType))
      {
         // Serialize memory-backend callers. The Postgres partial unique index
         // arbitrates this same race between independent replicas.
         synchronized(QContext.getQInstance())
         {
            if(hasTerminalEvent(userId, turnId))
            {
               return;
            }
            try
            {
               insertStreamEvent(turnId, userId, seq, eventType, dataJson);
            }
            catch(QException e)
            {
               if(!hasTerminalEvent(userId, turnId))
               {
                  throw (e);
               }
            }
         }
         return;
      }
      insertStreamEvent(turnId, userId, seq, eventType, dataJson);
   }



   /*******************************************************************************
    ** Whether a terminal replay frame has already been persisted by any owner.
    *******************************************************************************/
   private boolean hasTerminalEvent(String userId, Integer turnId) throws QException
   {
      return (!query(AssistantTurnEvent.TABLE_NAME, owned(userId).withCriteria("turnId", QCriteriaOperator.EQUALS, turnId)
         .withCriteria("eventType", QCriteriaOperator.EQUALS, TurnEvent.MESSAGE_END).withLimit(1), AssistantTurnEvent.class).isEmpty());
   }



   /*******************************************************************************
    ** Persist one frame through the normal encrypted, user-owned store path.
    *******************************************************************************/
   private void insertStreamEvent(Integer turnId, String userId, int seq, String eventType, String dataJson) throws QException
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
   public List<AssistantTurnEvent> listEvents(String userId, Integer turnId, int afterSeq, int limit) throws QException
   {
      return (query(AssistantTurnEvent.TABLE_NAME, owned(userId)
         .withCriteria("turnId", QCriteriaOperator.EQUALS, turnId)
         .withCriteria("seq", QCriteriaOperator.GREATER_THAN, afterSeq)
         .withOrderBy(new QFilterOrderBy("seq", true))
         .withLimit(limit), AssistantTurnEvent.class));
   }



   /*******************************************************************************
    ** The highest persisted event seq of a turn (0 when none).
    *******************************************************************************/
   public int lastEventSeq(String userId, Integer turnId) throws QException
   {
      List<AssistantTurnEvent> last = query(AssistantTurnEvent.TABLE_NAME, owned(userId)
         .withCriteria("turnId", QCriteriaOperator.EQUALS, turnId)
         .withOrderBy(new QFilterOrderBy("seq", false))
         .withLimit(1), AssistantTurnEvent.class);
      return (last.isEmpty() || last.get(0).getSeq() == null ? 0 : last.get(0).getSeq());
   }



   /*******************************************************************************
    ** The user's latest tool calls on a table, newest first.
    *******************************************************************************/
   public List<AssistantToolCall> recentToolCalls(String userId, String tableName, int limit) throws QException
   {
      return (query(AssistantToolCall.TABLE_NAME, owned(userId)
         .withCriteria("tableName", QCriteriaOperator.EQUALS, tableName)
         .withOrderBy(new QFilterOrderBy("id", false))
         .withLimit(limit), AssistantToolCall.class));
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
    **
    ** QQQ has no atomic increment, so this is read-modify-write, made safe as
    ** far as one JVM goes: every row key (scope, userId, date) has its own lock,
    ** the USER rows have a unique key (scope, userId, usageDate), and an insert
    ** that loses the race to a concurrent insert is retried as an update.
    ** Remaining gap: two replicas updating the same row in the same instant
    ** (the INSTANCE row under heavy multi-replica load) can lose one increment.
    *******************************************************************************/
   public void addUsage(String userId, LocalDate date, ModelUsage usage, long costMicros, boolean countTurn) throws QException
   {
      increment(SCOPE_USER, userId, date, usage, costMicros, countTurn);
      increment(SCOPE_INSTANCE, null, date, usage, costMicros, countTurn);
   }



   /*******************************************************************************
    ** Increment one usage row (under its in-process lock, insert-race retry).
    *******************************************************************************/
   private void increment(String scope, String userId, LocalDate date, ModelUsage usage, long costMicros, boolean countTurn) throws QException
   {
      Object lock = USAGE_LOCKS.computeIfAbsent(scope + "|" + userId + "|" + date, k -> new Object());
      synchronized(lock)
      {
         for(int attempt = 0; ; attempt++)
         {
            AssistantUsageDaily row = usageRow(scope, userId, date);
            if(row == null)
            {
               row = new AssistantUsageDaily().withScope(scope).withUserId(userId).withUsageDate(date);
            }
            row.setTurnCount(nz(row.getTurnCount()) + (countTurn ? 1 : 0));
            row.setInputTokens(nz(row.getInputTokens()) + nz(usage.getInputTokens()));
            row.setOutputTokens(nz(row.getOutputTokens()) + nz(usage.getOutputTokens()));
            row.setCacheReadTokens(nz(row.getCacheReadTokens()) + nz(usage.getCacheReadTokens()));
            row.setCacheWriteTokens(nz(row.getCacheWriteTokens()) + nz(usage.getCacheWriteTokens()));
            row.setCostMicros(nz(row.getCostMicros()) + costMicros);
            try
            {
               if(row.getId() == null)
               {
                  insert(row);
               }
               else
               {
                  update(row);
               }
               return;
            }
            catch(QException e)
            {
               ///////////////////////////////////////////////////////////////
               // a concurrent insert of the same key (unique key) - re-read //
               ///////////////////////////////////////////////////////////////
               if(row.getId() != null || attempt >= 2)
               {
                  throw (e);
               }
            }
         }
      }
   }



   /*******************************************************************************
    ** One usage row by key, or null.
    *******************************************************************************/
   private AssistantUsageDaily usageRow(String scope, String userId, LocalDate date) throws QException
   {
      QQueryFilter filter = new QQueryFilter()
         .withCriteria("scope", QCriteriaOperator.EQUALS, scope)
         .withCriteria("usageDate", QCriteriaOperator.EQUALS, date);
      if(userId == null)
      {
         filter.withCriteria("userId", QCriteriaOperator.IS_BLANK);
      }
      else
      {
         filter.withCriteria("userId", QCriteriaOperator.EQUALS, userId);
      }
      return (first(query(AssistantUsageDaily.TABLE_NAME, filter.withOrderBy(new QFilterOrderBy("id", true)), AssistantUsageDaily.class)));
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
    ** Delete stream events (replay data, incl. render payloads) created before
    ** a time; returns how many.
    *******************************************************************************/
   public int deleteTurnEventsBefore(Instant cutoff, int limit) throws QException
   {
      List<AssistantTurnEvent> events = query(AssistantTurnEvent.TABLE_NAME, new QQueryFilter()
         .withCriteria("createDate", QCriteriaOperator.LESS_THAN, cutoff)
         .withLimit(limit), AssistantTurnEvent.class);
      if(events.isEmpty())
      {
         return (0);
      }
      List<Serializable> ids = new ArrayList<>();
      events.forEach(e -> ids.add(e.getId()));
      new DeleteAction().execute(new DeleteInput(AssistantTurnEvent.TABLE_NAME).withPrimaryKeys(ids));
      return (events.size());
   }



   /*******************************************************************************
    ** How many client error reports (that the running session can see) were
    ** stored since a time.
    *******************************************************************************/
   public int countClientErrorsSince(Instant since) throws QException
   {
      Integer count = new CountAction().execute(new CountInput(AssistantClientError.TABLE_NAME)
         .withFilter(new QQueryFilter().withCriteria("createDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS, since))).getCount();
      return (count == null ? 0 : count);
   }



   /*******************************************************************************
    ** Delete client error reports stored before a time; returns how many.
    *******************************************************************************/
   public int deleteClientErrorsBefore(Instant cutoff, int limit) throws QException
   {
      List<AssistantClientError> errors = query(AssistantClientError.TABLE_NAME, new QQueryFilter()
         .withCriteria("createDate", QCriteriaOperator.LESS_THAN, cutoff)
         .withLimit(limit), AssistantClientError.class);
      if(errors.isEmpty())
      {
         return (0);
      }
      List<Serializable> ids = new ArrayList<>();
      errors.forEach(e -> ids.add(e.getId()));
      new DeleteAction().execute(new DeleteInput(AssistantClientError.TABLE_NAME).withPrimaryKeys(ids));
      return (errors.size());
   }



   /*******************************************************************************
    ** A page of assistant messages created before a time, by id after afterId.
    *******************************************************************************/
   public List<AssistantMessage> listAssistantMessagesBefore(Instant cutoff, int afterId, int limit) throws QException
   {
      return (query(AssistantMessage.TABLE_NAME, new QQueryFilter()
         .withCriteria("role", QCriteriaOperator.EQUALS, "ASSISTANT")
         .withCriteria("createDate", QCriteriaOperator.LESS_THAN, cutoff)
         .withCriteria("id", QCriteriaOperator.GREATER_THAN, afterId)
         .withOrderBy(new QFilterOrderBy("id", true))
         .withLimit(limit), AssistantMessage.class));
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


   /////////////////////////////////////////////////////////////////////////////
   // provider settings, prices and per-call usage                            //
   /////////////////////////////////////////////////////////////////////////////

   /*******************************************************************************
    ** All provider rows, by id.
    *******************************************************************************/
   public List<AssistantProvider> listProviders() throws QException
   {
      return (query(AssistantProvider.TABLE_NAME, new QQueryFilter().withOrderBy(new QFilterOrderBy("id", true)).withLimit(MAX_PROVIDERS), AssistantProvider.class));
   }



   /*******************************************************************************
    ** One provider row, or null.
    *******************************************************************************/
   public AssistantProvider getProvider(Integer id) throws QException
   {
      return (first(query(AssistantProvider.TABLE_NAME, new QQueryFilter().withCriteria("id", QCriteriaOperator.EQUALS, id), AssistantProvider.class)));
   }



   /*******************************************************************************
    ** Make every provider row other than `keepId` non-default.
    *******************************************************************************/
   public void clearOtherDefaultProviders(Integer keepId) throws QException
   {
      List<AssistantProvider> others = query(AssistantProvider.TABLE_NAME, new QQueryFilter()
         .withCriteria("isDefault", QCriteriaOperator.EQUALS, true)
         .withCriteria("id", QCriteriaOperator.NOT_EQUALS, keepId), AssistantProvider.class);
      for(AssistantProvider other : others)
      {
         updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", other.getId()).withValue("isDefault", false));
      }
   }



   /*******************************************************************************
    ** All price rows.
    *******************************************************************************/
   public List<AssistantModelPrice> listPrices() throws QException
   {
      return (query(AssistantModelPrice.TABLE_NAME, new QQueryFilter().withOrderBy(new QFilterOrderBy("id", true)).withLimit(MAX_PRICES), AssistantModelPrice.class));
   }



   /*******************************************************************************
    ** The cost (micro-dollars) of a provider's calls since a time.
    *******************************************************************************/
   public long sumUsageCostSince(Integer providerId, Instant since) throws QException
   {
      return (sumUsageCostSince(providerId, since, false));
   }



   /*******************************************************************************
    ** Automated health uses the existing private accounting projection so a cap
    ** includes all users, without reading their owner-locked usage records.
    *******************************************************************************/
   public long sumUsageCostSince(Integer providerId, Instant since, Boolean sharedAccounting) throws QException
   {
      long total = 0;
      for(QRecord record : queryRecords(sharedAccounting ? AssistantBudgetUsage.TABLE_NAME : AssistantUsage.TABLE_NAME, new QQueryFilter()
         .withCriteria("providerId", QCriteriaOperator.EQUALS, providerId)
         .withCriteria(sharedAccounting ? "occurredAt" : "createDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS, since), MAX_SUM_ROWS))
      {
         Long cost = record.getValueLong("costMicros");
         total += cost == null ? 0 : cost;
      }
      return (total);
   }



   /*******************************************************************************
    ** Raw rows of one of the qbit's tables (the app home's reads), at most
    ** `limit`.
    *******************************************************************************/
   public List<QRecord> queryRecords(String tableName, QQueryFilter filter, int limit) throws QException
   {
      requireOwnTable(tableName);
      List<QRecord> records = new QueryAction().execute(new QueryInput(tableName).withFilter(filter.withLimit(limit))).getRecords();
      return (records == null ? List.of() : records.stream().map(record -> cryptRecord(tableName, record, false)).toList());
   }




   /*******************************************************************************
    ** All registry models in stable id order.
    *******************************************************************************/
   public List<AssistantModel> listModels() throws QException
   {
      return (query(AssistantModel.TABLE_NAME, new QQueryFilter().withOrderBy(new QFilterOrderBy("id", true)), AssistantModel.class));
   }



   /*******************************************************************************
    ** Save the turn level and never lower persisted conversation history.
    *******************************************************************************/
   public synchronized void saveClassification(String userId, Integer conversationId, Integer turnId, TurnClassification classification) throws QException
   {
      AssistantConversation current = getConversation(userId, conversationId);
      classification.inherit(current);
      updateFields(AssistantTurn.TABLE_NAME, new QRecord().withValue("id", turnId)
         .withValue("classification", classification.getClassification().name()).withValue("classificationTables", classification.getTables()));
      if(current != null)
      {
         var maximum = Classifications.max(
            Classifications.stored(current.getMaxClassification()), classification.getClassification());
         if(!maximum.name().equals(current.getMaxClassification()))
         {
            updateFields(AssistantConversation.TABLE_NAME, new QRecord().withValue("id", conversationId).withValue("maxClassification", maximum.name()));
         }
      }
   }



   /*******************************************************************************
    ** Compatibility store: use the current instance runtime for encryption.
    *******************************************************************************/
   public AssistantStore()
   {
      encryption = null;
   }



   /*******************************************************************************
    ** Runtime-owned store validates its configured key before serving any requests.
    *******************************************************************************/
   public AssistantStore(AssistantQBitConfig config)
   {
      encryption = new ConversationEncryption(config);
   }



   /*******************************************************************************
    ** A copy of a record with the stored-encrypted fields encrypted (HIPAA
    ** mode), for writes that do not go through this store (the table UI).
    *******************************************************************************/
   public QRecord encryptForStorage(String tableName, QRecord record)
   {
      return (cryptRecord(tableName, record, true));
   }



   /*******************************************************************************
    ** Whether a stored value is in the encrypted form (HIPAA mode).
    *******************************************************************************/
   public static boolean isEncryptedValue(String value)
   {
      return (ConversationEncryption.isEncrypted(value));
   }



   /*******************************************************************************
    ** Copy records at the storage boundary so memory-backend records and callers are never mutated.
    *******************************************************************************/
   private QRecord cryptRecord(String tableName, QRecord record, Boolean encrypt)
   {
      ConversationEncryption codec = encryption;
      if(codec == null)
      {
         AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
         codec = runtime == null ? new ConversationEncryption(new AssistantQBitConfig()) : runtime.getStore().encryption;
      }
      QRecord copy = new QRecord();
      copy.setValues(new LinkedHashMap<>(record.getValues()));
      if(encrypt && TranscriptRedactor.current() != null)
      {
         copy = TranscriptRedactor.current().prepare(copy);
      }
      for(String field : ENCRYPTED_FIELDS.getOrDefault(tableName, List.of()))
      {
         if(copy.getValue(field) instanceof String text)
         {
            copy.setValue(field, encrypt ? codec.encrypt(text) : codec.decrypt(text));
         }
      }
      return (copy);
   }



   /*******************************************************************************
    ** Stage transcript payloads until all classified values of the turn are known.
    *******************************************************************************/
   private void rememberTranscript(String table, QRecord record, Serializable id)
   {
      if(TranscriptRedactor.current() != null)
      {
         TranscriptRedactor.current().saved(table, record, id);
      }
   }



   /*******************************************************************************
    ** Retention candidates are paged independently of deletion, so retained PHI cannot starve other rows.
    *******************************************************************************/
   public List<AssistantConversation> listConversationsInactiveSince(Instant cutoff, Integer afterId, Integer limit) throws QException
   {
      return (query(AssistantConversation.TABLE_NAME, new QQueryFilter()
         .withCriteria("modifyDate", QCriteriaOperator.LESS_THAN, cutoff)
         .withCriteria("id", QCriteriaOperator.GREATER_THAN, afterId)
         .withOrderBy(new QFilterOrderBy("id", true)).withLimit(limit), AssistantConversation.class));
   }



   /*******************************************************************************
    ** Enabled data-driven budgets, read once per evaluation (never per token).
    *******************************************************************************/
   public List<AssistantBudget> budgets() throws QException
   {
      return (query(AssistantBudget.TABLE_NAME, new QQueryFilter().withCriteria("enabled", QCriteriaOperator.EQUALS, true)
         .withOrderBy(new QFilterOrderBy("id", true)), AssistantBudget.class));
   }



   /*******************************************************************************
    ** One budget, including disabled rows for override validation.
    *******************************************************************************/
   public AssistantBudget budget(Integer id) throws QException
   {
      return (first(query(AssistantBudget.TABLE_NAME, new QQueryFilter().withCriteria("id", QCriteriaOperator.EQUALS, id), AssistantBudget.class)));
   }



   /*******************************************************************************
    ** Overrides of one budget; expiry and narrower scope are evaluated together.
    *******************************************************************************/
   public List<AssistantBudgetOverride> budgetOverrides(Integer id) throws QException
   {
      return (query(AssistantBudgetOverride.TABLE_NAME, new QQueryFilter().withCriteria("budgetId", QCriteriaOperator.EQUALS, id), AssistantBudgetOverride.class));
   }



   /*******************************************************************************
    ** Persist a transition once. The unique key arbitrates concurrent replicas;
    ** an insert failure is ignored only when the winning row can be read.
    *******************************************************************************/
   public synchronized boolean budgetEvent(AssistantBudgetEvent event) throws QException
   {
      QQueryFilter filter = new QQueryFilter().withCriteria("budgetId", QCriteriaOperator.EQUALS, event.getBudgetId())
         .withCriteria("scopeKey", QCriteriaOperator.EQUALS, event.getScopeKey())
         .withCriteria("periodStart", QCriteriaOperator.EQUALS, event.getPeriodStart())
         .withCriteria("state", QCriteriaOperator.EQUALS, event.getState());
      if(!query(AssistantBudgetEvent.TABLE_NAME, filter, AssistantBudgetEvent.class).isEmpty())
      {
         return (false);
      }
      try
      {
         insert(event);
         return (true);
      }
      catch(QException e)
      {
         if(query(AssistantBudgetEvent.TABLE_NAME, filter, AssistantBudgetEvent.class).isEmpty())
         {
            throw (e);
         }
         return (false);
      }
   }



   /*******************************************************************************
    ** Accounting derives only from persisted usage. No conversation or request
    ** data is copied, and no session or security key is changed.
    *******************************************************************************/
   private void projectBudgetUsage(QRecord usage) throws QException
   {
      List<AssistantBudgetUsage> existing = query(AssistantBudgetUsage.TABLE_NAME,
         new QQueryFilter().withCriteria("usageId", QCriteriaOperator.EQUALS, usage.getValueInteger("id")), AssistantBudgetUsage.class);
      QRecord projection = new QRecord().withValue("usageId", usage.getValueInteger("id")).withValue("occurredAt", usage.getValueInstant("createDate"));
      for(String field : List.of("userId", "providerId", "model", "costMicros", "inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens"))
      {
         projection.setValue(field, usage.getValue(field));
      }
      if(existing.isEmpty())
      {
         insert(new AssistantBudgetUsage(projection));
      }
      else
      {
         updateFields(AssistantBudgetUsage.TABLE_NAME, projection.withValue("id", existing.get(0).getId()));
      }
   }



   /*******************************************************************************
    ** Internal bookkeeping flag; never accepted from a UI request body.
    *******************************************************************************/
   public enum ScheduledWrite implements ActionFlag
   {
      INTERNAL
   }



   /*******************************************************************************
    ** Update computed task fields through QQQ under the caller's session.
    *******************************************************************************/
   public void scheduledUpdate(QRecord record) throws QException
   {
      UpdateOutput output = new UpdateAction().execute(new UpdateInput(AssistantScheduledTask.TABLE_NAME)
         .withRecord(record).withFlag(ScheduledWrite.INTERNAL));
      if(CollectionUtils.nullSafeHasContents(output.getRecords().get(0).getErrors()))
      {
         throw (new QException("Could not update scheduled task bookkeeping."));
      }
   }



   /*******************************************************************************
    ** Read a task definition, never application data.
    *******************************************************************************/
   public AssistantScheduledTask scheduledTask(Integer id) throws QException
   {
      return (first(query(AssistantScheduledTask.TABLE_NAME,
         new QQueryFilter().withCriteria("id", QCriteriaOperator.EQUALS, id), AssistantScheduledTask.class)));
   }



   /*******************************************************************************
    ** Optimistic slot claim. The database unique(taskId, scheduledFor) is the
    ** cross-replica arbiter; the monitor also covers the fixture memory backend.
    ** Advance before dispatch. A crash can skip a slot, never run it twice.
    *******************************************************************************/
   public ScheduledClaim claimScheduled(
      AssistantScheduledTask candidate, Instant now, Boolean manual) throws QException
   {
      synchronized(QContext.getQInstance())
      {
         var task = scheduledTask(candidate.getId());
         if(task == null || (!manual && (!Boolean.TRUE.equals(task.getEnabled()) || task.getNextRunAt() == null
            || task.getNextRunAt().isAfter(now) || !Objects.equals(task.getNextRunAt(), candidate.getNextRunAt()))))
         {
            return (null);
         }
         Instant slot = manual ? now : task.getNextRunAt();
         Instant next = Boolean.TRUE.equals(task.getEnabled()) ? ScheduledTasks.next(task, now) : null;
         var filter = new QQueryFilter().withCriteria("taskId", QCriteriaOperator.EQUALS, task.getId()).withCriteria("scheduledFor", QCriteriaOperator.EQUALS, slot);
         if(!query(AssistantScheduledRun.TABLE_NAME, filter, AssistantScheduledRun.class).isEmpty())
         {
            if(!manual)
            {
               scheduledUpdate(new QRecord().withValue("id", task.getId()).withValue("nextRunAt", next));
            }
            return (null);
         }
         AssistantScheduledRun run;
         try
         {
            run = insert(new AssistantScheduledRun().withTaskId(task.getId()).withUserId(task.getRunAsUserId())
               .withScheduledFor(slot).withStartedAt(now).withStatus("RUNNING").withCostMicros(0L));
         }
         catch(QException e)
         {
            if(!query(AssistantScheduledRun.TABLE_NAME, filter, AssistantScheduledRun.class).isEmpty())
            {
               return (null);
            }
            throw (e);
         }
         scheduledUpdate(new QRecord().withValue("id", task.getId()).withValue("nextRunAt", next));
         return (new ScheduledClaim(task, run));
      }
   }



   /*******************************************************************************
    ** The claimed task snapshot and its run owner must remain consistent.
    *******************************************************************************/
   public record ScheduledClaim(AssistantScheduledTask task, AssistantScheduledRun run)
   {
   }

}
