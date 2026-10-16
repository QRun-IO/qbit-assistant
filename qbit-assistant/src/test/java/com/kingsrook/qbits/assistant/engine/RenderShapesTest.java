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
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Records renders keep their (empty) lists in stored parts and stream events,
 ** and parts stored without them are completed when read.
 *******************************************************************************/
class RenderShapesTest
{

   /*******************************************************************************
    ** A query that matched nothing.
    *******************************************************************************/
   private static Map<String, Object> emptyRecordsPart()
   {
      Map<String, Object> render = new LinkedHashMap<>();
      render.put("type", "records");
      render.put("tableName", "db_perf_finding");
      render.put("tableLabel", "Findings");
      render.put("columns", new ArrayList<>());
      render.put("rows", new ArrayList<>());
      render.put("totalCount", 0);
      render.put("truncated", false);
      render.put("openHref", "/app/db_perf_finding");
      Map<String, Object> part = new LinkedHashMap<>();
      part.put("type", "tool");
      part.put("name", "query_records");
      part.put("render", render);
      return (part);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void emptyListsSurviveSerialization()
   {
      String json = RenderShapes.toJson(List.of(emptyRecordsPart()));
      assertThat(json).contains("\"columns\":[]").contains("\"rows\":[]");
      assertThat(new TurnEvent(1, "tool.result", emptyRecordsPart()).toJson()).contains("\"rows\":[]").contains("\"columns\":[]");
   }



   /*******************************************************************************
    ** The exact shape stored on kof22 before the fix (lists omitted).
    *******************************************************************************/
   @Test
   void partsStoredWithoutListsAreCompletedWhenRead()
   {
      String stored = "[{\"type\":\"tool\",\"name\":\"query_records\",\"outcome\":\"OK\",\"render\":{\"type\":\"records\",\"tableName\":\"db_perf_finding\",\"tableLabel\":\"Findings\",\"openHref\":\"/app/db_perf_finding\",\"totalCount\":0,\"truncated\":false}},"
         + "{\"type\":\"tool\",\"name\":\"history\",\"render\":{\"type\":\"history\",\"entries\":[{\"message\":\"Edited\"}]}}]";
      List<Map<String, Object>> parts = MessageParts.fromJson(stored).list();
      @SuppressWarnings("unchecked")
      Map<String, Object> records = (Map<String, Object>) parts.get(0).get("render");
      assertThat(records.get("rows")).isEqualTo(List.of());
      assertThat(records.get("columns")).isEqualTo(List.of());
      @SuppressWarnings("unchecked")
      Map<String, Object> history = (Map<String, Object>) parts.get(1).get("render");
      @SuppressWarnings("unchecked")
      Map<String, Object> entry = ((List<Map<String, Object>>) history.get("entries")).get(0);
      assertThat(entry.get("details")).isEqualTo(List.of());

      Map<String, Object> event = TurnEvent.fromJson(3, "tool.result", "{\"render\":{\"type\":\"records\",\"tableLabel\":\"Findings\"}}").getData();
      @SuppressWarnings("unchecked")
      Map<String, Object> eventRender = (Map<String, Object>) event.get("render");
      assertThat(eventRender.get("rows")).isEqualTo(List.of());
   }
}
