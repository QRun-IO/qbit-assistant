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

package com.kingsrook.qbits.assistant.model;


import java.math.BigDecimal;
import java.time.Instant;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;


/*******************************************************************************
 ** A model provider the assistant can use: its type, model, connection,
 ** limits and the result of its last connection test (the provider settings
 ** screen). The API key itself is never stored: apiKeyEnvName names the
 ** environment variable (or host secret) that holds it, resolved at runtime.
 ** The read-only apiKeyStatus field (filled on read, never stored) shows whether it is set and its
 ** last four characters.
 *******************************************************************************/
public class AssistantProvider extends QRecordEntity
{
   @QField(maxLength = 100)
   private String cloudProject;

   @QField(maxLength = 30)
   private String kind;

   @QField(maxLength = 10)
   private String location;

   @QField()
   private Boolean hipaaEligible;

   @QField(maxLength = 50)
   private String region;

   @QField(maxLength = 30)
   private String apiVersion;

   @QField()
   private Integer connectTimeoutSeconds;

   @QField()
   private Integer requestTimeoutSeconds;

   @QField(isEditable = false)
   private Instant lastDiscoveryAt;

   @QField(isEditable = false, maxLength = 16)
   private String lastDiscoveryStatus;

   @QField(isEditable = false, maxLength = 500)
   private String lastDiscoveryError;


