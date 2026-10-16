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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import com.kingsrook.qqq.backend.core.actions.ai.output.AiRedactor;


/*******************************************************************************
 ** Client error reports (POST /assistant/client-errors): the bounds of every
 ** field, the scrubbing of free text, and the per-user rate limit.
 **
 ** Reported text may contain personal data (an error message can quote a
 ** value), so message and stacks are scrubbed before they are stored: secrets
 ** through the kernel's AiRedactor, then e-mail addresses, long digit runs
 ** (more than 6 digits: phone, account and card numbers, ids), long opaque
 ** tokens (32 or more letters and digits, mixed) and query strings. Short identifiers keep only printable characters.
 ** A route keeps only its path.
 *******************************************************************************/
public class ClientErrors
{
   public static final int SCOPE_MAX           = 64;
   public static final int MESSAGE_MAX         = 500;
   public static final int ERROR_NAME_MAX      = 100;
   public static final int STACK_MAX           = 4000;
   public static final int COMPONENT_STACK_MAX = 4000;
   public static final int REQUEST_ID_MAX      = 128;
   public static final int ROUTE_MAX           = 300;
   public static final int PART_TYPE_MAX       = 64;
   public static final int RENDER_TYPE_MAX     = 64;
   public static final int APP_VERSION_MAX     = 64;

   private static final AiRedactor SECRETS = new AiRedactor(List.of());

   private static final Pattern EMAIL        = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
   private static final Pattern LONG_DIGITS  = Pattern.compile("\\d(?:[\\s.-]?\\d){6,}");
   private static final Pattern LONG_TOKEN   = Pattern.compile("(?=[A-Za-z0-9_+=-]*\\d)(?=[A-Za-z0-9_+=-]*[A-Za-z])[A-Za-z0-9_+=-]{32,}");
   private static final Pattern QUERY_STRING = Pattern.compile("\\?[^\\s)\"']*");
   private static final Pattern CONTROL      = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
   private static final Pattern NOT_TOKEN    = Pattern.compile("[^\\p{Graph} ]");

   private final Clock                       clock;
   private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   public ClientErrors(Clock clock)
   {
      this.clock = clock;
   }



   /*******************************************************************************
    ** Whether one more report from this user fits in the last minute (and
    ** count it if so).
    *******************************************************************************/
   public boolean tryAcquire(String userId, int perMinute)
   {
      Instant        now    = clock.instant();
      Deque<Instant> window = windows.computeIfAbsent(userId, key -> new ArrayDeque<>());
      synchronized(window)
      {
         while(!window.isEmpty() && !window.peekFirst().isAfter(now.minusSeconds(60)))
         {
            window.pollFirst();
         }
         if(window.size() >= perMinute)
         {
            return (false);
         }
         window.addLast(now);
         return (true);
      }
   }



   /*******************************************************************************
    ** Free text (message, stacks): secrets, e-mails, long numbers, tokens and
    ** query strings removed, control characters dropped, then truncated.
    ** Null for a non-string or blank value.
    *******************************************************************************/
   public static String text(Object value, int max)
   {
      if(!(value instanceof String text) || text.isBlank())
      {
         return (null);
      }
      String scrubbed = text.length() > max * 4 ? text.substring(0, max * 4) : text;
      scrubbed = SECRETS.redact(scrubbed, new HashSet<>());
      scrubbed = EMAIL.matcher(scrubbed).replaceAll("[email]");
      scrubbed = QUERY_STRING.matcher(scrubbed).replaceAll("?[query]");
      scrubbed = LONG_TOKEN.matcher(scrubbed).replaceAll("[token]");
      scrubbed = LONG_DIGITS.matcher(scrubbed).replaceAll("[number]");
      scrubbed = CONTROL.matcher(scrubbed).replaceAll("").strip();
      return (truncate(scrubbed, max));
   }



   /*******************************************************************************
    ** A short identifier (scope, error name, request id, types, version):
    ** printable characters only, truncated. Null for a non-string or blank value.
    *******************************************************************************/
   public static String token(Object value, int max)
   {
      if(!(value instanceof String text))
      {
         return (null);
      }
      String cleaned = NOT_TOKEN.matcher(text).replaceAll("").strip();
      cleaned = EMAIL.matcher(cleaned).replaceAll("[email]");
      return (cleaned.isEmpty() ? null : truncate(cleaned, max));
   }



   /*******************************************************************************
    ** The path of a route: scheme, host, query and fragment removed; long
    ** numbers and e-mails scrubbed. Null unless it is a path ("/...").
    *******************************************************************************/
   public static String route(Object value)
   {
      if(!(value instanceof String text))
      {
         return (null);
      }
      String path = text.strip().replaceFirst("^[A-Za-z][A-Za-z0-9+.-]*://[^/]*", "");
      int    cut  = path.indexOf('?');
      path = cut < 0 ? path : path.substring(0, cut);
      cut = path.indexOf('#');
      path = cut < 0 ? path : path.substring(0, cut);
      if(!path.startsWith("/") || path.startsWith("//"))
      {
         return (null);
      }
      path = NOT_TOKEN.matcher(path).replaceAll("").replace(" ", "");
      path = EMAIL.matcher(path).replaceAll("[email]");
      path = LONG_DIGITS.matcher(path).replaceAll("[number]");
      return (truncate(path, ROUTE_MAX));
   }



   /*******************************************************************************
    ** A positive integer id, or null.
    *******************************************************************************/
   public static Integer id(Object value)
   {
      if(value instanceof Number number && number.doubleValue() == number.longValue() && number.longValue() > 0 && number.longValue() <= Integer.MAX_VALUE)
      {
         return (number.intValue());
      }
      return (null);
   }



   /*******************************************************************************
    ** An ISO-8601 instant (with Z or an offset), or null; times in the future
    ** become now.
    *******************************************************************************/
   public Instant occurredAt(Object value)
   {
      if(!(value instanceof String text) || text.length() > 64)
      {
         return (null);
      }
      Instant instant;
      try
      {
         instant = OffsetDateTime.parse(text.strip()).toInstant();
      }
      catch(RuntimeException e)
      {
         try
         {
            instant = Instant.parse(text.strip());
         }
         catch(RuntimeException e2)
         {
            return (null);
         }
      }
      Instant now = clock.instant();
      return (instant.isAfter(now) ? now : instant);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String value, int max)
   {
      return (value.length() <= max ? value : value.substring(0, max));
   }
}
