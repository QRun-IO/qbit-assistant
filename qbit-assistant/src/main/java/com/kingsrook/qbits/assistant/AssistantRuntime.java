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
import java.util.regex.Pattern;
import com.kingsrook.qbits.assistant.access.AccessExplainer;
import com.kingsrook.qbits.assistant.access.DataAccessService;
import com.kingsrook.qbits.assistant.engine.BudgetService;
import com.kingsrook.qbits.assistant.engine.ContextBuilder;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.engine.ProfileService;
import com.kingsrook.qbits.assistant.engine.ProviderService;
import com.kingsrook.qbits.assistant.engine.RateLimiter;
import com.kingsrook.qbits.assistant.engine.RunningTurn;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.rag.InMemoryVectorStore;
import com.kingsrook.qbits.assistant.rag.RagService;
import com.kingsrook.qbits.assistant.rag.VectorStore;
import com.kingsrook.qbits.assistant.tools.KnowledgeTool;
import com.kingsrook.qbits.assistant.tools.bulkload.BulkLoadTools;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qbits.assistant.uiapi.CoreUiApiGateway;
import com.kingsrook.qbits.assistant.uiapi.UiApiGateway;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolKernel;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolKernelSettings;
import com.kingsrook.qqq.backend.core.actions.ai.exposure.AiExposurePolicy;
import com.kingsrook.qqq.backend.core.actions.ai.output.AiRedactor;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Runtime state of the QBit in one JVM: the tool kernel, the context
 ** builder, policy cache, rate limiter, the turns running here, and the
 ** thread pool (virtual threads) that runs turns and tool calls.
 *******************************************************************************/
public class AssistantRuntime
{
   private final RagService ragService;
   private final KnowledgeTool knowledgeTool;
   private final BulkLoadTools bulkLoadTools;

