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


import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;


/*******************************************************************************
 ** Validate images with ImageIO before sending them to a provider. Dimensions
 ** and the total frame pixel budget are checked before each decode. Readers
 ** use memory-only input, preserve host ImageIO settings, and are disposed.
 *******************************************************************************/
public final class ImageInfo
{
   static final long MAX_DECODED_PIXELS = 16_000_000L;
   static final int MAX_FRAMES = 100;



   /*******************************************************************************
    ** Image width and height.
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
    ** Validate with the default dimension limit.
    *******************************************************************************/
   public static Size size(byte[] bytes, AttachmentType type) throws AttachmentException
   {
      return (size(bytes, type, 8000));
   }



   /*******************************************************************************
    ** Decode every frame within a bounded pixel budget. Decoder warnings are
    ** rejected because JPEG readers may otherwise silently repair truncation.
    *******************************************************************************/
   public static Size size(byte[] bytes, AttachmentType type, int maxDimension) throws AttachmentException
   {
      try(MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes)))
      {
         Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
         if(!readers.hasNext())
         {
            throw (new AttachmentException("the image header could not be read (the file may be damaged)."));
         }
         ImageReader reader = readers.next();
         try
         {
            reader.setInput(input, false, true);
            if(!reader.getFormatName().equalsIgnoreCase(type.name()))
            {
               throw (new AttachmentException("the image format does not match its content."));
            }
            AtomicBoolean warning = new AtomicBoolean();
            reader.addIIOReadWarningListener((source, message) -> warning.set(true));
            int frames = reader.getNumImages(true);
            if(frames < 1 || frames > MAX_FRAMES)
            {
               throw (new AttachmentException("the image has too many frames to read safely (at most " + MAX_FRAMES + ")."));
            }
            long pixels = 0;
            Size first = null;
            for(int frame = 0; frame < frames; frame++)
            {
               int width = reader.getWidth(frame);
               int height = reader.getHeight(frame);
               if(width <= 0 || height <= 0 || width > maxDimension || height > maxDimension)
               {
                  throw (new AttachmentException("the image is " + width + " x " + height + " pixels; the most is " + maxDimension
                     + " on either side. Resize it and attach it again."));
               }
               pixels += (long) width * height;
               if(pixels > MAX_DECODED_PIXELS)
               {
                  throw (new AttachmentException("the image has too many decoded pixels to read safely. Resize it and attach it again."));
               }
               BufferedImage decoded = reader.read(frame);
               if(decoded == null || warning.get())
               {
                  throw (new AttachmentException("the image could not be decoded completely (the file may be damaged)."));
               }
               decoded.flush();
               if(first == null)
               {
                  first = new Size(width, height);
               }
            }
            return (first);
         }
         finally
         {
            reader.dispose();
         }
      }
      catch(IOException | RuntimeException e)
      {
         throw (new AttachmentException("the image header or image data could not be read (the file may be damaged).", e));
      }
   }
}
