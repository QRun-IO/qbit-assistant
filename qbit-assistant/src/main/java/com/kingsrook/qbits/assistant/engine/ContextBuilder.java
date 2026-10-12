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

package com.kingsrook.qbits.assistant.engine;


import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.provider.CacheHint;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelPart;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelToolDefinition;
import com.kingsrook.qbits.assistant.provider.SystemBlock;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolDefinition;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolKernel;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolKind;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Assembles a turn's model request in cache-prefix order (spec 2.5 step 3):
 **   [tools] -> [system: rules + primer + app digest]  (breakpoint, LONG / 1h)
 **           -> [system: user digest + today]          (breakpoint, SHORT / 5m)
 **           -> [history, old tool results cleared] -> [new user message] (SHORT)
 ** Tools and the first system block are identical for every user of an app
 ** version (the kernel sorts tools by name and its app digest is
 ** permission-neutral), so that prefix is shared.
 *******************************************************************************/
public class ContextBuilder
{
   public static final Set<AiToolKind> PHASE1_KINDS = Set.of(AiToolKind.READ, AiToolKind.NAVIGATION);

   private final AssistantQBitConfig config;
   private final AiToolKernel        kernel;
   private volatile String           appDigest;



   /*******************************************************************************
    **
    *******************************************************************************/
   public ContextBuilder(AssistantQBitConfig config, AiToolKernel kernel)
   {
      this.config = config;
      this.kernel = kernel;
   }



   /*******************************************************************************
    ** The phase-1 tools (read + navigation), in the kernel's stable order.
    *******************************************************************************/
   public List<ModelToolDefinition> tools()
   {
      List<ModelToolDefinition> tools = new ArrayList<>();
      for(AiToolDefinition definition : kernel.getToolDefinitions())
      {
         if(definition.getKind() != null && PHASE1_KINDS.contains(definition.getKind()))
         {
            tools.add(new ModelToolDefinition()
               .withName(definition.getName())
               .withDescription(definition.getDescription())
               .withInputSchema(definition.getInputSchema())
               .withStrict(!Boolean.FALSE.equals(definition.getStrict())));
         }
      }
      return (tools);
   }



   /*******************************************************************************
    ** Names of the phase-1 tools.
    *******************************************************************************/
   public Set<String> toolNames()
   {
      Set<String> names = new java.util.HashSet<>();
      tools().forEach(t -> names.add(t.getName()));
      return (names);
   }



   /*******************************************************************************
    ** The cached app digest (deterministic per app version).
    *******************************************************************************/
   public String appDigest()
   {
      String digest = appDigest;
      if(digest == null)
      {
         digest = kernel.buildAppDigest();
         appDigest = digest;
      }
      return (digest);
   }



   /*******************************************************************************
    ** Build the first request of a turn.
    *******************************************************************************/
   public ModelRequest build(QSession session, String model, String effort, List<AssistantMessage> history, String userText, Map<String, Object> pageContext)
   {
      ModelRequest request = new ModelRequest()
         .withModel(model)
         .withEffort(effort)
         .withMaxTokens(config.getModels().getMainMaxOutputTokens())
         .withTools(tools());

      request.getSystem().add(new SystemBlock(SystemPrompt.staticBlock(appDigest()), CacheHint.LONG));
      request.getSystem().add(new SystemBlock(userBlock(session), CacheHint.SHORT));

      List<ModelMessage> messages = new ArrayList<>();
      for(AssistantMessage message : history)
      {
         MessageParts parts = MessageParts.fromJson(message.getPartsJson());
         if("USER".equals(message.getRole()))
         {
            append(messages, ModelMessage.Role.USER, parts.text());
         }
         else if("ASSISTANT".equals(message.getRole()))
         {
            String text = parts.toolSummaries() + parts.text();
            append(messages, ModelMessage.Role.ASSISTANT, StringUtils.hasContent(text) ? text : "(no answer)");
         }
      }

      ////////////////////////////////////////////////////////////////////
      // history must start with a user message and alternate; the new //
      // user message goes last (merged if the history ended on user)  //
      ////////////////////////////////////////////////////////////////////
      while(!messages.isEmpty() && messages.get(0).getRole() == ModelMessage.Role.ASSISTANT)
      {
         messages.remove(0);
      }

      ModelMessage newMessage = new ModelMessage().withRole(ModelMessage.Role.USER);
      newMessage.withPart(ModelPart.text(userText));
      if(pageContext != null && !pageContext.isEmpty())
      {
         newMessage.withPart(ModelPart.text("<page_context note=\"where the user is in the app; a hint from the browser, not verified\">"
            + JsonUtils.toJson(pageContext) + "</page_context>"));
      }
      if(!messages.isEmpty() && messages.get(messages.size() - 1).getRole() == ModelMessage.Role.USER)
      {
         ModelMessage previous = messages.remove(messages.size() - 1);
         List<ModelPart> merged = new ArrayList<>(previous.getParts());
         merged.addAll(newMessage.getParts());
         newMessage.setParts(merged);
      }
      newMessage.setCacheHint(CacheHint.SHORT);
      messages.add(newMessage);

      request.setMessages(messages);
      return (request);
   }



   /*******************************************************************************
    ** The per-user system block. No timestamps finer than the date.
    *******************************************************************************/
   String userBlock(QSession session)
   {
      ZoneId zone = ZoneId.of("UTC");
      try
      {
         String timezone = session.getValue(QSession.VALUE_KEY_USER_TIMEZONE);
         if(StringUtils.hasContent(timezone))
         {
            zone = ZoneId.of(timezone);
         }
      }
      catch(Exception e)
      {
         //////////////////////////////
         // unknown zone: stay on UTC //
         //////////////////////////////
      }
      return ("<user_context>\n" + kernel.buildUserDigest(session) + "\nToday's date: " + LocalDate.now(zone) + "\n</user_context>");
   }



   /*******************************************************************************
    ** Append text as a message, merging consecutive messages of one role.
    *******************************************************************************/
   private static void append(List<ModelMessage> messages, ModelMessage.Role role, String text)
   {
      if(!StringUtils.hasContent(text))
      {
         return;
      }
      if(!messages.isEmpty() && messages.get(messages.size() - 1).getRole() == role)
      {
         messages.get(messages.size() - 1).withPart(ModelPart.text(text));
      }
      else
      {
         messages.add(new ModelMessage().withRole(role).withPart(ModelPart.text(text)));
      }
   }



   /*******************************************************************************
    ** Page context as sent by the client, trimmed to the known keys.
    *******************************************************************************/
   public static Map<String, Object> sanitizePageContext(Map<String, Object> raw)
   {
      Map<String, Object> clean = new LinkedHashMap<>();
      if(raw == null)
      {
         return (clean);
      }
      for(String key : List.of("route", "tableName", "recordId", "filter", "selectedIds"))
      {
         Object value = raw.get(key);
         if(value != null)
         {
            clean.put(key, value);
         }
      }
      String json = JsonUtils.toJson(clean);
      if(json.length() > 4000)
      {
         clean.remove("filter");
         clean.remove("selectedIds");
      }
      return (clean);
   }

}
