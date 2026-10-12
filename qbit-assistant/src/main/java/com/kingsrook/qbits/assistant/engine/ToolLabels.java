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


import java.util.Map;


/*******************************************************************************
 ** Human labels for tool steps shown before a result arrives (tool.start)
 ** and as a fallback summary.
 *******************************************************************************/
public class ToolLabels
{
   private static final Map<String, String[]> LABELS = Map.ofEntries(
      Map.entry("list_tables", new String[] { "Listing tables", "Listed tables" }),
      Map.entry("describe_table", new String[] { "Reading table details", "Read table details" }),
      Map.entry("query_records", new String[] { "Querying records", "Queried records" }),
      Map.entry("count_records", new String[] { "Counting records", "Counted records" }),
      Map.entry("get_record", new String[] { "Opening a record", "Read a record" }),
      Map.entry("search_records", new String[] { "Searching", "Searched" }),
      Map.entry("get_record_history", new String[] { "Reading the change history", "Read the change history" }),
      Map.entry("render_widget", new String[] { "Rendering a widget", "Rendered a widget" }),
      Map.entry("search_help", new String[] { "Searching help", "Searched help" }),
      Map.entry("navigate", new String[] { "Opening a screen", "Opened a screen" })
   );



   /*******************************************************************************
    ** "Querying records" style label.
    *******************************************************************************/
   public static String running(String toolName)
   {
      String[] labels = LABELS.get(toolName);
      return (labels == null ? "Working" : labels[0]);
   }



   /*******************************************************************************
    ** "Queried records" style label.
    *******************************************************************************/
   public static String done(String toolName)
   {
      String[] labels = LABELS.get(toolName);
      return (labels == null ? "Done" : labels[1]);
   }

}
