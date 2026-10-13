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
import java.util.Locale;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kingsrook.qbits.assistant.provider.ModelCapabilities;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelPart;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelToolDefinition;
import com.kingsrook.qbits.assistant.provider.SystemBlock;


/*******************************************************************************
 ** Chat Completions request mapping and conservative strict-schema checks.
 *******************************************************************************/
final class OpenAiMapping
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private OpenAiMapping()
   {
   }



   /*******************************************************************************
    ** Map only capabilities the target dialect supports.
    *******************************************************************************/
   static ObjectNode request(ModelRequest request, String kind, ModelCapabilities capabilities) throws JsonProcessingException
   {
      ObjectNode body = OpenAiHttp.JSON.createObjectNode();
      boolean streaming = Boolean.TRUE.equals(capabilities.getStreaming());
      boolean nativeOpenAi = List.of("OPENAI", "AZURE_OPENAI").contains(kind);
      String model = request.getModel();
      body.put("model", "VERTEX".equals(kind) && model != null && !model.contains("/") ? "google/" + model : model);
      body.put("stream", streaming);
      if(streaming)
      {
         body.putObject("stream_options").put("include_usage", true);
      }
      if(request.getMaxTokens() != null)
      {
         body.put(nativeOpenAi ? "max_completion_tokens" : "max_tokens", request.getMaxTokens());
      }
      if(Boolean.TRUE.equals(capabilities.getReasoningEffort()) && request.getEffort() != null)
      {
         String effort = request.getEffort().toLowerCase(Locale.ROOT);
         body.put("reasoning_effort", List.of("low", "medium").contains(effort) ? effort : "high");
      }
      ArrayNode messages = body.putArray("messages");
      if(!request.getSystem().isEmpty())
      {
         messages.addObject().put("role", "system").put("content", request.getSystem().stream().map(SystemBlock::getText).collect(Collectors.joining("\n\n")));
      }
      for(ModelMessage message : request.getMessages())
      {
         ObjectNode current = null;
         for(ModelPart part : message.getParts())
         {
            if(part.getType() == ModelPart.Type.TOOL_RESULT)
            {
               current = null;
               messages.addObject().put("role", "tool").put("tool_call_id", part.getToolUseId())
                  .put("content", (Boolean.TRUE.equals(part.getIsError()) ? "ERROR: " : "") + part.getText());
               continue;
            }
            if(current == null)
            {
               current = messages.addObject().put("role", message.getRole() == ModelMessage.Role.ASSISTANT ? "assistant" : "user");
               current.put("content", "");
            }
            if(part.getType() == ModelPart.Type.TEXT)
            {
               current.put("content", current.path("content").asText() + part.getText());
            }
            else if(part.getType() == ModelPart.Type.TOOL_USE)
            {
               ArrayNode calls = current.has("tool_calls") ? (ArrayNode) current.get("tool_calls") : current.putArray("tool_calls");
               calls.addObject().put("id", part.getToolUseId()).put("type", "function").putObject("function")
                  .put("name", part.getToolName()).put("arguments", OpenAiHttp.JSON.writeValueAsString(part.getInput()));
            }
         }
      }
      if(!request.getTools().isEmpty() && Boolean.TRUE.equals(capabilities.getTools()))
      {
         ArrayNode tools = body.putArray("tools");
         for(ModelToolDefinition tool : request.getTools())
         {
            JsonNode schema = OpenAiHttp.JSON.valueToTree(tool.getInputSchema());
            ObjectNode function = tools.addObject().put("type", "function").putObject("function").put("name", tool.getName()).put("description", tool.getDescription());
            function.set("parameters", schema);
            if(nativeOpenAi && Boolean.TRUE.equals(tool.getStrict()) && strictCompatible(schema))
            {
               function.put("strict", true);
            }
         }
         if(List.of("OPENAI", "AZURE_OPENAI", "OPENROUTER", "VLLM").contains(kind))
         {
            body.put("parallel_tool_calls", Boolean.TRUE.equals(request.getParallelToolCalls()) && Boolean.TRUE.equals(capabilities.getParallelToolCalls()));
         }
      }
      return (body);
   }



   /*******************************************************************************
    ** Every object must forbid extras and require every declared property.
    *******************************************************************************/
   static boolean strictCompatible(JsonNode schema)
   {
      if(schema == null || !schema.isObject())
      {
         return (false);
      }
      JsonNode type = schema.path("type");
      if("object".equals(type.asText()) || schema.has("properties") || (type.isArray() && type.toString().contains("\"object\"")))
      {
         if(!schema.has("additionalProperties") || !schema.get("additionalProperties").isBoolean() || schema.get("additionalProperties").asBoolean())
         {
            return (false);
         }
         JsonNode required = schema.path("required");
         var names = schema.path("properties").fieldNames();
         while(names.hasNext())
         {
            String name = names.next();
            boolean found = false;
            for(JsonNode value : required)
            {
               found |= name.equals(value.asText());
            }
            if(!found || !strictCompatible(schema.path("properties").get(name)))
            {
               return (false);
            }
         }
      }
      for(String keyword : List.of("items", "additionalProperties"))
      {
         if(schema.path(keyword).isObject() && !strictCompatible(schema.get(keyword)))
         {
            return (false);
         }
      }
      for(String keyword : List.of("anyOf", "oneOf", "allOf", "prefixItems"))
      {
         for(JsonNode child : schema.path(keyword))
         {
            if(!strictCompatible(child))
            {
               return (false);
            }
         }
      }
      for(String keyword : List.of("$defs", "definitions"))
      {
         for(JsonNode child : schema.path(keyword))
         {
            if(!strictCompatible(child))
            {
               return (false);
            }
         }
      }
      return (true);
   }
}
