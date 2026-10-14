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

package com.kingsrook.qbits.assistant.rag;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.classification.DataClassification;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.engine.TurnClassification;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantRagSource;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.processes.KnowledgeIndexProcess;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qbits.assistant.provider.EmbeddingRequest;
import com.kingsrook.qbits.assistant.provider.EmbeddingResponse;
import com.kingsrook.qbits.assistant.provider.ModelPart;
import com.kingsrook.qbits.assistant.provider.ModelUsage;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.tools.KnowledgeTool;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qqq.backend.core.actions.ai.AiExposure;
import com.kingsrook.qqq.backend.core.actions.ai.AiFieldMetaData;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolOutcome;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolResult;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Real UI API indexing/retrieval, with deterministic local embeddings and no credentials.
 *******************************************************************************/
class RagTest extends BaseAssistantTest
{
   private final InMemoryVectorStore vectors = new InMemoryVectorStore();
   private final Embeddings embeddings = new Embeddings();
   private AssistantRagSource source;
   private AssistantModel model;
   private AssistantProvider embeddingProvider;



   /*******************************************************************************
    ** A hashed bag of words; capture batches solely to assert routing and masking.
    *******************************************************************************/
   static class Embeddings extends ScriptedModelProvider
   {
      final List<EmbeddingRequest> batches = new ArrayList<>();



      /*******************************************************************************
       ** Same text always yields the same 16-dimensional vector.
       *******************************************************************************/
      @Override
      public EmbeddingResponse embed(EmbeddingRequest request)
      {
         batches.add(request);
         List<float[]> vectors = new ArrayList<>();
         for(String text : request.input())
         {
            float[] vector = new float[16];
            for(String word : text.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+"))
            {
               vector[Math.floorMod(word.hashCode(), vector.length)]++;
            }
            vectors.add(vector);
         }
         return (new EmbeddingResponse(vectors, new ModelUsage().withInputTokens((long) request.input().size()), "embed-test"));
      }
   }



   /*******************************************************************************
    ** Keyless local type with the same provider instance for chat and embeddings.
    *******************************************************************************/
   class Type implements AssistantProviderType
   {
      /*******************************************************************************
       ** Test type identity.
       *******************************************************************************/
      @Override
      public String getType()
      {
         return ("rag-test");
      }



      /*******************************************************************************
       ** Test label.
       *******************************************************************************/
      @Override
      public String getLabel()
      {
         return ("RAG test");
      }



      /*******************************************************************************
       ** Chat default deliberately differs from the embedding model.
       *******************************************************************************/
      @Override
      public List<String> getModels()
      {
         return (List.of("chat", "bge-m3", "nomic-embed-text"));
      }



      /*******************************************************************************
       ** No credentials for this in-process fake.
       *******************************************************************************/
      @Override
      public String getDefaultApiKeyEnvName()
      {
         return (null);
      }



      /*******************************************************************************
       ** Never contacted; satisfies provider configuration validation.
       *******************************************************************************/
      @Override
      public String getDefaultBaseUrl()
      {
         return ("http://localhost:11434/v1");
      }



      /*******************************************************************************
       ** Local test location.
       *******************************************************************************/
      @Override
      public String getDefaultLocation(String kind)
      {
         return ("LOCAL");
      }



      /*******************************************************************************
       ** Return the test recording provider.
       *******************************************************************************/
      @Override
      public AssistantModelProvider create(String key, String base)
      {
         return (embeddings);
      }
   }



