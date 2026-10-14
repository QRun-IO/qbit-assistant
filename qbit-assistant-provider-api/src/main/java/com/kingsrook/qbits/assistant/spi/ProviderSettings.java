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


import java.util.function.Supplier;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.net.ssl.SSLContext;


/*******************************************************************************
 ** Resolved connection settings. API keys and bearer-token suppliers are never
 ** included in diagnostics. A supplier resolves a fresh token for each request.
 *******************************************************************************/
public class ProviderSettings
{
   @JsonIgnore
   private Supplier<String> bearerTokenSupplier;

   private String cloudProject;

   private String tlsCaBundle;
   @JsonIgnore
   private transient SSLContext sslContext;
   private String apiKeyEnvName;
   private String kind;
   private String apiKey;
   private String baseUrl;
   private String apiVersion;
   private String region;
   private Integer connectTimeoutSeconds = 10;
   private Integer requestTimeoutSeconds = 120;



   /*******************************************************************************
    ** Getter for kind
    *******************************************************************************/
   public String getKind()
   {
      return (this.kind);
   }



   /*******************************************************************************
    ** Setter for kind
    *******************************************************************************/
   public void setKind(String kind)
   {
      this.kind = kind;
   }



   /*******************************************************************************
    ** Fluent setter for kind
    *******************************************************************************/
   public ProviderSettings withKind(String kind)
   {
      this.kind = kind;
      return (this);
   }



   /*******************************************************************************
    ** Getter for apiKey
    *******************************************************************************/
   public String getApiKey()
   {
      return (this.apiKey);
   }



   /*******************************************************************************
    ** Setter for apiKey
    *******************************************************************************/
   public void setApiKey(String apiKey)
   {
      this.apiKey = apiKey;
   }



