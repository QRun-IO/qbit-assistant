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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.engine.PolicyService;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantRagSource;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.actions.ai.AiExposure;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolOutcome;
import com.kingsrook.qqq.backend.core.actions.ai.exposure.AiExposurePolicy;
import com.kingsrook.qqq.backend.core.actions.ai.output.AiRedactor;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** Manual indexing as the clicking administrator, and shared safe UI API reads.
 *******************************************************************************/
public class RagService
{
   private final AssistantRuntime runtime;
   private final VectorStore vectors;
   private final AiExposurePolicy exposure;
   private final UiApiToolbox reader;
   private final EmbeddingService embeddings;



   /*******************************************************************************
    ** Reuse get_record's helpers through its public execution path, without
    ** imposing the chat cell-length cap on indexing or snippet boundaries.
    *******************************************************************************/
   public RagService(AssistantRuntime runtime, VectorStore vectors)
   {
      this.runtime = runtime;
      this.vectors = vectors;
      var settings = AssistantRuntime.kernelSettings(runtime.getConfig());
      exposure = new AiExposurePolicy(settings);
      reader = new UiApiToolbox(runtime.getQInstance(), runtime.getDataAccessService(), exposure, new AiRedactor(settings.getCustomRedactionPatterns()), Integer.MAX_VALUE);
      embeddings = new EmbeddingService(runtime);
   }



   /*******************************************************************************
    ** Configuration only; never query application data through AssistantStore.
    *******************************************************************************/
   public List<AssistantRagSource> sources() throws QException
   {
      return (runtime.getStore().query(AssistantRagSource.TABLE_NAME, new QQueryFilter(), AssistantRagSource.class));
   }



   /*******************************************************************************
    ** Source definitions only narrow existing AI table/field exposure.
    *******************************************************************************/
   public List<String> validate(AssistantRagSource source) throws QException
   {
      List<String> errors = new ArrayList<>();
      QTableMetaData table = runtime.getQInstance().getTable(source.getTableName());
      if(table == null || source.getTableName().startsWith("assistant_") || !runtime.getDataAccessService().forTable(source.getTableName()).mode().allowsRead())
      {
         errors.add("Knowledge sources require an application table with AI data access READ or READ_WRITE, never NONE or assistant_*.");
         return (errors);
      }
      if(fields(source).isEmpty())
      {
         errors.add("Select at least one text field.");
      }
      Set<String> selected = new LinkedHashSet<>(fields(source));
      if(source.getTitleFieldName() != null && !source.getTitleFieldName().isBlank())
      {
         selected.add(source.getTitleFieldName());
      }
      for(String name : selected)
      {
         QFieldMetaData field = table.getFields().get(name);
         AiExposure level = field == null ? AiExposure.NONE : exposure.getFieldExposure(table, field);
         if(field == null || !Set.of(QFieldType.STRING, QFieldType.TEXT).contains(field.getType()) || !level.allowsValues())
         {
            errors.add("Knowledge fields must be text fields with VALUES or MASKED exposure: " + name);
         }
      }
      if(size(source) < 100 || size(source) > 16000 || overlap(source) < 0 || overlap(source) >= size(source))
      {
         errors.add("Chunk size must be 100 to 16000; overlap must be nonnegative and smaller than chunk size.");
      }
      AssistantModel model = runtime.getStore().listModels().stream().filter(m -> Objects.equals(m.getId(), source.getEmbeddingModelId())).findFirst().orElse(null);
      if(model == null || !"EMBEDDING".equals(model.getModelKind()) || !Boolean.TRUE.equals(model.getEnabled()) || model.getEmbeddingDimensions() == null || model.getEmbeddingDimensions() < 1)
      {
         errors.add("Select an enabled EMBEDDING model with positive embedding dimensions.");
      }
      try
      {
         filter(source);
      }
      catch(QException e)
      {
         errors.add("The query filter must be valid QQQ query filter JSON.");
      }
      return (errors);
   }



