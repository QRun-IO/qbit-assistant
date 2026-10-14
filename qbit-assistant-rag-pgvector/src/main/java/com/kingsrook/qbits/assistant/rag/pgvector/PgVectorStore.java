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


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import javax.sql.DataSource;
import com.kingsrook.qbits.assistant.rag.ChunkVector;
import com.kingsrook.qbits.assistant.rag.VectorStore;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Host-supplied JDBC only, without QQQ RDBMS internals. No text is ever stored.
 *******************************************************************************/
public class PgVectorStore implements VectorStore
{
   private final ConnectionSupplier connections;
   private final Integer dimensions;
   private String schema = "assistant_rag";



   /*******************************************************************************
    ** The host owns credentials and connection pooling; each call closes its lease.
    *******************************************************************************/
   @FunctionalInterface
   public interface ConnectionSupplier
   {
      /*******************************************************************************
       ** Return a connection owned by this caller until it closes it.
       *******************************************************************************/
      Connection open() throws SQLException;
   }



   /*******************************************************************************
    ** Use any host DataSource and a fixed vector dimension for this instance.
    *******************************************************************************/
   public PgVectorStore(DataSource dataSource, Integer dimensions)
   {
      this(dataSource::getConnection, dimensions);
   }



   /*******************************************************************************
    ** HNSW vector cosine indexes support up to 2000 dimensions.
    *******************************************************************************/
   public PgVectorStore(ConnectionSupplier connections, Integer dimensions)
   {
      if(connections == null || dimensions == null || dimensions < 1 || dimensions > 2000)
      {
         throw (new IllegalArgumentException("A connection supplier and 1 to 2000 dimensions are required."));
      }
      this.connections = connections;
      this.dimensions = dimensions;
   }



   /*******************************************************************************
    ** Configure before use. Identifiers cannot be parameters, so validate strictly.
    *******************************************************************************/
   public PgVectorStore withSchema(String schema)
   {
      if(schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}"))
      {
         throw (new IllegalArgumentException("The vector schema must be a lower-case SQL identifier of at most 63 characters."));
      }
      this.schema = schema;
      return (this);
   }



   /*******************************************************************************
    ** The DBA normally installs vector. An existing extension needs no elevated
    ** privileges; if absent, attempt installation and report an actionable error.
    *******************************************************************************/
   public void ensureSchema() throws QException
   {
      try(Connection connection = connections.open())
      {
         try(PreparedStatement check = connection.prepareStatement("SELECT 1 FROM pg_extension WHERE extname = ?"))
         {
            check.setString(1, "vector");
            try(ResultSet rows = check.executeQuery())
            {
               if(!rows.next())
               {
                  try(PreparedStatement extension = connection.prepareStatement("CREATE EXTENSION IF NOT EXISTS vector"))
                  {
                     extension.execute();
                  }
                  catch(SQLException e)
                  {
                     throw (new QException("Ask the DBA to run CREATE EXTENSION IF NOT EXISTS vector in this database, then retry.", e));
                  }
               }
            }
         }
         for(String sql : List.of("CREATE SCHEMA IF NOT EXISTS " + schema,
            "CREATE TABLE IF NOT EXISTS " + table() + " (id BIGSERIAL PRIMARY KEY, source_id INT NOT NULL, table_name TEXT NOT NULL, record_pk TEXT NOT NULL, field_name TEXT NOT NULL, chunk_index INT NOT NULL, content_hash CHAR(64) NOT NULL, embedding vector(" + dimensions + ") NOT NULL, indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(source_id, table_name, record_pk, field_name, chunk_index))",
            "CREATE INDEX IF NOT EXISTS assistant_rag_chunk_cosine ON " + table() + " USING hnsw (embedding vector_cosine_ops)"))
         {
            try(PreparedStatement statement = connection.prepareStatement(sql))
            {
               statement.execute();
            }
         }
      }
      catch(SQLException e)
      {
         throw (failure(e));
      }
   }



   /*******************************************************************************
    ** Validate the entire batch before sending anything to JDBC.
    *******************************************************************************/
   @Override
   public void upsert(List<ChunkVector> chunks) throws QException
   {
      chunks.forEach(chunk -> vector(chunk.embedding()));
      try(Connection connection = connections.open())
      {
         upsert(connection, chunks);
      }
      catch(SQLException e)
      {
         throw (failure(e));
      }
   }



