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

package com.kingsrook.qbits.assistant.provider;


import java.time.Duration;
import com.kingsrook.qbits.assistant.engine.ModelPricing;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Standalone contracts moved from the runtime's component tests.
 *******************************************************************************/
class ProviderComponentsTest
{
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testPricing()
   {
      ModelPricing sonnet = ModelPricing.defaults().get("claude-sonnet-5-5");
      ModelUsage   usage  = new ModelUsage().withInputTokens(6000L).withOutputTokens(800L).withCacheReadTokens(12_000L).withCacheWriteTokens(1000L).withCacheWrite1hTokens(400L);
      //////////////////////////////////////////////////////////////////////////////////
      // 6000*2 + 800*10 + 12000*0.2 + 600*2.5 + 400*4 = 12000+8000+2400+1500+1600   //
      //////////////////////////////////////////////////////////////////////////////////
      assertThat(sonnet.costMicros(usage)).isEqualTo(25_500);
      assertThat(sonnet.costMicros(null)).isZero();
      assertThat(new ModelPricing().costMicros(usage)).isZero();
      assertThat(ModelPricing.defaults().get("claude-opus-5-5").getCacheReadUsdPerMTok()).isEqualTo(0.2);
      assertThat(usage.billableTokens()).isEqualTo(7800);
      assertThat(new ModelUsage().add(usage).add(null).getCacheReadTokens()).isEqualTo(12_000);
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
      cancelled.setOnCancel(() ->
      {
         throw (new IllegalStateException("closing"));
      });
      cancelled.cancel();
      cancelled.cancel();
      assertThat(cancelled.await(Duration.ofSeconds(1)).getStopReason()).isEqualTo(ModelResponse.STOP_CANCELLED);
      assertThat(new ModelProviderException(ModelProviderException.Kind.INVALID_REQUEST, 400, "x").isRetryable()).isFalse();
   }
}
