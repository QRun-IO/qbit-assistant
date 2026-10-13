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

package com.kingsrook.qbits.assistant.home;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.kingsrook.qbits.assistant.metadata.AssistantRecordScreens;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantModelPrice;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Renders the Assistant home: a status row, one row per provider across the
 ** full width, then cards for usage by day, costs this month, usage by user,
 ** top tools and recent failures. Every card has an honest empty state.
 ** Layout is flow and grid only (no tables, no fixed widths, no inner
 ** scrolling): rows wrap their cells, cards stack to one column on narrow
 ** screens, long names wrap, and colors are inherited (only status dots and
 ** bars use a fixed color), so it reads at every width in light and dark mode.
 *******************************************************************************/
public final class AssistantHomeView
{
   private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
   private static final DateTimeFormatter DAY  = DateTimeFormatter.ofPattern("MMM d", Locale.US);

   private static final String ROOT  = "display:grid;gap:1rem;min-width:0;";
   private static final String GRID  = "display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,22rem),1fr));gap:1rem;align-items:start;min-width:0;";
   private static final String CARD  = "border:1px solid rgba(127,127,127,.3);border-radius:8px;padding:1rem;min-width:0;overflow-wrap:anywhere;";
   private static final String ROW   = "border:1px solid rgba(127,127,127,.25);border-radius:6px;padding:.75rem;margin:0 0 .75rem;min-width:0;";
   private static final String CELLS = "display:grid;grid-template-columns:repeat(auto-fit,minmax(9.5rem,1fr));gap:.6rem 1rem;margin:.6rem 0 0;font-size:.9rem;";
   private static final String LIST  = "display:grid;grid-template-columns:minmax(6rem,auto) 1fr;gap:.25rem .75rem;margin:0 0 .75rem;font-size:.9rem;";
   private static final String ITEMS = "margin:0 0 .75rem;padding-left:1.25rem;list-style:disc;font-size:.9rem;display:grid;gap:.35rem;";
   private static final String LINE  = "display:flex;flex-wrap:wrap;justify-content:space-between;gap:.15rem 1rem;padding:.35rem 0;border-bottom:1px solid rgba(127,127,127,.2);font-size:.9rem;";
   private static final String MUTED = "opacity:.75;";
   private static final String LINK  = "color:inherit;text-decoration:underline;text-underline-offset:2px;";
   private static final String CHIP  = "display:inline-flex;align-items:center;gap:.4rem;padding:.05rem .6rem;border-radius:999px;border:1px solid rgba(127,127,127,.4);font-size:.8rem;white-space:nowrap;";
   private static final String H     = "margin:0 0 .75rem;font-size:1rem;font-weight:600;";

   private static final String GREEN = "#2e9d5b";
   private static final String AMBER = "#c98a12";
   private static final String RED   = "#d0453a";
   private static final String GREY  = "#8a8a8a";
   private static final String BAR   = "#4a7fd4";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AssistantHomeView()
   {
   }



   /*******************************************************************************
    ** The whole home; prefix is the front of table and record links.
    *******************************************************************************/
   public static String render(AssistantHomeData data, String prefix)
   {
      StringBuilder html = new StringBuilder("<div class=\"assistant-home\" style=\"" + ROOT + "\">");
      status(html, data, prefix);
      providers(html, data, prefix);
      html.append("<div style=\"").append(GRID).append("\">");
      usageByDay(html, data, prefix);
      costs(html, data, prefix);
      byUser(html, data);
      tools(html, data, prefix);
      failures(html, data, prefix);
      denials(html, data, prefix);
      html.append("</div>");
      if(data.isTruncated())
      {
         html.append("<p style=\"").append(MUTED).append("font-size:.85rem;margin:0;\">Some totals are partial: a table had more rows in this period than the home reads.</p>");
      }
      return (html.append("</div>").toString());
   }



