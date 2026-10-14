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

package com.kingsrook.qbits.assistant.provider.bedrock;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import com.kingsrook.qbits.assistant.testing.CloudProviderTestServer;


/*******************************************************************************
 ** Test-side AWS event-stream encoder: prelude, typed headers and both CRC32s.
 *******************************************************************************/
final class BedrockEventStream
{
   /*******************************************************************************
    ** Each chunk carries base64 Anthropic event JSON, not an SSE envelope.
    *******************************************************************************/
   static byte[] encode(List<String> events) throws IOException
   {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      for(String event : events)
      {
         byte[] payload = CloudProviderTestServer.JSON.writeValueAsBytes(Map.of("bytes", Base64.getEncoder().encodeToString(event.getBytes(StandardCharsets.UTF_8))));
         ByteArrayOutputStream headers = new ByteArrayOutputStream();
         DataOutputStream headerData = new DataOutputStream(headers);
         header(headerData, ":message-type", "event");
         header(headerData, ":event-type", "chunk");
         header(headerData, ":content-type", "application/json");
         ByteArrayOutputStream frame = new ByteArrayOutputStream();
         DataOutputStream data = new DataOutputStream(frame);
         data.writeInt(16 + headers.size() + payload.length);
         data.writeInt(headers.size());
         data.writeInt(crc(frame.toByteArray()));
         data.write(headers.toByteArray());
         data.write(payload);
         data.writeInt(crc(frame.toByteArray()));
         stream.write(frame.toByteArray());
      }
      return (stream.toByteArray());
   }



   /*******************************************************************************
    ** AWS header type 7 is a UTF-8 string with a two-byte length.
    *******************************************************************************/
   private static void header(DataOutputStream data, String name, String value) throws IOException
   {
      byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
      byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
      data.writeByte(nameBytes.length);
      data.write(nameBytes);
      data.writeByte(7);
      data.writeShort(valueBytes.length);
      data.write(valueBytes);
   }



   /*******************************************************************************
    ** Network-order CRC32 for the prelude and complete message respectively.
    *******************************************************************************/
   private static int crc(byte[] bytes)
   {
      CRC32 crc = new CRC32();
      crc.update(bytes);
      return ((int) crc.getValue());
   }
}
