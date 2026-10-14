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

package com.kingsrook.qbits.assistant.tools.bulkload;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.uiapi.UiApiCall;
import com.kingsrook.qbits.assistant.uiapi.UiApiGateway;
import com.kingsrook.qbits.assistant.uiapi.UiApiResult;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.json.JSONArray;
import org.json.JSONObject;


/*******************************************************************************
 ** A stand-in for the server's bulk-load UI API operations (core's
 ** /bulkLoad/... and the profile processes), recording every call. Its
 ** validate answers like core's dry run: the blog_post status field is
 ** required, so a profile that neither maps nor defaults it has errors.
 *******************************************************************************/
public class FakeBulkLoadServer implements UiApiGateway
{
   public static final String SAMPLE_SENTINEL = "SAMPLE_SENTINEL_5b1e";

   private static final Set<String> OPERATIONS = Set.of(
      "GET /bulkLoad/uploads/{uploadRef}/profile", "POST /bulkLoad/{tableName}/suggestMapping", "POST /bulkLoad/{tableName}/validate",
      "POST /table/{tableName}/query", "GET /table/{tableName}/{primaryKey}", "POST /processes/{processName}/init");

   private final List<UiApiCall> calls = new ArrayList<>();
   private final boolean         offersBulkLoad;



   /*******************************************************************************
    **
    *******************************************************************************/
   public FakeBulkLoadServer(boolean offersBulkLoad)
   {
      this.offersBulkLoad = offersBulkLoad;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public boolean isInvocable(String method, String pathTemplate)
   {
      String operation = method + " " + pathTemplate;
      return (OPERATIONS.contains(operation) && (offersBulkLoad || !pathTemplate.startsWith("/bulkLoad")));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public synchronized UiApiResult invoke(UiApiCall call)
   {
      calls.add(call);
      String operation = call.operation();
      return (switch(operation)
      {
         case "GET /bulkLoad/uploads/{uploadRef}/profile" -> "up_1".equals(call.getPathParams().get("uploadRef"))
            ? ok(new JSONObject().put("uploadRef", "up_1").put("fileName", "posts.csv").put("hasHeaderRow", true).put("rowsProfiled", 2)
            .put("columns", new JSONArray()
               .put(new JSONObject().put("columnIndex", 0).put("header", "Post Title").put("mappedFieldName", "title").put("samplesAllowed", true)
                  .put("sampleValues", new JSONArray().put(SAMPLE_SENTINEL)))
               .put(new JSONObject().put("columnIndex", 1).put("header", "Teaser").put("samplesAllowed", false))))
            : new UiApiResult(404, "{\"error\":\"Upload not found.\"}");
         case "POST /bulkLoad/{tableName}/suggestMapping" -> ok(new JSONObject()
            .put("profile", new JSONObject().put("version", "v1").put("layout", "FLAT").put("hasHeaderRow", true)
               .put("fieldList", new JSONArray().put(new JSONObject().put("fieldName", "title").put("columnIndex", 0).put("headerName", "Post Title"))))
            .put("unmappedColumns", new JSONArray().put(new JSONObject().put("columnIndex", 1).put("header", "Teaser")))
            .put("unmappedRequiredFields", new JSONArray().put(new JSONObject().put("fieldName", "status").put("label", "Status"))));
         case "POST /bulkLoad/{tableName}/validate" -> validate(call);
         case "POST /table/{tableName}/query" -> ok(new JSONObject().put("records", new JSONArray()
            .put(new JSONObject().put("values", new JSONObject().put("id", 3).put("label", "Posts from the CMS export").put("userId", "admin-1")
               .put("mappingJson", "{\"version\":\"v1\",\"fieldList\":[{\"fieldName\":\"title\",\"columnIndex\":0}]}")))));
         case "GET /table/{tableName}/{primaryKey}" -> ok(new JSONObject().put("record", new JSONObject().put("values", new JSONObject().put("id", 3)
            .put("label", "Posts from the CMS export").put("tableName", "blog_post").put("userId", "admin-1")
            .put("mappingJson", "{\"version\":\"v1\",\"fieldList\":[{\"fieldName\":\"title\",\"columnIndex\":0}]}"))));
         case "POST /processes/{processName}/init" -> "storeSavedBulkLoadProfile".equals(call.getPathParams().get("processName"))
            ? ok(new JSONObject().put("type", "COMPLETE").put("values", new JSONObject().put("savedBulkLoadProfileList",
            new JSONArray().put(new JSONObject().put("values", new JSONObject().put("id", 7).put("label", "x"))))))
            : ok(new JSONObject().put("type", "COMPLETE").put("values", new JSONObject()));
         default -> new UiApiResult(404, "{\"error\":\"no such operation\"}");
      });
   }



   /*******************************************************************************
    ** Errors unless status is mapped or defaulted.
    *******************************************************************************/
   private UiApiResult validate(UiApiCall call)
   {
      JSONObject body       = new JSONObject(JsonUtils.toJson(call.getJsonBody()));
      JSONArray  fieldList  = body.getJSONObject("profile").getJSONArray("fieldList");
      boolean    hasStatus  = false;
      for(int i = 0; i < fieldList.length(); i++)
      {
         JSONObject field = fieldList.getJSONObject(i);
         if("status".equals(field.optString("fieldName")) && (field.has("columnIndex") || !field.isNull("defaultValue") && field.has("defaultValue")))
         {
            hasStatus = true;
         }
      }
      JSONObject result = new JSONObject().put("mode", body.optString("mode", "SAMPLE")).put("processName", "blog_post.bulkInsert").put("recordCount", 2);
      if(hasStatus)
      {
         return (ok(result.put("okCount", 2).put("errorCount", 0).put("errors", new JSONArray())));
      }
      return (ok(result.put("okCount", 0).put("errorCount", 2).put("errors", new JSONArray()
         .put(new JSONObject().put("rowNumbers", "Rows 2-3").put("fieldName", "status").put("errorCode", "REQUIRED").put("message", "Missing value in required field: Status")))));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static UiApiResult ok(JSONObject json)
   {
      return (new UiApiResult(200, json.toString()));
   }



   /*******************************************************************************
    ** The calls, in order.
    *******************************************************************************/
   public synchronized List<UiApiCall> getCalls()
   {
      return (new ArrayList<>(calls));
   }



   /*******************************************************************************
    ** Calls of one operation.
    *******************************************************************************/
   public synchronized List<UiApiCall> calls(String operation)
   {
      return (calls.stream().filter(c -> c.operation().equals(operation)).toList());
   }



   /*******************************************************************************
    ** A form value of a process call, as JSON.
    *******************************************************************************/
   public static Map<String, Object> formValues(UiApiCall call)
   {
      return (new JSONObject(call.getFormParams().get("values")).toMap());
   }
}