   /*******************************************************************************
    ** The status row: is the assistant answering, with what, at what cost.
    *******************************************************************************/
   private static void status(StringBuilder html, AssistantHomeData data, String prefix)
   {
      open(html, "Health");
      AssistantHomeData.ProviderHealth active = data.getProviders().stream().filter(AssistantHomeData.ProviderHealth::active).findFirst().orElse(null);

      html.append("<div style=\"display:flex;flex-wrap:wrap;gap:.5rem .75rem;align-items:center;\">");
      if(data.isKillSwitch())
      {
         html.append(chip(RED, "Turned off by the kill switch"));
      }
      else if(data.getProviders().isEmpty())
      {
         html.append(chip(GREY, "No provider configured here (the application's built-in provider is used, if any)"));
      }
      else if(active == null)
      {
         html.append(chip(RED, "No provider is turned on"));
      }
      else if(!active.keyPresent() || !active.problems().isEmpty())
      {
         html.append(chip(RED, "Default provider cannot be used"));
      }
      else if("ERROR".equals(active.row().getLastTestStatus()))
      {
         html.append(chip(AMBER, "Last connection test failed"));
      }
      else
      {
         html.append(chip(GREEN, "Answering"));
      }
      html.append("</div><div style=\"").append(CELLS).append("\">");
      cell(html, "Provider", active == null ? "None" : active.row().getName() + " (" + nvl(active.row().getModel(), "default model") + ")");
      cell(html, "HIPAA mode", Boolean.TRUE.equals(data.getHipaaMode()) ? "On" : "Off");
      cell(html, "PHI retention", data.getHipaaRetentionDays() == null ? "Normal retention" : data.getHipaaRetentionDays() + " days");
      cell(html, "API key", active == null ? "Not applicable" : active.keyStatus());
      cell(html, "Last test", active == null ? "Not applicable" : lastTest(active.row()));
      Integer rate = data.errorRatePercent7d();
      cell(html, "Error rate, 7 days", rate == null ? "No requests yet" : rate + "% of " + number(data.getCalls7d()) + " requests");
      cell(html, "Cost this month", money(data.getMonthCostMicros()) + (data.getInstanceMonthlyCapMicros() == null ? "" : " of " + money(data.getInstanceMonthlyCapMicros()) + " limit"));
      cell(html, "Requests this month", number(data.getMonthRequests()));
      html.append("</div>");
      close(html);
   }



   /*******************************************************************************
    ** One row per provider.
    *******************************************************************************/
   private static void providers(StringBuilder html, AssistantHomeData data, String prefix)
   {
      open(html, "Providers");
      if(data.getProviders().isEmpty())
      {
         empty(html, "No providers yet. Add one from Providers, New: pick the type and model, and name the environment variable that holds the API key.");
      }
      for(AssistantHomeData.ProviderHealth p : data.getProviders())
      {
         AssistantProvider row = p.row();
         html.append("<div style=\"").append(ROW).append("\"><div style=\"display:flex;flex-wrap:wrap;align-items:center;gap:.5rem .75rem;\">");
         html.append("<strong>").append(recordLink(prefix, AssistantProvider.TABLE_NAME, row.getId(), row.getName())).append("</strong>");
         if(p.active())
         {
            html.append(chip(GREEN, "In use"));
         }
         else if(Boolean.TRUE.equals(row.getIsDefault()))
         {
            html.append(chip(GREY, "Default"));
         }
         if(!Boolean.TRUE.equals(row.getEnabled()))
         {
            html.append(chip(GREY, "Off"));
         }
         if(row.getLastTestStatus() != null)
         {
            html.append(chip("OK".equals(row.getLastTestStatus()) ? GREEN : RED, "OK".equals(row.getLastTestStatus()) ? "Last test passed" : "Last test failed"));
         }
         html.append("</div><div style=\"").append(CELLS).append("\">");
         cell(html, "Type", nvl(p.typeLabel(), "Not set"));
         cell(html, "Kind", AssistantRecordScreens.providerKindLabel(p.kind()));
         cell(html, "Location", "LOCAL".equals(p.location()) ? "Local" : "Cloud");
         if(Boolean.TRUE.equals(row.getHipaaEligible()))
         {
            cell(html, "Eligibility", "HIPAA-eligible");
         }
         cell(html, "Enabled models", String.valueOf(p.enabledModels()));
         cell(html, "Model", nvl(row.getModel(), "Default model"));
         cell(html, "API key", p.keyStatus());
         cell(html, "Last test", lastTest(row));
         cell(html, "Cost this month", money(p.monthCostMicros()) + (p.capMicros() == null ? ", no cap" : " of " + money(p.capMicros()) + " cap"));
         List<String> limits = new ArrayList<>();
         if(row.getMaxOutputTokensPerRequest() != null)
         {
            limits.add(number(row.getMaxOutputTokensPerRequest()) + " output tokens per request");
         }
         if(row.getMaxConcurrentRequests() != null)
         {
            limits.add(row.getMaxConcurrentRequests() + " at a time");
         }
         cell(html, "Limits", limits.isEmpty() ? "None" : String.join(", ", limits));
         html.append("</div>");
         if(!p.problems().isEmpty())
         {
            html.append("<ul style=\"").append(ITEMS).append("margin-top:.6rem;\">");
            p.problems().forEach(problem -> html.append("<li>").append(dot(RED)).append(esc(problem)).append("</li>"));
            html.append("</ul>");
         }
         html.append("</div>");
      }
      html.append("<div style=\"display:flex;flex-wrap:wrap;gap:.5rem 1rem;\">");
      link(html, prefix, AssistantProvider.TABLE_NAME, "Providers");
      link(html, prefix, AssistantModelPrice.TABLE_NAME, "Model prices");
      html.append("</div>");
      close(html);
   }



