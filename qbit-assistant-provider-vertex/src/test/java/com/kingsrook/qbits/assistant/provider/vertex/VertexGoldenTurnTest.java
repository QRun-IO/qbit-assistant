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

package com.kingsrook.qbits.assistant.provider.vertex;


import com.kingsrook.qbits.assistant.AssistantModelRouting;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.CloudProviderTestServer;
import com.kingsrook.qbits.assistant.testing.GoldenTasks;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.context.QContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.kingsrook.qbits.assistant.testing.CloudProviderTestServer.JSON;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Golden task through the cloud adapter, turn engine and permissioned UI tools.
 *******************************************************************************/
@Tag("golden")
class VertexGoldenTurnTest
{
   /*******************************************************************************
    ** A real count_records round trip returns the tool result to the model.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = { VertexContractTest.CLAUDE, VertexContractTest.GEMINI })
   void goldenToolRoundTrip(String model) throws Exception
   {
      try(CloudProviderTestServer server = new CloudProviderTestServer())
      {
         String answer = "There are " + AssistantTestFixture.PUBLISHED_POSTS + " published posts. /app/blog_post";
         boolean claude = model.startsWith("claude-");
         server.sse(claude ? CloudProviderTestServer.anthropicSse(model, "", CloudProviderTestServer.countInput()) : VertexContractTest.geminiSse("", CloudProviderTestServer.countInput()));
         server.sse(claude ? CloudProviderTestServer.anthropicSse(model, answer, null) : VertexContractTest.geminiSse(answer, null));
         var provider = new VertexModelProvider(VertexContractTest.settings(server), VertexContractTest.credentials());
         var config = new AssistantQBitConfig().withProviderName(provider.getName()).withModelProvider(provider)
            .withModels(new AssistantModelRouting().withMainModel(model));
         try
         {
            var instance = AssistantTestFixture.build(config);
            var task = GoldenTasks.all().stream().filter(t -> t.id().equals("count-published-posts")).findFirst().orElseThrow();
            var outcome = new TurnRunner(instance, config).run(task.userId(), task.prompt());
            assertThat(outcome.errorCode()).isNull();
            task.check().accept(outcome);
            assertThat(outcome.text()).endsWith(answer);
            assertThat(outcome.end()).isNotEmpty();
            assertThat(server.requests).hasSize(2);
            var messages = JSON.readTree(server.requests.get(1).body()).path("messages");
            var assistant = messages.get(messages.size() - 2);
            var result = messages.get(messages.size() - 1);
            assertThat(assistant.path("role").asText()).isEqualTo("assistant");
            if(claude)
            {
               assertThat(assistant.path("content").toString()).contains("count_records", "count");
               assertThat(result.path("role").asText()).isEqualTo("user");
               var toolResult = result.path("content").get(0);
               assertThat(toolResult.path("type").asText()).isEqualTo("tool_result");
               assertThat(toolResult.path("tool_use_id").asText()).isEqualTo("count");
               assertThat(toolResult.path("is_error").asBoolean()).isFalse();
               assertThat(toolResult.path("content").toString()).contains("count");
            }
            else
            {
               assertThat(assistant.path("tool_calls")).hasSize(1);
               assertThat(assistant.path("tool_calls").get(0).path("id").asText()).isEqualTo("count");
               assertThat(result.path("role").asText()).isEqualTo("tool");
               assertThat(result.path("tool_call_id").asText()).isEqualTo("count");
               assertThat(result.path("content").asText()).contains("count").doesNotStartWith("ERROR:");
            }
         }
         finally
         {
            if(config.getRuntime() != null)
            {
               config.getRuntime().close();
            }
            provider.close();
            QContext.clear();
         }
      }
   }
}