   /*******************************************************************************
    ** Values, including vectors and arbitrary record keys, are bound parameters.
    *******************************************************************************/
   private void upsert(Connection connection, List<ChunkVector> chunks) throws SQLException
   {
      String sql = "INSERT INTO " + table() + " (source_id, table_name, record_pk, field_name, chunk_index, content_hash, embedding) VALUES (?, ?, ?, ?, ?, ?, ?::vector) ON CONFLICT (source_id, table_name, record_pk, field_name, chunk_index) DO UPDATE SET content_hash = EXCLUDED.content_hash, embedding = EXCLUDED.embedding, indexed_at = CURRENT_TIMESTAMP";
      try(PreparedStatement statement = connection.prepareStatement(sql))
      {
         for(ChunkVector chunk : chunks)
         {
            statement.setInt(1, chunk.source());
            statement.setString(2, chunk.table());
            statement.setString(3, chunk.pk());
            statement.setString(4, chunk.field());
            statement.setInt(5, chunk.chunkIndex());
            statement.setString(6, chunk.contentHash());
            statement.setString(7, vector(chunk.embedding()));
            statement.addBatch();
         }
         statement.executeBatch();
      }
   }



   /*******************************************************************************
    ** A record replacement is transactional, including empty replacements.
    *******************************************************************************/
   @Override
   public void replaceRecord(Integer source, String table, String pk, List<ChunkVector> chunks) throws QException
   {
      for(ChunkVector chunk : chunks)
      {
         vector(chunk.embedding());
         if(!source.equals(chunk.source()) || !table.equals(chunk.table()) || !pk.equals(chunk.pk()))
         {
            throw (new IllegalArgumentException("Replacement chunks must belong to the specified record."));
         }
      }
      try(Connection connection = connections.open())
      {
         Boolean autoCommit = connection.getAutoCommit();
         connection.setAutoCommit(false);
         try
         {
            deleteRecord(connection, source, table, pk);
            upsert(connection, chunks);
            connection.commit();
         }
         catch(SQLException e)
         {
            connection.rollback();
            throw (e);
         }
         finally
         {
            connection.setAutoCommit(autoCommit);
         }
      }
      catch(SQLException e)
      {
         throw (failure(e));
      }
   }



   /*******************************************************************************
    ** Remove one record, always scoped to its source.
    *******************************************************************************/
   @Override
   public void deleteRecord(Integer source, String table, String pk) throws QException
   {
      try(Connection connection = connections.open())
      {
         deleteRecord(connection, source, table, pk);
      }
      catch(SQLException e)
      {
         throw (failure(e));
      }
   }