   /*******************************************************************************
    ** Usage by day: bars of requests, then the recent days in words.
    *******************************************************************************/
   private static void usageByDay(StringBuilder html, AssistantHomeData data, String prefix)
   {
      open(html, "Usage, last " + AssistantHomeData.DAYS + " days");
      Map<LocalDate, AssistantHomeData.DayUsage> days = data.getDays();
      long conversations = 0;
      long requests      = 0;
      long tokensIn      = 0;
      long tokensOut     = 0;
      long cost          = 0;
      long max           = 1;
      for(AssistantHomeData.DayUsage day : days.values())
      {
         conversations += day.conversations;
         requests += day.requests;
         tokensIn += day.tokensIn;
         tokensOut += day.tokensOut;
         cost += day.costMicros;
         max = Math.max(max, day.requests);
      }

      if(requests == 0 && conversations == 0)
      {
         empty(html, "No assistant use in the last " + AssistantHomeData.DAYS + " days.");
      }
      else
      {
         html.append("<div role=\"img\" aria-label=\"Model requests per day, last ").append(days.size()).append(" days\" style=\"display:flex;align-items:flex-end;gap:2px;height:3rem;margin:0 0 .25rem;\">");
         for(Map.Entry<LocalDate, AssistantHomeData.DayUsage> entry : days.entrySet())
         {
            AssistantHomeData.DayUsage day = entry.getValue();
            int pct = (int) Math.max(day.requests == 0 ? 0 : 4, Math.round(day.requests * 100.0 / max));
            html.append("<span title=\"").append(esc(DAY.format(entry.getKey()) + ": " + day.requests + " requests")).append("\" style=\"flex:1 1 0;min-width:0;height:").append(pct)
               .append("%;background:").append(day.errors > 0 ? AMBER : BAR).append(";border-radius:2px 2px 0 0;\"></span>");
         }
         html.append("</div><div style=\"display:flex;justify-content:space-between;font-size:.75rem;margin:0 0 .75rem;").append(MUTED).append("\"><span>")
            .append(esc(DAY.format(days.keySet().iterator().next()))).append("</span><span>").append(esc(DAY.format(data.getToday()))).append("</span></div>");

         html.append("<dl style=\"").append(LIST).append("\">");
         row(html, "Conversations", number(conversations));
         row(html, "Requests", number(requests));
         row(html, "Tokens in", number(tokensIn));
         row(html, "Tokens out", number(tokensOut));
         row(html, "Cost", money(cost));
         html.append("</dl>");

         List<LocalDate> recent = new ArrayList<>(days.keySet());
         recent = recent.subList(Math.max(0, recent.size() - 7), recent.size());
         html.append("<div style=\"margin:0 0 .75rem;\">");
         for(int i = recent.size() - 1; i >= 0; i--)
         {
            AssistantHomeData.DayUsage day = days.get(recent.get(i));
            html.append("<div style=\"").append(LINE).append("\"><span>").append(esc(DAY.format(recent.get(i)))).append("</span><span style=\"").append(MUTED).append("\">")
               .append(esc(day.conversations + " conv, " + day.requests + " req, " + compact(day.tokensIn) + " in, " + compact(day.tokensOut) + " out, " + money(day.costMicros)))
               .append("</span></div>");
         }
         html.append("</div>");
      }
      html.append("<div style=\"display:flex;flex-wrap:wrap;gap:.5rem 1rem;\">");
      link(html, prefix, AssistantUsage.TABLE_NAME, "All model calls");
      link(html, prefix, AssistantConversation.TABLE_NAME, "Conversations");
      html.append("</div>");
      close(html);
   }



