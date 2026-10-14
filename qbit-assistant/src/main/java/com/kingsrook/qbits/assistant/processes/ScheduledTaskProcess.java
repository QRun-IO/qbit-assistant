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

package com.kingsrook.qbits.assistant.processes;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantScheduledTask;
import com.kingsrook.qbits.assistant.scheduled.ScheduledTaskRunner;
import com.kingsrook.qbits.assistant.scheduled.ScheduledTasks;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.QScheduleMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;


/*******************************************************************************
 ** Admin record actions and the scheduler's bookkeeping step. No nav processes.
 *******************************************************************************/
public final class ScheduledTaskProcess
{
   public static final String RUNNER = "assistantScheduledTasksRunner";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ScheduledTaskProcess()
   {
   }



   /*******************************************************************************
    ** QQQ supplies the scheduler session. No session is created here.
    *******************************************************************************/
   public static List<QProcessMetaData> processes(AssistantQBitConfig config)
   {
      List<QProcessMetaData> result = new ArrayList<>();
      QProcessMetaData runner = new QProcessMetaData().withName(RUNNER).withLabel("Run due assistant tasks")
         .withPermissionRules(AssistantMetaDataBuilder.adminRules())
         .withStep(new QBackendStepMetaData().withName("runDue").withCode(new QCodeReference(Runner.class)));
      if(config.getScheduledTasksSchedulerName() != null && !config.getScheduledTasksSchedulerName().isBlank())
      {
         runner.withSchedule(new QScheduleMetaData().withSchedulerName(config.getScheduledTasksSchedulerName()).withRepeatSeconds(60));
      }
      result.add(runner);
      for(String action : List.of("Approve", "Revoke", "RunNow"))
      {
         String label = "Approve".equals(action) ? "Approve write mode" : "Revoke".equals(action) ? "Revoke write mode" : "Run now";
         Map<String, Serializable> values = new HashMap<>();
         values.put(StreamedETLWithFrontendProcess.FIELD_SOURCE_TABLE, AssistantScheduledTask.TABLE_NAME);
         values.put(StreamedETLWithFrontendProcess.FIELD_DESTINATION_TABLE, AssistantScheduledTask.TABLE_NAME);
         values.put(StreamedETLWithFrontendProcess.FIELD_SUPPORTS_FULL_VALIDATION, true);
         values.put(StreamedETLWithFrontendProcess.FIELD_DO_FULL_VALIDATION, true);
         values.put(StreamedETLWithFrontendProcess.FIELD_PREVIEW_MESSAGE, label + " for the selected tasks.");
         values.put("scheduledAction", action);
         QProcessMetaData process = StreamedETLWithFrontendProcess.defineProcessMetaData(ExtractViaQueryStep.class, Review.class, Result.class, values);
         process.setName("assistantScheduledTask" + action);
         process.setLabel(label);
         process.setIcon(new QIcon("schedule"));
         process.setTableName(AssistantScheduledTask.TABLE_NAME);
         process.setPermissionRules(AssistantMetaDataBuilder.adminRules());
         result.add(process);
      }
      return (result);
   }



   /*******************************************************************************
    ** Actions must enforce permission even when called outside the UI executor.
    *******************************************************************************/
   private static AssistantRuntime admin() throws QException
   {
      if(QContext.getQSession() == null || !QContext.getQSession().hasPermission(AssistantQBitConfig.ADMIN_PERMISSION))
      {
         throw (new QException("assistant.admin is required for scheduled task actions."));
      }
      return (AssistantRuntime.of(QContext.getQInstance()));
   }



   /*******************************************************************************
    ** Background scheduler step uses only the context supplied by QQQ.
    *******************************************************************************/
   public static class Runner implements BackendStep
   {
      /*******************************************************************************
       ** Dispatch the due page once.
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         output.addValue("runs", new ScheduledTaskRunner(AssistantRuntime.of(QContext.getQInstance())).runDue());
      }
   }



   /*******************************************************************************
    ** Review binds the displayed prompt hash and user to the exact settings.
    *******************************************************************************/
   public static class Review extends AbstractTransformStep implements ProcessSummaryProviderInterface
   {
      private final ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();



      /*******************************************************************************
       ** No login or model call on Review.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         admin();
         for(QRecord record : input.getRecords())
         {
            AssistantScheduledTask task = new AssistantScheduledTask(record);
            record.setValue("reviewedHash", ScheduledTasks.approvalHash(task));
            lines.add(TestConnectionProcess.single(Status.INFO, "Run as " + task.getRunAsUserId() + "; prompt SHA-256 " + ScheduledTasks.hash(task.getPrompt())
               + ". Write mode permits policy-allowed create, update and delete proposals; writes wait for this user's panel confirmation.", task.getId()));
            output.getRecords().add(record);
         }
      }



      /*******************************************************************************
       ** Standard streamed ETL Review lines.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         return (lines);
      }
   }



   /*******************************************************************************
    ** Result rereads tasks; a change since Review requires a fresh review.
    *******************************************************************************/
   public static class Result extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      private final ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();



      /*******************************************************************************
       ** Approval records the actual administrator's session user id.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AssistantRuntime runtime = admin();
         String action = input.getValueString("scheduledAction");
         for(QRecord record : input.getRecords())
         {
            AssistantScheduledTask task = runtime.getStore().scheduledTask(record.getValueInteger("id"));
            if(task == null || !ScheduledTasks.approvalHash(task).equals(record.getValueString("reviewedHash")))
            {
               throw (new QException("The task changed after Review. Review it again."));
            }
            String summary;
            if("RunNow".equals(action))
            {
               var run = new ScheduledTaskRunner(runtime).run(task, true);
               summary = run == null ? "Task already claimed." : "Run " + run.getId() + ": " + run.getStatus();
            }
            else if("Approve".equals(action) || "Revoke".equals(action))
            {
               Boolean approve = "Approve".equals(action);
               runtime.getStore().scheduledUpdate(new QRecord().withValue("id", task.getId())
                  .withValue("writeModeApprovedBy", approve ? AssistantSecurity.userId(QContext.getQSession()) : null)
                  .withValue("writeModeApprovedAt", approve ? runtime.getClock().instant() : null)
                  .withValue("writeModeApprovalHash", approve ? ScheduledTasks.approvalHash(task) : null));
               summary = approve ? "Write mode approved." : "Write mode revoked.";
            }
            else
            {
               throw (new QException("Unknown scheduled task action."));
            }
            lines.add(TestConnectionProcess.single(Status.INFO, summary, task.getId()));
            output.getRecords().add(record);
         }
      }



      /*******************************************************************************
       ** Standard streamed ETL Result lines.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         return (lines);
      }
   }
}
