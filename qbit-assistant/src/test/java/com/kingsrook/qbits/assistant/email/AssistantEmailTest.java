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

package com.kingsrook.qbits.assistant.email;


import java.time.Instant;
import java.util.List;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.classification.DataClassification;
import com.kingsrook.qbits.email.EmailQBitConfig;
import com.kingsrook.qbits.email.EmailQBitProducer;
import com.kingsrook.qbits.email.metadata.EmailTables;
import com.kingsrook.qbits.email.permissions.EmailPermissions;
import com.kingsrook.qbits.email.support.SystemWrites;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Scheduled-result email through qbit-email, with the email qbit produced on a
 ** memory-backend instance, and the behavior when it is not.
 *******************************************************************************/
class AssistantEmailTest
{
   private static final String RUN_TABLE = "assistant_scheduled_run";

   private QInstance           instance;
   private AssistantQBitConfig config;



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void setup() throws Exception
   {
      instance = newInstance(true);
      config = new AssistantQBitConfig().withEmailExternalApproved(true);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void cleanup()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** A memory-backend instance, with or without the email qbit.
    *******************************************************************************/
   private static QInstance newInstance(boolean withEmail) throws Exception
   {
      MemoryRecordStore.fullReset();
      QContext.clear();
      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData().withName("memory").withBackendType(MemoryBackendModule.class));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      if(withEmail)
      {
         new EmailQBitProducer().withQBitConfig(new EmailQBitConfig().withBackendName("memory").withPublicBaseUrl("https://app.example.com")
            .withSecretResolver(name -> "QQQ_EMAIL_UNSUBSCRIBE_KEY".equals(name) ? "test-unsubscribe-key" : null)).produce(qInstance);
      }
      if(withEmail)
      {
         QContext.init(qInstance, new QSession().withPermissions(EmailPermissions.ALL));
         new InsertAction().execute(new InsertInput(EmailTables.PROVIDER).withRecords(List.of(new QRecord().withValue("name", "relay").withValue("label", "Relay")
            .withValue("kind", "SMTP").withValue("host", "127.0.0.1").withValue("port", 25).withValue("security", "NONE")
            .withValue("fromAddress", "sender@example.com").withValue("enabled", true).withValue("isDefault", true))));
      }
      return (qInstance);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private ScheduledResultNotice notice(int runId, String summary, DataClassification classification, boolean optIn)
   {
      return (new ScheduledResultNotice(RUN_TABLE, runId, "Weekly review", Instant.parse("2026-10-12T09:00:00Z"), summary, classification,
         "owner@example.com", optIn, "https://app.example.com/app/" + RUN_TABLE + "/" + runId));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<QRecord> messages() throws Exception
   {
      return (SystemWrites.query(EmailTables.MESSAGE, new QQueryFilter()));
   }



   /*******************************************************************************
    ** A PUBLIC result queues one NOTIFICATION message from the template, with
    ** the idempotency key and the linked run; a repeat is a duplicate.
    *******************************************************************************/
   @Test
   void publicResultQueuesTemplateMessage() throws Exception
   {
      assertThat(AssistantEmail.isAvailable(instance)).isTrue();
      assertThat(AssistantEmail.sendScheduledResult(instance, config, notice(7, "Three items need attention.", null, true))).isEqualTo(ScheduledEmailStatus.QUEUED);

      List<QRecord> messages = messages();
      assertThat(messages).hasSize(1);
      QRecord message = messages.get(0);
      assertThat(message.getValueString("category")).isEqualTo("NOTIFICATION");
      assertThat(message.getValueString("toAddresses")).isEqualTo("owner@example.com");
      assertThat(message.getValueString("idempotencyKey")).isEqualTo("assistant:scheduled:7");
      assertThat(message.getValueString("linkedTableName")).isEqualTo(RUN_TABLE);
      assertThat(message.getValueString("linkedRecordId")).isEqualTo("7");
      assertThat(message.getValueString("senderQbit")).isEqualTo("assistant");
      assertThat(message.getValueString("subject")).contains("Weekly review");
      assertThat(message.getValueString("htmlBody")).contains("Three items need attention.").contains("https://app.example.com/app/assistant_scheduled_run/7");
      QRecord template = SystemWrites.query(EmailTables.TEMPLATE, new QQueryFilter()).stream().filter(t -> "assistant_scheduled_result".equals(t.getValueString("name"))).findFirst().orElseThrow();
      assertThat(template.getValueString("category")).isEqualTo("NOTIFICATION");
      assertThat(template.getValueString("ownerQbit")).isEqualTo("assistant");
      assertThat(message.getValueInteger("templateId")).isEqualTo(template.getValueInteger("id"));

      assertThat(AssistantEmail.sendScheduledResult(instance, config, notice(7, "Three items need attention.", null, true))).isEqualTo(ScheduledEmailStatus.DUPLICATE);
      assertThat(messages()).hasSize(1);
   }



   /*******************************************************************************
    ** Above PUBLIC, only the link goes out, with the secure-message pattern;
    ** the summary and task name are not in the message.
    *******************************************************************************/
   @Test
   void classifiedResultSendsLinkOnly() throws Exception
   {
      assertThat(AssistantEmail.sendScheduledResult(instance, config, notice(8, "Patient Jane Doe has diabetes", DataClassification.PHI, true))).isEqualTo(ScheduledEmailStatus.QUEUED);

      QRecord message = messages().get(0);
      assertThat(message.getValueString("category")).isEqualTo("SECURITY");
      assertThat(message.getValueString("htmlBody")).contains("https://app.example.com/app/assistant_scheduled_run/8").doesNotContain("Jane Doe").doesNotContain("Weekly review");
      assertThat(message.getValueString("textBody")).doesNotContain("Jane Doe");
      assertThat(message.getValueString("subject")).doesNotContain("Weekly review");
      assertThat(message.getValueString("idempotencyKey")).isEqualTo("assistant:scheduled:8");
      assertThat(message.getValueString("linkedRecordId")).isEqualTo("8");
   }



   /*******************************************************************************
    ** Each gate on its own: opt-in, the external approval, a recipient.
    *******************************************************************************/
   @Test
   void gates() throws Exception
   {
      assertThat(AssistantEmail.sendScheduledResult(instance, config, notice(1, "x", null, false))).isEqualTo(ScheduledEmailStatus.NOT_REQUESTED);
      assertThat(AssistantEmail.sendScheduledResult(instance, config, null)).isEqualTo(ScheduledEmailStatus.NOT_REQUESTED);
      assertThat(AssistantEmail.sendScheduledResult(instance, new AssistantQBitConfig(), notice(2, "x", null, true))).isEqualTo(ScheduledEmailStatus.NOT_APPROVED);

      ScheduledResultNotice noAddress = new ScheduledResultNotice(RUN_TABLE, 3, "t", Instant.now(), "x", null, "user-17", true, "https://app.example.com/app/x/3");
      assertThat(AssistantEmail.sendScheduledResult(instance, config, noAddress)).isEqualTo(ScheduledEmailStatus.NO_RECIPIENT);
      assertThat(AssistantEmail.sendScheduledResult(instance, config, new ScheduledResultNotice(RUN_TABLE, 3, "t", Instant.now(), "x", null, null, true, "https://app.example.com/a"))).isEqualTo(ScheduledEmailStatus.NO_RECIPIENT);
      assertThat(messages()).isEmpty();

      config.withEmailRecipientResolver(id -> "user-17".equals(id) ? " user17@example.com " : null);
      assertThat(AssistantEmail.sendScheduledResult(instance, config, noAddress)).isEqualTo(ScheduledEmailStatus.QUEUED);
      assertThat(messages().get(0).getValueString("toAddresses")).isEqualTo("user17@example.com");
   }



   /*******************************************************************************
    ** The outbox refusing the message (here a relative link) is reported, not thrown.
    *******************************************************************************/
   @Test
   void refusedIsReported() throws Exception
   {
      ScheduledResultNotice relative = new ScheduledResultNotice(RUN_TABLE, 4, "t", Instant.now(), "x", null, "owner@example.com", true, "/app/x/4");
      assertThat(AssistantEmail.sendScheduledResult(instance, config, relative)).isEqualTo(ScheduledEmailStatus.REFUSED);
      assertThat(messages()).isEmpty();
   }



   /*******************************************************************************
    ** The summary is one line and cut short.
    *******************************************************************************/
   @Test
   void summaryIsShort() throws Exception
   {
      assertThat(AssistantEmailSender.summary(null)).isNull();
      assertThat(AssistantEmailSender.summary("a\n\n  b")).isEqualTo("a b");
      assertThat(AssistantEmailSender.summary("x".repeat(1000))).hasSize(280).endsWith("...");

      AssistantEmail.sendScheduledResult(instance, config, notice(5, "y".repeat(2000), null, true));
      assertThat(messages().get(0).getValueString("htmlBody")).doesNotContain("y".repeat(281));
   }



   /*******************************************************************************
    ** Without the email qbit in the instance nothing is queued and nothing breaks.
    *******************************************************************************/
   @Test
   void withoutEmailQbit() throws Exception
   {
      QInstance bare = newInstance(false);
      assertThat(AssistantEmail.isAvailable(bare)).isFalse();
      assertThat(AssistantEmail.isAvailable(null)).isFalse();
      assertThat(AssistantEmail.sendScheduledResult(bare, config, notice(6, "x", null, true))).isEqualTo(ScheduledEmailStatus.NOT_AVAILABLE);
   }
}
