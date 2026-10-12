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


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/*******************************************************************************
 ** The in-memory event stream of a running turn. The engine appends; any
 ** number of readers (the POST stream, /events?after= resumes) follow it
 ** from a sequence number. Closed after message.end.
 *******************************************************************************/
public class TurnEventLog
{
   private final List<TurnEvent> events = new ArrayList<>();
   private final ReentrantLock   lock   = new ReentrantLock();
   private final Condition       added  = lock.newCondition();
   private boolean               closed = false;



   /*******************************************************************************
    ** Append an event with the next sequence number.
    *******************************************************************************/
   public TurnEvent append(String type, Map<String, Object> data)
   {
      lock.lock();
      try
      {
         TurnEvent event = new TurnEvent(events.size() + 1, type, data);
         events.add(event);
         if(event.isTerminal())
         {
            closed = true;
         }
         added.signalAll();
         return (event);
      }
      finally
      {
         lock.unlock();
      }
   }



   /*******************************************************************************
    ** Close without a terminal event (e.g. the engine crashed).
    *******************************************************************************/
   public void close()
   {
      lock.lock();
      try
      {
         closed = true;
         added.signalAll();
      }
      finally
      {
         lock.unlock();
      }
   }



   /*******************************************************************************
    ** The events after a sequence number, waiting up to timeoutMillis for at
    ** least one when there are none yet. Empty when the wait timed out or the
    ** log is closed with nothing new.
    *******************************************************************************/
   public List<TurnEvent> after(int afterSeq, long timeoutMillis) throws InterruptedException
   {
      lock.lock();
      try
      {
         long nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
         while(events.size() <= afterSeq && !closed && nanos > 0)
         {
            nanos = added.awaitNanos(nanos);
         }
         int from = Math.max(0, afterSeq);
         return (from >= events.size() ? List.of() : new ArrayList<>(events.subList(from, events.size())));
      }
      finally
      {
         lock.unlock();
      }
   }



   /*******************************************************************************
    ** Whether the log is closed.
    *******************************************************************************/
   public boolean isClosed()
   {
      lock.lock();
      try
      {
         return (closed);
      }
      finally
      {
         lock.unlock();
      }
   }



   /*******************************************************************************
    ** Number of events so far.
    *******************************************************************************/
   public int size()
   {
      lock.lock();
      try
      {
         return (events.size());
      }
      finally
      {
         lock.unlock();
      }
   }

}
