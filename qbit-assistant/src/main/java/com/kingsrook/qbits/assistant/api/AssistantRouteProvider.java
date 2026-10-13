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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.engine.RunningTurn;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.engine.TurnEventLog;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import com.kingsrook.qqq.middleware.javalin.QJavalinUtils;
import com.kingsrook.qqq.middleware.javalin.executors.ExecutorSessionUtils;
import io.javalin.config.JavalinConfig;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Javalin routes under /qqq/v1/assistant (contract section 3).
 **
 ** Every request gets the user's own session from the instance's
 ** authentication module (ExecutorSessionUtils.setupSession: session cookie,
 ** bearer token or API key) - never a system session. Bodies must be JSON
 ** (a cross-site form cannot send that without a CORS preflight).
 **
 ** Streams (POST /conversations/{id}/turns, GET /turns/{id}/events) are
 ** Server-Sent Events written to the response as they happen: "id: seq",
 ** "event: type", "data: json", a blank line; a ping every 15 seconds;
 ** Cache-Control no-cache and X-Accel-Buffering no (proxies must not
 ** buffer; give the route a long idle timeout). The turn itself runs on its
 ** own thread, so a client disconnect does not stop it; the client resumes
 ** with /events?after=<last seq>.
 **
 ** Request and response bodies are never logged.
 *******************************************************************************/
public class AssistantRouteProvider implements QJavalinRouteProviderInterface
{
   private static final QLogger LOG = QLogger.getLogger(AssistantRouteProvider.class);

   public static final String BASE_PATH       = "/qqq/v1/assistant";
   public static final long   PING_MILLIS     = 15_000;
   public static final long   REMOTE_POLL_MS  = 1_000;

   private QInstance qInstance;
   private long      pingMillis = PING_MILLIS;



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void setQInstance(QInstance qInstance)
   {
      this.qInstance = qInstance;
   }



