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


/*******************************************************************************
 ** Where attachment bytes come from: by default the core upload refs, read
 ** as the signed-in user (UploadRefSource). Implementations read into memory
 ** with the given cap and never write to local disk.
 *******************************************************************************/
public interface AttachmentSource
{

   /*******************************************************************************
    ** An upload's (sanitized) file name and bytes.
    *******************************************************************************/
   record Upload(String fileName, byte[] bytes)
   {
   }



   /*******************************************************************************
    ** Read the upload behind a ref as the current user, at most maxBytes.
    ** Throws with a user-facing reason when it is missing, someone else's,
    ** expired or too large.
    *******************************************************************************/
   Upload read(String uploadRef, long maxBytes) throws AttachmentException;

}