   private final QInstance                        qInstance;
   private final AssistantQBitConfig              config;
   private final Clock                            clock;
   private final AssistantStore                   store;
   private final PolicyService                    policyService;
   private final ProviderService                  providerService;
   private final BudgetService                    budgetService;
   private final RateLimiter                      rateLimiter;
   private final AiToolKernel                     kernel;
   private final ContextBuilder                   contextBuilder;
   private final DataAccessService                dataAccessService;
   private final ProfileService                   profileService;
   private final AccessExplainer                  accessExplainer;
   private final UiApiToolbox                     toolbox;
   private final UiApiGateway                     uiApiGateway;
   private volatile String                        localBaseUrl;
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
      this.store = new AssistantStore(config);
      this.policyService = new PolicyService(config, store, clock);
      this.providerService = new ProviderService(config, store, clock);
      this.budgetService = new BudgetService(store, providerService, clock);
      this.policyService.withBudgetService(budgetService, providerService);
      this.rateLimiter = new RateLimiter(clock);
      AiToolKernelSettings settings = kernelSettings(config);
      this.kernel = new AiToolKernel(qInstance, settings);
      AiExposurePolicy exposurePolicy = new AiExposurePolicy(settings);
      this.dataAccessService = new DataAccessService(qInstance, config, store, exposurePolicy, clock);
      this.profileService = new ProfileService(config, store, clock);
      this.accessExplainer = new AccessExplainer(qInstance, dataAccessService);
      this.toolbox = new UiApiToolbox(qInstance, dataAccessService, exposurePolicy, new AiRedactor(settings.getCustomRedactionPatterns()),
         config.getMaxCellChars() == null ? AiToolKernelSettings.DEFAULT_MAX_CELL_CHARS : config.getMaxCellChars());
      this.uiApiGateway = config.getUiApiGateway() != null ? config.getUiApiGateway() : new CoreUiApiGateway(this::uiApiBaseUrl, qInstance);
      VectorStore vectors = config.getVectorStore() == null ? new InMemoryVectorStore() : config.getVectorStore();
      this.ragService = new RagService(this, vectors);
      this.knowledgeTool = new KnowledgeTool(this, ragService, vectors);
      this.bulkLoadTools = new BulkLoadTools(qInstance, dataAccessService, config.getBulkLoadRunner());
      this.contextBuilder = new ContextBuilder(config, kernel, toolbox).withKnowledgeTool(knowledgeTool).withBulkLoadTools(bulkLoadTools);
   }



   /*******************************************************************************
    ** The server's own root URL for UI API loopback calls: the configured
    ** uiApiBaseUrl, else the address the first assistant request arrived on
    ** (127.0.0.1 and this server's local port).
    *******************************************************************************/
   public String uiApiBaseUrl()
   {
      return (StringUtils.hasContent(config.getUiApiBaseUrl()) ? config.getUiApiBaseUrl() : localBaseUrl);
   }



   /*******************************************************************************
    ** Remember this server's local port (from a request) for loopback calls.
    *******************************************************************************/
   public void learnLocalPort(int port)
   {
      if(port > 0 && localBaseUrl == null)
      {
         localBaseUrl = "http://127.0.0.1:" + port;
      }
   }



   /*******************************************************************************
    ** Getter for dataAccessService
    *******************************************************************************/
   public DataAccessService getDataAccessService()
   {
      return (dataAccessService);
   }



   /*******************************************************************************
    ** Getter for accessExplainer
    *******************************************************************************/
   public AccessExplainer getAccessExplainer()
   {
      return (accessExplainer);
   }



   /*******************************************************************************
    ** Getter for profileService (personas and skills: text only, never tools)
    *******************************************************************************/
   public ProfileService getProfileService()
   {
      return (profileService);
   }



   /*******************************************************************************
    ** The app a page context is in: the deepest /app/... route segment that is
    ** an app of this instance (null when none).
    *******************************************************************************/
   public String appNameFor(Map<String, Object> pageContext)
   {
      Object route = pageContext == null ? null : pageContext.get("route");
      String found = null;
      if(route instanceof String path)
      {
         for(String segment : path.split("[/?#]"))
         {
            if(StringUtils.hasContent(segment) && qInstance.getApp(segment) != null)
            {
               found = segment;
            }
         }
      }
      return (found);
   }



   /*******************************************************************************
    ** Getter for toolbox (the UI API data tools)
    *******************************************************************************/
   public UiApiToolbox getToolbox()
   {
      return (toolbox);
   }



   /*******************************************************************************
    ** Getter for uiApiGateway
    *******************************************************************************/
   public UiApiGateway getUiApiGateway()
   {
      return (uiApiGateway);
   }



   /*******************************************************************************
    ** The kernel settings from the QBit config: caps, default exposure, the
    ** widgets the assistant may render (default: none - the kernel denies
    ** widgets unless allowed), widgets it may never render, and extra
    ** redaction patterns.
    *******************************************************************************/
   public static AiToolKernelSettings kernelSettings(AssistantQBitConfig config)
   {
      AiToolKernelSettings settings = new AiToolKernelSettings()
         .withMaxRowsToModel(config.getMaxRowsToModel())
         .withMaxCellChars(config.getMaxCellChars())
         .withDefaultExposure(config.getDefaultExposure());
      if(config.getAllowedWidgetNames() != null)
      {
         config.getAllowedWidgetNames().forEach(settings::withAllowedWidgetName);
      }
      if(config.getHiddenWidgetNames() != null)
      {
         config.getHiddenWidgetNames().forEach(settings::withHiddenWidgetName);
      }
      if(config.getCustomRedactionPatterns() != null)
      {
         config.getCustomRedactionPatterns().forEach(p -> settings.withCustomRedactionPattern(Pattern.compile(p)));
      }
      return (settings);
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
    ** Cancel every turn running here (e.g. the kill switch flipped).
    *******************************************************************************/
   public void cancelAll(String reason)
   {
      runningTurns.values().forEach(t -> t.cancel(reason));
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



   /*******************************************************************************
    ** Getter for providerService
    *******************************************************************************/
   public ProviderService getProviderService()
   {
      return (providerService);
   }


   /*******************************************************************************
    ** Data-driven budget enforcement.
    *******************************************************************************/
   public BudgetService getBudgetService()
   {
      return (budgetService);
   }


   /*******************************************************************************
    ** Manual indexing service, always using the current caller's session.
    *******************************************************************************/
   public RagService getRagService()
   {
      return (ragService);
   }



   /*******************************************************************************
    ** Permission-filtered retrieval tool.
    *******************************************************************************/
   public KnowledgeTool getKnowledgeTool()
   {
      return (knowledgeTool);
   }



   /*******************************************************************************
    ** The bulk-load tools (saved profiles, attached files, dry runs, loads).
    *******************************************************************************/
   public BulkLoadTools getBulkLoadTools()
   {
      return (bulkLoadTools);
   }

}
