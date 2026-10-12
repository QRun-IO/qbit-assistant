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

package com.kingsrook.qbits.assistant.api;


import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.engine.ContextBuilder;
import com.kingsrook.qbits.assistant.engine.MessageParts;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.engine.RunningTurn;
import com.kingsrook.qbits.assistant.engine.TurnEngine;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantFeedback;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The assistant HTTP API (contract section 3), independent of the web
 ** framework. Every method runs with the requesting user's session already
 ** in QContext (ExecutorSessionUtils), and only touches that user's rows.
 *******************************************************************************/
public class AssistantApi
{
   public static final int TITLE_MAX_CHARS = 60;

   private final AssistantRuntime    runtime;
   private final AssistantQBitConfig config;
   private final AssistantStore      store;
   private final QSession            session;
   private final String              userId;



   /*******************************************************************************
    ** Binds the API to a request's session and gives the session its own
    ** owner key (so the record locks admit the user's own rows).
    *******************************************************************************/
   public AssistantApi(AssistantRuntime runtime, QSession session)
   {
      this.runtime = runtime;
      this.config = runtime.getConfig();
      this.store = runtime.getStore();
      this.session = session;
      this.userId = AssistantSecurity.userId(session);
      if(session != null)
      {
         AssistantSecurity.ownerKey(session, config);
      }
   }



   /*******************************************************************************
    ** GET /config - the feature probe. Never throws for a signed-in user.
    *******************************************************************************/
   public Map<String, Object> config() throws QException
   {
      Map<String, Object> result = new LinkedHashMap<>();
      String              reason = null;

      if(!config.isEffectivelyEnabled())
      {
         reason = "DISABLED";
      }
      else if(!hasAccess())
      {
         reason = "NO_PERMISSION";
      }
      else if(config.getActiveModelProvider() == null)
      {
         reason = "NOT_CONFIGURED";
      }
      else if(runtime.getPolicyService().current(false).killSwitch())
      {
         reason = "KILL_SWITCH";
      }

      result.put("enabled", reason == null);
      if(reason != null)
      {
         result.put("reason", reason);
      }
      result.put("shortcut", config.getShortcut());
      result.put("model", reason == null ? runtime.getPolicyService().current(false).mainModel() : config.getModels().getMainModel());
      result.put("limits", Map.of("maxTurnSeconds", config.getMaxTurnSeconds(), "maxToolCallsPerTurn", config.getMaxToolCallsPerTurn()));
      result.put("features", Map.of("writes", false));
      return (result);
   }



   /*******************************************************************************
    ** GET /conversations
    *******************************************************************************/
   public Map<String, Object> listConversations(String status) throws QException, AssistantApiException
   {
      requireAccess();
      String effective = "ARCHIVED".equalsIgnoreCase(status) ? "ARCHIVED" : "ACTIVE";
      List<Map<String, Object>> list = new ArrayList<>();
      for(AssistantConversation conversation : store.listConversations(userId, effective))
      {
         list.add(toJson(conversation));
      }
      return (Map.of("conversations", list));
   }



