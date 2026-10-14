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

package com.kingsrook.qbits.assistant.tools;


import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantPolicy;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.preferences.UserPreferencesAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Write mode (owner, 2026-10-13): Read-only by default; the write tools exist
 ** only in Write mode; the policy can turn write mode off or limit it to a
 ** role; the user's preference sets the default; auto-confirm only where the
 ** policy allows it, on tables marked low-risk, never for deletes.
 *******************************************************************************/
class WriteModeTest extends BaseAssistantTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void clear()
   {
      QContext.clear();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void save(String table, Map<String, Serializable> values) throws QException
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      try
      {
         QRecord record = new QRecord();
         record.setValues(new LinkedHashMap<>(values));
         QRecord saved = new InsertAction().execute(new InsertInput(table).withRecord(record)).getRecords().get(0);
         assertThat(saved.getErrors()).isEmpty();
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private AssistantApi api(String user)
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(user));
      return (new AssistantApi(config.getRuntime(), QContext.getQSession()));
   }



   /*******************************************************************************
    ** Read-only by default: no write tools in the request; Write mode adds
    ** them; the conversation remembers its mode.
    *******************************************************************************/
   @Test
   void testModeControlsTheToolList() throws Exception
   {
      save(AssistantDataAccess.TABLE_NAME, Map.of("objectType", "TABLE", "objectName", "page", "mode", "READ_WRITE"));

      AssistantApi        admin        = api(AssistantTestFixture.ADMIN);
      Map<String, Object> conversation = admin.createConversation(new LinkedHashMap<>());
      assertThat(conversation.get("writeMode")).isEqualTo(false);
      Integer id = (Integer) conversation.get("id");
      QContext.clear();

      provider.thenText("ok").thenText("ok");
      runner.run(AssistantTestFixture.ADMIN, id, "read only please", null);

      QContext.init(qInstance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
      assertThat(new AssistantApi(config.getRuntime(), QContext.getQSession()).patchConversation(id, Map.of("writeMode", true)).get("writeMode")).isEqualTo(true);
      QContext.clear();
      runner.run(AssistantTestFixture.ADMIN, id, "now write", null);

      List<ModelRequest> requests = provider.getRequests();
      assertThat(requests.get(0).getTools()).extracting(t -> t.getName()).doesNotContain(UiApiToolbox.CREATE_RECORD, UiApiToolbox.UPDATE_RECORD, UiApiToolbox.DELETE_RECORD);
      assertThat(requests.get(1).getTools()).extracting(t -> t.getName()).contains(UiApiToolbox.CREATE_RECORD, UiApiToolbox.UPDATE_RECORD, UiApiToolbox.DELETE_RECORD);
   }



   /*******************************************************************************
    ** A write tool called in a Read-only conversation is refused before any
    ** request (the model cannot get around the tool list).
    *******************************************************************************/
   @Test
   void testReadOnlyRefusesAWriteCall() throws Exception
   {
      save(AssistantDataAccess.TABLE_NAME, Map.of("objectType", "TABLE", "objectName", "page", "mode", "READ_WRITE"));
      provider.thenToolUse(UiApiToolbox.UPDATE_RECORD, Map.of("table", "page", "id", "12", "values", Map.of("title", "Hacked"))).thenText("ok");
      var outcome = runner.run(AssistantTestFixture.ADMIN, "change page 12");
      assertThat(outcome.toolCalls()).extracting(AssistantToolCall::getOutcome).containsExactly("INVALID_INPUT");
   }



   /*******************************************************************************
    ** The policy: write mode off for everyone, or only for a role; then 403.
    *******************************************************************************/
   @Test
   void testPolicyLimitsWriteMode() throws Exception
   {
      save(AssistantDataAccess.TABLE_NAME, Map.of("objectType", "TABLE", "objectName", "page", "mode", "READ_WRITE"));
      AssistantApi editor = api(AssistantTestFixture.EDITOR);
      Integer      id     = (Integer) editor.createConversation(new LinkedHashMap<>()).get("id");
      assertThat(editor.patchConversation(id, Map.of("writeMode", true)).get("writeMode")).isEqualTo(true);
      QContext.clear();

      save(AssistantPolicy.TABLE_NAME, Map.of("name", "default", "writeModePermission", "assistant.write"));
      config.getRuntime().getPolicyService().invalidate();
      AssistantApi again = api(AssistantTestFixture.EDITOR);
      assertThatThrownBy(() -> again.patchConversation(id, Map.of("writeMode", true))).hasMessageContaining("Write mode is not available");
      assertThat(again.config()).extracting(c -> ((Map<?, ?>) c.get("features")).get("writeMode")).isEqualTo(false);
   }



   /*******************************************************************************
    ** No READ_WRITE table: write mode is not available at all.
    *******************************************************************************/
   @Test
   void testNoWritableTableNoWriteMode() throws Exception
   {
      AssistantApi admin = api(AssistantTestFixture.ADMIN);
      Integer      id    = (Integer) admin.createConversation(new LinkedHashMap<>()).get("id");
      assertThatThrownBy(() -> admin.patchConversation(id, Map.of("writeMode", true))).hasMessageContaining("Write mode is not available");
   }



   /*******************************************************************************
    ** Preferences: the default mode applies to new conversations; auto-confirm
    ** is refused unless the policy allows it, and then runs a low-risk insert at
    ** once but never a delete.
    *******************************************************************************/
   @Test
   void testPreferencesAndAutoConfirm() throws Exception
   {
      save(AssistantDataAccess.TABLE_NAME, Map.of("objectType", "TABLE", "objectName", "page", "mode", "READ_WRITE", "autoConfirmOk", true));

      AssistantApi admin = api(AssistantTestFixture.ADMIN);
      new UserPreferencesAction().patchSection("assistant", Map.of("values", Map.of("defaultWriteMode", true)));
      assertThat(admin.createConversation(new LinkedHashMap<>()).get("writeMode")).isEqualTo(true);
      assertThatThrownBy(() -> new UserPreferencesAction().patchSection("assistant", Map.of("values", Map.of("autoConfirmLowRisk", true)))).hasMessageContaining("can't be changed");
      QContext.clear();

      save(AssistantPolicy.TABLE_NAME, Map.of("name", "default", "autoConfirmAllowed", true));
      config.getRuntime().getPolicyService().invalidate();
      AssistantApi allowed = api(AssistantTestFixture.ADMIN);
      assertThat(new UserPreferencesAction().patchSection("assistant", Map.of("values", Map.of("autoConfirmLowRisk", true))).getValues().getValues().get("autoConfirmLowRisk")).isEqualTo(true);
      QContext.clear();

      provider.thenToolUses("Saving.", List.of(
            new Object[] {UiApiToolbox.CREATE_RECORD, Map.of("table", "page", "values", Map.of("title", "Auto"))},
            new Object[] {UiApiToolbox.DELETE_RECORD, Map.of("table", "page", "id", "12")}))
         .thenText("done");
      var outcome = runner.run(AssistantTestFixture.ADMIN, "make a page and delete page 12");
      AssistantToolCall create = outcome.toolCalls().stream().filter(c -> UiApiToolbox.CREATE_RECORD.equals(c.getToolName())).findFirst().orElseThrow();
      AssistantToolCall delete = outcome.toolCalls().stream().filter(c -> UiApiToolbox.DELETE_RECORD.equals(c.getToolName())).findFirst().orElseThrow();
      assertThat(create.getHttpStatus()).as("auto-confirmed: ran through the UI API").isBetween(200, 299);
      assertThat(create.getPendingActionId()).isNull();
      assertThat(delete.getHttpStatus()).as("a delete is never auto-confirmed").isNull();
      assertThat(delete.getPendingActionId()).isNotNull();
   }

}
