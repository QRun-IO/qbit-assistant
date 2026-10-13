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
import com.kingsrook.qbits.assistant.home.AssistantHome;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.metadata.AssistantRecordScreens;
import com.kingsrook.qbits.assistant.processes.TestConnectionProcess;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
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

      qInstance.addWidget(AssistantHome.widget());

      QAppMetaData app = builder.buildAdminApp(tables);
      qInstance.addApp(app);

      postProduceActions(new MetaDataProducerMultiOutput(), qInstance);
      return (new MetaDataProducerMultiOutput());
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
         com.kingsrook.qbits.assistant.api.AssistantRoutes.register(qInstance);
         routes = true;
      }

      if(qBitConfig.isEffectivelyEnabled())
      {
         com.kingsrook.qbits.assistant.metadata.AssistantInstanceMetaData.register(qInstance);
      }

      boolean developerPanel = false;
      if(com.kingsrook.qbits.assistant.developer.AssistantDeveloperPanels.isContractAvailable())
      {
         com.kingsrook.qbits.assistant.developer.AssistantDeveloperPanels.register(qInstance);
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
