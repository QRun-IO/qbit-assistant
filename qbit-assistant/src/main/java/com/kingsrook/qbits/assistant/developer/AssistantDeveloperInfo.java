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


import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.AssistantSecurity;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qqq.backend.core.actions.ai.AiAccessReport;
import com.kingsrook.qqq.backend.core.actions.ai.output.AiLinkBuilder;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** What the record Developer screen shows about the assistant (contract
 ** section 5.2), for the CURRENT user: whether the assistant is on for them,
 ** what the tool kernel may read for this table and record under their own
 ** permissions (AiToolKernel.describeAccess - permission-scoped, never more
 ** than "not available" for what they cannot see), their own recent tool
 ** calls on the table, and the "Ask the assistant about this record" link
 ** (section 5.3).
 **
 ** Plain data, independent of the core developer-panel contract;
 ** AssistantDeveloperPanelProvider renders it into panel blocks.
 *******************************************************************************/
public class AssistantDeveloperInfo
{
   private static final QLogger LOG = QLogger.getLogger(AssistantDeveloperInfo.class);

   public static final String DEFAULT_PROMPT    = "Tell me about this record.";
   public static final int    RECENT_CALL_LIMIT = 5;



   /*******************************************************************************
    ** The assembled information.
    **
    ** @param enabled        the QBit is enabled for the application
    ** @param hasAccess      the user holds assistant.hasAccess
    ** @param killSwitch     an admin switched the assistant off
    ** @param model          the main model
    ** @param access         the kernel's access report (null when the user has
    **                       no assistant access - nothing is described then)
    ** @param recentToolCalls the user's own latest tool calls on this table
    ** @param askHref        deep link that opens the assistant on this record,
    **                       or null when the user cannot use it here
    *******************************************************************************/
   public record Info(boolean enabled, boolean hasAccess, boolean killSwitch, String model, AiAccessReport access, List<AssistantToolCall> recentToolCalls, String askHref)
   {
      /*******************************************************************************
       ** Whether the user can use the assistant right now.
       *******************************************************************************/
      public boolean usable()
      {
         return (enabled && hasAccess && !killSwitch);
      }
   }



   /*******************************************************************************
    ** Assemble the information for the session in QContext (the user's own).
    *******************************************************************************/
   public static Info build(AssistantRuntime runtime, QSession session, String tableName, Serializable primaryKey)
   {
      AssistantQBitConfig config    = runtime.getConfig();
      boolean             enabled   = config.isEffectivelyEnabled();
      boolean             hasAccess = session != null && AssistantSecurity.userId(session) != null && session.hasPermission(AssistantQBitConfig.ACCESS_PERMISSION);
      boolean             killSwitch;
      String              model;
      try
      {
         var policy = runtime.getPolicyService().current(false);
         killSwitch = policy.killSwitch();
         model = policy.mainModel();
      }
      catch(Exception e)
      {
         LOG.warn("Could not read the assistant policy for the developer panel", e);
         killSwitch = false;
         model = config.getModels().getMainModel();
      }

      if(!hasAccess)
      {
         return (new Info(enabled, false, killSwitch, model, null, List.of(), null));
      }

      AiAccessReport access = runtime.getKernel().describeAccess(session, tableName, primaryKey);

      List<AssistantToolCall> recent = new ArrayList<>();
      try
      {
         String userId = AssistantSecurity.userId(session);
         AssistantSecurity.ownerKey(session, config);
         if(access.getReason() == null || primaryKey == null || Boolean.TRUE.equals(access.getRecordReadable()))
         {
            recent.addAll(runtime.getStore().recentToolCalls(userId, tableName, RECENT_CALL_LIMIT));
         }
      }
      catch(Exception e)
      {
         LOG.warn("Could not read recent assistant tool calls for the developer panel", e);
      }

      String askHref = null;
      if(enabled && !killSwitch && primaryKey != null && Boolean.TRUE.equals(access.getRecordReadable()))
      {
         askHref = askHref(tableName, primaryKey, DEFAULT_PROMPT);
      }
      return (new Info(enabled, true, killSwitch, model, access, recent, askHref));
   }



   /*******************************************************************************
    ** The 5.3 deep link: the record view with ?assistant=open&assistantPrompt=.
    *******************************************************************************/
   public static String askHref(String tableName, Serializable primaryKey, String prompt)
   {
      return (AiLinkBuilder.recordHref(tableName, primaryKey) + "?assistant=open&assistantPrompt=" + URLEncoder.encode(prompt, StandardCharsets.UTF_8).replace("+", "%20"));
   }

}
