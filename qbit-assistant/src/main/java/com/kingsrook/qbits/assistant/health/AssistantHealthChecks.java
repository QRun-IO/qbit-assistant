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

package com.kingsrook.qbits.assistant.health;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.engine.ConnectionTest;
import com.kingsrook.qbits.assistant.engine.ProviderHealth;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantRagSource;
import com.kingsrook.qbits.assistant.spi.HealthResult;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.health.HealthCategory;
import com.kingsrook.qqq.backend.core.health.HealthCheck;
import com.kingsrook.qqq.backend.core.health.HealthCheckInput;
import com.kingsrook.qqq.backend.core.health.HealthCheckKind;
import com.kingsrook.qqq.backend.core.health.HealthCheckMetaData;
import com.kingsrook.qqq.backend.core.health.HealthCheckOutcome;
import com.kingsrook.qqq.backend.core.health.HealthCheckSupplier;
import com.kingsrook.qqq.backend.core.health.HealthCheckSupplierMetaData;
import com.kingsrook.qqq.backend.core.health.HealthChecksMetaData;
import com.kingsrook.qqq.backend.core.health.HealthDetails;
import com.kingsrook.qqq.backend.core.health.HealthStatus;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;


/*******************************************************************************
 ** Central health adapters reuse the existing probes without exposing their text.
 *******************************************************************************/
public final class AssistantHealthChecks
{
   public static final String CLIENT_ERRORS = "assistant.client-errors";
   public static final String NOT_SET_UP    = "Not set up yet; checks are skipped";

   private static final QLogger LOG = QLogger.getLogger(AssistantHealthChecks.class);

   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AssistantHealthChecks()
   {
   }



   /*******************************************************************************
    ** Register dynamic namespaces once, even if production is repeated.
    *******************************************************************************/
   public static void register(QInstance instance)
   {
      HealthChecksMetaData registry = HealthChecksMetaData.ofOrWithNew(instance);
      register(registry, "assistant.provider", Providers.class);
      register(registry, "assistant.model", Models.class);
      if(registry.getChecks().stream().noneMatch(existing -> CLIENT_ERRORS.equals(existing.getName())))
      {
         registry.withCheck(new HealthCheckMetaData().withName(CLIENT_ERRORS).withLabel("Assistant client errors").withComponent("assistant")
            .withKind(HealthCheckKind.DEPENDENCY).withCategory(HealthCategory.AI)
            .withIntervalSeconds(300).withTimeoutMs(10000)
            .withCheckInstance(input -> bounded(input, () -> clientErrors(AssistantRuntime.of(input.getQInstance())))));
      }
   }



   /*******************************************************************************
    ** Errors the panel reported in the last hour (POST /assistant/client-errors):
    ** DEGRADED at clientErrorWarnPerHour or more, so administrators see in
    ** System Health that users are hitting broken screens. Only the count.
    *******************************************************************************/
   public static HealthCheckOutcome clientErrors(AssistantRuntime runtime) throws QException
   {
      if(runtime == null)
      {
         return (HealthCheckOutcome.unknown("Assistant not installed"));
      }
      int count = runtime.getStore().countClientErrorsSince(runtime.getClock().instant().minusSeconds(3600));
      Integer threshold = runtime.getConfig().getClientErrorWarnPerHour();
      String  text      = count + " client error" + (count == 1 ? "" : "s") + " reported in the last hour";
      HealthCheckOutcome outcome = threshold != null && count >= threshold ? HealthCheckOutcome.degraded(text) : HealthCheckOutcome.up(text);
      return (outcome.withDetails(new HealthDetails().withValue("lastHour", count).withValue("warnAt", threshold)));
   }



   /*******************************************************************************
    ** Suppliers carry the component; individual definitions also declare it.
    *******************************************************************************/
   private static void register(HealthChecksMetaData registry, String prefix, Class<? extends HealthCheckSupplier> supplier)
   {
      if(registry.getSuppliers().stream().noneMatch(existing -> prefix.equals(existing.getNamePrefix())))
      {
         registry.withSupplier(new HealthCheckSupplierMetaData().withNamePrefix(prefix).withComponent("assistant").withSupplier(new QCodeReference(supplier)));
      }
   }



   /*******************************************************************************
    ** Provider and model ids are integer primary keys, hence already lowercase.
    *******************************************************************************/
   private static HealthCheckMetaData definition(String name, Integer interval, Integer timeout, HealthCheck check)
   {
      return (new HealthCheckMetaData().withName(name).withLabel(name).withComponent("assistant")
         .withKind(HealthCheckKind.DEPENDENCY).withCategory(HealthCategory.AI)
         .withIntervalSeconds(interval).withTimeoutMs(timeout).withCheckInstance(check));
   }



