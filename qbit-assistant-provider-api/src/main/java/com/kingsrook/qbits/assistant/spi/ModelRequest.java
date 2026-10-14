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


import java.util.ArrayList;
import java.util.List;


/*******************************************************************************
 ** A provider-neutral model request (spec 2.8): system blocks with cache
 ** hints, tool definitions (JSON Schema), messages, effort and max tokens.
 ** Order matters for caching: tools, then system, then messages.
 **
 ** Tool choice is always "auto" (forced tool choice is rejected by the
 ** current Claude models). parallelToolCalls false asks for one tool call
 ** per response.
 *******************************************************************************/
public class ModelRequest
{

   private ModelCapabilities capabilities;


   private Boolean                   streaming = true;
   private String                    model;
   private String                    effort;
   private Integer                   maxTokens;
   private Boolean                   parallelToolCalls = true;
   private List<SystemBlock>         system   = new ArrayList<>();
   private List<ModelToolDefinition> tools    = new ArrayList<>();
   private List<ModelMessage>        messages = new ArrayList<>();



   /*******************************************************************************
    ** A shallow copy (lists copied, elements shared) for the next loop step.
    *******************************************************************************/
   public ModelRequest copy()
   {
      return (new ModelRequest()
         .withStreaming(streaming)
         .withModel(model)
         .withCapabilities(capabilities)
         .withEffort(effort)
         .withMaxTokens(maxTokens)
         .withParallelToolCalls(parallelToolCalls)
         .withSystem(new ArrayList<>(system))
         .withTools(new ArrayList<>(tools))
         .withMessages(new ArrayList<>(messages)));
   }



   /*******************************************************************************
    ** Getter for model
    *******************************************************************************/
   public String getModel()
   {
      return (this.model);
   }



   /*******************************************************************************
    ** Setter for model
    *******************************************************************************/
   public void setModel(String model)
   {
      this.model = model;
   }



   /*******************************************************************************
    ** Fluent setter for model
    *******************************************************************************/
   public ModelRequest withModel(String model)
   {
      this.model = model;
      return (this);
   }



   /*******************************************************************************
    ** Getter for effort
    *******************************************************************************/
   public String getEffort()
   {
      return (this.effort);
   }



   /*******************************************************************************
    ** Setter for effort
    *******************************************************************************/
   public void setEffort(String effort)
   {
      this.effort = effort;
   }



   /*******************************************************************************
    ** Fluent setter for effort
    *******************************************************************************/
   public ModelRequest withEffort(String effort)
   {
      this.effort = effort;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxTokens
    *******************************************************************************/
   public Integer getMaxTokens()
   {
      return (this.maxTokens);
   }



   /*******************************************************************************
    ** Setter for maxTokens
    *******************************************************************************/
   public void setMaxTokens(Integer maxTokens)
   {
      this.maxTokens = maxTokens;
   }



   /*******************************************************************************
    ** Fluent setter for maxTokens
    *******************************************************************************/
   public ModelRequest withMaxTokens(Integer maxTokens)
   {
      this.maxTokens = maxTokens;
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
   public ModelRequest withParallelToolCalls(Boolean parallelToolCalls)
   {
      this.parallelToolCalls = parallelToolCalls;
      return (this);
   }



   /*******************************************************************************
    ** Getter for system
    *******************************************************************************/
   public List<SystemBlock> getSystem()
   {
      return (this.system);
   }



   /*******************************************************************************
    ** Setter for system
    *******************************************************************************/
   public void setSystem(List<SystemBlock> system)
   {
      this.system = system;
   }



   /*******************************************************************************
    ** Fluent setter for system
    *******************************************************************************/
   public ModelRequest withSystem(List<SystemBlock> system)
   {
      this.system = system;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tools
    *******************************************************************************/
   public List<ModelToolDefinition> getTools()
   {
      return (this.tools);
   }



   /*******************************************************************************
    ** Setter for tools
    *******************************************************************************/
   public void setTools(List<ModelToolDefinition> tools)
   {
      this.tools = tools;
   }



   /*******************************************************************************
    ** Fluent setter for tools
    *******************************************************************************/
   public ModelRequest withTools(List<ModelToolDefinition> tools)
   {
      this.tools = tools;
      return (this);
   }



   /*******************************************************************************
    ** Getter for messages
    *******************************************************************************/
   public List<ModelMessage> getMessages()
   {
      return (this.messages);
   }



   /*******************************************************************************
    ** Setter for messages
    *******************************************************************************/
   public void setMessages(List<ModelMessage> messages)
   {
      this.messages = messages;
   }



   /*******************************************************************************
    ** Fluent setter for messages
    *******************************************************************************/
   public ModelRequest withMessages(List<ModelMessage> messages)
   {
      this.messages = messages;
      return (this);
   }




   /*******************************************************************************
    ** Getter for capabilities
    *******************************************************************************/
   public ModelCapabilities getCapabilities()
   {
      return (this.capabilities);
   }



   /*******************************************************************************
    ** Setter for capabilities
    *******************************************************************************/
   public void setCapabilities(ModelCapabilities capabilities)
   {
      this.capabilities = capabilities;
   }



   /*******************************************************************************
    ** Fluent setter for capabilities
    *******************************************************************************/
   public ModelRequest withCapabilities(ModelCapabilities capabilities)
   {
      this.capabilities = capabilities;
      return (this);
   }




   /*******************************************************************************
    ** Whether to request streaming transport (classifiers use one JSON reply).
    *******************************************************************************/
   public Boolean getStreaming()
   {
      return (streaming);
   }



   /*******************************************************************************
    ** Select streaming transport independently of model capability.
    *******************************************************************************/
   public ModelRequest withStreaming(Boolean streaming)
   {
      this.streaming = streaming;
      return (this);
   }
}
