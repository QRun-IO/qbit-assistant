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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.access.DataAccessMode;
import com.kingsrook.qbits.assistant.access.DataAccessService;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractExtractStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;


/*******************************************************************************
 ** "Add rows for every table": a table action on assistant_data_access that
 ** writes one row per application table that has none yet, with the access
 ** the assistant already gets by default (READ, or NONE where the table's AI
 ** exposure is NONE). It changes no behavior on its own; it makes the defaults
 ** visible and editable. Existing rows are never touched. Standard streamed
 ** ETL: Review lists what will be added, Result what was added.
 *******************************************************************************/
public final class DataAccessDefaultsProcess
{
   public static final String NAME = "assistantDataAccessAddDefaults";

   public static final String NOTE = "Added by 'Add rows for every table': the assistant's default for this table. The user's own permissions still apply.";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private DataAccessDefaultsProcess()
   {
   }



   /*******************************************************************************
    ** The process: a table-level action (no records need to be selected).
    *******************************************************************************/
   public static QProcessMetaData process()
   {
      Map<String, Serializable> values = new HashMap<>();
      values.put(StreamedETLWithFrontendProcess.FIELD_SOURCE_TABLE, AssistantDataAccess.TABLE_NAME);
      values.put(StreamedETLWithFrontendProcess.FIELD_DESTINATION_TABLE, AssistantDataAccess.TABLE_NAME);
      values.put(StreamedETLWithFrontendProcess.FIELD_SUPPORTS_FULL_VALIDATION, false);
      values.put(StreamedETLWithFrontendProcess.FIELD_PREVIEW_MESSAGE,
         "These rows will be added: one per table that has none, with the access the assistant already has by default. Existing rows are not changed.");
      QProcessMetaData process = StreamedETLWithFrontendProcess.defineProcessMetaData(Extract.class, Transform.class, Load.class, values);
      process.setName(NAME);
      process.setLabel("Add rows for every table");
      process.setIcon(new QIcon("playlist_add"));
      process.setTableName(AssistantDataAccess.TABLE_NAME);
      process.setMinInputRecords(0);
      process.setPermissionRules(AssistantMetaDataBuilder.adminRules());
      return (process);
   }



   /*******************************************************************************
    ** The rows that are missing: one per application table without a row.
    *******************************************************************************/
   public static List<AssistantDataAccess> missingRows(QInstance qInstance, DataAccessService access, AssistantStore store) throws QException
   {
      Set<String> existing = new HashSet<>();
      for(AssistantDataAccess row : store.query(AssistantDataAccess.TABLE_NAME, new QQueryFilter().withLimit(10_000), AssistantDataAccess.class))
      {
         if(row.getObjectType() == null || AssistantDataAccess.TYPE_TABLE.equals(row.getObjectType()))
         {
            existing.add(row.getObjectName());
         }
      }

      List<AssistantDataAccess> rs = new ArrayList<>();
      for(QTableMetaData table : qInstance.getTables().values().stream().sorted((a, b) -> a.getName().compareTo(b.getName())).toList())
      {
         if(table.getName().startsWith("assistant_") || existing.contains(table.getName()))
         {
            continue;
         }
         DataAccessMode mode = access.forTable(table.getName()).mode();
         rs.add(new AssistantDataAccess().withObjectType(AssistantDataAccess.TYPE_TABLE).withObjectName(table.getName())
            .withMode(mode.name()).withNotes(NOTE));
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
    ** Extract: the missing rows, computed (no query of the selected records).
    *******************************************************************************/
   public static class Extract extends AbstractExtractStep
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public Integer doCount(RunBackendStepInput input) throws QException
      {
         AssistantRuntime runtime = runtime();
         return (missingRows(QContext.getQInstance(), runtime.getDataAccessService(), runtime.getStore()).size());
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AssistantRuntime runtime = runtime();
         for(AssistantDataAccess row : missingRows(QContext.getQInstance(), runtime.getDataAccessService(), runtime.getStore()))
         {
            getRecordPipe().addRecord(row.toQRecord());
         }
      }
   }



   /*******************************************************************************
    ** Transform: counts by mode for the review screen.
    *******************************************************************************/
   public static class Transform extends AbstractTransformStep implements ProcessSummaryProviderInterface
   {
      private final Map<String, ProcessSummaryLine> byMode = new HashMap<>();



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         for(QRecord record : input.getRecords())
         {
            String mode = record.getValueString("mode");
            byMode.computeIfAbsent(mode, m -> DiscoverModelsProcess.line("NONE".equals(m) ? Status.INFO : Status.OK,
               "table will get a row with access " + m, "tables will get a row with access " + m,
               "table got a row with access " + m, "tables got a row with access " + m)).incrementCountAndAddPrimaryKey(record.getValue("objectName"));
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
         byMode.values().forEach(line -> line.addSelfToListIfAnyCount(lines));
         if(lines.isEmpty())
         {
            lines.add(new ProcessSummaryLine(Status.INFO, null, "Every table already has a row."));
         }
         return (lines);
      }
   }



   /*******************************************************************************
    ** Load: insert through the store (the user's own session; admin only).
    *******************************************************************************/
   public static class Load extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      private final ProcessSummaryLine added = DiscoverModelsProcess.line(Status.OK, "row was added", "rows were added", "row was added", "rows were added");



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AssistantRuntime runtime = runtime();
         for(QRecord record : input.getRecords())
         {
            AssistantDataAccess row = runtime.getStore().insert(new AssistantDataAccess(record));
            added.incrementCountAndAddPrimaryKey(row.getId());
            output.getRecords().add(row.toQRecord());
         }
         runtime.getDataAccessService().invalidate();
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();
         added.addSelfToListIfAnyCount(lines);
         return (lines);
      }
   }

}
