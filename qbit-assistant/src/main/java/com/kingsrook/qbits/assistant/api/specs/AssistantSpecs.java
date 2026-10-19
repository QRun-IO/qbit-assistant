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

package com.kingsrook.qbits.assistant.api.specs;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.api.AssistantApiException;
import com.kingsrook.qbits.assistant.api.TurnStreams;
import com.kingsrook.qbits.assistant.engine.RunningTurn;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.utils.ExceptionUtils;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.middleware.javalin.QJavalinUtils;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.middleware.javalin.specs.BasicResponse;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.AbstractJsonEndpointSpec;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtension;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;
import com.kingsrook.qqq.middleware.javalin.specs.v1.responses.BasicErrorResponseV1;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.In;
import com.kingsrook.qqq.openapi.model.Parameter;
import com.kingsrook.qqq.openapi.model.Response;
import com.kingsrook.qqq.openapi.model.Schema;
import com.kingsrook.qqq.openapi.model.Type;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;


/*******************************************************************************
 ** The assistant's own endpoints as spec'd /qqq/v1 operations (UI API
 ** extension "assistant", established prefix /assistant; QRun-IO/qqq#1128,
 ** docs/UI-API-EXTENSIONS.md). They replace the raw AssistantRouteProvider
 ** routes, with the same URLs and bodies, so they appear in the served
 ** /qqq/v1/openapi.yaml and a generated client covers them.
 **
 ** Errors use BasicErrorResponseV1 and retain assistant-specific codes.
 ** Turns and events are text/event-stream; each event type is documented as
 ** AssistantTurnEventV1.
 *******************************************************************************/
public final class AssistantSpecs
{
   public static final String EXTENSION_NAME    = "assistant";
   public static final String EXTENSION_VERSION = "1.5.0";
   public static final String PREFIX            = "/assistant";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AssistantSpecs()
   {
   }



   /*******************************************************************************
    ** The extension to register in MiddlewareSpecExtensionsMetaData.
    *******************************************************************************/
   public static MiddlewareSpecExtension extension()
   {
      return (new MiddlewareSpecExtension()
         .withName(EXTENSION_NAME)
         .withSemanticVersion(EXTENSION_VERSION)
         .withPathPrefix(PREFIX)
         .withDescription("QQQ Assistant: conversations, streamed turns, feedback, pending actions and data access explanations")
         .withSpec(ConfigSpec.class)
         .withSpec(ListConversationsSpec.class)
         .withSpec(CreateConversationSpec.class)
         .withSpec(GetConversationSpec.class)
         .withSpec(UpdateConversationSpec.class)
         .withSpec(DeleteConversationSpec.class)
         .withSpec(StartTurnSpec.class)
         .withSpec(TurnEventsSpec.class)
         .withSpec(CancelTurnSpec.class)
         .withSpec(ActiveTurnsSpec.class)
         .withSpec(FeedbackSpec.class)
         .withSpec(GetActionSpec.class)
         .withSpec(ConfirmActionSpec.class)
         .withSpec(RejectActionSpec.class)
         .withSpec(AccessSpec.class)
         .withSpec(ClientErrorsSpec.class));
   }



   ////////////////////////////////////////////////////////////////////////////
   // schemas                                                                //
   ////////////////////////////////////////////////////////////////////////////

