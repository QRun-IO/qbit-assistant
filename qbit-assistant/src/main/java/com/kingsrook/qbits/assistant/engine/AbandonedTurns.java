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


import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Terminal state for runs whose engine is gone (a pod restart, a crash, a
 ** finalizer that could not write): the turn row, its assistant message and a
 ** terminal replay (error + message.end) are written, so no reader ever sees
 ** an answer "in progress" forever and the "answer already in progress"
 ** lockout cannot persist.
 **
 ** Used by the API when it reads an orphaned run (active turns, events,
 ** conversation detail, the next question) and by the retention process,
 ** which sweeps every run older than the hard stream limit. Only fixed
 ** notices are appended to already stored (redacted) parts; nothing is
 ** reloaded from the model, tools or the user's text.
 *******************************************************************************/
public final class AbandonedTurns
{
   private static final QLogger LOG = QLogger.getLogger(AbandonedTurns.class);

   public static final String INTERRUPTED_TEXT = "This answer was interrupted (the server restarted). Ask again.";
   public static final String UNFINISHED_TEXT  = "This answer did not finish. Ask again.";

   private static final int BATCH = 200;



   /*******************************************************************************
    ** not instantiable
    *******************************************************************************/
   private AbandonedTurns()
   {
   }



   /*******************************************************************************
    ** Finalize one abandoned run as ERROR (no cancel reason: interrupted) or
    ** CANCELLED (a stop nobody acknowledged). Each step is independent; the
    ** turn row is written last and only while it still says it runs.
    *******************************************************************************/
   public static void finalizeRun(AssistantStore store, Instant now, String userId, AssistantTurn current, String cancelReason) throws QException
   {
      String status = cancelReason == null ? "ERROR" : "CANCELLED";
      String text = cancelReason == null ? INTERRUPTED_TEXT
         : ("SUPERSEDED".equals(cancelReason) ? TurnEngine.SUPERSEDED_TEXT : "Stopped by you.");
      if(current.getAssistantMessageId() != null)
      {
         try
         {
            AssistantMessage message = store.getMessage(userId, current.getAssistantMessageId());
            if(message != null)
            {
               store.updateFields(AssistantMessage.TABLE_NAME, new QRecord().withValue("id", message.getId()).withValue("status", status)
                  .withValue("partsJson", MessageParts.fromJson(message.getPartsJson()).addNotice(cancelReason == null ? "error" : "info", text).toJson()));
            }
         }
         catch(Exception e)
         {
            LOG.warn("Could not persist recovered assistant message", logPair("turnId", current.getId()));
         }
      }
      try
      {
         int seq = store.lastEventSeq(userId, current.getId());
         List<AssistantTurnEvent> last = store.listEvents(userId, current.getId(), Math.max(0, seq - 1), 1);
         if(last.isEmpty() || !TurnEvent.MESSAGE_END.equals(last.getFirst().getEventType()))
         {
            if(cancelReason == null)
            {
               seq++;
               store.insertEvent(current.getId(), userId, seq, TurnEvent.ERROR,
                  new TurnEvent(seq, TurnEvent.ERROR, Map.of("code", "INTERNAL", "message", text)).toJson());
            }
            seq++;
            Map<String, Object> end = new LinkedHashMap<>();
            end.put("messageId", current.getAssistantMessageId());
            end.put("status", status);
            end.put("stopReason", cancelReason == null ? "interrupted" : "cancelled");
            store.insertEvent(current.getId(), userId, seq, TurnEvent.MESSAGE_END, new TurnEvent(seq, TurnEvent.MESSAGE_END, end).toJson());
         }
      }
      catch(Exception e)
      {
         LOG.warn("Could not persist recovered assistant events", logPair("turnId", current.getId()));
      }
      store.updateRunningTurn(userId, new QRecord().withValue("id", current.getId()).withValue("status", status)
         .withValue("errorCode", cancelReason == null ? "INTERNAL" : null).withValue("cancelReason", cancelReason)
         .withValue("stopReason", cancelReason == null ? "interrupted" : "cancelled").withValue("phase", "done").withValue("endedAt", now));
   }



   /*******************************************************************************
    ** An assistant message still STREAMING whose run is over (or gone): give it
    ** the run's terminal status (ERROR when the run is unknown) and, unless the
    ** run completed, an error notice. Returns the repaired message (or the
    ** given one when nothing changed or the repair could not be written).
    *******************************************************************************/
   public static AssistantMessage repairMessage(AssistantStore store, String userId, AssistantMessage message, AssistantTurn turn)
   {
      if(message == null || !"STREAMING".equals(message.getStatus()))
      {
         return (message);
      }
      String status = turn == null || turn.getStatus() == null || "RUNNING".equals(turn.getStatus()) || "CANCELLING".equals(turn.getStatus()) ? "ERROR" : turn.getStatus();
      try
      {
         MessageParts parts = MessageParts.fromJson(message.getPartsJson());
         if(!"COMPLETE".equals(status))
         {
            parts.addNotice("ERROR".equals(status) ? "error" : "info", "ERROR".equals(status) ? UNFINISHED_TEXT : "Stopped.");
         }
         String partsJson = parts.toJson();
         store.updateFields(AssistantMessage.TABLE_NAME, new QRecord().withValue("id", message.getId()).withValue("status", status).withValue("partsJson", partsJson));
         message.setStatus(status);
         message.setPartsJson(partsJson);
         LOG.info("Repaired an unfinished assistant message", logPair("messageId", message.getId()), logPair("turnId", message.getTurnId()), logPair("status", status));
      }
      catch(Exception e)
      {
         LOG.warn("Could not repair an unfinished assistant message", logPair("messageId", message.getId()), logPair("turnId", message.getTurnId()));
      }
      return (message);
   }



   /*******************************************************************************
    ** Retention sweep: every run (of any user the running session can see)
    ** still RUNNING or CANCELLING although it started before the hard stream
    ** limit (maxTurnSeconds + force-finalize grace + lease) is finalized as
    ** interrupted. Returns how many.
    *******************************************************************************/
   public static int sweep(AssistantQBitConfig config, AssistantStore store, Instant now) throws QException
   {
      Instant cutoff = now.minusSeconds(config.getMaxTurnSeconds() + (long) config.getTurnForceFinalizeGraceSeconds() + config.getTurnLeaseSeconds());
      int     count  = 0;
      Integer afterId = 0;
      while(true)
      {
         List<AssistantTurn> batch = store.listRunningTurnsStartedBefore(cutoff, afterId, BATCH);
         for(AssistantTurn turn : batch)
         {
            afterId = turn.getId();
            try
            {
               finalizeRun(store, now, turn.getUserId(), turn, null);
               count++;
            }
            catch(Exception e)
            {
               LOG.warn("Could not finalize an abandoned assistant turn", logPair("turnId", turn.getId()));
            }
         }
         if(batch.size() < BATCH)
         {
            break;
         }
      }
      return (count);
   }
}
