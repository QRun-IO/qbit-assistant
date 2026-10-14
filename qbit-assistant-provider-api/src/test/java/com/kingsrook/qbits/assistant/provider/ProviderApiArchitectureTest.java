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

package com.kingsrook.qbits.assistant.provider;


import java.util.Set;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;


/*******************************************************************************
 ** Keep external provider consumers independent of the assistant runtime and QQQ.
 *******************************************************************************/
class ProviderApiArchitectureTest
{
   /*******************************************************************************
    ** Allow only the extracted types, including their nested enums, under the
    ** assistant namespace. A future dependency on any runtime package must fail.
    *******************************************************************************/
   @Test
   void dependsOnlyOnApiTypes()
   {
      Set<String> apiTypes = Set.of(
         "com.kingsrook.qbits.assistant.provider.AssistantModelProvider",
         "com.kingsrook.qbits.assistant.provider.AssistantProviderType",
         "com.kingsrook.qbits.assistant.provider.CacheHint",
         "com.kingsrook.qbits.assistant.provider.CompletableStreamHandle",
         "com.kingsrook.qbits.assistant.provider.DiscoveredModel",
         "com.kingsrook.qbits.assistant.provider.EmbeddingRequest",
         "com.kingsrook.qbits.assistant.provider.EmbeddingResponse",
         "com.kingsrook.qbits.assistant.provider.HealthResult",
         "com.kingsrook.qbits.assistant.provider.ModelCapabilities",
         "com.kingsrook.qbits.assistant.provider.ModelEventListener",
         "com.kingsrook.qbits.assistant.provider.ModelMessage",
         "com.kingsrook.qbits.assistant.provider.ModelPart",
         "com.kingsrook.qbits.assistant.provider.ModelProviderException",
         "com.kingsrook.qbits.assistant.provider.ModelRequest",
         "com.kingsrook.qbits.assistant.provider.ModelResponse",
         "com.kingsrook.qbits.assistant.provider.ModelToolDefinition",
         "com.kingsrook.qbits.assistant.provider.ModelUsage",
         "com.kingsrook.qbits.assistant.provider.ProviderSettings",
         "com.kingsrook.qbits.assistant.provider.StreamHandle",
         "com.kingsrook.qbits.assistant.provider.SystemBlock",
         "com.kingsrook.qbits.assistant.provider.TokenCount",
         "com.kingsrook.qbits.assistant.provider.TrustedCaBundle",
         "com.kingsrook.qbits.assistant.engine.ModelPricing");
      JavaClasses imported = new ClassFileImporter()
         .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
         .importPackages("com.kingsrook.qbits.assistant");
      DescribedPredicate<JavaClass> outsideRuntime = DescribedPredicate.describe("outside assistant or an extracted API type", type ->
         !type.getName().startsWith("com.kingsrook.qbits.assistant.") || apiTypes.contains(type.getName().split("\\$", 2)[0]));
      classes().should().onlyDependOnClassesThat(outsideRuntime).check(imported);
      noClasses().should().dependOnClassesThat().resideInAnyPackage("com.kingsrook.qqq..").check(imported);
   }
}