   /*******************************************************************************
    ** Transient safe values from get_record, with the asking user's session.
    *******************************************************************************/
   public UiApiToolbox.Outcome read(UiApiToolbox.Context context, String table, String pk)
   {
      PolicyService.ToolPolicy policy = context.policy();
      UiApiToolbox.Context internal = new UiApiToolbox.Context(context.sessionUUID(), context.userTimezone(), context.gateway(),
         new PolicyService.ToolPolicy(true, Set.of(), policy == null ? 50 : policy.maxRowsPerCall(), policy == null || policy.redactPii()), null, context.credentials(), context.turnId());
      return (reader.execute(internal, "knowledge-read", UiApiToolbox.GET_RECORD, Map.of("table", table, "id", pk)));
   }



   /*******************************************************************************
    ** Read already masked/redacted fields from the existing record render.
    *******************************************************************************/
   public static Map<String, String> values(UiApiToolbox.Outcome outcome)
   {
      Map<String, String> values = new LinkedHashMap<>();
      if(outcome.getResult().getRender() instanceof Map<?, ?> render && render.get("fields") instanceof List<?> fields)
      {
         for(Object item : fields)
         {
            if(item instanceof Map<?, ?> field && field.get("name") instanceof String name && field.get("value") != null)
            {
               values.put(name, String.valueOf(field.get("value")));
            }
         }
      }
      return (values);
   }



   /*******************************************************************************
    ** Index result counters contain no record values or provider diagnostics.
    *******************************************************************************/
   public record Result(Integer recordsSeen, Integer embedded, Integer unchanged, Integer removed, Integer errors, String reason)
   {
      /*******************************************************************************
       ** Standard process summary wording.
       *******************************************************************************/
      public String summary()
      {
         return ("Records seen: " + recordsSeen + "; chunks embedded: " + embedded + "; unchanged: " + unchanged + "; removed: " + removed + "; errors: " + errors
            + (reason == null ? "" : ". " + reason));
      }
   }



