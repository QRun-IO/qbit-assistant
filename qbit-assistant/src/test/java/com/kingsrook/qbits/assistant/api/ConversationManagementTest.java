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

package com.kingsrook.qbits.assistant.api;


import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.kingsrook.qbits.assistant.metadata.AssistantAccessPermissionChecker;
import com.kingsrook.qbits.assistant.metadata.AssistantAdminPermissionChecker;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantConversation;
import com.kingsrook.qbits.assistant.model.AssistantFeedback;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.model.AssistantToolCall;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantTurnEvent;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.processes.ConversationActionsProcess;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionCheckResult;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.actions.processes.QProcessCallbackFactory;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.metadata.MetaDataInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.ADMIN;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.EDITOR;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.NO_ACCESS;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.OTHER_EDITOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Conversation management: the conversation table open to assistant users
 ** (owner-locked), its archive / restore / delete record actions, the table
 ** delete that leaves no child rows behind, and the UI API additions
 ** (running, read, conversationsTable).
 *******************************************************************************/
class ConversationManagementTest extends BaseAssistantTest
{
   private static final List<String> CHILD_TABLES = List.of(AssistantMessage.TABLE_NAME, AssistantTurn.TABLE_NAME, AssistantToolCall.TABLE_NAME,
      AssistantFeedback.TABLE_NAME, AssistantPendingAction.TABLE_NAME);



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void clearContext()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** Assistant users may use the conversation table (and its actions); the
    ** admin app, its nav and the other tables stay admin-only.
    *******************************************************************************/
   @Test
   void tableOpenToAssistantUsersAppStaysAdminOnly()
   {
      assertThat(qInstance.getTable(AssistantConversation.TABLE_NAME).getPermissionRules().getCustomPermissionChecker().getName())
         .isEqualTo(AssistantAccessPermissionChecker.class.getName());
      assertThat(qInstance.getTable(AssistantConversation.TABLE_NAME).getRecordSecurityLocks().get(0).getFieldName()).isEqualTo("userId");
      assertThat(qInstance.getTable(AssistantMessage.TABLE_NAME).getPermissionRules().getCustomPermissionChecker().getName())
         .isEqualTo(AssistantAdminPermissionChecker.class.getName());
      assertThat(qInstance.getApp(AssistantMetaDataBuilder.APP_NAME).getPermissionRules().getCustomPermissionChecker().getName())
         .isEqualTo(AssistantAdminPermissionChecker.class.getName());
      for(String name : List.of(ConversationActionsProcess.ARCHIVE, ConversationActionsProcess.RESTORE, ConversationActionsProcess.DELETE))
      {
         QProcessMetaData process = qInstance.getProcess(name);
         assertThat(process.getTableName()).isEqualTo(AssistantConversation.TABLE_NAME);
         assertThat(process.getPermissionRules().getCustomPermissionChecker().getName()).isEqualTo(AssistantAccessPermissionChecker.class.getName());
         assertThat(process.getPermissionRules().getDeniedClients()).contains("assistant");
      }
      assertThat(qInstance.getApp(AssistantMetaDataBuilder.APP_NAME).getChildren()).extracting(c -> c.getName())
         .doesNotContain(ConversationActionsProcess.ARCHIVE, ConversationActionsProcess.RESTORE, ConversationActionsProcess.DELETE);

      QContext.init(qInstance, AssistantTestFixture.ownerSession(EDITOR, config));
      for(TablePermissionSubType type : List.of(TablePermissionSubType.READ, TablePermissionSubType.EDIT, TablePermissionSubType.DELETE))
      {
         assertThat(PermissionsHelper.hasTablePermission(new QueryInput(AssistantConversation.TABLE_NAME), AssistantConversation.TABLE_NAME, type)).as(type.name()).isTrue();
      }
      assertThat(PermissionsHelper.hasTablePermission(new QueryInput(AssistantMessage.TABLE_NAME), AssistantMessage.TABLE_NAME, TablePermissionSubType.READ)).isFalse();
      assertThat(PermissionsHelper.getPermissionCheckResult(new MetaDataInput(), qInstance.getApp(AssistantMetaDataBuilder.APP_NAME))).isEqualTo(PermissionCheckResult.DENY_HIDE);
      assertThat(PermissionsHelper.hasProcessPermission(new MetaDataInput(), ConversationActionsProcess.DELETE)).isTrue();

      QContext.init(qInstance, AssistantTestFixture.ownerSession(NO_ACCESS, config));
      assertThat(PermissionsHelper.hasTablePermission(new QueryInput(AssistantConversation.TABLE_NAME), AssistantConversation.TABLE_NAME, TablePermissionSubType.READ)).isFalse();
      assertThat(PermissionsHelper.hasProcessPermission(new MetaDataInput(), ConversationActionsProcess.DELETE)).isFalse();
   }



