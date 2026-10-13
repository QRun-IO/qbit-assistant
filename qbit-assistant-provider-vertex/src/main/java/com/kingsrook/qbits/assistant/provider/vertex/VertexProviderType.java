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

package com.kingsrook.qbits.assistant.provider.vertex;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qbits.assistant.provider.DiscoveredModel;
import com.kingsrook.qbits.assistant.provider.ProviderSettings;


/*******************************************************************************
 ** Google Vertex AI (Claude, Gemini), using server credentials and region-derived endpoints.
 *******************************************************************************/
public class VertexProviderType implements AssistantProviderType
{



   /*******************************************************************************
    ** Provider registry contract.
    *******************************************************************************/
   @Override
   public String getType()
   {
      return ("vertex");
   }



   /*******************************************************************************
    ** Provider registry contract.
    *******************************************************************************/
   @Override
   public String getLabel()
   {
      return ("Google Vertex AI (Claude, Gemini)");
   }



   /*******************************************************************************
    ** Provider registry contract.
    *******************************************************************************/
   @Override
   public List<String> getModels()
   {
      return (List.of("claude-sonnet-4-5@20250929", "claude-haiku-4-5@20251001", "gemini-2.5-pro", "gemini-2.5-flash"));
   }



   /*******************************************************************************
    ** Provider registry contract.
    *******************************************************************************/
   @Override
   public String getDefaultApiKeyEnvName()
   {
      return (null);
   }



   /*******************************************************************************
    ** Provider registry contract.
    *******************************************************************************/
   @Override
   public String getDefaultBaseUrl()
   {
      return (null);
   }



   /*******************************************************************************
    ** Provider registry contract.
    *******************************************************************************/
   @Override
   public boolean requiresBaseUrl(String kind)
   {
      return (false);
   }



   /*******************************************************************************
    ** Validate cloud settings without loading credentials.
    *******************************************************************************/
   @Override
   public List<String> validate(AssistantProvider row)
   {
      List<String> errors = new ArrayList<>();
      if(row.getRegion() == null || row.getRegion().isBlank())
      {
         errors.add("Region is required for VERTEX.");
      }
      if(row.getApiKeyEnvName() != null && !row.getApiKeyEnvName().isBlank())
      {
         errors.add("Leave API key variable empty: Google Vertex AI (Claude, Gemini) uses Google Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS or workload identity).");
      }
      if(row.getCloudProject() == null || row.getCloudProject().isBlank())
      {
         errors.add("GCP project id (cloudProject) is required for VERTEX.");
      }
      return (errors);
   }



   /*******************************************************************************
    ** Cloud providers require resolved region/project settings.
    *******************************************************************************/
   @Override
   public AssistantModelProvider create(String apiKey, String baseUrl)
   {
      throw (new IllegalArgumentException("Use ProviderSettings with the required cloud region and project."));
   }



   /*******************************************************************************
    ** Static defaults; this adapter does not query the Vertex Model Garden.
    *******************************************************************************/
   @Override
   public List<DiscoveredModel> discoverModels(ProviderSettings settings)
   {
      return (getModels().stream().map(id -> new DiscoveredModel(id, id, null, null)).toList());
   }



   /*******************************************************************************
    ** Create the provider with server credentials.
    *******************************************************************************/
   @Override
   public AssistantModelProvider create(ProviderSettings settings)
   {
      return (new VertexModelProvider(settings));
   }
}