   /*******************************************************************************
    ** Costs this month: total against caps, then by model.
    *******************************************************************************/
   private static void costs(StringBuilder html, AssistantHomeData data, String prefix)
   {
      open(html, "Costs this month");
      html.append("<dl style=\"").append(LIST).append("\">");
      row(html, "Month to date", money(data.getMonthCostMicros()));
      if(data.getInstanceMonthlyCapMicros() != null)
      {
         row(html, "Monthly limit", money(data.getInstanceMonthlyCapMicros()) + " (" + percent(data.getMonthCostMicros(), data.getInstanceMonthlyCapMicros()) + " used)");
      }
      for(AssistantHomeData.ProviderHealth p : data.getProviders())
      {
         if(p.capMicros() != null)
         {
            row(html, p.row().getName() + " cap", money(p.capMicros()) + " (" + percent(p.monthCostMicros(), p.capMicros()) + " used)");
         }
      }
      html.append("</dl>");
      if(data.getByModel().isEmpty())
      {
         empty(html, "No model calls this month.");
      }
      else
      {
         html.append("<div style=\"margin:0 0 .75rem;\">");
         for(AssistantHomeData.Totals t : data.getByModel())
         {
            html.append("<div style=\"").append(LINE).append("\"><span>").append(esc(t.key)).append("</span><span>")
               .append(esc(money(t.costMicros) + ", " + number(t.requests) + " req, " + compact(t.tokensIn) + " in, " + compact(t.tokensOut) + " out")).append("</span></div>");
         }
         html.append("</div>");
      }
      html.append("<p style=\"").append(MUTED).append("font-size:.85rem;margin:0 0 .5rem;\">Cost is tokens times the model's price.</p>");
      link(html, prefix, AssistantModelPrice.TABLE_NAME, "Model prices");
      close(html);
   }



   /*******************************************************************************
    ** Usage by user this month.
    *******************************************************************************/
   private static void byUser(StringBuilder html, AssistantHomeData data)
   {
      open(html, "By user, this month");
      if(data.getByUser().isEmpty())
      {
         empty(html, "No one has used the assistant this month.");
      }
      for(AssistantHomeData.Totals t : data.getByUser())
      {
         html.append("<div style=\"").append(LINE).append("\"><span>").append(esc(t.key)).append("</span><span>")
            .append(esc(money(t.costMicros) + ", " + number(t.requests) + " req, " + compact(t.tokensIn + t.tokensOut) + " tokens")).append("</span></div>");
      }
      close(html);
   }



   /*******************************************************************************
    ** Top tools.
    *******************************************************************************/
   private static void tools(StringBuilder html, AssistantHomeData data, String prefix)
   {
      open(html, "Top tools, last " + AssistantHomeData.DAYS + " days");
      if(data.getByTool().isEmpty())
      {
         empty(html, "No tool calls yet.");
      }
      else
      {
         html.append("<div style=\"margin:0 0 .75rem;\">");
         for(AssistantHomeData.ToolTotals t : data.getByTool())
         {
            html.append("<div style=\"").append(LINE).append("\"><span>").append(esc(t.name)).append("</span><span>")
               .append(esc(number(t.calls) + " calls" + (t.failures == 0 ? "" : ", " + number(t.failures) + " failed") + ", " + (t.totalMs / Math.max(1, t.calls)) + " ms avg"))
               .append("</span></div>");
         }
         html.append("</div>");
      }
      link(html, prefix, AssistantToolCall.TABLE_NAME, "All tool calls");
      close(html);
   }



