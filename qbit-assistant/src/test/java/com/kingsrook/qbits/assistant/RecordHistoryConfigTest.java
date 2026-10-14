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


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.ClientChannelsMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Compatibility seam for the future core audit-read opt-in.
 *******************************************************************************/
public class RecordHistoryConfigTest
{
   /*******************************************************************************
    ** RC.6 changes exactly one pattern, preserving every other channel rule.
    *******************************************************************************/
   @Test
   void fallbackRemovesOnlyAuditPattern() throws Exception
   {
      ClientChannelsMetaData channels = ClientChannelsMetaData.defaults();
      channels.withDeniedPattern("assistant", "audit_private*").withDeniedPattern("next-ui", "audit*");
      channels.withDeniedObject("assistant", "auditDetail");
      List<String> expected = new ArrayList<>(channels.getDeniedPatterns().get("assistant"));
      expected.remove("audit*");
      enable(channels);
      enable(channels);
      assertThat(channels.getDeniedPatterns().get("assistant")).containsExactlyElementsOf(expected)
         .contains("worm_audit_*", "assistant_*", "audit_private*");
      assertThat(channels.getDeniedPatterns().get("next-ui")).contains("audit*");
      assertThat(channels.getDeniedObjects().get("assistant")).contains("auditDetail");
   }



   /*******************************************************************************
    ** Lookup uses the future method name and does not run the fallback afterward.
    *******************************************************************************/
   @Test
   void invokesFutureCoreMethod() throws Exception
   {
      FutureChannels channels = new FutureChannels();
      channels.withDeniedPattern("assistant", "audit*");
      enable(channels);
      assertThat(channels.called).isTrue();
      assertThat(channels.getDeniedPatterns().get("assistant")).contains("audit*");
   }



   /*******************************************************************************
    ** A broken future core method must fail startup rather than weaken the wall.
    *******************************************************************************/
   @Test
   void invocationFailureDoesNotFallBack()
   {
      FutureChannels channels = new FutureChannels();
      channels.fail = true;
      channels.withDeniedPattern("assistant", "audit*");
      assertThatThrownBy(() -> enable(channels)).cause().isInstanceOf(IllegalStateException.class);
      assertThat(channels.getDeniedPatterns().get("assistant")).contains("audit*");
   }



   /*******************************************************************************
    ** Exercise the one private compatibility helper without widening production API.
    *******************************************************************************/
   private void enable(ClientChannelsMetaData channels) throws Exception
   {
      Method method = AssistantQBitProducer.class.getDeclaredMethod("enableRecordHistory", ClientChannelsMetaData.class);
      method.setAccessible(true);
      method.invoke(new AssistantQBitProducer(), channels);
   }



   /*******************************************************************************
    ** A future core API stand-in; RC.6 metadata is not final.
    *******************************************************************************/
   public static class FutureChannels extends ClientChannelsMetaData
   {
      private Boolean called = false;
      private Boolean fail = false;



      /*******************************************************************************
       ** Record reflective dispatch; deliberately leave the pattern to detect fallback.
       *******************************************************************************/
      public ClientChannelsMetaData withAssistantAuditReads()
      {
         called = true;
         if(fail)
         {
            throw (new IllegalStateException("test failure"));
         }
         return (this);
      }
   }
}
