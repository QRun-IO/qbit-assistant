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


import java.util.List;
import java.util.Set;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Per-instance vectors only. Every hit must be re-read through the UI API.
 *******************************************************************************/
public interface VectorStore
{
   /*******************************************************************************
    ** Source identity disambiguates field/chunk layouts on the same table.
    *******************************************************************************/
   record Hit(Integer source, String table, String pk, String field, Integer chunkIndex, Double score)
   {
   }

   /*******************************************************************************
    ** Upsert by source/table/key/field/chunk, without storing content.
    *******************************************************************************/
   void upsert(List<ChunkVector> chunks) throws QException;

   /*******************************************************************************
    ** Delete all chunks for a record within a source.
    *******************************************************************************/
   void deleteRecord(Integer source, String table, String pk) throws QException;

   /*******************************************************************************
    ** Remove a source's entire index.
    *******************************************************************************/
   void deleteSource(Integer source) throws QException;

   /*******************************************************************************
    ** Cosine similarity, highest first; an empty table set returns no hits.
    *******************************************************************************/
   List<Hit> search(float[] query, Integer k, Set<String> tables) throws QException;

   /*******************************************************************************
    ** Restrict ranking to compatible enabled sources, before applying the limit.
    *******************************************************************************/
   List<Hit> search(float[] query, Integer k, Set<String> tables, Set<Integer> sources) throws QException;

   /*******************************************************************************
    ** Count indexed chunks.
    *******************************************************************************/
   Long count(Integer source) throws QException;

   /*******************************************************************************
    ** Content-free reconciliation seam for unchanged and disappeared chunks.
    *******************************************************************************/
   List<ChunkVector> chunks(Integer source) throws QException;

   /*******************************************************************************
    ** Atomically replace one record, removing shortened or removed fields.
    *******************************************************************************/
   void replaceRecord(Integer source, String table, String pk, List<ChunkVector> chunks) throws QException;
}
