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

package com.kingsrook.qbits.assistant.rag.pgvector;


import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import com.kingsrook.qbits.assistant.rag.ChunkVector;
import com.kingsrook.qbits.assistant.rag.TextChunks;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Always-on JDBC SQL-shape and transaction tests, with no Docker dependency.
 *******************************************************************************/
class PgVectorStoreTest
{
   private final List<String> sql = new ArrayList<>();
   private final List<Map<Integer, Object>> parameters = new ArrayList<>();
   private final List<String> operations = new ArrayList<>();
   private Boolean extensionExists = true;
   private Boolean failBatch = false;
   private Boolean failExtension = false;



   /*******************************************************************************
    ** JDBC recording connection; values never appear in SQL text.
    *******************************************************************************/
   private Connection connection()
   {
      return ((Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class }, (proxy, method, args) ->
      {
         operations.add(method.getName());
         if(method.getName().equals("prepareStatement"))
         {
            return (statement((String) args[0]));
         }
         return (method.getName().equals("getAutoCommit") ? true : null);
      }));
   }



   /*******************************************************************************
    ** Capture each binding and emulate rows or SQL failures.
    *******************************************************************************/
   private PreparedStatement statement(String query)
   {
      sql.add(query);
      Map<Integer, Object> values = new HashMap<>();
      parameters.add(values);
      return ((PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { PreparedStatement.class }, (proxy, method, args) ->
      {
         if(method.getName().startsWith("set"))
         {
            values.put((Integer) args[0], args[1]);
         }
         if(method.getName().equals("executeQuery"))
         {
            return (rows(query));
         }
         if(method.getName().equals("executeBatch"))
         {
            if(failBatch)
            {
               throw (new SQLException("synthetic batch failure"));
            }
            return (new int[] { 1 });
         }
         if(method.getName().equals("executeUpdate"))
         {
            return (1);
         }
         if(method.getName().equals("execute"))
         {
            if(failExtension && query.startsWith("CREATE EXTENSION"))
            {
               throw (new SQLException("extension privilege required"));
            }
            return (true);
         }
         return (null);
      }));
   }



   /*******************************************************************************
    ** One content-free result; the extension probe can return no rows.
    *******************************************************************************/
   private ResultSet rows(String query)
   {
      Boolean[] first = { true };
      return ((ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class }, (proxy, method, args) ->
      {
         if(method.getName().equals("next"))
         {
            Boolean next = first[0] && (!query.contains("pg_extension") || extensionExists);
            first[0] = false;
            return (next);
         }
         return (switch(method.getName())
         {
            case "getString" -> switch(String.valueOf(args[0]))
            {
               case "content_hash" -> TextChunks.hash("synthetic");
               case "embedding" -> "[1,0,0]";
               case "record_pk" -> "pk'; --";
               case "field_name" -> "body";
               default -> "article";
            };
            case "getInt" -> "chunk_index".equals(args[0]) ? 0 : 1;
            case "getLong" -> 1L;
            case "getDouble" -> 1.0;
            default -> null;
         });
      }));
   }



   /*******************************************************************************
    ** Exercise every statement with values that would be unsafe to interpolate.
    *******************************************************************************/
   @Test
   void statementsAreBoundAndContentFree() throws Exception
   {
      PgVectorStore store = new PgVectorStore(this::connection, 3).withSchema("instance_one");
      store.ensureSchema();
      assertThat(sql).anyMatch(s -> s.contains("embedding vector(3)"));
      assertThat(sql).anyMatch(s -> s.contains("USING hnsw (embedding vector_cosine_ops)"));
      assertThat(sql).noneMatch(s -> s.contains("CREATE EXTENSION"));
      ChunkVector chunk = new ChunkVector(1, "article", "pk'; --", "body", 0, TextChunks.hash("synthetic"), new float[] { 1, 0, 0 });
      store.upsert(List.of(chunk));
      store.replaceRecord(1, "article", "pk'; --", List.of(chunk));
      assertThat(store.search(new float[] { 1, 0, 0 }, 8, Set.of("article", "injection'; --"))).hasSize(1);
      assertThat(store.search(new float[] { 1, 0, 0 }, 8, Set.of("article"), Set.of(1))).hasSize(1);
      assertThat(store.chunks(1)).singleElement().satisfies(v -> assertThat(v.embedding()).containsExactly(1, 0, 0));
      assertThat(store.count(1)).isEqualTo(1);
      store.deleteRecord(1, "article", "pk'; --");
      store.deleteSource(1);
      assertThat(sql).anyMatch(s -> s.contains("?::vector") && s.contains("ON CONFLICT"));
      assertThat(sql).anyMatch(s -> s.contains("ORDER BY embedding <=> ?::vector LIMIT ?"));
      assertThat(sql).noneMatch(s -> s.contains("pk'; --") || s.contains("injection'; --") || s.contains("synthetic"));
      assertThat(parameters).anyMatch(p -> p.containsValue("pk'; --"));
      assertThat(parameters).anyMatch(p -> p.containsValue("injection'; --"));
      assertThat(operations).contains("commit", "close", "setAutoCommit");
      assertThat(store.search(new float[] { 1, 0, 0 }, 8, Set.of())).isEmpty();
      assertThat(store.search(new float[] { 1, 0, 0 }, 0, Set.of("article"))).isEmpty();
      DataSource dataSource = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { DataSource.class }, (p, method, args) -> connection());
      assertThat(new PgVectorStore(dataSource, 3).count(1)).isEqualTo(1);
   }



   /*******************************************************************************
    ** DBA failure is actionable; failed replacement rolls back before closing.
    *******************************************************************************/
   @Test
   void extensionAndRollback() throws Exception
   {
      extensionExists = false;
      PgVectorStore store = new PgVectorStore(this::connection, 3);
      store.ensureSchema();
      assertThat(sql).contains("CREATE EXTENSION IF NOT EXISTS vector");
      failExtension = true;
      assertThatThrownBy(store::ensureSchema).hasMessageContaining("DBA");
      failBatch = true;
      assertThatThrownBy(() -> store.replaceRecord(1, "article", "1", List.of())).hasMessageContaining("vector store");
      assertThat(operations).contains("rollback");
      PgVectorStore down = new PgVectorStore(() ->
      {
         throw (new SQLException("offline"));
      }, 3);
      assertThatThrownBy(down::ensureSchema).hasMessageContaining("vector store");
      assertThatThrownBy(() -> down.count(1)).hasMessageContaining("vector store");
      assertThatThrownBy(() -> down.chunks(1)).hasMessageContaining("vector store");
      assertThatThrownBy(() -> down.upsert(List.of())).hasMessageContaining("vector store");
      assertThatThrownBy(() -> down.deleteRecord(1, "a", "1")).hasMessageContaining("vector store");
      assertThatThrownBy(() -> down.deleteSource(1)).hasMessageContaining("vector store");
      assertThatThrownBy(() -> down.search(new float[] { 1, 0, 0 }, 8, Set.of("a"))).hasMessageContaining("vector store");
   }



   /*******************************************************************************
    ** Identifier and coordinate validation happens before opening a connection.
    *******************************************************************************/
   @Test
   void invalidInputsNeverReachJdbc()
   {
      PgVectorStore store = new PgVectorStore(this::connection, 3);
      for(String name : List.of("a.b", "bad; DROP SCHEMA public", "UPPER", "a".repeat(64)))
      {
         assertThatThrownBy(() -> store.withSchema(name)).isInstanceOf(IllegalArgumentException.class);
      }
      assertThatThrownBy(() -> new PgVectorStore(this::connection, 2001)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> store.search(new float[] { 1 }, 1, Set.of("a"))).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> store.search(new float[] { Float.NaN, 0, 0 }, 1, Set.of("a"))).isInstanceOf(IllegalArgumentException.class);
      assertThat(operations).isEmpty();
   }
}
