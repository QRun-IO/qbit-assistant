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

package com.kingsrook.qbits.assistant.tools;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.engine.MessageParts;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.PresentationGoldenTask;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiGateway;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolOutcome;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolResult;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.PresentationGoldenTask.block;
import static com.kingsrook.qbits.assistant.testing.PresentationGoldenTask.object;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Presentation persistence, permission boundaries, formatting and input limits.
 *******************************************************************************/
class PresentResultsToolTest extends BaseAssistantTest
{
   /*******************************************************************************
    ** Streamed, replayed and stored presentations agree; retention removes data.
    *******************************************************************************/
   @Test
   void testPersistenceAndReplay() throws Exception
   {
      PresentationGoldenTask.task().script().accept(provider);
      TurnRunner.Outcome outcome = runner.run(AssistantTestFixture.EDITOR, "How many images do I have by type?");
      PresentationGoldenTask.check(outcome);
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.EDITOR, config));
      Map<String, Object> event = outcome.events(TurnEvent.PRESENTATION).get(0);
      AssistantMessage message = runner.runtime().getStore().getMessage(AssistantTestFixture.EDITOR, (Integer) event.get("messageId"));
      MessageParts parts = MessageParts.fromJson(message.getPartsJson());
      Map<String, Object> part = parts.list().stream().filter(p -> "presentation".equals(p.get("type"))).findFirst().orElseThrow();
      PresentationGoldenTask.assertPart(part);
      assertThat(part).isEqualTo(com.kingsrook.qqq.backend.core.utils.JsonUtils.toObject(com.kingsrook.qqq.backend.core.utils.JsonUtils.toJson(event.get("part")), Map.class));
      List<?> rows = (List<?>) block(part, "table").get("rows");
      List<?> points = (List<?>) block(part, "chart").get("points");
      List<?> links = (List<?>) block(part, "filterLinks").get("items");
      for(Integer i = 0; i < 2; i++)
      {
         assertThat(object(rows.get(i)).get("href")).isEqualTo(object(links.get(i)).get("href"));
         assertThat(object(points.get(i)).get("href")).isEqualTo(object(rows.get(i)).get("href"));
      }
      assertThat(runner.runtime().getStore().listEvents(AssistantTestFixture.EDITOR, outcome.turnId(), 0, 1000))
         .anyMatch(e -> TurnEvent.PRESENTATION.equals(e.getEventType()));
      assertThat(parts.text()).contains("81", "20", "\n|");
      assertThat(parts.stripRecordData()).isTrue();
      assertThat(parts.list()).noneMatch(p -> "presentation".equals(p.get("type")));
      assertThat(outcome.toolCalls()).anySatisfy(call ->
      {
         assertThat(call.getToolName()).isEqualTo(PresentResultsTool.NAME);
         assertThat(call.getInputJson()).contains("presentation");
         assertThat(call.getResultJson()).contains("Do not repeat");
      });
   }



   /*******************************************************************************
    ** The real UI metadata hides media from an analyst, as does presentation.
    *******************************************************************************/
   @Test
   void testUiDenial() throws Exception
   {
      String user = AssistantTestFixture.ANALYST;
      UiApiResult metadata = runner.runtime().getUiApiGateway().invoke(new UiApiCall("GET", "/metaData").withSessionUUID(user));
      assertThat(object(metadata.json().get("tables"))).doesNotContainKey("media");
      provider.thenToolUse(PresentResultsTool.NAME, input("summary", "The list is unavailable.", "recordLinks", List.of(input("label", "Image", "tableName", "media", "recordId", "1")))).thenText("");
      TurnRunner.Outcome outcome = runner.run(user, "Open an image");
      assertThat(outcome.toolCalls().get(0).getResultJson()).contains("table not visible through UI API: media");
      Map<String, Object> part = object(outcome.events(TurnEvent.PRESENTATION).get(0).get("part"));
      assertThat((List<?>) part.get("blocks")).isEmpty();
      assertThat(outcome.toolCalls().get(0).getInputJson()).doesNotContain("recordId");
   }



   /*******************************************************************************
    ** Cache metadata once per turn and send the original session UUID.
    *******************************************************************************/
   @Test
   void testMetadataCache() throws Exception
   {
      List<UiApiCall> calls = new ArrayList<>();
      UiApiGateway gateway = new UiApiGateway()
      {
         /*******************************************************************************
          ** Fixture metadata gateway.
          *******************************************************************************/
         @Override
         public boolean isInvocable(String method, String path)
         {
            return (true);
         }



         /*******************************************************************************
          ** Fixture metadata gateway.
          *******************************************************************************/
         @Override
         public UiApiResult invoke(UiApiCall call)
         {
            calls.add(call);
            return (new UiApiResult(200, "{\"tables\":{\"media\":{\"readPermission\":true}}}"));
         }
      };
      UiApiToolbox.Context context = new UiApiToolbox.Context("editor-session", "UTC", gateway, PolicyService.ToolPolicy.defaults(), null);
      PresentResultsTool tool = new PresentResultsTool(qInstance);
      assertThat(tool.execute(context, "1", PresentationGoldenTask.input(81, 20)).getOutcome()).isEqualTo(AiToolOutcome.OK);
      tool.execute(context, "2", PresentationGoldenTask.input(81, 20));
      assertThat(calls).hasSize(1).allMatch(call -> "editor-session".equals(call.getSessionUUID()) && "/metaData".equals(call.getPathTemplate()));
      new PresentResultsTool(qInstance).execute(context, "3", PresentationGoldenTask.input(81, 20));
      assertThat(calls).hasSize(2);
   }



   /*******************************************************************************
    ** Unknown metadata is dropped independently without persisting its values.
    *******************************************************************************/
   @Test
   void testUnknownMetadata()
   {
      AiToolResult result = execute(input("summary", "Available results.", "metrics", List.of(
         input("label", "Unknown table", "value", 9876, "tableName", "missing"),
         input("label", "Unknown field", "value", 5432, "tableName", "media", "fieldName", "missing"),
         input("label", "Known", "value", 101))));
      assertThat(result.getModelContent()).contains("Dropped metrics[0]", "unknown table", "Dropped metrics[1]", "unknown field");
      assertThat(result.getRender().toString()).doesNotContain("9876", "5432");
      assertThat((List<?>) block(result.getRender(), "metrics").get("items")).hasSize(1);
   }



   /*******************************************************************************
    ** Markdown punctuation, external URLs and pipes remain literal text.
    *******************************************************************************/
   @Test
   void testUntrustedLabelsAndLinks()
   {
      AiToolResult result = execute(input("summary", "Available results.", "metrics", List.of(input("label", "[evil](https://evil.example) | <img>", "value", 1)),
         "recordLinks", List.of(input("label", "Open", "tableName", "media", "recordId", "a/b?x#y")),
         "actions", List.of(input("label", "Ask", "prompt", "Show images"), input("label", "Open media", "navigateTo", "media"), input("label", "Bad", "prompt", "Ask", "navigateTo", "media"))));
      String markdown = (String) result.getRender().get("markdown");
      assertThat(markdown).doesNotContain("https://", "<img>", "[evil]").contains("&#124;", "[Open](/app/media/a%2Fb%3Fx%23y)", "- Ask: Show images", "[Open media](/app/media)");
      assertThat(result.getModelContent()).contains("exactly one");
   }



   /*******************************************************************************
    ** Formatting follows metadata while raw values remain numeric.
    *******************************************************************************/
   @Test
   void testFormatting()
   {
      qInstance.getTable("media").addField(new QFieldMetaData("price", QFieldType.DECIMAL).withDisplayFormat("$%,.2f"));
      AiToolResult result = execute(input("summary", "Amounts.", "metrics", List.of(input("label", "Amount", "value", 1234.5, "tableName", "media", "fieldName", "price"), input("label", "Count", "value", 12000)),
         "table", input("columns", List.of(input("label", "Amount", "tableName", "media", "fieldName", "price")), "rows", List.of(List.of(1234.5)))));
      assertThat(block(result.getRender(), "metrics").toString()).contains("$1,234.50", "12,000", "1234.5");
      assertThat(block(result.getRender(), "table").toString()).contains("$1,234.50");
   }



   /*******************************************************************************
    ** Percentages cannot refer to labels, derived percentages or bad indexes.
    *******************************************************************************/
   @Test
   void testInvalidPercentColumn()
   {
      for(Object source : List.of(0, 3, 1.5))
      {
         AiToolResult result = execute(input("summary", "Invalid share.", "table", input("columns", List.of(input("label", "Name"), input("label", "Share", "percentOfColumn", source)), "rows", List.of(List.of("JPEG", 0)))));
         assertThat(result.getModelContent()).contains("Dropped table");
         assertThat((List<?>) result.getRender().get("blocks")).isEmpty();
      }
   }



   /*******************************************************************************
    ** Zero totals are defined, and charts never exceed twelve points.
    *******************************************************************************/
   @Test
   void testChartLimitAndZeroTotal()
   {
      List<List<Object>> rows = new ArrayList<>();
      for(Integer i = 0; i < 13; i++)
      {
         rows.add(List.of("Item " + i, 0, 0));
      }
      AiToolResult result = execute(input("summary", "Thirteen items.", "table", input("columns", List.of(input("label", "Item"), input("label", "Count"), input("label", "Share", "percentOfColumn", 1)), "rows", rows),
         "chart", input("chartType", "bar", "labelColumn", 0, "valueColumn", 1)));
      assertThat((List<?>) block(result.getRender(), "chart").get("points")).hasSize(12);
      assertThat((List<?>) block(result.getRender(), "table").get("rows")).hasSize(13);
      assertThat(result.getModelContent()).contains("truncated to 12");
      assertThat(block(result.getRender(), "table").toString()).contains("0.0%");
   }



   /*******************************************************************************
    ** Operator validation does not coerce raw numeric/boolean filter values.
    *******************************************************************************/
   @Test
   void testCriteriaValidation()
   {
      AiToolResult result = execute(input("summary", "Links.", "filterLinks", List.of(
         input("label", "Invalid", "tableName", "media", "criteria", List.of(input("fieldName", "width", "operator", "INVENTED", "values", List.of(10)))),
         input("label", "Valid", "tableName", "media", "criteria", List.of(input("fieldName", "width", "operator", "EQUALS", "values", List.of(512)))))));
      assertThat(result.getModelContent()).contains("invalid criteria operator");
      List<?> links = (List<?>) block(result.getRender(), "filterLinks").get("items");
      String decoded = java.net.URLDecoder.decode((String) object(links.get(0)).get("href"), java.nio.charset.StandardCharsets.UTF_8);
      assertThat(decoded).contains("\"values\":[512]");
   }



   /*******************************************************************************
    ** Invalid summaries produce an error tool result; no presentation is emitted.
    *******************************************************************************/
   @Test
   void testInvalidSummaryAndAlwaysOffered() throws Exception
   {
      assertThat(runner.runtime().getContextBuilder().tools(new PolicyService.ToolPolicy(true, java.util.Set.of("navigate"), 20, true)))
         .anyMatch(tool -> PresentResultsTool.NAME.equals(tool.getName()) && !Boolean.TRUE.equals(tool.getStrict()));
      assertThat(execute(input("summary", "x".repeat(301))).getOutcome()).isEqualTo(AiToolOutcome.INVALID_INPUT);
      provider.thenToolUse(PresentResultsTool.NAME, Map.of()).thenText("Unable to present.");
      TurnRunner.Outcome outcome = runner.run(AssistantTestFixture.EDITOR, "Present");
      assertThat(outcome.events(TurnEvent.PRESENTATION)).isEmpty();
      assertThat(outcome.toolCalls().get(0).getOutcome()).isEqualTo("INVALID_INPUT");
   }



   /*******************************************************************************
    ** Use the fixture's real UI gateway for unit-level presentation checks.
    *******************************************************************************/
   private AiToolResult execute(Map<String, Object> input)
   {
      return (new PresentResultsTool(qInstance).execute(new UiApiToolbox.Context(AssistantTestFixture.EDITOR, "UTC", runner.runtime().getUiApiGateway(), PolicyService.ToolPolicy.defaults(), null), "present", input));
   }
}
