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


import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantScheduledRun;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.alerts.NotificationSeverity;
import com.kingsrook.qqq.backend.core.alerts.NotificationSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The one notification a scheduled task's owner asked for (output mode
 ** NOTIFICATION or BOTH): through core's non-throwing QAlerts.notify, so it
 ** reaches whatever alerts sink the host installed (or core's logging sink).
 ** The audience is exactly the task's owner; the body has only the status and
 ** the link, never answer text.
 *******************************************************************************/
public class ScheduledAlerts
{
   private static final QLogger LOG = QLogger.getLogger(ScheduledAlerts.class);



   /*******************************************************************************
    ** Notify the run's owner. Never throws.
    *******************************************************************************/
   public void raise(AssistantScheduledRun run)
   {
      LOG.info("Scheduled assistant result", logPair("runId", run.getId()), logPair("status", run.getStatus()));
      QAlerts.notify(signal(run));
   }



   /*******************************************************************************
    ** The notification for a finished run.
    *******************************************************************************/
   static NotificationSignal signal(AssistantScheduledRun run)
   {
      String status = run.getStatus() == null ? "FINISHED" : run.getStatus();
      NotificationSignal signal = new NotificationSignal()
         .withTitle("OK".equals(status) ? "Scheduled assistant task finished" : "Scheduled assistant task did not finish")
         .withBody("Status: " + status + ". See the conversation.")
         .withSeverity("OK".equals(status) ? NotificationSeverity.SUCCESS : "FAILED".equals(status) ? NotificationSeverity.ERROR : NotificationSeverity.WARNING)
         .withCategory(NotificationCategory.JOB)
         .withSourceName("assistant")
         .withSourceKey("assistant/scheduled/run/" + run.getId())
         .withAudience(NotificationAudience.user(run.getUserId()));
      if(run.getConversationId() != null)
      {
         signal.withSourceTableName(AssistantConversation.TABLE_NAME)
            .withSourceRecordId(String.valueOf(run.getConversationId()))
            .withLink(AlertLink.toRecord(AssistantConversation.TABLE_NAME, run.getConversationId(), "Open the conversation"));
      }
      return (signal);
   }
}
