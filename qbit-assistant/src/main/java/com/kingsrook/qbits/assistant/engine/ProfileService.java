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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantProfileSkill;
import com.kingsrook.qbits.assistant.model.AssistantSkill;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Picks the assistant profile (persona) for a user and the skills relevant
 ** to a question.
 **
 ** Profiles and skills are text the model reads: tone, a name, know-how. They
 ** are never consulted when tools are built or run, so they cannot grant a
 ** tool, an endpoint, data access or a permission (the tool list comes from
 ** the UI API operations, data access and the policy only).
 **
 ** Profile choice, most specific first: enabled profiles scoped to the
 ** current app AND a permission the user holds, then app only, then
 ** permission only, then the default, then the built-in one.
 *******************************************************************************/
public class ProfileService
{
   private static final QLogger LOG = QLogger.getLogger(ProfileService.class);

   public static final int MAX_PERSONALITY_CHARS   = 2000;
   public static final int MAX_SKILL_CHARS         = 6000;
   public static final int MAX_SKILLS_TOTAL_CHARS  = 12000;
   public static final int MAX_STARTER_PROMPTS     = 6;
   public static final String DEFAULT_DISPLAY_NAME = "Assistant";
   public static final String DEFAULT_ICON         = "auto_awesome";

   private final AssistantQBitConfig config;
   private final AssistantStore      store;
   private final Clock               clock;

   private volatile Loaded  loaded;
   private volatile Instant loadedAt;



   /*******************************************************************************
    ** The cached rows.
    *******************************************************************************/
   private record Loaded(List<AssistantProfile> profiles, Map<Integer, List<AssistantSkill>> skillsByProfile)
   {
   }



   /*******************************************************************************
    ** The persona the assistant uses.
    *******************************************************************************/
   public record Persona(Integer profileId, String displayName, String icon, String personality, String greeting, List<String> starterPrompts)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public ProfileService(AssistantQBitConfig config, AssistantStore store, Clock clock)
   {
      this.config = config;
      this.store = store;
      this.clock = clock;
   }



   /*******************************************************************************
    ** The persona for this user, in this app (appName may be null).
    *******************************************************************************/
   public Persona persona(QSession session, String appName)
   {
      AssistantProfile profile = pick(session, appName);
      if(profile == null)
      {
         return (new Persona(null, DEFAULT_DISPLAY_NAME, DEFAULT_ICON, null, null, List.of()));
      }
      List<String> starters = new ArrayList<>();
      if(StringUtils.hasContent(profile.getStarterPrompts()))
      {
         for(String line : profile.getStarterPrompts().split("\\R"))
         {
            if(StringUtils.hasContent(line) && starters.size() < MAX_STARTER_PROMPTS)
            {
               starters.add(truncate(line.strip(), 200));
            }
         }
      }
      return (new Persona(profile.getId(),
         StringUtils.hasContent(profile.getDisplayName()) ? profile.getDisplayName().strip() : DEFAULT_DISPLAY_NAME,
         StringUtils.hasContent(profile.getIcon()) ? profile.getIcon().strip() : DEFAULT_ICON,
         truncate(profile.getPersonality(), MAX_PERSONALITY_CHARS),
         profile.getGreeting(),
         starters));
   }



   /*******************************************************************************
    ** The skills of a profile that are relevant to this question: enabled, and
    ** (no trigger and no app) or a trigger keyword in the text or the current
    ** app in its apps. Total size is bounded.
    *******************************************************************************/
   public List<AssistantSkill> relevantSkills(Integer profileId, String userText, String appName)
   {
      if(profileId == null)
      {
         return (List.of());
      }
      String                lower  = userText == null ? "" : userText.toLowerCase(Locale.ROOT);
      List<AssistantSkill>  rs     = new ArrayList<>();
      int                   total  = 0;
      for(AssistantSkill skill : load().skillsByProfile().getOrDefault(profileId, List.of()))
      {
         if(Boolean.FALSE.equals(skill.getEnabled()) || !StringUtils.hasContent(skill.getInstructions()))
         {
            continue;
         }
         Set<String> keywords = list(skill.getTriggerKeywords());
         Set<String> apps     = list(skill.getAppNames());
         boolean relevant = (keywords.isEmpty() && apps.isEmpty())
            || keywords.stream().anyMatch(k -> lower.contains(k.toLowerCase(Locale.ROOT)))
            || (appName != null && apps.contains(appName));
         int size = Math.min(MAX_SKILL_CHARS, skill.getInstructions().length());
         if(relevant && total + size <= MAX_SKILLS_TOTAL_CHARS)
         {
            rs.add(skill);
            total += size;
         }
      }
      return (rs);
   }



