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

package com.kingsrook.qbits.assistant.metadata;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantFeedback;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantUsageDaily;
import com.kingsrook.qbits.assistant.processes.AssistantRetentionStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.QScheduleMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Builds the QBit's metadata (spec section 4, owner decision D6): standard,
 ** visible QQQ tables on the configured backend, the owner security key type,
 ** the conversation possible-value source, the retention process and the
 ** "Assistant Admin" app.
 **
 ** Security:
 ** - Every user-owned table carries userId and a record security lock on it
 **   (key type ownerSecurityKeyType, default assistantUserId). The panel
 **   routes put the user's own id in their session, so reads and writes of
 **   these tables through QQQ actions only ever touch the user's own rows.
 ** - The key type's all-access key (assistantUserIdAllAccess) lets admins see
 **   everyone's rows; hosts grant it to users holding assistant.admin with
 **   AssistantSecurity.applySessionKeys in their authentication customizer.
 ** - Tables, app and process need assistant.admin in the generic UI / API.
 ** - assistantUsageDaily rows with a null userId are the INSTANCE rollup and
 **   are readable by any session (the lock allows null).
 ** - The assistant's own tool kernel never exposes assistant* tables to the
 **   model (exposure NONE by name).
 *******************************************************************************/
public class AssistantMetaDataBuilder
{
   public static final String APP_NAME               = "assistantAdmin";
   public static final String RETENTION_PROCESS_NAME = "assistantRetention";

   private final AssistantQBitConfig config;



   /*******************************************************************************
    **
    *******************************************************************************/
   public AssistantMetaDataBuilder(AssistantQBitConfig config)
   {
      this.config = config;
   }



   /*******************************************************************************
    ** The all-access key name of the owner key type.
    *******************************************************************************/
   public static String allAccessKeyName(String ownerKeyType)
   {
      return (ownerKeyType + "AllAccess");
   }



   /*******************************************************************************
    ** The owner security key type.
    *******************************************************************************/
   public QSecurityKeyType buildSecurityKeyType()
   {
      return (new QSecurityKeyType()
         .withName(config.getOwnerSecurityKeyType())
         .withAllAccessKeyName(allAccessKeyName(config.getOwnerSecurityKeyType())));
   }



   /*******************************************************************************
    ** All tables, conversations first.
    *******************************************************************************/
   public List<QTableMetaData> buildTables() throws QException
   {
      List<QTableMetaData> tables = new ArrayList<>();

      tables.add(owned(base(AssistantConversation.TABLE_NAME, "Assistant Conversation", "forum", AssistantConversation.class), false)
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("title")
         .withoutCapabilities(Capability.TABLE_INSERT));
      text(tables.get(0), "contextJson");

      tables.add(text(readOnly(owned(base(AssistantMessage.TABLE_NAME, "Assistant Message", "chat", AssistantMessage.class), false)), "partsJson")
         .withRecordLabelFormat("%s #%s").withRecordLabelFields("role", "id"));

      tables.add(text(readOnly(owned(base(AssistantTurn.TABLE_NAME, "Assistant Turn", "sync_alt", AssistantTurn.class), false)), "providerRequestIds")
         .withRecordLabelFormat("Turn #%s").withRecordLabelFields("id"));

      tables.add(text(readOnly(owned(base(AssistantTurnEvent.TABLE_NAME, "Assistant Turn Event", "stream", AssistantTurnEvent.class), false)), "dataJson")
         .withRecordLabelFormat("%s #%s").withRecordLabelFields("eventType", "seq"));

      tables.add(text(readOnly(owned(base(AssistantToolCall.TABLE_NAME, "Assistant Tool Call", "build", AssistantToolCall.class), false)), "inputJson", "resultJson", "redactionsJson")
         .withRecordLabelFormat("%s #%s").withRecordLabelFields("toolName", "id"));

      tables.add(text(readOnly(owned(base(AssistantFeedback.TABLE_NAME, "Assistant Feedback", "thumbs_up_down", AssistantFeedback.class), false)), "comment"));

      tables.add(base(AssistantPolicy.TABLE_NAME, "Assistant Policy", "policy", AssistantPolicy.class)
         .withRecordLabelFormat("%s").withRecordLabelFields("name"));

      tables.add(readOnly(owned(base(AssistantUsageDaily.TABLE_NAME, "Assistant Usage (Daily)", "query_stats", AssistantUsageDaily.class), true))
         .withRecordLabelFormat("%s %s").withRecordLabelFields("scope", "usageDate"));

      return (tables);
   }



