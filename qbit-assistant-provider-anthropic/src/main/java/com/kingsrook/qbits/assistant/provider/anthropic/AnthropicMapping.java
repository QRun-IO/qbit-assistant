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


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.models.messages.Usage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.assistant.spi.CacheHint;
import com.kingsrook.qbits.assistant.spi.ModelMessage;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.spi.ModelResponse;
import com.kingsrook.qbits.assistant.spi.ModelToolDefinition;
import com.kingsrook.qbits.assistant.spi.ModelUsage;
import com.kingsrook.qbits.assistant.spi.SystemBlock;


/*******************************************************************************
 ** Maps the provider-neutral request / response to the Anthropic Messages
 ** API (anthropic-java):
 ** - system blocks as TextBlockParams; CacheHint LONG = cache_control 1h,
 **   SHORT = 5m (longer TTLs come first in the request, as the API needs);
 ** - tools with strict input schemas, tool_choice auto (forced any / tool is
 **   rejected by Sonnet / Opus 5.5) and disable_parallel_tool_use when the
 **   request asks for one call at a time;
 ** - adaptive thinking with display "omitted" (thinking cannot be disabled
 **   on Sonnet / Opus 5.5; its text is never shown) and an explicit effort;
 ** - an assistant message this provider produced is sent back verbatim
 **   (its MessageParam, with thinking signatures) inside a tool loop.
 *******************************************************************************/
public class AnthropicMapping
{

