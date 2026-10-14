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
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantRagSource;
import com.kingsrook.qbits.assistant.rag.RagService;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
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
 ** Standard Review/Result record actions. No scheduler or service-user runner.
 *******************************************************************************/
public final class KnowledgeIndexProcess
{
   public static final String INDEX = "assistantKnowledgeIndex";
   public static final String CLEAR = "assistantKnowledgeClear";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private KnowledgeIndexProcess()
   {
   }



   /*******************************************************************************
    ** Both processes are table actions, never navigation entries.
    *******************************************************************************/
   public static List<QProcessMetaData> processes()
   {
      List<QProcessMetaData> processes = new ArrayList<>();
      for(Boolean clear : List.of(false, true))
      {
         Map<String, Serializable> values = new HashMap<>();
         values.put(StreamedETLWithFrontendProcess.FIELD_SOURCE_TABLE, AssistantRagSource.TABLE_NAME);
         values.put(StreamedETLWithFrontendProcess.FIELD_DESTINATION_TABLE, AssistantRagSource.TABLE_NAME);
         values.put(StreamedETLWithFrontendProcess.FIELD_SUPPORTS_FULL_VALIDATION, true);
         values.put(StreamedETLWithFrontendProcess.FIELD_DO_FULL_VALIDATION, true);
         values.put(StreamedETLWithFrontendProcess.FIELD_PREVIEW_MESSAGE, clear ? "Clear vectors for the selected knowledge sources." : "Read content through the UI API as you and embed changed chunks. Embedding usage counts toward budgets.");
         QProcessMetaData process = StreamedETLWithFrontendProcess.defineProcessMetaData(ExtractViaQueryStep.class, clear ? ClearReview.class : Review.class, clear ? Clear.class : Index.class, values);
         process.setName(clear ? CLEAR : INDEX);
         process.setLabel(clear ? "Clear index" : "Index now");
         process.setIcon(new QIcon(clear ? "delete_sweep" : "manage_search"));
         process.setTableName(AssistantRagSource.TABLE_NAME);
         process.setPermissionRules(AssistantMetaDataBuilder.adminRules());
         processes.add(process);
      }
      return (processes);
   }



   /*******************************************************************************
    ** Review validates configuration without sending content to any provider.
    *******************************************************************************/
   public static class Review extends AbstractTransformStep implements ProcessSummaryProviderInterface
   {
      private final ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();



      /*******************************************************************************
       ** Source records only; application records are read at Result time.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
         for(QRecord record : input.getRecords())
         {
            AssistantRagSource source = new AssistantRagSource(record);
            List<String> errors = problems(runtime, source);
            lines.add(TestConnectionProcess.single(errors.isEmpty() ? Status.INFO : Status.ERROR,
               source.getName() + (errors.isEmpty() ? ": source selected; no content has been sent." : ": " + String.join("; ", errors)), source.getId()));
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



      /*******************************************************************************
       ** Review reports PHI/model setup blocks before any indexing dispatch.
       *******************************************************************************/
      protected List<String> problems(AssistantRuntime runtime, AssistantRagSource source) throws QException
      {
         List<String> errors = runtime.getRagService().validate(source);
         if(errors.isEmpty())
         {
            try
            {
               runtime.getProviderService().resolveEmbedding(source.getEmbeddingModelId(),
                  runtime.getConfig().getClassificationSource().highest(source.getTableName()) == DataClassification.PHI);
            }
            catch(QException e)
            {
               errors.add(e.getMessage());
            }
         }
         return (errors);
      }
   }



   /*******************************************************************************
    ** Broken or disabled source configuration must never prevent clearing it.
    *******************************************************************************/
   public static class ClearReview extends Review
   {
      /*******************************************************************************
       ** Clearing has no model or source-content prerequisites.
       *******************************************************************************/
      @Override
      protected List<String> problems(AssistantRuntime runtime, AssistantRagSource source)
      {
         return (List.of());
      }
   }



   /*******************************************************************************
    ** Result performs indexing under the clicking administrator's QContext.
    *******************************************************************************/
   public static class Index extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      protected final ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();



      /*******************************************************************************
       ** Page of source configuration records, not application content.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
         for(QRecord record : input.getRecords())
         {
            AssistantRagSource source = new AssistantRagSource(record);
            RagService.Result result = runtime.getRagService().index(source);
            lines.add(TestConnectionProcess.single(result.errors() == 0 ? Status.OK : Status.ERROR, result.summary(), source.getId()));
            output.getRecords().add(record);
         }
      }



      /*******************************************************************************
       ** Counts from this run, suitable for the standard Result screen.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         return (lines);
      }
   }



   /*******************************************************************************
    ** Clear shares the process presentation but makes no embedding calls.
    *******************************************************************************/
   public static class Clear extends Index
   {
      /*******************************************************************************
       ** Remove only the selected sources' vectors.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         for(QRecord record : input.getRecords())
         {
            AssistantRagSource source = new AssistantRagSource(record);
            AssistantRuntime.of(QContext.getQInstance()).getRagService().clear(source);
            lines.add(TestConnectionProcess.single(Status.OK, source.getName() + ": index cleared.", source.getId()));
            output.getRecords().add(record);
         }
      }
   }
}
