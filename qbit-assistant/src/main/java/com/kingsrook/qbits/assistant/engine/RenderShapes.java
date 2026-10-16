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
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** The shape clients rely on for tool result renders: their lists are always
 ** present, empty when there is nothing (a query that matched no rows has
 ** "rows": [] and "columns": []). QQQ's default JSON omits empty lists, so
 ** parts and stream events are written with {@link #toJson(Object)}, and parts
 ** stored before this fix are completed by {@link #complete(Map)} when read.
 *******************************************************************************/
public final class RenderShapes
{
   /** each render type's list fields */
   private static final Map<String, List<String>> LISTS = Map.of(
      "records", List.of("columns", "rows"),
      "record", List.of("fields"),
      "history", List.of("entries"));



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
    ** Fill in a part's render lists that are missing (in place); returns the part.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public static Map<String, Object> complete(Map<String, Object> part)
   {
      if(part != null && part.get("render") instanceof Map<?, ?> render)
      {
         completeRender((Map<String, Object>) render);
      }
      return (part);
   }



   /*******************************************************************************
    ** Fill in a render's missing lists (in place).
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public static void completeRender(Map<String, Object> render)
   {
      if(render == null || !(render.get("type") instanceof String type))
      {
         return;
      }
      for(String list : LISTS.getOrDefault(type, List.of()))
      {
         if(!(render.get(list) instanceof List))
         {
            render.put(list, new ArrayList<>());
         }
      }
      if("history".equals(type))
      {
         for(Object entry : (List<Object>) render.get("entries"))
         {
            if(entry instanceof Map<?, ?> map && !(map.get("details") instanceof List))
            {
               ((Map<String, Object>) map).put("details", new ArrayList<>());
            }
         }
      }
   }
}
