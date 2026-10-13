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

package com.kingsrook.qbits.assistant.provider.anthropic.demo;


import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.engine.ConnectionTest;
import com.kingsrook.qbits.assistant.engine.ModelPricing;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantModelPrice;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.provider.anthropic.AnthropicModelProvider;
import com.kingsrook.qbits.assistant.provider.anthropic.AnthropicProviderType;
import com.kingsrook.qbits.assistant.provider.anthropic.RecordedAnthropicServer;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleCustomizerInterface;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;


/*******************************************************************************
 ** Local demo of the Assistant app home and provider settings, for
 ** screenshots (never against production). The Anthropic API is the
 ** recorded test server, so every call is a real HTTP call through the real
 ** adapter, engine, Test connection and usage recording - with canned
 ** answers and no key. Everyone is an admin.
 **
 ** Run with the test classpath plus the qqq-frontend-next jar; open
 ** http://localhost:8099/ and the Assistant app.
 *******************************************************************************/
public class AssistantDemoServer extends AbstractQQQApplication
{
   private static QInstance instance;



   /*******************************************************************************
    ** Every browser session is an admin with the all-access key.
    *******************************************************************************/
   public static class DemoSessionCustomizer implements QAuthenticationModuleCustomizerInterface
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void customizeSession(QInstance qInstance, QSession qSession, Map<String, Object> context)
      {
         QSession admin = AssistantTestFixture.sessionFor("admin-demo");
         qSession.setUser(admin.getUser());
         qSession.setPermissions(admin.getPermissions());
         qSession.withSecurityKeyValue(AssistantMetaDataBuilder.allAccessKeyName(AssistantQBitConfig.DEFAULT_OWNER_KEY_TYPE), true);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QInstance defineQInstance() throws QException
   {
      return (instance);
   }



   /*******************************************************************************
    ** Build, run real traffic against the recorded API, then serve.
    *******************************************************************************/
   public static void main(String[] args) throws Exception
   {
      int port = args.length > 0 ? Integer.parseInt(args[0]) : 8099;

      RecordedAnthropicServer api = new RecordedAnthropicServer();
      AssistantQBitConfig config = new AssistantQBitConfig()
         .withProviderName(ScriptedModelProvider.DEFAULT_NAME)
         .withModelProvider(new ScriptedModelProvider())
         .withProviderType(new AnthropicProviderType())
         .withAllowedBaseUrls(Set.of(api.baseUrl()))
         .withAllowedApiKeyEnvNames(Set.of("ANTHROPIC_API_KEY_STAGING"))
         .withSecretResolver(Map.of(AnthropicModelProvider.API_KEY_ENV, "sk-ant-demo-not-a-real-key-7f3a")::get)
         .withAllowedWidgetNames(Set.of());
      instance = AssistantTestFixture.build(config);

      QContext.init(instance, AssistantTestFixture.sessionFor("admin-1"));
      Integer production = insert(AssistantProvider.TABLE_NAME, Map.of("name", "Claude (production)", "providerType", "anthropic", "model", "claude-sonnet-5-5",
         "enabled", true, "isDefault", true, "baseUrl", api.baseUrl(), "monthlyCostCapUsd", new BigDecimal("25.00"),
         "maxOutputTokensPerRequest", 16000, "maxConcurrentRequests", 4));
      Integer staging = insert(AssistantProvider.TABLE_NAME, Map.of("name", "Claude (staging key)", "providerType", "anthropic", "model", "claude-haiku-5-5",
         "enabled", false, "isDefault", false, "apiKeyEnvName", "ANTHROPIC_API_KEY_STAGING"));
      for(Map.Entry<String, ModelPricing> price : ModelPricing.defaults().entrySet())
      {
         ModelPricing p = price.getValue();
         insert(AssistantModelPrice.TABLE_NAME, Map.of("providerType", "anthropic", "model", price.getKey(),
            "inputUsdPerMTok", BigDecimal.valueOf(p.getInputUsdPerMTok()), "outputUsdPerMTok", BigDecimal.valueOf(p.getOutputUsdPerMTok()),
            "cacheReadUsdPerMTok", BigDecimal.valueOf(p.getCacheReadUsdPerMTok()), "cacheWrite5mUsdPerMTok", BigDecimal.valueOf(p.getCacheWrite5mUsdPerMTok()),
            "cacheWrite1hUsdPerMTok", BigDecimal.valueOf(p.getCacheWrite1hUsdPerMTok()), "notes", "Anthropic list price, 2026-10"));
      }

      api.thenStream("text-with-thinking.sse", 0);
      ConnectionTest.run(config.getRuntime(), config.getRuntime().getStore().getProvider(production), "admin-1");
      ConnectionTest.run(config.getRuntime(), config.getRuntime().getStore().getProvider(staging), "admin-1");
      QContext.clear();

      TurnRunner runner = new TurnRunner(instance, config);
      api.thenStream("parallel-tools.sse", 0).thenStream("text-with-thinking.sse", 0);
      runner.run(AssistantTestFixture.EDITOR, "How many published posts are there, and what tables can I see?");
      api.thenStream("text-with-thinking.sse", 0);
      runner.run(AssistantTestFixture.EDITOR, "Summarize this week's page changes.");
      api.thenStream("parallel-tools.sse", 0).thenStream("text-with-thinking.sse", 0);
      runner.run(AssistantTestFixture.ANALYST, "What were the pageviews yesterday?");
      api.thenError(529, "overloaded.json").thenError(529, "overloaded.json").thenError(529, "overloaded.json");
      runner.run(AssistantTestFixture.OTHER_EDITOR, "Draft a post outline.");

      instance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("mock").withType(QAuthenticationType.MOCK)
         .withCustomizer(new QCodeReference(DemoSessionCustomizer.class)));
      instance.setHomeAppName(AssistantMetaDataBuilder.APP_NAME);

      new QApplicationJavalinServer(new AssistantDemoServer()).withPort(port).withServeFrontendNext(true).start();
      System.out.println("Assistant demo on http://localhost:" + port + "/");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Integer insert(String table, Map<String, Serializable> values) throws QException
   {
      QRecord record = new QRecord();
      record.setValues(new LinkedHashMap<>(values));
      QRecord inserted = new InsertAction().execute(new InsertInput(table).withRecord(record)).getRecords().get(0);
      if(!inserted.getErrors().isEmpty())
      {
         throw (new QException("Demo insert failed: " + inserted.getErrors()));
      }
      return (inserted.getValueInteger("id"));
   }

}
