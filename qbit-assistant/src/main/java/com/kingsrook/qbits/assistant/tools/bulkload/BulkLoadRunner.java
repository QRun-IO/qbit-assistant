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

package com.kingsrook.qbits.assistant.tools.bulkload;


import com.kingsrook.qbits.assistant.uiapi.UiApiGateway;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;


/*******************************************************************************
 ** How bulk_load_run starts a load (design spec #1171 section 3.4): one seam,
 ** so the run can move from today's path to durable jobs without the tool
 ** changing. Every implementation goes through the /qqq/v1 UI API as the user.
 *******************************************************************************/
public interface BulkLoadRunner
{
   /*******************************************************************************
    ** A load to start: the target table, the upload, and the saved profile.
    *******************************************************************************/
   record Request(String sessionUUID, String userTimezone, UiApiGateway gateway, String tableName, String uploadRef, Integer savedBulkLoadProfileId, UiApiCredentials credentials, String turnId)
   {
      /*******************************************************************************
       ** Without request credentials or a turn.
       *******************************************************************************/
      public Request(String sessionUUID, String userTimezone, UiApiGateway gateway, String tableName, String uploadRef, Integer savedBulkLoadProfileId)
      {
         this(sessionUUID, userTimezone, gateway, tableName, uploadRef, savedBulkLoadProfileId, null, null);
      }
   }



   /*******************************************************************************
    ** What started: status uses the durable job status names (jobs_job.status:
    ** AWAITING_CONFIRMATION, QUEUED, RUNNING, COMPLETED,
    ** COMPLETED_WITH_ERRORS, FAILED, ...); jobId when it runs as a job,
    ** processUUID when it ran synchronously; a short summary; the HTTP status.
    *******************************************************************************/
   record RunHandle(String status, Long jobId, String processUUID, String summary, String operation, Integer httpStatus)
   {
   }



   /*******************************************************************************
    ** Start the load. Throws when it cannot start at all (the handle's status
    ** reports a load that started and failed).
    *******************************************************************************/
   RunHandle start(Request request) throws QException;
}
