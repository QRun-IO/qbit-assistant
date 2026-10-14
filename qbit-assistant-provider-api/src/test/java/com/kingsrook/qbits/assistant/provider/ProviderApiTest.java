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

package com.kingsrook.qbits.assistant.provider;


import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.assistant.engine.ModelPricing;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Provider contracts work on a classpath with no assistant runtime or QQQ.
 *******************************************************************************/
class ProviderApiTest
{
   private final ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);



   /*******************************************************************************
    ** Preserve provider-neutral requests and tool order through JSON; signed
    ** provider state remains in memory only.
    *******************************************************************************/
   @Test
   void requestRoundTripAndCopy() throws Exception
   {
      ModelCapabilities capabilities = new ModelCapabilities().withTools(true).withParallelToolCalls(true)
         .withPromptCaching(true).withThinking(false).withMaxContextTokens(32000)
         .withVision(false).withStreaming(true).withReasoningEffort(true);
      ModelToolDefinition tool = new ModelToolDefinition().withName("lookup").withDescription("Look up a record")
         .withInputSchema(Map.of("type", "object")).withStrict(true);
      ModelMessage user = ModelMessage.user("Find it").withCacheHint(CacheHint.SHORT);
      ModelMessage assistant = ModelMessage.assistant("Looking").withPart(ModelPart.toolUse("call-1", "lookup", null))
         .withProviderState("private-signature");
      ModelMessage result = new ModelMessage().withRole(ModelMessage.Role.USER)
         .withParts(List.of(ModelPart.toolResult("call-1", "denied", true)));
      ModelRequest request = new ModelRequest().withModel("example").withEffort("low").withMaxTokens(100)
         .withParallelToolCalls(true).withStreaming(false).withCapabilities(capabilities)
         .withSystem(List.of(new SystemBlock("rules", CacheHint.LONG), new SystemBlock().withText("user context").withCacheHint(CacheHint.SHORT)))
         .withTools(List.of(tool)).withMessages(new ArrayList<>(List.of(user, assistant, result)));
      String json = mapper.writeValueAsString(request);
      assertThat(json).doesNotContain("private-signature", "providerState");
      ModelRequest restored = mapper.readValue(json, ModelRequest.class);
      assertThat(mapper.<JsonNode>valueToTree(restored)).isEqualTo(mapper.valueToTree(request));
      assertThat(restored.getMessages().get(1).getProviderState()).isNull();
      assertThat(restored.getMessages().get(2).getParts().get(0).getIsError()).isTrue();
      ModelRequest copy = request.copy();
      assertThat(mapper.<JsonNode>valueToTree(copy)).isEqualTo(mapper.valueToTree(request));
      copy.getMessages().clear();
      assertThat(request.getMessages()).hasSize(3);
   }



   /*******************************************************************************
    ** Response helpers retain tool ordering, text, usage and safe metadata.
    *******************************************************************************/
   @Test
   void responseAndPricingRoundTrip() throws Exception
   {
      ModelUsage usage = new ModelUsage().withInputTokens(2L).withOutputTokens(3L)
         .withCacheReadTokens(4L).withCacheWriteTokens(5L).withCacheWrite1hTokens(1L);
      ModelPart first = ModelPart.toolUse("first", "lookup", Map.of("id", 1));
      ModelPart second = ModelPart.toolUse("second", "lookup", Map.of("id", 2));
      ModelResponse response = new ModelResponse().withParts(List.of(ModelPart.text("a"), first, ModelPart.text("b"), second))
         .withModel("example").withStopReason(ModelResponse.STOP_TOOL_USE).withRequestId("request-1")
         .withUsage(usage).withProviderState("signature");
      assertThat(response.getText()).isEqualTo("ab");
      assertThat(response.getToolUses()).containsExactly(first, second);
      String json = mapper.writeValueAsString(response);
      assertThat(json).doesNotContain("signature", "providerState");
      ModelResponse restored = mapper.readValue(json, ModelResponse.class);
      assertThat(mapper.<JsonNode>valueToTree(restored)).isEqualTo(mapper.valueToTree(response));
      assertThat(restored.getProviderState()).isNull();
      assertThat(response.getProviderState()).isEqualTo("signature");
      ModelPricing pricing = ModelPricing.of(0.1, 0.5, 0.1);
      ModelPricing restoredPricing = mapper.readValue(mapper.writeValueAsString(pricing), ModelPricing.class);
      assertThat(restoredPricing.costMicros(usage)).isEqualTo(3);
      assertThat(new ModelUsage().withInputTokens(null).add(usage).add(null).billableTokens()).isEqualTo(10);
      TokenCount tokens = mapper.readValue(mapper.writeValueAsString(new TokenCount().withInputTokens(12L)), TokenCount.class);
      assertThat(tokens.getInputTokens()).isEqualTo(12L);
   }



   /*******************************************************************************
    ** Plain validation settings require no credentials or entity classes, and
    ** live token suppliers/TLS contexts cannot be serialized into diagnostics.
    *******************************************************************************/
   @Test
   void settingsRoundTrip() throws Exception
   {
      ProviderSettings settings = new ProviderSettings().withKind("EXAMPLE").withApiKeyEnvName("EXAMPLE_KEY")
         .withApiKey("test-only-key").withBaseUrl("https://example.invalid").withApiVersion("v1")
         .withRegion("region").withCloudProject("project").withTlsCaBundle("corporate")
         .withConnectTimeoutSeconds(5).withRequestTimeoutSeconds(30)
         .withBearerTokenSupplier(() -> "ephemeral-token").withSslContext(SSLContext.getDefault());
      String json = mapper.writeValueAsString(settings);
      assertThat(json).doesNotContain("ephemeral-token", "bearerTokenSupplier", "sslContext");
      ProviderSettings restored = mapper.readValue(json, ProviderSettings.class);
      assertThat(mapper.<JsonNode>valueToTree(restored)).isEqualTo(mapper.valueToTree(settings));
      assertThat(settings.getBearerTokenSupplier().get()).isEqualTo("ephemeral-token");
      assertThat(settings.getSslContext()).isNotNull();
      assertThat(restored.getBearerTokenSupplier()).isNull();
      assertThat(restored.getSslContext()).isNull();
   }



   /*******************************************************************************
    ** Embedding batches snapshot input and enforce the transport size contract.
    *******************************************************************************/
   @Test
   void embeddingContracts()
   {
      List<String> input = new ArrayList<>(List.of("first", "second"));
      EmbeddingRequest request = new EmbeddingRequest("embed", input);
      input.clear();
      assertThat(request.input()).containsExactly("first", "second");
      assertThatThrownBy(() -> request.input().add("third")).isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> new EmbeddingRequest(" ", List.of("text"))).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new EmbeddingRequest(null, List.of("text"))).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new EmbeddingRequest("embed", List.of())).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new EmbeddingRequest("embed", Collections.nCopies(65, "text"))).isInstanceOf(IllegalArgumentException.class);
      assertThat(new EmbeddingRequest("embed", Collections.nCopies(64, "text")).input()).hasSize(64);
      float[] vector = new float[] { 0.25F, 0.75F };
      EmbeddingResponse response = new EmbeddingResponse(List.of(vector), new ModelUsage().withInputTokens(2L), "request");
      assertThat(response.vectors().getFirst()).containsExactly(0.25F, 0.75F);
      assertThat(response.usage().getInputTokens()).isEqualTo(2L);
      assertThat(response.requestId()).isEqualTo("request");
   }



   /*******************************************************************************
    ** Cancellation is idempotent, preserves accrued usage, and wins over late
    ** completion. Timeout also cancels the underlying transport.
    *******************************************************************************/
   @Test
   void cancellationAndTimeout() throws Exception
   {
      AtomicInteger cancellations = new AtomicInteger();
      ModelUsage usage = new ModelUsage().withInputTokens(10L);
      CompletableStreamHandle handle = new CompletableStreamHandle();
      handle.setPartialUsage(() -> usage);
      handle.setOnCancel(cancellations::incrementAndGet);
      handle.cancel();
      handle.cancel();
      handle.complete(new ModelResponse().withStopReason(ModelResponse.STOP_END_TURN));
      handle.fail(new ModelProviderException(ModelProviderException.Kind.SERVER, 500, "late failure"));
      assertThat(handle.isCancelled()).isTrue();
      assertThat(cancellations.get()).isEqualTo(1);
      assertThat(handle.await(Duration.ofSeconds(1)).getUsage()).isSameAs(usage);
      CompletableStreamHandle timeout = new CompletableStreamHandle();
      timeout.setPartialUsage(() -> usage);
      assertThatThrownBy(() -> timeout.await(Duration.ofMillis(1))).isInstanceOfSatisfying(ModelProviderException.class, error ->
      {
         assertThat(error.getKind()).isEqualTo(ModelProviderException.Kind.TIMEOUT);
         assertThat(error.getPartialUsage()).isSameAs(usage);
      });
      assertThat(timeout.isCancelled()).isTrue();
      handle.setPartialUsage(() ->
      {
         throw (new IllegalStateException("closed"));
      });
      assertThat(handle.partialUsage()).isNull();
   }



   /*******************************************************************************
    ** Error metadata is bounded and redacted independently of error classification.
    *******************************************************************************/
   @Test
   void errorDetails()
   {
      ModelProviderException error = new ModelProviderException(ModelProviderException.Kind.INVALID_REQUEST, 400, "Invalid schema")
         .withStrictSchemaRejected(true).withProviderDetails("invalid_request", "bad key sk-abcdefghijk\ntry again", "req-123");
      assertThat(error.isStrictSchemaRejected()).isTrue();
      assertThat(error.getProviderErrorType()).isEqualTo("invalid_request");
      assertThat(error.getProviderMessage()).isEqualTo("bad key [redacted] try again");
      assertThat(error.getRequestId()).isEqualTo("req-123");
      assertThat(error.isRetryable()).isFalse();
      assertThat(ModelProviderException.sanitizeProviderMessage(" ", 10)).isNull();
      assertThat(ModelProviderException.sanitizeProviderMessage(null, 10)).isNull();
      assertThat(ModelProviderException.sanitizeProviderMessage("word ".repeat(100), 20)).hasSize(20);
      assertThat(ModelProviderException.sanitizeProviderMessage("a".repeat(50), 100)).isEqualTo("[redacted]");
   }
}
