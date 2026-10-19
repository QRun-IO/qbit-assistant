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

package com.kingsrook.qbits.assistant.engine;


import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.assistant.tools.UiApiToolbox;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiGateway;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.actions.ai.AiToolOutcome;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The example prompts the panel offers on a new conversation, built per user
 ** from what that user can actually reach.
 **
 ** The tables come from the same tool the model uses (list_tables, which is the
 ** UI API's metadata narrowed by data access), the row counts and fields from
 ** count_records and describe_table, all with the user's own session. So a
 ** prompt can only name a table the user may read through the assistant, and
 ** only one that has rows.
 **
 ** Configured profile prompts are kept only when they declare the tables they
 ** need, as a leading tag: {@code [blog_post] Which posts are drafts?} or
 ** {@code [blog_post, page] ...}, and the user can read every one. A line with
 ** {@code [none]} names no table and always stays. A line without a tag is
 ** dropped, because there is no way to tell which tables it mentions.
 **
 ** The result is stable for a user for the day (the pick is seeded by user id
 ** and date, not random), rechecked on every request, bounded in work and time,
 ** and never throws: any failure yields no generated prompts.
 *******************************************************************************/
public class StarterPrompts
{
   private static final QLogger LOG = QLogger.getLogger(StarterPrompts.class);

   public static final int      MAX_PROMPTS        = 3;
   public static final int      MAX_COUNT_CALLS    = 6;
   public static final int      MAX_DESCRIBE_CALLS = 3;
   public static final Duration CALL_TIMEOUT       = Duration.ofSeconds(3);
   public static final Duration TOTAL_BUDGET       = Duration.ofSeconds(8);
   public static final String   NO_TABLE_TAG       = "none";

   private static final int MAX_PROMPT_CHARS = 200;

   private static final ObjectMapper MAPPER = new ObjectMapper();

   private final UiApiToolbox               toolbox;
   private final Clock                      clock;



   /*******************************************************************************
    ** What was worked out for a user: the tables they may read and the
    ** generated prompts for this request.
    *******************************************************************************/
   private record PromptSelection(Set<String> readableTables, List<String> generated)
   {
   }



   /*******************************************************************************
    ** A readable table: its name and its singular label.
    *******************************************************************************/
   private record TableRef(String name, String label)
   {
   }



   /*******************************************************************************
    ** A table that has rows, with its date field (if any).
    *******************************************************************************/
   private record TableWithRows(TableRef table, String dateField)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public StarterPrompts(UiApiToolbox toolbox, Clock clock)
   {
      this.toolbox = toolbox;
      this.clock = clock;
   }



   /*******************************************************************************
    ** The prompts for this user: the configured lines they may see, then
    ** generated ones up to {@link #MAX_PROMPTS} in all. Never throws.
    **
    ** @param context where the calls run: the user's session and the policy
    ** @param userId the daily selection seed; with none, there are no generated prompts
    ** @param configured the profile's starter lines, tags included
    ** @param historyOffered whether the change-history tool is available
    *******************************************************************************/
   public List<String> forUser(UiApiToolbox.Context context, String userId, List<String> configured, boolean historyOffered)
   {
      try
      {
         PromptSelection entry = entryFor(context, userId, historyOffered);
         List<String>  rs    = new ArrayList<>();
         for(String line : configured == null ? List.<String>of() : configured)
         {
            if(rs.size() >= MAX_PROMPTS)
            {
               break;
            }
            String prompt = allowedConfigured(line, entry.readableTables());
            if(prompt != null)
            {
               rs.add(prompt);
            }
         }
         for(String generated : entry.generated())
         {
            if(rs.size() >= MAX_PROMPTS)
            {
               break;
            }
            rs.add(generated);
         }
         return (rs);
      }
      catch(RuntimeException e)
      {
         LOG.warn("Could not build the starter prompts; offering none", e);
         return (List.of());
      }
   }



   /*******************************************************************************
    ** Recompute eligibility with the current session, permissions and data.
    ** Table, row and field access may change without a new session or user id.
    *******************************************************************************/
   private PromptSelection entryFor(UiApiToolbox.Context context, String userId, boolean historyOffered)
   {
      Instant now = clock.instant();
      LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
      if(userId == null)
      {
         return (new PromptSelection(Set.of(), List.of()));
      }
      try
      {
         return (build(limited(context), userId, today, now, historyOffered));
      }
      catch(RuntimeException e)
      {
         LOG.warn("Could not read what the user can access for starter prompts", e);
         return (new PromptSelection(Set.of(), List.of()));
      }
   }



   /*******************************************************************************
    ** The same context with every call held to a short timeout.
    *******************************************************************************/
   private static UiApiToolbox.Context limited(UiApiToolbox.Context context)
   {
      UiApiGateway inner = context.gateway();
      UiApiGateway gateway = new UiApiGateway()
      {
         /*******************************************************************************
          **
          *******************************************************************************/
         @Override
         public boolean isInvocable(String method, String pathTemplate)
         {
            return (inner.isInvocable(method, pathTemplate));
         }



         /*******************************************************************************
          **
          *******************************************************************************/
         @Override
         public UiApiResult invoke(UiApiCall call) throws QException
         {
            return (inner.invoke(call.withTimeout(CALL_TIMEOUT)));
         }
      };
      return (new UiApiToolbox.Context(context.sessionUUID(), context.userTimezone(), gateway, context.policy(), null, context.credentials(), null));
   }



   /*******************************************************************************
    ** Work out the readable tables and the generated prompts.
    *******************************************************************************/
   private PromptSelection build(UiApiToolbox.Context context, String userId, LocalDate today, Instant now, boolean historyOffered)
   {
      Instant deadline = now.plus(TOTAL_BUDGET);
      List<TableRef> readable = readableTables(context);
      Set<String> names = new LinkedHashSet<>();
      readable.forEach(t -> names.add(t.name()));

      List<TableRef> order = new ArrayList<>(readable);
      order.sort(Comparator.comparingInt((TableRef t) -> seed(userId, today, t.name())).thenComparing(TableRef::name));

      List<TableWithRows> withRows = new ArrayList<>();
      int                 counts   = 0;
      for(TableRef table : order)
      {
         if(counts >= MAX_COUNT_CALLS || withRows.size() >= MAX_DESCRIBE_CALLS || clock.instant().isAfter(deadline))
         {
            break;
         }
         counts++;
         if(hasRows(context, table))
         {
            withRows.add(new TableWithRows(table, clock.instant().isAfter(deadline) ? null : dateField(context, table)));
         }
      }

      return (new PromptSelection(names, promptsFor(withRows, userId, today, historyOffered)));
   }



   /*******************************************************************************
    ** The tables the user may read through the assistant (list_tables).
    *******************************************************************************/
   private List<TableRef> readableTables(UiApiToolbox.Context context)
   {
      UiApiToolbox.Outcome outcome = toolbox.execute(context, "starter-list", UiApiToolbox.LIST_TABLES, Map.of());
      Map<String, Object>  data    = json(outcome);
      List<TableRef>       rs      = new ArrayList<>();
      if(data != null && data.get("tables") instanceof List<?> tables)
      {
         for(Object item : tables)
         {
            if(item instanceof Map<?, ?> table && table.get("name") instanceof String name && StringUtils.hasContent(name))
            {
               String label = table.get("label") instanceof String l && StringUtils.hasContent(l) ? l.strip() : name;
               rs.add(new TableRef(name, label));
            }
         }
      }
      return (rs);
   }



   /*******************************************************************************
    ** Whether the table has any row for this user (a count, as the user).
    *******************************************************************************/
   private boolean hasRows(UiApiToolbox.Context context, TableRef table)
   {
      Map<String, Object> filter = new LinkedHashMap<>();
      filter.put("booleanOperator", "AND");
      filter.put("criteria", List.of());
      UiApiToolbox.Outcome outcome = toolbox.execute(context, "starter-count", UiApiToolbox.COUNT_RECORDS, Map.of("table", table.name(), "filter", filter));
      if(outcome.getResult().getOutcome() != AiToolOutcome.OK || outcome.getResult().getRender() == null)
      {
         return (false);
      }
      return (outcome.getResult().getRender().get("count") instanceof Number n && n.longValue() > 0);
   }



   /*******************************************************************************
    ** The table's date-time field whose values the assistant may read (create
    ** date first), else a date field, else null.
    *******************************************************************************/
   private String dateField(UiApiToolbox.Context context, TableRef table)
   {
      UiApiToolbox.Outcome outcome = toolbox.execute(context, "starter-describe", UiApiToolbox.DESCRIBE_TABLE, Map.of("table", table.name()));
      Map<String, Object>  data    = json(outcome);
      if(data == null || !(data.get("fields") instanceof List<?> fields))
      {
         return (null);
      }
      String dateTime = null;
      String date     = null;
      for(Object item : fields)
      {
         if(item instanceof Map<?, ?> field && field.get("name") instanceof String name && field.get("type") instanceof String type && !field.containsKey("values"))
         {
            String normalized = type.replace("_", "").toLowerCase(Locale.ROOT);
            if("datetime".equals(normalized) && (dateTime == null || "createDate".equals(name)))
            {
               dateTime = name;
            }
            else if("date".equals(normalized) && date == null)
            {
               date = name;
            }
         }
      }
      return (dateTime != null ? dateTime : date);
   }



   /*******************************************************************************
    ** The tool result's JSON body (inside the data envelope), or null.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static Map<String, Object> json(UiApiToolbox.Outcome outcome)
   {
      if(outcome.getResult().getOutcome() != AiToolOutcome.OK || outcome.getResult().getModelContent() == null)
      {
         return (null);
      }
      String content = outcome.getResult().getModelContent();
      int    start   = content.indexOf('{');
      int    end     = content.lastIndexOf('}');
      if(start < 0 || end <= start)
      {
         return (null);
      }
      try
      {
         return (MAPPER.readValue(content.substring(start, end + 1), Map.class));
      }
      catch(Exception e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    ** Up to MAX_PROMPTS prompts: one per table first (in the user's daily
    ** order), then more from the same tables when there are too few tables.
    *******************************************************************************/
   private static List<String> promptsFor(List<TableWithRows> tables, String userId, LocalDate today, boolean historyOffered)
   {
      List<List<String>> candidates = new ArrayList<>();
      for(TableWithRows table : tables)
      {
         String       plural = plural(table.table().label());
         List<String> list   = new ArrayList<>();
         if(table.dateField() != null)
         {
            list.add("How many " + plural + " were added in the last 7 days?");
            list.add("Show my most recent " + plural);
         }
         else
         {
            list.add("How many " + plural + " are there?");
         }
         if(historyOffered)
         {
            list.add("What changed in " + plural + " this week?");
         }
         Collections.rotate(list, -Math.floorMod(seed(userId, today, table.table().name()), list.size()));
         candidates.add(list);
      }

      List<String> rs = new ArrayList<>();
      for(int round = 0; rs.size() < MAX_PROMPTS; round++)
      {
         boolean added = false;
         for(List<String> list : candidates)
         {
            if(round < list.size() && rs.size() < MAX_PROMPTS)
            {
               rs.add(truncate(list.get(round)));
               added = true;
            }
         }
         if(!added)
         {
            break;
         }
      }
      return (rs);
   }



   /*******************************************************************************
    ** The configured line without its tag when the user may see it, else null.
    *******************************************************************************/
   static String allowedConfigured(String line, Set<String> readableTables)
   {
      if(line == null)
      {
         return (null);
      }
      String text  = line.strip();
      int    close = text.indexOf(']');
      if(!text.startsWith("[") || close < 0)
      {
         return (null);
      }
      String prompt = text.substring(close + 1).strip();
      if(prompt.isEmpty())
      {
         return (null);
      }
      List<String> tags = new ArrayList<>();
      for(String tag : text.substring(1, close).split(","))
      {
         if(StringUtils.hasContent(tag))
         {
            tags.add(tag.strip());
         }
      }
      if(tags.size() == 1 && NO_TABLE_TAG.equalsIgnoreCase(tags.get(0)))
      {
         return (truncate(prompt));
      }
      if(tags.isEmpty() || !readableTables.containsAll(tags))
      {
         return (null);
      }
      return (truncate(prompt));
   }



   /*******************************************************************************
    ** A label as a lower-case plural ("Blog Post" becomes "blog posts").
    *******************************************************************************/
   static String plural(String label)
   {
      String word = label == null ? "" : label.strip().toLowerCase(Locale.ROOT);
      if(word.isEmpty() || word.endsWith("s") || word.endsWith("media") || word.endsWith("data"))
      {
         return (word);
      }
      if(word.endsWith("x") || word.endsWith("ch") || word.endsWith("sh") || word.endsWith("z"))
      {
         return (word + "es");
      }
      if(word.length() > 1 && word.endsWith("y") && "aeiou".indexOf(word.charAt(word.length() - 2)) < 0)
      {
         return (word.substring(0, word.length() - 1) + "ies");
      }
      return (word + "s");
   }



   /*******************************************************************************
    ** A stable pseudo-random number for a user, a day and a name.
    *******************************************************************************/
   private static int seed(String userId, LocalDate day, String name)
   {
      return ((userId + "|" + day + "|" + name).hashCode());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String value)
   {
      return (value.length() <= MAX_PROMPT_CHARS ? value : value.substring(0, MAX_PROMPT_CHARS));
   }

}
