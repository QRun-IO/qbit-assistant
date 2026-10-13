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

package com.kingsrook.qbits.assistant.testing;


import java.io.IOException;
import java.net.ServerSocket;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;


/*******************************************************************************
 ** The fixture app served by a real QQQ application server (the `/qqq/v1` UI
 ** API plus the assistant routes) on a free port, so the assistant's tools go
 ** through exactly the pipeline the UI uses. One server at a time: starting
 ** one for a new instance stops the previous one.
 *******************************************************************************/
public final class UiApiTestServer
{
   private static QApplicationJavalinServer current;
   private static QInstance                 currentInstance;
   private static int                       currentPort;



   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private UiApiTestServer()
   {
   }



   /*******************************************************************************
    ** The fixture application.
    *******************************************************************************/
   public static class FixtureApplication extends AbstractQQQApplication
   {
      private final QInstance qInstance;



      /*******************************************************************************
       **
       *******************************************************************************/
      public FixtureApplication(QInstance qInstance)
      {
         this.qInstance = qInstance;
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public QInstance defineQInstance() throws QException
      {
         return (qInstance);
      }
   }



   /*******************************************************************************
    ** Serve this instance (if not already) and point the config's UI API base
    ** URL at it. Returns the base URL.
    *******************************************************************************/
   public static synchronized String serve(QInstance qInstance, AssistantQBitConfig config)
   {
      if(current == null || currentInstance != qInstance)
      {
         stop();
         try
         {
            currentPort = freePort();
            current = new QApplicationJavalinServer(new FixtureApplication(qInstance))
               .withPort(currentPort)
               .withServeFrontendNext(false)
               .withServeFrontendMaterialDashboard(false)
               .withServeLegacyUnversionedMiddlewareAPI(false);
            current.start();
            currentInstance = qInstance;
         }
         catch(Exception e)
         {
            throw (new IllegalStateException("Could not start the UI API test server", e));
         }
      }
      String baseUrl = "http://127.0.0.1:" + currentPort;
      config.setUiApiBaseUrl(baseUrl);
      return (baseUrl);
   }



   /*******************************************************************************
    ** Stop the running server, if any.
    *******************************************************************************/
   public static synchronized void stop()
   {
      if(current != null)
      {
         try
         {
            current.stop();
         }
         catch(Exception e)
         {
            // already stopped
         }
         current = null;
         currentInstance = null;
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static int freePort() throws IOException
   {
      try(ServerSocket socket = new ServerSocket(0))
      {
         return (socket.getLocalPort());
      }
   }

}