   /*******************************************************************************
    ** One definition per enabled provider that is set up; no I/O to providers
    ** during resolution.
    *******************************************************************************/
   public static class Providers implements HealthCheckSupplier
   {
      /***************************************************************************
       ** Re-resolve rows when the central registry refreshes its suppliers.
       ***************************************************************************/
      @Override
      public List<HealthCheckMetaData> supply(QInstance instance) throws QException
      {
         AssistantRuntime runtime = AssistantRuntime.of(instance);
         List<HealthCheckMetaData> checks = new ArrayList<>();
         for(AssistantProvider row : runtime.getStore().listProviders())
         {
            if(row.isUsable())
            {
               checks.add(definition("assistant.provider." + row.getId(), "LOCAL".equals(runtime.getProviderService().location(row)) ? 60 : 300, 10000,
                  input -> optional(runtime, row.getId(), bounded(input, () -> provider(runtime, row.getId(), input)), false)));
            }
         }
         return (checks);
      }
   }



   /*******************************************************************************
    ** One definition per enabled model, including models of disabled providers,
    ** but none for a provider that is still being set up.
    *******************************************************************************/
   public static class Models implements HealthCheckSupplier
   {
      /***************************************************************************
       ** Eligibility is checked again when a scheduled check actually executes.
       ***************************************************************************/
      @Override
      public List<HealthCheckMetaData> supply(QInstance instance) throws QException
      {
         AssistantRuntime runtime = AssistantRuntime.of(instance);
         List<HealthCheckMetaData> checks = new ArrayList<>();
         Set<Integer> settingUp = runtime.getStore().listProviders().stream().filter(AssistantProvider::isSettingUp).map(AssistantProvider::getId).collect(Collectors.toSet());
         for(AssistantModel row : runtime.getStore().listModels())
         {
            if(Boolean.TRUE.equals(row.getEnabled()) && !settingUp.contains(row.getProviderId()))
            {
               checks.add(definition("assistant.model." + row.getId(), 21600, 30000,
                  input -> optional(runtime, row.getProviderId(), bounded(input, () -> model(runtime, row.getId(), input)), true)));
            }
         }
         return (checks);
      }
   }



   /*******************************************************************************
    ** Clamp transport timeouts and bound even an adapter that ignores them.
    ** Keep the caller's session; never create or elevate a health-check session.
    *******************************************************************************/
   private static HealthCheckOutcome bounded(HealthCheckInput input, Callable<HealthCheckOutcome> call)
   {
      long remaining = Duration.between(Instant.now(), input.getDeadline()).toMillis();
      if(remaining <= 0)
      {
         return (HealthCheckOutcome.down("Health check timed out"));
      }
      var session = QContext.getQSession();
      FutureTask<HealthCheckOutcome> task = new FutureTask<>(() ->
      {
         QContext.init(input.getQInstance(), session);
         try
         {
            return (call.call());
         }
         finally
         {
            QContext.clear();
         }
      });
      Thread.ofVirtual().name("assistant-central-health").start(task);
      try
      {
         return (task.get(remaining, TimeUnit.MILLISECONDS));
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         return (HealthCheckOutcome.down("Health check interrupted"));
      }
      catch(java.util.concurrent.TimeoutException e)
      {
         return (HealthCheckOutcome.down("Health check timed out"));
      }
      catch(java.util.concurrent.ExecutionException e)
      {
         return (HealthCheckOutcome.down("Health check failed"));
      }
      finally
      {
         task.cancel(true);
      }
   }



