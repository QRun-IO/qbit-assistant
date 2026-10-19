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
    ** A real PNG generated by the JDK codec.
    *******************************************************************************/
   public static byte[] png(int width, int height)
   {
      return (image("png", width, height));
   }



   /*******************************************************************************
    ** A real JPEG generated by the JDK codec.
    *******************************************************************************/
   public static byte[] jpeg(int width, int height)
   {
      return (image("jpeg", width, height));
   }



   /*******************************************************************************
    ** A real GIF generated by the JDK codec.
    *******************************************************************************/
   public static byte[] gif(int width, int height)
   {
      return (image("gif", width, height));
   }



   /*******************************************************************************
    ** A real 10 by 20 WebP fixture, encoded with libwebp.
    *******************************************************************************/
   public static byte[] webp()
   {
      try(java.io.InputStream input = TestFiles.class.getResourceAsStream("/attachments/10x20.webp"))
      {
         return (input.readAllBytes());
      }
      catch(java.io.IOException e)
      {
         throw (new IllegalStateException(e));
      }
   }



   /*******************************************************************************
    ** A real multi-frame GIF for aggregate decode limits.
    *******************************************************************************/
   public static byte[] animatedGif(int frames) throws Exception
   {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
      try(javax.imageio.stream.MemoryCacheImageOutputStream output = new javax.imageio.stream.MemoryCacheImageOutputStream(bytes))
      {
         writer.setOutput(output);
         writer.prepareWriteSequence(null);
         for(int i = 0; i < frames; i++)
         {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
            writer.writeToSequence(new javax.imageio.IIOImage(image, null, null), null);
            image.flush();
         }
         writer.endWriteSequence();
         output.flush();
         return (bytes.toByteArray());
      }
      finally
      {
         writer.dispose();
      }
   }



   /*******************************************************************************
    ** Encode an RGB image without any filesystem cache.
    *******************************************************************************/
   private static byte[] image(String format, int width, int height)
   {
      java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try(javax.imageio.stream.MemoryCacheImageOutputStream output = new javax.imageio.stream.MemoryCacheImageOutputStream(bytes))
      {
         javax.imageio.ImageIO.write(image, format, output);
         output.flush();
         return (bytes.toByteArray());
      }
      catch(java.io.IOException e)
      {
         throw (new IllegalStateException(e));
      }
      finally
      {
         image.flush();
      }
   }
}
