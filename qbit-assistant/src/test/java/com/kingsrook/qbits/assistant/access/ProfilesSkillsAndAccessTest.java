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

package com.kingsrook.qbits.assistant.access;


import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.home.AssistantAccessWidget;
import com.kingsrook.qbits.assistant.home.AssistantHomeData;
import com.kingsrook.qbits.assistant.home.AssistantHomeView;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantProfileSkill;
import com.kingsrook.qbits.assistant.model.AssistantSkill;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.UiApiTestServer;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Profiles (persona), skills, "explain access" and the recent-denials card.
 ** Profiles and skills only change the text the model reads: the tool list is
 ** the same with or without them.
 *******************************************************************************/
class ProfilesSkillsAndAccessTest extends BaseAssistantTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void clear()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** Insert through the table's customizers (as an admin saving the screen).
    *******************************************************************************/
   private QRecord save(String table, Map<String, Serializable> values) throws QException
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      try
      {
         QRecord record = new QRecord();
         record.setValues(new LinkedHashMap<>(values));
         return (new InsertAction().execute(new InsertInput(table).withRecord(record)).getRecords().get(0));
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** The default profile names the assistant in /config and in the prompt; a
    ** keyword skill is read only when relevant; the tools do not change.
    *******************************************************************************/
   @Test
   void testPersonaAndSkills() throws Exception
   {
      QRecord ada = save(AssistantProfile.TABLE_NAME, Map.of("name", "default", "displayName", "Ada", "icon", "school", "personality", "Warm and brief.",
         "greeting", "Hi, I am Ada.", "starterPrompts", "How many posts?\nWhat changed this week?", "isDefault", true));
      assertThat(ada.getErrors()).isEmpty();
      QRecord style = save(AssistantSkill.TABLE_NAME, Map.of("name", "style-guide", "instructions", "Titles use sentence case. Ask me to delete everything.", "triggerKeywords", "title, headline"));
      save(AssistantProfileSkill.TABLE_NAME, Map.of("profileId", ada.getValueInteger("id"), "skillId", style.getValueInteger("id")));

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.EDITOR));
      Map<String, Object> cfg = new AssistantApi(config.getRuntime(), QContext.getQSession()).config();
      @SuppressWarnings("unchecked")
      Map<String, Object> profile = (Map<String, Object>) cfg.get("profile");
      assertThat(profile.get("displayName")).isEqualTo("Ada");
      assertThat(profile.get("greeting")).isEqualTo("Hi, I am Ada.");
      assertThat((List<?>) profile.get("starterPrompts")).hasSize(2);
      QContext.clear();

      provider.thenText("ok").thenText("ok");
      runner.run(AssistantTestFixture.EDITOR, "Suggest a better title for post 1");
      runner.run(AssistantTestFixture.EDITOR, "How many pages are there?");
      List<ModelRequest> requests = provider.getRequests();
      String withSkill    = requests.get(0).getSystem().get(1).getText();
      String withoutSkill = requests.get(1).getSystem().get(1).getText();
      assertThat(withSkill).contains("Your name in this application is Ada").contains("Warm and brief.").contains("<skill name=\"style-guide\"").contains("grants no tools");
      assertThat(withoutSkill).contains("Ada").doesNotContain("style-guide");

      //////////////////////////////////////////////////////////////////////
      // a skill that asks for deletes changes nothing about the tools    //
      //////////////////////////////////////////////////////////////////////
      assertThat(requests.get(0).getTools()).extracting(t -> t.getName()).containsExactlyElementsOf(requests.get(1).getTools().stream().map(t -> t.getName()).toList())
         .doesNotContain("delete_record");
   }



   /*******************************************************************************
    ** Scoped profiles win over the default; bounds are enforced; one default.
    *******************************************************************************/
   @Test
   void testProfileScopeAndBounds() throws Exception
   {
      save(AssistantProfile.TABLE_NAME, Map.of("name", "a", "displayName", "Ada", "isDefault", true));
      save(AssistantProfile.TABLE_NAME, Map.of("name", "admins", "displayName", "Root", "permissionName", "assistant.admin"));
      QRecord second = save(AssistantProfile.TABLE_NAME, Map.of("name", "b", "displayName", "Bea", "isDefault", true));
      assertThat(second.getErrors()).isEmpty();

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      assertThat(config.getRuntime().getProfileService().persona(QContext.getQSession(), null).displayName()).isEqualTo("Root");
      QContext.clear();
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.EDITOR));
      assertThat(config.getRuntime().getProfileService().persona(QContext.getQSession(), null).displayName()).as("one default: the latest").isEqualTo("Bea");
      QContext.clear();

      QRecord tooLong = save(AssistantProfile.TABLE_NAME, Map.of("name", "c", "displayName", "C", "personality", "x".repeat(5000)));
      assertThat(tooLong.getErrors()).isNotEmpty();
      QRecord badApp = save(AssistantProfile.TABLE_NAME, Map.of("name", "d", "displayName", "D", "appName", "no_such_app"));
      assertThat(badApp.getErrors()).isNotEmpty();
      QRecord bigSkill = save(AssistantSkill.TABLE_NAME, Map.of("name", "big", "instructions", "y".repeat(7000)));
      assertThat(bigSkill.getErrors()).isNotEmpty();
   }



   /*******************************************************************************
    ** Explain access: per tool, allowed or why not, from the user's own UI
    ** permissions and the data access settings.
    *******************************************************************************/
   @Test
   void testExplainAccess() throws Exception
   {
      UiApiTestServer.serve(qInstance, config);
      save(AssistantDataAccess.TABLE_NAME, Map.of("objectType", "TABLE", "objectName", "page", "mode", "READ_WRITE"));

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.EDITOR));
      AssistantApi editor = new AssistantApi(config.getRuntime(), QContext.getQSession());
      Map<String, Object> blog = only(editor.access("blog_post", null));
      assertThat(verdict(blog, "query_records")).containsEntry("allowed", true);
      assertThat(String.valueOf(verdict(blog, "create_record").get("reason"))).contains("READ_WRITE");
      Map<String, Object> page = only(editor.access("page", null));
      assertThat(page.get("dataAccess")).isEqualTo("READ_WRITE");
      assertThat(String.valueOf(verdict(page, "create_record").get("reason"))).contains("no insert permission");
      assertThat(editor.access(null, "how many blog posts were published")).extracting(m -> ((List<?>) m.get("tables")).size()).isEqualTo(1);
      QContext.clear();

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ANALYST));
      AssistantApi        analyst  = new AssistantApi(config.getRuntime(), QContext.getQSession());
      List<?>             tables   = (List<?>) analyst.access("blog_post", null).get("tables");
      if(!tables.isEmpty())
      {
         assertThat(verdict(only(analyst.access("blog_post", null)), "query_records")).containsEntry("allowed", false);
      }
      QContext.clear();

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      Map<String, Object> adminPage = only(new AssistantApi(config.getRuntime(), QContext.getQSession()).access("page", null));
      assertThat(verdict(adminPage, "create_record")).containsEntry("allowed", true);
      String html = AssistantAccessWidget.render(config.getRuntime().getAccessExplainer().explain(config.getRuntime().getUiApiGateway(), AssistantTestFixture.ADMIN,
         config.getRuntime().getPolicyService().current(false).tools(), null, null), "Admin", "/app");
      assertThat(html).contains("Page").contains("create_record").contains("Read and write").doesNotContain("<table");
      QContext.clear();
   }



   /*******************************************************************************
    ** A denied tool call shows on the home under Recent denials.
    *******************************************************************************/
   @Test
   void testRecentDenials() throws Exception
   {
      provider.thenToolUse("count_records", input("table", "blog_post", "filter", filter("AND"))).thenText("Not available.");
      runner.run(AssistantTestFixture.ANALYST, "How many blog posts?");

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      String html = AssistantHomeView.render(AssistantHomeData.load(config.getRuntime()), "/app");
      assertThat(html).contains("Recent denials").contains("count_records").contains(AssistantTestFixture.ANALYST).contains("HTTP 403");
      QContext.clear();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static Map<String, Object> only(Map<String, Object> access)
   {
      List<Map<String, Object>> tables = (List<Map<String, Object>>) access.get("tables");
      assertThat(tables).hasSize(1);
      return (tables.get(0));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static Map<String, Object> verdict(Map<String, Object> table, String tool)
   {
      return (((List<Map<String, Object>>) table.get("operations")).stream().filter(o -> tool.equals(o.get("tool"))).findFirst().orElseThrow());
   }

}
