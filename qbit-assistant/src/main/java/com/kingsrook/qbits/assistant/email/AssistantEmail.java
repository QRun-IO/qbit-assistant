/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2026.  Kingsrook, LLC
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

package com.kingsrook.qbits.assistant.email;


import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;


/*******************************************************************************
 ** Email delivery of a scheduled task's result, when the optional qbit-email
 ** is present. This class names no qbit-email type, so it loads without the
 ** jar; the adapter (AssistantEmailSender) is only loaded after the guard passes.
 **
 ** Hosts can call sendScheduledResult after a run; the scheduled-task runner
 ** does not yet wire email delivery. The gates below apply to every call:
 ** the task opted in, the host approved EXTERNAL delivery
 ** (AssistantQBitConfig.getEmailExternalApproved, until the approval rule
 ** exists), and there is a recipient address.
 *******************************************************************************/
public final class AssistantEmail
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AssistantEmail()
   {
   }



   /*******************************************************************************
    ** Whether qbit-email is on the classpath and produced in this instance.
    *******************************************************************************/
   public static boolean isAvailable(QInstance qInstance)
   {
      try
      {
         Class.forName("com.kingsrook.qbits.email.outbox.EmailService");
      }
      catch(ClassNotFoundException | LinkageError e)
      {
         return (false);
      }
      return (AssistantEmailSender.isInstalled(qInstance));
   }



   /*******************************************************************************
    ** Email a finished scheduled run to its owner, if the task opted in and
    ** every gate allows it. A missing email qbit is not an error; the status
    ** says what happened.
    *******************************************************************************/
   public static ScheduledEmailStatus sendScheduledResult(QInstance qInstance, AssistantQBitConfig config, ScheduledResultNotice notice) throws QException
   {
      if(notice == null || !notice.emailResult())
      {
         return (ScheduledEmailStatus.NOT_REQUESTED);
      }
      if(!isAvailable(qInstance))
      {
         return (ScheduledEmailStatus.NOT_AVAILABLE);
      }
      if(!Boolean.TRUE.equals(config.getEmailExternalApproved()))
      {
         return (ScheduledEmailStatus.NOT_APPROVED);
      }
      String recipient = recipient(config, notice.ownerUserId());
      if(recipient == null)
      {
         return (ScheduledEmailStatus.NO_RECIPIENT);
      }
      return (AssistantEmailSender.send(notice, recipient));
   }



   /*******************************************************************************
    ** The owner's address: the host's resolver, else the owner id when it is
    ** itself an address. Null when there is none.
    *******************************************************************************/
   static String recipient(AssistantQBitConfig config, String ownerUserId)
   {
      if(ownerUserId == null || ownerUserId.isBlank())
      {
         return (null);
      }
      String address = config.getEmailRecipientResolver() != null ? config.getEmailRecipientResolver().apply(ownerUserId) : (ownerUserId.contains("@") ? ownerUserId : null);
      return (address == null || address.isBlank() ? null : address.trim());
   }
}