   /*******************************************************************************
    ** User A cannot see, update or delete user B's conversation through the
    ** table actions; their own rows they can.
    *******************************************************************************/
   @Test
   void ownerLockOnTableActions() throws Exception
   {
      Integer mine   = conversation(EDITOR, "Mine");
      Integer theirs = conversation(OTHER_EDITOR, "Theirs");

      asUser(EDITOR);
      List<Serializable> visible = new QueryAction().execute(new QueryInput(AssistantConversation.TABLE_NAME).withInputSource(QInputSource.USER)).getRecords()
         .stream().map(r -> r.getValue("id")).toList();
      assertThat(visible).contains(mine).doesNotContain(theirs);

      UpdateOutput update = new UpdateAction().execute(new UpdateInput(AssistantConversation.TABLE_NAME).withInputSource(QInputSource.USER)
         .withRecord(new QRecord().withValue("id", theirs).withValue("title", "Hijacked")));
      assertThat(update.getRecords()).allSatisfy(r -> assertThat(r.getErrors()).isNotEmpty());

      assertThatDeleteFailsOrDoesNothing(theirs);

      asUser(ADMIN);
      AssistantConversation stillThere = store().getConversation(OTHER_EDITOR, theirs);
      assertThat(stillThere).isNotNull();
      assertThat(stillThere.getTitle()).isEqualTo("Theirs");

      ////////////////////////////////////////////////////////////////
      // own row: title / pinned / status change; the rest is ours //
      ////////////////////////////////////////////////////////////////
      asUser(EDITOR);
      UpdateOutput giveAway = new UpdateAction().execute(new UpdateInput(AssistantConversation.TABLE_NAME).withInputSource(QInputSource.USER)
         .withRecord(new QRecord().withValue("id", mine).withValue("userId", OTHER_EDITOR)));
      assertThat(giveAway.getRecords().get(0).getErrors()).as("the owner lock refuses moving a row to another user").isNotEmpty();
      new UpdateAction().execute(new UpdateInput(AssistantConversation.TABLE_NAME).withInputSource(QInputSource.USER)
         .withRecord(new QRecord().withValue("id", mine).withValue("title", "Renamed").withValue("status", "ARCHIVED")
            .withValue("unreadCount", 99).withValue("maxClassification", "NONE")));
      AssistantConversation updated = store().getConversation(EDITOR, mine);
      assertThat(updated.getTitle()).isEqualTo("Renamed");
      assertThat(updated.getStatus()).isEqualTo("ARCHIVED");
      assertThat(updated.getUnreadCount()).isNotEqualTo(99);
      assertThat(updated.getUserId()).isEqualTo(EDITOR);

      UpdateOutput bad = new UpdateAction().execute(new UpdateInput(AssistantConversation.TABLE_NAME).withInputSource(QInputSource.USER)
         .withRecord(new QRecord().withValue("id", mine).withValue("status", "DELETED")));
      assertThat(bad.getRecords().get(0).getErrors()).isNotEmpty();
      assertThat(store().getConversation(EDITOR, mine).getStatus()).isEqualTo("ARCHIVED");
   }



   /*******************************************************************************
    ** A table delete removes the conversation's child rows too; a conversation
    ** with an answer in progress is refused.
    *******************************************************************************/
   @Test
   void tableDeleteRemovesChildrenAndRefusesRunning() throws Exception
   {
      asUser(ADMIN);
      new DeleteAction().execute(new DeleteInput(AssistantTurnEvent.TABLE_NAME).withQueryFilter(new QQueryFilter()));
      Integer mine = conversation(EDITOR, "With children");
      children(EDITOR, mine);
      Integer other = conversation(EDITOR, "Sibling");
      children(EDITOR, other);

      asUser(EDITOR);
      DeleteOutput output = new DeleteAction().execute(new DeleteInput(AssistantConversation.TABLE_NAME).withInputSource(QInputSource.USER).withPrimaryKeys(List.of(mine)));
      assertThat(output.getRecordsWithErrors()).isNullOrEmpty();
      assertThat(store().getConversation(EDITOR, mine)).isNull();
      for(String table : CHILD_TABLES)
      {
         assertThat(rows(table, mine)).as(table).isEmpty();
         assertThat(rows(table, other)).as(table + " of another conversation").isNotEmpty();
      }
      asUser(ADMIN);
      assertThat(new QueryAction().execute(new QueryInput(AssistantTurnEvent.TABLE_NAME)).getRecords()).as("only the sibling's turn event is left").hasSize(1);

      Integer busy = conversation(EDITOR, "Busy");
      runningTurn(EDITOR, busy, Instant.now());
      asUser(EDITOR);
      DeleteOutput refused = new DeleteAction().execute(new DeleteInput(AssistantConversation.TABLE_NAME).withInputSource(QInputSource.USER).withPrimaryKeys(List.of(busy)));
      assertThat(refused.getRecordsWithErrors()).isNotEmpty();
      assertThat(store().getConversation(EDITOR, busy)).isNotNull();
   }



