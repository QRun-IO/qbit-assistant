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

package com.kingsrook.qbits.assistant.persistence;


import java.util.Base64;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.engine.TurnEngine;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qbits.assistant.provider.CompletableStreamHandle;
import com.kingsrook.qbits.assistant.provider.ModelEventListener;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.StreamHandle;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Storage boundary tests inspect actual backend ciphertext and API plaintext, plus fail-closed initialization and logging.
 *******************************************************************************/
class HipaaStorageTest extends BaseAssistantTest
{
   private static final String KEY_NAME = "TEST_CONVERSATION_KEY";
   private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
   private Boolean failProvider = false;



   /*******************************************************************************
    ** Use a deterministic test key reference and an eligible local provider.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      provider = new ScriptedModelProvider()
      {
         /*******************************************************************************
          ** Deliberately simulate a provider that echoes private input in an error.
          *******************************************************************************/
         @Override
         public StreamHandle stream(ModelRequest request, ModelEventListener listener)
         {
            if(failProvider)
            {
               CompletableStreamHandle handle = new CompletableStreamHandle();
               handle.fail(new ModelProviderException(ModelProviderException.Kind.INVALID_REQUEST, 400, "PROMPT_SENTINEL PHI_DIAGNOSIS_SENTINEL")
                  .withProviderDetails("PROVIDER_TYPE_SENTINEL", "PROVIDER_MESSAGE_SENTINEL PHI_DIAGNOSIS_SENTINEL", "PROVIDER_REQUEST_SENTINEL"));
               return (handle);
            }
            return (super.stream(request, listener));
         }
      };
      config.withHipaaMode(true).withConversationEncryptionKeyEnvName(KEY_NAME).withSecretResolver(name -> KEY)
         .withDataClassification("patient", DataClassification.PHI)
         .withDataClassification("patient", "id", DataClassification.PUBLIC)
         .withDataClassification("patient", "email", DataClassification.PII)
         .withDataClassification("patient", "status", DataClassification.PUBLIC)
         .withProviderType(new StorageProviderType());
   }



   /*******************************************************************************
    ** Use the owning admin session for both API and raw-backend assertions.
    *******************************************************************************/
   private AssistantStore setupProvider() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
      AssistantStore store = config.getRuntime().getStore();
      store.insert(new AssistantProvider().withName("Local").withProviderType("storage-test").withModel("test-model").withLocation("LOCAL")
         .withHipaaEligible(true).withEnabled(true).withIsDefault(true));
      return (store);
   }



   /*******************************************************************************
    ** All required payload fields encrypt in the backend; API, replay and compatibility stores decrypt.
    *******************************************************************************/
   @Test
   void encryptedAtRestPlainInApi() throws Exception
   {
      AssistantStore store = setupProvider();
      provider.thenToolUse("query_records", Map.of("table", "patient")).thenText("answer text");
      var outcome = runner.run(AssistantTestFixture.ADMIN, "PROMPT_SENTINEL");
      QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
      for(String table : List.of(AssistantConversation.TABLE_NAME, AssistantMessage.TABLE_NAME, AssistantToolCall.TABLE_NAME, AssistantTurnEvent.TABLE_NAME))
      {
         List<QRecord> raw = new QueryAction().execute(new QueryInput(table)).getRecords();
         assertThat(raw).isNotEmpty();
         List<String> fields = switch(table)
         {
            case AssistantConversation.TABLE_NAME -> List.of("title");
            case AssistantMessage.TABLE_NAME -> List.of("partsJson");
            case AssistantToolCall.TABLE_NAME -> List.of("inputJson", "resultJson");
            default -> List.of("dataJson");
         };
         for(QRecord record : raw)
         {
            for(String field : fields)
            {
               assertThat(record.getValueString(field)).startsWith("enc:v1:").doesNotContain("PROMPT_SENTINEL", "PHI_DIAGNOSIS_SENTINEL");
            }
         }
      }
      String api = JsonUtils.toJson(new AssistantApi(config.getRuntime(), QContext.getQSession()).getConversation(outcome.conversationId(), null, 100));
      assertThat(api).contains("PROMPT_SENTINEL", "answer text").doesNotContain("enc:v1:");
      assertThat(new AssistantStore().listMessages(AssistantTestFixture.ADMIN, outcome.conversationId(), null, 100)).anyMatch(message -> message.getPartsJson().contains("answer text"));
      AssistantPendingAction action = store.insert(new AssistantPendingAction().withUserId(AssistantTestFixture.ADMIN).withConversationId(outcome.conversationId()).withParamsJson("payload"));
      assertThat(new QueryAction().execute(new QueryInput(AssistantPendingAction.TABLE_NAME)).getRecords().get(0).getValueString("paramsJson")).startsWith("enc:v1:");
      assertThat(store.getPendingAction(AssistantTestFixture.ADMIN, action.getId()).getParamsJson()).isEqualTo("payload");
      AssistantToolCall call = store.query(AssistantToolCall.TABLE_NAME, new QQueryFilter(), AssistantToolCall.class).get(0);
      call.setRedactionsJson("redactions");
      store.update(call);
      assertThat(new QueryAction().execute(new QueryInput(AssistantToolCall.TABLE_NAME)).getRecords().get(0).getValueString("redactionsJson")).startsWith("enc:v1:");
      assertThat(store.queryRecords(AssistantToolCall.TABLE_NAME, new QQueryFilter(), 10).get(0).getValueString("redactionsJson")).isEqualTo("redactions");
      String longTitle = "A".repeat(255);
      AssistantConversation longConversation = store.createConversation(AssistantTestFixture.ADMIN, longTitle, null);
      assertThat(store.getConversation(AssistantTestFixture.ADMIN, longConversation.getId()).getTitle()).isEqualTo(longTitle);
   }



   /*******************************************************************************
    ** Authentication failure never returns ciphertext or unauthenticated plaintext; legacy values still load.
    *******************************************************************************/
   @Test
   void tamperingAndLegacyReads() throws Exception
   {
      AssistantStore store = setupProvider();
      AssistantConversation conversation = store.createConversation(AssistantTestFixture.ADMIN, "same title", null);
      AssistantConversation second = store.createConversation(AssistantTestFixture.ADMIN, "same title", null);
      List<QRecord> raw = new QueryAction().execute(new QueryInput(AssistantConversation.TABLE_NAME)).getRecords();
      assertThat(raw.get(0).getValueString("title")).isNotEqualTo(raw.get(1).getValueString("title"));
      String encoded = raw.get(0).getValueString("title");
      byte[] bytes = Base64.getDecoder().decode(encoded.substring("enc:v1:".length()));
      bytes[bytes.length - 1] ^= 1;
      new UpdateAction().execute(new UpdateInput(AssistantConversation.TABLE_NAME).withRecord(new QRecord().withValue("id", conversation.getId())
         .withValue("title", "enc:v1:" + Base64.getEncoder().encodeToString(bytes))));
      assertThatThrownBy(() -> store.getConversation(AssistantTestFixture.ADMIN, conversation.getId())).hasMessageContaining("ciphertext authentication failed");
      new UpdateAction().execute(new UpdateInput(AssistantConversation.TABLE_NAME).withRecord(new QRecord().withValue("id", conversation.getId()).withValue("title", "legacy title")));
      assertThat(store.getConversation(AssistantTestFixture.ADMIN, conversation.getId()).getTitle()).isEqualTo("legacy title");
      assertThat(store.getConversation(AssistantTestFixture.ADMIN, second.getId()).getTitle()).isEqualTo("same title");
      AssistantStore noKey = new AssistantStore(new AssistantQBitConfig());
      assertThatThrownBy(() -> noKey.getConversation(AssistantTestFixture.ADMIN, second.getId())).hasMessageContaining("encryption key");
   }



   /*******************************************************************************
    ** HIPAA startup fails on missing, malformed, short or invalid key references.
    *******************************************************************************/
   @Test
   void startupRequiresKey()
   {
      assertThatThrownBy(() -> new AssistantRuntime(qInstance, new AssistantQBitConfig().withHipaaMode(true))).hasMessageContaining("requires conversationEncryptionKeyEnvName");
      for(String value : List.of("", "bad base64!", Base64.getEncoder().encodeToString(new byte[16])))
      {
         assertThatThrownBy(() -> new AssistantRuntime(qInstance, new AssistantQBitConfig().withHipaaMode(true)
            .withConversationEncryptionKeyEnvName(KEY_NAME).withSecretResolver(name -> value))).hasMessageContaining("32");
      }
      assertThatThrownBy(() -> new AssistantRuntime(qInstance, new AssistantQBitConfig().withConversationEncryptionKeyEnvName("sk-secret"))).hasMessageContaining("environment variable NAME");
      assertThatThrownBy(() -> new AssistantStore(new AssistantQBitConfig().withConversationEncryptionKeyEnvName(KEY_NAME).withSecretResolver(name -> null))).hasMessageContaining("32 bytes");
      ConversationEncryption codec = new ConversationEncryption(config);
      assertThat(codec.decrypt(codec.encrypt("enc:v1:literal user text"))).isEqualTo("enc:v1:literal user text");
      assertThat(codec.encrypt(null)).isNull();
      assertThat(codec.decrypt(null)).isNull();
      assertThatThrownBy(() -> codec.decrypt("enc:v2:unknown")).hasMessageContaining("supported version");
      assertThatThrownBy(() -> codec.decrypt("enc:v1:AA==")).hasMessageContaining("authentication failed");
   }



   /*******************************************************************************
    ** Provider diagnostics may echo PHI; capture the actual QLogger output and inspect usage and turn messages.
    *******************************************************************************/
   @Test
   void logsAndErrorsDoNotEchoContent() throws Exception
   {
      AssistantStore store = setupProvider();
      var logger = QLogger.activateCollectingLoggerForClass(TurnEngine.class);
      try
      {
         provider.thenToolUse("query_records", Map.of("table", "patient")).thenText("done");
         runner.run(AssistantTestFixture.ADMIN, "PROMPT_SENTINEL");
         failProvider = true;
         var outcome = runner.run(AssistantTestFixture.ADMIN, "PROMPT_SENTINEL");
         assertThat(outcome.errorCode()).isEqualTo("PROVIDER");
         QContext.init(qInstance, AssistantTestFixture.ownerSession(AssistantTestFixture.ADMIN, config));
         assertThat(logger.getCollectedMessages()).isNotEmpty();
         for(var entry : logger.getCollectedMessages())
         {
            assertThat(entry.getMessage()).doesNotContain("PROMPT_SENTINEL", "PHI_DIAGNOSIS_SENTINEL", "PROVIDER_MESSAGE_SENTINEL", "PROVIDER_TYPE_SENTINEL", "PROVIDER_REQUEST_SENTINEL");
         }
         assertThat(JsonUtils.toJson(store.query(AssistantUsage.TABLE_NAME, new QQueryFilter(), AssistantUsage.class)))
            .doesNotContain("PROMPT_SENTINEL", "PHI_DIAGNOSIS_SENTINEL", "PROVIDER_MESSAGE_SENTINEL");
         assertThat(outcome.events(com.kingsrook.qbits.assistant.engine.TurnEvent.ERROR).toString()).doesNotContain("PROVIDER_MESSAGE_SENTINEL", "PROMPT_SENTINEL");
      }
      finally
      {
         QLogger.deactivateCollectingLoggerForClass(TurnEngine.class);
      }
   }



   /*******************************************************************************
    ** Recording provider adapter for eligible rows.
    *******************************************************************************/
   class StorageProviderType implements AssistantProviderType
   {
      /*******************************************************************************
       ** Fixture adapter.
       *******************************************************************************/
      @Override
      public String getType()
      {
         return ("storage-test");
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
