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


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantModelPrice;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantProfileSkill;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantRagSource;
import com.kingsrook.qbits.assistant.model.AssistantScheduledTask;
import com.kingsrook.qbits.assistant.model.AssistantSkill;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.spi.ModelPricing;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;


/*******************************************************************************
 ** The Setup card on the Assistant home: each configuration table, whether it
 ** is set up, and the next step. Only reads the QBit's own configuration
 ** tables (small), as the signed-in administrator.
 *******************************************************************************/
public final class AssistantSetupStatus
{
   private static final int LIMIT = 1000;



   /*******************************************************************************
    ** OK: working. TODO: the assistant needs it. DEFAULT: working on built-in
    ** defaults (nothing to do). OPTIONAL: a feature that is off until set up.
    *******************************************************************************/
   public enum State
   {
      OK, TODO, DEFAULT, OPTIONAL
   }



   /*******************************************************************************
    ** One line of the card.
    *******************************************************************************/
   public record Item(String label, String tableName, State state, String detail, String nextStep)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AssistantSetupStatus()
   {
   }



   /*******************************************************************************
    ** Build the items. `providers` is what the home already loaded.
    *******************************************************************************/
   public static List<Item> load(AssistantRuntime runtime, List<AssistantHomeData.ProviderHealth> providers) throws QException
   {
      AssistantStore store = runtime.getStore();
      List<Item>     items = new ArrayList<>();

      ///////////////
      // providers //
      ///////////////
      AssistantHomeData.ProviderHealth active = providers.stream().filter(AssistantHomeData.ProviderHealth::active).findFirst().orElse(null);
      if(active != null && active.keyPresent() && active.problems().isEmpty())
      {
         items.add(new Item("Provider", AssistantProvider.TABLE_NAME, State.OK, active.row().getName() + " answers questions.", null));
      }
      else if(providers.isEmpty())
      {
         items.add(new Item("Provider", AssistantProvider.TABLE_NAME, State.TODO, "No provider yet.", "Add one with New: pick the type and model, and name the environment variable that holds the API key."));
      }
      else
      {
         items.add(new Item("Provider", AssistantProvider.TABLE_NAME, State.TODO, "The default provider cannot be used.", "Turn one on, set it as default, make sure its key variable is set, then run Test connection."));
      }

      //////////////////////////////////////////////////////////////////////
      // prices: resolved exactly as calls are costed (registry row, price //
      // row, then the configured price table); local models cost nothing //
      //////////////////////////////////////////////////////////////////////
      int priceRows = store.query(AssistantModelPrice.TABLE_NAME, new QQueryFilter().withLimit(LIMIT), AssistantModelPrice.class).size();
      String rowsText = priceRows + " price row" + (priceRows == 1 ? "" : "s");
      if(active == null)
      {
         items.add(new Item("Model prices", AssistantModelPrice.TABLE_NAME, State.DEFAULT, rowsText + "; checked once a provider answers.", null));
      }
      else
      {
         AssistantProvider row   = active.row();
         ModelPricing      price = runtime.getProviderService().pricing(new ProviderService.Active(row, row.getProviderType(), row.getModel(), null), row.getModel());
         boolean           local = "LOCAL".equals(active.location());
         boolean           known = price != null && (positive(price.getInputUsdPerMTok()) || positive(price.getOutputUsdPerMTok()));
         items.add(known || local
            ? new Item("Model prices", AssistantModelPrice.TABLE_NAME, State.OK, local && !known ? "The default model is local and costs nothing. " + rowsText + "."
               : "The default model is priced at $" + price.getInputUsdPerMTok() + " in / $" + price.getOutputUsdPerMTok() + " out per million tokens. " + rowsText + ".", null)
            : new Item("Model prices", AssistantModelPrice.TABLE_NAME, State.TODO, "The default model (" + row.getModel() + ") has no price, so its calls cost $0 and cost budgets never trigger.",
               "Add a price row for its provider type and model."));
      }

      ////////////
      // policy //
      ////////////
      List<AssistantPolicy> policies = store.query(AssistantPolicy.TABLE_NAME, new QQueryFilter().withLimit(1), AssistantPolicy.class);
      if(policies.isEmpty())
      {
         items.add(new Item("Policy", AssistantPolicy.TABLE_NAME, State.DEFAULT, "No policy row: the code configuration applies.", "Add one to control the kill switch, confirmations and tool limits here."));
      }
      else if(Boolean.TRUE.equals(policies.get(0).getKillSwitch()))
      {
         items.add(new Item("Policy", AssistantPolicy.TABLE_NAME, State.TODO, "The kill switch is on: the assistant answers nothing.", "Turn it off on the policy row when ready."));
      }
      else
      {
         items.add(new Item("Policy", AssistantPolicy.TABLE_NAME, State.OK, "Writes " + (Boolean.FALSE.equals(policies.get(0).getWritesRequireConfirmation()) ? "do not wait" : "wait") + " for confirmation.", null));
      }

      ////////////////////////
      // profiles and skills //
      ////////////////////////
      List<AssistantProfile> profiles = enabled(store.query(AssistantProfile.TABLE_NAME, new QQueryFilter().withLimit(LIMIT), AssistantProfile.class));
      boolean hasDefault = profiles.stream().anyMatch(p -> Boolean.TRUE.equals(p.getIsDefault()));
      items.add(profiles.isEmpty()
         ? new Item("Profiles", AssistantProfile.TABLE_NAME, State.DEFAULT, "No profile: the built-in assistant answers, with no skills.", "Add a default profile to name the assistant, set its tone and offer skills.")
         : new Item("Profiles", AssistantProfile.TABLE_NAME, hasDefault ? State.OK : State.DEFAULT,
            profiles.size() + " profile" + (profiles.size() == 1 ? "" : "s") + (hasDefault ? ", with a default." : ", none of them the default."),
            hasDefault ? null : "Mark one as the default so it applies everywhere else."));

      Set<Integer> linkedSkillIds = new HashSet<>();
      Set<Integer> profileIds     = new HashSet<>(profiles.stream().map(AssistantProfile::getId).toList());
      for(AssistantProfileSkill link : store.query(AssistantProfileSkill.TABLE_NAME, new QQueryFilter().withLimit(LIMIT * 5), AssistantProfileSkill.class))
      {
         if(profileIds.contains(link.getProfileId()))
         {
            linkedSkillIds.add(link.getSkillId());
         }
      }
      List<AssistantSkill> skills  = enabled(store.query(AssistantSkill.TABLE_NAME, new QQueryFilter().withLimit(LIMIT), AssistantSkill.class));
      long                 offered = skills.stream().filter(s -> linkedSkillIds.contains(s.getId())).count();
      items.add(skills.isEmpty()
         ? new Item("Skills", AssistantSkill.TABLE_NAME, State.OPTIONAL, "No skills.", "Write know-how for your business terms, then link it to a profile.")
         : offered == 0
            ? new Item("Skills", AssistantSkill.TABLE_NAME, State.TODO, skills.size() + " skill" + (skills.size() == 1 ? "" : "s") + ", but none is linked to a turned-on profile, so the model never sees them.", "Link them to a profile (Profile skills).")
            : new Item("Skills", AssistantSkill.TABLE_NAME, State.OK, offered + " of " + skills.size() + " skill" + (skills.size() == 1 ? " is" : "s are") + " offered through a profile.", null));

      /////////////
      // budgets //
      /////////////
      List<AssistantBudget> budgets = store.budgets();
      items.add(budgets.isEmpty()
         ? new Item("Budgets", AssistantBudget.TABLE_NAME, State.TODO, "No budget: spending is limited only by the policy's configured caps.", "Add a monthly instance budget (warn, downgrade, stop).")
         : new Item("Budgets", AssistantBudget.TABLE_NAME, State.OK, budgets.size() + " budget" + (budgets.size() == 1 ? "" : "s") + " checked before every question.", null));

      /////////////////
      // data access //
      /////////////////
      int accessRows = store.query(AssistantDataAccess.TABLE_NAME, new QQueryFilter().withLimit(LIMIT), AssistantDataAccess.class).size();
      items.add(accessRows == 0
         ? new Item("Data access", AssistantDataAccess.TABLE_NAME, State.DEFAULT, "Defaults: the assistant may read what each user can read; it proposes no changes.",
            "Run Add rows for every table to review them, then raise tables to READ_WRITE where it may propose changes.")
         : new Item("Data access", AssistantDataAccess.TABLE_NAME, State.OK, accessRows + " row" + (accessRows == 1 ? "" : "s") + "; tables without a row use the default.", null));

      ///////////////////////////////////////
      // optional: knowledge and schedules //
      ///////////////////////////////////////
      long sources = enabled(store.query(AssistantRagSource.TABLE_NAME, new QQueryFilter().withLimit(LIMIT), AssistantRagSource.class)).size();
      items.add(sources == 0
         ? new Item("Knowledge sources", AssistantRagSource.TABLE_NAME, State.OPTIONAL, "Off: search by meaning is not offered.", "Add a source (a readable table, its text fields and an embedding model), then index it.")
         : new Item("Knowledge sources", AssistantRagSource.TABLE_NAME, State.OK, sources + " source" + (sources == 1 ? "" : "s") + " turned on.", null));

      List<AssistantScheduledTask> tasks = store.query(AssistantScheduledTask.TABLE_NAME, new QQueryFilter().withLimit(LIMIT), AssistantScheduledTask.class);
      long onTasks = tasks.stream().filter(t -> Boolean.TRUE.equals(t.getEnabled())).count();
      items.add(onTasks == 0
         ? new Item("Scheduled tasks", AssistantScheduledTask.TABLE_NAME, State.OPTIONAL, tasks.isEmpty() ? "None." : tasks.size() + " task" + (tasks.size() == 1 ? "" : "s") + ", all turned off.",
            "Set a run-as user and an allowed credential variable on a task, then turn it on.")
         : new Item("Scheduled tasks", AssistantScheduledTask.TABLE_NAME, State.OK, onTasks + " task" + (onTasks == 1 ? "" : "s") + " turned on.", null));
      return (items);
   }



   /*******************************************************************************
    ** Rows not explicitly turned off.
    *******************************************************************************/
   private static <T extends QRecordEntity> List<T> enabled(List<T> rows)
   {
      return (rows.stream().filter(row -> !Boolean.FALSE.equals(row.toQRecord().getValueBoolean("enabled"))).toList());
   }




   /*******************************************************************************
    **
    *******************************************************************************/
   private static boolean positive(Double value)
   {
      return (value != null && value > 0);
   }

}