   /*******************************************************************************
    ** Fluent setter for apiKey
    *******************************************************************************/
   public ProviderSettings withApiKey(String apiKey)
   {
      this.apiKey = apiKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for baseUrl
    *******************************************************************************/
   public String getBaseUrl()
   {
      return (this.baseUrl);
   }



   /*******************************************************************************
    ** Setter for baseUrl
    *******************************************************************************/
   public void setBaseUrl(String baseUrl)
   {
      this.baseUrl = baseUrl;
   }



   /*******************************************************************************
    ** Fluent setter for baseUrl
    *******************************************************************************/
   public ProviderSettings withBaseUrl(String baseUrl)
   {
      this.baseUrl = baseUrl;
      return (this);
   }



   /*******************************************************************************
    ** Getter for apiVersion
    *******************************************************************************/
   public String getApiVersion()
   {
      return (this.apiVersion);
   }



   /*******************************************************************************
    ** Setter for apiVersion
    *******************************************************************************/
   public void setApiVersion(String apiVersion)
   {
      this.apiVersion = apiVersion;
   }



   /*******************************************************************************
    ** Fluent setter for apiVersion
    *******************************************************************************/
   public ProviderSettings withApiVersion(String apiVersion)
   {
      this.apiVersion = apiVersion;
      return (this);
   }



   /*******************************************************************************
    ** Getter for region
    *******************************************************************************/
   public String getRegion()
   {
      return (this.region);
   }



   /*******************************************************************************
    ** Setter for region
    *******************************************************************************/
   public void setRegion(String region)
   {
      this.region = region;
   }



   /*******************************************************************************
    ** Fluent setter for region
    *******************************************************************************/
   public ProviderSettings withRegion(String region)
   {
      this.region = region;
      return (this);
   }



   /*******************************************************************************
    ** Getter for connectTimeoutSeconds
    *******************************************************************************/
   public Integer getConnectTimeoutSeconds()
   {
      return (this.connectTimeoutSeconds);
   }



   /*******************************************************************************
    ** Setter for connectTimeoutSeconds
    *******************************************************************************/
   public void setConnectTimeoutSeconds(Integer connectTimeoutSeconds)
   {
      this.connectTimeoutSeconds = connectTimeoutSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for connectTimeoutSeconds
    *******************************************************************************/
   public ProviderSettings withConnectTimeoutSeconds(Integer connectTimeoutSeconds)
   {
      this.connectTimeoutSeconds = connectTimeoutSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for requestTimeoutSeconds
    *******************************************************************************/
   public Integer getRequestTimeoutSeconds()
   {
      return (this.requestTimeoutSeconds);
   }



   /*******************************************************************************
    ** Setter for requestTimeoutSeconds
    *******************************************************************************/
   public void setRequestTimeoutSeconds(Integer requestTimeoutSeconds)
   {
      this.requestTimeoutSeconds = requestTimeoutSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for requestTimeoutSeconds
    *******************************************************************************/
   public ProviderSettings withRequestTimeoutSeconds(Integer requestTimeoutSeconds)
   {
      this.requestTimeoutSeconds = requestTimeoutSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Safe diagnostic representation.
    *******************************************************************************/
   @Override
   public String toString()
   {
      return ("ProviderSettings[kind=" + kind + "]");
   }



   /*******************************************************************************
    ** Getter for tlsCaBundle
    *******************************************************************************/
   public String getTlsCaBundle()
   {
      return (this.tlsCaBundle);
   }



   /*******************************************************************************
    ** Setter for tlsCaBundle
    *******************************************************************************/
   public void setTlsCaBundle(String tlsCaBundle)
   {
      this.tlsCaBundle = tlsCaBundle;
   }



   /*******************************************************************************
    ** Fluent setter for tlsCaBundle
    *******************************************************************************/
   public ProviderSettings withTlsCaBundle(String tlsCaBundle)
   {
      this.tlsCaBundle = tlsCaBundle;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sslContext
    *******************************************************************************/
   @JsonIgnore
   public SSLContext getSslContext()
   {
      return (this.sslContext);
   }



   /*******************************************************************************
    ** Setter for sslContext
    *******************************************************************************/
   public void setSslContext(SSLContext sslContext)
   {
      this.sslContext = sslContext;
   }



   /*******************************************************************************
    ** Fluent setter for sslContext
    *******************************************************************************/
   public ProviderSettings withSslContext(SSLContext sslContext)
   {
      this.sslContext = sslContext;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cloudProject
    *******************************************************************************/
   public String getCloudProject()
   {
      return (this.cloudProject);
   }



   /*******************************************************************************
    ** Setter for cloudProject
    *******************************************************************************/
   public void setCloudProject(String cloudProject)
   {
      this.cloudProject = cloudProject;
   }



   /*******************************************************************************
    ** Fluent setter for cloudProject
    *******************************************************************************/
   public ProviderSettings withCloudProject(String cloudProject)
   {
      this.cloudProject = cloudProject;
      return (this);
   }



   /*******************************************************************************
    ** Getter for bearerTokenSupplier
    *******************************************************************************/
   @JsonIgnore
   public Supplier<String> getBearerTokenSupplier()
   {
      return (this.bearerTokenSupplier);
   }



   /*******************************************************************************
    ** Setter for bearerTokenSupplier
    *******************************************************************************/
   public void setBearerTokenSupplier(Supplier<String> bearerTokenSupplier)
   {
      this.bearerTokenSupplier = bearerTokenSupplier;
   }



   /*******************************************************************************
    ** Fluent setter for bearerTokenSupplier
    *******************************************************************************/
   public ProviderSettings withBearerTokenSupplier(Supplier<String> bearerTokenSupplier)
   {
      this.bearerTokenSupplier = bearerTokenSupplier;
      return (this);
   }



   /*******************************************************************************
    ** The configured environment variable name, never its secret value.
    *******************************************************************************/
   public String getApiKeyEnvName()
   {
      return (this.apiKeyEnvName);
   }



   /*******************************************************************************
    ** Setter for apiKeyEnvName
    *******************************************************************************/
   public void setApiKeyEnvName(String apiKeyEnvName)
   {
      this.apiKeyEnvName = apiKeyEnvName;
   }



   /*******************************************************************************
    ** Fluent setter for apiKeyEnvName
    *******************************************************************************/
   public ProviderSettings withApiKeyEnvName(String apiKeyEnvName)
   {
      this.apiKeyEnvName = apiKeyEnvName;
      return (this);
   }
}
