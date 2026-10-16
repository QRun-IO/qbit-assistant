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
import java.util.Set;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The shapes clients rely on for message parts, tool result renders and
 ** presentations (extension "assistant", AssistantMessageV1 parts and the
 ** tool.result / presentation events). Every part, render and presentation
 ** is checked before it is persisted or streamed, and again when stored
 ** parts and events are read back (older rows are repaired on read):
 **
 ** - required lists are always present, empty when there is nothing (a query
 **   that matched no rows has "rows": [] and "columns": []);
 ** - list entries that are not objects are dropped, required strings are
 **   filled with safe defaults;
 ** - a render that cannot be repaired (no type, a pending action without an
 **   action id, a presentation with nothing to show) is dropped and a notice
 **   part ({@link #UNDISPLAYABLE_TEXT}) takes its place;
 ** - unknown part and render types pass through untouched.
 **
 ** Problems are logged with the render type and a fixed problem code only;
 ** never with values. QQQ's default JSON omits empty lists, so parts and
 ** stream events are written with {@link #toJson(Object)}.
 *******************************************************************************/
public final class RenderShapes
{
   private static final QLogger LOG = QLogger.getLogger(RenderShapes.class);

   /** what the user sees instead of a result that cannot be shown */
   public static final String UNDISPLAYABLE_TEXT = "This result could not be displayed.";

   private static final Pattern    SAFE_TYPE     = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
   private static final Set<String> NOTICE_LEVELS = Set.of("info", "warning", "error");
   private static final Set<String> ITEM_BLOCKS   = Set.of("metrics", "filterLinks", "recordLinks", "actions");



   /*******************************************************************************
    ** not instantiable
    *******************************************************************************/
   private RenderShapes()
   {
   }



   /*******************************************************************************
    ** JSON that keeps empty lists (nulls are still left out).
    *******************************************************************************/
   public static String toJson(Object value)
   {
      return (JsonUtils.toJsonCustomized(value, builder -> builder.serializationInclusion(JsonInclude.Include.NON_NULL)));
   }



   /*******************************************************************************
    ** The notice part shown in place of a part or render that cannot be shown.
    *******************************************************************************/
   public static Map<String, Object> undisplayableNotice()
   {
      Map<String, Object> notice = new LinkedHashMap<>();
      notice.put("type", "notice");
      notice.put("level", "error");
      notice.put("text", UNDISPLAYABLE_TEXT);
      return (notice);
   }



   /*******************************************************************************
    ** Normalized copies of message parts (never throws; never null). A part
    ** that is not an object, or has no type, becomes an error notice; a tool
    ** part whose render cannot be repaired loses its render and is followed by
    ** an error notice; unknown part types pass through.
    *******************************************************************************/
   public static List<Map<String, Object>> parts(Object parts)
   {
      List<Map<String, Object>> result = new ArrayList<>();
      if(!(parts instanceof List<?> list))
      {
         return (result);
      }
      for(Object entry : list)
      {
         try
         {
            addPart(result, entry);
         }
         catch(RuntimeException e)
         {
            problem(entry instanceof Map<?, ?> map ? map.get("type") : null, "part-exception");
            result.add(undisplayableNotice());
         }
      }
      return (result);
   }



   /*******************************************************************************
    ** Normalize one part into the result.
    *******************************************************************************/
   private static void addPart(List<Map<String, Object>> result, Object entry)
   {
      if(!(entry instanceof Map<?, ?> map) || !(map.get("type") instanceof String type))
      {
         problem(null, "part-without-type");
         result.add(undisplayableNotice());
         return;
      }
      Map<String, Object> part = copy(map);
      switch(type)
      {
         case "text" -> part.put("text", string(part.get("text"), ""));
         case "notice" ->
         {
            part.put("level", part.get("level") instanceof String level && NOTICE_LEVELS.contains(level) ? level : "info");
            part.put("text", string(part.get("text"), ""));
         }
         case "navigate" ->
         {
            if(!(part.get("href") instanceof String href) || !TurnEngine.isAppHref(href))
            {
               problem(type, "navigate-without-app-href");
               return;
            }
         }
         case "record_refs" -> part.put("refs", objects(part.get("refs")));
         case "tool" ->
         {
            part.put("name", string(part.get("name"), "tool"));
            part.put("summary", string(part.get("summary"), ""));
            part.put("outcome", string(part.get("outcome"), "ERROR"));
            if(part.containsKey("render"))
            {
               Map<String, Object> render = render(part.get("render"));
               if(render == null)
               {
                  part.remove("render");
                  result.add(part);
                  result.add(undisplayableNotice());
                  return;
               }
               part.put("render", render);
            }
         }
         case "presentation" ->
         {
            Map<String, Object> presentation = presentation(part);
            result.add(presentation == null ? undisplayableNotice() : presentation);
            return;
         }
         default ->
         {
            //////////////////////////////////////////////////
            // unknown part types pass through untouched    //
            //////////////////////////////////////////////////
         }
      }
      result.add(part);
   }



   /*******************************************************************************
    ** A normalized copy of a tool result render, or null when it cannot be
    ** repaired (the problem is logged without values). Unknown render types
    ** pass through as a shallow copy.
    *******************************************************************************/
   public static Map<String, Object> render(Object value)
   {
      try
      {
         if(!(value instanceof Map<?, ?> map) || !(map.get("type") instanceof String type))
         {
            problem(null, "render-without-type");
            return (null);
         }
         Map<String, Object> render = copy(map);
         switch(type)
         {
            case "records" ->
            {
               tableLabel(render);
               List<Map<String, Object>> columns = new ArrayList<>();
               for(Map<String, Object> column : objects(render.get("columns")))
               {
                  if(column.get("name") instanceof String name)
                  {
                     column.put("label", string(column.get("label"), name));
                     columns.add(column);
                  }
               }
               render.put("columns", columns);
               List<Map<String, Object>> rows = objects(render.get("rows"));
               for(Map<String, Object> row : rows)
               {
                  row.put("values", row.get("values") instanceof Map<?, ?> values ? copy(values) : new LinkedHashMap<>());
               }
               render.put("rows", rows);
               render.put("totalCount", number(render.get("totalCount")));
               render.put("truncated", Boolean.TRUE.equals(render.get("truncated")));
            }
            case "record" ->
            {
               tableLabel(render);
               List<Map<String, Object>> fields = new ArrayList<>();
               for(Map<String, Object> field : objects(render.get("fields")))
               {
                  if(field.get("name") instanceof String name)
                  {
                     field.put("label", string(field.get("label"), name));
                     Object fieldValue = field.get("value");
                     field.put("value", fieldValue == null || fieldValue instanceof String ? fieldValue : String.valueOf(fieldValue));
                     fields.add(field);
                  }
               }
               render.put("fields", fields);
            }
            case "count" ->
            {
               tableLabel(render);
               render.put("count", number(render.get("count")));
            }
            case "widget" ->
            {
               render.put("label", string(render.get("label"), string(render.get("widgetName"), "Widget")));
               render.put("data", render.get("data") instanceof Map<?, ?> data ? copy(data) : new LinkedHashMap<>());
            }
            case "history" ->
            {
               List<Map<String, Object>> entries = objects(render.get("entries"));
               for(Map<String, Object> entry : entries)
               {
                  entry.put("details", objects(entry.get("details")));
               }
               render.put("entries", entries);
            }
            case "pending_action" ->
            {
               Object actionId = number(render.get("actionId"));
               if(actionId == null)
               {
                  problem(type, "pending-action-without-id");
                  return (null);
               }
               render.put("actionId", actionId);
               render.put("summary", string(render.get("summary"), ""));
               render.put("changes", objects(render.get("changes")));
            }
            case "bulk_load_dry_run" -> render.put("errors", objects(render.get("errors")));
            default ->
            {
               //////////////////////////////////////////////////
               // unknown render types pass through untouched  //
               //////////////////////////////////////////////////
            }
         }
         return (render);
      }
      catch(RuntimeException e)
      {
         problem(value instanceof Map<?, ?> map ? map.get("type") : null, "render-exception");
         return (null);
      }
   }



   /*******************************************************************************
    ** A normalized copy of a presentation part, or null when it has nothing to
    ** show. Blocks that are not objects or have no kind are dropped; table,
    ** chart and item blocks always carry their lists.
    *******************************************************************************/
   public static Map<String, Object> presentation(Object value)
   {
      try
      {
         if(!(value instanceof Map<?, ?> map))
         {
            problem("presentation", "presentation-not-object");
            return (null);
         }
         Map<String, Object> part = copy(map);
         part.put("type", "presentation");
         part.put("summary", string(part.get("summary"), ""));
         List<Map<String, Object>> blocks = new ArrayList<>();
         for(Map<String, Object> block : objects(part.get("blocks")))
         {
            if(!(block.get("kind") instanceof String kind))
            {
               problem("presentation", "block-without-kind");
               continue;
            }
            if("table".equals(kind))
            {
               block.put("title", string(block.get("title"), ""));
               List<Map<String, Object>> columns = new ArrayList<>();
               for(Map<String, Object> column : objects(block.get("columns")))
               {
                  if(column.get("key") instanceof String key)
                  {
                     column.put("label", string(column.get("label"), key));
                     columns.add(column);
                  }
               }
               block.put("columns", columns);
               List<Map<String, Object>> rows = objects(block.get("rows"));
               for(Map<String, Object> row : rows)
               {
                  row.put("cells", row.get("cells") instanceof Map<?, ?> cells ? copy(cells) : new LinkedHashMap<>());
                  row.put("display", row.get("display") instanceof Map<?, ?> display ? copy(display) : new LinkedHashMap<>());
               }
               block.put("rows", rows);
            }
            else if("chart".equals(kind))
            {
               block.put("title", string(block.get("title"), ""));
               block.put("chartType", string(block.get("chartType"), "bar"));
               block.put("points", objects(block.get("points")));
            }
            else if(ITEM_BLOCKS.contains(kind))
            {
               block.put("items", objects(block.get("items")));
            }
            blocks.add(block);
         }
         part.put("blocks", blocks);
         part.put("markdown", string(part.get("markdown"), (String) part.get("summary")));
         if(blocks.isEmpty() && ((String) part.get("summary")).isBlank() && ((String) part.get("markdown")).isBlank())
         {
            problem("presentation", "presentation-empty");
            return (null);
         }
         return (part);
      }
      catch(RuntimeException e)
      {
         problem("presentation", "presentation-exception");
         return (null);
      }
   }



   /*******************************************************************************
    ** Normalize an event's data in place: a tool.result render that cannot be
    ** repaired is removed; a presentation event's part that cannot be shown is
    ** replaced by the error notice part.
    *******************************************************************************/
   public static void normalizeEvent(String type, Map<String, Object> data)
   {
      if(data == null)
      {
         return;
      }
      if(data.containsKey("render"))
      {
         Map<String, Object> render = render(data.get("render"));
         if(render == null)
         {
            data.remove("render");
         }
         else
         {
            data.put("render", render);
         }
      }
      if(TurnEvent.PRESENTATION.equals(type) && data.containsKey("part"))
      {
         Map<String, Object> part = presentation(data.get("part"));
         data.put("part", part == null ? undisplayableNotice() : part);
      }
   }



   /*******************************************************************************
    ** Fill in a single part (in place) - kept for callers of the 1.4.0 helper.
    ** A render that cannot be repaired is removed.
    *******************************************************************************/
   public static Map<String, Object> complete(Map<String, Object> part)
   {
      if(part != null)
      {
         normalizeEvent(null, part);
      }
      return (part);
   }



   /*******************************************************************************
    ** tableLabel defaults to the table name.
    *******************************************************************************/
   private static void tableLabel(Map<String, Object> render)
   {
      render.put("tableLabel", string(render.get("tableLabel"), string(render.get("tableName"), "")));
   }



   /*******************************************************************************
    ** Copies of the object entries of a list (others dropped); empty when the
    ** value is not a list.
    *******************************************************************************/
   private static List<Map<String, Object>> objects(Object value)
   {
      List<Map<String, Object>> result = new ArrayList<>();
      if(value instanceof List<?> list)
      {
         for(Object entry : list)
         {
            if(entry instanceof Map<?, ?> map)
            {
               result.add(copy(map));
            }
         }
      }
      return (result);
   }



   /*******************************************************************************
    ** A mutable copy with string keys.
    *******************************************************************************/
   private static Map<String, Object> copy(Map<?, ?> map)
   {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((key, value) -> copy.put(String.valueOf(key), value));
      return (copy);
   }



   /*******************************************************************************
    ** The value when it is a string; otherwise the default.
    *******************************************************************************/
   private static String string(Object value, String defaultValue)
   {
      return (value instanceof String s ? s : defaultValue);
   }



   /*******************************************************************************
    ** A number (numeric strings are converted), or null.
    *******************************************************************************/
   private static Number number(Object value)
   {
      if(value instanceof Number n)
      {
         return (n);
      }
      if(value instanceof String s && s.matches("-?\\d{1,18}"))
      {
         return (Long.parseLong(s));
      }
      return (null);
   }



   /*******************************************************************************
    ** Log a shape problem: the type (when it looks like a type) and a fixed
    ** code, never values.
    *******************************************************************************/
   private static void problem(Object type, String code)
   {
      String safeType = type instanceof String s && SAFE_TYPE.matcher(s).matches() ? s : null;
      LOG.warn("Assistant part or render was not displayable", logPair("renderType", safeType), logPair("problem", code));
   }
}
