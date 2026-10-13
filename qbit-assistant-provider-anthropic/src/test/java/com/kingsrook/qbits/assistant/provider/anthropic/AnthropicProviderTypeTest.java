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

package com.kingsrook.qbits.assistant.provider.anthropic;


import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.engine.ConnectionTest;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The Anthropic provider type with Test connection against a recorded HTTP
 ** server (no live calls): the key from the row's variable reaches the API as
 ** x-api-key, the result and the TEST usage row are saved, and a 401 becomes
 ** an admin-readable error without the key.
 *******************************************************************************/
class AnthropicProviderTypeTest
{
   private static final String KEY = "sk-ant-test-0000000000000000-abcd";

   private AssistantQBitConfig config;



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      if(config != null && config.getRuntime() != null)
      {
         config.getRuntime().close();
      }
      QContext.clear();
   }



   /*******************************************************************************
    ** An instance with the Anthropic type allowed to use the recorded server.
    *******************************************************************************/
   private QInstance instance(String baseUrl) throws Exception
   {
      config = new AssistantQBitConfig()
         .withProviderName(ScriptedModelProvider.DEFAULT_NAME)
         .withModelProvider(new ScriptedModelProvider())
         .withProviderType(new AnthropicProviderType())
         .withAllowedBaseUrls(Set.of(baseUrl))
         .withSecretResolver(Map.of(AnthropicModelProvider.API_KEY_ENV, KEY)::get);
      QInstance qInstance = AssistantTestFixture.build(config);
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      return (qInstance);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private AssistantProvider insertProvider(String baseUrl) throws Exception
   {
      QRecord record = new InsertAction().execute(new InsertInput(AssistantProvider.TABLE_NAME).withRecord(new QRecord()
         .withValue("name", "Claude").withValue("providerType", "anthropic").withValue("model", "claude-sonnet-5-5")
         .withValue("enabled", true).withValue("isDefault", true).withValue("baseUrl", baseUrl))).getRecords().get(0);
      assertThat(record.getErrors()).isEmpty();
      return (config.getRuntime().getStore().getProvider(record.getValueInteger("id")));
   }



   /*******************************************************************************
    ** A good connection.
    *******************************************************************************/
   @Test
   void testConnectionOk() throws Exception
   {
      try(RecordedAnthropicServer server = new RecordedAnthropicServer())
      {
         instance(server.baseUrl());
         server.thenStream("text-with-thinking.sse", 0);
         AssistantProvider row = insertProvider(server.baseUrl());
         assertThat(row.getApiKeyStatus()).isEqualTo("Set • …abcd (from ANTHROPIC_API_KEY)");

         ConnectionTest.Result result = ConnectionTest.run(config.getRuntime(), row, AssistantTestFixture.ADMIN);
         assertThat(result.ok()).as(result.error()).isTrue();
         assertThat(result.model()).isEqualTo("claude-sonnet-5-5");
         assertThat(server.getRequests()).hasSize(1);
         assertThat(server.getRequests().get(0).get("apiKey")).isEqualTo(KEY);
         assertThat(server.getRequests().get(0).get("body")).contains("\"max_tokens\":" + ConnectionTest.PING_MAX_TOKENS);

         AssistantProvider saved = config.getRuntime().getStore().getProvider(row.getId());
         assertThat(saved.getLastTestStatus()).isEqualTo("OK");
         assertThat(saved.getLastTestModel()).isEqualTo("claude-sonnet-5-5");

         QRecord usage = new QueryAction().execute(new QueryInput(AssistantUsage.TABLE_NAME).withFilter(new QQueryFilter())).getRecords().get(0);
         assertThat(usage.getValueString("purpose")).isEqualTo("TEST");
         assertThat(usage.getValueString("status")).isEqualTo("OK");
         assertThat(usage.getValueLong("inputTokens")).isPositive();
      }
   }



   /*******************************************************************************
    ** A refused key.
    *******************************************************************************/
   @Test
   void testConnectionUnauthorized() throws Exception
   {
      try(RecordedAnthropicServer server = new RecordedAnthropicServer())
      {
         instance(server.baseUrl());
         server.thenError(401, "unauthorized.json");
         AssistantProvider row = insertProvider(server.baseUrl());

         ConnectionTest.Result result = ConnectionTest.run(config.getRuntime(), row, AssistantTestFixture.ADMIN);
         assertThat(result.ok()).isFalse();
         assertThat(result.error()).contains("API key was refused").contains("HTTP 401").doesNotContain(KEY);

         AssistantProvider saved = config.getRuntime().getStore().getProvider(row.getId());
         assertThat(saved.getLastTestStatus()).isEqualTo("ERROR");
         assertThat(saved.getLastTestError()).doesNotContain(KEY);
      }
   }



   /*******************************************************************************
    ** The type itself.
    *******************************************************************************/
   @Test
   void testType()
   {
      AnthropicProviderType type = new AnthropicProviderType();
      assertThat(type.getType()).isEqualTo("anthropic");
      assertThat(type.getModels().get(0)).isEqualTo("claude-sonnet-5-5");
      assertThat(type.getDefaultApiKeyEnvName()).isEqualTo("ANTHROPIC_API_KEY");
      assertThat(type.getDefaultBaseUrl()).isEqualTo("https://api.anthropic.com");
      assertThat(type.getLabel()).contains("Anthropic");
      assertThat(type.create(KEY, null).toString()).doesNotContain(KEY);
   }

}
