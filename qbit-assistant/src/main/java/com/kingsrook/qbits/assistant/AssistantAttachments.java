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

package com.kingsrook.qbits.assistant;


import java.io.Serializable;
import java.util.List;


/*******************************************************************************
 ** Limits for files the user attaches in the assistant panel. Every upload is
 ** untrusted: it is read in memory as the signed-in user, its type is sniffed
 ** from its bytes, and only bounded text (or a bounded image for a vision
 ** model) reaches the model.
 *******************************************************************************/
public class AssistantAttachments implements Serializable
{
   private Boolean enabled = true;
   private Long maxFileBytes = 10L * 1024 * 1024;
   private Long maxTurnBytes = 25L * 1024 * 1024;
   private Integer maxFileChars = 50_000;
   private Integer maxTurnChars = 150_000;
   private Integer maxPdfPages = 50;
   private Integer previewRows = 20;
   private Long maxImageBytes = 5L * 1024 * 1024;
   private Integer maxImageDimension = 8000;



   /*******************************************************************************
    ** Collect errors (missing or non-positive limits, per-file above per-turn).
    *******************************************************************************/
   public void validate(List<String> errors)
   {
      if(maxFileBytes == null || maxFileBytes <= 0)
      {
         errors.add("attachments.maxFileBytes must be positive.");
      }
      if(maxTurnBytes == null || maxTurnBytes <= 0)
      {
         errors.add("attachments.maxTurnBytes must be positive.");
      }
      if(maxFileChars == null || maxFileChars <= 0)
      {
         errors.add("attachments.maxFileChars must be positive.");
      }
      if(maxTurnChars == null || maxTurnChars <= 0)
      {
         errors.add("attachments.maxTurnChars must be positive.");
      }
      if(maxPdfPages == null || maxPdfPages <= 0)
      {
         errors.add("attachments.maxPdfPages must be positive.");
      }
      if(previewRows == null || previewRows <= 0)
      {
         errors.add("attachments.previewRows must be positive.");
      }
      if(maxImageBytes == null || maxImageBytes <= 0)
      {
         errors.add("attachments.maxImageBytes must be positive.");
      }
      if(maxImageDimension == null || maxImageDimension <= 0)
      {
         errors.add("attachments.maxImageDimension must be positive.");
      }
      if(maxFileBytes != null && maxTurnBytes != null && maxFileBytes > maxTurnBytes)
      {
         errors.add("attachments.maxFileBytes must not exceed attachments.maxTurnBytes.");
      }
      if(maxFileChars != null && maxTurnChars != null && maxFileChars > maxTurnChars)
      {
         errors.add("attachments.maxFileChars must not exceed attachments.maxTurnChars.");
      }
   }



   /*******************************************************************************
    ** Getter for enabled: Whether files the user attaches in the panel are read and given to the model (default true). Uploads must also be configured in core (durable jobs with an uploads storage table).
    *******************************************************************************/
   public Boolean getEnabled()
   {
      return (this.enabled);
   }



   /*******************************************************************************
    ** Setter for enabled
    *******************************************************************************/
   public void setEnabled(Boolean enabled)
   {
      this.enabled = enabled;
   }



   /*******************************************************************************
    ** Fluent setter for enabled
    *******************************************************************************/
   public AssistantAttachments withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxFileBytes: The largest file read, in bytes (default 10 MB). Larger files get a notice and are skipped.
    *******************************************************************************/
   public Long getMaxFileBytes()
   {
      return (this.maxFileBytes);
   }



   /*******************************************************************************
    ** Setter for maxFileBytes
    *******************************************************************************/
   public void setMaxFileBytes(Long maxFileBytes)
   {
      this.maxFileBytes = maxFileBytes;
   }



