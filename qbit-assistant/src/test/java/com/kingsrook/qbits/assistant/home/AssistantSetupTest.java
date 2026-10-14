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

package com.kingsrook.qbits.assistant.home;


import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.kingsrook.qbits.assistant.access.DataAccessMode;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantProfileSkill;
import com.kingsrook.qbits.assistant.model.AssistantSkill;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.processes.DataAccessDefaultsProcess;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The home's Setup card and the "Add rows for every table" action.
 *******************************************************************************/
class AssistantSetupTest extends BaseAssistantTest
{

   /*******************************************************************************
    ** An empty installation says what to do next; filling the tables flips
    ** each item to set up.
    *******************************************************************************/
   @Test
   void setupCardTracksTheTables() throws Exception
   {
      Map<String, AssistantSetupStatus.Item> items = items();
      assertThat(items.keySet()).containsExactly("Provider", "Model prices", "Policy", "Profiles", "Skills", "Budgets", "Data access", "Knowledge sources", "Scheduled tasks");
      assertThat(items.get("Model prices").state()).isEqualTo(AssistantSetupStatus.State.TODO);
      assertThat(items.get("Budgets").state()).isEqualTo(AssistantSetupStatus.State.TODO);
      assertThat(items.get("Budgets").nextStep()).contains("monthly instance budget");
      assertThat(items.get("Profiles").state()).isEqualTo(AssistantSetupStatus.State.DEFAULT);
      assertThat(items.get("Data access").state()).isEqualTo(AssistantSetupStatus.State.DEFAULT);
      assertThat(items.get("Data access").nextStep()).contains("Add rows for every table");
      assertThat(items.get("Knowledge sources").state()).isEqualTo(AssistantSetupStatus.State.OPTIONAL);

      AssistantStore   store   = adminStore();
      AssistantProfile profile = store.insert(new AssistantProfile().withName("default").withDisplayName("Assistant").withIsDefault(true).withEnabled(true));
      AssistantSkill   skill   = store.insert(new AssistantSkill().withName("terms").withInstructions("Our terms.").withEnabled(true));
      assertThat(items().get("Skills").state()).as("a skill no profile offers is flagged").isEqualTo(AssistantSetupStatus.State.TODO);
      store.insert(new AssistantProfileSkill().withProfileId(profile.getId()).withSkillId(skill.getId()));
      store.insert(new AssistantBudget().withName("Monthly").withScopeType("INSTANCE").withPeriod("MONTH").withMetric("COST_USD").withLimitValue(BigDecimal.TEN));

      items = items();
      assertThat(items.get("Profiles").state()).isEqualTo(AssistantSetupStatus.State.OK);
      assertThat(items.get("Skills").state()).isEqualTo(AssistantSetupStatus.State.OK);
      assertThat(items.get("Budgets").state()).isEqualTo(AssistantSetupStatus.State.OK);

      String html = AssistantHomeView.render(AssistantHomeData.load(config.getRuntime()), "/app/assistantAdmin");
      assertThat(html).contains(">Setup</h3>").contains("href=\"/app/assistantAdmin/assistant_model_price\"").contains("Needs setup").contains("Next: ");
   }



   /*******************************************************************************
    ** The action writes the defaults out as rows (READ, or NONE for sensitive
    ** tables), keeps existing rows, adds nothing the second time, and the
    ** written rows are what DataAccessService then reads.
    *******************************************************************************/
   @Test
   void addRowsForEveryTable() throws Exception
   {
      AssistantStore store = adminStore();
      store.insert(new AssistantDataAccess().withObjectType(AssistantDataAccess.TYPE_TABLE).withObjectName("media")
         .withMode(DataAccessMode.READ_WRITE.name()));

      runAction();
      List<AssistantDataAccess> rows = adminStore().query(AssistantDataAccess.TABLE_NAME, new QQueryFilter(), AssistantDataAccess.class);
      long appTables = qInstance.getTables().keySet().stream().filter(t -> !t.startsWith("assistant_")).count();
      assertThat(rows).hasSize((int) appTables);
      assertThat(rows).noneMatch(r -> r.getObjectName().startsWith("assistant_"));
      assertThat(rows).filteredOn(r -> r.getObjectName().equals("media")).singleElement()
         .extracting(AssistantDataAccess::getMode).isEqualTo("READ_WRITE");
      for(AssistantDataAccess row : rows)
      {
         if(!"media".equals(row.getObjectName()))
         {
            assertThat(row.getNotes()).isEqualTo(DataAccessDefaultsProcess.NOTE);
            assertThat(config.getRuntime().getDataAccessService().forTable(row.getObjectName()).source()).as(row.getObjectName()).isEqualTo("setting");
            assertThat(config.getRuntime().getDataAccessService().forTable(row.getObjectName()).mode().name()).isEqualTo(row.getMode());
         }
      }
      assertThat(rows).anyMatch(r -> "READ".equals(r.getMode()));

      runAction();
      assertThat(adminStore().query(AssistantDataAccess.TABLE_NAME, new QQueryFilter(), AssistantDataAccess.class)).hasSize(rows.size());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void runAction() throws Exception
   {
      adminStore();
      RunProcessInput input = new RunProcessInput();
      input.setProcessName(DataAccessDefaultsProcess.NAME);
      input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      new RunProcessAction().execute(input);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private Map<String, AssistantSetupStatus.Item> items() throws Exception
   {
      adminStore();
      return (AssistantHomeData.load(config.getRuntime()).getSetup().stream()
         .collect(Collectors.toMap(AssistantSetupStatus.Item::label, i -> i, (a, b) -> a, java.util.LinkedHashMap::new)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private AssistantStore adminStore()
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
      return (config.getRuntime().getStore());
   }

}
