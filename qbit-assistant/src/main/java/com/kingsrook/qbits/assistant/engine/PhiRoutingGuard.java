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


import java.util.Optional;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;


/*******************************************************************************
 ** The fail-closed routing rule, independent of provider selection and fallback chains.
 *******************************************************************************/
public final class PhiRoutingGuard
{
   /*******************************************************************************
    ** A safe block result; the engine may ask the eligible-alternative seam before stopping.
    *******************************************************************************/
   public record Block(String errorCode, String reason)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private PhiRoutingGuard()
   {
   }



   /*******************************************************************************
    ** Only a persisted, explicitly eligible provider may receive PHI, or any turn in HIPAA mode.
    *******************************************************************************/
   public static Optional<Block> check(TurnClassification classification, AssistantProvider provider, Boolean hipaaMode)
   {
      return (check(classification.getClassification(), provider, hipaaMode).map(block ->
         new Block(block.errorCode(), block.reason().replace("this conversation", "this conversation" + (classification.getTables().isEmpty() ? " (including its history)" : " (" + classification.getTables() + ")")))));
   }



   /*******************************************************************************
    ** No row, null eligibility or false eligibility always means not eligible.
    *******************************************************************************/
   public static Optional<Block> check(DataClassification classification, AssistantProvider provider, Boolean hipaaMode)
   {
      boolean eligible = provider != null && provider.getId() != null && Boolean.TRUE.equals(provider.getHipaaEligible());
      if(eligible)
      {
         return (Optional.empty());
      }
      String name = provider == null || provider.getName() == null ? "the configured provider" : provider.getName();
      if(Boolean.TRUE.equals(hipaaMode))
      {
         return (Optional.of(new Block("HIPAA_PROVIDER", "HIPAA mode: " + name + " is not marked HIPAA-eligible, so this conversation was not sent. Ask an administrator to enable an eligible provider.")));
      }
      if(classification == DataClassification.PHI)
      {
         return (Optional.of(new Block("PHI_BLOCKED", "Protected health information was touched by this conversation. The provider " + name
            + " is not marked HIPAA-eligible, so the question was not sent. Ask an administrator to route PHI to an eligible provider.")));
      }
      return (Optional.empty());
   }
}
