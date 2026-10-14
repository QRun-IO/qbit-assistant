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
import com.fasterxml.jackson.annotation.JsonIgnore;


/*******************************************************************************
 ** One message of the conversation sent to the model.
 **
 ** providerState carries a provider's own representation of an assistant
 ** message it produced (e.g. Anthropic's content blocks with thinking
 ** signatures), so the same provider can send it back verbatim inside a tool
 ** loop. Other providers ignore it; it is never persisted or serialized.
 *******************************************************************************/
public class ModelMessage
{
   /*******************************************************************************
    ** Who said it.
    *******************************************************************************/
   public enum Role
   {
      USER,
      ASSISTANT
   }

   private Role            role;
   private List<ModelPart> parts     = new ArrayList<>();
   private CacheHint       cacheHint = CacheHint.NONE;

   @JsonIgnore
   private transient Object providerState;



   /*******************************************************************************
    ** A user message with one text part.
    *******************************************************************************/
   public static ModelMessage user(String text)
   {
      return (new ModelMessage().withRole(Role.USER).withPart(ModelPart.text(text)));
   }



   /*******************************************************************************
    ** An assistant message with one text part.
    *******************************************************************************/
   public static ModelMessage assistant(String text)
   {
      return (new ModelMessage().withRole(Role.ASSISTANT).withPart(ModelPart.text(text)));
   }



   /*******************************************************************************
    ** Add a part.
    *******************************************************************************/
   public ModelMessage withPart(ModelPart part)
   {
      parts.add(part);
      return (this);
   }



   /*******************************************************************************
    ** Getter for providerState
    *******************************************************************************/
   @JsonIgnore
   public Object getProviderState()
   {
      return (this.providerState);
   }



   /*******************************************************************************
    ** Fluent setter for providerState
    *******************************************************************************/
   public ModelMessage withProviderState(Object providerState)
   {
      this.providerState = providerState;
      return (this);
   }



   /*******************************************************************************
    ** Getter for role
    *******************************************************************************/
   public Role getRole()
   {
      return (this.role);
   }



   /*******************************************************************************
    ** Setter for role
    *******************************************************************************/
   public void setRole(Role role)
   {
      this.role = role;
   }



   /*******************************************************************************
    ** Fluent setter for role
    *******************************************************************************/
   public ModelMessage withRole(Role role)
   {
      this.role = role;
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
   public ModelMessage withParts(List<ModelPart> parts)
   {
      this.parts = parts;
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
   public ModelMessage withCacheHint(CacheHint cacheHint)
   {
      this.cacheHint = cacheHint;
      return (this);
   }

}
