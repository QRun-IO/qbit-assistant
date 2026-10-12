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


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import static com.kingsrook.qbits.assistant.testing.EvalSupport.answer;
import static com.kingsrook.qbits.assistant.testing.EvalSupport.firstResult;
import static com.kingsrook.qbits.assistant.testing.EvalSupport.lastToolResults;
import static com.kingsrook.qbits.assistant.testing.EvalSupport.unwrap;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Golden tasks v1 (spec 7.3) against the fixture app. Each task has:
 ** - a prompt and the user asking it;
 ** - a script for the fake provider: the tool calls a good model makes, and
 **   an answer composed ONLY from the tool results (a "faithful model");
 ** - checks that hold for any model: which tools ran (names, key
 **   arguments, outcomes), numbers versus ground truth, deep links.
 ** The same tasks run against a real model in live mode (provider module).
 *******************************************************************************/
public class GoldenTasks
{

   /*******************************************************************************
    ** One task.
    *******************************************************************************/
   public record Task(String id, String userId, String prompt, Consumer<ScriptedModelProvider> script, Consumer<TurnRunner.Outcome> check, Consumer<TurnRunner.Outcome> liveCheck)
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public String toString()
      {
         return (id);
      }
   }



   /*******************************************************************************
    ** All tasks.
    *******************************************************************************/
   public static List<Task> all()
   {
      List<Task> tasks = new ArrayList<>();

      ////////////////////////////////////////////////////////////
      // count with a filter; number from the tool; a deep link //
      ////////////////////////////////////////////////////////////
      tasks.add(new Task("count-published-posts", AssistantTestFixture.EDITOR, "How many published blog posts are there?",
         p -> p.thenToolUse("count_records", map("table", "blog_post", "filter", filter("AND", criterion("status", "EQUALS", "PUBLISHED"))))
            .thenRespond(answer(r ->
            {
               Map<String, Object> result = firstResult(r);
               return ("There are " + result.get("count") + " published blog posts. [Open them](" + result.get("openHref") + ")");
            })),
         o ->
         {
            AssistantToolCall call = onlyCall(o, "count_records");
            assertThat(call.getInputJson()).contains("blog_post").contains("PUBLISHED");
            assertThat(call.getOutcome()).isEqualTo("OK");
            assertThat(o.text()).contains(String.valueOf(AssistantTestFixture.PUBLISHED_POSTS));
            assertThat(o.text()).contains("/app/blog_post");
         },
         o -> live(o, "count_records", String.valueOf(AssistantTestFixture.PUBLISHED_POSTS))));

      /////////////////////////////////////////////////////////////////
      // example D: "Show me what changed on page 12 this week"      //
      /////////////////////////////////////////////////////////////////
      tasks.add(new Task("D-page-12-this-week", AssistantTestFixture.EDITOR, "Show me what changed on page 12 this week",
         p -> p.thenToolUse("get_record_history", map("table", "page", "id", "12", "since", "THIS_WEEK"))
            .thenRespond(answer(r ->
            {
               Map<String, Object> result = firstResult(r);
               StringBuilder       text   = new StringBuilder("Page **" + result.get("label") + "** [" + result.get("href") + "](" + result.get("href") + ") has "
                  + result.get("entryCount") + " changes this week:\n");
               for(Object entry : (List<?>) result.get("entries"))
               {
                  for(Object detail : (List<?>) ((Map<?, ?>) entry).get("details"))
                  {
                     text.append("- `").append(((Map<?, ?>) detail).get("field")).append("` changed\n");
                  }
               }
               return (text.toString());
            })),
         o ->
         {
            AssistantToolCall call = onlyCall(o, "get_record_history");
            assertThat(call.getInputJson()).contains("\"page\"").contains("12");
            assertThat(call.getOutcome()).isEqualTo("OK");
            assertThat(o.text()).contains(AssistantTestFixture.PAGE_12_CHANGES_THIS_WEEK + " change");
            assertThat(o.text()).contains("/app/page/12");
            assertThat(o.text()).contains("title").contains("metaDescription").contains("content");

            Map<String, Object> result = o.events(TurnEvent.TOOL_RESULT).get(0);
            assertThat(String.valueOf(result.get("render"))).contains("history");
         },
         o -> live(o, "get_record_history", "/app/page/12")));

      /////////////////////////////////////////////////////////////////////////
      // example A, read half: "find posts missing SEO data" (two parallel   //
      // reads: count missing meta descriptions, list missing excerpts)      //
      /////////////////////////////////////////////////////////////////////////
      tasks.add(new Task("A-read-missing-seo", AssistantTestFixture.EDITOR, "find posts missing SEO data",
         p ->
         {
            List<Object[]> calls = new ArrayList<>();
            calls.add(new Object[] { "count_records", map("table", "blog_post", "filter",
               filter("AND", criterion("status", "EQUALS", "PUBLISHED"), criterion("metaDescription", "IS_BLANK"))) });
            calls.add(new Object[] { "query_records", map("table", "blog_post", "fields", List.of("id", "title", "slug"), "filter",
               filter("AND", criterion("status", "EQUALS", "PUBLISHED"), criterion("excerpt", "IS_BLANK"))) });
            p.thenToolUses("Checking the published posts.", calls).thenRespond(answer(r ->
            {
               Map<String, Object> meta    = unwrap(lastToolResults(r).get(0).getText());
               Map<String, Object> excerpt = unwrap(lastToolResults(r).get(1).getText());
               return ("\n" + meta.get("count") + " published posts have no meta description, and " + excerpt.get("totalCount") + " have no excerpt. "
                  + "[Open the posts missing an excerpt](" + excerpt.get("openHref") + ")");
            }));
         },
         o ->
         {
            assertThat(o.toolCalls()).extracting(AssistantToolCall::getToolName).containsExactly("count_records", "query_records");
            assertThat(o.toolCalls()).allMatch(c -> "OK".equals(c.getOutcome()));
            assertThat(o.text()).contains(AssistantTestFixture.PUBLISHED_POSTS_NO_META + " published posts have no meta description");
            assertThat(o.text()).contains(AssistantTestFixture.PUBLISHED_POSTS_NO_EXCERPT + " have no excerpt");
            assertThat(o.text()).contains("(/app/blog_post?filter=");
            Map<String, Object> query = o.events(TurnEvent.TOOL_RESULT).get(1);
            assertThat((List<?>) query.get("recordRefs")).hasSize(AssistantTestFixture.PUBLISHED_POSTS_NO_EXCERPT);
         },
         o -> live(o, null, String.valueOf(AssistantTestFixture.PUBLISHED_POSTS_NO_META), String.valueOf(AssistantTestFixture.PUBLISHED_POSTS_NO_EXCERPT))));

      ///////////////////////////////////////////
      // navigation: "take me to the blog posts"
      ///////////////////////////////////////////
      tasks.add(new Task("navigate-blog-posts", AssistantTestFixture.EDITOR, "Take me to the blog posts list",
         p -> p.thenToolUse("navigate", map("target", "blog_post")).thenText("Opening Blog Posts."),
         o ->
         {
            assertThat(onlyCall(o, "navigate").getOutcome()).isEqualTo("OK");
            List<Map<String, Object>> navigations = o.events(TurnEvent.UI_NAVIGATE);
            assertThat(navigations).hasSize(1);
            assertThat(String.valueOf(navigations.get(0).get("href"))).startsWith("/app/blog_post");
         },
         o -> assertThat(o.events(TurnEvent.UI_NAVIGATE)).anyMatch(n -> String.valueOf(n.get("href")).startsWith("/app/blog_post"))));

      ////////////////////////////////////////////////
      // fuzzy reference -> record (search_records) //
      ////////////////////////////////////////////////
      tasks.add(new Task("search-proxmox-post", AssistantTestFixture.EDITOR, "Find the post about the Proxmox home lab",
         p -> p.thenToolUse("search_records", map("term", "Proxmox home lab")).thenRespond(answer(r -> "Found: " + firstResult(r))),
         o ->
         {
            assertThat(onlyCall(o, "search_records").getOutcome()).isEqualTo("OK");
            assertThat(o.text()).contains("Proxmox home lab notes");
            assertThat(o.text()).contains("/app/blog_post/1");
         },
         o -> live(o, "search_records", "/app/blog_post/1")));

      return (tasks);
   }



   /*******************************************************************************
    ** Live-model check: the turn completed, the tool (if given) ran OK, and the
    ** answer mentions each expected string (a real model words it freely).
    *******************************************************************************/
   static void live(TurnRunner.Outcome outcome, String toolName, String... expected)
   {
      assertThat(outcome.end()).containsEntry("status", "COMPLETE");
      if(toolName != null)
      {
         assertThat(outcome.toolCalls()).anyMatch(c -> toolName.equals(c.getToolName()) && "OK".equals(c.getOutcome()));
      }
      for(String text : expected)
      {
         assertThat(outcome.text()).contains(text);
      }
   }



   /*******************************************************************************
    ** The single call of a tool in the turn.
    *******************************************************************************/
   static AssistantToolCall onlyCall(TurnRunner.Outcome outcome, String toolName)
   {
      List<AssistantToolCall> calls = outcome.toolCalls().stream().filter(c -> toolName.equals(c.getToolName())).toList();
      assertThat(calls).as("calls of " + toolName).isNotEmpty();
      return (calls.get(calls.size() - 1));
   }



   /*******************************************************************************
    ** A filter as the model writes it.
    *******************************************************************************/
   @SafeVarargs
   public static Map<String, Object> filter(String booleanOperator, Map<String, Object>... criteria)
   {
      return (map("booleanOperator", booleanOperator, "criteria", List.of(criteria)));
   }



   /*******************************************************************************
    ** One criterion.
    *******************************************************************************/
   public static Map<String, Object> criterion(String field, String operator, Object... values)
   {
      return (map("field", field, "operator", operator, "values", List.of(values)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public static Map<String, Object> map(Object... keyValues)
   {
      Map<String, Object> map = new LinkedHashMap<>();
      for(int i = 0; i + 1 < keyValues.length; i += 2)
      {
         map.put((String) keyValues[i], keyValues[i + 1]);
      }
      return (map);
   }

}