   /*******************************************************************************
    ** Archive, restore and delete as standard record actions, only on rows the
    ** user can see.
    *******************************************************************************/
   @Test
   void bulkRecordActions() throws Exception
   {
      Integer one    = conversation(EDITOR, "One");
      Integer two    = conversation(EDITOR, "Two");
      Integer busy   = conversation(EDITOR, "Busy");
      Integer theirs = conversation(OTHER_EDITOR, "Theirs");
      children(EDITOR, one);
      runningTurn(EDITOR, busy, Instant.now());

      asUser(EDITOR);
      runProcess(ConversationActionsProcess.ARCHIVE, List.of(one, two, theirs));
      assertThat(store().getConversation(EDITOR, one).getStatus()).isEqualTo("ARCHIVED");
      assertThat(store().getConversation(EDITOR, two).getStatus()).isEqualTo("ARCHIVED");

      runProcess(ConversationActionsProcess.RESTORE, List.of(two, theirs));
      assertThat(store().getConversation(EDITOR, two).getStatus()).isEqualTo("ACTIVE");
      assertThat(store().getConversation(EDITOR, one).getStatus()).isEqualTo("ARCHIVED");

      runProcess(ConversationActionsProcess.DELETE, List.of(one, busy, theirs));
      assertThat(store().getConversation(EDITOR, one)).isNull();
      assertThat(rows(AssistantMessage.TABLE_NAME, one)).isEmpty();
      assertThat(store().getConversation(EDITOR, busy)).as("an answer in progress is not deleted").isNotNull();

      asUser(ADMIN);
      AssistantConversation other = store().getConversation(OTHER_EDITOR, theirs);
      assertThat(other).isNotNull();
      assertThat(other.getStatus()).isEqualTo("ACTIVE");

      ////////////////////////////////////////////////////////
      // review and result summaries use the standard lines //
      ////////////////////////////////////////////////////////
      assertThat(ConversationActionsProcess.processes()).extracting(QProcessMetaData::getLabel)
         .containsExactly("Archive conversations", "Restore conversations", "Delete conversations");
   }



   /*******************************************************************************
    ** GET /conversations items carry running; an orphaned run is not running.
    *******************************************************************************/
   @Test
   void listShowsRunning() throws Exception
   {
      Integer live  = conversation(EDITOR, "Live");
      Integer stale = conversation(EDITOR, "Stale");
      Integer idle  = conversation(EDITOR, "Idle");
      runningTurn(EDITOR, live, Instant.now());
      runningTurn(EDITOR, stale, Instant.now().minusSeconds(3600));

      AssistantApi api = asUser(EDITOR);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> list = (List<Map<String, Object>>) api.listConversations(null).get("conversations");
      Map<Object, Object> running = list.stream().collect(Collectors.toMap(c -> c.get("id"), c -> c.get("running")));
      assertThat(running).containsEntry(live, true).containsEntry(stale, false).containsEntry(idle, false);
   }



