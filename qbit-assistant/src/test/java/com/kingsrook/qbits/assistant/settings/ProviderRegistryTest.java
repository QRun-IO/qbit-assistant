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


import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.engine.ConnectionTest;
import com.kingsrook.qbits.assistant.engine.ModelDiscovery;
import com.kingsrook.qbits.assistant.engine.ModelPricing;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.home.AssistantHomeData;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantModelPrice;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.processes.DiscoverModelsProcess;
import com.kingsrook.qbits.assistant.processes.TestModelConnectionProcess;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qbits.assistant.provider.DiscoveredModel;
import com.kingsrook.qbits.assistant.provider.HealthResult;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ProviderSettings;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.actions.processes.QProcessCallbackFactory;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Registry behavior through the memory-backed settings tables; no network.
 *******************************************************************************/
class ProviderRegistryTest extends BaseAssistantTest
{
   private final FakeType type = new FakeType();
   private int creates;
   private int discoveries;
   private int healthChecks;
   private boolean unhealthy;
   private boolean discoveryError;
   private int modelCount = 3;
   private String secret;



   /*******************************************************************************
    ** Test adapter with cloud and keyless local dialects.
    *******************************************************************************/
   class FakeType implements AssistantProviderType
   {



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public String getType()
      {
         return ("openai-compatible");
      }



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public String getLabel()
      {
         return ("Test registry");
      }



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public List<String> getModels()
      {
         return (List.of("fallback"));
      }



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public List<String> getKinds()
      {
         return (List.of("OLLAMA", "OPENAI", "AZURE_OPENAI", "OPENAI_COMPATIBLE"));
      }



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public String getDefaultApiKeyEnvName()
      {
         return (null);
      }



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public String getDefaultApiKeyEnvName(String kind)
      {
         return (kind.equals("OPENAI") ? "OPENAI_API_KEY" : (kind.equals("AZURE_OPENAI") ? "AZURE_OPENAI_API_KEY" : null));
      }



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public String getDefaultBaseUrl()
      {
         return (null);
      }



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public String getDefaultBaseUrl(String kind)
      {
         return (kind.equals("OLLAMA") ? "http://localhost:11434/v1" : (kind.equals("OPENAI") ? "https://api.openai.com/v1" : null));
      }



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public String getDefaultLocation(String kind)
      {
         return (kind.equals("OLLAMA") ? "LOCAL" : "CLOUD");
      }



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public AssistantModelProvider create(String key, String url)
      {
         creates++;
         return (provider);
      }



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public List<DiscoveredModel> discoverModels(ProviderSettings settings) throws ModelProviderException
      {
         discoveries++;
         if(discoveryError)
         {
            throw (new ModelProviderException(ModelProviderException.Kind.NETWORK, null, "Discovery unreachable"));
         }
         List<DiscoveredModel> models = new ArrayList<>();
         for(int i = 0; i < modelCount; i++)
         {
            models.add(new DiscoveredModel("model-" + i, "Model " + i, 8192, "test"));
         }
         return (models);
      }



      /*******************************************************************************
       ** Provider contract.
       *******************************************************************************/
      @Override
      public HealthResult checkHealth(ProviderSettings settings)
      {
         healthChecks++;
         return (new HealthResult(!unhealthy, 1L, unhealthy ? "Unreachable" : "Healthy"));
      }
   }



