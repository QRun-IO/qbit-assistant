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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;


/*******************************************************************************
 ** The conversation table as users use it in the standard table UI (their own
 ** rows, through the owner record lock):
 ** - update: only title, pinned and status (ACTIVE / ARCHIVED) can change;
 **   the other fields (owner, classification, counters, context) are the
 **   assistant's own. A new title is encrypted (HIPAA mode) as the panel's
 **   writes are; a stored, still-encrypted title sent back unchanged is kept.
 ** - delete: refused while the conversation has an answer in progress (as
 **   DELETE /conversations/{id}); afterwards its child rows are deleted the
 **   same way that route deletes them, so nothing is orphaned.
 ** The assistant's own writes (system input source) pass through unchanged.
 *******************************************************************************/
public class AssistantConversationCustomizer implements TableCustomizerInterface
{
   public static final Set<String> USER_EDITABLE_FIELDS = Set.of("title", "pinned", "status");

   public static final String IN_PROGRESS = "This conversation has an answer in progress. Stop it first.";



   /*******************************************************************************
    ** Users change only title, pinned and status.
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      if(updateInput.getInputSource() == QInputSource.SYSTEM)
      {
         return (records);
      }
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      for(QRecord record : records)
      {
         for(String field : new ArrayList<>(record.getValues().keySet()))
         {
            if(!"id".equals(field) && !USER_EDITABLE_FIELDS.contains(field))
            {
               record.removeValue(field);
            }
         }
         Serializable status = record.getValue("status");
         if(record.getValues().containsKey("status") && !"ACTIVE".equals(status) && !"ARCHIVED".equals(status))
         {
            record.addError(new BadInputStatusMessage("Status must be Active or Archived."));
         }
         if(!isPreview && runtime != null && record.getValue("title") instanceof String title && !AssistantStore.isEncryptedValue(title))
         {
            record.setValue("title", runtime.getStore().encryptForStorage(AssistantConversation.TABLE_NAME, new QRecord().withValue("title", title)).getValue("title"));
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** A conversation with an answer in progress is not deleted.
    *******************************************************************************/
   @Override
   public List<QRecord> preDelete(DeleteInput deleteInput, List<QRecord> records, boolean isPreview) throws QException
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      if(runtime == null || deleteInput.getInputSource() == QInputSource.SYSTEM || records.isEmpty())
      {
         return (records);
      }
      AssistantApi api = new AssistantApi(runtime, QContext.getQSession());
      for(QRecord record : records)
      {
         if(hasAnswerInProgress(runtime, api, record.getValueInteger("id")))
         {
            record.addError(new BadInputStatusMessage(IN_PROGRESS));
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** Delete the deleted conversations' child rows, as the owner's rows.
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      if(runtime != null)
      {
         for(QRecord record : records)
         {
            if(record.getValueInteger("id") != null && record.getValueString("userId") != null)
            {
               runtime.getStore().deleteConversationChildren(record.getValueString("userId"), record.getValueInteger("id"));
            }
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** Whether a conversation (of any owner the session can see) has a running
    ** or cancelling turn that is not orphaned.
    *******************************************************************************/
   public static boolean hasAnswerInProgress(AssistantRuntime runtime, AssistantApi api, Integer conversationId) throws QException
   {
      if(conversationId == null)
      {
         return (false);
      }
      List<AssistantTurn> turns = runtime.getStore().query(AssistantTurn.TABLE_NAME, new QQueryFilter()
         .withCriteria("conversationId", QCriteriaOperator.EQUALS, conversationId)
         .withCriteria("status", QCriteriaOperator.IN, List.of("RUNNING", "CANCELLING")), AssistantTurn.class);
      return (turns.stream().anyMatch(api::inProgress));
   }
}
