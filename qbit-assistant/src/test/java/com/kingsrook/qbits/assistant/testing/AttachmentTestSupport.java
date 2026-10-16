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


import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.jobs.DurableJobsMetaData;
import com.kingsrook.qqq.backend.core.jobs.DurableJobsMetaDataProvider;
import com.kingsrook.qqq.backend.core.jobs.uploads.UploadRefs;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Core uploads on the memory backend for attachment tests: durable-jobs
 ** tables plus an uploads storage table, and uploads made as a given user
 ** through core UploadRefs (as POST /qqq/v1/uploads does).
 *******************************************************************************/
public final class AttachmentTestSupport
{
   public static final String STORAGE_TABLE = "upload_storage";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AttachmentTestSupport()
   {
   }



   /*******************************************************************************
    ** Turn on core uploads in a built fixture instance.
    *******************************************************************************/
   public static void enableUploads(QInstance qInstance) throws Exception
   {
      if(DurableJobsMetaData.of(qInstance) != null)
      {
         return;
      }
      new DurableJobsMetaDataProvider().defineAll(qInstance, AssistantTestFixture.BACKEND, null);
      qInstance.addTable(new QTableMetaData()
         .withName(STORAGE_TABLE)
         .withBackendName(AssistantTestFixture.BACKEND)
         .withPrimaryKeyField("reference")
         .withField(new QFieldMetaData("reference", QFieldType.STRING))
         .withField(new QFieldMetaData("contents", QFieldType.BLOB)));
      qInstance.withSupplementalMetaData(new DurableJobsMetaData().withUploadsStorageTableName(STORAGE_TABLE).withPodId("test-pod"));
      new QInstanceEnricher(qInstance).enrich();
   }



   /*******************************************************************************
    ** Upload bytes as a user; the ref.
    *******************************************************************************/
   public static String upload(QInstance qInstance, String userId, String fileName, byte[] bytes) throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(userId));
      try
      {
         return (UploadRefs.store(fileName, "application/octet-stream", new ByteArrayInputStream(bytes)).ref());
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** Upload UTF-8 text as a user; the ref.
    *******************************************************************************/
   public static String upload(QInstance qInstance, String userId, String fileName, String text) throws Exception
   {
      return (upload(qInstance, userId, fileName, text.getBytes(StandardCharsets.UTF_8)));
   }



   /*******************************************************************************
    ** A page context naming attachments: ref, fileName, ref, fileName, ...
    *******************************************************************************/
   public static Map<String, Object> attachments(String... refsAndNames)
   {
      List<Map<String, Object>> list = new ArrayList<>();
      for(int i = 0; i + 1 < refsAndNames.length; i += 2)
      {
         Map<String, Object> one = new LinkedHashMap<>();
         one.put("uploadRef", refsAndNames[i]);
         one.put("fileName", refsAndNames[i + 1]);
         list.add(one);
      }
      Map<String, Object> context = new LinkedHashMap<>();
      context.put("attachments", list);
      return (context);
   }

}
