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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.provider.CacheHint;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelPart;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelToolDefinition;
import com.kingsrook.qbits.assistant.provider.SystemBlock;
import com.kingsrook.qbits.assistant.tools.KnowledgeTool;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolDefinition;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolKernel;
import com.kingsrook.qqq.backend.core.context.QContext;
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
   ///////////////////////////////////////////////////////////////////////
   // tools that read no records stay on the core kernel: navigate only //
   // builds an /app link (the page enforces permission when it opens)  //
   // and search_help reads help text from metadata. Every tool that    //
   // reads or writes data is a UI API tool (UiApiToolbox).             //
   ///////////////////////////////////////////////////////////////////////
   public static final Set<String> KERNEL_TOOLS = Set.of("navigate", "search_help");

   public static final int MAX_CONTEXT_CHARS = 512;
   public static final int MAX_SELECTED_IDS  = 100;

   private final AssistantQBitConfig config;
   private final AiToolKernel        kernel;
   private final UiApiToolbox        toolbox;
   private volatile String           appDigest;
   private KnowledgeTool knowledgeTool;



   /*******************************************************************************
    **
    *******************************************************************************/
   public ContextBuilder(AssistantQBitConfig config, AiToolKernel kernel, UiApiToolbox toolbox)
   {
      this.config = config;
      this.kernel = kernel;
      this.toolbox = toolbox;
   }



   /*******************************************************************************
    ** The tools with the default policy.
    *******************************************************************************/
   public List<ModelToolDefinition> tools()
   {
      return (tools(PolicyService.ToolPolicy.defaults()));
   }



   /*******************************************************************************
    ** The tools a policy allows, sorted by name (a stable cache prefix): the UI
    ** API data tools (write tools only when some table allows writes) plus the
    ** kernel's navigate and search_help.
    *******************************************************************************/
   public List<ModelToolDefinition> tools(PolicyService.ToolPolicy policy)
   {
      Map<String, ModelToolDefinition> byName = new TreeMap<>();
      for(ModelToolDefinition definition : toolbox.definitions(policy, toolbox.anyWritableTable()))
      {
         byName.put(definition.getName(), definition.withStrict(isStrict(definition.getName(), true)));
      }
      for(AiToolDefinition definition : kernel.getToolDefinitions())
      {
         if(KERNEL_TOOLS.contains(definition.getName()) && (policy == null || policy.allows(definition.getName())))
         {
            byName.put(definition.getName(), new ModelToolDefinition()
               .withName(definition.getName())
               .withDescription(definition.getDescription())
               .withInputSchema(definition.getInputSchema())
               .withStrict(isStrict(definition.getName(), !Boolean.FALSE.equals(definition.getStrict()))));
         }
      }
      if(knowledgeTool != null && knowledgeTool.offered(policy, QContext.getQSession()))
      {
         byName.put(KnowledgeTool.NAME, knowledgeTool.definition());
      }
      return (new ArrayList<>(byName.values()));
   }



   /*******************************************************************************
    ** Strict only when the host names the tool in strictToolNames: the provider
    ** compiles every strict schema of a request into one grammar, which it
    ** refuses when it grows too large.
    *******************************************************************************/
   boolean isStrict(String toolName, boolean strictCapable)
   {
      return (strictCapable && config.getStrictToolNames() != null && config.getStrictToolNames().contains(toolName));
   }



   /*******************************************************************************
    ** Names of the tools a policy allows.
    *******************************************************************************/
   public Set<String> toolNames(PolicyService.ToolPolicy policy)
   {
      Set<String> names = new HashSet<>();
      tools(policy).forEach(t -> names.add(t.getName()));
      return (names);
   }



   /*******************************************************************************
    ** Names of the tools with the default policy.
    *******************************************************************************/
   public Set<String> toolNames()
   {
      return (toolNames(PolicyService.ToolPolicy.defaults()));
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
      return (build(session, model, effort, history, userText, pageContext, PolicyService.ToolPolicy.defaults()));
   }



   /*******************************************************************************
    ** The request, with the tools the policy allows.
    *******************************************************************************/
   public ModelRequest build(QSession session, String model, String effort, List<AssistantMessage> history, String userText, Map<String, Object> pageContext, PolicyService.ToolPolicy toolPolicy)
   {
      return (build(session, model, effort, history, userText, pageContext, toolPolicy, null));
   }



   /*******************************************************************************
    ** The request, with the tools the policy allows and the persona / skill
    ** text (instructions only) after the user context.
    *******************************************************************************/
   public ModelRequest build(QSession session, String model, String effort, List<AssistantMessage> history, String userText, Map<String, Object> pageContext, PolicyService.ToolPolicy toolPolicy,
      String profileInstructions)
   {
      ModelRequest request = new ModelRequest()
         .withModel(model)
         .withEffort(effort)
         .withMaxTokens(config.getModels().getMainMaxOutputTokens())
         .withTools(tools(toolPolicy));

      request.getSystem().add(new SystemBlock(SystemPrompt.staticBlock(appDigest()), CacheHint.LONG));
      request.getSystem().add(new SystemBlock(userBlock(session) + (StringUtils.hasContent(profileInstructions) ? "\n" + profileInstructions : ""), CacheHint.SHORT));

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
    ** Page context as sent by the client - a hint, so it is trimmed hard:
    ** route / tableName / recordId as strings (or a number for recordId) of at
    ** most MAX_CONTEXT_CHARS, selectedIds as at most MAX_SELECTED_IDS such
    ** scalars, filter as an object - dropped (with selectedIds) when the whole
    ** context JSON exceeds 4000 characters. Anything else is dropped.
    *******************************************************************************/
   public static Map<String, Object> sanitizePageContext(Map<String, Object> raw)
   {
      Map<String, Object> clean = new LinkedHashMap<>();
      if(raw == null)
      {
         return (clean);
      }
      for(String key : List.of("route", "tableName", "recordId"))
      {
         Object value = scalar(raw.get(key));
         if(value != null)
         {
            clean.put(key, value);
         }
      }
      if(raw.get("filter") instanceof Map<?, ?> filter)
      {
         clean.put("filter", filter);
      }
      if(raw.get("selectedIds") instanceof List<?> ids)
      {
         List<Object> kept = new ArrayList<>();
         for(Object id : ids)
         {
            Object value = scalar(id);
            if(value != null && kept.size() < MAX_SELECTED_IDS)
            {
               kept.add(value);
            }
         }
         if(!kept.isEmpty())
         {
            clean.put("selectedIds", kept);
         }
      }

      if(JsonUtils.toJson(clean).length() > 4000)
      {
         clean.remove("filter");
         clean.remove("selectedIds");
      }
      return (clean);
   }



   /*******************************************************************************
    ** A string (trimmed, at most MAX_CONTEXT_CHARS) or a number; else null.
    *******************************************************************************/
   private static Object scalar(Object value)
   {
      if(value instanceof Number number)
      {
         return (number);
      }
      if(value instanceof String string && StringUtils.hasContent(string))
      {
         String trimmed = string.trim();
         return (trimmed.length() > MAX_CONTEXT_CHARS ? trimmed.substring(0, MAX_CONTEXT_CHARS) : trimmed);
      }
      return (null);
   }



   /*******************************************************************************
    ** Additive optional tool hook; UI API toolbox stays independently maintained.
    *******************************************************************************/
   public ContextBuilder withKnowledgeTool(KnowledgeTool knowledgeTool)
   {
      this.knowledgeTool = knowledgeTool;
      return (this);
   }

}
