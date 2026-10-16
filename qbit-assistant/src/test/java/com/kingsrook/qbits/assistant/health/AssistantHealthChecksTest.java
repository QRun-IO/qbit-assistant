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

package com.kingsrook.qbits.assistant.health;


import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.engine.ProviderHealth;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.engine.UsageRecorder;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qbits.assistant.spi.AssistantModelProvider;
import com.kingsrook.qbits.assistant.spi.AssistantProviderType;
import com.kingsrook.qbits.assistant.spi.EmbeddingRequest;
import com.kingsrook.qbits.assistant.spi.EmbeddingResponse;
import com.kingsrook.qbits.assistant.spi.HealthResult;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelUsage;
import com.kingsrook.qbits.assistant.spi.ProviderSettings;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.health.HealthCategory;
import com.kingsrook.qqq.backend.core.health.HealthCheckInput;
import com.kingsrook.qqq.backend.core.health.HealthCheckKind;
import com.kingsrook.qqq.backend.core.health.HealthCheckMetaData;
import com.kingsrook.qqq.backend.core.health.HealthCheckOutcome;
import com.kingsrook.qqq.backend.core.health.HealthChecksMetaData;
import com.kingsrook.qqq.backend.core.health.HealthRunner;
import com.kingsrook.qqq.backend.core.health.HealthStatus;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.StatisticsData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;


/*******************************************************************************
 ** Registration, deadlines, billing and secret safety through the memory backend.
 *******************************************************************************/
class AssistantHealthChecksTest extends BaseAssistantTest
{
   private static final String SECRET = "S3NT1NEL-secret";
   private final FakeType type = new FakeType();
   private Boolean healthy = true;
   private Boolean failProbe = false;
   private Boolean failCreate = false;
   private Boolean failEmbedding = false;
   private Integer probes = 0;
   private Integer embeddings = 0;
   private ProviderSettings lastSettings;
   private CountDownLatch probeWait;
   private final CountDownLatch interrupted = new CountDownLatch(1);



   /*******************************************************************************
    ** Resolve a poisoned secret through the normal key-reference mechanism.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      provider = new EmbeddingProvider();
      config.withModelProvider(provider).withProviderType(type).withAllowedApiKeyEnvNames(Set.of("HEALTH_TEST_KEY"))
         .withSecretResolver(name -> SECRET);
   }



   /*******************************************************************************
    ** An adapter with no network and controllable provider failures.
    *******************************************************************************/
   class FakeType implements AssistantProviderType
   {
      /***************************************************************************
       ** Fake provider contract.
       ***************************************************************************/
      @Override
      public String getType()
      {
         return ("health-fake");
      }



      /***************************************************************************
       ** Fake provider contract.
       ***************************************************************************/
      @Override
      public String getLabel()
      {
         return ("Health fake");
      }



      /***************************************************************************
       ** Fake provider contract.
       ***************************************************************************/
      @Override
      public List<String> getModels()
      {
         return (List.of("model"));
      }



      /***************************************************************************
       ** Fake provider contract.
       ***************************************************************************/
      @Override
      public String getDefaultApiKeyEnvName()
      {
         return ("HEALTH_TEST_KEY");
      }



      /***************************************************************************
       ** Fake provider contract.
       ***************************************************************************/
      @Override
      public String getDefaultBaseUrl()
      {
         return ("https://health.invalid");
      }



      /***************************************************************************
       ** Fake provider contract.
       ***************************************************************************/
      @Override
      public String getDefaultLocation(String kind)
      {
         return ("LOCAL");
      }



      /***************************************************************************
       ** Reuse the existing scripted completion fake.
       ***************************************************************************/
      @Override
      public AssistantModelProvider create(String key, String url)
      {
         assertThat(key).isEqualTo(SECRET);
         if(failCreate)
         {
            throw (new IllegalStateException(SECRET));
         }
         return (provider);
      }



