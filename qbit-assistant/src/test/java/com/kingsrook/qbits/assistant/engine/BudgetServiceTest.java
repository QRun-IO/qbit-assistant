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

package com.kingsrook.qbits.assistant.engine;


import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.api.AssistantApiException;
import com.kingsrook.qbits.assistant.home.AssistantHomeData;
import com.kingsrook.qbits.assistant.home.AssistantHomeView;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantBudgetEvent;
import com.kingsrook.qbits.assistant.model.AssistantBudgetOverride;
import com.kingsrook.qbits.assistant.model.AssistantBudgetUsage;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.spi.AssistantModelProvider;
import com.kingsrook.qbits.assistant.spi.AssistantProviderType;
import com.kingsrook.qbits.assistant.spi.ModelUsage;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.LoggingAlertSink;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.InitializableViaCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReferenceWithProperties;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Budget boundaries through the fixture store and scripted model dispatch.
 *******************************************************************************/
class BudgetServiceTest extends BaseAssistantTest
{
   private static final String USER = "editor-budget";
   private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");



   /*******************************************************************************
    ** Keyless test adapter.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      config.withProviderType(new BudgetProviderType());
   }



   /*******************************************************************************
    ** Minimal adapter sharing the fixture scripted provider.
    *******************************************************************************/
   class BudgetProviderType implements AssistantProviderType
   {
      /*******************************************************************************
       ** Provider type.
       *******************************************************************************/
      public String getType()
      {
         return ("budget-test");
      }



      /*******************************************************************************
       ** Provider label.
       *******************************************************************************/
      public String getLabel()
      {
         return ("Budget test");
      }



      /*******************************************************************************
       ** Models.
       *******************************************************************************/
      public List<String> getModels()
      {
         return (List.of("main", "cheap"));
      }



      /*******************************************************************************
       ** Keyless.
       *******************************************************************************/
      public String getDefaultApiKeyEnvName()
      {
         return (null);
      }



      /*******************************************************************************
       ** No network.
       *******************************************************************************/
      public String getDefaultBaseUrl()
      {
         return ("https://budget.invalid");
      }



      /*******************************************************************************
       ** Scripted adapter.
       *******************************************************************************/
      public AssistantModelProvider create(String key, String url)
      {
         return (provider);
      }
   }



