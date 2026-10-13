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

package com.kingsrook.qbits.assistant.engine;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantModelPrice;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Which provider and model answer, from the assistant_provider settings rows,
 ** and what a call costs, from the assistant_model_price rows.
 **
 ** - The active provider is the enabled row marked default (lowest id), else
 **   the lowest enabled row. With no enabled rows, the provider registered in
 **   the QBit config (withModelProvider / providerName) is used, so hosts
 **   that have not created a row keep working.
 ** - A row's API key is never stored: apiKeyEnvName names an environment
 **   variable (resolved through config.getSecretResolver()). Only names in
 **   the allow-list may be used, and a base URL override must be allowed in
 **   the config, so an admin cannot send another secret, or the key, to a
 **   host of their choosing.
 ** - Built providers are cached by row settings and a SHA-256 fingerprint of
 **   the key, so a rotated key builds a new client. Keys are never logged.
 *******************************************************************************/
public class ProviderService
{
   private static final QLogger LOG = QLogger.getLogger(ProviderService.class);

   public static final String KEY_STATUS_FIELD = "apiKeyStatus";

   private final AssistantQBitConfig config;
   private final AssistantStore      store;
   private final Clock               clock;

   private final Map<String, AssistantModelProvider> built       = new ConcurrentHashMap<>();
   private final Map<Integer, Semaphore>             concurrency = new ConcurrentHashMap<>();

   private volatile List<AssistantProvider>          cachedRows;
   private volatile Map<String, ModelPricing>        cachedPrices;
   private volatile Instant                          cachedAt;



   /*******************************************************************************
    ** The provider a turn uses. row is null when the config provider is used.
    *******************************************************************************/
   public record Active(AssistantProvider row, String providerType, String model, AssistantModelProvider provider)
   {
      /*******************************************************************************
       ** The row id, or null.
       *******************************************************************************/
      public Integer providerId()
      {
         return (row == null ? null : row.getId());
      }
   }