   /*******************************************************************************
    ** Fluent setter for maxFileBytes
    *******************************************************************************/
   public AssistantAttachments withMaxFileBytes(Long maxFileBytes)
   {
      this.maxFileBytes = maxFileBytes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxTurnBytes: The most bytes read across all files of one question (default 25 MB).
    *******************************************************************************/
   public Long getMaxTurnBytes()
   {
      return (this.maxTurnBytes);
   }



   /*******************************************************************************
    ** Setter for maxTurnBytes
    *******************************************************************************/
   public void setMaxTurnBytes(Long maxTurnBytes)
   {
      this.maxTurnBytes = maxTurnBytes;
   }



   /*******************************************************************************
    ** Fluent setter for maxTurnBytes
    *******************************************************************************/
   public AssistantAttachments withMaxTurnBytes(Long maxTurnBytes)
   {
      this.maxTurnBytes = maxTurnBytes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxFileChars: The most characters of extracted text given to the model per file (default 50,000); longer text is cut and marked as truncated.
    *******************************************************************************/
   public Integer getMaxFileChars()
   {
      return (this.maxFileChars);
   }



   /*******************************************************************************
    ** Setter for maxFileChars
    *******************************************************************************/
   public void setMaxFileChars(Integer maxFileChars)
   {
      this.maxFileChars = maxFileChars;
   }



   /*******************************************************************************
    ** Fluent setter for maxFileChars
    *******************************************************************************/
   public AssistantAttachments withMaxFileChars(Integer maxFileChars)
   {
      this.maxFileChars = maxFileChars;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxTurnChars: The most characters of extracted text given to the model across all files of one question (default 150,000).
    *******************************************************************************/
   public Integer getMaxTurnChars()
   {
      return (this.maxTurnChars);
   }



   /*******************************************************************************
    ** Setter for maxTurnChars
    *******************************************************************************/
   public void setMaxTurnChars(Integer maxTurnChars)
   {
      this.maxTurnChars = maxTurnChars;
   }



   /*******************************************************************************
    ** Fluent setter for maxTurnChars
    *******************************************************************************/
   public AssistantAttachments withMaxTurnChars(Integer maxTurnChars)
   {
      this.maxTurnChars = maxTurnChars;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxPdfPages: The most PDF pages whose text is extracted (default 50).
    *******************************************************************************/
   public Integer getMaxPdfPages()
   {
      return (this.maxPdfPages);
   }



   /*******************************************************************************
    ** Setter for maxPdfPages
    *******************************************************************************/
   public void setMaxPdfPages(Integer maxPdfPages)
   {
      this.maxPdfPages = maxPdfPages;
   }



   /*******************************************************************************
    ** Fluent setter for maxPdfPages
    *******************************************************************************/
   public AssistantAttachments withMaxPdfPages(Integer maxPdfPages)
   {
      this.maxPdfPages = maxPdfPages;
      return (this);
   }



   /*******************************************************************************
    ** Getter for previewRows: The data rows shown for CSV, TSV and XLSX files (default 20), after the header row; the total row count is always given.
    *******************************************************************************/
   public Integer getPreviewRows()
   {
      return (this.previewRows);
   }



   /*******************************************************************************
    ** Setter for previewRows
    *******************************************************************************/
   public void setPreviewRows(Integer previewRows)
   {
      this.previewRows = previewRows;
   }



   /*******************************************************************************
    ** Fluent setter for previewRows
    *******************************************************************************/
   public AssistantAttachments withPreviewRows(Integer previewRows)
   {
      this.previewRows = previewRows;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxImageBytes: The largest image sent to a vision model, in bytes (default 5 MB).
    *******************************************************************************/
   public Long getMaxImageBytes()
   {
      return (this.maxImageBytes);
   }



   /*******************************************************************************
    ** Setter for maxImageBytes
    *******************************************************************************/
   public void setMaxImageBytes(Long maxImageBytes)
   {
      this.maxImageBytes = maxImageBytes;
   }



   /*******************************************************************************
    ** Fluent setter for maxImageBytes
    *******************************************************************************/
   public AssistantAttachments withMaxImageBytes(Long maxImageBytes)
   {
      this.maxImageBytes = maxImageBytes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxImageDimension: The largest image width or height in pixels (default 8000).
    *******************************************************************************/
   public Integer getMaxImageDimension()
   {
      return (this.maxImageDimension);
   }



   /*******************************************************************************
    ** Setter for maxImageDimension
    *******************************************************************************/
   public void setMaxImageDimension(Integer maxImageDimension)
   {
      this.maxImageDimension = maxImageDimension;
   }



   /*******************************************************************************
    ** Fluent setter for maxImageDimension
    *******************************************************************************/
   public AssistantAttachments withMaxImageDimension(Integer maxImageDimension)
   {
      this.maxImageDimension = maxImageDimension;
      return (this);
   }

}
