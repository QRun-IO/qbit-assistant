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


/*******************************************************************************
 ** STAND-IN(core=QRun-IO/qqq#1210, removeBy=RC.9): core job tracking
 ** (jobs_job tracking rows) will implement this contract; hosts may wire their own.
 *******************************************************************************/
public interface AssistantRunTracker
{
   AssistantRunTracker NOOP = new AssistantRunTracker()
   {
      /*******************************************************************************
       ** No tracking is configured.
       *******************************************************************************/
      @Override
      public String start(String userId, Integer turnId, Integer conversationId, String label)
      {
         return (null);
      }

      /*******************************************************************************
       ** No tracking is configured.
       *******************************************************************************/
      @Override
      public void progress(String trackingId, String phase, String message)
      {
      }

      /*******************************************************************************
       ** No tracking is configured.
       *******************************************************************************/
      @Override
      public void finish(String trackingId, String status, String message)
      {
      }
   };

   /*******************************************************************************
    ** Publish a run and return its tracking id.
    *******************************************************************************/
   String start(String userId, Integer turnId, Integer conversationId, String label);

   /*******************************************************************************
    ** Publish a changed phase.
    *******************************************************************************/
   void progress(String trackingId, String phase, String message);

   /*******************************************************************************
    ** Publish the terminal outcome.
    *******************************************************************************/
   void finish(String trackingId, String status, String message);

   /*******************************************************************************
    ** Wire the host's Stop action to the owner's cancellation path.
    *******************************************************************************/
   default void onCancel(String trackingId, Runnable cancel)
   {
   }
}
