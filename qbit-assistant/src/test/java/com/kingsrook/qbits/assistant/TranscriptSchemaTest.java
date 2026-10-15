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


import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Execute the payload column definitions and upgrade statements on H2. Other
 ** schema constraints have their own Postgres guardrail; this checks expansion.
 *******************************************************************************/
class TranscriptSchemaTest
{
   /*******************************************************************************
    ** Both fresh installs and upgraded columns accept expanded token sequences.
    *******************************************************************************/
   @Test
   void redactedColumnsAcceptExpandedTokens() throws Exception
   {
      Map<String, List<String>> columns = Map.of(
         "assistant_turn", List.of("stop_reason"),
         "assistant_tool_call", List.of("tool_call_id", "result_summary"),
         "assistant_feedback", List.of("reason"),
         "assistant_usage", List.of("request_id"),
         "assistant_pending_action", List.of("tool_call_id", "summary", "result_summary"),
         "assistant_data_audit", List.of("reason"),
         "assistant_budget_override", List.of("reason"),
         "assistant_scheduled_run", List.of("summary", "error"));
      String ddl = Files.readString(Path.of("..", "docs", "schema-postgres.sql"));
      String tokens = "hmac:v1:test:0123456789abcdefghijklmnopqrstuvwxyzABCDEFG".repeat(100);
      try(var connection = DriverManager.getConnection("jdbc:h2:mem:transcript" + System.nanoTime() + ";MODE=PostgreSQL");
         var statement = connection.createStatement())
      {
         for(var table : columns.entrySet())
         {
            var create = Pattern.compile("CREATE TABLE " + table.getKey() + "\\s*\\((.*?)\\n\\);", Pattern.DOTALL).matcher(ddl);
            assertThat(create.find()).as(table.getKey()).isTrue();
            for(String column : table.getValue())
            {
               var definition = Pattern.compile("(?m)^   " + column + " (TEXT|VARCHAR\\(\\d+\\))").matcher(create.group(1));
               assertThat(definition.find()).as(column).isTrue();
               statement.execute("DROP TABLE IF EXISTS " + table.getKey());
               statement.execute("CREATE TABLE " + table.getKey() + " (" + column + " " + definition.group(1) + ")");
               try(var insert = connection.prepareStatement("INSERT INTO " + table.getKey() + " VALUES (?)"))
               {
                  insert.setString(1, tokens);
                  assertThat(insert.executeUpdate()).isEqualTo(1);
               }
               String migration = "ALTER TABLE " + table.getKey() + " ALTER COLUMN " + column + " SET DATA TYPE TEXT";
               if(!"result_summary".equals(column) && !("assistant_pending_action".equals(table.getKey()) && "summary".equals(column)))
               {
                  assertThat(ddl).contains(migration + ";");
                  statement.execute("DELETE FROM " + table.getKey());
                  statement.execute("ALTER TABLE " + table.getKey() + " ALTER COLUMN " + column + " SET DATA TYPE VARCHAR(64)");
                  statement.execute(migration);
                  statement.execute(migration);
                  try(var insert = connection.prepareStatement("INSERT INTO " + table.getKey() + " VALUES (?)"))
                  {
                     insert.setString(1, tokens);
                     assertThat(insert.executeUpdate()).isEqualTo(1);
                  }
               }
               try(var result = statement.executeQuery("SELECT " + column + " FROM " + table.getKey()))
               {
                  assertThat(result.next()).isTrue();
                  assertThat(result.getString(1)).isEqualTo(tokens);
               }
            }
         }
      }
   }
}
