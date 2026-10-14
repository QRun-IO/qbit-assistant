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

package com.kingsrook.qbits.assistant.spi;


import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;


/*******************************************************************************
 ** Keep external provider consumers independent of the assistant runtime and QQQ.
 *******************************************************************************/
class ProviderApiArchitectureTest
{
   /*******************************************************************************
    ** Import the entire artifact so an accidental package outside spi also fails.
    *******************************************************************************/
   @Test
   void dependsOnlyOnApiTypes()
   {
      String spiPackage = "com.kingsrook.qbits.assistant.spi";
      JavaClasses imported = new ClassFileImporter().importUrl(AssistantModelProvider.class.getProtectionDomain().getCodeSource().getLocation());
      classes().should().resideInAPackage(spiPackage).check(imported);
      noClasses().should().dependOnClassesThat(resideInAPackage("com.kingsrook.qbits..").and(resideOutsideOfPackage(spiPackage))).check(imported);
      noClasses().should().dependOnClassesThat().resideInAnyPackage("com.kingsrook.qqq..").check(imported);
   }
}
