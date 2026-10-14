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


import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qbits.assistant.model.AssistantDataAudit;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.processes.AssistantRetentionStep;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** PHI routing through real fixture UI API tools and persisted provider rows.
 *******************************************************************************/
class PhiRoutingTest extends BaseAssistantTest
{
   /*******************************************************************************
    ** Classify the optional patient fixture and register a local recording provider type.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      config.withDataClassification("patient", DataClassification.PHI)
         .withDataClassification("patient", "id", DataClassification.PUBLIC)
         .withDataClassification("patient", "email", DataClassification.PII)
         .withDataClassification("patient", "status", DataClassification.PUBLIC)
         .withProviderType(new PatientProviderType());
   }



   /*******************************************************************************
    ** Two rows exercise routing eligibility independently of the adapter.
    *******************************************************************************/
   private AssistantProvider providers(Boolean eligible) throws Exception
   {
      admin();
      AssistantStore store = config.getRuntime().getStore();
      AssistantProvider local = store.insert(new AssistantProvider().withName("Local eligible").withProviderType("patient-test").withModel("test-model")
         .withLocation("LOCAL").withHipaaEligible(true).withEnabled(true).withIsDefault(eligible));
      AssistantProvider cloud = store.insert(new AssistantProvider().withName("OpenRouter").withProviderType("patient-test").withModel("test-model")
         .withLocation("CLOUD").withHipaaEligible(false).withEnabled(true).withIsDefault(!eligible));
      return (eligible ? local : cloud);
   }



