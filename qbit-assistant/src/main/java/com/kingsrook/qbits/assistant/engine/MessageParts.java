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


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The UI parts of a message (contract section 3 "Part"), as stored in
 ** assistantMessage.partsJson and returned by the API:
 **   {type:"text", text} | {type:"tool", toolCallId, name, summary, outcome, render?}
 **   | {type:"record_refs", refs:[...]} | {type:"navigate", href}
 **   | {type:"notice", level, text} | {type:"presentation", summary, blocks, markdown}
 ** Consecutive text is merged into one part. Thread-safe.
 *******************************************************************************/
public class MessageParts
{
   private final List<Map<String, Object>> parts = new ArrayList<>();



   /*******************************************************************************
    ** Parts read back from JSON (empty for null / bad JSON).
    *******************************************************************************/
   public static MessageParts fromJson(String json)
   {
      MessageParts result = new MessageParts();
      if(StringUtils.hasContent(json))
      {
         try
         {
            List<Map<String, Object>> list = JsonUtils.toObject(json, new TypeReference<List<Map<String, Object>>>() {});
            if(list != null)
            {
               result.parts.addAll(list);
            }
         }
         catch(Exception e)
         {
            ///////////////////////////////////////
            // unreadable parts: show no content //
            ///////////////////////////////////////
         }
      }
      return (result);
   }



   /*******************************************************************************
    ** Append text, merging with a trailing text part.
    *******************************************************************************/
   public synchronized MessageParts addText(String text)
   {
      if(text == null || text.isEmpty())
      {
         return (this);
      }
      if(!parts.isEmpty() && "text".equals(parts.get(parts.size() - 1).get("type")))
      {
         Map<String, Object> last = parts.get(parts.size() - 1);
         last.put("text", last.get("text") + text);
      }
      else
      {
         parts.add(part("text", "text", text));
      }
      return (this);
   }



   /*******************************************************************************
    ** Append a tool step.
    *******************************************************************************/
   public synchronized MessageParts addTool(String toolCallId, String name, String summary, String outcome, Map<String, Object> render)
   {
      Map<String, Object> part = part("tool", "toolCallId", toolCallId);
      part.put("name", name);
      part.put("summary", summary);
      part.put("outcome", outcome);
      if(render != null)
      {
         part.put("render", render);
      }
      parts.add(part);
      return (this);
   }



   /*******************************************************************************
    ** Append a server-built presentation, one part per successful tool call.
    *******************************************************************************/
   public synchronized MessageParts addPresentation(Map<String, Object> presentation)
   {
      parts.add(new LinkedHashMap<>(presentation));
      return (this);
   }



   /*******************************************************************************
    ** Append record references.
    *******************************************************************************/
   public synchronized MessageParts addRecordRefs(List<Map<String, Object>> refs)
   {
      if(refs != null && !refs.isEmpty())
      {
         parts.add(part("record_refs", "refs", refs));
      }
      return (this);
   }



   /*******************************************************************************
    ** Append a navigation.
    *******************************************************************************/
   public synchronized MessageParts addNavigate(String href)
   {
      parts.add(part("navigate", "href", href));
      return (this);
   }



   /*******************************************************************************
    ** Append a notice (info / warning / error).
    *******************************************************************************/
   public synchronized MessageParts addNotice(String level, String text)
   {
      Map<String, Object> part = part("notice", "level", level);
      part.put("text", text);
      parts.add(part);
      return (this);
   }



   /*******************************************************************************
    ** Remove record data (retention): the render payloads of tool parts and
    ** the record_refs and presentation parts. Tool summaries, text, navigation and notices
    ** stay. Returns whether anything was removed.
    *******************************************************************************/
   public synchronized boolean stripRecordData()
   {
      boolean changed = parts.removeIf(p -> ("record_refs".equals(p.get("type")) || "presentation".equals(p.get("type"))));
      for(Map<String, Object> part : parts)
      {
         if("tool".equals(part.get("type")) && part.remove("render") != null)
         {
            changed = true;
         }
      }
      return (changed);
   }



   /*******************************************************************************
    ** Text and presentation fallbacks joined (for model history and titles).
    *******************************************************************************/
   public synchronized String text()
   {
      StringBuilder builder = new StringBuilder();
      for(Map<String, Object> part : parts)
      {
         if("text".equals(part.get("type")) && part.get("text") != null)
         {
            builder.append(part.get("text"));
         }
         else if("presentation".equals(part.get("type")))
         {
            builder.append("\n").append(part.get("markdown")).append("\n");
         }
      }
      return (builder.toString());
   }



   /*******************************************************************************
    ** One line per tool step ("[looked up: Queried Blog Posts · 4 rows]"), for
    ** the model's view of earlier turns (whose tool results are cleared).
    *******************************************************************************/
   public synchronized String toolSummaries()
   {
      StringBuilder builder = new StringBuilder();
      for(Map<String, Object> part : parts)
      {
         if("tool".equals(part.get("type")))
         {
            builder.append("[earlier step ").append(part.get("name")).append(": ").append(part.get("summary")).append(" (").append(part.get("outcome")).append(")]\n");
         }
      }
      return (builder.toString());
   }



   /*******************************************************************************
    ** A copy of the parts.
    *******************************************************************************/
   public synchronized List<Map<String, Object>> list()
   {
      List<Map<String, Object>> copy = new ArrayList<>();
      for(Map<String, Object> part : parts)
      {
         copy.add(new LinkedHashMap<>(part));
      }
      return (copy);
   }



   /*******************************************************************************
    ** JSON for partsJson.
    *******************************************************************************/
   public synchronized String toJson()
   {
      return (JsonUtils.toJson(parts));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Object> part(String type, String key, Object value)
   {
      Map<String, Object> part = new LinkedHashMap<>();
      part.put("type", type);
      part.put(key, value);
      return (part);
   }

}