   /*******************************************************************************
    ** A provider row that cannot be used (the message is safe to show).
    *******************************************************************************/
   public static class ProviderSetupException extends QException
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      public ProviderSetupException(String message)
      {
         super(message);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public ProviderService(AssistantQBitConfig config, AssistantStore store, Clock clock)
   {
      this.config = config;
      this.store = store;
      this.clock = clock;
   }



   /*******************************************************************************
    ** The active provider for a model choice from the policy (used when the row
    ** has no model), or null when nothing usable is configured.
    *******************************************************************************/
   public Active resolve(String policyModel) throws QException
   {
      AssistantProvider row = activeRow();
      if(row == null)
      {
         AssistantModelProvider provider = config.getActiveModelProvider();
         return (provider == null ? null : new Active(null, provider.getName(), policyModel, provider));
      }

      try
      {
         return (forRow(row, policyModel));
      }
      catch(ProviderSetupException e)
      {
         LOG.warn("The default assistant provider cannot be used", e, logPair("providerId", row.getId()));
         return (null);
      }
   }



   /*******************************************************************************
    ** The default enabled provider row, or null.
    *******************************************************************************/
   public AssistantProvider activeRow() throws QException
   {
      AssistantProvider first = null;
      for(AssistantProvider row : rows())
      {
         if(Boolean.TRUE.equals(row.getEnabled()))
         {
            if(Boolean.TRUE.equals(row.getIsDefault()))
            {
               return (row);
            }
            if(first == null)
            {
               first = row;
            }
         }
      }
      return (first);
   }



   /*******************************************************************************
    ** Build (or reuse) the provider of one row. Throws a ProviderSetupException
    ** with a message for the admin when the row cannot be used.
    *******************************************************************************/
   public Active forRow(AssistantProvider row, String fallbackModel) throws ProviderSetupException
   {
      List<String> errors = validate(row);
      if(!errors.isEmpty())
      {
         throw (new ProviderSetupException(String.join(" ", errors)));
      }

      AssistantProviderType type = config.getProviderTypes().get(row.getProviderType());
      String                key  = resolveKey(row);
      if(!StringUtils.hasContent(key))
      {
         throw (new ProviderSetupException("No API key: the environment variable " + keyEnvName(row) + " is not set on this server."));
      }

      String model = StringUtils.hasContent(row.getModel()) ? row.getModel() : (StringUtils.hasContent(fallbackModel) ? fallbackModel : first(type.getModels()));
      String baseUrl = StringUtils.hasContent(row.getBaseUrl()) ? row.getBaseUrl().trim() : null;
      String cacheKey = row.getId() + "|" + row.getProviderType() + "|" + baseUrl + "|" + fingerprint(key);
      AssistantModelProvider provider = built.computeIfAbsent(cacheKey, k -> type.create(key, baseUrl));
      return (new Active(row, row.getProviderType(), model, provider));
   }



   /*******************************************************************************
    ** What is wrong with a row's settings (empty when it can be used, apart
    ** from the key being present). Messages never contain a key.
    *******************************************************************************/
   public List<String> validate(AssistantProvider row)
   {
      List<String> errors = new ArrayList<>();
      AssistantProviderType type = row.getProviderType() == null ? null : config.getProviderTypes().get(row.getProviderType());
      if(type == null)
      {
         errors.add("Provider type " + row.getProviderType() + " is not installed on this server.");
         return (errors);
      }

      String envName = keyEnvName(row);
      if(!AssistantQBitConfig.ENV_NAME.matcher(envName).matches())
      {
         errors.add("API key variable must be the NAME of an environment variable (capital letters, digits and _), not the key itself.");
      }
      else if(!config.getEffectiveAllowedApiKeyEnvNames().contains(envName))
      {
         errors.add("API key variable " + envName + " is not allowed on this server. Allowed: " + String.join(", ", config.getEffectiveAllowedApiKeyEnvNames()) + ".");
      }

      if(StringUtils.hasContent(row.getBaseUrl()))
      {
         String url = row.getBaseUrl().trim();
         if(!url.equals(type.getDefaultBaseUrl()) && (config.getAllowedBaseUrls() == null || !config.getAllowedBaseUrls().contains(url)))
         {
            errors.add("Base URL " + url + " is not allowed on this server; leave it empty to use " + type.getDefaultBaseUrl() + ".");
         }
      }

      if(row.getMonthlyCostCapUsd() != null && row.getMonthlyCostCapUsd().signum() < 0)
      {
         errors.add("Monthly cost cap must not be negative.");
      }
      if(row.getMaxOutputTokensPerRequest() != null && row.getMaxOutputTokensPerRequest() < 1)
      {
         errors.add("Max output tokens per request must be at least 1.");
      }
      if(row.getMaxConcurrentRequests() != null && row.getMaxConcurrentRequests() < 1)
      {
         errors.add("Max concurrent requests must be at least 1.");
      }
      return (errors);
   }



   /*******************************************************************************
    ** The masked key status shown on the settings screen: whether the key is
    ** set and its last four characters, never more.
    *******************************************************************************/
   public String keyStatus(AssistantProvider row)
   {
      String envName = keyEnvName(row);
      if(!AssistantQBitConfig.ENV_NAME.matcher(envName).matches() || !config.getEffectiveAllowedApiKeyEnvNames().contains(envName))
      {
         return ("Not usable: the reference is not an allowed environment variable name");
      }
      String key = resolveKey(row);
      if(!StringUtils.hasContent(key))
      {
         return ("Not set (" + envName + " is empty)");
      }
      return (mask(key) + " (from " + envName + ")");
   }



   /*******************************************************************************
    ** "Set • …abcd" for a key (only the last four characters, and none for a
    ** key shorter than 12).
    *******************************************************************************/
   public static String mask(String key)
   {
      if(key == null || key.isEmpty())
      {
         return ("Not set");
      }
      String trimmed = key.trim();
      return (trimmed.length() < 12 ? "Set" : "Set • …" + trimmed.substring(trimmed.length() - 4));
   }



   /*******************************************************************************
    ** The key variable of a row (its own, else the type's default).
    *******************************************************************************/
   public String keyEnvName(AssistantProvider row)
   {
      if(StringUtils.hasContent(row.getApiKeyEnvName()))
      {
         return (row.getApiKeyEnvName().trim());
      }
      AssistantProviderType type = row.getProviderType() == null ? null : config.getProviderTypes().get(row.getProviderType());
      return (type == null ? "" : type.getDefaultApiKeyEnvName());
   }



   /*******************************************************************************
    ** The key of a row (only for allowed variable names), or null.
    *******************************************************************************/
   private String resolveKey(AssistantProvider row)
   {
      String envName = keyEnvName(row);
      if(!AssistantQBitConfig.ENV_NAME.matcher(envName).matches() || !config.getEffectiveAllowedApiKeyEnvNames().contains(envName))
      {
         return (null);
      }
      try
      {
         String value = config.getSecretResolver() == null ? null : config.getSecretResolver().apply(envName);
         return (value == null ? null : value.trim());
      }
      catch(RuntimeException e)
      {
         LOG.warn("Could not resolve an assistant API key reference", logPair("envName", envName));
         return (null);
      }
   }



   /*******************************************************************************
    ** The concurrency gate of a provider row (null: unlimited).
    *******************************************************************************/
   public Semaphore gate(AssistantProvider row)
   {
      if(row == null || row.getMaxConcurrentRequests() == null || row.getMaxConcurrentRequests() < 1)
      {
         return (null);
      }
      int limit = row.getMaxConcurrentRequests();
      return (concurrency.compute(row.getId(), (id, existing) -> limitOf(existing) == limit ? existing : new LimitedSemaphore(limit)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static int limitOf(Semaphore semaphore)
   {
      return (semaphore instanceof LimitedSemaphore limited ? limited.limit : -1);
   }



   /*******************************************************************************
    ** A semaphore that remembers its size (so a changed limit makes a new one).
    *******************************************************************************/
   private static class LimitedSemaphore extends Semaphore
   {
      private final int limit;



      /*******************************************************************************
       **
       *******************************************************************************/
      LimitedSemaphore(int limit)
      {
         super(limit, true);
         this.limit = limit;
      }
   }



   /*******************************************************************************
    ** The monthly cost cap of a row in micro-dollars, or null.
    *******************************************************************************/
   public static Long capMicros(AssistantProvider row)
   {
      if(row == null || row.getMonthlyCostCapUsd() == null)
      {
         return (null);
      }
      return (row.getMonthlyCostCapUsd().multiply(BigDecimal.valueOf(1_000_000)).setScale(0, RoundingMode.DOWN).longValueExact());
   }



   /*******************************************************************************
    ** What a provider has cost since the start of the month (UTC), in
    ** micro-dollars, from the usage rows the session can see.
    *******************************************************************************/
   public long monthToDateCostMicros(Integer providerId) throws QException
   {
      if(providerId == null)
      {
         return (0);
      }
      Instant monthStart = LocalDate.now(clock.withZone(ZoneOffset.UTC)).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
      return (store.sumUsageCostSince(providerId, monthStart));
   }



   /*******************************************************************************
    ** The price of a model: the assistant_model_price row for the type and
    ** model, else the config price table, else zero.
    *******************************************************************************/
   public ModelPricing pricing(String providerType, String model)
   {
      try
      {
         ModelPricing row = prices().get(priceKey(providerType, model));
         if(row != null)
         {
            return (row);
         }
      }
      catch(QException e)
      {
         LOG.warn("Could not read the assistant price table; using the configured prices", e);
      }
      ModelPricing configured = config.getPricing() == null || model == null ? null : config.getPricing().get(model);
      return (configured == null ? new ModelPricing() : configured);
   }



   /*******************************************************************************
    ** A price row as ModelPricing (cache prices default to the standard
    ** multipliers of the input price when empty).
    *******************************************************************************/
   public static ModelPricing toPricing(AssistantModelPrice price)
   {
      double input = d(price.getInputUsdPerMTok());
      return (new ModelPricing()
         .withInputUsdPerMTok(input)
         .withOutputUsdPerMTok(d(price.getOutputUsdPerMTok()))
         .withCacheReadUsdPerMTok(price.getCacheReadUsdPerMTok() == null ? input * 0.1 : d(price.getCacheReadUsdPerMTok()))
         .withCacheWrite5mUsdPerMTok(price.getCacheWrite5mUsdPerMTok() == null ? input * 1.25 : d(price.getCacheWrite5mUsdPerMTok()))
         .withCacheWrite1hUsdPerMTok(price.getCacheWrite1hUsdPerMTok() == null ? input * 2.0 : d(price.getCacheWrite1hUsdPerMTok())));
   }



   /*******************************************************************************
    ** Drop the cached rows and prices (after a settings change).
    *******************************************************************************/
   public void invalidate()
   {
      cachedAt = null;
      cachedRows = null;
      cachedPrices = null;
   }



   /*******************************************************************************
    ** The provider rows (cached for policyCacheSeconds).
    *******************************************************************************/
   public List<AssistantProvider> rows() throws QException
   {
      refresh();
      return (cachedRows);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private Map<String, ModelPricing> prices() throws QException
   {
      refresh();
      return (cachedPrices);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private synchronized void refresh() throws QException
   {
      Instant now = clock.instant();
      if(cachedAt != null && cachedRows != null && cachedPrices != null && cachedAt.plusSeconds(config.getPolicyCacheSeconds()).isAfter(now))
      {
         return;
      }
      List<AssistantProvider>   rows   = store.listProviders();
      Map<String, ModelPricing> prices = new ConcurrentHashMap<>();
      for(AssistantModelPrice price : store.listPrices())
      {
         if(price.getModel() != null && price.getInputUsdPerMTok() != null && price.getOutputUsdPerMTok() != null)
         {
            prices.put(priceKey(price.getProviderType(), price.getModel()), toPricing(price));
         }
      }
      cachedRows = rows;
      cachedPrices = prices;
      cachedAt = now;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String priceKey(String providerType, String model)
   {
      return ((providerType == null ? "" : providerType) + "|" + model);
   }



   /*******************************************************************************
    ** SHA-256 of a key (hex), for the client cache key only.
    *******************************************************************************/
   static String fingerprint(String key)
   {
      try
      {
         return (HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))));
      }
      catch(Exception e)
      {
         throw (new IllegalStateException("SHA-256 is not available", e));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String first(List<String> list)
   {
      return (list == null || list.isEmpty() ? null : list.get(0));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static double d(BigDecimal value)
   {
      return (value == null ? 0 : value.doubleValue());
   }

}
