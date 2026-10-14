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


import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.kingsrook.qbits.assistant.metadata.AssistantHelp;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Guardrail (owner requirement 2026-10-14: "all tables should be populated and
 ** the qbit should be fully using them"). For every assistant_* table:
 ** - runtime code (not just its model, metadata or home screen) reads or
 **   writes it, so no table exists only on paper;
 ** - it is either seeded by the shipped defaults changelog, or listed here
 **   with why it starts empty;
 ** - it has bundle help that says what it is for and how its rows get
 **   created (shown on the table screen and when it is empty).
 ** A new table fails this test until it is wired, seeded or explained.
 *******************************************************************************/
class AssistantTablesGuardrailTest extends BaseAssistantTest
{
   private static final Path MAIN = Path.of("src", "main", "java", "com", "kingsrook", "qbits", "assistant");

   /*******************************************************************************
    ** Tables that legitimately start empty, and where their rows come from.
    *******************************************************************************/
   static final Map<String, String> STARTS_EMPTY = Map.ofEntries(
      Map.entry("assistant_provider", "host connection config: needs the host's key variable (kof22 seeds its providers)"),
      Map.entry("assistant_model", "Discover models on a provider, or the host"),
      Map.entry("assistant_rag_source", "host-specific: a host table plus an embedding model"),
      Map.entry("assistant_data_access", "defaults apply without rows; 'Add rows for every table' writes them out"),
      Map.entry("assistant_budget_override", "an administrator, when a budget is reached"),
      Map.entry("assistant_budget_event", "runtime: a budget threshold is crossed"),
      Map.entry("assistant_budget_usage", "runtime: every model call"),
      Map.entry("assistant_scheduled_run", "runtime: a scheduled task runs"),
      Map.entry("assistant_conversation", "user content: someone asks a question"),
      Map.entry("assistant_message", "user content: the conversation"),
      Map.entry("assistant_turn", "runtime: every answer"),
      Map.entry("assistant_turn_event", "runtime: every answer's event stream"),
      Map.entry("assistant_tool_call", "runtime: every tool call"),
      Map.entry("assistant_pending_action", "runtime: a proposed change"),
      Map.entry("assistant_feedback", "user content: a rating"),
      Map.entry("assistant_usage", "runtime: every model call"),
      Map.entry("assistant_usage_daily", "runtime: after every answer"),
      Map.entry("assistant_data_audit", "runtime: every model call carrying data"));



   /*******************************************************************************
    ** Every table is used by runtime code.
    *******************************************************************************/
   @Test
   void everyTableIsUsedAtRuntime() throws Exception
   {
      Map<String, String> entityByTable = entityByTable();
      List<String>        runtimeSource;
      try(Stream<Path> files = Files.walk(MAIN))
      {
         runtimeSource = files.filter(p -> p.toString().endsWith(".java"))
            .filter(p -> !p.startsWith(MAIN.resolve("model")) && !p.startsWith(MAIN.resolve("metadata")) && !p.startsWith(MAIN.resolve("home")))
            .map(AssistantTablesGuardrailTest::read).toList();
      }
      for(String table : assistantTables())
      {
         String entity = entityByTable.get(table);
         assertThat(entity).as("entity class for " + table).isNotNull();
         Pattern use = Pattern.compile("\\b" + entity + "(\\.TABLE_NAME|\\.class|\\(|::)");
         assertThat(runtimeSource.stream().anyMatch(source -> use.matcher(source).find()))
            .as(table + " (" + entity + ") must be read or written by runtime code, not only declared").isTrue();
      }
   }



   /*******************************************************************************
    ** Every table is seeded or explained.
    *******************************************************************************/
   @Test
   void everyTableIsSeededOrExplained() throws Exception
   {
      String changelog = AssistantDefaultsChangelogTest.changelogText();
      for(String table : assistantTables())
      {
         boolean seeded = AssistantDefaultsChangelogTest.SEEDED_TABLES.contains(table);
         assertThat(seeded || STARTS_EMPTY.containsKey(table)).as(table + " must be seeded by the defaults changelog or listed in STARTS_EMPTY").isTrue();
         assertThat(seeded && STARTS_EMPTY.containsKey(table)).as(table + " cannot be both seeded and listed as starting empty").isFalse();
         assertThat(changelog.contains("INSERT INTO " + table + " ")).as(table + " seeded iff the changelog inserts it").isEqualTo(seeded);
      }
      assertThat(STARTS_EMPTY.keySet()).allMatch(table -> qInstance.getTable(table) != null, "STARTS_EMPTY names only real tables");
   }



   /*******************************************************************************
    ** Every table and the app have bundle help that says how rows get created.
    *******************************************************************************/
   @Test
   void everyTableHasEmptyStateHelp() throws Exception
   {
      assertThat(AssistantHelp.entries()).allSatisfy((key, entry) -> assertThat(entry.path("summary").asText().length()).as(key).isLessThanOrEqualTo(120));
      for(String table : assistantTables())
      {
         List<QHelpContent> help = qInstance.getTable(table).getHelpContent() == null ? null : qInstance.getTable(table).getHelpContent().get(AssistantHelp.SLOT);
         assertThat(help).as(table + " table help").isNotEmpty();
         assertThat(help.get(0).getContent()).as(table + " help says how rows get created").contains("How rows get created");
      }
      assertThat(qInstance.getApp(AssistantMetaDataBuilder.APP_NAME).getHelpContent()).containsKey(AssistantHelp.SLOT);
      assertThat(AssistantHelp.entries().keySet()).allMatch(key -> !key.startsWith("table:") || qInstance.getTable(key.substring(6, key.indexOf(';'))) != null,
         "help names only real tables");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<String> assistantTables()
   {
      List<String> tables = qInstance.getTables().values().stream().map(QTableMetaData::getName).filter(n -> n.startsWith("assistant_")).sorted().toList();
      assertThat(tables).hasSizeGreaterThan(20);
      return (tables);
   }



   /*******************************************************************************
    ** model/X.java declares TABLE_NAME = "x".
    *******************************************************************************/
   private static Map<String, String> entityByTable() throws Exception
   {
      Map<String, String> rs = new HashMap<>();
      Pattern             p  = Pattern.compile("TABLE_NAME\\s*=\\s*\"([a-z_]+)\"");
      try(Stream<Path> files = Files.list(MAIN.resolve("model")))
      {
         for(Path file : files.toList())
         {
            Matcher m = p.matcher(read(file));
            if(m.find())
            {
               rs.put(m.group(1), file.getFileName().toString().replace(".java", ""));
            }
         }
      }
      return (rs);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String read(Path path)
   {
      try
      {
         return (Files.readString(path, StandardCharsets.UTF_8));
      }
      catch(Exception e)
      {
         throw (new IllegalStateException(e));
      }
   }

}
