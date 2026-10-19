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


import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.kingsrook.qbits.assistant.AssistantAttachments;
import com.kingsrook.qqq.backend.core.processes.implementations.bulk.insert.filehandling.CsvFileToRows;
import com.kingsrook.qqq.backend.core.processes.implementations.bulk.insert.filehandling.FileToRowsInterface;
import com.kingsrook.qqq.backend.core.processes.implementations.bulk.insert.model.BulkLoadFileRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;


/*******************************************************************************
 ** Turns sniffed attachment bytes into bounded text for the model: decoded
 ** text and JSON (cut at the character cap), PDF page text, and for CSV, TSV
 ** and XLSX a preview - the header, the first rows and the total row count.
 ** CSV and XLSX use core's bulk-load file parsers (the same ones the bulk
 ** load tools use); TSV uses commons-csv, which core already ships.
 *******************************************************************************/
public final class AttachmentExtractor
{
   static final int  MAX_CELL_CHARS     = 200;
   static final long PDF_MEMORY_BYTES   = 128L * 1024 * 1024;
   static final int  MAX_COUNTED_ROWS   = 1_048_576;



   /*******************************************************************************
    ** Extracted text plus extra (safe) block attributes.
    *******************************************************************************/
   public record Extracted(String text, String details)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AttachmentExtractor()
   {
   }



   /*******************************************************************************
    ** The text of a non-image attachment, at most maxChars characters.
    *******************************************************************************/
   public static Extracted extract(byte[] bytes, AttachmentSniffer.Sniffed sniffed, AssistantAttachments limits, int maxChars) throws AttachmentException
   {
      return (switch(sniffed.type())
      {
         case TEXT, JSON -> text(sniffed.text(), maxChars);
         case CSV -> table(csvRows(sniffed.text()), limits.getPreviewRows(), maxChars);
         case TSV -> table(tsvRows(sniffed.text()), limits.getPreviewRows(), maxChars);
         case XLSX -> xlsx(bytes, limits.getPreviewRows(), maxChars);
         case PDF -> pdf(bytes, limits.getMaxPdfPages(), maxChars);
         default -> throw (new AttachmentException("it is not a document type the assistant reads."));
      });
   }



   /*******************************************************************************
    ** Text cut at maxChars.
    *******************************************************************************/
   static Extracted text(String text, int maxChars)
   {
      if(text.length() <= maxChars)
      {
         return (new Extracted(text, "chars=\"" + text.length() + "\""));
      }
      return (new Extracted(text.substring(0, maxChars), "chars=\"" + text.length() + "\" truncated=\"true\""));
   }



   /*******************************************************************************
    ** PDF page text.
    *******************************************************************************/
   static Extracted pdf(byte[] bytes, int maxPages, int maxChars) throws AttachmentException
   {
      PdfText.Extracted extracted = PdfText.extract(bytes, maxPages, maxChars, PDF_MEMORY_BYTES);
      String            text      = extracted.text().strip();
      String            details   = "pages=\"" + extracted.pages() + "\"" + (extracted.pagesRead() < extracted.pages() ? " pagesRead=\"" + extracted.pagesRead() + "\"" : "")
         + (extracted.truncated() ? " truncated=\"true\"" : "");
      if(text.isEmpty())
      {
         return (new Extracted("(This PDF has no extractable text; it may be a scanned image.)", details));
      }
      return (new Extracted(text, details));
   }



   /*******************************************************************************
    ** CSV rows through core's bulk-load CSV parser.
    *******************************************************************************/
   static Iterator<List<String>> csvRows(String text) throws AttachmentException
   {
      try
      {
         CsvFileToRows rows = CsvFileToRows.forString(text);
         return (mapped(rows));
      }
      catch(Exception e)
      {
         throw (new AttachmentException("the CSV could not be parsed.", e));
      }
   }



