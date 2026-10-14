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


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiGateway;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;


/*******************************************************************************
 ** "Explain access": for a user (by their own session), which UI API
 ** operations the assistant could call on each table and which it would be
 ** denied, with the reason.
 **
 ** Nothing is hard-coded: the user's side comes from GET /qqq/v1/metaData made
 ** with the user's session (the same permission checks that decide what the
 ** UI shows them), and the assistant's side from the same data access and
 ** policy the tools use. A tool allowed here can still be refused by the UI
 ** API for a single record (record locks, validation): the call itself is the
 ** final check.
 *******************************************************************************/
public class AccessExplainer
{
   private static final List<String> READ_TOOLS  = List.of(UiApiToolbox.QUERY_RECORDS, UiApiToolbox.COUNT_RECORDS, UiApiToolbox.GET_RECORD, UiApiToolbox.RECORD_HISTORY);
   private static final List<String> WRITE_TOOLS = List.of(UiApiToolbox.CREATE_RECORD, UiApiToolbox.UPDATE_RECORD, UiApiToolbox.DELETE_RECORD);

   private final QInstance         qInstance;
   private final DataAccessService dataAccess;



   /*******************************************************************************
    **
    *******************************************************************************/
   public AccessExplainer(QInstance qInstance, DataAccessService dataAccess)
   {
      this.qInstance = qInstance;
      this.dataAccess = dataAccess;
   }



   /*******************************************************************************
    ** One tool on one table: allowed, or why not.
    *******************************************************************************/
   public record OperationCheck(String tool, String operation, boolean allowed, String reason)
   {
   }



   /*******************************************************************************
    ** One table: the user's UI permissions, the assistant's data access, and
    ** each tool's verdict.
    *******************************************************************************/
   public record TableAccess(String table, String label, DataAccessMode mode, String modeSource, boolean uiVisible, boolean uiRead, boolean uiInsert, boolean uiEdit,
                             boolean uiDelete, List<OperationCheck> operations)
   {
   }



   /*******************************************************************************
    ** Explain every table, or only tableName, or only the tables a prompt
    ** mentions (by name or label).
    *******************************************************************************/
   public List<TableAccess> explain(UiApiGateway gateway, String sessionUUID, PolicyService.ToolPolicy policy, String tableName, String prompt) throws QException
   {
      return (explain(gateway, sessionUUID, null, policy, tableName, prompt));
   }



   /*******************************************************************************
    ** Explain, with the caller's request credentials when they are known.
    *******************************************************************************/
   public List<TableAccess> explain(UiApiGateway gateway, String sessionUUID, UiApiCredentials credentials, PolicyService.ToolPolicy policy, String tableName, String prompt) throws QException
   {
      return (explain(gateway, sessionUUID, credentials, policy, tableName, prompt, null));
   }



   /*******************************************************************************
    ** Preserve a known turn's attribution on the metadata request.
    *******************************************************************************/
   public List<TableAccess> explain(UiApiGateway gateway, String sessionUUID, UiApiCredentials credentials, PolicyService.ToolPolicy policy, String tableName, String prompt, String turnId) throws QException
   {
      UiApiResult response = gateway.invoke(new UiApiCall("GET", "/metaData").withSessionUUID(sessionUUID).withCredentials(credentials).withInvokedBy("assistant-explain").withClientDetail(turnId));
      if(!response.isSuccess())
      {
         throw (new QException("GET /metaData answered HTTP " + response.status() + (response.errorMessage() == null ? "" : ": " + response.errorMessage())));
      }
      Map<String, Object> uiTables = asMap(asMap(response.json()).get("tables"));

      List<TableAccess> rs = new ArrayList<>();
      for(QTableMetaData table : new TreeMap<>(qInstance.getTables()).values())
      {
         if(!matches(table, tableName, prompt))
         {
            continue;
         }
         Map<String, Object>        ui       = asMap(uiTables.get(table.getName()));
         boolean                    visible  = uiTables.containsKey(table.getName());
         boolean                    read     = Boolean.TRUE.equals(ui.get("readPermission"));
         boolean                    insert   = Boolean.TRUE.equals(ui.get("insertPermission"));
         boolean                    edit     = Boolean.TRUE.equals(ui.get("editPermission"));
         boolean                    delete   = Boolean.TRUE.equals(ui.get("deletePermission"));
         DataAccessService.Decision decision = dataAccess.forTable(table.getName());

         List<OperationCheck> checks = new ArrayList<>();
         for(String tool : READ_TOOLS)
         {
            checks.add(check(tool, policy, decision, visible, read, decision.mode().allowsRead(), "read"));
         }
         checks.add(check(UiApiToolbox.CREATE_RECORD, policy, decision, visible, insert, decision.mode().allowsWrite(), "insert"));
         checks.add(check(UiApiToolbox.UPDATE_RECORD, policy, decision, visible, edit, decision.mode().allowsWrite(), "edit"));
         checks.add(check(UiApiToolbox.DELETE_RECORD, policy, decision, visible, delete, decision.mode().allowsWrite(), "delete"));

         rs.add(new TableAccess(table.getName(), table.getLabel() == null ? table.getName() : table.getLabel(), decision.mode(), decision.source(), visible, read, insert, edit, delete, checks));
      }
      return (rs);
   }



   /*******************************************************************************
    ** The verdict for one tool: the policy, then data access, then the user's
    ** own permission as the UI API reports it.
    *******************************************************************************/
   private static OperationCheck check(String tool, PolicyService.ToolPolicy policy, DataAccessService.Decision decision, boolean visible, boolean uiPermission, boolean modeAllows, String permissionWord)
   {
      String[] operation = UiApiToolbox.OPERATIONS.get(tool);
      String   op        = operation[0] + " " + operation[1];
      if(policy != null && !policy.allows(tool))
      {
         return (new OperationCheck(tool, op, false, "The policy does not allow the " + tool + " tool."));
      }
      if(!modeAllows)
      {
         String needs = WRITE_TOOLS.contains(tool) ? "READ_WRITE" : "READ";
         return (new OperationCheck(tool, op, false, "Assistant data access is " + decision.mode() + " (" + decision.source() + "); this needs " + needs + "."));
      }
      if(!visible)
      {
         return (new OperationCheck(tool, op, false, "The user cannot see this table in the UI (GET /metaData does not list it)."));
      }
      if(!uiPermission)
      {
         return (new OperationCheck(tool, op, false, "The user has no " + permissionWord + " permission on this table (the UI API would answer 403)."));
      }
      return (new OperationCheck(tool, op, true, "Allowed: the user may " + permissionWord + " it in the UI, and data access is " + decision.mode() + "."));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static boolean matches(QTableMetaData table, String tableName, String prompt)
   {
      if(StringUtils.hasContent(tableName))
      {
         return (table.getName().equals(tableName.strip()));
      }
      if(StringUtils.hasContent(prompt))
      {
         String lower = prompt.toLowerCase(Locale.ROOT);
         String label = table.getLabel() == null ? "" : table.getLabel().toLowerCase(Locale.ROOT);
         return (lower.contains(table.getName().toLowerCase(Locale.ROOT)) || (StringUtils.hasContent(label) && (lower.contains(label) || lower.contains(label + "s"))));
      }
      return (true);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static Map<String, Object> asMap(Object value)
   {
      return (value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of());
   }

}
