/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.assistant.engine;


import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.classification.DataClassification;
import com.kingsrook.qbits.assistant.model.AssistantDataAudit;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Checks and records routing before a model receives a request. Audit failures fail closed.
 *******************************************************************************/
public final class TurnDataRouting
{
   /*******************************************************************************
    ** Typed stop carrying only the safe rule reason.
    *******************************************************************************/
   public static class Blocked extends QException
   {
      private final PhiRoutingGuard.Block block;



      /*******************************************************************************
       ** Preserve the rule decision for the terminal event.
       *******************************************************************************/
      public Blocked(PhiRoutingGuard.Block block)
      {
         super(block.reason());
         this.block = block;
      }



      /*******************************************************************************
       ** Safe error code and reason.
       *******************************************************************************/
      public PhiRoutingGuard.Block getBlock()
      {
         return (block);
      }
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private TurnDataRouting()
   {
   }



   /*******************************************************************************
    ** Refresh the row before every call, including strict-schema retries.
    *******************************************************************************/
   public static void beforeModelCall(AssistantRuntime runtime, TurnClassification classification, AssistantTurn turn, ProviderService.Active active) throws QException
   {
      classification.inherit(runtime.getStore().getConversation(turn.getUserId(), turn.getConversationId()));
      AssistantProvider row = active.row() == null ? null : runtime.getStore().getProvider(active.providerId());
      check(runtime, classification, turn, row, active.model());
   }



   /*******************************************************************************
    ** Also guard the selected row before HIPAA provider resolution can refuse it.
    *******************************************************************************/
   public static void check(AssistantRuntime runtime, TurnClassification classification, AssistantTurn turn, AssistantProvider row, String model) throws QException
   {
      PhiRoutingGuard.Block block = PhiRoutingGuard.check(classification, row, runtime.getConfig().getHipaaMode()).orElse(null);
      if(block != null)
      {
         runtime.getProviderService().eligibleAlternative(classification.getClassification(), row);
      }
      if(block != null || classification.getClassification().isAtLeast(DataClassification.PII))
      {
         runtime.getStore().insert(new AssistantDataAudit()
            .withUserId(turn.getUserId()).withConversationId(turn.getConversationId()).withTurnId(turn.getId())
            .withProviderId(row == null ? null : row.getId()).withProviderName(row == null ? "Code-registered provider" : row.getName())
            .withModel(model).withHipaaEligible(row != null && Boolean.TRUE.equals(row.getHipaaEligible()))
            .withClassification(classification.getClassification().name()).withTables(classification.getTables())
            .withDecision(block == null ? "ALLOWED" : "BLOCKED")
            .withReason(block == null ? "Provider permitted for this classification." : block.reason())
            .withCreateDate(runtime.getClock().instant()));
      }
      if(block != null)
      {
         throw (new Blocked(block));
      }
   }



   /*******************************************************************************
    ** Check an initial block before resolving a provider; allowed requests are audited at dispatch.
    *******************************************************************************/
   public static void beforeResolve(AssistantRuntime runtime, TurnClassification classification, AssistantTurn turn, String model) throws QException
   {
      AssistantProvider row = runtime.getProviderService().activeRow();
      if(PhiRoutingGuard.check(classification, row, runtime.getConfig().getHipaaMode()).isPresent())
      {
         String selectedModel = row != null && row.getModel() != null && !row.getModel().isBlank() ? row.getModel() : model;
         check(runtime, classification, turn, row, selectedModel);
      }
   }
}
