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


import com.kingsrook.qbits.assistant.model.AssistantScheduledRun;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Optional Alerts notification SPI bridge. The audience is exactly one user;
 ** notification bodies contain only status and a link, never answer text.
 *******************************************************************************/
public class ScheduledAlerts
{
   private static final QLogger LOG = QLogger.getLogger(ScheduledAlerts.class);



   /*******************************************************************************
    ** Reflection keeps the optional notification implementation out of the ABI.
    *******************************************************************************/
   public void raise(AssistantScheduledRun run)
   {
      LOG.info("Scheduled assistant result", logPair("runId", run.getId()), logPair("status", run.getStatus()));
      try
      {
         String prefix = "com.kingsrook.qbits.alerts.spi.";
         Class<?> type = Class.forName(prefix + "NotificationSignal");
         Object signal = type.getConstructor(String.class).newInstance("Scheduled assistant task finished");
         type.getMethod("withSourceName", String.class).invoke(signal, "assistant");
         type.getMethod("withSourceKey", String.class).invoke(signal, "assistant/scheduled/run/" + run.getId());
         type.getMethod("withBody", String.class).invoke(signal, "Status: " + run.getStatus() + ". See conversation.");
         Class<?> audienceType = Class.forName(prefix + "NotificationAudience");
         Object audience = audienceType.getMethod("user", String.class).invoke(null, run.getUserId());
         type.getMethod("withAudience", audienceType).invoke(signal, audience);
         if(run.getConversationId() != null)
         {
            Class<?> linkType = Class.forName(prefix + "AlertLink");
            Object link = linkType.getMethod("toPath", String.class, String.class).invoke(null,
               "/app/assistantAdmin/assistant_conversation/" + run.getConversationId(), "See conversation");
            type.getMethod("withLink", linkType).invoke(signal, link);
         }
         Object runtime = Class.forName("com.kingsrook.qbits.alerts.AlertsRuntime").getMethod("get").invoke(null);
         Object sink = runtime.getClass().getMethod("sink").invoke(runtime);
         sink.getClass().getMethod("notify", type).invoke(sink, signal);
      }
      catch(Exception | LinkageError e)
      {
         LOG.info("Scheduled assistant notification unavailable", logPair("runId", run.getId()));
      }
   }
}
