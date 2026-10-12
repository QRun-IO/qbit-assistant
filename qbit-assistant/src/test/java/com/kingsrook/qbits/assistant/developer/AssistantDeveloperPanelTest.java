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

package com.kingsrook.qbits.assistant.developer;


import java.util.List;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.actions.ai.AiExposure;
import com.kingsrook.qqq.backend.core.actions.developer.DeveloperPanelsAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanel;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelContext;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelsMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.ANALYST;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.EDITOR;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.NO_ACCESS;
import static com.kingsrook.qbits.assistant.testing.GoldenTasks.filter;
import static com.kingsrook.qbits.assistant.testing.GoldenTasks.map;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The record Developer screen's Assistant panel (contract 5.2 / 5.3):
 ** permission-scoped content, the ask-the-assistant link, and that a user
 ** learns nothing about tables or records they cannot see.
 *******************************************************************************/
class AssistantDeveloperPanelTest extends BaseAssistantTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   private AssistantDeveloperInfo.Info info(String userId, String table, Integer id)
   {
      QSession session = AssistantTestFixture.sessionFor(userId);
      QContext.init(qInstance, session);
      try
      {
         return (AssistantDeveloperInfo.build(config.getRuntime(), session, table, id));
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testEditorOnReadableRecord() throws Exception
   {
      provider.thenToolUse("get_record", map("table", "blog_post", "id", "1")).thenText("It is about Proxmox.");
      runner.run(EDITOR, "tell me about post 1");

      AssistantDeveloperInfo.Info info = info(EDITOR, "blog_post", 1);
      assertThat(info.usable()).isTrue();
      assertThat(info.model()).isEqualTo("claude-sonnet-5-5");
      assertThat(info.access().getTableExposure()).isEqualTo(AiExposure.VALUES);
      assertThat(info.access().getRecordReadable()).isTrue();
      assertThat(info.access().getTools()).extracting(t -> t.getToolName()).contains("query_records", "get_record_history", "navigate");
      assertThat(info.access().getFields()).extracting(f -> f.getFieldName()).contains("title").doesNotContain("internalNotes", "apiSecret");
      assertThat(info.recentToolCalls()).hasSize(1);
      assertThat(info.askHref()).isEqualTo("/app/blog_post/1?assistant=open&assistantPrompt=Tell%20me%20about%20this%20record.");

      DeveloperPanel panel = AssistantDeveloperPanelProvider.render(info);
      String         json  = JsonUtils.toJson(panel);
      assertThat(panel.getStatusLabel()).isEqualTo("Available");
      assertThat(json).contains("Ask the assistant about this record").contains("get_record").contains("Proxmox home lab notes");
      assertThat(json).doesNotContain("internalNotes").doesNotContain(AssistantTestFixture.HIDDEN_SENTINEL).doesNotContain(AssistantTestFixture.PASSWORD_SENTINEL);
   }



   /*******************************************************************************
    ** No READ on the table: "not available", nothing else (no label, no
    ** fields, no link) - same as for a table that does not exist.
    *******************************************************************************/
   @Test
   void testUnreadableTableRevealsNothing()
   {
      AssistantDeveloperInfo.Info hidden  = info(ANALYST, "page", 12);
      AssistantDeveloperInfo.Info missing = info(ANALYST, "no_such_table", 12);
      for(AssistantDeveloperInfo.Info info : List.of(hidden, missing))
      {
         assertThat(info.access().getReason()).isEqualTo("not available");
         assertThat(info.access().getFields()).isEmpty();
         assertThat(info.access().getTools()).isEmpty();
         assertThat(info.access().getRecordLabel()).isNull();
         assertThat(info.askHref()).isNull();
         String json = JsonUtils.toJson(AssistantDeveloperPanelProvider.render(info));
         assertThat(json).doesNotContain("About").doesNotContain("metaDescription").contains("not available to the assistant for you");
      }
      assertThat(JsonUtils.toJson(AssistantDeveloperPanelProvider.render(hidden))).isEqualTo(JsonUtils.toJson(AssistantDeveloperPanelProvider.render(missing)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testNoAccessAndKillSwitch()
   {
      AssistantDeveloperInfo.Info none = info(NO_ACCESS, "blog_post", 1);
      assertThat(none.hasAccess()).isFalse();
      assertThat(none.access()).isNull();
      DeveloperPanel panel = AssistantDeveloperPanelProvider.render(none);
      assertThat(panel.getStatusLabel()).isEqualTo("No access");
      assertThat(JsonUtils.toJson(panel)).doesNotContain("Proxmox");

      config.setEnabled(false);
      assertThat(info(EDITOR, "blog_post", 1).askHref()).isNull();
   }



   /*******************************************************************************
    ** Through the core registry and action, as the endpoint runs it.
    *******************************************************************************/
   @Test
   void testRegisteredWithCore() throws Exception
   {
      assertThat(DeveloperPanelsMetaData.of(qInstance).getProviders()).anyMatch(r -> r.getName().equals(AssistantDeveloperPanelProvider.class.getName()));

      QContext.init(qInstance, AssistantTestFixture.sessionFor(EDITOR));
      try
      {
         DeveloperPanelContext context = new DeveloperPanelContext().withTable(qInstance.getTable("blog_post")).withRecord(new QRecord().withValue("id", 1));
         List<DeveloperPanel>  panels  = new DeveloperPanelsAction().execute(context);
         DeveloperPanel        panel   = panels.stream().filter(p -> AssistantDeveloperPanelProvider.ID.equals(p.getId())).findFirst().orElseThrow();
         assertThat(panel.getError()).isNull();
         assertThat(panel.getTitle()).isEqualTo("Assistant");
         assertThat(JsonUtils.toJson(panel)).contains("/app/blog_post/1?assistant=open");

         config.setEnabled(false);
         assertThat(new DeveloperPanelsAction().execute(context)).noneMatch(p -> AssistantDeveloperPanelProvider.ID.equals(p.getId()));
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testAskHref()
   {
      assertThat(AssistantDeveloperInfo.askHref("page", 12, "What changed?")).isEqualTo("/app/page/12?assistant=open&assistantPrompt=What%20changed%3F");
      assertThat(filter("AND")).containsKey("criteria");
   }

}