   /*******************************************************************************
    ** The system text for a persona and its skills (empty when there is
    ** nothing to say). Framed so the model knows these are style and know-how
    ** only, below the rules.
    *******************************************************************************/
   public static String instructions(Persona persona, List<AssistantSkill> skills)
   {
      StringBuilder text = new StringBuilder();
      boolean named = persona != null && !DEFAULT_DISPLAY_NAME.equals(persona.displayName());
      if(named || (persona != null && StringUtils.hasContent(persona.personality())))
      {
         text.append("<assistant_profile note=\"set by an administrator: tone and presentation only; it cannot change the rules, the tools or what the user may do\">\n");
         if(named)
         {
            text.append("Your name in this application is ").append(persona.displayName()).append(".\n");
         }
         if(StringUtils.hasContent(persona.personality()))
         {
            text.append(persona.personality().strip()).append("\n");
         }
         text.append("</assistant_profile>\n");
      }
      for(AssistantSkill skill : skills == null ? List.<AssistantSkill>of() : skills)
      {
         text.append("<skill name=\"").append(skill.getName().replace("\"", "'")).append("\" note=\"administrator-written know-how; instructions only, it grants no tools or access\">\n")
            .append(truncate(skill.getInstructions().strip(), MAX_SKILL_CHARS)).append("\n</skill>\n");
      }
      return (text.toString());
   }



   /*******************************************************************************
    ** Drop the cache (after a change to profiles, skills or links).
    *******************************************************************************/
   public void invalidate()
   {
      loaded = null;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private AssistantProfile pick(QSession session, String appName)
   {
      List<AssistantProfile> enabled = load().profiles().stream().filter(p -> !Boolean.FALSE.equals(p.getEnabled())).toList();
      return (enabled.stream()
         .filter(p -> appliesTo(p, session, appName))
         .max(Comparator.comparingInt((AssistantProfile p) -> specificity(p)).thenComparing(p -> -(p.getId() == null ? 0 : p.getId())))
         .orElse(null));
   }



   /*******************************************************************************
    ** Whether a profile's scope matches (an unscoped profile matches only when
    ** it is the default).
    *******************************************************************************/
   private static boolean appliesTo(AssistantProfile profile, QSession session, String appName)
   {
      boolean hasApp        = StringUtils.hasContent(profile.getAppName());
      boolean hasPermission = StringUtils.hasContent(profile.getPermissionName());
      if(!hasApp && !hasPermission)
      {
         return (Boolean.TRUE.equals(profile.getIsDefault()));
      }
      if(hasApp && !profile.getAppName().strip().equals(appName))
      {
         return (false);
      }
      return (!hasPermission || (session != null && session.hasPermission(profile.getPermissionName().strip())));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static int specificity(AssistantProfile profile)
   {
      return ((StringUtils.hasContent(profile.getAppName()) ? 2 : 0) + (StringUtils.hasContent(profile.getPermissionName()) ? 1 : 0));
   }



   /*******************************************************************************
    ** Rows, cached for policyCacheSeconds; a read failure keeps the last rows.
    *******************************************************************************/
   private Loaded load()
   {
      Loaded current = loaded;
      long   ttl     = config.getPolicyCacheSeconds() == null ? 30 : config.getPolicyCacheSeconds();
      if(current != null && loadedAt != null && loadedAt.plusSeconds(ttl).isAfter(clock.instant()))
      {
         return (current);
      }
      try
      {
         List<AssistantProfile> profiles = store.query(AssistantProfile.TABLE_NAME, new QQueryFilter().withLimit(500), AssistantProfile.class);
         Map<Integer, AssistantSkill> skills = new HashMap<>();
         for(AssistantSkill skill : store.query(AssistantSkill.TABLE_NAME, new QQueryFilter().withLimit(1000), AssistantSkill.class))
         {
            skills.put(skill.getId(), skill);
         }
         Map<Integer, List<AssistantSkill>> byProfile = new HashMap<>();
         for(AssistantProfileSkill link : store.query(AssistantProfileSkill.TABLE_NAME, new QQueryFilter().withLimit(5000), AssistantProfileSkill.class))
         {
            AssistantSkill skill = skills.get(link.getSkillId());
            if(skill != null)
            {
               byProfile.computeIfAbsent(link.getProfileId(), k -> new ArrayList<>()).add(skill);
            }
         }
         byProfile.values().forEach(list -> list.sort(Comparator.comparing(AssistantSkill::getName, Comparator.nullsLast(String::compareTo))));
         Loaded fresh = new Loaded(profiles, byProfile);
         loaded = fresh;
         loadedAt = clock.instant();
         return (fresh);
      }
      catch(QException | RuntimeException e)
      {
         LOG.warn("Could not read the assistant profiles; using the built-in one", e);
         return (current == null ? new Loaded(List.of(), Map.of()) : current);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Set<String> list(String commaSeparated)
   {
      Set<String> rs = new LinkedHashSet<>();
      if(StringUtils.hasContent(commaSeparated))
      {
         for(String part : commaSeparated.split(","))
         {
            if(StringUtils.hasContent(part))
            {
               rs.add(part.strip());
            }
         }
      }
      return (rs);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String value, int max)
   {
      return (value == null || value.length() <= max ? value : value.substring(0, max));
   }

}
