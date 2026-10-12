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


import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonIgnore;


/*******************************************************************************
 ** The complete result of one model call: visible content parts (text and
 ** tool_use; never thinking), the stop reason, usage and the provider's
 ** request id. Stop reasons use the Anthropic vocabulary: end_turn,
 ** tool_use, max_tokens, refusal, stop_sequence, pause_turn,
 ** model_context_window_exceeded, plus "cancelled" for a cancelled stream.
 *******************************************************************************/
public class ModelResponse
{
   public static final String STOP_END_TURN  = "end_turn";
   public static final String STOP_TOOL_USE  = "tool_use";
   public static final String STOP_MAX_TOKENS = "max_tokens";
   public static final String STOP_REFUSAL   = "refusal";
   public static final String STOP_CANCELLED = "cancelled";
   public static final String STOP_CONTEXT   = "model_context_window_exceeded";

   private List<ModelPart> parts = new ArrayList<>();
   private String          stopReason;
   private ModelUsage      usage = new ModelUsage();
   private String          requestId;

   @JsonIgnore
   private transient Object providerState;



   /*******************************************************************************
    ** The tool_use parts, in order.
    *******************************************************************************/
   public List<ModelPart> getToolUses()
   {
      return (parts.stream().filter(p -> ModelPart.Type.TOOL_USE.equals(p.getType())).collect(Collectors.toList()));
   }



   /*******************************************************************************
    ** All text parts joined.
    *******************************************************************************/
   public String getText()
   {
      return (parts.stream().filter(p -> ModelPart.Type.TEXT.equals(p.getType())).map(ModelPart::getText).collect(Collectors.joining()));
   }



   /*******************************************************************************
    ** Getter for providerState (see ModelMessage)
    *******************************************************************************/
   @JsonIgnore
   public Object getProviderState()
   {
      return (this.providerState);
   }



   /*******************************************************************************
    ** Fluent setter for providerState
    *******************************************************************************/
   public ModelResponse withProviderState(Object providerState)
   {
      this.providerState = providerState;
      return (this);
   }



   /*******************************************************************************
    ** Getter for parts
    *******************************************************************************/
   public List<ModelPart> getParts()
   {
      return (this.parts);
   }



   /*******************************************************************************
    ** Setter for parts
    *******************************************************************************/
   public void setParts(List<ModelPart> parts)
   {
      this.parts = parts;
   }



   /*******************************************************************************
    ** Fluent setter for parts
    *******************************************************************************/
   public ModelResponse withParts(List<ModelPart> parts)
   {
      this.parts = parts;
      return (this);
   }



   /*******************************************************************************
    ** Getter for stopReason
    *******************************************************************************/
   public String getStopReason()
   {
      return (this.stopReason);
   }



   /*******************************************************************************
    ** Setter for stopReason
    *******************************************************************************/
   public void setStopReason(String stopReason)
   {
      this.stopReason = stopReason;
   }



   /*******************************************************************************
    ** Fluent setter for stopReason
    *******************************************************************************/
   public ModelResponse withStopReason(String stopReason)
   {
      this.stopReason = stopReason;
      return (this);
   }



   /*******************************************************************************
    ** Getter for usage
    *******************************************************************************/
   public ModelUsage getUsage()
   {
      return (this.usage);
   }



   /*******************************************************************************
    ** Setter for usage
    *******************************************************************************/
   public void setUsage(ModelUsage usage)
   {
      this.usage = usage;
   }



   /*******************************************************************************
    ** Fluent setter for usage
    *******************************************************************************/
   public ModelResponse withUsage(ModelUsage usage)
   {
      this.usage = usage;
      return (this);
   }



   /*******************************************************************************
    ** Getter for requestId
    *******************************************************************************/
   public String getRequestId()
   {
      return (this.requestId);
   }



   /*******************************************************************************
    ** Setter for requestId
    *******************************************************************************/
   public void setRequestId(String requestId)
   {
      this.requestId = requestId;
   }



   /*******************************************************************************
    ** Fluent setter for requestId
    *******************************************************************************/
   public ModelResponse withRequestId(String requestId)
   {
      this.requestId = requestId;
      return (this);
   }

}