   /*******************************************************************************
    ** Unsupported probes may read recent traffic-produced breaker state only.
    *******************************************************************************/
   private static HealthCheckOutcome provider(AssistantRuntime runtime, Integer id, HealthCheckInput input) throws QException
   {
      AssistantProvider row = runtime.getStore().getProvider(id);
      if(row == null || !Boolean.TRUE.equals(row.getEnabled()))
      {
         return (HealthCheckOutcome.unknown("Provider disabled or unavailable"));
      }
      if(row.isSettingUp())
      {
         return (HealthCheckOutcome.up(NOT_SET_UP).withDetails(new HealthDetails().withValue("providerId", id)));
      }
      var type = runtime.getConfig().getProviderTypes().get(row.getProviderType());
      if(type == null)
      {
         return (HealthCheckOutcome.unknown("Provider type unavailable"));
      }
      var settings = runtime.getProviderService().settings(row);
      int seconds = (int) Math.max(1, Math.min(10, Duration.between(Instant.now(), input.getDeadline()).toSeconds()));
      settings.withConnectTimeoutSeconds(Math.min(settings.getConnectTimeoutSeconds(), seconds))
         .withRequestTimeoutSeconds(Math.min(settings.getRequestTimeoutSeconds(), seconds));
      HealthResult result = type.checkHealth(settings);
      HealthCheckOutcome outcome;
      if(result.healthy() == null)
      {
         Instant checked = row.getHealthCheckedAt();
         Instant now = runtime.getClock().instant();
         if(checked != null && !checked.isAfter(now) && checked.plusSeconds(600).isAfter(now) && List.of("UP", "DOWN", "DEGRADED").contains(row.getHealthState() == null ? "" : row.getHealthState()))
         {
            outcome = new HealthCheckOutcome().withStatus(HealthStatus.valueOf(row.getHealthState())).withMessage("Recent circuit-breaker state");
         }
         else
         {
            outcome = HealthCheckOutcome.unknown("health probe not supported");
         }
      }
      else
      {
         outcome = result.healthy() ? HealthCheckOutcome.up("Provider reachable") : HealthCheckOutcome.down("Provider health probe failed");
      }
      return (outcome.withLatencyMs(result.latencyMs()).withDetails(new HealthDetails().withValue("providerId", id)));
   }



   /*******************************************************************************
    ** Reuse the minimal connection call and its accounting, without lastTest writes.
    *******************************************************************************/
   private static HealthCheckOutcome model(AssistantRuntime runtime, Integer id, HealthCheckInput input) throws QException
   {
      AssistantModel row = runtime.getStore().listModels().stream().filter(model -> id.equals(model.getId())).findFirst().orElse(null);
      if(row == null || !Boolean.TRUE.equals(row.getEnabled()))
      {
         return (HealthCheckOutcome.unknown("Model disabled or unavailable"));
      }
      AssistantProvider provider = runtime.getStore().getProvider(row.getProviderId());
      if(provider == null || !Boolean.TRUE.equals(provider.getEnabled()))
      {
         return (HealthCheckOutcome.unknown("Provider disabled or unavailable"));
      }
      if(provider.isSettingUp())
      {
         return (HealthCheckOutcome.up(NOT_SET_UP).withDetails(new HealthDetails().withValue("modelId", id).withValue("providerId", provider.getId())));
      }
      Long cap = ProviderService.capMicros(provider);
      if(cap != null && runtime.getProviderService().monthToDateCostMicros(provider.getId(), true) >= cap)
      {
         return (HealthCheckOutcome.unknown("skipped: cost cap"));
      }
      if(runtime.getPolicyService().current(true).killSwitch())
      {
         return (HealthCheckOutcome.unknown("skipped: kill switch"));
      }
      AssistantSecurity.ownerKey(QContext.getQSession(), runtime.getConfig());
      ConnectionTest.Result result = ConnectionTest.checkModel(runtime, row, input.getDeadline());
      return ((result.ok() ? HealthCheckOutcome.up("Model connection succeeded") : HealthCheckOutcome.down("Model connection failed"))
         .withDetails(new HealthDetails().withValue("modelId", id).withValue("providerId", provider.getId()))
         .withLatencyMs(result.latencyMs() == null ? null : result.latencyMs().longValue()));
   }



   /*******************************************************************************
    ** A failed provider (or model) check is DOWN only when the provider matters:
    ** it is the default, or the only route for a configured profile model, budget
    ** downgrade or knowledge source. Any other enabled provider is optional, so its
    ** failure is DEGRADED with a message that says where requests go instead.
    ** Provider failures also carry reachable=false, so the circuit breaker still
    ** refuses the provider. If the routes cannot be read, the outcome stays DOWN.
    *******************************************************************************/
   static HealthCheckOutcome optional(AssistantRuntime runtime, Integer providerId, HealthCheckOutcome outcome, boolean model)
   {
      if(outcome == null || outcome.getStatus() != HealthStatus.DOWN)
      {
         return (outcome);
      }
      HealthDetails details = outcome.getDetails() == null ? new HealthDetails() : outcome.getDetails();
      outcome.withDetails(details);
      if(!model)
      {
         details.withValue(ProviderHealth.DETAIL_REACHABLE, false);
      }
      try
      {
         AssistantProvider row = providerId == null ? null : runtime.getStore().getProvider(providerId);
         if(row == null || !row.isUsable())
         {
            return (outcome);
         }
         String required = requiredBecause(runtime, row);
         if(required != null)
         {
            details.withValue("required", required);
            return (outcome);
         }
         AssistantProvider active  = runtime.getProviderService().activeRow();
         String            instead = active == null ? "the other providers" : "'" + active.getName() + "'";
         String            what    = model ? "Model connection failed; provider '" + row.getName() + "' is optional" : "Provider '" + row.getName() + "' is unreachable; it is optional";
         details.withValue("optional", true);
         return (outcome.withStatus(HealthStatus.DEGRADED).withMessage(what + " (not the default and not the only route), so requests use " + instead));
      }
      catch(Exception e)
      {
         LOG.warn("Could not tell whether an assistant provider is optional; reporting it as down", e);
         return (outcome);
      }
   }



