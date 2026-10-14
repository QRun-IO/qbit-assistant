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


import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.classification.ConfiguredClassificationSource;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantDataAudit;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantScheduledRun;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.persistence.TranscriptRedactor;
import com.kingsrook.qbits.assistant.spi.AssistantModelProvider;
import com.kingsrook.qbits.assistant.spi.AssistantProviderType;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelResponse;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.actions.ai.AiFieldMetaData;
import com.kingsrook.qqq.backend.core.actions.ai.AiTableMetaData;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.actions.permissions.fieldaccess.ClassifiedValueKeySource;
import com.kingsrook.qqq.backend.core.actions.permissions.fieldaccess.ClassifiedValueKeySourceMetaData;
import com.kingsrook.qqq.backend.core.actions.permissions.fieldaccess.ClassifiedValueTokens;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.audits.AuditLevel;
import com.kingsrook.qqq.backend.core.model.metadata.audits.QAuditRules;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Secured-data egress canaries: inspect actual backend rows, core audits and logs,
 ** while independently proving that the eligible model still received the values.
 *******************************************************************************/
@Tag("redteam")
class TranscriptRedactionTest extends BaseAssistantTest
{
   private final String phi = "CANARY-PHI-" + UUID.randomUUID();
   private final String pii = "CANARY-PII-" + UUID.randomUUID();