   /*******************************************************************************
    ** The request parameters.
    *******************************************************************************/
   public static MessageCreateParams toParams(ModelRequest request)
   {
      MessageCreateParams.Builder builder = MessageCreateParams.builder()
         .model(request.getModel())
         .maxTokens(request.getMaxTokens() == null ? 16000L : request.getMaxTokens().longValue())
         .thinking(ThinkingConfigAdaptive.builder().display(ThinkingConfigAdaptive.Display.OMITTED).build());

      if(request.getEffort() != null)
      {
         builder.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(request.getEffort())).build());
      }

      List<TextBlockParam> system = new ArrayList<>();
      for(SystemBlock block : request.getSystem())
      {
         TextBlockParam.Builder text = TextBlockParam.builder().text(block.getText() == null ? "" : block.getText());
         CacheControlEphemeral cache = cacheControl(block.getCacheHint());
         if(cache != null)
         {
            text.cacheControl(cache);
         }
         system.add(text.build());
      }
      if(!system.isEmpty())
      {
         builder.systemOfTextBlockParams(system);
      }

      if(!request.getTools().isEmpty())
      {
         for(ModelToolDefinition definition : request.getTools())
         {
            builder.addTool(toTool(definition));
         }
         builder.toolChoice(ToolChoiceAuto.builder().disableParallelToolUse(Boolean.FALSE.equals(request.getParallelToolCalls())).build());
      }

      for(ModelMessage message : request.getMessages())
      {
         builder.addMessage(toMessageParam(message));
      }
      return (builder.build());
   }



   /*******************************************************************************
    ** One tool.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   static Tool toTool(ModelToolDefinition definition)
   {
      Map<String, Object>    schema      = definition.getInputSchema() == null ? Map.of("type", "object") : definition.getInputSchema();
      Tool.InputSchema.Builder inputSchema = Tool.InputSchema.builder();
      for(Map.Entry<String, Object> entry : schema.entrySet())
      {
         switch(entry.getKey())
         {
            case "type" ->
            {
               /////////////////////////////////////////
               // always "object" (the SDK default)   //
               /////////////////////////////////////////
            }
            case "properties" ->
            {
               Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
               if(entry.getValue() instanceof Map<?, ?> map)
               {
                  ((Map<String, Object>) map).forEach((name, value) -> properties.putAdditionalProperty(name, JsonValue.from(value)));
               }
               inputSchema.properties(properties.build());
            }
            case "required" ->
            {
               List<String> required = new ArrayList<>();
               if(entry.getValue() instanceof List<?> list)
               {
                  list.forEach(o -> required.add(String.valueOf(o)));
               }
               inputSchema.required(required);
            }
            default -> inputSchema.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
         }
      }

      return (Tool.builder()
         .name(definition.getName())
         .description(definition.getDescription() == null ? "" : definition.getDescription())
         .strict(!Boolean.FALSE.equals(definition.getStrict()))
         .inputSchema(inputSchema.build())
         .build());
   }



   /*******************************************************************************
    ** One message: the provider's own param when it produced it, else built
    ** from the parts; a cache hint goes on the last block.
    *******************************************************************************/
   static MessageParam toMessageParam(ModelMessage message)
   {
      if(message.getProviderState() instanceof MessageParam param && CacheHint.NONE.equals(message.getCacheHint()))
      {
         return (param);
      }

      List<ContentBlockParam> blocks = new ArrayList<>();
      List<ModelPart>         parts  = message.getParts();
      for(int i = 0; i < parts.size(); i++)
      {
         ModelPart             part  = parts.get(i);
         CacheControlEphemeral cache = i == parts.size() - 1 ? cacheControl(message.getCacheHint()) : null;
         switch(part.getType())
         {
            case TEXT ->
            {
               TextBlockParam.Builder text = TextBlockParam.builder().text(part.getText() == null || part.getText().isEmpty() ? " " : part.getText());
               if(cache != null)
               {
                  text.cacheControl(cache);
               }
               blocks.add(ContentBlockParam.ofText(text.build()));
            }
            case TOOL_USE ->
            {
               ToolUseBlockParam.Input.Builder input = ToolUseBlockParam.Input.builder();
               if(part.getInput() != null)
               {
                  part.getInput().forEach((k, v) -> input.putAdditionalProperty(k, JsonValue.from(v)));
               }
               ToolUseBlockParam.Builder use = ToolUseBlockParam.builder().id(part.getToolUseId()).name(part.getToolName()).input(input.build());
               if(cache != null)
               {
                  use.cacheControl(cache);
               }
               blocks.add(ContentBlockParam.ofToolUse(use.build()));
            }
            case TOOL_RESULT ->
            {
               ToolResultBlockParam.Builder result = ToolResultBlockParam.builder()
                  .toolUseId(part.getToolUseId())
                  .content(part.getText() == null ? "" : part.getText());
               if(Boolean.TRUE.equals(part.getIsError()))
               {
                  result.isError(true);
               }
               if(cache != null)
               {
                  result.cacheControl(cache);
               }
               blocks.add(ContentBlockParam.ofToolResult(result.build()));
            }
            default -> throw (new IllegalStateException("Unknown part type"));
         }
      }

      return (MessageParam.builder()
         .role(message.getRole() == ModelMessage.Role.ASSISTANT ? MessageParam.Role.ASSISTANT : MessageParam.Role.USER)
         .contentOfBlockParams(blocks)
         .build());
   }



   /*******************************************************************************
    ** The response: visible parts (thinking dropped), stop reason, usage, and
    ** the message itself as provider state for the next loop step.
    *******************************************************************************/
   public static ModelResponse toResponse(Message message)
   {
      List<ModelPart> parts = new ArrayList<>();
      for(ContentBlock block : message.content())
      {
         if(block.text().isPresent())
         {
            parts.add(ModelPart.text(block.text().get().text()));
         }
         else if(block.toolUse().isPresent())
         {
            ToolUseBlock use = block.toolUse().get();
            parts.add(ModelPart.toolUse(use.id(), use.name(), inputMap(use._input())));
         }
         /////////////////////////////////////////////////////////////////
         // thinking / redacted thinking: never surfaced; they travel   //
         // back to the API only inside the provider state below        //
         /////////////////////////////////////////////////////////////////
      }

      return (new ModelResponse()
         .withParts(parts)
         .withStopReason(message.stopReason().map(s -> s.asString()).orElse(ModelResponse.STOP_END_TURN))
         .withUsage(toUsage(message.usage()))
         .withRequestId(message.id())
         .withModel(message.model().asString())
         .withProviderState(message.toParam()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static ModelUsage toUsage(Usage usage)
   {
      if(usage == null)
      {
         return (new ModelUsage());
      }
      long write1h = usage.cacheCreation().map(c -> c.ephemeral1hInputTokens()).orElse(0L);
      return (new ModelUsage()
         .withInputTokens(usage.inputTokens())
         .withOutputTokens(usage.outputTokens())
         .withCacheReadTokens(usage.cacheReadInputTokens().orElse(0L))
         .withCacheWriteTokens(usage.cacheCreationInputTokens().orElse(0L))
         .withCacheWrite1hTokens(write1h));
   }



   /*******************************************************************************
    ** A tool_use input as a plain map.
    *******************************************************************************/
   static Map<String, Object> inputMap(JsonValue input)
   {
      if(input == null)
      {
         return (new LinkedHashMap<>());
      }
      try
      {
         Map<String, Object> map = input.convert(new TypeReference<Map<String, Object>>() {});
         return (map == null ? new LinkedHashMap<>() : map);
      }
      catch(RuntimeException e)
      {
         return (new LinkedHashMap<>());
      }
   }



   /*******************************************************************************
    ** Whether a 400 message says the strict tool schemas are the problem:
    ** "The compiled grammar is too large ... reduce the number of strict tools"
    ** or "Schema is too complex for compilation".
    *******************************************************************************/
   static boolean isStrictSchemaMessage(String message)
   {
      if(message == null)
      {
         return (false);
      }
      String lower = message.toLowerCase(java.util.Locale.ROOT);
      return (lower.contains("grammar") || lower.contains("too complex for compilation") || lower.contains("strict tool"));
   }



   /*******************************************************************************
    ** The API's own error message ({"error": {"message": ...}}), or null. The
    ** caller sanitizes it; Anthropic's messages name parameters, not content.
    *******************************************************************************/
   static String errorMessage(AnthropicServiceException service)
   {
      try
      {
         Map<String, Object> body = service.body() == null ? null : service.body().convert(new TypeReference<Map<String, Object>>() {});
         if(body != null && body.get("error") instanceof Map<?, ?> error && error.get("message") instanceof String message)
         {
            return (message);
         }
      }
      catch(RuntimeException e)
      {
         //////////////////////////////////////////////
         // not JSON / not the documented error shape //
         //////////////////////////////////////////////
      }
      return (null);
   }



   /*******************************************************************************
    ** Map an SDK error to a provider error. The message holds the status, the
    ** API error type and the API's own (sanitized) error message - never request
    ** bodies, prompts or keys.
    *******************************************************************************/
   public static ModelProviderException toProviderException(Throwable throwable)
   {
      if(throwable instanceof AnthropicServiceException service)
      {
         int    status = service.statusCode();
         String type   = service.errorType().map(t -> t.asString()).orElse(null);

         ModelProviderException.Kind kind;
         if(status == 401 || status == 403)
         {
            kind = ModelProviderException.Kind.AUTHENTICATION;
         }
         else if(status == 429)
         {
            kind = ModelProviderException.Kind.RATE_LIMITED;
         }
         else if(status == 529 || "overloaded_error".equals(type))
         {
            kind = ModelProviderException.Kind.OVERLOADED;
         }
         else if(status >= 500)
         {
            kind = ModelProviderException.Kind.SERVER;
         }
         else if(status >= 400)
         {
            kind = ModelProviderException.Kind.INVALID_REQUEST;
         }
         else
         {
            kind = "overloaded_error".equals(type) ? ModelProviderException.Kind.OVERLOADED : ModelProviderException.Kind.OTHER;
         }
         String apiMessage = errorMessage(service);
         String requestId  = service.headers() == null ? null : service.headers().values("request-id").stream().findFirst().orElse(null);
         ModelProviderException exception = new ModelProviderException(kind, status >= 400 ? status : null, "Anthropic API error" + (status >= 400 ? " (HTTP " + status + ")" : "") + (type == null ? "" : " " + type)
            + (apiMessage == null ? "" : ": " + ModelProviderException.sanitizeProviderMessage(apiMessage, ModelProviderException.MAX_PROVIDER_MESSAGE_CHARS)));
         return (exception.withProviderDetails(type, apiMessage, requestId).withStrictSchemaRejected(status == 400 && isStrictSchemaMessage(apiMessage)));
      }
      if(throwable instanceof AnthropicIoException)
      {
         return (new ModelProviderException(ModelProviderException.Kind.NETWORK, null, "Could not reach the Anthropic API."));
      }
      return (new ModelProviderException(ModelProviderException.Kind.OTHER, null, "Anthropic call failed (" + throwable.getClass().getSimpleName() + ")."));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static CacheControlEphemeral cacheControl(CacheHint hint)
   {
      if(CacheHint.LONG.equals(hint))
      {
         return (CacheControlEphemeral.builder().ttl(CacheControlEphemeral.Ttl.TTL_1H).build());
      }
      if(CacheHint.SHORT.equals(hint))
      {
         return (CacheControlEphemeral.builder().ttl(CacheControlEphemeral.Ttl.TTL_5M).build());
      }
      return (null);
   }

}
