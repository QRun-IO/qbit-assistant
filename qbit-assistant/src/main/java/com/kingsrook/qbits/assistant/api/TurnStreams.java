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


import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.engine.TurnEventLog;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import io.javalin.http.Context;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Writes a turn's events as server-sent events: the live log of a turn
 ** running here, or the persisted events (polling while the turn runs on
 ** another replica), with a ping every pingMillis while idle.
 *******************************************************************************/
public final class TurnStreams
{
   private static final QLogger LOG = QLogger.getLogger(TurnStreams.class);

   public static final long PING_MILLIS    = 15_000;
   public static final long REMOTE_POLL_MS = 1_000;

   private static volatile long pingMillis = PING_MILLIS;



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private TurnStreams()
   {
   }



   /*******************************************************************************
    ** The ping interval (tests use a short one).
    *******************************************************************************/
   public static void setPingMillis(long millis)
   {
      pingMillis = millis;
   }



   /*******************************************************************************
    ** Stream a live log from a sequence number until message.end.
    *******************************************************************************/
   public static void streamLive(Context context, TurnEventLog log, int after)
   {
      try
      {
         writeLive(startStream(context), log, after);
      }
      catch(IOException e)
      {
         LOG.debug("Assistant stream closed by the client (the turn continues)");
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
   }



   /*******************************************************************************
    ** Stream the events of a turn after a sequence number: live when it runs
    ** here, else the persisted ones (polling while it runs elsewhere). The
    ** caller's QContext (session) must be set: the replay reads the store.
    *******************************************************************************/
   public static void streamEvents(Context context, AssistantApi api, Integer turnId, int after, AssistantApi.TurnEvents first)
   {
      if(first.live() != null)
      {
         streamLive(context, first.live().getLog(), after);
         return;
      }
      try
      {
         OutputStream out       = startStream(context);
         int          last      = after;
         long         lastWrite = System.currentTimeMillis();
         long         giveUpAt  = System.currentTimeMillis() + (api.maxTurnSeconds() + 60L) * 1000L;
         AssistantApi.TurnEvents current = first;
         while(true)
         {
            boolean ended = false;
            for(TurnEvent event : current.persisted())
            {
               write(out, event.toSseFrame());
               last = event.getSeq();
               lastWrite = System.currentTimeMillis();
               ended |= event.isTerminal();
            }
            if(ended || (current.finished() && !current.more()) || System.currentTimeMillis() > giveUpAt)
            {
               return;
            }
            if(!current.more())
            {
               Thread.sleep(REMOTE_POLL_MS);
            }
            if(System.currentTimeMillis() - lastWrite >= pingMillis)
            {
               write(out, ping());
               lastWrite = System.currentTimeMillis();
            }
            current = api.turnEvents(turnId, last);
            if(current.live() != null)
            {
               writeLive(out, current.live().getLog(), last);
               return;
            }
         }
      }
      catch(IOException e)
      {
         LOG.debug("Assistant event stream closed by the client", logPair("turnId", turnId));
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
      catch(Exception e)
      {
         LOG.warn("Assistant event replay failed", e, logPair("turnId", turnId));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void writeLive(OutputStream out, TurnEventLog log, int after) throws IOException, InterruptedException
   {
      int last = after;
      while(true)
      {
         List<TurnEvent> events = log.after(last, pingMillis);
         if(events.isEmpty())
         {
            if(log.isClosed())
            {
               return;
            }
            write(out, ping());
            continue;
         }
         for(TurnEvent event : events)
         {
            write(out, event.toSseFrame());
            last = event.getSeq();
            if(event.isTerminal())
            {
               return;
            }
         }
      }
   }



   /*******************************************************************************
    ** Start an SSE response.
    *******************************************************************************/
   private static OutputStream startStream(Context context) throws IOException
   {
      context.status(200);
      context.disableCompression();
      context.res().setContentType("text/event-stream; charset=utf-8");
      context.res().setHeader("Cache-Control", "no-cache");
      context.res().setHeader("X-Accel-Buffering", "no");
      context.res().setHeader("Connection", "keep-alive");
      OutputStream out = context.res().getOutputStream();
      out.flush();
      return (out);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void write(OutputStream out, String frame) throws IOException
   {
      out.write(frame.getBytes(StandardCharsets.UTF_8));
      out.flush();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String ping()
   {
      return ("event: " + TurnEvent.PING + "\ndata: {}\n\n");
   }

}
