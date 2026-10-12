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

package com.kingsrook.qbits.assistant.api;


import java.util.Map;
import com.kingsrook.qbits.assistant.metadata.AssistantInstanceMetaData;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.actions.metadata.MetaDataAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.metadata.MetaDataInput;
import com.kingsrook.qqq.backend.core.model.actions.metadata.MetaDataOutput;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.middleware.javalin.specs.v1.responses.components.SupplementalInstanceMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The Next UI probes /assistant/config only when /metaData carries
 ** supplementalInstanceMetaData.assistant. The QBit registers it; the v1
 ** middleware publishes it through its allow-list (QRun-IO/qqq#1078).
 *******************************************************************************/
class AssistantMetaDataProbeTest extends BaseAssistantTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testMetaDataCarriesAssistant() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.EDITOR));
      try
      {
         MetaDataOutput output = new MetaDataAction().execute(new MetaDataInput());
         assertThat(output.getSupplementalInstanceMetaData()).containsKey(AssistantInstanceMetaData.NAME);

         Map<String, Object> published = SupplementalInstanceMetaData.of(output).getAssistant();
         assertThat(published).containsEntry("available", true).containsEntry("version", 1).containsEntry("basePath", "/qqq/v1/assistant");
         assertThat(JsonUtils.toJson(SupplementalInstanceMetaData.of(output))).contains("\"assistant\"").doesNotContain("apiKey");
      }
      finally
      {
         QContext.clear();
      }
   }

}
