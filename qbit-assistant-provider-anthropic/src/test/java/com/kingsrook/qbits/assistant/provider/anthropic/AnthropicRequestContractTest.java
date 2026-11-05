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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.anthropic.core.ObjectMappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qbits.assistant.spi.CacheHint;
import com.kingsrook.qbits.assistant.spi.ModelMessage;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.spi.ModelToolDefinition;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The exact Messages API request the turn engine sends, checked against the
 ** API's documented constraints, plus a conservative budget for strict tools.
 **
 ** Why the budget: Anthropic compiles every strict tool schema of a request
 ** into one grammar and refuses the request when that grammar is too large
 ** (400 invalid_request_error "The compiled grammar is too large ... reduce the
 ** number of strict tools"). With all ten read tools strict (0.1.0-RC.3 to
 ** RC.4) every kof22 turn failed that way, though the request stayed inside
 ** the documented limits (20 strict tools, 24 optional parameters, 16 union
 ** parameters). The real limit is not published, so the budget below is far
 ** under the shape that failed (10 tools / 12 optional / 3 unions). A real-API
 ** check is in docs/DESIGN.md ("Strict tools") for dev use with a key.
 *******************************************************************************/
class AnthropicRequestContractTest
{
   /////////////////////////////////////////////////////////////////////
   // conservative strict-tool budget (see the class comment)         //
   /////////////////////////////////////////////////////////////////////
   static final int MAX_STRICT_TOOLS           = 3;
   static final int MAX_STRICT_OPTIONAL_PARAMS = 6;
   static final int MAX_STRICT_UNION_PARAMS    = 2;