      /***************************************************************************
       ** Verify bounded settings and exercise hostile exception messages.
       ***************************************************************************/
      @Override
      public HealthResult checkHealth(ProviderSettings settings)
      {
         probes++;
         lastSettings = settings;
         assertThat(settings.getApiKey()).isEqualTo(SECRET);
         if(failProbe)
         {
            throw (new IllegalStateException(SECRET));
         }
         if(probeWait != null)
         {
            try
            {
               probeWait.await();
            }
            catch(InterruptedException e)
            {
               interrupted.countDown();
               Thread.currentThread().interrupt();
            }
         }
         return (new HealthResult(healthy, 7L, SECRET));
      }
   }



   /*******************************************************************************
    ** Keep embedding tests on the existing scripted-provider fixture as well.
    *******************************************************************************/
   class EmbeddingProvider extends ScriptedModelProvider
   {
      /***************************************************************************
       ** Minimal embedding result; no external service is contacted.
       ***************************************************************************/
      @Override
      public EmbeddingResponse embed(EmbeddingRequest request) throws ModelProviderException
      {
         embeddings++;
         if(failEmbedding)
         {
            throw (new ModelProviderException(ModelProviderException.Kind.NETWORK, null, SECRET));
         }
         return (new EmbeddingResponse(List.of(new float[] { 1, 0 }), new ModelUsage().withInputTokens(2L), "fake"));
      }
   }



