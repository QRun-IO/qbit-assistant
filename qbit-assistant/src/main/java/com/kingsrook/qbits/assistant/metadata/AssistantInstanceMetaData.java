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

package com.kingsrook.qbits.assistant.metadata;


import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.QSupplementalInstanceMetaData;


/*******************************************************************************
 ** The "assistant" supplemental instance meta-data: tells frontends (via
 ** /metaData) that the assistant is installed and enabled, so they probe
 ** /qqq/v1/assistant/config. Deliberately tiny: a format version and the
 ** routes' base path - no secrets, nothing user-specific (whether THIS user
 ** may use it is /config's job).
 *******************************************************************************/
public class AssistantInstanceMetaData implements QSupplementalInstanceMetaData
{
   public static final String NAME = "assistant";

   private Integer version  = 1;
   private String  basePath = "/qqq/v1/assistant";



   /*******************************************************************************
    ** The registered instance, or null.
    *******************************************************************************/
   public static AssistantInstanceMetaData of(QInstance qInstance)
   {
      return (QSupplementalInstanceMetaData.of(qInstance, NAME));
   }



   /*******************************************************************************
    ** Register it (idempotent).
    *******************************************************************************/
   public static AssistantInstanceMetaData register(QInstance qInstance)
   {
      return (QSupplementalInstanceMetaData.ofOrWithNew(qInstance, NAME, AssistantInstanceMetaData::new));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getName()
   {
      return (NAME);
   }



   /*******************************************************************************
    ** Getter for version
    *******************************************************************************/
   public Integer getVersion()
   {
      return (this.version);
   }



   /*******************************************************************************
    ** Getter for basePath
    *******************************************************************************/
   public String getBasePath()
   {
      return (this.basePath);
   }

}
