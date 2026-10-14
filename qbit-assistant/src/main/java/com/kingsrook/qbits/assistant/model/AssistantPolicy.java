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

package com.kingsrook.qbits.assistant.model;


import java.time.Instant;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.fields.ValueTooLongBehavior;


/*******************************************************************************
 ** The admin policy: kill switch and runtime overrides of the model routing
 ** and budgets. One row (the first by id) is used; null values fall back to
 ** the QBit config.
 *******************************************************************************/
public class AssistantPolicy extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_policy";

   /*******************************************************************************
    ** The table name before 0.1.0-RC.3 (camelCase). See UPGRADE.md.
    *******************************************************************************/
   @Deprecated(since = "0.1.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "assistantPolicy";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(maxLength = 64)
   private String name;

   @QField()
   private Boolean killSwitch;

   @QField(maxLength = 255, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String killSwitchReason;

   @QField(maxLength = 64)
   private String mainModel;

   @QField(maxLength = 16)
   private String mainEffort;

   @QField()
   private Long userDailyTokensSoft;

   @QField()
   private Long userDailyTokensHard;

   @QField()
   private Long userDailyCostMicrosSoft;

   @QField()
   private Long userDailyCostMicrosHard;

   @QField()
   private Long instanceMonthlyTokensSoft;

   @QField()
   private Long instanceMonthlyTokensHard;

   @QField()
   private Long instanceMonthlyCostMicrosSoft;

   @QField()
   private Long instanceMonthlyCostMicrosHard;

   @QField()
   private Boolean writesRequireConfirmation;

   @QField(maxLength = 1000)
   private String allowedTools;

   @QField()
   private Integer maxRowsPerCall;

   @QField()
   private Boolean redactPii;

   @QField()
   private Boolean writeModeAllowed;

   @QField(maxLength = 128)
   private String writeModePermission;

   @QField()
   private Boolean autoConfirmAllowed;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AssistantPolicy()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AssistantPolicy(QRecord record) throws QException
   {
      populateFromQRecord(record);
   }



   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (this.id);
   }



   /*******************************************************************************
    ** Setter for id
    *******************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /*******************************************************************************
    ** Fluent setter for id
    *******************************************************************************/
   public AssistantPolicy withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for name
    *******************************************************************************/
   public String getName()
   {
      return (this.name);
   }



   /*******************************************************************************
    ** Setter for name
    *******************************************************************************/
   public void setName(String name)
   {
      this.name = name;
   }



   /*******************************************************************************
    ** Fluent setter for name
    *******************************************************************************/
   public AssistantPolicy withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for killSwitch
    *******************************************************************************/
   public Boolean getKillSwitch()
   {
      return (this.killSwitch);
   }



   /*******************************************************************************
    ** Setter for killSwitch
    *******************************************************************************/
   public void setKillSwitch(Boolean killSwitch)
   {
      this.killSwitch = killSwitch;
   }



   /*******************************************************************************
    ** Fluent setter for killSwitch
    *******************************************************************************/
   public AssistantPolicy withKillSwitch(Boolean killSwitch)
   {
      this.killSwitch = killSwitch;
      return (this);
   }



   /*******************************************************************************
    ** Getter for killSwitchReason
    *******************************************************************************/
   public String getKillSwitchReason()
   {
      return (this.killSwitchReason);
   }



   /*******************************************************************************
    ** Setter for killSwitchReason
    *******************************************************************************/
   public void setKillSwitchReason(String killSwitchReason)
   {
      this.killSwitchReason = killSwitchReason;
   }



   /*******************************************************************************
    ** Fluent setter for killSwitchReason
    *******************************************************************************/
   public AssistantPolicy withKillSwitchReason(String killSwitchReason)
   {
      this.killSwitchReason = killSwitchReason;
      return (this);
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
   public AssistantPolicy withMainModel(String mainModel)
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
   public AssistantPolicy withMainEffort(String mainEffort)
   {
      this.mainEffort = mainEffort;
      return (this);
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
   public AssistantPolicy withUserDailyTokensSoft(Long userDailyTokensSoft)
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
   public AssistantPolicy withUserDailyTokensHard(Long userDailyTokensHard)
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
   public AssistantPolicy withUserDailyCostMicrosSoft(Long userDailyCostMicrosSoft)
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
   public AssistantPolicy withUserDailyCostMicrosHard(Long userDailyCostMicrosHard)
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
   public AssistantPolicy withInstanceMonthlyTokensSoft(Long instanceMonthlyTokensSoft)
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
   public AssistantPolicy withInstanceMonthlyTokensHard(Long instanceMonthlyTokensHard)
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
   public AssistantPolicy withInstanceMonthlyCostMicrosSoft(Long instanceMonthlyCostMicrosSoft)
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
   public AssistantPolicy withInstanceMonthlyCostMicrosHard(Long instanceMonthlyCostMicrosHard)
   {
      this.instanceMonthlyCostMicrosHard = instanceMonthlyCostMicrosHard;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (this.createDate);
   }



   /*******************************************************************************
    ** Setter for createDate
    *******************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /*******************************************************************************
    ** Fluent setter for createDate
    *******************************************************************************/
   public AssistantPolicy withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (this.modifyDate);
   }



   /*******************************************************************************
    ** Setter for modifyDate
    *******************************************************************************/
   public void setModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
   }



   /*******************************************************************************
    ** Fluent setter for modifyDate
    *******************************************************************************/
   public AssistantPolicy withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }


   /*******************************************************************************
    ** Getter for writesRequireConfirmation (whether every write waits for the user to confirm a card (default true))
    *******************************************************************************/
   public Boolean getWritesRequireConfirmation()
   {
      return (this.writesRequireConfirmation);
   }



   /*******************************************************************************
    ** Setter for writesRequireConfirmation
    *******************************************************************************/
   public void setWritesRequireConfirmation(Boolean writesRequireConfirmation)
   {
      this.writesRequireConfirmation = writesRequireConfirmation;
   }



   /*******************************************************************************
    ** Fluent setter for writesRequireConfirmation
    *******************************************************************************/
   public AssistantPolicy withWritesRequireConfirmation(Boolean writesRequireConfirmation)
   {
      this.writesRequireConfirmation = writesRequireConfirmation;
      return (this);
   }


   /*******************************************************************************
    ** Getter for allowedTools (comma-separated tool names; empty allows all)
    *******************************************************************************/
   public String getAllowedTools()
   {
      return (this.allowedTools);
   }



   /*******************************************************************************
    ** Setter for allowedTools
    *******************************************************************************/
   public void setAllowedTools(String allowedTools)
   {
      this.allowedTools = allowedTools;
   }



   /*******************************************************************************
    ** Fluent setter for allowedTools
    *******************************************************************************/
   public AssistantPolicy withAllowedTools(String allowedTools)
   {
      this.allowedTools = allowedTools;
      return (this);
   }


   /*******************************************************************************
    ** Getter for maxRowsPerCall (most rows one tool call may return to the model)
    *******************************************************************************/
   public Integer getMaxRowsPerCall()
   {
      return (this.maxRowsPerCall);
   }



   /*******************************************************************************
    ** Setter for maxRowsPerCall
    *******************************************************************************/
   public void setMaxRowsPerCall(Integer maxRowsPerCall)
   {
      this.maxRowsPerCall = maxRowsPerCall;
   }



   /*******************************************************************************
    ** Fluent setter for maxRowsPerCall
    *******************************************************************************/
   public AssistantPolicy withMaxRowsPerCall(Integer maxRowsPerCall)
   {
      this.maxRowsPerCall = maxRowsPerCall;
      return (this);
   }


   /*******************************************************************************
    ** Getter for redactPii (whether secrets and personal identifiers are redacted from tool results (default true))
    *******************************************************************************/
   public Boolean getRedactPii()
   {
      return (this.redactPii);
   }



   /*******************************************************************************
    ** Setter for redactPii
    *******************************************************************************/
   public void setRedactPii(Boolean redactPii)
   {
      this.redactPii = redactPii;
   }



   /*******************************************************************************
    ** Fluent setter for redactPii
    *******************************************************************************/
   public AssistantPolicy withRedactPii(Boolean redactPii)
   {
      this.redactPii = redactPii;
      return (this);
   }


   /*******************************************************************************
    ** Getter for writeModeAllowed (whether any conversation may switch to Write mode (default true))
    *******************************************************************************/
   public Boolean getWriteModeAllowed()
   {
      return (this.writeModeAllowed);
   }



   /*******************************************************************************
    ** Setter for writeModeAllowed
    *******************************************************************************/
   public void setWriteModeAllowed(Boolean writeModeAllowed)
   {
      this.writeModeAllowed = writeModeAllowed;
   }



   /*******************************************************************************
    ** Fluent setter for writeModeAllowed
    *******************************************************************************/
   public AssistantPolicy withWriteModeAllowed(Boolean writeModeAllowed)
   {
      this.writeModeAllowed = writeModeAllowed;
      return (this);
   }


   /*******************************************************************************
    ** Getter for writeModePermission (only holders of this permission may switch to Write mode (empty: everyone))
    *******************************************************************************/
   public String getWriteModePermission()
   {
      return (this.writeModePermission);
   }



   /*******************************************************************************
    ** Setter for writeModePermission
    *******************************************************************************/
   public void setWriteModePermission(String writeModePermission)
   {
      this.writeModePermission = writeModePermission;
   }



   /*******************************************************************************
    ** Fluent setter for writeModePermission
    *******************************************************************************/
   public AssistantPolicy withWriteModePermission(String writeModePermission)
   {
      this.writeModePermission = writeModePermission;
      return (this);
   }


   /*******************************************************************************
    ** Getter for autoConfirmAllowed (whether users may auto-confirm low-risk writes (default false))
    *******************************************************************************/
   public Boolean getAutoConfirmAllowed()
   {
      return (this.autoConfirmAllowed);
   }



   /*******************************************************************************
    ** Setter for autoConfirmAllowed
    *******************************************************************************/
   public void setAutoConfirmAllowed(Boolean autoConfirmAllowed)
   {
      this.autoConfirmAllowed = autoConfirmAllowed;
   }



   /*******************************************************************************
    ** Fluent setter for autoConfirmAllowed
    *******************************************************************************/
   public AssistantPolicy withAutoConfirmAllowed(Boolean autoConfirmAllowed)
   {
      this.autoConfirmAllowed = autoConfirmAllowed;
      return (this);
   }

}
