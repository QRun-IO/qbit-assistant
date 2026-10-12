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

package com.kingsrook.qbits.assistant;


import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Session helpers for the owner record locks on the assistant* tables.
 **
 ** The assistant never creates sessions. These methods only add security key
 ** values to the user's OWN session:
 ** - ownerKey(session): the user's own id as the owner key, so the user can
 **   read and write their own conversations (the panel routes call this).
 ** - applySessionKeys(session): ownerKey plus, for users holding
 **   assistant.admin, the owner key type's all-access key. Hosts call it from
 **   their authentication customizer so admins see everyone's rows in the
 **   Assistant Admin app.
 *******************************************************************************/
public class AssistantSecurity
{

   /*******************************************************************************
    ** The id used as owner of assistant data: the user's id reference.
    *******************************************************************************/
   public static String userId(QSession session)
   {
      if(session == null || session.getUser() == null)
      {
         return (null);
      }
      String id = session.getUser().getIdReference();
      return (StringUtils.hasContent(id) ? id : null);
   }



   /*******************************************************************************
    ** Give the session its own owner key value (idempotent).
    *******************************************************************************/
   public static void ownerKey(QSession session, AssistantQBitConfig config)
   {
      String userId = userId(session);
      String keyType = config.getOwnerSecurityKeyType();
      if(userId != null && !session.hasSecurityKeyValue(keyType, userId, QFieldType.STRING))
      {
         session.withSecurityKeyValue(keyType, userId);
      }
   }



   /*******************************************************************************
    ** ownerKey, plus the all-access key for assistant.admin holders.
    *******************************************************************************/
   public static void applySessionKeys(QSession session, AssistantQBitConfig config)
   {
      if(session == null)
      {
         return;
      }
      ownerKey(session, config);

      String allAccess = AssistantMetaDataBuilder.allAccessKeyName(config.getOwnerSecurityKeyType());
      if(session.hasPermission(AssistantQBitConfig.ADMIN_PERMISSION) && !session.hasSecurityKeyValue(allAccess, true, QFieldType.BOOLEAN))
      {
         session.withSecurityKeyValue(allAccess, true);
      }
   }

}