   /*******************************************************************************
    ** Set up real settings records in the existing admin fixture session.
    *******************************************************************************/
   private AssistantProvider row() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      return (config.getRuntime().getStore().insert(new AssistantProvider().withName("Health test")
         .withProviderType(type.getType()).withEnabled(true)));
   }



   /*******************************************************************************
    ** Enabled chat model with a stable integer id.
    *******************************************************************************/
   private AssistantModel model(AssistantProvider row) throws Exception
   {
      return (config.getRuntime().getStore().insert(new AssistantModel().withProviderId(row.getId()).withModelId("model").withEnabled(true)));
   }



   /*******************************************************************************
    ** Exercise the producer-installed suppliers, not a separately constructed check.
    *******************************************************************************/
   private HealthCheckMetaData check(String name) throws Exception
   {
      return (HealthChecksMetaData.ofOrWithNew(qInstance).resolveAll(qInstance).stream()
         .filter(check -> name.equals(check.getName())).findFirst().orElseThrow());
   }



   /*******************************************************************************
    ** Raw outcomes must be clean; core's sanitize method is package-private.
    *******************************************************************************/
   private HealthCheckOutcome run(HealthCheckMetaData check) throws Exception
   {
      HealthCheckOutcome outcome = check.getCheckInstance().run(new HealthCheckInput().withQInstance(qInstance).withDeadline(Instant.now().plusSeconds(5)));
      assertThat(outcome.getMessage()).doesNotContain(SECRET, "health.invalid", "Connection test.", "\n");
      assertThat(outcome.getDetails().toJson()).doesNotContain(SECRET, "health.invalid", "Connection test.");
      return (outcome);
   }



   /*******************************************************************************
    ** Both suppliers are installed by production, refresh enabled sets and use AI.
    *******************************************************************************/
   @Test
   void registration() throws Exception
   {
      AssistantProvider local = row();
      AssistantProvider cloud = config.getRuntime().getStore().insert(new AssistantProvider().withName("Cloud")
         .withProviderType(type.getType()).withEnabled(true).withLocation("CLOUD"));
      AssistantModel model = model(local);
      model(cloud);
      config.getRuntime().getStore().insert(new AssistantProvider().withName("Disabled").withProviderType(type.getType()).withEnabled(false));
      config.getRuntime().getStore().insert(new AssistantModel().withProviderId(local.getId()).withModelId("disabled").withEnabled(false));
      var registry = HealthChecksMetaData.ofOrWithNew(qInstance);
      AssistantHealthChecks.register(qInstance);
      assertThat(registry.getSuppliers()).hasSize(2).allSatisfy(supplier -> assertThat(supplier.getComponent()).isEqualTo("assistant"));
      assertThat(registry.resolveAll(qInstance)).hasSize(5).allSatisfy(check ->
      {
         assertThat(check.getKind()).isEqualTo(HealthCheckKind.DEPENDENCY);
         assertThat(check.getCategory()).isEqualTo(HealthCategory.AI);
         assertThat(check.getComponent()).isEqualTo("assistant");
         assertThat(check.getName()).matches("[a-z0-9]+([._-][a-z0-9]+)*");
      });
      assertThat(check("assistant.provider." + local.getId()).getIntervalSeconds()).isEqualTo(60);
      assertThat(check("assistant.provider." + cloud.getId()).getIntervalSeconds()).isEqualTo(300);
      var modelCheck = check("assistant.model." + model.getId());
      assertThat(modelCheck.getIntervalSeconds()).isEqualTo(21600);
      assertThat(modelCheck.getTimeoutMs()).isEqualTo(30000);
      assertThat(check("assistant.provider." + local.getId()).getTimeoutMs()).isEqualTo(10000);
      config.getRuntime().getStore().updateFields(AssistantModel.TABLE_NAME, new QRecord().withValue("id", model.getId()).withValue("enabled", false));
      assertThat(registry.resolveAll(qInstance)).hasSize(4);
      assertThat(run(modelCheck).getStatus()).isEqualTo(HealthStatus.UNKNOWN);
   }



   /*******************************************************************************
    ** HealthResult messages and thrown adapter messages never enter outcomes.
    *******************************************************************************/
   @Test
   void providerStatesAndNoSecrets() throws Exception
   {
      AssistantProvider row = row();
      var check = check("assistant.provider." + row.getId());
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.UP);
      assertThat(lastSettings.getRequestTimeoutSeconds()).isLessThanOrEqualTo(5);
      healthy = false;
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.DOWN);
      failProbe = true;
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.DOWN);
      failProbe = false;
      healthy = null;
      assertThat(run(check).getMessage()).isEqualTo("health probe not supported");
      for(String state : List.of("UP", "DOWN", "DEGRADED"))
      {
         config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", row.getId())
            .withValue("healthState", state).withValue("healthCheckedAt", Instant.now().minusSeconds(30)));
         assertThat(run(check).getStatus()).isEqualTo(HealthStatus.valueOf(state));
      }
      for(Instant time : List.of(Instant.now().minusSeconds(600), Instant.now().plusSeconds(30)))
      {
         config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", row.getId()).withValue("healthCheckedAt", time));
         assertThat(run(check).getStatus()).isEqualTo(HealthStatus.UNKNOWN);
      }
      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", row.getId()).withValue("enabled", false));
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.UNKNOWN);
   }



   /*******************************************************************************
    ** Missing adapter/configuration is safe and does not dispatch a completion.
    *******************************************************************************/
   @Test
   void missingAdapterAndSecret() throws Exception
   {
      AssistantProvider row = row();
      var check = check("assistant.provider." + row.getId());
      config.withSecretResolver(name -> null);
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.DOWN);
      config.getProviderTypes().remove(type.getType());
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.UNKNOWN);
   }



   /*******************************************************************************
    ** Successful and failed completions use TEST and preserve manual test history.
    *******************************************************************************/
   @Test
   void modelAccountingAndNoSecrets() throws Exception
   {
      AssistantModel model = model(row());
      Instant previous = Instant.now().minusSeconds(1000);
      config.getRuntime().getStore().updateFields(AssistantModel.TABLE_NAME, new QRecord().withValue("id", model.getId())
         .withValue("lastTestAt", previous).withValue("lastTestStatus", "OK"));
      var check = check("assistant.model." + model.getId());
      provider.thenText(SECRET);
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.UP);
      assertThat(provider.getRequests().getFirst().getMaxTokens()).isEqualTo(32);
      assertThat(probes).isZero();
      provider.thenError(ModelProviderException.Kind.NETWORK, SECRET);
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.DOWN);
      var usage = config.getRuntime().getStore().queryRecords(AssistantUsage.TABLE_NAME, new QQueryFilter(), 10);
      assertThat(usage).hasSize(2).allSatisfy(record -> assertThat(record.getValueString("purpose")).isEqualTo(UsageRecorder.PURPOSE_TEST));
      assertThat(config.getRuntime().getStore().listModels().getFirst().getLastTestAt()).isEqualTo(previous);
      failCreate = true;
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.DOWN);
   }



   /*******************************************************************************
    ** Monthly cap skips even on-demand checks, before any billable request.
    *******************************************************************************/
   @Test
   void modelCostCapAndDisabledProvider() throws Exception
   {
      AssistantProvider row = row();
      AssistantModel model = model(row);
      var check = check("assistant.model." + model.getId());
      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", row.getId()).withValue("monthlyCostCapUsd", BigDecimal.ZERO));
      var outcome = check.getCheckInstance().run(new HealthCheckInput().withQInstance(qInstance).withDeadline(Instant.now().plusSeconds(5)).withOnDemand(true));
      assertThat(outcome.getStatus()).isEqualTo(HealthStatus.UNKNOWN);
      assertThat(outcome.getMessage()).isEqualTo("skipped: cost cap");
      assertThat(provider.getRequests()).isEmpty();
      assertThat(config.getRuntime().getStore().queryRecords(AssistantUsage.TABLE_NAME, new QQueryFilter(), 10)).isEmpty();
      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", row.getId()).withValue("enabled", false));
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.UNKNOWN);
   }



   /*******************************************************************************
    ** The real runner identity records only its own usage and sees shared caps.
    *******************************************************************************/
   @Test
   void automatedSessionAccountingAndSharedCap() throws Exception
   {
      AssistantProvider row = row();
      AssistantModel model = model(row);
      var check = check("assistant.model." + model.getId());
      QContext.init(qInstance, new QSession().withUser(new QUser().withIdReference("health_runner"))
         .withSecurityKeyValues(Map.of()).withPermissions("health.read", "health.write"));
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.UP);
      assertThat(QContext.getQSession().getSecurityKeyValues()).containsOnlyKeys(config.getOwnerSecurityKeyType());
      assertThat(QContext.getQSession().hasPermission("assistant.admin")).isFalse();
      var usage = config.getRuntime().getStore().queryRecords(AssistantUsage.TABLE_NAME, new QQueryFilter(), 10);
      assertThat(usage).hasSize(1);
      assertThat(usage.getFirst().getValueString("userId")).isEqualTo("health_runner");
      var healthSession = QContext.getQSession();
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
      config.getRuntime().getStore().insert(new AssistantUsage().withUserId(AssistantTestFixture.EDITOR).withProviderId(row.getId())
         .withCostMicros(1_000_000L).withCreateDate(Instant.now()).withPurpose("TURN").withStatus("OK"));
      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", row.getId()).withValue("monthlyCostCapUsd", BigDecimal.ONE));
      QContext.init(qInstance, healthSession);
      assertThat(config.getRuntime().getProviderService().monthToDateCostMicros(row.getId())).isZero();
      assertThat(run(check).getMessage()).isEqualTo("skipped: cost cap");
      assertThat(provider.getRequests()).hasSize(1);
   }



   /*******************************************************************************
    ** The kill switch is read fresh before every scheduled model call.
    *******************************************************************************/
   @Test
   void modelKillSwitch() throws Exception
   {
      var check = check("assistant.model." + model(row()).getId());
      config.getRuntime().getStore().insert(new AssistantPolicy().withName("Stop").withKillSwitch(true));
      assertThat(run(check).getMessage()).isEqualTo("skipped: kill switch");
      assertThat(provider.getRequests()).isEmpty();
   }



   /*******************************************************************************
    ** Embedding models reuse ConnectionTest's embedding path, never chat.
    *******************************************************************************/
   @Test
   void embeddingAccountingAndNoSecrets() throws Exception
   {
      AssistantProvider row = row();
      AssistantModel model = config.getRuntime().getStore().insert(new AssistantModel().withProviderId(row.getId()).withModelId("embedding")
         .withModelKind("EMBEDDING").withEnabled(true));
      var check = check("assistant.model." + model.getId());
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.UP);
      failEmbedding = true;
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.DOWN);
      assertThat(embeddings).isEqualTo(2);
      assertThat(provider.getRequests()).isEmpty();
      assertThat(config.getRuntime().getStore().queryRecords(AssistantUsage.TABLE_NAME, new QQueryFilter(), 10))
         .hasSize(2).allSatisfy(record -> assertThat(record.getValueString("purpose")).isEqualTo("TEST"));
   }



   /*******************************************************************************
    ** Expired input performs no work; a stuck existing probe is interrupted.
    *******************************************************************************/
   @Test
   void providerDeadline() throws Exception
   {
      var check = check("assistant.provider." + row().getId());
      assertThat(check.getCheckInstance().run(new HealthCheckInput().withQInstance(qInstance).withDeadline(Instant.now().minusSeconds(1))).getStatus()).isEqualTo(HealthStatus.DOWN);
      assertThat(probes).isZero();
      probeWait = new CountDownLatch(1);
      assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
      {
         QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
         try
         {
            var outcome = check.getCheckInstance().run(new HealthCheckInput().withQInstance(qInstance).withDeadline(Instant.now().plusMillis(200)));
            assertThat(outcome.getMessage()).isEqualTo("Health check timed out");
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
         }
         finally
         {
            QContext.clear();
         }
      });
   }



   /*******************************************************************************
    ** A slow completion cannot outlive the caller's deadline.
    *******************************************************************************/
   @Test
   void modelDeadline() throws Exception
   {
      var check = check("assistant.model." + model(row()).getId());
      provider.withDelay(Duration.ofSeconds(10)).thenText("OK");
      assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
      {
         QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
         try
         {
            var outcome = check.getCheckInstance().run(new HealthCheckInput().withQInstance(qInstance).withDeadline(Instant.now().plusMillis(300)));
            assertThat(outcome.getStatus()).isEqualTo(HealthStatus.DOWN);
         }
         finally
         {
            QContext.clear();
         }
      });
   }



   /*******************************************************************************
    ** Drive the real runner cache without its scheduler or health-table writes.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private HealthRunner cached(String name, HealthCheckOutcome outcome, Instant at) throws Exception
   {
      HealthRunner runner = new HealthRunner(qInstance, "test", Clock.fixed(at, ZoneOffset.UTC));
      var cache = HealthRunner.class.getDeclaredMethod("cacheOutcome", String.class, HealthCheckOutcome.class);
      cache.setAccessible(true);
      cache.invoke(runner, name, outcome);
      var runners = HealthRunner.class.getDeclaredField("RUNNERS");
      runners.setAccessible(true);
      ((Map<QInstance, HealthRunner>) runners.get(null)).put(qInstance, runner);
      return (runner);
   }



   /*******************************************************************************
    ** Fresh central results avoid duplicate local probes; stale/unknown fall back.
    *******************************************************************************/
   @Test
   void breakerUsesCentralResults() throws Exception
   {
      AssistantProvider row = row();
      var active = new ProviderService.Active(row, type.getType(), "model", provider);
      ProviderHealth breaker = new ProviderHealth(config.getRuntime());
      String name = "assistant.provider." + row.getId();
      for(HealthStatus status : List.of(HealthStatus.UP, HealthStatus.DEGRADED, HealthStatus.DOWN))
      {
         try(var central = cached(name, new HealthCheckOutcome().withStatus(status), Instant.now().minusSeconds(1)))
         {
            if(status == HealthStatus.DOWN)
            {
               assertThatThrownBy(() -> breaker.beforeCall(active)).isInstanceOf(ModelProviderException.class);
            }
            else
            {
               breaker.beforeCall(active);
            }
            assertThat(probes).isZero();
         }
      }
      try(var central = cached(name, HealthCheckOutcome.down("Down"), Instant.now().minusSeconds(60)))
      {
         breaker.beforeCall(active);
         assertThat(probes).isEqualTo(1);
      }
      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", row.getId()).withValue("healthCheckedAt", null));
      try(var central = cached(name, HealthCheckOutcome.unknown("Unsupported"), Instant.now()))
      {
         breaker.beforeCall(active);
         assertThat(probes).isEqualTo(2);
      }
   }



   /*******************************************************************************
    ** The replacement card is generic and links to filtered central health.
    *******************************************************************************/
   @Test
   void centralHealthCard() throws Exception
   {
      AssistantProvider row = row();
      assertThat(qInstance.getWidget(AssistantHealthWidget.NAME).getType()).isEqualTo(WidgetType.STATISTICS.getType());
      var renderer = new AssistantHealthWidget();
      StatisticsData data = (StatisticsData) renderer.render(new RenderWidgetInput()).getWidgetData();
      assertThat(data.getCount()).isEqualTo("Not checked yet");
      assertThat(data.getCountContext()).contains("Provider checks run every 1 to 5 minutes");
      assertThat(data.getCountURL()).startsWith("/app/").contains("health_check", "assistant");
      try(var central = cached("assistant.provider." + row.getId(), HealthCheckOutcome.up("Reachable"), Instant.now()))
      {
         var cache = HealthRunner.class.getDeclaredMethod("cacheOutcome", String.class, HealthCheckOutcome.class);
         cache.setAccessible(true);
         cache.invoke(central, AssistantHealthChecks.CLIENT_ERRORS, HealthCheckOutcome.up("0 client errors reported in the last hour"));
         data = (StatisticsData) renderer.render(new RenderWidgetInput()).getWidgetData();
         assertThat(data.getCount()).isEqualTo("Healthy");

         //////////////////////////////////////////////////////////////////
         // many client errors in the last hour show on the card as well //
         //////////////////////////////////////////////////////////////////
         cache.invoke(central, AssistantHealthChecks.CLIENT_ERRORS, HealthCheckOutcome.degraded("25 client errors reported in the last hour"));
         data = (StatisticsData) renderer.render(new RenderWidgetInput()).getWidgetData();
         assertThat(data.getCount()).isEqualTo("Degraded");
      }
   }


   /*******************************************************************************
    ** A second enabled provider, not the default.
    *******************************************************************************/
   private AssistantProvider optionalRow(AssistantProvider defaultRow) throws Exception
   {
      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", defaultRow.getId()).withValue("isDefault", true).withValue("name", "Claude"));
      AssistantProvider optional = config.getRuntime().getStore().insert(new AssistantProvider().withName("lore (local GPU)")
         .withProviderType(type.getType()).withEnabled(true).withIsDefault(false));
      config.getRuntime().getProviderService().invalidate();
      return (optional);
   }



   /*******************************************************************************
    ** An unreachable optional provider is DEGRADED and names the default; the
    ** default stays DOWN, and the breaker still refuses the optional provider.
    *******************************************************************************/
   @Test
   void optionalProviderIsDegradedDefaultIsDown() throws Exception
   {
      AssistantProvider defaultRow = row();
      AssistantProvider lore       = optionalRow(defaultRow);
      healthy = false;

      HealthCheckOutcome outcome = run(check("assistant.provider." + lore.getId()));
      assertThat(outcome.getStatus()).isEqualTo(HealthStatus.DEGRADED);
      assertThat(outcome.getMessage()).isEqualTo("Provider 'lore (local GPU)' is unreachable; it is optional (not the default and not the only route), so requests use 'Claude'");
      assertThat(outcome.getDetails().getValues()).containsEntry("reachable", false).containsEntry("optional", true).containsEntry("providerId", lore.getId());
      assertThat(ProviderHealth.isUnreachable(outcome)).isTrue();

      HealthCheckOutcome required = run(check("assistant.provider." + defaultRow.getId()));
      assertThat(required.getStatus()).isEqualTo(HealthStatus.DOWN);
      assertThat(required.getDetails().getValues()).containsEntry("required", "default provider").containsEntry("reachable", false);

      healthy = true;
      assertThat(run(check("assistant.provider." + lore.getId())).getStatus()).isEqualTo(HealthStatus.UP);
      assertThat(ProviderHealth.isUnreachable(HealthCheckOutcome.degraded("Slow"))).isFalse();
      assertThat(ProviderHealth.isUnreachable(null)).isFalse();

      ////////////////////////////////////////////////////////////////////
      // a fresh DEGRADED-but-unreachable central result opens the breaker //
      ////////////////////////////////////////////////////////////////////
      var active = new ProviderService.Active(lore, type.getType(), "model", provider);
      try(var central = cached("assistant.provider." + lore.getId(), outcome, Instant.now().minusSeconds(1)))
      {
         assertThatThrownBy(() -> new ProviderHealth(config.getRuntime()).beforeCall(active)).isInstanceOf(ModelProviderException.class);
         assertThat(probes).isEqualTo(3);
      }
   }



   /*******************************************************************************
    ** Model checks follow their provider: DEGRADED when optional.
    *******************************************************************************/
   @Test
   void optionalProviderModelIsDegraded() throws Exception
   {
      AssistantProvider defaultRow = row();
      AssistantProvider lore       = optionalRow(defaultRow);
      AssistantModel    model      = model(lore);
      provider.thenError(ModelProviderException.Kind.NETWORK, SECRET);
      HealthCheckOutcome outcome = run(check("assistant.model." + model.getId()));
      assertThat(outcome.getStatus()).isEqualTo(HealthStatus.DEGRADED);
      assertThat(outcome.getMessage()).startsWith("Model connection failed; provider 'lore (local GPU)' is optional").endsWith("so requests use 'Claude'");
      assertThat(outcome.getDetails().getValues()).doesNotContainKey("reachable").containsEntry("modelId", model.getId());

      AssistantModel defaultModel = model(defaultRow);
      provider.thenError(ModelProviderException.Kind.NETWORK, SECRET);
      assertThat(run(check("assistant.model." + defaultModel.getId())).getStatus()).isEqualTo(HealthStatus.DOWN);
   }



   /*******************************************************************************
    ** A provider that is the only route for routing or a budget downgrade stays
    ** DOWN; an explicit fallback on another provider makes it optional again.
    *******************************************************************************/
   @Test
   void soleRouteStaysDown() throws Exception
   {
      AssistantProvider defaultRow = row();
      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", defaultRow.getId()).withValue("location", "CLOUD"));
      AssistantProvider lore         = optionalRow(defaultRow);
      AssistantModel    loreModel    = model(lore);
      AssistantModel    defaultModel = model(defaultRow);
      healthy = false;
      var check = check("assistant.provider." + lore.getId());
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.DEGRADED);

      AssistantProfile profile = config.getRuntime().getStore().insert(new AssistantProfile().withName("routing").withDisplayName("Assistant").withEnabled(true)
         .withRoutingMode("HEURISTIC").withSimpleModelId(loreModel.getId()).withComplexModelId(defaultModel.getId()));
      HealthCheckOutcome outcome = run(check);
      assertThat(outcome.getStatus()).isEqualTo(HealthStatus.DOWN);
      assertThat(outcome.getDetails().getValues()).containsEntry("required", "only route for a configured model");

      config.getRuntime().getStore().updateFields(AssistantModel.TABLE_NAME, new QRecord().withValue("id", loreModel.getId()).withValue("fallbackModelId", defaultModel.getId()));
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.DEGRADED);

      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", defaultRow.getId()).withValue("setupState", AssistantProvider.SETUP_STATE_SETTING_UP));
      config.getRuntime().getProviderService().invalidate();
      assertThat(run(check).getStatus()).as("lore is the only usable provider left, hence the default").isEqualTo(HealthStatus.DOWN);
      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", defaultRow.getId()).withValue("setupState", AssistantProvider.SETUP_STATE_ACTIVE));
      config.getRuntime().getProviderService().invalidate();

      config.getRuntime().getStore().updateFields(AssistantModel.TABLE_NAME, new QRecord().withValue("id", loreModel.getId()).withValue("fallbackModelId", null));
      config.getRuntime().getStore().updateFields(AssistantProfile.TABLE_NAME, new QRecord().withValue("id", profile.getId()).withValue("routingMode", "OFF"));
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.DEGRADED);

      config.getRuntime().getStore().insert(new AssistantBudget().withName("Downgrade").withScopeType("INSTANCE").withPeriod("MONTH").withMetric("COST_USD")
         .withLimitValue(BigDecimal.ONE).withEnabled(true).withDowngradeModelId(loreModel.getId()));
      assertThat(run(check).getStatus()).isEqualTo(HealthStatus.DOWN);
   }



   /*******************************************************************************
    ** Another local provider of the same type is the automatic fallback.
    *******************************************************************************/
   @Test
   void localPoolIsAFallback() throws Exception
   {
      AssistantProvider defaultRow = row();
      AssistantProvider lore       = optionalRow(defaultRow);
      AssistantModel    loreModel  = model(lore);
      model(defaultRow);
      config.getRuntime().getStore().insert(new AssistantProfile().withName("routing").withDisplayName("Assistant").withEnabled(true)
         .withRoutingMode("HEURISTIC").withSimpleModelId(loreModel.getId()).withComplexModelId(loreModel.getId()));
      healthy = false;
      assertThat(run(check("assistant.provider." + lore.getId())).getStatus()).isEqualTo(HealthStatus.DEGRADED);
   }



   /*******************************************************************************
    ** Setting up: no checks registered, no routing, and an already resolved check
    ** reports UP without probing.
    *******************************************************************************/
   @Test
   void settingUpSkipsChecksAndRouting() throws Exception
   {
      AssistantProvider defaultRow = row();
      AssistantProvider lore       = optionalRow(defaultRow);
      AssistantModel    model      = model(lore);
      assertThat(lore.getSetupState()).isEqualTo(AssistantProvider.SETUP_STATE_ACTIVE);
      var providerCheck = check("assistant.provider." + lore.getId());
      var modelCheck    = check("assistant.model." + model.getId());

      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", lore.getId()).withValue("setupState", AssistantProvider.SETUP_STATE_SETTING_UP));
      config.getRuntime().getProviderService().invalidate();
      assertThat(HealthChecksMetaData.ofOrWithNew(qInstance).resolveAll(qInstance)).extracting(HealthCheckMetaData::getName)
         .doesNotContain("assistant.provider." + lore.getId(), "assistant.model." + model.getId())
         .contains("assistant.provider." + defaultRow.getId());
      Integer before = probes;
      assertThat(run(providerCheck).getMessage()).isEqualTo(AssistantHealthChecks.NOT_SET_UP);
      assertThat(run(modelCheck).getStatus()).isEqualTo(HealthStatus.UP);
      assertThat(probes).isEqualTo(before);
      assertThat(provider.getRequests()).isEmpty();

      assertThat(config.getRuntime().getProviderService().resolveModel(model.getId(), false)).isNull();
      assertThatThrownBy(() -> new ProviderHealth(config.getRuntime()).beforeCall(new ProviderService.Active(lore, type.getType(), "model", provider)))
         .hasMessageContaining("not set up yet");

      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", lore.getId()).withValue("isDefault", true));
      config.getRuntime().getProviderService().invalidate();
      assertThat(config.getRuntime().getProviderService().activeRow().getId()).as("a default that is setting up is skipped").isEqualTo(defaultRow.getId());

      assertThatThrownBy(() -> config.getRuntime().getStore().insert(new AssistantProvider().withName("Bad").withProviderType(type.getType()).withEnabled(true).withSetupState("BOGUS")))
         .hasMessageContaining("Setup state must be Active or Setting up.");
   }



   /*******************************************************************************
    ** If routes cannot be read the failure stays DOWN; other statuses pass through.
    *******************************************************************************/
   @Test
   void optionalPassThrough() throws Exception
   {
      assertThat(AssistantHealthChecks.optional(config.getRuntime(), null, null, false)).isNull();
      HealthCheckOutcome up = HealthCheckOutcome.up("fine");
      assertThat(AssistantHealthChecks.optional(config.getRuntime(), 1, up, false)).isSameAs(up);
      assertThat(AssistantHealthChecks.optional(config.getRuntime(), null, HealthCheckOutcome.down("x"), true).getStatus()).isEqualTo(HealthStatus.DOWN);
      assertThat(AssistantHealthChecks.optional(null, 1, HealthCheckOutcome.down("x"), false).getStatus()).isEqualTo(HealthStatus.DOWN);
   }
}
