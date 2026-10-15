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

package com.kingsrook.qbits.assistant.jobs;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.jobs.model.JobStatus;
import com.kingsrook.qqq.backend.core.jobs.progress.JobPhase;
import com.kingsrook.qqq.backend.core.jobs.progress.JobProgress;
import com.kingsrook.qqq.backend.core.jobs.progress.JobTracking;
import com.kingsrook.qqq.backend.core.jobs.progress.TrackedJobRequest;
import com.kingsrook.qqq.backend.core.jobs.runtime.DurableJobsRuntime;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;


/*******************************************************************************
 ** Publishes each assistant answer to the Jobs tray as a QQQ tracking job
 ** (core JobTracking, qqq#1210): processName "assistant.turn", phases
 ** thinking / tool / writing, the tool's label as the message, and the tray's
 ** Stop wired to the turn's own Stop. Never prompt or answer text. When the
 ** durable jobs runtime is not running on this pod, answers are simply not
 ** tracked.
 *******************************************************************************/
public class JobTrackingRunTracker implements AssistantRunTracker
{
   public static final String PROCESS_NAME = "assistant.turn";
   public static final String LABEL        = "Assistant answer";

   private final Map<String, JobProgress> reporters = new ConcurrentHashMap<>();



   /*******************************************************************************
    ** Start a tracking job linked to the conversation, or null when the jobs
    ** runtime is not running here.
    *******************************************************************************/
   @Override
   public String start(String userId, Integer turnId, Integer conversationId, String label) throws QException
   {
      QInstance qInstance = QContext.getQInstance();
      if(qInstance == null || DurableJobsRuntime.get(qInstance) == null)
      {
         return (null);
      }

      Integer jobId = JobTracking.start(new TrackedJobRequest()
         .withUserId(userId)
         .withLabel(LABEL)
         .withProcessName(PROCESS_NAME)
         .withTableName(AssistantConversation.TABLE_NAME)
         .withRecordId(conversationId));
      String trackingId = String.valueOf(jobId);
      reporters.put(trackingId, JobTracking.progress(jobId).phases(
         JobPhase.of("thinking", "Thinking", 1),
         JobPhase.of("tool", "Using a tool", 1),
         JobPhase.of("writing", "Writing", 1)));
      return (trackingId);
   }



   /*******************************************************************************
    ** The current phase, with the tool's label as the message.
    *******************************************************************************/
   @Override
   public void progress(String trackingId, String phase, String message)
   {
      JobProgress reporter = trackingId == null ? null : reporters.get(trackingId);
      if(reporter == null || !("thinking".equals(phase) || "tool".equals(phase) || "writing".equals(phase)))
      {
         return;
      }
      reporter.phase(phase);
      if("tool".equals(phase) && message != null)
      {
         reporter.message(message);
         reporter.flush();
      }
   }



   /*******************************************************************************
    ** COMPLETE -> COMPLETED, CANCELLED -> CANCELLED, anything else -> FAILED.
    *******************************************************************************/
   @Override
   public void finish(String trackingId, String status, String message) throws QException
   {
      if(trackingId == null)
      {
         return;
      }
      JobProgress reporter = reporters.remove(trackingId);
      if(reporter != null)
      {
         reporter.flush();
      }
      JobTracking.finish(Integer.valueOf(trackingId), jobStatus(status), finishMessage(status));
   }



   /*******************************************************************************
    ** The tray's Stop runs on this pod and stops the turn; the turn's own
    ** finaliser then finishes the job as CANCELLED.
    *******************************************************************************/
   @Override
   public void onCancel(String trackingId, Runnable cancel) throws QException
   {
      if(trackingId != null)
      {
         JobTracking.onCancel(Integer.valueOf(trackingId), cancel);
      }
   }



   /*******************************************************************************
    ** The jobs status for an assistant turn status.
    *******************************************************************************/
   static JobStatus jobStatus(String status)
   {
      if("COMPLETE".equals(status))
      {
         return (JobStatus.COMPLETED);
      }
      if("CANCELLED".equals(status))
      {
         return (JobStatus.CANCELLED);
      }
      return (JobStatus.FAILED);
   }



   /*******************************************************************************
    ** A fixed message (never the answer, the prompt or a provider error).
    *******************************************************************************/
   static String finishMessage(String status)
   {
      if("COMPLETE".equals(status))
      {
         return ("Answered.");
      }
      if("CANCELLED".equals(status))
      {
         return ("Stopped.");
      }
      return ("The answer did not finish.");
   }

}
