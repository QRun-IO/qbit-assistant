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

package com.kingsrook.qbits.assistant.persistence;


import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinOn;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Guardrail for the reference DDL (docs/schema-postgres.sql) on a real
 ** PostgreSQL: every relation the qbit declares (a table possible-value source
 ** on an assistant_ field, or a join between assistant_ tables), plus the
 ** relations only the code knows, has a foreign key with the intended ON DELETE
 ** rule, and every foreign key column leads an index. A new relation without a
 ** foreign key fails here unless it is added to EXEMPT with a reason.
 *******************************************************************************/
@Testcontainers(disabledWithoutDocker = true)
class SchemaForeignKeyPostgresTest extends BaseAssistantTest
{
   @Container
   static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

   /////////////////////////////////////////////////////////////////////////
   // relations the meta-data does not declare (no possible-value source) //
   /////////////////////////////////////////////////////////////////////////
   private static final List<Relation> CODE_ONLY = List.of(
      new Relation("assistant_data_audit", "turn_id", "assistant_turn"),
      new Relation("assistant_data_audit", "provider_id", "assistant_provider"),
      new Relation("assistant_budget_usage", "provider_id", "assistant_provider"));

   //////////////////////////////////////////////////////////////////
   // relations that deliberately have no foreign key, with reason //
   //////////////////////////////////////////////////////////////////
   private static final Map<Relation, String> EXEMPT = Map.of(
      new Relation("assistant_budget_usage", "usage_id", "assistant_usage"),
      "budget accounting projection: it must outlive its usage row so budgets keep counting the spend");

   ///////////////////////////////////////////////////////////////////////////
   // the intended ON DELETE rule (pg confdeltype: a NO ACTION, c CASCADE,  //
   // n SET NULL) - see the foreign key block in docs/schema-postgres.sql   //
   ///////////////////////////////////////////////////////////////////////////
   private static final Map<Relation, String> RULES = new LinkedHashMap<>();