   /*******************************************************************************
    ** Why a provider is required (null when it is optional): it is the provider
    ** the assistant uses by default, or one of its enabled models is assigned to
    ** a routing profile, a budget downgrade or a knowledge source and that model
    ** has no fallback on another usable provider.
    *******************************************************************************/
   static String requiredBecause(AssistantRuntime runtime, AssistantProvider row) throws QException
   {
      AssistantProvider active = runtime.getProviderService().activeRow();
      if(active == null || Objects.equals(active.getId(), row.getId()))
      {
         return ("default provider");
      }

      Map<Integer, AssistantModel>    models    = runtime.getStore().listModels().stream().collect(Collectors.toMap(AssistantModel::getId, Function.identity(), (a, b) -> a));
      Map<Integer, AssistantProvider> providers = runtime.getStore().listProviders().stream().collect(Collectors.toMap(AssistantProvider::getId, Function.identity(), (a, b) -> a));
      Set<Integer>                    routed    = new HashSet<>();
      for(AssistantProfile profile : runtime.getStore().query(AssistantProfile.TABLE_NAME, new QQueryFilter(), AssistantProfile.class))
      {
         if(!Boolean.FALSE.equals(profile.getEnabled()) && profile.getRoutingMode() != null && !"OFF".equals(profile.getRoutingMode()))
         {
            routed.add(profile.getSimpleModelId());
            routed.add(profile.getComplexModelId());
         }
      }
      for(AssistantBudget budget : runtime.getStore().budgets())
      {
         routed.add(budget.getDowngradeModelId());
      }
      for(Integer id : routed)
      {
         AssistantModel model = id == null ? null : models.get(id);
         if(onProvider(model, row) && !hasFallback(runtime, model, row, models, providers))
         {
            return ("only route for a configured model");
         }
      }
      for(AssistantRagSource source : runtime.getStore().query(AssistantRagSource.TABLE_NAME, new QQueryFilter(), AssistantRagSource.class))
      {
         if(!Boolean.FALSE.equals(source.getEnabled()) && source.getEmbeddingModelId() != null && onProvider(models.get(source.getEmbeddingModelId()), row))
         {
            return ("only route for a knowledge source");
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** An enabled model of this provider.
    *******************************************************************************/
   private static boolean onProvider(AssistantModel model, AssistantProvider row)
   {
      return (model != null && Boolean.TRUE.equals(model.getEnabled()) && Objects.equals(model.getProviderId(), row.getId()));
   }



   /*******************************************************************************
    ** The fallback FallbackChain would try: the explicit chain when the model has
    ** one, else (local providers only) another local provider of the same type.
    ** Only enabled models of other usable providers count.
    *******************************************************************************/
   private static boolean hasFallback(AssistantRuntime runtime, AssistantModel model, AssistantProvider row, Map<Integer, AssistantModel> models, Map<Integer, AssistantProvider> providers)
   {
      if(model.getFallbackModelId() != null)
      {
         Set<Integer> seen = new HashSet<>();
         Integer      next = model.getFallbackModelId();
         while(next != null && seen.add(next))
         {
            AssistantModel candidate = models.get(next);
            if(candidate == null)
            {
               return (false);
            }
            AssistantProvider owner = providers.get(candidate.getProviderId());
            if(Boolean.TRUE.equals(candidate.getEnabled()) && owner != null && owner.isUsable() && !Objects.equals(owner.getId(), row.getId()))
            {
               return (true);
            }
            next = candidate.getFallbackModelId();
         }
         return (false);
      }
      if(!"LOCAL".equals(runtime.getProviderService().location(row)))
      {
         return (false);
      }
      return (providers.values().stream().anyMatch(other -> other.isUsable() && !Objects.equals(other.getId(), row.getId())
         && "LOCAL".equals(runtime.getProviderService().location(other)) && Objects.equals(other.getProviderType(), row.getProviderType())
         && models.values().stream().anyMatch(candidate -> Boolean.TRUE.equals(candidate.getEnabled()) && Objects.equals(candidate.getProviderId(), other.getId()))));
   }
}
