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
import java.util.List;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.health.AssistantHealthWidget;
import com.kingsrook.qbits.assistant.home.AssistantAccessWidget;
import com.kingsrook.qbits.assistant.home.AssistantHome;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantBudgetEvent;
import com.kingsrook.qbits.assistant.model.AssistantBudgetOverride;
import com.kingsrook.qbits.assistant.model.AssistantBudgetUsage;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantDataAudit;
import com.kingsrook.qbits.assistant.model.AssistantFeedback;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantModelPrice;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantProfileSkill;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantRagSource;
import com.kingsrook.qbits.assistant.model.AssistantScheduledRun;
import com.kingsrook.qbits.assistant.model.AssistantScheduledTask;
import com.kingsrook.qbits.assistant.model.AssistantSkill;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.processes.AssistantRetentionStep;
import com.kingsrook.qqq.backend.core.actions.ai.AiExposure;
import com.kingsrook.qqq.backend.core.actions.ai.AiTableMetaData;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.ValueTooLongBehavior;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.DenyBehavior;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.QScheduleMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Builds the QBit's metadata (spec section 4, owner decision D6): standard,
 ** visible QQQ tables on the configured backend, the owner security key type,
 ** the conversation possible-value source, the retention process and the
 ** "Assistant" app (its home plus the main tables).
 **
 ** Security:
 ** - Every user-owned table carries userId and a record security lock on it
 **   (key type ownerSecurityKeyType, default assistant_user_id). The panel
 **   routes put the user's own id in their session, so reads and writes of
 **   these tables through QQQ actions only ever touch the user's own rows.
 ** - The key type's all-access key (assistant_user_id_all_access) lets admins see
 **   everyone's rows; hosts grant it to users holding assistant.admin with
 **   AssistantSecurity.applySessionKeys in their authentication customizer.
 ** - Tables, app and process need assistant.admin in the generic UI / API.
 ** - assistant_usage_daily rows with a null userId are the INSTANCE rollup and
 **   are readable by any session (the lock allows null).
 ** - The assistant's own tool kernel never exposes assistant_* tables to the
 **   model (exposure NONE by name).
 *******************************************************************************/
public class AssistantMetaDataBuilder
{
   public static final String APP_NAME               = "assistantAdmin";
   public static final String RETENTION_PROCESS_NAME = "assistantRetention";
   public static final String ALL_ACCESS_SUFFIX      = "_all_access";

   /*******************************************************************************
    ** The all-access key suffix before 0.1.0-RC.3 (assistant_user_id_all_access).
    ** See UPGRADE.md.
    *******************************************************************************/
   @Deprecated(since = "0.1.0-RC.3")
   public static final String LEGACY_ALL_ACCESS_SUFFIX = "AllAccess";

   /*******************************************************************************
    ** The tables in the app's nav, in order (the main tables). The others are
    ** child lists on these (messages, turns, tool calls, feedback and model
    ** calls on a conversation; model calls on a provider) or reached from the
    ** home. Processes are table and record actions, never nav entries.
    *******************************************************************************/
   public static final List<String> NAV_TABLES = List.of(AssistantScheduledTask.TABLE_NAME, AssistantRagSource.TABLE_NAME, AssistantBudget.TABLE_NAME, AssistantProvider.TABLE_NAME, AssistantProfile.TABLE_NAME, AssistantSkill.TABLE_NAME, AssistantDataAccess.TABLE_NAME, AssistantConversation.TABLE_NAME, AssistantModelPrice.TABLE_NAME, AssistantPolicy.TABLE_NAME);

   private final AssistantQBitConfig config;



   /*******************************************************************************
    **
    *******************************************************************************/
   public AssistantMetaDataBuilder(AssistantQBitConfig config)
   {
      this.config = config;
   }



   /*******************************************************************************
    ** The all-access key name of the owner key type.
    *******************************************************************************/
   public static String allAccessKeyName(String ownerKeyType)
   {
      return (ownerKeyType + ALL_ACCESS_SUFFIX);
   }



