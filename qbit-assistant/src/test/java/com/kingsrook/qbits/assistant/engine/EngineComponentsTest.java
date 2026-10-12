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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.kingsrook.qbits.assistant.AssistantModelRouting;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.provider.CompletableStreamHandle;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelResponse;
import com.kingsrook.qbits.assistant.provider.ModelUsage;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.context.QContext;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Unit tests of the engine's parts: policy overlay and cache, budgets,
 ** rate limits, message parts, pricing, the event log, stream handles.
 *******************************************************************************/
class EngineComponentsTest extends BaseAssistantTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testPolicyOverlayAndCache() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      try
      {
         PolicyService service = new PolicyService(config, new AssistantStore(), Clock.systemUTC());
         PolicyService.EffectivePolicy base = service.current(false);
         assertThat(base.killSwitch()).isFalse();
         assertThat(base.mainModel()).isEqualTo(AssistantModelRouting.SONNET_5_5);
         assertThat(base.mainEffort()).isEqualTo("medium");

         new AssistantStore().insert(new AssistantPolicy().withName("default").withKillSwitch(true).withMainModel(AssistantModelRouting.OPUS_5_5).withMainEffort("high")
            .withUserDailyCostMicrosHard(5L));

         assertThat(service.current(false).killSwitch()).as("cached for 30s").isFalse();
         PolicyService.EffectivePolicy fresh = service.current(true);
         assertThat(fresh.killSwitch()).isTrue();
         assertThat(fresh.mainModel()).isEqualTo(AssistantModelRouting.OPUS_5_5);
         assertThat(fresh.mainEffort()).isEqualTo("high");
         assertThat(fresh.budgets().getUserDailyCostMicrosHard()).isEqualTo(5L);
         assertThat(service.isKillSwitchOn()).isTrue();

         service.invalidate();
         assertThat(service.current(false).killSwitch()).isTrue();

         assertThat(service.checkBudget(fresh, AssistantTestFixture.ADMIN, 0, 0).hardExceeded()).isFalse();
         PolicyService.BudgetStatus over = service.checkBudget(fresh, AssistantTestFixture.ADMIN, 0, 5);
         assertThat(over.hardExceeded()).isTrue();
         assertThat(over.message()).contains("daily");
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testInstanceBudgets() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.EDITOR, config));
      try
      {
         AssistantStore store = new AssistantStore();
         store.addUsage(AssistantTestFixture.EDITOR, java.time.LocalDate.now(ZoneOffset.UTC), new ModelUsage().withInputTokens(100L).withOutputTokens(50L), 300, true);

         PolicyService service = new PolicyService(config, store, Clock.systemUTC());
         config.getBudgets().setInstanceMonthlyCostMicrosSoft(200L);
         assertThat(service.checkBudget(service.current(true), AssistantTestFixture.OTHER_EDITOR, 0, 0).softExceeded()).isTrue();

         config.getBudgets().setInstanceMonthlyTokensHard(150L);
         PolicyService.BudgetStatus hard = service.checkBudget(service.current(true), AssistantTestFixture.OTHER_EDITOR, 0, 0);
         assertThat(hard.hardExceeded()).isTrue();
         assertThat(hard.message()).contains("monthly");

         config.getBudgets().setInstanceMonthlyTokensHard(null);
         config.getBudgets().setInstanceMonthlyCostMicrosSoft(null);
         config.getBudgets().setUserDailyCostMicrosSoft(100L);
         assertThat(service.checkBudget(service.current(true), AssistantTestFixture.EDITOR, 0, 0).message()).contains("close to your daily");
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testRateLimiter()
   {
      AtomicReference<Instant> now   = new AtomicReference<>(Instant.parse("2026-10-12T10:00:00Z"));
      Clock                    clock = new Clock()
      {
         @Override
         public ZoneOffset getZone()
         {
            return (ZoneOffset.UTC);
         }



         @Override
         public Clock withZone(java.time.ZoneId zone)
         {
            return (this);
         }



         @Override
         public Instant instant()
         {
            return (now.get());
         }
      };

      RateLimiter limiter = new RateLimiter(clock);
      assertThat(limiter.tryAcquire("u", 2, 1)).isNull();
      assertThat(limiter.tryAcquire("u", 2, 1)).contains("in progress");
      limiter.release("u");
      assertThat(limiter.tryAcquire("u", 2, 1)).isNull();
      limiter.release("u");
      assertThat(limiter.tryAcquire("u", 2, 1)).contains("last minute");
      assertThat(limiter.tryAcquire("v", 2, 1)).isNull();

      now.set(now.get().plusSeconds(61));
      assertThat(limiter.tryAcquire("u", 2, 1)).isNull();
      limiter.release("u");
      limiter.release("u");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testMessageParts()
   {
      MessageParts parts = new MessageParts()
         .addText("Hello ")
         .addText("world")
         .addText(null)
         .addTool("t1", "count_records", "Counted 4", "OK", Map.of("type", "count", "count", 4))
         .addRecordRefs(List.of(Map.of("tableName", "blog_post", "id", 1, "href", "/app/blog_post/1")))
         .addRecordRefs(List.of())
         .addNavigate("/app/blog_post")
         .addNotice("warning", "careful")
         .addText("!");

      List<Map<String, Object>> list = parts.list();
      assertThat(list).extracting(p -> p.get("type")).containsExactly("text", "tool", "record_refs", "navigate", "notice", "text");
      assertThat(list.get(0)).containsEntry("text", "Hello world");
      assertThat(list.get(1)).containsEntry("render", Map.of("type", "count", "count", 4));
      assertThat(parts.text()).isEqualTo("Hello world!");
      assertThat(parts.toolSummaries()).contains("earlier step count_records: Counted 4 (OK)");

      MessageParts back = MessageParts.fromJson(parts.toJson());
      assertThat(back.list()).hasSize(6);
      assertThat(MessageParts.fromJson("not json").list()).isEmpty();
      assertThat(MessageParts.fromJson(null).list()).isEmpty();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testPricing()
   {
      ModelPricing sonnet = ModelPricing.defaults().get(AssistantModelRouting.SONNET_5_5);
      ModelUsage   usage  = new ModelUsage().withInputTokens(6000L).withOutputTokens(800L).withCacheReadTokens(12_000L).withCacheWriteTokens(1000L).withCacheWrite1hTokens(400L);
      //////////////////////////////////////////////////////////////////////////////////
      // 6000*2 + 800*10 + 12000*0.2 + 600*2.5 + 400*4 = 12000+8000+2400+1500+1600   //
      //////////////////////////////////////////////////////////////////////////////////
      assertThat(sonnet.costMicros(usage)).isEqualTo(25_500);
      assertThat(sonnet.costMicros(null)).isZero();
      assertThat(new ModelPricing().costMicros(usage)).isZero();
      assertThat(ModelPricing.defaults().get(AssistantModelRouting.OPUS_5_5).getCacheReadUsdPerMTok()).isEqualTo(0.2);
      assertThat(usage.billableTokens()).isEqualTo(7800);
      assertThat(new ModelUsage().add(usage).add(null).getCacheReadTokens()).isEqualTo(12_000);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testEventLog() throws Exception
   {
      TurnEventLog log = new TurnEventLog();
      log.append(TurnEvent.MESSAGE_START, Map.of("turnId", 1));
      log.append(TurnEvent.TEXT_DELTA, Map.of("text", "hi", "seq", 99));
      assertThat(log.after(0, 10)).hasSize(2);
      assertThat(log.after(1, 10).get(0).getData()).containsEntry("seq", 2);
      assertThat(log.after(2, 50)).isEmpty();
      assertThat(log.isClosed()).isFalse();

      Thread.ofVirtual().start(() ->
      {
         try
         {
            Thread.sleep(100);
         }
         catch(InterruptedException e)
         {
            Thread.currentThread().interrupt();
         }
         log.append(TurnEvent.MESSAGE_END, Map.of("status", "COMPLETE"));
      });
      List<TurnEvent> end = log.after(2, 5000);
      assertThat(end).hasSize(1);
      assertThat(end.get(0).isTerminal()).isTrue();
      assertThat(log.isClosed()).isTrue();
      assertThat(log.size()).isEqualTo(3);
      assertThat(end.get(0).toSseFrame()).isEqualTo("id: 3\nevent: message.end\ndata: {\"seq\":3,\"status\":\"COMPLETE\"}\n\n");

      TurnEvent parsed = TurnEvent.fromJson(7, "notice", "{\"text\":\"x\"}");
      assertThat(parsed.getData()).containsEntry("seq", 7).containsEntry("text", "x");
      assertThat(TurnEvent.fromJson(1, "x", "garbage").getData()).containsOnlyKeys("seq");

      TurnEventLog closed = new TurnEventLog();
      closed.close();
      assertThat(closed.after(0, 1000)).isEmpty();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testStreamHandle() throws Exception
   {
      CompletableStreamHandle handle = new CompletableStreamHandle();
      handle.complete(new ModelResponse().withStopReason("end_turn"));
      assertThat(handle.await(Duration.ofSeconds(1)).getStopReason()).isEqualTo("end_turn");

      CompletableStreamHandle failing = new CompletableStreamHandle();
      failing.fail(new ModelProviderException(ModelProviderException.Kind.RATE_LIMITED, 429, "slow down"));
      assertThatThrownBy(() -> failing.await(Duration.ofSeconds(1))).isInstanceOfSatisfying(ModelProviderException.class, e ->
      {
         assertThat(e.isRetryable()).isTrue();
         assertThat(e.getHttpStatus()).isEqualTo(429);
      });

      CompletableStreamHandle cancelled = new CompletableStreamHandle();
      cancelled.setOnCancel(() -> { throw (new IllegalStateException("closing")); });
      cancelled.cancel();
      cancelled.cancel();
      assertThat(cancelled.await(Duration.ofSeconds(1)).getStopReason()).isEqualTo(ModelResponse.STOP_CANCELLED);
      assertThat(new ModelProviderException(ModelProviderException.Kind.INVALID_REQUEST, 400, "x").isRetryable()).isFalse();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSmallHelpers()
   {
      assertThat(ToolLabels.running("query_records")).isEqualTo("Querying records");
      assertThat(ToolLabels.done("query_records")).isEqualTo("Queried records");
      assertThat(ToolLabels.running("mystery")).isEqualTo("Working");
      assertThat(ToolLabels.done("mystery")).isEqualTo("Done");

      Map<String, Object> clean = ContextBuilder.sanitizePageContext(Map.of("route", "/app/x", "tableName", "page", "recordId", 12, "evil", "x"));
      assertThat(clean).containsOnlyKeys("route", "tableName", "recordId");
      assertThat(ContextBuilder.sanitizePageContext(null)).isEmpty();
      Map<String, Object> many = ContextBuilder.sanitizePageContext(Map.of("tableName", "page", "selectedIds", java.util.Collections.nCopies(2000, 123456)));
      assertThat((List<?>) many.get("selectedIds")).hasSize(ContextBuilder.MAX_SELECTED_IDS);

      Map<String, Object> hostile = ContextBuilder.sanitizePageContext(Map.of(
         "route", "x".repeat(5000),
         "tableName", Map.of("nested", "object"),
         "recordId", List.of(1, 2),
         "selectedIds", List.of("a", Map.of("b", 1), "y".repeat(2000)),
         "filter", "not an object"));
      assertThat(hostile).containsOnlyKeys("route", "selectedIds");
      assertThat((String) hostile.get("route")).hasSize(ContextBuilder.MAX_CONTEXT_CHARS);
      assertThat((List<?>) hostile.get("selectedIds")).hasSize(2);
      assertThat(((String) ((List<?>) hostile.get("selectedIds")).get(1))).hasSize(ContextBuilder.MAX_CONTEXT_CHARS);

      Map<String, Object> bigFilter = ContextBuilder.sanitizePageContext(Map.of("tableName", "page", "filter", Map.of("criteria", "z".repeat(5000))));
      assertThat(bigFilter).containsOnlyKeys("tableName");

      assertThat(TurnEngine.map("a", 1, "b", null, "c", new Object() { public String toString() { return ("obj"); } })).containsEntry("a", 1).doesNotContainKey("b").containsEntry("c", "obj");
   }

}
