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
 ** Image width and height read from the header bytes only (PNG IHDR, GIF
 ** screen descriptor, JPEG start-of-frame, WebP VP8/VP8L/VP8X). The image is
 ** never decoded on the server.
 *******************************************************************************/
public final class ImageInfo
{

   /*******************************************************************************
    ** Width and height in pixels.
    *******************************************************************************/
   public record Size(Integer width, Integer height)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private ImageInfo()
   {
   }



   /*******************************************************************************
    ** The size of a sniffed image; throws when the header is unreadable.
    *******************************************************************************/
   public static Size size(byte[] bytes, AttachmentType type) throws AttachmentException
   {
      Size size = switch(type)
      {
         case PNG -> bytes.length >= 24 && TextDecoding.startsWithAt(bytes, 12, 'I', 'H', 'D', 'R') ? new Size(int32be(bytes, 16), int32be(bytes, 20)) : null;
         case GIF -> bytes.length >= 10 ? new Size(int16le(bytes, 6), int16le(bytes, 8)) : null;
         case JPEG -> jpeg(bytes);
         case WEBP -> webp(bytes);
         default -> null;
      };
      if(size == null || size.width() <= 0 || size.height() <= 0)
      {
         throw (new AttachmentException("the image header could not be read (the file may be damaged)."));
      }
      return (size);
   }



   /*******************************************************************************
    ** Walk the JPEG markers to the first start-of-frame (SOF0-SOF15 except
    ** DHT, JPG and DAC).
    *******************************************************************************/
   private static Size jpeg(byte[] bytes)
   {
      int i = 2;
      while(i + 9 < bytes.length)
      {
         if((bytes[i] & 0xFF) != 0xFF)
         {
            return (null);
         }
         int marker = bytes[i + 1] & 0xFF;
         if(marker == 0xFF)
         {
            i++;
            continue;
         }
         if(marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01)
         {
            i += 2;
            continue;
         }
         int length = int16be(bytes, i + 2);
         if(marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC)
         {
            return (new Size(int16be(bytes, i + 7), int16be(bytes, i + 5)));
         }
         if(length < 2)
         {
            return (null);
         }
         i += 2 + length;
      }
      return (null);
   }



   /*******************************************************************************
    ** The WebP chunk after "RIFF....WEBP".
    *******************************************************************************/
   private static Size webp(byte[] bytes)
   {
      if(bytes.length < 30)
      {
         return (null);
      }
      if(TextDecoding.startsWithAt(bytes, 12, 'V', 'P', '8', ' '))
      {
         return (new Size(int16le(bytes, 26) & 0x3FFF, int16le(bytes, 28) & 0x3FFF));
      }
      if(TextDecoding.startsWithAt(bytes, 12, 'V', 'P', '8', 'L'))
      {
         int b0 = bytes[21] & 0xFF;
         int b1 = bytes[22] & 0xFF;
         int b2 = bytes[23] & 0xFF;
         int b3 = bytes[24] & 0xFF;
         return (new Size(1 + (((b1 & 0x3F) << 8) | b0), 1 + (((b3 & 0x0F) << 10) | (b2 << 2) | ((b1 & 0xC0) >> 6))));
      }
      if(TextDecoding.startsWithAt(bytes, 12, 'V', 'P', '8', 'X'))
      {
         return (new Size(1 + int24le(bytes, 24), 1 + int24le(bytes, 27)));
      }
      return (null);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static int int32be(byte[] b, int i)
   {
      return (((b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16) | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static int int16be(byte[] b, int i)
   {
      return (((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static int int16le(byte[] b, int i)
   {
      return ((b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static int int24le(byte[] b, int i)
   {
      return ((b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8) | ((b[i + 2] & 0xFF) << 16));
   }

}
