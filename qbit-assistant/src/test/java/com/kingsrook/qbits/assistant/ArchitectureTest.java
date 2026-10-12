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
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;


/*******************************************************************************
 ** Architecture rules (contract section 2) on the production classes:
 ** - no QSystemUserSession, no QSession construction (the assistant only
 **   ever uses the requesting user's own session);
 ** - QQQ data actions only from the persistence package (the QBit's own
 **   assistant* tables); everything else reaches data through the
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
         .because("tools run only through the AiToolKernel; only the persistence package touches the QBit's own tables");
      rule.check(classes);
   }

}
