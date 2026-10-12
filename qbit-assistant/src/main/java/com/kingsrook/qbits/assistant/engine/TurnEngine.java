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

package com.kingsrook.qbits.assistant.engine;


import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.CacheHint;
import com.kingsrook.qbits.assistant.provider.ModelCapabilities;
import com.kingsrook.qbits.assistant.provider.ModelEventListener;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelPart;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelResponse;
import com.kingsrook.qbits.assistant.provider.ModelUsage;
import com.kingsrook.qbits.assistant.provider.StreamHandle;
import com.kingsrook.qqq.backend.core.actions.ai.AiRecordRef;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolCall;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolOutcome;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolResult;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The agent loop of one turn (spec 2.5). Runs on its own thread with the
 ** requesting user's session in QContext:
 **
 ** 1. Build the request (ContextBuilder) and stream the model's answer;
 **    text deltas become text.delta events (thinking is never forwarded).
 ** 2. For tool_use blocks: run them through the AiToolKernel under the
 **    user's session - independent calls in parallel, at most
 **    maxParallelToolCalls at once - and return ALL results in ONE user
 **    message (errors as is_error tool_results). Each call is audited.
 ** 3. Repeat until the model answers without tools, or a limit stops it:
 **    maxToolCallsPerTurn, maxTurnSeconds, the hard budget, a user cancel
 **    or the kill switch (checked before every model call and every tool
 **    step, so a flipped switch stops the turn within one step).
 ** 4. Persist the assistant message parts, turn accounting, tool calls and
 **    usage; emit usage and message.end.
 **
 ** Events go to the in-memory log at once (live SSE readers) and to the
 ** assistantTurnEvent table from this thread (replay). Logs never contain
 ** message content.
 *******************************************************************************/
public class TurnEngine implements Runnable
{
   private static final QLogger LOG = QLogger.getLogger(TurnEngine.class);

   private static final int MAX_INPUT_JSON_CHARS  = 4000;
   private static final int MAX_RESULT_JSON_CHARS = 64_000;
   private static final int TEXT_FLUSH_CHARS      = 48;
   private static final long TEXT_FLUSH_MILLIS    = 120;

   private static final Set<AiToolOutcome> ERROR_OUTCOMES = Set.of(AiToolOutcome.DENIED, AiToolOutcome.NOT_FOUND, AiToolOutcome.INVALID_INPUT, AiToolOutcome.ERROR);

   private final AssistantRuntime           runtime;
   private final AssistantQBitConfig        config;
   private final AssistantStore             store;
   private final QSession                   session;
   private final String                     userId;
   private final AssistantConversation      conversation;
   private final AssistantMessage           userMessage;
   private final AssistantMessage           assistantMessage;
   private final AssistantTurn              turn;
   private final String                     userText;
   private final Map<String, Object>        pageContext;
   private final PolicyService.EffectivePolicy policy;
   private final RunningTurn                running;
   private final String                     softBudgetNotice;

   private final MessageParts               parts         = new MessageParts();
   private final ModelUsage                 usage         = new ModelUsage();
   private final List<String>               requestIds    = new ArrayList<>();
   private final ConcurrentLinkedQueue<TurnEvent> toPersist = new ConcurrentLinkedQueue<>();
   private final StringBuilder              textBuffer    = new StringBuilder();
   private long                             lastTextFlush = System.currentTimeMillis();

   private Instant startedAt;
   private Instant deadline;
   private int     toolCallCount  = 0;
   private int     modelCallCount = 0;
   private long    costMicros     = 0;

   private volatile boolean finished = false;



   /*******************************************************************************
    ** Everything a turn needs; the rows are already persisted by the API.
    *******************************************************************************/
   public TurnEngine(AssistantRuntime runtime, QSession session, String userId, AssistantConversation conversation, AssistantMessage userMessage, AssistantMessage assistantMessage,
      AssistantTurn turn, String userText, Map<String, Object> pageContext, PolicyService.EffectivePolicy policy, RunningTurn running, String softBudgetNotice)
   {
      this.runtime = runtime;
      this.config = runtime.getConfig();
      this.store = runtime.getStore();
      this.session = session;
      this.userId = userId;
      this.conversation = conversation;
      this.userMessage = userMessage;
      this.assistantMessage = assistantMessage;
      this.turn = turn;
      this.userText = userText;
      this.pageContext = pageContext;
      this.policy = policy;
      this.running = running;
      this.softBudgetNotice = softBudgetNotice;
   }



