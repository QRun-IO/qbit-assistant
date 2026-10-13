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

package com.kingsrook.qbits.assistant.metadata;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantModelRouting;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantFeedback;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantModelPrice;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.ChildRecordListRenderer;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.DisplayFormat;
import com.kingsrook.qqq.backend.core.model.metadata.fields.FieldAdornment;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinOn;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.PVSValueFormatAndFields;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Tier;


/*******************************************************************************
 ** Record screens (create, edit, view) for the Assistant Admin tables.
 **
 ** - Named sections instead of QQQ's generic "Identity / Other Fields", with
 **   any field not placed in one kept in a final "Details" section, so a new
 **   column always shows.
 ** - JSON columns use the JSON code viewer; cost columns are micro-dollars
 **   and say so; counts use thousands separators.
 ** - Message, turn and tool-call ids link to their records; a conversation
 **   lists its messages, turns, tool calls and feedback.
 ** - Policy: the effort is picked from the supported levels, every limit has
 **   help text, and the conversation status (the assistant's own) is
 **   read-only.
 *******************************************************************************/
public final class AssistantRecordScreens
{
   public static final String EFFORT_PVS_NAME   = "assistant_effort";
   public static final String DETAILS_SECTION   = "details";
   static final String        JSON              = "json";
   static final String        MICROS_HELP       = "In micro-dollars: 1,000,000 is $1.00.";
   static final String        CHILD_LIST_PREFIX = "conversation";

   public static final String PROVIDER_TYPE_PVS_NAME = "assistant_provider_type";
   public static final String MODEL_PVS_NAME         = "assistant_model";

   private static final List<String> DATES             = List.of("createDate", "modifyDate");
   private static final List<String> USAGE_LIST_FIELDS = List.of("purpose", "status", "model", "inputTokens", "outputTokens", "costMicros", "latencyMs", "createDate");



   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private AssistantRecordScreens()
   {
   }



   /*******************************************************************************
    ** Possible-value sources the screens use: the effort levels, and the turn
    ** and message tables (for their id fields).
    *******************************************************************************/
   public static List<QPossibleValueSource> buildPossibleValueSources()
   {
      List<QPossibleValue<?>> efforts = new ArrayList<>();
      for(String effort : List.of("low", "medium", "high", "xhigh", "max"))
      {
         if(AssistantModelRouting.EFFORTS.contains(effort))
         {
            efforts.add(new QPossibleValue<>(effort, effort.substring(0, 1).toUpperCase() + effort.substring(1)));
         }
      }

      return (List.of(
         new QPossibleValueSource().withName(EFFORT_PVS_NAME).withType(QPossibleValueSourceType.ENUM)
            .withValueFormatAndFields(PVSValueFormatAndFields.LABEL_ONLY).withEnumValues(efforts),
         QPossibleValueSource.newForTable(AssistantTurn.TABLE_NAME),
         QPossibleValueSource.newForTable(AssistantMessage.TABLE_NAME)));
   }



   /*******************************************************************************
    ** Sections, viewers, links and help for one table (by its name).
    *******************************************************************************/
   public static void apply(QTableMetaData table)
   {
      switch(table.getName())
      {
         case AssistantConversation.TABLE_NAME -> conversation(table);
         case AssistantMessage.TABLE_NAME -> message(table);
         case AssistantTurn.TABLE_NAME -> turn(table);
         case AssistantTurnEvent.TABLE_NAME -> turnEvent(table);
         case AssistantToolCall.TABLE_NAME -> toolCall(table);
         case AssistantFeedback.TABLE_NAME -> feedback(table);
         case AssistantPolicy.TABLE_NAME -> policy(table);
         case AssistantUsageDaily.TABLE_NAME -> usageDaily(table);
         case AssistantProvider.TABLE_NAME -> provider(table);
         case AssistantModelPrice.TABLE_NAME -> price(table);
         case AssistantUsage.TABLE_NAME -> usage(table);
         default ->
         {
            // not an assistant table
         }
      }
   }