   /*******************************************************************************
    ** Ping interval (tests use a short one).
    *******************************************************************************/
   public AssistantRouteProvider withPingMillis(long pingMillis)
   {
      this.pingMillis = pingMillis;
      return (this);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void acceptJavalinConfig(JavalinConfig config)
   {
      config.routes.get(BASE_PATH + "/config", ctx -> json(ctx, false, (api, body) -> api.config()));
      config.routes.get(BASE_PATH + "/conversations", ctx -> json(ctx, false, (api, body) -> api.listConversations(ctx.queryParam("status"))));
      config.routes.post(BASE_PATH + "/conversations", ctx -> json(ctx, true, (api, body) ->
      {
         ctx.status(201);
         return (api.createConversation(body));
      }));
      config.routes.get(BASE_PATH + "/conversations/{id}", ctx -> json(ctx, false, (api, body) -> api.getConversation(pathInt(ctx, "id"), queryInt(ctx, "before"), queryInt(ctx, "limit"))));
      config.routes.patch(BASE_PATH + "/conversations/{id}", ctx -> json(ctx, true, (api, body) -> api.patchConversation(pathInt(ctx, "id"), body)));
      config.routes.delete(BASE_PATH + "/conversations/{id}", ctx -> json(ctx, false, (api, body) ->
      {
         api.deleteConversation(pathInt(ctx, "id"));
         ctx.status(204);
         return (null);
      }));
      config.routes.post(BASE_PATH + "/conversations/{id}/turns", this::startTurn);
      config.routes.get(BASE_PATH + "/turns/{turnId}/events", this::turnEvents);
      config.routes.post(BASE_PATH + "/turns/{turnId}/cancel", ctx -> json(ctx, true, (api, body) ->
      {
         ctx.status(202);
         return (api.cancelTurn(pathInt(ctx, "turnId")));
      }));
      config.routes.get(BASE_PATH + "/access", ctx -> json(ctx, false, (api, body) -> api.access(ctx.queryParam("table"), ctx.queryParam("prompt"))));
      config.routes.get(BASE_PATH + "/actions/{id}", ctx -> json(ctx, false, (api, body) -> api.getAction(pathInt(ctx, "id"))));
      config.routes.post(BASE_PATH + "/actions/{id}/confirm", ctx -> json(ctx, true, (api, body) -> api.confirmAction(pathInt(ctx, "id"))));
      config.routes.post(BASE_PATH + "/actions/{id}/reject", ctx -> json(ctx, true, (api, body) -> api.rejectAction(pathInt(ctx, "id"))));
      config.routes.post(BASE_PATH + "/messages/{id}/feedback", ctx -> json(ctx, true, (api, body) ->
      {
         api.feedback(pathInt(ctx, "id"), body);
         ctx.status(204);
         return (null);
      }));
   }



   /*******************************************************************************
    ** Session, runtime, body, handler, JSON - or the error.
    *******************************************************************************/
   private void json(Context context, boolean readsBody, Handler handler)
   {
      try
      {
         AssistantApi        api    = api(context);
         Map<String, Object> body   = readsBody ? readJsonBody(context) : Map.of();
         Object              result = handler.handle(api, body);
         if(result != null)
         {
            context.contentType(ContentType.APPLICATION_JSON);
            context.result(JsonUtils.toJsonCustomized(result, builder -> builder.serializationInclusion(JsonInclude.Include.ALWAYS)));
         }
      }
      catch(Exception e)
      {
         handleException(context, e);
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** POST /conversations/{id}/turns
    *******************************************************************************/
   private void startTurn(Context context)
   {
      RunningTurn turn;
      try
      {
         AssistantApi api = api(context);
         turn = api.startTurn(pathInt(context, "id"), readJsonBody(context));
      }
      catch(Exception e)
      {
         handleException(context, e);
         QContext.clear();
         return;
      }
      QContext.clear();
      streamLive(context, turn.getLog(), 0);
   }



   /*******************************************************************************
    ** GET /turns/{turnId}/events?after=
    *******************************************************************************/
   private void turnEvents(Context context)
   {
      Integer      turnId;
      int          after;
      AssistantApi api;
      AssistantApi.TurnEvents events;
      try
      {
         api = api(context);
         turnId = pathInt(context, "turnId");
         Integer afterParam = queryInt(context, "after");
         after = afterParam == null ? 0 : Math.max(0, afterParam);
         events = api.turnEvents(turnId, after);
      }
      catch(Exception e)
      {
         handleException(context, e);
         QContext.clear();
         return;
      }

      if(events.live() != null)
      {
         QContext.clear();
         streamLive(context, events.live().getLog(), after);
         return;
      }

      ///////////////////////////////////////////////////////////////////////
      // persisted events; if the turn still runs on another replica, poll //
      ///////////////////////////////////////////////////////////////////////
      try
      {
         OutputStream out       = startStream(context);
         int          last      = after;
         long         lastWrite = System.currentTimeMillis();
         long         giveUpAt  = System.currentTimeMillis() + (api.maxTurnSeconds() + 60L) * 1000L;
         AssistantApi.TurnEvents current = events;
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
               QContext.clear();
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
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** Stream a live log from a sequence number until message.end.
    *******************************************************************************/
   private void streamLive(Context context, TurnEventLog log, int after)
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
    **
    *******************************************************************************/
   private void writeLive(OutputStream out, TurnEventLog log, int after) throws IOException, InterruptedException
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



   /*******************************************************************************
    ** Session + API for a request. 404 when the QBit is not in the instance.
    *******************************************************************************/
   private AssistantApi api(Context context) throws Exception
   {
      AssistantRuntime runtime = AssistantRuntime.of(qInstance);
      if(runtime == null)
      {
         throw (new AssistantApiException(404, "The assistant is not installed."));
      }
      ////////////////////////////////////////////////////////////////////
      // the tools call /qqq/v1 on this server: remember its local port //
      ////////////////////////////////////////////////////////////////////
      try
      {
         runtime.learnLocalPort(context.req().getLocalPort());
      }
      catch(RuntimeException e)
      {
         LOG.debug("Could not read the local port", e);
      }
      QSession session = ExecutorSessionUtils.setupSession(context, qInstance);
      return (new AssistantApi(runtime, session));
   }



   /*******************************************************************************
    ** Map errors: API errors with their status, others through QQQ's handler
    ** (401 / 403 / 404 / 500).
    *******************************************************************************/
   private static void handleException(Context context, Exception e)
   {
      if(e instanceof AssistantApiException apiException)
      {
         Map<String, Object> body = new LinkedHashMap<>();
         body.put("error", apiException.getMessage());
         if(apiException.getCode() != null)
         {
            body.put("code", apiException.getCode());
         }
         context.status(apiException.getStatus());
         context.contentType(ContentType.APPLICATION_JSON);
         context.result(JsonUtils.toJson(body));
         return;
      }
      if(!(e instanceof QException))
      {
         LOG.warn("Assistant request failed", e);
      }
      QJavalinUtils.handleException(null, context, e);
   }



   /*******************************************************************************
    ** A JSON object body (empty body = empty object).
    *******************************************************************************/
   static Map<String, Object> readJsonBody(Context context) throws AssistantApiException
   {
      String body = context.body();
      if(!StringUtils.hasContent(body))
      {
         return (new LinkedHashMap<>());
      }

      String contentType = context.contentType();
      if(contentType == null || !contentType.toLowerCase().startsWith("application/json"))
      {
         throw (new AssistantApiException(400, "Request bodies must be JSON (Content-Type: application/json)."));
      }

      try
      {
         Map<String, Object> map = JsonUtils.toObject(body, new TypeReference<Map<String, Object>>() {}, mapper -> mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
         return (map == null ? new LinkedHashMap<>() : map);
      }
      catch(Exception e)
      {
         throw (new AssistantApiException(400, "The request body is not a JSON object."));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Integer pathInt(Context context, String name) throws AssistantApiException
   {
      try
      {
         return (Integer.parseInt(context.pathParam(name)));
      }
      catch(NumberFormatException e)
      {
         throw (new AssistantApiException(400, name + " must be an integer."));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Integer queryInt(Context context, String name) throws AssistantApiException
   {
      String value = context.queryParam(name);
      if(!StringUtils.hasContent(value))
      {
         return (null);
      }
      try
      {
         return (Integer.parseInt(value.trim()));
      }
      catch(NumberFormatException e)
      {
         throw (new AssistantApiException(400, name + " must be an integer."));
      }
   }



   /*******************************************************************************
    ** One JSON endpoint's work.
    *******************************************************************************/
   @FunctionalInterface
   private interface Handler
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      Object handle(AssistantApi api, Map<String, Object> body) throws Exception;
   }

}
