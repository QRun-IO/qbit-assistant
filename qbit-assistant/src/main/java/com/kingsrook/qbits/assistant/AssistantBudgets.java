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


import java.io.Serializable;
import java.util.List;


/*******************************************************************************
 ** Usage budgets. Every limit is optional (null = unlimited). "Tokens" are
 ** billable tokens: uncached input + cache writes + output (cache reads are
 ** cheap and excluded; they still count toward cost). Cost is in micro-USD.
 **
 ** A soft limit adds a warning notice to the turn; a hard limit refuses new
 ** turns (HTTP 429) and stops a running turn after its current step.
 **
 ** The assistantPolicy row can override any of these at runtime.
 *******************************************************************************/
public class AssistantBudgets implements Serializable
{
   private Long userDailyTokensSoft;
   private Long userDailyTokensHard;
   private Long userDailyCostMicrosSoft;
   private Long userDailyCostMicrosHard;
   private Long instanceMonthlyTokensSoft;
   private Long instanceMonthlyTokensHard;
   private Long instanceMonthlyCostMicrosSoft;
   private Long instanceMonthlyCostMicrosHard;



   /*******************************************************************************
    ** Collect errors (negative limits, soft above hard).
    *******************************************************************************/
   public void validate(List<String> errors)
   {
      check(errors, "userDailyTokens", userDailyTokensSoft, userDailyTokensHard);
      check(errors, "userDailyCostMicros", userDailyCostMicrosSoft, userDailyCostMicrosHard);
      check(errors, "instanceMonthlyTokens", instanceMonthlyTokensSoft, instanceMonthlyTokensHard);
      check(errors, "instanceMonthlyCostMicros", instanceMonthlyCostMicrosSoft, instanceMonthlyCostMicrosHard);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void check(List<String> errors, String name, Long soft, Long hard)
   {
      if(soft != null && soft < 0)
      {
         errors.add("budgets." + name + "Soft must not be negative");
      }
      if(hard != null && hard < 0)
      {
         errors.add("budgets." + name + "Hard must not be negative");
      }
      if(soft != null && hard != null && soft > hard)
      {
         errors.add("budgets." + name + "Soft must not exceed budgets." + name + "Hard");
      }
   }



   /*******************************************************************************
    ** Getter for userDailyTokensSoft
    *******************************************************************************/
   public Long getUserDailyTokensSoft()
   {
      return (this.userDailyTokensSoft);
   }



   /*******************************************************************************
    ** Setter for userDailyTokensSoft
    *******************************************************************************/
   public void setUserDailyTokensSoft(Long userDailyTokensSoft)
   {
      this.userDailyTokensSoft = userDailyTokensSoft;
   }



   /*******************************************************************************
    ** Fluent setter for userDailyTokensSoft
    *******************************************************************************/
   public AssistantBudgets withUserDailyTokensSoft(Long userDailyTokensSoft)
   {
      this.userDailyTokensSoft = userDailyTokensSoft;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userDailyTokensHard
    *******************************************************************************/
   public Long getUserDailyTokensHard()
   {
      return (this.userDailyTokensHard);
   }



   /*******************************************************************************
    ** Setter for userDailyTokensHard
    *******************************************************************************/
   public void setUserDailyTokensHard(Long userDailyTokensHard)
   {
      this.userDailyTokensHard = userDailyTokensHard;
   }



   /*******************************************************************************
    ** Fluent setter for userDailyTokensHard
    *******************************************************************************/
   public AssistantBudgets withUserDailyTokensHard(Long userDailyTokensHard)
   {
      this.userDailyTokensHard = userDailyTokensHard;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userDailyCostMicrosSoft
    *******************************************************************************/
   public Long getUserDailyCostMicrosSoft()
   {
      return (this.userDailyCostMicrosSoft);
   }



   /*******************************************************************************
    ** Setter for userDailyCostMicrosSoft
    *******************************************************************************/
   public void setUserDailyCostMicrosSoft(Long userDailyCostMicrosSoft)
   {
      this.userDailyCostMicrosSoft = userDailyCostMicrosSoft;
   }



   /*******************************************************************************
    ** Fluent setter for userDailyCostMicrosSoft
    *******************************************************************************/
   public AssistantBudgets withUserDailyCostMicrosSoft(Long userDailyCostMicrosSoft)
   {
      this.userDailyCostMicrosSoft = userDailyCostMicrosSoft;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userDailyCostMicrosHard
    *******************************************************************************/
   public Long getUserDailyCostMicrosHard()
   {
      return (this.userDailyCostMicrosHard);
   }



   /*******************************************************************************
    ** Setter for userDailyCostMicrosHard
    *******************************************************************************/
   public void setUserDailyCostMicrosHard(Long userDailyCostMicrosHard)
   {
      this.userDailyCostMicrosHard = userDailyCostMicrosHard;
   }



   /*******************************************************************************
    ** Fluent setter for userDailyCostMicrosHard
    *******************************************************************************/
   public AssistantBudgets withUserDailyCostMicrosHard(Long userDailyCostMicrosHard)
   {
      this.userDailyCostMicrosHard = userDailyCostMicrosHard;
      return (this);
   }



   /*******************************************************************************
    ** Getter for instanceMonthlyTokensSoft
    *******************************************************************************/
   public Long getInstanceMonthlyTokensSoft()
   {
      return (this.instanceMonthlyTokensSoft);
   }



   /*******************************************************************************
    ** Setter for instanceMonthlyTokensSoft
    *******************************************************************************/
   public void setInstanceMonthlyTokensSoft(Long instanceMonthlyTokensSoft)
   {
      this.instanceMonthlyTokensSoft = instanceMonthlyTokensSoft;
   }



   /*******************************************************************************
    ** Fluent setter for instanceMonthlyTokensSoft
    *******************************************************************************/
   public AssistantBudgets withInstanceMonthlyTokensSoft(Long instanceMonthlyTokensSoft)
   {
      this.instanceMonthlyTokensSoft = instanceMonthlyTokensSoft;
      return (this);
   }



   /*******************************************************************************
    ** Getter for instanceMonthlyTokensHard
    *******************************************************************************/
   public Long getInstanceMonthlyTokensHard()
   {
      return (this.instanceMonthlyTokensHard);
   }



   /*******************************************************************************
    ** Setter for instanceMonthlyTokensHard
    *******************************************************************************/
   public void setInstanceMonthlyTokensHard(Long instanceMonthlyTokensHard)
   {
      this.instanceMonthlyTokensHard = instanceMonthlyTokensHard;
   }



   /*******************************************************************************
    ** Fluent setter for instanceMonthlyTokensHard
    *******************************************************************************/
   public AssistantBudgets withInstanceMonthlyTokensHard(Long instanceMonthlyTokensHard)
   {
      this.instanceMonthlyTokensHard = instanceMonthlyTokensHard;
      return (this);
   }



   /*******************************************************************************
    ** Getter for instanceMonthlyCostMicrosSoft
    *******************************************************************************/
   public Long getInstanceMonthlyCostMicrosSoft()
   {
      return (this.instanceMonthlyCostMicrosSoft);
   }



   /*******************************************************************************
    ** Setter for instanceMonthlyCostMicrosSoft
    *******************************************************************************/
   public void setInstanceMonthlyCostMicrosSoft(Long instanceMonthlyCostMicrosSoft)
   {
      this.instanceMonthlyCostMicrosSoft = instanceMonthlyCostMicrosSoft;
   }



   /*******************************************************************************
    ** Fluent setter for instanceMonthlyCostMicrosSoft
    *******************************************************************************/
   public AssistantBudgets withInstanceMonthlyCostMicrosSoft(Long instanceMonthlyCostMicrosSoft)
   {
      this.instanceMonthlyCostMicrosSoft = instanceMonthlyCostMicrosSoft;
      return (this);
   }



   /*******************************************************************************
    ** Getter for instanceMonthlyCostMicrosHard
    *******************************************************************************/
   public Long getInstanceMonthlyCostMicrosHard()
   {
      return (this.instanceMonthlyCostMicrosHard);
   }



   /*******************************************************************************
    ** Setter for instanceMonthlyCostMicrosHard
    *******************************************************************************/
   public void setInstanceMonthlyCostMicrosHard(Long instanceMonthlyCostMicrosHard)
   {
      this.instanceMonthlyCostMicrosHard = instanceMonthlyCostMicrosHard;
   }



   /*******************************************************************************
    ** Fluent setter for instanceMonthlyCostMicrosHard
    *******************************************************************************/
   public AssistantBudgets withInstanceMonthlyCostMicrosHard(Long instanceMonthlyCostMicrosHard)
   {
      this.instanceMonthlyCostMicrosHard = instanceMonthlyCostMicrosHard;
      return (this);
   }

}
