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


import java.util.concurrent.atomic.AtomicReference;
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
   private volatile StreamHandle               currentHandle;



   /*******************************************************************************
    **
    *******************************************************************************/
   public RunningTurn(Integer turnId, String userId)
   {
      this.turnId = turnId;
      this.userId = userId;
   }



   /*******************************************************************************
    ** Ask the turn to stop: aborts the model stream; pending tools do not run.
    *******************************************************************************/
   public void cancel(String reason)
   {
      if(cancelReason.compareAndSet(null, reason))
      {
         StreamHandle handle = currentHandle;
         if(handle != null)
         {
            handle.cancel();
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

}
