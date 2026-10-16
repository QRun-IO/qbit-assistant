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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.api.AssistantConversationCustomizer;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;


/*******************************************************************************
 ** "Archive conversations", "Restore conversations" and "Delete conversations":
 ** standard streamed-ETL record actions on assistant_conversation. The
 ** selected records come in through the user's own query (so the owner
 ** record lock decides which rows they act on), Review says what will
 ** happen, Result what happened. Same permission as the table: assistant
 ** access or assistant.admin. Table actions, never navigation entries.
 ** Delete removes a conversation with its messages, turns, events, tool
 ** calls, feedback and pending actions, and skips one whose answer is still
 ** in progress, as DELETE /conversations/{id} does.
 *******************************************************************************/
public final class ConversationActionsProcess
{
   public static final String ARCHIVE = "assistantConversationArchive";
   public static final String RESTORE = "assistantConversationRestore";
   public static final String DELETE  = "assistantConversationDelete";



   /*******************************************************************************
    ** What a process does with one selected conversation.
    *******************************************************************************/
   enum Outcome
   {
      ACT,
      SKIP,
      IN_PROGRESS
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ConversationActionsProcess()
   {
   }



   /*******************************************************************************
    ** The three processes.
    *******************************************************************************/
   public static List<QProcessMetaData> processes()
   {
      return (List.of(
         process(ARCHIVE, "Archive conversations", "archive", "The selected conversations will be archived. They leave the assistant's list; nothing is deleted."),
         process(RESTORE, "Restore conversations", "unarchive", "The selected archived conversations will return to the assistant's list."),
         process(DELETE, "Delete conversations", "delete", "The selected conversations will be deleted with their messages. This cannot be undone.")));
   }



   /*******************************************************************************
    ** One standard streamed-ETL record action on the conversation table.
    *******************************************************************************/
   private static QProcessMetaData process(String name, String label, String icon, String previewMessage)
   {
      Map<String, Serializable> values = new HashMap<>();
      values.put(StreamedETLWithFrontendProcess.FIELD_SOURCE_TABLE, AssistantConversation.TABLE_NAME);
      values.put(StreamedETLWithFrontendProcess.FIELD_DESTINATION_TABLE, AssistantConversation.TABLE_NAME);
      values.put(StreamedETLWithFrontendProcess.FIELD_SUPPORTS_FULL_VALIDATION, true);
      values.put(StreamedETLWithFrontendProcess.FIELD_DO_FULL_VALIDATION, true);
      values.put(StreamedETLWithFrontendProcess.FIELD_PREVIEW_MESSAGE, previewMessage);
      QProcessMetaData process = StreamedETLWithFrontendProcess.defineProcessMetaData(ExtractViaQueryStep.class, Review.class, Apply.class, values);
      process.setName(name);
      process.setLabel(label);
      process.setIcon(new QIcon(icon));
      process.setTableName(AssistantConversation.TABLE_NAME);
      process.setPermissionRules(AssistantMetaDataBuilder.accessRules());
      return (process);
   }



   /*******************************************************************************
    ** Classify one selected conversation for this process.
    *******************************************************************************/
   static Outcome outcome(String processName, QRecord record, AssistantRuntime runtime, AssistantApi api) throws QException
   {
      String status = record.getValueString("status");
      return (switch(processName)
      {
         case ARCHIVE -> "ARCHIVED".equals(status) ? Outcome.SKIP : Outcome.ACT;
         case RESTORE -> "ARCHIVED".equals(status) ? Outcome.ACT : Outcome.SKIP;
         default -> AssistantConversationCustomizer.hasAnswerInProgress(runtime, api, record.getValueInteger("id")) ? Outcome.IN_PROGRESS : Outcome.ACT;
      });
   }



