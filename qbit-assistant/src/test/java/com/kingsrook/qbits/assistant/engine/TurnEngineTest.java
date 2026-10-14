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


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.spi.CacheHint;
import com.kingsrook.qbits.assistant.spi.ModelMessage;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.spi.ModelResponse;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.EDITOR;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The agent loop with a scripted model: request assembly and caching,
 ** tool execution through the kernel, parallel results in one message,
 ** persistence, limits, refusal / max_tokens / provider errors.
 *******************************************************************************/
class TurnEngineTest extends BaseAssistantTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testToolLoopAndPersistence() throws Exception
   {
      provider.thenToolUse("count_records", input("table", "blog_post", "filter", filter("AND", "status", "EQUALS", List.of("PUBLISHED"))))
         .thenRespond(request -> new ModelResponse().withParts(new ArrayList<>(List.of(ModelPart.text("There are 4 published posts.")))).withStopReason("end_turn"));

      TurnRunner.Outcome outcome = runner.run(EDITOR, "how many published posts?");

      assertThat(outcome.end()).containsEntry("status", "COMPLETE");
      assertThat(outcome.text()).isEqualTo("There are 4 published posts.");
      assertThat(outcome.events().get(0).getType()).isEqualTo(TurnEvent.MESSAGE_START);
      assertThat(outcome.events().get(outcome.events().size() - 1).getType()).isEqualTo(TurnEvent.MESSAGE_END);
      assertThat(outcome.events(TurnEvent.TOOL_START)).hasSize(1);
      assertThat(outcome.events(TurnEvent.TOOL_RESULT).get(0)).containsEntry("outcome", "OK").containsEntry("name", "count_records");
      assertThat(outcome.events(TurnEvent.USAGE)).hasSize(1);

      ////////////////////////////////////
      // seq ids are 1..n without gaps //
      ////////////////////////////////////
      for(int i = 0; i < outcome.events().size(); i++)
      {
         assertThat(outcome.events().get(i).getSeq()).isEqualTo(i + 1);
      }

      assertThat(outcome.toolCalls()).hasSize(1);
      assertThat(outcome.toolCalls().get(0).getOutcome()).isEqualTo("OK");
      assertThat(outcome.toolCalls().get(0).getRowCount()).isNotNull();
      assertThat(outcome.toolCalls().get(0).getInputJson()).contains("blog_post");
      assertThat(outcome.toolCalls().get(0).getResultJson()).startsWith("<qqq_data");

      //////////////////////////////////////////////////////////////////////
      // the second request: same prefix, one SHORT breakpoint at the end //
      //////////////////////////////////////////////////////////////////////
      List<ModelRequest> requests = provider.getRequests();
      assertThat(requests).hasSize(2);
      ModelRequest first = requests.get(0);
      assertThat(first.getModel()).isEqualTo("claude-sonnet-5-5");
      assertThat(first.getEffort()).isEqualTo("medium");
      assertThat(first.getSystem()).hasSize(2);
      assertThat(first.getSystem().get(0).getCacheHint()).isEqualTo(CacheHint.LONG);
      assertThat(first.getSystem().get(0).getText()).contains("qqq_data").contains("untrusted").contains("/app/");
      assertThat(first.getSystem().get(1).getCacheHint()).isEqualTo(CacheHint.SHORT);
      assertThat(first.getTools()).extracting(t -> t.getName()).contains("count_records", "query_records", "navigate").doesNotHaveDuplicates();
      //////////////////////////////////////////////////////////////////////
      // no strict tools by default: Anthropic refused all ten strict      //
      // ("compiled grammar is too large"); the kernel validates inputs   //
      //////////////////////////////////////////////////////////////////////
      assertThat(first.getTools()).noneMatch(t -> Boolean.TRUE.equals(t.getStrict()));

      ModelRequest second = requests.get(1);
      assertThat(second.getSystem().get(0).getText()).isEqualTo(first.getSystem().get(0).getText());
      assertThat(second.getMessages()).hasSize(3);
      assertThat(second.getMessages().get(2).getParts().get(0).getType()).isEqualTo(ModelPart.Type.TOOL_RESULT);
      assertThat(second.getMessages().stream().filter(m -> m.getCacheHint() != CacheHint.NONE)).hasSize(1);
      assertThat(second.getMessages().get(2).getCacheHint()).isEqualTo(CacheHint.SHORT);

      QContext.init(qInstance, AssistantTestFixture.ownerSession(EDITOR, config));
      try
      {
         AssistantStore store = new AssistantStore();
         AssistantTurn  turn  = store.getTurn(EDITOR, outcome.turnId());
         assertThat(turn.getStatus()).isEqualTo("COMPLETE");
         assertThat(turn.getToolCallCount()).isEqualTo(1);
         assertThat(turn.getModelCallCount()).isEqualTo(2);
         assertThat(turn.getInputTokens()).isEqualTo(2400);
         assertThat(turn.getCostMicros()).isGreaterThan(0);

         List<AssistantMessage> messages = store.listMessages(EDITOR, outcome.conversationId(), null, 10);
         assertThat(messages).extracting(AssistantMessage::getRole).containsExactly("USER", "ASSISTANT");
         assertThat(messages.get(1).getStatus()).isEqualTo("COMPLETE");
         assertThat(messages.get(1).getPartsJson()).contains("\"type\":\"tool\"").contains("There are 4 published posts.");

         assertThat(store.listEvents(EDITOR, outcome.turnId(), 0, 1000)).hasSize(outcome.events().size());

         AssistantUsageDaily usage = store.query(AssistantUsageDaily.TABLE_NAME, new QQueryFilter().withCriteria("userId", QCriteriaOperator.EQUALS, EDITOR), AssistantUsageDaily.class).get(0);
         assertThat(usage.getTurnCount()).isEqualTo(1);
         assertThat(usage.getCostMicros()).isEqualTo(turn.getCostMicros());
         assertThat(store.getConversation(EDITOR, outcome.conversationId()).getTitle()).isEqualTo("how many published posts?");
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** Parallel tool calls: all results in ONE user message, in order.
    *******************************************************************************/
   @Test
   void testParallelToolResultsInOneMessage() throws Exception
   {
      List<Object[]> calls = new ArrayList<>();
      calls.add(new Object[] { "count_records", input("table", "blog_post", "filter", filter("AND")) });
      calls.add(new Object[] { "count_records", input("table", "page", "filter", filter("AND")) });
      calls.add(new Object[] { "count_records", input("table", "analytics_daily_stat", "filter", filter("AND")) });
      calls.add(new Object[] { "list_tables", input() });
      calls.add(new Object[] { "no_such_tool", input() });
      provider.thenToolUses("Checking.", calls).thenText("Done.");

      TurnRunner.Outcome outcome = runner.run(EDITOR, "counts please");
      assertThat(outcome.end()).containsEntry("status", "COMPLETE");

      ModelMessage results = provider.getRequests().get(1).getMessages().get(2);
      assertThat(results.getRole()).isEqualTo(ModelMessage.Role.USER);
      assertThat(results.getParts()).hasSize(5);
      assertThat(results.getParts()).extracting(ModelPart::getToolUseId).containsExactly("toolu_0_0", "toolu_0_1", "toolu_0_2", "toolu_0_3", "toolu_0_4");
      assertThat(results.getParts().get(0).getIsError()).isFalse();
      assertThat(results.getParts().get(2).getIsError()).as("editor cannot read analytics").isTrue();
      assertThat(results.getParts().get(4).getIsError()).isTrue();
      assertThat(outcome.text()).isEqualTo("Checking.Done.");
      assertThat(outcome.toolCalls()).hasSize(5);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testToolCallLimit() throws Exception
   {
      config.setMaxToolCallsPerTurn(2);
      List<Object[]> calls = new ArrayList<>();
      for(int i = 0; i < 3; i++)
      {
         calls.add(new Object[] { "list_tables", input() });
      }
      provider.thenToolUses(null, calls).thenToolUse("list_tables", input()).thenText("never");

      TurnRunner.Outcome outcome = runner.run(EDITOR, "loop");
      assertThat(outcome.toolCalls()).hasSize(2);
      assertThat(outcome.end()).containsEntry("stopReason", "tool_limit");
      assertThat(outcome.events(TurnEvent.NOTICE)).isNotEmpty();
      assertThat(provider.getRequests().get(1).getMessages().get(2).getParts().get(2).getIsError()).isTrue();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testRefusalMaxTokensAndProviderError() throws Exception
   {
      provider.thenRespond(r -> new ModelResponse().withStopReason(ModelResponse.STOP_REFUSAL));
      TurnRunner.Outcome refused = runner.run(EDITOR, "something");
      assertThat(refused.end()).containsEntry("status", "COMPLETE").containsEntry("stopReason", "refusal");
      assertThat(refused.events(TurnEvent.NOTICE).get(0)).containsEntry("level", "warning");

      provider.thenRespond(r -> new ModelResponse().withParts(new ArrayList<>(List.of(ModelPart.text("long...")))).withStopReason(ModelResponse.STOP_MAX_TOKENS));
      TurnRunner.Outcome cut = runner.run(EDITOR, "write a novel");
      assertThat(cut.end()).containsEntry("stopReason", "max_tokens");
      assertThat(cut.events(TurnEvent.NOTICE)).hasSize(1);

      provider.thenError(ModelProviderException.Kind.OVERLOADED, "overloaded");
      TurnRunner.Outcome failed = runner.run(EDITOR, "hello");
      assertThat(failed.end()).containsEntry("status", "ERROR");
      assertThat(failed.errorCode()).isEqualTo("PROVIDER");

      ////////////////////////////////////////////////
      // error is followed directly by message.end //
      ////////////////////////////////////////////////
      List<TurnEvent> events = failed.events();
      assertThat(events.get(events.size() - 2).getType()).isEqualTo(TurnEvent.ERROR);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testTimeout() throws Exception
   {
      config.setMaxTurnSeconds(1);
      provider.withDelay(Duration.ofSeconds(3)).thenText("too late");
      TurnRunner.Outcome outcome = runner.run(EDITOR, "slow");
      assertThat(outcome.errorCode()).isEqualTo("TIMEOUT");
      assertThat(outcome.end()).containsEntry("status", "ERROR");
   }



   /*******************************************************************************
    ** History: earlier turns go back as text (tool results cleared, tool
    ** summaries kept), and the conversation continues.
    *******************************************************************************/
   @Test
   void testHistory() throws Exception
   {
      provider.thenToolUse("count_records", input("table", "page", "filter", filter("AND"))).thenText("There are 12 pages.").thenText("You asked about pages.");
      TurnRunner.Outcome first = runner.run(EDITOR, "how many pages?");
      runner.run(EDITOR, first.conversationId(), "what did I ask?", Map.of("route", "/app/content/page", "tableName", "page", "evil", "dropped"));

      ModelRequest third = provider.getRequests().get(2);
      assertThat(third.getMessages()).hasSize(3);
      assertThat(third.getMessages().get(0).getParts().get(0).getText()).isEqualTo("how many pages?");
      assertThat(third.getMessages().get(1).getParts().get(0).getText()).contains("earlier step count_records").contains("There are 12 pages.");
      String lastUser = third.getMessages().get(2).getParts().stream().map(ModelPart::getText).reduce("", String::concat);
      assertThat(lastUser).contains("what did I ask?").contains("page_context").contains("/app/content/page").doesNotContain("evil");
      assertThat(third.getMessages().get(2).getCacheHint()).isEqualTo(CacheHint.SHORT);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testAppHref()
   {
      assertThat(TurnEngine.isAppHref("/app/blog_post/1")).isTrue();
      assertThat(TurnEngine.isAppHref("/app")).isTrue();
      assertThat(TurnEngine.isAppHref("https://evil.example/app/")).isFalse();
      assertThat(TurnEngine.isAppHref("/app//evil.example")).isFalse();
      assertThat(TurnEngine.isAppHref("/application")).isFalse();
      assertThat(TurnEngine.isAppHref(null)).isFalse();
   }

}
