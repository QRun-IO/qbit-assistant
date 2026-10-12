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


/*******************************************************************************
 ** An API error with its HTTP status and a message safe to show the user.
 ** code is one of the SSE error codes (KILL_SWITCH, BUDGET, RATE_LIMIT, ...)
 ** or the /config reasons, when one applies.
 *******************************************************************************/
public class AssistantApiException extends Exception
{
   private final int    status;
   private final String code;



   /*******************************************************************************
    **
    *******************************************************************************/
   public AssistantApiException(int status, String message)
   {
      this(status, null, message);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public AssistantApiException(int status, String code, String message)
   {
      super(message);
      this.status = status;
      this.code = code;
   }



   /*******************************************************************************
    ** Getter for status
    *******************************************************************************/
   public int getStatus()
   {
      return (status);
   }



   /*******************************************************************************
    ** Getter for code
    *******************************************************************************/
   public String getCode()
   {
      return (code);
   }

}
