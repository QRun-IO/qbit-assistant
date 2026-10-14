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


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;


/*******************************************************************************
 ** Deterministic overlapping chunks. Text exists only during indexing/retrieval.
 *******************************************************************************/
public final class TextChunks
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private TextChunks()
   {
   }



   /*******************************************************************************
    ** Fixed character boundaries are reproduced on the fresh UI API value.
    *******************************************************************************/
   public static List<String> split(String text, Integer size, Integer overlap)
   {
      if(size == null || overlap == null || size < 1 || overlap < 0 || overlap >= size)
      {
         throw (new IllegalArgumentException("Chunk overlap must be nonnegative and smaller than chunk size."));
      }
      List<String> chunks = new ArrayList<>();
      for(Integer start = 0; start < text.length(); start += size - overlap)
      {
         Integer end = Math.min(text.length(), start + size);
         chunks.add(text.substring(start, end));
         if(end == text.length())
         {
            break;
         }
      }
      return (chunks);
   }



   /*******************************************************************************
    ** SHA-256 permits unchanged detection without retaining any text.
    *******************************************************************************/
   public static String hash(String text)
   {
      try
      {
         return (HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))));
      }
      catch(NoSuchAlgorithmException e)
      {
         throw (new IllegalStateException("SHA-256 is required by the Java runtime.", e));
      }
   }
}
