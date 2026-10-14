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

package com.kingsrook.qbits.assistant.settings;


import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.api.AssistantApiException;
import com.kingsrook.qbits.assistant.engine.ConnectionTest;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.engine.UsageRecorder;
import com.kingsrook.qbits.assistant.home.AssistantHome;
import com.kingsrook.qbits.assistant.home.AssistantHomeData;
import com.kingsrook.qbits.assistant.home.AssistantHomeView;
import com.kingsrook.qbits.assistant.metadata.AssistantRecordScreens;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantModelPrice;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.processes.TestConnectionProcess;
import com.kingsrook.qbits.assistant.spi.AssistantModelProvider;
import com.kingsrook.qbits.assistant.spi.AssistantProviderType;
import com.kingsrook.qbits.assistant.spi.ModelPricing;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelUsage;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.actions.ai.AiExposure;
import com.kingsrook.qqq.backend.core.actions.ai.AiTableMetaData;
import com.kingsrook.qqq.backend.core.actions.customizers.QCodeLoader;
import com.kingsrook.qqq.backend.core.actions.dashboard.RenderWidgetAction;
import com.kingsrook.qqq.backend.core.actions.processes.QProcessCallbackFactory;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.actions.values.QCustomPossibleValueProvider;
import com.kingsrook.qqq.backend.core.actions.values.SearchPossibleValueSourceAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.actions.values.SearchPossibleValueSourceInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The provider settings, key handling, Test connection, per-call usage, cost
 ** math and the app home.
 *******************************************************************************/
class AssistantSettingsTest extends BaseAssistantTest
{
   static final String FAKE_TYPE = "fake";
   static final String KEY       = "sk-test-SECRET-VALUE-0123456789-wxyz";

   private final Map<String, String> secrets = new HashMap<>();