   /*******************************************************************************
    ** Fixture admin context for inspecting persisted state.
    *******************************************************************************/
   private void admin()
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
   }



   /*******************************************************************************
    ** Persisted data audit rows.
    *******************************************************************************/
   private List<AssistantDataAudit> audits() throws Exception
   {
      admin();
      return (config.getRuntime().getStore().query(AssistantDataAudit.TABLE_NAME, new QQueryFilter(), AssistantDataAudit.class));
   }



   /*******************************************************************************
    ** PUBLIC no-tool requests need no data audit and may use a non-eligible provider.
    *******************************************************************************/
   @Test
   void publicPageAllowed() throws Exception
   {
      providers(false);
      provider.thenText("hello");
      TurnRunner.Outcome outcome = runner.run(AssistantTestFixture.EDITOR, null, "hello", Map.of("tableName", "page"));
      assertThat(outcome.errorCode()).isNull();
      assertThat(audits()).isEmpty();
      assertThat(config.getRuntime().getStore().getTurn(AssistantTestFixture.EDITOR, outcome.turnId()).getClassification()).isEqualTo("PUBLIC");
      assertThat(provider.getRequests()).hasSize(1);
   }



   /*******************************************************************************
    ** The UI API read succeeds, but its PHI never reaches the second model request or a later turn.
    *******************************************************************************/
   @Test
   void phiToolResultBlockedAndHistoryStaysPhi() throws Exception
   {
      providers(false);
      provider.thenToolUse("query_records", Map.of("table", "patient", "limit", 5)).thenText("must not run");
      TurnRunner.Outcome outcome = runner.run(AssistantTestFixture.EDITOR, "read patients");
      assertThat(outcome.errorCode()).isEqualTo("PHI_BLOCKED");
      assertThat(outcome.end()).containsEntry("status", "ERROR");
      assertThat(outcome.toolCalls()).singleElement().satisfies(call ->
      {
         assertThat(call.getOutcome()).isEqualTo("OK");
         assertThat(call.getResultJson()).contains("PHI_DIAGNOSIS_SENTINEL");
      });
      assertThat(provider.getRequests()).hasSize(1);
      assertThat(audits()).singleElement().satisfies(row ->
      {
         assertThat(row.getDecision()).isEqualTo("BLOCKED");
         assertThat(row.getReason()).contains("patient", "OpenRouter").doesNotContain("PHI_DIAGNOSIS_SENTINEL");
         assertThat(row.getUserId()).isEqualTo(AssistantTestFixture.EDITOR);
      });
      assertThat(config.getRuntime().getStore().getConversation(AssistantTestFixture.EDITOR, outcome.conversationId()).getMaxClassification()).isEqualTo("PHI");
      TurnRunner.Outcome followup = runner.run(AssistantTestFixture.EDITOR, outcome.conversationId(), "hello", null);
      assertThat(followup.errorCode()).isEqualTo("PHI_BLOCKED");
      assertThat(provider.getRequests()).hasSize(1);
      assertThat(audits()).hasSize(2);
   }



   /*******************************************************************************
    ** An eligible provider may receive the tool result, and the call is audited.
    *******************************************************************************/
   @Test
   void eligibleProviderReceivesPhi() throws Exception
   {
      AssistantProvider row = providers(true);
      provider.thenToolUse("query_records", Map.of("table", "patient")).thenText("done");
      TurnRunner.Outcome outcome = runner.run(AssistantTestFixture.EDITOR, "read patients");
      assertThat(outcome.errorCode()).isNull();
      assertThat(provider.getRequests()).hasSize(2);
      assertThat(audits()).singleElement().satisfies(audit ->
      {
         assertThat(audit.getClassification()).isEqualTo("PHI");
         assertThat(audit.getDecision()).isEqualTo("ALLOWED");
         assertThat(audit.getProviderId()).isEqualTo(row.getId());
         assertThat(audit.getHipaaEligible()).isTrue();
         assertThat(audit.getTables()).contains("patient");
      });
   }



   /*******************************************************************************
    ** PHI page context stops before even the first request.
    *******************************************************************************/
   @Test
   void phiPageBlocked() throws Exception
   {
      providers(false);
      TurnRunner.Outcome outcome = runner.run(AssistantTestFixture.EDITOR, null, "this record", Map.of("tableName", "patient", "recordId", 1));
      assertThat(outcome.errorCode()).isEqualTo("PHI_BLOCKED");
      assertThat(provider.getRequests()).isEmpty();
      assertThat(audits()).singleElement().satisfies(audit -> assertThat(audit.getModel()).isEqualTo("test-model"));
   }



   /*******************************************************************************
    ** An explicit PII subset can be sent to a non-eligible provider and is audited.
    *******************************************************************************/
   @Test
   void piiSubsetAllowed() throws Exception
   {
      providers(false);
      provider.thenToolUse("query_records", Map.of("table", "patient", "fields", List.of("email"))).thenText("done");
      TurnRunner.Outcome outcome = runner.run(AssistantTestFixture.EDITOR, "read emails");
      assertThat(outcome.errorCode()).isNull();
      assertThat(provider.getRequests()).hasSize(2);
      assertThat(audits()).singleElement().satisfies(audit ->
      {
         assertThat(audit.getClassification()).isEqualTo("PII");
         assertThat(audit.getDecision()).isEqualTo("ALLOWED");
      });
      assertThat(outcome.toolCalls().get(0).getResultJson()).doesNotContain("PHI_DIAGNOSIS_SENTINEL");
   }



   /*******************************************************************************
    ** Extra input keys cannot hide the actual table or a select list ignored by the tool.
    *******************************************************************************/
   @Test
   void ignoredSelectionAndSpoofedTableCannotLowerClassification() throws Exception
   {
      providers(false);
      provider.thenToolUse("query_records", Map.of("table", "patient", "tableName", "page", "select", List.of("status")));
      TurnRunner.Outcome outcome = runner.run(AssistantTestFixture.EDITOR, "read patients");
      assertThat(outcome.errorCode()).isEqualTo("PHI_BLOCKED");
      assertThat(provider.getRequests()).hasSize(1);
      assertThat(audits()).singleElement().satisfies(audit -> assertThat(audit.getClassification()).isEqualTo("PHI"));
   }



   /*******************************************************************************
    ** Revoking eligibility between calls is honored despite the provider cache.
    *******************************************************************************/
   @Test
   void eligibilityRecheckedAfterTools() throws Exception
   {
      AssistantProvider row = providers(true);
      provider.thenRespond(request ->
      {
         try
         {
            QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
            config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", row.getId()).withValue("hipaaEligible", false));
         }
         catch(Exception e)
         {
            throw (new IllegalStateException(e));
         }
         finally
         {
            QContext.clear();
         }
         return (new com.kingsrook.qbits.assistant.provider.ModelResponse().withParts(List.of(
            com.kingsrook.qbits.assistant.provider.ModelPart.toolUse("read", "query_records", Map.of("table", "patient"))))
            .withStopReason(com.kingsrook.qbits.assistant.provider.ModelResponse.STOP_TOOL_USE));
      });
      assertThat(runner.run(AssistantTestFixture.EDITOR, "read patients").errorCode()).isEqualTo("PHI_BLOCKED");
      assertThat(provider.getRequests()).hasSize(1);
   }



   /*******************************************************************************
    ** HIPAA mode rejects selection, enabling, and the code-only fallback.
    *******************************************************************************/
   @Test
   void hipaaProviderRules() throws Exception
   {
      AssistantProvider row = providers(false);
      config.setHipaaMode(true);
      assertThatThrownBy(() -> config.getRuntime().getProviderService().resolve("test-model")).hasMessageContaining("HIPAA");
      assertThatThrownBy(() -> config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME,
         new QRecord().withValue("id", row.getId()).withValue("enabled", true))).hasMessageContaining("only HIPAA-eligible");
      assertThatThrownBy(() -> runner.run(AssistantTestFixture.EDITOR, "public question")).hasMessageContaining("HIPAA");
      admin();
      config.getRuntime().getStore().delete(AssistantProvider.TABLE_NAME, new QQueryFilter());
      config.getRuntime().getProviderService().invalidate();
      assertThatThrownBy(() -> config.getRuntime().getProviderService().resolve("test-model")).hasMessageContaining("persisted eligible");
      assertThat(provider.getRequests()).isEmpty();
   }



   /*******************************************************************************
    ** Code-only providers carry no operator eligibility fact, even when their name suggests local use.
    *******************************************************************************/
   @Test
   void codeOnlyProviderCannotReceivePhi() throws Exception
   {
      var outcome = runner.run(AssistantTestFixture.EDITOR, null, "patient", Map.of("tableName", "patient", "recordId", 1));
      assertThat(outcome.errorCode()).isEqualTo("PHI_BLOCKED");
      assertThat(provider.getRequests()).isEmpty();
      assertThat(audits()).singleElement().satisfies(row -> assertThat(row.getProviderId()).isNull());
   }



   /*******************************************************************************
    ** The dispatch guard also enforces HIPAA mode for PUBLIC requests and records the block.
    *******************************************************************************/
   @Test
   void hipaaDispatchGuardAuditsPublicBlock() throws Exception
   {
      AssistantProvider row = providers(false);
      AssistantTurn turn = config.getRuntime().getStore().insert(new AssistantTurn().withUserId(AssistantTestFixture.ADMIN).withConversationId(1));
      config.setHipaaMode(true);
      TurnClassification classification = new TurnClassification(config.getClassificationSource(), new com.kingsrook.qbits.assistant.model.AssistantConversation(), null);
      assertThatThrownBy(() -> TurnDataRouting.beforeModelCall(config.getRuntime(), classification, turn,
         new ProviderService.Active(row, "patient-test", "test-model", provider)))
         .isInstanceOf(TurnDataRouting.Blocked.class).hasMessageContaining("HIPAA mode");
      assertThat(audits()).singleElement().satisfies(audit ->
      {
         assertThat(audit.getClassification()).isEqualTo("PUBLIC");
         assertThat(audit.getDecision()).isEqualTo("BLOCKED");
      });
   }



   /*******************************************************************************
    ** PHI has its own retention even when HIPAA mode is off; audit follows conversation expiry.
    *******************************************************************************/
   @Test
   void phiRetention() throws Exception
   {
      providers(true);
      config.setHipaaRetentionDays(180);
      provider.thenToolUse("query_records", Map.of("table", "patient")).thenText("done").thenText("public");
      TurnRunner.Outcome phi = runner.run(AssistantTestFixture.EDITOR, "patients");
      TurnRunner.Outcome regular = runner.run(AssistantTestFixture.EDITOR, "hello");
      admin();
      AssistantStore store = config.getRuntime().getStore();
      assertThat(AssistantRetentionStep.purge(config, store, Instant.now().plus(Duration.ofDays(91))).conversationsDeleted()).isEqualTo(1);
      assertThat(store.getConversation(AssistantTestFixture.EDITOR, regular.conversationId())).isNull();
      assertThat(store.getConversation(AssistantTestFixture.EDITOR, phi.conversationId())).isNotNull();
      assertThat(audits()).hasSize(1);
      assertThat(AssistantRetentionStep.purge(config, store, Instant.now().plus(Duration.ofDays(181))).conversationsDeleted()).isEqualTo(1);
      assertThat(audits()).isEmpty();
   }



   /*******************************************************************************
    ** A stale PUBLIC turn cannot lower a conversation already raised by another turn.
    *******************************************************************************/
   @Test
   void persistedHistoryOverridesStaleSnapshot() throws Exception
   {
      admin();
      AssistantStore store = config.getRuntime().getStore();
      var conversation = store.createConversation(AssistantTestFixture.ADMIN, "history", null);
      var stale = new TurnClassification(config.getClassificationSource(), conversation, null);
      var phi = new TurnClassification(config.getClassificationSource(), conversation, Map.of("tableName", "patient", "recordId", 1));
      AssistantTurn first = store.insert(new AssistantTurn().withUserId(AssistantTestFixture.ADMIN).withConversationId(conversation.getId()));
      AssistantTurn second = store.insert(new AssistantTurn().withUserId(AssistantTestFixture.ADMIN).withConversationId(conversation.getId()));
      store.saveClassification(AssistantTestFixture.ADMIN, conversation.getId(), first.getId(), phi);
      store.saveClassification(AssistantTestFixture.ADMIN, conversation.getId(), second.getId(), stale);
      assertThat(stale.getClassification()).isEqualTo(DataClassification.PHI);
      assertThat(store.getTurn(AssistantTestFixture.ADMIN, second.getId()).getClassification()).isEqualTo("PHI");
      assertThat(store.getConversation(AssistantTestFixture.ADMIN, conversation.getId()).getMaxClassification()).isEqualTo("PHI");
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