   /*******************************************************************************
    ** A conversation's messages, turns, tool calls and feedback, listed on it
    ** (joins on conversationId, read-only child lists).
    *******************************************************************************/
   public static void addConversationChildLists(QInstance qInstance)
   {
      QTableMetaData conversation = qInstance.getTable(AssistantConversation.TABLE_NAME);
      if(conversation == null)
      {
         return;
      }

      childList(qInstance, conversation, AssistantMessage.TABLE_NAME, "Messages", "chat", List.of("seq", "role", "status", "model", "createDate"));
      childList(qInstance, conversation, AssistantTurn.TABLE_NAME, "Turns", "sync_alt", List.of("status", "model", "toolCallCount", "costMicros", "startedAt"));
      childList(qInstance, conversation, AssistantToolCall.TABLE_NAME, "Tool Calls", "build", List.of("toolName", "outcome", "rowCount", "durationMs", "createDate"));
      childList(qInstance, conversation, AssistantFeedback.TABLE_NAME, "Feedback", "thumbs_up_down", List.of("rating", "reason", "createDate"));
      childList(qInstance, conversation, AssistantUsage.TABLE_NAME, "Model Calls", "receipt_long", USAGE_LIST_FIELDS);

      QTableMetaData provider = qInstance.getTable(AssistantProvider.TABLE_NAME);
      if(provider != null)
      {
         childList(qInstance, provider, AssistantUsage.TABLE_NAME, "Model Calls", "receipt_long", USAGE_LIST_FIELDS, "providerId", "provider");
      }
   }



   /*******************************************************************************
    ** Possible-value sources of the settings tables: the registered provider
    ** types, the models they offer (plus any priced model), and the providers.
    *******************************************************************************/
   public static List<QPossibleValueSource> buildSettingsPossibleValueSources(AssistantQBitConfig config)
   {
      List<QPossibleValue<?>> types  = new ArrayList<>();
      Set<String>             models = new LinkedHashSet<>();
      for(AssistantProviderType type : config.getProviderTypes().values())
      {
         types.add(new QPossibleValue<>(type.getType(), type.getLabel()));
         models.addAll(type.getModels());
      }
      if(config.getPricing() != null)
      {
         models.addAll(config.getPricing().keySet());
      }
      if(types.isEmpty())
      {
         ////////////////////////////////////////////////////////////////////
         // no type registered: offer the configured provider's name, so   //
         // the field still has a value (rows of it cannot be used)        //
         ////////////////////////////////////////////////////////////////////
         types.add(new QPossibleValue<>(config.getProviderName(), config.getProviderName()));
      }
      List<QPossibleValue<?>> modelValues = new ArrayList<>();
      models.forEach(m -> modelValues.add(new QPossibleValue<>(m, m)));

      return (List.of(
         new QPossibleValueSource().withName(PROVIDER_TYPE_PVS_NAME).withType(QPossibleValueSourceType.ENUM).withIdType(QFieldType.STRING)
            .withValueFormatAndFields(PVSValueFormatAndFields.LABEL_ONLY).withEnumValues(types),
         new QPossibleValueSource().withName(MODEL_PVS_NAME).withType(QPossibleValueSourceType.ENUM).withIdType(QFieldType.STRING)
            .withValueFormatAndFields(PVSValueFormatAndFields.LABEL_ONLY).withEnumValues(modelValues),
         QPossibleValueSource.newForTable(AssistantProvider.TABLE_NAME)));
   }