   /*******************************************************************************
    ** Shared transactional delete statement.
    *******************************************************************************/
   private void deleteRecord(Connection connection, Integer source, String table, String pk) throws SQLException
   {
      try(PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table() + " WHERE source_id = ? AND table_name = ? AND record_pk = ?"))
      {
         statement.setInt(1, source);
         statement.setString(2, table);
         statement.setString(3, pk);
         statement.executeUpdate();
      }
   }



   /*******************************************************************************
    ** Clear a source, preserving every other source in the instance.
    *******************************************************************************/
   @Override
   public void deleteSource(Integer source) throws QException
   {
      try(Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table() + " WHERE source_id = ?"))
      {
         statement.setInt(1, source);
         statement.executeUpdate();
      }
      catch(SQLException e)
      {
         throw (failure(e));
      }
   }



   /*******************************************************************************
    ** HNSW cosine ordering; tables are values, never interpolated SQL identifiers.
    *******************************************************************************/
   @Override
   public List<Hit> search(float[] query, Integer k, Set<String> tables) throws QException
   {
      return (search(query, k, tables, Set.of()));
   }



   /*******************************************************************************
    ** Apply enabled compatible source restrictions before the nearest-hit limit.
    *******************************************************************************/
   @Override
   public List<Hit> search(float[] query, Integer k, Set<String> tables, Set<Integer> sources) throws QException
   {
      if(tables.isEmpty() || k <= 0)
      {
         return (List.of());
      }
      String vector = vector(query);
      String sql = "SELECT source_id, table_name, record_pk, field_name, chunk_index, 1 - (embedding <=> ?::vector) AS score FROM " + table()
         + " WHERE table_name IN (" + String.join(", ", Collections.nCopies(tables.size(), "?")) + ")"
         + (sources.isEmpty() ? "" : " AND source_id IN (" + String.join(", ", Collections.nCopies(sources.size(), "?")) + ")")
         + " ORDER BY embedding <=> ?::vector LIMIT ?";
      try(Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql))
      {
         Integer parameter = 1;
         statement.setString(parameter++, vector);
         for(String table : tables)
         {
            statement.setString(parameter++, table);
         }
         for(Integer source : sources)
         {
            statement.setInt(parameter++, source);
         }
         statement.setString(parameter++, vector);
         statement.setInt(parameter, k);
         List<Hit> hits = new ArrayList<>();
         try(ResultSet rows = statement.executeQuery())
         {
            while(rows.next())
            {
               Double score = rows.getDouble("score");
               if(Double.isFinite(score))
               {
                  hits.add(new Hit(rows.getInt("source_id"), rows.getString("table_name"), rows.getString("record_pk"), rows.getString("field_name"), rows.getInt("chunk_index"), score));
               }
            }
         }
         return (hits);
      }
      catch(SQLException e)
      {
         throw (failure(e));
      }
   }



   /*******************************************************************************
    ** Count chunks for the source's status screen.
    *******************************************************************************/
   @Override
   public Long count(Integer source) throws QException
   {
      try(Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table() + " WHERE source_id = ?"))
      {
         statement.setInt(1, source);
         try(ResultSet rows = statement.executeQuery())
         {
            rows.next();
            return (rows.getLong(1));
         }
      }
      catch(SQLException e)
      {
         throw (failure(e));
      }
   }



   /*******************************************************************************
    ** Content-free reconciliation data. Embedding text is pgvector's numeric form.
    *******************************************************************************/
   @Override
   public List<ChunkVector> chunks(Integer source) throws QException
   {
      try(Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement("SELECT table_name, record_pk, field_name, chunk_index, content_hash, embedding::text FROM " + table() + " WHERE source_id = ?"))
      {
         statement.setInt(1, source);
         List<ChunkVector> chunks = new ArrayList<>();
         try(ResultSet rows = statement.executeQuery())
         {
            while(rows.next())
            {
               chunks.add(new ChunkVector(source, rows.getString("table_name"), rows.getString("record_pk"), rows.getString("field_name"), rows.getInt("chunk_index"), rows.getString("content_hash"), parse(rows.getString("embedding"))));
            }
         }
         return (chunks);
      }
      catch(SQLException e)
      {
         throw (failure(e));
      }
   }



   /*******************************************************************************
    ** Qualified name uses only the validated instance schema.
    *******************************************************************************/
   private String table()
   {
      return (schema + ".assistant_rag_chunk");
   }



   /*******************************************************************************
    ** Serialize only finite coordinates with the configured dimension.
    *******************************************************************************/
   private String vector(float[] vector)
   {
      if(vector == null || vector.length != dimensions)
      {
         throw (new IllegalArgumentException("Vector dimensions differ from the configured pgvector schema."));
      }
      StringJoiner values = new StringJoiner(",", "[", "]");
      for(float value : vector)
      {
         if(!Float.isFinite(value))
         {
            throw (new IllegalArgumentException("Vector coordinates must be finite."));
         }
         values.add(Float.toString(value));
      }
      return (values.toString());
   }



   /*******************************************************************************
    ** Parse the database's numeric representation without any source content.
    *******************************************************************************/
   private float[] parse(String text)
   {
      String[] parts = text.substring(1, text.length() - 1).split(",");
      float[] vector = new float[parts.length];
      for(Integer i = 0; i < parts.length; i++)
      {
         vector[i] = Float.parseFloat(parts[i]);
      }
      vector(vector);
      return (vector);
   }



   /*******************************************************************************
    ** Do not expose database URLs, SQL values or credentials in tool responses.
    *******************************************************************************/
   private static QException failure(SQLException cause)
   {
      return (new QException("The knowledge vector store is unavailable. Check its schema, dimensions and database permissions.", cause));
   }
}
