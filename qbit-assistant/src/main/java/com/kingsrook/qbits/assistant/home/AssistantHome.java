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

package com.kingsrook.qbits.assistant.home;


import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;


/*******************************************************************************
 ** The Assistant app home: one full-width HTML widget, the single owner of the
 ** assistant's configuration, usage and cost data. Health lives in System Health.
 *******************************************************************************/
public final class AssistantHome
{
   public static final String WIDGET_NAME = "assistantHome";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AssistantHome()
   {
   }



   /*******************************************************************************
    ** The widget.
    *******************************************************************************/
   public static QWidgetMetaData widget()
   {
      return (new QWidgetMetaData()
         .withName(WIDGET_NAME)
         .withLabel("Overview")
         .withType(WidgetType.HTML.getType())
         .withCodeReference(new QCodeReference(Renderer.class))
         .withGridColumns(12)
         .withIsCard(false)
         .withPermissionRules(AssistantMetaDataBuilder.adminRules()));
   }



   /*******************************************************************************
    ** Renders the home for the viewing admin.
    *******************************************************************************/
   public static class Renderer extends AbstractWidgetRenderer
   {
      /*******************************************************************************
       ** Render.
       *******************************************************************************/
      @Override
      public RenderWidgetOutput render(RenderWidgetInput input) throws QException
      {
         AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
         if(runtime == null)
         {
            return (new RenderWidgetOutput(new RawHTML(null, "<p>The assistant is not running on this server.</p>")));
         }
         AssistantHomeData data = AssistantHomeData.load(runtime);
         return (new RenderWidgetOutput(new RawHTML(null, AssistantHomeView.render(data, runtime.getConfig().getTablePathPrefix()))));
      }
   }

}