   /*******************************************************************************
    ** The provider settings screen.
    *******************************************************************************/
   private static void provider(QTableMetaData table)
   {
      pvs(table, "providerType", PROVIDER_TYPE_PVS_NAME, "Type");
      pvs(table, "model", MODEL_PVS_NAME, "Model");
      table.getField("isDefault").withLabel("Default");
      table.getField("apiKeyEnvName").withLabel("API Key Variable");
      table.getField("baseUrl").withLabel("Base URL Override");
      table.getField("monthlyCostCapUsd").withLabel("Monthly Cost Cap (USD)").withDisplayFormat(DisplayFormat.CURRENCY);
      counts(table, "maxOutputTokensPerRequest", "lastTestLatencyMs");
      table.getField("lastTestLatencyMs").withLabel("Last Test Latency (ms)");
      help(table, "name", "A name for this provider, e.g. Claude (production).");
      help(table, "providerType", "Which kind of provider this is. Only the types installed on this server are offered.");
      help(table, "model", "The model answers come from. Empty uses the policy's (or the application's) main model.");
      help(table, "enabled", "Off: the assistant never uses this provider.");
      help(table, "isDefault", "The assistant uses the default enabled provider (else the first enabled one). Marking one default unmarks the others.");
      help(table, "apiKeyEnvName", "The NAME of the environment variable that holds the API key (e.g. ANTHROPIC_API_KEY), never the key itself. "
         + "The key stays in the server's secret store; only allowed variable names are accepted. Empty uses the type's default variable. To replace the key, change it in the secret store.");
      help(table, "baseUrl", "Leave empty to use the provider's own API. Other URLs must be allowed in the server's configuration.");
      help(table, "monthlyCostCapUsd", "When this provider's cost this month (UTC) reaches the cap, new requests are refused. Empty: no cap.");
      help(table, "maxOutputTokensPerRequest", "The most output tokens one model call may produce. Empty uses the configured limit.");
      help(table, "maxConcurrentRequests", "How many model calls may run at the same time on this server. Empty: no limit.");
      table.getField(ProviderService.KEY_STATUS_FIELD).withLabel("API Key").withHelpContent(new QHelpContent("Whether the key variable is set on this server, with the key's last four characters. The key itself is never shown or returned."));
      sections(table,
         section("provider", "Provider", "settings_input_component", Tier.T1, "id", "name", "providerType", "model", "enabled", "isDefault"),
         section("connection", "Connection", "key", Tier.T2, "apiKeyEnvName", ProviderService.KEY_STATUS_FIELD, "baseUrl"),
         section("limits", "Limits", "speed", Tier.T2, "monthlyCostCapUsd", "maxOutputTokensPerRequest", "maxConcurrentRequests"),
         section("lastTest", "Last connection test", "network_check", Tier.T2, "lastTestAt", "lastTestStatus", "lastTestLatencyMs", "lastTestModel", "lastTestError"));
   }



   /*******************************************************************************
    ** The price table.
    *******************************************************************************/
   private static void price(QTableMetaData table)
   {
      pvs(table, "providerType", PROVIDER_TYPE_PVS_NAME, "Provider Type");
      for(String field : List.of("inputUsdPerMTok", "outputUsdPerMTok", "cacheWrite5mUsdPerMTok", "cacheWrite1hUsdPerMTok"))
      {
         help(table, field, "USD per million tokens.");
      }
      table.getField("inputUsdPerMTok").withLabel("Input (USD / MTok)");
      table.getField("outputUsdPerMTok").withLabel("Output (USD / MTok)");
      table.getField("cacheReadUsdPerMTok").withLabel("Cache Read (USD / MTok)");
      table.getField("cacheWrite5mUsdPerMTok").withLabel("Cache Write 5m (USD / MTok)");
      table.getField("cacheWrite1hUsdPerMTok").withLabel("Cache Write 1h (USD / MTok)");
      help(table, "model", "The exact model id, e.g. claude-sonnet-5-5.");
      help(table, "cacheReadUsdPerMTok", "USD per million tokens. Empty: 0.1 x the input price.");
      sections(table,
         section("price", "Model", "sell", Tier.T1, "id", "providerType", "model"),
         section("prices", "Prices", "payments", Tier.T2, "inputUsdPerMTok", "outputUsdPerMTok", "cacheReadUsdPerMTok", "cacheWrite5mUsdPerMTok", "cacheWrite1hUsdPerMTok"),
         section("notes", "Notes", "notes", Tier.T3, "notes"));
   }



   /*******************************************************************************
    ** One model call.
    *******************************************************************************/
   private static void usage(QTableMetaData table)
   {
      link(table, "turnId", AssistantTurn.TABLE_NAME, "Turn");
      link(table, "providerId", AssistantProvider.TABLE_NAME, "Provider");
      micros(table, "costMicros");
      counts(table, "inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens", "latencyMs");
      table.getField("latencyMs").withLabel("Latency (ms)");
      help(table, "purpose", "TURN: a step of an answer. TEST: a Test connection call.");
      sections(table,
         section("call", "Model Call", "receipt_long", Tier.T1, "id", "purpose", "status", "model", "providerId", "providerType", "userId", "conversationId", "turnId"),
         section("result", "Result", "fact_check", Tier.T2, "latencyMs", "errorKind", "httpStatus", "requestId"),
         section("usage", "Tokens and cost", "query_stats", Tier.T2, "inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens", "costMicros"));
   }