   /*******************************************************************************
    ** Run the turn to completion. Never throws.
    *******************************************************************************/
   @Override
   public void run()
   {
      startedAt = runtime.getClock().instant();
      deadline = startedAt.plusSeconds(config.getMaxTurnSeconds());

      String status     = "COMPLETE";
      String stopReason = null;
      String errorCode  = null;
      String errorText  = null;

      try
      {
         QContext.init(runtime.getQInstance(), session);
         startKillSwitchWatcher();

         emit(TurnEvent.MESSAGE_START, map("turnId", turn.getId(), "conversationId", conversation.getId(), "userMessageId", userMessage.getId(), "messageId", assistantMessage.getId()));
         if(softBudgetNotice != null)
         {
            notice("warning", softBudgetNotice);
         }
         persistPendingEvents();

         AssistantModelProvider provider = config.getActiveModelProvider();
         ModelCapabilities      caps     = provider.getCapabilities(policy.mainModel());

         List<AssistantMessage> history = new ArrayList<>();
         for(AssistantMessage message : store.listMessages(userId, conversation.getId(), userMessage.getId(), config.getHistoryMessageLimit()))
         {
            if("COMPLETE".equals(message.getStatus()))
            {
               history.add(message);
            }
         }

         ModelRequest request = runtime.getContextBuilder().build(session, policy.mainModel(), policy.mainEffort(), history, userText, pageContext);
         if(caps != null && Boolean.FALSE.equals(caps.getParallelToolCalls()))
         {
            request.setParallelToolCalls(false);
         }
         Set<String> allowedTools = runtime.getContextBuilder().toolNames();

         boolean toolLimitReached = false;
         while(true)
         {
            Stop stop = checkStop();
            if(stop != null)
            {
               status = stop.status;
               errorCode = stop.errorCode;
               errorText = stop.message;
               stopReason = stop.stopReason;
               break;
            }

            ModelResponse response = callModel(provider, request);
            stopReason = response.getStopReason();

            if(ModelResponse.STOP_CANCELLED.equals(stopReason))
            {
               Stop cancelStop = cancelledStop();
               status = cancelStop.status;
               errorCode = cancelStop.errorCode;
               errorText = cancelStop.message;
               break;
            }

            PolicyService.BudgetStatus budget = runtime.getPolicyService().checkBudget(policy, userId, usage.billableTokens(), costMicros);
            if(budget.hardExceeded())
            {
               status = "ERROR";
               errorCode = "BUDGET";
               errorText = budget.message();
               break;
            }

            if(ModelResponse.STOP_REFUSAL.equals(stopReason))
            {
               notice("warning", "The assistant declined to answer this request.");
               break;
            }
            if(ModelResponse.STOP_MAX_TOKENS.equals(stopReason))
            {
               notice("warning", "The answer was cut off because it got too long. Ask for a shorter answer or a narrower question.");
               break;
            }
            if(ModelResponse.STOP_CONTEXT.equals(stopReason))
            {
               notice("warning", "This conversation is too long for the assistant. Start a new conversation.");
               break;
            }

            List<ModelPart> toolUses = response.getToolUses();
            if(toolUses.isEmpty())
            {
               break;
            }
            if(toolLimitReached)
            {
               notice("warning", "The assistant stopped after " + toolCallCount + " steps (the limit per question). Ask a narrower question to continue.");
               stopReason = "tool_limit";
               break;
            }

            ModelMessage assistantTurnMessage = new ModelMessage().withRole(ModelMessage.Role.ASSISTANT).withParts(new ArrayList<>(response.getParts())).withProviderState(response.getProviderState());
            request.getMessages().add(assistantTurnMessage);

            stop = checkStop();
            if(stop != null)
            {
               status = stop.status;
               errorCode = stop.errorCode;
               errorText = stop.message;
               stopReason = stop.stopReason;
               break;
            }

            ToolBatch batch = executeTools(toolUses, allowedTools);
            toolLimitReached = batch.limitReached;

            for(ModelMessage message : request.getMessages())
            {
               message.setCacheHint(CacheHint.NONE);
            }
            ModelMessage results = new ModelMessage().withRole(ModelMessage.Role.USER).withParts(batch.results).withCacheHint(CacheHint.SHORT);
            request.getMessages().add(results);

            if(batch.cancelled)
            {
               Stop cancelStop = cancelledStop();
               status = cancelStop.status;
               errorCode = cancelStop.errorCode;
               errorText = cancelStop.message;
               stopReason = ModelResponse.STOP_CANCELLED;
               break;
            }
         }
      }
      catch(ModelProviderException e)
      {
         if(e.getPartialUsage() != null)
         {
            usage.add(e.getPartialUsage());
            costMicros += pricing(policy.mainModel()).costMicros(e.getPartialUsage());
         }
         status = "ERROR";
         errorCode = e.getKind() == ModelProviderException.Kind.TIMEOUT ? "TIMEOUT" : "PROVIDER";
         errorText = e.getKind() == ModelProviderException.Kind.TIMEOUT
            ? "The assistant took too long to answer."
            : (e.isRetryable() ? "The AI service is busy or unavailable right now. Try again in a moment." : "The AI service could not answer this request.");
         LOG.warn("Assistant model call failed", logPair("turnId", turn.getId()), logPair("kind", e.getKind()), logPair("httpStatus", e.getHttpStatus()));
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         status = "CANCELLED";
         stopReason = ModelResponse.STOP_CANCELLED;
      }
      catch(Exception e)
      {
         status = "ERROR";
         errorCode = "INTERNAL";
         errorText = "Something went wrong while answering. Try again.";
         LOG.warn("Assistant turn failed", e, logPair("turnId", turn.getId()));
      }
      finally
      {
         finished = true;
         finish(status, stopReason, errorCode, errorText);
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** Poll the kill switch while the turn runs (with the user's own session),
    ** so a flip cancels the turn - and every other turn here - even while it
    ** waits on the model.
    *******************************************************************************/
   private void startKillSwitchWatcher()
   {
      long pollMillis = config.getKillSwitchPollMillis() == null ? 2000 : config.getKillSwitchPollMillis();
      Thread.ofVirtual().name("assistant-killswitch-" + turn.getId()).start(() ->
      {
         try
         {
            while(!finished && !running.isCancelled())
            {
               Thread.sleep(pollMillis);
               if(finished)
               {
                  return;
               }
               QContext.init(runtime.getQInstance(), session);
               try
               {
                  if(runtime.getPolicyService().isKillSwitchOn())
                  {
                     runtime.cancelAll("KILL_SWITCH");
                  }
               }
               finally
               {
                  QContext.clear();
               }
            }
         }
         catch(InterruptedException e)
         {
            Thread.currentThread().interrupt();
         }
      });
   }



   /*******************************************************************************
    ** Why a turn stops.
    *******************************************************************************/
   private record Stop(String status, String errorCode, String message, String stopReason)
   {
   }



   /*******************************************************************************
    ** The results of one batch of tool calls.
    *******************************************************************************/
   private record ToolBatch(List<ModelPart> results, boolean limitReached, boolean cancelled)
   {
   }



   /*******************************************************************************
    ** Cancel, kill switch, time limit, or a cancel recorded by another replica.
    *******************************************************************************/
   private Stop checkStop()
   {
      if(running.isCancelled())
      {
         return (cancelledStop());
      }
      if(runtime.getPolicyService().isKillSwitchOn())
      {
         runtime.cancelAll("KILL_SWITCH");
         return (cancelledStop());
      }
      if(!runtime.getClock().instant().isBefore(deadline))
      {
         return (new Stop("ERROR", "TIMEOUT", "The assistant ran out of time for this question.", "timeout"));
      }
      try
      {
         AssistantTurn row = store.getTurn(userId, turn.getId());
         if(row != null && "CANCELLING".equals(row.getStatus()))
         {
            running.cancel("USER");
            return (cancelledStop());
         }
      }
      catch(QException e)
      {
         LOG.debug("Could not re-read turn status", e);
      }
      return (null);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private Stop cancelledStop()
   {
      if("KILL_SWITCH".equals(running.getCancelReason()))
      {
         String reason = StringUtils.hasContent(policy.killSwitchReason()) ? " " + policy.killSwitchReason() : "";
         return (new Stop("CANCELLED", "KILL_SWITCH", "The assistant has been turned off by an administrator." + reason, ModelResponse.STOP_CANCELLED));
      }
      return (new Stop("CANCELLED", null, null, ModelResponse.STOP_CANCELLED));
   }



   /*******************************************************************************
    ** One streamed model call.
    *******************************************************************************/
   private ModelResponse callModel(AssistantModelProvider provider, ModelRequest request) throws ModelProviderException, InterruptedException
   {
      modelCallCount++;
      ModelEventListener listener = new ModelEventListener()
      {
         @Override
         public void onTextDelta(String text)
         {
            bufferText(text);
         }
      };

      StreamHandle handle = provider.stream(request.copy(), listener);
      running.setCurrentHandle(handle);
      ModelResponse response;
      try
      {
         Duration remaining = Duration.between(runtime.getClock().instant(), deadline);
         response = handle.await(remaining.isNegative() ? Duration.ofMillis(1) : remaining);
      }
      finally
      {
         running.setCurrentHandle(null);
         flushText(true);
      }

      if(response.getUsage() != null)
      {
         usage.add(response.getUsage());
         costMicros += pricing(policy.mainModel()).costMicros(response.getUsage());
      }
      if(response.getRequestId() != null)
      {
         requestIds.add(response.getRequestId());
      }
      persistPendingEvents();
      return (response);
   }



   /*******************************************************************************
    ** Run a batch of tool calls through the kernel.
    *******************************************************************************/
   private ToolBatch executeTools(List<ModelPart> toolUses, Set<String> allowedTools) throws InterruptedException
   {
      int              allowed      = Math.max(0, config.getMaxToolCallsPerTurn() - toolCallCount);
      boolean          limitReached = false;
      AiToolResult[]   results      = new AiToolResult[toolUses.size()];
      List<Future<?>>  futures      = new ArrayList<>();
      Semaphore        permits      = new Semaphore(Math.max(1, config.getMaxParallelToolCalls()));

      for(int i = 0; i < toolUses.size(); i++)
      {
         ModelPart use = toolUses.get(i);
         if(i >= allowed)
         {
            limitReached = true;
            continue;
         }
         toolCallCount++;
         emit(TurnEvent.TOOL_START, map("messageId", assistantMessage.getId(), "toolCallId", use.getToolUseId(), "name", use.getToolName(), "summary", ToolLabels.running(use.getToolName())));

         if(!allowedTools.contains(use.getToolName()))
         {
            results[i] = new AiToolResult().withToolCallId(use.getToolUseId()).withToolName(use.getToolName()).withOutcome(AiToolOutcome.INVALID_INPUT)
               .withSummary("Unknown tool").withModelContent("Unknown tool \"" + use.getToolName() + "\". Use only the tools provided.");
            continue;
         }

         final int    index = i;
         AiToolCall   call  = new AiToolCall().withId(use.getToolUseId()).withName(use.getToolName()).withInput(use.getInput());
         futures.add(runtime.getExecutor().submit(() ->
         {
            try
            {
               permits.acquire();
               try
               {
                  if(!running.isCancelled())
                  {
                     results[index] = runtime.getKernel().execute(session, call);
                  }
               }
               finally
               {
                  permits.release();
               }
            }
            catch(InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            catch(RuntimeException e)
            {
               LOG.warn("Tool kernel threw", e, logPair("tool", call.getName()));
               results[index] = new AiToolResult().withToolCallId(call.getId()).withToolName(call.getName()).withOutcome(AiToolOutcome.ERROR)
                  .withSummary("Failed").withModelContent("The tool failed unexpectedly.");
            }
         }));
      }
      persistPendingEvents();

      for(Future<?> future : futures)
      {
         long waitMillis = Math.max(1, Duration.between(runtime.getClock().instant(), deadline).toMillis());
         try
         {
            future.get(waitMillis, TimeUnit.MILLISECONDS);
         }
         catch(java.util.concurrent.TimeoutException e)
         {
            future.cancel(true);
         }
         catch(java.util.concurrent.ExecutionException e)
         {
            LOG.debug("tool future failed", e);
         }
      }

      List<ModelPart> modelResults = new ArrayList<>();
      for(int i = 0; i < toolUses.size(); i++)
      {
         ModelPart use = toolUses.get(i);
         if(i >= allowed)
         {
            modelResults.add(ModelPart.toolResult(use.getToolUseId(), "Not run: the limit of tool calls for this question was reached. Answer with what you have.", true));
            continue;
         }

         AiToolResult result = results[i];
         if(result == null)
         {
            result = new AiToolResult().withToolCallId(use.getToolUseId()).withToolName(use.getToolName()).withOutcome(AiToolOutcome.ERROR)
               .withSummary(running.isCancelled() ? "Not run (stopped)" : "Timed out")
               .withModelContent(running.isCancelled() ? "Not run: the user stopped the answer." : "The tool did not finish in time.");
         }
         recordToolResult(use, result);

         String content = result.getModelContent() == null ? "" : result.getModelContent();
         modelResults.add(ModelPart.toolResult(use.getToolUseId(), content, result.getOutcome() != null && ERROR_OUTCOMES.contains(result.getOutcome())));
      }
      persistPendingEvents();
      return (new ToolBatch(modelResults, limitReached, running.isCancelled()));
   }



   /*******************************************************************************
    ** Audit row, events and message parts of one tool result.
    *******************************************************************************/
   private void recordToolResult(ModelPart use, AiToolResult result)
   {
      String outcome = result.getOutcome() == null ? AiToolOutcome.ERROR.name() : result.getOutcome().name();
      String summary = StringUtils.hasContent(result.getSummary()) ? result.getSummary() : ToolLabels.done(use.getToolName());

      List<Map<String, Object>> refs = new ArrayList<>();
      for(AiRecordRef ref : result.getRecordRefs() == null ? List.<AiRecordRef>of() : result.getRecordRefs())
      {
         if(ref.getHref() == null || isAppHref(ref.getHref()))
         {
            refs.add(map("tableName", ref.getTableName(), "tableLabel", ref.getTableLabel(), "id", ref.getId(), "label", ref.getLabel(), "href", ref.getHref()));
         }
      }

      Map<String, Object> data = map("messageId", assistantMessage.getId(), "toolCallId", use.getToolUseId(), "name", use.getToolName(), "outcome", outcome, "summary", summary);
      if(result.getRender() != null)
      {
         data.put("render", result.getRender());
      }
      if(!refs.isEmpty())
      {
         data.put("recordRefs", refs);
      }
      emit(TurnEvent.TOOL_RESULT, data);

      parts.addTool(use.getToolUseId(), use.getToolName(), summary, outcome, result.getRender());
      parts.addRecordRefs(refs);

      String href = result.getNavigateHref();
      if(AiToolOutcome.OK.equals(result.getOutcome()) && href != null && isAppHref(href))
      {
         emit(TurnEvent.UI_NAVIGATE, map("messageId", assistantMessage.getId(), "href", href));
         parts.addNavigate(href);
      }

      try
      {
         store.insert(new AssistantToolCall()
            .withTurnId(turn.getId())
            .withConversationId(conversation.getId())
            .withUserId(userId)
            .withToolCallId(use.getToolUseId())
            .withToolName(use.getToolName())
            .withInputJson(truncate(JsonUtils.toJson(use.getInput()), MAX_INPUT_JSON_CHARS))
            .withOutcome(outcome)
            .withPermissionCheck(result.getPermissionCheck())
            .withTableName(result.getTableName())
            .withProcessName(result.getProcessName())
            .withWidgetName(result.getWidgetName())
            .withRowCount(result.getRowCount())
            .withDurationMs(result.getDurationMs())
            .withResultSummary(summary)
            .withResultJson(truncate(result.getModelContent(), MAX_RESULT_JSON_CHARS))
            .withRedactionsJson(result.getRedactions() == null || result.getRedactions().isEmpty() ? null : JsonUtils.toJson(result.getRedactions())));
      }
      catch(QException e)
      {
         LOG.warn("Could not record assistant tool call", e, logPair("turnId", turn.getId()), logPair("tool", use.getToolName()));
      }
   }



   /*******************************************************************************
    ** Only same-origin application links are passed on.
    *******************************************************************************/
   static boolean isAppHref(String href)
   {
      return (href != null && (href.equals("/app") || href.startsWith("/app/")) && !href.contains("//") && !href.contains("\\"));
   }



   /*******************************************************************************
    ** Persist the message, turn, conversation and usage; emit the end events.
    *******************************************************************************/
   private void finish(String status, String stopReason, String errorCode, String errorText)
   {
      try
      {
         flushText(true);
         Instant now = runtime.getClock().instant();

         if(errorText != null)
         {
            parts.addNotice("error", errorText);
         }

         String model = policy == null ? null : policy.mainModel();
         try
         {
            store.updateFields(AssistantMessage.TABLE_NAME, new QRecord()
               .withValue("id", assistantMessage.getId())
               .withValue("status", status)
               .withValue("model", model)
               .withValue("partsJson", parts.toJson()));

            AssistantConversation current = store.getConversation(userId, conversation.getId());
            if(current != null)
            {
               store.updateFields(AssistantConversation.TABLE_NAME, new QRecord()
                  .withValue("id", current.getId())
                  .withValue("lastMessageAt", now)
                  .withValue("unreadCount", (current.getUnreadCount() == null ? 0 : current.getUnreadCount()) + 1)
                  .withValue("totalTokensIn", nz(current.getTotalTokensIn()) + nz(usage.getInputTokens()) + nz(usage.getCacheReadTokens()) + nz(usage.getCacheWriteTokens()))
                  .withValue("totalTokensOut", nz(current.getTotalTokensOut()) + nz(usage.getOutputTokens()))
                  .withValue("totalCostMicros", nz(current.getTotalCostMicros()) + costMicros));
            }

            store.addUsage(userId, LocalDate.now(runtime.getClock().withZone(ZoneOffset.UTC)), usage, costMicros, true);
         }
         catch(QException e)
         {
            LOG.warn("Could not persist the end of an assistant turn", e, logPair("turnId", turn.getId()));
         }

         /////////////////////////////////////////////////////////////////////
         // terminal events are persisted BEFORE the turn row turns final, //
         // so a replay of a finished turn always ends with message.end    //
         /////////////////////////////////////////////////////////////////////
         emit(TurnEvent.USAGE, map("turnId", turn.getId(), "inputTokens", usage.getInputTokens(), "outputTokens", usage.getOutputTokens(),
            "cacheReadTokens", usage.getCacheReadTokens(), "cacheWriteTokens", usage.getCacheWriteTokens(), "costMicros", costMicros));
         if(errorCode != null)
         {
            emit(TurnEvent.ERROR, map("code", errorCode, "message", errorText));
         }
         Map<String, Object> end = map("messageId", assistantMessage.getId(), "status", status);
         if(stopReason != null)
         {
            end.put("stopReason", stopReason);
         }
         emit(TurnEvent.MESSAGE_END, end);
         persistPendingEvents();

         try
         {
            store.updateFields(AssistantTurn.TABLE_NAME, new QRecord()
               .withValue("id", turn.getId())
               .withValue("status", status)
               .withValue("stopReason", truncate(stopReason, 64))
               .withValue("errorCode", errorCode)
               .withValue("cancelReason", running.getCancelReason())
               .withValue("model", model)
               .withValue("endedAt", now)
               .withValue("toolCallCount", toolCallCount)
               .withValue("modelCallCount", modelCallCount)
               .withValue("inputTokens", usage.getInputTokens())
               .withValue("outputTokens", usage.getOutputTokens())
               .withValue("cacheReadTokens", usage.getCacheReadTokens())
               .withValue("cacheWriteTokens", usage.getCacheWriteTokens())
               .withValue("costMicros", costMicros)
               .withValue("providerRequestIds", requestIds.isEmpty() ? null : String.join(",", requestIds)));
         }
         catch(QException e)
         {
            LOG.warn("Could not persist the end of an assistant turn", e, logPair("turnId", turn.getId()));
         }

         LOG.info("Assistant turn finished",
            logPair("turnId", turn.getId()),
            logPair("conversationId", conversation.getId()),
            logPair("userId", userId),
            logPair("model", model),
            logPair("status", status),
            logPair("stopReason", stopReason),
            logPair("errorCode", errorCode),
            logPair("modelCalls", modelCallCount),
            logPair("toolCalls", toolCallCount),
            logPair("inputTokens", usage.getInputTokens()),
            logPair("outputTokens", usage.getOutputTokens()),
            logPair("cacheReadTokens", usage.getCacheReadTokens()),
            logPair("cacheWriteTokens", usage.getCacheWriteTokens()),
            logPair("costMicros", costMicros),
            logPair("durationMs", Duration.between(startedAt, now).toMillis()));
      }
      catch(RuntimeException e)
      {
         LOG.warn("Assistant turn finish failed", e, logPair("turnId", turn.getId()));
      }
      finally
      {
         running.getLog().close();
         runtime.unregister(turn.getId());
         runtime.getRateLimiter().release(userId);
      }
   }



   /*******************************************************************************
    ** Buffer streamed text; emit it in small chunks (provider thread).
    *******************************************************************************/
   private void bufferText(String text)
   {
      synchronized(textBuffer)
      {
         textBuffer.append(text);
      }
      flushText(false);
   }



   /*******************************************************************************
    ** Emit buffered text as one text.delta (when big or old enough, or forced).
    *******************************************************************************/
   private void flushText(boolean force)
   {
      String chunk;
      synchronized(textBuffer)
      {
         long now = System.currentTimeMillis();
         if(textBuffer.isEmpty() || (!force && textBuffer.length() < TEXT_FLUSH_CHARS && now - lastTextFlush < TEXT_FLUSH_MILLIS))
         {
            return;
         }
         chunk = textBuffer.toString();
         textBuffer.setLength(0);
         lastTextFlush = now;
         parts.addText(chunk);
         emit(TurnEvent.TEXT_DELTA, map("messageId", assistantMessage.getId(), "text", chunk));
      }
   }



   /*******************************************************************************
    ** A notice event + part.
    *******************************************************************************/
   private void notice(String level, String text)
   {
      parts.addNotice(level, text);
      emit(TurnEvent.NOTICE, map("messageId", assistantMessage.getId(), "level", level, "text", text));
   }



   /*******************************************************************************
    ** Append to the live log; queue for persistence (done on the engine
    ** thread, which has the QContext).
    *******************************************************************************/
   private void emit(String type, Map<String, Object> data)
   {
      toPersist.add(running.getLog().append(type, data));
   }



   /*******************************************************************************
    ** Persist queued events.
    *******************************************************************************/
   private void persistPendingEvents()
   {
      TurnEvent event;
      while((event = toPersist.poll()) != null)
      {
         try
         {
            store.insertEvent(turn.getId(), userId, event.getSeq(), event.getType(), event.toJson());
         }
         catch(QException e)
         {
            LOG.warn("Could not persist assistant turn event", e, logPair("turnId", turn.getId()), logPair("seq", event.getSeq()));
         }
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private ModelPricing pricing(String model)
   {
      ModelPricing pricing = config.getPricing() == null ? null : config.getPricing().get(model);
      return (pricing == null ? new ModelPricing() : pricing);
   }



   /*******************************************************************************
    ** An insertion-ordered map from key / value pairs (nulls skipped).
    *******************************************************************************/
   static Map<String, Object> map(Object... keyValues)
   {
      Map<String, Object> map = new LinkedHashMap<>();
      for(int i = 0; i + 1 < keyValues.length; i += 2)
      {
         if(keyValues[i + 1] != null)
         {
            map.put((String) keyValues[i], keyValues[i + 1] instanceof Serializable || keyValues[i + 1] instanceof Map || keyValues[i + 1] instanceof List ? keyValues[i + 1] : String.valueOf(keyValues[i + 1]));
         }
      }
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
    **
    *******************************************************************************/
   private static long nz(Long value)
   {
      return (value == null ? 0 : value);
   }

}
