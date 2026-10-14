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


import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;


/*******************************************************************************
 ** Budget signals through core's non-throwing Alerts API and default logging sink.
 *******************************************************************************/
public final class BudgetAlerts
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private BudgetAlerts()
   {
   }



   /*******************************************************************************
    ** Raise one deduplicated budget transition through the configured core sink.
    *******************************************************************************/
   public static void raise(String type, String dedupKey, AlertSeverity severity, String title, String summary)
   {
      QAlerts.raise(new AlertSignal(type).withDedupKey(dedupKey).withSeverity(severity).withTitle(title).withSummary(summary));
   }
}
