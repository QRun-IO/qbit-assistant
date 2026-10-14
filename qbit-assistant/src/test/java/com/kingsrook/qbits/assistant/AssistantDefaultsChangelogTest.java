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


import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.kingsrook.qbits.assistant.engine.BudgetService;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.engine.ProfileService;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantBudgetUsage;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantProfileSkill;
import com.kingsrook.qbits.assistant.model.AssistantSkill;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.context.QContext;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The shipped starter defaults (db/liquibase/qbit-assistant/
 ** assistant-defaults.changelog.sql): they apply to a fresh schema built from
 ** docs/schema-postgres.sql, re-running inserts nothing and keeps an
 ** administrator's edits, and the seeded rows change what the assistant does
 ** (persona, skills offered to the model, budget warnings, policy).
 *******************************************************************************/
class AssistantDefaultsChangelogTest extends BaseAssistantTest
{
   static final String CHANGELOG = "/db/liquibase/qbit-assistant/assistant-defaults.changelog.sql";

   /*******************************************************************************
    ** The tables the changelog seeds; each must be created by the reference DDL.
    *******************************************************************************/
   static final Set<String> SEEDED_TABLES = Set.of("assistant_policy", "assistant_profile", "assistant_skill", "assistant_profile_skill",
      "assistant_budget", "assistant_model_price", "assistant_scheduled_task");

   private static final Set<String> DDL_TABLES = Set.of("assistant_provider", "assistant_model", "assistant_model_price", "assistant_policy",
      "assistant_profile", "assistant_skill", "assistant_profile_skill", "assistant_budget", "assistant_scheduled_task");



   /*******************************************************************************
    ** Fresh schema, seed twice: same rows; an edit survives.
    *******************************************************************************/
   @Test
   void seedsAFreshSchemaInsertOnly() throws Exception
   {
      try(Connection connection = freshSchema())
      {
         runChangelog(connection);
         assertThat(ranChangesets(connection)).hasSize(10).allSatisfy((id, type) -> assertThat(type).as(id).isEqualTo("EXECUTED"));
         Map<String, Integer> first = counts(connection);
         assertThat(first).containsEntry("assistant_policy", 1).containsEntry("assistant_profile", 1).containsEntry("assistant_skill", 4)
            .containsEntry("assistant_profile_skill", 4).containsEntry("assistant_budget", 1).containsEntry("assistant_scheduled_task", 1);
         assertThat(first.get("assistant_model_price")).isEqualTo(12);

         try(Statement statement = connection.createStatement())
         {
            statement.execute("UPDATE assistant_budget SET limit_value = 75 WHERE name = 'Monthly spend (whole instance)'");
            statement.execute("UPDATE assistant_profile SET display_name = 'Ada' WHERE name = 'default'");
         }

         runChangelog(connection);
         assertThat(ranChangesets(connection)).hasSize(10);
         assertThat(counts(connection)).isEqualTo(first);
         assertThat(scalar(connection, "SELECT limit_value FROM assistant_budget")).isEqualTo("75");
         assertThat(scalar(connection, "SELECT display_name FROM assistant_profile")).isEqualTo("Ada");
         assertThat(scalar(connection, "SELECT COUNT(*) FROM assistant_scheduled_task WHERE enabled = FALSE AND profile_id IS NOT NULL")).isEqualTo("1");
      }
   }



   /*******************************************************************************
    ** A host that already configured the assistant: its policy, default
    ** profile, budget and prices are left exactly as they were (no second
    ** default, no new limit, no price that overrides its configured pricing).
    *******************************************************************************/
   @Test
   void neverChangesAnExistingHost() throws Exception
   {
      try(Connection connection = freshSchema(); Statement statement = connection.createStatement())
      {
         statement.execute("INSERT INTO assistant_policy (name, kill_switch) VALUES ('host', TRUE)");
         statement.execute("INSERT INTO assistant_profile (name, display_name, is_default, enabled) VALUES ('host', 'Hal', TRUE, TRUE)");
         statement.execute("INSERT INTO assistant_budget (name, scope_type, period, metric, limit_value) VALUES ('Host daily', 'USER', 'DAY', 'TOKENS', 1000)");
         statement.execute("INSERT INTO assistant_model_price (provider_type, model, input_usd_per_m_tok, output_usd_per_m_tok) VALUES ('anthropic', 'claude-sonnet-5-5', 1.00, 5.00)");

         runChangelog(connection);
         assertThat(scalar(connection, "SELECT COUNT(*) FROM assistant_policy")).isEqualTo("1");
         assertThat(scalar(connection, "SELECT name FROM assistant_policy")).isEqualTo("host");
         assertThat(scalar(connection, "SELECT COUNT(*) FROM assistant_profile WHERE is_default = TRUE")).isEqualTo("1");
         assertThat(scalar(connection, "SELECT name FROM assistant_profile")).isEqualTo("host");
         assertThat(scalar(connection, "SELECT COUNT(*) FROM assistant_budget")).isEqualTo("1");
         assertThat(scalar(connection, "SELECT name FROM assistant_budget")).isEqualTo("Host daily");
         assertThat(scalar(connection, "SELECT COUNT(*) FROM assistant_model_price")).isEqualTo("1");
         assertThat(scalar(connection, "SELECT input_usd_per_m_tok FROM assistant_model_price")).isEqualTo("1");
         assertThat(scalar(connection, "SELECT COUNT(*) FROM assistant_skill")).as("starter skills are still added").isEqualTo("4");
         assertThat(scalar(connection, "SELECT COUNT(*) FROM assistant_profile_skill")).as("the host's profile is not changed").isEqualTo("0");
         assertThat(ranChangesets(connection)).containsEntry("defaults-1", "MARK_RAN").containsEntry("defaults-2", "MARK_RAN")
            .containsEntry("defaults-8", "MARK_RAN").containsEntry("defaults-9", "MARK_RAN");
      }
   }



