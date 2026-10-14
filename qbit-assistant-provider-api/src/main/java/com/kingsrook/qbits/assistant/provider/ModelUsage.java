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
 ** Token usage of one or more model calls. inputTokens is the uncached
 ** remainder; cacheWriteTokens includes cacheWrite1hTokens.
 *******************************************************************************/
public class ModelUsage
{
   private Long inputTokens        = 0L;
   private Long outputTokens       = 0L;
   private Long cacheReadTokens    = 0L;
   private Long cacheWriteTokens   = 0L;
   private Long cacheWrite1hTokens = 0L;



   /*******************************************************************************
    ** Add another usage into this one.
    *******************************************************************************/
   public ModelUsage add(ModelUsage other)
   {
      if(other != null)
      {
         inputTokens = nz(inputTokens) + nz(other.inputTokens);
         outputTokens = nz(outputTokens) + nz(other.outputTokens);
         cacheReadTokens = nz(cacheReadTokens) + nz(other.cacheReadTokens);
         cacheWriteTokens = nz(cacheWriteTokens) + nz(other.cacheWriteTokens);
         cacheWrite1hTokens = nz(cacheWrite1hTokens) + nz(other.cacheWrite1hTokens);
      }
      return (this);
   }



   /*******************************************************************************
    ** Billable tokens for budgets: uncached input + cache writes + output.
    *******************************************************************************/
   public long billableTokens()
   {
      return (nz(inputTokens) + nz(cacheWriteTokens) + nz(outputTokens));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static long nz(Long value)
   {
      return (value == null ? 0 : value);
   }



   /*******************************************************************************
    ** Getter for inputTokens
    *******************************************************************************/
   public Long getInputTokens()
   {
      return (this.inputTokens);
   }



   /*******************************************************************************
    ** Setter for inputTokens
    *******************************************************************************/
   public void setInputTokens(Long inputTokens)
   {
      this.inputTokens = inputTokens;
   }



   /*******************************************************************************
    ** Fluent setter for inputTokens
    *******************************************************************************/
   public ModelUsage withInputTokens(Long inputTokens)
   {
      this.inputTokens = inputTokens;
      return (this);
   }



   /*******************************************************************************
    ** Getter for outputTokens
    *******************************************************************************/
   public Long getOutputTokens()
   {
      return (this.outputTokens);
   }



   /*******************************************************************************
    ** Setter for outputTokens
    *******************************************************************************/
   public void setOutputTokens(Long outputTokens)
   {
      this.outputTokens = outputTokens;
   }



   /*******************************************************************************
    ** Fluent setter for outputTokens
    *******************************************************************************/
   public ModelUsage withOutputTokens(Long outputTokens)
   {
      this.outputTokens = outputTokens;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheReadTokens
    *******************************************************************************/
   public Long getCacheReadTokens()
   {
      return (this.cacheReadTokens);
   }



   /*******************************************************************************
    ** Setter for cacheReadTokens
    *******************************************************************************/
   public void setCacheReadTokens(Long cacheReadTokens)
   {
      this.cacheReadTokens = cacheReadTokens;
   }



   /*******************************************************************************
    ** Fluent setter for cacheReadTokens
    *******************************************************************************/
   public ModelUsage withCacheReadTokens(Long cacheReadTokens)
   {
      this.cacheReadTokens = cacheReadTokens;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheWriteTokens
    *******************************************************************************/
   public Long getCacheWriteTokens()
   {
      return (this.cacheWriteTokens);
   }



   /*******************************************************************************
    ** Setter for cacheWriteTokens
    *******************************************************************************/
   public void setCacheWriteTokens(Long cacheWriteTokens)
   {
      this.cacheWriteTokens = cacheWriteTokens;
   }



   /*******************************************************************************
    ** Fluent setter for cacheWriteTokens
    *******************************************************************************/
   public ModelUsage withCacheWriteTokens(Long cacheWriteTokens)
   {
      this.cacheWriteTokens = cacheWriteTokens;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheWrite1hTokens
    *******************************************************************************/
   public Long getCacheWrite1hTokens()
   {
      return (this.cacheWrite1hTokens);
   }



   /*******************************************************************************
    ** Setter for cacheWrite1hTokens
    *******************************************************************************/
   public void setCacheWrite1hTokens(Long cacheWrite1hTokens)
   {
      this.cacheWrite1hTokens = cacheWrite1hTokens;
   }



   /*******************************************************************************
    ** Fluent setter for cacheWrite1hTokens
    *******************************************************************************/
   public ModelUsage withCacheWrite1hTokens(Long cacheWrite1hTokens)
   {
      this.cacheWrite1hTokens = cacheWrite1hTokens;
      return (this);
   }

}