   /*******************************************************************************
    ** Complete scans alone remove disappeared records. Failed or denied pages
    ** never erase previously indexed records. Re-indexing is deliberately manual.
    *******************************************************************************/
   public synchronized Result index(AssistantRagSource source) throws QException
   {
      requireAdmin();
      Integer seen = 0;
      Integer embedded = 0;
      Integer unchanged = 0;
      Integer removed = 0;
      String reason = null;
      try
      {
         List<String> errors = validate(source);
         if(!errors.isEmpty())
         {
            throw (new QException(errors.getFirst()));
         }
         var classification = runtime.getConfig().getClassificationSource().highest(source.getTableName());
         embeddings.resolve(source.getEmbeddingModelId(), classification, source.getTableName(), null, null);
         Map<String, List<ChunkVector>> old = new LinkedHashMap<>();
         vectors.chunks(source.getId()).forEach(v -> old.computeIfAbsent(v.pk(), key -> new ArrayList<>()).add(v));
         Set<String> seenIds = new LinkedHashSet<>();
         QTableMetaData table = runtime.getQInstance().getTable(source.getTableName());
         QQueryFilter filter = filter(source);
         filter.setOrderBys(List.of(new QFilterOrderBy(table.getPrimaryKeyField(), true)));
         filter.setLimit(100);
         UiApiToolbox.Context context = context();
         for(Integer skip = 0; ; skip += 100)
         {
            checkKillSwitch();
            filter.setSkip(skip);
            UiApiResult page = runtime.getUiApiGateway().invoke(new UiApiCall("POST", "/table/{tableName}/query").withPathParam("tableName", source.getTableName())
               .withSessionUUID(context.sessionUUID()).withCredentials(context.credentials()).withUserTimezone(context.userTimezone()).withJsonBody("{\"filter\":" + JsonUtils.toJson(filter) + "}"));
            if(!page.isSuccess())
            {
               throw (new QException("Index query answered HTTP " + page.status() + "; existing chunks were preserved."));
            }
            if(!(page.json() instanceof Map<?, ?> json) || !(json.get("records") instanceof List<?> records))
            {
               throw (new QException("The UI API returned an invalid query page; existing chunks were preserved."));
            }
            for(Object item : records)
            {
               if(!(item instanceof Map<?, ?> record) || !(record.get("values") instanceof Map<?, ?> raw) || raw.get(table.getPrimaryKeyField()) == null)
               {
                  throw (new QException("The UI API returned a record without its primary key."));
               }
               String pk = String.valueOf(raw.get(table.getPrimaryKeyField()));
               if(!seenIds.add(pk))
               {
                  throw (new QException("Records moved during paging. Retry indexing; disappeared-record cleanup was skipped."));
               }
               seen++;
               checkKillSwitch();
               UiApiToolbox.Outcome read = read(context, source.getTableName(), pk);
               if(read.getResult().getOutcome() != AiToolOutcome.OK)
               {
                  throw (new QException("Index record read was refused; existing chunks were preserved."));
               }
               List<ChunkVector> previous = old.getOrDefault(pk, List.of());
               List<ChunkVector> next = new ArrayList<>();
               List<String> texts = new ArrayList<>();
               List<String> names = new ArrayList<>();
               List<Integer> indexes = new ArrayList<>();
               Map<String, String> values = values(read);
               for(String field : fields(source))
               {
                  List<String> chunks = TextChunks.split(values.getOrDefault(field, ""), size(source), overlap(source));
                  for(Integer i = 0; i < chunks.size(); i++)
                  {
                     Integer index = i;
                     String hash = TextChunks.hash(chunks.get(i));
                     ChunkVector same = previous.stream().filter(v -> v.field().equals(field) && v.chunkIndex().equals(index) && v.contentHash().equals(hash)).findFirst().orElse(null);
                     if(same != null)
                     {
                        next.add(same);
                        unchanged++;
                     }
                     else
                     {
                        texts.add(chunks.get(i));
                        names.add(field);
                        indexes.add(i);
                     }
                  }
               }
               List<float[]> generated = embeddings.embed(source.getEmbeddingModelId(), texts, classification, source.getTableName(), null, null);
               Integer dimensions = runtime.getStore().listModels().stream().filter(m -> m.getId().equals(source.getEmbeddingModelId())).findFirst().orElseThrow().getEmbeddingDimensions();
               for(Integer i = 0; i < generated.size(); i++)
               {
                  if(generated.get(i).length != dimensions)
                  {
                     throw (new QException("Embedding dimensions differ from the model registry; no record replacement was made."));
                  }
                  next.add(new ChunkVector(source.getId(), source.getTableName(), pk, names.get(i), indexes.get(i), TextChunks.hash(texts.get(i)), generated.get(i)));
               }
               embedded += generated.size();
               removed += (int) previous.stream().filter(v -> next.stream().noneMatch(n -> n.field().equals(v.field()) && n.chunkIndex().equals(v.chunkIndex()))).count();
               if(!texts.isEmpty() || next.size() != previous.size())
               {
                  vectors.replaceRecord(source.getId(), source.getTableName(), pk, next);
               }
            }
            if(records.size() < 100)
            {
               break;
            }
         }
         checkKillSwitch();
         for(var entry : old.entrySet())
         {
            if(!seenIds.contains(entry.getKey()))
            {
               vectors.deleteRecord(source.getId(), source.getTableName(), entry.getKey());
               removed += entry.getValue().size();
            }
         }
      }
      catch(QException e)
      {
         reason = e.getMessage();
      }
      catch(RuntimeException e)
      {
         reason = "Indexing failed. Check the source and vector store configuration.";
      }
      Integer chunkCount = source.getIndexedChunkCount();
      try
      {
         chunkCount = vectors.count(source.getId()).intValue();
      }
      catch(QException e)
      {
         reason = "The vector store is unavailable; the indexed chunk count could not be refreshed.";
      }
      Result result = new Result(seen, embedded, unchanged, removed, reason == null ? 0 : 1, reason);
      runtime.getStore().updateFields(AssistantRagSource.TABLE_NAME, new QRecord().withValue("id", source.getId())
         .withValue("lastIndexedAt", runtime.getClock().instant()).withValue("lastIndexStatus", reason == null ? "OK" : "ERROR")
         .withValue("lastIndexError", reason == null ? null : reason.substring(0, Math.min(500, reason.length())))
         .withValue("indexedRecordCount", seen).withValue("indexedChunkCount", chunkCount));
      return (result);
   }



