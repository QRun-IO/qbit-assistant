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

package com.kingsrook.qbits.assistant.classification;


import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.engine.PhiRoutingGuard;
import com.kingsrook.qbits.assistant.engine.TurnClassification;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.provider.ModelPart;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolResult;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.context.QContext;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Classification precedence, null safety, conservative cross-table reads and the host seam.
 *******************************************************************************/
class ClassificationTest extends BaseAssistantTest
{
   /*******************************************************************************
    ** Ordering, field fallback and explicit subsets.
    *******************************************************************************/
   @Test
   void configuredSource()
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.EDITOR));
      config.withDataClassification("page", DataClassification.CONFIDENTIAL)
         .withDataClassification("page", "title", DataClassification.PII)
         .withDataClassification("page", "content", DataClassification.PHI)
         .withDataClassification("assistant_message", DataClassification.PHI);
      ClassificationSource source = config.getClassificationSource();
      assertThat(source.ofTable(null)).isEqualTo(DataClassification.PUBLIC);
      assertThat(source.ofField("page", "unknown")).isEqualTo(DataClassification.CONFIDENTIAL);
      assertThat(source.ofField("page", null)).isEqualTo(DataClassification.CONFIDENTIAL);
      assertThat(source.ofField(null, null)).isEqualTo(DataClassification.PUBLIC);
      assertThat(source.ofField("assistant_message", "partsJson")).isEqualTo(DataClassification.PUBLIC);
      assertThat(source.highest("page")).isEqualTo(DataClassification.PHI);
      assertThat(source.highest("page", List.of("title"))).isEqualTo(DataClassification.PII);
      assertThat(source.highest("page", Arrays.asList((String) null))).isEqualTo(DataClassification.PHI);
      assertThat(source.highest("page", List.of("*"))).isEqualTo(DataClassification.PHI);
      assertThat(source.highest("page", List.of())).isEqualTo(DataClassification.PHI);
      assertThat(source.highest(null)).isEqualTo(DataClassification.PUBLIC);
      assertThat(source.highest("assistant_message")).isEqualTo(DataClassification.PUBLIC);
      assertThat(source.highestAnywhere()).isEqualTo(DataClassification.PHI);
      assertThat(Classifications.max(null, null)).isEqualTo(DataClassification.PUBLIC);
      assertThat(Classifications.max(DataClassification.PHI, DataClassification.PII)).isEqualTo(DataClassification.PHI);
      assertThat(DataClassification.PII.isAtLeast(null)).isTrue();
      assertThat(DataClassification.PUBLIC.isAtLeast(DataClassification.PII)).isFalse();
      assertThat(Classifications.stored("future")).isEqualTo(DataClassification.PHI);
      ClassificationSource host = new ConfiguredClassificationSource().withClassification("media", DataClassification.PII);
      assertThat(config.withClassificationSource(host).getClassificationSource()).isSameAs(host);
      assertThat(new ConfiguredClassificationSource().withClassification(null, null).withClassification(null, null, null).ofTable(null)).isEqualTo(DataClassification.PUBLIC);
   }



   /*******************************************************************************
    ** Unknown search results, history, metadata and page table versus record context.
    *******************************************************************************/
   @Test
   void tracking()
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.EDITOR));
      config.withDataClassification("page", "content", DataClassification.PHI).withDataClassification("media", DataClassification.PII);
      TurnClassification page = new TurnClassification(config.getClassificationSource(), new AssistantConversation(), Map.of("tableName", "page"));
      assertThat(page.getClassification()).isEqualTo(DataClassification.PUBLIC);
      TurnClassification record = new TurnClassification(config.getClassificationSource(), new AssistantConversation(), Map.of("tableName", "page", "recordId", 1));
      assertThat(record.getClassification()).isEqualTo(DataClassification.PHI);
      page.observe(ModelPart.toolUse("1", "describe_table", Map.of("table", "page")), new AiToolResult());
      assertThat(page.getClassification()).isEqualTo(DataClassification.PUBLIC);
      page.observe(ModelPart.toolUse("present", "present_results", Map.of("summary", "Available results")), new AiToolResult());
      assertThat(page.getClassification()).isEqualTo(DataClassification.PUBLIC);
      record.observe(ModelPart.toolUse("present", "present_results", Map.of("summary", "Available results")), new AiToolResult());
      assertThat(record.getClassification()).isEqualTo(DataClassification.PHI);
      page.observe(ModelPart.toolUse("2", "search_records", Map.of()), new AiToolResult().withModelContent("<qqq_data>{\"results\":[{\"table\":\"media\"}]}</qqq_data>"));
      assertThat(page.getClassification()).isEqualTo(DataClassification.PII);
      page.observe(ModelPart.toolUse("3", "search_records", Map.of()), new AiToolResult());
      assertThat(page.getClassification()).isEqualTo(DataClassification.PHI);
      assertThat(page.getTables()).contains("page", "media");
      TurnClassification history = new TurnClassification(config.getClassificationSource(), new AssistantConversation(), null);
      history.observe(ModelPart.toolUse("4", "get_record_history", Map.of("tableName", "page", "fields", List.of("title"))), new AiToolResult());
      assertThat(history.getClassification()).isEqualTo(DataClassification.PHI);
      assertThat(PhiRoutingGuard.check(DataClassification.PHI, new AssistantProvider().withHipaaEligible(true), false)).isPresent();
      assertThat(PhiRoutingGuard.check(DataClassification.PHI, new AssistantProvider().withId(1).withHipaaEligible(true), false)).isEmpty();
   }
}
