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


import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.assistant.provider.CacheHint;
import com.kingsrook.qbits.assistant.provider.ModelEventListener;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelPart;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelResponse;
import com.kingsrook.qbits.assistant.provider.ModelToolDefinition;
import com.kingsrook.qbits.assistant.provider.StreamHandle;
import com.kingsrook.qbits.assistant.provider.SystemBlock;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The Anthropic adapter against recorded SSE streams served locally:
 ** request shape (caching, strict tools, tool_choice auto, adaptive thinking
 ** with omitted display, explicit effort), streaming text, thinking never
 ** forwarded, parallel tool_use, the thinking signature sent back verbatim,
 ** refusal, errors, cancel, and that the key never shows.
 *******************************************************************************/
class AnthropicModelProviderTest
{
   private static final String TEST_KEY = "sk-ant-test-0000000000000000000000";

   private RecordedAnthropicServer server;
   private AnthropicModelProvider  provider;



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      server = new RecordedAnthropicServer();
      provider = AnthropicModelProvider.builder().apiKey(TEST_KEY).baseUrl(server.baseUrl()).maxRetries(0).timeout(Duration.ofSeconds(20)).build();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      provider.close();
      server.close();
   }



   /*******************************************************************************
    ** A request like the turn engine builds.
    *******************************************************************************/
   static ModelRequest request()
   {
      Map<String, Object> schema = new LinkedHashMap<>();
      schema.put("type", "object");
      schema.put("additionalProperties", false);
      schema.put("required", List.of("table"));
      schema.put("properties", Map.of("table", Map.of("type", "string")));

      ModelRequest request = new ModelRequest()
         .withModel("claude-sonnet-5-5")
         .withEffort("medium")
         .withMaxTokens(16000)
         .withTools(new ArrayList<>(List.of(new ModelToolDefinition().withName("count_records").withDescription("Count").withInputSchema(schema).withStrict(true))));
      request.getSystem().add(new SystemBlock("static rules and app digest", CacheHint.LONG));
      request.getSystem().add(new SystemBlock("user digest", CacheHint.SHORT));
      request.getMessages().add(ModelMessage.user("how many posts?").withCacheHint(CacheHint.SHORT));
      return (request);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private Map<String, Object> sentBody(int index) throws Exception
   {
      return (JsonUtils.toObject(server.getRequests().get(index).get("body"), new TypeReference<Map<String, Object>>() {}));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testTextStreamRequestShapeAndNoThinking() throws Exception
   {
      server.thenStream("text-with-thinking.sse", 0);
      List<String> deltas = Collections.synchronizedList(new ArrayList<>());

      ModelResponse response = provider.stream(request(), deltas::add).await(Duration.ofSeconds(10));

      assertThat(String.join("", deltas)).isEqualTo("There are 4 published posts.");
      assertThat(response.getText()).isEqualTo("There are 4 published posts.");
      assertThat(response.getStopReason()).isEqualTo("end_turn");
      assertThat(response.getUsage().getInputTokens()).isEqualTo(120);
      assertThat(response.getUsage().getOutputTokens()).isEqualTo(42);
      assertThat(response.getUsage().getCacheWriteTokens()).isEqualTo(2048);
      assertThat(response.getUsage().getCacheWrite1hTokens()).isEqualTo(2000);
      assertThat(response.getRequestId()).isEqualTo("msg_text_01");

      ////////////////////////////////////////////////
      // thinking never reaches deltas or the parts //
      ////////////////////////////////////////////////
      assertThat(String.join("", deltas)).doesNotContain("SECRET_THINKING_TEXT");
      assertThat(JsonUtils.toJson(response.getParts())).doesNotContain("SECRET_THINKING_TEXT");

      Map<String, Object> body = sentBody(0);
      assertThat(body.get("model")).isEqualTo("claude-sonnet-5-5");
      assertThat(body.get("max_tokens")).isEqualTo(16000);
      assertThat((Map<String, Object>) body.get("thinking")).containsEntry("type", "adaptive").containsEntry("display", "omitted");
      assertThat((Map<String, Object>) body.get("output_config")).containsEntry("effort", "medium");
      assertThat((Map<String, Object>) body.get("tool_choice")).containsEntry("type", "auto").containsEntry("disable_parallel_tool_use", false);

      List<Map<String, Object>> system = (List<Map<String, Object>>) body.get("system");
      assertThat((Map<String, Object>) system.get(0).get("cache_control")).containsEntry("type", "ephemeral").containsEntry("ttl", "1h");
      assertThat((Map<String, Object>) system.get(1).get("cache_control")).containsEntry("ttl", "5m");

      Map<String, Object> tool = ((List<Map<String, Object>>) body.get("tools")).get(0);
      assertThat(tool.get("strict")).isEqualTo(true);
      assertThat((Map<String, Object>) tool.get("input_schema")).containsEntry("additionalProperties", false).containsEntry("type", "object");

      List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
      Map<String, Object>       lastBlock = ((List<Map<String, Object>>) messages.get(0).get("content")).get(0);
      assertThat((Map<String, Object>) lastBlock.get("cache_control")).containsEntry("ttl", "5m");

      assertThat(server.getRequests().get(0).get("apiKey")).isEqualTo(TEST_KEY);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testParallelToolsAndRoundTrip() throws Exception
   {
      server.thenStream("parallel-tools.sse", 0).thenStream("text-with-thinking.sse", 0);
      List<String> started = Collections.synchronizedList(new ArrayList<>());
      ModelEventListener listener = new ModelEventListener()
      {
         @Override
         public void onTextDelta(String text)
         {
         }



         @Override
         public void onToolUseStart(String toolUseId, String toolName)
         {
            started.add(toolName);
         }
      };

      ModelRequest  request  = request();
      ModelResponse response = provider.stream(request, listener).await(Duration.ofSeconds(10));
      assertThat(response.getStopReason()).isEqualTo("tool_use");
      assertThat(started).containsExactly("count_records", "list_tables");
      assertThat(response.getToolUses()).hasSize(2);
      assertThat(response.getToolUses().get(0).getToolUseId()).isEqualTo("toolu_A");
      assertThat(response.getToolUses().get(0).getInput()).containsEntry("table", "blog_post");
      assertThat(response.getToolUses().get(1).getInput()).isEmpty();
      assertThat(response.getUsage().getCacheReadTokens()).isEqualTo(5000);

      /////////////////////////////////////////////////////////////////////////
      // next step: assistant message verbatim + both results in ONE message //
      /////////////////////////////////////////////////////////////////////////
      request.getMessages().forEach(m -> m.setCacheHint(CacheHint.NONE));
      request.getMessages().add(new ModelMessage().withRole(ModelMessage.Role.ASSISTANT).withParts(response.getParts()).withProviderState(response.getProviderState()));
      request.getMessages().add(new ModelMessage().withRole(ModelMessage.Role.USER)
         .withPart(ModelPart.toolResult("toolu_A", "<qqq_data source=\"count_records\" trust=\"untrusted\">{\"count\":4}</qqq_data>", false))
         .withPart(ModelPart.toolResult("toolu_B", "denied", true))
         .withCacheHint(CacheHint.SHORT));
      provider.stream(request, t -> { }).await(Duration.ofSeconds(10));

      List<Map<String, Object>> messages = (List<Map<String, Object>>) sentBody(1).get("messages");
      assertThat(messages).hasSize(3);

      List<Map<String, Object>> assistantBlocks = (List<Map<String, Object>>) messages.get(1).get("content");
      assertThat(assistantBlocks.get(0)).containsEntry("type", "thinking").containsEntry("signature", "sig-tools-1");
      assertThat(assistantBlocks).extracting(b -> b.get("type")).containsExactly("thinking", "text", "tool_use", "tool_use");

      List<Map<String, Object>> results = (List<Map<String, Object>>) messages.get(2).get("content");
      assertThat(results).hasSize(2);
      assertThat(results.get(0)).containsEntry("type", "tool_result").containsEntry("tool_use_id", "toolu_A");
      assertThat(results.get(1)).containsEntry("is_error", true);
      assertThat((Map<String, Object>) results.get(1).get("cache_control")).containsEntry("ttl", "5m");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testRefusal() throws Exception
   {
      server.thenStream("refusal.sse", 0);
      ModelResponse response = provider.stream(request(), t -> { }).await(Duration.ofSeconds(10));
      assertThat(response.getStopReason()).isEqualTo(ModelResponse.STOP_REFUSAL);
      assertThat(response.getParts()).isEmpty();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testErrors() throws Exception
   {
      server.thenError(529, "overloaded.json").thenError(401, "unauthorized.json").thenStream("midstream-error.sse", 0);

      assertThatThrownBy(() -> provider.stream(request(), t -> { }).await(Duration.ofSeconds(10)))
         .isInstanceOfSatisfying(ModelProviderException.class, e ->
         {
            assertThat(e.getKind()).isEqualTo(ModelProviderException.Kind.OVERLOADED);
            assertThat(e.isRetryable()).isTrue();
            assertThat(e.getMessage()).doesNotContain(TEST_KEY);
         });

      assertThatThrownBy(() -> provider.stream(request(), t -> { }).await(Duration.ofSeconds(10)))
         .isInstanceOfSatisfying(ModelProviderException.class, e ->
         {
            assertThat(e.getKind()).isEqualTo(ModelProviderException.Kind.AUTHENTICATION);
            assertThat(e.getHttpStatus()).isEqualTo(401);
            assertThat(e.getMessage()).doesNotContain(TEST_KEY).contains("authentication_error");
            assertThat(e.getProviderErrorType()).isEqualTo("authentication_error");
            assertThat(e.getProviderMessage()).isEqualTo("invalid x-api-key");
            assertThat(e.isStrictSchemaRejected()).isFalse();
         });

      assertThatThrownBy(() -> provider.stream(request(), t -> { }).await(Duration.ofSeconds(10)))
         .isInstanceOfSatisfying(ModelProviderException.class, e -> assertThat(e.getPartialUsage().getInputTokens()).isEqualTo(50));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testCancel() throws Exception
   {
      server.thenStream("slow-start.sse", 10_000);
      List<String> deltas = Collections.synchronizedList(new ArrayList<>());
      StreamHandle handle = provider.stream(request(), deltas::add);

      long deadline = System.currentTimeMillis() + 5000;
      while(deltas.isEmpty() && System.currentTimeMillis() < deadline)
      {
         Thread.sleep(20);
      }
      assertThat(deltas).containsExactly("Partial");

      long start = System.currentTimeMillis();
      handle.cancel();
      ModelResponse response = handle.await(Duration.ofSeconds(5));
      assertThat(response.getStopReason()).isEqualTo(ModelResponse.STOP_CANCELLED);
      assertThat(System.currentTimeMillis() - start).isLessThan(3000);
      assertThat(response.getUsage().getInputTokens()).as("usage of the cancelled call is kept").isEqualTo(50);
      assertThat(handle.isCancelled()).isTrue();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testTimeoutCancels() throws Exception
   {
      server.thenStream("slow-start.sse", 10_000);
      StreamHandle handle = provider.stream(request(), t -> { });
      assertThatThrownBy(() -> handle.await(Duration.ofMillis(300)))
         .isInstanceOfSatisfying(ModelProviderException.class, e -> assertThat(e.getKind()).isEqualTo(ModelProviderException.Kind.TIMEOUT));
      assertThat(handle.isCancelled()).isTrue();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testKeyHandling()
   {
      assertThat(provider.toString()).doesNotContain(TEST_KEY);
      assertThat(provider.getName()).isEqualTo("anthropic");
      assertThat(provider.getCapabilities("claude-sonnet-5-5").getPromptCaching()).isTrue();

      AnthropicModelProvider.Builder builder = AnthropicModelProvider.builder().apiKey(TEST_KEY);
      assertThat(builder.toString()).doesNotContain(TEST_KEY).contains("[set]");

      assertThat(AnthropicModelProvider.Builder.resolveSecret("${env.QBIT_ASSISTANT_SURELY_UNSET_VAR}")).isNull();
      assertThat(AnthropicModelProvider.Builder.resolveSecret(" literal ")).isEqualTo("literal");
      assertThat(AnthropicModelProvider.Builder.resolveSecret("${env.PATH}")).isEqualTo(System.getenv("PATH"));

      assertThatThrownBy(() -> AnthropicModelProvider.builder().apiKey("${env.QBIT_ASSISTANT_SURELY_UNSET_VAR}").build())
         .isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("ANTHROPIC_API_KEY");
   }




   /*******************************************************************************
    ** An injected cloud-capable client wins over the key and HTTP builder options.
    *******************************************************************************/
   @Test
   void injectedClientTakesPrecedence() throws Exception
   {
      server.thenStream("text-with-thinking.sse", 0);
      var client = com.anthropic.client.okhttp.AnthropicOkHttpClient.builder().apiKey(TEST_KEY).baseUrl(server.baseUrl()).maxRetries(0).build();
      var injected = AnthropicModelProvider.builder().client(client).apiKey(null).baseUrl("https://unused.invalid").build();
      try
      {
         var response = injected.stream(request(), delta -> {}).await(Duration.ofSeconds(10));
         assertThat(response.getText()).isEqualTo("There are 4 published posts.");
         assertThat(server.getRequests()).hasSize(1);
         assertThat(server.getRequests().getFirst().get("apiKey")).isEqualTo(TEST_KEY);
      }
      finally
      {
         injected.close();
      }
   }
}
