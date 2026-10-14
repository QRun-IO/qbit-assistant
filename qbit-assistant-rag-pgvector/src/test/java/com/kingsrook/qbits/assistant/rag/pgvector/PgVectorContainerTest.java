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


import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.assistant.rag.ChunkVector;
import com.kingsrook.qbits.assistant.rag.TextChunks;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Real pgvector contract; skipped automatically on hosts without Docker.
 *******************************************************************************/
@Testcontainers(disabledWithoutDocker = true)
class PgVectorContainerTest
{
   @Container
   static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));



   /*******************************************************************************
    ** DDL idempotence, cosine ranking, upsert, deletes and instance isolation.
    *******************************************************************************/
   @Test
   void realVectorLifecycle() throws Exception
   {
      PgVectorStore.ConnectionSupplier connections = () -> DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
      PgVectorStore store = new PgVectorStore(connections, 3).withSchema("instance_one");
      PgVectorStore other = new PgVectorStore(connections, 3).withSchema("instance_two");
      store.ensureSchema();
      store.ensureSchema();
      other.ensureSchema();
      ChunkVector first = new ChunkVector(1, "article", "1", "body", 0, TextChunks.hash("one"), new float[] { 1, 0, 0 });
      ChunkVector second = new ChunkVector(1, "article", "2", "body", 0, TextChunks.hash("two"), new float[] { 0, 1, 0 });
      store.upsert(List.of(first, second));
      store.upsert(List.of(first));
      assertThat(store.count(1)).isEqualTo(2);
      assertThat(other.count(1)).isZero();
      assertThat(store.search(new float[] { 1, 0, 0 }, 1, Set.of("article"))).singleElement().satisfies(hit ->
      {
         assertThat(hit.pk()).isEqualTo("1");
         assertThat(hit.score()).isEqualTo(1.0);
      });
      assertThat(store.chunks(1)).hasSize(2);
      store.replaceRecord(1, "article", "1", List.of());
      assertThat(store.count(1)).isEqualTo(1);
      store.deleteRecord(1, "article", "2");
      assertThat(store.count(1)).isZero();
      store.upsert(List.of(first));
      store.deleteSource(1);
      assertThat(store.count(1)).isZero();
   }
}