   static
   {
      rule("assistant_message", "conversation_id", "assistant_conversation", "c");
      rule("assistant_message", "parent_message_id", "assistant_message", "n");
      rule("assistant_message", "turn_id", "assistant_turn", "n");
      rule("assistant_turn", "conversation_id", "assistant_conversation", "c");
      rule("assistant_turn", "user_message_id", "assistant_message", "n");
      rule("assistant_turn", "assistant_message_id", "assistant_message", "n");
      rule("assistant_turn_event", "turn_id", "assistant_turn", "c");
      rule("assistant_tool_call", "conversation_id", "assistant_conversation", "c");
      rule("assistant_tool_call", "turn_id", "assistant_turn", "c");
      rule("assistant_tool_call", "pending_action_id", "assistant_pending_action", "n");
      rule("assistant_feedback", "conversation_id", "assistant_conversation", "c");
      rule("assistant_feedback", "message_id", "assistant_message", "c");
      rule("assistant_client_error", "conversation_id", "assistant_conversation", "n");
      rule("assistant_client_error", "message_id", "assistant_message", "n");
      rule("assistant_client_error", "turn_id", "assistant_turn", "n");
      rule("assistant_pending_action", "conversation_id", "assistant_conversation", "c");
      rule("assistant_pending_action", "turn_id", "assistant_turn", "n");
      rule("assistant_data_audit", "conversation_id", "assistant_conversation", "n");
      rule("assistant_data_audit", "turn_id", "assistant_turn", "n");
      rule("assistant_data_audit", "provider_id", "assistant_provider", "a");
      rule("assistant_usage", "conversation_id", "assistant_conversation", "n");
      rule("assistant_usage", "turn_id", "assistant_turn", "n");
      rule("assistant_usage", "provider_id", "assistant_provider", "a");
      rule("assistant_budget_usage", "provider_id", "assistant_provider", "a");
      rule("assistant_profile_skill", "profile_id", "assistant_profile", "c");
      rule("assistant_profile_skill", "skill_id", "assistant_skill", "c");
      rule("assistant_model", "provider_id", "assistant_provider", "c");
      rule("assistant_scheduled_run", "conversation_id", "assistant_conversation", "n");
      rule("assistant_scheduled_run", "turn_id", "assistant_turn", "n");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   record Relation(String child, String column, String parent)
   {
      @Override
      public String toString()
      {
         return (child + "." + column + " -> " + parent);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void rule(String child, String column, String parent, String rule)
   {
      RULES.put(new Relation(child, column, parent), rule);
   }



   /*******************************************************************************
    ** Apply the whole reference DDL, then its foreign key block a second time
    ** (it must be re-runnable on an existing database).
    *******************************************************************************/
   @BeforeAll
   static void applyDdl() throws Exception
   {
      String ddl = Files.readString(Path.of("..", "docs", "schema-postgres.sql"));
      try(Connection connection = connect(); Statement statement = connection.createStatement())
      {
         statement.execute(ddl);
         int start = ddl.indexOf("-- Foreign keys (2026-10-14)");
         if(start >= 0)
         {
            statement.execute(ddl.substring(start));
         }
      }
   }



   /*******************************************************************************
    ** Every declared or code-only relation has a foreign key, unless exempt.
    *******************************************************************************/
   @Test
   void everyRelationHasAForeignKey() throws Exception
   {
      Set<Relation> relations = new LinkedHashSet<>(declaredRelations());
      assertThat(relations).as("declared relations found in the meta-data").hasSizeGreaterThan(20);
      relations.addAll(CODE_ONLY);

      Map<Relation, String> foreignKeys = foreignKeys();
      List<String>          missing     = new ArrayList<>();
      for(Relation relation : relations)
      {
         if(!foreignKeys.containsKey(relation) && !EXEMPT.containsKey(relation))
         {
            missing.add(relation.toString());
         }
      }
      assertThat(missing).as("relations without a foreign key (add one to docs/schema-postgres.sql, or to EXEMPT with a reason)").isEmpty();

      for(Relation exempt : EXEMPT.keySet())
      {
         assertThat(foreignKeys).as("exempt relation " + exempt + " must stay without a foreign key").doesNotContainKey(exempt);
      }
   }



   /*******************************************************************************
    ** The ON DELETE rules match how the qbit deletes.
    *******************************************************************************/
   @Test
   void deleteRulesMatchTheDeletePaths() throws Exception
   {
      Map<Relation, String> foreignKeys = foreignKeys();
      for(Map.Entry<Relation, String> entry : RULES.entrySet())
      {
         assertThat(foreignKeys.get(entry.getKey())).as(entry.getKey().toString()).isEqualTo(entry.getValue());
      }
   }



   /*******************************************************************************
    ** Every foreign key column is the first column of some index.
    *******************************************************************************/
   @Test
   void everyForeignKeyColumnLeadsAnIndex() throws Exception
   {
      List<String> unindexed = new ArrayList<>();
      try(Connection connection = connect(); Statement statement = connection.createStatement();
         ResultSet rs = statement.executeQuery("""
            SELECT c.conrelid::regclass::text, a.attname
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
            WHERE c.contype = 'f' AND c.connamespace = 'public'::regnamespace
              AND NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = c.conrelid AND i.indkey[0] = c.conkey[1])
            """))
      {
         while(rs.next())
         {
            unindexed.add(rs.getString(1) + "." + rs.getString(2));
         }
      }
      assertThat(unindexed).isEmpty();
   }



   /*******************************************************************************
    ** AssistantStore.deleteConversation's delete order works under the foreign
    ** keys; usage (cost accounting) survives with its pointers nulled. A plain
    ** delete of a conversation (admin screen) cascades through the
    ** message <-> turn cycle and keeps usage and the data audit.
    *******************************************************************************/
   @Test
   void conversationDeletesWorkUnderTheForeignKeys() throws Exception
   {
      try(Connection connection = connect(); Statement statement = connection.createStatement())
      {
         connection.setAutoCommit(false);
         for(int c : new int[] { 1, 2 })
         {
            int m = c * 10;
            statement.execute("INSERT INTO assistant_provider (id, name, provider_type) VALUES (" + c + ", 'p" + c + "', 'anthropic')");
            statement.execute("INSERT INTO assistant_conversation (id, user_id) VALUES (" + c + ", 'u')");
            statement.execute("INSERT INTO assistant_message (id, conversation_id, user_id, role) VALUES (" + m + ", " + c + ", 'u', 'USER')");
            statement.execute("INSERT INTO assistant_turn (id, conversation_id, user_id, user_message_id, provider_id) VALUES (" + c + ", " + c + ", 'u', " + m + ", NULL)");
            statement.execute("INSERT INTO assistant_message (id, conversation_id, user_id, role, turn_id, parent_message_id) VALUES (" + (m + 1) + ", " + c + ", 'u', 'ASSISTANT', " + c + ", " + m + ")");
            statement.execute("UPDATE assistant_message SET turn_id = " + c + " WHERE id = " + m);
            statement.execute("UPDATE assistant_turn SET assistant_message_id = " + (m + 1) + " WHERE id = " + c);
            statement.execute("INSERT INTO assistant_turn_event (turn_id, user_id) VALUES (" + c + ", 'u')");
            statement.execute("INSERT INTO assistant_pending_action (id, conversation_id, turn_id, user_id) VALUES (" + c + ", " + c + ", " + c + ", 'u')");
            statement.execute("INSERT INTO assistant_tool_call (conversation_id, turn_id, pending_action_id, user_id) VALUES (" + c + ", " + c + ", " + c + ", 'u')");
            statement.execute("INSERT INTO assistant_feedback (conversation_id, message_id, user_id) VALUES (" + c + ", " + (m + 1) + ", 'u')");
            statement.execute("INSERT INTO assistant_data_audit (id, conversation_id, turn_id, provider_id, user_id) VALUES (" + c + ", " + c + ", " + c + ", " + c + ", 'u')");
            statement.execute("INSERT INTO assistant_usage (id, conversation_id, turn_id, provider_id, user_id) VALUES (" + c + ", " + c + ", " + c + ", " + c + ", 'u')");
         }

         ////////////////////////////////////////////////////////////
         // conversation 1: AssistantStore.deleteConversation order //
         ////////////////////////////////////////////////////////////
         statement.execute("DELETE FROM assistant_turn_event WHERE turn_id IN (SELECT id FROM assistant_turn WHERE conversation_id = 1)");
         for(String table : List.of("assistant_tool_call", "assistant_feedback", "assistant_message", "assistant_turn", "assistant_data_audit", "assistant_pending_action"))
         {
            statement.execute("DELETE FROM " + table + " WHERE conversation_id = 1");
         }
         statement.execute("DELETE FROM assistant_conversation WHERE id = 1");
         assertThat(queryString(statement, "SELECT conversation_id || '/' || turn_id FROM assistant_usage WHERE id = 1")).as("usage kept, pointers nulled").isNull();
         assertThat(queryString(statement, "SELECT provider_id::text FROM assistant_usage WHERE id = 1")).isEqualTo("1");

         //////////////////////////////////////////////
         // conversation 2: a plain conversation delete //
         //////////////////////////////////////////////
         statement.execute("DELETE FROM assistant_conversation WHERE id = 2");
         for(String table : List.of("assistant_message", "assistant_turn", "assistant_tool_call", "assistant_feedback", "assistant_pending_action"))
         {
            assertThat(queryString(statement, "SELECT COUNT(*)::text FROM " + table + " WHERE conversation_id = 2")).as(table).isEqualTo("0");
         }
         assertThat(queryString(statement, "SELECT COUNT(*)::text FROM assistant_turn_event")).isEqualTo("0");
         assertThat(queryString(statement, "SELECT (conversation_id IS NULL AND turn_id IS NULL)::text FROM assistant_data_audit WHERE id = 2")).as("data audit kept").isEqualTo("true");
         assertThat(queryString(statement, "SELECT (conversation_id IS NULL AND turn_id IS NULL)::text FROM assistant_usage WHERE id = 2")).as("usage kept").isEqualTo("true");
         connection.rollback();
      }
   }



   /*******************************************************************************
    ** Possible-value-source fields and joins between assistant_ tables.
    *******************************************************************************/
   private List<Relation> declaredRelations()
   {
      List<Relation> relations = new ArrayList<>();
      for(QTableMetaData table : qInstance.getTables().values())
      {
         if(!table.getName().startsWith("assistant_"))
         {
            continue;
         }
         for(QFieldMetaData field : table.getFields().values())
         {
            QPossibleValueSource pvs = field.getPossibleValueSourceName() == null ? null : qInstance.getPossibleValueSource(field.getPossibleValueSourceName());
            if(pvs != null && pvs.getType() == QPossibleValueSourceType.TABLE && pvs.getTableName().startsWith("assistant_"))
            {
               relations.add(new Relation(table.getName(), snake(field.getName()), pvs.getTableName()));
            }
         }
      }
      for(QJoinMetaData join : qInstance.getJoins().values())
      {
         if(!join.getLeftTable().startsWith("assistant_") || !join.getRightTable().startsWith("assistant_"))
         {
            continue;
         }
         for(JoinOn on : join.getJoinOns())
         {
            if("id".equals(on.getLeftField()))
            {
               relations.add(new Relation(join.getRightTable(), snake(on.getRightField()), join.getLeftTable()));
            }
            else if("id".equals(on.getRightField()))
            {
               relations.add(new Relation(join.getLeftTable(), snake(on.getLeftField()), join.getRightTable()));
            }
         }
      }
      return (relations);
   }



   /*******************************************************************************
    ** Single-column foreign keys in the database, with their delete rule.
    *******************************************************************************/
   private static Map<Relation, String> foreignKeys() throws Exception
   {
      Map<Relation, String> foreignKeys = new LinkedHashMap<>();
      try(Connection connection = connect(); Statement statement = connection.createStatement();
         ResultSet rs = statement.executeQuery("""
            SELECT c.conrelid::regclass::text, a.attname, c.confrelid::regclass::text, c.confdeltype
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
            WHERE c.contype = 'f' AND array_length(c.conkey, 1) = 1 AND c.connamespace = 'public'::regnamespace
            """))
      {
         while(rs.next())
         {
            foreignKeys.put(new Relation(rs.getString(1), rs.getString(2), rs.getString(3)), rs.getString(4));
         }
      }
      return (foreignKeys);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String queryString(Statement statement, String sql) throws Exception
   {
      try(ResultSet rs = statement.executeQuery(sql))
      {
         return (rs.next() ? rs.getString(1) : null);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String snake(String camel)
   {
      return (camel.replaceAll("([A-Z])", "_$1").toLowerCase());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Connection connect() throws Exception
   {
      return (DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
   }
}
