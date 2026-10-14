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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;


/*******************************************************************************
 ** Per-user starts per minute, in memory per replica. The API implements
 ** concurrency by superseding turns, never by refusing another question.
 *******************************************************************************/
public class RateLimiter
{
   private final Map<String, Deque<Instant>> starts  = new HashMap<>();
   private final Clock                       clock;



   /*******************************************************************************
    **
    *******************************************************************************/
   public RateLimiter(Clock clock)
   {
      this.clock = clock;
   }



   /*******************************************************************************
    ** Try to start a turn: null when allowed (and counted), else the reason.
    *******************************************************************************/
   public synchronized String tryAcquire(String userId, int perMinute, int concurrent)
   {
      Instant         now   = clock.instant();
      Deque<Instant>  queue = starts.computeIfAbsent(userId, k -> new ArrayDeque<>());
      while(!queue.isEmpty() && queue.peekFirst().isBefore(now.minusSeconds(60)))
      {
         queue.pollFirst();
      }

      if(queue.size() >= perMinute)
      {
         return ("Too many assistant requests in the last minute. Try again shortly.");
      }

      queue.addLast(now);
      return (null);
   }



   /*******************************************************************************
    ** A turn finished.
    *******************************************************************************/
   public synchronized void release(String userId)
   {
      // Only starts per minute are counted; completion does not refund a start.
   }

}
