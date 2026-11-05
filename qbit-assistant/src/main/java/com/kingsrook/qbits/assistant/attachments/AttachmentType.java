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


import java.util.List;


/*******************************************************************************
 ** The kinds of attached file the assistant reads, as sniffed from content.
 *******************************************************************************/
public enum AttachmentType
{
   TEXT("text", "text/plain"),
   JSON("JSON", "application/json"),
   CSV("CSV table", "text/csv"),
   TSV("TSV table", "text/tab-separated-values"),
   XLSX("Excel workbook", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
   PDF("PDF", "application/pdf"),
   PNG("PNG image", "image/png"),
   JPEG("JPEG image", "image/jpeg"),
   GIF("GIF image", "image/gif"),
   WEBP("WebP image", "image/webp");

   /*******************************************************************************
    ** What the file picker hint lists for documents and data files.
    *******************************************************************************/
   public static final List<String> DOCUMENT_TYPE_LABELS = List.of("Text (.txt, .md)", "JSON", "CSV", "TSV", "Excel (.xlsx)", "PDF");

   /*******************************************************************************
    ** What the file picker hint lists for images (vision models only).
    *******************************************************************************/
   public static final List<String> IMAGE_TYPE_LABELS = List.of("PNG", "JPEG", "GIF", "WebP");

   private final String label;
   private final String mediaType;



   /*******************************************************************************
    **
    *******************************************************************************/
   AttachmentType(String label, String mediaType)
   {
      this.label = label;
      this.mediaType = mediaType;
   }



   /*******************************************************************************
    ** A short human-readable name.
    *******************************************************************************/
   public String getLabel()
   {
      return (label);
   }



   /*******************************************************************************
    ** The media type.
    *******************************************************************************/
   public String getMediaType()
   {
      return (mediaType);
   }



   /*******************************************************************************
    ** Whether the file is an image (sent as an image part to vision models).
    *******************************************************************************/
   public boolean isImage()
   {
      return (this == PNG || this == JPEG || this == GIF || this == WEBP);
   }

}
