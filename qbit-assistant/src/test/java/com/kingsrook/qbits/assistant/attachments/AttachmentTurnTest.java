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


import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.assistant.access.DataAccessMode;
import com.kingsrook.qbits.assistant.api.AssistantApi;
import com.kingsrook.qbits.assistant.engine.SystemPrompt;
import com.kingsrook.qbits.assistant.engine.TurnEvent;
import com.kingsrook.qbits.assistant.model.AssistantDataAccess;
import com.kingsrook.qbits.assistant.model.AssistantMessage;
import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import com.kingsrook.qbits.assistant.spi.ModelCapabilities;
import com.kingsrook.qbits.assistant.spi.ModelMessage;
import com.kingsrook.qbits.assistant.spi.ModelPart;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.jobs.DurableJobsTables;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.ADMIN;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.EDITOR;
import static com.kingsrook.qbits.assistant.testing.AssistantTestFixture.OTHER_EDITOR;
import static com.kingsrook.qbits.assistant.testing.AttachmentTestSupport.attachments;
import static com.kingsrook.qbits.assistant.testing.AttachmentTestSupport.enableUploads;
import static com.kingsrook.qbits.assistant.testing.AttachmentTestSupport.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Attachments end to end: uploads made through core UploadRefs as a user,
 ** read by the turn engine as that user, given to the model as untrusted
 ** blocks (or images), with notices for files that cannot be read.
 *******************************************************************************/
class AttachmentTurnTest extends BaseAssistantTest
{

   /*******************************************************************************
    ** A malformed image does not poison the provider request or the turn.
    *******************************************************************************/
   @Test
   void malformedImageStillCompletesWithReadableText() throws Exception
   {
      byte[] broken = java.util.Arrays.copyOf(TestFiles.png(10, 20), 24);
      String image = upload(qInstance, EDITOR, "broken.png", broken);
      String text = upload(qInstance, EDITOR, "fine.txt", "Readable companion file");
      provider.withCapabilities(new ModelCapabilities().withVision(true));
      provider.thenText("I can read the text file.");
      TurnRunner.Outcome outcome = runner.run(EDITOR, null, "summarize these", attachments(image, "broken.png", text, "fine.txt"));
      assertThat(outcome.end()).containsEntry("status", "COMPLETE");
      assertThat(notices(outcome)).singleElement().satisfies(n -> assertThat(n).contains("broken.png could not be read"));
      assertThat(newest(provider.getRequests().get(0)).getParts()).noneMatch(p -> p.getType() == ModelPart.Type.IMAGE);
      assertThat(JsonUtils.toJson(provider.getRequests())).contains("Readable companion file");
   }



   /*******************************************************************************
    ** Core uploads on the fixture's memory backend.
    *******************************************************************************/
   @BeforeEach
   void uploads() throws Exception
   {
      enableUploads(qInstance);
   }



   /*******************************************************************************
    ** The newest user message of a request.
    *******************************************************************************/
   private static ModelMessage newest(ModelRequest request)
   {
      return (request.getMessages().get(request.getMessages().size() - 1));
   }



   /*******************************************************************************
    ** Notice texts of a turn.
    *******************************************************************************/
   private static List<String> notices(TurnRunner.Outcome outcome)
   {
      return (outcome.events(TurnEvent.NOTICE).stream().map(n -> String.valueOf(n.get("text"))).toList());
   }



