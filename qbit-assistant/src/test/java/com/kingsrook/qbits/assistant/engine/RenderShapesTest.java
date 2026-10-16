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



   /*******************************************************************************
    ** One render through the normalizer, as a stored part would be read.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static Map<String, Object> renderOf(String renderJson)
   {
      List<Map<String, Object>> parts = MessageParts.fromJson("[{\"type\":\"tool\",\"name\":\"t\",\"render\":" + renderJson + "}]").list();
      assertThat(parts).hasSize(1);
      return ((Map<String, Object>) parts.get(0).get("render"));
   }



   /*******************************************************************************
    ** Every known render type gets its required lists and safe defaults.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void everyRenderTypeIsNormalized()
   {
      Map<String, Object> records = renderOf("{\"type\":\"records\",\"tableName\":\"t\",\"columns\":[{\"name\":\"a\"},\"junk\",{\"label\":\"no name\"}],\"rows\":[{\"id\":1},7],\"totalCount\":\"3\",\"truncated\":\"yes\"}");
      assertThat(records).containsEntry("tableLabel", "t").containsEntry("totalCount", 3L).containsEntry("truncated", false);
      assertThat((List<Map<String, Object>>) records.get("columns")).containsExactly(Map.of("name", "a", "label", "a"));
      assertThat((List<Map<String, Object>>) records.get("rows")).hasSize(1).first().satisfies(row -> assertThat(row.get("values")).isEqualTo(Map.of()));

      Map<String, Object> record = renderOf("{\"type\":\"record\",\"tableName\":\"t\",\"fields\":[{\"name\":\"n\",\"value\":5},{\"value\":\"orphan\"}]}");
      assertThat((List<Map<String, Object>>) record.get("fields")).containsExactly(Map.of("name", "n", "label", "n", "value", "5"));

      Map<String, Object> count = renderOf("{\"type\":\"count\",\"tableName\":\"t\",\"count\":\"many\"}");
      assertThat(count).containsEntry("tableLabel", "t").containsEntry("count", null);

      Map<String, Object> widget = renderOf("{\"type\":\"widget\",\"widgetName\":\"w\",\"data\":\"bad\"}");
      assertThat(widget).containsEntry("label", "w").containsEntry("data", Map.of());

      Map<String, Object> history = renderOf("{\"type\":\"history\",\"entries\":[{\"message\":\"x\",\"details\":\"bad\"},3]}");
      assertThat((List<Map<String, Object>>) history.get("entries")).containsExactly(Map.of("message", "x", "details", List.of()));

      Map<String, Object> pending = renderOf("{\"type\":\"pending_action\",\"actionId\":\"12\"}");
      assertThat(pending).containsEntry("actionId", 12L).containsEntry("summary", "").containsEntry("changes", List.of());

      assertThat(renderOf("{\"type\":\"bulk_load_dry_run\",\"okCount\":1}")).containsEntry("errors", List.of());
      assertThat(renderOf("{\"type\":\"records\"}")).containsEntry("rows", List.of()).containsEntry("columns", List.of()).containsEntry("tableLabel", "");
   }



   /*******************************************************************************
    ** Unknown render and part types pass through untouched.
    *******************************************************************************/
   @Test
   void unknownTypesPassThrough()
   {
      assertThat(renderOf("{\"type\":\"sparkline\",\"points\":\"whatever\"}")).isEqualTo(Map.of("type", "sparkline", "points", "whatever"));
      List<Map<String, Object>> parts = MessageParts.fromJson("[{\"type\":\"future_part\",\"x\":1}]").list();
      assertThat(parts).containsExactly(Map.of("type", "future_part", "x", 1));
   }



   /*******************************************************************************
    ** What cannot be repaired becomes the error notice; nothing throws.
    *******************************************************************************/
   @Test
   void unrepairableRendersBecomeNotices()
   {
      List<Map<String, Object>> parts = MessageParts.fromJson("[{\"type\":\"tool\",\"name\":\"t\",\"outcome\":\"OK\",\"render\":{\"type\":\"pending_action\"}},"
         + "{\"type\":\"tool\",\"name\":\"u\",\"render\":\"not an object\"},"
         + "42, {\"no\":\"type\"}, {\"type\":\"presentation\"},"
         + "{\"type\":\"navigate\",\"href\":\"https://evil.example\"}, {\"type\":\"notice\",\"level\":\"panic\"}]").list();
      Map<String, Object> notice = RenderShapes.undisplayableNotice();
      assertThat(parts).containsExactly(
         Map.of("type", "tool", "name", "t", "outcome", "OK", "summary", ""), notice,
         Map.of("type", "tool", "name", "u", "outcome", "ERROR", "summary", ""), notice,
         notice, notice, notice,
         Map.of("type", "notice", "level", "info", "text", ""));
      assertThat(notice).containsEntry("level", "error").containsEntry("text", RenderShapes.UNDISPLAYABLE_TEXT);
      assertThat(MessageParts.fromJson("{\"not\":\"a list\"}").list()).isEmpty();
      assertThat(RenderShapes.parts(null)).isEmpty();
      assertThat(RenderShapes.render(null)).isNull();
   }



   /*******************************************************************************
    ** Presentations: blocks keep their lists; a presentation with nothing to
    ** show is replaced (in parts and in the presentation event).
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void presentationsAreNormalized()
   {
      Map<String, Object> presentation = RenderShapes.presentation(Map.of("summary", "Sales", "blocks", List.of(
         Map.of("kind", "table", "columns", List.of(Map.of("key", "c0"), "junk"), "rows", List.of(Map.of("cells", "bad"))),
         Map.of("kind", "chart"), Map.of("kind", "metrics"), Map.of("no", "kind"), Map.of("kind", "future"))));
      assertThat(presentation).containsEntry("type", "presentation").containsEntry("markdown", "Sales");
      List<Map<String, Object>> blocks = (List<Map<String, Object>>) presentation.get("blocks");
      assertThat(blocks).hasSize(4);
      assertThat(blocks.get(0)).containsEntry("title", "").containsEntry("columns", List.of(Map.of("key", "c0", "label", "c0")))
         .containsEntry("rows", List.of(Map.of("cells", Map.of(), "display", Map.of())));
      assertThat(blocks.get(1)).containsEntry("chartType", "bar").containsEntry("points", List.of());
      assertThat(blocks.get(2)).containsEntry("items", List.of());
      assertThat(blocks.get(3)).isEqualTo(Map.of("kind", "future"));

      assertThat(RenderShapes.presentation(Map.of("blocks", List.of()))).isNull();
      TurnEvent event = new TurnEvent(4, TurnEvent.PRESENTATION, Map.of("part", Map.of("type", "presentation")));
      assertThat(event.getData().get("part")).isEqualTo(RenderShapes.undisplayableNotice());
      TurnEvent toolResult = TurnEvent.fromJson(5, TurnEvent.TOOL_RESULT, "{\"name\":\"t\",\"render\":{\"type\":\"pending_action\"}}");
      assertThat(toolResult.getData()).doesNotContainKey("render").containsEntry("name", "t");
      assertThat(TurnEvent.fromJson(6, TurnEvent.TOOL_RESULT, "not json").getData()).containsOnlyKeys("seq");
   }
}
