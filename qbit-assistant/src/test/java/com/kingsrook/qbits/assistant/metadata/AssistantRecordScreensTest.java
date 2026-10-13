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

package com.kingsrook.qbits.assistant.metadata;


import java.util.List;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantFeedback;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Unit test for AssistantRecordScreens: the Assistant Admin record screens.
 *******************************************************************************/
class AssistantRecordScreensTest extends BaseAssistantTest
{
   private static final List<String> TABLES = List.of(AssistantConversation.TABLE_NAME, AssistantMessage.TABLE_NAME, AssistantTurn.TABLE_NAME,
      AssistantTurnEvent.TABLE_NAME, AssistantToolCall.TABLE_NAME, AssistantFeedback.TABLE_NAME, AssistantPolicy.TABLE_NAME, AssistantUsageDaily.TABLE_NAME);



   /*******************************************************************************
    ** Every table has named sections, never the generic ones, and every field
    ** is in exactly one section.
    *******************************************************************************/
   @Test
   void testEveryFieldInOneNamedSection()
   {
      for(String tableName : TABLES)
      {
         QTableMetaData table = qInstance.getTable(tableName);
         assertThat(table.getSections()).extracting(QFieldSection::getName).as(tableName).doesNotContain("identity", "otherFields");

         List<String> sectioned = table.getSections().stream()
            .filter(section -> section.getFieldNames() != null)
            .flatMap(section -> section.getFieldNames().stream()).toList();
         assertThat(sectioned).as(tableName).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(table.getFields().keySet());
      }
   }



   /*******************************************************************************
    ** JSON columns get the JSON viewer, ids link, costs say micro-dollars,
    ** the policy effort is a picker, the conversation status is read-only.
    *******************************************************************************/
   @Test
   void testFields()
   {
      for(String[] json : List.of(new String[] { AssistantConversation.TABLE_NAME, "contextJson" }, new String[] { AssistantMessage.TABLE_NAME, "partsJson" },
         new String[] { AssistantToolCall.TABLE_NAME, "resultJson" }, new String[] { AssistantTurnEvent.TABLE_NAME, "dataJson" }))
      {
         QFieldMetaData field = qInstance.getTable(json[0]).getField(json[1]);
         assertThat(field.getType()).isEqualTo(QFieldType.TEXT);
         assertThat(field.getAdornments()).anyMatch(a -> AdornmentType.CODE_EDITOR.equals(a.getType()));
      }

      assertThat(qInstance.getTable(AssistantToolCall.TABLE_NAME).getField("turnId").getPossibleValueSourceName()).isEqualTo(AssistantTurn.TABLE_NAME);
      assertThat(qInstance.getTable(AssistantFeedback.TABLE_NAME).getField("messageId").getPossibleValueSourceName()).isEqualTo(AssistantMessage.TABLE_NAME);
      assertThat(qInstance.getPossibleValueSource(AssistantTurn.TABLE_NAME)).isNotNull();
      assertThat(qInstance.getPossibleValueSource(AssistantMessage.TABLE_NAME)).isNotNull();

      assertThat(qInstance.getTable(AssistantTurn.TABLE_NAME).getField("costMicros").getLabel()).contains("micro-dollars");
      assertThat(qInstance.getTable(AssistantTurn.TABLE_NAME).getField("costMicros").getHelpContents()).isNotEmpty();

      QFieldMetaData effort = qInstance.getTable(AssistantPolicy.TABLE_NAME).getField("mainEffort");
      assertThat(effort.getPossibleValueSourceName()).isEqualTo(AssistantRecordScreens.EFFORT_PVS_NAME);
      assertThat(qInstance.getPossibleValueSource(AssistantRecordScreens.EFFORT_PVS_NAME).getEnumValues()).hasSize(5);
      assertThat(qInstance.getTable(AssistantPolicy.TABLE_NAME).getField("userDailyCostMicrosHard").getHelpContents()).isNotEmpty();

      assertThat(qInstance.getTable(AssistantConversation.TABLE_NAME).getField("status").getIsEditable()).isFalse();
   }



   /*******************************************************************************
    ** A conversation lists its messages, turns, tool calls, feedback and model
    ** calls.
    *******************************************************************************/
   @Test
   void testConversationChildLists()
   {
      QTableMetaData conversation = qInstance.getTable(AssistantConversation.TABLE_NAME);
      List<String> widgets = conversation.getSections().stream().map(QFieldSection::getWidgetName).filter(name -> name != null).toList();
      assertThat(widgets).hasSize(5);
      for(String widget : widgets)
      {
         assertThat(qInstance.getWidget(widget)).as(widget).isNotNull();
      }

      ///////////////////////////////////////////////////
      // a second pass does not add them again         //
      ///////////////////////////////////////////////////
      AssistantRecordScreens.addConversationChildLists(qInstance);
      assertThat(conversation.getSections().stream().filter(section -> section.getWidgetName() != null)).hasSize(5);
   }



   /*******************************************************************************
    ** A field the screens do not place (e.g. a new column) lands in Details.
    *******************************************************************************/
   @Test
   void testUnplacedFieldGoesToDetails()
   {
      QTableMetaData table = new QTableMetaData().withName(AssistantFeedback.TABLE_NAME)
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("rating", QFieldType.INTEGER))
         .withField(new QFieldMetaData("brandNew", QFieldType.STRING))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME));
      AssistantRecordScreens.apply(table);
      assertThat(table.getSection(AssistantRecordScreens.DETAILS_SECTION).getFieldNames()).containsExactly("brandNew");
      assertThat(table.getSection("dates").getFieldNames()).containsExactly("createDate");
   }
}
