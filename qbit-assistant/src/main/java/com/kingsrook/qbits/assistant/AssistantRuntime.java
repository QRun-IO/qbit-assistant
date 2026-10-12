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


import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.kingsrook.qbits.assistant.engine.ContextBuilder;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.engine.RateLimiter;
import com.kingsrook.qbits.assistant.engine.RunningTurn;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolKernel;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolKernelSettings;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;


/*******************************************************************************
 ** Runtime state of the QBit in one JVM: the tool kernel, the context
 ** builder, policy cache, rate limiter, the turns running here, and the
 ** thread pool (virtual threads) that runs turns and tool calls.
 *******************************************************************************/
public class AssistantRuntime
{
   private final QInstance                        qInstance;
   private final AssistantQBitConfig              config;
   private final Clock                            clock;
   private final AssistantStore                   store;
   private final PolicyService                    policyService;
   private final RateLimiter                      rateLimiter;
   private final AiToolKernel                     kernel;
   private final ContextBuilder                   contextBuilder;
   private final ExecutorService                  executor     = Executors.newVirtualThreadPerTaskExecutor();
   private final Map<Integer, RunningTurn>        runningTurns = new ConcurrentHashMap<>();



   /*******************************************************************************
    **
    *******************************************************************************/
   public AssistantRuntime(QInstance qInstance, AssistantQBitConfig config)
   {
      this(qInstance, config, Clock.systemUTC());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public AssistantRuntime(QInstance qInstance, AssistantQBitConfig config, Clock clock)
   {
      this.qInstance = qInstance;
      this.config = config;
      this.clock = clock;
      this.store = new AssistantStore();
      this.policyService = new PolicyService(config, store, clock);
      this.rateLimiter = new RateLimiter(clock);
      this.kernel = new AiToolKernel(qInstance, new AiToolKernelSettings()
         .withMaxRowsToModel(config.getMaxRowsToModel())
         .withMaxCellChars(config.getMaxCellChars())
         .withDefaultExposure(config.getDefaultExposure()));
      this.contextBuilder = new ContextBuilder(config, kernel);
   }



   /*******************************************************************************
    ** The runtime of the instance's assistant QBit, or null.
    *******************************************************************************/
   public static AssistantRuntime of(QInstance qInstance)
   {
      if(qInstance == null || qInstance.getQBits() == null)
      {
         return (null);
      }
      for(QBitMetaData qBit : qInstance.getQBits().values())
      {
         if(qBit.getConfig() instanceof AssistantQBitConfig config && config.getRuntime() != null)
         {
            return (config.getRuntime());
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** Track a turn running here.
    *******************************************************************************/
   public RunningTurn register(RunningTurn turn)
   {
      runningTurns.put(turn.getTurnId(), turn);
      return (turn);
   }



   /*******************************************************************************
    ** Stop tracking a turn.
    *******************************************************************************/
   public void unregister(Integer turnId)
   {
      runningTurns.remove(turnId);
   }



   /*******************************************************************************
    ** A turn running here, or null.
    *******************************************************************************/
   public RunningTurn getRunningTurn(Integer turnId)
   {
      return (turnId == null ? null : runningTurns.get(turnId));
   }



   /*******************************************************************************
    ** Stop the thread pool (turns in flight are interrupted).
    *******************************************************************************/
   public void close()
   {
      runningTurns.values().forEach(t -> t.cancel("SHUTDOWN"));
      executor.shutdownNow();
   }



   /*******************************************************************************
    ** Getter for qInstance
    *******************************************************************************/
   public QInstance getQInstance()
   {
      return (qInstance);
   }



   /*******************************************************************************
    ** Getter for config
    *******************************************************************************/
   public AssistantQBitConfig getConfig()
   {
      return (config);
   }



   /*******************************************************************************
    ** Getter for clock
    *******************************************************************************/
   public Clock getClock()
   {
      return (clock);
   }



   /*******************************************************************************
    ** Getter for store
    *******************************************************************************/
   public AssistantStore getStore()
   {
      return (store);
   }



   /*******************************************************************************
    ** Getter for policyService
    *******************************************************************************/
   public PolicyService getPolicyService()
   {
      return (policyService);
   }



   /*******************************************************************************
    ** Getter for rateLimiter
    *******************************************************************************/
   public RateLimiter getRateLimiter()
   {
      return (rateLimiter);
   }



   /*******************************************************************************
    ** Getter for kernel
    *******************************************************************************/
   public AiToolKernel getKernel()
   {
      return (kernel);
   }



   /*******************************************************************************
    ** Getter for contextBuilder
    *******************************************************************************/
   public ContextBuilder getContextBuilder()
   {
      return (contextBuilder);
   }



   /*******************************************************************************
    ** Getter for executor
    *******************************************************************************/
   public ExecutorService getExecutor()
   {
      return (executor);
   }

}
