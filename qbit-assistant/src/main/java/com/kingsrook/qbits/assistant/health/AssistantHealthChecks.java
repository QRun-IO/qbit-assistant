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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.engine.ConnectionTest;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
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
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;


/*******************************************************************************
 ** Central health adapters reuse the existing probes without exposing their text.
 *******************************************************************************/
public final class AssistantHealthChecks
{
   public static final String CLIENT_ERRORS = "assistant.client-errors";

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
    ** One definition per enabled provider; no I/O to providers during resolution.
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
            if(Boolean.TRUE.equals(row.getEnabled()))
            {
               checks.add(definition("assistant.provider." + row.getId(), "LOCAL".equals(runtime.getProviderService().location(row)) ? 60 : 300, 10000,
                  input -> bounded(input, () -> provider(runtime, row.getId(), input))));
            }
         }
         return (checks);
      }
   }



   /*******************************************************************************
    ** One definition per enabled model, including models of disabled providers.
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
         for(AssistantModel row : runtime.getStore().listModels())
         {
            if(Boolean.TRUE.equals(row.getEnabled()))
            {
               checks.add(definition("assistant.model." + row.getId(), 21600, 30000,
                  input -> bounded(input, () -> model(runtime, row.getId(), input))));
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
}