   /*******************************************************************************
    ** Summary lines, worded for the process (future on Review, past on Result).
    *******************************************************************************/
   static Map<Outcome, ProcessSummaryLine> lines(String processName)
   {
      Map<Outcome, ProcessSummaryLine> lines = new EnumMap<>(Outcome.class);
      switch(processName)
      {
         case ARCHIVE ->
         {
            lines.put(Outcome.ACT, DiscoverModelsProcess.line(Status.OK, "conversation will be archived", "conversations will be archived",
               "conversation was archived", "conversations were archived"));
            lines.put(Outcome.SKIP, DiscoverModelsProcess.line(Status.INFO, "conversation is already archived", "conversations are already archived",
               "conversation was already archived", "conversations were already archived"));
         }
         case RESTORE ->
         {
            lines.put(Outcome.ACT, DiscoverModelsProcess.line(Status.OK, "conversation will be restored", "conversations will be restored",
               "conversation was restored", "conversations were restored"));
            lines.put(Outcome.SKIP, DiscoverModelsProcess.line(Status.INFO, "conversation is not archived", "conversations are not archived",
               "conversation was not archived", "conversations were not archived"));
         }
         default ->
         {
            lines.put(Outcome.ACT, DiscoverModelsProcess.line(Status.OK, "conversation will be deleted with its messages", "conversations will be deleted with their messages",
               "conversation was deleted", "conversations were deleted"));
            lines.put(Outcome.IN_PROGRESS, DiscoverModelsProcess.line(Status.ERROR, "conversation has an answer in progress and will not be deleted",
               "conversations have an answer in progress and will not be deleted", "conversation was not deleted: an answer was in progress",
               "conversations were not deleted: an answer was in progress"));
         }
      }
      return (lines);
   }



   /*******************************************************************************
    ** The lines that have a count, in a fixed order.
    *******************************************************************************/
   static ArrayList<ProcessSummaryLineInterface> summary(Map<Outcome, ProcessSummaryLine> lines, String nothing)
   {
      ArrayList<ProcessSummaryLineInterface> rs = new ArrayList<>();
      for(ProcessSummaryLine line : lines.values())
      {
         line.addSelfToListIfAnyCount(rs);
      }
      if(rs.isEmpty())
      {
         rs.add(new ProcessSummaryLine(Status.INFO, null, nothing));
      }
      return (rs);
   }



   /*******************************************************************************
    ** The running assistant (or a clear error).
    *******************************************************************************/
   private static AssistantRuntime runtime() throws QException
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      if(runtime == null)
      {
         throw (new QException("The assistant is not running on this server."));
      }
      return (runtime);
   }



   /*******************************************************************************
    ** Review: what will happen to each selected conversation. Changes nothing.
    *******************************************************************************/
   public static class Review extends AbstractTransformStep implements ProcessSummaryProviderInterface
   {
      private Map<Outcome, ProcessSummaryLine> lines;



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AssistantRuntime runtime = runtime();
         AssistantApi     api     = new AssistantApi(runtime, QContext.getQSession());
         if(lines == null)
         {
            lines = lines(input.getProcessName());
         }
         for(QRecord record : input.getRecords())
         {
            lines.get(outcome(input.getProcessName(), record, runtime, api)).incrementCountAndAddPrimaryKey(record.getValue("id"));
            output.getRecords().add(record);
         }
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         return (summary(lines == null ? Map.of() : lines, "No conversations were selected."));
      }
   }



   /*******************************************************************************
    ** Result: archive, restore or delete each conversation (checked again), as
    ** the user, through the assistant's store.
    *******************************************************************************/
   public static class Apply extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      private Map<Outcome, ProcessSummaryLine> lines;



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AssistantRuntime runtime = runtime();
         AssistantApi     api     = new AssistantApi(runtime, QContext.getQSession());
         if(lines == null)
         {
            lines = lines(input.getProcessName());
         }
         for(QRecord record : input.getRecords())
         {
            Integer id      = record.getValueInteger("id");
            Outcome outcome = outcome(input.getProcessName(), record, runtime, api);
            if(outcome == Outcome.ACT)
            {
               switch(input.getProcessName())
               {
                  case ARCHIVE -> runtime.getStore().updateFields(AssistantConversation.TABLE_NAME, new QRecord().withValue("id", id).withValue("status", "ARCHIVED"));
                  case RESTORE -> runtime.getStore().updateFields(AssistantConversation.TABLE_NAME, new QRecord().withValue("id", id).withValue("status", "ACTIVE"));
                  default -> runtime.getStore().deleteConversation(record.getValueString("userId"), id);
               }
            }
            lines.get(outcome).incrementCountAndAddPrimaryKey(id);
            output.getRecords().add(record);
         }
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         return (summary(lines == null ? Map.of() : lines, "No conversations were changed."));
      }
   }
}
