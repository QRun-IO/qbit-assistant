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

package com.kingsrook.qbits.assistant.provider;


import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;


/*******************************************************************************
 ** A StreamHandle backed by a CompletableFuture, for provider implementations:
 ** complete() or fail() it from the streaming thread; onCancel runs once when
 ** cancel() is called (e.g. to close the HTTP stream).
 *******************************************************************************/
public class CompletableStreamHandle implements StreamHandle
{
   private final CompletableFuture<ModelResponse> future    = new CompletableFuture<>();
   private final AtomicBoolean                    cancelled = new AtomicBoolean(false);
   private volatile Runnable                      onCancel;



   /*******************************************************************************
    ** Set what cancel() does (in addition to completing as cancelled).
    *******************************************************************************/
   public void setOnCancel(Runnable onCancel)
   {
      this.onCancel = onCancel;
   }



   /*******************************************************************************
    ** Finish with a response.
    *******************************************************************************/
   public void complete(ModelResponse response)
   {
      future.complete(response);
   }



   /*******************************************************************************
    ** Finish with an error.
    *******************************************************************************/
   public void fail(ModelProviderException exception)
   {
      future.completeExceptionally(exception);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void cancel()
   {
      if(cancelled.compareAndSet(false, true))
      {
         Runnable runnable = onCancel;
         if(runnable != null)
         {
            try
            {
               runnable.run();
            }
            catch(RuntimeException e)
            {
               ////////////////////////////////////////////////
               // closing an aborted stream may throw - fine //
               ////////////////////////////////////////////////
            }
         }
         future.complete(new ModelResponse().withStopReason(ModelResponse.STOP_CANCELLED));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public boolean isCancelled()
   {
      return (cancelled.get());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public ModelResponse await(Duration timeout) throws ModelProviderException, InterruptedException
   {
      try
      {
         return (future.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS));
      }
      catch(TimeoutException e)
      {
         cancel();
         throw (new ModelProviderException(ModelProviderException.Kind.TIMEOUT, null, "The model did not answer in time."));
      }
      catch(ExecutionException e)
      {
         if(e.getCause() instanceof ModelProviderException mpe)
         {
            throw (mpe);
         }
         throw (new ModelProviderException(ModelProviderException.Kind.OTHER, null, "The model call failed."));
      }
   }

}
