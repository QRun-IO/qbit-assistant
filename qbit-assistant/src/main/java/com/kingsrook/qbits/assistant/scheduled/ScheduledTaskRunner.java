/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.assistant.scheduled;


import java.time.Instant;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.api.AssistantApiException;
import com.kingsrook.qbits.assistant.engine.MessageParts;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.engine.RunningTurn;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantScheduledRun;
import com.kingsrook.qbits.assistant.model.AssistantScheduledTask;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;


/*******************************************************************************
 ** Scheduler bookkeeping uses its supplied QContext. Each actual turn switches
 ** to the designated user's freshly authenticated UI session, restoring context
 ** afterwards. No user data is fetched by the scheduler or the store.
 *******************************************************************************/
public class ScheduledTaskRunner
{
   private final AssistantRuntime runtime;
   private final ScheduledLogin login;
   private final ScheduledAlerts alerts;



   /*******************************************************************************
    ** Default production collaborators.
    *******************************************************************************/
   public ScheduledTaskRunner(AssistantRuntime runtime)
   {
      this(runtime, new ScheduledLogin(), new ScheduledAlerts());
   }



   /*******************************************************************************
    ** Notification seam for hosts and tests; authentication stays explicit.
    *******************************************************************************/
   public ScheduledTaskRunner(AssistantRuntime runtime, ScheduledLogin login, ScheduledAlerts alerts)
   {
      this.runtime = runtime;
      this.login = login;
      this.alerts = alerts;
   }



   /*******************************************************************************
    ** One bounded page; each slot is advanced before any authentication/model.
    *******************************************************************************/
   public Integer runDue() throws QException
   {
      Instant now = runtime.getClock().instant();
      Integer count = 0;
      for(AssistantScheduledTask task : runtime.getStore().query(AssistantScheduledTask.TABLE_NAME, new QQueryFilter()
         .withCriteria("enabled", QCriteriaOperator.EQUALS, true).withCriteria("nextRunAt", QCriteriaOperator.LESS_THAN_OR_EQUALS, now)
         .withOrderBy(new QFilterOrderBy("nextRunAt", true)).withLimit(100), AssistantScheduledTask.class))
      {
         if(run(task, false) != null)
         {
            count++;
         }
      }
      return (count);
   }