   /*******************************************************************************
    ** Rolling the changelog back never deletes data: not the host's own rows
    ** (changesets marked ran included), not the seeded ones.
    *******************************************************************************/
   @Test
   void rollbackKeepsEveryRow() throws Exception
   {
      try(Connection connection = freshSchema(); Statement statement = connection.createStatement())
      {
         statement.execute("INSERT INTO assistant_policy (name, kill_switch) VALUES ('default', FALSE)");
         statement.execute("INSERT INTO assistant_profile (name, display_name, is_default, enabled) VALUES ('default', 'Hal', TRUE, TRUE)");
         statement.execute("INSERT INTO assistant_skill (name, instructions) VALUES ('find-and-count', 'The host wrote this.')");
         runChangelog(connection);
         Map<String, Integer> before = counts(connection);

         Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
         new Liquibase(CHANGELOG.substring(1), new ClassLoaderResourceAccessor(), database).rollback(10, (String) null);

         assertThat(ranChangesets(connection)).isEmpty();
         assertThat(counts(connection)).isEqualTo(before);
         assertThat(scalar(connection, "SELECT display_name FROM assistant_profile WHERE name = 'default'")).isEqualTo("Hal");
         assertThat(scalar(connection, "SELECT instructions FROM assistant_skill WHERE name = 'find-and-count'")).isEqualTo("The host wrote this.");
      }
   }



   /*******************************************************************************
    ** A host without one of the tables: that changeset is marked ran, the
    ** rest apply, nothing fails.
    *******************************************************************************/
   @Test
   void missingTableIsSkipped() throws Exception
   {
      try(Connection connection = freshSchema(); Statement statement = connection.createStatement())
      {
         statement.execute("DROP TABLE assistant_model_price");
         runChangelog(connection);
         assertThat(ranChangesets(connection)).containsEntry("defaults-9", "MARK_RAN").containsEntry("defaults-8", "EXECUTED").containsEntry("defaults-7", "EXECUTED");
         assertThat(scalar(connection, "SELECT COUNT(*) FROM assistant_profile_skill")).isEqualTo("4");
      }
   }



   /*******************************************************************************
    ** Every changeset guards itself: a precondition (host without the table
    ** marks it ran) and an insert that skips rows that exist.
    *******************************************************************************/
   @Test
   void everyChangesetIsGuarded() throws Exception
   {
      List<String> changesets = changesets(changelogText());
      assertThat(changesets).hasSizeGreaterThanOrEqualTo(SEEDED_TABLES.size());
      for(String changeset : changesets)
      {
         assertThat(changeset).contains("-- preconditions onFail:MARK_RAN onError:MARK_RAN").contains("-- precondition-sql-check expectedResult:0 ")
            .doesNotContain("precondition-table-exists").contains("-- rollback empty").contains("NOT EXISTS");
      }
      for(String table : SEEDED_TABLES)
      {
         assertThat(changelogText()).contains("INSERT INTO " + table + " ");
      }
   }



