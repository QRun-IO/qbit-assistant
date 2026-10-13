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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.engine.ConnectionTest;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
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
 ** Test connection: a record action on assistant_provider. A standard
 ** streamed ETL: Review says what will be called (and what is wrong with a
 ** row before any call); Result has one line per provider with the status,
 ** latency and model reached, or the error. The result is also saved on the
 ** provider row and the call is recorded in assistant_usage (purpose TEST).
 *******************************************************************************/
public final class TestConnectionProcess
{
   public static final String NAME = "assistantProviderTestConnection";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private TestConnectionProcess()
   {
   }



   /*******************************************************************************
    ** The process.
    *******************************************************************************/
   public static QProcessMetaData process()
   {
      Map<String, Serializable> values = new HashMap<>();
      values.put(StreamedETLWithFrontendProcess.FIELD_SOURCE_TABLE, AssistantProvider.TABLE_NAME);
      values.put(StreamedETLWithFrontendProcess.FIELD_DESTINATION_TABLE, AssistantProvider.TABLE_NAME);
      values.put(StreamedETLWithFrontendProcess.FIELD_SUPPORTS_FULL_VALIDATION, true);
      values.put(StreamedETLWithFrontendProcess.FIELD_DO_FULL_VALIDATION, true);
      values.put(StreamedETLWithFrontendProcess.FIELD_PREVIEW_MESSAGE,
         "This makes one short live call to each selected provider's model (a few tokens) and saves the result on the provider.");
      QProcessMetaData process = StreamedETLWithFrontendProcess.defineProcessMetaData(ExtractViaQueryStep.class, Transform.class, Load.class, values);
      process.setName(NAME);
      process.setLabel("Test connection");
      process.setIcon(new QIcon("network_check"));
      process.setTableName(AssistantProvider.TABLE_NAME);
      process.setPermissionRules(AssistantMetaDataBuilder.adminRules());
      return (process);
   }



   /*******************************************************************************
    ** A summary line with future (Review) and past (Result) wording.
    *******************************************************************************/
   static ProcessSummaryLine line(Status status, String singularFuture, String pluralFuture, String singularPast, String pluralPast)
   {
      ProcessSummaryLine line = new ProcessSummaryLine(status, 0, null);
      line.setSingularFutureMessage(singularFuture);
      line.setPluralFutureMessage(pluralFuture);
      line.setSingularPastMessage(singularPast);
      line.setPluralPastMessage(pluralPast);
      return (line);
   }



   /*******************************************************************************
    ** A one-record line with the same text on both screens.
    *******************************************************************************/
   static ProcessSummaryLine single(Status status, String text, Serializable primaryKey)
   {
      ProcessSummaryLine line = line(status, text, text, text, text);
      line.incrementCountAndAddPrimaryKey(primaryKey);
      return (line);
   }



   /*******************************************************************************
    ** Review: nothing is called yet.
    *******************************************************************************/
   public static class Transform extends AbstractTransformStep implements ProcessSummaryProviderInterface
   {
      private final ProcessSummaryLine              willTest = line(Status.OK, "provider will be tested with a live call", "providers will be tested with a live call",
         "provider was tested", "providers were tested");
      private final ProcessSummaryLine              disabled = line(Status.INFO, "provider is turned off (it is tested anyway)", "providers are turned off (they are tested anyway)",
         "provider was turned off", "providers were turned off");
      private final List<ProcessSummaryLineInterface> problems = new ArrayList<>();



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
         for(QRecord record : input.getRecords())
         {
            AssistantProvider row = new AssistantProvider(record);
            willTest.incrementCountAndAddPrimaryKey(row.getId());
            if(!Boolean.TRUE.equals(row.getEnabled()))
            {
               disabled.incrementCountAndAddPrimaryKey(row.getId());
            }
            if(runtime != null)
            {
               ProviderService service = runtime.getProviderService();
               for(String error : service.validate(row))
               {
                  problems.add(single(Status.ERROR, row.getName() + ": " + error, row.getId()));
               }
               problems.add(single(Status.INFO, row.getName() + ": API key " + service.keyStatus(row), row.getId()));
            }
            output.getRecords().add(record);
         }
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();
         willTest.addSelfToListIfAnyCount(lines);
         disabled.addSelfToListIfAnyCount(lines);
         lines.addAll(problems);
         return (lines);
      }
   }



   /*******************************************************************************
    ** Result: one line per provider.
    *******************************************************************************/
   public static class Load extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      private final List<ProcessSummaryLineInterface> lines = new ArrayList<>();



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
         if(runtime == null)
         {
            throw (new QException("The assistant is not running on this server."));
         }
         String userId = AssistantSecurity.userId(QContext.getQSession());
         for(QRecord record : input.getRecords())
         {
            AssistantProvider       row    = new AssistantProvider(record);
            ConnectionTest.Result   result = ConnectionTest.run(runtime, row, userId);
            String label = row.getName() + " (" + runtime.getProviderService().kind(row) + ", " + runtime.getProviderService().location(row) + ")";
            if(result.ok())
            {
               lines.add(single(Status.OK, label + ": connected to " + result.model() + " in " + result.latencyMs() + " ms", row.getId()));
            }
            else
            {
               lines.add(single(Status.ERROR, label + ": " + result.error(), row.getId()));
            }
            output.getRecords().add(record);
         }
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         return (new ArrayList<>(lines));
      }
   }

}
