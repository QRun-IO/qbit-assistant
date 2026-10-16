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


import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.tools.FilterLinkBuilder;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.health.HealthChecksMetaData;
import com.kingsrook.qqq.backend.core.health.HealthRunner;
import com.kingsrook.qqq.backend.core.health.HealthStatus;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.StatisticsData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;


/*******************************************************************************
 ** A single generic status card links to the central checks, without probing.
 *******************************************************************************/
public class AssistantHealthWidget extends AbstractWidgetRenderer
{
   public static final String NAME = "assistantHealth";

   public static final String NOT_CHECKED = "Not checked yet";



   /*******************************************************************************
    ** Human words for a health status; unknown means no check has reported yet.
    *******************************************************************************/
   static String wording(HealthStatus status)
   {
      return (switch(status)
      {
         case UP -> "Healthy";
         case DEGRADED -> "Degraded";
         case DOWN -> "Down";
         case UNKNOWN -> NOT_CHECKED;
      });
   }



   /*******************************************************************************
    ** No HTML, history or per-provider check list on the Assistant home.
    *******************************************************************************/
   public static QWidgetMetaData widget()
   {
      return (new QWidgetMetaData().withName(NAME).withLabel("Assistant health")
         .withType(WidgetType.STATISTICS.getType()).withCodeReference(new QCodeReference(AssistantHealthWidget.class))
         .withGridColumns(12).withPermissionRules(AssistantMetaDataBuilder.adminRules()));
   }



   /*******************************************************************************
    ** Summarize the runner's latest results; full history lives in System Health.
    *******************************************************************************/
   @Override
   public RenderWidgetOutput render(RenderWidgetInput input) throws QException
   {
      var instance = QContext.getQInstance();
      HealthRunner runner = HealthRunner.getForInstance(instance);
      HealthStatus status = HealthStatus.UNKNOWN;
      if(runner != null)
      {
         status = HealthStatus.aggregate(HealthChecksMetaData.ofOrWithNew(instance).resolveAll(instance).stream()
            .filter(check -> "assistant".equals(check.getComponent()))
            .map(check -> runner.getLastOutcome(check.getName()))
            .map(outcome -> outcome == null ? HealthStatus.UNKNOWN : outcome.getStatus()).toArray(HealthStatus[]::new));
      }
      return (new RenderWidgetOutput(new StatisticsData().withCount(wording(status))
         .withCountContext("System Health. Provider checks run every 1 to 5 minutes; model checks every 6 hours")
         .withCountURL(FilterLinkBuilder.href("health_check", List.of(Map.of("fieldName", "component", "operator", "EQUALS", "values", List.of("assistant")))))));
   }
}
