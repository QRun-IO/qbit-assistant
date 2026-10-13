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

package com.kingsrook.qbits.assistant.access;


/*******************************************************************************
 ** What the assistant may do with a table or process, on top of (never instead
 ** of) the user's own permissions: NONE, READ (read tools), READ_WRITE (read
 ** and write tools; processes: run).
 *******************************************************************************/
public enum DataAccessMode
{
   NONE("None"),
   READ("Read"),
   READ_WRITE("Read and write");

   private final String label;



   /*******************************************************************************
    **
    *******************************************************************************/
   DataAccessMode(String label)
   {
      this.label = label;
   }



   /*******************************************************************************
    ** Getter for label
    *******************************************************************************/
   public String getLabel()
   {
      return (label);
   }



   /*******************************************************************************
    ** Whether read tools may use the object.
    *******************************************************************************/
   public boolean allowsRead()
   {
      return (this != NONE);
   }



   /*******************************************************************************
    ** Whether write tools may use the object.
    *******************************************************************************/
   public boolean allowsWrite()
   {
      return (this == READ_WRITE);
   }



   /*******************************************************************************
    ** Parse a stored value (null or unknown: null).
    *******************************************************************************/
   public static DataAccessMode parse(String value)
   {
      if(value == null)
      {
         return (null);
      }
      for(DataAccessMode mode : values())
      {
         if(mode.name().equalsIgnoreCase(value.trim()))
         {
            return (mode);
         }
      }
      return (null);
   }
}
