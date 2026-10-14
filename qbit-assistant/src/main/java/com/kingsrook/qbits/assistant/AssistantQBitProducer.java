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


import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import com.kingsrook.qbits.assistant.api.AssistantRoutes;
import com.kingsrook.qbits.assistant.developer.AssistantDeveloperPanels;
import com.kingsrook.qbits.assistant.home.AssistantAccessWidget;
import com.kingsrook.qbits.assistant.home.AssistantHome;
import com.kingsrook.qbits.assistant.metadata.AssistantChannelWall;
import com.kingsrook.qbits.assistant.metadata.AssistantHelp;
import com.kingsrook.qbits.assistant.metadata.AssistantInstanceMetaData;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.metadata.AssistantRecordScreens;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.processes.DataAccessDefaultsProcess;
import com.kingsrook.qbits.assistant.processes.DiscoverModelsProcess;
import com.kingsrook.qbits.assistant.processes.KnowledgeIndexProcess;
import com.kingsrook.qbits.assistant.processes.ScheduledTaskProcess;
import com.kingsrook.qbits.assistant.processes.TestConnectionProcess;
import com.kingsrook.qbits.assistant.processes.TestModelConnectionProcess;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.ClientChannelsMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Entry point of the Assistant QBit (QQQ 4.1 QBitMetaDataProducer).
 **
 ** produce() validates the config, registers the QBitMetaData
 ** (com.kingsrook.qbits:assistant), adds the security key type (unless the
 ** host defines it), the tables, the conversation PVS, the retention
 ** process and the Assistant Admin app, then builds the runtime (tool
 ** kernel, policy, rate limits) and registers the /qqq/v1/assistant routes
 ** when Javalin is on the classpath.
 **
 ** Host usage:
 **   new AssistantQBitProducer().withQBitConfig(new AssistantQBitConfig()
 **      .withBackendName("rdbms")
 **      .withModelProvider(AnthropicModelProvider.fromEnvironment()))
 **      .produce(qInstance);
 *******************************************************************************/
public class AssistantQBitProducer implements QBitMetaDataProducer<AssistantQBitConfig>
{
   private static final QLogger LOG = QLogger.getLogger(AssistantQBitProducer.class);

   public static final String GROUP_ID    = "com.kingsrook.qbits";
   public static final String ARTIFACT_ID = "assistant";

   private static final String JAVALIN_CLASS = "com.kingsrook.qqq.middleware.javalin.QJavalinMetaData";
   private static final String VERSION       = loadVersion();

   private AssistantQBitConfig qBitConfig;