   /*******************************************************************************
    ** Recent denials: tool calls refused by the UI API (the user's own
    ** permissions), by data access or by the policy. Each says who, what and why.
    *******************************************************************************/
   private static void denials(StringBuilder html, AssistantHomeData data, String prefix)
   {
      open(html, "Recent denials");
      if(data.getDeniedTools().isEmpty())
      {
         empty(html, "No tool call was denied in the last " + AssistantHomeData.DAYS + " days.");
      }
      else
      {
         html.append("<ul style=\"").append(ITEMS).append("\">");
         for(QRecord tool : data.getDeniedTools())
         {
            String status = tool.getValueInteger("httpStatus") == null ? "refused before the UI API (" + nvl(tool.getValueString("permissionCheck"), "assistant setting") + ")"
               : "HTTP " + tool.getValueInteger("httpStatus") + " from " + nvl(tool.getValueString("uiApiOperation"), "the UI API");
            html.append("<li>").append(recordLink(prefix, AssistantToolCall.TABLE_NAME, tool.getValue("id"), nvl(tool.getValueString("toolName"), "Tool call"))).append(": ")
               .append(esc(nvl(tool.getValueString("userId"), "unknown user") + ", " + nvl(tool.getValueString("tableName"), "no table") + ", " + status + ", " + when(tool.getValueInstant("createDate"))))
               .append("</li>");
         }
         html.append("</ul>");
      }
      close(html);
   }



   /*******************************************************************************
    ** Recent failures: model calls and tool calls.
    *******************************************************************************/
   private static void failures(StringBuilder html, AssistantHomeData data, String prefix)
   {
      open(html, "Recent failures");
      if(data.getFailedCalls().isEmpty() && data.getFailedTools().isEmpty())
      {
         empty(html, "No failed model calls or tool calls in the last " + AssistantHomeData.DAYS + " days.");
      }
      else
      {
         html.append("<ul style=\"").append(ITEMS).append("\">");
         for(QRecord call : data.getFailedCalls())
         {
            html.append("<li>").append(recordLink(prefix, AssistantUsage.TABLE_NAME, call.getValue("id"), "Model call #" + call.getValue("id"))).append(": ")
               .append(esc(nvl(call.getValueString("errorKind"), "Error") + (call.getValueInteger("httpStatus") == null ? "" : " (HTTP " + call.getValueInteger("httpStatus") + ")")
                  + ", " + nvl(call.getValueString("model"), "unknown model") + ", " + when(call.getValueInstant("createDate"))))
               .append("</li>");
         }
         for(QRecord tool : data.getFailedTools())
         {
            html.append("<li>").append(recordLink(prefix, AssistantToolCall.TABLE_NAME, tool.getValue("id"), nvl(tool.getValueString("toolName"), "Tool call"))).append(": ")
               .append(esc(label(tool.getValueString("outcome")) + ", " + when(tool.getValueInstant("createDate")))).append("</li>");
         }
         html.append("</ul>");
      }
      close(html);
   }



   /*******************************************************************************
    ** A provider's last test in words.
    *******************************************************************************/
   static String lastTest(AssistantProvider row)
   {
      if(row.getLastTestAt() == null)
      {
         return ("Not tested yet: run Test connection");
      }
      if("OK".equals(row.getLastTestStatus()))
      {
         return ("Passed " + WHEN.format(row.getLastTestAt()) + ", " + nvl(row.getLastTestModel(), "model") + " in " + row.getLastTestLatencyMs() + " ms");
      }
      return ("Failed " + WHEN.format(row.getLastTestAt()) + ": " + nvl(row.getLastTestError(), "no detail"));
   }



