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

package com.kingsrook.qbits.assistant.api;


import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.engine.TurnEventLog;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Terminal events must be drained even when closure races an empty poll.
 *******************************************************************************/
class TurnStreamsTest
{
   /*******************************************************************************
    ** Reproduce the poll/close boundary without relying on thread timing.
    *******************************************************************************/
   @Test
   void drainsTerminalEventsAfterEmptyPoll() throws Exception
   {
      TurnEventLog log = new TurnEventLog()
      {
         /*******************************************************************************
          ** Append just after a timed-out poll, before the reader checks closure.
          *******************************************************************************/
         @Override
         public List<TurnEvent> after(int afterSeq, long timeoutMillis) throws InterruptedException
         {
            if(size() == 0)
            {
               append(TurnEvent.ERROR, Map.of("code", "TIMEOUT"));
               append(TurnEvent.MESSAGE_END, Map.of("status", "ERROR"));
               return (List.of());
            }
            return (super.after(afterSeq, timeoutMillis));
         }
      };
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      TurnStreams.writeLive(output, log, 0);
      assertThat(output.toString(StandardCharsets.UTF_8)).contains("event: error", "TIMEOUT", "event: message.end");
   }
}
