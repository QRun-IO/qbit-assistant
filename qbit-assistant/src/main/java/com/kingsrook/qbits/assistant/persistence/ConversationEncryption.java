/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.assistant.persistence;


import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;


/*******************************************************************************
 ** Server-only AES-256-GCM codec used exclusively by AssistantStore. No key or value enters diagnostics.
 *******************************************************************************/
final class ConversationEncryption
{
   private static final String PREFIX = "enc:v1:";
   private static final SecureRandom RANDOM = new SecureRandom();
   private final SecretKeySpec key;



   /*******************************************************************************
    ** Resolve and validate once at runtime initialization, never retaining the environment value.
    *******************************************************************************/
   ConversationEncryption(AssistantQBitConfig config)
   {
      String name = config.getConversationEncryptionKeyEnvName();
      if(name == null || name.isBlank())
      {
         if(Boolean.TRUE.equals(config.getHipaaMode()))
         {
            throw (new IllegalArgumentException("HIPAA mode requires conversationEncryptionKeyEnvName with a base64 32-byte encryption key."));
         }
         key = null;
         return;
      }
      if(!AssistantQBitConfig.ENV_NAME.matcher(name).matches())
      {
         throw (new IllegalArgumentException("Conversation encryption key reference must be an environment variable NAME (capital letters, digits and _)."));
      }
      byte[] decoded;
      try
      {
         String encoded = config.getSecretResolver().apply(name);
         decoded = Base64.getDecoder().decode(encoded == null ? "" : encoded);
      }
      catch(RuntimeException e)
      {
         throw (new IllegalArgumentException("Conversation encryption requires a base64 32-byte key in the configured environment variable."));
      }
      try
      {
         if(decoded.length != 32)
         {
            throw (new IllegalArgumentException("Conversation encryption key must decode to exactly 32 bytes (AES-256)."));
         }
         key = new SecretKeySpec(decoded, "AES");
      }
      finally
      {
         Arrays.fill(decoded, (byte) 0);
      }
   }



   /*******************************************************************************
    ** Whether a stored value is in this codec's encrypted form.
    *******************************************************************************/
   public static boolean isEncrypted(String value)
   {
      return (value != null && value.startsWith(PREFIX));
   }



   /*******************************************************************************
    ** Always encrypt plaintext, even strings that happen to start with enc:v1:.
    *******************************************************************************/
   String encrypt(String value)
   {
      if(value == null || key == null)
      {
         return (value);
      }
      try
      {
         byte[] iv = new byte[12];
         RANDOM.nextBytes(iv);
         Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
         cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
         byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
         return (PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array()));
      }
      catch(GeneralSecurityException e)
      {
         throw (new IllegalStateException("Conversation storage encryption failed."));
      }
   }



   /*******************************************************************************
    ** Legacy plaintext remains readable; corrupt, unknown-version or keyless ciphertext fails closed.
    *******************************************************************************/
   String decrypt(String value)
   {
      if(value == null || !value.startsWith("enc:"))
      {
         return (value);
      }
      if(key == null || !value.startsWith(PREFIX))
      {
         throw (new IllegalStateException("Conversation storage decryption failed: encryption key or supported version is unavailable."));
      }
      try
      {
         byte[] encoded = Base64.getDecoder().decode(value.substring(PREFIX.length()));
         if(encoded.length < 28)
         {
            throw (new IllegalArgumentException());
         }
         Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
         cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOfRange(encoded, 0, 12)));
         return (new String(cipher.doFinal(encoded, 12, encoded.length - 12), StandardCharsets.UTF_8));
      }
      catch(GeneralSecurityException | IllegalArgumentException e)
      {
         throw (new IllegalStateException("Conversation storage decryption failed: ciphertext authentication failed."));
      }
   }
}
