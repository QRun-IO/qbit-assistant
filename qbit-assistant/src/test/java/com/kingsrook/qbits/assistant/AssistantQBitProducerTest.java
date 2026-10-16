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

package com.kingsrook.qbits.assistant;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.assistant.metadata.AssistantAdminPermissionChecker;
import com.kingsrook.qbits.assistant.metadata.AssistantInstanceMetaData;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionCheckResult;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.actions.metadata.MetaDataInput;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.DenyBehavior;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Production of the QBit: tables (standard, visible, locked), key type,
 ** app, process, runtime, routes; config validation.
 *******************************************************************************/
class AssistantQBitProducerTest extends BaseAssistantTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testProducedMetaData()
   {
      List<String> owned = List.of("assistant_conversation", "assistant_message", "assistant_turn", "assistant_turn_event", "assistant_tool_call", "assistant_feedback", "assistant_usage_daily", "assistant_usage", "assistant_data_audit");
      for(String name : owned)
      {
         QTableMetaData table = qInstance.getTable(name);
         assertThat(table).as(name).isNotNull();
         assertThat(table.getIsHidden()).as(name + " visible").isFalse();
         assertThat(table.getBackendName()).isEqualTo(AssistantTestFixture.BACKEND);
         assertThat(table.getSourceQBitName()).isNotNull();
         RecordSecurityLock lock = table.getRecordSecurityLocks().get(0);
         assertThat(lock.getFieldName()).isEqualTo("userId");
         assertThat(lock.getSecurityKeyType()).isEqualTo(AssistantQBitConfig.DEFAULT_OWNER_KEY_TYPE);
         assertThat(table.getPermissionRules().getCustomPermissionChecker().getName()).isEqualTo(AssistantAdminPermissionChecker.class.getName());
      }
      assertThat(qInstance.getTable(AssistantUsageDaily.TABLE_NAME).getRecordSecurityLocks().get(0).getNullValueBehavior()).isEqualTo(RecordSecurityLock.NullValueBehavior.ALLOW);
      assertThat(qInstance.getTable(AssistantPolicy.TABLE_NAME).getRecordSecurityLocks()).isNullOrEmpty();

      QSecurityKeyType keyType = qInstance.getSecurityKeyType(AssistantQBitConfig.DEFAULT_OWNER_KEY_TYPE);
      assertThat(keyType.getAllAccessKeyName()).isEqualTo("assistant_user_id_all_access");

      //////////////////////////////////////////////////////////////////////
      // the nav: the home plus the main tables; processes are actions    //
      //////////////////////////////////////////////////////////////////////
      assertThat(qInstance.getApp(AssistantMetaDataBuilder.APP_NAME).getChildren()).extracting(c -> c.getName())
         .containsExactly("assistant_scheduled_task", "assistant_rag_source", "assistant_budget", "assistant_provider", "assistant_profile", "assistant_skill", "assistant_data_access", "assistant_conversation", "assistant_client_error", "assistant_model_price", "assistant_policy");
      assertThat(qInstance.getApp(AssistantMetaDataBuilder.APP_NAME).getWidgets()).containsExactly("assistantHealth", "assistantHome", "assistantAccess");
      assertThat(qInstance.getProcess(AssistantMetaDataBuilder.RETENTION_PROCESS_NAME).getTableName()).isEqualTo("assistant_conversation");
      assertThat(qInstance.getProcess(AssistantMetaDataBuilder.RETENTION_PROCESS_NAME)).isNotNull();
      assertThat(qInstance.getProcess(AssistantMetaDataBuilder.RETENTION_PROCESS_NAME).getSchedule()).isNull();
      assertThat(qInstance.getPossibleValueSource(AssistantConversation.TABLE_NAME)).isNotNull();
      assertThat(qInstance.getQBits().values()).anyMatch(q -> "assistant".equals(q.getArtifactId()));

      assertThat(AssistantInstanceMetaData.of(qInstance)).isNotNull();
      assertThat(AssistantInstanceMetaData.of(qInstance).getVersion()).isEqualTo(1);
      assertThat(AssistantInstanceMetaData.of(qInstance).getBasePath()).isEqualTo("/qqq/v1/assistant");
      assertThat(config.getRuntime()).isNotNull();
      assertThat(AssistantRuntime.of(qInstance)).isSameAs(config.getRuntime());
      assertThat(AssistantQBitProducer.getVersion()).isNotBlank();
   }



   /*******************************************************************************
    ** Budget types are discoverable through core with admin-only visibility.
    *******************************************************************************/
   @Test
   void budgetAlertTypes()
   {
      var types = AlertsInstanceMetaData.of(qInstance).getAlertTypes();
      assertThat(types).containsOnlyKeys("assistant.budget.warning", "assistant.budget.downgraded", "assistant.budget.stopped");
      for(QAlertTypeMetaData type : types.values())
      {
         assertThat(type.getLabel()).isNotBlank();
         assertThat(type.getDescription()).isNotBlank();
         assertThat(type.getDefaultSeverity()).isEqualTo(type.getName().endsWith("stopped") ? AlertSeverity.ERROR : AlertSeverity.WARNING);
         assertThat(type.getHowToFix()).isEqualTo("Raise the budget or grant an override in Assistant → Budgets");
         assertThat(type.getSourceQBitName()).isEqualTo("assistant");
         assertThat(type.getVisibilityPermission()).isEqualTo("assistant.admin");
         assertThat(type.getSubjectTableName()).isEqualTo("assistant_budget");
      }
   }



   /*******************************************************************************
    ** The admin app and tables: visible only with assistant.admin.
    *******************************************************************************/
   @Test
   void testAdminPermissionChecker() throws Exception
   {
      AssistantAdminPermissionChecker checker = new AssistantAdminPermissionChecker();

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      checker.checkPermissionsThrowing(new MetaDataInput(), qInstance.getTable(AssistantConversation.TABLE_NAME));
      QContext.clear();

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.EDITOR));
      assertThatThrownBy(() -> checker.checkPermissionsThrowing(new MetaDataInput(), qInstance.getTable(AssistantConversation.TABLE_NAME)))
         .isInstanceOf(QPermissionDeniedException.class);
      QContext.clear();
   }



   /*******************************************************************************
    ** On a host whose default permission level is NOT_PROTECTED (enriched),
    ** the meta-data path hides the admin app, tables and retention process from
    ** a user without assistant.admin, and shows them to an admin.
    *******************************************************************************/
   @Test
   void testAdminObjectsHiddenInMetaDataOnUnprotectedHost() throws Exception
   {
      QInstance host = AssistantTestFixture.build(config);
      host.setDefaultPermissionRules(new QPermissionRules().withLevel(PermissionLevel.NOT_PROTECTED).withDenyBehavior(DenyBehavior.DISABLED));
      new QInstanceEnricher(host).enrich();

      List<MetaDataWithPermissionRules> adminObjects = List.of(host.getApp(AssistantMetaDataBuilder.APP_NAME),
         host.getProcess(AssistantMetaDataBuilder.RETENTION_PROCESS_NAME), host.getTable(AssistantPolicy.TABLE_NAME), host.getTable(AssistantConversation.TABLE_NAME));

      QContext.init(host, AssistantTestFixture.sessionFor(AssistantTestFixture.EDITOR));
      for(MetaDataWithPermissionRules object : adminObjects)
      {
         assertThat(PermissionsHelper.getPermissionCheckResult(new MetaDataInput(), object)).as(object.getName()).isEqualTo(PermissionCheckResult.DENY_HIDE);
      }
      QContext.clear();

      QContext.init(host, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      for(MetaDataWithPermissionRules object : adminObjects)
      {
         assertThat(PermissionsHelper.getPermissionCheckResult(new MetaDataInput(), object)).as(object.getName()).isEqualTo(PermissionCheckResult.ALLOW);
      }
      QContext.clear();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSessionKeys()
   {
      QSession editor = AssistantTestFixture.sessionFor(AssistantTestFixture.EDITOR);
      AssistantSecurity.applySessionKeys(editor, config);
      AssistantSecurity.applySessionKeys(editor, config);
      assertThat(editor.getSecurityKeyValues(AssistantQBitConfig.DEFAULT_OWNER_KEY_TYPE)).containsExactly(AssistantTestFixture.EDITOR);
      assertThat(editor.getSecurityKeyValues("assistant_user_id_all_access")).isNullOrEmpty();

      QSession admin = new QSession().withUser(new QUser().withIdReference("boss"));
      admin.withPermission(AssistantQBitConfig.ADMIN_PERMISSION);
      AssistantSecurity.applySessionKeys(admin, config);
      assertThat(admin.getSecurityKeyValues("assistant_user_id_all_access")).containsExactly(true);

      AssistantSecurity.applySessionKeys(null, config);
      assertThat(AssistantSecurity.userId(new QSession())).isNull();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testValidation()
   {
      List<String> errors = new ArrayList<>();
      new AssistantQBitConfig().validate(null, errors);
      assertThat(errors).contains("backendName is required for the Assistant QBit");

      errors.clear();
      AssistantQBitConfig bad = new AssistantQBitConfig()
         .withBackendName("nope")
         .withProviderName("")
         .withOwnerSecurityKeyType(null)
         .withMaxToolCallsPerTurn(0)
         .withMaxRowsToModel(1000)
         .withMaxParallelToolCalls(99)
         .withPolicyCacheSeconds(-1)
         .withDefaultExposure(null)
         .withBudgets(new AssistantBudgets().withUserDailyTokensSoft(10L).withUserDailyTokensHard(5L).withInstanceMonthlyCostMicrosHard(-1L));
      bad.getModels().withMainEffort("turbo").withMainMaxOutputTokens(10).withEscalationEffort("nope").withMainModel(null);
      QInstance instance = new QInstance();
      instance.addBackend(new QBackendMetaData().withName("memory").withBackendType(MemoryBackendModule.class));
      bad.validate(instance, errors);
      assertThat(errors).contains("Backend not found: nope", "providerName is required", "ownerSecurityKeyType is required", "maxToolCallsPerTurn must be a positive number",
         "maxRowsToModel must not exceed 500", "maxParallelToolCalls must not exceed 16", "policyCacheSeconds must not be negative", "defaultExposure is required",
         "budgets.userDailyTokensSoft must not exceed budgets.userDailyTokensHard", "budgets.instanceMonthlyCostMicrosHard must not be negative",
         "models.mainModel is required", "models.mainMaxOutputTokens must be at least 1024");
      assertThat(errors).anyMatch(e -> e.startsWith("models.mainEffort")).anyMatch(e -> e.startsWith("models.escalationEffort"));

      errors.clear();
      new AssistantQBitConfig().withBackendName("memory").withModels(null).validate(instance, errors);
      assertThat(errors).contains("models is required");

      assertThatThrownBy(() -> new AssistantQBitProducer().produce(new QInstance())).isInstanceOf(QException.class).hasMessageContaining("no config");
      assertThatThrownBy(() -> new AssistantQBitProducer().withQBitConfig(new AssistantQBitConfig()).produce(new QInstance()))
         .isInstanceOf(QException.class).hasMessageContaining("backendName is required");
      assertThat(new AssistantQBitProducer().isEnabled()).isFalse();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testConfigBasics()
   {
      assertThat(config.toString()).contains("providerName=scripted").contains("providers=[scripted]");
      assertThat(config.getActiveModelProvider()).isSameAs(provider);
      assertThat(config.withModelProvider(null)).isSameAs(config);
      assertThat(config.isEffectivelyEnabled()).isTrue();
      assertThat(new AssistantQBitConfig().withEnabled(false).isEffectivelyEnabled()).isFalse();
      assertThat(config.getDefaultBackendNameForTables()).isEqualTo(AssistantTestFixture.BACKEND);
      assertThat(config.getPricing()).containsKeys(AssistantModelRouting.SONNET_5_5, AssistantModelRouting.OPUS_5_5, AssistantModelRouting.HAIKU_5_5);
   }



   /*******************************************************************************
    ** A host that already defines the owner key type keeps its own; a
    ** scheduler name schedules retention.
    *******************************************************************************/
   @Test
   void testHostKeyTypeAndSchedule() throws Exception
   {
      QInstance instance = new QInstance();
      instance.addBackend(new QBackendMetaData().withName("memory").withBackendType(MemoryBackendModule.class));
      instance.addSecurityKeyType(new QSecurityKeyType().withName("userKey").withAllAccessKeyName("userKeyAll"));

      AssistantQBitConfig hostConfig = new AssistantQBitConfig().withBackendName("memory").withOwnerSecurityKeyType("userKey").withRetentionSchedulerName("quartz").withRegisterRoutes(false);
      hostConfig.setEnabled(false);
      new AssistantQBitProducer().withQBitConfig(hostConfig).produce(instance);
      assertThat(AssistantInstanceMetaData.of(instance)).as("not advertised when disabled").isNull();
      assertThat(instance.getSecurityKeyType("userKey").getAllAccessKeyName()).isEqualTo("userKeyAll");
      assertThat(instance.getProcess(AssistantMetaDataBuilder.RETENTION_PROCESS_NAME).getSchedule().getSchedulerName()).isEqualTo("quartz");
      hostConfig.getRuntime().close();
   }

}
