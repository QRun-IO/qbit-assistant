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

package com.kingsrook.qbits.assistant.provider.openai;


import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.GoldenTasks;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.context.QContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Golden fixture task through the real adapter, engine and tool result mapping.
 *******************************************************************************/
@Tag("golden")
class OpenAiGoldenTurnTest
{
   /*******************************************************************************
    ** A golden task travels through the engine, real UI tools and a tool round trip.
    *******************************************************************************/
   @Test
   void goldenToolRoundTrip() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         Map<String, Object> arguments = Map.of("table", "blog_post", "filter", Map.of("booleanOperator", "AND", "criteria",
            List.of(Map.of("field", "status", "operator", "EQUALS", "values", List.of("PUBLISHED")))));
         Map<String, Object> call = Map.of("index", 0, "id", "count", "function", Map.of("name", "count_records", "arguments", OpenAiHttp.JSON.writeValueAsString(arguments)));
         server.enqueue(200, OpenAiContractTest.event(Map.of("choices", List.of(Map.of("delta", Map.of("tool_calls", List.of(call)), "finish_reason", "stop")))));
         server.enqueue(200, OpenAiContractTest.event(Map.of("choices", List.of(Map.of("delta", Map.of("content", "There are " + AssistantTestFixture.PUBLISHED_POSTS + " published posts. /app/blog_post"), "finish_reason", "stop")))));
         var provider = new OpenAiCompatibleModelProvider(OpenAiContractTest.settings(server, "OLLAMA"));
         var config = new AssistantQBitConfig().withProviderName(provider.getName()).withModelProvider(provider);
         try
         {
            var instance = AssistantTestFixture.build(config);
            var task = GoldenTasks.all().stream().filter(t -> t.id().equals("count-published-posts")).findFirst().orElseThrow();
            var outcome = new TurnRunner(instance, config).run(task.userId(), task.prompt());
            assertThat(outcome.errorCode()).isNull();
            task.check().accept(outcome);
            assertThat(outcome.text()).isEqualTo("There are " + AssistantTestFixture.PUBLISHED_POSTS + " published posts. /app/blog_post");
            assertThat(outcome.end()).isNotEmpty();
            assertThat(server.requests).hasSize(2);
            JsonNode messages = OpenAiHttp.JSON.readTree(server.requests.get(1).body()).path("messages");
            JsonNode assistant = messages.get(messages.size() - 2);
            assertThat(assistant.path("role").asText()).isEqualTo("assistant");
            assertThat(assistant.path("tool_calls")).hasSize(1);
            assertThat(assistant.path("tool_calls").get(0).path("id").asText()).isEqualTo("count");
            assertThat(assistant.path("tool_calls").get(0).path("function").path("name").asText()).isEqualTo("count_records");
            assertThat(messages.get(messages.size() - 1).path("content").asText()).contains("count").doesNotStartWith("ERROR:");
            assertThat(messages.get(messages.size() - 1).path("role").asText()).isEqualTo("tool");
            assertThat(messages.get(messages.size() - 1).path("tool_call_id").asText()).isEqualTo("count");
         }
         finally
         {
            if(config.getRuntime() != null)
            {
               config.getRuntime().close();
            }
            QContext.clear();
         }
      }
   }



}
