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


import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;


/*******************************************************************************
 ** Registers AssistantRouteProvider with the Javalin server through the
 ** instance's QJavalinMetaData (once). Only touched when
 ** qqq-middleware-javalin is on the classpath (the producer checks first).
 *******************************************************************************/
public class AssistantRoutes
{

   /*******************************************************************************
    **
    *******************************************************************************/
   public static void register(QInstance qInstance)
   {
      QJavalinMetaData javalinMetaData = QJavalinMetaData.ofOrWithNew(qInstance);
      boolean registered = CollectionUtils.nonNullList(javalinMetaData.getAdditionalRouteProviderReferences()).stream()
         .anyMatch(reference -> reference != null && AssistantRouteProvider.class.getName().equals(reference.getName()));
      if(!registered)
      {
         javalinMetaData.withAdditionalRouteProviderReference(new QCodeReference(AssistantRouteProvider.class));
      }
   }

}
