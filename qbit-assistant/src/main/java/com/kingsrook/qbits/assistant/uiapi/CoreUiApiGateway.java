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


import java.util.Locale;
import java.util.function.Supplier;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.HeaderedUiApiInvoker;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInvoker;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiResponse;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import com.kingsrook.qqq.openapi.model.HttpMethod;


/*******************************************************************************
 ** The assistant's gateway over core's {@link UiApiInvoker} (QRun-IO/qqq#1128,
 ** docs/UI-API-EXTENSIONS.md section 5): only spec'd /qqq/v1 operations (core
 ** and registered extensions), a loopback request through the identical
 ** Javalin pipeline, carrying the caller's own credentials exactly as they
 ** came in (session cookies, Authorization, API key). There is no service
 ** session and no way to elevate.
 *******************************************************************************/
public class CoreUiApiGateway implements UiApiGateway
{
   public static final String OWN_PREFIX     = "/assistant";
   public static final String CLIENT_CHANNEL = "assistant";

   private final Supplier<String> baseUrlSupplier;
   private final QInstance        qInstance;
   private volatile UiApiInvoker  invoker;



   /*******************************************************************************
    ** @param baseUrlSupplier this server's own root URL (e.g. http://127.0.0.1:8000)
    ** @param qInstance the instance (its spec extensions are invocable too)
    *******************************************************************************/
   public CoreUiApiGateway(Supplier<String> baseUrlSupplier, QInstance qInstance)
   {
      this.baseUrlSupplier = baseUrlSupplier;
      this.qInstance = qInstance;
   }



   /*******************************************************************************
    ** The invoker, over a v1 middleware version that knows the instance's
    ** extensions (built on first use: the instance is complete by then).
    *******************************************************************************/
   UiApiInvoker invoker()
   {
      UiApiInvoker current = invoker;
      if(current == null)
      {
         MiddlewareVersionV1 version = new MiddlewareVersionV1();
         version.setQInstance(qInstance);
         ////////////////////////////////////////////////////////////////////////////
         // the same as UiApiInvoker.forServer(server, "assistant"): this server's  //
         // loopback address, its first version, the client channel "assistant"     //
         // (a call adds only the detail after the slash: the turn id)              //
         ////////////////////////////////////////////////////////////////////////////
         current = new HeaderedUiApiInvoker(baseUrlSupplier, version).withClientChannel(CLIENT_CHANNEL);
         invoker = current;
      }
      return (current);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public boolean isInvocable(String method, String pathTemplate)
   {
      ///////////////////////////////////////////////////////////////////
      // never the assistant's own operations (no recursion, no turns   //
      // started by a turn)                                             //
      ///////////////////////////////////////////////////////////////////
      if(pathTemplate == null || pathTemplate.equals(OWN_PREFIX) || pathTemplate.startsWith(OWN_PREFIX + "/"))
      {
         return (false);
      }
      try
      {
         return (invoker().findOperation(HttpMethod.valueOf(method.toUpperCase(Locale.ROOT)), pathTemplate).isPresent());
      }
      catch(IllegalArgumentException e)
      {
         return (false);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public UiApiResult invoke(UiApiCall call) throws QException
   {
      if(call == null || call.getMethod() == null || call.getPathTemplate() == null)
      {
         throw (new QException("A UI API call needs a method and a path template"));
      }
      if(!isInvocable(call.getMethod(), call.getPathTemplate()))
      {
         throw (new QException("Not a spec'd UI API operation: " + call.operation()));
      }
      UiApiCredentials credentials = call.getCredentials() != null ? call.getCredentials()
         : (call.getSessionUUID() == null ? null : UiApiCredentials.ofSessionUUID(call.getSessionUUID()));
      if(credentials == null || !credentials.isPresent())
      {
         throw (new QException("A session is required to invoke " + call.operation()));
      }

      HeaderedUiApiInvoker.HeaderedRequest request = new HeaderedUiApiInvoker.HeaderedRequest();
      request.withMethod(HttpMethod.valueOf(call.getMethod().toUpperCase(Locale.ROOT)))
         .withPath(call.getPathTemplate())
         .withCredentials(credentials)
         .withUserTimezone(call.getUserTimezone())
         .withInvokedBy(call.getInvokedBy())
         .withTimeout(call.getTimeout())
         .withClientDetail(call.getClientDetail());
      call.getHeaders().forEach(request::withHeader);
      call.getPathParams().forEach(request::withPathParam);
      call.getQueryParams().forEach(request::withQueryParam);
      call.getFormParams().forEach(request::withFormParam);
      if(call.getJsonBody() instanceof String json)
      {
         ////////////////////////////////////////////////////////////////
         // a pre-serialized body (e.g. a QQueryFilter as QQQ writes   //
         // it): parse it, the invoker serializes the object           //
         ////////////////////////////////////////////////////////////////
         try
         {
            request.withJsonBody(JsonUtils.toObject(json, Object.class));
         }
         catch(Exception e)
         {
            throw (new QException("The body of " + call.operation() + " is not valid JSON", e));
         }
      }
      else if(call.getJsonBody() != null)
      {
         request.withJsonBody(call.getJsonBody());
      }
      UiApiResponse response = invoker().invoke(request);
      return (new UiApiResult(response.getStatus(), response.getBody()));
   }

}
