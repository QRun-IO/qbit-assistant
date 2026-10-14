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

package com.kingsrook.qbits.assistant.provider.bedrock;


import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.provider.ModelEventListener;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelToolDefinition;
import com.kingsrook.qbits.assistant.provider.ProviderSettings;
import com.kingsrook.qbits.assistant.provider.anthropic.AnthropicModelProvider;
import com.kingsrook.qbits.assistant.testing.CloudProviderTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import static com.kingsrook.qbits.assistant.testing.CloudProviderTestServer.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Real Anthropic Bedrock SDK against signed loopback requests and binary streams.
 *******************************************************************************/
class BedrockContractTest
{
   static final String MODEL = "us.anthropic.claude-sonnet-4-5-20250929-v1:0";



   /*******************************************************************************
    ** Explicit fake credentials avoid all default-chain network lookup in tests.
    *******************************************************************************/
   static AnthropicModelProvider provider(CloudProviderTestServer server)
   {
      return (new BedrockProviderType().create(new ProviderSettings().withRegion("us-east-1").withBaseUrl(server.url())
         .withConnectTimeoutSeconds(2).withRequestTimeoutSeconds(5),
         StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIDEXAMPLE", "synthetic-secret"))));
   }



   /*******************************************************************************
    ** Engine-shaped request with an opted-in strict tool.
    *******************************************************************************/
   static ModelRequest request()
   {
      return (new ModelRequest().withModel(MODEL).withMaxTokens(1024).withMessages(List.of(ModelMessage.user("Count posts")))
         .withTools(List.of(new ModelToolDefinition().withName("count_records").withDescription("Count records").withStrict(true)
            .withInputSchema(Map.of("type", "object", "properties", Map.of("table", Map.of("type", "string")), "required", List.of("table"), "additionalProperties", false)))));
   }



   /*******************************************************************************
    ** Binary chunk framing, SigV4, request rewriting, text, tools and usage.
    *******************************************************************************/
   @Test
   void streamingContract() throws Exception
   {
      try(CloudProviderTestServer server = new CloudProviderTestServer())
      {
         server.enqueue(200, "application/vnd.amazon.eventstream", BedrockEventStream.encode(CloudProviderTestServer.anthropicEvents(MODEL, "Checking.", Map.of("table", "blog_post"))));
         AnthropicModelProvider provider = provider(server);
         try
         {
            StringBuilder text = new StringBuilder();
            List<String> tools = new ArrayList<>();
            var response = provider.stream(request(), new ModelEventListener()
            {
               /*******************************************************************************
                ** Capture only visible text.
                *******************************************************************************/
               @Override
               public void onTextDelta(String delta)
               {
                  text.append(delta);
               }

               /*******************************************************************************
                ** Confirm tool-start callbacks pass through the reused adapter.
                *******************************************************************************/
               @Override
               public void onToolUseStart(String id, String name)
               {
                  tools.add(id + ":" + name);
               }
            }).await(Duration.ofSeconds(10));
            assertThat(text.toString()).isEqualTo("Checking.");
            assertThat(response.getText()).isEqualTo("Checking.");
            assertThat(tools).containsExactly("count:count_records");
            assertThat(response.getToolUses()).singleElement().satisfies(tool ->
            {
               assertThat(tool.getToolUseId()).isEqualTo("count");
               assertThat(tool.getInput()).containsEntry("table", "blog_post");
            });
            assertThat(response.getStopReason()).isEqualTo("tool_use");
            assertThat(response.getUsage().getInputTokens()).isEqualTo(12);
            assertThat(response.getUsage().getOutputTokens()).isEqualTo(7);
            var sent = server.requests.getFirst();
            assertThat(sent.path()).isEqualTo("/model/" + MODEL + "/invoke-with-response-stream");
            assertThat(sent.authorization()).startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/").contains("/us-east-1/bedrock/aws4_request", "Signature=");
            var body = JSON.readTree(sent.body());
            assertThat(body.path("anthropic_version").asText()).isEqualTo("bedrock-2023-05-31");
            assertThat(body.has("model")).isFalse();
            assertThat(body.has("stream")).isFalse();
            assertThat(body.path("max_tokens").asInt()).isEqualTo(1024);
            assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
            assertThat(body.path("tools").get(0).path("strict").asBoolean()).isTrue();
         }
         finally
         {
            provider.close();
         }
      }
   }



   /*******************************************************************************
    ** The cloud backend keeps the Anthropic adapter's HTTP error mapping.
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource({ "403,AUTHENTICATION", "429,RATE_LIMITED", "500,SERVER" })
   void errors(Integer status, ModelProviderException.Kind kind) throws Exception
   {
      try(CloudProviderTestServer server = new CloudProviderTestServer())
      {
         for(int attempt = 0; attempt < 3; attempt++)
         {
            server.enqueue(status, "application/json", "{\"message\":\"Synthetic failure\"}".getBytes(StandardCharsets.UTF_8));
         }
         AnthropicModelProvider provider = provider(server);
         try
         {
            assertThatThrownBy(() -> provider.stream(request(), delta -> {}).await(Duration.ofSeconds(20)))
               .isInstanceOfSatisfying(ModelProviderException.class, error ->
               {
                  assertThat(error.getKind()).isEqualTo(kind);
                  assertThat(error.getHttpStatus()).isEqualTo(status);
               });
         }
         finally
         {
            provider.close();
         }
      }
   }



   /*******************************************************************************
    ** Registry defaults and validation run without resolving AWS credentials.
    *******************************************************************************/
   @Test
   void settingsValidationAndDefaults() throws Exception
   {
      var type = new BedrockProviderType();
      var config = new AssistantQBitConfig().withProviderType(type).withAllowedBaseUrls(Set.of("http://127.0.0.1:1234"));
      var service = new ProviderService(config, null, Clock.systemUTC());
      var row = new AssistantProvider().withProviderType("bedrock");
      assertThat(service.validate(row)).anyMatch(error -> error.contains("Region is required"));
      row.withRegion(" ");
      assertThat(service.validate(row)).anyMatch(error -> error.contains("Region is required"));
      row.withRegion("us-east-1");
      assertThat(service.validate(row)).isEmpty();
      row.withApiKeyEnvName("AWS_API_KEY");
      assertThat(service.validate(row)).anyMatch(error -> error.contains("default credential chain"));
      row.withApiKeyEnvName(" ").withBaseUrl("https://unlisted.example");
      assertThat(service.validate(row)).anyMatch(error -> error.contains("not allowed"));
      row.withBaseUrl("http://127.0.0.1:1234");
      assertThat(service.validate(row)).isEmpty();
      assertThat(type.getType()).isEqualTo("bedrock");
      assertThat(type.getKinds()).containsExactly("BEDROCK");
      assertThat(type.getLabel()).isEqualTo("AWS Bedrock (Claude)");
      assertThat(type.getDefaultLocation("BEDROCK")).isEqualTo("CLOUD");
      assertThat(type.getDefaultApiKeyEnvName()).isNull();
      assertThat(type.getDefaultBaseUrl()).isNull();
      assertThat(type.discoverModels(new ProviderSettings())).extracting(model -> model.modelId()).containsExactlyElementsOf(type.getModels());
      assertThat(type.checkHealth(new ProviderSettings()).healthy()).isNull();
      assertThatThrownBy(() -> type.create("key", null)).isInstanceOf(IllegalArgumentException.class);
      // Client creation is lazy: no credentials are resolved and no request sent.
      var defaultChain = (AnthropicModelProvider) type.create(new ProviderSettings().withRegion("us-east-1"));
      defaultChain.close();
   }
}