   /*******************************************************************************
    ** PATCH {"read": true} clears the unread count; anything else is a 400.
    *******************************************************************************/
   @Test
   void patchRead() throws Exception
   {
      Integer id = conversation(EDITOR, "Unread");
      asUser(EDITOR);
      store().updateFields(AssistantConversation.TABLE_NAME, new QRecord().withValue("id", id).withValue("unreadCount", 3));

      AssistantApi api = asUser(EDITOR);
      assertThat(api.patchConversation(id, Map.of("read", true))).containsEntry("unreadCount", 0);
      assertThat(store().getConversation(EDITOR, id).getUnreadCount()).isZero();
      assertThatThrownBy(() -> api.patchConversation(id, Map.of("read", false)))
         .isInstanceOfSatisfying(AssistantApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
   }



   /*******************************************************************************
    ** GET /config names the conversation table only when the session may
    ** query it.
    *******************************************************************************/
   @Test
   void configNamesTheTable() throws Exception
   {
      assertThat(asUser(EDITOR).config()).containsEntry("conversationsTable", AssistantConversation.TABLE_NAME);
      assertThat(asUser(ADMIN).config()).containsEntry("conversationsTable", AssistantConversation.TABLE_NAME);
      assertThat(asUser(NO_ACCESS).config()).doesNotContainKey("conversationsTable");
   }



   /*******************************************************************************
    ** A delete of someone else's row either errors or deletes nothing.
    *******************************************************************************/
   private void assertThatDeleteFailsOrDoesNothing(Integer id)
   {
      try
      {
         DeleteOutput output = new DeleteAction().execute(new DeleteInput(AssistantConversation.TABLE_NAME).withInputSource(QInputSource.USER).withPrimaryKeys(List.of(id)));
         assertThat(output.getDeletedRecordCount()).isZero();
      }
      catch(QException e)
      {
         //////////////////////////////////
         // refused outright: also fine //
         //////////////////////////////////
      }
   }



   /*******************************************************************************
    ** Run a record action as the current user over the given ids.
    *******************************************************************************/
   private void runProcess(String process, List<Integer> ids) throws Exception
   {
      RunProcessInput input = new RunProcessInput();
      input.setProcessName(process);
      input.setCallback(QProcessCallbackFactory.forPrimaryKeys("id", ids));
      input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      new RunProcessAction().execute(input);
   }



   /*******************************************************************************
    ** Switch the context to a user's own session; returns an API bound to it.
    *******************************************************************************/
   private AssistantApi asUser(String userId)
   {
      QSession session = AssistantTestFixture.ownerSession(userId, config);
      QContext.init(qInstance, session);
      return (new AssistantApi(config.getRuntime(), session));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private AssistantStore store()
   {
      return (config.getRuntime().getStore());
   }



   /*******************************************************************************
    ** A conversation owned by a user (created as an admin).
    *******************************************************************************/
   private Integer conversation(String owner, String title) throws Exception
   {
      asUser(ADMIN);
      return (store().createConversation(owner, title, null).getId());
   }



   /*******************************************************************************
    ** One of each child row under a conversation.
    *******************************************************************************/
   private void children(String owner, Integer conversationId) throws Exception
   {
      asUser(ADMIN);
      AssistantMessage message = store().insert(new AssistantMessage().withConversationId(conversationId).withUserId(owner).withRole("USER").withStatus("COMPLETE").withSeq(1).withPartsJson("[]"));
      AssistantTurn turn = store().insert(new AssistantTurn().withConversationId(conversationId).withUserId(owner).withStatus("COMPLETE").withUserMessageId(message.getId()));
      store().insert(new AssistantTurnEvent().withTurnId(turn.getId()).withUserId(owner).withSeq(1).withEventType("message.end"));
      store().insert(new AssistantToolCall().withConversationId(conversationId).withTurnId(turn.getId()).withUserId(owner).withToolName("query"));
      store().insert(new AssistantFeedback().withConversationId(conversationId).withMessageId(message.getId()).withUserId(owner).withRating(1));
      store().insert(new AssistantPendingAction().withConversationId(conversationId).withTurnId(turn.getId()).withUserId(owner).withStatus(AssistantPendingAction.STATUS_PENDING));
   }



   /*******************************************************************************
    ** A RUNNING turn on another (possibly dead) pod with the given heartbeat.
    *******************************************************************************/
   private void runningTurn(String owner, Integer conversationId, Instant heartbeat) throws Exception
   {
      asUser(ADMIN);
      store().insert(new AssistantTurn().withConversationId(conversationId).withUserId(owner).withStatus("RUNNING")
         .withStartedAt(heartbeat).withHeartbeatAt(heartbeat).withLeaseOwner("another-pod").withPhase("thinking"));
   }



   /*******************************************************************************
    ** Rows of a child table for one conversation (any owner).
    *******************************************************************************/
   private List<QRecord> rows(String table, Integer conversationId) throws QException
   {
      QSession previous = QContext.getQSession();
      asUser(ADMIN);
      try
      {
         return (new QueryAction().execute(new QueryInput(table).withFilter(new QQueryFilter().withCriteria("conversationId", QCriteriaOperator.EQUALS, conversationId))).getRecords());
      }
      finally
      {
         QContext.init(qInstance, previous);
      }
   }
}
