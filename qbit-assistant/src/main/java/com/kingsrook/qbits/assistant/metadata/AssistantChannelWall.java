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


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantQBitProducer;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.ClientChannelsMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.SourceQBitAware;


/*******************************************************************************
 ** Register and verify the assistant's configuration wall beneath user permissions.
 *******************************************************************************/
public final class AssistantChannelWall
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AssistantChannelWall()
   {
   }



   /*******************************************************************************
    ** Enumerate the instance so new assistant metadata cannot miss registration.
    ** Preserve host permissions and other denied channels.
    *******************************************************************************/
   public static void register(QInstance qInstance)
   {
      ClientChannelsMetaData.ofOrWithNew(qInstance).withDeniedPattern("assistant", "assistant_*");
      for(MetaDataWithPermissionRules object : objects(qInstance))
      {
         QPermissionRules rules = object.getPermissionRules();
         rules = rules == null ? qInstance.getDefaultPermissionRules().clone() : rules.clone();
         Set<String> denied = new HashSet<>(rules.getDeniedClients() == null ? Set.of() : rules.getDeniedClients());
         denied.add("assistant");
         object.setPermissionRules(rules.withDeniedClients(denied));
      }
   }



   /*******************************************************************************
    ** Fail before runtime initialization if a host customization opens the wall.
    *******************************************************************************/
   public static void validate(QInstance qInstance)
   {
      ClientChannelsMetaData channels = ClientChannelsMetaData.of(qInstance);
      for(MetaDataWithPermissionRules object : objects(qInstance))
      {
         QPermissionRules rules = object.getPermissionRules() == null ? qInstance.getDefaultPermissionRules() : object.getPermissionRules();
         if(!channels.isDenied("assistant", object.getName(), rules))
         {
            throw (new IllegalStateException("Assistant channel wall: " + object.getName() + " must be denied to client channel assistant before startup."));
         }
      }
   }



   /*******************************************************************************
    ** Tables, processes, reports and widgets, including host-added assistant objects.
    *******************************************************************************/
   private static List<MetaDataWithPermissionRules> objects(QInstance qInstance)
   {
      List<MetaDataWithPermissionRules> objects = new ArrayList<>();
      objects.addAll(qInstance.getTables().values());
      objects.addAll(qInstance.getProcesses().values());
      objects.addAll(qInstance.getReports().values());
      objects.addAll(qInstance.getWidgets().values());
      return (objects.stream().filter(object -> (object.getName() != null && object.getName().startsWith("assistant"))
         || (object instanceof SourceQBitAware source && (AssistantQBitProducer.GROUP_ID + ":" + AssistantQBitProducer.ARTIFACT_ID).equals(source.getSourceQBitName()))).toList());
   }
}
