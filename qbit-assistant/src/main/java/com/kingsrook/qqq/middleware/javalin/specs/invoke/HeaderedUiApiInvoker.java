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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.middleware.javalin.specs.AbstractMiddlewareVersion;


/*******************************************************************************
 ** Core's {@link UiApiInvoker} plus extra request headers (the assistant's
 ** X-QQQ-AI-Route). Core's {@link UiApiRequest} carries none, and the hook that
 ** builds the HTTP request is package-private, so this subclass lives in core's
 ** package to override it. Everything else (the operation allow-list, the
 ** credentials, the client channel, the depth limit) is core's own.
 **
 ** stand-in until QRun-IO/qqq adds UiApiRequest.withHeader; delete then
 *******************************************************************************/
public class HeaderedUiApiInvoker extends UiApiInvoker
{
   /** Headers a request cannot set itself (lower case): core sets these. */
   private static final Set<String> RESERVED_HEADERS = Set.of("cookie", "authorization", "x-api-key", "content-type", "accept",
      INVOKED_BY_HEADER.toLowerCase(Locale.ROOT), TIMEZONE_HEADER.toLowerCase(Locale.ROOT), CLIENT_CHANNEL_HEADER.toLowerCase(Locale.ROOT));



   /*******************************************************************************
    ** @param baseUrlSupplier the server's own root URL
    ** @param middlewareVersion the version whose operations can be invoked
    *******************************************************************************/
   public HeaderedUiApiInvoker(Supplier<String> baseUrlSupplier, AbstractMiddlewareVersion middlewareVersion)
   {
      super(baseUrlSupplier, middlewareVersion);
   }



   /*******************************************************************************
    ** Core's request, then the extra headers of a {@link HeaderedRequest}
    ** (never the reserved ones).
    *******************************************************************************/
   @Override
   HttpRequest buildHttpRequest(UiApiOperation operation, UiApiRequest request) throws QException
   {
      HttpRequest built = super.buildHttpRequest(operation, request);
      HttpRequest.Builder builder = HttpRequest.newBuilder(built, (name, value) -> true);
      ////////////////////////////////////////////////////////////////////////////
      // RC.6 strips slashes from client detail. Preserve nested attribution    //
      // while keeping the channel chosen by core, never by extra headers.     //
      ////////////////////////////////////////////////////////////////////////////
      if(request.getClientDetail() != null && request.getClientDetail().contains("/"))
      {
         String channel = built.headers().firstValue(CLIENT_CHANNEL_HEADER).orElseThrow().split("/", 2)[0];
         String detail = request.getClientDetail().replaceAll("[^A-Za-z0-9._:/-]", "");
         builder.setHeader(CLIENT_CHANNEL_HEADER, channel + "/" + detail);
      }
      if(!(request instanceof HeaderedRequest headered))
      {
         return (builder.build());
      }
      headered.getHeaders().forEach((name, value) ->
      {
         if(!RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT)))
         {
            builder.header(name, value);
         }
      });
      return (builder.build());
   }



   /*******************************************************************************
    ** A UI API request that also carries extra headers.
    *******************************************************************************/
   public static class HeaderedRequest extends UiApiRequest
   {
      private final Map<String, String> headers = new LinkedHashMap<>();



      /*******************************************************************************
       ** Add a header (ignored when the name or value is null).
       *******************************************************************************/
      public HeaderedRequest withHeader(String name, String value)
      {
         if(name != null && value != null)
         {
            headers.put(name, value);
         }
         return (this);
      }



      /*******************************************************************************
       ** Getter for headers
       *******************************************************************************/
      public Map<String, String> getHeaders()
      {
         return (headers);
      }
   }

}