   /*******************************************************************************
    ** Configure explicit reference and URL allowlists.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      config.withProviderType(type).withSecretResolver(n -> secret).withAllowedApiKeyEnvNames(Set.of("LOCAL_PROXY_KEY"))
         .withAllowedBaseUrls(Set.of("https://approved.example"));
   }



   /*******************************************************************************
    ** Create settings under the admin's own session.
    *******************************************************************************/
   private AssistantProvider row() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      return (config.getRuntime().getStore().insert(new AssistantProvider().withName("Local").withProviderType(type.getType()).withKind("OLLAMA").withEnabled(true)));
   }



   /*******************************************************************************
    ** Keyless rows, kind validation, URL guards, timeouts and client fingerprints.
    *******************************************************************************/
   @Test
   void settingsAndCache() throws Exception
   {
      AssistantProvider row = row();
      var service = config.getRuntime().getProviderService();
      assertThat(row.getLocation()).isEqualTo("LOCAL");
      assertThat(row.getHipaaEligible()).isFalse();
      assertThat(row.getConnectTimeoutSeconds()).isEqualTo(10);
      assertThat(row.getRequestTimeoutSeconds()).isEqualTo(120);
      assertThat(service.keyStatus(row)).isEqualTo("Not needed (no API key)");
      assertThat(service.forRow(row, "legacy").model()).isEqualTo("legacy");
      service.forRow(row, "legacy");
      assertThat(creates).isEqualTo(1);
      row.withApiVersion("v1");
      service.forRow(row, "legacy");
      row.withRegion("r1");
      service.forRow(row, "legacy");
      row.withConnectTimeoutSeconds(11);
      service.forRow(row, "legacy");
      row.withRequestTimeoutSeconds(121);
      service.forRow(row, "legacy");
      assertThat(creates).isEqualTo(5);
      row.withApiKeyEnvName("LOCAL_PROXY_KEY");
      assertThatThrownBy(() -> service.settings(row)).hasMessageContaining("not set");
      secret = "private-test-key";
      assertThat(service.settings(row).toString()).doesNotContain(secret);
      service.forRow(row, "legacy");
      secret = "rotated-test-key";
      service.forRow(row, "legacy");
      assertThat(creates).isEqualTo(7);
      assertThat(service.validate(row.withKind("ANTHROPIC"))).anyMatch(e -> e.contains("kind"));
      row.withKind("AZURE_OPENAI").withApiKeyEnvName(null);
      assertThat(service.validate(row)).anyMatch(e -> e.contains("base URL"));
      assertThat(service.validate(row.withBaseUrl("https://unapproved.example"))).anyMatch(e -> e.contains("not allowed"));
      assertThat(service.validate(row.withBaseUrl("https://approved.example"))).isEmpty();
      assertThat(config.getEffectiveAllowedApiKeyEnvNames()).contains("OPENAI_API_KEY", "AZURE_OPENAI_API_KEY").doesNotContainNull();
      for(int invalid : List.of(0, 601))
      {
         assertThat(service.validate(row.withConnectTimeoutSeconds(invalid))).anyMatch(e -> e.contains("600"));
         row.withConnectTimeoutSeconds(10);
         assertThat(service.validate(row.withRequestTimeoutSeconds(invalid))).anyMatch(e -> e.contains("600"));
      }
      row.withRequestTimeoutSeconds(120).withApiKeyEnvName("DATABASE_PASSWORD");
      assertThat(service.validate(row)).anyMatch(e -> e.contains("not allowed"));
      row.withApiKeyEnvName("not a name");
      assertThat(service.validate(row)).anyMatch(e -> e.contains("NAME"));
   }



   /*******************************************************************************
    ** Registry selection, invalidation, price precedence and call capabilities.
    *******************************************************************************/
   @Test
   void modelsPricesAndCapabilities() throws Exception
   {
      AssistantProvider row = row();
      var store = config.getRuntime().getStore();
      var service = config.getRuntime().getProviderService();
      AssistantModel disabled = store.insert(new AssistantModel().withProviderId(row.getId()).withModelId("disabled").withEnabled(false));
      assertThatThrownBy(() -> service.forRow(row, "fallback")).hasMessageContaining("All models");
      AssistantModel model = store.insert(new AssistantModel().withProviderId(row.getId()).withModelId("active").withEnabled(true)
         .withSupportsTools(false).withSupportsVision(true).withSupportsStreaming(false).withSupportsReasoningEffort(true).withContextWindowTokens(4096).withMaxOutputTokens(16));
      var active = service.forRow(row, "fallback");
      assertThat(active.model()).isEqualTo("active");
      var caps = active.provider().getCapabilities("active");
      assertThat(caps.getTools()).isFalse();
      assertThat(caps.getVision()).isTrue();
      assertThat(caps.getStreaming()).isFalse();
      assertThat(caps.getReasoningEffort()).isTrue();
      assertThat(caps.getMaxContextTokens()).isEqualTo(4096);
      provider.thenText("OK");
      active.provider().stream(new ModelRequest().withModel("active").withMaxTokens(32), t -> {}).await(Duration.ofSeconds(2));
      assertThat(provider.getRequests().getLast().getMaxTokens()).isEqualTo(16);
      assertThat(provider.getRequests().getLast().getCapabilities().getStreaming()).isFalse();
      assertThat(active.provider().countTokens(new ModelRequest())).isNull();
      assertThatThrownBy(() -> service.forRow(row.withModel("disabled"), "fallback")).hasMessageContaining("disabled");
      row.withModel(null);
      assertThat(service.pricing(active, "active").getInputUsdPerMTok()).isZero();
      config.getPricing().put("active", new ModelPricing().withInputUsdPerMTok(9.0));
      assertThat(service.pricing(active, "active").getInputUsdPerMTok()).isEqualTo(9.0);
      store.insert(new AssistantModelPrice().withProviderType(type.getType()).withModel("active").withInputUsdPerMTok(new BigDecimal("5")).withOutputUsdPerMTok(new BigDecimal("6")));
      assertThat(service.pricing(active, "active").getInputUsdPerMTok()).isEqualTo(5.0);
      store.update(model.withInputUsdPerMTok(new BigDecimal("2")).withOutputUsdPerMTok(new BigDecimal("3")));
      assertThat(service.pricing(active, "active").getInputUsdPerMTok()).isEqualTo(2.0);
      store.update(row.withModel("active"));
      assertThat(service.forRow(store.getProvider(row.getId()), "fallback").model()).isEqualTo("active");
      row.withModel(null);
      store.update(disabled.withEnabled(true));
      assertThat(service.forRow(row, "fallback").model()).isEqualTo("disabled");
   }



   /*******************************************************************************
    ** Discovery preserves edits, disables large cloud catalogs and records errors.
    *******************************************************************************/
   @Test
   void discovery() throws Exception
   {
      AssistantProvider row = row();
      var runtime = config.getRuntime();
      var transform = new DiscoverModelsProcess.Transform();
      var input = new RunBackendStepInput();
      input.setRecords(List.of(row.toQRecord()));
      var output = new RunBackendStepOutput();
      transform.runOnePage(input, output);
      assertThat(discoveries).isZero();
      assertThat(creates).isZero();
      assertThat(transform.getProcessSummary(output, false)).isNotEmpty();
      runProcess(DiscoverModelsProcess.NAME, row.getId());
      assertThat(discoveries).isEqualTo(1);
      assertThat(runtime.getStore().listModels()).hasSize(3).allSatisfy(m ->
      {
         assertThat(m.getEnabled()).isTrue();
         assertThat(m.getSource()).isEqualTo("DISCOVERED");
         assertThat(m.getInputUsdPerMTok()).isEqualByComparingTo(BigDecimal.ZERO);
      });
      AssistantModel edited = runtime.getStore().listModels().getFirst().withDisplayName("Admin name").withEnabled(false).withInputUsdPerMTok(BigDecimal.TEN);
      runtime.getStore().update(edited);
      assertThat(ModelDiscovery.run(runtime, row).message()).isEqualTo("0 models added, 3 already present");
      assertThat(runtime.getStore().listModels().getFirst().getDisplayName()).isEqualTo("Admin name");
      modelCount = 23;
      secret = "test-key";
      row = runtime.getStore().insert(new AssistantProvider().withName("Cloud").withProviderType(type.getType()).withKind("OPENAI"));
      assertThat(ModelDiscovery.run(runtime, row).ok()).isTrue();
      assertThat(runtime.getProviderService().models(row.getId())).hasSize(23).allMatch(m -> !m.getEnabled());
      discoveryError = true;
      assertThat(ModelDiscovery.run(runtime, row).ok()).isFalse();
      assertThat(runtime.getStore().getProvider(row.getId()).getLastDiscoveryError()).contains("Discovery unreachable");
   }



   /*******************************************************************************
    ** Model tests use the requested id, account usage and persist health failures.
    *******************************************************************************/
   @Test
   void modelTestAndMetadata() throws Exception
   {
      AssistantProvider row = row();
      var runtime = config.getRuntime();
      AssistantModel model = runtime.getStore().insert(new AssistantModel().withProviderId(row.getId()).withModelId("disabled-test").withEnabled(false));
      provider.thenText("OK");
      runProcess(TestModelConnectionProcess.NAME, model.getId());
      assertThat(healthChecks).isEqualTo(1);
      assertThat(provider.getRequests().getLast().getModel()).isEqualTo("disabled-test");
      assertThat(provider.getRequests().getLast().getMaxTokens()).isEqualTo(32);
      assertThat(runtime.getStore().listModels().getFirst().getLastTestStatus()).isEqualTo("OK");
      assertThat(runtime.getStore().queryRecords(AssistantUsage.TABLE_NAME, new QQueryFilter(), 10)).hasSize(1);
      unhealthy = true;
      assertThat(ConnectionTest.runModel(runtime, model, AssistantTestFixture.ADMIN).ok()).isFalse();
      assertThat(runtime.getStore().listModels().getFirst().getLastTestError()).contains("Unreachable");
      assertThat(provider.getRequests()).hasSize(1);
      assertThat(AssistantMetaDataBuilder.NAV_TABLES).doesNotContain(AssistantModel.TABLE_NAME);
      assertThat(qInstance.getTable(AssistantProvider.TABLE_NAME).getSections()).anyMatch(s -> s.getWidgetName() != null && s.getWidgetName().contains("model"));
      assertThat(qInstance.getTable(AssistantModel.TABLE_NAME).getField("providerId").getPossibleValueSourceName()).isEqualTo(AssistantProvider.TABLE_NAME);
      assertThat(qInstance.getTable(AssistantModel.TABLE_NAME).getField("lastTestError").getIsEditable()).isFalse();
      assertThat(qInstance.getTable(AssistantProvider.TABLE_NAME).getField("lastDiscoveryAt").getIsEditable()).isFalse();
      assertThat(AssistantHomeData.load(runtime).getProviders().getFirst().enabledModels()).isZero();
      assertThat(AssistantHomeData.load(runtime).getProviders().getFirst().keyPresent()).isTrue();
   }



   /*******************************************************************************
    ** Standard streamed ETL with Review and Result enabled.
    *******************************************************************************/
   private void runProcess(String process, Integer id) throws Exception
   {
      RunProcessInput input = new RunProcessInput();
      input.setProcessName(process);
      input.setCallback(QProcessCallbackFactory.forPrimaryKey("id", id));
      input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      new RunProcessAction().execute(input);
   }



   /*******************************************************************************
    ** Cloud project survives record mapping and changing it rebuilds the client.
    *******************************************************************************/
   @Test
   void cloudProjectRoundTripAndCache() throws Exception
   {
      AssistantProvider row = row();
      row.setCloudProject("project-one");
      var recordCopy = new AssistantProvider(row.toQRecord());
      assertThat(recordCopy.getCloudProject()).isEqualTo("project-one");
      var service = config.getRuntime().getProviderService();
      assertThat(service.settings(row).getCloudProject()).isEqualTo("project-one");
      assertThat(qInstance.getTable(AssistantProvider.TABLE_NAME).getField("cloudProject").getMaxLength()).isEqualTo(100);
      service.forRow(row, "legacy");
      service.forRow(row, "legacy");
      assertThat(creates).isEqualTo(1);
      row.withCloudProject("project-two");
      service.forRow(row, "legacy");
      assertThat(creates).isEqualTo(2);
      var settings = new ProviderSettings();
      settings.setCloudProject("project-three");
      assertThat(settings.getCloudProject()).isEqualTo("project-three");
   }
}