   private static final Set<String> EFFORTS            = Set.of("low", "medium", "high", "xhigh", "max");
   private static final Set<String> UNSUPPORTED_STRICT = Set.of("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf", "minLength", "maxLength",
      "maxItems", "uniqueItems", "oneOf", "not", "if", "then", "else", "patternProperties");



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** The request body for the fixture app, as the SDK serializes it.
    *******************************************************************************/
   static Map<String, Object> requestBody(AssistantQBitConfig config) throws Exception
   {
      QContext.init(AssistantTestFixture.build(config), AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      ModelRequest request = config.getRuntime().getContextBuilder().build(QContext.getQSession(), config.getModels().getMainModel(), config.getModels().getMainEffort(),
         List.of(), "How many published posts are there?", Map.of("tableName", "blog_post"));
      String json = ObjectMappers.jsonMapper().writeValueAsString(AnthropicMapping.toParams(request)._body());
      return (JsonUtils.toObject(json, new TypeReference<Map<String, Object>>() {}));
   }



   /*******************************************************************************
    ** An image part becomes a base64 image block; as the last block of the
    ** newest message it carries the cache hint.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testImagePartMapping() throws Exception
   {
      ModelRequest request = new ModelRequest().withModel("claude-test").withMaxTokens(64).withMessages(List.of(new ModelMessage().withRole(ModelMessage.Role.USER)
         .withPart(ModelPart.text("Describe it")).withPart(ModelPart.image("image/webp", "UklGRg==")).withCacheHint(CacheHint.SHORT)));
      String              json  = ObjectMappers.jsonMapper().writeValueAsString(AnthropicMapping.toParams(request)._body());
      Map<String, Object> body  = JsonUtils.toObject(json, new TypeReference<Map<String, Object>>() {});
      List<Object>        content = (List<Object>) ((Map<String, Object>) ((List<Object>) body.get("messages")).get(0)).get("content");
      Map<String, Object> image = (Map<String, Object>) content.get(1);
      assertThat(image).containsEntry("type", "image").containsKey("cache_control");
      assertThat((Map<String, Object>) image.get("source")).containsEntry("type", "base64").containsEntry("media_type", "image/webp").containsEntry("data", "UklGRg==");
      assertThat((Map<String, Object>) content.get(0)).doesNotContainKey("cache_control");
   }



   /*******************************************************************************
    ** The default request meets every documented constraint and the budget.
    *******************************************************************************/
   @Test
   void testDefaultRequestMeetsTheApiContract() throws Exception
   {
      Map<String, Object> body = requestBody(new AssistantQBitConfig().withModelProvider(new ScriptedModelProvider()));
      assertThat(violations(body)).isEmpty();
      assertThat(strictBudgetViolations(body)).isEmpty();
      assertThat(tools(body)).hasSizeGreaterThanOrEqualTo(8).allSatisfy(tool -> assertThat(tool.get("strict")).isNotEqualTo(true));
   }



   /*******************************************************************************
    ** Presentation remains inside the actual serialized strict request budget.
    *******************************************************************************/
   @Test
   void testPresentationStrictOptIn() throws Exception
   {
      Map<String, Object> body = requestBody(new AssistantQBitConfig().withModelProvider(new ScriptedModelProvider()).withStrictToolNames(Set.of("present_results")));
      assertThat(violations(body)).isEmpty();
      assertThat(strictBudgetViolations(body)).isEmpty();
      assertThat(tools(body)).anySatisfy(tool ->
      {
         assertThat(tool.get("name")).isEqualTo("present_results");
         assertThat(tool.get("strict")).isEqualTo(true);
      });
   }



   /*******************************************************************************
    ** The RC.3 shape (every tool strict) is recognized as over budget, and a
    ** host can still opt a few tools in.
    *******************************************************************************/
   @Test
   void testStrictBudget() throws Exception
   {
      AssistantQBitConfig probe = new AssistantQBitConfig().withModelProvider(new ScriptedModelProvider());
      Set<String> all = new LinkedHashSet<>();
      requestBody(probe);
      for(ModelToolDefinition tool : probe.getRuntime().getContextBuilder().tools())
      {
         all.add(tool.getName());
      }
      QContext.clear();

      Map<String, Object> rc3 = requestBody(new AssistantQBitConfig().withModelProvider(new ScriptedModelProvider()).withStrictToolNames(all));
      assertThat(violations(rc3)).isEmpty();
      assertThat(strictBudgetViolations(rc3)).isNotEmpty();
      QContext.clear();

      Map<String, Object> few = requestBody(new AssistantQBitConfig().withModelProvider(new ScriptedModelProvider()).withStrictToolNames(Set.of("get_record", "describe_table")));
      assertThat(violations(few)).isEmpty();
      assertThat(strictBudgetViolations(few)).isEmpty();
      assertThat(tools(few).stream().filter(t -> Boolean.TRUE.equals(t.get("strict"))).map(t -> t.get("name"))).containsExactlyInAnyOrder("get_record", "describe_table");
   }



   /*******************************************************************************
    ** A 400 about the grammar is flagged as a strict-schema rejection, carries
    ** the API's message and request id, and never the key.
    *******************************************************************************/
   @Test
   void testGrammarRejectionIsRecognized() throws Exception
   {
      try(RecordedAnthropicServer server = new RecordedAnthropicServer())
      {
         server.thenError(400, "grammar-too-large.json");
         AnthropicModelProvider provider = AnthropicModelProvider.builder().apiKey("sk-ant-test-0000000000000000000000").baseUrl(server.baseUrl()).maxRetries(0).build();
         assertThatThrownBy(() -> provider.stream(AnthropicModelProviderTest.request(), t -> { }).await(Duration.ofSeconds(10)))
            .isInstanceOfSatisfying(ModelProviderException.class, e ->
            {
               assertThat(e.getKind()).isEqualTo(ModelProviderException.Kind.INVALID_REQUEST);
               assertThat(e.isStrictSchemaRejected()).isTrue();
               assertThat(e.getProviderErrorType()).isEqualTo("invalid_request_error");
               assertThat(e.getProviderMessage()).startsWith("The compiled grammar is too large");
               assertThat(e.getMessage()).contains("compiled grammar").doesNotContain("sk-ant-test");
            });
         provider.close();
      }
   }



   /*******************************************************************************
    ** End to end: when the provider refuses strict schemas, the turn retries
    ** once without strict tools and answers.
    *******************************************************************************/
   @Test
   void testTurnRetriesWithoutStrictTools() throws Exception
   {
      try(RecordedAnthropicServer server = new RecordedAnthropicServer())
      {
         server.thenError(400, "grammar-too-large.json").thenStream("text-with-thinking.sse", 0);
         AnthropicModelProvider provider = AnthropicModelProvider.builder().apiKey("sk-ant-test-0000000000000000000000").baseUrl(server.baseUrl()).maxRetries(0).build();
         AssistantQBitConfig config = new AssistantQBitConfig().withModelProvider(provider).withStrictToolNames(Set.of("get_record", "count_records", "query_records"));
         TurnRunner          runner = new TurnRunner(AssistantTestFixture.build(config), config);

         TurnRunner.Outcome outcome = runner.run(AssistantTestFixture.EDITOR, "How many published posts are there?");
         assertThat(outcome.end().get("status")).isEqualTo("COMPLETE");
         assertThat(server.getRequests()).hasSize(2);
         assertThat(server.getRequests().get(0).get("body")).contains("\"strict\":true");
         assertThat(server.getRequests().get(1).get("body")).doesNotContain("\"strict\":true");
         provider.close();
      }
   }



   /*******************************************************************************
    ** Documented request constraints (Messages API, claude-sonnet-5-5 family).
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   static List<String> violations(Map<String, Object> body)
   {
      List<String> errors = new ArrayList<>();
      if(!(body.get("model") instanceof String model) || !model.matches("claude-[a-z]+-[0-9]+(-[0-9]+)?"))
      {
         errors.add("model id " + body.get("model"));
      }
      if(!(body.get("max_tokens") instanceof Number max) || max.intValue() < 1 || max.intValue() > 128_000)
      {
         errors.add("max_tokens " + body.get("max_tokens"));
      }
      for(String sampling : List.of("temperature", "top_p", "top_k"))
      {
         if(body.containsKey(sampling))
         {
            errors.add(sampling + " is rejected with thinking on current models");
         }
      }
      Map<String, Object> thinking = (Map<String, Object>) body.get("thinking");
      if(thinking != null && (!"adaptive".equals(thinking.get("type")) || thinking.containsKey("budget_tokens")))
      {
         errors.add("thinking must be adaptive without budget_tokens: " + thinking);
      }
      Map<String, Object> outputConfig = (Map<String, Object>) body.get("output_config");
      if(outputConfig != null && outputConfig.get("effort") != null && !EFFORTS.contains(outputConfig.get("effort")))
      {
         errors.add("effort " + outputConfig.get("effort"));
      }
      Map<String, Object> toolChoice = (Map<String, Object>) body.get("tool_choice");
      if(toolChoice != null && !"auto".equals(toolChoice.get("type")) && !"none".equals(toolChoice.get("type")))
      {
         errors.add("forced tool_choice is rejected: " + toolChoice);
      }

      ///////////////////////////////////////////////////////////////////
      // cache breakpoints: at most 4, longer TTLs before shorter ones //
      ///////////////////////////////////////////////////////////////////
      List<String> ttls = new ArrayList<>();
      collectCacheTtls(body.get("tools"), ttls);
      collectCacheTtls(body.get("system"), ttls);
      collectCacheTtls(body.get("messages"), ttls);
      if(ttls.size() > 4)
      {
         errors.add("more than 4 cache breakpoints: " + ttls);
      }
      for(int i = 1; i < ttls.size(); i++)
      {
         if("1h".equals(ttls.get(i)) && "5m".equals(ttls.get(i - 1)))
         {
            errors.add("a 1h cache entry after a 5m one: " + ttls);
         }
      }

      List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
      if(messages == null || messages.isEmpty() || !"user".equals(messages.get(0).get("role")))
      {
         errors.add("messages must start with a user message");
      }
      else if(!"user".equals(messages.get(messages.size() - 1).get("role")))
      {
         errors.add("no assistant prefill on current models");
      }
      for(Map<String, Object> message : messages == null ? List.<Map<String, Object>>of() : messages)
      {
         for(Map<String, Object> block : (List<Map<String, Object>>) message.get("content"))
         {
            if("text".equals(block.get("type")) && (!(block.get("text") instanceof String text) || text.isBlank()))
            {
               errors.add("empty text block");
            }
         }
      }

      Set<String> names = new LinkedHashSet<>();
      for(Map<String, Object> tool : tools(body))
      {
         String name = String.valueOf(tool.get("name"));
         if(!name.matches("[a-zA-Z0-9_-]{1,64}") || !names.add(name))
         {
            errors.add("tool name " + name);
         }
         Map<String, Object> schema = (Map<String, Object>) tool.get("input_schema");
         if(schema == null || !"object".equals(schema.get("type")))
         {
            errors.add(name + ": input_schema must be an object schema");
            continue;
         }
         if(Boolean.TRUE.equals(tool.get("strict")))
         {
            checkStrictSchema(name, schema, errors);
         }
      }
      return (errors);
   }



   /*******************************************************************************
    ** The strict-tool budget: count, optional parameters and unions across all
    ** strict schemas.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   static List<String> strictBudgetViolations(Map<String, Object> body)
   {
      int[] counts = new int[2];
      int   strict = 0;
      for(Map<String, Object> tool : tools(body))
      {
         if(Boolean.TRUE.equals(tool.get("strict")))
         {
            strict++;
            countOptionalAndUnions(tool.get("input_schema"), counts);
         }
      }
      List<String> errors = new ArrayList<>();
      if(strict > MAX_STRICT_TOOLS)
      {
         errors.add(strict + " strict tools (budget " + MAX_STRICT_TOOLS + ")");
      }
      if(counts[0] > MAX_STRICT_OPTIONAL_PARAMS)
      {
         errors.add(counts[0] + " optional parameters in strict tools (budget " + MAX_STRICT_OPTIONAL_PARAMS + ")");
      }
      if(counts[1] > MAX_STRICT_UNION_PARAMS)
      {
         errors.add(counts[1] + " union parameters in strict tools (budget " + MAX_STRICT_UNION_PARAMS + ")");
      }
      return (errors);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> tools(Map<String, Object> body)
   {
      return (body.get("tools") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of());
   }



   /*******************************************************************************
    ** Strict schemas: additionalProperties false on every object, no
    ** unsupported keywords, minItems 0 or 1 only, no recursive $ref.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static void checkStrictSchema(String path, Object node, List<String> errors)
   {
      if(node instanceof Map<?, ?> map)
      {
         Map<String, Object> schema = (Map<String, Object>) map;
         if(("object".equals(schema.get("type")) || schema.containsKey("properties")) && !Boolean.FALSE.equals(schema.get("additionalProperties")))
         {
            errors.add(path + ": additionalProperties must be false");
         }
         for(String keyword : UNSUPPORTED_STRICT)
         {
            if(schema.containsKey(keyword))
            {
               errors.add(path + ": unsupported keyword " + keyword);
            }
         }
         if(schema.get("minItems") instanceof Number minItems && minItems.intValue() > 1)
         {
            errors.add(path + ": minItems above 1");
         }
         if(schema.get("$defs") instanceof Map<?, ?> defs)
         {
            for(Map.Entry<?, ?> def : defs.entrySet())
            {
               if(JsonUtils.toJson(def.getValue()).contains("\"#/$defs/" + def.getKey() + "\""))
               {
                  errors.add(path + ": recursive $defs/" + def.getKey());
               }
            }
         }
         schema.forEach((key, value) -> checkStrictSchema(path + "/" + key, value, errors));
      }
      else if(node instanceof List<?> list)
      {
         list.forEach(value -> checkStrictSchema(path, value, errors));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static void countOptionalAndUnions(Object node, int[] counts)
   {
      if(node instanceof Map<?, ?> map)
      {
         Map<String, Object> schema = (Map<String, Object>) map;
         if(schema.get("properties") instanceof Map<?, ?> properties)
         {
            List<Object> required = schema.get("required") instanceof List<?> r ? (List<Object>) r : List.of();
            for(Object name : properties.keySet())
            {
               if(!required.contains(name))
               {
                  counts[0]++;
               }
            }
         }
         if(schema.get("type") instanceof List<?> || schema.containsKey("anyOf"))
         {
            counts[1]++;
         }
         schema.values().forEach(value -> countOptionalAndUnions(value, counts));
      }
      else if(node instanceof List<?> list)
      {
         list.forEach(value -> countOptionalAndUnions(value, counts));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static void collectCacheTtls(Object node, List<String> ttls)
   {
      if(node instanceof Map<?, ?> map)
      {
         if(map.get("cache_control") instanceof Map<?, ?> cache)
         {
            ttls.add(cache.get("ttl") == null ? "5m" : String.valueOf(cache.get("ttl")));
         }
         for(Map.Entry<?, ?> entry : map.entrySet())
         {
            if(!"cache_control".equals(entry.getKey()) && !"input_schema".equals(entry.getKey()))
            {
               collectCacheTtls(entry.getValue(), ttls);
            }
         }
      }
      else if(node instanceof List<?> list)
      {
         list.forEach(value -> collectCacheTtls(value, ttls));
      }
   }

}
