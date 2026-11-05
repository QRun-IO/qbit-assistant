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


import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;


/*******************************************************************************
 ** Decides whether bytes are text and decodes them: a byte-order mark (UTF-8,
 ** UTF-16), then UTF-16 without a mark (alternating zero bytes), then strict
 ** UTF-8, then windows-1252. Anything with NUL bytes or many control
 ** characters is not text.
 *******************************************************************************/
public final class TextDecoding
{
   private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

   /////////////////////////////////////////////////////////////////////////
   // at most 1 control character (other than tab, CR, LF, FF) per 100   //
   /////////////////////////////////////////////////////////////////////////
   private static final double MAX_CONTROL_RATIO = 0.01;



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private TextDecoding()
   {
   }



   /*******************************************************************************
    ** The decoded text (without a byte-order mark), or null when the bytes are
    ** not text.
    *******************************************************************************/
   public static String decode(byte[] bytes)
   {
      if(bytes == null || bytes.length == 0)
      {
         return (null);
      }

      String text;
      if(startsWith(bytes, 0xEF, 0xBB, 0xBF))
      {
         text = strict(bytes, 3, StandardCharsets.UTF_8);
      }
      else if(startsWith(bytes, 0xFF, 0xFE))
      {
         text = strict(bytes, 2, StandardCharsets.UTF_16LE);
      }
      else if(startsWith(bytes, 0xFE, 0xFF))
      {
         text = strict(bytes, 2, StandardCharsets.UTF_16BE);
      }
      else if(looksLikeUtf16(bytes, 1))
      {
         text = strict(bytes, 0, StandardCharsets.UTF_16LE);
      }
      else if(looksLikeUtf16(bytes, 0))
      {
         text = strict(bytes, 0, StandardCharsets.UTF_16BE);
      }
      else
      {
         for(byte b : bytes)
         {
            if(b == 0)
            {
               return (null);
            }
         }
         text = strict(bytes, 0, StandardCharsets.UTF_8);
         if(text == null)
         {
            text = new String(bytes, WINDOWS_1252);
         }
      }

      return (text != null && isMostlyPrintable(text) ? text : null);
   }



   /*******************************************************************************
    ** Strict decoding from an offset; null when the bytes are not valid.
    *******************************************************************************/
   private static String strict(byte[] bytes, int offset, Charset charset)
   {
      try
      {
         return (charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
            .toString());
      }
      catch(CharacterCodingException e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    ** UTF-16 without a byte-order mark: most bytes at zeroIndex parity are zero
    ** (ASCII-range text) and almost none at the other parity.
    *******************************************************************************/
   private static boolean looksLikeUtf16(byte[] bytes, int zeroParity)
   {
      if(bytes.length < 4 || bytes.length % 2 != 0)
      {
         return (false);
      }
      int sample    = Math.min(bytes.length, 4096);
      int zeros     = 0;
      int otherZero = 0;
      for(int i = 0; i < sample; i++)
      {
         if(bytes[i] == 0)
         {
            if(i % 2 == zeroParity)
            {
               zeros++;
            }
            else
            {
               otherZero++;
            }
         }
      }
      return (zeros > sample / 2 * 0.6 && otherZero == 0);
   }



   /*******************************************************************************
    ** Few control characters besides tab, newline, carriage return, form feed.
    *******************************************************************************/
   private static boolean isMostlyPrintable(String text)
   {
      if(text.isEmpty())
      {
         return (true);
      }
      int control = 0;
      for(int i = 0; i < text.length(); i++)
      {
         char c = text.charAt(i);
         if(c == 0)
         {
            return (false);
         }
         if((c < 0x20 && c != '\t' && c != '\n' && c != '\r' && c != '\f') || c == 0x7F)
         {
            control++;
         }
      }
      return (control <= Math.max(1, text.length() * MAX_CONTROL_RATIO));
   }



   /*******************************************************************************
    ** Whether bytes start with the given unsigned values.
    *******************************************************************************/
   static boolean startsWith(byte[] bytes, int... prefix)
   {
      return (startsWithAt(bytes, 0, prefix));
   }



   /*******************************************************************************
    ** Whether bytes hold the given unsigned values at an offset.
    *******************************************************************************/
   static boolean startsWithAt(byte[] bytes, int offset, int... prefix)
   {
      if(bytes.length < offset + prefix.length)
      {
         return (false);
      }
      for(int i = 0; i < prefix.length; i++)
      {
         if((bytes[offset + i] & 0xFF) != prefix[i])
         {
            return (false);
         }
      }
      return (true);
   }

}
