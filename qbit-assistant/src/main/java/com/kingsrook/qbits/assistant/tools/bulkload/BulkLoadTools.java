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


import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.access.DataAccessService;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.spi.ModelToolDefinition;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolOutcome;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolResult;
import com.kingsrook.qqq.backend.core.actions.ai.output.AiDataEnvelope;
import com.kingsrook.qqq.backend.core.actions.ai.tools.AiJsonSchema;
import com.kingsrook.qqq.backend.core.actions.ai.tools.AiToolException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import org.json.JSONArray;
import org.json.JSONObject;


/*******************************************************************************
 ** The assistant's bulk-load tools (design spec #1171 section 3.4): use and
 ** create saved bulk load profiles from a file the user attached in the chat.
 ** Every call goes through the /qqq/v1 UI API with the user's own session, so
 ** table and process permissions, record locks and the PHI rule are enforced
 ** by the server, never by the model:
 ** - bulk_profiles_list / bulk_profile_describe: the user's (and shared)
 **   saved profiles, through the savedBulkLoadProfile table (record locks);
 ** - bulk_file_attach: an upload the panel already made (by its ref), with
 **   its column profiles (samples only where the server allows);
 ** - bulk_mapping_suggest: core's suggester, then the model refines it;
 ** - bulk_load_dry_run: the table's real bulk-insert validation, no store;
 ** - bulk_profile_save (write): storeSavedBulkLoadProfile, and shares through
 **   insertSharedRecord, after the user confirms;
 ** - bulk_load_run (write): through {@link BulkLoadRunner}, after the user
 **   confirms (and, as a durable job, again on its job record).
 **
 ** The AI route header (X-QQQ-AI-Route: hipaa|standard) is set here from the
 ** turn's provider, never from the model's input. Tool-call logs keep
 ** profiles and counts, never sample values (see {@link #forLog}).
 **
 ** STAND-IN(core=#1179, removeBy=RC.8): the profile, suggestMapping and
 ** validate operations ship in a QQQ RC after 4.1.0-RC.5; until then the
 ** tools report that the server does not offer them.
 *******************************************************************************/
public class BulkLoadTools
{
   public static final String LIST     = "bulk_profiles_list";
   public static final String DESCRIBE = "bulk_profile_describe";
   public static final String ATTACH   = "bulk_file_attach";
   public static final String SUGGEST  = "bulk_mapping_suggest";
   public static final String DRY_RUN  = "bulk_load_dry_run";
   public static final String SAVE     = "bulk_profile_save";
   public static final String RUN      = "bulk_load_run";

   public static final Set<String> NAMES       = Set.of(LIST, DESCRIBE, ATTACH, SUGGEST, DRY_RUN, SAVE, RUN);
   public static final Set<String> WRITE_TOOLS = Set.of(SAVE, RUN);

   public static final String PROFILE_TABLE = "savedBulkLoadProfile";
   public static final String ROUTE_HEADER  = "X-QQQ-AI-Route";

   private static final Set<String> LOG_STRIPPED_KEYS = Set.of("sampleValues", "bulletsOfText", "valueMappings");

   private final QInstance         qInstance;
   private final DataAccessService dataAccess;
   private final BulkLoadRunner    runner;



   /*******************************************************************************
    **
    *******************************************************************************/
   public BulkLoadTools(QInstance qInstance, DataAccessService dataAccess, BulkLoadRunner runner)
   {
      this.qInstance = qInstance;
      this.dataAccess = dataAccess;
      this.runner = runner == null ? new JobsLaunchBulkLoadRunner() : runner;
   }



   /*******************************************************************************
    ** Offered when the instance has saved bulk load profiles.
    *******************************************************************************/
   public boolean offered()
   {
      return (qInstance != null && qInstance.getTable(PROFILE_TABLE) != null);
   }



