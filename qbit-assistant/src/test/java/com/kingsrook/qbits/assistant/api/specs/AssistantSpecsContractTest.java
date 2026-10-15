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

package com.kingsrook.qbits.assistant.api.specs;


import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import com.kingsrook.qqq.middleware.javalin.tools.UiApiContract;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The assistant's UI API extension: registered by the producer, and its
 ** OpenAPI fragment matches the committed one
 ** (src/main/resources/openapi/qqq-v1/assistant.yaml) and is compatible with
 ** the last release's (baseline). Regenerate with
 ** -Dassistant.regenerateOpenApi=true.
 *******************************************************************************/
class AssistantSpecsContractTest extends BaseAssistantTest
{
   static final Path COMMITTED = Path.of("src/main/resources/openapi/qqq-v1/assistant.yaml");
   static final Path BASELINE  = Path.of("src/test/resources/openapi/qqq-v1/assistant-baseline.yaml");



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testRegisteredAndCommitted() throws Exception
   {
      assertThat(MiddlewareSpecExtensionsMetaData.ofOrWithNew(qInstance).getExtensions()).containsKey(AssistantSpecs.EXTENSION_NAME);

      String generated = UiApiContract.generateExtensionYaml(new MiddlewareVersionV1(), AssistantSpecs.extension());
      if(Boolean.getBoolean("assistant.regenerateOpenApi"))
      {
         Files.createDirectories(COMMITTED.getParent());
         Files.writeString(COMMITTED, generated, StandardCharsets.UTF_8);
         if(!Files.exists(BASELINE))
         {
            Files.createDirectories(BASELINE.getParent());
            Files.writeString(BASELINE, generated, StandardCharsets.UTF_8);
         }
      }

      assertThat(generated).contains("/qqq/v1/assistant/conversations/{id}/turns").contains("text/event-stream").contains("/qqq/v1/assistant/actions/{id}/confirm")
         .contains("AssistantTurnEventV1").contains("/qqq/v1/assistant/access");
      ///////////////////////////////////////////////////////////////////////////////////////
      // The standard error schema retains error/code and adds message/traceId/fieldErrors. //
      // The two 204 responses never had bodies; their old refs pointed to schema "null".   //
      // Keep the release baseline and accept only these documented migration corrections. //
      ///////////////////////////////////////////////////////////////////////////////////////
      List<String> acceptedChanges = List.of(
         "$ref changed from #/components/schemas/AssistantErrorV1 to #/components/schemas/BasicErrorResponseV1",
         "DELETE /qqq/v1/assistant/conversations/{id} response 204: content type removed: application/json",
         "POST /qqq/v1/assistant/messages/{id}/feedback response 204: content type removed: application/json");
      List<String> problems = UiApiContract.check(generated, Files.readString(COMMITTED, StandardCharsets.UTF_8),
         Files.exists(BASELINE) ? Files.readString(BASELINE, StandardCharsets.UTF_8) : null, 1, "./mvnw test -pl qbit-assistant -Dtest=AssistantSpecsContractTest -Dassistant.regenerateOpenApi=true", acceptedChanges);
      assertThat(problems).isEmpty();
   }

}
