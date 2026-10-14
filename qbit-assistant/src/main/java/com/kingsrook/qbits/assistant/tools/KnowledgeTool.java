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

package com.kingsrook.qbits.assistant.tools;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.engine.TurnEngine;
import com.kingsrook.qbits.assistant.model.AssistantRagSource;
import com.kingsrook.qbits.assistant.rag.EmbeddingService;
import com.kingsrook.qbits.assistant.rag.RagService;
import com.kingsrook.qbits.assistant.rag.TextChunks;
import com.kingsrook.qbits.assistant.rag.VectorStore;
import com.kingsrook.qbits.assistant.spi.ModelToolDefinition;
import com.kingsrook.qqq.backend.core.actions.ai.AiRecordRef;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolOutcome;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolResult;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.actions.ai.output.AiDataEnvelope;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** Permission-filtered semantic retrieval. Every candidate is re-read as the caller.
 *******************************************************************************/
public class KnowledgeTool
{
   public static final String NAME = "search_knowledge";
   private final AssistantRuntime runtime;
   private final RagService rag;
   private final VectorStore vectors;



   /*******************************************************************************
    ** Bind the existing services; the vector store contains no record content.
    *******************************************************************************/
   public KnowledgeTool(AssistantRuntime runtime, RagService rag, VectorStore vectors)
   {
      this.runtime = runtime;
      this.rag = rag;
      this.vectors = vectors;
   }



   /*******************************************************************************
    ** A source must be enabled and readable in the UI before offering the tool.
    *******************************************************************************/
   public Boolean offered(PolicyService.ToolPolicy policy, QSession session)
   {
      return (offered(policy, session, null));
   }



