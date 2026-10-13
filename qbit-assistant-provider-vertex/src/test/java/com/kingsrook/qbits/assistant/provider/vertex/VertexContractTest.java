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


import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelToolDefinition;
import com.kingsrook.qbits.assistant.provider.ProviderSettings;
import com.kingsrook.qbits.assistant.testing.CloudProviderTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.kingsrook.qbits.assistant.testing.CloudProviderTestServer.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Real Vertex Claude and OpenAI-compatible transports using fake ADC tokens.
 *******************************************************************************/
class VertexContractTest
{
   static final String CLAUDE = "claude-sonnet-4-5@20250929";
   static final String GEMINI = "gemini-2.5-flash";



   /*******************************************************************************
    ** Fixed fake token, valid well beyond the test duration.
    *******************************************************************************/
   static GoogleCredentials credentials()
   {
      return (GoogleCredentials.create(new AccessToken("fake-google-token", new Date(System.currentTimeMillis() + 3_600_000))));
   }



   /*******************************************************************************
    ** Scoped connection settings with a loopback override.
    *******************************************************************************/
   static ProviderSettings settings(CloudProviderTestServer server)
   {
      return (new ProviderSettings().withKind("VERTEX").withRegion("us-east5").withCloudProject("test-project").withBaseUrl(server.url())
         .withConnectTimeoutSeconds(2).withRequestTimeoutSeconds(5));
   }



   /*******************************************************************************
    ** A strict request verifies that only the Claude backend preserves strict.
    *******************************************************************************/
   static ModelRequest request(String model)
   {
      return (new ModelRequest().withModel(model).withMaxTokens(1024).withMessages(List.of(ModelMessage.user("Count posts")))
         .withTools(List.of(new ModelToolDefinition().withName("count_records").withDescription("Count records").withStrict(true)
            .withInputSchema(Map.of("type", "object", "properties", Map.of("table", Map.of("type", "string")), "required", List.of("table"), "additionalProperties", false)))));
   }



   /*******************************************************************************
    ** Text and a fragmented tool call in a Vertex OpenAI-compatible stream.
    *******************************************************************************/
   static String geminiSse(String text, Map<String, Object> input) throws IOException
   {
      StringBuilder events = new StringBuilder();
      events.append("data: ").append(JSON.writeValueAsString(Map.of("choices", List.of(Map.of("delta", Map.of("content", text)))))).append("\n\n");
      if(input != null)
      {
         String arguments = JSON.writeValueAsString(input);
         Integer middle = arguments.length() / 2;
         events.append("data: ").append(JSON.writeValueAsString(Map.of("choices", List.of(Map.of("delta", Map.of("tool_calls", List.of(
            Map.of("index", 0, "id", "count", "type", "function", "function", Map.of("name", "count_records", "arguments", arguments.substring(0, middle)))))))))).append("\n\n");
         events.append("data: ").append(JSON.writeValueAsString(Map.of("choices", List.of(Map.of("delta", Map.of("tool_calls", List.of(
            Map.of("index", 0, "function", Map.of("arguments", arguments.substring(middle)))))))))).append("\n\n");
      }
      events.append("data: ").append(JSON.writeValueAsString(Map.of("choices", List.of(Map.of("delta", Map.of(), "finish_reason", input == null ? "stop" : "tool_calls")),
         "usage", Map.of("prompt_tokens", 12, "completion_tokens", 7)))).append("\n\ndata: [DONE]\n\n");
      return (events.toString());
   }



