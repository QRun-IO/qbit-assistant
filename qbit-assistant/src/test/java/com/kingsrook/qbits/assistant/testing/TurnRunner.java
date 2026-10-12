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

package com.kingsrook.qbits.assistant.testing;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.engine.RunningTurn;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** Runs turns through the assistant API (no HTTP) as a given user and
 ** collects everything a test asserts on: the streamed events, the final
 ** answer text, the tool results and the audited tool calls.
 *******************************************************************************/
public class TurnRunner
{
   private final QInstance           qInstance;
   private final AssistantQBitConfig config;



   /*******************************************************************************
    ** The outcome of one turn.
    *******************************************************************************/
   public record Outcome(Integer conversationId, Integer turnId, List<TurnEvent> events, List<AssistantToolCall> toolCalls)
   {
      /*******************************************************************************
       ** The answer text (all text.delta events).
       *******************************************************************************/
      public String text()
      {
         return (events.stream().filter(e -> TurnEvent.TEXT_DELTA.equals(e.getType())).map(e -> String.valueOf(e.getData().get("text"))).collect(Collectors.joining()));
      }



      /*******************************************************************************
       ** Events of a type.
       *******************************************************************************/
      public List<Map<String, Object>> events(String type)
      {
         return (events.stream().filter(e -> type.equals(e.getType())).map(TurnEvent::getData).collect(Collectors.toList()));
      }



      /*******************************************************************************
       ** The message.end data.
       *******************************************************************************/
      public Map<String, Object> end()
      {
         List<Map<String, Object>> ends = events(TurnEvent.MESSAGE_END);
         return (ends.isEmpty() ? Map.of() : ends.get(ends.size() - 1));
      }



      /*******************************************************************************
       ** The error code, or null.
       *******************************************************************************/
      public String errorCode()
      {
         List<Map<String, Object>> errors = events(TurnEvent.ERROR);
         return (errors.isEmpty() ? null : String.valueOf(errors.get(0).get("code")));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public TurnRunner(QInstance qInstance, AssistantQBitConfig config)
   {
      this.qInstance = qInstance;
      this.config = config;
   }



   /*******************************************************************************
    ** The runtime.
    *******************************************************************************/
   public AssistantRuntime runtime()
   {
      return (config.getRuntime());
   }



   /*******************************************************************************
    ** Run one turn in a new conversation.
    *******************************************************************************/
   public Outcome run(String userId, String text) throws Exception
   {
      return (run(userId, null, text, null));
   }



   /*******************************************************************************
    ** Run one turn (conversationId null = a new conversation) and wait for it.
    *******************************************************************************/
   public Outcome run(String userId, Integer conversationId, String text, Map<String, Object> pageContext) throws Exception
   {
      RunningTurn running;
      Integer     convId = conversationId;
      QSession    session = AssistantTestFixture.sessionFor(userId);
      QContext.init(qInstance, session);
      try
      {
         AssistantApi api = new AssistantApi(runtime(), session);
         if(convId == null)
         {
            convId = (Integer) api.createConversation(new LinkedHashMap<>()).get("id");
         }
         Map<String, Object> body = new LinkedHashMap<>();
         body.put("text", text);
         if(pageContext != null)
         {
            body.put("pageContext", pageContext);
         }
         running = api.startTurn(convId, body);
      }
      finally
      {
         QContext.clear();
      }

      List<TurnEvent> events = new ArrayList<>();
      int             last   = 0;
      long            until  = System.currentTimeMillis() + 60_000;
      while(System.currentTimeMillis() < until)
      {
         List<TurnEvent> batch = running.getLog().after(last, 1000);
         events.addAll(batch);
         if(!batch.isEmpty())
         {
            last = batch.get(batch.size() - 1).getSeq();
         }
         if(batch.isEmpty() && running.getLog().isClosed())
         {
            break;
         }
      }

      ///////////////////////////////////////////////////////////////////////
      // the engine unregisters after closing the log; wait for that so a  //
      // following turn of the same user is not refused as concurrent      //
      ///////////////////////////////////////////////////////////////////////
      long wait = System.currentTimeMillis() + 5000;
      while(runtime().getRunningTurn(running.getTurnId()) != null && System.currentTimeMillis() < wait)
      {
         Thread.sleep(5);
      }

      return (new Outcome(convId, running.getTurnId(), events, toolCalls(userId, running.getTurnId())));
   }



   /*******************************************************************************
    ** The audited tool calls of a turn (read as the user).
    *******************************************************************************/
   public List<AssistantToolCall> toolCalls(String userId, Integer turnId) throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession(userId, config));
      try
      {
         return (new AssistantStore().query(AssistantToolCall.TABLE_NAME, new QQueryFilter()
            .withCriteria("turnId", QCriteriaOperator.EQUALS, turnId)
            .withOrderBy(new QFilterOrderBy("id", true)), AssistantToolCall.class));
      }
      finally
      {
         QContext.clear();
      }
   }

}
