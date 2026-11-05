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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import static com.kingsrook.qbits.assistant.attachments.TextDecoding.startsWith;
import static com.kingsrook.qbits.assistant.attachments.TextDecoding.startsWithAt;


/*******************************************************************************
 ** Decides what an upload is from its bytes - never from its name or the
 ** content type the browser sent. Binary formats are recognized by their
 ** magic bytes; anything else must decode as text. Executables, archives and
 ** unknown binary data are refused.
 **
 ** The file name is only a hint between text forms: a ".csv" or ".tsv" file
 ** whose text is delimited is previewed as a table, and plain text that is
 ** clearly delimited (3+ lines, same column count) is too.
 *******************************************************************************/
public final class AttachmentSniffer
{
   static final int  MAX_ZIP_ENTRIES        = 5_000;
   static final long MAX_UNZIPPED_BYTES     = 200L * 1024 * 1024;
   static final int  DELIMITED_SAMPLE_CHARS = 64 * 1024;

   private static final ObjectMapper JSON = new ObjectMapper();

   public static final String SUPPORTED_HINT = "Attach text, Markdown, JSON, CSV, TSV, Excel (.xlsx), PDF or an image (PNG, JPEG, GIF, WebP).";



   /*******************************************************************************
    ** What was found: the type and, for text types, the decoded text.
    *******************************************************************************/
   public record Sniffed(AttachmentType type, String text)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AttachmentSniffer()
   {
   }



   /*******************************************************************************
    ** Sniff the bytes; throws with a user-facing reason for refused content.
    *******************************************************************************/
   public static Sniffed sniff(byte[] bytes, String fileName) throws AttachmentException
   {
      if(bytes == null || bytes.length == 0)
      {
         throw (new AttachmentException("the file is empty."));
      }

      if(isPdf(bytes))
      {
         return (new Sniffed(AttachmentType.PDF, null));
      }
      if(startsWith(bytes, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))
      {
         return (new Sniffed(AttachmentType.PNG, null));
      }
      if(startsWith(bytes, 0xFF, 0xD8, 0xFF))
      {
         return (new Sniffed(AttachmentType.JPEG, null));
      }
      if(startsWith(bytes, 'G', 'I', 'F', '8', '7', 'a') || startsWith(bytes, 'G', 'I', 'F', '8', '9', 'a'))
      {
         return (new Sniffed(AttachmentType.GIF, null));
      }
      if(startsWith(bytes, 'R', 'I', 'F', 'F') && startsWithAt(bytes, 8, 'W', 'E', 'B', 'P'))
      {
         return (new Sniffed(AttachmentType.WEBP, null));
      }
      if(startsWith(bytes, 'P', 'K', 0x03, 0x04) || startsWith(bytes, 'P', 'K', 0x05, 0x06))
      {
         return (new Sniffed(zipType(bytes), null));
      }
      if(isExecutable(bytes))
      {
         throw (new AttachmentException("it is a program (executable) file, which the assistant never opens. " + SUPPORTED_HINT));
      }
      if(isArchive(bytes))
      {
         throw (new AttachmentException("it is a compressed archive, which the assistant does not open. " + SUPPORTED_HINT));
      }
      if(startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1))
      {
         throw (new AttachmentException("it is a legacy Office file (.xls or .doc). Save it as .xlsx, .csv or .pdf and attach it again."));
      }

