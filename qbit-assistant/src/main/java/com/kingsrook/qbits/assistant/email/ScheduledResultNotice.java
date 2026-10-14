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

package com.kingsrook.qbits.assistant.email;


import java.time.Instant;
import com.kingsrook.qbits.assistant.classification.DataClassification;


/*******************************************************************************
 ** What the scheduled-task runner knows about one finished run, for the
 ** result email. The full model output is deliberately not part of it: only a
 ** short summary, and only when the result is classified PUBLIC.
 **
 ** @param runTable      the table holding the run record (the email's linked record)
 ** @param runId         the run's id (also the idempotency key)
 ** @param taskName      the scheduled task's name
 ** @param runTime       when the run finished
 ** @param summary       a short summary (cut to 280 characters; never the model output)
 ** @param classification the result's classification; null means PUBLIC
 ** @param ownerUserId   the task owner's user id (the way the qbit identifies users)
 ** @param emailResult   whether the task opted in to email delivery
 ** @param link          absolute link to the run record in the app
 *******************************************************************************/
public record ScheduledResultNotice(String runTable, Object runId, String taskName, Instant runTime, String summary, DataClassification classification,
                                    String ownerUserId, boolean emailResult, String link)
{
}
