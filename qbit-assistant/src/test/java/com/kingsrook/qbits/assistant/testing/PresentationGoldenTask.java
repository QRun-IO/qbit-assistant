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

package com.kingsrook.qbits.assistant.testing;


import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.provider.ModelPart;
import com.kingsrook.qbits.assistant.provider.ModelResponse;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import static com.kingsrook.qbits.assistant.testing.GoldenTasks.map;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Owner's image breakdown, shared by deterministic and live provider evals.
 *******************************************************************************/
public class PresentationGoldenTask
{
   /*******************************************************************************
    ** Counts come from UI API tool results, including the omitted zero types.
    *******************************************************************************/
   public static GoldenTasks.Task task()
   {
      return (new GoldenTasks.Task("images-by-type-presentation", AssistantTestFixture.EDITOR, "How many images do I have by type?", provider ->
      {
         List<Object[]> calls = new ArrayList<>();
         for(String mime : List.of("image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml"))
         {
            calls.add(new Object[] {"count_records", map("table", "media", "filter", GoldenTasks.filter("AND", GoldenTasks.criterion("mimeType", "EQUALS", mime)))});
         }
         provider.thenToolUses(null, calls).thenRespond(request ->
         {
            List<ModelPart> results = EvalSupport.lastToolResults(request);
            Integer jpeg = ((Number) EvalSupport.unwrap(results.get(0).getText()).get("count")).intValue();
            Integer png = ((Number) EvalSupport.unwrap(results.get(1).getText()).get("count")).intValue();
            for(Integer i = 2; i < results.size(); i++)
            {
               assertThat(((Number) EvalSupport.unwrap(results.get(i).getText()).get("count")).intValue()).isZero();
            }
            return (new ModelResponse().withParts(List.of(ModelPart.toolUse("present", "present_results", input(jpeg, png)))).withStopReason(ModelResponse.STOP_TOOL_USE));
         }).thenText("");
      }, PresentationGoldenTask::check, PresentationGoldenTask::check));
   }



   /*******************************************************************************
    ** The presentation input, deliberately using null percentage placeholders.
    *******************************************************************************/
   public static Map<String, Object> input(Integer jpeg, Integer png)
   {
      return (map("summary", (jpeg + png) + " media files are images: " + jpeg + " JPEG and " + png + " PNG.",
         "metrics", List.of(map("label", "Images", "value", jpeg + png)),
         "table", map("title", "Media by type", "columns", List.of(map("label", "Type"), map("label", "Count"), map("label", "Share", "percentOfColumn", 1)),
            "rows", List.of(java.util.Arrays.asList("JPEG", jpeg, null), java.util.Arrays.asList("PNG", png, null)),
            "rowFilter", map("tableName", "media", "fieldName", "mimeType"), "rowKeys", List.of("image/jpeg", "image/png")),
         "chart", map("chartType", "pie", "title", "Share by type", "labelColumn", 0, "valueColumn", 1),
         "filterLinks", List.of(filterLink("JPEG", "image/jpeg"), filterLink("PNG", "image/png"))));
   }



   /*******************************************************************************
    ** A raw MIME criterion for the presentation tool.
    *******************************************************************************/
   private static Map<String, Object> filterLink(String label, String mime)
   {
      return (map("label", "Open " + label + " media", "tableName", "media", "criteria", List.of(map("fieldName", "mimeType", "operator", "EQUALS", "values", List.of(mime)))));
   }



   /*******************************************************************************
    ** Check actual block semantics, allowing a live model to choose labels and
    ** column order. Values, shares and filter criteria must remain exact.
    *******************************************************************************/
   public static void check(TurnRunner.Outcome outcome)
   {
      assertThat(outcome.end()).containsEntry("status", "COMPLETE");
      assertThat(outcome.toolCalls()).anyMatch(call -> "count_records".equals(call.getToolName()) && "OK".equals(call.getOutcome()));
      assertThat(outcome.events(TurnEvent.PRESENTATION)).hasSize(1);
      Map<String, Object> part = object(outcome.events(TurnEvent.PRESENTATION).get(0).get("part"));
      assertPart(part);
   }



   /*******************************************************************************
    ** Also used against the persisted part, not only the streamed event.
    *******************************************************************************/
   public static void assertPart(Map<String, Object> part)
   {
      assertThat(part.get("type")).isEqualTo("presentation");
      assertThat((String) part.get("summary")).isNotBlank().doesNotContain("\n").hasSizeLessThanOrEqualTo(300);
      assertThat((String) part.get("markdown")).contains("\n|");
      Map<String, Object> table = block(part, "table");
      List<?> rows = (List<?>) table.get("rows");
      assertThat(rows).hasSize(2);
      assertThat(rows).anySatisfy(row -> assertThat(object(object(row).get("display")).values()).contains("81", "80.2%"));
      assertThat(rows).anySatisfy(row -> assertThat(object(object(row).get("display")).values()).contains("20", "19.8%"));
      Map<String, Object> chart = block(part, "chart");
      assertThat(chart.get("chartType")).isEqualTo("pie");
      List<?> points = (List<?>) chart.get("points");
      assertThat(points).hasSize(2);
      assertThat(points.stream().map(p -> ((Number) object(p).get("value")).intValue()).toList()).containsExactlyInAnyOrder(81, 20);
      List<?> links = (List<?>) block(part, "filterLinks").get("items");
      assertThat(links).hasSize(2);
      List<Object> mimes = new ArrayList<>();
      for(Object entry : links)
      {
         String href = (String) object(entry).get("href");
         assertThat(href).startsWith("/app/media?filter=");
         try
         {
            Map<String, Object> filter = JsonUtils.toObject(URLDecoder.decode(href.substring(href.indexOf('=') + 1), StandardCharsets.UTF_8), new TypeReference<Map<String, Object>>() {});
            assertThat(filter).containsEntry("booleanOperator", "AND");
            List<?> criteria = (List<?>) filter.get("criteria");
            assertThat(criteria).hasSize(1);
            Map<String, Object> criterion = object(criteria.get(0));
            assertThat(criterion).containsEntry("fieldName", "mimeType").containsEntry("operator", "EQUALS");
            mimes.addAll((List<?>) criterion.get("values"));
         }
         catch(Exception e)
         {
            throw (new AssertionError("Invalid filtered list href", e));
         }
      }
      assertThat(mimes).containsExactlyInAnyOrder("image/jpeg", "image/png");
   }



   /*******************************************************************************
    ** Find one block of a given kind.
    *******************************************************************************/
   public static Map<String, Object> block(Map<String, Object> part, String kind)
   {
      return (((List<?>) part.get("blocks")).stream().map(PresentationGoldenTask::object).filter(block -> kind.equals(block.get("kind"))).findFirst().orElseThrow());
   }



   /*******************************************************************************
    ** Typed test view of a JSON object.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public static Map<String, Object> object(Object value)
   {
      return ((Map<String, Object>) value);
   }
}
