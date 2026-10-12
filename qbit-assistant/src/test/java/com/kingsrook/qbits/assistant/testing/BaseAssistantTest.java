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

package com.kingsrook.qbits.assistant.testing;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;


/*******************************************************************************
 ** Test base: the fixture app with the QBit and a scripted model provider.
 ** Subclasses script the provider, then run turns with `runner`.
 *******************************************************************************/
public abstract class BaseAssistantTest
{
   protected QInstance             qInstance;
   protected AssistantQBitConfig   config;
   protected ScriptedModelProvider provider;
   protected TurnRunner            runner;



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void baseSetUp() throws Exception
   {
      provider = new ScriptedModelProvider();
      config = new AssistantQBitConfig()
         .withProviderName(ScriptedModelProvider.DEFAULT_NAME)
         .withModelProvider(provider);
      customizeConfig(config);
      qInstance = AssistantTestFixture.build(config);
      runner = new TurnRunner(qInstance, config);
   }



   /*******************************************************************************
    ** Hook to change the config before production.
    *******************************************************************************/
   protected void customizeConfig(AssistantQBitConfig config)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void baseTearDown()
   {
      if(config.getRuntime() != null)
      {
         config.getRuntime().close();
      }
      QContext.clear();
   }



   /*******************************************************************************
    ** A filter input as the model writes it.
    *******************************************************************************/
   protected static Map<String, Object> filter(String booleanOperator, Object... criteria)
   {
      List<Object> list = new ArrayList<>();
      for(int i = 0; i + 2 < criteria.length + 1; i += 3)
      {
         Map<String, Object> criterion = new LinkedHashMap<>();
         criterion.put("field", criteria[i]);
         criterion.put("operator", criteria[i + 1]);
         criterion.put("values", criteria[i + 2]);
         list.add(criterion);
      }
      Map<String, Object> filter = new LinkedHashMap<>();
      filter.put("booleanOperator", booleanOperator);
      filter.put("criteria", list);
      return (filter);
   }



   /*******************************************************************************
    ** An input map from key / value pairs.
    *******************************************************************************/
   protected static Map<String, Object> input(Object... keyValues)
   {
      Map<String, Object> map = new LinkedHashMap<>();
      for(int i = 0; i + 1 < keyValues.length; i += 2)
      {
         map.put((String) keyValues[i], keyValues[i + 1]);
      }
      return (map);
   }

}
