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


import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.UiApiTestServer;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiGateway;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The per-user starter prompts: built only from tables the user can read and
 ** that have rows, stable for the day, cached, and never failing.
 *******************************************************************************/
class StarterPromptsTest extends BaseAssistantTest
{
   private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void clear()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** The context as the route builds it, with the signed-in session.
    *******************************************************************************/
   private UiApiToolbox.Context context(String user, UiApiGateway gateway)
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(user, config));
      return (new UiApiToolbox.Context(user, null, gateway, PolicyService.ToolPolicy.defaults(), null));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private StarterPrompts prompts(Instant now)
   {
      UiApiTestServer.serve(qInstance, config);
      return (new StarterPrompts(config.getRuntime().getToolbox(), Clock.fixed(now, ZoneOffset.UTC)));
   }



   /*******************************************************************************
    ** A user who can read only the analytics table is never offered a prompt
    ** about the blog, pages or media; an editor never one about analytics.
    *******************************************************************************/
   @Test
   void promptsNameOnlyTablesTheUserCanRead() throws Exception
   {
      StarterPrompts starter = prompts(NOW);
      UiApiGateway   gateway = config.getRuntime().getUiApiGateway();

      List<String> analyst = starter.forUser(context(AssistantTestFixture.ANALYST, gateway), AssistantTestFixture.ANALYST, List.of(), false);
      assertThat(analyst).isNotEmpty().hasSizeLessThanOrEqualTo(3);
      assertThat(String.join("|", analyst)).contains("daily stats").doesNotContain("blog").doesNotContain("page").doesNotContain("media").doesNotContain("assistant");

      List<String> editor = starter.forUser(context(AssistantTestFixture.EDITOR, gateway), AssistantTestFixture.EDITOR, List.of(), false);
      assertThat(editor).isNotEmpty().hasSizeLessThanOrEqualTo(3);
      assertThat(String.join("|", editor)).doesNotContain("analytics").doesNotContain("assistant");
      assertThat(String.join("|", editor)).containsAnyOf("blog posts", "pages", "media");

      assertThat(starter.forUser(context(AssistantTestFixture.LIMITED, gateway), AssistantTestFixture.LIMITED, List.of(), false)).isEmpty();
   }



   /*******************************************************************************
    ** A table with no rows is skipped.
    *******************************************************************************/
   @Test
   void emptyTableIsSkipped() throws Exception
   {
      UiApiTestServer.serve(qInstance, config);
      QContext.init(qInstance, AssistantTestFixture.sessionFor("seeder-0"));
      new DeleteAction().execute(new DeleteInput("page").withQueryFilter(new QQueryFilter()));
      QContext.clear();

      UiApiGateway gateway = config.getRuntime().getUiApiGateway();
      for(int day = 0; day < 20; day++)
      {
         StarterPrompts daily = new StarterPrompts(config.getRuntime().getToolbox(), Clock.fixed(NOW.plusSeconds(86400L * day), ZoneOffset.UTC));
         List<String>   rs    = daily.forUser(context(AssistantTestFixture.EDITOR, gateway), AssistantTestFixture.EDITOR, List.of(), true);
         assertThat(rs).isNotEmpty();
         assertThat(String.join("|", rs)).doesNotContain("pages");
      }
   }



   /*******************************************************************************
    ** The prompts are the same for a user all day.
    *******************************************************************************/
   @Test
   void stableForTheDay() throws Exception
   {
      UiApiGateway gateway = config.getRuntime().getUiApiGateway();
      List<String> first   = prompts(NOW).forUser(context(AssistantTestFixture.EDITOR, gateway), AssistantTestFixture.EDITOR, List.of(), true);
      List<String> later   = prompts(NOW.plusSeconds(3600)).forUser(context(AssistantTestFixture.EDITOR, gateway), AssistantTestFixture.EDITOR, List.of(), true);
      assertThat(later).isEqualTo(first);
      assertThat(String.join("|", first)).containsAnyOf("added in the last 7 days", "most recent", "changed in");
   }



   /*******************************************************************************
    ** Errors from the UI API give no generated prompts and never throw.
    *******************************************************************************/
   @Test
   void errorsDegradeToEmpty() throws Exception
   {
      UiApiGateway broken = new UiApiGateway()
      {
         /*******************************************************************************
          **
          *******************************************************************************/
         @Override
         public boolean isInvocable(String method, String pathTemplate)
         {
            return (true);
         }



         /*******************************************************************************
          **
          *******************************************************************************/
         @Override
         public UiApiResult invoke(UiApiCall call) throws QException
         {
            throw (new IllegalStateException("down"));
         }
      };
      StarterPrompts starter = prompts(NOW);
      List<String>   rs      = starter.forUser(context(AssistantTestFixture.EDITOR, broken), AssistantTestFixture.EDITOR,
         List.of("[blog_post] How many posts?", "[none] What can you do?", "How many pages?"), true);
      assertThat(rs).containsExactly("What can you do?");
   }



   /*******************************************************************************
    ** The answer is cached per user for a few minutes, and rebuilt after.
    *******************************************************************************/
   @Test
   void cachedBriefly() throws Exception
   {
      UiApiTestServer.serve(qInstance, config);
      UiApiGateway  delegate = config.getRuntime().getUiApiGateway();
      AtomicInteger calls    = new AtomicInteger();
      UiApiGateway counting = new UiApiGateway()
      {
         /*******************************************************************************
          **
          *******************************************************************************/
         @Override
         public boolean isInvocable(String method, String pathTemplate)
         {
            return (delegate.isInvocable(method, pathTemplate));
         }



         /*******************************************************************************
          **
          *******************************************************************************/
         @Override
         public UiApiResult invoke(UiApiCall call) throws QException
         {
            calls.incrementAndGet();
            return (delegate.invoke(call));
         }
      };
      Instant[] now = { NOW };
      Clock clock = new Clock()
      {
         /*******************************************************************************
          **
          *******************************************************************************/
         @Override
         public ZoneId getZone()
         {
            return (ZoneOffset.UTC);
         }



         /*******************************************************************************
          **
          *******************************************************************************/
         @Override
         public Clock withZone(ZoneId zone)
         {
            return (this);
         }



         /*******************************************************************************
          **
          *******************************************************************************/
         @Override
         public Instant instant()
         {
            return (now[0]);
         }
      };
      StarterPrompts starter = new StarterPrompts(config.getRuntime().getToolbox(), clock);

      starter.forUser(context(AssistantTestFixture.EDITOR, counting), AssistantTestFixture.EDITOR, List.of(), false);
      int firstCalls = calls.get();
      assertThat(firstCalls).isBetween(2, 1 + StarterPrompts.MAX_COUNT_CALLS + StarterPrompts.MAX_DESCRIBE_CALLS);

      now[0] = NOW.plusSeconds(60);
      starter.forUser(context(AssistantTestFixture.EDITOR, counting), AssistantTestFixture.EDITOR, List.of(), false);
      assertThat(calls.get()).isEqualTo(firstCalls);

      now[0] = NOW.plusSeconds(StarterPrompts.CACHE_TTL.toSeconds() + 1);
      starter.forUser(context(AssistantTestFixture.EDITOR, counting), AssistantTestFixture.EDITOR, List.of(), false);
      assertThat(calls.get()).isEqualTo(firstCalls * 2);

      starter.forUser(context(AssistantTestFixture.ANALYST, counting), AssistantTestFixture.ANALYST, List.of(), false);
      assertThat(calls.get()).isGreaterThan(firstCalls * 2);
   }



   /*******************************************************************************
    ** A configured prompt stays only when it declares tables the user can read.
    *******************************************************************************/
   @Test
   void configuredPromptsNeedReadableTables()
   {
      Set<String> readable = Set.of("blog_post", "page");
      assertThat(StarterPrompts.allowedConfigured("[blog_post] Which posts are drafts?", readable)).isEqualTo("Which posts are drafts?");
      assertThat(StarterPrompts.allowedConfigured("[blog_post, page] Posts and pages?", readable)).isEqualTo("Posts and pages?");
      assertThat(StarterPrompts.allowedConfigured("[blog_post, media] Posts and media?", readable)).isNull();
      assertThat(StarterPrompts.allowedConfigured("[media] Which media is new?", readable)).isNull();
      assertThat(StarterPrompts.allowedConfigured("Which blog posts were published this month?", readable)).isNull();
      assertThat(StarterPrompts.allowedConfigured("[none] What can you help with?", readable)).isEqualTo("What can you help with?");
      assertThat(StarterPrompts.allowedConfigured("[] Empty tag", readable)).isNull();
      assertThat(StarterPrompts.allowedConfigured("[blog_post]", readable)).isNull();
      assertThat(StarterPrompts.plural("Blog Post")).isEqualTo("blog posts");
      assertThat(StarterPrompts.plural("Category")).isEqualTo("categories");
      assertThat(StarterPrompts.plural("Media")).isEqualTo("media");
   }

}
