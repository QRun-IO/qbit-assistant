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


import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.assistant.provider.ModelToolDefinition;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolOutcome;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolResult;
import com.kingsrook.qqq.backend.core.actions.ai.output.AiLinkBuilder;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** Builds presentations from previously retrieved values, never reading records.
 ** One instance belongs to one turn: UI metadata, including denials, is cached
 ** only for that turn and session. All output links are built by the server.
 *******************************************************************************/
public class PresentResultsTool
{
   public static final String NAME = "present_results";

   private final QInstance qInstance;
   private UiApiResult metadata;



   /*******************************************************************************
    ** Create a turn-scoped presenter.
    *******************************************************************************/
   public PresentResultsTool(QInstance qInstance)
   {
      this.qInstance = qInstance;
   }



   /*******************************************************************************
    ** Stable schema. Strict opt-in requires empty placeholders instead of many
    ** optional productions, keeping the existing strict grammar budget small.
    *******************************************************************************/
   public static ModelToolDefinition definition(Boolean strict)
   {
      try(InputStream stream = PresentResultsTool.class.getResourceAsStream("/present-results.schema.json"))
      {
         Map<String, Object> schema = JsonUtils.toObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8), new TypeReference<Map<String, Object>>() {});
         if(strict)
         {
            requireProperties(schema);
         }
         return (new ModelToolDefinition().withName(NAME).withDescription("Present an answer as metrics, a table, a chart and actionable links. Use only data from tool results; never supply URLs.")
            .withInputSchema(schema).withStrict(strict));
      }
      catch(Exception e)
      {
         throw (new IllegalStateException("Cannot load presentation schema", e));
      }
   }



   /*******************************************************************************
    ** Eliminate optional productions for strict mode without adding unions.
    *******************************************************************************/
   private static void requireProperties(Object node)
   {
      if(node instanceof Map<?, ?>)
      {
         Map<String, Object> schema = object(node);
         schema.remove("maxLength");
         if(schema.get("properties") instanceof Map<?, ?> properties)
         {
            schema.put("required", new ArrayList<>(properties.keySet()));
         }
         schema.values().forEach(PresentResultsTool::requireProperties);
      }
      else if(node instanceof List<?> list)
      {
         list.forEach(PresentResultsTool::requireProperties);
      }
   }



   /*******************************************************************************
    ** Preserve valid items when others are dropped; never echo rejected values.
    *******************************************************************************/
   public synchronized AiToolResult execute(UiApiToolbox.Context context, String callId, Map<String, Object> input)
   {
      AiToolResult result = new AiToolResult().withToolCallId(callId).withToolName(NAME);
      try
      {
         require(input != null && input.get("summary") instanceof String, "summary required");
         String summary = text(input.get("summary"));
         require(!summary.isBlank() && summary.length() <= 300, "summary must contain 1 to 300 characters");
         List<String> notes = new ArrayList<>();
         List<Map<String, Object>> blocks = new ArrayList<>();
         addItems(blocks, "metrics", input.get("metrics"), notes, item -> metric(context, item));
         Map<String, Object> table = null;
         if(input.get("table") != null)
         {
            try
            {
               table = table(context, object(input.get("table")));
               if(!list(table.get("columns")).isEmpty())
               {
                  blocks.add(table);
               }
            }
            catch(IllegalArgumentException e)
            {
               notes.add("Dropped table: " + e.getMessage());
            }
         }
         if(input.get("chart") != null && table != null && !list(table.get("rows")).isEmpty())
         {
            try
            {
               blocks.add(chart(object(input.get("chart")), table, notes));
            }
            catch(IllegalArgumentException e)
            {
               notes.add("Dropped chart: " + e.getMessage());
            }
         }
         if(input.get("chart") != null && (table == null || list(table.get("rows")).isEmpty()))
         {
            notes.add("Dropped chart: requires a valid nonempty table.");
         }
         addItems(blocks, "filterLinks", input.get("filterLinks"), notes, item -> filterLink(context, item));
         addItems(blocks, "recordLinks", input.get("recordLinks"), notes, item -> recordLink(context, item));
         addItems(blocks, "actions", input.get("actions"), notes, item -> action(context, item));
         Map<String, Object> part = map("type", "presentation", "summary", summary, "blocks", blocks, "markdown", markdown(summary, blocks));
         return (result.withOutcome(AiToolOutcome.OK).withSummary("Presented results").withRender(part)
            .withPermissionCheck(metadata == null ? "presentation:no table references" : "uiApi:GET /metaData:" + metadata.status())
            .withModelContent("Presented to the user: " + summary + (summary.endsWith(".") ? "" : ".") + " Do not repeat the table or links in text." + (notes.isEmpty() ? "" : "\n" + String.join("\n", notes))));
      }
      catch(IllegalArgumentException e)
      {
         return (result.withOutcome(AiToolOutcome.INVALID_INPUT).withSummary("Invalid presentation").withModelContent(e.getMessage()));
      }
   }



   /*******************************************************************************
    ** Resolve tables through the same metadata operation and session as the UI.
    *******************************************************************************/
   private QTableMetaData visibleTable(UiApiToolbox.Context context, String name)
   {
      QTableMetaData table = qInstance.getTable(name);
      require(table != null, "unknown table " + name);
      if(metadata == null)
      {
         try
         {
            metadata = context.gateway().invoke(context.prepare(new UiApiCall("GET", "/metaData")));
         }
         catch(QException e)
         {
            metadata = new UiApiResult(503, "{}");
         }
      }
      require(metadata.isSuccess(), "GET /metaData answered HTTP " + metadata.status());
      Map<String, Object> tables = object(object(metadata.json()).get("tables"));
      require(tables.containsKey(name) && Boolean.TRUE.equals(object(tables.get(name)).get("readPermission")), "table not visible through UI API: " + name);
      return (table);
   }



   /*******************************************************************************
    ** A field reference must be paired with a visible table reference.
    *******************************************************************************/
   private QFieldMetaData field(UiApiToolbox.Context context, Map<String, Object> item)
   {
      String tableName = text(item.get("tableName"));
      String fieldName = text(item.get("fieldName"));
      require(fieldName.isEmpty() || !tableName.isEmpty(), "fieldName requires tableName");
      QTableMetaData table = tableName.isEmpty() ? null : visibleTable(context, tableName);
      QFieldMetaData field = fieldName.isEmpty() ? null : table.getFields().get(fieldName);
      require(fieldName.isEmpty() || field != null, "unknown field " + fieldName);
      return (field);
   }



   /*******************************************************************************
    ** A metric preserves its numeric value and adds the metadata display.
    *******************************************************************************/
   private Map<String, Object> metric(UiApiToolbox.Context context, Map<String, Object> item)
   {
      QFieldMetaData field = field(context, item);
      number(item.get("value"));
      return (map("label", label(item), "value", item.get("value"), "display", display(item.get("value"), field)));
   }



   /*******************************************************************************
    ** Build table cells and percentages from raw values, never model displays.
    *******************************************************************************/
   private Map<String, Object> table(UiApiToolbox.Context context, Map<String, Object> input)
   {
      List<?> columns = list(input.get("columns"));
      List<?> rows = list(input.get("rows"));
      require(columns.size() <= 20 && rows.size() <= 200, "table exceeds 20 columns or 200 rows");
      require(rows.isEmpty() || !columns.isEmpty(), "rows require columns");
      List<QFieldMetaData> fields = new ArrayList<>();
      List<Map<String, Object>> outputColumns = new ArrayList<>();
      List<BigDecimal> totals = new ArrayList<>();
      for(Integer c = 0; c < columns.size(); c++)
      {
         Map<String, Object> column = object(columns.get(c));
         fields.add(field(context, column));
         BigDecimal total = BigDecimal.ZERO;
         Boolean numeric = !rows.isEmpty();
         for(Object row : rows)
         {
            require(list(row).size() == columns.size(), "row width must match columns");
            Object value = list(row).get(c);
            scalar(value);
            if(value instanceof Number)
            {
               total = total.add(number(value));
            }
            else
            {
               numeric = false;
            }
         }
         totals.add(numeric ? total : null);
         outputColumns.add(map("key", "c" + c, "label", label(column), "align", numeric || percentColumn(column) >= 0 ? "right" : "left"));
      }
      Map<String, Object> rowFilter = input.get("rowFilter") == null ? Map.of() : object(input.get("rowFilter"));
      List<?> rowKeys = input.get("rowKeys") == null ? List.of() : list(input.get("rowKeys"));
      Boolean hasFilter = !text(rowFilter.get("tableName")).isEmpty();
      require(!hasFilter || rowKeys.size() == rows.size(), "rowKeys must match rows");
      QFieldMetaData filterField = field(context, rowFilter);
      require(!hasFilter || filterField != null, "rowFilter requires a field");
      List<Map<String, Object>> outputRows = new ArrayList<>();
      for(Integer r = 0; r < rows.size(); r++)
      {
         Map<String, Object> cells = new LinkedHashMap<>();
         Map<String, Object> displays = new LinkedHashMap<>();
         for(Integer c = 0; c < columns.size(); c++)
         {
            Integer source = percentColumn(object(columns.get(c)));
            Object value = list(rows.get(r)).get(c);
            String formatted;
            if(source >= 0)
            {
               require(source < columns.size() && totals.get(source) != null && percentColumn(object(columns.get(source))) < 0, "percentOfColumn must reference a numeric raw column");
               BigDecimal share = totals.get(source).signum() == 0 ? BigDecimal.ZERO : number(list(rows.get(r)).get(source)).divide(totals.get(source), MathContext.DECIMAL64);
               value = share;
               formatted = String.format(Locale.US, "%.1f%%", share.multiply(BigDecimal.valueOf(100)));
            }
            else
            {
               formatted = display(value, fields.get(c));
            }
            cells.put("c" + c, value);
            displays.put("c" + c, formatted);
         }
         Map<String, Object> row = map("cells", cells, "display", displays);
         if(hasFilter)
         {
            Object key = rowKeys.get(r);
            scalar(key);
            Map<String, Object> criterion = map("fieldName", rowFilter.get("fieldName"), "operator", rowFilter.getOrDefault("operator", "EQUALS"), "values", java.util.Collections.singletonList(key));
            row.put("href", filterHref(context, text(rowFilter.get("tableName")), List.of(criterion)));
         }
         outputRows.add(row);
      }
      return (map("kind", "table", "title", text(input.get("title")), "columns", outputColumns, "rows", outputRows));
   }



   /*******************************************************************************
    ** A chart uses the table's raw values, formatted values and row links.
    *******************************************************************************/
   private Map<String, Object> chart(Map<String, Object> input, Map<String, Object> table, List<String> notes)
   {
      String type = text(input.get("chartType"));
      require(List.of("bar", "pie", "line").contains(type), "invalid chartType");
      Integer labelColumn = index(input.get("labelColumn"));
      Integer valueColumn = index(input.get("valueColumn"));
      Integer width = list(table.get("columns")).size();
      require(labelColumn >= 0 && valueColumn >= 0 && labelColumn < width && valueColumn < width, "chart column index out of range");
      List<Map<String, Object>> points = new ArrayList<>();
      List<?> rows = list(table.get("rows"));
      for(Object entry : rows)
      {
         Map<String, Object> row = object(entry);
         Object value = object(row.get("cells")).get("c" + valueColumn);
         number(value);
         if(points.size() < 12)
         {
            Map<String, Object> point = map("label", object(row.get("display")).get("c" + labelColumn), "value", value, "display", object(row.get("display")).get("c" + valueColumn));
            if(row.containsKey("href"))
            {
               point.put("href", row.get("href"));
            }
            points.add(point);
         }
      }
      if(rows.size() > 12)
      {
         notes.add("Chart truncated to 12 points; the table retains all rows.");
      }
      return (map("kind", "chart", "chartType", type, "title", text(input.get("title")), "points", points));
   }



   /*******************************************************************************
    ** A filtered list link, including only validated metadata and raw values.
    *******************************************************************************/
   private Map<String, Object> filterLink(UiApiToolbox.Context context, Map<String, Object> item)
   {
      String tableName = text(item.get("tableName"));
      return (map("label", label(item), "tableName", tableName, "href", filterHref(context, tableName, list(item.get("criteria")))));
   }



   /*******************************************************************************
    ** Serialize the UI's JSON filter format. The table path uses core navigate's
    ** helper; filter values stay raw inside the URL-encoded JSON.
    *******************************************************************************/
   private String filterHref(UiApiToolbox.Context context, String tableName, List<?> criteria)
   {
      visibleTable(context, tableName);
      require(!criteria.isEmpty() && criteria.size() <= 20, "filter requires 1 to 20 criteria");
      List<Map<String, Object>> output = new ArrayList<>();
      for(Object entry : criteria)
      {
         Map<String, Object> criterion = object(entry);
         String fieldName = text(criterion.get("fieldName"));
         require(!fieldName.isBlank(), "filter requires fieldName");
         field(context, map("tableName", tableName, "fieldName", fieldName));
         String operator = text(criterion.get("operator"));
         operator = operator.isEmpty() ? "EQUALS" : operator;
         try
         {
            QCriteriaOperator.valueOf(operator);
         }
         catch(IllegalArgumentException e)
         {
            throw (new IllegalArgumentException("invalid criteria operator"));
         }
         List<?> values = list(criterion.get("values"));
         require(values.size() <= 200, "too many filter values");
         values.forEach(PresentResultsTool::scalar);
         output.add(map("fieldName", fieldName, "operator", operator, "values", values));
      }
      return (safeHref(AiLinkBuilder.objectHref(tableName) + "?filter=" + URLEncoder.encode(JsonUtils.toJson(map("criteria", output, "booleanOperator", "AND")), StandardCharsets.UTF_8)));
   }



   /*******************************************************************************
    ** Record ids are opaque path segments, encoded by core's link helper.
    *******************************************************************************/
   private Map<String, Object> recordLink(UiApiToolbox.Context context, Map<String, Object> item)
   {
      String tableName = text(item.get("tableName"));
      visibleTable(context, tableName);
      Object id = item.get("recordId");
      require(id instanceof String || id instanceof Number, "recordId must be a string or number");
      String recordId = text(id);
      require(!recordId.isBlank() && !List.of(".", "..").contains(recordId), "invalid recordId");
      return (map("label", label(item), "tableName", tableName, "recordId", recordId, "href", safeHref(AiLinkBuilder.recordHref(tableName, recordId))));
   }



   /*******************************************************************************
    ** Actions are either a next-message prompt or a visible table destination.
    *******************************************************************************/
   private Map<String, Object> action(UiApiToolbox.Context context, Map<String, Object> item)
   {
      String prompt = text(item.get("prompt"));
      String destination = text(item.get("navigateTo"));
      require(prompt.isBlank() != destination.isBlank(), "action requires exactly one of prompt or navigateTo");
      if(!destination.isBlank())
      {
         visibleTable(context, destination);
         return (map("label", label(item), "href", safeHref(AiLinkBuilder.objectHref(destination))));
      }
      return (map("label", label(item), "prompt", prompt));
   }



   /*******************************************************************************
    ** Preserve independently valid items and report dropped positions.
    *******************************************************************************/
   private static void addItems(List<Map<String, Object>> blocks, String kind, Object input, List<String> notes, Function<Map<String, Object>, Map<String, Object>> build)
   {
      if(input == null)
      {
         return;
      }
      List<?> entries = list(input);
      require(entries.size() <= 100, "too many " + kind);
      List<Map<String, Object>> items = new ArrayList<>();
      for(Integer i = 0; i < entries.size(); i++)
      {
         try
         {
            items.add(build.apply(object(entries.get(i))));
         }
         catch(IllegalArgumentException e)
         {
            notes.add("Dropped " + kind + "[" + i + "]: " + e.getMessage());
         }
      }
      if(!items.isEmpty())
      {
         blocks.add(map("kind", kind, "items", items));
      }
   }



   /*******************************************************************************
    ** Metadata formats use QQQ field coercion and Java display format strings.
    *******************************************************************************/
   private static String display(Object value, QFieldMetaData field)
   {
      if(value == null)
      {
         return ("");
      }
      if(field != null)
      {
         try
         {
            Object typed = ValueUtils.getValueAsFieldType(field.getType(), value);
            if(field.getDisplayFormat() != null)
            {
               return (String.format(Locale.US, field.getDisplayFormat(), typed));
            }
            value = typed;
         }
         catch(Exception e)
         {
            throw (new IllegalArgumentException("value does not match field format"));
         }
      }
      if(value instanceof Number)
      {
         BigDecimal decimal = number(value);
         return (decimal.stripTrailingZeros().scale() <= 0 ? String.format(Locale.US, "%,d", decimal.toBigIntegerExact()) : decimal.toPlainString());
      }
      return (text(value));
   }



   /*******************************************************************************
    ** Complete GFM fallback with real newlines; chart data is already in table.
    *******************************************************************************/
   private static String markdown(String summary, List<Map<String, Object>> blocks)
   {
      StringBuilder markdown = new StringBuilder(escape(summary)).append("\n");
      for(Map<String, Object> block : blocks)
      {
         if("table".equals(block.get("kind")))
         {
            markdown.append("\n").append(escape(text(block.get("title")))).append("\n\n|");
            List<?> columns = list(block.get("columns"));
            for(Object column : columns)
            {
               markdown.append(' ').append(escape(text(object(column).get("label")))).append(" |");
            }
            markdown.append("\n|");
            for(Object column : columns)
            {
               markdown.append("right".equals(object(column).get("align")) ? " ---: |" : " --- |");
            }
            for(Object entry : list(block.get("rows")))
            {
               Map<String, Object> row = object(entry);
               markdown.append("\n|");
               for(Integer c = 0; c < columns.size(); c++)
               {
                  String value = escape(text(object(row.get("display")).get("c" + c)));
                  markdown.append(' ').append(c == 0 && row.containsKey("href") ? "[" + value + "](" + row.get("href") + ")" : value).append(" |");
               }
            }
            markdown.append("\n");
         }
         else if(block.get("items") instanceof List<?> items)
         {
            markdown.append("\n");
            for(Object entry : items)
            {
               Map<String, Object> item = object(entry);
               String label = escape(text(item.get("label")));
               markdown.append("- ");
               if(item.containsKey("href"))
               {
                  markdown.append('[').append(label).append("](").append(item.get("href")).append(')');
               }
               else
               {
                  markdown.append(label);
                  if(item.containsKey("display") || item.containsKey("prompt"))
                  {
                     markdown.append(": ").append(escape(text(item.getOrDefault("display", item.get("prompt")))));
                  }
               }
               markdown.append("\n");
            }
         }
      }
      return (markdown.toString());
   }



   /*******************************************************************************
    ** Literal text only: no Markdown links, HTML or GFM URL autolinks from labels.
    *******************************************************************************/
   private static String escape(String value)
   {
      StringBuilder output = new StringBuilder();
      for(char character : value.toCharArray())
      {
         if("\\`*_{}[]()<>|!&#:.@~".indexOf(character) >= 0)
         {
            output.append("&#").append((int) character).append(';');
         }
         else
         {
            output.append(character);
         }
      }
      return (output.toString());
   }



   /*******************************************************************************
    ** Reject unsafe paths even if metadata contains unexpected names.
    *******************************************************************************/
   private static String safeHref(String href)
   {
      require(AiLinkBuilder.isSafeAppPath(href), "unsafe application path");
      return (href);
   }



   /*******************************************************************************
    ** A column with no percentage uses -1 in strict mode.
    *******************************************************************************/
   private static Integer percentColumn(Map<String, Object> column)
   {
      return (column.get("percentOfColumn") == null ? -1 : index(column.get("percentOfColumn")));
   }



   /*******************************************************************************
    ** Indexes must be integral; never silently truncate model input.
    *******************************************************************************/
   private static Integer index(Object value)
   {
      try
      {
         return (number(value).intValueExact());
      }
      catch(ArithmeticException e)
      {
         throw (new IllegalArgumentException("index must be an integer"));
      }
   }



   /*******************************************************************************
    ** Finite numeric input only.
    *******************************************************************************/
   private static BigDecimal number(Object value)
   {
      require(value instanceof Number, "numeric value required");
      try
      {
         return (new BigDecimal(value.toString()));
      }
      catch(NumberFormatException e)
      {
         throw (new IllegalArgumentException("finite numeric value required"));
      }
   }



   /*******************************************************************************
    ** Cells and filter values remain raw JSON scalars.
    *******************************************************************************/
   private static void scalar(Object value)
   {
      require(value == null || value instanceof String || value instanceof Number || value instanceof Boolean, "scalar value required");
      if(value instanceof Number)
      {
         number(value);
      }
      if(value instanceof String)
      {
         text(value);
      }
   }



   /*******************************************************************************
    ** Bounded single-line display text.
    *******************************************************************************/
   private static String text(Object value)
   {
      require(value == null || value instanceof String || value instanceof Number || value instanceof Boolean, "text or scalar required");
      String text = value == null ? "" : String.valueOf(value);
      require(text.length() <= 2000, "text exceeds 2000 characters");
      return (text.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", " "));
   }



   /*******************************************************************************
    ** Labels are mandatory plain text.
    *******************************************************************************/
   private static String label(Map<String, Object> item)
   {
      String label = text(item.get("label"));
      require(!label.isBlank(), "label required");
      return (label);
   }



   /*******************************************************************************
    ** Validate a JSON object without converting arbitrary values to strings.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static Map<String, Object> object(Object value)
   {
      require(value instanceof Map<?, ?>, "object required");
      return ((Map<String, Object>) value);
   }



   /*******************************************************************************
    ** Validate a JSON array.
    *******************************************************************************/
   private static List<?> list(Object value)
   {
      require(value instanceof List<?>, "array required");
      return ((List<?>) value);
   }



   /*******************************************************************************
    ** Ordered output maps allow null raw cells.
    *******************************************************************************/
   private static Map<String, Object> map(Object... pairs)
   {
      Map<String, Object> map = new LinkedHashMap<>();
      for(Integer i = 0; i < pairs.length; i += 2)
      {
         map.put((String) pairs[i], pairs[i + 1]);
      }
      return (map);
   }



   /*******************************************************************************
    ** Validation failures contain only server-authored explanations.
    *******************************************************************************/
   private static void require(Boolean condition, String message)
   {
      if(!condition)
      {
         throw (new IllegalArgumentException(message));
      }
   }
}