   /*******************************************************************************
    ** Configure only local fakes and the optional PHI fixture table.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig config)
   {
      config.withVectorStore(vectors).withProviderType(new Type()).withDataClassification("patient", DataClassification.PHI);
   }



   /*******************************************************************************
    ** Registered EMBEDDING model and a source in the real metadata-backed store.
    *******************************************************************************/
   @BeforeEach
   void sources() throws Exception
   {
      as(AssistantTestFixture.ADMIN);
      embeddingProvider = config.getRuntime().getStore().insert(new AssistantProvider().withName("Local embeddings").withProviderType("rag-test")
         .withModel("chat").withEnabled(true).withHipaaEligible(false));
      model = config.getRuntime().getStore().insert(new AssistantModel().withProviderId(embeddingProvider.getId()).withModelId("bge-m3")
         .withModelKind("EMBEDDING").withEmbeddingDimensions(16).withEnabled(true));
      config.getRuntime().getStore().insert(new AssistantModel().withProviderId(embeddingProvider.getId()).withModelId("chat").withEnabled(true));
      source = config.getRuntime().getStore().insert(new AssistantRagSource().withName("Blog knowledge").withTableName("blog_post")
         .withFieldNames("title,content").withTitleFieldName("title").withEmbeddingModelId(model.getId()));
      assertThat(source.getChunkChars()).isEqualTo(1500);
   }



