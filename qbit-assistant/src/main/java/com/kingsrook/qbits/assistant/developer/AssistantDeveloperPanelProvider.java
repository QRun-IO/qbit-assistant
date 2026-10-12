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

package com.kingsrook.qbits.assistant.developer;


import java.util.List;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qqq.backend.core.actions.ai.AiAccessReport;
import com.kingsrook.qqq.backend.core.actions.ai.AiFieldAccess;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolApplicability;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanel;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelBlock;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelContext;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelLink;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelProviderInterface;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelTone;


/*******************************************************************************
 ** The "Assistant" panel of the record Developer screen (contract 5.2), on
 ** the core developer-panel-provider contract. Available whenever the QBit
 ** is installed and enabled; for a user without assistant.has_access it only
 ** says so. Everything else comes from AssistantDeveloperInfo, i.e. from the
 ** kernel's permission-scoped access report for the CURRENT user.
 *******************************************************************************/
public class AssistantDeveloperPanelProvider implements DeveloperPanelProviderInterface
{
   public static final String ID = "assistant";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getId()
   {
      return (ID);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getTitle()
   {
      return ("Assistant");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public Integer getOrder()
   {
      return (700);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getIcon()
   {
      return ("smart_toy");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public boolean isAvailable(DeveloperPanelContext context) throws QException
   {
      AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
      return (runtime != null && runtime.getConfig().isEffectivelyEnabled() && context.getTable() != null);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public DeveloperPanel build(DeveloperPanelContext context) throws QException
   {
      AssistantRuntime             runtime = AssistantRuntime.of(QContext.getQInstance());
      AssistantDeveloperInfo.Info  info    = AssistantDeveloperInfo.build(runtime, QContext.getQSession(), context.getTableName(), context.getPrimaryKey());
      return (render(info));
   }



   /*******************************************************************************
    ** The panel for an assembled Info.
    *******************************************************************************/
   public static DeveloperPanel render(AssistantDeveloperInfo.Info info)
   {
      DeveloperPanel panel = new DeveloperPanel()
         .withId(ID)
         .withTitle("Assistant")
         .withIcon("smart_toy")
         .withOrder(700)
         .withDescription("What the in-app assistant can read and open for this record, with YOUR permissions (the assistant never has more).");

      if(!info.hasAccess())
      {
         return (panel.withStatusLabel("No access").withStatusTone(DeveloperPanelTone.INFO)
            .withBlock(DeveloperPanelBlock.text("You do not have access to the assistant (permission assistant.has_access).", DeveloperPanelTone.INFO)));
      }

      if(info.killSwitch())
      {
         panel.withStatusLabel("Switched off").withStatusTone(DeveloperPanelTone.WARNING);
      }
      else
      {
         panel.withStatusLabel("Available").withStatusTone(DeveloperPanelTone.SUCCESS);
      }

      panel.withBlock(DeveloperPanelBlock.keyValues("Status")
         .withItem("Enabled for this application", yesNo(info.enabled()))
         .withItem("Kill switch", info.killSwitch() ? "On - the assistant is off for everyone" : "Off")
         .withItem("Your access (assistant.has_access)", "Yes")
         .withItem("Model", info.model())
         .withItem("Writes", "Not available in this version (read and navigate only)"));

      AiAccessReport access = info.access();
      if(access == null || access.getReason() != null && access.getTableExposure() == null)
      {
         panel.withBlock(DeveloperPanelBlock.text("This table is not available to the assistant for you.", DeveloperPanelTone.WARNING));
         return (panel);
      }

      DeveloperPanelBlock record = DeveloperPanelBlock.keyValues("This table and record (as you)")
         .withItem("Table exposure", String.valueOf(access.getTableExposure()));
      if(access.getPrimaryKey() != null)
      {
         record.withItem("Record readable", yesNo(Boolean.TRUE.equals(access.getRecordReadable())));
         if(access.getRecordLabel() != null)
         {
            record.withItem("Record", access.getRecordLabel());
         }
      }
      access.getUserTablePermissions().forEach((permission, granted) -> record.withItem("Your " + permission + " permission", yesNo(Boolean.TRUE.equals(granted))));
      panel.withBlock(record);

      if(!Boolean.FALSE.equals(access.getRecordReadable()))
      {
         DeveloperPanelBlock tools = DeveloperPanelBlock.table("Tools", List.of("Tool", "Kind", "Applies", "Why"));
         for(AiToolApplicability tool : access.getTools())
         {
            tools.withRow(List.of(tool.getToolName(), String.valueOf(tool.getKind()), yesNo(Boolean.TRUE.equals(tool.getApplies())), tool.getReason() == null ? "" : tool.getReason()));
         }
         panel.withBlock(tools);

         DeveloperPanelBlock fields = DeveloperPanelBlock.table("Fields", List.of("Field", "Label", "Exposure", "Why", "Could propose changes"));
         for(AiFieldAccess field : access.getFields())
         {
            fields.withRow(List.of(field.getFieldName(), field.getLabel() == null ? "" : field.getLabel(), String.valueOf(field.getExposure()),
               field.getReason() == null ? "" : field.getReason(), yesNo(Boolean.TRUE.equals(field.getCanPropose())) + " (phase 3)"));
         }
         panel.withBlock(fields);
      }

      if(!info.recentToolCalls().isEmpty())
      {
         DeveloperPanelBlock calls = DeveloperPanelBlock.table("Your recent assistant steps on this table", List.of("When", "Tool", "Outcome", "Summary"));
         for(AssistantToolCall call : info.recentToolCalls())
         {
            calls.withRow(List.of(call.getCreateDate() == null ? "" : call.getCreateDate().toString(), call.getToolName(), call.getOutcome(),
               call.getResultSummary() == null ? "" : call.getResultSummary()));
         }
         panel.withBlock(calls);
      }

      if(info.askHref() != null)
      {
         panel.withBlock(DeveloperPanelBlock.links("Assistant")
            .withLink(new DeveloperPanelLink().withLabel("Ask the assistant about this record").withHref(info.askHref())
               .withDescription("Opens the assistant on this record with a suggested question (not sent until you press Enter).")));
      }
      return (panel);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String yesNo(boolean value)
   {
      return (value ? "Yes" : "No");
   }

}
