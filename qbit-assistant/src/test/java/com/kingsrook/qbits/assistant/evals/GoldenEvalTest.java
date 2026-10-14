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

package com.kingsrook.qbits.assistant.evals;


import java.util.stream.Stream;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.GoldenTasks;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.ClientChannelsMetaData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Golden tasks v1 with the deterministic (scripted, faithful) model. Runs
 ** in every build; a failure fails the build. The live variant runs the same
 ** tasks against Claude (qbit-assistant-provider-anthropic, opt-in).
 *******************************************************************************/
@Tag("golden")
class GoldenEvalTest extends BaseAssistantTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   static Stream<GoldenTasks.Task> tasks()
   {
      return (GoldenTasks.all().stream());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @ParameterizedTest(name = "{0}")
   @MethodSource("tasks")
   void golden(GoldenTasks.Task task) throws Exception
   {
      if("D-page-12-this-week".equals(task.id()))
      {
         ClientChannelsMetaData.ofOrWithNew(qInstance).getDeniedPatterns().get("assistant").remove("audit*");
      }
      task.script().accept(provider);
      TurnRunner.Outcome outcome = runner.run(task.userId(), task.prompt());

      assertThat(outcome.end()).as("turn status").containsEntry("status", "COMPLETE");
      assertThat(outcome.errorCode()).isNull();
      task.check().accept(outcome);
      task.liveCheck().accept(outcome);
   }

}
