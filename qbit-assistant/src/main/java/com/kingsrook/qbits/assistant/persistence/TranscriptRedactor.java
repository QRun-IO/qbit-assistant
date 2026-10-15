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


import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.kingsrook.qbits.assistant.classification.ClassificationSource;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.actions.permissions.fieldaccess.ClassifiedValueTokens;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.LogRedactorInterface;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** Turn-local storage egress protection. Values and provisional payloads live only
 ** in memory; QQQ actions receive placeholders until the complete turn is known.
 ** The log hook is global while active, covering provider and tool worker threads.
 *******************************************************************************/
public class TranscriptRedactor implements LogRedactorInterface, AutoCloseable
{
   private static final ThreadLocal<TranscriptRedactor> CURRENT = new ThreadLocal<>();
   private static final Set<String> PAYLOAD_FIELDS = Set.of("partsJson", "inputJson", "resultJson", "redactionsJson", "dataJson", "paramsJson",
      "summary", "resultSummary", "reason", "title", "contextJson", "errorMessage", "errorText", "error", "requestId", "comment", "providerRequestId", "providerRequestIds", "auditHref", "toolCallId", "stopReason");
   private final ClassificationSource classifications;
   private final QInstance instance;
   private final Map<String, String> replacements = new LinkedHashMap<>();
   private final Map<List<String>, String> recordTokens = new LinkedHashMap<>();
   private final Map<List<String>, QRecord> pending = new LinkedHashMap<>();
   private Boolean flushing = false;
   private volatile Boolean closed = false;
   private volatile Boolean released = false;
   private Integer workers = 0;



   /*******************************************************************************
    ** Register only for this turn; close releases both values and the log hook.
    *******************************************************************************/
   public TranscriptRedactor(QInstance instance, ClassificationSource classifications)
   {
      this.instance = instance;
      this.classifications = classifications;
      QLogger.registerRedactor(this);
   }



   /*******************************************************************************
    ** Explicit propagation works for pooled tool executors, without inheritable state.
    *******************************************************************************/
   public void attach()
   {
      CURRENT.set(this);
   }



   /*******************************************************************************
    ** Remove only the thread binding, retaining the turn's in-memory state.
    *******************************************************************************/
   public static void detach()
   {
      CURRENT.remove();
   }



   /*******************************************************************************
    ** Storage and tools share the currently bound turn, if any.
    *******************************************************************************/
   public static TranscriptRedactor current()
   {
      return (CURRENT.get());
   }



   /*******************************************************************************
    ** Capture the raw canonical value and the actual displayed/truncated variant.
    ** Tokens remain bound to the original record field, including equal values.
    *******************************************************************************/
   public void observe(String tableName, String fieldName, Serializable primaryKey, Object value, Object displayed)
   {
      QTableMetaData table = instance.getTable(tableName);
      if(released || value == null || table == null || table.getField(fieldName) == null
         || !classifications.ofField(tableName, fieldName).isAtLeast(DataClassification.PII))
      {
         return;
      }
      Serializable original = value instanceof Serializable serializable ? serializable : String.valueOf(value);
      String token = primaryKey == null
         ? ClassifiedValueTokens.forContext(List.of("assistant", tableName, fieldName), original)
         : ClassifiedValueTokens.forRecordField(table, table.getField(fieldName), primaryKey, original);
      synchronized(this)
      {
         if(released)
         {
            return;
         }
         recordTokens.put(List.of(tableName, fieldName, String.valueOf(primaryKey), String.valueOf(displayed)), token);
         remember(ClassifiedValueTokens.canonical(original), token);
         remember(String.valueOf(value), token);
         if(displayed != null)
         {
            remember(String.valueOf(displayed), token);
         }
      }
   }



   /*******************************************************************************
    ** A snippet is another spelling of an already observed field, not a new value.
    *******************************************************************************/
   public synchronized void variant(String table, String field, String primaryKey, String text)
   {
      if(!closed && text != null && table != null && field != null && primaryKey != null)
      {
         recordTokens.entrySet().stream().filter(entry -> entry.getKey().subList(0, 3).equals(List.of(table, field, primaryKey)))
            .findFirst().ifPresent(entry -> remember(text, entry.getValue()));
      }
   }



   /*******************************************************************************
    ** Also protect JSON-escaped and URL-encoded occurrences in envelopes and links.
    *******************************************************************************/
   private void remember(String value, String token)
   {
      if(value == null || value.isEmpty())
      {
         return;
      }
      replacements.putIfAbsent(value, token);
      String json = JsonUtils.toJson(value);
      replacements.putIfAbsent(json.substring(1, json.length() - 1), token);
      replacements.putIfAbsent(URLEncoder.encode(value, StandardCharsets.UTF_8), token);
   }



