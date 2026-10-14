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


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.kingsrook.qbits.assistant.provider.ModelCapabilities;
import com.kingsrook.qbits.assistant.provider.ModelEventListener;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelPart;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelResponse;
import com.kingsrook.qbits.assistant.provider.ModelToolDefinition;
import com.kingsrook.qbits.assistant.provider.ProviderSettings;
import com.kingsrook.qbits.assistant.provider.SystemBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Contract tests exercise the wire protocol through localhost HTTP only.
 *******************************************************************************/
class OpenAiContractTest
{
   /*******************************************************************************
    ** Classifiers opt out of streaming even when the model supports it.
    *******************************************************************************/
   @Test
   void nonStreamingClassifier() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         server.enqueue(200, "{\"model\":\"test\",\"choices\":[{\"message\":{\"content\":\"SIMPLE\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":18,\"completion_tokens\":1}}");
         var provider = new OpenAiCompatibleModelProvider(settings(server, "OPENAI"));
         ModelResponse response = provider.stream(request().withStreaming(false).withMaxTokens(4), new StringBuilder()::append).await(Duration.ofSeconds(3));
         assertThat(response.getText()).isEqualTo("SIMPLE");
         JsonNode body = OpenAiHttp.JSON.readTree(server.requests.getFirst().body());
         assertThat(body.path("stream").asBoolean()).isFalse();
         assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(4);
         assertThat(body.has("stream_options")).isFalse();
      }
   }



   static final String TEXT = "data: {\"model\":\"test\",\"choices\":[{\"delta\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\r\n\r\ndata: [DONE]\r\n\r\n";



   /*******************************************************************************
    ** A minimal request, also shared by the opt-in live test.
    *******************************************************************************/
   static ModelRequest request()
   {
      return (new ModelRequest().withModel("test").withMaxTokens(32).withEffort("max").withMessages(List.of(ModelMessage.user("Hello"))));
   }



   /*******************************************************************************
    ** Synthetic settings for the local server.
    *******************************************************************************/
   static ProviderSettings settings(MockOpenAiServer server, String kind)
   {
      return (new ProviderSettings().withKind(kind).withBaseUrl(server.url()).withApiKey("synthetic-key"));
   }



   /*******************************************************************************
    ** Strict-compatible schema with one required property.
    *******************************************************************************/
   static ModelToolDefinition tool()
   {
      return (new ModelToolDefinition().withName("lookup").withDescription("Look up a value").withStrict(true)
         .withInputSchema(Map.of("type", "object", "additionalProperties", false, "properties", Map.of("q", Map.of("type", "string")), "required", List.of("q"))));
   }



   /*******************************************************************************
    ** Kind-specific parameters, ordered tool results, auth and strict behavior.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = { "OPENAI", "AZURE_OPENAI", "OPENROUTER", "OLLAMA", "LM_STUDIO", "VLLM", "LLAMA_CPP", "OPENAI_COMPATIBLE" })
   void requestContract(String kind) throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         server.enqueue(200, TEXT);
         boolean nativeKind = kind.equals("OPENAI") || kind.equals("AZURE_OPENAI");
         ModelRequest request = request().withCapabilities(new ModelCapabilities().withReasoningEffort(true)).withTools(List.of(tool()))
            .withSystem(List.of(new SystemBlock().withText("one"), new SystemBlock().withText("two")))
            .withMessages(List.of(ModelMessage.user("hi"), new ModelMessage().withRole(ModelMessage.Role.ASSISTANT)
               .withPart(ModelPart.toolUse("a", "lookup", Map.of("q", "a"))).withPart(ModelPart.toolUse("b", "lookup", Map.of("q", "b"))),
               new ModelMessage().withRole(ModelMessage.Role.USER).withPart(ModelPart.toolResult("a", "first", false)).withPart(ModelPart.toolResult("b", "second", true))));
         var provider = new OpenAiCompatibleModelProvider(settings(server, kind));
         StringBuilder text = new StringBuilder();
         assertThat(provider.stream(request, text::append).await(Duration.ofSeconds(5)).getText()).isEqualTo("OK");
         assertThat(text.toString()).isEqualTo("OK");
         var captured = server.requests.getFirst();
         JsonNode body = OpenAiHttp.JSON.readTree(captured.body());
         assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("one\n\ntwo");
         assertThat(body.path("messages").get(2).path("tool_calls").get(0).path("function").path("arguments").asText()).isEqualTo("{\"q\":\"a\"}");
         assertThat(body.path("messages").get(3).path("tool_call_id").asText()).isEqualTo("a");
         assertThat(body.path("messages").get(4).path("content").asText()).isEqualTo("ERROR: second");
         assertThat(body.path("tools").get(0).path("function").has("strict")).isEqualTo(nativeKind);
         assertThat(body.has("max_completion_tokens")).isEqualTo(nativeKind);
         assertThat(body.has("max_tokens")).isEqualTo(!nativeKind);
         assertThat(body.path("reasoning_effort").asText()).isEqualTo("high");
         assertThat(body.has("tool_choice")).isFalse();
         assertThat(body.has("parallel_tool_calls")).isEqualTo(List.of("OPENAI", "AZURE_OPENAI", "OPENROUTER", "VLLM").contains(kind));
         assertThat(provider.countTokens(request)).isNull();
         if(kind.equals("AZURE_OPENAI"))
         {
            assertThat(captured.path()).isEqualTo("/v1/openai/deployments/test/chat/completions?api-version=2024-10-21");
            assertThat(captured.apiKey()).isEqualTo("synthetic-key");
            assertThat(captured.authorization()).isNull();
         }
         else
         {
            assertThat(captured.authorization()).isEqualTo("Bearer synthetic-key");
         }
      }
   }



   /*******************************************************************************
    ** Fragmented parallel tools, comments, cache usage and ignored reasoning.
    *******************************************************************************/
   @Test
   void streamedToolsAndUsage() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         StringBuilder script = new StringBuilder(": comment\r\n\r\n");
         script.append(event(Map.of("choices", List.of(Map.of("delta", Map.of("reasoning_content", "never expose", "tool_calls", List.of(
            Map.of("index", 1, "id", "b", "function", Map.of("name", "lookup", "arguments", "")),
            Map.of("index", 0, "id", "a", "function", Map.of("name", "lookup", "arguments", "")))))))));
         for(char c : "{\"q\":\"value\"}".toCharArray())
         {
            script.append(event(Map.of("choices", List.of(Map.of("delta", Map.of("tool_calls", List.of(
               Map.of("index", 0, "function", Map.of("arguments", String.valueOf(c))), Map.of("index", 1, "function", Map.of("arguments", String.valueOf(c))))))))));
         }
         script.append(event(Map.of("choices", List.of(Map.of("delta", Map.of(), "finish_reason", "tool_calls")), "usage",
            Map.of("prompt_tokens", 100, "completion_tokens", 8, "prompt_tokens_details", Map.of("cached_tokens", 70)))));
         server.enqueue(200, script + "data: [DONE]\n\n");
         List<String> starts = new ArrayList<>();
         StringBuilder text = new StringBuilder();
         ModelResponse response = new OpenAiCompatibleModelProvider(settings(server, "OLLAMA").withApiKey(null)).stream(request(), new ModelEventListener()
         {



            /*******************************************************************************
             ** Provider contract.
             *******************************************************************************/
            @Override
            public void onTextDelta(String delta)
            {
               text.append(delta);
            }



            /*******************************************************************************
             ** Provider contract.
             *******************************************************************************/
            @Override
            public void onToolUseStart(String id, String name)
            {
               starts.add(id);
            }
         }).await(Duration.ofSeconds(5));
         assertThat(response.getToolUses()).extracting(ModelPart::getToolUseId).containsExactly("a", "b");
         assertThat(response.getToolUses().getFirst().getInput()).containsEntry("q", "value");
         assertThat(starts).containsExactly("b", "a");
         assertThat(text).isEmpty();
         assertThat(response.getUsage().getInputTokens()).isEqualTo(30L);
         assertThat(response.getUsage().getCacheReadTokens()).isEqualTo(70L);
         assertThat(response.getUsage().getOutputTokens()).isEqualTo(8L);
         assertThat(response.getStopReason()).isEqualTo(ModelResponse.STOP_TOOL_USE);
         assertThat(server.requests.getFirst().authorization()).isNull();
      }
   }



   /*******************************************************************************
    ** Single JSON mode and defaults without optional request fields.
    *******************************************************************************/
   @Test
   void nonStreamingAndNoUsage() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         server.enqueue(200, "{\"choices\":[{\"message\":{\"content\":\"done\"},\"finish_reason\":\"length\"}]}");
         var provider = new OpenAiCompatibleModelProvider(settings(server, "OPENAI"));
         StringBuilder text = new StringBuilder();
         ModelResponse response = provider.stream(request().withCapabilities(new ModelCapabilities().withStreaming(false)), text::append).await(Duration.ofSeconds(5));
         assertThat(response.getStopReason()).isEqualTo(ModelResponse.STOP_MAX_TOKENS);
         assertThat(text.toString()).isEqualTo("done");
         assertThat(response.getUsage().billableTokens()).isZero();
         JsonNode body = OpenAiHttp.JSON.readTree(server.requests.getFirst().body());
         assertThat(body.path("stream").asBoolean()).isFalse();
         assertThat(body.has("stream_options")).isFalse();
         assertThat(body.has("reasoning_effort")).isFalse();
         assertThat(body.has("parallel_tool_calls")).isFalse();
         server.enqueue(200, TEXT);
         assertThat(provider.stream(request(), text::append).await(Duration.ofSeconds(5)).getUsage().billableTokens()).isZero();
      }
   }



   /*******************************************************************************
    ** Every HTTP error category carries safe details.
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource({ "401,AUTHENTICATION", "403,AUTHENTICATION", "429,RATE_LIMITED", "503,OVERLOADED", "529,OVERLOADED", "400,INVALID_REQUEST", "404,INVALID_REQUEST", "422,INVALID_REQUEST", "500,SERVER", "502,SERVER" })
   void errors(int status, String kind) throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         server.enqueue(status, "{\"error\":{\"type\":\"bad_request\",\"message\":\"Rejected synthetic-key\"}}");
         assertThatThrownBy(() -> new OpenAiCompatibleModelProvider(settings(server, "OPENAI")).stream(request(), t -> {}).await(Duration.ofSeconds(5)))
            .isInstanceOfSatisfying(ModelProviderException.class, e ->
            {
               assertThat(e.getKind().name()).isEqualTo(kind);
               assertThat(e.getHttpStatus()).isEqualTo(status);
               assertThat(e.getRequestId()).isEqualTo("mock-request");
               assertThat(e.getMessage()).doesNotContain("synthetic-key");
               assertThat(e.getProviderMessage()).contains("[redacted]");
            });
      }
   }



   /*******************************************************************************
    ** Strict rejection is opt-in; incompatible nested object schemas are omitted.
    *******************************************************************************/
   @Test
   void strictAndStreamErrors() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         var provider = new OpenAiCompatibleModelProvider(settings(server, "OPENAI"));
         String error = "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"strict schema grammar refused\"}}";
         for(boolean sse : List.of(false, true))
         {
            server.enqueue(sse ? 200 : 400, sse ? "data: " + error + "\n\n" : error);
            assertThatThrownBy(() -> provider.stream(request().withTools(List.of(tool())), t -> {}).await(Duration.ofSeconds(5)))
               .isInstanceOfSatisfying(ModelProviderException.class, e -> assertThat(e.isStrictSchemaRejected()).isTrue());
         }
         server.enqueue(200, TEXT);
         provider.stream(request().withTools(List.of(tool().withInputSchema(Map.of("type", "object", "properties", Map.of())))), t -> {}).await(Duration.ofSeconds(5));
         assertThat(OpenAiHttp.JSON.readTree(server.requests.getLast().body()).path("tools").get(0).path("function").has("strict")).isFalse();
         assertThat(OpenAiMapping.strictCompatible(OpenAiHttp.JSON.readTree("{\"type\":\"array\",\"items\":{\"type\":\"object\"}}"))).isFalse();
         assertThat(OpenAiMapping.strictCompatible(OpenAiHttp.JSON.readTree("{\"anyOf\":[{\"type\":\"object\"}]}"))).isFalse();
      }
   }



   /*******************************************************************************
    ** Header timeout, stalled body timeout, cancellation and network failure.
    *******************************************************************************/
   @Test
   void timeoutCancelAndNetwork() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         var provider = new OpenAiCompatibleModelProvider(settings(server, "OLLAMA").withRequestTimeoutSeconds(1));
         for(boolean afterHeaders : List.of(false, true))
         {
            server.stall(afterHeaders);
            assertThatThrownBy(() -> provider.stream(request(), t -> {}).await(Duration.ofSeconds(5)))
               .isInstanceOfSatisfying(ModelProviderException.class, e -> assertThat(e.getKind()).isEqualTo(ModelProviderException.Kind.TIMEOUT));
         }
         var started = server.stall(true);
         var handle = provider.stream(request(), t -> {});
         assertThat(started.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
         handle.cancel();
         assertThat(handle.await(Duration.ofSeconds(2)).getStopReason()).isEqualTo(ModelResponse.STOP_CANCELLED);
         assertThat(handle.isCancelled()).isTrue();
      }
      String closed;
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         closed = server.url();
      }
      var provider = new OpenAiCompatibleModelProvider(new ProviderSettings().withKind("OLLAMA").withBaseUrl(closed));
      assertThatThrownBy(() -> provider.stream(request(), t -> {}).await(Duration.ofSeconds(5)))
         .isInstanceOfSatisfying(ModelProviderException.class, e -> assertThat(e.getKind()).isEqualTo(ModelProviderException.Kind.NETWORK));
   }



   /*******************************************************************************
    ** Discovery timeouts include the body, and failed probes never complete.
    *******************************************************************************/
   @Test
   void discoveryTimeoutAndMalformedResponse() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         var type = new OpenAiCompatibleProviderType();
         var settings = settings(server, "OLLAMA").withRequestTimeoutSeconds(1);
         server.stall(true);
         assertThatThrownBy(() -> type.discoverModels(settings)).isInstanceOfSatisfying(ModelProviderException.class,
            e -> assertThat(e.getKind()).isEqualTo(ModelProviderException.Kind.TIMEOUT));
         server.enqueue(200, "not JSON");
         assertThat(type.checkHealth(settings).healthy()).isFalse();
         server.enqueue(200, "{\"error\":{\"message\":\"failed\"}}");
         assertThat(type.checkHealth(settings).healthy()).isFalse();
      }
   }



   /*******************************************************************************
    ** JDK header validation must never expose even an invalid credential.
    *******************************************************************************/
   @Test
   void invalidHeaderDoesNotLeakKey() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         var settings = settings(server, "OPENAI").withApiKey("private\ncredential");
         assertThatThrownBy(() -> new OpenAiCompatibleProviderType().discoverModels(settings))
            .isInstanceOfSatisfying(ModelProviderException.class, e ->
            {
               assertThat(e.getMessage()).doesNotContain("private", "credential");
               assertThat(e.getCause()).isNull();
            });
         assertThat(new OpenAiCompatibleProviderType().checkHealth(settings).healthy()).isFalse();
         assertThat(server.requests).isEmpty();
      }
   }



   /*******************************************************************************
    ** Non-streaming calls preserve tools and usage, including cached tokens.
    *******************************************************************************/
   @Test
   void nonStreamingTools() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         server.enqueue(200, """
            {"choices":[{"message":{"tool_calls":[
               {"id":"one","function":{"name":"lookup","arguments":"{\\"q\\":\\"x\\"}"}},
               {"id":"two","function":{"name":"lookup","arguments":"{}"}}]},"finish_reason":"tool_calls"}],
               "usage":{"prompt_tokens":12,"completion_tokens":4,"prompt_tokens_details":{"cached_tokens":2}}}
            """);
         var response = new OpenAiCompatibleModelProvider(settings(server, "OPENAI")).stream(
            request().withCapabilities(new ModelCapabilities().withStreaming(false)), t -> {}).await(Duration.ofSeconds(5));
         assertThat(response.getToolUses()).extracting(ModelPart::getToolUseId).containsExactly("one", "two");
         assertThat(response.getToolUses().getFirst().getInput()).containsEntry("q", "x");
         assertThat(response.getUsage().getInputTokens()).isEqualTo(10L);
      }
   }



   /*******************************************************************************
    ** Model discovery context dialects, Azure path and health without completions.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = { "OPENAI", "OPENROUTER", "AZURE_OPENAI", "VLLM", "LLAMA_CPP" })
   void discoveryAndHealth(String kind) throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         String models = "{\"data\":[{\"id\":\"a\",\"owned_by\":\"owner\",\"context_length\":64000},{\"id\":\"b\",\"max_model_len\":32000},{\"id\":\"c\",\"meta\":{\"n_ctx_train\":16000}}]}";
         server.enqueue(200, models);
         var type = new OpenAiCompatibleProviderType();
         var settings = settings(server, kind).withApiVersion("2024-10-21");
         assertThat(type.discoverModels(settings)).extracting(m -> m.contextWindowTokens()).containsExactly(64000, 32000, 16000);
         server.enqueue(200, models);
         assertThat(type.checkHealth(settings).message()).isEqualTo("3 models available");
         server.enqueue(401, "{\"error\":{\"message\":\"bad key\"}}");
         assertThat(type.checkHealth(settings).healthy()).isFalse();
         assertThat(server.requests).allSatisfy(r -> assertThat(r.path()).isEqualTo(kind.equals("AZURE_OPENAI") ? "/v1/openai/models?api-version=2024-10-21" : "/v1/models"));
         assertThat(type.getKinds()).hasSize(8);
         assertThat(type.getModels()).isEmpty();
         assertThat(type.getDefaultBaseUrl()).isNull();
         assertThat(type.getDefaultApiKeyEnvName()).isNull();
         assertThat(type.getLabel()).isEqualTo("OpenAI-compatible");
         for(String k : type.getKinds())
         {
            assertThat(type.getDefaultLocation(k)).isIn("LOCAL", "CLOUD");
            type.getDefaultBaseUrl(k);
            type.getDefaultApiKeyEnvName(k);
         }
         assertThat(type.create(null, server.url()).getName()).isEqualTo(type.getType());
      }
   }



   /*******************************************************************************
    ** Serialize a scripted SSE event.
    *******************************************************************************/
   static String event(Object value) throws Exception
   {
      return ("data: " + OpenAiHttp.JSON.writeValueAsString(value) + "\n\n");
   }

   /*******************************************************************************
    ** Repeated identities, missing ids and older servers' stop reason are tolerated.
    *******************************************************************************/
   @Test
   void repeatedToolIdentityAndMissingId() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         StringBuilder script = new StringBuilder();
         for(String fragment : List.of("{", "\"q\":", "\"hello\"}"))
         {
            script.append(event(Map.of("choices", List.of(Map.of("delta", Map.of("tool_calls", List.of(
               Map.of("index", 0, "id", "repeated", "function", Map.of("name", "lookup", "arguments", fragment)),
               Map.of("index", 1, "function", Map.of("name", "lookup", "arguments", fragment)))), "finish_reason", "stop")))));
         }
         server.enqueue(200, script.toString());
         ModelResponse response = new OpenAiCompatibleModelProvider(settings(server, "OLLAMA")).stream(request(), t -> {}).await(Duration.ofSeconds(5));
         assertThat(response.getStopReason()).isEqualTo(ModelResponse.STOP_TOOL_USE);
         assertThat(response.getToolUses()).extracting(ModelPart::getToolUseId).containsExactly("repeated", "call_1");
         assertThat(response.getToolUses()).allSatisfy(tool ->
         {
            assertThat(tool.getToolName()).isEqualTo("lookup");
            assertThat(tool.getInput()).containsExactlyEntriesOf(Map.of("q", "hello"));
         });
      }
   }



   /*******************************************************************************
    ** Invalid JSON becomes bounded input for kernel validation, never NETWORK.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = { "{broken", "null", "[]", "{} trailing", "long" })
   void malformedArguments(String input) throws Exception
   {
      String arguments = input.equals("long") ? "x".repeat(250) : input;
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         server.enqueue(200, event(Map.of("choices", List.of(Map.of("delta", Map.of("tool_calls", List.of(
            Map.of("index", 0, "function", Map.of("name", "lookup", "arguments", arguments)))), "finish_reason", "stop")))));
         ModelResponse response = new OpenAiCompatibleModelProvider(settings(server, "LLAMA_CPP")).stream(request(), t -> {}).await(Duration.ofSeconds(5));
         assertThat(response.getStopReason()).isEqualTo(ModelResponse.STOP_TOOL_USE);
         assertThat(response.getToolUses().getFirst().getInput()).containsExactlyEntriesOf(Map.of("_invalid_arguments", arguments.substring(0, Math.min(200, arguments.length()))));
      }
   }



   /*******************************************************************************
    ** Supplier tokens take precedence over static keys and refresh per request.
    *******************************************************************************/
   @Test
   void bearerSupplierPrecedenceAndRedaction() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         var token = new java.util.concurrent.atomic.AtomicReference<>("first-private-token");
         ProviderSettings settings = settings(server, "VERTEX").withBearerTokenSupplier(token::get);
         var provider = new OpenAiCompatibleModelProvider(settings);
         server.enqueue(200, TEXT);
         provider.stream(request(), delta -> {}).await(Duration.ofSeconds(5));
         assertThat(server.requests.getFirst().authorization()).isEqualTo("Bearer first-private-token");
         token.set("next-private-token");
         server.enqueue(403, "{\"error\":{\"type\":\"next-private-token\",\"message\":\"next-private-token\"}}");
         assertThatThrownBy(() -> provider.stream(request(), delta -> {}).await(Duration.ofSeconds(5)))
            .isInstanceOfSatisfying(ModelProviderException.class, error ->
            {
               assertThat(error.getKind()).isEqualTo(ModelProviderException.Kind.AUTHENTICATION);
               assertThat(error.getProviderMessage()).isNull();
               assertThat(error.getProviderErrorType()).isNull();
               assertThat(error.toString()).doesNotContain("private-token");
            });
         assertThat(server.requests.get(1).authorization()).isEqualTo("Bearer next-private-token");
         assertThat(settings.toString()).doesNotContain("private-token");
         assertThat(OpenAiHttp.JSON.writeValueAsString(settings)).doesNotContain("bearerTokenSupplier", "private-token");
      }
   }



   /*******************************************************************************
    ** Missing or failed dynamic credentials never fall back to a static secret.
    *******************************************************************************/
   @Test
   void unavailableBearerSupplier() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         ProviderSettings settings = settings(server, "VERTEX");
         for(String value : java.util.Arrays.asList(null, "", " "))
         {
            settings.setBearerTokenSupplier(() -> value);
            var provider = new OpenAiCompatibleModelProvider(settings);
            assertThatThrownBy(() -> provider.stream(request(), delta -> {}).await(Duration.ofSeconds(5)))
               .isInstanceOfSatisfying(ModelProviderException.class, error -> assertThat(error.getKind()).isEqualTo(ModelProviderException.Kind.AUTHENTICATION));
         }
         settings.withBearerTokenSupplier(() ->
         {
            throw (new IllegalStateException("private-token"));
         });
         var provider = new OpenAiCompatibleModelProvider(settings);
         assertThatThrownBy(() -> provider.stream(request(), delta -> {}).await(Duration.ofSeconds(5)))
            .isInstanceOfSatisfying(ModelProviderException.class, error ->
            {
               assertThat(error.getKind()).isEqualTo(ModelProviderException.Kind.AUTHENTICATION);
               assertThat(error.toString()).doesNotContain("private-token");
               assertThat(error.getCause()).isNull();
            });
         assertThat(server.requests).isEmpty();
      }
   }
}
