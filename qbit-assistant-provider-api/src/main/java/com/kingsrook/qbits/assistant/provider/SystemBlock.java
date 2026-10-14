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

package com.kingsrook.qbits.assistant.provider;


/*******************************************************************************
 ** One block of the system prompt, optionally followed by a cache breakpoint.
 *******************************************************************************/
public class SystemBlock
{
   private String    text;
   private CacheHint cacheHint = CacheHint.NONE;



   /*******************************************************************************
    **
    *******************************************************************************/
   public SystemBlock()
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public SystemBlock(String text, CacheHint cacheHint)
   {
      this.text = text;
      this.cacheHint = cacheHint;
   }



   /*******************************************************************************
    ** Getter for text
    *******************************************************************************/
   public String getText()
   {
      return (this.text);
   }



   /*******************************************************************************
    ** Setter for text
    *******************************************************************************/
   public void setText(String text)
   {
      this.text = text;
   }



   /*******************************************************************************
    ** Fluent setter for text
    *******************************************************************************/
   public SystemBlock withText(String text)
   {
      this.text = text;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheHint
    *******************************************************************************/
   public CacheHint getCacheHint()
   {
      return (this.cacheHint);
   }



   /*******************************************************************************
    ** Setter for cacheHint
    *******************************************************************************/
   public void setCacheHint(CacheHint cacheHint)
   {
      this.cacheHint = cacheHint;
   }



   /*******************************************************************************
    ** Fluent setter for cacheHint
    *******************************************************************************/
   public SystemBlock withCacheHint(CacheHint cacheHint)
   {
      this.cacheHint = cacheHint;
      return (this);
   }

}