   /*******************************************************************************
    ** Micro-dollars as dollars ("$0.00", "<$0.01" for a little, "$12.34").
    *******************************************************************************/
   public static String money(long micros)
   {
      if(micros > 0 && micros < 10_000)
      {
         return ("<$0.01");
      }
      NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
      format.setMinimumFractionDigits(2);
      format.setMaximumFractionDigits(2);
      return ("$" + format.format(BigDecimal.valueOf(micros).movePointLeft(6).setScale(2, RoundingMode.HALF_UP)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String percent(long value, long of)
   {
      return (of <= 0 ? "n/a" : Math.round(value * 100.0 / of) + "%");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static String number(long value)
   {
      return (NumberFormat.getIntegerInstance(Locale.US).format(value));
   }



   /*******************************************************************************
    ** 1234567 as 1.2M, 4500 as 4.5k.
    *******************************************************************************/
   static String compact(long value)
   {
      if(value >= 1_000_000)
      {
         return (BigDecimal.valueOf(value).movePointLeft(6).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "M");
      }
      if(value >= 1_000)
      {
         return (BigDecimal.valueOf(value).movePointLeft(3).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "k");
      }
      return (String.valueOf(value));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String when(Instant instant)
   {
      return (instant == null ? "time unknown" : WHEN.format(instant));
   }



   /*******************************************************************************
    ** SOME_STATUS to Some status.
    *******************************************************************************/
   private static String label(String value)
   {
      if(value == null)
      {
         return ("Unknown");
      }
      String lower = value.replace('_', ' ').toLowerCase(Locale.ROOT);
      return (Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void open(StringBuilder html, String title)
   {
      html.append("<section style=\"").append(CARD).append("\"><h3 style=\"").append(H).append("\">").append(esc(title)).append("</h3>");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void close(StringBuilder html)
   {
      html.append("</section>");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void empty(StringBuilder html, String text)
   {
      html.append("<p style=\"").append(MUTED).append("margin:0 0 .75rem;font-size:.9rem;\">").append(esc(text)).append("</p>");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void cell(StringBuilder html, String label, String value)
   {
      html.append("<div style=\"min-width:0;\"><div style=\"").append(MUTED).append("font-size:.8rem;\">").append(esc(label)).append("</div><div>").append(esc(value)).append("</div></div>");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void row(StringBuilder html, String label, String value)
   {
      html.append("<dt style=\"").append(MUTED).append("\">").append(esc(label)).append("</dt><dd style=\"margin:0;min-width:0;\">").append(esc(value)).append("</dd>");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String chip(String color, String text)
   {
      return ("<span style=\"" + CHIP + "\">" + dot(color) + esc(text) + "</span>");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String dot(String color)
   {
      return ("<span aria-hidden=\"true\" style=\"display:inline-block;width:.55rem;height:.55rem;border-radius:50%;margin-right:.35rem;background:" + color + ";\"></span>");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void link(StringBuilder html, String prefix, String table, String text)
   {
      html.append("<a style=\"").append(LINK).append("font-size:.9rem;\" href=\"").append(esc(prefix + "/" + table)).append("\">").append(esc(text)).append("</a>");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String recordLink(String prefix, String table, Object id, String text)
   {
      return ("<a style=\"" + LINK + "\" href=\"" + esc(prefix + "/" + table + "/" + id) + "\">" + esc(text == null ? String.valueOf(id) : text) + "</a>");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String nvl(String value, String fallback)
   {
      return (value == null || value.isBlank() ? fallback : value);
   }



   /*******************************************************************************
    ** HTML-escape.
    *******************************************************************************/
   static String esc(String value)
   {
      if(value == null)
      {
         return ("");
      }
      StringBuilder out = new StringBuilder(value.length());
      for(char c : value.toCharArray())
      {
         switch(c)
         {
            case '&' -> out.append("&amp;");
            case '<' -> out.append("&lt;");
            case '>' -> out.append("&gt;");
            case '"' -> out.append("&quot;");
            case '\'' -> out.append("&#39;");
            default -> out.append(c);
         }
      }
      return (out.toString());
   }

}