   /*******************************************************************************
    ** The session used both for configuration reads and UI API calls.
    *******************************************************************************/
   private void as(String user)
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(user, config));
   }



   /*******************************************************************************
    ** Retrieval helper, retaining the caller's exact session UUID.
    *******************************************************************************/
   private AiToolResult search(String user, DataClassification classification) throws Exception
   {
      as(user);
      return (config.getRuntime().getKnowledgeTool().execute(config.getRuntime().getRagService().context(), "search-1", Map.of("query", "Drain upgrade verify", "limit", 8), classification, null, null));
   }



   /*******************************************************************************
    ** Unchanged content performs no embedding; disappeared records are removed.
    *******************************************************************************/
   @Test
   void indexesReconcilesAndReports() throws Exception
   {
      RagService rag = config.getRuntime().getRagService();
      RunBackendStepInput input = new RunBackendStepInput().withRecords(List.of(source.toQRecord()));
      RunBackendStepOutput output = new RunBackendStepOutput();
      KnowledgeIndexProcess.Review review = new KnowledgeIndexProcess.Review();
      review.runOnePage(input, output);
      assertThat(review.getProcessSummary(output, false)).hasSize(1);
      assertThat(embeddings.batches).isEmpty();
      KnowledgeIndexProcess.Index index = new KnowledgeIndexProcess.Index();
      index.runOnePage(input, new RunBackendStepOutput());
      assertThat(index.getProcessSummary(output, true)).hasSize(1);
      assertThat(vectors.count(source.getId())).isPositive();
      Integer calls = embeddings.batches.size();
      RagService.Result unchanged = rag.index(source);
      assertThat(unchanged.errors()).as(unchanged.reason()).isZero();
      assertThat(unchanged.embedded()).isZero();
      assertThat(unchanged.unchanged()).isEqualTo(vectors.count(source.getId()).intValue());
      assertThat(embeddings.batches).hasSize(calls);
      assertThat(vectors.chunks(source.getId())).allSatisfy(v -> assertThat(v.contentHash()).matches("[a-f0-9]{64}"));
      assertThat(ChunkVector.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
         .containsExactly("source", "table", "pk", "field", "chunkIndex", "contentHash", "embedding");
      assertThat(config.getRuntime().getUiApiGateway().invoke(new UiApiCall("DELETE", "/table/{tableName}/{primaryKey}")
         .withPathParam("tableName", "blog_post").withPathParam("primaryKey", "1").withSessionUUID(AssistantTestFixture.ADMIN)).status()).isEqualTo(200);
      RagService.Result changed = rag.index(source);
      assertThat(changed.errors()).as(changed.reason()).isZero();
      assertThat(changed.removed()).isEqualTo(2);
      assertThat(vectors.chunks(source.getId())).noneMatch(v -> v.pk().equals("1"));
      new KnowledgeIndexProcess.Clear().runOnePage(input, new RunBackendStepOutput());
      assertThat(vectors.count(source.getId())).isZero();
   }



   /*******************************************************************************
    ** UI read permission is checked again for every vector hit, with no identifiers
    ** or snippets leaking from denied records. Exposure is reused from get_record.
    *******************************************************************************/
   @Test
   void retrievesAsUserAndFiltersUiDenials() throws Exception
   {
      assertThat(config.getRuntime().getRagService().index(source).errors()).isZero();
      AiToolResult allowed = search(AssistantTestFixture.EDITOR, DataClassification.PUBLIC);
      assertThat(allowed.getOutcome()).as(allowed.getModelContent()).isEqualTo(AiToolOutcome.OK);
      assertThat(allowed.getRowCount()).isPositive();
      assertThat(allowed.getModelContent()).contains("Drain, upgrade, verify.", "/app/blog_post/").doesNotContain(AssistantTestFixture.HIDDEN_SENTINEL, AssistantTestFixture.PASSWORD_SENTINEL);
      assertThat(config.getRuntime().getKnowledgeTool().offered(PolicyService.ToolPolicy.defaults(), QContext.getQSession())).isTrue();
      AiToolResult denied = search(AssistantTestFixture.ANALYST, DataClassification.PUBLIC);
      assertThat(denied.getOutcome()).isEqualTo(AiToolOutcome.OK);
      assertThat(denied.getRowCount()).isZero();
      assertThat(denied.getSummary()).doesNotEndWith("filtered 0");
      assertThat(denied.getRecordRefs()).isEmpty();
      assertThat(denied.getModelContent()).doesNotContain("blog_post", "Drain", "/app/");
      assertThat(config.getRuntime().getKnowledgeTool().offered(PolicyService.ToolPolicy.defaults(), QContext.getQSession())).isFalse();
      assertThat(config.getRuntime().getUiApiGateway().invoke(new UiApiCall("GET", "/table/{tableName}/{primaryKey}")
         .withPathParam("tableName", "blog_post").withPathParam("primaryKey", "1").withSessionUUID(AssistantTestFixture.ANALYST)).status()).isEqualTo(403);
   }



   /*******************************************************************************
    ** Fresh field/table restrictions narrow both index validation and tool offering.
    *******************************************************************************/
   @Test
   void validatesExposureAndMasksBeforeEmbedding() throws Exception
   {
      RagService rag = config.getRuntime().getRagService();
      assertThat(rag.validate(new AssistantRagSource(source.toQRecord()).withFieldNames("internalNotes"))).isNotEmpty();
      assertThat(rag.validate(new AssistantRagSource(source.toQRecord()).withFieldNames("id"))).isNotEmpty();
      assertThat(rag.validate(new AssistantRagSource(source.toQRecord()).withTableName("assistant_model"))).isNotEmpty();
      assertThat(rag.validate(new AssistantRagSource(source.toQRecord()).withChunkChars(100).withChunkOverlapChars(100))).isNotEmpty();
      qInstance.getTable("blog_post").getField("content").withSupplementalMetaData(new AiFieldMetaData().withExposure(AiExposure.MASKED));
      assertThat(rag.index(source).errors()).isZero();
      assertThat(embeddings.batches.stream().flatMap(b -> b.input().stream())).noneMatch(s -> s.contains("Drain, upgrade"));
      assertThat(search(AssistantTestFixture.EDITOR, DataClassification.PUBLIC).getModelContent()).doesNotContain("Drain, upgrade");
      as(AssistantTestFixture.ADMIN);
      config.getRuntime().getStore().insert(new AssistantDataAccess().withObjectType("TABLE").withObjectName("blog_post").withMode("NONE"));
      assertThat(rag.validate(source)).anyMatch(s -> s.contains("NONE"));
      assertThat(config.getRuntime().getKnowledgeTool().offered(PolicyService.ToolPolicy.defaults(), QContext.getQSession())).isFalse();
      assertThat(search(AssistantTestFixture.EDITOR, DataClassification.PUBLIC).getRowCount()).isZero();
   }



   /*******************************************************************************
    ** PHI dispatch fails before text leaves the instance; eligible retrieval
    ** contributes table classification through the existing record-ref mechanism.
    *******************************************************************************/
   @Test
   void phiEmbeddingGuardAndClassification() throws Exception
   {
      AssistantRagSource patient = config.getRuntime().getStore().insert(new AssistantRagSource().withName("Patients").withTableName("patient")
         .withFieldNames("diagnosis").withEmbeddingModelId(model.getId()));
      RagService.Result blocked = config.getRuntime().getRagService().index(patient);
      assertThat(blocked.errors()).isEqualTo(1);
      assertThat(blocked.reason()).contains("HIPAA-eligible");
      assertThat(embeddings.batches).isEmpty();
      assertThat(search(AssistantTestFixture.EDITOR, DataClassification.PHI).getModelContent()).contains("HIPAA-eligible");
      assertThat(embeddings.batches).isEmpty();
      as(AssistantTestFixture.ADMIN);
      config.getRuntime().getStore().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", embeddingProvider.getId()).withValue("hipaaEligible", true));
      assertThat(config.getRuntime().getRagService().index(patient).errors()).isZero();
      as(AssistantTestFixture.EDITOR);
      AiToolResult result = config.getRuntime().getKnowledgeTool().execute(config.getRuntime().getRagService().context(), "phi", Map.of("query", "diagnosis", "tables", List.of("patient")), DataClassification.PUBLIC, null, null);
      assertThat(result.getRowCount()).isPositive();
      TurnClassification classification = new TurnClassification(config.getClassificationSource(), new AssistantConversation().withMaxClassification("PUBLIC"), null);
      classification.observe(ModelPart.toolUse("phi", KnowledgeTool.NAME, Map.of()), result);
      assertThat(classification.getClassification()).isEqualTo(DataClassification.PHI);
   }



   /*******************************************************************************
    ** Batches are capped, accounted as EMBED, and blocked by the kill switch.
    *******************************************************************************/
   @Test
   void batchesUsageAndSelection() throws Exception
   {
      new EmbeddingService(config.getRuntime()).embed(model.getId(), java.util.Collections.nCopies(130, "word"), DataClassification.PUBLIC, "blog_post", null, null);
      assertThat(embeddings.batches).extracting(b -> b.input().size()).containsExactly(64, 64, 2);
      assertThat(config.getRuntime().getStore().query(AssistantUsage.TABLE_NAME, new QQueryFilter(), AssistantUsage.class))
         .hasSize(3).allSatisfy(row -> assertThat(row.getPurpose()).isEqualTo("EMBED"));
      assertThat(config.getRuntime().getProviderService().resolveModel(model.getId(), false)).isNull();
      assertThat(config.getRuntime().getProviderService().forRow(new AssistantProvider(embeddingProvider.toQRecord()).withModel(null), "chat").model()).isEqualTo("chat");
      assertThatThrownBy(() -> config.getRuntime().getProviderService().resolveEmbedding(-1, false)).hasMessageContaining("EMBEDDING");
      assertThat(com.kingsrook.qbits.assistant.engine.ModelDiscovery.run(config.getRuntime(), embeddingProvider).ok()).isTrue();
      assertThat(config.getRuntime().getStore().listModels()).filteredOn(m -> m.getModelId().equals("nomic-embed-text"))
         .singleElement().satisfies(m ->
         {
            assertThat(m.getModelKind()).isEqualTo("EMBEDDING");
            assertThat(m.getEmbeddingDimensions()).isEqualTo(768);
         });
      config.getRuntime().getStore().insert(new AssistantPolicy().withName("Stop").withKillSwitch(true));
      assertThatThrownBy(() -> new EmbeddingService(config.getRuntime()).embed(model.getId(), List.of("blocked"), DataClassification.PUBLIC, "blog_post", null, null)).hasMessageContaining("kill switch");
      assertThat(embeddings.batches).hasSize(3);
      assertThat(com.kingsrook.qbits.assistant.engine.ConnectionTest.runModel(config.getRuntime(), model, AssistantTestFixture.ADMIN).status()).isEqualTo("ERROR");
   }



   /*******************************************************************************
    ** A full golden turn cites fresh knowledge and writes the standard tool log.
    *******************************************************************************/
   @Test
   @Tag("golden")
   void goldenAnswerCitesKnowledge() throws Exception
   {
      assertThat(config.getRuntime().getRagService().index(source).errors()).isZero();
      embeddings.thenToolUse(KnowledgeTool.NAME, Map.of("query", "Drain upgrade verify"))
         .thenRespond(com.kingsrook.qbits.assistant.testing.EvalSupport.answer(request ->
         {
            Map<String, Object> data = com.kingsrook.qbits.assistant.testing.EvalSupport.firstResult(request);
            Map<?, ?> record = (Map<?, ?>) ((List<?>) data.get("results")).getFirst();
            return (record.get("snippet") + " [Source](" + record.get("href") + ")");
         }));
      var outcome = runner.run(AssistantTestFixture.EDITOR, "Find the knowledge about safely upgrading");
      assertThat(outcome.errorCode()).isNull();
      assertThat(outcome.text()).contains("[Source](/app/blog_post/");
      assertThat(outcome.toolCalls()).singleElement().satisfies(call ->
      {
         assertThat(call.getToolName()).isEqualTo(KnowledgeTool.NAME);
         assertThat(call.getOutcome()).isEqualTo("OK");
         assertThat(call.getUiApiOperation()).startsWith("GET /table/");
      });
   }



   /*******************************************************************************
    ** Defensive vectors and chunk boundary behavior, including invalid inputs.
    *******************************************************************************/
   @Test
   void vectorAndChunkContracts() throws Exception
   {
      float[] raw = { 1, 0 };
      ChunkVector chunk = new ChunkVector(1, "table", "key", "body", 0, TextChunks.hash("text"), raw);
      raw[0] = 9;
      assertThat(chunk.embedding()).containsExactly(1, 0);
      chunk.embedding()[0] = 5;
      assertThat(chunk.embedding()).containsExactly(1, 0);
      InMemoryVectorStore store = new InMemoryVectorStore();
      store.upsert(List.of(chunk));
      assertThat(store.search(new float[] { 1, 0 }, 1, Set.of("table"))).singleElement().satisfies(hit -> assertThat(hit.score()).isEqualTo(1.0));
      assertThat(store.search(new float[] { 0, 0 }, 1, Set.of("table"))).isEmpty();
      assertThat(store.search(new float[] { 1 }, 1, Set.of("table"))).isEmpty();
      assertThat(store.search(raw, 1, Set.of())).isEmpty();
      store.replaceRecord(1, "table", "key", List.of());
      assertThat(store.count(1)).isZero();
      assertThat(TextChunks.split("abcdefgh", 4, 1)).containsExactly("abcd", "defg", "gh");
      assertThat(TextChunks.split("", 4, 0)).isEmpty();
      assertThatThrownBy(() -> TextChunks.split("a", 1, 1)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new ChunkVector(1, "t", "p", "f", 0, "invalid", raw)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new ChunkVector(1, "t", "p", "f", 0, TextChunks.hash("x"), new float[] { Float.NaN })).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new ScriptedModelProvider().embed(new EmbeddingRequest("chat", List.of("text")))).hasMessageContaining("unsupported");
   }



   /*******************************************************************************
    ** Test auth supplies a record-lock key for each real user's UI API session.
    *******************************************************************************/
   public static class LockedSessions extends AssistantTestFixture.TestSessionCustomizer
   {
      /*******************************************************************************
       ** The admin can index all records; each editor can see only its own key.
       *******************************************************************************/
      @Override
      public void customizeSession(com.kingsrook.qqq.backend.core.model.metadata.QInstance instance, com.kingsrook.qqq.backend.core.model.session.QSession session, Map<String, Object> context)
      {
         super.customizeSession(instance, session, context);
         session.withSecurityKeyValue("rag_owner", session.getUuid());
         if(session.getUuid().startsWith("admin"))
         {
            session.withSecurityKeyValue("rag_all", true);
         }
      }
   }



   /*******************************************************************************
    ** The owner and non-owner hit identical UI record-lock enforcement.
    *******************************************************************************/
   @Test
   void recordLocksFilterNonOwner() throws Exception
   {
      qInstance.addSecurityKeyType(new com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType().withName("rag_owner").withAllAccessKeyName("rag_all"));
      qInstance.getAuthentication().withCustomizer(new com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference(LockedSessions.class));
      var table = qInstance.getTable("blog_post");
      table.withRecordSecurityLock(new com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock().withFieldName("slug").withSecurityKeyType("rag_owner"));
      assertThat(config.getRuntime().getUiApiGateway().invoke(new UiApiCall("PATCH", "/table/{tableName}/{primaryKey}")
         .withPathParam("tableName", "blog_post").withPathParam("primaryKey", "1").withSessionUUID(AssistantTestFixture.ADMIN)
         .withJsonBody(Map.of("slug", AssistantTestFixture.EDITOR))).status()).isEqualTo(200);
      RagService.Result indexed = config.getRuntime().getRagService().index(source);
      assertThat(indexed.errors()).as(indexed.reason()).isZero();
      AiToolResult owner = search(AssistantTestFixture.EDITOR, DataClassification.PUBLIC);
      assertThat(owner.getRowCount()).as(owner.getModelContent()).isPositive();
      assertThat(owner.getRecordRefs()).allSatisfy(ref -> assertThat(String.valueOf(ref.getId())).isEqualTo("1"));
      AiToolResult other = search(AssistantTestFixture.OTHER_EDITOR, DataClassification.PUBLIC);
      assertThat(other.getRowCount()).isZero();
      assertThat(other.getSummary()).doesNotEndWith("filtered 0");
      Integer status = config.getRuntime().getUiApiGateway().invoke(new UiApiCall("GET", "/table/{tableName}/{primaryKey}")
         .withPathParam("tableName", "blog_post").withPathParam("primaryKey", "1").withSessionUUID(AssistantTestFixture.OTHER_EDITOR)).status();
      assertThat(status).isIn(403, 404);
   }



   /*******************************************************************************
    ** Filter paging and chunk shrinkage reconcile without holding record content.
    *******************************************************************************/
   @Test
   void sourceFilterAndChangedChunks() throws Exception
   {
      source.setFilterJson("{\"criteria\":[{\"fieldName\":\"id\",\"operator\":\"EQUALS\",\"values\":[1]}]}");
      source.setFieldNames("content");
      source.setChunkChars(100);
      source.setChunkOverlapChars(20);
      config.getRuntime().getStore().update(source);
      var gateway = config.getRuntime().getUiApiGateway();
      var update = new UiApiCall("PATCH", "/table/{tableName}/{primaryKey}").withPathParam("tableName", "blog_post").withPathParam("primaryKey", "1").withSessionUUID(AssistantTestFixture.ADMIN);
      assertThat(gateway.invoke(update.withJsonBody(Map.of("content", "Knowledge ".repeat(40)))).status()).isEqualTo(200);
      RagService.Result first = config.getRuntime().getRagService().index(source);
      assertThat(first.errors()).as(first.reason()).isZero();
      assertThat(first.recordsSeen()).isEqualTo(1);
      assertThat(first.embedded()).isGreaterThan(1);
      assertThat(gateway.invoke(update.withJsonBody(Map.of("content", "Short updated knowledge"))).status()).isEqualTo(200);
      assertThat(search(AssistantTestFixture.EDITOR, DataClassification.PUBLIC).getModelContent()).contains("Short updated knowledge").doesNotContain("Knowledge Knowledge");
      as(AssistantTestFixture.ADMIN);
      RagService.Result shortened = config.getRuntime().getRagService().index(source);
      assertThat(shortened.errors()).as(shortened.reason()).isZero();
      assertThat(shortened.embedded()).isEqualTo(1);
      assertThat(shortened.removed()).isEqualTo(first.embedded() - 1);
      source.setFieldNames("title");
      config.getRuntime().getStore().update(source);
      assertThat(vectors.count(source.getId())).isZero();
      assertThat(config.getRuntime().getRagService().validate(new AssistantRagSource(source.toQRecord()).withFilterJson("{"))).isNotEmpty();
      as(AssistantTestFixture.EDITOR);
      assertThatThrownBy(() -> config.getRuntime().getRagService().index(source)).hasMessageContaining("assistant.admin");
   }



   /*******************************************************************************
    ** Standard process execution reaches both Review and Result backend steps.
    *******************************************************************************/
   @Test
   void standardProcessAndPolicy() throws Exception
   {
      var input = new com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput();
      input.setProcessName(KnowledgeIndexProcess.INDEX);
      input.setCallback(com.kingsrook.qqq.backend.core.actions.processes.QProcessCallbackFactory.forPrimaryKey("id", source.getId()));
      input.setFrontendStepBehavior(com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput.FrontendStepBehavior.SKIP);
      new com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction().execute(input);
      assertThat(vectors.count(source.getId())).isPositive();
      assertThat(com.kingsrook.qbits.assistant.engine.ConnectionTest.runModel(config.getRuntime(), model, AssistantTestFixture.ADMIN).status()).isEqualTo("OK");
      RunBackendStepOutput clearOutput = new RunBackendStepOutput();
      KnowledgeIndexProcess.ClearReview clearReview = new KnowledgeIndexProcess.ClearReview();
      clearReview.runOnePage(new RunBackendStepInput().withRecords(List.of(source.toQRecord())), clearOutput);
      assertThat(clearReview.getProcessSummary(clearOutput, false)).hasSize(1);
      var policy = new PolicyService.ToolPolicy(true, Set.of("search_help"), 50, true);
      assertThat(config.getRuntime().getKnowledgeTool().offered(policy, QContext.getQSession())).isFalse();
      var context = new UiApiToolbox.Context(AssistantTestFixture.ADMIN, null, config.getRuntime().getUiApiGateway(), policy, null);
      assertThat(config.getRuntime().getKnowledgeTool().execute(context, "denied", Map.of("query", "x"), DataClassification.PUBLIC, null, null).getOutcome()).isEqualTo(AiToolOutcome.DENIED);
      assertThat(config.getRuntime().getKnowledgeTool().execute(config.getRuntime().getRagService().context(), "invalid", Map.of(), DataClassification.PUBLIC, null, null).getOutcome()).isEqualTo(AiToolOutcome.INVALID_INPUT);
      config.getBudgets().withUserDailyTokensHard(1L);
      assertThatThrownBy(() -> new EmbeddingService(config.getRuntime()).embed(model.getId(), List.of("blocked"), DataClassification.PUBLIC, "blog_post", null, null)).hasMessageContaining("budget");
   }



   /*******************************************************************************
    ** A multi-page scan does not stop at the first UI API query page.
    *******************************************************************************/
   @Test
   void indexesMultiplePages() throws Exception
   {
      List<QRecord> records = new ArrayList<>();
      for(Integer i = 0; i < 105; i++)
      {
         records.add(new QRecord().withValue("title", "Extra page " + i).withValue("content", "Paged knowledge " + i));
      }
      new com.kingsrook.qqq.backend.core.actions.tables.InsertAction().execute(new com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput("page").withRecords(records));
      source.setTableName("page");
      source.setFieldNames("content");
      config.getRuntime().getStore().update(source);
      RagService.Result result = config.getRuntime().getRagService().index(source);
      assertThat(result.errors()).as(result.reason()).isZero();
      assertThat(result.recordsSeen()).isEqualTo(117);
      assertThat(vectors.count(source.getId())).isEqualTo(117);
   }
}
