/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
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

package com.kingsrook.qbits.assistant.model;


import java.math.BigDecimal;
import java.time.Instant;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;


/*******************************************************************************
 ** Scheduled assistant bookkeeping; credentials are references only.
 *******************************************************************************/
public class AssistantScheduledTask extends QRecordEntity
{
   public static final String TABLE_NAME = "assistant_scheduled_task";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 200)
   private String name;

   @QField()
   private Boolean enabled;

   @QField(isRequired = true, maxLength = 4000)
   private String prompt;

   @QField(isRequired = true, maxLength = 200)
   private String runAsUserId;

   @QField(isRequired = true, maxLength = 200)
   private String credentialEnvName;

   @QField(isEditable = false, maxLength = 200)
   private String credentialStatus;

   @QField()
   private Integer profileId;

   @QField(isRequired = true, maxLength = 200)
   private String cronExpression;

   @QField(maxLength = 100)
   private String timezone;

   @QField(isEditable = false)
   private Instant nextRunAt;

   @QField(maxLength = 20)
   private String outputMode;

   @QField()
   private Boolean writeModeRequested;

   @QField(isEditable = false, maxLength = 200)
   private String writeModeApprovedBy;

   @QField(isEditable = false)
   private Instant writeModeApprovedAt;

   @QField(isEditable = false, maxLength = 64)
   private String writeModeApprovalHash;

   @QField()
   private BigDecimal maxCostUsdPerRun;

   @QField(isEditable = false)
   private Instant lastRunAt;

   @QField(isEditable = false, maxLength = 20)
   private String lastRunStatus;

   @QField(isEditable = false, maxLength = 500)
   private String lastRunError;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Empty entity for framework mapping.
    *******************************************************************************/
   public AssistantScheduledTask()
   {
   }



   /*******************************************************************************
    ** Map a bookkeeping record.
    *******************************************************************************/
   public AssistantScheduledTask(QRecord record) throws QException
   {
      populateFromQRecord(record);
   }



   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (id);
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
   public AssistantScheduledTask withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for name
    *******************************************************************************/
   public String getName()
   {
      return (name);
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
   public AssistantScheduledTask withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for enabled
    *******************************************************************************/
   public Boolean getEnabled()
   {
      return (enabled);
   }



   /*******************************************************************************
    ** Setter for enabled
    *******************************************************************************/
   public void setEnabled(Boolean enabled)
   {
      this.enabled = enabled;
   }



   /*******************************************************************************
    ** Fluent setter for enabled
    *******************************************************************************/
   public AssistantScheduledTask withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for prompt
    *******************************************************************************/
   public String getPrompt()
   {
      return (prompt);
   }



   /*******************************************************************************
    ** Setter for prompt
    *******************************************************************************/
   public void setPrompt(String prompt)
   {
      this.prompt = prompt;
   }



   /*******************************************************************************
    ** Fluent setter for prompt
    *******************************************************************************/
   public AssistantScheduledTask withPrompt(String prompt)
   {
      this.prompt = prompt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for runAsUserId
    *******************************************************************************/
   public String getRunAsUserId()
   {
      return (runAsUserId);
   }



   /*******************************************************************************
    ** Setter for runAsUserId
    *******************************************************************************/
   public void setRunAsUserId(String runAsUserId)
   {
      this.runAsUserId = runAsUserId;
   }



   /*******************************************************************************
    ** Fluent setter for runAsUserId
    *******************************************************************************/
   public AssistantScheduledTask withRunAsUserId(String runAsUserId)
   {
      this.runAsUserId = runAsUserId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for credentialEnvName
    *******************************************************************************/
   public String getCredentialEnvName()
   {
      return (credentialEnvName);
   }



   /*******************************************************************************
    ** Setter for credentialEnvName
    *******************************************************************************/
   public void setCredentialEnvName(String credentialEnvName)
   {
      this.credentialEnvName = credentialEnvName;
   }



   /*******************************************************************************
    ** Fluent setter for credentialEnvName
    *******************************************************************************/
   public AssistantScheduledTask withCredentialEnvName(String credentialEnvName)
   {
      this.credentialEnvName = credentialEnvName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for credentialStatus
    *******************************************************************************/
   public String getCredentialStatus()
   {
      return (credentialStatus);
   }



   /*******************************************************************************
    ** Setter for credentialStatus
    *******************************************************************************/
   public void setCredentialStatus(String credentialStatus)
   {
      this.credentialStatus = credentialStatus;
   }



   /*******************************************************************************
    ** Fluent setter for credentialStatus
    *******************************************************************************/
   public AssistantScheduledTask withCredentialStatus(String credentialStatus)
   {
      this.credentialStatus = credentialStatus;
      return (this);
   }



   /*******************************************************************************
    ** Getter for profileId
    *******************************************************************************/
   public Integer getProfileId()
   {
      return (profileId);
   }



   /*******************************************************************************
    ** Setter for profileId
    *******************************************************************************/
   public void setProfileId(Integer profileId)
   {
      this.profileId = profileId;
   }



   /*******************************************************************************
    ** Fluent setter for profileId
    *******************************************************************************/
   public AssistantScheduledTask withProfileId(Integer profileId)
   {
      this.profileId = profileId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for cronExpression
    *******************************************************************************/
   public String getCronExpression()
   {
      return (cronExpression);
   }



   /*******************************************************************************
    ** Setter for cronExpression
    *******************************************************************************/
   public void setCronExpression(String cronExpression)
   {
      this.cronExpression = cronExpression;
   }



   /*******************************************************************************
    ** Fluent setter for cronExpression
    *******************************************************************************/
   public AssistantScheduledTask withCronExpression(String cronExpression)
   {
      this.cronExpression = cronExpression;
      return (this);
   }



   /*******************************************************************************
    ** Getter for timezone
    *******************************************************************************/
   public String getTimezone()
   {
      return (timezone);
   }



   /*******************************************************************************
    ** Setter for timezone
    *******************************************************************************/
   public void setTimezone(String timezone)
   {
      this.timezone = timezone;
   }



   /*******************************************************************************
    ** Fluent setter for timezone
    *******************************************************************************/
   public AssistantScheduledTask withTimezone(String timezone)
   {
      this.timezone = timezone;
      return (this);
   }



   /*******************************************************************************
    ** Getter for nextRunAt
    *******************************************************************************/
   public Instant getNextRunAt()
   {
      return (nextRunAt);
   }



   /*******************************************************************************
    ** Setter for nextRunAt
    *******************************************************************************/
   public void setNextRunAt(Instant nextRunAt)
   {
      this.nextRunAt = nextRunAt;
   }



   /*******************************************************************************
    ** Fluent setter for nextRunAt
    *******************************************************************************/
   public AssistantScheduledTask withNextRunAt(Instant nextRunAt)
   {
      this.nextRunAt = nextRunAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for outputMode
    *******************************************************************************/
   public String getOutputMode()
   {
      return (outputMode);
   }



   /*******************************************************************************
    ** Setter for outputMode
    *******************************************************************************/
   public void setOutputMode(String outputMode)
   {
      this.outputMode = outputMode;
   }



   /*******************************************************************************
    ** Fluent setter for outputMode
    *******************************************************************************/
   public AssistantScheduledTask withOutputMode(String outputMode)
   {
      this.outputMode = outputMode;
      return (this);
   }



   /*******************************************************************************
    ** Getter for writeModeRequested
    *******************************************************************************/
   public Boolean getWriteModeRequested()
   {
      return (writeModeRequested);
   }



   /*******************************************************************************
    ** Setter for writeModeRequested
    *******************************************************************************/
   public void setWriteModeRequested(Boolean writeModeRequested)
   {
      this.writeModeRequested = writeModeRequested;
   }



   /*******************************************************************************
    ** Fluent setter for writeModeRequested
    *******************************************************************************/
   public AssistantScheduledTask withWriteModeRequested(Boolean writeModeRequested)
   {
      this.writeModeRequested = writeModeRequested;
      return (this);
   }



   /*******************************************************************************
    ** Getter for writeModeApprovedBy
    *******************************************************************************/
   public String getWriteModeApprovedBy()
   {
      return (writeModeApprovedBy);
   }



   /*******************************************************************************
    ** Setter for writeModeApprovedBy
    *******************************************************************************/
   public void setWriteModeApprovedBy(String writeModeApprovedBy)
   {
      this.writeModeApprovedBy = writeModeApprovedBy;
   }



   /*******************************************************************************
    ** Fluent setter for writeModeApprovedBy
    *******************************************************************************/
   public AssistantScheduledTask withWriteModeApprovedBy(String writeModeApprovedBy)
   {
      this.writeModeApprovedBy = writeModeApprovedBy;
      return (this);
   }



   /*******************************************************************************
    ** Getter for writeModeApprovedAt
    *******************************************************************************/
   public Instant getWriteModeApprovedAt()
   {
      return (writeModeApprovedAt);
   }



   /*******************************************************************************
    ** Setter for writeModeApprovedAt
    *******************************************************************************/
   public void setWriteModeApprovedAt(Instant writeModeApprovedAt)
   {
      this.writeModeApprovedAt = writeModeApprovedAt;
   }



   /*******************************************************************************
    ** Fluent setter for writeModeApprovedAt
    *******************************************************************************/
   public AssistantScheduledTask withWriteModeApprovedAt(Instant writeModeApprovedAt)
   {
      this.writeModeApprovedAt = writeModeApprovedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for writeModeApprovalHash
    *******************************************************************************/
   public String getWriteModeApprovalHash()
   {
      return (writeModeApprovalHash);
   }



   /*******************************************************************************
    ** Setter for writeModeApprovalHash
    *******************************************************************************/
   public void setWriteModeApprovalHash(String writeModeApprovalHash)
   {
      this.writeModeApprovalHash = writeModeApprovalHash;
   }



   /*******************************************************************************
    ** Fluent setter for writeModeApprovalHash
    *******************************************************************************/
   public AssistantScheduledTask withWriteModeApprovalHash(String writeModeApprovalHash)
   {
      this.writeModeApprovalHash = writeModeApprovalHash;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxCostUsdPerRun
    *******************************************************************************/
   public BigDecimal getMaxCostUsdPerRun()
   {
      return (maxCostUsdPerRun);
   }



   /*******************************************************************************
    ** Setter for maxCostUsdPerRun
    *******************************************************************************/
   public void setMaxCostUsdPerRun(BigDecimal maxCostUsdPerRun)
   {
      this.maxCostUsdPerRun = maxCostUsdPerRun;
   }



   /*******************************************************************************
    ** Fluent setter for maxCostUsdPerRun
    *******************************************************************************/
   public AssistantScheduledTask withMaxCostUsdPerRun(BigDecimal maxCostUsdPerRun)
   {
      this.maxCostUsdPerRun = maxCostUsdPerRun;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastRunAt
    *******************************************************************************/
   public Instant getLastRunAt()
   {
      return (lastRunAt);
   }



   /*******************************************************************************
    ** Setter for lastRunAt
    *******************************************************************************/
   public void setLastRunAt(Instant lastRunAt)
   {
      this.lastRunAt = lastRunAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastRunAt
    *******************************************************************************/
   public AssistantScheduledTask withLastRunAt(Instant lastRunAt)
   {
      this.lastRunAt = lastRunAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastRunStatus
    *******************************************************************************/
   public String getLastRunStatus()
   {
      return (lastRunStatus);
   }



   /*******************************************************************************
    ** Setter for lastRunStatus
    *******************************************************************************/
   public void setLastRunStatus(String lastRunStatus)
   {
      this.lastRunStatus = lastRunStatus;
   }



   /*******************************************************************************
    ** Fluent setter for lastRunStatus
    *******************************************************************************/
   public AssistantScheduledTask withLastRunStatus(String lastRunStatus)
   {
      this.lastRunStatus = lastRunStatus;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastRunError
    *******************************************************************************/
   public String getLastRunError()
   {
      return (lastRunError);
   }



   /*******************************************************************************
    ** Setter for lastRunError
    *******************************************************************************/
   public void setLastRunError(String lastRunError)
   {
      this.lastRunError = lastRunError;
   }



   /*******************************************************************************
    ** Fluent setter for lastRunError
    *******************************************************************************/
   public AssistantScheduledTask withLastRunError(String lastRunError)
   {
      this.lastRunError = lastRunError;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (createDate);
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
   public AssistantScheduledTask withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (modifyDate);
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
   public AssistantScheduledTask withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