   /*******************************************************************************
    ** Enable the fixture table and a local eligible provider.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      config.withDataClassification("patient", DataClassification.PHI).withProviderType(new PatientProviderType());
   }



   /*******************************************************************************
    ** Real core metadata, including table fallback, drives the redactor and core audits.
    *******************************************************************************/
   @BeforeEach
   void seed() throws Exception
   {
      admin();
      var table = qInstance.getTable("patient");
      table.withSupplementalMetaData(new AiTableMetaData().withClassification(DataClassification.PHI));
      table.getField("email").withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PII));
      table.getField("id").withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PUBLIC));
      table.getField("status").withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PUBLIC));
      table.withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD));
      config.withClassificationSource(new ConfiguredClassificationSource());
      assertThat(config.getClassificationSource().ofTable("patient")).isEqualTo(DataClassification.PHI);
      new UpdateAction().execute(new UpdateInput("patient").withRecord(new QRecord().withValue("id", 1).withValue("email", pii).withValue("diagnosis", phi)));
      config.getRuntime().getStore().insert(new AssistantProvider().withName("Eligible local provider").withProviderType("patient-test")
         .withModel("test-model").withLocation("LOCAL").withHipaaEligible(true).withEnabled(true).withIsDefault(true));
      config.getRuntime().getStore().insert(new AssistantDataAccess().withObjectType("TABLE").withObjectName("patient").withMode("READ_WRITE"));
      config.getRuntime().getDataAccessService().invalidate();
   }



   /*******************************************************************************
    ** Scan raw backend storage as an administrator, not just decoded transcript APIs.
    *******************************************************************************/
   private void admin()
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
   }



   /*******************************************************************************
    ** Every five-character window is sufficient to detect any leaked substring > 4.
    *******************************************************************************/
   private void assertNoCanaries(String text)
   {
      for(String canary : List.of(phi, pii))
      {
         for(int i = 0; i <= canary.length() - 5; i++)
         {
            assertThat(text).doesNotContain(canary.substring(i, i + 5));
         }
      }
   }



   /*******************************************************************************
    ** Query every registered assistant table and all core audit tables, without a row cap.
    *******************************************************************************/
   private void scanStorage() throws Exception
   {
      admin();
      for(String table : qInstance.getTables().keySet())
      {
         if(table.startsWith("assistant_") || table.toLowerCase().startsWith("audit"))
         {
            var rows = new QueryAction().execute(new QueryInput(table).withFilter(new QQueryFilter())).getRecords();
            assertNoCanaries(JsonUtils.toJson(rows));
         }
      }
   }



   /*******************************************************************************
    ** Query, get, input filters, presentation, split deltas, proposals and errors.
    ** Log capture includes exception detail and a provider thread without QContext.
    *******************************************************************************/
   @Test
   void canariesNeverReachStorageOrLogs() throws Exception
   {
      List<String> logs = new CopyOnWriteArrayList<>();
      AbstractAppender appender = new AbstractAppender("transcript-canary", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY)
      {
         /*******************************************************************************
          ** Retain the emitted log representation, including any exception.
          *******************************************************************************/
         @Override
         public void append(LogEvent event)
         {
            logs.add(event.getMessage().getFormattedMessage() + (event.getThrown() == null ? "" : event.getThrown().toString()));
         }
      };
      appender.start();
      Logger logger = (Logger) LogManager.getRootLogger();
      logger.addAppender(appender);
      try
      {
         provider.thenToolUse("query_records", Map.of("table", "patient"))
            .thenToolUse("get_record", Map.of("table", "patient", "id", "1"))
            .thenRespond(request ->
            {
               assertThat(JsonUtils.toJson(request)).contains(phi, pii);
               QLogger.getLogger(TranscriptRedactionTest.class).warn("Provider echoed " + phi + " " + pii, new IllegalStateException(phi + " " + pii));
               return (new ModelResponse().withParts(List.of(ModelPart.toolUse("present", "present_results", Map.of("summary", phi + " " + pii))))
                  .withStopReason(ModelResponse.STOP_TOOL_USE));
            })
            .thenToolUse("query_records", Map.of("table", "patient", "filter", filter("AND", "email", "EQUALS", List.of(pii))))
            .thenToolUse("update_record", Map.of("table", "patient", "id", "1", "values", Map.of("diagnosis", phi, "email", pii)))
            .thenText("x".repeat(43) + phi + " " + pii);
         var outcome = runner.run(AssistantTestFixture.ADMIN, phi + " " + pii);
         assertThat(outcome.errorCode()).isNull();
         assertThat(outcome.text()).contains(phi, pii);
         assertThat(outcome.toolCalls()).hasSize(5).allSatisfy(call -> assertThat(call.getOutcome()).isEqualTo("OK"));
         admin();
         var store = config.getRuntime().getStore();
         var proposals = store.query(AssistantPendingAction.TABLE_NAME, new QQueryFilter(), AssistantPendingAction.class);
         assertThat(proposals).singleElement().satisfies(action ->
         {
            assertThat(action.getParamsJson()).contains("changed:v1");
            assertThat(action.getStatus()).isEqualTo(AssistantPendingAction.STATUS_REJECTED);
         });
         assertThatThrownBy(() -> new AssistantApi(config.getRuntime(), QContext.getQSession()).confirmAction(proposals.getFirst().getId()))
            .hasMessageContaining("rejected");
         var events = store.query(AssistantTurnEvent.TABLE_NAME, new QQueryFilter(), AssistantTurnEvent.class);
         assertThat(events).isNotEmpty();
         assertThat(events.stream().map(AssistantTurnEvent::getDataJson).reduce("", String::concat)).contains("changed:v1");
         scanStorage();

         provider.thenToolUse("get_record", Map.of("table", "patient", "id", "1"))
            .thenError(ModelProviderException.Kind.INVALID_REQUEST, phi + " " + pii);
         assertThat(runner.run(AssistantTestFixture.ADMIN, "Read patients again").errorCode()).isEqualTo("PROVIDER");
         scanStorage();
         assertThat(logs).isNotEmpty();
         assertNoCanaries(String.join("\n", logs));
         assertThat(QLogger.redact(phi)).as("turn hook released").isEqualTo(phi);
      }
      finally
      {
         logger.removeAppender(appender);
         appender.stop();
      }
   }



   /*******************************************************************************
    ** Content written before discovery never reaches storage, even transiently.
    ** Includes the audit reason, scheduled summary, bulk log and redactions payloads.
    *******************************************************************************/
   @Test
   void provisionalPayloadsAndNoResultCriteria() throws Exception
   {
      admin();
      var store = config.getRuntime().getStore();
      try(TranscriptRedactor redactor = new TranscriptRedactor(qInstance, config.getClassificationSource()))
      {
         redactor.attach();
         var audit = store.insert(new AssistantDataAudit().withUserId(AssistantTestFixture.ADMIN).withReason(phi + " " + pii));
         store.insert(new AssistantScheduledRun().withUserId(AssistantTestFixture.ADMIN).withTaskId(1).withScheduledFor(Instant.now()).withSummary(phi));
         store.insert(new AssistantToolCall().withUserId(AssistantTestFixture.ADMIN).withToolName("bulk_load_preview")
            .withInputJson(JsonUtils.toJson(Map.of("email", pii))).withResultJson(phi).withRedactionsJson(JsonUtils.toJson(List.of(phi, pii))));
         scanStorage();
         redactor.input(Map.of("table", "patient", "filter", Map.of("subFilters", List.of(filter("AND", "email", "EQUALS", List.of(pii))))));
         redactor.observe("patient", "diagnosis", 1, phi, phi);
         store.updateFields(AssistantDataAudit.TABLE_NAME, new QRecord().withValue("id", audit.getId()).withValue("reason", pii + " " + phi));
         redactor.flush(store);
         scanStorage();
      }
      provider.thenToolUse("query_records", Map.of("table", "patient", "filter", filter("AND", "email", "EQUALS", List.of(pii + "-absent"))))
         .thenText(pii + "-absent");
      runner.run(AssistantTestFixture.ADMIN, "Find missing patient");
      scanStorage();
   }



   /*******************************************************************************
    ** A late tool retains the log hook; incomplete/cancelled turns keep placeholders
    ** and cannot turn a provisional pending payload into an executable empty write.
    *******************************************************************************/
   @Test
   void lateWorkerKeepsProtectionAndFailsClosed() throws Exception
   {
      admin();
      var store = config.getRuntime().getStore();
      TranscriptRedactor redactor = new TranscriptRedactor(qInstance, config.getClassificationSource());
      try
      {
         redactor.attach();
         redactor.observe("patient", "diagnosis", 1, phi, phi);
         assertThat(redactor.retainWorker()).isTrue();
         var action = store.insert(new AssistantPendingAction().withUserId(AssistantTestFixture.ADMIN)
            .withStatus(AssistantPendingAction.STATUS_PENDING).withParamsJson(JsonUtils.toJson(Map.of("diagnosis", phi))));
         assertThat(store.getPendingAction(AssistantTestFixture.ADMIN, action.getId()).getStatus()).isEqualTo("PREPARING");
         redactor.flush(store);
         assertThat(store.getPendingAction(AssistantTestFixture.ADMIN, action.getId()).getStatus()).isEqualTo(AssistantPendingAction.STATUS_REJECTED);
         redactor.close();
         assertThat(redactor.retainWorker()).isFalse();
         assertThat(QLogger.redact(phi)).isEqualTo("changed:v1");
         redactor.releaseWorker();
         assertThat(QLogger.redact(phi)).isEqualTo(phi);
         scanStorage();
      }
      finally
      {
         redactor.close();
      }
   }



   /*******************************************************************************
    ** Equal values keep distinct record bindings; canonical decimals and escaped text
    ** redact exactly and case-sensitively, with no mutation of model-facing objects.
    *******************************************************************************/
   @Test
   void recordBindingsAndCanonicalVariants() throws Exception
   {
      admin();
      qInstance.withSupplementalMetaData(new ClassifiedValueKeySourceMetaData().withKeySourceInstance(new ClassifiedValueKeySource()
      {
         /*******************************************************************************
          ** Deterministic test key, never a production secret.
          *******************************************************************************/
         @Override
         public Optional<KeyAndId> instanceKey()
         {
            return (Optional.of(new KeyAndId(new byte[32], "test")));
         }



         /*******************************************************************************
          ** No per-record keys in this instance-key test.
          *******************************************************************************/
         @Override
         public Optional<KeyAndId> keyForRecordField(String table, String field, Serializable pk, boolean create)
         {
            return (Optional.empty());
         }
      }));
      var table = qInstance.getTable("patient");
      try(TranscriptRedactor redactor = new TranscriptRedactor(qInstance, config.getClassificationSource()))
      {
         redactor.observe("patient", "diagnosis", 1, phi, phi);
         redactor.observe("patient", "diagnosis", 2, phi, phi);
         String first = ClassifiedValueTokens.forRecordField(table, table.getField("diagnosis"), 1, phi);
         String second = ClassifiedValueTokens.forRecordField(table, table.getField("diagnosis"), 2, phi);
         assertThat(first).isNotEqualTo(second);
         String stored = redactor.payload(JsonUtils.toJson(Map.of("table", "patient", "records", List.of(
            Map.of("id", 1, "values", Map.of("diagnosis", phi)), Map.of("id", 2, "values", Map.of("diagnosis", phi))))));
         assertThat(stored).contains(first, second).doesNotContain(phi);
         assertThat(ClassifiedValueTokens.verifyRecordField(table, table.getField("diagnosis"), 1, phi, first)).isTrue();
         redactor.observe("patient", "diagnosis", 3, new BigDecimal("123.4500"), "123.4500");
         String numeric = redactor.redact("123.4500 and 123.45");
         assertThat(numeric).doesNotContain("123.45").contains("hmac:v1:test:");
         assertThat(redactor.redact(phi.toLowerCase())).isEqualTo(phi.toLowerCase());
         redactor.variant("patient", "diagnosis", "1", phi.substring(0, 25));
         assertThat(redactor.redact(phi.substring(0, 25))).isEqualTo(first);
         redactor.truncated("Detail: " + pii, "Detail: " + pii.substring(0, 20));
         redactor.observe("patient", "email", 1, pii, pii);
         redactor.truncated("Detail: " + pii, "Detail: " + pii.substring(0, 20));
         assertThat(redactor.redact("Detail: " + pii.substring(0, 20))).doesNotContain("CANARY");
         assertThat(redactor.redact(first)).isEqualTo(first);
         redactor.observe("patient", "diagnosis", 4, "quote\"line\n", "quote\"line\n");
         assertThat(redactor.payload(JsonUtils.toJson(Map.of("text", "quote\"line\n")))).doesNotContain("quote");
      }
   }



   /*******************************************************************************
    ** No cloud dependency: both rows use the recording provider.
    *******************************************************************************/
   class PatientProviderType implements AssistantProviderType
   {
      /*******************************************************************************
       ** Fixture adapter.
       *******************************************************************************/
      @Override
      public String getType()
      {
         return ("patient-test");
      }



      /*******************************************************************************
       ** Fixture adapter.
       *******************************************************************************/
      @Override
      public String getLabel()
      {
         return ("Test");
      }



      /*******************************************************************************
       ** Fixture adapter.
       *******************************************************************************/
      @Override
      public List<String> getModels()
      {
         return (List.of("test-model"));
      }



      /*******************************************************************************
       ** Fixture adapter.
       *******************************************************************************/
      @Override
      public String getDefaultApiKeyEnvName()
      {
         return (null);
      }



      /*******************************************************************************
       ** Fixture adapter.
       *******************************************************************************/
      @Override
      public String getDefaultBaseUrl()
      {
         return ("http://localhost");
      }



      /*******************************************************************************
       ** Fixture adapter.
       *******************************************************************************/
      @Override
      public AssistantModelProvider create(String apiKey, String baseUrl)
      {
         return (provider);
      }



   }
}