   /*******************************************************************************
    ** Claude path, ADC bearer, Anthropic version, streaming text, tools and usage.
    *******************************************************************************/
   @Test
   void claudeContract() throws Exception
   {
      try(CloudProviderTestServer server = new CloudProviderTestServer())
      {
         server.sse(CloudProviderTestServer.anthropicSse(CLAUDE, "Checking.", Map.of("table", "blog_post")));
         var provider = new VertexModelProvider(settings(server), credentials());
         try
         {
            StringBuilder text = new StringBuilder();
            var response = provider.stream(request(CLAUDE), text::append).await(Duration.ofSeconds(10));
            assertThat(provider.getName()).isEqualTo("vertex");
            assertThat(provider.getCapabilities(CLAUDE).getPromptCaching()).isTrue();
            assertThat(text.toString()).isEqualTo("Checking.");
            assertThat(response.getToolUses()).singleElement().satisfies(tool ->
            {
               assertThat(tool.getToolUseId()).isEqualTo("count");
               assertThat(tool.getToolName()).isEqualTo("count_records");
               assertThat(tool.getInput()).containsEntry("table", "blog_post");
            });
            assertThat(response.getUsage().getInputTokens()).isEqualTo(12);
            assertThat(response.getUsage().getOutputTokens()).isEqualTo(7);
            var sent = server.requests.getFirst();
            assertThat(sent.path()).isEqualTo("/v1/projects/test-project/locations/us-east5/publishers/anthropic/models/" + CLAUDE + ":streamRawPredict");
            assertThat(sent.authorization()).isEqualTo("Bearer fake-google-token");
            var body = JSON.readTree(sent.body());
            assertThat(body.path("anthropic_version").asText()).isEqualTo("vertex-2023-10-16");
            assertThat(body.has("model")).isFalse();
            assertThat(body.path("tools").get(0).path("strict").asBoolean()).isTrue();
         }
         finally
         {
            provider.close();
         }
      }
   }



   /*******************************************************************************
    ** Gemini, already-prefixed and other ids all use OpenAI without strict.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = { "gemini-2.5-flash", "google/gemini-2.5-pro", "other-model" })
   void geminiContractAndTokenRefresh(String model) throws Exception
   {
      try(CloudProviderTestServer server = new CloudProviderTestServer())
      {
         var credentials = new RefreshingCredentials();
         String endpoint = "/v1/projects/test-project/locations/us-east5/endpoints/openapi";
         var provider = new VertexModelProvider(settings(server).withBaseUrl(server.url() + endpoint), credentials);
         try
         {
            for(int call = 1; call <= 2; call++)
            {
               server.sse(geminiSse("Checking.", Map.of("table", "blog_post")));
               StringBuilder text = new StringBuilder();
               ModelRequest request = request(model);
               var response = provider.stream(request, text::append).await(Duration.ofSeconds(10));
               assertThat(text.toString()).isEqualTo("Checking.");
               assertThat(request.getModel()).isEqualTo(model);
               assertThat(response.getToolUses()).singleElement().satisfies(tool ->
               {
                  assertThat(tool.getToolName()).isEqualTo("count_records");
                  assertThat(tool.getInput()).containsEntry("table", "blog_post");
               });
               assertThat(response.getUsage().getInputTokens()).isEqualTo(12);
               assertThat(response.getUsage().getOutputTokens()).isEqualTo(7);
               var sent = server.requests.get(call - 1);
               assertThat(sent.path()).isEqualTo(endpoint + "/chat/completions");
               assertThat(sent.authorization()).isEqualTo("Bearer refreshed-" + call);
               var body = JSON.readTree(sent.body());
               assertThat(body.path("model").asText()).isEqualTo(model.contains("/") ? model : "google/" + model);
               assertThat(body.path("tools").get(0).path("function").has("strict")).isFalse();
               if(call == 1)
               {
                  credentials.refresh();
               }
            }
            assertThat(credentials.refreshes).isEqualTo(2);
            assertThat(provider.getCapabilities(model).getPromptCaching()).isFalse();
            assertThat(provider.getCapabilities(null).getTools()).isTrue();
         }
         finally
         {
            provider.close();
         }
      }
   }



   /*******************************************************************************
    ** Start expired so refreshIfExpired must obtain the first token itself.
    *******************************************************************************/
   static class RefreshingCredentials extends GoogleCredentials
   {
      private Integer refreshes = 0;

      /*******************************************************************************
       ** An expired fake token cannot authorize the first request.
       *******************************************************************************/
      RefreshingCredentials()
      {
         super(new AccessToken("expired", new Date(0)));
      }

