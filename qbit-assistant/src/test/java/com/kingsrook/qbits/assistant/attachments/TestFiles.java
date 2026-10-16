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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;


/*******************************************************************************
 ** Small in-memory files of each type for the attachment tests.
 *******************************************************************************/
public final class TestFiles
{

   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private TestFiles()
   {
   }



   /*******************************************************************************
    ** UTF-8 bytes.
    *******************************************************************************/
   public static byte[] utf8(String text)
   {
      return (text.getBytes(StandardCharsets.UTF_8));
   }



   /*******************************************************************************
    ** A PDF with one line of text per page.
    *******************************************************************************/
   public static byte[] pdf(String... pages) throws Exception
   {
      return (pdfWith(null, null, pages));
   }



   /*******************************************************************************
    ** A PDF with an optional document-open JavaScript action and optional
    ** password protection (user password).
    *******************************************************************************/
   public static byte[] pdfWith(String javaScript, String userPassword, String... pages) throws Exception
   {
      PdfText.installFontMapper();
      try(PDDocument document = new PDDocument())
      {
         for(String text : pages)
         {
            PDPage page = new PDPage();
            document.addPage(page);
            try(PDPageContentStream content = new PDPageContentStream(document, page))
            {
               content.beginText();
               content.setFont(PDType1Font.HELVETICA, 12);
               content.newLineAtOffset(50, 700);
               content.showText(text);
               content.endText();
            }
         }
         if(javaScript != null)
         {
            document.getDocumentCatalog().setOpenAction(new PDActionJavaScript(javaScript));
         }
         if(userPassword != null)
         {
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", userPassword, new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
         }
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         document.save(out);
         return (out.toByteArray());
      }
   }



   /*******************************************************************************
    ** An XLSX workbook with the given rows on its first sheet.
    *******************************************************************************/
   public static byte[] xlsx(List<List<String>> rows) throws Exception
   {
      ByteArrayOutputStream out      = new ByteArrayOutputStream();
      Workbook              workbook = new Workbook(out, "test", "1.0");
      Worksheet             sheet    = workbook.newWorksheet("Sheet1");
      for(int r = 0; r < rows.size(); r++)
      {
         for(int c = 0; c < rows.get(r).size(); c++)
         {
            sheet.value(r, c, rows.get(r).get(c));
         }
      }
      workbook.finish();
      return (out.toByteArray());
   }



   /*******************************************************************************
    ** A ZIP with the named entries (each holding its name as content).
    *******************************************************************************/
   public static byte[] zip(String... names) throws Exception
   {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try(ZipOutputStream zip = new ZipOutputStream(out))
      {
         for(String name : names)
         {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(utf8(name));
            zip.closeEntry();
         }
      }
      return (out.toByteArray());
   }



   /*******************************************************************************
    ** A PNG header (signature + IHDR) of a size, padded.
    *******************************************************************************/
   public static byte[] png(int width, int height)
   {
      byte[] bytes = new byte[64];
      int[]  head  = { 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R' };
      for(int i = 0; i < head.length; i++)
      {
         bytes[i] = (byte) head[i];
      }
      putInt32be(bytes, 16, width);
      putInt32be(bytes, 20, height);
      return (bytes);
   }



   /*******************************************************************************
    ** A JPEG with an APP0 segment and a baseline SOF0 of a size.
    *******************************************************************************/
   public static byte[] jpeg(int width, int height)
   {
      byte[] bytes = new byte[64];
      int[] head = { 0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
         0xFF, 0xC0, 0x00, 0x11, 0x08 };
      for(int i = 0; i < head.length; i++)
      {
         bytes[i] = (byte) head[i];
      }
      bytes[25] = (byte) (height >> 8);
      bytes[26] = (byte) height;
      bytes[27] = (byte) (width >> 8);
      bytes[28] = (byte) width;
      return (bytes);
   }



   /*******************************************************************************
    ** A GIF89a header of a size.
    *******************************************************************************/
   public static byte[] gif(int width, int height)
   {
      byte[] bytes = new byte[32];
      byte[] head  = utf8("GIF89a");
      System.arraycopy(head, 0, bytes, 0, head.length);
      bytes[6] = (byte) width;
      bytes[7] = (byte) (width >> 8);
      bytes[8] = (byte) height;
      bytes[9] = (byte) (height >> 8);
      return (bytes);
   }



   /*******************************************************************************
    ** A WebP (VP8X) header of a size.
    *******************************************************************************/
   public static byte[] webp(int width, int height)
   {
      byte[] bytes = new byte[40];
      System.arraycopy(utf8("RIFF"), 0, bytes, 0, 4);
      System.arraycopy(utf8("WEBPVP8X"), 0, bytes, 8, 8);
      putInt24le(bytes, 24, width - 1);
      putInt24le(bytes, 27, height - 1);
      return (bytes);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void putInt32be(byte[] bytes, int offset, int value)
   {
      bytes[offset] = (byte) (value >> 24);
      bytes[offset + 1] = (byte) (value >> 16);
      bytes[offset + 2] = (byte) (value >> 8);
      bytes[offset + 3] = (byte) value;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void putInt24le(byte[] bytes, int offset, int value)
   {
      bytes[offset] = (byte) value;
      bytes[offset + 1] = (byte) (value >> 8);
      bytes[offset + 2] = (byte) (value >> 16);
   }

}
