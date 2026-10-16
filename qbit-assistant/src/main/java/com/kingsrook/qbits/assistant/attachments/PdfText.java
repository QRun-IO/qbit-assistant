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


import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.font.CIDFontMapping;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.PDCIDSystemInfo;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;


/*******************************************************************************
 ** Text extraction from an uploaded PDF with Apache PDFBox (Apache-2.0, already
 ** on the classpath through qqq-backend-core). Only the page text is read:
 ** nothing is rendered, no JavaScript or form action runs (PDFBox has no
 ** script engine), embedded files and external references are never opened,
 ** and the document is parsed in main memory only (never a temp file), with
 ** a memory cap.
 **
 ** PDFBox looks up fonts a PDF does not embed by scanning the host's system
 ** fonts and writing a font cache file. Text extraction does not need real
 ** glyphs, so the first extraction installs a font mapper that always answers
 ** with PDFBox's bundled fallback font: no disk scan, no cache file.
 *******************************************************************************/
public final class PdfText
{
   private static final String FALLBACK_FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

   private static volatile boolean fontMapperInstalled = false;



   /*******************************************************************************
    ** The text of the first pages and whether anything was cut.
    *******************************************************************************/
   public record Extracted(String text, Integer pages, Integer pagesRead, Boolean truncated)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private PdfText()
   {
   }



   /*******************************************************************************
    ** Extract at most maxChars characters from at most maxPages pages.
    *******************************************************************************/
   public static Extracted extract(byte[] bytes, int maxPages, int maxChars, long maxMemoryBytes) throws AttachmentException
   {
      installFontMapper();
      try(PDDocument document = PDDocument.load(bytes, "", null, null, MemoryUsageSetting.setupMainMemoryOnly(maxMemoryBytes)))
      {
         if(document.isEncrypted())
         {
            throw (new AttachmentException("the PDF is encrypted. Save an unprotected copy and attach it again."));
         }
         int pages     = document.getNumberOfPages();
         int pagesRead = Math.min(pages, maxPages);

         PDFTextStripper stripper = new PDFTextStripper();
         stripper.setStartPage(1);
         stripper.setEndPage(pagesRead);
         CappedWriter writer = new CappedWriter(maxChars);
         try
         {
            stripper.writeText(document, writer);
         }
         catch(CappedWriter.Full full)
         {
            ////////////////////////////////////////////
            // enough text: stop reading more pages   //
            ////////////////////////////////////////////
         }
         return (new Extracted(writer.text(), pages, pagesRead, writer.isFull() || pagesRead < pages));
      }
      catch(InvalidPasswordException e)
      {
         throw (new AttachmentException("the PDF is password-protected. Save an unprotected copy and attach it again.", e));
      }
      catch(IOException | RuntimeException e)
      {
         throw (new AttachmentException("the PDF could not be parsed (it may be damaged or too complex).", e));
      }
   }



   /*******************************************************************************
    ** Install the no-scan font mapper once.
    *******************************************************************************/
   static void installFontMapper()
   {
      if(!fontMapperInstalled)
      {
         synchronized(PdfText.class)
         {
            if(!fontMapperInstalled)
            {
               FontMappers.set(new FallbackOnlyFontMapper());
               fontMapperInstalled = true;
            }
         }
      }
   }



   /*******************************************************************************
    ** Answers every font lookup with the bundled fallback font (a fallback
    ** mapping), loaded once from the PDFBox jar.
    *******************************************************************************/
   static class FallbackOnlyFontMapper implements FontMapper
   {
      private volatile TrueTypeFont fallback;



      /*******************************************************************************
       ** The bundled font.
       *******************************************************************************/
      private TrueTypeFont fallback()
      {
         TrueTypeFont font = fallback;
         if(font == null)
         {
            synchronized(this)
            {
               font = fallback;
               if(font == null)
               {
                  try(InputStream stream = PDFTextStripper.class.getResourceAsStream(FALLBACK_FONT))
                  {
                     if(stream == null)
                     {
                        throw (new IllegalStateException("PDFBox fallback font is missing"));
                     }
                     font = new TTFParser().parse(stream);
                     fallback = font;
                  }
                  catch(IOException e)
                  {
                     throw (new IllegalStateException("PDFBox fallback font could not be read", e));
                  }
               }
            }
         }
         return (font);
      }



      /*******************************************************************************
       ** A TrueType substitute.
       *******************************************************************************/
      @Override
      public FontMapping<TrueTypeFont> getTrueTypeFont(String baseFont, PDFontDescriptor fontDescriptor)
      {
         return (new FontMapping<>(fallback(), true));
      }



      /*******************************************************************************
       ** A Type 1 / CFF substitute.
       *******************************************************************************/
      @Override
      public FontMapping<FontBoxFont> getFontBoxFont(String baseFont, PDFontDescriptor fontDescriptor)
      {
         return (new FontMapping<>(fallback(), true));
      }



      /*******************************************************************************
       ** A CID font substitute.
       *******************************************************************************/
      @Override
      public CIDFontMapping getCIDFont(String baseFont, PDFontDescriptor fontDescriptor, PDCIDSystemInfo cidSystemInfo)
      {
         return (new CIDFontMapping(null, fallback(), true));
      }
   }



   /*******************************************************************************
    ** A writer that keeps at most `max` characters and then stops the stripper.
    *******************************************************************************/
   static class CappedWriter extends Writer
   {
      private final StringBuilder text = new StringBuilder();
      private final int           max;
      private boolean             full = false;



      /*******************************************************************************
       ** Thrown to stop extraction once the cap is reached.
       *******************************************************************************/
      static class Full extends IOException
      {
         /*******************************************************************************
          **
          *******************************************************************************/
         Full()
         {
            super("full");
         }
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      CappedWriter(int max)
      {
         this.max = max;
      }



      /*******************************************************************************
       ** Append up to the cap; then throw Full.
       *******************************************************************************/
      @Override
      public void write(char[] buffer, int offset, int length) throws IOException
      {
         int room = max - text.length();
         if(length > room)
         {
            text.append(buffer, offset, Math.max(room, 0));
            full = true;
            throw (new Full());
         }
         text.append(buffer, offset, length);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void flush()
      {
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void close()
      {
      }



      /*******************************************************************************
       ** The text so far.
       *******************************************************************************/
      String text()
      {
         return (text.toString());
      }



      /*******************************************************************************
       ** Whether the cap was reached.
       *******************************************************************************/
      boolean isFull()
      {
         return (full);
      }
   }

}