   /*******************************************************************************
    ** Tab-separated rows.
    *******************************************************************************/
   static Iterator<List<String>> tsvRows(String text) throws AttachmentException
   {
      try
      {
         Iterator<CSVRecord> records = CSVParser.parse(new StringReader(text), CSVFormat.TDF).iterator();
         return (new Iterator<>()
         {
            /*******************************************************************************
             **
             *******************************************************************************/
            @Override
            public boolean hasNext()
            {
               return (records.hasNext());
            }



            /*******************************************************************************
             **
             *******************************************************************************/
            @Override
            public List<String> next()
            {
               return (records.next().toList());
            }
         });
      }
      catch(Exception e)
      {
         throw (new AttachmentException("the TSV could not be parsed.", e));
      }
   }



   /*******************************************************************************
    ** The first sheet of a workbook through core's bulk-load XLSX parser.
    *******************************************************************************/
   static Extracted xlsx(byte[] bytes, int previewRows, int maxChars) throws AttachmentException
   {
      try(FileToRowsInterface rows = FileToRowsInterface.forFile("attachment.xlsx", new ByteArrayInputStream(bytes)))
      {
         Extracted table = table(mapped(rows), previewRows, maxChars);
         return (new Extracted(table.text(), "sheet=\"first\" " + table.details()));
      }
      catch(AttachmentException e)
      {
         throw (e);
      }
      catch(Exception e)
      {
         throw (new AttachmentException("the workbook could not be read (it may be damaged).", e));
      }
   }



   /*******************************************************************************
    ** Core rows as lists of display strings.
    *******************************************************************************/
   private static Iterator<List<String>> mapped(Iterator<BulkLoadFileRow> rows)
   {
      return (new Iterator<>()
      {
         /*******************************************************************************
          **
          *******************************************************************************/
         @Override
         public boolean hasNext()
         {
            return (rows.hasNext());
         }



         /*******************************************************************************
          **
          *******************************************************************************/
         @Override
         public List<String> next()
         {
            BulkLoadFileRow row    = rows.next();
            List<String>    values = new ArrayList<>();
            for(int i = 0; i < row.size(); i++)
            {
               Object value = row.getValueElseNull(i);
               values.add(value == null ? "" : String.valueOf(value));
            }
            return (values);
         }
      });
   }



   /*******************************************************************************
    ** The header, the first previewRows data rows and the total row count.
    *******************************************************************************/
   static Extracted table(Iterator<List<String>> rows, int previewRows, int maxChars)
   {
      StringBuilder text      = new StringBuilder();
      boolean       truncated = false;
      int           dataRows  = 0;
      int           columns   = 0;
      boolean       header    = true;
      while(rows.hasNext())
      {
         List<String> row = rows.next();
         if(header)
         {
            columns = row.size();
            text.append("Columns (").append(columns).append("): ").append(line(row)).append('\n');
            header = false;
            continue;
         }
         if(dataRows >= MAX_COUNTED_ROWS)
         {
            break;
         }
         dataRows++;
         if(dataRows <= previewRows && !truncated)
         {
            String line = "Row " + dataRows + ": " + line(row) + "\n";
            if(text.length() + line.length() > maxChars)
            {
               truncated = true;
            }
            else
            {
               text.append(line);
            }
         }
      }
      int shown = Math.min(dataRows, previewRows);
      text.append("(").append(dataRows >= MAX_COUNTED_ROWS ? "at least " + MAX_COUNTED_ROWS : dataRows).append(" data rows in total")
         .append(dataRows > shown || truncated ? "; only the first rows are shown" : "").append(".)");
      String result = text.length() > maxChars ? text.substring(0, maxChars) : text.toString();
      return (new Extracted(result, "columns=\"" + columns + "\" rows=\"" + dataRows + "\"" + (dataRows > shown || truncated ? " truncated=\"true\"" : "")));
   }



   /*******************************************************************************
    ** One row as "a | b | c", cells cut and flattened.
    *******************************************************************************/
   private static String line(List<String> row)
   {
      List<String> cells = new ArrayList<>();
      for(String cell : row)
      {
         String value = cell == null ? "" : cell.replace("\r", " ").replace("\n", " ").replace("|", "\\|").strip();
         cells.add(value.length() > MAX_CELL_CHARS ? value.substring(0, MAX_CELL_CHARS) + "..." : value);
      }
      return (String.join(" | ", cells));
   }

}