   /*******************************************************************************
    ** Claim, authenticate, start the normal API turn, and record its result.
    *******************************************************************************/
   public AssistantScheduledRun run(AssistantScheduledTask candidate, Boolean manual) throws QException
   {
      var store = runtime.getStore();
      var claim = store.claimScheduled(candidate, runtime.getClock().instant(), manual);
      if(claim == null)
      {
         return (null);
      }
      AssistantScheduledRun run = claim.run();
      AssistantScheduledTask task = claim.task();
      var original = QContext.capture();
      QSession session = null;
      Boolean attached = false;
      try
      {
         session = login.login(runtime, task);
         QContext.init(runtime.getQInstance(), session);
         AssistantApi api = new AssistantApi(runtime, session).withCredentials(UiApiCredentials.ofSessionUUID(session.getUuid()));
         Integer conversationId = ((Number) api.createConversation(Map.of("title", task.getName())).get("id")).intValue();
         run.setConversationId(conversationId);
         store.updateFields(AssistantConversation.TABLE_NAME, new QRecord().withValue("id", conversationId)
            .withValue("scheduledTaskId", task.getId()).withValue("channel", "assistant/scheduled"));
         runtime.getScheduledTurn().attach(conversationId, session.getUuid(), task);
         attached = true;
         RunningTurn running = api.startTurn(conversationId, Map.of("text", task.getPrompt()));
         run.setTurnId(running.getTurnId());
         while(runtime.getRunningTurn(running.getTurnId()) != null)
         {
            Thread.sleep(20);
         }
         var turn = store.getTurn(task.getRunAsUserId(), run.getTurnId());
         if(turn == null || "RUNNING".equals(turn.getStatus()))
         {
            throw (new QException("The scheduled turn did not persist a terminal result."));
         }
         run.setCostMicros(ScheduledTurn.cost(runtime, turn.getId()));
         run.setStatus("COMPLETE".equals(turn.getStatus()) ? "OK" : blocked(turn.getErrorCode()) ? "BLOCKED" : "FAILED");
         run.setError("OK".equals(run.getStatus()) ? null : "Turn stopped: " + (turn.getErrorCode() == null ? turn.getStatus() : turn.getErrorCode()));
         String summary = MessageParts.fromJson(store.getMessage(task.getRunAsUserId(), turn.getAssistantMessageId()).getPartsJson()).text();
         Boolean pending = !store.query(AssistantPendingAction.TABLE_NAME, new QQueryFilter().withCriteria("turnId", QCriteriaOperator.EQUALS, turn.getId())
            .withCriteria("userId", QCriteriaOperator.EQUALS, task.getRunAsUserId()).withCriteria("status", QCriteriaOperator.EQUALS, "PENDING").withLimit(1), AssistantPendingAction.class).isEmpty();
         run.setSummary(Boolean.TRUE.equals(runtime.getConfig().getHipaaMode()) ? "see conversation"
            : truncate((pending ? "Waiting for your confirmation in the panel. " : "") + summary, 2000));
      }
      catch(AssistantApiException e)
      {
         run.withStatus(blocked(e.getCode()) ? "BLOCKED" : "FAILED").withError("Turn could not start: " + e.getCode());
      }
      catch(InterruptedException e)
      {
         if(run.getTurnId() != null && runtime.getRunningTurn(run.getTurnId()) != null)
         {
            runtime.getRunningTurn(run.getTurnId()).cancel("SCHEDULER_INTERRUPTED");
         }
         Thread.currentThread().interrupt();
         run.withStatus("FAILED").withError("Scheduled runner interrupted.");
      }
      catch(QException e)
      {
         if(e instanceof ProviderService.ProviderSetupException && blocked(e.getMessage()))
         {
            run.withStatus("BLOCKED").withError("Provider blocked by PHI/HIPAA policy.");
         }
         else
         {
            run.withStatus("FAILED").withError(run.getConversationId() == null ? truncate(e.getMessage(), 500) : "Scheduled turn failed; see conversation.");
         }
      }
      catch(RuntimeException e)
      {
         run.withStatus("FAILED").withError("Scheduled task failed.");
      }
      finally
      {
         if(attached && (run.getTurnId() == null || runtime.getRunningTurn(run.getTurnId()) == null))
         {
            runtime.getScheduledTurn().detach(run.getConversationId(), session.getUuid());
         }
         QContext.init(original);
      }
      run.setEndedAt(runtime.getClock().instant());
      if(Boolean.TRUE.equals(runtime.getConfig().getHipaaMode()))
      {
         run.setSummary("see conversation");
      }
      store.update(run);
      store.scheduledUpdate(new QRecord().withValue("id", task.getId()).withValue("lastRunAt", run.getStartedAt())
         .withValue("lastRunStatus", run.getStatus()).withValue("lastRunError", run.getError()));
      if(!"RECORD".equals(task.getOutputMode()))
      {
         alerts.raise(run);
      }
      return (run);
   }



   /*******************************************************************************
    ** Policy denials are distinct from operational failures.
    *******************************************************************************/
   private static Boolean blocked(String code)
   {
      return (code != null && (code.contains("BUDGET") || code.contains("PHI") || code.contains("HIPAA") || code.contains("KILL_SWITCH")));
   }



   /*******************************************************************************
    ** Bound stored summaries and sanitized errors.
    *******************************************************************************/
   private static String truncate(String value, Integer limit)
   {
      return (value == null || value.length() <= limit ? value : value.substring(0, limit));
   }
}
