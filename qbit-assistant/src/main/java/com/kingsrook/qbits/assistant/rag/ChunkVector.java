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


/*******************************************************************************
 ** Content-free index entry. The embedding is copied on entry and on read.
 *******************************************************************************/
public record ChunkVector(Integer source, String table, String pk, String field, Integer chunkIndex, String contentHash, float[] embedding)
{
   /*******************************************************************************
    ** No mutable vector can alter an indexed entry after insertion.
    *******************************************************************************/
   public ChunkVector
   {
      if(source == null || table == null || pk == null || field == null || chunkIndex == null || chunkIndex < 0
         || contentHash == null || !contentHash.matches("[a-f0-9]{64}") || embedding == null || embedding.length == 0)
      {
         throw (new IllegalArgumentException("Incomplete chunk vector metadata."));
      }
      embedding = embedding.clone();
      for(float value : embedding)
      {
         if(!Float.isFinite(value))
         {
            throw (new IllegalArgumentException("Embedding coordinates must be finite."));
         }
      }
   }



   /*******************************************************************************
    ** Return a defensive copy.
    *******************************************************************************/
   @Override
   public float[] embedding()
   {
      return (embedding.clone());
   }
}
