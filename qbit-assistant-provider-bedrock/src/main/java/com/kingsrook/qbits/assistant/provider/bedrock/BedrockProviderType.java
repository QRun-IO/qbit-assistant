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

package com.kingsrook.qbits.assistant.provider.bedrock;


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import com.anthropic.backends.Backend;
import com.anthropic.bedrock.backends.BedrockBackend;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.Timeout;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qbits.assistant.provider.DiscoveredModel;
import com.kingsrook.qbits.assistant.provider.ProviderSettings;
import com.kingsrook.qbits.assistant.provider.anthropic.AnthropicModelProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;


/*******************************************************************************
 ** AWS Bedrock (Claude), using server credentials and region-derived endpoints.
 ** Environment, profile and container (ECS/EKS Pod Identity) credentials use
 ** the transitive SDK dependencies. Hosts using IRSA / web identity must add
 ** software.amazon.awssdk:sts to their runtime classpath; the default credential
 ** chain discovers it when present. This adapter does not depend on STS.
 *******************************************************************************/
public class BedrockProviderType implements AssistantProviderType
{



   /*******************************************************************************
    ** Provider registry contract.
    *******************************************************************************/
   @Override
   public String getType()
   {
      return ("bedrock");
   }



   /*******************************************************************************
    ** Provider registry contract.
    *******************************************************************************/
   @Override
   public String getLabel()
   {
      return ("AWS Bedrock (Claude)");
   }



   /*******************************************************************************
    ** Provider registry contract.
    *******************************************************************************/
   @Override
   public List<String> getModels()
   {
      return (List.of("us.anthropic.claude-sonnet-4-5-20250929-v1:0", "us.anthropic.claude-haiku-4-5-20251001-v1:0"));
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
   public List<String> validate(ProviderSettings settings)
   {
      List<String> errors = new ArrayList<>();
      if(settings.getRegion() == null || settings.getRegion().isBlank())
      {
         errors.add("Region is required for BEDROCK.");
      }
      if(settings.getApiKeyEnvName() != null && !settings.getApiKeyEnvName().isBlank())
      {
         errors.add("Leave API key variable empty: AWS Bedrock (Claude) uses the AWS default credential chain (environment, profile or server role).");
      }
      return (errors);
   }



   /*******************************************************************************
    ** Cloud providers require resolved region/project settings.
    *******************************************************************************/
   @Override
   public AssistantModelProvider create(String apiKey, String baseUrl)
   {
      throw (new IllegalArgumentException("Use ProviderSettings with the required cloud region."));
   }



   /*******************************************************************************
    ** Static defaults: ListFoundationModels requires the Bedrock control-plane SDK,
    ** which this adapter deliberately does not add.
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
      return (create(settings, DefaultCredentialsProvider.builder().build()));
   }



   /*******************************************************************************
    ** Credential injection for contract tests; no credential values are stored.
    *******************************************************************************/
   AnthropicModelProvider create(ProviderSettings settings, AwsCredentialsProvider credentials)
   {
      Backend backend = BedrockBackend.builder().region(Region.of(settings.getRegion())).awsCredentialsProvider(credentials).build();
      if(settings.getBaseUrl() != null)
      {
         backend = new BedrockEndpointBackend(backend, settings.getBaseUrl());
      }
      var client = AnthropicOkHttpClient.builder().backend(backend)
         .timeout(Timeout.builder().connect(Duration.ofSeconds(settings.getConnectTimeoutSeconds()))
            .request(Duration.ofSeconds(settings.getRequestTimeoutSeconds())).build());
      return (AnthropicModelProvider.builder().client(client.build()).build());
   }
}
