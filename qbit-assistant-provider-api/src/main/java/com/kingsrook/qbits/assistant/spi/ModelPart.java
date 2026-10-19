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

package com.kingsrook.qbits.assistant.spi;


import java.util.LinkedHashMap;
import java.util.Map;


/*******************************************************************************
 ** One content part of a model message: text, a tool_use (assistant), a
 ** tool_result (user) or an image (user; base64 data plus its media type).
 ** Tool results that report a failure set isError. toString never includes
 ** text or image data, so a part can never leak content into a log line.
 *******************************************************************************/
public class ModelPart
{
   /*******************************************************************************
    ** The kinds of part.
    *******************************************************************************/
   public enum Type
   {
      TEXT,
      TOOL_USE,
      TOOL_RESULT,
      IMAGE
   }

   private Type                type;
   private String              text;
   private String              toolUseId;
   private String              toolName;
   private Map<String, Object> input;
   private Boolean             isError = false;
   private String              mediaType;
   private String              data;



   /*******************************************************************************
    ** A text part.
    *******************************************************************************/
   public static ModelPart text(String text)
   {
      return (new ModelPart().withType(Type.TEXT).withText(text));
   }



   /*******************************************************************************
    ** A tool_use part.
    *******************************************************************************/
   public static ModelPart toolUse(String toolUseId, String toolName, Map<String, Object> input)
   {
      return (new ModelPart().withType(Type.TOOL_USE).withToolUseId(toolUseId).withToolName(toolName).withInput(input == null ? new LinkedHashMap<>() : input));
   }



   /*******************************************************************************
    ** A tool_result part (text is the content sent back to the model).
    *******************************************************************************/
   public static ModelPart toolResult(String toolUseId, String content, boolean isError)
   {
      return (new ModelPart().withType(Type.TOOL_RESULT).withToolUseId(toolUseId).withText(content).withIsError(isError));
   }



   /*******************************************************************************
    ** An image part: the media type (image/png, image/jpeg, image/gif or
    ** image/webp) and the base64-encoded bytes. Only for models whose
    ** capabilities report vision.
    *******************************************************************************/
   public static ModelPart image(String mediaType, String base64Data)
   {
      return (new ModelPart().withType(Type.IMAGE).withMediaType(mediaType).withData(base64Data));
   }



   /*******************************************************************************
    ** The kind and sizes only; never the text, input or image data.
    *******************************************************************************/
   @Override
   public String toString()
   {
      return ("ModelPart{type=" + type + ", textChars=" + (text == null ? 0 : text.length()) + ", toolName=" + toolName
         + (mediaType == null ? "" : ", mediaType=" + mediaType) + (data == null ? "" : ", dataChars=" + data.length()) + "}");
   }



   /*******************************************************************************
    ** Getter for type
    *******************************************************************************/
   public Type getType()
   {
      return (this.type);
   }



   /*******************************************************************************
    ** Setter for type
    *******************************************************************************/
   public void setType(Type type)
   {
      this.type = type;
   }



   /*******************************************************************************
    ** Fluent setter for type
    *******************************************************************************/
   public ModelPart withType(Type type)
   {
      this.type = type;
      return (this);
   }



   /*******************************************************************************
    ** Getter for text
    *******************************************************************************/
   public String getText()
   {
      return (this.text);
   }



   /*******************************************************************************
    ** Setter for text
    *******************************************************************************/
   public void setText(String text)
   {
      this.text = text;
   }



   /*******************************************************************************
    ** Fluent setter for text
    *******************************************************************************/
   public ModelPart withText(String text)
   {
      this.text = text;
      return (this);
   }



   /*******************************************************************************
    ** Getter for toolUseId
    *******************************************************************************/
   public String getToolUseId()
   {
      return (this.toolUseId);
   }



   /*******************************************************************************
    ** Setter for toolUseId
    *******************************************************************************/
   public void setToolUseId(String toolUseId)
   {
      this.toolUseId = toolUseId;
   }



   /*******************************************************************************
    ** Fluent setter for toolUseId
    *******************************************************************************/
   public ModelPart withToolUseId(String toolUseId)
   {
      this.toolUseId = toolUseId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for toolName
    *******************************************************************************/
   public String getToolName()
   {
      return (this.toolName);
   }



   /*******************************************************************************
    ** Setter for toolName
    *******************************************************************************/
   public void setToolName(String toolName)
   {
      this.toolName = toolName;
   }



   /*******************************************************************************
    ** Fluent setter for toolName
    *******************************************************************************/
   public ModelPart withToolName(String toolName)
   {
      this.toolName = toolName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for input
    *******************************************************************************/
   public Map<String, Object> getInput()
   {
      return (this.input);
   }



   /*******************************************************************************
    ** Setter for input
    *******************************************************************************/
   public void setInput(Map<String, Object> input)
   {
      this.input = input;
   }



   /*******************************************************************************
    ** Fluent setter for input
    *******************************************************************************/
   public ModelPart withInput(Map<String, Object> input)
   {
      this.input = input;
      return (this);
   }



   /*******************************************************************************
    ** Getter for isError
    *******************************************************************************/
   public Boolean getIsError()
   {
      return (this.isError);
   }



   /*******************************************************************************
    ** Setter for isError
    *******************************************************************************/
   public void setIsError(Boolean isError)
   {
      this.isError = isError;
   }



   /*******************************************************************************
    ** Fluent setter for isError
    *******************************************************************************/
   public ModelPart withIsError(Boolean isError)
   {
      this.isError = isError;
      return (this);
   }




   /*******************************************************************************
    ** Getter for mediaType
    *******************************************************************************/
   public String getMediaType()
   {
      return (this.mediaType);
   }



   /*******************************************************************************
    ** Setter for mediaType
    *******************************************************************************/
   public void setMediaType(String mediaType)
   {
      this.mediaType = mediaType;
   }



   /*******************************************************************************
    ** Fluent setter for mediaType
    *******************************************************************************/
   public ModelPart withMediaType(String mediaType)
   {
      this.mediaType = mediaType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for data (base64)
    *******************************************************************************/
   public String getData()
   {
      return (this.data);
   }



   /*******************************************************************************
    ** Setter for data (base64)
    *******************************************************************************/
   public void setData(String data)
   {
      this.data = data;
   }



   /*******************************************************************************
    ** Fluent setter for data (base64)
    *******************************************************************************/
   public ModelPart withData(String data)
   {
      this.data = data;
      return (this);
   }

}
