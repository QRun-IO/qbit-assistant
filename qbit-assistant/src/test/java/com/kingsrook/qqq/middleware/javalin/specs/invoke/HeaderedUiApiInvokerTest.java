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

package com.kingsrook.qqq.middleware.javalin.specs.invoke;


import java.net.http.HttpRequest;
import java.util.List;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Extra route headers cannot override core-owned credentials or attribution.
 *******************************************************************************/
class HeaderedUiApiInvokerTest
{
   /*******************************************************************************
    ** Non-reserved headers pass through; reserved headers are ignored regardless
    ** of case, retaining only values supplied through core's request properties.
    *******************************************************************************/
   @Test
   void forwardsOnlyNonReservedHeaders() throws Exception
   {
      HeaderedUiApiInvoker invoker = new HeaderedUiApiInvoker(() -> "http://127.0.0.1:8000", new MiddlewareVersionV1());
      invoker.withClientChannel("assistant");
      HeaderedUiApiInvoker.HeaderedRequest request = new HeaderedUiApiInvoker.HeaderedRequest();
      request.withMethod(HttpMethod.POST).withPath("/table/{tableName}/query").withPathParam("tableName", "page")
         .withCredentials(new UiApiCredentials("session-value", null, "Bearer credential-value", "key-value"))
         .withJsonBody(java.util.Map.of()).withUserTimezone("America/Chicago").withInvokedBy("turn-engine").withClientDetail("scheduled/42");
      for(String name : List.of("cOoKiE", "AUTHORIZATION", "X-API-Key", "Content-Type", "ACCEPT", "X-QQQ-Invoked-By", "x-qqq-usertimezone", "X-QQQ-CLIENT"))
      {
         request.withHeader(name, "must-not-forward");
      }
      request.withHeader("X-QQQ-AI-Route", "phi-route").withHeader("X-Request-Tag", "tag-value").withHeader(null, "ignored").withHeader("X-Null", null);
      UiApiOperation operation = invoker.findOperation(HttpMethod.POST, request.getPath()).orElseThrow();
      HttpRequest built = invoker.buildHttpRequest(operation, request);
      assertThat(built.headers().allValues("Cookie")).containsExactly("sessionUUID=session-value");
      assertThat(built.headers().allValues("Authorization")).containsExactly("Bearer credential-value");
      assertThat(built.headers().allValues("x-api-key")).containsExactly("key-value");
      assertThat(built.headers().allValues("Content-Type")).containsExactly("application/json");
      assertThat(built.headers().allValues("Accept")).isEmpty();
      assertThat(built.headers().allValues(UiApiInvoker.INVOKED_BY_HEADER)).containsExactly("turn-engine");
      assertThat(built.headers().allValues(UiApiInvoker.TIMEZONE_HEADER)).containsExactly("America/Chicago");
      assertThat(built.headers().allValues(UiApiInvoker.CLIENT_CHANNEL_HEADER)).containsExactly("assistant/scheduled/42");
      assertThat(built.headers().allValues("X-QQQ-AI-Route")).containsExactly("phi-route");
      assertThat(built.headers().allValues("X-Request-Tag")).containsExactly("tag-value");
      assertThat(built.headers().allValues("X-Null")).isEmpty();
      assertThat(built.headers().map().values()).allSatisfy(values -> assertThat(values).doesNotContain("must-not-forward"));
   }



   /*******************************************************************************
    ** Ordinary core requests retain attribution without requiring extra headers.
    *******************************************************************************/
   @Test
   void ordinaryRequestPreservesCoreHeaders() throws Exception
   {
      HeaderedUiApiInvoker invoker = new HeaderedUiApiInvoker(() -> "http://127.0.0.1:8000", new MiddlewareVersionV1());
      invoker.withClientChannel("assistant");
      UiApiRequest request = new UiApiRequest().withMethod(HttpMethod.GET).withPath("/metaData").withClientDetail("turn-42");
      HttpRequest built = invoker.buildHttpRequest(invoker.findOperation(HttpMethod.GET, request.getPath()).orElseThrow(), request);
      assertThat(built.headers().allValues(UiApiInvoker.CLIENT_CHANNEL_HEADER)).containsExactly("assistant/turn-42");
      assertThat(built.headers().allValues("X-QQQ-AI-Route")).isEmpty();
   }
}
