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

package com.kingsrook.qbits.assistant.classification;


import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;


/*******************************************************************************
 ** The assistant's conventions over core's {@link DataClassification}: an
 ** absent classification is PUBLIC, and a stored level this version does not
 ** know is treated as PHI (fail conservative).
 *******************************************************************************/
public final class Classifications
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private Classifications()
   {
   }



   /*******************************************************************************
    ** The level, or PUBLIC when absent.
    *******************************************************************************/
   public static DataClassification orPublic(DataClassification classification)
   {
      return (classification == null ? DataClassification.PUBLIC : classification);
   }



   /*******************************************************************************
    ** Highest of two levels; absent classifications are PUBLIC.
    *******************************************************************************/
   public static DataClassification max(DataClassification left, DataClassification right)
   {
      return (DataClassification.max(orPublic(left), orPublic(right)));
   }



   /*******************************************************************************
    ** Legacy nulls mean PUBLIC; unknown persisted levels fail conservatively.
    *******************************************************************************/
   public static DataClassification stored(String value)
   {
      if(value == null)
      {
         return (DataClassification.PUBLIC);
      }
      try
      {
         return (DataClassification.valueOf(value));
      }
      catch(IllegalArgumentException e)
      {
         return (DataClassification.PHI);
      }
   }
}
