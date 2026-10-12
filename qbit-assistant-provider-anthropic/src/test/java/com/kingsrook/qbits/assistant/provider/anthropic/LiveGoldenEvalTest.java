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

package com.kingsrook.qbits.assistant.provider.anthropic;


import java.util.stream.Stream;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.GoldenTasks;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The golden tasks against the real model (claude-sonnet-5-5). Opt-in:
 ** runs only with ANTHROPIC_API_KEY set AND -Dassistant.live=true (and the
 ** "live" group, which the default build excludes). Never in CI by default.
 *******************************************************************************/
@Tag("live")
@EnabledIf("liveEnabled")
class LiveGoldenEvalTest
{
   private AssistantQBitConfig    config;
   private QInstance              qInstance;
   private AnthropicModelProvider provider;



   /*******************************************************************************
    **
    *******************************************************************************/
   static boolean liveEnabled()
   {
      String key = System.getenv(AnthropicModelProvider.API_KEY_ENV);
      return (key != null && !key.isBlank() && Boolean.getBoolean("assistant.live"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      provider = AnthropicModelProvider.fromEnvironment();
      config = new AssistantQBitConfig().withModelProvider(provider);
      qInstance = AssistantTestFixture.build(config);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      config.getRuntime().close();
      provider.close();
      QContext.clear();
   }



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
   void live(GoldenTasks.Task task) throws Exception
   {
      TurnRunner.Outcome outcome = new TurnRunner(qInstance, config).run(task.userId(), task.prompt());
      assertThat(outcome.errorCode()).as("error").isNull();
      task.liveCheck().accept(outcome);
   }

}