   /*******************************************************************************
    ** Fluent setter for the config.
    *******************************************************************************/
   public AssistantQBitProducer withQBitConfig(AssistantQBitConfig config)
   {
      this.qBitConfig = config;
      return (this);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public AssistantQBitConfig getQBitConfig()
   {
      return (qBitConfig);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QBitMetaData getQBitMetaData()
   {
      return (new QBitMetaData()
         .withGroupId(GROUP_ID)
         .withArtifactId(ARTIFACT_ID)
         .withVersion(VERSION)
         .withConfig(getQBitConfig()));
   }



   /*******************************************************************************
    ** A producer found by package scanning has no config; skip it.
    *******************************************************************************/
   @Override
   public boolean isEnabled()
   {
      return (qBitConfig != null);
   }



   /*******************************************************************************
    ** Produce and add everything to the instance. Returns an empty output so a
    ** host that also calls addSelfToInstance on the result adds nothing twice.
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      if(qBitConfig == null)
      {
         throw (new QException("Assistant QBit configuration validation failed: no config was supplied (call withQBitConfig)"));
      }

      List<String> errors = new ArrayList<>();
      qBitConfig.validate(qInstance, errors);
      if(!errors.isEmpty())
      {
         throw (new QException("Assistant QBit configuration validation failed: " + String.join("; ", errors)));
      }

      QBitMetaData qBitMetaData = getQBitMetaData();
      qInstance.addQBit(qBitMetaData);

      AssistantMetaDataBuilder builder = new AssistantMetaDataBuilder(qBitConfig);
      if(qInstance.getSecurityKeyType(qBitConfig.getOwnerSecurityKeyType()) == null)
      {
         qInstance.addSecurityKeyType(builder.buildSecurityKeyType());
      }

      List<QTableMetaData> tables = new ArrayList<>();
      for(QTableMetaData table : builder.buildTables())
      {
         QTableMetaData customized = qBitConfig.getTableMetaDataCustomizer() == null ? table : qBitConfig.getTableMetaDataCustomizer().customizeMetaData(qInstance, table);
         customized.setSourceQBitName(qBitMetaData.getName());
         qInstance.addTable(customized);
         tables.add(customized);
      }
      qInstance.add(budgetAlertType("warning", "Assistant budget warning", "Assistant usage has reached the budget warning threshold.", AlertSeverity.WARNING));
      qInstance.add(budgetAlertType("downgraded", "Assistant budget downgraded", "Assistant usage has reached the budget limit and switched to a cheaper model.", AlertSeverity.WARNING));
      qInstance.add(budgetAlertType("stopped", "Assistant budget stopped", "Assistant dispatch has stopped because a budget limit was reached.", AlertSeverity.ERROR));
      qInstance.addPossibleValueSource(builder.buildConversationPossibleValueSource());
      AssistantRecordScreens.buildPossibleValueSources().forEach(qInstance::addPossibleValueSource);
      AssistantRecordScreens.buildSettingsPossibleValueSources(qBitConfig).forEach(qInstance::addPossibleValueSource);
      AssistantRecordScreens.addConversationChildLists(qInstance);

      QProcessMetaData retention = builder.buildRetentionProcess();
      retention.setSourceQBitName(qBitMetaData.getName());
      qInstance.addProcess(retention);

      QProcessMetaData testConnection = TestConnectionProcess.process();
      testConnection.setSourceQBitName(qBitMetaData.getName());
      qInstance.addProcess(testConnection);
      for(QProcessMetaData process : List.of(DiscoverModelsProcess.process(), TestModelConnectionProcess.process(), DataAccessDefaultsProcess.process()))
      {
         process.setSourceQBitName(qBitMetaData.getName());
         qInstance.addProcess(process);
      }

      for(QProcessMetaData process : KnowledgeIndexProcess.processes())
      {
         process.setSourceQBitName(qBitMetaData.getName());
         qInstance.addProcess(process);
      }

      for(QProcessMetaData process : ScheduledTaskProcess.processes(qBitConfig))
      {
         process.setSourceQBitName(qBitMetaData.getName());
         qInstance.addProcess(process);
      }
      qInstance.addWidget(AssistantHome.widget());
      qInstance.addWidget(AssistantAccessWidget.widget());

      QAppMetaData app = builder.buildAdminApp(tables);
      qInstance.addApp(app);
      AssistantHelp.apply(qInstance);

      AssistantChannelWall.register(qInstance);
      if(Boolean.TRUE.equals(qBitConfig.getRecordHistory()))
      {
         enableRecordHistory(ClientChannelsMetaData.ofOrWithNew(qInstance));
      }
      else
      {
         ClientChannelsMetaData.ofOrWithNew(qInstance).withDeniedPattern("assistant", "audit*");
      }
      postProduceActions(new MetaDataProducerMultiOutput(), qInstance);
      return (new MetaDataProducerMultiOutput());
   }



   /*******************************************************************************
    ** STAND-IN(core=QRun-IO/qqq#1193, removeBy=RC.8): call withAssistantAuditReads() directly once the qbit builds on a QQQ RC that has it
    *******************************************************************************/
   private void enableRecordHistory(ClientChannelsMetaData channels)
   {
      try
      {
         channels.getClass().getMethod("withAssistantAuditReads").invoke(channels);
      }
      catch(NoSuchMethodException e)
      {
         List<String> denied = channels.getDeniedPatterns().get("assistant");
         if(denied != null)
         {
            denied.removeIf("audit*"::equals);
         }
      }
      catch(ReflectiveOperationException e)
      {
         throw (new IllegalStateException("Could not enable assistant audit reads.", e));
      }
   }



   /*******************************************************************************
    ** Declare budget alerts as top-level core metadata for admin visibility.
    *******************************************************************************/
   private QAlertTypeMetaData budgetAlertType(String state, String label, String description, AlertSeverity severity)
   {
      return (new QAlertTypeMetaData().withName("assistant.budget." + state).withLabel(label).withDescription(description)
         .withDefaultSeverity(severity).withHowToFix("Raise the budget or grant an override in Assistant → Budgets")
         .withSourceQBitName(ARTIFACT_ID).withVisibilityPermission("assistant.admin").withSubjectTableName(AssistantBudget.TABLE_NAME));
   }



   /*******************************************************************************
    ** Build the runtime and register the routes.
    *******************************************************************************/
   @Override
   public void postProduceActions(MetaDataProducerMultiOutput output, QInstance qInstance) throws QException
   {
      AssistantRuntime previous = qBitConfig.getRuntime();
      if(previous != null)
      {
         previous.close();
      }
      qBitConfig.setRuntime(new AssistantRuntime(qInstance, qBitConfig));

      boolean routes = false;
      if(Boolean.TRUE.equals(qBitConfig.getRegisterRoutes()) && isJavalinAvailable())
      {
         AssistantRoutes.register(qInstance);
         routes = true;
      }

      if(qBitConfig.isEffectivelyEnabled())
      {
         AssistantInstanceMetaData.register(qInstance);
      }

      boolean developerPanel = false;
      if(AssistantDeveloperPanels.isContractAvailable())
      {
         AssistantDeveloperPanels.register(qInstance);
         developerPanel = true;
      }

      LOG.info("Assistant QBit produced", logPair("version", VERSION), logPair("backendName", qBitConfig.getBackendName()), logPair("routes", routes), logPair("developerPanel", developerPanel),
         logPair("enabled", qBitConfig.isEffectivelyEnabled()), logPair("providerName", qBitConfig.getProviderName()),
         logPair("providerRegistered", qBitConfig.getActiveModelProvider() != null), logPair("providerTypes", qBitConfig.getProviderTypes().keySet()),logPair("mainModel", qBitConfig.getModels().getMainModel()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static boolean isJavalinAvailable()
   {
      try
      {
         Class.forName(JAVALIN_CLASS, false, AssistantQBitProducer.class.getClassLoader());
         return (true);
      }
      catch(ClassNotFoundException | LinkageError e)
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** The artifact version, from the Maven-filtered qbit-assistant.properties.
    *******************************************************************************/
   static String loadVersion()
   {
      try(InputStream inputStream = AssistantQBitProducer.class.getClassLoader().getResourceAsStream("qbit-assistant.properties"))
      {
         if(inputStream != null)
         {
            Properties properties = new Properties();
            properties.load(inputStream);
            String version = properties.getProperty("version");
            if(StringUtils.hasContent(version) && !version.contains("${"))
            {
               return (version);
            }
         }
      }
      catch(Exception e)
      {
         LOG.debug("Could not read qbit-assistant.properties", e);
      }
      return ("unknown");
   }



   /*******************************************************************************
    ** Getter for the artifact version.
    *******************************************************************************/
   public static String getVersion()
   {
      return (VERSION);
   }

}