   /*******************************************************************************
    ** Possible value source over conversations (for the conversationId fields).
    *******************************************************************************/
   public QPossibleValueSource buildConversationPossibleValueSource()
   {
      return (QPossibleValueSource.newForTable(AssistantConversation.TABLE_NAME));
   }



   /*******************************************************************************
    ** The retention process (spec section 4), scheduled daily when a scheduler
    ** is configured.
    *******************************************************************************/
   public QProcessMetaData buildRetentionProcess()
   {
      QProcessMetaData process = new QProcessMetaData()
         .withName(RETENTION_PROCESS_NAME)
         .withLabel("Assistant Retention")
         .withIcon(new QIcon("auto_delete"))
         .withPermissionRules(adminRules())
         .withStep(new QBackendStepMetaData()
            .withName("purge")
            .withCode(new QCodeReference(AssistantRetentionStep.class)));

      if(StringUtils.hasContent(config.getRetentionSchedulerName()))
      {
         process.withSchedule(new QScheduleMetaData()
            .withSchedulerName(config.getRetentionSchedulerName())
            .withRepeatSeconds(24 * 60 * 60));
      }
      return (process);
   }



   /*******************************************************************************
    ** The Assistant Admin app over the tables and the retention process.
    *******************************************************************************/
   public QAppMetaData buildAdminApp(List<QTableMetaData> tables, QProcessMetaData retention)
   {
      QAppMetaData app = new QAppMetaData()
         .withName(APP_NAME)
         .withLabel("Assistant Admin")
         .withIcon(new QIcon("smart_toy"))
         .withPermissionRules(adminRules());
      for(QTableMetaData table : tables)
      {
         app.withChild(table);
      }
      if(retention != null)
      {
         app.withChild(retention);
      }
      return (app);
   }



   /*******************************************************************************
    ** assistant.admin, through the custom checker.
    *******************************************************************************/
   public static QPermissionRules adminRules()
   {
      return (new QPermissionRules().withCustomPermissionChecker(new QCodeReference(AssistantAdminPermissionChecker.class)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private QTableMetaData base(String name, String label, String icon, Class<? extends QRecordEntity> entity) throws QException
   {
      QTableMetaData table = new QTableMetaData()
         .withName(name)
         .withLabel(label)
         .withIcon(new QIcon(icon))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withPermissionRules(adminRules())
         .withFieldsFromEntity(entity);

      QFieldMetaData conversationId = table.getFields().get("conversationId");
      if(conversationId != null)
      {
         conversationId.setPossibleValueSourceName(AssistantConversation.TABLE_NAME);
      }
      return (table);
   }



   /*******************************************************************************
    ** Lock the table to its owner (userId).
    *******************************************************************************/
   private QTableMetaData owned(QTableMetaData table, boolean allowNullOwner)
   {
      return (table.withRecordSecurityLock(new RecordSecurityLock()
         .withSecurityKeyType(config.getOwnerSecurityKeyType())
         .withFieldName("userId")
         .withNullValueBehavior(allowNullOwner ? RecordSecurityLock.NullValueBehavior.ALLOW : RecordSecurityLock.NullValueBehavior.DENY)));
   }



   /*******************************************************************************
    ** Only the qbit writes these (admins may still delete rows).
    *******************************************************************************/
   private static QTableMetaData readOnly(QTableMetaData table)
   {
      return (table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE));
   }



   /*******************************************************************************
    ** Unbounded text columns.
    *******************************************************************************/
   private static QTableMetaData text(QTableMetaData table, String... names)
   {
      for(String name : names)
      {
         table.getField(name).setType(QFieldType.TEXT);
      }
      return (table);
   }

}
