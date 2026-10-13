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


import com.kingsrook.qbits.assistant.engine.ContextBuilder;
import com.kingsrook.qbits.assistant.engine.ProfileService;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantProfileSkill;
import com.kingsrook.qbits.assistant.model.AssistantSkill;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qbits.assistant.uiapi.CoreUiApiGateway;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolKernel;
import com.kingsrook.qqq.backend.core.actions.dashboard.RenderWidgetAction;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.reporting.ExportAction;
import com.kingsrook.qqq.backend.core.actions.tables.AggregateAction;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.RecordSearchAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Architecture rules (contract section 2) on the production classes:
 ** - no QSystemUserSession, no QSession construction (the assistant only
 **   ever uses the requesting user's own session);
 ** - QQQ data actions only from the persistence package (the QBit's own
 **   assistant_* tables); everything else reaches data through the
 **   AiToolKernel.
 *******************************************************************************/
class ArchitectureTest
{
   private static final String BASE        = "com.kingsrook.qbits.assistant..";
   private static final String PERSISTENCE = "com.kingsrook.qbits.assistant.persistence..";

   private static JavaClasses classes;



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeAll
   static void importClasses()
   {
      classes = new ClassFileImporter()
         .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
         .importPackages("com.kingsrook.qbits.assistant");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void noSystemUserSession()
   {
      ArchRule rule = noClasses().that().resideInAPackage(BASE)
         .should().dependOnClassesThat().areAssignableTo(QSystemUserSession.class)
         .because("the assistant runs everything as the requesting user");
      rule.check(classes);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void noSessionConstruction()
   {
      ArchRule rule = noClasses().that().resideInAPackage(BASE)
         .should().callConstructorWhere(com.tngtech.archunit.core.domain.JavaConstructorCall.Predicates.target(
            com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner(
               com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo(QSession.class))))
         .because("the assistant never creates sessions");
      rule.check(classes);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void dataActionsOnlyInPersistence()
   {
      ArchRule rule = noClasses().that().resideInAPackage(BASE).and().resideOutsideOfPackage(PERSISTENCE)
         .should().dependOnClassesThat().belongToAnyOf(QueryAction.class, GetAction.class, CountAction.class, AggregateAction.class,
            InsertAction.class, UpdateAction.class, DeleteAction.class, RunProcessAction.class, RenderWidgetAction.class, ExportAction.class, RecordSearchAction.class)
         .because("application data is reached only through the /qqq/v1 UI API; only the persistence package touches the QBit's own tables");
      rule.check(classes);
   }



   /*******************************************************************************
    ** The tool, UI API and access code never touches QQQ actions (tables,
    ** processes, reports, widgets) nor the core AI kernel: every capability is
    ** a /qqq/v1 operation through the gateway.
    *******************************************************************************/
   @Test
   void toolsOnlyThroughTheUiApi()
   {
      ArchRule rule = noClasses().that().resideInAnyPackage("com.kingsrook.qbits.assistant.tools..", "com.kingsrook.qbits.assistant.uiapi..", "com.kingsrook.qbits.assistant.access..")
         .should().dependOnClassesThat().resideInAnyPackage("com.kingsrook.qqq.backend.core.actions.tables..", "com.kingsrook.qqq.backend.core.actions.processes..",
            "com.kingsrook.qqq.backend.core.actions.reporting..", "com.kingsrook.qqq.backend.core.actions.dashboard..", "com.kingsrook.qqq.backend.core.actions.async..")
         .orShould().dependOnClassesThat().belongToAnyOf(AiToolKernel.class)
         .because("owner decision 2026-10-13: the assistant uses only the UI API, as the signed-in user");
      rule.check(classes);

      ////////////////////////////////////////////////////////////////////////
      // the tool and gateway code does not even use the QBit's own store   //
      // (data access reads its settings rows through it; the store itself  //
      // refuses any table that is not assistant_*)                         //
      ////////////////////////////////////////////////////////////////////////
      noClasses().that().resideInAnyPackage("com.kingsrook.qbits.assistant.tools..", "com.kingsrook.qbits.assistant.uiapi..")
         .should().dependOnClassesThat().belongToAnyOf(AssistantStore.class)
         .because("tools reach data only through the UI API")
         .check(classes);
   }



   /*******************************************************************************
    ** Profiles and skills are text: nothing in the tool path may read them.
    *******************************************************************************/
   @Test
   void profilesAndSkillsNeverReachTools()
   {
      ArchRule rule = noClasses().that().resideInAnyPackage("com.kingsrook.qbits.assistant.tools..", "com.kingsrook.qbits.assistant.uiapi..", "com.kingsrook.qbits.assistant.access..")
         .should().dependOnClassesThat().belongToAnyOf(ProfileService.class, AssistantProfile.class, AssistantSkill.class, AssistantProfileSkill.class)
         .because("a persona or skill can never grant tools, endpoints or data access");
      rule.check(classes);
   }



   /*******************************************************************************
    ** Only two tools stay on the core kernel, and both read no records.
    *******************************************************************************/
   @Test
   void onlyRecordFreeToolsOnTheKernel()
   {
      assertThat(ContextBuilder.KERNEL_TOOLS).containsExactlyInAnyOrder("navigate", "search_help");
      assertThat(UiApiToolbox.OPERATIONS.keySet()).doesNotContainAnyElementsOf(ContextBuilder.KERNEL_TOOLS);
      CoreUiApiGateway gateway = new CoreUiApiGateway(() -> "http://127.0.0.1:1", new QInstance());
      for(String[] operation : UiApiToolbox.OPERATIONS.values())
      {
         assertThat(gateway.isInvocable(operation[0], operation[1])).as("spec'd: " + operation[0] + " " + operation[1]).isTrue();
      }
   }



   /*******************************************************************************
    ** The store refuses any table that is not the QBit's own.
    *******************************************************************************/
   @Test
   void storeOnlyOwnTables()
   {
      assertThatThrownBy(() -> AssistantStore.requireOwnTable("blog_post")).hasMessageContaining("UI API");
      assertThatThrownBy(() -> AssistantStore.requireOwnTable(null)).isInstanceOf(QException.class);
   }

}