   /*******************************************************************************
    ** Existing tool summaries may clip a value before storage. Remember that exact
    ** prefix as another variant while keeping the live/model summary unchanged.
    *******************************************************************************/
   public synchronized void truncated(String original, String prefix)
   {
      for(var entry : new ArrayList<>(replacements.entrySet()))
      {
         int start = original.indexOf(entry.getKey());
         while(start >= 0)
         {
            if(start < prefix.length() && start + entry.getKey().length() > prefix.length())
            {
               remember(prefix.substring(start), entry.getValue());
            }
            start = original.indexOf(entry.getKey(), start + 1);
         }
      }
   }



   /*******************************************************************************
    ** Criteria and proposed values are classified even when a query returns no rows.
    *******************************************************************************/
   public void input(Map<String, Object> input)
   {
      if(input != null && input.get("table") instanceof String table)
      {
         criteria(table, input.get("filter"));
         if(input.get("values") instanceof Map<?, ?> values)
         {
            values.forEach((field, value) -> observe(table, String.valueOf(field), null, value, value));
         }
      }
   }



   /*******************************************************************************
    ** Traverse both model and QQQ filter spellings, including nested boolean groups.
    *******************************************************************************/
   private void criteria(String table, Object node)
   {
      if(node instanceof Map<?, ?> map)
      {
         Object field = map.containsKey("field") ? map.get("field") : map.get("fieldName");
         if(field instanceof String name && map.get("values") instanceof List<?> values)
         {
            values.forEach(value -> observe(table, name, null, value, value));
         }
         map.values().forEach(value -> criteria(table, value));
      }
      else if(node instanceof List<?> list)
      {
         list.forEach(value -> criteria(table, value));
      }
   }



   /*******************************************************************************
    ** Longest exact match wins; a single pass never redacts a generated token again.
    *******************************************************************************/
   @Override
   public synchronized String redact(String text)
   {
      if(text == null || replacements.isEmpty())
      {
         return (text);
      }
      List<String> values = new ArrayList<>(replacements.keySet());
      values.sort(Comparator.comparingInt(String::length).reversed());
      Pattern pattern = Pattern.compile("(?:hmac|shred):v1:[A-Za-z0-9._-]+:[A-Za-z0-9_-]+|changed:v1|" + String.join("|", values.stream().map(Pattern::quote).toList()));
      Matcher matcher = pattern.matcher(text);
      return (matcher.replaceAll(match -> Matcher.quoteReplacement(replacements.getOrDefault(match.group(), match.group()))));
   }



   /*******************************************************************************
    ** Redact JSON leaves without damaging property names or numeric syntax. Enveloped
    ** tool data retains its framing. Invalid/opaque content is still exact-redacted.
    *******************************************************************************/
   public String payload(String text)
   {
      if(text == null)
      {
         return (null);
      }
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      try
      {
         if(text.stripLeading().startsWith("["))
         {
            return (JsonUtils.toJson(tree(JsonUtils.toObject(text, Object.class), null, null)));
         }
         if(start >= 0 && end >= start)
         {
            Object json = JsonUtils.toObject(text.substring(start, end + 1), Object.class);
            return (redact(text.substring(0, start)) + JsonUtils.toJson(tree(json, null, null)) + redact(text.substring(end + 1)));
         }
      }
      catch(Exception e)
      {
         return (redact(text));
      }
      return (redact(text));
   }



   /*******************************************************************************
    ** Preserve record bindings in structured query/get results and render copies.
    *******************************************************************************/
   private synchronized Object tree(Object node, String table, String primaryKey)
   {
      if(node instanceof Map<?, ?> map)
      {
         Object namedTable = map.containsKey("table") ? map.get("table") : map.get("tableName");
         String resolvedTable = namedTable instanceof String name ? name : table;
         Object id = map.containsKey("id") ? map.get("id") : map.get("recordId");
         String resolvedKey = id == null ? primaryKey : String.valueOf(id);
         Map<String, Object> copy = new LinkedHashMap<>();
         map.forEach((key, value) ->
         {
            String token = recordTokens.get(List.of(String.valueOf(resolvedTable), String.valueOf(key), String.valueOf(resolvedKey), String.valueOf(value)));
            copy.put(redact(String.valueOf(key)), token == null ? tree(value, resolvedTable, resolvedKey) : token);
         });
         return (copy);
      }
      if(node instanceof List<?> list)
      {
         return (list.stream().map(value -> tree(value, table, primaryKey)).toList());
      }
      if(node instanceof String text)
      {
         return (redact(text));
      }
      if(node instanceof Number && replacements.containsKey(String.valueOf(node)))
      {
         return (replacements.get(String.valueOf(node)));
      }
      return (node);
   }