   public static final String TABLE_NAME = "assistant_provider";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 100)
   private String name;

   @QField(isRequired = true, maxLength = 50)
   private String providerType;

   @QField(maxLength = 200)
   private String model;

   @QField()
   private Boolean enabled;

   @QField()
   private Boolean isDefault;

   @QField(maxLength = 255)
   private String baseUrl;

   @QField(maxLength = 100)
   private String tlsCaBundle;

   @QField(maxLength = 100)
   private String apiKeyEnvName;

   /*******************************************************************************
    ** Computed when a row is read (whether the key is set, and its last four
    ** characters); never written - saves drop it.
    *******************************************************************************/
   @QField(isEditable = false, maxLength = 200)
   private String apiKeyStatus;

   @QField()
   private BigDecimal monthlyCostCapUsd;

   @QField()
   private Integer maxOutputTokensPerRequest;

   @QField()
   private Integer maxConcurrentRequests;

   @QField(isEditable = false)
   private Instant lastTestAt;

   @QField(isEditable = false, maxLength = 16)
   private String lastTestStatus;

   @QField(isEditable = false)
   private Integer lastTestLatencyMs;

   @QField(isEditable = false, maxLength = 200)
   private String lastTestModel;

   @QField(isEditable = false, maxLength = 500)
   private String lastTestError;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantProvider()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantProvider(QRecord record) throws QException
   {
      populateFromQRecord(record);
   }



   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (this.id);
   }



   /*******************************************************************************
    ** Setter for id
    *******************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /*******************************************************************************
    ** Fluent setter for id
    *******************************************************************************/
   public AssistantProvider withId(Integer id)
   {
      this.id = id;
      return (this);
   }



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
   public AssistantProvider withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for providerType
    *******************************************************************************/
   public String getProviderType()
   {
      return (this.providerType);
   }



   /*******************************************************************************
    ** Setter for providerType
    *******************************************************************************/
   public void setProviderType(String providerType)
   {
      this.providerType = providerType;
   }



   /*******************************************************************************
    ** Fluent setter for providerType
    *******************************************************************************/
   public AssistantProvider withProviderType(String providerType)
   {
      this.providerType = providerType;
      return (this);
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
   public AssistantProvider withModel(String model)
   {
      this.model = model;
      return (this);
   }



   /*******************************************************************************
    ** Getter for enabled
    *******************************************************************************/
   public Boolean getEnabled()
   {
      return (this.enabled);
   }



   /*******************************************************************************
    ** Setter for enabled
    *******************************************************************************/
   public void setEnabled(Boolean enabled)
   {
      this.enabled = enabled;
   }



   /*******************************************************************************
    ** Fluent setter for enabled
    *******************************************************************************/
   public AssistantProvider withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for isDefault
    *******************************************************************************/
   public Boolean getIsDefault()
   {
      return (this.isDefault);
   }



   /*******************************************************************************
    ** Setter for isDefault
    *******************************************************************************/
   public void setIsDefault(Boolean isDefault)
   {
      this.isDefault = isDefault;
   }



   /*******************************************************************************
    ** Fluent setter for isDefault
    *******************************************************************************/
   public AssistantProvider withIsDefault(Boolean isDefault)
   {
      this.isDefault = isDefault;
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
   public AssistantProvider withBaseUrl(String baseUrl)
   {
      this.baseUrl = baseUrl;
      return (this);
   }



   /*******************************************************************************
    ** Getter for apiKeyEnvName
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
   public AssistantProvider withApiKeyEnvName(String apiKeyEnvName)
   {
      this.apiKeyEnvName = apiKeyEnvName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for monthlyCostCapUsd
    *******************************************************************************/
   public BigDecimal getMonthlyCostCapUsd()
   {
      return (this.monthlyCostCapUsd);
   }



   /*******************************************************************************
    ** Setter for monthlyCostCapUsd
    *******************************************************************************/
   public void setMonthlyCostCapUsd(BigDecimal monthlyCostCapUsd)
   {
      this.monthlyCostCapUsd = monthlyCostCapUsd;
   }



   /*******************************************************************************
    ** Fluent setter for monthlyCostCapUsd
    *******************************************************************************/
   public AssistantProvider withMonthlyCostCapUsd(BigDecimal monthlyCostCapUsd)
   {
      this.monthlyCostCapUsd = monthlyCostCapUsd;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxOutputTokensPerRequest
    *******************************************************************************/
   public Integer getMaxOutputTokensPerRequest()
   {
      return (this.maxOutputTokensPerRequest);
   }



   /*******************************************************************************
    ** Setter for maxOutputTokensPerRequest
    *******************************************************************************/
   public void setMaxOutputTokensPerRequest(Integer maxOutputTokensPerRequest)
   {
      this.maxOutputTokensPerRequest = maxOutputTokensPerRequest;
   }



   /*******************************************************************************
    ** Fluent setter for maxOutputTokensPerRequest
    *******************************************************************************/
   public AssistantProvider withMaxOutputTokensPerRequest(Integer maxOutputTokensPerRequest)
   {
      this.maxOutputTokensPerRequest = maxOutputTokensPerRequest;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxConcurrentRequests
    *******************************************************************************/
   public Integer getMaxConcurrentRequests()
   {
      return (this.maxConcurrentRequests);
   }



   /*******************************************************************************
    ** Setter for maxConcurrentRequests
    *******************************************************************************/
   public void setMaxConcurrentRequests(Integer maxConcurrentRequests)
   {
      this.maxConcurrentRequests = maxConcurrentRequests;
   }



   /*******************************************************************************
    ** Fluent setter for maxConcurrentRequests
    *******************************************************************************/
   public AssistantProvider withMaxConcurrentRequests(Integer maxConcurrentRequests)
   {
      this.maxConcurrentRequests = maxConcurrentRequests;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastTestAt
    *******************************************************************************/
   public Instant getLastTestAt()
   {
      return (this.lastTestAt);
   }



   /*******************************************************************************
    ** Setter for lastTestAt
    *******************************************************************************/
   public void setLastTestAt(Instant lastTestAt)
   {
      this.lastTestAt = lastTestAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastTestAt
    *******************************************************************************/
   public AssistantProvider withLastTestAt(Instant lastTestAt)
   {
      this.lastTestAt = lastTestAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastTestStatus
    *******************************************************************************/
   public String getLastTestStatus()
   {
      return (this.lastTestStatus);
   }



   /*******************************************************************************
    ** Setter for lastTestStatus
    *******************************************************************************/
   public void setLastTestStatus(String lastTestStatus)
   {
      this.lastTestStatus = lastTestStatus;
   }



   /*******************************************************************************
    ** Fluent setter for lastTestStatus
    *******************************************************************************/
   public AssistantProvider withLastTestStatus(String lastTestStatus)
   {
      this.lastTestStatus = lastTestStatus;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastTestLatencyMs
    *******************************************************************************/
   public Integer getLastTestLatencyMs()
   {
      return (this.lastTestLatencyMs);
   }



   /*******************************************************************************
    ** Setter for lastTestLatencyMs
    *******************************************************************************/
   public void setLastTestLatencyMs(Integer lastTestLatencyMs)
   {
      this.lastTestLatencyMs = lastTestLatencyMs;
   }



   /*******************************************************************************
    ** Fluent setter for lastTestLatencyMs
    *******************************************************************************/
   public AssistantProvider withLastTestLatencyMs(Integer lastTestLatencyMs)
   {
      this.lastTestLatencyMs = lastTestLatencyMs;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastTestModel
    *******************************************************************************/
   public String getLastTestModel()
   {
      return (this.lastTestModel);
   }



   /*******************************************************************************
    ** Setter for lastTestModel
    *******************************************************************************/
   public void setLastTestModel(String lastTestModel)
   {
      this.lastTestModel = lastTestModel;
   }



   /*******************************************************************************
    ** Fluent setter for lastTestModel
    *******************************************************************************/
   public AssistantProvider withLastTestModel(String lastTestModel)
   {
      this.lastTestModel = lastTestModel;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastTestError
    *******************************************************************************/
   public String getLastTestError()
   {
      return (this.lastTestError);
   }



   /*******************************************************************************
    ** Setter for lastTestError
    *******************************************************************************/
   public void setLastTestError(String lastTestError)
   {
      this.lastTestError = lastTestError;
   }



   /*******************************************************************************
    ** Fluent setter for lastTestError
    *******************************************************************************/
   public AssistantProvider withLastTestError(String lastTestError)
   {
      this.lastTestError = lastTestError;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (this.createDate);
   }



   /*******************************************************************************
    ** Setter for createDate
    *******************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /*******************************************************************************
    ** Fluent setter for createDate
    *******************************************************************************/
   public AssistantProvider withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (this.modifyDate);
   }



   /*******************************************************************************
    ** Setter for modifyDate
    *******************************************************************************/
   public void setModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
   }



   /*******************************************************************************
    ** Fluent setter for modifyDate
    *******************************************************************************/
   public AssistantProvider withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for apiKeyStatus (masked key status, computed on read)
    *******************************************************************************/
   public String getApiKeyStatus()
   {
      return (this.apiKeyStatus);
   }



   /*******************************************************************************
    ** Setter for apiKeyStatus
    *******************************************************************************/
   public void setApiKeyStatus(String apiKeyStatus)
   {
      this.apiKeyStatus = apiKeyStatus;
   }



   /*******************************************************************************
    ** Fluent setter for apiKeyStatus
    *******************************************************************************/
   public AssistantProvider withApiKeyStatus(String apiKeyStatus)
   {
      this.apiKeyStatus = apiKeyStatus;
      return (this);
   }




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
   public AssistantProvider withKind(String kind)
   {
      this.kind = kind;
      return (this);
   }



   /*******************************************************************************
    ** Getter for location
    *******************************************************************************/
   public String getLocation()
   {
      return (this.location);
   }



   /*******************************************************************************
    ** Setter for location
    *******************************************************************************/
   public void setLocation(String location)
   {
      this.location = location;
   }



   /*******************************************************************************
    ** Fluent setter for location
    *******************************************************************************/
   public AssistantProvider withLocation(String location)
   {
      this.location = location;
      return (this);
   }



   /*******************************************************************************
    ** Getter for hipaaEligible
    *******************************************************************************/
   public Boolean getHipaaEligible()
   {
      return (this.hipaaEligible);
   }



   /*******************************************************************************
    ** Setter for hipaaEligible
    *******************************************************************************/
   public void setHipaaEligible(Boolean hipaaEligible)
   {
      this.hipaaEligible = hipaaEligible;
   }



   /*******************************************************************************
    ** Fluent setter for hipaaEligible
    *******************************************************************************/
   public AssistantProvider withHipaaEligible(Boolean hipaaEligible)
   {
      this.hipaaEligible = hipaaEligible;
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
   public AssistantProvider withRegion(String region)
   {
      this.region = region;
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
   public AssistantProvider withApiVersion(String apiVersion)
   {
      this.apiVersion = apiVersion;
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
   public AssistantProvider withConnectTimeoutSeconds(Integer connectTimeoutSeconds)
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
   public AssistantProvider withRequestTimeoutSeconds(Integer requestTimeoutSeconds)
   {
      this.requestTimeoutSeconds = requestTimeoutSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastDiscoveryAt
    *******************************************************************************/
   public Instant getLastDiscoveryAt()
   {
      return (this.lastDiscoveryAt);
   }



   /*******************************************************************************
    ** Setter for lastDiscoveryAt
    *******************************************************************************/
   public void setLastDiscoveryAt(Instant lastDiscoveryAt)
   {
      this.lastDiscoveryAt = lastDiscoveryAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastDiscoveryAt
    *******************************************************************************/
   public AssistantProvider withLastDiscoveryAt(Instant lastDiscoveryAt)
   {
      this.lastDiscoveryAt = lastDiscoveryAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastDiscoveryStatus
    *******************************************************************************/
   public String getLastDiscoveryStatus()
   {
      return (this.lastDiscoveryStatus);
   }



   /*******************************************************************************
    ** Setter for lastDiscoveryStatus
    *******************************************************************************/
   public void setLastDiscoveryStatus(String lastDiscoveryStatus)
   {
      this.lastDiscoveryStatus = lastDiscoveryStatus;
   }



   /*******************************************************************************
    ** Fluent setter for lastDiscoveryStatus
    *******************************************************************************/
   public AssistantProvider withLastDiscoveryStatus(String lastDiscoveryStatus)
   {
      this.lastDiscoveryStatus = lastDiscoveryStatus;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastDiscoveryError
    *******************************************************************************/
   public String getLastDiscoveryError()
   {
      return (this.lastDiscoveryError);
   }



   /*******************************************************************************
    ** Setter for lastDiscoveryError
    *******************************************************************************/
   public void setLastDiscoveryError(String lastDiscoveryError)
   {
      this.lastDiscoveryError = lastDiscoveryError;
   }



   /*******************************************************************************
    ** Fluent setter for lastDiscoveryError
    *******************************************************************************/
   public AssistantProvider withLastDiscoveryError(String lastDiscoveryError)
   {
      this.lastDiscoveryError = lastDiscoveryError;
      return (this);
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
   public AssistantProvider withTlsCaBundle(String tlsCaBundle)
   {
      this.tlsCaBundle = tlsCaBundle;
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
   public AssistantProvider withCloudProject(String cloudProject)
   {
      this.cloudProject = cloudProject;
      return (this);
   }
}