   /*******************************************************************************
    ** Tool discovery is a UI API call too: retain the turn when known.
    *******************************************************************************/
   public Boolean offered(PolicyService.ToolPolicy policy, QSession session, String turnId)
   {
      if(session == null || (policy != null && !policy.allows(NAME)))
      {
         return (false);
      }
      try
      {
         Set<String> tables = new LinkedHashSet<>();
         for(AssistantRagSource source : rag.sources())
         {
            if(Boolean.TRUE.equals(source.getEnabled()) && runtime.getDataAccessService().forTable(source.getTableName()).mode().allowsRead() && rag.validate(source).isEmpty())
            {
               tables.add(source.getTableName());
            }
         }
         if(tables.isEmpty())
         {
            return (false);
         }
         return (runtime.getAccessExplainer().explain(runtime.getUiApiGateway(), session.getUuid(), null, policy, null, null, turnId).stream()
            .anyMatch(t -> tables.contains(t.table()) && t.uiRead() && t.mode().allowsRead()));
      }
      catch(QException e)
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** Stable, small schema in the same sorted tool list as other tools.
    *******************************************************************************/
   public ModelToolDefinition definition()
   {
      return (new ModelToolDefinition().withName(NAME).withDescription("Search indexed application knowledge. Cite returned records using their links.").withStrict(false)
         .withInputSchema(Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"),
            "tables", Map.of("type", "array", "items", Map.of("type", "string")), "limit", Map.of("type", "integer", "minimum", 1, "maximum", 8)), "required", List.of("query"))));
   }



   /*******************************************************************************
    ** Expected denials disclose only a filtered count, never hit identifiers.
    *******************************************************************************/
   public AiToolResult execute(UiApiToolbox.Context context, String callId, Map<String, Object> input, DataClassification classification, Integer conversationId, Integer turnId)
   {
      AiToolResult result = new AiToolResult().withToolName(NAME).withToolCallId(callId);
      Long started = System.nanoTime();
      try
      {
         if(context.policy() != null && !context.policy().allows(NAME))
         {
            return (result.withOutcome(AiToolOutcome.DENIED).withSummary("Not allowed").withModelContent("The policy does not allow search_knowledge."));
         }
         if(input == null || !(input.get("query") instanceof String query) || query.isBlank() || query.length() > 8000
            || (input.containsKey("tables") && (!(input.get("tables") instanceof List<?> requested) || requested.stream().anyMatch(t -> !(t instanceof String))))
            || (input.containsKey("limit") && (!(input.get("limit") instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue() < 1)))
         {
            return (result.withOutcome(AiToolOutcome.INVALID_INPUT).withSummary("Invalid search").withModelContent("Supply a nonempty query (at most 8000 characters), optional table names and a positive integer limit."));
         }
         Integer limit = input.get("limit") instanceof Number n ? Math.min(8, n.intValue()) : 5;
         Set<?> requested = input.get("tables") instanceof List<?> list ? new LinkedHashSet<>(list) : Set.of();
         Map<Integer, AssistantRagSource> sources = new LinkedHashMap<>();
         Map<Integer, Set<String>> modelTables = new LinkedHashMap<>();
         for(AssistantRagSource source : rag.sources())
         {
            if(Boolean.TRUE.equals(source.getEnabled()) && (requested.isEmpty() || requested.contains(source.getTableName()))
               && runtime.getDataAccessService().forTable(source.getTableName()).mode().allowsRead() && rag.validate(source).isEmpty())
            {
               sources.put(source.getId(), source);
               modelTables.computeIfAbsent(source.getEmbeddingModelId(), key -> new LinkedHashSet<>()).add(source.getTableName());
            }
         }
         List<VectorStore.Hit> hits = new ArrayList<>();
         EmbeddingService embeddings = new EmbeddingService(runtime);
         for(var model : modelTables.entrySet())
         {
            float[] vector = embeddings.embed(model.getKey(), List.of(query), classification, String.join(", ", model.getValue()), conversationId, turnId).getFirst();
            Set<Integer> compatible = new LinkedHashSet<>();
            sources.values().stream().filter(s -> model.getKey().equals(s.getEmbeddingModelId())).forEach(s -> compatible.add(s.getId()));
            for(VectorStore.Hit hit : vectors.search(vector, limit * 4, model.getValue(), compatible))
            {
               AssistantRagSource source = sources.get(hit.source());
               if(source != null && model.getKey().equals(source.getEmbeddingModelId()) && source.getTableName().equals(hit.table()) && RagService.fields(source).contains(hit.field()))
               {
                  hits.add(hit);
               }
            }
         }
         hits.sort(Comparator.comparing(VectorStore.Hit::score).reversed());
         List<Map<String, Object>> records = new ArrayList<>();
         Set<List<String>> returned = new LinkedHashSet<>();
         Integer filtered = 0;
         for(VectorStore.Hit hit : hits)
         {
            if(records.size() >= limit)
            {
               break;
            }
            if(runtime.getPolicyService().current(true).killSwitch())
            {
               throw (new QException("The assistant kill switch is on."));
            }
            UiApiToolbox.Outcome read = rag.read(context, hit.table(), hit.pk());
            if(read.getResult().getOutcome() == AiToolOutcome.DENIED || read.getResult().getOutcome() == AiToolOutcome.NOT_FOUND)
            {
               filtered++;
               continue;
            }
            if(read.getResult().getOutcome() != AiToolOutcome.OK)
            {
               throw (new QException("A knowledge record could not be read through the UI API. Retry the search."));
            }
            Map<String, String> values = RagService.values(read);
            AssistantRagSource source = sources.get(hit.source());
            List<String> chunks = TextChunks.split(values.getOrDefault(hit.field(), ""), RagService.size(source), RagService.overlap(source));
            if(hit.chunkIndex() >= chunks.size() || !returned.add(List.of(hit.table(), hit.pk(), hit.field(), String.valueOf(hit.chunkIndex()))))
            {
               filtered++;
               continue;
            }
            String href = String.valueOf(((Map<?, ?>) read.getResult().getRender()).get("href"));
            if(!TurnEngine.isAppHref(href))
            {
               filtered++;
               continue;
            }
            String title = values.getOrDefault(source.getTitleFieldName(), hit.pk());
            title = title.substring(0, Math.min(300, title.length()));
            String snippet = chunks.get(hit.chunkIndex());
            snippet = snippet.substring(0, Math.min(1500, snippet.length()));
            records.add(Map.of("table", hit.table(), "recordId", hit.pk(), "title", title, "snippet", snippet, "score", hit.score(), "href", href));
            result.getRecordRefs().add(new AiRecordRef().withTableName(hit.table()).withId(hit.pk()).withLabel(title).withHref(href));
            result.getRedactions().addAll(read.getResult().getRedactions());
         }
         Map<String, Object> content = Map.of("results", records, "filtered", filtered);
         return (result.withOutcome(AiToolOutcome.OK).withRowCount(records.size()).withSummary("Found " + records.size() + " knowledge results; filtered " + filtered)
            .withPermissionCheck("uiApi:GET /table/{tableName}/{primaryKey}").withModelContent(AiDataEnvelope.wrap(NAME, null, JsonUtils.toJson(content))));
      }
      catch(QException e)
      {
         return (result.withOutcome(AiToolOutcome.ERROR).withSummary("Knowledge search failed").withModelContent(e.getMessage()));
      }
      catch(RuntimeException e)
      {
         return (result.withOutcome(AiToolOutcome.ERROR).withSummary("Knowledge search failed").withModelContent("Knowledge search could not complete. Check the source and embedding configuration."));
      }
      finally
      {
         result.withDurationMs((System.nanoTime() - started) / 1_000_000);
      }
   }
}
