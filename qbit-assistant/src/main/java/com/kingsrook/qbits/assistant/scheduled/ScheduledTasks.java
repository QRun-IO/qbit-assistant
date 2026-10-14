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

package com.kingsrook.qbits.assistant.scheduled;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantScheduledTask;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.quartz.CronExpression;


/*******************************************************************************
 ** Schedule and credential validation shared by saves, reviews and execution.
 *******************************************************************************/
public final class ScheduledTasks
{
   public static final List<String> APPROVAL_FIELDS = List.of("prompt", "runAsUserId", "credentialEnvName", "profileId", "writeModeRequested");



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ScheduledTasks()
   {
   }



   /*******************************************************************************
    ** Quartz computes wall-clock schedules in a validated IANA timezone.
    *******************************************************************************/
   public static Instant next(AssistantScheduledTask task, Instant after) throws QException
   {
      try
      {
         CronExpression cron = new CronExpression(task.getCronExpression());
         cron.setTimeZone(TimeZone.getTimeZone(ZoneId.of(task.getTimezone() == null ? "UTC" : task.getTimezone())));
         Date next = cron.getNextValidTimeAfter(Date.from(after));
         return (next == null ? null : next.toInstant());
      }
      catch(Exception e)
      {
         throw (new QException("Invalid Quartz cron expression or timezone."));
      }
   }



   /*******************************************************************************
    ** Refuse a pasted token or an unrelated host secret without echoing it.
    *******************************************************************************/
   public static void checkReference(AssistantQBitConfig config, String name) throws QException
   {
      if(name == null || !name.matches("[A-Z_][A-Z0-9_]*"))
      {
         throw (new QException("The scheduled credential must be an environment variable name."));
      }
      if(config.getAllowedScheduledCredentialEnvNames() == null || !config.getAllowedScheduledCredentialEnvNames().contains(name))
      {
         throw (new QException("The scheduled credential environment variable is not allowed."));
      }
   }



   /*******************************************************************************
    ** Resolve only after the reference passes the dedicated allowlist.
    *******************************************************************************/
   public static String credential(AssistantQBitConfig config, AssistantScheduledTask task) throws QException
   {
      checkReference(config, task.getCredentialEnvName());
      String token = config.getSecretResolver().apply(task.getCredentialEnvName());
      if(token == null || token.isBlank())
      {
         throw (new QException("The scheduled credential environment variable is not set."));
      }
      return (token);
   }



   /*******************************************************************************
    ** Presence only; no token characters are exposed in record screens.
    *******************************************************************************/
   public static String credentialStatus(AssistantQBitConfig config, AssistantScheduledTask task)
   {
      try
      {
         credential(config, task);
         return ("Set (from " + task.getCredentialEnvName() + ")");
      }
      catch(QException e)
      {
         return (e.getMessage());
      }
   }



   /*******************************************************************************
    ** Bind approval to all security-relevant settings, including null values.
    *******************************************************************************/
   public static String approvalHash(AssistantScheduledTask task) throws QException
   {
      return (hash(JsonUtils.toJson(APPROVAL_FIELDS.stream().map(name -> task.toQRecord().getValue(name)).toList())));
   }



   /*******************************************************************************
    ** SHA-256 displayed on Review and checked again on Result.
    *******************************************************************************/
   public static String hash(String text) throws QException
   {
      try
      {
         return (HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))));
      }
      catch(Exception e)
      {
         throw (new QException("Could not hash the scheduled task."));
      }
   }



   /*******************************************************************************
    ** An approval for an older task definition never authorizes writes.
    *******************************************************************************/
   public static Boolean writesApproved(AssistantScheduledTask task) throws QException
   {
      return (Boolean.TRUE.equals(task.getWriteModeRequested()) && task.getWriteModeApprovedAt() != null
         && task.getWriteModeApprovedBy() != null && Objects.equals(task.getWriteModeApprovalHash(), approvalHash(task)));
   }
}