   /*******************************************************************************
    ** Clear is an admin record action, independent of the current model setup.
    *******************************************************************************/
   public synchronized void clear(AssistantRagSource source) throws QException
   {
      requireAdmin();
      vectors.deleteSource(source.getId());
      runtime.getStore().updateFields(AssistantRagSource.TABLE_NAME, new QRecord().withValue("id", source.getId()).withValue("indexedRecordCount", 0)
         .withValue("indexedChunkCount", 0).withValue("lastIndexStatus", "CLEARED").withValue("lastIndexError", null));
   }



   /*******************************************************************************
    ** Only a real clicking administrator may launch manual indexing.
    *******************************************************************************/
   private static void requireAdmin() throws QException
   {
      if(QContext.getQSession() == null || !QContext.getQSession().hasPermission(AssistantQBitConfig.ADMIN_PERMISSION))
      {
         throw (new QException("Knowledge indexing requires assistant.admin."));
      }
   }



   /*******************************************************************************
    ** Stop between UI API reads as well as before every embedding dispatch.
    *******************************************************************************/
   private void checkKillSwitch() throws QException
   {
      if(runtime.getPolicyService().current(true).killSwitch())
      {
         throw (new QException("The assistant kill switch is on."));
      }
   }



   /*******************************************************************************
    ** Current caller context, never a stored or service session.
    *******************************************************************************/
   public UiApiToolbox.Context context() throws QException
   {
      return (new UiApiToolbox.Context(QContext.getQSession().getUuid(), QContext.getQSession().getValue("userTimezone"), runtime.getUiApiGateway(), runtime.getPolicyService().current(true).tools(), null));
   }



   /*******************************************************************************
    ** Configured text field names in stable order.
    *******************************************************************************/
   public static List<String> fields(AssistantRagSource source)
   {
      return (source.getFieldNames() == null ? List.of() : java.util.Arrays.stream(source.getFieldNames().split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList());
   }



   /*******************************************************************************
    ** Default chunk size.
    *******************************************************************************/
   public static Integer size(AssistantRagSource source)
   {
      return (source.getChunkChars() == null ? 1500 : source.getChunkChars());
   }



   /*******************************************************************************
    ** Default overlap.
    *******************************************************************************/
   public static Integer overlap(AssistantRagSource source)
   {
      return (source.getChunkOverlapChars() == null ? 200 : source.getChunkOverlapChars());
   }



   /*******************************************************************************
    ** Source filter is QQQ filter JSON, passed unchanged apart from paging/order.
    *******************************************************************************/
   private static QQueryFilter filter(AssistantRagSource source) throws QException
   {
      try
      {
         QQueryFilter filter = source.getFilterJson() == null || source.getFilterJson().isBlank() ? new QQueryFilter() : JsonUtils.toObject(source.getFilterJson(), QQueryFilter.class);
         if(filter == null)
         {
            throw (new IllegalArgumentException("null filter"));
         }
         return (filter);
      }
      catch(Exception e)
      {
         throw (new QException("The query filter must be valid QQQ query filter JSON."));
      }
   }
   /*******************************************************************************
    ** Remove a deleted source without attempting to update its missing row.
    *******************************************************************************/
   public synchronized void remove(AssistantRagSource source) throws QException
   {
      requireAdmin();
      vectors.deleteSource(source.getId());
   }

}
