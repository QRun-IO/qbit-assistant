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


import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantFeedback;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Owner naming rule (2026-10-12): table names are lowercase snake_case and
 ** prefixed with the qbit's name (assistant_). Pins the new names and the
 ** deprecated pre-RC.2 names that UPGRADE.md tells hosts to migrate from.
 *******************************************************************************/
@SuppressWarnings("deprecation")
class NamingTest extends BaseAssistantTest
{
   private static final String SNAKE_WITH_PREFIX = "^assistant_[a-z0-9]+(_[a-z0-9]+)*$";



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, String> tableNames()
   {
      Map<String, String> names = new LinkedHashMap<>();
      names.put(AssistantConversation.TABLE_NAME, AssistantConversation.LEGACY_TABLE_NAME);
      names.put(AssistantMessage.TABLE_NAME, AssistantMessage.LEGACY_TABLE_NAME);
      names.put(AssistantTurn.TABLE_NAME, AssistantTurn.LEGACY_TABLE_NAME);
      names.put(AssistantTurnEvent.TABLE_NAME, AssistantTurnEvent.LEGACY_TABLE_NAME);
      names.put(AssistantToolCall.TABLE_NAME, AssistantToolCall.LEGACY_TABLE_NAME);
      names.put(AssistantFeedback.TABLE_NAME, AssistantFeedback.LEGACY_TABLE_NAME);
      names.put(AssistantPolicy.TABLE_NAME, AssistantPolicy.LEGACY_TABLE_NAME);
      names.put(AssistantUsageDaily.TABLE_NAME, AssistantUsageDaily.LEGACY_TABLE_NAME);
      return (names);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testTableNamesAreSnakeCaseWithPrefix()
   {
      assertThat(tableNames()).hasSize(8);
      for(Map.Entry<String, String> entry : tableNames().entrySet())
      {
         String name = entry.getKey();
         assertThat(name).matches(SNAKE_WITH_PREFIX);
         QTableMetaData table = qInstance.getTable(name);
         assertThat(table).as(name).isNotNull();

         /////////////////////////////////////////////////////////////
         // the old camelCase names are no longer registered at all //
         /////////////////////////////////////////////////////////////
         assertThat(qInstance.getTable(entry.getValue())).as(entry.getValue()).isNull();
      }

      ////////////////////////////////////////////////////////////////
      // every produced table follows the rule (none left behind) //
      ////////////////////////////////////////////////////////////////
      for(QTableMetaData table : qInstance.getTables().values())
      {
         if(table.getName().toLowerCase().startsWith("assistant"))
         {
            assertThat(table.getName()).matches(SNAKE_WITH_PREFIX);
         }
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testLegacyNames()
   {
      assertThat(tableNames()).containsEntry("assistant_conversation", "assistantConversation")
         .containsEntry("assistant_message", "assistantMessage")
         .containsEntry("assistant_turn", "assistantTurn")
         .containsEntry("assistant_turn_event", "assistantTurnEvent")
         .containsEntry("assistant_tool_call", "assistantToolCall")
         .containsEntry("assistant_feedback", "assistantFeedback")
         .containsEntry("assistant_policy", "assistantPolicy")
         .containsEntry("assistant_usage_daily", "assistantUsageDaily");

      assertThat(AssistantQBitConfig.ACCESS_PERMISSION).isEqualTo("assistant.has_access");
      assertThat(AssistantQBitConfig.LEGACY_ACCESS_PERMISSION).isEqualTo("assistant.hasAccess");
      assertThat(AssistantQBitConfig.ADMIN_PERMISSION).isEqualTo("assistant.admin");
      assertThat(AssistantQBitConfig.DEFAULT_OWNER_KEY_TYPE).isEqualTo("assistant_user_id");
      assertThat(AssistantQBitConfig.LEGACY_DEFAULT_OWNER_KEY_TYPE).isEqualTo("assistantUserId");
      assertThat(AssistantMetaDataBuilder.allAccessKeyName(AssistantQBitConfig.DEFAULT_OWNER_KEY_TYPE)).isEqualTo("assistant_user_id_all_access");
      assertThat(AssistantQBitConfig.LEGACY_DEFAULT_OWNER_KEY_TYPE + AssistantMetaDataBuilder.LEGACY_ALL_ACCESS_SUFFIX).isEqualTo("assistantUserIdAllAccess");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testPossibleValueSourceUsesTableName()
   {
      assertThat(qInstance.getPossibleValueSource("assistant_conversation")).isNotNull();
      assertThat(qInstance.getPossibleValueSource("assistantConversation")).isNull();
      assertThat(qInstance.getTable(AssistantMessage.TABLE_NAME).getField("conversationId").getPossibleValueSourceName()).isEqualTo("assistant_conversation");
   }

}
