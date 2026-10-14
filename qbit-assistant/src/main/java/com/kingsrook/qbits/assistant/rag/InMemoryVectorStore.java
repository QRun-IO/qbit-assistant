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

package com.kingsrook.qbits.assistant.rag;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/*******************************************************************************
 ** Exact cosine search for tests and small hosts. Never stores source text.
 *******************************************************************************/
public class InMemoryVectorStore implements VectorStore
{
   private final Map<List<Object>, ChunkVector> vectors = new LinkedHashMap<>();



   /*******************************************************************************
    ** Upsert immutable entries.
    *******************************************************************************/
   @Override
   public synchronized void upsert(List<ChunkVector> chunks)
   {
      for(ChunkVector chunk : chunks)
      {
         vectors.put(List.of(chunk.source(), chunk.table(), chunk.pk(), chunk.field(), chunk.chunkIndex()), chunk);
      }
   }



   /*******************************************************************************
    ** Remove a record within its source only.
    *******************************************************************************/
   @Override
   public synchronized void deleteRecord(Integer source, String table, String pk)
   {
      vectors.values().removeIf(v -> v.source().equals(source) && v.table().equals(table) && v.pk().equals(pk));
   }



   /*******************************************************************************
    ** A source can be cleared independently of other sources.
    *******************************************************************************/
   @Override
   public synchronized void deleteSource(Integer source)
   {
      vectors.values().removeIf(v -> v.source().equals(source));
   }



   /*******************************************************************************
    ** Search only the named tables and vectors with compatible dimensions.
    *******************************************************************************/
   @Override
   public synchronized List<Hit> search(float[] query, Integer k, Set<String> tables)
   {
      return (search(query, k, tables, Set.of()));
   }



   /*******************************************************************************
    ** Different embedding models are different spaces even at equal dimensions.
    *******************************************************************************/
   @Override
   public synchronized List<Hit> search(float[] query, Integer k, Set<String> tables, Set<Integer> sources)
   {
      List<Hit> hits = new ArrayList<>();
      for(ChunkVector vector : vectors.values())
      {
         if(tables.contains(vector.table()) && (sources.isEmpty() || sources.contains(vector.source())) && query.length == vector.embedding().length)
         {
            Double score = cosine(query, vector.embedding());
            if(Double.isFinite(score))
            {
               hits.add(new Hit(vector.source(), vector.table(), vector.pk(), vector.field(), vector.chunkIndex(), score));
            }
         }
      }
      return (hits.stream().sorted(Comparator.comparing(Hit::score).reversed()).limit(Math.max(0, k)).toList());
   }



   /*******************************************************************************
    ** Zero vectors have no direction and never match.
    *******************************************************************************/
   private static Double cosine(float[] a, float[] b)
   {
      Double dot = 0.0;
      Double left = 0.0;
      Double right = 0.0;
      for(Integer i = 0; i < a.length; i++)
      {
         dot += (double) a[i] * b[i];
         left += (double) a[i] * a[i];
         right += (double) b[i] * b[i];
      }
      return (left == 0 || right == 0 ? Double.NaN : dot / Math.sqrt(left * right));
   }



   /*******************************************************************************
    ** Count by source.
    *******************************************************************************/
   @Override
   public synchronized Long count(Integer source)
   {
      return (vectors.values().stream().filter(v -> v.source().equals(source)).count());
   }



   /*******************************************************************************
    ** Snapshot the content-free entries for reconciliation.
    *******************************************************************************/
   @Override
   public synchronized List<ChunkVector> chunks(Integer source)
   {
      return (vectors.values().stream().filter(v -> v.source().equals(source)).toList());
   }



   /*******************************************************************************
    ** Readers see either complete version of this record.
    *******************************************************************************/
   @Override
   public synchronized void replaceRecord(Integer source, String table, String pk, List<ChunkVector> chunks)
   {
      deleteRecord(source, table, pk);
      upsert(chunks);
   }
}