   /*******************************************************************************
    ** Admin store context.
    *******************************************************************************/
   private AssistantStore store()
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession("admin-budget", config));
      return (config.getRuntime().getStore());
   }



   /*******************************************************************************
    ** Default per-user daily USD budget.
    *******************************************************************************/
   private AssistantBudget budget() throws Exception
   {
      return (store().insert(new AssistantBudget().withName("Per user daily").withScopeType("USER").withScopeValue(USER)
         .withPeriod("DAY").withMetric("COST_USD").withLimitValue(BigDecimal.ONE)));
   }



   /*******************************************************************************
    ** Normal usage insertion, with a deterministic date.
    *******************************************************************************/
   private void usage(long micros, Instant at) throws Exception
   {
      AssistantUsage row = store().insert(new AssistantUsage().withUserId(USER).withPurpose("TURN").withStatus("OK").withCostMicros(micros)
         .withInputTokens(micros).withOutputTokens(0L).withCreateDate(at));
      store().updateFields(AssistantUsage.TABLE_NAME, new QRecord().withValue("id", row.getId()).withValue("createDate", at));
   }



   /*******************************************************************************
    ** Fixed-clock evaluator with the production core sink configuration.
    *******************************************************************************/
   private BudgetService service(List<AlertSignal> alerts)
   {
      AlertsInstanceMetaData.ofOrWithNew(qInstance).withSink(new QCodeReferenceWithProperties(TestAlertSink.class, Map.of("signals", (ArrayList<AlertSignal>) alerts)));
      return (new BudgetService(store(), config.getRuntime().getProviderService(), Clock.fixed(NOW, ZoneOffset.UTC)));
   }



   /*******************************************************************************
    ** Core loads a separate sink per instance; captured signals stay test-local.
    *******************************************************************************/
   public static class TestAlertSink extends LoggingAlertSink implements InitializableViaCodeReference
   {
      private List<AlertSignal> signals;



      /*******************************************************************************
       ** Receive this test's capture list from the configured code reference.
       *******************************************************************************/
      @Override
      @SuppressWarnings("unchecked")
      public void initialize(QCodeReference codeReference)
      {
         signals = (List<AlertSignal>) ((QCodeReferenceWithProperties) codeReference).getProperties().get("signals");
      }



      /*******************************************************************************
       ** Capture the signal delivered through core.
       *******************************************************************************/
      @Override
      public void raise(AlertSignal signal)
      {
         signals.add(signal);
      }
   }



   /*******************************************************************************
    ** 79 percent is quiet; 80 warns and deduplicates event, notice and alert.
    *******************************************************************************/
   @Test
   void warningBoundaryAndDedup() throws Exception
   {
      AssistantBudget budget = budget();
      usage(790_000, NOW);
      List<AlertSignal> alerts = new ArrayList<>();
      BudgetService service = service(alerts);
      assertThat(service.evaluate(USER, null, false, false).notices()).isEmpty();
      usage(10_000, NOW);
      assertThat(service.evaluate(USER, null, false, false).notices()).singleElement().asString().contains("80%", "Per user daily");
      assertThat(service.evaluate(USER, null, false, false).notices()).isEmpty();
      assertThat(alerts).singleElement().satisfies(signal ->
      {
         assertThat(signal.getAlertTypeName()).isEqualTo("assistant.budget.warning");
         assertThat(signal.getDedupKey()).isEqualTo(budget.getId() + ":USER:" + USER + ":2026-10-13T00:00:00Z");
         assertThat(signal.getSeverity()).isEqualTo(AlertSeverity.WARNING);
         assertThat(signal.getTitle()).isEqualTo("Assistant budget Per user daily");
         assertThat(signal.getSummary()).contains("80%", "Per user daily");
      });
      assertThat(store().query(AssistantBudgetEvent.TABLE_NAME, new QQueryFilter(), AssistantBudgetEvent.class)).singleElement()
         .extracting(AssistantBudgetEvent::getState).isEqualTo("WARNED");
   }



   /*******************************************************************************
    ** Explicit and automatic downgrade selection, including PHI eligibility.
    *******************************************************************************/
   @Test
   void downgradeAndPhi() throws Exception
   {
      AssistantBudget budget = budget();
      AssistantProvider cloud = store().insert(new AssistantProvider().withName("Cloud").withProviderType("budget-test").withModel("main").withEnabled(true).withIsDefault(true));
      AssistantProvider local = store().insert(new AssistantProvider().withName("Local").withProviderType("budget-test").withLocation("LOCAL").withModel("cheap").withEnabled(true));
      AssistantModel cheap = store().insert(new AssistantModel().withProviderId(local.getId()).withModelId("cheap").withEnabled(true).withInputUsdPerMTok(BigDecimal.ONE));
      store().insert(new AssistantModel().withProviderId(cloud.getId()).withModelId("main").withEnabled(true).withInputUsdPerMTok(BigDecimal.ZERO));
      usage(1_000_000, NOW);
      List<AlertSignal> alerts = new ArrayList<>();
      BudgetService service = service(alerts);
      ProviderService.Active active = config.getRuntime().getProviderService().resolve("main");
      assertThat(service.evaluate(USER, active, false, false).active().providerId()).isEqualTo(local.getId());
      assertThat(service.evaluate(USER, active, true, false).stopped()).isTrue();
      assertThat(alerts).extracting(AlertSignal::getAlertTypeName).containsExactly("assistant.budget.downgraded", "assistant.budget.stopped");
      assertThat(alerts).extracting(AlertSignal::getSeverity).containsExactly(AlertSeverity.WARNING, AlertSeverity.ERROR);
      assertThat(alerts).extracting(AlertSignal::getDedupKey).containsOnly(budget.getId() + ":USER:" + USER + ":2026-10-13T00:00:00Z");
      store().updateFields(AssistantBudget.TABLE_NAME, new QRecord().withValue("id", budget.getId()).withValue("downgradeModelId", cheap.getId()));
      assertThat(service.evaluate(USER, active, false, false).active().model()).isEqualTo("cheap");
   }



   /*******************************************************************************
    ** Hard ceiling always stops, and the API uses its existing 429 path.
    *******************************************************************************/
   @Test
   void ceilingAndStartTurn429() throws Exception
   {
      budget();
      usage(1_200_000, NOW);
      assertThat(service(new ArrayList<>()).evaluate(USER, null, false, false).stopped()).isTrue();
      usage(1_200_000, Instant.now());
      assertThatThrownBy(() -> runner.run(USER, "Hello")).isInstanceOf(AssistantApiException.class)
         .hasMessageContaining("Per user daily").satisfies(e ->
         {
            assertThat(((AssistantApiException) e).getStatus()).isEqualTo(429);
            assertThat(((AssistantApiException) e).getCode()).isEqualTo("BUDGET");
         });
   }



   /*******************************************************************************
    ** Additive, unlimited and expired overrides; grantor cannot be supplied.
    *******************************************************************************/
   @Test
   void overridesAndPermission() throws Exception
   {
      AssistantBudget budget = budget();
      usage(1_100_000, NOW);
      AssistantBudgetOverride extra = store().insert(new AssistantBudgetOverride().withBudgetId(budget.getId()).withExtraValue(BigDecimal.ONE)
         .withReason("Project launch").withExpiresAt(NOW.plusSeconds(3600)).withGrantedBy("forged"));
      assertThat(extra.getGrantedBy()).isEqualTo("admin-budget");
      BudgetService service = service(new ArrayList<>());
      assertThat(service.snapshot(budget, USER).limit()).isEqualByComparingTo("2");
      store().insert(new AssistantBudgetOverride().withBudgetId(budget.getId()).withUnlimitedUntil(NOW.plusSeconds(60))
         .withExpiresAt(NOW.plusSeconds(60)).withReason("Investigation"));
      assertThat(service.snapshot(budget, USER).state()).isEqualTo("UNLIMITED");
      BudgetService later = new BudgetService(store(), config.getRuntime().getProviderService(), Clock.fixed(NOW.plusSeconds(3601), ZoneOffset.UTC));
      assertThat(later.snapshot(budget, USER).limit()).isEqualByComparingTo("1");
      AssistantStore store = store();
      QContext.init(qInstance, AssistantTestFixture.ownerSession(USER, config));
      assertThatThrownBy(() -> store.insert(new AssistantBudgetOverride().withBudgetId(budget.getId()).withExtraValue(BigDecimal.ONE)
         .withExpiresAt(NOW.plusSeconds(60)).withReason("No permission"))).hasMessageContaining("assistant.admin");
   }



   /*******************************************************************************
    ** Monthly UTC boundaries and token accounting include all token counters.
    *******************************************************************************/
   @Test
   void monthAndTokens() throws Exception
   {
      AssistantBudget budget = store().insert(new AssistantBudget().withName("Month").withScopeType("INSTANCE").withPeriod("MONTH")
         .withMetric("TOKENS").withLimitValue(BigDecimal.valueOf(100)).withAtLimitAction("STOP"));
      usage(500, Instant.parse("2026-09-30T23:59:59Z"));
      usage(50, Instant.parse("2026-10-01T00:00:00Z"));
      usage(500, Instant.parse("2026-11-01T00:00:00Z"));
      assertThat(service(new ArrayList<>()).snapshot(budget, USER).used()).isEqualByComparingTo("50");
   }



   /*******************************************************************************
    ** A stop dominates a warning or downgrade in another applicable budget.
    *******************************************************************************/
   @Test
   void strictestWinsAndRoleMembership() throws Exception
   {
      budget();
      AssistantBudget role = store().insert(new AssistantBudget().withName("Role").withScopeType("ROLE").withScopeValue("assistant.admin")
         .withPeriod("DAY").withMetric("TOKENS").withLimitValue(BigDecimal.TEN).withAtLimitAction("STOP"));
      usage(800_000, NOW);
      BudgetService service = service(new ArrayList<>());
      assertThat(service.evaluate(USER, null, false, false).stopped()).isTrue();
      QContext.init(qInstance, AssistantTestFixture.ownerSession(USER, config));
      assertThat(service.evaluate(USER, null, false, false).stopped()).isFalse();
      store().updateFields(AssistantBudget.TABLE_NAME, new QRecord().withValue("id", role.getId()).withValue("scopeType", "INSTANCE").withValue("scopeValue", ""));
      assertThat(service.evaluate(USER, null, false, false).stopped()).isTrue();
   }



   /*******************************************************************************
    ** App home has budget and time-series data and honest empty states.
    *******************************************************************************/
   @Test
   void homeDataAndEmptyStates() throws Exception
   {
      store();
      String empty = AssistantHomeView.render(AssistantHomeData.load(config.getRuntime()), "/app/assistantAdmin");
      assertThat(empty).contains("No enabled data-driven budgets", "No assistant use", "No provider usage");
      budget();
      usage(800_000, Instant.now());
      String html = AssistantHomeView.render(AssistantHomeData.load(config.getRuntime()), "/app/assistantAdmin");
      assertThat(html).contains("Budgets", "Per user daily", "Usage over time", "Daily tokens", "Daily cost", "By provider");
      assertThat(html).doesNotContain("<table", "overflow:auto");
   }



   /*******************************************************************************
    ** Next actual model dispatch uses the explicit downgrade model.
    *******************************************************************************/
   @Test
   void turnDispatchUsesDowngradeModel() throws Exception
   {
      AssistantBudget budget = budget();
      AssistantProvider row = store().insert(new AssistantProvider().withName("Local").withProviderType("budget-test").withModel("main").withLocation("LOCAL").withEnabled(true));
      AssistantModel cheap = store().insert(new AssistantModel().withProviderId(row.getId()).withModelId("cheap").withEnabled(true));
      store().updateFields(AssistantBudget.TABLE_NAME, new QRecord().withValue("id", budget.getId()).withValue("downgradeModelId", cheap.getId()));
      usage(1_000_000, Instant.now());
      provider.thenText("Done");
      assertThat(runner.run(USER, "Hello").errorCode()).isNull();
      assertThat(provider.getRequests()).singleElement().extracting(r -> r.getModel()).isEqualTo("cheap");
   }



   /*******************************************************************************
    ** Provider and model scopes isolate the other provider's same-named model.
    *******************************************************************************/
   @Test
   void providerAndModelScopes() throws Exception
   {
      AssistantProvider row = store().insert(new AssistantProvider().withName("Scope provider").withProviderType("budget-test").withModel("main").withEnabled(true));
      AssistantModel model = store().insert(new AssistantModel().withProviderId(row.getId()).withModelId("main").withEnabled(true));
      AssistantBudget budget = store().insert(new AssistantBudget().withName("Provider").withScopeType("PROVIDER").withScopeValue(row.getId().toString())
         .withPeriod("DAY").withMetric("COST_USD").withLimitValue(BigDecimal.ONE).withAtLimitAction("STOP"));
      AssistantUsage usage = store().insert(new AssistantUsage().withUserId(USER).withProviderId(row.getId()).withModel("main").withCostMicros(1_000_000L));
      store().updateFields(AssistantUsage.TABLE_NAME, new QRecord().withValue("id", usage.getId()).withValue("createDate", NOW));
      BudgetService service = service(new ArrayList<>());
      ProviderService.Active active = config.getRuntime().getProviderService().resolve("main");
      assertThat(service.evaluate(USER, active, false, false).stopped()).isTrue();
      assertThat(service.evaluate(USER, null, false, false).stopped()).isFalse();
      store().updateFields(AssistantBudget.TABLE_NAME, new QRecord().withValue("id", budget.getId()).withValue("scopeType", "MODEL").withValue("scopeValue", model.getId().toString()));
      assertThat(service.evaluate(USER, active, false, false).stopped()).isTrue();
      assertThat(service.evaluate(USER, new ProviderService.Active(row, "budget-test", "another-model", provider), false, false).stopped()).isFalse();
   }



   /*******************************************************************************
    ** Usage from the first step stops the next dispatch with BUDGET.
    *******************************************************************************/
   @Test
   void midTurnStop() throws Exception
   {
      store().insert(new AssistantBudget().withName("Token steps").withScopeType("USER").withScopeValue(USER)
         .withPeriod("DAY").withMetric("TOKENS").withLimitValue(BigDecimal.valueOf(100)).withAtLimitAction("STOP"));
      provider.withUsagePerCall(new ModelUsage().withInputTokens(100L).withOutputTokens(0L));
      provider.thenToolUse("count_records", Map.of("table", "page")).thenText("Should not dispatch");
      assertThat(runner.run(USER, "Count pages").errorCode()).isEqualTo("BUDGET");
      assertThat(provider.getRequests()).hasSize(1);
   }



   /*******************************************************************************
    ** The start-turn warning reaches the panel once and is not replayed.
    *******************************************************************************/
   @Test
   void panelWarningOnce() throws Exception
   {
      budget();
      usage(800_000, Instant.now());
      provider.thenText("Hello").thenText("Hello again");
      assertThat(runner.run(USER, "Hello").events(TurnEvent.NOTICE)).hasSize(1);
      assertThat(runner.run(USER, "Hello again").events(TurnEvent.NOTICE)).isEmpty();
   }



   /*******************************************************************************
    ** Another user's cost counts against shared budgets without unlocking detail.
    *******************************************************************************/
   @Test
   void sharedBudgetKeepsUsageOwnerLocks() throws Exception
   {
      store().insert(new AssistantBudget().withName("Whole instance").withScopeType("INSTANCE").withPeriod("DAY")
         .withMetric("COST_USD").withLimitValue(BigDecimal.ONE).withAtLimitAction("STOP"));
      usage(1_000_000, NOW);
      BudgetService service = service(new ArrayList<>());
      AssistantStore store = store();
      QContext.init(qInstance, AssistantTestFixture.ownerSession("editor-other", config));
      assertThat(store.query(AssistantUsage.TABLE_NAME, new QQueryFilter(), AssistantUsage.class)).isEmpty();
      assertThat(service.evaluate("editor-other", null, false, false).stopped()).isTrue();
      assertThat(qInstance.getTable(AssistantBudgetUsage.TABLE_NAME).getRecordSecurityLocks()).isNullOrEmpty();
      assertThat(qInstance.getTable(AssistantUsage.TABLE_NAME).getRecordSecurityLocks()).isNotEmpty();
   }



   /*******************************************************************************
    ** A provider STOP is not skipped after a user budget selects another provider.
    *******************************************************************************/
   @Test
   void originalProviderStopWinsOverDowngrade() throws Exception
   {
      AssistantBudget user = budget();
      AssistantProvider original = store().insert(new AssistantProvider().withName("Original").withProviderType("budget-test").withModel("main").withEnabled(true).withIsDefault(true));
      AssistantProvider local = store().insert(new AssistantProvider().withName("Target").withProviderType("budget-test").withModel("cheap").withEnabled(true).withLocation("LOCAL"));
      AssistantModel model = store().insert(new AssistantModel().withProviderId(local.getId()).withModelId("cheap").withEnabled(true));
      store().updateFields(AssistantBudget.TABLE_NAME, new QRecord().withValue("id", user.getId()).withValue("downgradeModelId", model.getId()));
      store().insert(new AssistantBudget().withName("Provider stop").withScopeType("PROVIDER").withScopeValue(original.getId().toString())
         .withPeriod("DAY").withMetric("COST_USD").withLimitValue(BigDecimal.ONE).withAtLimitAction("STOP"));
      AssistantUsage usage = store().insert(new AssistantUsage().withUserId(USER).withProviderId(original.getId()).withModel("main").withCostMicros(1_000_000L));
      store().updateFields(AssistantUsage.TABLE_NAME, new QRecord().withValue("id", usage.getId()).withValue("createDate", NOW));
      BudgetService service = service(new ArrayList<>());
      assertThat(service.evaluate(USER, config.getRuntime().getProviderService().resolve("main"), false, false).reason()).contains("Provider stop");
      assertThat(store().query(AssistantBudgetEvent.TABLE_NAME, new QQueryFilter(), AssistantBudgetEvent.class)).extracting(AssistantBudgetEvent::getState).containsExactly("STOPPED");
   }



   /*******************************************************************************
    ** A PHI conversation cannot downgrade back to its original model or to an
    ** ineligible candidate; no model receives the request.
    *******************************************************************************/
   @Test
   void phiConversationWithoutEligibleDowngradeStops() throws Exception
   {
      budget();
      AssistantProvider original = store().insert(new AssistantProvider().withName("Eligible original").withProviderType("budget-test")
         .withModel("main").withEnabled(true).withIsDefault(true).withHipaaEligible(true));
      AssistantProvider target = store().insert(new AssistantProvider().withName("Ineligible target").withProviderType("budget-test")
         .withModel("cheap").withEnabled(true).withLocation("LOCAL").withHipaaEligible(false));
      store().insert(new AssistantModel().withProviderId(original.getId()).withModelId("main").withEnabled(true));
      store().insert(new AssistantModel().withProviderId(target.getId()).withModelId("cheap").withEnabled(true));
      AssistantConversation conversation = store().insert(new AssistantConversation().withUserId(USER).withTitle("Classified")
         .withMaxClassification("PHI").withStatus("ACTIVE"));
      usage(1_000_000, Instant.now());
      assertThat(runner.run(USER, conversation.getId(), "Continue", Map.of()).errorCode()).isEqualTo("BUDGET");
      assertThat(provider.getRequests()).isEmpty();
   }



   /*******************************************************************************
    ** Partial edits validate the full row; narrow overrides affect only their user.
    *******************************************************************************/
   @Test
   void validationAndNarrowOverrides() throws Exception
   {
      AssistantBudget budget = store().insert(new AssistantBudget().withName("Shared").withScopeType("INSTANCE").withPeriod("DAY")
         .withMetric("COST_USD").withLimitValue(BigDecimal.ONE));
      assertThatThrownBy(() -> store().updateFields(AssistantBudget.TABLE_NAME,
         new QRecord().withValue("id", budget.getId()).withValue("hardCeilingPercent", 99))).hasMessageContaining("hard ceiling");
      store().insert(new AssistantBudgetOverride().withBudgetId(budget.getId()).withScopeValue(USER).withExtraValue(BigDecimal.ONE)
         .withExpiresAt(NOW.plusSeconds(3600)).withReason("One user"));
      BudgetService service = service(new ArrayList<>());
      assertThat(service.snapshot(budget, USER).limit()).isEqualByComparingTo("2");
      assertThat(service.snapshot(budget, "editor-other").limit()).isEqualByComparingTo("1");
      assertThatThrownBy(() -> store().insert(new AssistantBudget().withName("Invalid provider").withScopeType("PROVIDER")
         .withScopeValue("missing").withPeriod("DAY").withMetric("TOKENS").withLimitValue(BigDecimal.TEN))).hasMessageContaining("numeric");
   }
}
