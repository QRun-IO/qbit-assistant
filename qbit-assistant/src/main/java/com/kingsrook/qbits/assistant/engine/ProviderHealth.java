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


import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.spi.HealthResult;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ProviderSettings;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Persisted sixty-second circuit breaker; only local endpoints are probed.
 *******************************************************************************/
public class ProviderHealth
{
   private final AssistantRuntime runtime;



   /*******************************************************************************
    ** Use the runtime clock for all cache and breaker boundaries.
    *******************************************************************************/
   public ProviderHealth(AssistantRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Refuse an open circuit; probe unknown or stale local endpoints within two seconds.
    *******************************************************************************/
   public void beforeCall(ProviderService.Active active) throws QException, ModelProviderException, InterruptedException
   {
      if(active.providerId() == null)
      {
         return;
      }
      AssistantProvider row = runtime.getStore().getProvider(active.providerId());
      if(row == null || !Boolean.TRUE.equals(row.getEnabled()))
      {
         throw (new ModelProviderException(ModelProviderException.Kind.AUTHENTICATION, null, "Provider is disabled."));
      }
      if(isDown(row))
      {
         throw (new ModelProviderException(ModelProviderException.Kind.OVERLOADED, null, "Provider circuit is open."));
      }
      Instant now = runtime.getClock().instant();
      if(!"LOCAL".equals(runtime.getProviderService().location(row)) || (row.getHealthCheckedAt() != null && row.getHealthCheckedAt().plusSeconds(60).isAfter(now)))
      {
         return;
      }
      ProviderSettings settings = runtime.getProviderService().settings(row).withConnectTimeoutSeconds(2).withRequestTimeoutSeconds(2);
      FutureTask<HealthResult> probe = new FutureTask<>(() -> runtime.getConfig().getProviderTypes().get(row.getProviderType()).checkHealth(settings));
      Thread.ofVirtual().name("assistant-health").start(probe);
      try
      {
         HealthResult result = probe.get(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS);
         if(Boolean.FALSE.equals(result.healthy()))
         {
            throw (new ModelProviderException(ModelProviderException.Kind.NETWORK, null, "Provider health check failed."));
         }
         save(row.getId(), Boolean.TRUE.equals(result.healthy()) ? "UP" : null, null);
      }
      catch(java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e)
      {
         throw (new ModelProviderException(ModelProviderException.Kind.TIMEOUT, null, "Provider health check did not complete."));
      }
      finally
      {
         probe.cancel(true);
      }
   }



   /*******************************************************************************
    ** A future downUntil alone keeps the circuit open; expiry permits recovery.
    *******************************************************************************/
   public Boolean isDown(AssistantProvider row)
   {
      return (row != null && row.getDownUntil() != null && row.getDownUntil().isAfter(runtime.getClock().instant()));
   }



   /*******************************************************************************
    ** Connection errors open the breaker; model errors do not poison the endpoint.
    *******************************************************************************/
   public void failed(ProviderService.Active active, ModelProviderException error) throws QException
   {
      if(active.providerId() != null && (error.getKind() == ModelProviderException.Kind.NETWORK || error.getKind() == ModelProviderException.Kind.TIMEOUT))
      {
         save(active.providerId(), "DOWN", runtime.getClock().instant().plusSeconds(60));
      }
   }



   /*******************************************************************************
    ** A successful completion closes the breaker.
    *******************************************************************************/
   public void succeeded(ProviderService.Active active) throws QException
   {
      if(active.providerId() != null)
      {
         save(active.providerId(), "UP", null);
      }
   }



   /*******************************************************************************
    ** Only health columns are changed, under the requesting session.
    *******************************************************************************/
   private void save(Integer providerId, String state, Instant downUntil) throws QException
   {
      runtime.getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", providerId)
         .withValue("healthState", state).withValue("healthCheckedAt", runtime.getClock().instant()).withValue("downUntil", downUntil));
      runtime.getProviderService().invalidate();
   }
}
