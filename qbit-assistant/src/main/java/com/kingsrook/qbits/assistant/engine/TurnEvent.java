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


import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** One SSE event of a turn (contract section 3): a sequence number, a type
 ** and a JSON object that always includes "seq".
 *******************************************************************************/
public class TurnEvent
{
   public static final String MESSAGE_START = "message.start";
   public static final String TEXT_DELTA    = "text.delta";
   public static final String TOOL_START    = "tool.start";
   public static final String TOOL_RESULT   = "tool.result";
   public static final String UI_NAVIGATE   = "ui.navigate";
   public static final String NOTICE        = "notice";
   public static final String USAGE         = "usage";
   public static final String MESSAGE_END   = "message.end";
   public static final String ERROR         = "error";
   public static final String PING          = "ping";

   private final int                 seq;
   private final String              type;
   private final Map<String, Object> data;



   /*******************************************************************************
    ** The data map is copied and "seq" is put into it.
    *******************************************************************************/
   public TurnEvent(int seq, String type, Map<String, Object> data)
   {
      this.seq = seq;
      this.type = type;
      this.data = new LinkedHashMap<>();
      this.data.put("seq", seq);
      if(data != null)
      {
         data.forEach((k, v) ->
         {
            if(!"seq".equals(k))
            {
               this.data.put(k, v);
            }
         });
      }
   }



   /*******************************************************************************
    ** An event read back from JSON.
    *******************************************************************************/
   public static TurnEvent fromJson(int seq, String type, String json)
   {
      Map<String, Object> data = Map.of();
      try
      {
         if(json != null)
         {
            data = JsonUtils.toObject(json, new TypeReference<Map<String, Object>>() {});
         }
      }
      catch(Exception e)
      {
         ////////////////////////////////////////////
         // unreadable stored event: replay it empty //
         ////////////////////////////////////////////
      }
      return (new TurnEvent(seq, type, data));
   }



   /*******************************************************************************
    ** The SSE frame: id, event, data, blank line.
    *******************************************************************************/
   public String toSseFrame()
   {
      return ("id: " + seq + "\nevent: " + type + "\ndata: " + toJson() + "\n\n");
   }



   /*******************************************************************************
    ** The data as JSON.
    *******************************************************************************/
   public String toJson()
   {
      return (JsonUtils.toJson(data));
   }



   /*******************************************************************************
    ** Whether this event ends the stream.
    *******************************************************************************/
   public boolean isTerminal()
   {
      return (MESSAGE_END.equals(type));
   }



   /*******************************************************************************
    ** Getter for seq
    *******************************************************************************/
   public int getSeq()
   {
      return (seq);
   }



   /*******************************************************************************
    ** Getter for type
    *******************************************************************************/
   public String getType()
   {
      return (type);
   }



   /*******************************************************************************
    ** Getter for data
    *******************************************************************************/
   public Map<String, Object> getData()
   {
      return (data);
   }

}
