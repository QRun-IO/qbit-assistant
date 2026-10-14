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

package com.kingsrook.qbits.assistant.access;


import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.actions.ai.AiExposure;
import com.kingsrook.qqq.backend.core.actions.ai.exposure.AiExposurePolicy;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** The assistant's data access per table and process: the admin's
 ** `assistant_data_access` row, else the default.
 **
 ** Defaults:
 ** - tables whose AI exposure is NONE (the `assistant_*` tables, sessions,
 **   OAuth, and any table its metadata marks NONE): NONE;
 ** - other tables: READ (the assistant never writes unless an admin says so);
 ** - processes: NONE.
 **
 ** The mode only narrows: every call still runs through the UI API with the
 ** user's own session.
 *******************************************************************************/
public class DataAccessService
{
   private static final QLogger LOG = QLogger.getLogger(DataAccessService.class);

   private final QInstance           qInstance;
   private final AssistantQBitConfig config;
   private final AssistantStore      store;
   private final AiExposurePolicy    exposurePolicy;
   private final Clock               clock;

   private volatile Map<String, AssistantDataAccess> rows;
   private volatile Instant                          loadedAt;



   /*******************************************************************************
    **
    *******************************************************************************/
   public DataAccessService(QInstance qInstance, AssistantQBitConfig config, AssistantStore store, AiExposurePolicy exposurePolicy, Clock clock)
   {
      this.qInstance = qInstance;
      this.config = config;
      this.store = store;
      this.exposurePolicy = exposurePolicy;
      this.clock = clock;
   }



   /*******************************************************************************
    ** A decision and where it came from.
    *******************************************************************************/
   public record Decision(DataAccessMode mode, String source)
   {
   }



   /*******************************************************************************
    ** The mode for a table (unknown table: NONE).
    *******************************************************************************/
   public Decision forTable(String tableName)
   {
      QTableMetaData table = tableName == null ? null : qInstance.getTable(tableName);
      if(table == null)
      {
         return (new Decision(DataAccessMode.NONE, "unknown table"));
      }

      AssistantDataAccess row = rows().get(key(AssistantDataAccess.TYPE_TABLE, tableName));
      DataAccessMode      set = row == null ? null : DataAccessMode.parse(row.getMode());
      AiExposure          exposure = exposurePolicy.getTableExposure(table);

      ////////////////////////////////////////////////////////////////////
      // a table whose AI exposure is NONE stays NONE: its metadata says //
      // its values must never reach a model, whatever the row says     //
      ////////////////////////////////////////////////////////////////////
      if(exposure == AiExposure.NONE)
      {
         return (new Decision(DataAccessMode.NONE, set != null && set != DataAccessMode.NONE ? "sensitive table (AI exposure NONE; the setting cannot raise it)" : "sensitive table (AI exposure NONE)"));
      }
      if(set != null)
      {
         return (new Decision(set, "setting"));
      }
      return (new Decision(DataAccessMode.READ, "default"));
   }



   /*******************************************************************************
    ** The mode for a process (unknown process: NONE; default NONE).
    *******************************************************************************/
   public Decision forProcess(String processName)
   {
      if(processName == null || qInstance.getProcess(processName) == null)
      {
         return (new Decision(DataAccessMode.NONE, "unknown process"));
      }
      AssistantDataAccess row = rows().get(key(AssistantDataAccess.TYPE_PROCESS, processName));
      DataAccessMode      set = row == null ? null : DataAccessMode.parse(row.getMode());
      return (set == null ? new Decision(DataAccessMode.NONE, "default") : new Decision(set, "setting"));
   }



   /*******************************************************************************
    ** Whether an admin marked the table's single-record inserts and updates as
    ** low-risk (auto-confirmable, when the policy and the user allow it).
    *******************************************************************************/
   public boolean autoConfirmOk(String tableName)
   {
      AssistantDataAccess row = rows().get(key(AssistantDataAccess.TYPE_TABLE, tableName));
      return (row != null && Boolean.TRUE.equals(row.getAutoConfirmOk()) && forTable(tableName).mode().allowsWrite());
   }



   /*******************************************************************************
    ** Drop the cache (after a change to the table).
    *******************************************************************************/
   public void invalidate()
   {
      rows = null;
   }



   /*******************************************************************************
    ** The rows, cached for policyCacheSeconds. A read failure keeps the last
    ** rows; with none, every table falls back to its default.
    *******************************************************************************/
   private Map<String, AssistantDataAccess> rows()
   {
      Map<String, AssistantDataAccess> current = rows;
      long                             ttl     = config.getPolicyCacheSeconds() == null ? 30 : config.getPolicyCacheSeconds();
      if(current != null && loadedAt != null && loadedAt.plusSeconds(ttl).isAfter(clock.instant()))
      {
         return (current);
      }
      try
      {
         Map<String, AssistantDataAccess> loaded = new HashMap<>();
         for(AssistantDataAccess row : store.query(AssistantDataAccess.TABLE_NAME, new QQueryFilter().withLimit(10_000), AssistantDataAccess.class))
         {
            loaded.put(key(row.getObjectType(), row.getObjectName()), row);
         }
         rows = loaded;
         loadedAt = clock.instant();
         return (loaded);
      }
      catch(QException | RuntimeException e)
      {
         LOG.warn("Could not read the assistant data access settings; using the defaults", e);
         return (current == null ? Map.of() : current);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String key(String type, String name)
   {
      return ((type == null ? AssistantDataAccess.TYPE_TABLE : type) + ":" + name);
   }

}