   /*******************************************************************************
    ** The definitions the policy allows (writes only when anyWritable).
    *******************************************************************************/
   public List<ModelToolDefinition> definitions(PolicyService.ToolPolicy policy, boolean anyWritable)
   {
      List<ModelToolDefinition> definitions = new ArrayList<>();
      if(!offered())
      {
         return (definitions);
      }

      Map<String, Object> table   = AiJsonSchema.string("The table to load into (from list_tables).");
      Map<String, Object> upload  = AiJsonSchema.string("The upload ref of the file the user attached (from the conversation's attachments).");
      Map<String, Object> profile = new LinkedHashMap<>(Map.of("type", "object", "description", """
         A v1 bulk load profile: {"version":"v1","layout":"FLAT","hasHeaderRow":true,"fieldList":[{"fieldName":"...","columnIndex":0, \
         "headerName":"...","defaultValue":null,"doValueMapping":false,"valueMappings":{"file value":"field value"}}]}."""));

      add(definitions, policy, LIST, "List the saved bulk load profiles (the user's own and those shared with them) for a table.",
         AiJsonSchema.object(AiJsonSchema.properties("tableName", table), List.of("tableName")));
      add(definitions, policy, DESCRIBE, "Read one saved bulk load profile: its table, owner and column mapping.",
         AiJsonSchema.object(AiJsonSchema.properties("id", AiJsonSchema.integer("The profile id (from bulk_profiles_list).")), List.of("id")));
      add(definitions, policy, ATTACH, """
         Look at a file the user attached for a bulk load: whether row 1 is a header, and each column's type, blanks, distinct count, \
         pattern and (only where the server allows) a few sample values.""",
         AiJsonSchema.object(AiJsonSchema.properties("uploadRef", upload, "tableName", table), List.of("uploadRef", "tableName")));
      add(definitions, policy, SUGGEST, """
         Suggest a bulk load profile for an attached file: the server's header matching, with the columns it left unmapped, required \
         fields it did not fill, and fields whose values need mapping. Refine the profile yourself from the headers and column \
         profiles, then check it with bulk_load_dry_run.""",
         AiJsonSchema.object(AiJsonSchema.properties("uploadRef", upload, "tableName", table), List.of("uploadRef", "tableName")));
      add(definitions, policy, DRY_RUN, """
         Dry-run a bulk load profile over an attached file through the table's real bulk insert validation. Nothing is stored. \
         Returns counts, summary lines and error groups with row numbers. SAMPLE checks the first 100 rows; FULL the whole file. \
         Fix the profile and run it again until there are no errors (or explain the rest to the user).""",
         AiJsonSchema.object(AiJsonSchema.properties("uploadRef", upload, "tableName", table, "profile", profile,
            "mode", AiJsonSchema.enumOf(List.of("SAMPLE", "FULL"), "SAMPLE (default) or FULL.")), List.of("uploadRef", "tableName", "profile")));

      if(anyWritable)
      {
         Map<String, Object> share = AiJsonSchema.object(AiJsonSchema.properties(
            "audienceType", AiJsonSchema.enumOf(List.of("user", "group"), "Share with a user (email) or a group."),
            "audienceId", AiJsonSchema.string("The user's email or the group's name."),
            "scope", AiJsonSchema.enumOf(List.of("READ_ONLY", "READ_WRITE"), "The share's scope (default READ_ONLY).")), List.of("audienceType", "audienceId"));
         add(definitions, policy, SAVE, """
            Save a bulk load profile (after a clean dry run) under a name, optionally sharing it. The user confirms before it is saved; \
            it is saved as the user, and shares are limited to what the user may share.""",
            AiJsonSchema.object(AiJsonSchema.properties("tableName", table, "label", AiJsonSchema.string("The profile's name."), "profile", profile,
               "shares", AiJsonSchema.array(share, "Optional shares.")), List.of("tableName", "label", "profile")));
         add(definitions, policy, RUN, """
            Load an attached file into a table with a saved profile. The user confirms first; a large load runs as a background job that \
            the user starts from its job record.""",
            AiJsonSchema.object(AiJsonSchema.properties("tableName", table, "uploadRef", upload, "profileId", AiJsonSchema.integer("The saved profile id.")),
               List.of("tableName", "uploadRef", "profileId")));
      }
      return (definitions);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void add(List<ModelToolDefinition> definitions, PolicyService.ToolPolicy policy, String name, String description, Map<String, Object> schema)
   {
      if(policy == null || policy.allows(name))
      {
         definitions.add(new ModelToolDefinition().withName(name).withDescription(description).withInputSchema(schema).withStrict(false));
      }
   }



   /*******************************************************************************
    ** Run a tool. hipaaRoute is the turn's provider's HIPAA eligibility (sent
    ** as X-QQQ-AI-Route; the model cannot set it).
    *******************************************************************************/
   public UiApiToolbox.Outcome execute(UiApiToolbox.Context context, String toolCallId, String toolName, Map<String, Object> input, boolean hipaaRoute)
   {
      AiToolResult        result  = new AiToolResult().withToolCallId(toolCallId).withToolName(toolName);
      Map<String, Object> in      = input == null ? Map.of() : input;
      Call                call    = new Call(context, hipaaRoute);
      Integer             actionId = null;
      long                started = System.nanoTime();
      try
      {
         if(context.policy() != null && !context.policy().allows(toolName))
         {
            deny(result, "policy:tool", "The administrator has not allowed the " + toolName + " tool.");
            return (UiApiToolbox.Outcome.of(result, null, null, null));
         }

         String tableName = in.get("tableName") instanceof String t ? t.strip() : null;
         if(tableName != null)
         {
            result.setTableName(tableName);
            if(!dataAccess.forTable(tableName).mode().allowsRead())
            {
               deny(result, "dataAccess:" + dataAccess.forTable(tableName).mode(), "The assistant may not use the " + tableName + " table.");
               return (UiApiToolbox.Outcome.of(result, null, null, null));
            }
         }

         switch(toolName)
         {
            case LIST -> list(call, result, required(in, "tableName"));
            case DESCRIBE -> describe(call, result, requiredInteger(in, "id"));
            case ATTACH -> attach(call, result, required(in, "tableName"), required(in, "uploadRef"));
            case SUGGEST -> suggest(call, result, required(in, "tableName"), required(in, "uploadRef"));
            case DRY_RUN -> dryRun(call, result, required(in, "tableName"), required(in, "uploadRef"), requiredMap(in, "profile"), in.get("mode") instanceof String m ? m : "SAMPLE");
            case SAVE, RUN -> actionId = propose(context, result, toolName, required(in, "tableName"), in);
            default -> throw (AiToolException.invalidInput("Unknown tool \"" + toolName + "\"."));
         }
      }
      catch(AiToolException e)
      {
         result.withOutcome(e.getOutcome() == null ? AiToolOutcome.INVALID_INPUT : e.getOutcome()).withSummary(shortText(e.getMessage())).withModelContent(e.getMessage());
      }
      catch(QException | RuntimeException e)
      {
         result.withOutcome(AiToolOutcome.ERROR).withSummary("Failed").withModelContent("The tool failed: " + shortText(e.getMessage()));
      }
      finally
      {
         result.setDurationMs((System.nanoTime() - started) / 1_000_000);
      }
      return (UiApiToolbox.Outcome.of(result, call.operation, call.httpStatus, actionId));
   }



   /*******************************************************************************
    ** Run a confirmed write (from a pending action) with the confirming user's
    ** session.
    *******************************************************************************/
   public UiApiToolbox.Outcome executeConfirmed(UiApiToolbox.Context context, String toolCallId, String toolName, String tableName, Map<String, Object> values)
   {
      AiToolResult result  = new AiToolResult().withToolCallId(toolCallId).withToolName(toolName).withTableName(tableName);
      Call         call    = new Call(context, false);
      long         started = System.nanoTime();
      try
      {
         if(!WRITE_TOOLS.contains(toolName))
         {
            throw (AiToolException.invalidInput("Not a write tool: " + toolName));
         }
         DataAccessService.Decision decision = dataAccess.forTable(SAVE.equals(toolName) ? PROFILE_TABLE : tableName);
         if(!decision.mode().allowsWrite())
         {
            deny(result, "dataAccess:" + decision.mode(), "The assistant may not write here (data access " + decision.mode() + ", " + decision.source() + ").");
            return (UiApiToolbox.Outcome.of(result, null, null, null));
         }
         if(SAVE.equals(toolName))
         {
            save(call, result, tableName, values);
         }
         else
         {
            run(call, result, context, tableName, values);
         }
      }
      catch(AiToolException e)
      {
         result.withOutcome(e.getOutcome() == null ? AiToolOutcome.INVALID_INPUT : e.getOutcome()).withSummary(shortText(e.getMessage())).withModelContent(e.getMessage());
      }
      catch(QException | RuntimeException e)
      {
         result.withOutcome(AiToolOutcome.ERROR).withSummary("Failed").withModelContent("The write failed: " + shortText(e.getMessage()));
      }
      finally
      {
         result.setDurationMs((System.nanoTime() - started) / 1_000_000);
      }
      return (UiApiToolbox.Outcome.of(result, call.operation, call.httpStatus, null));
   }



   ////////////////////////////////////////////////////////////////////////////
   // read tools                                                             //
   ////////////////////////////////////////////////////////////////////////////



   /*******************************************************************************
    ** POST /table/savedBulkLoadProfile/query: the profiles the record locks let
    ** the user see, for one table.
    *******************************************************************************/
   private void list(Call call, AiToolResult result, String tableName) throws QException
   {
      Map<String, Object> filter = Map.of(
         "criteria", List.of(Map.of("fieldName", "tableName", "operator", "EQUALS", "values", List.of(tableName))),
         "orderBys", List.of(Map.of("fieldName", "label", "isAscending", true)));
      UiApiResult response = call.invoke(new UiApiCall("POST", "/table/{tableName}/query").withPathParam("tableName", PROFILE_TABLE)
         .withJsonBody(Map.of("filter", filter)));
      if(response == null || !response.isSuccess())
      {
         fail(result, call, response);
         return;
      }

      List<Map<String, Object>> rows = new ArrayList<>();
      for(Object record : list(response.json().get("records")))
      {
         Map<String, Object> values = map(map(record).get("values"));
         Map<String, Object> row    = new LinkedHashMap<>();
         row.put("id", values.get("id"));
         row.put("label", values.get("label"));
         row.put("owner", values.get("userId"));
         row.put("fieldCount", fieldList(values.get("mappingJson")).size());
         rows.add(row);
      }
      result.withOutcome(AiToolOutcome.OK).withRowCount(rows.size()).withSummary("Found " + rows.size() + " saved profile" + (rows.size() == 1 ? "" : "s"))
         .withPermissionCheck(call.permissionCheck())
         .withModelContent(AiDataEnvelope.wrap(LIST, PROFILE_TABLE, JsonUtils.toJson(Map.of("tableName", tableName, "profiles", rows))));
   }



   /*******************************************************************************
    ** GET /table/savedBulkLoadProfile/{id}: one profile (when the locks allow).
    *******************************************************************************/
   private void describe(Call call, AiToolResult result, Integer id) throws QException
   {
      UiApiResult response = call.invoke(new UiApiCall("GET", "/table/{tableName}/{primaryKey}").withPathParam("tableName", PROFILE_TABLE).withPathParam("primaryKey", id));
      if(response == null || !response.isSuccess())
      {
         fail(result, call, response);
         return;
      }
      Map<String, Object> values = map(map(response.json().get("record")).get("values"));
      Map<String, Object> out    = new LinkedHashMap<>();
      out.put("id", values.get("id"));
      out.put("label", values.get("label"));
      out.put("tableName", values.get("tableName"));
      out.put("owner", values.get("userId"));
      out.put("profile", values.get("mappingJson") == null ? null : new JSONObject(String.valueOf(values.get("mappingJson"))).toMap());
      result.setTableName(String.valueOf(values.get("tableName")));
      result.withOutcome(AiToolOutcome.OK).withRowCount(1).withSummary("Read profile " + values.get("label"))
         .withPermissionCheck(call.permissionCheck())
         .withModelContent(AiDataEnvelope.wrap(DESCRIBE, PROFILE_TABLE, JsonUtils.toJson(out)));
   }



   /*******************************************************************************
    ** GET /bulkLoad/uploads/{uploadRef}/profile.
    *******************************************************************************/
   private void attach(Call call, AiToolResult result, String tableName, String uploadRef) throws QException
   {
      UiApiResult response = call.invoke(new UiApiCall("GET", "/bulkLoad/uploads/{uploadRef}/profile").withPathParam("uploadRef", uploadRef)
         .withQueryParam("tableName", tableName).withQueryParam("sampleCount", 5));
      if(response == null || !response.isSuccess())
      {
         fail(result, call, response);
         return;
      }
      Map<String, Object> json    = response.json();
      int                 columns = list(json.get("columns")).size();
      result.withOutcome(AiToolOutcome.OK).withRowCount(columns).withSummary("Read " + json.get("fileName") + ": " + columns + " columns")
         .withPermissionCheck(call.permissionCheck())
         .withModelContent(AiDataEnvelope.wrap(ATTACH, tableName, response.body()));
   }



   /*******************************************************************************
    ** POST /bulkLoad/{tableName}/suggestMapping.
    *******************************************************************************/
   private void suggest(Call call, AiToolResult result, String tableName, String uploadRef) throws QException
   {
      UiApiResult response = call.invoke(new UiApiCall("POST", "/bulkLoad/{tableName}/suggestMapping").withPathParam("tableName", tableName)
         .withJsonBody(Map.of("uploadRef", uploadRef)));
      if(response == null || !response.isSuccess())
      {
         fail(result, call, response);
         return;
      }
      Map<String, Object> json   = response.json();
      int                 mapped = list(map(json.get("profile")).get("fieldList")).size();
      result.withOutcome(AiToolOutcome.OK).withRowCount(mapped).withSummary("Suggested a mapping of " + mapped + " field" + (mapped == 1 ? "" : "s"))
         .withPermissionCheck(call.permissionCheck())
         .withModelContent(AiDataEnvelope.wrap(SUGGEST, tableName, response.body()));
   }



   /*******************************************************************************
    ** POST /bulkLoad/{tableName}/validate (nothing is stored).
    *******************************************************************************/
   private void dryRun(Call call, AiToolResult result, String tableName, String uploadRef, Map<String, Object> profile, String mode) throws QException
   {
      UiApiResult response = call.invoke(new UiApiCall("POST", "/bulkLoad/{tableName}/validate").withPathParam("tableName", tableName)
         .withJsonBody(Map.of("uploadRef", uploadRef, "profile", profile, "mode", "FULL".equalsIgnoreCase(mode) ? "FULL" : "SAMPLE"))
         .withTimeout(Duration.ofSeconds(120)));
      if(response == null || !response.isSuccess())
      {
         fail(result, call, response);
         return;
      }
      Map<String, Object> json   = response.json();
      Integer             ok     = ValueUtils.getValueAsInteger(json.get("okCount"));
      Integer             errors = ValueUtils.getValueAsInteger(json.get("errorCount"));
      result.withOutcome(AiToolOutcome.OK).withRowCount(ok)
         .withSummary("Dry run (" + json.get("mode") + "): " + ok + " ok, " + errors + " with errors")
         .withPermissionCheck(call.permissionCheck())
         .withRender(render("bulk_load_dry_run", tableName, json))
         .withModelContent(AiDataEnvelope.wrap(DRY_RUN, tableName, response.body()));
   }



   ////////////////////////////////////////////////////////////////////////////
   // write tools                                                            //
   ////////////////////////////////////////////////////////////////////////////



   /*******************************************************************************
    ** Propose a save or a run as a pending action (the user confirms in the
    ** panel; nothing happens until then).
    *******************************************************************************/
   private Integer propose(UiApiToolbox.Context context, AiToolResult result, String toolName, String tableName, Map<String, Object> in) throws QException
   {
      DataAccessService.Decision decision = dataAccess.forTable(SAVE.equals(toolName) ? PROFILE_TABLE : tableName);
      if(!decision.mode().allowsWrite())
      {
         deny(result, "dataAccess:" + decision.mode(), "The assistant may only read here (data access " + decision.mode() + ", " + decision.source() + "). Writes need READ_WRITE.");
         return (null);
      }

      Map<String, Object> values  = new LinkedHashMap<>();
      String              summary;
      if(SAVE.equals(toolName))
      {
         String label = required(in, "label");
         values.put("label", label);
         values.put("profile", requiredMap(in, "profile"));
         if(in.get("shares") instanceof List<?> shares && !shares.isEmpty())
         {
            values.put("shares", shares);
         }
         summary = "Save the bulk load profile \"" + label + "\" for " + tableName + (values.containsKey("shares") ? " and share it" : "");
      }
      else
      {
         values.put("uploadRef", required(in, "uploadRef"));
         values.put("profileId", requiredInteger(in, "profileId"));
         summary = "Load the attached file into " + tableName + " with saved profile " + values.get("profileId");
      }

      if(context.pendingActions() == null)
      {
         throw (new QException("The " + toolName + " action could not be proposed."));
      }
      String  operation = SAVE.equals(toolName) ? "POST /processes/{processName}/init" : "POST " + JobsLaunchBulkLoadRunner.LAUNCH_OPERATION_PATH;
      Integer actionId  = context.pendingActions().propose(new UiApiToolbox.ProposedWrite(result.getToolCallId(), toolName, operation, tableName, null, values, summary));
      if(actionId == null)
      {
         throw (new QException("The " + toolName + " action could not be proposed."));
      }

      Map<String, Object> render = new LinkedHashMap<>();
      render.put("type", "pending_action");
      render.put("actionId", actionId);
      render.put("toolName", toolName);
      render.put("tableName", tableName);
      render.put("summary", summary);
      result.withOutcome(AiToolOutcome.OK).withSummary("Waiting for your confirmation").withRender(render)
         .withModelContent("Proposed (pending action " + actionId + "): " + summary + ". Nothing happens until the user confirms it in the panel.");
      return (actionId);
   }



   /*******************************************************************************
    ** Confirmed save: storeSavedBulkLoadProfile, then insertSharedRecord per
    ** share (core lets only the owner share).
    *******************************************************************************/
   private void save(Call call, AiToolResult result, String tableName, Map<String, Object> values) throws QException
   {
      Map<String, Object> processValues = new LinkedHashMap<>();
      processValues.put("tableName", tableName);
      processValues.put("label", values.get("label"));
      processValues.put("mappingJson", JsonUtils.toJson(values.get("profile")));
      processValues.put("isBulkEdit", false);
      UiApiResult response = call.invoke(new UiApiCall("POST", "/processes/{processName}/init").withPathParam("processName", "storeSavedBulkLoadProfile")
         .withFormParam("values", JsonUtils.toJson(processValues)));
      String error = processError(response);
      if(error != null)
      {
         result.withOutcome(response == null || response.isSuccess() ? AiToolOutcome.ERROR : outcomeFor(response.status())).withSummary(shortText(error)).withPermissionCheck(call.permissionCheck())
            .withModelContent("The profile was not saved: " + error);
         return;
      }
      Integer id = ValueUtils.getValueAsInteger(map(map(list(map(response.json().get("values")).get("savedBulkLoadProfileList")).stream().findFirst().orElse(null)).get("values")).get("id"));

      List<String> shared = new ArrayList<>();
      List<String> failed = new ArrayList<>();
      for(Object share : list(values.get("shares")))
      {
         Map<String, Object> s = map(share);
         Map<String, Object> shareValues = new LinkedHashMap<>();
         shareValues.put("tableName", PROFILE_TABLE);
         shareValues.put("recordId", id);
         shareValues.put("audienceType", s.get("audienceType"));
         shareValues.put("audienceId", s.get("audienceId"));
         shareValues.put("scopeId", s.get("scope") == null ? "READ_ONLY" : s.get("scope"));
         UiApiResult shareResponse = call.invoke(new UiApiCall("POST", "/processes/{processName}/init").withPathParam("processName", "insertSharedRecord")
            .withFormParam("values", JsonUtils.toJson(shareValues)));
         String shareError = processError(shareResponse);
         (shareError == null ? shared : failed).add(s.get("audienceType") + " " + s.get("audienceId") + (shareError == null ? "" : " (" + shortText(shareError) + ")"));
      }

      String summary = "Saved profile \"" + values.get("label") + "\" (id " + id + ")" + (shared.isEmpty() ? "" : ", shared with " + String.join(", ", shared))
         + (failed.isEmpty() ? "" : "; not shared with " + String.join(", ", failed));
      result.withOutcome(AiToolOutcome.OK).withRowCount(1).withSummary(shortText(summary)).withPermissionCheck(call.permissionCheck())
         .withModelContent(JsonUtils.toJson(Map.of("id", id == null ? "" : id, "label", values.get("label"), "shared", shared, "notShared", failed)));
   }



   /*******************************************************************************
    ** Confirmed run: through the runner seam.
    *******************************************************************************/
   private void run(Call call, AiToolResult result, UiApiToolbox.Context context, String tableName, Map<String, Object> values) throws QException
   {
      BulkLoadRunner.RunHandle handle = runner.start(new BulkLoadRunner.Request(context.sessionUUID(), context.userTimezone(), context.gateway(), tableName,
         String.valueOf(values.get("uploadRef")), ValueUtils.getValueAsInteger(values.get("profileId")), context.credentials(), context.turnId()));
      call.operation = handle.operation();
      call.httpStatus = handle.httpStatus();
      boolean ok = !"FAILED".equals(handle.status()) && !"NOT_AVAILABLE".equals(handle.status());
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("status", handle.status());
      out.put("jobId", handle.jobId());
      out.put("processUUID", handle.processUUID());
      out.put("summary", handle.summary());
      result.withOutcome(ok ? AiToolOutcome.OK : AiToolOutcome.ERROR).withSummary(shortText(handle.summary())).withPermissionCheck(call.permissionCheck())
         .withModelContent(JsonUtils.toJson(out));
   }



   ////////////////////////////////////////////////////////////////////////////
   // helpers                                                                //
   ////////////////////////////////////////////////////////////////////////////



   /*******************************************************************************
    ** A tool's input or result as kept in the tool-call log: profiles and
    ** counts, never sample values, example bullets or value mappings.
    *******************************************************************************/
   public static String forLog(String json)
   {
      if(json == null)
      {
         return (null);
      }
      try
      {
         int start = json.indexOf('{');
         int end   = json.lastIndexOf('}');
         if(start < 0 || end < start)
         {
            return (json);
         }
         JSONObject object = new JSONObject(json.substring(start, end + 1));
         strip(object);
         return (object.toString());
      }
      catch(RuntimeException e)
      {
         return ("{\"note\":\"not logged\"}");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void strip(Object node)
   {
      if(node instanceof JSONObject object)
      {
         for(String key : new ArrayList<>(object.keySet()))
         {
            if(LOG_STRIPPED_KEYS.contains(key))
            {
               object.remove(key);
            }
            else
            {
               strip(object.get(key));
            }
         }
      }
      else if(node instanceof JSONArray array)
      {
         for(int i = 0; i < array.length(); i++)
         {
            strip(array.get(i));
         }
      }
   }



   /*******************************************************************************
    ** A compact render for the panel (counts and error groups, no values).
    *******************************************************************************/
   private static Map<String, Object> render(String type, String tableName, Map<String, Object> json)
   {
      Map<String, Object> render = new LinkedHashMap<>();
      render.put("type", type);
      render.put("tableName", tableName);
      render.put("mode", json.get("mode"));
      render.put("okCount", json.get("okCount"));
      render.put("errorCount", json.get("errorCount"));
      List<Map<String, Object>> errors = new ArrayList<>();
      for(Object error : list(json.get("errors")))
      {
         Map<String, Object> e = map(error);
         errors.add(new LinkedHashMap<>(Map.of("rows", String.valueOf(e.get("rowNumbers")), "field", String.valueOf(e.get("fieldName")), "message", String.valueOf(e.get("message")))));
      }
      render.put("errors", errors);
      return (render);
   }



   /*******************************************************************************
    ** The error of a process init call (HTTP or the process's ERROR type), or
    ** null when it completed.
    *******************************************************************************/
   private static String processError(UiApiResult response)
   {
      if(response == null)
      {
         return ("No response.");
      }
      if(!response.isSuccess())
      {
         return (response.errorMessage() == null ? "HTTP " + response.status() : response.errorMessage());
      }
      Map<String, Object> json = response.json();
      if("ERROR".equals(json.get("type")))
      {
         return (String.valueOf(json.getOrDefault("userFacingError", json.getOrDefault("error", "The process failed."))));
      }
      return (null);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void fail(AiToolResult result, Call call, UiApiResult response)
   {
      if(response == null)
      {
         result.withOutcome(AiToolOutcome.ERROR).withSummary("Not available")
            .withModelContent("This server does not offer " + call.operation + " yet (it needs a newer QQQ). Use the table's Bulk Load screen instead.");
         return;
      }
      AiToolOutcome kind    = outcomeFor(response.status());
      String        message = response.errorMessage() == null ? "HTTP " + response.status() : response.errorMessage();
      result.withOutcome(kind).withSummary(kind == AiToolOutcome.DENIED ? "Not allowed" : shortText(message)).withPermissionCheck(call.permissionCheck())
         .withModelContent(call.operation + " answered HTTP " + response.status() + ": " + shortText(message));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static AiToolOutcome outcomeFor(int status)
   {
      return (switch(status)
      {
         case 401, 403 -> AiToolOutcome.DENIED;
         case 404 -> AiToolOutcome.NOT_FOUND;
         case 400, 409, 422 -> AiToolOutcome.INVALID_INPUT;
         default -> AiToolOutcome.ERROR;
      });
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void deny(AiToolResult result, String check, String message)
   {
      result.withOutcome(AiToolOutcome.DENIED).withSummary("Not allowed").withPermissionCheck(check).withModelContent(message);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String required(Map<String, Object> in, String name) throws AiToolException
   {
      if(in.get(name) instanceof String s && StringUtils.hasContent(s))
      {
         return (s.strip());
      }
      throw (AiToolException.invalidInput(name + " is required."));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Integer requiredInteger(Map<String, Object> in, String name) throws AiToolException
   {
      try
      {
         Integer value = ValueUtils.getValueAsInteger(in.get(name));
         if(value != null)
         {
            return (value);
         }
      }
      catch(RuntimeException e)
      {
         // fall through
      }
      throw (AiToolException.invalidInput(name + " must be a number."));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static Map<String, Object> requiredMap(Map<String, Object> in, String name) throws AiToolException
   {
      Object value = in.get(name);
      if(value instanceof String s && s.strip().startsWith("{"))
      {
         return (new JSONObject(s).toMap());
      }
      if(value instanceof Map<?, ?> m && !m.isEmpty())
      {
         return ((Map<String, Object>) m);
      }
      throw (AiToolException.invalidInput(name + " is required (a JSON object)."));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<Object> fieldList(Object mappingJson)
   {
      if(mappingJson == null)
      {
         return (List.of());
      }
      try
      {
         return (new JSONObject(String.valueOf(mappingJson)).optJSONArray("fieldList") == null ? List.of()
            : new JSONObject(String.valueOf(mappingJson)).getJSONArray("fieldList").toList());
      }
      catch(RuntimeException e)
      {
         return (List.of());
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static Map<String, Object> map(Object value)
   {
      return (value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static List<Object> list(Object value)
   {
      return (value instanceof List<?> l ? (List<Object>) l : List.of());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String shortText(String text)
   {
      if(text == null)
      {
         return ("");
      }
      return (text.length() > 300 ? text.substring(0, 297) + "..." : text);
   }



   /*******************************************************************************
    ** One tool call's UI API invocations: the user's session, the route header,
    ** and the last operation and status (for the log).
    *******************************************************************************/
   private static class Call
   {
      private final UiApiToolbox.Context context;
      private final boolean              hipaaRoute;
      private String                     operation;
      private Integer                    httpStatus;



      /*******************************************************************************
       **
       *******************************************************************************/
      Call(UiApiToolbox.Context context, boolean hipaaRoute)
      {
         this.context = context;
         this.hipaaRoute = hipaaRoute;
      }



      /*******************************************************************************
       ** Invoke, or null when the server does not offer the operation (an
       ** older QQQ).
       *******************************************************************************/
      UiApiResult invoke(UiApiCall uiApiCall) throws QException
      {
         operation = uiApiCall.operation();
         if(!context.gateway().isInvocable(uiApiCall.getMethod(), uiApiCall.getPathTemplate()))
         {
            return (null);
         }
         context.prepare(uiApiCall).withInvokedBy("assistant")
            .withHeader(ROUTE_HEADER, hipaaRoute ? "hipaa" : "standard");
         UiApiResult response = context.gateway().invoke(uiApiCall);
         httpStatus = response.status();
         return (response);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      String permissionCheck()
      {
         return ("uiApi:" + operation + ":" + httpStatus);
      }
   }
}
