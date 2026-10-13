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


import com.kingsrook.qbits.assistant.model.AssistantModel;


/*******************************************************************************
 ** Applies registry capabilities to both the engine and the adapter's request.
 *******************************************************************************/
public class RegistryModelProvider implements AssistantModelProvider
{
   private final AssistantModelProvider delegate;
   private final AssistantModel model;



   /*******************************************************************************
    ** One resolved model's overrides.
    *******************************************************************************/
   public RegistryModelProvider(AssistantModelProvider delegate, AssistantModel model)
   {
      this.delegate = delegate;
      this.model = model;
   }



   /*******************************************************************************
    ** Adapter identity.
    *******************************************************************************/
   @Override
   public String getName()
   {
      return (delegate.getName());
   }



   /*******************************************************************************
    ** Copy defaults without mutating a cached adapter capability object.
    *******************************************************************************/
   @Override
   public ModelCapabilities getCapabilities(String modelId)
   {
      ModelCapabilities defaults = delegate.getCapabilities(modelId);
      return (new ModelCapabilities()
         .withTools(model.getSupportsTools() == null ? defaults.getTools() : model.getSupportsTools())
         .withVision(model.getSupportsVision() == null ? defaults.getVision() : model.getSupportsVision())
         .withStreaming(model.getSupportsStreaming() == null ? defaults.getStreaming() : model.getSupportsStreaming())
         .withReasoningEffort(model.getSupportsReasoningEffort() == null ? defaults.getReasoningEffort() : model.getSupportsReasoningEffort())
         .withMaxContextTokens(model.getContextWindowTokens() == null ? defaults.getMaxContextTokens() : model.getContextWindowTokens())
         .withParallelToolCalls(defaults.getParallelToolCalls()).withThinking(defaults.getThinking()).withPromptCaching(defaults.getPromptCaching()));
   }



   /*******************************************************************************
    ** Pass effective capabilities and the output ceiling to the adapter.
    *******************************************************************************/
   @Override
   public StreamHandle stream(ModelRequest request, ModelEventListener listener)
   {
      ModelRequest effective = request.copy().withCapabilities(getCapabilities(request.getModel()));
      if(model.getMaxOutputTokens() != null)
      {
         effective.setMaxTokens(request.getMaxTokens() == null ? model.getMaxOutputTokens() : Math.min(request.getMaxTokens(), model.getMaxOutputTokens()));
      }
      return (delegate.stream(effective, listener));
   }



   /*******************************************************************************
    ** Preserve optional token counting.
    *******************************************************************************/
   @Override
   public TokenCount countTokens(ModelRequest request)
   {
      return (delegate.countTokens(request));
   }
}
