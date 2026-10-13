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


import com.kingsrook.qqq.backend.core.logging.QLogger;
import java.lang.reflect.Method;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Optional Alerts producer SPI, with log-only fallback and no dependency.
 *******************************************************************************/
public final class BudgetAlerts
{
   private static final QLogger LOG = QLogger.getLogger(BudgetAlerts.class);
   private static final String QALERTS = "com.kingsrook.qqq.backend.core.alerts.QAlerts";
   private static final String ALERT_SIGNAL = "com.kingsrook.qqq.backend.core.alerts.AlertSignal";
   private static final String SEVERITY = "com.kingsrook.qqq.backend.core.alerts.AlertSeverity";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private BudgetAlerts()
   {
   }



   /*******************************************************************************
    ** True when the Alerts SPI is on the classpath.
    *******************************************************************************/
   public static boolean available()
   {
      try
      {
         Class.forName(QALERTS, false, BudgetAlerts.class.getClassLoader());
         return (true);
      }
      catch(ClassNotFoundException | LinkageError e)
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** QAlerts.raise(new AlertSignal(type).withDedupKey(..).withSeverity(..)
    ** .withTitle(..).withSummary(..)), or a log line.
    *******************************************************************************/
   public static void raise(String type, String dedupKey, String severity, String title, String summary)
   {
      LOG.info("Assistant budget alert", logPair("type", type), logPair("dedupKey", dedupKey), logPair("title", title));
      if(!available())
      {
         return;
      }
      try
      {
         ClassLoader loader     = BudgetAlerts.class.getClassLoader();
         Class<?>    signalType = Class.forName(ALERT_SIGNAL, true, loader);
         Object      signal     = signalType.getConstructor(String.class).newInstance(type);
         signal = signalType.getMethod("withDedupKey", String.class).invoke(signal, dedupKey);
         Class<?> severityType = Class.forName(SEVERITY, true, loader);
         @SuppressWarnings({"unchecked", "rawtypes"})
         Object level = Enum.valueOf((Class<? extends Enum>) severityType.asSubclass(Enum.class), severity);
         signal = signalType.getMethod("withSeverity", severityType).invoke(signal, level);
         signal = signalType.getMethod("withTitle", String.class).invoke(signal, title);
         signal = signalType.getMethod("withSummary", String.class).invoke(signal, summary);
         Method raise = Class.forName(QALERTS, true, loader).getMethod("raise", signalType);
         raise.invoke(null, signal);
      }
      catch(Exception | LinkageError e)
      {
         LOG.info("Could not raise a assistant budget alert through the Alerts SPI", logPair("type", type), logPair("reason", e.getClass().getSimpleName()));
      }
   }



}
