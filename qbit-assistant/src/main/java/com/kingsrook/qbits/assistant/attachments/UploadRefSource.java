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

package com.kingsrook.qbits.assistant.attachments;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import com.kingsrook.qqq.backend.core.actions.tables.StorageAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.jobs.DurableJobsMetaData;
import com.kingsrook.qqq.backend.core.jobs.DurableJobsSecurity;
import com.kingsrook.qqq.backend.core.jobs.uploads.UploadRefs;
import com.kingsrook.qqq.backend.core.model.actions.tables.storage.StorageInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Reads an upload made through core POST /qqq/v1/uploads. UploadRefs.get
 ** runs under the signed-in user's session and enforces the expiry; this
 ** class then also requires that the upload is the user's OWN (core lets a
 ** jobs administrator open any upload; the assistant never does). The bytes
 ** are read with StorageAction into memory, with a hard cap.
 *******************************************************************************/
public class UploadRefSource implements AttachmentSource
{
   static final String NOT_FOUND = "the file was not found or has expired. Attach it again.";



   /*******************************************************************************
    ** Whether uploads are configured in this application (durable jobs with an
    ** uploads storage table).
    *******************************************************************************/
   public static boolean configured()
   {
      DurableJobsMetaData settings = DurableJobsMetaData.of(QContext.getQInstance());
      return (settings != null && settings.getEffectiveUploadsStorageTableName() != null);
   }



   /*******************************************************************************
    ** Read the user's own upload, at most maxBytes.
    *******************************************************************************/
   @Override
   public Upload read(String uploadRef, long maxBytes) throws AttachmentException
   {
      QRecord row;
      try
      {
         row = UploadRefs.get(uploadRef);
      }
      catch(QNotFoundException e)
      {
         throw (new AttachmentException(NOT_FOUND, e));
      }
      catch(QException e)
      {
         throw (new AttachmentException("uploads are not available right now.", e));
      }

      String me = DurableJobsSecurity.userId(QContext.getQSession());
      if(me == null || !Objects.equals(me, row.getValueString("userId")))
      {
         throw (new AttachmentException(NOT_FOUND));
      }

      Long declared = row.getValueLong("sizeBytes");
      if(declared != null && declared > maxBytes)
      {
         throw (new AttachmentException(tooLarge(maxBytes)));
      }

      StorageInput storageInput = new StorageInput(row.getValueString("storageTableName")).withReference(row.getValueString("storageReference"));
      try(InputStream stream = new StorageAction().getInputStream(storageInput))
      {
         return (new Upload(row.getValueString("fileName"), readCapped(stream, maxBytes)));
      }
      catch(QNotFoundException e)
      {
         throw (new AttachmentException(NOT_FOUND, e));
      }
      catch(QException | IOException e)
      {
         throw (new AttachmentException("the file could not be read from storage.", e));
      }
   }



   /*******************************************************************************
    ** At most maxBytes from a stream; throws when there is more.
    *******************************************************************************/
   static byte[] readCapped(InputStream stream, long maxBytes) throws IOException, AttachmentException
   {
      ByteArrayOutputStream out    = new ByteArrayOutputStream();
      byte[]                buffer = new byte[64 * 1024];
      long                  total  = 0;
      int                   read;
      while((read = stream.read(buffer)) != -1)
      {
         total += read;
         if(total > maxBytes)
         {
            throw (new AttachmentException(tooLarge(maxBytes)));
         }
         out.write(buffer, 0, read);
      }
      return (out.toByteArray());
   }



   /*******************************************************************************
    ** The user-facing size message.
    *******************************************************************************/
   static String tooLarge(long maxBytes)
   {
      return ("the file is larger than " + AttachmentService.megabytes(maxBytes) + ", the most the assistant reads.");
   }

}
