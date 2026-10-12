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


import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qqq.backend.core.actions.permissions.CustomPermissionChecker;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;
import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** Guards the Assistant Admin app, its tables and the retention process: the
 ** user needs the permission "assistant.admin" (QQQ's HAS_ACCESS level can
 ** only express "&lt;base&gt;.hasAccess", so a custom checker is used).
 *******************************************************************************/
public class AssistantAdminPermissionChecker implements CustomPermissionChecker
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void checkPermissionsThrowing(AbstractActionInput actionInput, MetaDataWithPermissionRules metaDataWithPermissionRules) throws QPermissionDeniedException
   {
      QSession session = QContext.getQSession();
      if(session == null || !session.hasPermission(AssistantQBitConfig.ADMIN_PERMISSION))
      {
         throw (new QPermissionDeniedException("Not authorized to access the assistant admin data."));
      }
   }

}