   /*******************************************************************************
    ** The seeded rows drive the runtime: persona, skills reach the model's
    ** request, the budget warns at 80%, and the policy is read.
    *******************************************************************************/
   @Test
   void seededRowsChangeBehavior() throws Exception
   {
      try(Connection connection = freshSchema())
      {
         runChangelog(connection);
         copyIntoStore(connection);
      }

      ProfileService profiles = config.getRuntime().getProfileService();
      profiles.invalidate();
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.EDITOR));
      ProfileService.Persona persona = profiles.persona(QContext.getQSession(), "content");
      assertThat(persona.profileId()).isNotNull();
      assertThat(persona.greeting()).startsWith("Hi! I can find and count records");
      assertThat(persona.starterPrompts()).contains("What changed in the last 7 days?");
      assertThat(profiles.relevantSkills(persona.profileId(), "How many posts were published this month?", "content"))
         .extracting(AssistantSkill::getName).contains("find-and-count").doesNotContain("safe-changes");

      ///////////////////////////////////////////////////////////////
      // a real turn: the matching skill is in the model's request //
      ///////////////////////////////////////////////////////////////
      provider.thenText("There are 4.");
      runner.run(AssistantTestFixture.EDITOR, "How many posts were published this month?");
      assertThat(provider.getRequestJson()).last().asString().contains("find-and-count").contains("Always include a filterLinks item").doesNotContain("safe-changes");

      ///////////////////////////////////////////
      // the starter budget warns at 80% of $50 //
      ///////////////////////////////////////////
      AssistantStore store = adminStore();
      store.insert(new AssistantBudgetUsage().withUsageId(900_001).withUserId(AssistantTestFixture.EDITOR).withOccurredAt(Instant.now())
         .withCostMicros(40_000_000L).withInputTokens(0L).withOutputTokens(0L));
      BudgetService.Decision decision = config.getRuntime().getBudgetService().evaluate(AssistantTestFixture.EDITOR, null, false, false);
      assertThat(decision.stopped()).isFalse();
      assertThat(decision.notices()).singleElement().asString().contains("80%", "Monthly spend (whole instance)");

      ////////////////////////////////////////////////
      // the seeded policy: writes wait, 50 rows max //
      ////////////////////////////////////////////////
      PolicyService.EffectivePolicy policy = config.getRuntime().getPolicyService().current(true);
      assertThat(policy.killSwitch()).isFalse();
      assertThat(policy.tools().writesRequireConfirmation()).isTrue();
      assertThat(policy.tools().maxRowsPerCall()).isEqualTo(50);
   }



   /*******************************************************************************
    ** Copy the seeded profile, skills, links, budget and policy into the
    ** test instance's store (ids remapped).
    *******************************************************************************/
   private void copyIntoStore(Connection connection) throws Exception
   {
      AssistantStore       store      = adminStore();
      Map<Integer, Integer> profileIds = new HashMap<>();
      Map<Integer, Integer> skillIds   = new HashMap<>();
      try(Statement statement = connection.createStatement())
      {
         try(ResultSet rs = statement.executeQuery("SELECT * FROM assistant_profile"))
         {
            while(rs.next())
            {
               AssistantProfile row = store.insert(new AssistantProfile().withName(rs.getString("name")).withDisplayName(rs.getString("display_name"))
                  .withIcon(rs.getString("icon")).withPersonality(rs.getString("personality")).withGreeting(rs.getString("greeting"))
                  .withStarterPrompts(rs.getString("starter_prompts")).withIsDefault(rs.getBoolean("is_default")).withEnabled(rs.getBoolean("enabled")));
               profileIds.put(rs.getInt("id"), row.getId());
            }
         }
         try(ResultSet rs = statement.executeQuery("SELECT * FROM assistant_skill"))
         {
            while(rs.next())
            {
               AssistantSkill row = store.insert(new AssistantSkill().withName(rs.getString("name")).withDescription(rs.getString("description"))
                  .withInstructions(rs.getString("instructions")).withEnabled(rs.getBoolean("enabled")).withTriggerKeywords(rs.getString("trigger_keywords")));
               skillIds.put(rs.getInt("id"), row.getId());
            }
         }
         try(ResultSet rs = statement.executeQuery("SELECT * FROM assistant_profile_skill"))
         {
            while(rs.next())
            {
               store.insert(new AssistantProfileSkill().withProfileId(profileIds.get(rs.getInt("profile_id"))).withSkillId(skillIds.get(rs.getInt("skill_id"))));
            }
         }
         try(ResultSet rs = statement.executeQuery("SELECT * FROM assistant_budget"))
         {
            while(rs.next())
            {
               store.insert(new AssistantBudget().withName(rs.getString("name")).withEnabled(rs.getBoolean("enabled")).withScopeType(rs.getString("scope_type"))
                  .withPeriod(rs.getString("period")).withMetric(rs.getString("metric")).withLimitValue(rs.getBigDecimal("limit_value"))
                  .withWarnPercent(rs.getInt("warn_percent")).withAtLimitAction(rs.getString("at_limit_action")).withHardCeilingPercent(rs.getInt("hard_ceiling_percent")));
            }
         }
         try(ResultSet rs = statement.executeQuery("SELECT * FROM assistant_policy"))
         {
            while(rs.next())
            {
               store.insert(new AssistantPolicy().withName(rs.getString("name")).withKillSwitch(rs.getBoolean("kill_switch"))
                  .withWritesRequireConfirmation(rs.getBoolean("writes_require_confirmation")).withRedactPii(rs.getBoolean("redact_pii"))
                  .withMaxRowsPerCall(rs.getInt("max_rows_per_call")));
            }
         }
      }
      assertThat(new BigDecimal("50")).isEqualByComparingTo(store.budgets().get(0).getLimitValue());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private AssistantStore adminStore()
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
      return (config.getRuntime().getStore());
   }



   /*******************************************************************************
    ** A fresh H2 (PostgreSQL mode) with the seeded tables from the reference DDL.
    *******************************************************************************/
   static Connection freshSchema() throws Exception
   {
      Connection connection = DriverManager.getConnection("jdbc:h2:mem:defaults" + System.nanoTime() + ";MODE=PostgreSQL;DEFAULT_NULL_ORDERING=HIGH");
      String     ddl        = Files.readString(Path.of("..", "docs", "schema-postgres.sql"), StandardCharsets.UTF_8);
      try(Statement statement = connection.createStatement())
      {
         for(String sql : statements(ddl))
         {
            Matcher m = Pattern.compile("^(CREATE TABLE|ALTER TABLE) (assistant_[a-z_]+)\\b(.*)", Pattern.DOTALL).matcher(sql);
            if(m.find() && DDL_TABLES.contains(m.group(2)) && (m.group(1).startsWith("CREATE") || m.group(3).strip().startsWith("ADD COLUMN")))
            {
               statement.execute(sql.replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE"));
            }
         }
      }
      return (connection);
   }



   /*******************************************************************************
    ** Run the changelog through Liquibase, as a host does.
    *******************************************************************************/
   static void runChangelog(Connection connection) throws Exception
   {
      Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
      new Liquibase(CHANGELOG.substring(1), new ClassLoaderResourceAccessor(), database).update(new Contexts());
   }



   /*******************************************************************************
    ** changeset id -> EXECTYPE (EXECUTED, MARK_RAN, ...).
    *******************************************************************************/
   static Map<String, String> ranChangesets(Connection connection) throws Exception
   {
      Map<String, String> rs = new HashMap<>();
      try(Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT id, exectype FROM databasechangelog"))
      {
         while(result.next())
         {
            rs.put(result.getString(1), result.getString(2));
         }
      }
      return (rs);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static String changelogText() throws Exception
   {
      try(InputStream stream = AssistantDefaultsChangelogTest.class.getResourceAsStream(CHANGELOG))
      {
         assertThat(stream).as(CHANGELOG + " is on the classpath").isNotNull();
         return (new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
   }



   /*******************************************************************************
    ** The text of each changeset (from its "-- changeset" line).
    *******************************************************************************/
   static List<String> changesets(String changelog)
   {
      List<String> rs    = new ArrayList<>();
      String[]     parts = changelog.split("(?m)^(?=-- changeset )");
      for(String part : parts)
      {
         if(part.startsWith("-- changeset "))
         {
            rs.add(part);
         }
      }
      return (rs);
   }



   /*******************************************************************************
    ** SQL statements, without comment lines, split at a ';' that ends a line.
    *******************************************************************************/
   static List<String> statements(String sql)
   {
      StringBuilder text = new StringBuilder();
      for(String line : sql.split("\\R"))
      {
         String stripped = line.replaceAll("\\s+--.*$", "");
         if(!line.strip().startsWith("--"))
         {
            text.append(stripped).append("\n");
         }
      }
      List<String> rs = new ArrayList<>();
      for(String statement : text.toString().split(";\\s*\\n"))
      {
         if(!statement.isBlank())
         {
            rs.add(statement.strip().replaceAll(";$", ""));
         }
      }
      return (rs);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Integer> counts(Connection connection) throws Exception
   {
      Map<String, Integer> rs = new HashMap<>();
      for(String table : SEEDED_TABLES)
      {
         rs.put(table, Integer.valueOf(scalar(connection, "SELECT COUNT(*) FROM " + table)));
      }
      return (rs);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String scalar(Connection connection, String sql) throws Exception
   {
      try(Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql))
      {
         rs.next();
         BigDecimal number = rs.getObject(1) instanceof BigDecimal bd ? bd.stripTrailingZeros() : null;
         return (number != null ? number.toPlainString() : rs.getString(1));
      }
   }

}
