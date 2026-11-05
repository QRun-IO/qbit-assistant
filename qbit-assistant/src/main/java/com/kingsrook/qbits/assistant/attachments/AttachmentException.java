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
 ** An attachment that cannot be read. The message is shown to the user after
 ** the file name ("LORE.txt could not be read: ..."), so it never contains
 ** file content.
 *******************************************************************************/
public class AttachmentException extends Exception
{

   /*******************************************************************************
    ** A user-facing reason.
    *******************************************************************************/
   public AttachmentException(String message)
   {
      super(message);
   }



   /*******************************************************************************
    ** A user-facing reason with the underlying cause (never logged with content).
    *******************************************************************************/
   public AttachmentException(String message, Throwable cause)
   {
      super(message, cause);
   }

}
