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

package com.kingsrook.qbits.assistant.engine;


import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qbits.assistant.provider.ModelUsage;


/*******************************************************************************
 ** Prices of one model in USD per million tokens. A price of P USD/MTok is
 ** exactly P micro-USD per token, so costMicros = tokens * P.
 **
 ** Defaults are the Anthropic list prices of 2026-10 (spec 2.9); re-check them
 ** before budgeting. Cache reads: 0.1x input (0.05x on Opus 5.5); cache
 ** writes: 1.25x (5 minute) and 2x (1 hour).
 *******************************************************************************/
public class ModelPricing implements Serializable
{
   private Double inputUsdPerMTok;
   private Double outputUsdPerMTok;
   private Double cacheReadUsdPerMTok;
   private Double cacheWrite5mUsdPerMTok;
   private Double cacheWrite1hUsdPerMTok;



   /*******************************************************************************
    ** Price from input/output, with the standard cache multipliers.
    *******************************************************************************/
   public static ModelPricing of(double input, double output, double cacheReadMultiplier)
   {
      return (new ModelPricing()
         .withInputUsdPerMTok(input)
         .withOutputUsdPerMTok(output)
         .withCacheReadUsdPerMTok(input * cacheReadMultiplier)
         .withCacheWrite5mUsdPerMTok(input * 1.25)
         .withCacheWrite1hUsdPerMTok(input * 2.0));
   }



   /*******************************************************************************
    ** The built-in price table.
    *******************************************************************************/
   public static Map<String, ModelPricing> defaults()
   {
      Map<String, ModelPricing> map = new LinkedHashMap<>();
      map.put("claude-sonnet-5-5", of(2.0, 10.0, 0.1));
      map.put("claude-opus-5-5", of(4.0, 20.0, 0.05));
      map.put("claude-haiku-5-5", of(0.10, 0.50, 0.1));
      map.put("claude-fable-5-1", of(10.0, 50.0, 0.025));
      map.put("claude-fable-5", of(10.0, 50.0, 0.1));
      map.put("claude-opus-5", of(5.0, 25.0, 0.1));
      map.put("claude-opus-4-8", of(5.0, 25.0, 0.1));
      map.put("claude-opus-4-7", of(5.0, 25.0, 0.1));
      map.put("claude-opus-4-6", of(5.0, 25.0, 0.1));
      map.put("claude-sonnet-5", of(2.0, 10.0, 0.1));
      map.put("claude-sonnet-4-6", of(3.0, 15.0, 0.1));
      map.put("claude-haiku-4-5", of(1.0, 5.0, 0.1));
      return (map);
   }



   /*******************************************************************************
    ** Cost of a usage, in micro-USD (rounded up, never negative).
    *******************************************************************************/
   public long costMicros(ModelUsage usage)
   {
      if(usage == null)
      {
         return (0);
      }

      long write1h = nz(usage.getCacheWrite1hTokens());
      long write5m = Math.max(0, nz(usage.getCacheWriteTokens()) - write1h);

      double cost = nz(usage.getInputTokens()) * nz(inputUsdPerMTok)
         + nz(usage.getOutputTokens()) * nz(outputUsdPerMTok)
         + nz(usage.getCacheReadTokens()) * nz(cacheReadUsdPerMTok)
         + write5m * nz(cacheWrite5mUsdPerMTok)
         + write1h * nz(cacheWrite1hUsdPerMTok);
      return ((long) Math.ceil(Math.max(0, cost)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static long nz(Long value)
   {
      return (value == null ? 0 : value);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static double nz(Double value)
   {
      return (value == null ? 0 : value);
   }



   /*******************************************************************************
    ** Getter for inputUsdPerMTok
    *******************************************************************************/
   public Double getInputUsdPerMTok()
   {
      return (this.inputUsdPerMTok);
   }



   /*******************************************************************************
    ** Setter for inputUsdPerMTok
    *******************************************************************************/
   public void setInputUsdPerMTok(Double inputUsdPerMTok)
   {
      this.inputUsdPerMTok = inputUsdPerMTok;
   }



   /*******************************************************************************
    ** Fluent setter for inputUsdPerMTok
    *******************************************************************************/
   public ModelPricing withInputUsdPerMTok(Double inputUsdPerMTok)
   {
      this.inputUsdPerMTok = inputUsdPerMTok;
      return (this);
   }



   /*******************************************************************************
    ** Getter for outputUsdPerMTok
    *******************************************************************************/
   public Double getOutputUsdPerMTok()
   {
      return (this.outputUsdPerMTok);
   }



   /*******************************************************************************
    ** Setter for outputUsdPerMTok
    *******************************************************************************/
   public void setOutputUsdPerMTok(Double outputUsdPerMTok)
   {
      this.outputUsdPerMTok = outputUsdPerMTok;
   }



   /*******************************************************************************
    ** Fluent setter for outputUsdPerMTok
    *******************************************************************************/
   public ModelPricing withOutputUsdPerMTok(Double outputUsdPerMTok)
   {
      this.outputUsdPerMTok = outputUsdPerMTok;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheReadUsdPerMTok
    *******************************************************************************/
   public Double getCacheReadUsdPerMTok()
   {
      return (this.cacheReadUsdPerMTok);
   }



   /*******************************************************************************
    ** Setter for cacheReadUsdPerMTok
    *******************************************************************************/
   public void setCacheReadUsdPerMTok(Double cacheReadUsdPerMTok)
   {
      this.cacheReadUsdPerMTok = cacheReadUsdPerMTok;
   }



   /*******************************************************************************
    ** Fluent setter for cacheReadUsdPerMTok
    *******************************************************************************/
   public ModelPricing withCacheReadUsdPerMTok(Double cacheReadUsdPerMTok)
   {
      this.cacheReadUsdPerMTok = cacheReadUsdPerMTok;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheWrite5mUsdPerMTok
    *******************************************************************************/
   public Double getCacheWrite5mUsdPerMTok()
   {
      return (this.cacheWrite5mUsdPerMTok);
   }



   /*******************************************************************************
    ** Setter for cacheWrite5mUsdPerMTok
    *******************************************************************************/
   public void setCacheWrite5mUsdPerMTok(Double cacheWrite5mUsdPerMTok)
   {
      this.cacheWrite5mUsdPerMTok = cacheWrite5mUsdPerMTok;
   }



   /*******************************************************************************
    ** Fluent setter for cacheWrite5mUsdPerMTok
    *******************************************************************************/
   public ModelPricing withCacheWrite5mUsdPerMTok(Double cacheWrite5mUsdPerMTok)
   {
      this.cacheWrite5mUsdPerMTok = cacheWrite5mUsdPerMTok;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cacheWrite1hUsdPerMTok
    *******************************************************************************/
   public Double getCacheWrite1hUsdPerMTok()
   {
      return (this.cacheWrite1hUsdPerMTok);
   }



   /*******************************************************************************
    ** Setter for cacheWrite1hUsdPerMTok
    *******************************************************************************/
   public void setCacheWrite1hUsdPerMTok(Double cacheWrite1hUsdPerMTok)
   {
      this.cacheWrite1hUsdPerMTok = cacheWrite1hUsdPerMTok;
   }



   /*******************************************************************************
    ** Fluent setter for cacheWrite1hUsdPerMTok
    *******************************************************************************/
   public ModelPricing withCacheWrite1hUsdPerMTok(Double cacheWrite1hUsdPerMTok)
   {
      this.cacheWrite1hUsdPerMTok = cacheWrite1hUsdPerMTok;
      return (this);
   }

}