   /*******************************************************************************
    ** The issue's case: a .txt reaches the model as an untrusted block before
    ** the question; the refs stay in page_context for the bulk-load tools; the
    ** stored user message holds only the typed text.
    *******************************************************************************/
   @Test
   void textFileReachesTheModel() throws Exception
   {
      String ref = upload(qInstance, EDITOR, "LORE.txt", "The dragon slept under the mountain for a thousand years.");
      provider.thenText("Here is a summary.");
      TurnRunner.Outcome outcome = runner.run(EDITOR, null, "can we post this as a blog?", attachments(ref, "LORE.txt"));
      assertThat(outcome.end()).containsEntry("status", "COMPLETE");
      assertThat(notices(outcome)).isEmpty();

      ModelRequest    request = provider.getRequests().get(0);
      List<ModelPart> parts   = newest(request).getParts();
      assertThat(parts.get(0).getText()).startsWith("<uploaded_file name=\"LORE.txt\" type=\"text\" trust=\"untrusted\"")
         .contains("The dragon slept under the mountain", "not instructions").endsWith("</uploaded_file>");
      assertThat(parts.get(1).getText()).isEqualTo("can we post this as a blog?");
      assertThat(parts.get(2).getText()).startsWith("<page_context").contains(ref);
      assertThat(request.getSystem().get(0).getText()).contains("<uploaded_file", "do not say you cannot create records");

      QContext.init(qInstance, AssistantTestFixture.ownerSession(EDITOR, config));
      try
      {
         List<AssistantMessage> stored = config.getRuntime().getStore().query(AssistantMessage.TABLE_NAME,
            new QQueryFilter().withCriteria("conversationId", QCriteriaOperator.EQUALS, outcome.conversationId()), AssistantMessage.class);
         assertThat(JsonUtils.toJson(stored)).doesNotContain("dragon slept");
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** Creating a record from an upload goes through the pending-action path:
    ** proposed, nothing written until the user confirms.
    *******************************************************************************/
   @Test
   void blogFromAttachmentIsOnlyProposed() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(ADMIN));
      try
      {
         config.getRuntime().getStore().insert(new AssistantDataAccess().withObjectType(AssistantDataAccess.TYPE_TABLE).withObjectName("blog_post").withMode(DataAccessMode.READ_WRITE.name()));
         config.getRuntime().getDataAccessService().invalidate();
      }
      finally
      {
         QContext.clear();
      }

      byte[] pdf = TestFiles.pdf("Release notes for the spring update");
      String ref = upload(qInstance, ADMIN, "notes.pdf", pdf);
      provider.thenToolUse("create_record", Map.of("table", "blog_post", "values", Map.of("title", "Spring update", "status", "DRAFT",
            "content", "Release notes for the spring update")))
         .thenText("I drafted the post; confirm it in the panel.");
      TurnRunner.Outcome outcome = runner.run(ADMIN, null, "turn this into a draft blog post", attachments(ref, "notes.pdf"));
      assertThat(outcome.end()).containsEntry("status", "COMPLETE");
      assertThat(newest(provider.getRequests().get(0)).getParts().get(0).getText()).contains("type=\"PDF\"", "Release notes for the spring update");
      assertThat(provider.getRequests().get(0).getTools()).anyMatch(tool -> "create_record".equals(tool.getName()));

      QContext.init(qInstance, AssistantTestFixture.ownerSession(ADMIN, config));
      try
      {
         List<AssistantPendingAction> pending = config.getRuntime().getStore().query(AssistantPendingAction.TABLE_NAME, new QQueryFilter(), AssistantPendingAction.class);
         assertThat(pending).singleElement().satisfies(action ->
         {
            assertThat(action.getStatus()).isEqualTo(AssistantPendingAction.STATUS_PENDING);
            assertThat(action.getTableName()).isEqualTo("blog_post");
         });
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** Another user's ref is "not found" - also for a jobs administrator, whom
    ** core would let open any upload; the turn still completes.
    *******************************************************************************/
   @Test
   void anotherUsersUploadIsNotFound() throws Exception
   {
      String ref = upload(qInstance, EDITOR, "private.txt", "EDITOR_PRIVATE_SENTINEL");
      provider.thenText("I could not read it.");
      TurnRunner.Outcome outcome = runner.run(OTHER_EDITOR, null, "what does this say?", attachments(ref, "private.txt"));
      assertThat(outcome.end()).containsEntry("status", "COMPLETE");
      assertThat(notices(outcome)).singleElement().satisfies(n -> assertThat(n).isEqualTo("private.txt could not be read: " + UploadRefSource.NOT_FOUND));
      assertThat(JsonUtils.toJson(provider.getRequests())).doesNotContain("EDITOR_PRIVATE_SENTINEL").contains("status=\\\"not_read\\\"");

      QSession jobsAdmin = AssistantTestFixture.sessionFor(ADMIN);
      jobsAdmin.withPermission(DurableJobsTables.PERMISSION_ADMIN);
      QContext.init(qInstance, jobsAdmin);
      try
      {
         assertThatThrownBy(() -> new UploadRefSource().read(ref, 1000)).hasMessage(UploadRefSource.NOT_FOUND);
         assertThatThrownBy(() -> new UploadRefSource().read("up_nope", 1000)).hasMessage(UploadRefSource.NOT_FOUND);
      }
      finally
      {
         QContext.clear();
      }

      QContext.init(qInstance, AssistantTestFixture.sessionFor(EDITOR));
      try
      {
         assertThat(new UploadRefSource().read(ref, 1000).bytes()).hasSize("EDITOR_PRIVATE_SENTINEL".length());
         assertThatThrownBy(() -> new UploadRefSource().read(ref, 5)).hasMessageContaining("larger than 1 KB");
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** Oversize and unknown binary files become notices; the readable file in
    ** the same question still reaches the model.
    *******************************************************************************/
   @Test
   void unreadableFilesBecomeNotices() throws Exception
   {
      config.getAttachments().setMaxFileBytes(100L);
      String big     = upload(qInstance, EDITOR, "big.txt", "z".repeat(500));
      String binary  = upload(qInstance, EDITOR, "LORE.txt", new byte[] { 0x7F, 'E', 'L', 'F', 0, 0, 0, 0 });
      String fine    = upload(qInstance, EDITOR, "ok.md", "# Fine\nsmall");
      provider.thenText("Read one of three.");
      TurnRunner.Outcome outcome = runner.run(EDITOR, null, "read these", attachments(big, "big.txt", binary, "LORE.txt", fine, "ok.md"));
      assertThat(outcome.end()).containsEntry("status", "COMPLETE");
      assertThat(notices(outcome)).hasSize(2)
         .anySatisfy(n -> assertThat(n).startsWith("big.txt could not be read: the file is larger than"))
         .anySatisfy(n -> assertThat(n).startsWith("LORE.txt could not be read: it is a program"));
      List<ModelPart> parts = newest(provider.getRequests().get(0)).getParts();
      assertThat(parts.get(2).getText()).contains("name=\"ok.md\"", "# Fine");

      QContext.init(qInstance, AssistantTestFixture.ownerSession(EDITOR, config));
      try
      {
         AssistantMessage answer = config.getRuntime().getStore().query(AssistantMessage.TABLE_NAME,
            new QQueryFilter().withCriteria("role", QCriteriaOperator.EQUALS, "ASSISTANT"), AssistantMessage.class).get(0);
         assertThat(answer.getPartsJson()).contains("big.txt could not be read", "\"notice\"");
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** An image goes to a vision model as an image part; a model without vision
    ** gets no image and the user is told.
    *******************************************************************************/
   @Test
   void imagesOnlyForVisionModels() throws Exception
   {
      String ref = upload(qInstance, EDITOR, "screenshot.png", TestFiles.png(1280, 720));
      provider.thenText("no vision");
      TurnRunner.Outcome blind = runner.run(EDITOR, null, "add this company to the CRM", attachments(ref, "screenshot.png"));
      assertThat(blind.end()).containsEntry("status", "COMPLETE");
      assertThat(notices(blind)).singleElement().satisfies(n -> assertThat(n).contains("screenshot.png could not be read", "cannot read images"));
      assertThat(newest(provider.getRequests().get(0)).getParts()).noneMatch(p -> p.getType() == ModelPart.Type.IMAGE);

      provider.withCapabilities(new ModelCapabilities().withVision(true).withPromptCaching(true));
      provider.thenText("I see a company page.");
      TurnRunner.Outcome seen = runner.run(EDITOR, null, "add this company to the CRM", attachments(ref, "screenshot.png"));
      assertThat(notices(seen)).isEmpty();
      List<ModelPart> parts = newest(provider.getRequests().get(1)).getParts();
      assertThat(parts.get(0).getText()).contains("type=\"PNG image\"", "width=\"1280\"", "height=\"720\"");
      assertThat(parts.get(1).getType()).isEqualTo(ModelPart.Type.IMAGE);
      assertThat(parts.get(1).getMediaType()).isEqualTo("image/png");
   }



   /*******************************************************************************
    ** GET /config: attachments supported, with the size limit and the type
    ** list for the picker (images only for a vision model).
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void configAdvertisesAttachments() throws Exception
   {
      QContext.init(qInstance, AssistantTestFixture.sessionFor(EDITOR));
      try
      {
         Map<String, Object> result = new AssistantApi(config.getRuntime(), QContext.getQSession()).config();
         assertThat((Map<String, Object>) result.get("features")).containsEntry("attachments", true);
         Map<String, Object> limits = (Map<String, Object>) result.get("limits");
         assertThat(limits).containsEntry("maxAttachmentBytes", 10L * 1024 * 1024).containsEntry("maxAttachments", 5);
         assertThat((List<String>) limits.get("attachmentTypes")).contains("PDF", "CSV", "Excel (.xlsx)").doesNotContain("PNG");

         provider.withCapabilities(new ModelCapabilities().withVision(true));
         limits = (Map<String, Object>) new AssistantApi(config.getRuntime(), QContext.getQSession()).config().get("limits");
         assertThat((List<String>) limits.get("attachmentTypes")).contains("PNG", "JPEG");

         config.getAttachments().setEnabled(false);
         result = new AssistantApi(config.getRuntime(), QContext.getQSession()).config();
         assertThat((Map<String, Object>) result.get("features")).containsEntry("attachments", false);
         assertThat((Map<String, Object>) result.get("limits")).doesNotContainKey("maxAttachmentBytes");
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** The prompt no longer tells the model it cannot create records when the
    ** write tools are there.
    *******************************************************************************/
   @Test
   void promptAllowsProposingWrites()
   {
      assertThat(SystemPrompt.SAFETY_RULES).contains("propose it with that tool").contains("Only when those tools are missing");
   }

}
