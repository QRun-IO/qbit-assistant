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
import com.kingsrook.qbits.assistant.provider.EmbeddingRequest;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ProviderSettings;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Embedding wire contracts through the same local HTTP harness as chat.
 *******************************************************************************/
class OpenAiEmbeddingTest
{
   /*******************************************************************************
    ** Input arrays, response indexes, usage and authentication are preserved.
    *******************************************************************************/
   @Test
   void shapeOrderAndAzure() throws Exception
   {
      for(String kind : List.of("OPENAI", "AZURE_OPENAI", "OLLAMA"))
      {
         try(MockOpenAiServer server = new MockOpenAiServer())
         {
            server.enqueue(200, "{\"data\":[{\"index\":1,\"embedding\":[0,1]},{\"index\":0,\"embedding\":[1,0]}],\"usage\":{\"prompt_tokens\":7}}");
            var provider = new OpenAiCompatibleModelProvider(new ProviderSettings().withKind(kind).withBaseUrl(server.url()).withApiKey("synthetic-key").withApiVersion("2024-10-21"));
            var response = provider.embed(new EmbeddingRequest("bge-m3", List.of("first", "second")));
            assertThat(response.vectors()).hasSize(2);
            assertThat(response.vectors().getFirst()).containsExactly(1, 0);
            assertThat(response.usage().getInputTokens()).isEqualTo(7L);
            var captured = server.requests.getFirst();
            assertThat(captured.path()).isEqualTo(kind.equals("AZURE_OPENAI") ? "/v1/openai/deployments/bge-m3/embeddings?api-version=2024-10-21" : "/v1/embeddings");
            var body = OpenAiHttp.JSON.readTree(captured.body());
            assertThat(body.path("model").asText()).isEqualTo("bge-m3");
            assertThat(body.path("input").size()).isEqualTo(2);
            assertThat(body.has("messages")).isFalse();
            assertThat(body.has("stream")).isFalse();
            assertThat(kind.equals("AZURE_OPENAI") ? captured.apiKey() : captured.authorization()).contains("synthetic-key");
         }
      }
   }



   /*******************************************************************************
    ** Reuse chat's safe error categories, without echoing credentials.
    *******************************************************************************/
   @Test
   void errorsAndMalformedVectors() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         var provider = new OpenAiCompatibleModelProvider(new ProviderSettings().withKind("OPENAI").withBaseUrl(server.url()).withApiKey("synthetic-key"));
         server.enqueue(429, "{\"error\":{\"message\":\"synthetic-key rejected\"}}");
         assertThatThrownBy(() -> provider.embed(new EmbeddingRequest("embed", List.of("text"))))
            .isInstanceOfSatisfying(ModelProviderException.class, e ->
            {
               assertThat(e.getKind()).isEqualTo(ModelProviderException.Kind.RATE_LIMITED);
               assertThat(e.getMessage()).doesNotContain("synthetic-key");
            });
         for(String body : List.of("{}", "{\"data\":[{\"index\":0,\"embedding\":[\"not numeric\"]}]}",
            "{\"data\":[{\"index\":2,\"embedding\":[1]}]}", "{\"data\":[{\"index\":0,\"embedding\":[1]},{\"index\":0,\"embedding\":[1]}]}"))
         {
            server.enqueue(200, body);
            assertThatThrownBy(() -> provider.embed(new EmbeddingRequest("embed", List.of("text")))).isInstanceOf(ModelProviderException.class);
         }
         assertThatThrownBy(() -> new EmbeddingRequest("embed", java.util.Collections.nCopies(65, "text"))).isInstanceOf(IllegalArgumentException.class);
         assertThatThrownBy(() -> new EmbeddingRequest("embed", List.of())).isInstanceOf(IllegalArgumentException.class);
      }
   }



   /*******************************************************************************
    ** Deadline covers a stalled response body, not only HTTP headers.
    *******************************************************************************/
   @Test
   void bodyTimeout() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         server.stall(true);
         var provider = new OpenAiCompatibleModelProvider(new ProviderSettings().withKind("OLLAMA").withBaseUrl(server.url()).withRequestTimeoutSeconds(1));
         assertThatThrownBy(() -> provider.embed(new EmbeddingRequest("embed", List.of("text"))))
            .isInstanceOfSatisfying(ModelProviderException.class, e -> assertThat(e.getKind()).isEqualTo(ModelProviderException.Kind.TIMEOUT));
      }
   }



   /*******************************************************************************
    ** The transport accepts a complete 64-input batch and rejects overflow
    ** before sending. EmbeddingService owns splitting larger indexing jobs.
    *******************************************************************************/
   @Test
   void maximumBatch() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer())
      {
         var body = OpenAiHttp.JSON.createObjectNode();
         var data = body.putArray("data");
         for(Integer i = 0; i < 64; i++)
         {
            data.addObject().put("index", i).putArray("embedding").add(1).add(0);
         }
         server.enqueue(200, body.toString());
         var provider = new OpenAiCompatibleModelProvider(new ProviderSettings().withKind("OLLAMA").withBaseUrl(server.url()));
         assertThat(provider.embed(new EmbeddingRequest("embed", java.util.Collections.nCopies(64, "text"))).vectors()).hasSize(64);
         assertThat(OpenAiHttp.JSON.readTree(server.requests.getFirst().body()).path("input").size()).isEqualTo(64);
         assertThatThrownBy(() -> provider.embed(new EmbeddingRequest("embed", java.util.Collections.nCopies(65, "text")))).isInstanceOf(IllegalArgumentException.class);
         assertThat(server.requests).hasSize(1);
      }
   }
}
