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


import java.util.Map;


/*******************************************************************************
 ** A tool offered to the model: name, description and a JSON Schema for its
 ** input. strict asks the provider to guarantee schema-valid input.
 *******************************************************************************/
public class ModelToolDefinition
{
   private String              name;
   private String              description;
   private Map<String, Object> inputSchema;
   private Boolean             strict = true;

   /*******************************************************************************
    ** Getter for name
    *******************************************************************************/
   public String getName()
   {
      return (this.name);
   }



   /*******************************************************************************
    ** Setter for name
    *******************************************************************************/
   public void setName(String name)
   {
      this.name = name;
   }



   /*******************************************************************************
    ** Fluent setter for name
    *******************************************************************************/
   public ModelToolDefinition withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for description
    *******************************************************************************/
   public String getDescription()
   {
      return (this.description);
   }



   /*******************************************************************************
    ** Setter for description
    *******************************************************************************/
   public void setDescription(String description)
   {
      this.description = description;
   }



   /*******************************************************************************
    ** Fluent setter for description
    *******************************************************************************/
   public ModelToolDefinition withDescription(String description)
   {
      this.description = description;
      return (this);
   }



   /*******************************************************************************
    ** Getter for inputSchema
    *******************************************************************************/
   public Map<String, Object> getInputSchema()
   {
      return (this.inputSchema);
   }



   /*******************************************************************************
    ** Setter for inputSchema
    *******************************************************************************/
   public void setInputSchema(Map<String, Object> inputSchema)
   {
      this.inputSchema = inputSchema;
   }



   /*******************************************************************************
    ** Fluent setter for inputSchema
    *******************************************************************************/
   public ModelToolDefinition withInputSchema(Map<String, Object> inputSchema)
   {
      this.inputSchema = inputSchema;
      return (this);
   }



   /*******************************************************************************
    ** Getter for strict
    *******************************************************************************/
   public Boolean getStrict()
   {
      return (this.strict);
   }



   /*******************************************************************************
    ** Setter for strict
    *******************************************************************************/
   public void setStrict(Boolean strict)
   {
      this.strict = strict;
   }



   /*******************************************************************************
    ** Fluent setter for strict
    *******************************************************************************/
   public ModelToolDefinition withStrict(Boolean strict)
   {
      this.strict = strict;
      return (this);
   }

}
