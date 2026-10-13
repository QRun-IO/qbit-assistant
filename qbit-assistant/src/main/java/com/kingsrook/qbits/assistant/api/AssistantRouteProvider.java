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

package com.kingsrook.qbits.assistant.api;


import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import io.javalin.config.JavalinConfig;


/*******************************************************************************
 ** No routes any more: the assistant's endpoints are spec'd /qqq/v1
 ** operations ({@link com.kingsrook.qbits.assistant.api.specs.AssistantSpecs}).
 ** What is left is a before-handler that learns this server's local port from
 ** the first /qqq/v1 request, so the tools (and Explain access) can call the
 ** UI API on 127.0.0.1 before any assistant operation has run.
 *******************************************************************************/
public class AssistantRouteProvider implements QJavalinRouteProviderInterface
{
   private static final QLogger LOG = QLogger.getLogger(AssistantRouteProvider.class);

   public static final String BASE_PATH = "/qqq/v1/assistant";

   private QInstance qInstance;



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void setQInstance(QInstance qInstance)
   {
      this.qInstance = qInstance;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void acceptJavalinConfig(JavalinConfig config)
   {
      config.routes.before("/qqq/v1/*", ctx ->
      {
         AssistantRuntime runtime = AssistantRuntime.of(qInstance);
         if(runtime != null && runtime.uiApiBaseUrl() == null)
         {
            try
            {
               runtime.learnLocalPort(ctx.req().getLocalPort());
            }
            catch(RuntimeException e)
            {
               LOG.debug("Could not read the local port", e);
            }
         }
      });
   }

}