   /*******************************************************************************
    ** The owner security key type.
    *******************************************************************************/
   public QSecurityKeyType buildSecurityKeyType()
   {
      return (new QSecurityKeyType()
         .withName(config.getOwnerSecurityKeyType())
         .withAllAccessKeyName(allAccessKeyName(config.getOwnerSecurityKeyType())));
   }



   /*******************************************************************************
    ** All tables, conversations first.
    *******************************************************************************/
   public List<QTableMetaData> buildTables() throws QException
   {
      List<QTableMetaData> tables = new ArrayList<>();

      tables.add(owned(base(AssistantConversation.TABLE_NAME, "Assistant Conversation", "forum", AssistantConversation.class), false)
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("title")
         .withoutCapabilities(Capability.TABLE_INSERT));
      text(tables.get(0), "contextJson");

      tables.add(text(readOnly(owned(base(AssistantMessage.TABLE_NAME, "Assistant Message", "chat", AssistantMessage.class), false)), "partsJson")
         .withRecordLabelFormat("%s #%s").withRecordLabelFields("role", "id"));

      tables.add(text(readOnly(owned(base(AssistantTurn.TABLE_NAME, "Assistant Turn", "sync_alt", AssistantTurn.class), false)), "providerRequestIds")
         .withRecordLabelFormat("Turn #%s").withRecordLabelFields("id"));

      tables.add(text(readOnly(owned(base(AssistantTurnEvent.TABLE_NAME, "Assistant Turn Event", "stream", AssistantTurnEvent.class), false)), "dataJson")
         .withRecordLabelFormat("%s #%s").withRecordLabelFields("eventType", "seq"));

      tables.add(text(readOnly(owned(base(AssistantToolCall.TABLE_NAME, "Assistant Tool Call", "build", AssistantToolCall.class), false)), "inputJson", "resultJson", "redactionsJson")
         .withRecordLabelFormat("%s #%s").withRecordLabelFields("toolName", "id"));

      tables.add(text(readOnly(owned(base(AssistantFeedback.TABLE_NAME, "Assistant Feedback", "thumbs_up_down", AssistantFeedback.class), false)), "comment"));

      tables.add(base(AssistantPolicy.TABLE_NAME, "Assistant Policy", "policy", AssistantPolicy.class)
         .withRecordLabelFormat("%s").withRecordLabelFields("name"));

      tables.add(readOnly(owned(base(AssistantUsageDaily.TABLE_NAME, "Assistant Usage (Daily)", "query_stats", AssistantUsageDaily.class), true))
         .withUniqueKey(new UniqueKey("scope", "userId", "usageDate"))
         .withRecordLabelFormat("%s %s").withRecordLabelFields("scope", "usageDate"));

      tables.add(settings(base(AssistantProvider.TABLE_NAME, "Assistant Provider", "settings_input_component", AssistantProvider.class))
         .withUniqueKey(new UniqueKey("name"))
         .withRecordLabelFormat("%s").withRecordLabelFields("name"));

      QCodeReference scheduledCustomizer = new QCodeReference(AssistantScheduledTaskCustomizer.class);
      tables.add(text(base(AssistantScheduledTask.TABLE_NAME, "Scheduled tasks", "schedule", AssistantScheduledTask.class), "prompt")
         .withRecordLabelFormat("%s").withRecordLabelFields("name")
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, scheduledCustomizer).withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, scheduledCustomizer)
         .withCustomizer(TableCustomizers.POST_QUERY_RECORD, scheduledCustomizer));
      tables.add(readOnly(owned(base(AssistantScheduledRun.TABLE_NAME, "Scheduled runs", "history", AssistantScheduledRun.class), false))
         .withoutCapabilities(Capability.TABLE_DELETE)
         .withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.NOT_PROTECTED))
         .withUniqueKey(new UniqueKey("taskId", "scheduledFor"))
         .withRecordLabelFormat("Run #%s").withRecordLabelFields("id"));

      QCodeReference ragCustomizer = new QCodeReference(AssistantRagSourceCustomizer.class);
      tables.add(text(base(AssistantRagSource.TABLE_NAME, "Knowledge sources", "manage_search", AssistantRagSource.class), "filterJson")
         .withRecordLabelFormat("%s").withRecordLabelFields("name")
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, ragCustomizer).withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, ragCustomizer)
         .withCustomizer(TableCustomizers.POST_INSERT_RECORD, ragCustomizer).withCustomizer(TableCustomizers.POST_UPDATE_RECORD, ragCustomizer)
         .withCustomizer(TableCustomizers.POST_DELETE_RECORD, ragCustomizer));

      tables.add(settings(base(AssistantModel.TABLE_NAME, "Assistant Model", "memory", AssistantModel.class))
         .withUniqueKey(new UniqueKey("providerId", "modelId"))
         .withRecordLabelFormat("%s").withRecordLabelFields("modelId"));

      tables.add(settings(base(AssistantModelPrice.TABLE_NAME, "Assistant Model Price", "sell", AssistantModelPrice.class))
         .withUniqueKey(new UniqueKey("providerType", "model"))
         .withRecordLabelFormat("%s").withRecordLabelFields("model"));

      tables.add(readOnly(owned(base(AssistantUsage.TABLE_NAME, "Assistant Model Call", "receipt_long", AssistantUsage.class), false))
         .withRecordLabelFormat("%s #%s").withRecordLabelFields("purpose", "id"));

      tables.add(dataAccess(base(AssistantDataAccess.TABLE_NAME, "Assistant Data Access", "admin_panel_settings", AssistantDataAccess.class))
         .withUniqueKey(new UniqueKey("objectType", "objectName"))
         .withRecordLabelFormat("%s %s").withRecordLabelFields("objectType", "objectName"));

      tables.add(profiles(base(AssistantProfile.TABLE_NAME, "Assistant Profile", "face", AssistantProfile.class))
         .withUniqueKey(new UniqueKey("name"))
         .withRecordLabelFormat("%s").withRecordLabelFields("displayName"));
      text(tables.get(tables.size() - 1), "personality", "starterPrompts");

      tables.add(profiles(base(AssistantSkill.TABLE_NAME, "Assistant Skill", "school", AssistantSkill.class))
         .withUniqueKey(new UniqueKey("name"))
         .withRecordLabelFormat("%s").withRecordLabelFields("name"));
      text(tables.get(tables.size() - 1), "instructions");

      tables.add(profiles(base(AssistantProfileSkill.TABLE_NAME, "Assistant Profile Skill", "link", AssistantProfileSkill.class))
         .withUniqueKey(new UniqueKey("profileId", "skillId"))
         .withRecordLabelFormat("%s · %s").withRecordLabelFields("profileId", "skillId"));

      tables.add(readOnly(owned(base(AssistantPendingAction.TABLE_NAME, "Assistant Pending Action", "pending_actions", AssistantPendingAction.class), false))
         .withRecordLabelFormat("%s #%s").withRecordLabelFields("toolName", "id"));
      text(tables.get(tables.size() - 1), "paramsJson");

      tables.add(readOnly(owned(base(AssistantDataAudit.TABLE_NAME, "Assistant Data Audit", "shield", AssistantDataAudit.class), false))
         .withoutCapabilities(Capability.TABLE_DELETE)
         .withRecordLabelFormat("%s #%s").withRecordLabelFields("decision", "id"));
      for(QTableMetaData table : tables)
      {
         if(table.getName().equals(AssistantConversation.TABLE_NAME))
         {
            text(table, "title");
            table.getField("title").setMaxLength(null);
            table.getField("title").withBehavior(ValueTooLongBehavior.PASS_THROUGH);
         }
         if(table.getName().equals(AssistantToolCall.TABLE_NAME))
         {
            text(table, "resultSummary");
            table.getField("resultSummary").setMaxLength(null);
            table.getField("resultSummary").withBehavior(ValueTooLongBehavior.PASS_THROUGH);
         }
         if(table.getName().equals(AssistantPendingAction.TABLE_NAME))
         {
            text(table, "summary", "resultSummary");
            table.getField("summary").setMaxLength(null);
            table.getField("resultSummary").setMaxLength(null);
            table.getField("summary").withBehavior(ValueTooLongBehavior.PASS_THROUGH);
            table.getField("resultSummary").withBehavior(ValueTooLongBehavior.PASS_THROUGH);
         }
      }
      tables.add(readOnly(base(AssistantBudgetUsage.TABLE_NAME, "Budget Accounting", "receipt_long", AssistantBudgetUsage.class))
         .withoutCapabilities(Capability.TABLE_DELETE).withUniqueKey(new UniqueKey("usageId")));
      QCodeReference budgetCustomizer = new QCodeReference(AssistantBudgetCustomizer.class);
      tables.add(base(AssistantBudget.TABLE_NAME, "Budgets", "savings", AssistantBudget.class)
         .withRecordLabelFormat("%s").withRecordLabelFields("name")
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, budgetCustomizer)
         .withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, budgetCustomizer));
      tables.add(base(AssistantBudgetOverride.TABLE_NAME, "Budget Override", "approval", AssistantBudgetOverride.class)
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, budgetCustomizer)
         .withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, budgetCustomizer));
      tables.add(readOnly(base(AssistantBudgetEvent.TABLE_NAME, "Budget Event", "notifications", AssistantBudgetEvent.class))
         .withoutCapabilities(Capability.TABLE_DELETE)
         .withUniqueKey(new UniqueKey("budgetId", "scopeKey", "periodStart", "state")));
      tables.forEach(AssistantRecordScreens::apply);
      return (tables);
   }



   /*******************************************************************************
    ** A settings table: the customizer checks rows before a save, masks the
    ** key status after a read and drops the provider caches after a change.
    *******************************************************************************/
   private static QTableMetaData settings(QTableMetaData table)
   {
      QCodeReference customizer = new QCodeReference(AssistantSettingsCustomizer.class);
      return (table
         .withCustomizer(TableCustomizers.POST_QUERY_RECORD, customizer)
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, customizer)
         .withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, customizer)
         .withCustomizer(TableCustomizers.POST_INSERT_RECORD, customizer)
         .withCustomizer(TableCustomizers.POST_UPDATE_RECORD, customizer)
         .withCustomizer(TableCustomizers.POST_DELETE_RECORD, customizer));
   }



   /*******************************************************************************
    ** Profile, skill and link tables: checked before a save; a change drops the
    ** runtime's cached profiles.
    *******************************************************************************/
   private static QTableMetaData profiles(QTableMetaData table)
   {
      QCodeReference customizer = new QCodeReference(AssistantProfileCustomizer.class);
      return (table
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, customizer)
         .withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, customizer)
         .withCustomizer(TableCustomizers.POST_INSERT_RECORD, customizer)
         .withCustomizer(TableCustomizers.POST_UPDATE_RECORD, customizer)
         .withCustomizer(TableCustomizers.POST_DELETE_RECORD, customizer));
   }



   /*******************************************************************************
    ** The data access table: a change drops the runtime's cached modes.
    *******************************************************************************/
   private static QTableMetaData dataAccess(QTableMetaData table)
   {
      QCodeReference customizer = new QCodeReference(AssistantDataAccessCustomizer.class);
      return (table
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, customizer)
         .withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, customizer)
         .withCustomizer(TableCustomizers.POST_INSERT_RECORD, customizer)
         .withCustomizer(TableCustomizers.POST_UPDATE_RECORD, customizer)
         .withCustomizer(TableCustomizers.POST_DELETE_RECORD, customizer));
   }



   /*******************************************************************************
    ** Possible value source over conversations (for the conversationId fields).
    *******************************************************************************/
   public QPossibleValueSource buildConversationPossibleValueSource()
   {
      return (QPossibleValueSource.newForTable(AssistantConversation.TABLE_NAME));
   }



   /*******************************************************************************
    ** The retention process (spec section 4), scheduled daily when a scheduler
    ** is configured.
    *******************************************************************************/
   public QProcessMetaData buildRetentionProcess()
   {
      QProcessMetaData process = new QProcessMetaData()
         .withName(RETENTION_PROCESS_NAME)
         .withLabel("Assistant Retention")
         .withIcon(new QIcon("auto_delete"))
         .withTableName(AssistantConversation.TABLE_NAME)
         .withPermissionRules(adminRules())
         .withStep(new QBackendStepMetaData()
            .withName("purge")
            .withCode(new QCodeReference(AssistantRetentionStep.class)));

      if(StringUtils.hasContent(config.getRetentionSchedulerName()))
      {
         process.withSchedule(new QScheduleMetaData()
            .withSchedulerName(config.getRetentionSchedulerName())
            .withRepeatSeconds(24 * 60 * 60));
      }
      return (process);
   }



   /*******************************************************************************
    ** The Assistant app: the home widget, and the main tables in the nav.
    *******************************************************************************/
   public QAppMetaData buildAdminApp(List<QTableMetaData> tables)
   {
      QAppMetaData app = new QAppMetaData()
         .withName(APP_NAME)
         .withLabel("Assistant")
         .withIcon(new QIcon("smart_toy"))
         .withPermissionRules(adminRules())
         .withWidgets(List.of(AssistantHealthWidget.NAME, AssistantHome.WIDGET_NAME, AssistantAccessWidget.WIDGET_NAME));
      for(String name : NAV_TABLES)
      {
         tables.stream().filter(t -> t.getName().equals(name)).findFirst().ifPresent(app::withChild);
      }
      return (app);
   }



   /*******************************************************************************
    ** assistant.admin, through the custom checker. The level and deny behavior
    ** are explicit: left null, the enricher copies the host's default level
    ** (often NOT_PROTECTED), and the meta-data path (app tree, table list) then
    ** allows the object without ever calling the checker, so every user would
    ** see the Assistant app and its tables in the nav.
    *******************************************************************************/
   public static QPermissionRules adminRules()
   {
      return (new QPermissionRules()
         .withLevel(PermissionLevel.HAS_ACCESS_PERMISSION)
         .withDenyBehavior(DenyBehavior.HIDDEN)
         .withCustomPermissionChecker(new QCodeReference(AssistantAdminPermissionChecker.class)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private QTableMetaData base(String name, String label, String icon, Class<? extends QRecordEntity> entity) throws QException
   {
      QTableMetaData table = new QTableMetaData()
         .withName(name)
         .withLabel(label)
         .withIcon(new QIcon(icon))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withPermissionRules(adminRules())
         .withFieldsFromEntity(entity);

      //////////////////////////////////////////////////////////////////
      // the assistant never reads its own tables (settings, audit)    //
      //////////////////////////////////////////////////////////////////
      AiTableMetaData.ofOrWithNew(table).withExposure(AiExposure.NONE);

      QFieldMetaData conversationId = table.getFields().get("conversationId");
      if(conversationId != null)
      {
         conversationId.setPossibleValueSourceName(AssistantConversation.TABLE_NAME);
      }
      return (table);
   }



   /*******************************************************************************
    ** Lock the table to its owner (userId).
    *******************************************************************************/
   private QTableMetaData owned(QTableMetaData table, boolean allowNullOwner)
   {
      return (table.withRecordSecurityLock(new RecordSecurityLock()
         .withSecurityKeyType(config.getOwnerSecurityKeyType())
         .withFieldName("userId")
         .withNullValueBehavior(allowNullOwner ? RecordSecurityLock.NullValueBehavior.ALLOW : RecordSecurityLock.NullValueBehavior.DENY)));
   }



   /*******************************************************************************
    ** Only the qbit writes these (admins may still delete rows).
    *******************************************************************************/
   private static QTableMetaData readOnly(QTableMetaData table)
   {
      return (table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE));
   }



   /*******************************************************************************
    ** Unbounded text columns.
    *******************************************************************************/
   private static QTableMetaData text(QTableMetaData table, String... names)
   {
      for(String name : names)
      {
         table.getField(name).setType(QFieldType.TEXT);
      }
      return (table);
   }

}
