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

package com.kingsrook.qbits.assistant.uiapi;


import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.middleware.javalin.specs.AbstractEndpointSpec;
import com.kingsrook.qqq.middleware.javalin.specs.CompleteOperation;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;


/*******************************************************************************
 ** Stand-in for core's `UiApiInvoker` (QRun-IO/qqq#1128; REPLACE with an
 ** adapter over it once a QQQ release has it). Same contract:
 **
 ** - only spec'd `/qqq/v1` operations (from {@link MiddlewareVersionV1}'s
 **   endpoint specs) can be called; anything else is refused before a request
 **   is made, so raw routes (including the assistant's own) are unreachable;
 ** - a loopback HTTP request to this server, carrying the user's own session
 **   cookie: the identical Javalin pipeline (session setup, permission checks,
 **   record locks, validation, audit, error mapping) the UI goes through. There
 **   is no service session and no way to elevate;
 ** - X-QQQ-Invoked-By marks the request for the access log.
 **
 ** Why loopback rather than calling spec executors directly: buildInput, the
 ** version's preExecute, filters and error mapping hang off the Javalin
 ** pipeline; calling executors would skip some of them and drift over time.
 *******************************************************************************/
public class LoopbackUiApiGateway implements UiApiGateway
{
   public static final String BASE_PATH         = "/qqq/v1";
   public static final String INVOKED_BY_HEADER = "X-QQQ-Invoked-By";
   public static final String TIMEZONE_HEADER   = "X-QQQ-UserTimezone";

   private static final Set<String> SPEC_OPERATIONS = specOperations();

   private final Supplier<String> baseUrlSupplier;
   private final boolean          mockAuthentication;
   private final HttpClient       httpClient;



   /*******************************************************************************
    ** @param baseUrlSupplier the server's own root URL, e.g. http://127.0.0.1:8000
    ** @param qInstance the instance (MOCK authentication reads the sessionId
    **        cookie rather than sessionUUID, so both are sent there)
    *******************************************************************************/
   public LoopbackUiApiGateway(Supplier<String> baseUrlSupplier, QInstance qInstance)
   {
      this.baseUrlSupplier = baseUrlSupplier;
      this.mockAuthentication = qInstance != null && qInstance.getAuthentication() != null && QAuthenticationType.MOCK.equals(qInstance.getAuthentication().getType());
      this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build();
   }



   /*******************************************************************************
    ** "METHOD /path/template" of every v1 endpoint spec.
    *******************************************************************************/
   static Set<String> specOperations()
   {
      Set<String> operations = new LinkedHashSet<>();
      for(AbstractEndpointSpec<?, ?, ?> spec : new MiddlewareVersionV1().getEndpointSpecs())
      {
         CompleteOperation operation = spec.defineCompleteOperation();
         operations.add(operation.getHttpMethod().name() + " " + operation.getPath());
      }
      return (Collections.unmodifiableSet(operations));
   }



   /*******************************************************************************
    ** The spec'd operations ("METHOD template").
    *******************************************************************************/
   public static Set<String> getSpecOperations()
   {
      return (SPEC_OPERATIONS);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public boolean isInvocable(String method, String pathTemplate)
   {
      return (SPEC_OPERATIONS.contains(method + " " + pathTemplate));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public UiApiResult invoke(UiApiCall call) throws QException
   {
      if(call == null || !StringUtils.hasContent(call.getMethod()) || !StringUtils.hasContent(call.getPathTemplate()))
      {
         throw (new QException("A UI API call needs a method and a path template"));
      }
      if(!isInvocable(call.getMethod(), call.getPathTemplate()))
      {
         throw (new QException("Not a spec'd UI API operation: " + call.operation()));
      }
      if(!StringUtils.hasContent(call.getSessionUUID()))
      {
         throw (new QException("A session is required to invoke " + call.operation()));
      }

      HttpRequest request = buildRequest(call);
      try
      {
         HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
         return (new UiApiResult(response.statusCode(), response.body()));
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         throw (new QException("Interrupted invoking " + call.operation(), e));
      }
      catch(IOException e)
      {
         throw (new QException("Error invoking " + call.operation() + ": " + e.getMessage(), e));
      }
   }



   /*******************************************************************************
    ** The HTTP request: path parameters filled and encoded, query string, JSON
    ** body, the session cookie and the marker headers.
    *******************************************************************************/
   HttpRequest buildRequest(UiApiCall call) throws QException
   {
      String path = call.getPathTemplate();
      for(Map.Entry<String, String> entry : call.getPathParams().entrySet())
      {
         if(entry.getValue() == null)
         {
            throw (new QException("Missing path parameter " + entry.getKey() + " for " + call.operation()));
         }
         path = path.replace("{" + entry.getKey() + "}", encode(entry.getValue()));
      }
      if(path.contains("{"))
      {
         throw (new QException("Missing path parameter(s) for " + call.operation() + ": " + path));
      }

      StringJoiner query = new StringJoiner("&", "?", "").setEmptyValue("");
      call.getQueryParams().forEach((name, value) -> query.add(encode(name) + "=" + encode(value)));

      String base = baseUrlSupplier.get();
      if(!StringUtils.hasContent(base))
      {
         throw (new QException("The UI API base URL is not known yet"));
      }
      URI uri = URI.create(base.replaceAll("/+$", "") + BASE_PATH + path + query);

      HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(call.getTimeout() == null ? Duration.ofSeconds(60) : call.getTimeout());
      String cookie = "sessionUUID=" + call.getSessionUUID();
      if(mockAuthentication)
      {
         cookie += "; sessionId=" + call.getSessionUUID();
      }
      builder.header("Cookie", cookie);
      builder.header("Accept", "application/json");
      builder.header(INVOKED_BY_HEADER, StringUtils.hasContent(call.getInvokedBy()) ? call.getInvokedBy() : "assistant");
      if(StringUtils.hasContent(call.getUserTimezone()))
      {
         builder.header(TIMEZONE_HEADER, call.getUserTimezone());
      }

      if(call.getJsonBody() != null)
      {
         builder.header("Content-Type", "application/json");
         builder.method(call.getMethod(), HttpRequest.BodyPublishers.ofString(call.getJsonBody() instanceof String s ? s : JsonUtils.toJson(call.getJsonBody()), StandardCharsets.UTF_8));
      }
      else
      {
         builder.method(call.getMethod(), HttpRequest.BodyPublishers.noBody());
      }
      return (builder.build());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String encode(String value)
   {
      return (URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
   }

}
