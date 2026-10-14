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

package com.kingsrook.qbits.assistant.spi;


/*******************************************************************************
 ** What a model supports. The turn engine turns features off that a model
 ** lacks (e.g. runs tools one at a time without parallelToolCalls).
 *******************************************************************************/
public class ModelCapabilities
{

   private Boolean vision = false;


   private Boolean streaming = true;


   private Boolean reasoningEffort = false;


   private Boolean tools             = true;
   private Boolean parallelToolCalls = true;
   private Boolean promptCaching     = false;
   private Boolean thinking          = false;
   private Integer maxContextTokens  = 200_000;

   /*******************************************************************************
    ** Getter for tools
    *******************************************************************************/
   public Boolean getTools()
   {
      return (this.tools);
   }



   /*******************************************************************************
    ** Setter for tools
    *******************************************************************************/
   public void setTools(Boolean tools)
   {
      this.tools = tools;
   }



   /*******************************************************************************
    ** Fluent setter for tools
    *******************************************************************************/
   public ModelCapabilities withTools(Boolean tools)
   {
      this.tools = tools;
      return (this);
   }



   /*******************************************************************************
    ** Getter for parallelToolCalls
    *******************************************************************************/
   public Boolean getParallelToolCalls()
   {
      return (this.parallelToolCalls);
   }



   /*******************************************************************************
    ** Setter for parallelToolCalls
    *******************************************************************************/
   public void setParallelToolCalls(Boolean parallelToolCalls)
   {
      this.parallelToolCalls = parallelToolCalls;
   }



   /*******************************************************************************
    ** Fluent setter for parallelToolCalls
    *******************************************************************************/
   public ModelCapabilities withParallelToolCalls(Boolean parallelToolCalls)
   {
      this.parallelToolCalls = parallelToolCalls;
      return (this);
   }



   /*******************************************************************************
    ** Getter for promptCaching
    *******************************************************************************/
   public Boolean getPromptCaching()
   {
      return (this.promptCaching);
   }



   /*******************************************************************************
    ** Setter for promptCaching
    *******************************************************************************/
   public void setPromptCaching(Boolean promptCaching)
   {
      this.promptCaching = promptCaching;
   }



   /*******************************************************************************
    ** Fluent setter for promptCaching
    *******************************************************************************/
   public ModelCapabilities withPromptCaching(Boolean promptCaching)
   {
      this.promptCaching = promptCaching;
      return (this);
   }



   /*******************************************************************************
    ** Getter for thinking
    *******************************************************************************/
   public Boolean getThinking()
   {
      return (this.thinking);
   }



   /*******************************************************************************
    ** Setter for thinking
    *******************************************************************************/
   public void setThinking(Boolean thinking)
   {
      this.thinking = thinking;
   }



   /*******************************************************************************
    ** Fluent setter for thinking
    *******************************************************************************/
   public ModelCapabilities withThinking(Boolean thinking)
   {
      this.thinking = thinking;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxContextTokens
    *******************************************************************************/
   public Integer getMaxContextTokens()
   {
      return (this.maxContextTokens);
   }



   /*******************************************************************************
    ** Setter for maxContextTokens
    *******************************************************************************/
   public void setMaxContextTokens(Integer maxContextTokens)
   {
      this.maxContextTokens = maxContextTokens;
   }



   /*******************************************************************************
    ** Fluent setter for maxContextTokens
    *******************************************************************************/
   public ModelCapabilities withMaxContextTokens(Integer maxContextTokens)
   {
      this.maxContextTokens = maxContextTokens;
      return (this);
   }




   /*******************************************************************************
    ** Getter for vision
    *******************************************************************************/
   public Boolean getVision()
   {
      return (this.vision);
   }



   /*******************************************************************************
    ** Setter for vision
    *******************************************************************************/
   public void setVision(Boolean vision)
   {
      this.vision = vision;
   }



   /*******************************************************************************
    ** Fluent setter for vision
    *******************************************************************************/
   public ModelCapabilities withVision(Boolean vision)
   {
      this.vision = vision;
      return (this);
   }



   /*******************************************************************************
    ** Getter for streaming
    *******************************************************************************/
   public Boolean getStreaming()
   {
      return (this.streaming);
   }



   /*******************************************************************************
    ** Setter for streaming
    *******************************************************************************/
   public void setStreaming(Boolean streaming)
   {
      this.streaming = streaming;
   }



   /*******************************************************************************
    ** Fluent setter for streaming
    *******************************************************************************/
   public ModelCapabilities withStreaming(Boolean streaming)
   {
      this.streaming = streaming;
      return (this);
   }



   /*******************************************************************************
    ** Getter for reasoningEffort
    *******************************************************************************/
   public Boolean getReasoningEffort()
   {
      return (this.reasoningEffort);
   }



   /*******************************************************************************
    ** Setter for reasoningEffort
    *******************************************************************************/
   public void setReasoningEffort(Boolean reasoningEffort)
   {
      this.reasoningEffort = reasoningEffort;
   }



   /*******************************************************************************
    ** Fluent setter for reasoningEffort
    *******************************************************************************/
   public ModelCapabilities withReasoningEffort(Boolean reasoningEffort)
   {
      this.reasoningEffort = reasoningEffort;
      return (this);
   }

}
