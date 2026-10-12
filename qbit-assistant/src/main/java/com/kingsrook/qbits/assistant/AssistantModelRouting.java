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
import java.util.Set;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Which model does which job (spec 2.9). Model ids are the exact strings
 ** from the provider (no date suffix). Effort is sent explicitly on every
 ** request, because the models' defaults differ (Sonnet 5.5 defaults to high).
 **
 ** Phase 1 uses only the main slot. Escalation (Opus) and the summary model
 ** (Haiku: titles, compaction) arrive in phase 2; they are configurable now
 ** so the admin policy shape is stable.
 *******************************************************************************/
public class AssistantModelRouting implements Serializable
{
   public static final String SONNET_5_5 = "claude-sonnet-5-5";
   public static final String OPUS_5_5   = "claude-opus-5-5";
   public static final String HAIKU_5_5  = "claude-haiku-5-5";

   public static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");

   private String  mainModel           = SONNET_5_5;
   private String  mainEffort          = "medium";
   private Integer mainMaxOutputTokens = 16000;
   private String  escalationModel     = OPUS_5_5;
   private String  escalationEffort    = "high";
   private Boolean escalationEnabled   = false;
   private String  summaryModel        = HAIKU_5_5;



   /*******************************************************************************
    ** Collect errors.
    *******************************************************************************/
   public void validate(List<String> errors)
   {
      if(!StringUtils.hasContent(mainModel))
      {
         errors.add("models.mainModel is required");
      }
      if(mainEffort == null || !EFFORTS.contains(mainEffort))
      {
         errors.add("models.mainEffort must be one of " + EFFORTS);
      }
      if(escalationEffort != null && !EFFORTS.contains(escalationEffort))
      {
         errors.add("models.escalationEffort must be one of " + EFFORTS);
      }
      if(mainMaxOutputTokens == null || mainMaxOutputTokens < 1024)
      {
         errors.add("models.mainMaxOutputTokens must be at least 1024");
      }
   }



   /*******************************************************************************
    ** Getter for mainModel
    *******************************************************************************/
   public String getMainModel()
   {
      return (this.mainModel);
   }



   /*******************************************************************************
    ** Setter for mainModel
    *******************************************************************************/
   public void setMainModel(String mainModel)
   {
      this.mainModel = mainModel;
   }



   /*******************************************************************************
    ** Fluent setter for mainModel
    *******************************************************************************/
   public AssistantModelRouting withMainModel(String mainModel)
   {
      this.mainModel = mainModel;
      return (this);
   }



   /*******************************************************************************
    ** Getter for mainEffort
    *******************************************************************************/
   public String getMainEffort()
   {
      return (this.mainEffort);
   }



   /*******************************************************************************
    ** Setter for mainEffort
    *******************************************************************************/
   public void setMainEffort(String mainEffort)
   {
      this.mainEffort = mainEffort;
   }



   /*******************************************************************************
    ** Fluent setter for mainEffort
    *******************************************************************************/
   public AssistantModelRouting withMainEffort(String mainEffort)
   {
      this.mainEffort = mainEffort;
      return (this);
   }



   /*******************************************************************************
    ** Getter for mainMaxOutputTokens
    *******************************************************************************/
   public Integer getMainMaxOutputTokens()
   {
      return (this.mainMaxOutputTokens);
   }



   /*******************************************************************************
    ** Setter for mainMaxOutputTokens
    *******************************************************************************/
   public void setMainMaxOutputTokens(Integer mainMaxOutputTokens)
   {
      this.mainMaxOutputTokens = mainMaxOutputTokens;
   }



   /*******************************************************************************
    ** Fluent setter for mainMaxOutputTokens
    *******************************************************************************/
   public AssistantModelRouting withMainMaxOutputTokens(Integer mainMaxOutputTokens)
   {
      this.mainMaxOutputTokens = mainMaxOutputTokens;
      return (this);
   }



   /*******************************************************************************
    ** Getter for escalationModel
    *******************************************************************************/
   public String getEscalationModel()
   {
      return (this.escalationModel);
   }



   /*******************************************************************************
    ** Setter for escalationModel
    *******************************************************************************/
   public void setEscalationModel(String escalationModel)
   {
      this.escalationModel = escalationModel;
   }



   /*******************************************************************************
    ** Fluent setter for escalationModel
    *******************************************************************************/
   public AssistantModelRouting withEscalationModel(String escalationModel)
   {
      this.escalationModel = escalationModel;
      return (this);
   }



   /*******************************************************************************
    ** Getter for escalationEffort
    *******************************************************************************/
   public String getEscalationEffort()
   {
      return (this.escalationEffort);
   }



   /*******************************************************************************
    ** Setter for escalationEffort
    *******************************************************************************/
   public void setEscalationEffort(String escalationEffort)
   {
      this.escalationEffort = escalationEffort;
   }



   /*******************************************************************************
    ** Fluent setter for escalationEffort
    *******************************************************************************/
   public AssistantModelRouting withEscalationEffort(String escalationEffort)
   {
      this.escalationEffort = escalationEffort;
      return (this);
   }



   /*******************************************************************************
    ** Getter for escalationEnabled
    *******************************************************************************/
   public Boolean getEscalationEnabled()
   {
      return (this.escalationEnabled);
   }



   /*******************************************************************************
    ** Setter for escalationEnabled
    *******************************************************************************/
   public void setEscalationEnabled(Boolean escalationEnabled)
   {
      this.escalationEnabled = escalationEnabled;
   }



   /*******************************************************************************
    ** Fluent setter for escalationEnabled
    *******************************************************************************/
   public AssistantModelRouting withEscalationEnabled(Boolean escalationEnabled)
   {
      this.escalationEnabled = escalationEnabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for summaryModel
    *******************************************************************************/
   public String getSummaryModel()
   {
      return (this.summaryModel);
   }



   /*******************************************************************************
    ** Setter for summaryModel
    *******************************************************************************/
   public void setSummaryModel(String summaryModel)
   {
      this.summaryModel = summaryModel;
   }



   /*******************************************************************************
    ** Fluent setter for summaryModel
    *******************************************************************************/
   public AssistantModelRouting withSummaryModel(String summaryModel)
   {
      this.summaryModel = summaryModel;
      return (this);
   }

}