   /*******************************************************************************
    **
    *******************************************************************************/
   static Schema object(String description, Object... namesAndSchemas)
   {
      Map<String, Schema> properties = new LinkedHashMap<>();
      for(int i = 0; i + 1 < namesAndSchemas.length; i += 2)
      {
         properties.put((String) namesAndSchemas[i], (Schema) namesAndSchemas[i + 1]);
      }
      return (new Schema().withType(Type.OBJECT).withDescription(description).withProperties(properties));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Schema string(String description)
   {
      return (new Schema().withType(Type.STRING).withDescription(description));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Schema integer(String description)
   {
      return (new Schema().withType(Type.INTEGER).withDescription(description));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Schema bool(String description)
   {
      return (new Schema().withType(Type.BOOLEAN).withDescription(description));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Schema array(Schema items, String description)
   {
      return (new Schema().withType(Type.ARRAY).withItems(items).withDescription(description));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Schema ref(String component)
   {
      return (new Schema().withRefToSchema(component));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Schema freeObject(String description)
   {
      return (new Schema().withType(Type.OBJECT).withDescription(description).withAdditionalProperties(true));
   }



   /*******************************************************************************
    ** Every component schema of the extension (each spec publishes all of them;
    ** the OpenAPI model merges equal components).
    *******************************************************************************/
   static Map<String, Schema> components()
   {
      Map<String, Schema> rs = new LinkedHashMap<>();
      rs.put("AssistantProfileV1", object("How the assistant presents itself (admin-configured; text only, it never changes access).",
         "displayName", string("The assistant's name."), "icon", string("A Material icon name."), "greeting", string("Shown on a new conversation.").withNullable(true),
         "starterPrompts", array(string(null), "Up to three suggested first questions in total: permitted configured questions first, then generated questions. Table, row and field access are checked on every request.")));
      rs.put("AssistantConfigResponseV1", object("Whether the assistant is available to this user, and how.",
         "enabled", bool("Whether the panel may be used."),
         "reason", string("Why not: DISABLED, NO_PERMISSION, NOT_CONFIGURED or KILL_SWITCH.").withNullable(true),
         "shortcut", string("Keyboard shortcut, e.g. mod+j."),
         "model", string("The model that answers."),
         "limits", freeObject("maxTurnSeconds and maxToolCallsPerTurn."),
         "features", freeObject("writes: some table allows writes; pendingActions: writes wait for confirmation."),
         "profile", ref("AssistantProfileV1")));
      rs.put("AssistantConversationV1", object("One of the user's own conversations.",
         "id", integer(null), "title", string(null).withNullable(true), "status", string("ACTIVE or ARCHIVED."), "pinned", bool(null),
         "lastMessageAt", string("ISO-8601 instant.").withNullable(true), "unreadCount", integer(null), "createDate", string("ISO-8601 instant.").withNullable(true)));
      rs.put("AssistantConversationListResponseV1", object("The user's conversations, newest first.", "conversations", array(ref("AssistantConversationV1"), null)));
      rs.put("AssistantMessageV1", object("A message: the user's text or the assistant's reply as parts (text, tool, record_refs, navigate, notice).",
         "id", integer(null), "role", string("USER or ASSISTANT."), "status", string(null), "turnId", integer(null).withNullable(true),
         "createDate", string(null).withNullable(true), "parts", array(freeObject("A message part."), null)));
      rs.put("AssistantRecordsRenderV1", object("A tool part's render of type records (query results). columns and rows are always present - empty when the query matched nothing (since 1.4.0; older stored parts are completed when read).",
         "type", string("records"), "tableName", string(null), "tableLabel", string(null),
         "columns", array(object("A column.", "name", string(null), "label", string(null)), "Always present; empty when nothing matched."),
         "rows", array(object("A row.", "id", string("The primary key."), "href", string("The record's /app link."), "values", freeObject("Display values by column name.")), "Always present; empty when nothing matched."),
         "totalCount", integer("All matching rows.").withNullable(true), "truncated", bool("More rows match than are shown."), "openHref", string("The Query screen link.")));
      rs.put("AssistantConversationDetailResponseV1", object("A conversation and a page of its messages, oldest first.",
         "conversation", ref("AssistantConversationV1"), "messages", array(ref("AssistantMessageV1"), null)));
      rs.put("AssistantStatusResponseV1", object("An acknowledgement.", "status", string(null)));
      rs.put("AssistantClientErrorReportV1", object("An error the client hit while showing the assistant (since 1.5.0). scope and message are required; "
            + "longer values are truncated, message and stacks are scrubbed of e-mail addresses, long numbers, tokens and query strings, and ids "
            + "are kept only when they are the user's own.",
         "scope", string("Required. Where it happened: assistant-panel, assistant-message, assistant-part, assistant-render, assistant-stream, widget, record-section, page...").withMaxLength(64),
         "message", string("Required. The error message.").withMaxLength(500),
         "errorName", string("The error's name, e.g. TypeError.").withMaxLength(100).withNullable(true),
         "stack", string("The JavaScript stack.").withMaxLength(4000).withNullable(true),
         "componentStack", string("The React component stack.").withMaxLength(4000).withNullable(true),
         "requestId", string("The request or trace id the client saw.").withMaxLength(128).withNullable(true),
         "route", string("The page path (no host, query or fragment).").withMaxLength(300).withNullable(true),
         "conversationId", integer(null).withNullable(true), "messageId", integer(null).withNullable(true), "turnId", integer(null).withNullable(true),
         "partType", string("The message part type being shown.").withMaxLength(64).withNullable(true),
         "renderType", string("The render type being shown.").withMaxLength(64).withNullable(true),
         "appVersion", string("The client build.").withMaxLength(64).withNullable(true),
         "occurredAt", string("ISO-8601 instant.").withFormat("date-time").withNullable(true)));
      rs.put("AssistantClientErrorResponseV1", object("Whether the report was stored: false when the user's reports are over the per-minute limit (still 202).",
         "accepted", bool(null)));
      rs.put("AssistantActionV1", object("A write the assistant proposed and its state.",
         "id", integer(null), "status", string("PENDING, RUNNING, EXECUTED, FAILED, REJECTED or EXPIRED."), "toolName", string(null), "tableName", string(null),
         "recordId", string(null).withNullable(true), "summary", string(null), "httpStatus", integer("The UI API status of the write.").withNullable(true),
         "resultSummary", string(null).withNullable(true), "expiresAt", string(null).withNullable(true),
         "outcome", string("After a confirm: the tool outcome.").withNullable(true), "message", string("After a confirm: what happened.").withNullable(true),
         "href", string("After a confirm: the written record's /app link.").withNullable(true)));
      rs.put("AssistantAccessResponseV1", object("Explain access: per table, the user's UI permissions, the assistant's data access, and each tool's verdict.",
         "tables", array(freeObject("table, label, dataAccess, dataAccessSource, ui {visible, read, insert, edit, delete}, operations [{tool, operation, allowed, reason}]."), null)));
      rs.put("AssistantActiveTurnsResponseV1", object("The caller's active runs, newest first; stale leases are reaped.",
         "turns", array(object("An active assistant run.", "turnId", integer(null), "conversationId", integer(null),
            "assistantMessageId", integer(null), "userMessageId", integer(null), "status", string("RUNNING or CANCELLING."),
            "phase", string(null), "startedAt", string("ISO-8601."), "heartbeatAt", string("ISO-8601."),
            "elapsedMs", integer(null), "live", bool("Running on this replica."), "concurrencyMode", string("SUPERSEDE or CONCURRENT.")), null)));
      rs.put("AssistantTurnEventV1", object("One server-sent event of a turn (event: <type>, id: <seq>, data: this object). Types: message.start, turn.phase, text.delta, tool.start, tool.result, ui.navigate, notice, usage, message.end, error, ping.",
         "seq", integer("Sequence number; resume with ?after=<seq>."), "type", string("The event type (also the SSE event name)."),
         "data", freeObject("The event's fields, by type. message.start includes startedAt (ISO-8601). turn.phase includes phase (thinking, tool, writing), optional toolName and label.")));
      rs.put("AssistantErrorV1", object("Deprecated since 1.3.0: errors use BasicErrorResponseV1.", "error", string("A message for the user."), "code", string("BUDGET, RATE_LIMIT, NOT_CONFIGURED, KILL_SWITCH, DISABLED, NO_PERMISSION...").withNullable(true)));
      return (rs);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Parameter pathParam(String name, String description)
   {
      return (new Parameter().withName(name).withDescription(description).withRequired(true).withSchema(new Schema().withType(Type.INTEGER)).withIn(In.PATH));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Parameter queryParam(String name, Type type, String description)
   {
      return (new Parameter().withName(name).withDescription(description).withRequired(false).withSchema(new Schema().withType(type)).withIn(In.QUERY));
   }



   ////////////////////////////////////////////////////////////////////////////
   // base                                                                   //
   ////////////////////////////////////////////////////////////////////////////

   /*******************************************************************************
    ** Base of the assistant's JSON specs: the user's own AssistantApi (with the
    ** request's credentials, forwarded to the UI API by the tools), errors in
    ** the standard error shape with their status and assistant-specific code.
    *******************************************************************************/
   public abstract static class AssistantJsonSpec extends AbstractJsonEndpointSpec
   {
      private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();



      /*******************************************************************************
       ** The operation: method and path under /assistant.
       *******************************************************************************/
      protected abstract HttpMethod method();

      /*******************************************************************************
       ** The path, e.g. /assistant/conversations/{id}.
       *******************************************************************************/
      protected abstract String path();

      /*******************************************************************************
       ** A one-line summary.
       *******************************************************************************/
      protected abstract String summary();

      /*******************************************************************************
       ** The work, with the user's AssistantApi.
       *******************************************************************************/
      protected abstract Object handle(AssistantApi api, JsonInput input) throws Exception;



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public BasicOperation defineBasicOperation()
      {
         return (new BasicOperation().withPath(path()).withHttpMethod(method()).withShortSummary(summary()).withLongDescription(summary()));
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public Map<String, Schema> defineComponentSchemas()
      {
         return (components());
      }



      /*******************************************************************************
       ** The success status (200 unless an operation says otherwise).
       *******************************************************************************/
      protected HttpStatus successStatus()
      {
         return (HttpStatus.OK);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public BasicResponse defineBasicSuccessResponse()
      {
         HttpStatus status = successStatus();
         return (status == HttpStatus.NO_CONTENT
            ? new BasicResponse(ContentType.APPLICATION_JSON.getMimeType(), status, "Done (no body)", null, null)
            : new BasicResponse(ContentType.APPLICATION_JSON.getMimeType(), status, "Successful Response", getResponseSchemaName(), null));
      }



      /*******************************************************************************
       ** A bodyless response has empty content. Core 1.6's schema traversal
       ** requires a non-null map even when the response has no schema.
       *******************************************************************************/
      @Override
      public Map<Integer, Response> defineResponses()
      {
         Map<Integer, Response> responses = super.defineResponses();
         for(Response response : responses.values())
         {
            if(response.getContent() == null)
            {
               response.withContent(Map.of());
            }
         }
         return (responses);
      }



      /*******************************************************************************
       ** Keep the request (for its credentials and the server's local port).
       *******************************************************************************/
      @Override
      public JsonInput buildInput(Context context) throws Exception
      {
         CONTEXT.set(context);
         return (super.buildInput(context));
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      protected Object handle(JsonInput input) throws Exception
      {
         AssistantRuntime runtime = AssistantRuntime.of(QContext.getQInstance());
         if(runtime == null)
         {
            throw (new AssistantApiException(404, "The assistant is not installed."));
         }
         Context context = CONTEXT.get();
         if(context != null)
         {
            try
            {
               runtime.learnLocalPort(context.req().getLocalPort());
            }
            catch(RuntimeException e)
            {
               ////////////////////////////////////
               // not a servlet request (tests)  //
               ////////////////////////////////////
            }
         }
         AssistantApi api = new AssistantApi(runtime, QContext.getQSession()).withCredentials(context == null ? null : UiApiCredentials.fromContext(context));
         return (handle(api, input));
      }



      /*******************************************************************************
       ** JSON with nulls kept (as before).
       *******************************************************************************/
      @Override
      public void handleOutput(Context context, JsonOutput output) throws Exception
      {
         try
         {
            context.status(successStatus().getCode());
            if(successStatus() != HttpStatus.NO_CONTENT)
            {
               context.contentType(ContentType.APPLICATION_JSON);
               context.result(JsonUtils.toJsonCustomized(output.getResult(), builder -> builder.serializationInclusion(JsonInclude.Include.ALWAYS)));
            }
         }
         finally
         {
            CONTEXT.remove();
         }
      }



      /*******************************************************************************
       ** Assistant errors keep their status and code in the standard shape; others go
       ** through QQQ's mapping (400 / 401 / 403 / 404 / 500).
       *******************************************************************************/
      @Override
      protected void handleException(Context context, Exception e)
      {
         CONTEXT.remove();
         AssistantApiException apiException = ExceptionUtils.findClassInRootChain(e, AssistantApiException.class);
         if(apiException != null)
         {
            context.contentType(ContentType.APPLICATION_JSON);
            QJavalinUtils.respondWithError(context, org.eclipse.jetty.http.HttpStatus.getCode(apiException.getStatus()), apiException.getMessage());
            if(apiException.getCode() != null)
            {
               context.result(JsonUtils.toJson(new BasicErrorResponseV1()
                  .withError(apiException.getMessage())
                  .withCode(apiException.getCode())
                  .withTraceId(QJavalinUtils.getTraceId(context))
                  .toMap()));
            }
            return;
         }
         QJavalinUtils.handleException(null, context, e);
      }



      /*******************************************************************************
       ** The error statuses every assistant operation may answer.
       *******************************************************************************/
      @Override
      public List<BasicResponse> defineAdditionalBasicResponses()
      {
         return (List.of(
            new BasicResponse(HttpStatus.BAD_REQUEST, "Invalid input", BasicErrorResponseV1.class.getSimpleName()),
            new BasicResponse(HttpStatus.FORBIDDEN, "No access to the assistant", BasicErrorResponseV1.class.getSimpleName()),
            new BasicResponse(HttpStatus.NOT_FOUND, "Not found (or not the user's own)", BasicErrorResponseV1.class.getSimpleName()),
            new BasicResponse(HttpStatus.CONFLICT, "Already finished or decided", BasicErrorResponseV1.class.getSimpleName()),
            new BasicResponse(HttpStatus.TOO_MANY_REQUESTS, "Budget or rate limit", BasicErrorResponseV1.class.getSimpleName()),
            new BasicResponse(HttpStatus.SERVICE_UNAVAILABLE, "Disabled, not configured, or the kill switch is on", BasicErrorResponseV1.class.getSimpleName())));
      }



      /*******************************************************************************
       ** An integer path parameter, or 400.
       *******************************************************************************/
      protected static Integer pathInt(JsonInput input, String name) throws QBadRequestException
      {
         try
         {
            return (Integer.parseInt(input.getPathParam(name)));
         }
         catch(NumberFormatException | NullPointerException e)
         {
            throw (new QBadRequestException(name + " must be an integer."));
         }
      }
   }



   ////////////////////////////////////////////////////////////////////////////
   // the operations                                                         //
   ////////////////////////////////////////////////////////////////////////////

   /*******************************************************************************
    ** GET /assistant/config
    *******************************************************************************/
   public static class ConfigSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.GET);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/config");
      }



      @Override
      protected String summary()
      {
         return ("Whether the assistant is available to this user, its persona and limits");
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantConfigResponseV1");
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.config());
      }
   }



   /*******************************************************************************
    ** GET /assistant/conversations
    *******************************************************************************/
   public static class ListConversationsSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.GET);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/conversations");
      }



      @Override
      protected String summary()
      {
         return ("The user's own conversations");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(queryParam("status", Type.STRING, "ARCHIVED for archived conversations; active ones by default.")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantConversationListResponseV1");
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.listConversations(input.getQueryParam("status")));
      }
   }



   /*******************************************************************************
    ** GET /assistant/turns/active
    *******************************************************************************/
   public static class ActiveTurnsSpec extends AssistantJsonSpec
   {
      /*******************************************************************************
       ** HTTP method.
       *******************************************************************************/
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.GET);
      }



      /*******************************************************************************
       ** Route path.
       *******************************************************************************/
      @Override
      protected String path()
      {
         return (PREFIX + "/turns/active");
      }



      /*******************************************************************************
       ** Operation summary.
       *******************************************************************************/
      @Override
      protected String summary()
      {
         return ("Find the user's active assistant runs");
      }



      /*******************************************************************************
       ** Response schema.
       *******************************************************************************/
      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantActiveTurnsResponseV1");
      }



      /*******************************************************************************
       ** Runs in the invoker's authenticated user session.
       *******************************************************************************/
      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.activeTurns());
      }
   }



   /*******************************************************************************
    ** POST /assistant/conversations
    *******************************************************************************/
   public static class CreateConversationSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.POST);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/conversations");
      }



      @Override
      protected String summary()
      {
         return ("Start a conversation");
      }



      @Override
      protected Schema defineRequestBodySchema()
      {
         return (object("Optional title and the page context it starts from.", "title", string(null), "context", freeObject("route, tableName, recordId, filter, selectedIds.")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantConversationV1");
      }



      @Override
      protected HttpStatus successStatus()
      {
         return (HttpStatus.CREATED);
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.createConversation(input.getBody()));
      }
   }



   /*******************************************************************************
    ** GET /assistant/conversations/{id}
    *******************************************************************************/
   public static class GetConversationSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.GET);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/conversations/{id}");
      }



      @Override
      protected String summary()
      {
         return ("A conversation and a page of its messages");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(pathParam("id", "The conversation id."), queryParam("before", Type.INTEGER, "Only messages older than this message id."),
            queryParam("limit", Type.INTEGER, "Page size (default 50, at most 200).")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantConversationDetailResponseV1");
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.getConversation(pathInt(input, "id"), input.getQueryParamInteger("before"), input.getQueryParamInteger("limit")));
      }
   }



   /*******************************************************************************
    ** PATCH /assistant/conversations/{id}
    *******************************************************************************/
   public static class UpdateConversationSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.PATCH);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/conversations/{id}");
      }



      @Override
      protected String summary()
      {
         return ("Rename, pin or archive a conversation");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(pathParam("id", "The conversation id.")));
      }



      @Override
      protected Schema defineRequestBodySchema()
      {
         return (object("The changed properties.", "title", string(null), "pinned", bool(null), "status", string("ACTIVE or ARCHIVED.")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantConversationV1");
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.patchConversation(pathInt(input, "id"), input.getBody()));
      }
   }



   /*******************************************************************************
    ** DELETE /assistant/conversations/{id}
    *******************************************************************************/
   public static class DeleteConversationSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.DELETE);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/conversations/{id}");
      }



      @Override
      protected String summary()
      {
         return ("Delete a conversation");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(pathParam("id", "The conversation id.")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantStatusResponseV1");
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         api.deleteConversation(pathInt(input, "id"));
         return (null);
      }



      @Override
      protected HttpStatus successStatus()
      {
         return (HttpStatus.NO_CONTENT);
      }
   }



   /*******************************************************************************
    ** POST /assistant/conversations/{id}/turns (text/event-stream)
    *******************************************************************************/
   public static class StartTurnSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.POST);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/conversations/{id}/turns");
      }



      @Override
      protected String summary()
      {
         return ("Ask: start a turn and stream the reply as server-sent events");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(pathParam("id", "The conversation id.")));
      }



      @Override
      protected Schema defineRequestBodySchema()
      {
         return (object("The user's text and the page context hint.", "text", string("The user's message."), "pageContext", freeObject("Where the user is in the app."), "concurrency", string("supersede or concurrent (case-insensitive); defaults to the host configuration.")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantTurnEventV1");
      }



      @Override
      public BasicResponse defineBasicSuccessResponse()
      {
         return (new BasicResponse("text/event-stream", HttpStatus.OK, "The turn's events, until message.end", "AssistantTurnEventV1", null));
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.startTurn(pathInt(input, "id"), input.getBody()));
      }



      /*******************************************************************************
       ** Stream the running turn's live log.
       *******************************************************************************/
      @Override
      public void handleOutput(Context context, JsonOutput output) throws Exception
      {
         try
         {
            TurnStreams.streamLive(context, ((RunningTurn) output.getResult()).getLog(), 0);
         }
         finally
         {
            AssistantJsonSpec.CONTEXT.remove();
         }
      }
   }



   /*******************************************************************************
    ** GET /assistant/turns/{turnId}/events?after= (text/event-stream)
    *******************************************************************************/
   public static class TurnEventsSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.GET);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/turns/{turnId}/events");
      }



      @Override
      protected String summary()
      {
         return ("Resume a turn's events after a sequence number (replay, then the live tail)");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(pathParam("turnId", "The turn id."), queryParam("after", Type.INTEGER, "The last sequence number already received (0 for all).")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantTurnEventV1");
      }



      @Override
      public BasicResponse defineBasicSuccessResponse()
      {
         return (new BasicResponse("text/event-stream", HttpStatus.OK, "The turn's events after `after`, until message.end", "AssistantTurnEventV1", null));
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      record Events(AssistantApi api, Integer turnId, int after, AssistantApi.TurnEvents first)
      {
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         Integer turnId = pathInt(input, "turnId");
         Integer after  = input.getQueryParamInteger("after");
         int     from   = after == null ? 0 : Math.max(0, after);
         return (new Events(api, turnId, from, api.turnEvents(turnId, from)));
      }



      /*******************************************************************************
       ** Stream (the QContext session is still set: the replay reads the store).
       *******************************************************************************/
      @Override
      public void handleOutput(Context context, JsonOutput output) throws Exception
      {
         try
         {
            Events events = (Events) output.getResult();
            TurnStreams.streamEvents(context, events.api(), events.turnId(), events.after(), events.first());
         }
         finally
         {
            AssistantJsonSpec.CONTEXT.remove();
         }
      }
   }



   /*******************************************************************************
    ** POST /assistant/turns/{turnId}/cancel
    *******************************************************************************/
   public static class CancelTurnSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.POST);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/turns/{turnId}/cancel");
      }



      @Override
      protected String summary()
      {
         return ("Stop a running turn");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(pathParam("turnId", "The turn id.")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantStatusResponseV1");
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.cancelTurn(pathInt(input, "turnId")));
      }



      /*******************************************************************************
       ** Older replicas can still return 202 during a rolling upgrade. New
       ** replicas return 200 for both active and already finished turns.
       *******************************************************************************/
      @Override
      public List<BasicResponse> defineAdditionalBasicResponses()
      {
         List<BasicResponse> responses = new java.util.ArrayList<>(super.defineAdditionalBasicResponses());
         responses.add(new BasicResponse(HttpStatus.ACCEPTED, "Legacy replica: cancellation accepted", "AssistantStatusResponseV1"));
         return (responses);
      }



      @Override
      protected HttpStatus successStatus()
      {
         return (HttpStatus.OK);
      }
   }



   /*******************************************************************************
    ** POST /assistant/messages/{id}/feedback
    *******************************************************************************/
   public static class FeedbackSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.POST);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/messages/{id}/feedback");
      }



      @Override
      protected String summary()
      {
         return ("Thumbs up or down on an assistant message");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(pathParam("id", "The assistant message id.")));
      }



      @Override
      protected Schema defineRequestBodySchema()
      {
         return (object("The rating and an optional reason or comment.", "rating", integer("1 or -1."), "reason", string(null), "comment", string(null)));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantStatusResponseV1");
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         api.feedback(pathInt(input, "id"), input.getBody());
         return (null);
      }



      @Override
      protected HttpStatus successStatus()
      {
         return (HttpStatus.NO_CONTENT);
      }
   }



   /*******************************************************************************
    ** GET /assistant/actions/{id}
    *******************************************************************************/
   public static class GetActionSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.GET);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/actions/{id}");
      }



      @Override
      protected String summary()
      {
         return ("A write the assistant proposed, and its state");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(pathParam("id", "The pending action id.")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantActionV1");
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.getAction(pathInt(input, "id")));
      }
   }



   /*******************************************************************************
    ** POST /assistant/actions/{id}/confirm
    *******************************************************************************/
   public static class ConfirmActionSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.POST);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/actions/{id}/confirm");
      }



      @Override
      protected String summary()
      {
         return ("Confirm a proposed write: it runs once, through the UI API, with this request's session");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(pathParam("id", "The pending action id.")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantActionV1");
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.confirmAction(pathInt(input, "id")));
      }
   }



   /*******************************************************************************
    ** POST /assistant/actions/{id}/reject
    *******************************************************************************/
   public static class RejectActionSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.POST);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/actions/{id}/reject");
      }



      @Override
      protected String summary()
      {
         return ("Cancel a proposed write; nothing changes");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(pathParam("id", "The pending action id.")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantActionV1");
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.rejectAction(pathInt(input, "id")));
      }
   }



   /*******************************************************************************
    ** POST /assistant/client-errors (since 1.5.0)
    *******************************************************************************/
   public static class ClientErrorsSpec extends AssistantJsonSpec
   {
      /*******************************************************************************
       ** HTTP method.
       *******************************************************************************/
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.POST);
      }



      /*******************************************************************************
       ** Route path.
       *******************************************************************************/
      @Override
      protected String path()
      {
         return (PREFIX + "/client-errors");
      }



      /*******************************************************************************
       ** Operation summary.
       *******************************************************************************/
      @Override
      protected String summary()
      {
         return ("Report an error the client hit while showing the assistant (stored for administrators, rate limited per user)");
      }



      /*******************************************************************************
       ** Request body.
       *******************************************************************************/
      @Override
      protected Schema defineRequestBodySchema()
      {
         return (ref("AssistantClientErrorReportV1"));
      }



      /*******************************************************************************
       ** Response schema.
       *******************************************************************************/
      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantClientErrorResponseV1");
      }



      /*******************************************************************************
       ** 202: accepted (or over the limit and dropped).
       *******************************************************************************/
      @Override
      protected HttpStatus successStatus()
      {
         return (HttpStatus.ACCEPTED);
      }



      /*******************************************************************************
       ** Runs in the invoker's authenticated user session.
       *******************************************************************************/
      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         return (api.reportClientError(input.getBody()));
      }
   }



   /*******************************************************************************
    ** GET /assistant/access
    *******************************************************************************/
   public static class AccessSpec extends AssistantJsonSpec
   {
      @Override
      protected HttpMethod method()
      {
         return (HttpMethod.GET);
      }



      @Override
      protected String path()
      {
         return (PREFIX + "/access");
      }



      @Override
      protected String summary()
      {
         return ("Explain access: which UI API operations the assistant could call for this user, and why not");
      }



      @Override
      public List<Parameter> defineRequestParameters()
      {
         return (List.of(queryParam("table", Type.STRING, "Only this table."), queryParam("prompt", Type.STRING, "Only the tables this text mentions."), queryParam("turnId", Type.INTEGER, "Attribute this request to an owned turn.")));
      }



      @Override
      protected String getResponseSchemaName()
      {
         return ("AssistantAccessResponseV1");
      }



      @Override
      protected Object handle(AssistantApi api, JsonInput input) throws Exception
      {
         String table = input.getQueryParam("table");
         return (api.access(StringUtils.hasContent(table) ? table : null, input.getQueryParam("prompt"), input.getQueryParamInteger("turnId")));
      }
   }

}