      String text = TextDecoding.decode(bytes);
      if(text == null)
      {
         throw (new AttachmentException("its content is not a supported file type. " + SUPPORTED_HINT));
      }
      return (new Sniffed(textType(text, fileName), text));
   }



   /*******************************************************************************
    ** A PDF header within the first 1024 bytes (as readers accept it).
    *******************************************************************************/
   static boolean isPdf(byte[] bytes)
   {
      int limit = Math.min(bytes.length, 1024) - 5;
      for(int i = 0; i <= limit; i++)
      {
         if(startsWithAt(bytes, i, '%', 'P', 'D', 'F', '-'))
         {
            return (true);
         }
      }
      return (false);
   }



   /*******************************************************************************
    ** Windows/DOS (MZ with binary header bytes), ELF, Mach-O (thin and fat)
    ** and Java class files.
    *******************************************************************************/
   static boolean isExecutable(byte[] bytes)
   {
      return ((startsWith(bytes, 'M', 'Z') && hasZeroByte(bytes, 512))
         || startsWith(bytes, 0x7F, 'E', 'L', 'F')
         || startsWith(bytes, 0xFE, 0xED, 0xFA, 0xCE) || startsWith(bytes, 0xFE, 0xED, 0xFA, 0xCF)
         || startsWith(bytes, 0xCE, 0xFA, 0xED, 0xFE) || startsWith(bytes, 0xCF, 0xFA, 0xED, 0xFE)
         || startsWith(bytes, 0xCA, 0xFE, 0xBA, 0xBE));
   }



   /*******************************************************************************
    ** Whether one of the first `limit` bytes is zero (binary, not text).
    *******************************************************************************/
   private static boolean hasZeroByte(byte[] bytes, int limit)
   {
      for(int i = 0; i < Math.min(bytes.length, limit); i++)
      {
         if(bytes[i] == 0)
         {
            return (true);
         }
      }
      return (false);
   }



   /*******************************************************************************
    ** gzip, bzip2, xz, 7-Zip, RAR, zstd and tar.
    *******************************************************************************/
   static boolean isArchive(byte[] bytes)
   {
      return (startsWith(bytes, 0x1F, 0x8B)
         || (startsWith(bytes, 'B', 'Z', 'h') && bytes.length > 3 && bytes[3] >= '1' && bytes[3] <= '9' && hasZeroByte(bytes, 512))
         || startsWith(bytes, 0xFD, '7', 'z', 'X', 'Z', 0x00)
         || startsWith(bytes, '7', 'z', 0xBC, 0xAF, 0x27, 0x1C)
         || startsWith(bytes, 'R', 'a', 'r', '!', 0x1A, 0x07)
         || startsWith(bytes, 0x28, 0xB5, 0x2F, 0xFD)
         || startsWithAt(bytes, 257, 'u', 's', 't', 'a', 'r'));
   }



   /*******************************************************************************
    ** A ZIP container is accepted only as an Excel workbook (xl/workbook.xml).
    ** The central directory is read in memory (commons-compress, as the XLSX
    ** reader does) and every entry is unpacked with hard caps on the number of
    ** entries and the total unpacked size, so a "zip bomb" is refused before
    ** any parser sees it.
    *******************************************************************************/
   static AttachmentType zipType(byte[] bytes) throws AttachmentException
   {
      boolean workbook     = false;
      boolean contentTypes = false;
      boolean macros       = false;
      String  other        = null;
      int     entries      = 0;
      long    unzipped     = 0;
      byte[]  buffer       = new byte[64 * 1024];
      try(ZipFile zip = ZipFile.builder().setSeekableByteChannel(new SeekableInMemoryByteChannel(bytes)).get())
      {
         Enumeration<ZipArchiveEntry> all = zip.getEntries();
         while(all.hasMoreElements())
         {
            ZipArchiveEntry entry = all.nextElement();
            if(++entries > MAX_ZIP_ENTRIES)
            {
               throw (new AttachmentException("the file holds too many parts to open safely."));
            }
            String name = entry.getName();
            workbook |= "xl/workbook.xml".equals(name);
            contentTypes |= "[Content_Types].xml".equals(name);
            macros |= name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin");
            if(name.startsWith("word/"))
            {
               other = "a Word document (.docx). Save it as PDF or text and attach it again.";
            }
            else if(name.startsWith("ppt/"))
            {
               other = "a PowerPoint file (.pptx). Save it as PDF and attach it again.";
            }

            ////////////////////////////////////////////////////////////////
            // count what each entry really unpacks to (declared sizes can //
            // lie), stopping at the cap                                   //
            ////////////////////////////////////////////////////////////////
            try(InputStream stream = zip.getInputStream(entry))
            {
               int read;
               while(stream != null && (read = stream.read(buffer)) != -1)
               {
                  unzipped += read;
                  if(unzipped > MAX_UNZIPPED_BYTES)
                  {
                     throw (new AttachmentException("the file unpacks to more than " + (MAX_UNZIPPED_BYTES / (1024 * 1024)) + " MB, which is too large to open safely."));
                  }
               }
            }
         }
      }
      catch(IOException | RuntimeException e)
      {
         throw (new AttachmentException("the file looks like a damaged archive or workbook.", e));
      }

      if(workbook && contentTypes)
      {
         if(macros)
         {
            throw (new AttachmentException("the workbook contains macros. Save it as a plain .xlsx (or .csv) and attach it again."));
         }
         return (AttachmentType.XLSX);
      }
      if(other != null)
      {
         throw (new AttachmentException("it is " + other));
      }
      throw (new AttachmentException("it is a compressed archive (ZIP), which the assistant does not open. " + SUPPORTED_HINT));
   }



   /*******************************************************************************
    ** JSON when it parses as an object or array; a table when delimited (see
    ** the class comment); otherwise text (Markdown is text).
    *******************************************************************************/
   static AttachmentType textType(String text, String fileName)
   {
      String trimmed = text.strip();
      if((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]")))
      {
         try
         {
            JSON.readTree(trimmed);
            return (AttachmentType.JSON);
         }
         catch(Exception e)
         {
            ////////////////////////////////////
            // not JSON after all: keep going //
            ////////////////////////////////////
         }
      }

      String  name   = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
      boolean csvHint = name.endsWith(".csv");
      boolean tsvHint = name.endsWith(".tsv") || name.endsWith(".tab");
      String  sample = text;
      if(text.length() > DELIMITED_SAMPLE_CHARS)
      {
         int cut = text.lastIndexOf('\n', DELIMITED_SAMPLE_CHARS);
         sample = text.substring(0, cut > 0 ? cut : DELIMITED_SAMPLE_CHARS);
      }

      Integer tabColumns = consistentColumns(sample, CSVFormat.TDF);
      if(tabColumns != null && (tsvHint ? tabColumns >= 2 : tabColumns >= 2 && lines(sample) >= 3))
      {
         return (AttachmentType.TSV);
      }
      Integer commaColumns = consistentColumns(sample, CSVFormat.DEFAULT);
      if(commaColumns != null && (csvHint ? commaColumns >= 2 : commaColumns >= 3 && lines(sample) >= 3))
      {
         return (AttachmentType.CSV);
      }
      return (AttachmentType.TEXT);
   }



   /*******************************************************************************
    ** The column count when the first (up to 20) records of a sample all have
    ** the same count; else null.
    *******************************************************************************/
   static Integer consistentColumns(String sample, CSVFormat format)
   {
      try(CSVParser parser = CSVParser.parse(new StringReader(sample), format))
      {
         List<Integer> sizes = new ArrayList<>();
         for(CSVRecord record : parser)
         {
            if(record.size() == 1 && record.get(0).isBlank())
            {
               continue;
            }
            sizes.add(record.size());
            if(sizes.size() >= 20)
            {
               break;
            }
         }
         if(sizes.size() < 2 || sizes.stream().distinct().count() != 1)
         {
            return (null);
         }
         return (sizes.get(0));
      }
      catch(Exception e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    ** Non-blank lines in a sample.
    *******************************************************************************/
   private static long lines(String sample)
   {
      return (sample.lines().filter(line -> !line.isBlank()).count());
   }


}
