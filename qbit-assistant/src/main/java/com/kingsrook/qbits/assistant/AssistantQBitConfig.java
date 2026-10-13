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

package com.kingsrook.qbits.assistant;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kingsrook.qbits.assistant.engine.ModelPricing;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qqq.backend.core.actions.ai.AiExposure;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.producers.MetaDataCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Configuration of the Assistant QBit (spec 2.3).
 **
 ** Required: backendName (where the assistant_* tables live) and at least one
 ** model provider (withModelProvider), named by providerName. API keys never
 ** go here: a provider reads its key from the environment or from a value the
 ** host passes in from its secret store.
 **
 ** validate() is list-based, null-safe and never throws; the producer fails
 ** production if it reports errors.
 *******************************************************************************/
public class AssistantQBitConfig implements QBitConfig
{
   public static final String ACCESS_PERMISSION        = "assistant.hasAccess";
   public static final String ADMIN_PERMISSION         = "assistant.admin";
   public static final String DEFAULT_OWNER_KEY_TYPE   = "assistant_user_id";
   public static final String ENABLED_ENV_VAR          = "QQQ_ASSISTANT_ENABLED";
   public static final String DEFAULT_SHORTCUT         = "mod+j";
   public static final String DEFAULT_PROVIDER_NAME    = "anthropic";

   /*******************************************************************************
    ** What an API key reference must look like: an environment variable name.
    *******************************************************************************/
   public static final Pattern ENV_NAME = Pattern.compile("^[A-Z_][A-Z0-9_]{0,99}$");

   /*******************************************************************************
    ** The owner key type used before 0.1.0-RC.3 (camelCase). See UPGRADE.md.
    *******************************************************************************/
   @Deprecated(since = "0.1.0-RC.3")
   public static final String LEGACY_DEFAULT_OWNER_KEY_TYPE = "assistantUserId";

   private Boolean               enabled                    = true;
   private String                backendName;
   private String                providerName               = DEFAULT_PROVIDER_NAME;
   private AssistantModelRouting models                     = new AssistantModelRouting();
   private AiExposure            defaultExposure            = AiExposure.VALUES;
   private Integer               maxToolCallsPerTurn        = 25;
   private Integer               maxParallelToolCalls       = 4;
   private Integer               maxRowsToModel             = 50;
   private Integer               maxCellChars               = 500;
   private Integer               maxTurnSeconds             = 300;
   private Integer               maxUserMessageChars        = 8000;
   private Integer               historyMessageLimit        = 40;
   private AssistantBudgets      budgets                    = new AssistantBudgets();
   private Integer               maxTurnsPerUserPerMinute   = 10;
   private Integer               maxConcurrentTurnsPerUser  = 1;
   private Integer               conversationRetentionDays  = 90;
   private Integer               toolResultRetentionDays    = 30;
   private Integer               approvalRetentionDays      = 365;
   private Integer               policyCacheSeconds         = 30;
   private String                ownerSecurityKeyType       = DEFAULT_OWNER_KEY_TYPE;
   private String                shortcut                   = DEFAULT_SHORTCUT;
   private String                retentionSchedulerName;
   private Boolean               registerRoutes             = true;
   private Map<String, ModelPricing> pricing                = ModelPricing.defaults();

   private Set<String>               hiddenWidgetNames        = new LinkedHashSet<>();
   private Set<String>               allowedWidgetNames       = new LinkedHashSet<>();
   private List<String>              customRedactionPatterns  = new ArrayList<>();
   private Integer                   killSwitchPollMillis     = 2000;
   private Set<String>               allowedApiKeyEnvNames    = new LinkedHashSet<>();
   private Set<String>               allowedBaseUrls          = new LinkedHashSet<>();
   private Integer                   providerTestTimeoutSeconds = 30;
   private Integer                   homeMaxRows              = 20000;
   private String                    tablePathPrefix          = "/app";

   private transient MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer;
   private transient Map<String, AssistantModelProvider>        modelProviders = new LinkedHashMap<>();
   private transient Map<String, AssistantProviderType>         providerTypes  = new LinkedHashMap<>();
   private transient Function<String, String>                   secretResolver = System::getenv;
   private transient AssistantRuntime                           runtime;