      /*******************************************************************************
       ** Deterministic replacement tokens, without a token-server request.
       *******************************************************************************/
      @Override
      public AccessToken refreshAccessToken() throws IOException
      {
         refreshes++;
         return (new AccessToken("refreshed-" + refreshes, new Date(System.currentTimeMillis() + 3_600_000)));
      }
   }



   /*******************************************************************************
    ** ADC refresh failures cannot leak the credential exception's details.
    *******************************************************************************/
   @Test
   void refreshFailureIsSanitized() throws Exception
   {
      try(CloudProviderTestServer server = new CloudProviderTestServer())
      {
         var credentials = new RefreshingCredentials()
         {
            /*******************************************************************************
             ** Simulate a token service failure containing confidential diagnostics.
             *******************************************************************************/
            @Override
            public AccessToken refreshAccessToken() throws IOException
            {
               throw (new IOException("synthetic-private-token"));
            }
         };
         var provider = new VertexModelProvider(settings(server), credentials);
         try
         {
            assertThatThrownBy(() -> provider.stream(request(GEMINI), delta -> {}).await(Duration.ofSeconds(10)))
               .isInstanceOfSatisfying(ModelProviderException.class, error ->
               {
                  assertThat(error.getKind()).isEqualTo(ModelProviderException.Kind.AUTHENTICATION);
                  assertThat(error.toString()).doesNotContain("synthetic-private-token");
                  assertThat(error.getCause()).isNull();
               });
            assertThat(server.requests).isEmpty();
         }
         finally
         {
            provider.close();
         }
      }
   }



   /*******************************************************************************
    ** Cloud requirements, static discovery and URL allowlists need no ADC lookup.
    *******************************************************************************/
   @Test
   void settingsValidationAndDefaults() throws Exception
   {
      var type = new VertexProviderType();
      var config = new AssistantQBitConfig().withProviderType(type).withAllowedBaseUrls(Set.of("http://127.0.0.1:1234"));
      var service = new ProviderService(config, null, Clock.systemUTC());
      var row = new AssistantProvider().withProviderType("vertex");
      assertThat(service.validate(row)).anyMatch(error -> error.contains("Region is required")).anyMatch(error -> error.contains("project id"));
      row.withRegion(" ").withCloudProject(" ");
      assertThat(service.validate(row)).hasSize(2);
      row.withRegion("us-east5").withCloudProject("test-project");
      assertThat(service.validate(row)).isEmpty();
      row.withApiKeyEnvName("GOOGLE_API_KEY");
      assertThat(service.validate(row)).anyMatch(error -> error.contains("Application Default Credentials"));
      row.withApiKeyEnvName(" ").withBaseUrl("https://unlisted.example");
      assertThat(service.validate(row)).anyMatch(error -> error.contains("not allowed"));
      row.withBaseUrl("http://127.0.0.1:1234");
      assertThat(service.validate(row)).isEmpty();
      assertThat(type.getType()).isEqualTo("vertex");
      assertThat(type.getKinds()).containsExactly("VERTEX");
      assertThat(type.getLabel()).isEqualTo("Google Vertex AI (Claude, Gemini)");
      assertThat(type.getDefaultLocation("VERTEX")).isEqualTo("CLOUD");
      assertThat(type.getDefaultApiKeyEnvName()).isNull();
      assertThat(type.getDefaultBaseUrl()).isNull();
      assertThat(type.discoverModels(new ProviderSettings())).extracting(model -> model.modelId()).containsExactlyElementsOf(type.getModels());
      assertThat(type.checkHealth(new ProviderSettings()).healthy()).isNull();
      assertThatThrownBy(() -> type.create("key", null)).isInstanceOf(IllegalArgumentException.class);
      // Exercise endpoint derivation without sending any request to that endpoint.
      var provider = new VertexModelProvider(new ProviderSettings().withRegion("us-east5").withCloudProject("test-project"), credentials());
      provider.close();
   }
}
