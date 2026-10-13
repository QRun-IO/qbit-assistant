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


import java.util.List;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.access.AccessExplainer;
import com.kingsrook.qbits.assistant.access.DataAccessMode;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** "Explain access" on the Assistant home: for the viewing user, each table's
 ** assistant data access, the user's own UI permissions (from GET
 ** /qqq/v1/metaData made with their session) and which tool operations the
 ** assistant could call or would be denied, with the reason. Optional widget
 ** inputs `table` and `prompt` narrow it. Flow layout only (no tables, no
 ** inner scroll), inherited colors for light and dark.
 *******************************************************************************/
public final class AssistantAccessWidget
{
   public static final String WIDGET_NAME = "assistantAccess";

   private static final String ROOT  = "display:grid;gap:.75rem;min-width:0;";
   private static final String CARD  = "border:1px solid rgba(127,127,127,.3);border-radius:8px;padding:.75rem 1rem;min-width:0;overflow-wrap:anywhere;";
   private static final String CHIPS = "display:flex;flex-wrap:wrap;gap:.35rem;margin:.5rem 0 0;padding:0;list-style:none;";
   private static final String CHIP  = "display:inline-flex;align-items:center;gap:.35rem;padding:.05rem .6rem;border-radius:999px;border:1px solid rgba(127,127,127,.4);font-size:.8rem;";
   private static final String MUTED = "opacity:.75;font-size:.85rem;";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AssistantAccessWidget()
   {
   }



   /*******************************************************************************
    ** The widget.
    *******************************************************************************/
   public static QWidgetMetaData widget()
   {
      return (new QWidgetMetaData()
         .withName(WIDGET_NAME)
         .withLabel("Data access: what the assistant can do for you")
         .withType(WidgetType.HTML.getType())
         .withCodeReference(new QCodeReference(Renderer.class))
         .withGridColumns(12)
         .withIsCard(true)
         .withPermissionRules(AssistantMetaDataBuilder.adminRules()));
   }



   /*******************************************************************************
    ** HTML for an explanation.
    *******************************************************************************/
   public static String render(List<AccessExplainer.TableAccess> tables, String userLabel, String prefix)
   {
      StringBuilder html = new StringBuilder("<div style=\"").append(ROOT).append("\">");
      html.append("<p style=\"").append(MUTED).append("margin:0;\">Computed for ").append(AssistantHomeView.esc(userLabel))
         .append(" from the UI API (GET /qqq/v1/metaData with your session) and the assistant's data access settings. Every call is still checked by the UI API itself.</p>");
      if(tables.isEmpty())
      {
         html.append("<p style=\"").append(MUTED).append("\">No table matches.</p>");
      }
      for(AccessExplainer.TableAccess table : tables)
      {
         long allowed = table.operations().stream().filter(AccessExplainer.OperationCheck::allowed).count();
         if(!table.uiVisible() && table.mode() == DataAccessMode.NONE)
         {
            continue;
         }
         html.append("<section style=\"").append(CARD).append("\">");
         html.append("<div style=\"display:flex;flex-wrap:wrap;justify-content:space-between;gap:.25rem 1rem;\"><strong>").append(AssistantHomeView.esc(table.label()))
            .append(" <span style=\"").append(MUTED).append("\">").append(AssistantHomeView.esc(table.table())).append("</span></strong>")
            .append("<span>Assistant access: <strong>").append(table.mode().getLabel()).append("</strong> <span style=\"").append(MUTED).append("\">(")
            .append(AssistantHomeView.esc(table.modeSource())).append(")</span></span></div>");
         html.append("<p style=\"").append(MUTED).append("margin:.35rem 0 0;\">Your UI permissions: ")
            .append(table.uiVisible() ? (yes(table.uiRead(), "read") + ", " + yes(table.uiInsert(), "create") + ", " + yes(table.uiEdit(), "edit") + ", " + yes(table.uiDelete(), "delete")) : "not visible to you")
            .append(". ").append(allowed).append(" of ").append(table.operations().size()).append(" tools allowed.</p>");
         html.append("<ul style=\"").append(CHIPS).append("\">");
         for(AccessExplainer.OperationCheck check : table.operations())
         {
            html.append("<li style=\"").append(CHIP).append("\" title=\"").append(AssistantHomeView.esc(check.operation() + " · " + check.reason())).append("\">")
               .append("<span aria-hidden=\"true\" style=\"color:").append(check.allowed() ? "#2e9d5b" : "#d0453a").append(";\">").append(check.allowed() ? "&#10003;" : "&#10005;").append("</span>")
               .append(AssistantHomeView.esc(check.tool())).append("<span class=\"sr-only\" style=\"position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);\">: ")
               .append(AssistantHomeView.esc(check.reason())).append("</span></li>");
         }
         html.append("</ul>");
         String denied = table.operations().stream().filter(c -> !c.allowed()).map(AccessExplainer.OperationCheck::reason).distinct().findFirst().orElse(null);
         if(denied != null)
         {
            html.append("<p style=\"").append(MUTED).append("margin:.4rem 0 0;\">Why not: ").append(AssistantHomeView.esc(denied)).append("</p>");
         }
         html.append("</section>");
      }
      html.append("<p style=\"").append(MUTED).append("margin:0;\"><a style=\"color:inherit;text-decoration:underline;\" href=\"").append(AssistantHomeView.esc(prefix + "/" + AssistantDataAccess.TABLE_NAME))
         .append("\">Change the assistant's data access</a></p>");
      return (html.append("</div>").toString());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String yes(boolean value, String word)
   {
      return ((value ? "" : "no ") + word);
   }



   /*******************************************************************************
    ** Renders for the viewing user.
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
         String table  = input.getQueryParams() == null ? null : input.getQueryParams().get("table");
         String prompt = input.getQueryParams() == null ? null : input.getQueryParams().get("prompt");
         List<AccessExplainer.TableAccess> tables;
         try
         {
            tables = runtime.getAccessExplainer().explain(runtime.getUiApiGateway(), QContext.getQSession().getUuid(), runtime.getPolicyService().current(false).tools(), table, prompt);
         }
         catch(QException e)
         {
            ////////////////////////////////////////////////////////////////////
            // the UI API is not reachable (no assistant request has told the  //
            // server its port yet, or the call failed): say so, don't guess  //
            ////////////////////////////////////////////////////////////////////
            return (new RenderWidgetOutput(new RawHTML(null, "<p style=\"opacity:.75;font-size:.9rem;margin:0;\">Access could not be computed from the UI API right now ("
               + AssistantHomeView.esc(e.getMessage()) + "). Open the assistant panel once, or set uiApiBaseUrl, then refresh.</p>")));
         }
         String user = QContext.getQSession().getUser() == null ? "you" : StringUtils.hasContent(QContext.getQSession().getUser().getFullName())
            ? QContext.getQSession().getUser().getFullName() : QContext.getQSession().getUser().getIdReference();
         return (new RenderWidgetOutput(new RawHTML(null, AssistantAccessWidget.render(tables, user, runtime.getConfig().getTablePathPrefix()))));
      }
   }

}