   /*******************************************************************************
    ** A field picked from a possible-value source.
    *******************************************************************************/
   private static void pvs(QTableMetaData table, String fieldName, String pvsName, String label)
   {
      QFieldMetaData field = table.getFields().get(fieldName);
      if(field != null)
      {
         field.withPossibleValueSourceName(pvsName).withLabel(label);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void conversation(QTableMetaData table)
   {
      readOnly(table, "status");
      json(table, "contextJson");
      micros(table, "totalCostMicros");
      counts(table, "totalTokensIn", "totalTokensOut", "unreadCount");
      sections(table,
         section("conversation", "Conversation", "forum", Tier.T1, "id", "title", "userId", "status", "pinned", "lastMessageAt", "unreadCount"),
         section("usage", "Usage", "query_stats", Tier.T2, "totalTokensIn", "totalTokensOut", "totalCostMicros"),
         section("context", "Context", "data_object", Tier.T3, "contextJson"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void message(QTableMetaData table)
   {
      json(table, "partsJson");
      link(table, "turnId", AssistantTurn.TABLE_NAME, "Turn");
      link(table, "parentMessageId", AssistantMessage.TABLE_NAME, "Parent Message");
      table.getField("partsJson").withLabel("Parts");
      sections(table,
         section("message", "Message", "chat", Tier.T1, "id", "conversationId", "role", "status", "seq", "model", "turnId", "parentMessageId", "userId"),
         section("parts", "Parts", "data_object", Tier.T2, "partsJson"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void turn(QTableMetaData table)
   {
      link(table, "userMessageId", AssistantMessage.TABLE_NAME, "User Message");
      link(table, "assistantMessageId", AssistantMessage.TABLE_NAME, "Assistant Message");
      micros(table, "costMicros");
      counts(table, "inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens", "toolCallCount", "modelCallCount");
      sections(table,
         section("turn", "Turn", "sync_alt", Tier.T1, "id", "conversationId", "status", "model", "startedAt", "endedAt", "userMessageId", "assistantMessageId", "userId"),
         section("outcome", "Outcome", "flag", Tier.T2, "stopReason", "cancelReason", "errorCode"),
         section("usage", "Usage", "query_stats", Tier.T2, "inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens", "costMicros", "toolCallCount", "modelCallCount"),
         section("provider", "Provider requests", "receipt_long", Tier.T3, "providerRequestIds"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void turnEvent(QTableMetaData table)
   {
      json(table, "dataJson");
      link(table, "turnId", AssistantTurn.TABLE_NAME, "Turn");
      table.getField("dataJson").withLabel("Data");
      sections(table,
         section("event", "Event", "stream", Tier.T1, "id", "turnId", "seq", "eventType", "userId"),
         section("data", "Data", "data_object", Tier.T2, "dataJson"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void toolCall(QTableMetaData table)
   {
      json(table, "inputJson");
      json(table, "resultJson");
      json(table, "redactionsJson");
      link(table, "turnId", AssistantTurn.TABLE_NAME, "Turn");
      counts(table, "rowCount", "durationMs");
      table.getField("inputJson").withLabel("Input");
      table.getField("resultJson").withLabel("Result");
      table.getField("redactionsJson").withLabel("Redactions");
      sections(table,
         section("call", "Tool Call", "build", Tier.T1, "id", "toolName", "outcome", "conversationId", "turnId", "toolCallId", "userId"),
         section("target", "Target and permission", "policy", Tier.T2, "tableName", "processName", "widgetName", "permissionCheck"),
         section("result", "Result", "fact_check", Tier.T2, "resultSummary", "rowCount", "durationMs"),
         section("payloads", "Payloads", "data_object", Tier.T3, "inputJson", "resultJson", "redactionsJson"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void feedback(QTableMetaData table)
   {
      link(table, "messageId", AssistantMessage.TABLE_NAME, "Message");
      sections(table,
         section("feedback", "Feedback", "thumbs_up_down", Tier.T1, "id", "rating", "reason", "comment"),
         section("about", "About", "chat", Tier.T2, "conversationId", "messageId", "userId"));
   }



   /*******************************************************************************
    ** The one table people edit: limits are in tokens or micro-dollars, soft
    ** limits warn and hard limits stop.
    *******************************************************************************/
   private static void policy(QTableMetaData table)
   {
      QFieldMetaData effort = table.getFields().get("mainEffort");
      if(effort != null)
      {
         effort.withPossibleValueSourceName(EFFORT_PVS_NAME).withLabel("Effort")
            .withHelpContent(new QHelpContent("How hard the main model thinks before answering; higher costs more and is slower. Empty uses the configured effort."));
      }
      help(table, "name", "A name for this policy. The assistant uses one policy, the one with the lowest id; other rows have no effect.");
      help(table, "mainModel", "The model id answers come from; empty uses the model configured for the application.");
      help(table, "killSwitch", "On: the assistant takes no new requests from anyone.");
      help(table, "killSwitchReason", "Shown to users while the kill switch is on.");
      for(String scope : List.of("userDaily", "instanceMonthly"))
      {
         String who = scope.equals("userDaily") ? "one user in a day" : "the whole instance in a month";
         help(table, scope + "TokensSoft", "Tokens " + who + " may use before a warning. Empty uses the configured limit.");
         help(table, scope + "TokensHard", "Tokens " + who + " may use before requests are refused. Empty uses the configured limit.");
         help(table, scope + "CostMicrosSoft", "Cost " + who + " may reach before a warning. Empty uses the configured limit. " + MICROS_HELP);
         help(table, scope + "CostMicrosHard", "Cost " + who + " may reach before requests are refused. Empty uses the configured limit. " + MICROS_HELP);
      }
      counts(table, "userDailyTokensSoft", "userDailyTokensHard", "userDailyCostMicrosSoft", "userDailyCostMicrosHard",
         "instanceMonthlyTokensSoft", "instanceMonthlyTokensHard", "instanceMonthlyCostMicrosSoft", "instanceMonthlyCostMicrosHard");
      sections(table,
         section("policy", "Policy", "policy", Tier.T1, "id", "name", "killSwitch", "killSwitchReason"),
         section("model", "Model", "smart_toy", Tier.T2, "mainModel", "mainEffort"),
         section("userDaily", "Per user, per day", "person", Tier.T2, "userDailyTokensSoft", "userDailyTokensHard", "userDailyCostMicrosSoft", "userDailyCostMicrosHard"),
         section("instanceMonthly", "Whole instance, per month", "domain", Tier.T2, "instanceMonthlyTokensSoft", "instanceMonthlyTokensHard", "instanceMonthlyCostMicrosSoft", "instanceMonthlyCostMicrosHard"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void usageDaily(QTableMetaData table)
   {
      micros(table, "costMicros");
      counts(table, "turnCount", "inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens");
      help(table, "scope", "USER rows are one user's day; the INSTANCE row (no user) is everyone's.");
      sections(table,
         section("usage", "Usage", "query_stats", Tier.T1, "id", "usageDate", "scope", "userId"),
         section("totals", "Totals", "functions", Tier.T2, "turnCount", "inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens", "costMicros"));
   }



   /*******************************************************************************
    ** Set a table's sections, then the dates, then a Details section with any
    ** field not placed (absent fields are skipped).
    *******************************************************************************/
   static void sections(QTableMetaData table, QFieldSection... sections)
   {
      Set<String>         placed = new LinkedHashSet<>();
      List<QFieldSection> result = new ArrayList<>();
      for(QFieldSection section : sections)
      {
         List<String> names = new ArrayList<>(section.getFieldNames());
         names.removeIf(name -> !table.getFields().containsKey(name) || placed.contains(name));
         placed.addAll(names);
         if(!names.isEmpty())
         {
            section.setFieldNames(names);
            result.add(section);
         }
      }

      List<String> dates = new ArrayList<>(DATES);
      dates.removeIf(name -> !table.getFields().containsKey(name) || placed.contains(name));
      placed.addAll(dates);

      List<String> rest = new ArrayList<>(table.getFields().keySet());
      rest.removeIf(placed::contains);
      if(!rest.isEmpty())
      {
         result.add(section(DETAILS_SECTION, "Details", "list", Tier.T2, rest.toArray(new String[0])));
      }
      if(!dates.isEmpty())
      {
         result.add(section("dates", "Dates", "calendar_month", Tier.T3, dates.toArray(new String[0])));
      }
      table.setSections(result);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static QFieldSection section(String name, String label, String icon, Tier tier, String... fieldNames)
   {
      return (new QFieldSection(name, label, new QIcon(icon), tier, new ArrayList<>(List.of(fieldNames))));
   }



   /*******************************************************************************
    ** A JSON column: TEXT with the JSON code viewer.
    *******************************************************************************/
   static void json(QTableMetaData table, String fieldName)
   {
      QFieldMetaData field = table.getFields().get(fieldName);
      if(field != null)
      {
         field.setType(QFieldType.TEXT);
         field.withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode(JSON)));
      }
   }



   /*******************************************************************************
    ** An id column shown as a link to its record.
    *******************************************************************************/
   static void link(QTableMetaData table, String fieldName, String tableName, String label)
   {
      QFieldMetaData field = table.getFields().get(fieldName);
      if(field != null)
      {
         field.withPossibleValueSourceName(tableName).withLabel(label);
      }
   }



   /*******************************************************************************
    ** Cost columns in micro-dollars.
    *******************************************************************************/
   static void micros(QTableMetaData table, String... fieldNames)
   {
      for(String fieldName : fieldNames)
      {
         QFieldMetaData field = table.getFields().get(fieldName);
         if(field != null)
         {
            String label = field.getLabel() == null ? QInstanceEnricher.nameToLabel(fieldName) : field.getLabel();
            field.withLabel(label.replace("Micros", "(micro-dollars)"));
            field.withDisplayFormat(DisplayFormat.COMMAS);
            help(table, fieldName, MICROS_HELP);
         }
      }
   }



   /*******************************************************************************
    ** Whole-number columns shown with thousands separators.
    *******************************************************************************/
   static void counts(QTableMetaData table, String... fieldNames)
   {
      for(String fieldName : fieldNames)
      {
         QFieldMetaData field = table.getFields().get(fieldName);
         if(field != null)
         {
            field.withDisplayFormat(DisplayFormat.COMMAS);
         }
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static void readOnly(QTableMetaData table, String... fieldNames)
   {
      for(String fieldName : fieldNames)
      {
         QFieldMetaData field = table.getFields().get(fieldName);
         if(field != null)
         {
            field.setIsEditable(false);
         }
      }
   }



   /*******************************************************************************
    ** Give a field help text, unless it has some (or is absent).
    *******************************************************************************/
   static void help(QTableMetaData table, String fieldName, String text)
   {
      QFieldMetaData field = table.getFields().get(fieldName);
      if(field != null && (field.getHelpContents() == null || field.getHelpContents().isEmpty()))
      {
         field.withHelpContent(new QHelpContent(text));
      }
   }



   /*******************************************************************************
    ** Join the conversation to a child table on conversationId and list the
    ** children on the conversation's view (read-only).
    *******************************************************************************/
   private static void childList(QInstance qInstance, QTableMetaData conversation, String childTableName, String label, String icon, List<String> fields)
   {
      childList(qInstance, conversation, childTableName, label, icon, fields, "conversationId", CHILD_LIST_PREFIX);
   }



   /*******************************************************************************
    ** Join a parent to a child table on one of the child's fields and list the
    ** children on the parent's view (read-only).
    *******************************************************************************/
   private static void childList(QInstance qInstance, QTableMetaData conversation, String childTableName, String label, String icon, List<String> fields, String joinField, String sectionPrefix)
   {
      if(qInstance.getTable(childTableName) == null)
      {
         return;
      }

      QJoinMetaData join = new QJoinMetaData()
         .withLeftTable(conversation.getName())
         .withRightTable(childTableName)
         .withInferredName()
         .withType(JoinType.ONE_TO_MANY)
         .withJoinOn(new JoinOn("id", joinField));
      if(qInstance.getJoin(join.getName()) != null)
      {
         return;
      }
      qInstance.addJoin(join);

      List<String> shown = new ArrayList<>(fields);
      shown.removeIf(name -> !qInstance.getTable(childTableName).getFields().containsKey(name));
      qInstance.addWidget(ChildRecordListRenderer.widgetMetaDataBuilder(join)
         .withLabel(label)
         .withMaxRows(50)
         .withCanAddChildRecord(false)
         .withOnlyIncludeFieldNames(shown)
         .getWidgetMetaData());

      String sectionName = sectionPrefix + label.replace(" ", "");
      conversation.addSection(new QFieldSection(sectionName, new QIcon(icon), Tier.T2).withLabel(label).withWidgetName(join.getName()));
   }
}
