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


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.access.AccessExplainer;
import com.kingsrook.qbits.assistant.access.DataAccessMode;
import com.kingsrook.qbits.assistant.engine.AbandonedTurns;
import com.kingsrook.qbits.assistant.engine.ClientErrors;
import com.kingsrook.qbits.assistant.engine.ContextBuilder;
import com.kingsrook.qbits.assistant.engine.MessageParts;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.engine.ProfileService;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.engine.RunningTurn;
import com.kingsrook.qbits.assistant.engine.TurnEngine;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantClientError;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantFeedback;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.persistence.TranscriptRedactor;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qbits.assistant.tools.bulkload.BulkLoadTools;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolOutcome;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolResult;
import com.kingsrook.qqq.backend.core.actions.ai.output.AiRedactor;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The assistant HTTP API (contract section 3), independent of the web
 ** framework. Every method runs with the requesting user's session already
 ** in QContext (ExecutorSessionUtils), and only touches that user's rows.
 *******************************************************************************/
public class AssistantApi
{
   private static final QLogger LOG = QLogger.getLogger(AssistantApi.class);

   public static final int TITLE_MAX_CHARS = 60;

   private final AssistantRuntime    runtime;
   private final AssistantQBitConfig config;
   private final AssistantStore      store;
   private final QSession            session;
   private final String              userId;
   private UiApiCredentials          credentials;



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
    ** The credentials of the request this API serves, exactly as they came in;
    ** the tools forward them to the UI API. Never logged.
    *******************************************************************************/
   public AssistantApi withCredentials(UiApiCredentials credentials)
   {
      this.credentials = credentials;
      return (this);
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
      else if(activeProvider() == null)
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
      result.put("model", reason == null ? activeProvider().model() : config.getModels().getMainModel());
      result.put("limits", Map.of("maxTurnSeconds", config.getMaxTurnSeconds(), "maxToolCallsPerTurn", config.getMaxToolCallsPerTurn()));
      result.put("features", Map.of("writes", runtime.getToolbox().anyWritableTable(), "pendingActions", true));
      ProfileService.Persona persona = runtime.getProfileService().persona(session, null);
      Map<String, Object>    profile = new LinkedHashMap<>();
      profile.put("displayName", persona.displayName());
      profile.put("icon", persona.icon());
      profile.put("greeting", persona.greeting());
      profile.put("starterPrompts", persona.starterPrompts());
      result.put("profile", profile);
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
         messages.add(toJson(repairIfAbandoned(message)));
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
    ** A message still STREAMING whose run is not live here: an orphaned run is
    ** recovered; a run that already ended (or is gone) gives the message its
    ** terminal status. A run still alive on another replica is left alone.
    ** Never throws: the conversation is shown either way.
    *******************************************************************************/
   private AssistantMessage repairIfAbandoned(AssistantMessage message)
   {
      if(!"STREAMING".equals(message.getStatus()) || (message.getTurnId() != null && runtime.getRunningTurn(message.getTurnId()) != null))
      {
         return (message);
      }
      try
      {
         AssistantTurn turn = message.getTurnId() == null ? null : store.getTurn(userId, message.getTurnId());
         if(isRunning(turn))
         {
            turn = reapIfOrphaned(turn);
            if(isRunning(turn))
            {
               return (message);
            }
            AssistantMessage reread = store.getMessage(userId, message.getId());
            if(reread != null && !"STREAMING".equals(reread.getStatus()))
            {
               return (reread);
            }
         }
         return (AbandonedTurns.repairMessage(store, userId, message, turn));
      }
      catch(Exception e)
      {
         LOG.warn("Could not check an unfinished assistant message", logPair("messageId", message.getId()));
         return (message);
      }
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
      if(runtime.isShuttingDown())
      {
         throw (new AssistantApiException(503, "SHUTTING_DOWN", "The server is restarting. Try again in a moment."));
      }
      String concurrency = config.getDefaultConcurrencyMode();
      if(body.containsKey("concurrency"))
      {
         if(!(body.get("concurrency") instanceof String mode) || !("supersede".equalsIgnoreCase(mode) || "concurrent".equalsIgnoreCase(mode)))
         {
            throw (new AssistantApiException(400, "concurrency must be supersede or concurrent."));
         }
         concurrency = mode.toUpperCase(Locale.ROOT);
      }
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

      PolicyService.EffectivePolicy policy = runtime.getPolicyService().current(true);
      ProviderService.Active        active = runtime.getProviderService().resolve(policy.mainModel());
      if(active == null)
      {
         throw (new AssistantApiException(503, "NOT_CONFIGURED", "The assistant is not configured on this server."));
      }
      if(policy.killSwitch())
      {
         throw (new AssistantApiException(503, "KILL_SWITCH", "The assistant has been turned off by an administrator."));
      }

      Long capMicros = ProviderService.capMicros(active.row());
      if(capMicros != null && runtime.getProviderService().monthToDateCostMicros(active.providerId()) >= capMicros)
      {
         throw (new AssistantApiException(429, "BUDGET", "The assistant has reached this provider's monthly cost cap. Ask an administrator."));
      }

      PolicyService.BudgetStatus budget = runtime.getPolicyService().checkBudget(policy, userId, 0, 0);
      if(budget.hardExceeded())
      {
         throw (new AssistantApiException(429, "BUDGET", budget.message()));
      }

      String limited = runtime.getRateLimiter().tryAcquire(userId, config.getMaxTurnsPerUserPerMinute(), config.getMaxConcurrentTurnsPerUser());
      if(limited != null)
      {
         throw (new AssistantApiException(429, "RATE_LIMIT", limited));
      }

      List<AssistantTurn> remaining = new ArrayList<>();
      for(AssistantTurn other : store.listRunningTurns(userId))
      {
         other = reapIfOrphaned(other);
         if(!isRunning(other) || "SUPERSEDED".equals(other.getCancelReason()))
         {
            continue;
         }
         if("SUPERSEDE".equals(concurrency) && conversationId.equals(other.getConversationId()))
         {
            requestStop(other, "SUPERSEDED");
         }
         else
         {
            remaining.add(other);
         }
      }
      remaining.sort(Comparator.comparing(AssistantTurn::getStartedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
         .thenComparing(AssistantTurn::getId));
      while(remaining.size() >= config.getMaxConcurrentTurnsPerUser())
      {
         requestStop(remaining.removeFirst(), "SUPERSEDED");
      }

      TranscriptRedactor transcript = new TranscriptRedactor(runtime.getQInstance(), config.getClassificationSource());
      transcript.attach();
      try
      {
         if(!StringUtils.hasContent(conversation.getTitle()))
         {
            String title = text;
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

         Instant startedAt = runtime.getClock().instant();
         AssistantTurn turn = store.insert(new AssistantTurn()
            .withConversationId(conversationId)
            .withUserId(userId)
            .withUserMessageId(userMessage.getId())
            .withStatus("RUNNING")
            .withModel(active.model())
            .withStartedAt(startedAt)
            .withHeartbeatAt(startedAt)
            .withLeaseOwner(runtime.getInstanceId())
            .withPhase("starting")
            .withConcurrencyMode(concurrency)
            .withToolCallCount(0)
            .withModelCallCount(0));

         AssistantMessage assistantMessage = store.insert(new AssistantMessage()
            .withConversationId(conversationId)
            .withUserId(userId)
            .withRole("ASSISTANT")
            .withStatus("STREAMING")
            .withTurnId(turn.getId())
            .withSeq(seq + 1)
            .withModel(active.model())
            .withPartsJson("[]"));

         store.updateFields(AssistantMessage.TABLE_NAME, new QRecord().withValue("id", userMessage.getId()).withValue("turnId", turn.getId()));
         store.updateFields(AssistantTurn.TABLE_NAME, new QRecord().withValue("id", turn.getId()).withValue("assistantMessageId", assistantMessage.getId()));
         turn.setAssistantMessageId(assistantMessage.getId());

         RunningTurn runningTurn = new RunningTurn(turn.getId(), userId);
         TurnEngine  engine      = new TurnEngine(runtime, session, userId, conversation, userMessage, assistantMessage, turn, text, pageContext, policy, runningTurn,
            budget.softExceeded() ? budget.message() : null).withCredentials(credentials).withTranscript(transcript);
         try
         {
            if(config.getRunTracker() != null)
            {
               ///////////////////////////////////////////////////////////////////
               // never the conversation title: it is derived from the prompt  //
               ///////////////////////////////////////////////////////////////////
               runningTurn.setTrackingId(config.getRunTracker().start(userId, turn.getId(), conversationId, "Assistant answer"));
               config.getRunTracker().onCancel(runningTurn.getTrackingId(), () -> runningTurn.cancel("USER"));
            }
         }
         catch(Exception e)
         {
            LOG.warn("Assistant run tracker start failed", logPair("turnId", turn.getId()));
         }
         runtime.register(runningTurn);
         if(!runningTurn.isFinalized())
         {
            try
            {
               runtime.getExecutor().submit(engine);
            }
            catch(RuntimeException e)
            {
               runningTurn.forceFinalize("SHUTDOWN");
               throw (e);
            }
         }
         return (runningTurn);
      }
      catch(QException | RuntimeException e)
      {
         transcript.close();
         runtime.getRateLimiter().release(userId);
         throw (e);
      }
      finally
      {
         TranscriptRedactor.detach();
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
      if(isRunning(turn))
      {
         turn = requestStop(turn, "USER");
      }
      return (Map.of("status", turn.getStatus()));
   }



   /*******************************************************************************
    ** Durable Stop, shared by the API and superseding another question.
    *******************************************************************************/
   private AssistantTurn requestStop(AssistantTurn turn, String reason) throws QException
   {
      synchronized(store)
      {
         turn = store.getTurn(userId, turn.getId());
         if(!isRunning(turn))
         {
            return (turn);
         }
         RunningTurn live = runtime.getRunningTurn(turn.getId());
         if(live == null && orphaned(turn))
         {
            return (reap(turn, reason));
         }
         Instant now = runtime.getClock().instant();
         if(!store.updateRunningTurn(userId, new QRecord().withValue("id", turn.getId())
            .withValue("status", "CANCELLING").withValue("cancelReason", reason).withValue("cancelRequestedAt", now)))
         {
            return (store.getTurn(userId, turn.getId()));
         }
         turn.setStatus("CANCELLING");
         turn.setCancelReason(reason);
         turn.setCancelRequestedAt(now);
         if(live != null)
         {
            try
            {
               live.cancel(reason);
            }
            catch(Exception e)
            {
               LOG.warn("Assistant stream cancellation failed; watchdog will finalize");
            }
         }
         return (turn);
      }
   }



   /*******************************************************************************
    ** GET /turns/active: the caller's runs, newest first, after orphan recovery.
    *******************************************************************************/
   public Map<String, Object> activeTurns() throws QException, AssistantApiException
   {
      requireAccess();
      List<AssistantTurn> turns = new ArrayList<>();
      for(AssistantTurn turn : store.listRunningTurns(userId))
      {
         turn = reapIfOrphaned(turn);
         if(isRunning(turn))
         {
            turns.add(turn);
         }
      }
      turns.sort(Comparator.comparing(AssistantTurn::getStartedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
         .thenComparing(AssistantTurn::getId).reversed());
      List<Map<String, Object>> rows = new ArrayList<>();
      for(AssistantTurn turn : turns)
      {
         RunningTurn live = runtime.getRunningTurn(turn.getId());
         Map<String, Object> row = new LinkedHashMap<>();
         row.put("turnId", turn.getId());
         row.put("conversationId", turn.getConversationId());
         row.put("assistantMessageId", turn.getAssistantMessageId());
         row.put("userMessageId", turn.getUserMessageId());
         row.put("status", turn.getStatus());
         row.put("phase", live == null ? turn.getPhase() : live.getPhase());
         row.put("startedAt", turn.getStartedAt() == null ? null : turn.getStartedAt().toString());
         row.put("heartbeatAt", turn.getHeartbeatAt() == null ? null : turn.getHeartbeatAt().toString());
         row.put("elapsedMs", turn.getStartedAt() == null ? 0 : Math.max(0, Duration.between(turn.getStartedAt(), runtime.getClock().instant()).toMillis()));
         row.put("live", live != null);
         row.put("concurrencyMode", turn.getConcurrencyMode());
         rows.add(row);
      }
      return (Map.of("turns", rows));
   }



   /*******************************************************************************
    ** Whether a turn row still says it runs.
    *******************************************************************************/
   static boolean isRunning(AssistantTurn turn)
   {
      return (turn != null && ("RUNNING".equals(turn.getStatus()) || "CANCELLING".equals(turn.getStatus())));
   }



   /*******************************************************************************
    ** A remote run loses its lease, exceeds the absolute deadline, or has a
    ** cancellation request that its owner did not acknowledge in time.
    *******************************************************************************/
   private boolean orphaned(AssistantTurn turn)
   {
      Instant now = runtime.getClock().instant();
      Instant heartbeat = turn.getHeartbeatAt() == null ? turn.getStartedAt() : turn.getHeartbeatAt();
      return ((heartbeat != null && heartbeat.isBefore(now.minusSeconds(config.getTurnLeaseSeconds())))
         || (turn.getStartedAt() != null && turn.getStartedAt().isBefore(now.minusSeconds(maxStreamSeconds())))
         || ("CANCELLING".equals(turn.getStatus()) && turn.getCancelRequestedAt() != null
         && turn.getCancelRequestedAt().isBefore(now.minusSeconds(config.getTurnLeaseSeconds()))));
   }



   /*******************************************************************************
    ** Recover an abandoned run and leave a terminal replay for refreshes.
    *******************************************************************************/
   AssistantTurn reapIfOrphaned(AssistantTurn turn) throws QException
   {
      synchronized(store)
      {
         if(!isRunning(turn) || runtime.getRunningTurn(turn.getId()) != null || !orphaned(turn))
         {
            return (turn);
         }
         String reason = "CANCELLING".equals(turn.getStatus()) && turn.getCancelRequestedAt() != null
            && turn.getCancelRequestedAt().isBefore(runtime.getClock().instant().minusSeconds(config.getTurnLeaseSeconds()))
            ? (turn.getCancelReason() == null ? "USER" : turn.getCancelReason()) : null;
         return (reap(turn, reason));
      }
   }



   /*******************************************************************************
    ** Re-read before recovery; never overwrite an already terminal row or
    ** append another terminal event. Each persistence step is independent.
    *******************************************************************************/
   private AssistantTurn reap(AssistantTurn turn, String cancelReason) throws QException
   {
      AssistantTurn current = store.getTurn(userId, turn.getId());
      if(!isRunning(current))
      {
         return (current);
      }
      if(!orphaned(current))
      {
         return (cancelReason == null ? current : requestStop(current, cancelReason));
      }
      // Recovery only appends fixed notices to already redacted stored parts (or
      // crash placeholders). It never reloads model/tool values or raw user text.
      AbandonedTurns.finalizeRun(store, runtime.getClock().instant(), userId, current, cancelReason);
      return (store.getTurn(userId, current.getId()));
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
    ** POST /client-errors: an error the panel hit, reported by the user's own
    ** browser. Any signed-in user may report (also when the assistant is off
    ** for them); only bounded, scrubbed fields are stored (ClientErrors), and
    ** conversation / message / turn ids only when they are the user's own.
    ** Over the per-user rate limit the report is acknowledged but dropped
    ** (accepted: false), never a 429, so a looping client is not spammed.
    *******************************************************************************/
   public Map<String, Object> reportClientError(Map<String, Object> body) throws QException, AssistantApiException
   {
      if(userId == null)
      {
         throw (new AssistantApiException(403, "NO_PERMISSION", "Sign in to report errors."));
      }
      if(body == null)
      {
         throw (new AssistantApiException(400, "A JSON object is required."));
      }
      String scope = ClientErrors.token(body.get("scope"), ClientErrors.SCOPE_MAX);
      if(scope == null)
      {
         throw (new AssistantApiException(400, "scope is required."));
      }
      String message = ClientErrors.text(body.get("message"), ClientErrors.MESSAGE_MAX);
      if(message == null)
      {
         throw (new AssistantApiException(400, "message is required."));
      }
      if(!runtime.getClientErrors().tryAcquire(userId, config.getClientErrorsPerUserPerMinute()))
      {
         return (Map.of("accepted", false));
      }

      Integer conversationId = ClientErrors.id(body.get("conversationId"));
      Integer messageId      = ClientErrors.id(body.get("messageId"));
      Integer turnId         = ClientErrors.id(body.get("turnId"));
      AssistantClientError row = new AssistantClientError()
         .withUserId(userId)
         .withScope(scope)
         .withMessage(message)
         .withErrorName(ClientErrors.token(body.get("errorName"), ClientErrors.ERROR_NAME_MAX))
         .withStack(ClientErrors.text(body.get("stack"), ClientErrors.STACK_MAX))
         .withComponentStack(ClientErrors.text(body.get("componentStack"), ClientErrors.COMPONENT_STACK_MAX))
         .withRequestId(ClientErrors.token(body.get("requestId"), ClientErrors.REQUEST_ID_MAX))
         .withRoute(ClientErrors.route(body.get("route")))
         .withConversationId(conversationId != null && store.getConversation(userId, conversationId) != null ? conversationId : null)
         .withMessageId(messageId != null && store.getMessage(userId, messageId) != null ? messageId : null)
         .withTurnId(turnId != null && store.getTurn(userId, turnId) != null ? turnId : null)
         .withPartType(ClientErrors.token(body.get("partType"), ClientErrors.PART_TYPE_MAX))
         .withRenderType(ClientErrors.token(body.get("renderType"), ClientErrors.RENDER_TYPE_MAX))
         .withAppVersion(ClientErrors.token(body.get("appVersion"), ClientErrors.APP_VERSION_MAX))
         .withOccurredAt(runtime.getClientErrors().occurredAt(body.get("occurredAt")));
      AssistantClientError saved = store.insert(row);

      //////////////////////////////////////////////////////////////////////
      // structured, no reported text: the row holds the (scrubbed) text //
      //////////////////////////////////////////////////////////////////////
      LOG.info("Assistant client error reported", logPair("clientErrorId", saved.getId()), logPair("scope", row.getScope()),
         logPair("partType", row.getPartType()), logPair("renderType", row.getRenderType()), logPair("conversationId", row.getConversationId()),
         logPair("messageId", row.getMessageId()), logPair("turnId", row.getTurnId()));
      return (Map.of("accepted", true));
   }



   /*******************************************************************************
    ** GET /actions/{id}: one of the user's pending actions.
    *******************************************************************************/
   public Map<String, Object> getAction(Integer id) throws QException, AssistantApiException
   {
      requireAccess();
      return (toJson(expireIfDue(requireAction(id))));
   }



   /*******************************************************************************
    ** POST /actions/{id}/confirm: run the proposed write through the UI API
    ** with THIS request's session (the user's own), then record the result and
    ** a tool-call log row. A write is never run twice.
    *******************************************************************************/
   public Map<String, Object> confirmAction(Integer id) throws QException, AssistantApiException
   {
      requireAccess();
      AssistantPendingAction action = expireIfDue(requireAction(id));
      if(!AssistantPendingAction.STATUS_PENDING.equals(action.getStatus()))
      {
         throw (new AssistantApiException(409, "This action is already " + action.getStatus().toLowerCase() + "."));
      }

      AssistantTurn proposingTurn = store.getTurn(userId, action.getTurnId());
      if(proposingTurn != null && isRunning(proposingTurn))
      {
         throw (new AssistantApiException(409, "Wait for the assistant to finish before confirming this action."));
      }

      ///////////////////////////////////////////////////////////////////////
      // claim it first, so a double click cannot run the write twice     //
      ///////////////////////////////////////////////////////////////////////
      Instant now = runtime.getClock().instant();
      store.updateFields(AssistantPendingAction.TABLE_NAME, new QRecord().withValue("id", id).withValue("status", "RUNNING").withValue("decidedAt", now));
      AssistantPendingAction claimed = store.getPendingAction(userId, id);
      if(claimed == null || !"RUNNING".equals(claimed.getStatus()))
      {
         throw (new AssistantApiException(409, "This action is already being handled."));
      }

      Map<String, Object> values;
      try
      {
         values = action.getParamsJson() == null ? Map.of() : JsonUtils.toObject(action.getParamsJson(), new TypeReference<Map<String, Object>>() {});
      }
      catch(Exception e)
      {
         throw (new QException("The stored values of action " + id + " are not valid JSON", e));
      }
      PolicyService.EffectivePolicy policy = runtime.getPolicyService().current(true);
      UiApiToolbox.Context context = new UiApiToolbox.Context(session.getUuid(), session.getValue(QSession.VALUE_KEY_USER_TIMEZONE), runtime.getUiApiGateway(), policy.tools(), null, credentials, action.getTurnId() == null ? null : String.valueOf(action.getTurnId()));
      UiApiToolbox.Outcome outcome = BulkLoadTools.WRITE_TOOLS.contains(action.getToolName())
         ? runtime.getBulkLoadTools().executeConfirmed(context, action.getToolCallId(), action.getToolName(), action.getTableName(), values)
         : runtime.getToolbox().executeConfirmedWrite(context, action.getToolCallId(), action.getToolName(), action.getTableName(), action.getRecordId(), values);
      AiToolResult result = outcome.getResult();
      boolean ok = result.getOutcome() == AiToolOutcome.OK;

      store.updateFields(AssistantPendingAction.TABLE_NAME, new QRecord()
         .withValue("id", id)
         .withValue("status", ok ? AssistantPendingAction.STATUS_EXECUTED : AssistantPendingAction.STATUS_FAILED)
         .withValue("httpStatus", outcome.getHttpStatus())
         .withValue("resultSummary", truncate(result.getSummary(), 500)));

      store.insert(new AssistantToolCall()
         .withTurnId(action.getTurnId())
         .withClientChannel("assistant" + (action.getTurnId() == null ? "" : "/" + action.getTurnId()))
         .withConversationId(action.getConversationId())
         .withUserId(userId)
         .withToolCallId(action.getToolCallId())
         .withToolName(action.getToolName())
         .withInputJson(truncate(new AiRedactor(List.of()).redact(action.getParamsJson() == null ? "{}" : action.getParamsJson(), new HashSet<>()), 4000))
         .withOutcome(result.getOutcome() == null ? AiToolOutcome.ERROR.name() : result.getOutcome().name())
         .withPermissionCheck(result.getPermissionCheck())
         .withTableName(action.getTableName())
         .withRowCount(result.getRowCount())
         .withDurationMs(result.getDurationMs())
         .withResultSummary(truncate("Confirmed: " + result.getSummary(), 500))
         .withResultJson(result.getModelContent())
         .withUiApiOperation(outcome.getOperation())
         .withHttpStatus(outcome.getHttpStatus())
         .withAuditHref(outcome.getAuditHref())
         .withPendingActionId(id));

      Map<String, Object> rs = toJson(store.getPendingAction(userId, id));
      rs.put("outcome", result.getOutcome() == null ? null : result.getOutcome().name());
      rs.put("message", result.getModelContent());
      if(outcome.getAuditHref() != null)
      {
         rs.put("href", outcome.getAuditHref());
      }
      return (rs);
   }



   /*******************************************************************************
    ** POST /actions/{id}/reject: nothing runs.
    *******************************************************************************/
   public Map<String, Object> rejectAction(Integer id) throws QException, AssistantApiException
   {
      requireAccess();
      AssistantPendingAction action = expireIfDue(requireAction(id));
      if(!AssistantPendingAction.STATUS_PENDING.equals(action.getStatus()))
      {
         throw (new AssistantApiException(409, "This action is already " + action.getStatus().toLowerCase() + "."));
      }
      store.updateFields(AssistantPendingAction.TABLE_NAME, new QRecord().withValue("id", id).withValue("status", AssistantPendingAction.STATUS_REJECTED)
         .withValue("decidedAt", runtime.getClock().instant()).withValue("resultSummary", "Cancelled by the user; nothing changed."));
      return (toJson(store.getPendingAction(userId, id)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private AssistantPendingAction requireAction(Integer id) throws QException, AssistantApiException
   {
      AssistantPendingAction action = id == null ? null : store.getPendingAction(userId, id);
      if(action == null)
      {
         throw (new AssistantApiException(404, "Action not found."));
      }
      return (action);
   }



   /*******************************************************************************
    ** Mark a pending action past its expiry as EXPIRED.
    *******************************************************************************/
   private AssistantPendingAction expireIfDue(AssistantPendingAction action) throws QException
   {
      if(AssistantPendingAction.STATUS_PENDING.equals(action.getStatus()) && action.getExpiresAt() != null && action.getExpiresAt().isBefore(runtime.getClock().instant()))
      {
         store.updateFields(AssistantPendingAction.TABLE_NAME, new QRecord().withValue("id", action.getId()).withValue("status", AssistantPendingAction.STATUS_EXPIRED)
            .withValue("resultSummary", "Expired before it was confirmed; nothing changed."));
         action.setStatus(AssistantPendingAction.STATUS_EXPIRED);
         action.setResultSummary("Expired before it was confirmed; nothing changed.");
      }
      return (action);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Map<String, Object> toJson(AssistantPendingAction action)
   {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", action.getId());
      map.put("status", action.getStatus());
      map.put("toolName", action.getToolName());
      map.put("tableName", action.getTableName());
      map.put("recordId", action.getRecordId());
      map.put("summary", action.getSummary());
      map.put("httpStatus", action.getHttpStatus());
      map.put("resultSummary", action.getResultSummary());
      map.put("expiresAt", action.getExpiresAt() == null ? null : action.getExpiresAt().toString());
      return (map);
   }



   /*******************************************************************************
    ** GET /access?table=&prompt=: "explain access" for the calling user: per
    ** table, the user's UI permissions (GET /qqq/v1/metaData with their own
    ** session), the assistant's data access, and each tool's verdict with the
    ** reason.
    *******************************************************************************/
   public Map<String, Object> access(String table, String prompt) throws QException, AssistantApiException
   {
      return (access(table, prompt, null));
   }



   /*******************************************************************************
    ** A supplied turn must belong to this user before using it for attribution.
    *******************************************************************************/
   public Map<String, Object> access(String table, String prompt, Integer turnId) throws QException, AssistantApiException
   {
      requireAccess();
      if(turnId != null)
      {
         requireTurn(turnId);
      }
      List<Map<String, Object>> tables = new ArrayList<>();
      for(AccessExplainer.TableAccess access : runtime.getAccessExplainer().explain(runtime.getUiApiGateway(), session.getUuid(), credentials, runtime.getPolicyService().current(false).tools(), table, prompt, turnId == null ? null : String.valueOf(turnId)))
      {
         if(!access.uiVisible() && access.mode() == DataAccessMode.NONE)
         {
            continue;
         }
         Map<String, Object> row = new LinkedHashMap<>();
         row.put("table", access.table());
         row.put("label", access.label());
         row.put("dataAccess", access.mode().name());
         row.put("dataAccessSource", access.modeSource());
         row.put("ui", Map.of("visible", access.uiVisible(), "read", access.uiRead(), "insert", access.uiInsert(), "edit", access.uiEdit(), "delete", access.uiDelete()));
         List<Map<String, Object>> operations = new ArrayList<>();
         for(AccessExplainer.OperationCheck check : access.operations())
         {
            operations.add(Map.of("tool", check.tool(), "operation", check.operation(), "allowed", check.allowed(), "reason", check.reason()));
         }
         row.put("operations", operations);
         tables.add(row);
      }
      return (Map.of("tables", tables));
   }



   /*******************************************************************************
    ** The configured turn time limit (replay loops are capped by it).
    *******************************************************************************/
   public int maxTurnSeconds()
   {
      return (config.getMaxTurnSeconds());
   }



   /*******************************************************************************
    ** Hard upper bound for remote stream polling and stale-run recovery.
    *******************************************************************************/
   public long maxStreamSeconds()
   {
      return (config.getMaxTurnSeconds() + (long) config.getTurnForceFinalizeGraceSeconds() + config.getTurnLeaseSeconds());
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



   /*******************************************************************************
    ** The provider answers would come from now, or null.
    *******************************************************************************/
   private ProviderService.Active activeProvider() throws QException
   {
      return (runtime.getProviderService().resolve(runtime.getPolicyService().current(false).mainModel()));
   }

}
