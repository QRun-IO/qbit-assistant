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

package com.kingsrook.qbits.assistant.processes;


import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The assistantRetention process (spec section 4):
 ** - deletes conversations not active for conversationRetentionDays, with
 **   their messages, turns, events, tool calls and feedback;
 ** - nulls assistantToolCall.resultJson older than toolResultRetentionDays
 **   (the audit row stays).
 ** It works on the rows the running session can see: run on a schedule (the
 ** scheduler's session) or by an admin holding the owner all-access key.
 *******************************************************************************/
public class AssistantRetentionStep implements BackendStep
{
   private static final QLogger LOG   = QLogger.getLogger(AssistantRetentionStep.class);
   private static final int     BATCH = 200;



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      if(runtime == null)
      {
         throw (new QException("The assistant QBit is not configured in this instance."));
      }
      Result result = purge(runtime.getConfig(), runtime.getStore(), runtime.getClock().instant());
      output.addValue("conversationsDeleted", result.conversationsDeleted());
      output.addValue("toolResultsCleared", result.toolResultsCleared());
   }



   /*******************************************************************************
    ** What was purged.
    *******************************************************************************/
   public record Result(int conversationsDeleted, int toolResultsCleared)
   {
   }



   /*******************************************************************************
    ** Purge relative to `now`.
    *******************************************************************************/
   public static Result purge(AssistantQBitConfig config, AssistantStore store, Instant now) throws QException
   {
      Instant conversationCutoff = now.minus(Duration.ofDays(config.getConversationRetentionDays()));
      Instant toolResultCutoff   = now.minus(Duration.ofDays(config.getToolResultRetentionDays()));

      int deleted = 0;
      while(true)
      {
         List<AssistantConversation> batch = store.listConversationsInactiveSince(conversationCutoff, BATCH);
         for(AssistantConversation conversation : batch)
         {
            store.deleteConversation(conversation.getUserId(), conversation.getId());
            deleted++;
         }
         if(batch.size() < BATCH)
         {
            break;
         }
      }

      int cleared = 0;
      while(true)
      {
         int count = store.clearToolResultsBefore(toolResultCutoff, BATCH);
         cleared += count;
         if(count < BATCH)
         {
            break;
         }
      }

      LOG.info("Assistant retention ran", logPair("conversationsDeleted", deleted), logPair("toolResultsCleared", cleared));
      return (new Result(deleted, cleared));
   }

}
