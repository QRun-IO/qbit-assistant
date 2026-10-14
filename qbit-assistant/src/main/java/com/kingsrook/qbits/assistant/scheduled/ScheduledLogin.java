/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.assistant.scheduled;


import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.model.AssistantScheduledTask;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleDispatcher;


/*******************************************************************************
 ** Log in exactly as a UI client, then resolve the returned session UUID with
 ** the host's authentication module. Never authenticate directly with a token
 ** in process, create an automated session, or manufacture session permissions.
 *******************************************************************************/
public class ScheduledLogin
{
   /*******************************************************************************
    ** No response body or transport exception can leak an access token.
    *******************************************************************************/
   public QSession login(AssistantRuntime runtime, AssistantScheduledTask task) throws QException
   {
      String token = ScheduledTasks.credential(runtime.getConfig(), task);
      String base = runtime.uiApiBaseUrl();
      if(base == null || base.isBlank())
      {
         throw (new QException("Configure uiApiBaseUrl before scheduling assistant tasks."));
      }
      String uuid;
      try
      {
         HttpRequest request = HttpRequest.newBuilder(URI.create(base.replaceAll("/+$", "") + "/qqq/v1/manageSession"))
            .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
            .header("X-QQQ-Client", "assistant/scheduled/" + task.getId())
            .POST(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(Map.of("accessToken", token)), StandardCharsets.UTF_8)).build();
         HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
         if(response.statusCode() != 200)
         {
            throw (new QException("Scheduled login manageSession answered HTTP " + response.statusCode() + "."));
         }
         uuid = response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
            .filter(cookie -> "sessionUUID".equals(cookie.getName())).map(HttpCookie::getValue).findFirst().orElse("");
         if(uuid.isBlank())
         {
            uuid = new ObjectMapper().readTree(response.body()).path("uuid").asText();
         }
         if(uuid.isBlank())
         {
            throw (new QException("Scheduled login returned no session UUID."));
         }
      }
      catch(QException e)
      {
         throw (e);
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         throw (new QException("Scheduled login interrupted."));
      }
      catch(Exception e)
      {
         throw (new QException("Scheduled login failed."));
      }
      QSession session;
      try
      {
         var instance = runtime.getQInstance();
         String key = QAuthenticationType.MOCK.equals(instance.getAuthentication().getType()) ? "sessionId" : "sessionUUID";
         session = new QAuthenticationModuleDispatcher().getQModule(instance.getAuthentication()).createSession(instance, Map.of(key, uuid));
      }
      catch(Exception e)
      {
         throw (new QException("Scheduled login session could not be resolved."));
      }
      if(!task.getRunAsUserId().equals(AssistantSecurity.userId(session)))
      {
         throw (new QException("The credential belongs to another user."));
      }
      if(!uuid.equals(session.getUuid()))
      {
         throw (new QException("The authentication module did not preserve the logged-in session UUID."));
      }
      AssistantSecurity.applySessionKeys(session, runtime.getConfig());
      return (session);
   }
}
