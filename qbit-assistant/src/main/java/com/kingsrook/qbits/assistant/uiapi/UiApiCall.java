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


import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;


/*******************************************************************************
 ** One call of a spec'd `/qqq/v1` operation, made with a user's own session.
 ** Mirrors core's `UiApiRequest` (QRun-IO/qqq#1128, docs/UI-API-EXTENSIONS.md
 ** section 5), so the stand-in gateway can be swapped for core's invoker.
 *******************************************************************************/
public class UiApiCall
{
   private final String              method;
   private final String              pathTemplate;
   private final Map<String, String> pathParams  = new LinkedHashMap<>();
   private final Map<String, String> queryParams = new LinkedHashMap<>();
   private Object                    jsonBody;
   private String                    sessionUUID;
   private UiApiCredentials          credentials;
   private String                    userTimezone;
   private String                    invokedBy   = "assistant";
   private Duration                  timeout     = Duration.ofSeconds(60);



   /*******************************************************************************
    ** A call of METHOD pathTemplate: the template as in openapi.yaml, relative
    ** to /qqq/v1 (e.g. "/table/{tableName}/query").
    *******************************************************************************/
   public UiApiCall(String method, String pathTemplate)
   {
      this.method = method;
      this.pathTemplate = pathTemplate;
   }



   /*******************************************************************************
    ** "METHOD template": the operation's identity in logs and the allow-list.
    *******************************************************************************/
   public String operation()
   {
      return (method + " " + pathTemplate);
   }



   /*******************************************************************************
    ** Getter for method
    *******************************************************************************/
   public String getMethod()
   {
      return (method);
   }



   /*******************************************************************************
    ** Getter for pathTemplate
    *******************************************************************************/
   public String getPathTemplate()
   {
      return (pathTemplate);
   }



   /*******************************************************************************
    ** Add a path parameter (URL-encoded by the gateway).
    *******************************************************************************/
   public UiApiCall withPathParam(String name, Object value)
   {
      pathParams.put(name, value == null ? null : String.valueOf(value));
      return (this);
   }



   /*******************************************************************************
    ** Getter for pathParams
    *******************************************************************************/
   public Map<String, String> getPathParams()
   {
      return (pathParams);
   }



   /*******************************************************************************
    ** Add a query parameter (ignored when null).
    *******************************************************************************/
   public UiApiCall withQueryParam(String name, Object value)
   {
      if(value != null)
      {
         queryParams.put(name, String.valueOf(value));
      }
      return (this);
   }



   /*******************************************************************************
    ** Getter for queryParams
    *******************************************************************************/
   public Map<String, String> getQueryParams()
   {
      return (queryParams);
   }



   /*******************************************************************************
    ** Fluent setter for jsonBody
    *******************************************************************************/
   public UiApiCall withJsonBody(Object jsonBody)
   {
      this.jsonBody = jsonBody;
      return (this);
   }



   /*******************************************************************************
    ** Getter for jsonBody
    *******************************************************************************/
   public Object getJsonBody()
   {
      return (jsonBody);
   }



   /*******************************************************************************
    ** The user's own session UUID (required): the call is authenticated exactly
    ** as the user's browser request would be.
    *******************************************************************************/
   public UiApiCall withSessionUUID(String sessionUUID)
   {
      this.sessionUUID = sessionUUID;
      return (this);
   }



   /*******************************************************************************
    ** The caller's credentials exactly as their request carried them (session
    ** cookies, Authorization, API key); preferred over the session UUID alone.
    ** Never logged (their toString is redacted).
    *******************************************************************************/
   public UiApiCall withCredentials(UiApiCredentials credentials)
   {
      this.credentials = credentials;
      return (this);
   }



   /*******************************************************************************
    ** Getter for credentials
    *******************************************************************************/
   public UiApiCredentials getCredentials()
   {
      return (credentials);
   }



   /*******************************************************************************
    ** Getter for sessionUUID
    *******************************************************************************/
   public String getSessionUUID()
   {
      return (sessionUUID);
   }



   /*******************************************************************************
    ** Fluent setter for userTimezone
    *******************************************************************************/
   public UiApiCall withUserTimezone(String userTimezone)
   {
      this.userTimezone = userTimezone;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userTimezone
    *******************************************************************************/
   public String getUserTimezone()
   {
      return (userTimezone);
   }



   /*******************************************************************************
    ** Fluent setter for invokedBy (sent as X-QQQ-Invoked-By)
    *******************************************************************************/
   public UiApiCall withInvokedBy(String invokedBy)
   {
      this.invokedBy = invokedBy;
      return (this);
   }



   /*******************************************************************************
    ** Getter for invokedBy
    *******************************************************************************/
   public String getInvokedBy()
   {
      return (invokedBy);
   }



   /*******************************************************************************
    ** Fluent setter for timeout
    *******************************************************************************/
   public UiApiCall withTimeout(Duration timeout)
   {
      this.timeout = timeout;
      return (this);
   }



   /*******************************************************************************
    ** Getter for timeout
    *******************************************************************************/
   public Duration getTimeout()
   {
      return (timeout);
   }

}
