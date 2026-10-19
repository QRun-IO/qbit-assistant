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


import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import com.kingsrook.qbits.assistant.spi.StreamHandle;


/*******************************************************************************
 ** A turn running on this replica: its event log and its cancel state.
 *******************************************************************************/
public class RunningTurn
{
   private final Integer                       turnId;
   private final String                        userId;
   private final TurnEventLog                  log          = new TurnEventLog();
   private final AtomicReference<String>       cancelReason = new AtomicReference<>();
   private final AtomicBoolean finalized = new AtomicBoolean();
   private Instant startedAt;
   private volatile String phase = "starting";
   private volatile String toolName;
   private volatile Thread engineThread;
   private volatile Consumer<String> forceFinalizer;
   private volatile long cancelledNanos;
   private volatile StreamHandle               currentHandle;



   /*******************************************************************************
    **
    *******************************************************************************/
   public RunningTurn(Integer turnId, String userId)
   {
      this.startedAt = Instant.now();
      this.turnId = turnId;
      this.userId = userId;
   }



   /*******************************************************************************
    ** Ask the turn to stop: aborts the model stream; pending tools do not run.
    *******************************************************************************/
   public void cancel(String reason)
   {
      if(!finalized.get() && cancelReason.compareAndSet(null, reason))
      {
         cancelledNanos = System.nanoTime();
         try
         {
            StreamHandle handle = currentHandle;
            if(handle != null)
            {
               handle.cancel();
            }
         }
         finally
         {
            Thread thread = engineThread;
            if(thread != null && thread != Thread.currentThread())
            {
               thread.interrupt();
            }
         }
      }
   }



   /*******************************************************************************
    ** Whether cancel was requested.
    *******************************************************************************/
   public boolean isCancelled()
   {
      return (cancelReason.get() != null);
   }



   /*******************************************************************************
    ** Why it was cancelled (USER, KILL_SWITCH, ...), or null.
    *******************************************************************************/
   public String getCancelReason()
   {
      return (cancelReason.get());
   }



   /*******************************************************************************
    ** The model stream in flight (cancelled at once when the turn is).
    *******************************************************************************/
   public void setCurrentHandle(StreamHandle handle)
   {
      this.currentHandle = handle;
      if(handle != null && isCancelled())
      {
         handle.cancel();
      }
   }



   /*******************************************************************************
    ** Getter for turnId
    *******************************************************************************/
   public Integer getTurnId()
   {
      return (turnId);
   }



   /*******************************************************************************
    ** Getter for userId
    *******************************************************************************/
   public String getUserId()
   {
      return (userId);
   }



   /*******************************************************************************
    ** Getter for log
    *******************************************************************************/
   public TurnEventLog getLog()
   {
      return (log);
   }




   /*******************************************************************************
    ** Claim terminal processing exactly once.
    *******************************************************************************/
   public boolean tryFinalize()
   {
      return (finalized.compareAndSet(false, true));
   }



   /*******************************************************************************
    ** Whether terminal processing has been claimed.
    *******************************************************************************/
   public boolean isFinalized()
   {
      return (finalized.get());
   }



   /*******************************************************************************
    ** Getter for startedAt
    *******************************************************************************/
   public Instant getStartedAt()
   {
      return (startedAt);
   }



   /*******************************************************************************
    ** Setter for startedAt
    *******************************************************************************/
   public void setStartedAt(Instant startedAt)
   {
      this.startedAt = startedAt;
   }



   /*******************************************************************************
    ** Getter for phase
    *******************************************************************************/
   public String getPhase()
   {
      return (phase);
   }



   /*******************************************************************************
    ** Getter for toolName
    *******************************************************************************/
   public String getToolName()
   {
      return (toolName);
   }



   /*******************************************************************************
    ** Record the current phase and optional tool.
    *******************************************************************************/
   public void setPhase(String phase, String toolName)
   {
      this.phase = phase;
      this.toolName = toolName;
   }



   /*******************************************************************************
    ** Record the thread to interrupt when cancelling.
    *******************************************************************************/
   public void setEngineThread(Thread engineThread)
   {
      this.engineThread = engineThread;
   }



   /*******************************************************************************
    ** Install the engine finalizer before registration.
    *******************************************************************************/
   public void setForceFinalizer(Consumer<String> forceFinalizer)
   {
      this.forceFinalizer = forceFinalizer;
   }



   /*******************************************************************************
    ** Force terminal processing using the original user session.
    *******************************************************************************/
   public void forceFinalize(String reason)
   {
      Consumer<String> finalizer = forceFinalizer;
      if(finalizer != null)
      {
         finalizer.accept(reason);
      }
   }



   /*******************************************************************************
    ** Whether cancellation has exceeded its bounded grace period.
    *******************************************************************************/
   public boolean cancelGraceElapsed(int seconds)
   {
      return (isCancelled() && cancelledNanos != 0 && System.nanoTime() - cancelledNanos >= seconds * 1_000_000_000L);
   }

}