   /*******************************************************************************
    ** Never put provisional content in a backend, its audits, or its SQL logs.
    *******************************************************************************/
   public synchronized QRecord prepare(QRecord original)
   {
      QRecord copy = new QRecord();
      copy.setValues(new LinkedHashMap<>(original.getValues()));
      for(String field : PAYLOAD_FIELDS)
      {
         if(copy.getValue(field) instanceof String text)
         {
            copy.setValue(field, flushing && !closed ? stored(field, text) : (field.endsWith("Json") ? (field.equals("partsJson") ? "[]" : "{}") : ClassifiedValueTokens.changed()));
         }
      }
      if(!flushing && copy.getValues().containsKey("paramsJson") && !AssistantPendingAction.STATUS_REJECTED.equals(copy.getValueString("status")))
      {
         copy.setValue("status", "PREPARING");
      }
      return (copy);
   }



   /*******************************************************************************
    ** Truncate only after redaction, so a storage bound never cuts a raw value.
    *******************************************************************************/
   private String stored(String field, String text)
   {
      String safe = payload(text);
      Integer limit = switch(field)
      {
         case "inputJson" -> 4000;
         case "resultJson" -> 64000;
         case "title" -> 60;
         default -> null;
      };
      if("title".equals(field))
      {
         safe = safe.strip().split("\\R", 2)[0].replaceAll("\\s+", " ").strip();
      }
      return (limit != null && safe.length() > limit ? safe.substring(0, limit - 1) + "…" : safe);
   }



   /*******************************************************************************
    ** Return live content to the caller without losing backend-generated defaults.
    *******************************************************************************/
   public void restore(QRecord returned, QRecord original)
   {
      for(String field : PAYLOAD_FIELDS)
      {
         if(original.getValues().containsKey(field))
         {
            returned.setValue(field, original.getValue(field));
         }
      }
   }



   /*******************************************************************************
    ** Remember only content columns, merging later writes without stale bookkeeping.
    *******************************************************************************/
   public synchronized void saved(String table, QRecord original, Serializable id)
   {
      if(flushing || closed || PAYLOAD_FIELDS.stream().noneMatch(original.getValues()::containsKey))
      {
         return;
      }
      QRecord content = pending.computeIfAbsent(List.of(table, String.valueOf(id)), key -> new QRecord().withValue("id", id));
      for(String field : PAYLOAD_FIELDS)
      {
         if(original.getValues().containsKey(field))
         {
            content.setValue(field, original.getValue(field));
         }
      }
   }



   /*******************************************************************************
    ** Flush only redacted copies. A crash/failure leaves value-free placeholders.
    ** Redacted proposals cannot later submit token strings to an application field.
    *******************************************************************************/
   public void flush(AssistantStore store) throws QException
   {
      Map<List<String>, QRecord> writes;
      Boolean complete;
      synchronized(this)
      {
         complete = workers == 0;
         flushing = complete;
         writes = new LinkedHashMap<>(pending);
         pending.clear();
      }
      for(var entry : writes.entrySet())
      {
         QRecord record = entry.getValue();
         String table = entry.getKey().getFirst();
         if(AssistantPendingAction.TABLE_NAME.equals(table) && record.getValueString("paramsJson") != null)
         {
            Boolean changed = !complete || !record.getValueString("paramsJson").equals(payload(record.getValueString("paramsJson")));
            record.setValue("status", changed ? AssistantPendingAction.STATUS_REJECTED : AssistantPendingAction.STATUS_PENDING);
            if(changed)
            {
               record.setValue("resultSummary", "Classified values were removed. Make this change in the application.");
            }
         }
         store.updateFields(table, record);
      }
   }



   /*******************************************************************************
    ** A timed-out tool can finish after the engine; retain its log protection.
    *******************************************************************************/
   public synchronized Boolean retainWorker()
   {
      if(closed)
      {
         return (false);
      }
      workers++;
      return (true);
   }



   /*******************************************************************************
    ** Release a completed worker.
    *******************************************************************************/
   public synchronized void releaseWorker()
   {
      workers--;
      releaseIfDone();
   }



   /*******************************************************************************
    ** Clear classified values only after the engine and all tool workers finish.
    *******************************************************************************/
   private void releaseIfDone()
   {
      if(closed && workers == 0)
      {
         released = true;
         QLogger.unregisterRedactor(this);
         replacements.clear();
         recordTokens.clear();
      }
   }



   /*******************************************************************************
    ** Always release raw data and unregister, including failed/cancelled turns.
    *******************************************************************************/
   @Override
   public synchronized void close()
   {
      closed = true;
      pending.clear();
      releaseIfDone();
      detach();
   }
}
