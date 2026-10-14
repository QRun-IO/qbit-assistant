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


import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qbits.email.api.EmailApi;
import com.kingsrook.qbits.email.api.EmailApiCategory;
import com.kingsrook.qbits.email.api.EmailApiRequest;
import com.kingsrook.qbits.email.api.EmailApiResult;
import com.kingsrook.qbits.email.api.EmailApiTemplate;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;


/*******************************************************************************
 ** The only class that touches qbit-email. Registers the scheduled-result
 ** template on first use (never overwriting edits) and queues one message per
 ** recipient. Addresses, summaries and bodies are never logged.
 *******************************************************************************/
final class AssistantEmailSender
{
   static final String TEMPLATE_NAME   = "assistant_scheduled_result";
   static final String SECURE_TEMPLATE = "email_secure_message";
   static final String SENDER_QBIT     = "assistant";
   static final String SENDER_PROCESS  = "assistantScheduledTask";

   private static final int MAX_SUMMARY = 280;



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AssistantEmailSender()
   {
   }



   /*******************************************************************************
    ** Whether the email qbit is produced in this instance.
    *******************************************************************************/
   static boolean isInstalled(QInstance qInstance)
   {
      return (EmailApi.isInstalled(qInstance));
   }



   /*******************************************************************************
    ** Queue one message to one recipient. A result classified above PUBLIC
    ** sends only the link, with the secure-message pattern.
    *******************************************************************************/
   static ScheduledEmailStatus send(ScheduledResultNotice notice, String recipient) throws QException
   {
      boolean linkOnly = notice.classification() != null && notice.classification().isAtLeast(DataClassification.CONFIDENTIAL);

      EmailApiRequest request = new EmailApiRequest().withTo(recipient)
         .withIdempotencyKey("assistant:scheduled:" + notice.runId())
         .withLinkedRecord(notice.runTable(), notice.runId())
         .withSender(SENDER_QBIT, SENDER_PROCESS);

      Map<String, Serializable> variables = new LinkedHashMap<>();
      variables.put("link", notice.link());
      if(linkOnly)
      {
         request.withTemplateName(SECURE_TEMPLATE);
      }
      else
      {
         ensureTemplate();
         request.withTemplateName(TEMPLATE_NAME);
         variables.put("taskName", notice.taskName());
         variables.put("runTime", notice.runTime() == null ? null : notice.runTime().toString());
         variables.put("summary", summary(notice.summary()));
      }
      request.withVariables(variables);

      return (map(EmailApi.send(request)));
   }



   /*******************************************************************************
    ** One line, cut short; the full model output never goes in an email.
    *******************************************************************************/
   static String summary(String value)
   {
      if(value == null)
      {
         return (null);
      }
      String oneLine = value.replaceAll("\\s+", " ").trim();
      return (oneLine.length() <= MAX_SUMMARY ? oneLine : oneLine.substring(0, MAX_SUMMARY - 3) + "...");
   }



   /*******************************************************************************
    ** Outbox result to the qbit's own vocabulary.
    *******************************************************************************/
   private static ScheduledEmailStatus map(EmailApiResult result)
   {
      return (switch(result.status())
      {
         case QUEUED -> ScheduledEmailStatus.QUEUED;
         case DUPLICATE -> ScheduledEmailStatus.DUPLICATE;
         case SUPPRESSED -> ScheduledEmailStatus.SUPPRESSED;
         case REFUSED -> ScheduledEmailStatus.REFUSED;
      });
   }



   /*******************************************************************************
    ** Register the NOTIFICATION template once; an existing template is left alone.
    *******************************************************************************/
   static void ensureTemplate() throws QException
   {
      EmailApi.ensureTemplate(new EmailApiTemplate()
         .withName(TEMPLATE_NAME)
         .withLabel("Assistant scheduled task result")
         .withCategory(EmailApiCategory.NOTIFICATION)
         .withKind("MARKDOWN")
         .withOwnerQbit(SENDER_QBIT)
         .withSubject("Assistant task result: {{taskName}}")
         .withBody("Your scheduled assistant task **{{taskName}}** finished at {{runTime}}.\n\n{{summary}}\n\n[Open the run]({{link}}){.button}")
         .withVariable("taskName", "Task name", "STRING", true, "CONFIDENTIAL")
         .withVariable("runTime", "Run time", "DATE_TIME", true, "PUBLIC")
         .withVariable("summary", "Short summary", "STRING", false, "CONFIDENTIAL")
         .withVariable("link", "Link to the run", "URL", true, "PUBLIC")
         .withSampleVariables("{\"taskName\":\"Weekly review\",\"runTime\":\"2026-10-12T09:00:00Z\",\"summary\":\"Three items need attention.\",\"link\":\"https://app.example.com/app/assistant_run/1\"}"));
   }
}
