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
import com.kingsrook.qbits.assistant.engine.AbandonedTurns;
import com.kingsrook.qbits.assistant.engine.MessageParts;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The assistantRetention process (spec section 4):
 ** - deletes conversations not active for conversationRetentionDays, with
 **   their messages, turns, events, tool calls and feedback;
 ** - nulls assistantToolCall.resultJson older than toolResultRetentionDays
 **   (the audit row stays);
 ** - finalizes abandoned runs (still RUNNING past the hard stream limit, e.g.
 **   after a pod restart nobody looked at) as interrupted;
 ** - deletes client error reports older than toolResultRetentionDays.
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
      output.addValue("turnEventsDeleted", result.turnEventsDeleted());
      output.addValue("messagesStripped", result.messagesStripped());
      output.addValue("turnsFinalized", result.turnsFinalized());
      output.addValue("clientErrorsDeleted", result.clientErrorsDeleted());
   }



   /*******************************************************************************
    ** What was purged.
    *******************************************************************************/
   public record Result(int conversationsDeleted, int toolResultsCleared, int turnEventsDeleted, int messagesStripped, int turnsFinalized, int clientErrorsDeleted)
   {
   }



   /*******************************************************************************
    ** Purge relative to `now`.
    *******************************************************************************/
   public static Result purge(AssistantQBitConfig config, AssistantStore store, Instant now) throws QException
   {
      Instant conversationCutoff = now.minus(Duration.ofDays(config.getConversationRetentionDays()));
      Instant toolResultCutoff   = now.minus(Duration.ofDays(config.getToolResultRetentionDays()));

      Integer phiDays = config.getHipaaRetentionDays() == null ? config.getConversationRetentionDays() : config.getHipaaRetentionDays();
      Instant phiCutoff = now.minus(Duration.ofDays(phiDays));
      Instant candidateCutoff = conversationCutoff.isAfter(phiCutoff) ? conversationCutoff : phiCutoff;
      Integer afterId = 0;
      int deleted = 0;
      while(true)
      {
         List<AssistantConversation> batch = store.listConversationsInactiveSince(candidateCutoff, afterId, BATCH);
         for(AssistantConversation conversation : batch)
         {
            afterId = conversation.getId();
            Instant cutoff = "PHI".equals(conversation.getMaxClassification()) ? phiCutoff : conversationCutoff;
            if(conversation.getModifyDate().isBefore(cutoff))
            {
               store.deleteConversation(conversation.getUserId(), conversation.getId());
               deleted++;
            }
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

      ///////////////////////////////////////////////////////////////////////
      // the same cutoff for record data kept elsewhere: stream events     //
      // (replay only - deleted) and render / record refs in messages      //
      ///////////////////////////////////////////////////////////////////////
      int eventsDeleted = 0;
      while(true)
      {
         int count = store.deleteTurnEventsBefore(toolResultCutoff, BATCH);
         eventsDeleted += count;
         if(count < BATCH)
         {
            break;
         }
      }

      int messagesStripped = 0;
      afterId = 0;
      while(true)
      {
         List<AssistantMessage> batch = store.listAssistantMessagesBefore(toolResultCutoff, afterId, BATCH);
         for(AssistantMessage message : batch)
         {
            afterId = message.getId();
            MessageParts parts = MessageParts.fromJson(message.getPartsJson());
            if(parts.stripRecordData())
            {
               store.updateFields(AssistantMessage.TABLE_NAME, new QRecord().withValue("id", message.getId()).withValue("partsJson", parts.toJson()));
               messagesStripped++;
            }
         }
         if(batch.size() < BATCH)
         {
            break;
         }
      }

      int turnsFinalized = AbandonedTurns.sweep(config, store, now);

      int clientErrorsDeleted = 0;
      while(true)
      {
         int count = store.deleteClientErrorsBefore(toolResultCutoff, BATCH);
         clientErrorsDeleted += count;
         if(count < BATCH)
         {
            break;
         }
      }

      LOG.info("Assistant retention ran", logPair("conversationsDeleted", deleted), logPair("toolResultsCleared", cleared),
         logPair("turnEventsDeleted", eventsDeleted), logPair("messagesStripped", messagesStripped), logPair("turnsFinalized", turnsFinalized),
         logPair("clientErrorsDeleted", clientErrorsDeleted));
      return (new Result(deleted, cleared, eventsDeleted, messagesStripped, turnsFinalized, clientErrorsDeleted));
   }

}