   /*******************************************************************************
    ** Collect configuration errors. Never throws; null-safe for a partially
    ** built QInstance.
    *******************************************************************************/
   @Override
   public void validate(QInstance qInstance, List<String> errors)
   {
      if(!StringUtils.hasContent(backendName))
      {
         errors.add("backendName is required for the Assistant QBit");
      }
      else if(qInstance != null && qInstance.getBackends() != null && qInstance.getBackend(backendName) == null)
      {
         errors.add("Backend not found: " + backendName);
      }

      if(!StringUtils.hasContent(providerName))
      {
         errors.add("providerName is required");
      }
      if(!StringUtils.hasContent(ownerSecurityKeyType))
      {
         errors.add("ownerSecurityKeyType is required");
      }

      if(models == null)
      {
         errors.add("models is required");
      }
      else
      {
         models.validate(errors);
      }

      if(budgets != null)
      {
         budgets.validate(errors);
      }

      if(defaultExposure == null)
      {
         errors.add("defaultExposure is required");
      }

      requirePositive(errors, "maxToolCallsPerTurn", maxToolCallsPerTurn);
      requirePositive(errors, "maxParallelToolCalls", maxParallelToolCalls);
      requirePositive(errors, "maxRowsToModel", maxRowsToModel);
      requirePositive(errors, "maxCellChars", maxCellChars);
      requirePositive(errors, "maxTurnSeconds", maxTurnSeconds);
      requirePositive(errors, "maxUserMessageChars", maxUserMessageChars);
      requirePositive(errors, "historyMessageLimit", historyMessageLimit);
      requirePositive(errors, "maxTurnsPerUserPerMinute", maxTurnsPerUserPerMinute);
      requirePositive(errors, "maxConcurrentTurnsPerUser", maxConcurrentTurnsPerUser);
      requirePositive(errors, "conversationRetentionDays", conversationRetentionDays);
      requirePositive(errors, "toolResultRetentionDays", toolResultRetentionDays);
      requirePositive(errors, "approvalRetentionDays", approvalRetentionDays);
      requirePositive(errors, "killSwitchPollMillis", killSwitchPollMillis);
      requirePositive(errors, "providerTestTimeoutSeconds", providerTestTimeoutSeconds);
      requirePositive(errors, "homeMaxRows", homeMaxRows);
      for(String name : allowedApiKeyEnvNames == null ? Set.<String>of() : allowedApiKeyEnvNames)
      {
         if(name == null || !ENV_NAME.matcher(name).matches())
         {
            errors.add("allowedApiKeyEnvNames: not an environment variable name: " + name);
         }
      }
      for(String pattern : customRedactionPatterns == null ? List.<String>of() : customRedactionPatterns)
      {
         try
         {
            Pattern.compile(pattern);
         }
         catch(RuntimeException e)
         {
            errors.add("customRedactionPatterns: not a valid regular expression: " + pattern);
         }
      }
      if(policyCacheSeconds == null || policyCacheSeconds < 0)
      {
         errors.add("policyCacheSeconds must not be negative");
      }

      if(maxRowsToModel != null && maxRowsToModel > 500)
      {
         errors.add("maxRowsToModel must not exceed 500");
      }
      if(maxParallelToolCalls != null && maxParallelToolCalls > 16)
      {
         errors.add("maxParallelToolCalls must not exceed 16");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void requirePositive(List<String> errors, String name, Integer value)
   {
      if(value == null || value <= 0)
      {
         errors.add(name + " must be a positive number");
      }
   }



   /*******************************************************************************
    ** Enabled by config and not switched off by the QQQ_ASSISTANT_ENABLED
    ** environment variable ("false" disables; anything else leaves config).
    *******************************************************************************/
   @JsonIgnore
   public boolean isEffectivelyEnabled()
   {
      if(!Boolean.TRUE.equals(enabled))
      {
         return (false);
      }
      String env = System.getenv(ENABLED_ENV_VAR);
      return (env == null || !"false".equalsIgnoreCase(env.trim()));
   }



   /*******************************************************************************
    ** Register a model provider under its own name.
    *******************************************************************************/
   public AssistantQBitConfig withModelProvider(AssistantModelProvider provider)
   {
      if(provider != null)
      {
         modelProviders.put(provider.getName(), provider);
      }
      return (this);
   }



   /*******************************************************************************
    ** The provider named by providerName, or null.
    *******************************************************************************/
   @JsonIgnore
   public AssistantModelProvider getActiveModelProvider()
   {
      return (modelProviders == null || providerName == null ? null : modelProviders.get(providerName));
   }



   /*******************************************************************************
    ** All registered providers (read-only view not needed: hosts may add).
    *******************************************************************************/
   @JsonIgnore
   public Map<String, AssistantModelProvider> getModelProviders()
   {
      return (modelProviders);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getDefaultBackendNameForTables()
   {
      return (backendName);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   @JsonIgnore
   public MetaDataCustomizerInterface<QTableMetaData> getTableMetaDataCustomizer()
   {
      return (tableMetaDataCustomizer);
   }



   /*******************************************************************************
    ** Setter for tableMetaDataCustomizer (e.g. snake_case backend names).
    *******************************************************************************/
   public void setTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer)
   {
      this.tableMetaDataCustomizer = tableMetaDataCustomizer;
   }



   /*******************************************************************************
    ** Fluent setter for tableMetaDataCustomizer
    *******************************************************************************/
   public AssistantQBitConfig withTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer)
   {
      this.tableMetaDataCustomizer = tableMetaDataCustomizer;
      return (this);
   }



   /*******************************************************************************
    ** The runtime built by the producer (null before production).
    *******************************************************************************/
   @JsonIgnore
   public AssistantRuntime getRuntime()
   {
      return (runtime);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public void setRuntime(AssistantRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** No secrets live here, but keep logs short.
    *******************************************************************************/
   @Override
   public String toString()
   {
      List<String> parts = new ArrayList<>();
      parts.add("enabled=" + enabled);
      parts.add("backendName=" + backendName);
      parts.add("providerName=" + providerName);
      parts.add("mainModel=" + (models == null ? null : models.getMainModel()));
      parts.add("providers=" + (modelProviders == null ? "[]" : modelProviders.keySet()));
      return ("AssistantQBitConfig{" + String.join(", ", parts) + "}");
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
   public AssistantQBitConfig withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for backendName
    *******************************************************************************/
   public String getBackendName()
   {
      return (this.backendName);
   }



   /*******************************************************************************
    ** Setter for backendName
    *******************************************************************************/
   public void setBackendName(String backendName)
   {
      this.backendName = backendName;
   }



   /*******************************************************************************
    ** Fluent setter for backendName
    *******************************************************************************/
   public AssistantQBitConfig withBackendName(String backendName)
   {
      this.backendName = backendName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for providerName
    *******************************************************************************/
   public String getProviderName()
   {
      return (this.providerName);
   }



   /*******************************************************************************
    ** Setter for providerName
    *******************************************************************************/
   public void setProviderName(String providerName)
   {
      this.providerName = providerName;
   }



   /*******************************************************************************
    ** Fluent setter for providerName
    *******************************************************************************/
   public AssistantQBitConfig withProviderName(String providerName)
   {
      this.providerName = providerName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for models
    *******************************************************************************/
   public AssistantModelRouting getModels()
   {
      return (this.models);
   }



   /*******************************************************************************
    ** Setter for models
    *******************************************************************************/
   public void setModels(AssistantModelRouting models)
   {
      this.models = models;
   }



   /*******************************************************************************
    ** Fluent setter for models
    *******************************************************************************/
   public AssistantQBitConfig withModels(AssistantModelRouting models)
   {
      this.models = models;
      return (this);
   }



   /*******************************************************************************
    ** Getter for defaultExposure
    *******************************************************************************/
   public AiExposure getDefaultExposure()
   {
      return (this.defaultExposure);
   }



   /*******************************************************************************
    ** Setter for defaultExposure
    *******************************************************************************/
   public void setDefaultExposure(AiExposure defaultExposure)
   {
      this.defaultExposure = defaultExposure;
   }



   /*******************************************************************************
    ** Fluent setter for defaultExposure
    *******************************************************************************/
   public AssistantQBitConfig withDefaultExposure(AiExposure defaultExposure)
   {
      this.defaultExposure = defaultExposure;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxToolCallsPerTurn
    *******************************************************************************/
   public Integer getMaxToolCallsPerTurn()
   {
      return (this.maxToolCallsPerTurn);
   }



   /*******************************************************************************
    ** Setter for maxToolCallsPerTurn
    *******************************************************************************/
   public void setMaxToolCallsPerTurn(Integer maxToolCallsPerTurn)
   {
      this.maxToolCallsPerTurn = maxToolCallsPerTurn;
   }



   /*******************************************************************************
    ** Fluent setter for maxToolCallsPerTurn
    *******************************************************************************/
   public AssistantQBitConfig withMaxToolCallsPerTurn(Integer maxToolCallsPerTurn)
   {
      this.maxToolCallsPerTurn = maxToolCallsPerTurn;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxParallelToolCalls
    *******************************************************************************/
   public Integer getMaxParallelToolCalls()
   {
      return (this.maxParallelToolCalls);
   }



   /*******************************************************************************
    ** Setter for maxParallelToolCalls
    *******************************************************************************/
   public void setMaxParallelToolCalls(Integer maxParallelToolCalls)
   {
      this.maxParallelToolCalls = maxParallelToolCalls;
   }



   /*******************************************************************************
    ** Fluent setter for maxParallelToolCalls
    *******************************************************************************/
   public AssistantQBitConfig withMaxParallelToolCalls(Integer maxParallelToolCalls)
   {
      this.maxParallelToolCalls = maxParallelToolCalls;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxRowsToModel
    *******************************************************************************/
   public Integer getMaxRowsToModel()
   {
      return (this.maxRowsToModel);
   }



   /*******************************************************************************
    ** Setter for maxRowsToModel
    *******************************************************************************/
   public void setMaxRowsToModel(Integer maxRowsToModel)
   {
      this.maxRowsToModel = maxRowsToModel;
   }



   /*******************************************************************************
    ** Fluent setter for maxRowsToModel
    *******************************************************************************/
   public AssistantQBitConfig withMaxRowsToModel(Integer maxRowsToModel)
   {
      this.maxRowsToModel = maxRowsToModel;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxCellChars
    *******************************************************************************/
   public Integer getMaxCellChars()
   {
      return (this.maxCellChars);
   }



   /*******************************************************************************
    ** Setter for maxCellChars
    *******************************************************************************/
   public void setMaxCellChars(Integer maxCellChars)
   {
      this.maxCellChars = maxCellChars;
   }



   /*******************************************************************************
    ** Fluent setter for maxCellChars
    *******************************************************************************/
   public AssistantQBitConfig withMaxCellChars(Integer maxCellChars)
   {
      this.maxCellChars = maxCellChars;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxTurnSeconds
    *******************************************************************************/
   public Integer getMaxTurnSeconds()
   {
      return (this.maxTurnSeconds);
   }



   /*******************************************************************************
    ** Setter for maxTurnSeconds
    *******************************************************************************/
   public void setMaxTurnSeconds(Integer maxTurnSeconds)
   {
      this.maxTurnSeconds = maxTurnSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for maxTurnSeconds
    *******************************************************************************/
   public AssistantQBitConfig withMaxTurnSeconds(Integer maxTurnSeconds)
   {
      this.maxTurnSeconds = maxTurnSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxUserMessageChars
    *******************************************************************************/
   public Integer getMaxUserMessageChars()
   {
      return (this.maxUserMessageChars);
   }



   /*******************************************************************************
    ** Setter for maxUserMessageChars
    *******************************************************************************/
   public void setMaxUserMessageChars(Integer maxUserMessageChars)
   {
      this.maxUserMessageChars = maxUserMessageChars;
   }



   /*******************************************************************************
    ** Fluent setter for maxUserMessageChars
    *******************************************************************************/
   public AssistantQBitConfig withMaxUserMessageChars(Integer maxUserMessageChars)
   {
      this.maxUserMessageChars = maxUserMessageChars;
      return (this);
   }



   /*******************************************************************************
    ** Getter for historyMessageLimit
    *******************************************************************************/
   public Integer getHistoryMessageLimit()
   {
      return (this.historyMessageLimit);
   }



   /*******************************************************************************
    ** Setter for historyMessageLimit
    *******************************************************************************/
   public void setHistoryMessageLimit(Integer historyMessageLimit)
   {
      this.historyMessageLimit = historyMessageLimit;
   }



   /*******************************************************************************
    ** Fluent setter for historyMessageLimit
    *******************************************************************************/
   public AssistantQBitConfig withHistoryMessageLimit(Integer historyMessageLimit)
   {
      this.historyMessageLimit = historyMessageLimit;
      return (this);
   }



   /*******************************************************************************
    ** Getter for budgets
    *******************************************************************************/
   public AssistantBudgets getBudgets()
   {
      return (this.budgets);
   }



   /*******************************************************************************
    ** Setter for budgets
    *******************************************************************************/
   public void setBudgets(AssistantBudgets budgets)
   {
      this.budgets = budgets;
   }



   /*******************************************************************************
    ** Fluent setter for budgets
    *******************************************************************************/
   public AssistantQBitConfig withBudgets(AssistantBudgets budgets)
   {
      this.budgets = budgets;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxTurnsPerUserPerMinute
    *******************************************************************************/
   public Integer getMaxTurnsPerUserPerMinute()
   {
      return (this.maxTurnsPerUserPerMinute);
   }



   /*******************************************************************************
    ** Setter for maxTurnsPerUserPerMinute
    *******************************************************************************/
   public void setMaxTurnsPerUserPerMinute(Integer maxTurnsPerUserPerMinute)
   {
      this.maxTurnsPerUserPerMinute = maxTurnsPerUserPerMinute;
   }



   /*******************************************************************************
    ** Fluent setter for maxTurnsPerUserPerMinute
    *******************************************************************************/
   public AssistantQBitConfig withMaxTurnsPerUserPerMinute(Integer maxTurnsPerUserPerMinute)
   {
      this.maxTurnsPerUserPerMinute = maxTurnsPerUserPerMinute;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxConcurrentTurnsPerUser
    *******************************************************************************/
   public Integer getMaxConcurrentTurnsPerUser()
   {
      return (this.maxConcurrentTurnsPerUser);
   }



   /*******************************************************************************
    ** Setter for maxConcurrentTurnsPerUser
    *******************************************************************************/
   public void setMaxConcurrentTurnsPerUser(Integer maxConcurrentTurnsPerUser)
   {
      this.maxConcurrentTurnsPerUser = maxConcurrentTurnsPerUser;
   }



   /*******************************************************************************
    ** Fluent setter for maxConcurrentTurnsPerUser
    *******************************************************************************/
   public AssistantQBitConfig withMaxConcurrentTurnsPerUser(Integer maxConcurrentTurnsPerUser)
   {
      this.maxConcurrentTurnsPerUser = maxConcurrentTurnsPerUser;
      return (this);
   }



   /*******************************************************************************
    ** Getter for conversationRetentionDays
    *******************************************************************************/
   public Integer getConversationRetentionDays()
   {
      return (this.conversationRetentionDays);
   }



   /*******************************************************************************
    ** Setter for conversationRetentionDays
    *******************************************************************************/
   public void setConversationRetentionDays(Integer conversationRetentionDays)
   {
      this.conversationRetentionDays = conversationRetentionDays;
   }



   /*******************************************************************************
    ** Fluent setter for conversationRetentionDays
    *******************************************************************************/
   public AssistantQBitConfig withConversationRetentionDays(Integer conversationRetentionDays)
   {
      this.conversationRetentionDays = conversationRetentionDays;
      return (this);
   }



   /*******************************************************************************
    ** Getter for toolResultRetentionDays
    *******************************************************************************/
   public Integer getToolResultRetentionDays()
   {
      return (this.toolResultRetentionDays);
   }



   /*******************************************************************************
    ** Setter for toolResultRetentionDays
    *******************************************************************************/
   public void setToolResultRetentionDays(Integer toolResultRetentionDays)
   {
      this.toolResultRetentionDays = toolResultRetentionDays;
   }



   /*******************************************************************************
    ** Fluent setter for toolResultRetentionDays
    *******************************************************************************/
   public AssistantQBitConfig withToolResultRetentionDays(Integer toolResultRetentionDays)
   {
      this.toolResultRetentionDays = toolResultRetentionDays;
      return (this);
   }



   /*******************************************************************************
    ** Getter for approvalRetentionDays
    *******************************************************************************/
   public Integer getApprovalRetentionDays()
   {
      return (this.approvalRetentionDays);
   }



   /*******************************************************************************
    ** Setter for approvalRetentionDays
    *******************************************************************************/
   public void setApprovalRetentionDays(Integer approvalRetentionDays)
   {
      this.approvalRetentionDays = approvalRetentionDays;
   }



   /*******************************************************************************
    ** Fluent setter for approvalRetentionDays
    *******************************************************************************/
   public AssistantQBitConfig withApprovalRetentionDays(Integer approvalRetentionDays)
   {
      this.approvalRetentionDays = approvalRetentionDays;
      return (this);
   }



   /*******************************************************************************
    ** Getter for policyCacheSeconds
    *******************************************************************************/
   public Integer getPolicyCacheSeconds()
   {
      return (this.policyCacheSeconds);
   }



   /*******************************************************************************
    ** Setter for policyCacheSeconds
    *******************************************************************************/
   public void setPolicyCacheSeconds(Integer policyCacheSeconds)
   {
      this.policyCacheSeconds = policyCacheSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for policyCacheSeconds
    *******************************************************************************/
   public AssistantQBitConfig withPolicyCacheSeconds(Integer policyCacheSeconds)
   {
      this.policyCacheSeconds = policyCacheSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for ownerSecurityKeyType
    *******************************************************************************/
   public String getOwnerSecurityKeyType()
   {
      return (this.ownerSecurityKeyType);
   }



   /*******************************************************************************
    ** Setter for ownerSecurityKeyType
    *******************************************************************************/
   public void setOwnerSecurityKeyType(String ownerSecurityKeyType)
   {
      this.ownerSecurityKeyType = ownerSecurityKeyType;
   }



   /*******************************************************************************
    ** Fluent setter for ownerSecurityKeyType
    *******************************************************************************/
   public AssistantQBitConfig withOwnerSecurityKeyType(String ownerSecurityKeyType)
   {
      this.ownerSecurityKeyType = ownerSecurityKeyType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for shortcut
    *******************************************************************************/
   public String getShortcut()
   {
      return (this.shortcut);
   }



   /*******************************************************************************
    ** Setter for shortcut
    *******************************************************************************/
   public void setShortcut(String shortcut)
   {
      this.shortcut = shortcut;
   }



   /*******************************************************************************
    ** Fluent setter for shortcut
    *******************************************************************************/
   public AssistantQBitConfig withShortcut(String shortcut)
   {
      this.shortcut = shortcut;
      return (this);
   }



   /*******************************************************************************
    ** Getter for retentionSchedulerName
    *******************************************************************************/
   public String getRetentionSchedulerName()
   {
      return (this.retentionSchedulerName);
   }



   /*******************************************************************************
    ** Setter for retentionSchedulerName
    *******************************************************************************/
   public void setRetentionSchedulerName(String retentionSchedulerName)
   {
      this.retentionSchedulerName = retentionSchedulerName;
   }



   /*******************************************************************************
    ** Fluent setter for retentionSchedulerName
    *******************************************************************************/
   public AssistantQBitConfig withRetentionSchedulerName(String retentionSchedulerName)
   {
      this.retentionSchedulerName = retentionSchedulerName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for registerRoutes
    *******************************************************************************/
   public Boolean getRegisterRoutes()
   {
      return (this.registerRoutes);
   }



   /*******************************************************************************
    ** Setter for registerRoutes
    *******************************************************************************/
   public void setRegisterRoutes(Boolean registerRoutes)
   {
      this.registerRoutes = registerRoutes;
   }



   /*******************************************************************************
    ** Fluent setter for registerRoutes
    *******************************************************************************/
   public AssistantQBitConfig withRegisterRoutes(Boolean registerRoutes)
   {
      this.registerRoutes = registerRoutes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for pricing
    *******************************************************************************/
   public Map<String, ModelPricing> getPricing()
   {
      return (this.pricing);
   }



   /*******************************************************************************
    ** Setter for pricing
    *******************************************************************************/
   public void setPricing(Map<String, ModelPricing> pricing)
   {
      this.pricing = pricing;
   }



   /*******************************************************************************
    ** Fluent setter for pricing
    *******************************************************************************/
   public AssistantQBitConfig withPricing(Map<String, ModelPricing> pricing)
   {
      this.pricing = pricing;
      return (this);
   }



   /*******************************************************************************
    ** Getter for hiddenWidgetNames
    *******************************************************************************/
   public Set<String> getHiddenWidgetNames()
   {
      return (this.hiddenWidgetNames);
   }



   /*******************************************************************************
    ** Setter for hiddenWidgetNames
    *******************************************************************************/
   public void setHiddenWidgetNames(Set<String> hiddenWidgetNames)
   {
      this.hiddenWidgetNames = hiddenWidgetNames;
   }



   /*******************************************************************************
    ** Fluent setter for hiddenWidgetNames
    *******************************************************************************/
   public AssistantQBitConfig withHiddenWidgetNames(Set<String> hiddenWidgetNames)
   {
      this.hiddenWidgetNames = hiddenWidgetNames;
      return (this);
   }



   /*******************************************************************************
    ** Getter for allowedWidgetNames
    *******************************************************************************/
   public Set<String> getAllowedWidgetNames()
   {
      return (this.allowedWidgetNames);
   }



   /*******************************************************************************
    ** Setter for allowedWidgetNames
    *******************************************************************************/
   public void setAllowedWidgetNames(Set<String> allowedWidgetNames)
   {
      this.allowedWidgetNames = allowedWidgetNames;
   }



   /*******************************************************************************
    ** Fluent setter for allowedWidgetNames
    *******************************************************************************/
   public AssistantQBitConfig withAllowedWidgetNames(Set<String> allowedWidgetNames)
   {
      this.allowedWidgetNames = allowedWidgetNames;
      return (this);
   }



   /*******************************************************************************
    ** Getter for customRedactionPatterns
    *******************************************************************************/
   public List<String> getCustomRedactionPatterns()
   {
      return (this.customRedactionPatterns);
   }



   /*******************************************************************************
    ** Setter for customRedactionPatterns
    *******************************************************************************/
   public void setCustomRedactionPatterns(List<String> customRedactionPatterns)
   {
      this.customRedactionPatterns = customRedactionPatterns;
   }



   /*******************************************************************************
    ** Fluent setter for customRedactionPatterns
    *******************************************************************************/
   public AssistantQBitConfig withCustomRedactionPatterns(List<String> customRedactionPatterns)
   {
      this.customRedactionPatterns = customRedactionPatterns;
      return (this);
   }



   /*******************************************************************************
    ** Getter for killSwitchPollMillis
    *******************************************************************************/
   public Integer getKillSwitchPollMillis()
   {
      return (this.killSwitchPollMillis);
   }



   /*******************************************************************************
    ** Setter for killSwitchPollMillis
    *******************************************************************************/
   public void setKillSwitchPollMillis(Integer killSwitchPollMillis)
   {
      this.killSwitchPollMillis = killSwitchPollMillis;
   }



   /*******************************************************************************
    ** Fluent setter for killSwitchPollMillis
    *******************************************************************************/
   public AssistantQBitConfig withKillSwitchPollMillis(Integer killSwitchPollMillis)
   {
      this.killSwitchPollMillis = killSwitchPollMillis;
      return (this);
   }


   /*******************************************************************************
    ** Register a provider type for the provider settings table.
    *******************************************************************************/
   public AssistantQBitConfig withProviderType(AssistantProviderType providerType)
   {
      if(providerType != null)
      {
         providerTypes.put(providerType.getType(), providerType);
      }
      return (this);
   }



   /*******************************************************************************
    ** The registered provider types, by type id.
    *******************************************************************************/
   @JsonIgnore
   public Map<String, AssistantProviderType> getProviderTypes()
   {
      return (providerTypes);
   }



   /*******************************************************************************
    ** The environment variable names an assistant_provider row may use as its
    ** API key reference: the configured allow-list plus each registered type's
    ** default (e.g. ANTHROPIC_API_KEY). An admin can therefore never point a
    ** provider at an unrelated secret such as a database password.
    *******************************************************************************/
   @JsonIgnore
   public Set<String> getEffectiveAllowedApiKeyEnvNames()
   {
      Set<String> names = new LinkedHashSet<>(allowedApiKeyEnvNames == null ? Set.of() : allowedApiKeyEnvNames);
      providerTypes.values().forEach(t -> names.add(t.getDefaultApiKeyEnvName()));
      return (names);
   }



   /*******************************************************************************
    ** Resolve a key reference (an environment variable name) to the secret.
    *******************************************************************************/
   @JsonIgnore
   public Function<String, String> getSecretResolver()
   {
      return (secretResolver);
   }



   /*******************************************************************************
    ** Use another secret lookup than System.getenv (a host secret store; tests).
    *******************************************************************************/
   public AssistantQBitConfig withSecretResolver(Function<String, String> secretResolver)
   {
      this.secretResolver = secretResolver;
      return (this);
   }



   /*******************************************************************************
    ** Getter for allowedApiKeyEnvNames (extra API key environment variable names a provider row may reference)
    *******************************************************************************/
   public Set<String> getAllowedApiKeyEnvNames()
   {
      return (this.allowedApiKeyEnvNames);
   }



   /*******************************************************************************
    ** Setter for allowedApiKeyEnvNames
    *******************************************************************************/
   public void setAllowedApiKeyEnvNames(Set<String> allowedApiKeyEnvNames)
   {
      this.allowedApiKeyEnvNames = allowedApiKeyEnvNames;
   }



   /*******************************************************************************
    ** Fluent setter for allowedApiKeyEnvNames
    *******************************************************************************/
   public AssistantQBitConfig withAllowedApiKeyEnvNames(Set<String> allowedApiKeyEnvNames)
   {
      this.allowedApiKeyEnvNames = allowedApiKeyEnvNames;
      return (this);
   }



   /*******************************************************************************
    ** Getter for allowedBaseUrls (base URL overrides a provider row may use; empty allows only the type's own URL)
    *******************************************************************************/
   public Set<String> getAllowedBaseUrls()
   {
      return (this.allowedBaseUrls);
   }



   /*******************************************************************************
    ** Setter for allowedBaseUrls
    *******************************************************************************/
   public void setAllowedBaseUrls(Set<String> allowedBaseUrls)
   {
      this.allowedBaseUrls = allowedBaseUrls;
   }



   /*******************************************************************************
    ** Fluent setter for allowedBaseUrls
    *******************************************************************************/
   public AssistantQBitConfig withAllowedBaseUrls(Set<String> allowedBaseUrls)
   {
      this.allowedBaseUrls = allowedBaseUrls;
      return (this);
   }



   /*******************************************************************************
    ** Getter for providerTestTimeoutSeconds (how long Test connection waits for the model)
    *******************************************************************************/
   public Integer getProviderTestTimeoutSeconds()
   {
      return (this.providerTestTimeoutSeconds);
   }



   /*******************************************************************************
    ** Setter for providerTestTimeoutSeconds
    *******************************************************************************/
   public void setProviderTestTimeoutSeconds(Integer providerTestTimeoutSeconds)
   {
      this.providerTestTimeoutSeconds = providerTestTimeoutSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for providerTestTimeoutSeconds
    *******************************************************************************/
   public AssistantQBitConfig withProviderTestTimeoutSeconds(Integer providerTestTimeoutSeconds)
   {
      this.providerTestTimeoutSeconds = providerTestTimeoutSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for homeMaxRows (rows per table the app home reads for one period)
    *******************************************************************************/
   public Integer getHomeMaxRows()
   {
      return (this.homeMaxRows);
   }



   /*******************************************************************************
    ** Setter for homeMaxRows
    *******************************************************************************/
   public void setHomeMaxRows(Integer homeMaxRows)
   {
      this.homeMaxRows = homeMaxRows;
   }



   /*******************************************************************************
    ** Fluent setter for homeMaxRows
    *******************************************************************************/
   public AssistantQBitConfig withHomeMaxRows(Integer homeMaxRows)
   {
      this.homeMaxRows = homeMaxRows;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tablePathPrefix (the front of the links the app home makes to tables and records)
    *******************************************************************************/
   public String getTablePathPrefix()
   {
      return (this.tablePathPrefix);
   }



   /*******************************************************************************
    ** Setter for tablePathPrefix
    *******************************************************************************/
   public void setTablePathPrefix(String tablePathPrefix)
   {
      this.tablePathPrefix = tablePathPrefix;
   }



   /*******************************************************************************
    ** Fluent setter for tablePathPrefix
    *******************************************************************************/
   public AssistantQBitConfig withTablePathPrefix(String tablePathPrefix)
   {
      this.tablePathPrefix = tablePathPrefix;
      return (this);
   }

}
