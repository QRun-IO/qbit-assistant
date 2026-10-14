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


import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.engine.BudgetService;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.model.AssistantScheduledTask;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;


/*******************************************************************************
 ** Server-owned per-conversation execution settings. They never come from a
 ** panel request or prompt. Scoped to one runtime and removed after completion.
 *******************************************************************************/
public class ScheduledTurn
{
   private final Map<Integer, AssistantScheduledTask> tasks = new ConcurrentHashMap<>();
   private final Map<String, String> channels = new ConcurrentHashMap<>();



   /*******************************************************************************
    ** Attach a snapshot before the unchanged API starts its normal turn.
    *******************************************************************************/
   public void attach(Integer conversationId, String uuid, AssistantScheduledTask task) throws QException
   {
      if(channels.putIfAbsent(uuid, "assistant/scheduled/" + task.getId()) != null)
      {
         throw (new QException("This login session already has a scheduled task in progress."));
      }
      tasks.put(conversationId, task);
   }



   /*******************************************************************************
    ** Remove references to the completed run's context.
    *******************************************************************************/
   public void detach(Integer conversationId, String uuid)
   {
      if(conversationId != null)
      {
         AssistantScheduledTask task = tasks.remove(conversationId);
         if(task != null)
         {
            channels.remove(uuid, "assistant/scheduled/" + task.getId());
         }
      }
   }



   /*******************************************************************************
    ** Snapshot of task settings for this conversation.
    *******************************************************************************/
   public AssistantScheduledTask task(Integer conversationId)
   {
      return (tasks.get(conversationId));
   }



   /*******************************************************************************
    ** Client attribution also covers metadata and knowledge retrieval calls.
    *******************************************************************************/
   public String channel(String uuid)
   {
      return (uuid == null ? null : channels.get(uuid));
   }



   /*******************************************************************************
    ** Narrow the normal policy. Scheduled writes always require confirmation.
    *******************************************************************************/
   public static PolicyService.ToolPolicy tools(AssistantRuntime runtime, AssistantScheduledTask task, PolicyService.ToolPolicy policy) throws QException
   {
      boolean write = approved(task) && policy.writeModeAvailableTo(QContext.getQSession());
      return (new PolicyService.ToolPolicy(true, policy.allowedTools(), policy.maxRowsPerCall(), policy.redactPii(),
         policy.writeModeAllowed(), policy.writeModePermission(), policy.autoConfirmAllowed(), write, false));
   }



   /*******************************************************************************
    ** Approval failure closes write mode.
    *******************************************************************************/
   private static Boolean approved(AssistantScheduledTask task)
   {
      try
      {
         return (ScheduledTasks.writesApproved(task));
      }
      catch(QException e)
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** Extra STOP cap at dispatch boundaries; a completed call can cross it.
    *******************************************************************************/
   public static void checkCost(AssistantScheduledTask task, Long costMicros) throws BudgetService.Blocked
   {
      if(task != null && task.getMaxCostUsdPerRun() != null
         && BigDecimal.valueOf(costMicros, 6).compareTo(task.getMaxCostUsdPerRun()) >= 0)
      {
         throw (new BudgetService.Blocked("The scheduled task reached its per-run cost cap."));
      }
   }



   /*******************************************************************************
    ** Include chat, classifier, failed attempts and knowledge-query embeddings.
    *******************************************************************************/
   public static Long cost(AssistantRuntime runtime, Integer turnId) throws QException
   {
      return (runtime.getStore().query(AssistantUsage.TABLE_NAME,
         new QQueryFilter().withCriteria("turnId",
            QCriteriaOperator.EQUALS, turnId),
         AssistantUsage.class).stream().mapToLong(row -> row.getCostMicros() == null ? 0 : row.getCostMicros()).sum());
   }



   /*******************************************************************************
    ** Check persisted accounting before each embedding and model dispatch.
    *******************************************************************************/
   public static void checkCost(AssistantRuntime runtime, Integer conversationId, Integer turnId) throws QException
   {
      AssistantScheduledTask task = conversationId == null ? null : runtime.getScheduledTurn().task(conversationId);
      if(task != null)
      {
         checkCost(task, cost(runtime, turnId));
      }
   }
}
