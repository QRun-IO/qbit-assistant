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

package com.kingsrook.qbits.assistant.processes;


import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.GoldenTasks.filter;
import static com.kingsrook.qbits.assistant.testing.GoldenTasks.map;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The retention process: old conversations (and everything under them) go;
 ** old tool results are nulled, their audit rows stay.
 *******************************************************************************/
class AssistantRetentionStepTest extends BaseAssistantTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testPurge() throws Exception
   {
      provider.thenToolUse("count_records", map("table", "page", "filter", filter("AND"))).thenText("12 pages");
      TurnRunner.Outcome editor = runner.run(AssistantTestFixture.EDITOR, "pages?");
      provider.thenText("hi");
      runner.run(AssistantTestFixture.OTHER_EDITOR, "hello");

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      try
      {
         AssistantStore store = new AssistantStore();

         ///////////////////////////////////////////////////
         // nothing is old yet                            //
         ///////////////////////////////////////////////////
         AssistantRetentionStep.Result none = AssistantRetentionStep.purge(config, store, Instant.now());
         assertThat(none.conversationsDeleted()).isZero();
         assertThat(none.toolResultsCleared()).isZero();

         //////////////////////////////////////////////////////////////////
         // 31 days on: tool results cleared; 91 days: conversations gone //
         //////////////////////////////////////////////////////////////////
         AssistantRetentionStep.Result results = AssistantRetentionStep.purge(config, store, Instant.now().plus(Duration.ofDays(31)));
         assertThat(results.toolResultsCleared()).isEqualTo(1);
         assertThat(results.conversationsDeleted()).isZero();
         List<AssistantToolCall> calls = store.query(AssistantToolCall.TABLE_NAME, new QQueryFilter(), AssistantToolCall.class);
         assertThat(calls).hasSize(1);
         assertThat(calls.get(0).getResultJson()).isNull();
         assertThat(calls.get(0).getResultSummary()).isNotBlank();

         AssistantRetentionStep.Result old = AssistantRetentionStep.purge(config, store, Instant.now().plus(Duration.ofDays(91)));
         assertThat(old.conversationsDeleted()).isEqualTo(2);
         for(Class<? extends com.kingsrook.qqq.backend.core.model.data.QRecordEntity> entity : List.of(AssistantConversation.class, AssistantMessage.class, AssistantToolCall.class, AssistantTurnEvent.class))
         {
            assertThat(store.query(com.kingsrook.qqq.backend.core.model.data.QRecordEntity.getTableName(entity), new QQueryFilter(), entity)).as(entity.getSimpleName()).isEmpty();
         }
         assertThat(editor.turnId()).isNotNull();
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** The process runs through QQQ (as an admin).
    *******************************************************************************/
   @Test
   void testProcess() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      try
      {
         RunProcessInput input = new RunProcessInput();
         input.setProcessName(AssistantMetaDataBuilder.RETENTION_PROCESS_NAME);
         input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
         RunProcessOutput output = new RunProcessAction().execute(input);
         assertThat(output.getValues()).containsEntry("conversationsDeleted", (Serializable) 0).containsEntry("toolResultsCleared", (Serializable) 0);
      }
      finally
      {
         QContext.clear();
      }
   }

}