   /*******************************************************************************
    ** A provider type whose providers are the test's scripted provider.
    *******************************************************************************/
   class FakeType implements AssistantProviderType
   {
      String lastKey;



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public String getType()
      {
         return (FAKE_TYPE);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public String getLabel()
      {
         return ("Fake");
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public List<String> getModels()
      {
         return (List.of("fake-model-1", "fake-model-2"));
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public String getDefaultApiKeyEnvName()
      {
         return ("FAKE_API_KEY");
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public String getDefaultBaseUrl()
      {
         return ("https://fake.example");
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public AssistantModelProvider create(String apiKey, String baseUrl)
      {
         lastKey = apiKey;
         return (provider);
      }
   }



   private final FakeType fakeType = new FakeType();



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      secrets.put("FAKE_API_KEY", KEY);
      secrets.put("DATABASE_PASSWORD", "db-secret-should-never-leak");
      config.withProviderType(fakeType).withSecretResolver(secrets::get).withPolicyCacheSeconds(0);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void asAdmin()
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private InsertOutput insertProvider(Map<String, Serializable> values) throws Exception
   {
      QRecord record = new QRecord().withValue("name", "Fake").withValue("providerType", FAKE_TYPE).withValue("model", "fake-model-1")
         .withValue("enabled", true).withValue("isDefault", true);
      values.forEach(record::setValue);
      return (new InsertAction().execute(new InsertInput(AssistantProvider.TABLE_NAME).withRecord(record)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private Integer providerId() throws Exception
   {
      InsertOutput output = insertProvider(Map.of());
      assertThat(output.getRecords().get(0).getErrors()).isEmpty();
      return (output.getRecords().get(0).getValueInteger("id"));
   }



   /*******************************************************************************
    ** Tables, nav, actions, child lists and exposure.
    *******************************************************************************/
   @Test
   void testMetaData() throws Exception
   {
      asAdmin();
      for(String name : List.of(AssistantProvider.TABLE_NAME, AssistantModelPrice.TABLE_NAME, AssistantUsage.TABLE_NAME))
      {
         QTableMetaData table = qInstance.getTable(name);
         assertThat(table).as(name).isNotNull();
         assertThat(table.getIsHidden()).isFalse();
         assertThat(AiTableMetaData.of(table).getExposure()).isEqualTo(AiExposure.NONE);
      }
      assertThat(qInstance.getTable(AssistantUsage.TABLE_NAME).getRecordSecurityLocks()).hasSize(1);
      assertThat(qInstance.getTable(AssistantProvider.TABLE_NAME).getField("apiKeyStatus").getIsEditable()).isFalse();
      assertThat(qInstance.getProcess(TestConnectionProcess.NAME).getTableName()).isEqualTo(AssistantProvider.TABLE_NAME);
      assertThat(qInstance.getProcess(TestConnectionProcess.NAME).getLabel()).isEqualTo("Test connection");
      assertThat(qInstance.getWidget(AssistantHome.WIDGET_NAME)).isNotNull();
      assertThat(qInstance.getPossibleValueSource(AssistantRecordScreens.PROVIDER_TYPE_PVS_NAME).getEnumValues()).extracting(v -> String.valueOf(v.getId())).containsExactly(FAKE_TYPE);
      assertThat(new SearchPossibleValueSourceAction().execute(new SearchPossibleValueSourceInput().withPossibleValueSourceName(AssistantRecordScreens.MODEL_PVS_NAME)).getResults()).extracting(v -> String.valueOf(v.getId())).contains("fake-model-1", "claude-sonnet-5-5");
      assertThat(qInstance.getTable(AssistantProvider.TABLE_NAME).getSections()).anyMatch(s -> s.getWidgetName() != null);
      assertThat(qInstance.getTable(AssistantProvider.TABLE_NAME).getSections()).anyMatch(s -> s.getFieldNames() != null && s.getFieldNames().contains("apiKeyStatus"));
   }



   /*******************************************************************************
    ** No read path returns the key: query, get, the entity and its JSON hold
    ** only the masked status.
    *******************************************************************************/
   @Test
   void testKeyNeverReturned() throws Exception
   {
      asAdmin();
      Integer id = providerId();

      List<QRecord> queried = new QueryAction().execute(new QueryInput(AssistantProvider.TABLE_NAME).withFilter(new QQueryFilter())).getRecords();
      QRecord       got     = new GetAction().execute(new GetInput(AssistantProvider.TABLE_NAME).withPrimaryKey(id)).getRecord();
      for(QRecord record : List.of(queried.get(0), got))
      {
         assertThat(record.getValueString("apiKeyStatus")).isEqualTo("Set • …wxyz (from FAKE_API_KEY)");
         assertThat(JsonUtils.toJson(record.getValues())).doesNotContain(KEY).doesNotContain("SECRET");
         assertThat(JsonUtils.toJson(new AssistantProvider(record))).doesNotContain(KEY);
      }

      //////////////////////////////////////////////////////////////
      // the status is never stored, even if a caller sends one   //
      //////////////////////////////////////////////////////////////
      new UpdateAction().execute(new UpdateInput(AssistantProvider.TABLE_NAME).withRecord(new QRecord().withValue("id", id).withValue("apiKeyStatus", "forged").withValue("name", "Renamed")));
      secrets.remove("FAKE_API_KEY");
      QRecord after = new GetAction().execute(new GetInput(AssistantProvider.TABLE_NAME).withPrimaryKey(id)).getRecord();
      assertThat(after.getValueString("apiKeyStatus")).isEqualTo("Not set (FAKE_API_KEY is empty)");
      assertThat(after.getValueString("name")).isEqualTo("Renamed");

      assertThat(ProviderService.mask("short")).isEqualTo("Set");
      assertThat(ProviderService.mask(null)).isEqualTo("Not set");
   }



   /*******************************************************************************
    ** A key pasted into the variable field, an unrelated secret's variable and
    ** an unknown base URL are all refused (and nothing is stored).
    *******************************************************************************/
   @Test
   void testUnsafeSettingsRefused() throws Exception
   {
      asAdmin();
      InsertOutput pasted = insertProvider(Map.of("apiKeyEnvName", KEY));
      assertThat(pasted.getRecords().get(0).getErrors()).isNotEmpty();
      assertThat(pasted.getRecords().get(0).getErrors().get(0).getMessage()).contains("NAME of an environment variable").doesNotContain(KEY);

      InsertOutput other = insertProvider(Map.of("name", "Other", "apiKeyEnvName", "DATABASE_PASSWORD"));
      assertThat(other.getRecords().get(0).getErrors().get(0).getMessage()).contains("not allowed");

      InsertOutput url = insertProvider(Map.of("name", "Url", "baseUrl", "https://evil.example"));
      assertThat(url.getRecords().get(0).getErrors().get(0).getMessage()).contains("not allowed");

      InsertOutput badType = insertProvider(Map.of("name", "Type", "providerType", "nope"));
      assertThat(badType.getRecords().get(0).getErrors().get(0).getMessage()).contains("not installed");

      InsertOutput limits = insertProvider(Map.of("name", "Limits", "monthlyCostCapUsd", new BigDecimal("-1"), "maxOutputTokensPerRequest", 0, "maxConcurrentRequests", 0));
      assertThat(limits.getRecords().get(0).getErrors()).hasSize(3);

      assertThat(new QueryAction().execute(new QueryInput(AssistantProvider.TABLE_NAME).withFilter(new QQueryFilter())).getRecords()).isEmpty();
   }



   /*******************************************************************************
    ** Marking a provider default unmarks the others; the default enabled one is
    ** active; with no rows the configured provider is used.
    *******************************************************************************/
   @Test
   void testActiveProvider() throws Exception
   {
      asAdmin();
      ProviderService service = config.getRuntime().getProviderService();
      ProviderService.Active fallback = service.resolve("main-model");
      assertThat(fallback.row()).isNull();
      assertThat(fallback.model()).isEqualTo("main-model");

      Integer first  = providerId();
      Integer second = insertProvider(Map.of("name", "Second", "model", "fake-model-2")).getRecords().get(0).getValueInteger("id");
      assertThat(service.activeRow().getId()).isEqualTo(second);
      assertThat(new GetAction().execute(new GetInput(AssistantProvider.TABLE_NAME).withPrimaryKey(first)).getRecord().getValueBoolean("isDefault")).isFalse();

      ProviderService.Active active = service.resolve("main-model");
      assertThat(active.providerId()).isEqualTo(second);
      assertThat(active.model()).isEqualTo("fake-model-2");
      assertThat(active.provider()).isSameAs(provider);
      assertThat(fakeType.lastKey).isEqualTo(KEY);

      new UpdateAction().execute(new UpdateInput(AssistantProvider.TABLE_NAME).withRecord(new QRecord().withValue("id", second).withValue("enabled", false)));
      assertThat(service.activeRow().getId()).isEqualTo(first);

      secrets.remove("FAKE_API_KEY");
      assertThat(service.resolve("main-model")).isNull();
   }



   /*******************************************************************************
    ** Test connection (a record action): Result lines, the saved result, and a
    ** TEST usage row - success and failure.
    *******************************************************************************/
   @Test
   void testTestConnectionAction() throws Exception
   {
      asAdmin();
      Integer id = providerId();

      provider.thenText("OK");
      List<ProcessSummaryLineInterface> lines = runTest(id);
      assertThat(lines).anyMatch(l -> l.getStatus() == Status.OK && l.getMessage().contains("connected to fake-model-1"));
      AssistantProvider row = config.getRuntime().getStore().getProvider(id);
      assertThat(row.getLastTestStatus()).isEqualTo("OK");
      assertThat(row.getLastTestModel()).isEqualTo("fake-model-1");
      assertThat(row.getLastTestLatencyMs()).isNotNull();
      assertThat(provider.getRequests().get(provider.getRequests().size() - 1).getMaxTokens()).isEqualTo(ConnectionTest.PING_MAX_TOKENS);

      provider.thenError(ModelProviderException.Kind.AUTHENTICATION, "invalid x-api-key");
      lines = runTest(id);
      assertThat(lines).anyMatch(l -> l.getStatus() == Status.ERROR && l.getMessage().contains("API key was refused"));
      row = config.getRuntime().getStore().getProvider(id);
      assertThat(row.getLastTestStatus()).isEqualTo("ERROR");
      assertThat(row.getLastTestError()).contains("AUTHENTICATION").doesNotContain(KEY);

      List<QRecord> usage = new QueryAction().execute(new QueryInput(AssistantUsage.TABLE_NAME).withFilter(new QQueryFilter()
         .withCriteria("purpose", QCriteriaOperator.EQUALS, UsageRecorder.PURPOSE_TEST))).getRecords();
      assertThat(usage).extracting(r -> r.getValueString("status")).containsExactlyInAnyOrder("OK", "ERROR");
      assertThat(usage).allMatch(r -> AssistantTestFixture.ADMIN.equals(r.getValueString("userId")) && id.equals(r.getValueInteger("providerId")));

      ////////////////////////////////////////////////////////////////
      // a row that cannot be used fails before any call            //
      ////////////////////////////////////////////////////////////////
      int calls = provider.getCallCount();
      secrets.remove("FAKE_API_KEY");
      lines = runTest(id);
      assertThat(lines).anyMatch(l -> l.getStatus() == Status.ERROR && l.getMessage().contains("No API key"));
      assertThat(provider.getCallCount()).isEqualTo(calls);
   }



   /*******************************************************************************
    ** Run Test connection on one provider; the Result screen's lines.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private List<ProcessSummaryLineInterface> runTest(Integer id) throws Exception
   {
      RunProcessInput input = new RunProcessInput();
      input.setProcessName(TestConnectionProcess.NAME);
      input.setCallback(QProcessCallbackFactory.forPrimaryKey("id", id));
      input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      RunProcessOutput output = new RunProcessAction().execute(input);
      List<ProcessSummaryLineInterface> review = (List<ProcessSummaryLineInterface>) output.getValues().get(StreamedETLWithFrontendProcess.FIELD_VALIDATION_SUMMARY);
      assertThat(review).anyMatch(l -> l.getMessage() != null && l.getMessage().contains("API key"));
      return ((List<ProcessSummaryLineInterface>) output.getValues().get(StreamedETLWithFrontendProcess.FIELD_PROCESS_SUMMARY));
   }



   /*******************************************************************************
    ** Every model call of a turn writes a usage row, priced from the price
    ** table; the per-request output cap applies; the monthly cap stops turns.
    *******************************************************************************/
   @Test
   void testTurnUsageAndLimits() throws Exception
   {
      asAdmin();
      Integer id = insertProvider(Map.of("maxOutputTokensPerRequest", 500)).getRecords().get(0).getValueInteger("id");
      new InsertAction().execute(new InsertInput(AssistantModelPrice.TABLE_NAME).withRecord(new QRecord()
         .withValue("providerType", FAKE_TYPE).withValue("model", "fake-model-1")
         .withValue("inputUsdPerMTok", new BigDecimal("3")).withValue("outputUsdPerMTok", new BigDecimal("15")).withValue("cacheReadUsdPerMTok", new BigDecimal("0.3"))));

      provider.thenToolUse("list_tables", Map.of()).thenText("There are some tables.");
      TurnRunner.Outcome outcome = runner.run(AssistantTestFixture.EDITOR, "what tables are there?");
      assertThat(outcome.errorCode()).isNull();
      assertThat(provider.getRequests()).allMatch(r -> r.getMaxTokens() == 500 && "fake-model-1".equals(r.getModel()));

      asAdmin();
      List<QRecord> usage = new QueryAction().execute(new QueryInput(AssistantUsage.TABLE_NAME).withFilter(new QQueryFilter()
         .withCriteria("turnId", QCriteriaOperator.EQUALS, outcome.turnId()))).getRecords();
      assertThat(usage).hasSize(2);
      for(QRecord row : usage)
      {
         assertThat(row.getValueString("purpose")).isEqualTo("TURN");
         assertThat(row.getValueString("status")).isEqualTo("OK");
         assertThat(row.getValueInteger("providerId")).isEqualTo(id);
         assertThat(row.getValueInteger("conversationId")).isEqualTo(outcome.conversationId());
         assertThat(row.getValueString("userId")).isEqualTo(AssistantTestFixture.EDITOR);
         //////////////////////////////////////////////////////////////
         // 1200 in x $3 + 150 out x $15 + 3000 cache read x $0.30   //
         //////////////////////////////////////////////////////////////
         assertThat(row.getValueLong("costMicros")).isEqualTo(3600 + 2250 + 900);
      }

      ////////////////////////////////////////////////////////////////
      // month to date is $0.01350; a $0.01 cap refuses new turns   //
      ////////////////////////////////////////////////////////////////
      assertThat(config.getRuntime().getProviderService().monthToDateCostMicros(id)).isEqualTo(13_500);
      new UpdateAction().execute(new UpdateInput(AssistantProvider.TABLE_NAME).withRecord(new QRecord().withValue("id", id).withValue("monthlyCostCapUsd", new BigDecimal("0.01"))));
      provider.thenText("never");
      int calls = provider.getCallCount();
      assertThatThrownBy(() -> runner.run(AssistantTestFixture.EDITOR, "again"))
         .isInstanceOfSatisfying(AssistantApiException.class, e -> assertThat(e.getCode()).isEqualTo("BUDGET"));
      assertThat(provider.getCallCount()).isEqualTo(calls);
   }



   /*******************************************************************************
    ** A failed model call is recorded with its kind.
    *******************************************************************************/
   @Test
   void testFailedCallRecorded() throws Exception
   {
      asAdmin();
      providerId();
      provider.thenError(ModelProviderException.Kind.OVERLOADED, "overloaded");
      TurnRunner.Outcome outcome = runner.run(AssistantTestFixture.EDITOR, "hello");
      assertThat(outcome.errorCode()).isEqualTo("PROVIDER");

      asAdmin();
      List<QRecord> usage = new QueryAction().execute(new QueryInput(AssistantUsage.TABLE_NAME).withFilter(new QQueryFilter())).getRecords();
      assertThat(usage).hasSize(1);
      assertThat(usage.get(0).getValueString("status")).isEqualTo("ERROR");
      assertThat(usage.get(0).getValueString("errorKind")).isEqualTo("OVERLOADED");
   }



   /*******************************************************************************
    ** Cost math: price rows override the config, cache defaults, formatting.
    *******************************************************************************/
   @Test
   void testCostMath() throws Exception
   {
      asAdmin();
      ProviderService service = config.getRuntime().getProviderService();
      ModelUsage      usage   = new ModelUsage().withInputTokens(1_000_000L).withOutputTokens(100_000L).withCacheReadTokens(1_000_000L).withCacheWriteTokens(0L);

      ////////////////////////////////////////////////
      // no row: config defaults (Sonnet 5.5 $2/$10) //
      ////////////////////////////////////////////////
      assertThat(service.pricing("anthropic", "claude-sonnet-5-5").costMicros(usage)).isEqualTo(2_000_000 + 1_000_000 + 200_000);
      assertThat(service.pricing("x", "unknown-model").costMicros(usage)).isZero();

      new InsertAction().execute(new InsertInput(AssistantModelPrice.TABLE_NAME).withRecord(new QRecord()
         .withValue("providerType", "anthropic").withValue("model", "claude-sonnet-5-5")
         .withValue("inputUsdPerMTok", new BigDecimal("1")).withValue("outputUsdPerMTok", new BigDecimal("5"))));
      ModelPricing row = service.pricing("anthropic", "claude-sonnet-5-5");
      assertThat(row.getCacheReadUsdPerMTok()).isEqualTo(0.1);
      assertThat(row.getCacheWrite5mUsdPerMTok()).isEqualTo(1.25);
      assertThat(row.costMicros(usage)).isEqualTo(1_000_000 + 500_000 + 100_000);
      assertThat(service.pricing("other-type", "claude-sonnet-5-5").costMicros(usage)).isEqualTo(3_200_000);

      assertThat(ModelPricing.defaults()).containsKeys("claude-fable-5-1", "claude-haiku-4-5");
      assertThat(ProviderService.capMicros(new AssistantProvider().withMonthlyCostCapUsd(new BigDecimal("12.345678")))).isEqualTo(12_345_678L);
      assertThat(ProviderService.capMicros(null)).isNull();
      assertThat(AssistantHomeView.money(0)).isEqualTo("$0.00");
      assertThat(AssistantHomeView.money(5)).isEqualTo("<$0.01");
      assertThat(AssistantHomeView.money(12_345_678)).isEqualTo("$12.35");
      assertThat(AssistantHomeView.money(1_234_000_000L)).isEqualTo("$1,234.00");
   }



   /*******************************************************************************
    ** The home with nothing in it: honest empty states, no tables.
    *******************************************************************************/
   @Test
   void testHomeEmpty() throws Exception
   {
      asAdmin();
      String html = render();
      assertThat(html).contains("No providers yet").contains("No assistant use in the last 30 days").contains("No tool calls yet")
         .contains("No failed model calls").contains("No one has used the assistant in the last 30 days").doesNotContain("<table");
   }



   /*******************************************************************************
    ** The home with real rows: providers with health, usage, cost, tools and
    ** failures.
    *******************************************************************************/
   @Test
   void testHomeWithData() throws Exception
   {
      asAdmin();
      Integer id = insertProvider(Map.of("monthlyCostCapUsd", new BigDecimal("50"))).getRecords().get(0).getValueInteger("id");
      provider.thenText("OK");
      ConnectionTest.run(config.getRuntime(), config.getRuntime().getStore().getProvider(id), AssistantTestFixture.ADMIN);

      provider.thenToolUse("list_tables", Map.of()).thenText("Here.");
      runner.run(AssistantTestFixture.EDITOR, "what tables?");
      provider.thenToolUse("describe_table", Map.of("table", "nope")).thenError(ModelProviderException.Kind.SERVER, "boom");
      runner.run(AssistantTestFixture.ANALYST, "describe nope");

      asAdmin();
      AssistantHomeData data = AssistantHomeData.load(config.getRuntime());
      assertThat(data.getProviders()).hasSize(1);
      assertThat(data.getProviders().get(0).active()).isTrue();
      assertThat(data.getProviders().get(0).keyPresent()).isTrue();
      assertThat(data.getByUser()).extracting(t -> t.key).contains(AssistantTestFixture.EDITOR, AssistantTestFixture.ANALYST, AssistantTestFixture.ADMIN);
      assertThat(data.getByTool()).extracting(t -> t.name).contains("list_tables", "describe_table");
      assertThat(data.getFailedCalls()).hasSize(1);
      assertThat(data.getFailedTools()).hasSize(1);
      assertThat(data.errorRatePercent7d()).isEqualTo(25);
      assertThat(data.getDays().get(data.getToday()).conversations).isEqualTo(2);
      assertThat(data.getMonthRequests()).isEqualTo(5);
      assertThat(data.getMonthCostMicros()).isZero();

      String html = render();
      assertThat(html).contains("Answering").contains("Fake").contains("In use").contains("Last test passed").contains("…wxyz")
         .contains("of $50.00 cap").contains("list_tables").contains("Model call #").contains("25% of 4 requests").doesNotContain(KEY).doesNotContain("<table");
   }



   /*******************************************************************************
    ** Render the home widget as the current session.
    *******************************************************************************/
   private String render() throws Exception
   {
      RenderWidgetInput input = new RenderWidgetInput();
      input.setWidgetMetaData(qInstance.getWidget(AssistantHome.WIDGET_NAME));
      RenderWidgetOutput output = new RenderWidgetAction().execute(input);
      return (((RawHTML) output.getWidgetData()).getHtml());
   }



   /*******************************************************************************
    ** Provider models are scoped, with only this row's enabled chat models added.
    *******************************************************************************/
   @Test
   void testContingentProviderModels() throws Exception
   {
      asAdmin();
      Integer id = providerId();
      AssistantStore store = new AssistantStore();
      store.insert(new AssistantModel().withProviderId(id).withModelId("registered-chat").withEnabled(true).withModelKind("CHAT"));
      store.insert(new AssistantModel().withProviderId(id).withModelId("disabled-chat").withEnabled(false).withModelKind("CHAT"));
      store.insert(new AssistantModel().withProviderId(id).withModelId("embedding").withEnabled(true).withModelKind("EMBEDDING"));
      Integer otherId = insertProvider(Map.of("name", "Other")).getRecords().get(0).getValueInteger("id");
      store.insert(new AssistantModel().withProviderId(otherId).withModelId("other-chat").withEnabled(true).withModelKind("CHAT"));
      assertThat(settingsValues(AssistantRecordScreens.MODEL_PVS_NAME, Map.of("providerType", FAKE_TYPE, "id", id.toString())))
         .extracting(value -> String.valueOf(value.getId())).containsExactly("fake-model-1", "fake-model-2", "registered-chat");
      assertThat(settingsValues(AssistantRecordScreens.MODEL_PVS_NAME, Map.of("providerType", FAKE_TYPE)))
         .extracting(value -> String.valueOf(value.getId())).containsExactly("fake-model-1", "fake-model-2");
      assertThat(new SearchPossibleValueSourceAction().execute(new SearchPossibleValueSourceInput()
         .withPossibleValueSourceName(AssistantRecordScreens.MODEL_PVS_NAME).withOtherValues(Map.of("providerType", FAKE_TYPE)).withSearchTerm("fake-model-2")).getResults())
         .extracting(value -> String.valueOf(value.getId())).containsExactly("fake-model-2");
   }



   /*******************************************************************************
    ** Kinds use the selected type and keep their existing labels.
    *******************************************************************************/
   @Test
   void testContingentProviderKinds() throws Exception
   {
      assertThat(settingsValues("assistant_provider_kind", Map.of("providerType", FAKE_TYPE)))
         .extracting(value -> String.valueOf(value.getId())).containsExactly("FAKE");
      assertThat(settingsValues("assistant_provider_kind", Map.of())).anySatisfy(value ->
      {
         assertThat(value.getId()).isEqualTo("AZURE_OPENAI");
         assertThat(value.getLabel()).isEqualTo("Azure OpenAI");
      });
   }



   /*******************************************************************************
    ** A price id must not be interpreted as a provider id.
    *******************************************************************************/
   @Test
   void testContingentPriceModels() throws Exception
   {
      asAdmin();
      Integer id = providerId();
      new AssistantStore().insert(new AssistantModel().withProviderId(id).withModelId("registered-chat").withEnabled(true).withModelKind("CHAT"));
      String source = qInstance.getTable(AssistantModelPrice.TABLE_NAME).getField("model").getPossibleValueSourceName();
      assertThat(settingsValues(source, Map.of("providerType", FAKE_TYPE, "id", id)))
         .extracting(value -> String.valueOf(value.getId())).containsExactly("fake-model-1", "fake-model-2");
   }



   /*******************************************************************************
    ** Missing and unknown types retain the original union, including pricing.
    *******************************************************************************/
   @Test
   void testContingentUnionFallback() throws Exception
   {
      for(String source : List.of(AssistantRecordScreens.MODEL_PVS_NAME, "assistant_price_model", "assistant_provider_kind"))
      {
         List<QPossibleValue<?>> union = settingsValues(source, null);
         assertThat(settingsValues(source, Map.of())).usingRecursiveComparison().isEqualTo(union);
         assertThat(settingsValues(source, Map.of("providerType", "unknown"))).usingRecursiveComparison().isEqualTo(union);
      }
      assertThat(settingsValues(AssistantRecordScreens.MODEL_PVS_NAME, null)).extracting(value -> String.valueOf(value.getId()))
         .contains("fake-model-1", "claude-sonnet-5-5");
   }



   /*******************************************************************************
    ** Saved values resolve without a selected type, even disabled registry ids.
    *******************************************************************************/
   @Test
   void testSavedPossibleValue() throws Exception
   {
      asAdmin();
      Integer id = providerId();
      new AssistantStore().insert(new AssistantModel().withProviderId(id).withModelId("saved-model").withEnabled(false).withModelKind("CHAT"));
      for(String source : List.of(AssistantRecordScreens.MODEL_PVS_NAME, "assistant_price_model", "assistant_provider_kind"))
      {
         QCustomPossibleValueProvider<?> custom = QCodeLoader.getAdHoc(QCustomPossibleValueProvider.class, qInstance.getPossibleValueSource(source).getCustomCodeReference());
         String saved = source.equals("assistant_provider_kind") ? "OPENAI" : "saved-model";
         assertThat(custom.getPossibleValue(saved).getId()).isEqualTo(saved);
         assertThat(custom.getPossibleValue("not-known")).isNull();
      }
   }



   /*******************************************************************************
    ** Exercise the registered source through QQQ's search action.
    *******************************************************************************/
   private List<QPossibleValue<?>> settingsValues(String source, Map<String, Serializable> otherValues) throws Exception
   {
      asAdmin();
      return (new SearchPossibleValueSourceAction().execute(new SearchPossibleValueSourceInput()
         .withPossibleValueSourceName(source).withOtherValues(otherValues)).getResults());
   }

}
