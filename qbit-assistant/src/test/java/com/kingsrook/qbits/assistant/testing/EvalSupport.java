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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qbits.assistant.provider.ModelMessage;
import com.kingsrook.qbits.assistant.provider.ModelPart;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelResponse;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** Helpers for scripted "faithful model" steps: read the tool results the
 ** engine sent back (inside their qqq_data envelope) and answer only from
 ** them - the behaviour the system prompt demands of a real model.
 *******************************************************************************/
public class EvalSupport
{

   /*******************************************************************************
    ** The tool_result parts of the last message of a request.
    *******************************************************************************/
   public static List<ModelPart> lastToolResults(ModelRequest request)
   {
      List<ModelMessage> messages = request.getMessages();
      List<ModelPart>    results  = new ArrayList<>();
      if(!messages.isEmpty())
      {
         for(ModelPart part : messages.get(messages.size() - 1).getParts())
         {
            if(ModelPart.Type.TOOL_RESULT.equals(part.getType()))
            {
               results.add(part);
            }
         }
      }
      return (results);
   }



   /*******************************************************************************
    ** The JSON inside a qqq_data envelope.
    *******************************************************************************/
   public static Map<String, Object> unwrap(String envelope)
   {
      if(envelope == null || !envelope.startsWith("<qqq_data") || !envelope.endsWith("</qqq_data>"))
      {
         throw (new IllegalStateException("not an enveloped tool result: " + envelope));
      }
      String json = envelope.substring(envelope.indexOf('>') + 1, envelope.length() - "</qqq_data>".length());
      try
      {
         return (JsonUtils.toObject(json, new TypeReference<Map<String, Object>>() {}));
      }
      catch(Exception e)
      {
         throw (new IllegalStateException("tool result is not JSON: " + json, e));
      }
   }



   /*******************************************************************************
    ** The first tool result of the last message, unwrapped.
    *******************************************************************************/
   public static Map<String, Object> firstResult(ModelRequest request)
   {
      return (unwrap(lastToolResults(request).get(0).getText()));
   }



   /*******************************************************************************
    ** A step that answers with text computed from the request.
    *******************************************************************************/
   public static Function<ModelRequest, ModelResponse> answer(Function<ModelRequest, String> text)
   {
      return (request -> new ModelResponse().withParts(new ArrayList<>(List.of(ModelPart.text(text.apply(request))))).withStopReason(ModelResponse.STOP_END_TURN));
   }

}
