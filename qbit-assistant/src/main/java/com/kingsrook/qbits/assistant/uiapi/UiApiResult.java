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

package com.kingsrook.qbits.assistant.uiapi;


import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** A `/qqq/v1` response exactly as the UI would see it: status and body.
 *******************************************************************************/
public record UiApiResult(int status, String body)
{
   /*******************************************************************************
    ** Whether the status is 2xx.
    *******************************************************************************/
   public boolean isSuccess()
   {
      return (status >= 200 && status < 300);
   }



   /*******************************************************************************
    ** The body as a JSON object, or null when it is not one.
    *******************************************************************************/
   public Map<String, Object> json()
   {
      if(!StringUtils.hasContent(body))
      {
         return (null);
      }
      try
      {
         return (JsonUtils.toObject(body, new TypeReference<Map<String, Object>>() {}));
      }
      catch(Exception e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    ** The API's own error message ({"error": "..."}), or null.
    *******************************************************************************/
   public String errorMessage()
   {
      Map<String, Object> json = json();
      return (json != null && json.get("error") instanceof String error && StringUtils.hasContent(error) ? error : null);
   }

}