   /*******************************************************************************
    ** POST /conversations
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public Map<String, Object> createConversation(Map<String, Object> body) throws QException, AssistantApiException
   {
      requireAccess();
      String title = body.get("title") instanceof String s && StringUtils.hasContent(s) ? truncate(s.trim(), 255) : null;
      Map<String, Object> context = body.get("context") instanceof Map<?, ?> m ? ContextBuilder.sanitizePageContext((Map<String, Object>) m) : null;
      AssistantConversation conversation = store.createConversation(userId, title, context == null || context.isEmpty() ? null : JsonUtils.toJson(context));
      return (toJson(conversation));
   }



   /*******************************************************************************
    ** GET /conversations/{id} - the conversation and a page of messages;
    ** marks it read.
    *******************************************************************************/
   public Map<String, Object> getConversation(Integer id, Integer beforeMessageId, Integer limit) throws QException, AssistantApiException
   {
      requireAccess();
      AssistantConversation conversation = requireConversation(id);
      int pageSize = limit == null ? 50 : Math.max(1, Math.min(200, limit));

      List<Map<String, Object>> messages = new ArrayList<>();
      for(AssistantMessage message : store.listMessages(userId, id, beforeMessageId, pageSize))
      {
         messages.add(toJson(message));
      }

      if(conversation.getUnreadCount() != null && conversation.getUnreadCount() > 0)
      {
         store.updateFields(AssistantConversation.TABLE_NAME, new QRecord().withValue("id", id).withValue("unreadCount", 0));
         conversation.setUnreadCount(0);
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("conversation", toJson(conversation));
      result.put("messages", messages);
      return (result);
   }



   /*******************************************************************************
    ** PATCH /conversations/{id}
    *******************************************************************************/
   public Map<String, Object> patchConversation(Integer id, Map<String, Object> body) throws QException, AssistantApiException
   {
      requireAccess();
      AssistantConversation conversation = requireConversation(id);
      QRecord               update       = new QRecord().withValue("id", id);

      if(body.containsKey("title"))
      {
         Object title = body.get("title");
         if(title != null && !(title instanceof String))
         {
            throw (new AssistantApiException(400, "title must be a string."));
         }
         String value = title == null || !StringUtils.hasContent((String) title) ? null : truncate(((String) title).trim(), 255);
         update.setValue("title", value);
         conversation.setTitle(value);
      }
      if(body.containsKey("pinned"))
      {
         if(!(body.get("pinned") instanceof Boolean pinned))
         {
            throw (new AssistantApiException(400, "pinned must be true or false."));
         }
         update.setValue("pinned", pinned);
         conversation.setPinned(pinned);
      }
      if(body.containsKey("status"))
      {
         Object status = body.get("status");
         if(!"ACTIVE".equals(status) && !"ARCHIVED".equals(status))
         {
            throw (new AssistantApiException(400, "status must be ACTIVE or ARCHIVED."));
         }
         update.setValue("status", status);
         conversation.setStatus((String) status);
      }

      store.updateFields(AssistantConversation.TABLE_NAME, update);
      return (toJson(conversation));
   }



   /*******************************************************************************
    ** DELETE /conversations/{id}
    *******************************************************************************/
   public void deleteConversation(Integer id) throws QException, AssistantApiException
   {
      requireAccess();
      requireConversation(id);
      for(AssistantTurn turn : store.listRunningTurns(userId))
      {
         if(id.equals(turn.getConversationId()) && isRunning(reapIfOrphaned(turn)))
         {
            throw (new AssistantApiException(409, "This conversation has an answer in progress. Stop it first."));
         }
      }
      store.deleteConversation(userId, id);
   }



   /*******************************************************************************
    ** POST /conversations/{id}/turns - checks, persists the user message, the
    ** (streaming) assistant message and the turn, then starts the engine.
    ** Returns the running turn, whose event log the caller streams.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public RunningTurn startTurn(Integer conversationId, Map<String, Object> body) throws QException, AssistantApiException
   {
      requireAccess();
      AssistantConversation conversation = requireConversation(conversationId);

      if(!(body.get("text") instanceof String text) || !StringUtils.hasContent(text))
      {
         throw (new AssistantApiException(400, "text is required."));
      }
      if(text.length() > config.getMaxUserMessageChars())
      {
         throw (new AssistantApiException(400, "The message is too long (at most " + config.getMaxUserMessageChars() + " characters)."));
      }
      Map<String, Object> pageContext = body.get("pageContext") instanceof Map<?, ?> m ? ContextBuilder.sanitizePageContext((Map<String, Object>) m) : Map.of();

      if(config.getActiveModelProvider() == null)
      {
         throw (new AssistantApiException(503, "NOT_CONFIGURED", "The assistant is not configured on this server."));
      }

      PolicyService.EffectivePolicy policy = runtime.getPolicyService().current(true);
      if(policy.killSwitch())
      {
         throw (new AssistantApiException(503, "KILL_SWITCH", "The assistant has been turned off by an administrator."));
      }

      PolicyService.BudgetStatus budget = runtime.getPolicyService().checkBudget(policy, userId, 0, 0);
      if(budget.hardExceeded())
      {
         throw (new AssistantApiException(429, "BUDGET", budget.message()));
      }

      long running = 0;
      for(AssistantTurn other : store.listRunningTurns(userId))
      {
         if(isRunning(reapIfOrphaned(other)))
         {
            running++;
         }
      }
      if(running >= config.getMaxConcurrentTurnsPerUser())
      {
         throw (new AssistantApiException(429, "RATE_LIMIT", "You already have an assistant answer in progress. Wait for it to finish or stop it."));
      }

      String limited = runtime.getRateLimiter().tryAcquire(userId, config.getMaxTurnsPerUserPerMinute(), config.getMaxConcurrentTurnsPerUser());
      if(limited != null)
      {
         throw (new AssistantApiException(429, "RATE_LIMIT", limited));
      }

      try
      {
         if(!StringUtils.hasContent(conversation.getTitle()))
         {
            String title = deriveTitle(text);
            store.updateFields(AssistantConversation.TABLE_NAME, new QRecord().withValue("id", conversationId).withValue("title", title));
            conversation.setTitle(title);
         }

         int seq = store.nextMessageSeq(userId, conversationId);
         AssistantMessage userMessage = store.insert(new AssistantMessage()
            .withConversationId(conversationId)
            .withUserId(userId)
            .withRole("USER")
            .withStatus("COMPLETE")
            .withSeq(seq)
            .withPartsJson(new MessageParts().addText(text).toJson()));

         AssistantTurn turn = store.insert(new AssistantTurn()
            .withConversationId(conversationId)
            .withUserId(userId)
            .withUserMessageId(userMessage.getId())
            .withStatus("RUNNING")
            .withModel(policy.mainModel())
            .withStartedAt(runtime.getClock().instant())
            .withToolCallCount(0)
            .withModelCallCount(0));

         AssistantMessage assistantMessage = store.insert(new AssistantMessage()
            .withConversationId(conversationId)
            .withUserId(userId)
            .withRole("ASSISTANT")
            .withStatus("STREAMING")
            .withTurnId(turn.getId())
            .withSeq(seq + 1)
            .withModel(policy.mainModel())
            .withPartsJson("[]"));

         store.updateFields(AssistantMessage.TABLE_NAME, new QRecord().withValue("id", userMessage.getId()).withValue("turnId", turn.getId()));
         store.updateFields(AssistantTurn.TABLE_NAME, new QRecord().withValue("id", turn.getId()).withValue("assistantMessageId", assistantMessage.getId()));
         turn.setAssistantMessageId(assistantMessage.getId());

         RunningTurn runningTurn = runtime.register(new RunningTurn(turn.getId(), userId));
         TurnEngine  engine      = new TurnEngine(runtime, session, userId, conversation, userMessage, assistantMessage, turn, text, pageContext, policy, runningTurn,
            budget.softExceeded() ? budget.message() : null);
         runtime.getExecutor().submit(engine);
         return (runningTurn);
      }
      catch(QException | RuntimeException e)
      {
         runtime.getRateLimiter().release(userId);
         throw (e);
      }
   }



   /*******************************************************************************
    ** Where to read a turn's events from: the live log (this replica) or, for
    ** finished / remote turns, the persisted events.
    *******************************************************************************/
   public record TurnEvents(RunningTurn live, List<TurnEvent> persisted, boolean finished, boolean more)
   {
   }



   /*******************************************************************************
    ** GET /turns/{turnId}/events?after=
    *******************************************************************************/
   public TurnEvents turnEvents(Integer turnId, int after) throws QException, AssistantApiException
   {
      requireAccess();
      AssistantTurn turn = requireTurn(turnId);

      RunningTurn live = runtime.getRunningTurn(turnId);
      if(live != null && userId.equals(live.getUserId()))
      {
         return (new TurnEvents(live, null, false, false));
      }
      turn = reapIfOrphaned(turn);

      List<TurnEvent> events = new ArrayList<>();
      List<AssistantTurnEvent> page = store.listEvents(userId, turnId, after, AssistantStore.EVENT_PAGE_SIZE);
      for(AssistantTurnEvent event : page)
      {
         events.add(TurnEvent.fromJson(event.getSeq(), event.getEventType(), event.getDataJson()));
      }
      return (new TurnEvents(null, events, !isRunning(turn), page.size() >= AssistantStore.EVENT_PAGE_SIZE));
   }



   /*******************************************************************************
    ** POST /turns/{turnId}/cancel
    *******************************************************************************/
   public Map<String, Object> cancelTurn(Integer turnId) throws QException, AssistantApiException
   {
      requireAccess();
      AssistantTurn turn = requireTurn(turnId);
      if(runtime.getRunningTurn(turnId) == null)
      {
         turn = reapIfOrphaned(turn);
      }
      if(!isRunning(turn))
      {
         throw (new AssistantApiException(409, "This answer has already finished."));
      }

      store.updateFields(AssistantTurn.TABLE_NAME, new QRecord().withValue("id", turnId).withValue("status", "CANCELLING"));
      RunningTurn live = runtime.getRunningTurn(turnId);
      if(live != null && userId.equals(live.getUserId()))
      {
         live.cancel("USER");
      }
      return (Map.of("status", "CANCELLING"));
   }



   /*******************************************************************************
    ** Whether a turn row still says it runs.
    *******************************************************************************/
   static boolean isRunning(AssistantTurn turn)
   {
      return (turn != null && ("RUNNING".equals(turn.getStatus()) || "CANCELLING".equals(turn.getStatus())));
   }



   /*******************************************************************************
    ** A turn whose replica died: it still says RUNNING / CANCELLING, no turn
    ** of this runtime owns it, and it started longer ago than maxTurnSeconds
    ** + 60 s (a live engine always ends itself by then). On first sight it is
    ** finished as ERROR: the assistant message gets status ERROR and a
    ** notice, and synthetic error (INTERNAL) + message.end events are
    ** persisted, so replays end. Returns the (possibly updated) turn.
    *******************************************************************************/
   AssistantTurn reapIfOrphaned(AssistantTurn turn) throws QException
   {
      if(!isRunning(turn) || runtime.getRunningTurn(turn.getId()) != null || turn.getStartedAt() == null)
      {
         return (turn);
      }
      Instant stale = runtime.getClock().instant().minusSeconds(config.getMaxTurnSeconds() + 60L);
      if(!turn.getStartedAt().isBefore(stale))
      {
         return (turn);
      }

      String text = "This answer was interrupted (the server restarted). Ask again.";
      if(turn.getAssistantMessageId() != null)
      {
         AssistantMessage message = store.getMessage(userId, turn.getAssistantMessageId());
         if(message != null)
         {
            store.updateFields(AssistantMessage.TABLE_NAME, new QRecord()
               .withValue("id", message.getId())
               .withValue("status", "ERROR")
               .withValue("partsJson", MessageParts.fromJson(message.getPartsJson()).addNotice("error", text).toJson()));
         }
      }

      int seq = store.lastEventSeq(userId, turn.getId());
      store.insertEvent(turn.getId(), userId, seq + 1, TurnEvent.ERROR, new TurnEvent(seq + 1, TurnEvent.ERROR, Map.of("code", "INTERNAL", "message", text)).toJson());
      Map<String, Object> end = new java.util.LinkedHashMap<>();
      if(turn.getAssistantMessageId() != null)
      {
         end.put("messageId", turn.getAssistantMessageId());
      }
      end.put("status", "ERROR");
      end.put("stopReason", "interrupted");
      store.insertEvent(turn.getId(), userId, seq + 2, TurnEvent.MESSAGE_END, new TurnEvent(seq + 2, TurnEvent.MESSAGE_END, end).toJson());

      Instant now = runtime.getClock().instant();
      store.updateFields(AssistantTurn.TABLE_NAME, new QRecord()
         .withValue("id", turn.getId())
         .withValue("status", "ERROR")
         .withValue("errorCode", "INTERNAL")
         .withValue("stopReason", "interrupted")
         .withValue("endedAt", now));
      turn.setStatus("ERROR");
      turn.setErrorCode("INTERNAL");
      turn.setEndedAt(now);
      return (turn);
   }



   /*******************************************************************************
    ** POST /messages/{id}/feedback
    *******************************************************************************/
   public void feedback(Integer messageId, Map<String, Object> body) throws QException, AssistantApiException
   {
      requireAccess();
      AssistantMessage message = store.getMessage(userId, messageId);
      if(message == null)
      {
         throw (new AssistantApiException(404, "Message not found."));
      }
      if(!"ASSISTANT".equals(message.getRole()))
      {
         throw (new AssistantApiException(400, "Feedback is only for assistant messages."));
      }
      Object rating = body.get("rating");
      if(!(rating instanceof Number number) || (number.intValue() != 1 && number.intValue() != -1) || number.doubleValue() != number.intValue())
      {
         throw (new AssistantApiException(400, "rating must be 1 or -1."));
      }
      String reason  = body.get("reason") instanceof String s ? truncate(s, 64) : null;
      String comment = body.get("comment") instanceof String s ? truncate(s, 4000) : null;

      store.insert(new AssistantFeedback()
         .withMessageId(messageId)
         .withConversationId(message.getConversationId())
         .withUserId(userId)
         .withRating(number.intValue())
         .withReason(reason)
         .withComment(comment));
   }



   /*******************************************************************************
    ** The configured turn time limit (replay loops are capped by it).
    *******************************************************************************/
   public int maxTurnSeconds()
   {
      return (config.getMaxTurnSeconds());
   }



   /*******************************************************************************
    ** The user may use the panel: enabled, signed in, assistant.hasAccess.
    *******************************************************************************/
   public boolean hasAccess()
   {
      return (userId != null && session.hasPermission(AssistantQBitConfig.ACCESS_PERMISSION));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void requireAccess() throws AssistantApiException
   {
      if(!config.isEffectivelyEnabled())
      {
         throw (new AssistantApiException(503, "DISABLED", "The assistant is turned off for this application."));
      }
      if(!hasAccess())
      {
         throw (new AssistantApiException(403, "NO_PERMISSION", "You do not have access to the assistant."));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private AssistantConversation requireConversation(Integer id) throws QException, AssistantApiException
   {
      AssistantConversation conversation = id == null ? null : store.getConversation(userId, id);
      if(conversation == null)
      {
         throw (new AssistantApiException(404, "Conversation not found."));
      }
      return (conversation);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private AssistantTurn requireTurn(Integer id) throws QException, AssistantApiException
   {
      AssistantTurn turn = id == null ? null : store.getTurn(userId, id);
      if(turn == null)
      {
         throw (new AssistantApiException(404, "Turn not found."));
      }
      return (turn);
   }



   /*******************************************************************************
    ** A title from the first user message (phase 1; Haiku titles in phase 2).
    *******************************************************************************/
   static String deriveTitle(String text)
   {
      String line = text.strip().split("\\R", 2)[0].replaceAll("\\s+", " ").strip();
      if(line.length() <= TITLE_MAX_CHARS)
      {
         return (line);
      }
      String cut = line.substring(0, TITLE_MAX_CHARS - 1);
      int    space = cut.lastIndexOf(' ');
      if(space > TITLE_MAX_CHARS / 2)
      {
         cut = cut.substring(0, space);
      }
      return (cut + "…");
   }



   /*******************************************************************************
    ** Conversation JSON (contract type Conversation).
    *******************************************************************************/
   static Map<String, Object> toJson(AssistantConversation conversation)
   {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", conversation.getId());
      map.put("title", conversation.getTitle());
      map.put("status", conversation.getStatus());
      map.put("pinned", Boolean.TRUE.equals(conversation.getPinned()));
      map.put("lastMessageAt", conversation.getLastMessageAt() == null ? null : conversation.getLastMessageAt().toString());
      map.put("unreadCount", conversation.getUnreadCount() == null ? 0 : conversation.getUnreadCount());
      map.put("createDate", conversation.getCreateDate() == null ? null : conversation.getCreateDate().toString());
      return (map);
   }



   /*******************************************************************************
    ** Message JSON (contract type Message).
    *******************************************************************************/
   static Map<String, Object> toJson(AssistantMessage message)
   {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", message.getId());
      map.put("role", message.getRole());
      map.put("status", message.getStatus());
      map.put("turnId", message.getTurnId());
      map.put("createDate", message.getCreateDate() == null ? null : message.getCreateDate().toString());
      map.put("parts", MessageParts.fromJson(message.getPartsJson()).list());
      return (map);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String value, int max)
   {
      return (value == null || value.length() <= max ? value : value.substring(0, max));
   }

}
