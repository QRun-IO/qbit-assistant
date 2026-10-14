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


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** The default {@link BulkLoadRunner}: the durable jobs launch,
 ** POST /qqq/v1/processes/{table}.bulkInsert/jobs with the upload ref and the
 ** saved profile (design spec #1171 sections 2.4 and 3.4). From the assistant
 ** channel core creates the job AWAITING_CONFIRMATION, so it starts only when
 ** the user confirms it outside the assistant.
 **
 ** STAND-IN(core=#1178, removeBy=RC.8): until the jobs kernel ships in a QQQ
 ** RC, the launch is not a spec'd operation on the server; the runner then
 ** says so instead of guessing at another path (a load needs the upload ref,
 ** which the bulk-insert process endpoints cannot take).
 *******************************************************************************/
public class JobsLaunchBulkLoadRunner implements BulkLoadRunner
{
   public static final String LAUNCH_OPERATION_PATH = "/processes/{processName}/jobs";

   public static final String NOT_AVAILABLE = "Running a bulk load from the assistant needs the durable jobs launch "
      + "(POST /qqq/v1/processes/{process}/jobs), which this server does not have yet. Open the table's Bulk Load "
      + "screen and choose the saved profile instead.";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public RunHandle start(Request request) throws QException
   {
      String processName = request.tableName() + ".bulkInsert";
      if(!request.gateway().isInvocable("POST", LAUNCH_OPERATION_PATH))
      {
         return (new RunHandle("NOT_AVAILABLE", null, null, NOT_AVAILABLE, "POST " + LAUNCH_OPERATION_PATH, null));
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("uploadRefs", List.of(request.uploadRef()));
      body.put("savedBulkLoadProfileId", request.savedBulkLoadProfileId());
      body.put("mode", "EXECUTE");
      body.put("label", "Bulk load into " + request.tableName() + " (assistant)");

      UiApiResult response = request.gateway().invoke(new UiApiCall("POST", LAUNCH_OPERATION_PATH)
         .withPathParam("processName", processName)
         .withJsonBody(body)
         .withSessionUUID(request.sessionUUID())
         .withCredentials(request.credentials())
         .withClientDetail(request.turnId())
         .withUserTimezone(request.userTimezone())
         .withInvokedBy("assistant"));

      if(!response.isSuccess())
      {
         return (new RunHandle("FAILED", null, null, response.errorMessage() == null ? "HTTP " + response.status() : response.errorMessage(),
            "POST " + LAUNCH_OPERATION_PATH, response.status()));
      }

      Map<String, Object> json   = response.json();
      Long                jobId  = ValueUtils.getValueAsLong(json.get("jobId"));
      String              status = json.get("status") == null ? "QUEUED" : String.valueOf(json.get("status"));
      String summary = "AWAITING_CONFIRMATION".equals(status)
         ? "Job " + jobId + " is waiting for your confirmation on its job record before it starts."
         : "Job " + jobId + " is " + status.toLowerCase() + ".";
      return (new RunHandle(status, jobId, null, summary, "POST " + LAUNCH_OPERATION_PATH, response.status()));
   }
}
