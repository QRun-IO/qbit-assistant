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

package com.kingsrook.qbits.assistant.provider.openai.demo;


import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.metadata.AssistantMetaDataBuilder;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.processes.DiscoverModelsProcess;
import com.kingsrook.qbits.assistant.processes.TestConnectionProcess;
import com.kingsrook.qbits.assistant.processes.TestModelConnectionProcess;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qbits.assistant.provider.openai.OpenAiCompatibleProviderType;
import com.kingsrook.qbits.assistant.spi.AssistantProviderType;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.EvalSupport;
import com.kingsrook.qbits.assistant.testing.GoldenTasks;
import com.kingsrook.qbits.assistant.testing.PresentationGoldenTask;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.actions.processes.QProcessCallbackFactory;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleCustomizerInterface;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;


/*******************************************************************************
 ** Memory-backed P-a screenshot demo. All discovery, tests and usage come from
 ** real application calls to a localhost mock; no external credentials are used.
 *******************************************************************************/
public class OpenAiDemoServer extends AbstractQQQApplication
{
   private static final String ADMIN = "admin-demo";
   private static final String MODEL = "qwen3:30b-a3b";
   private static final String IMAGE_QUESTION = "How many images do I have by type?";
   private static final ObjectMapper JSON = new ObjectMapper();
   private static QInstance instance;



   /*******************************************************************************
    ** Give browser sessions the same admin identity used for demo traffic.
    *******************************************************************************/
   public static class DemoSessionCustomizer implements QAuthenticationModuleCustomizerInterface
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void customizeSession(QInstance qInstance, QSession qSession, Map<String, Object> context)
      {
         QSession admin = AssistantTestFixture.sessionFor(ADMIN);
         qSession.setUser(admin.getUser());
         qSession.setPermissions(admin.getPermissions());
         qSession.withSecurityKeyValue(AssistantMetaDataBuilder.allAccessKeyName(AssistantQBitConfig.DEFAULT_OWNER_KEY_TYPE), true);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QInstance defineQInstance() throws QException
   {
      return (instance);
   }



   /*******************************************************************************
    ** Seed settings, execute the record actions and a turn, then serve Next UI.
    *******************************************************************************/
   public static void main(String[] args) throws Exception
   {
      int port = args.length > 0 ? Integer.parseInt(args[0]) : 8099;
      HttpServer mock = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      mock.createContext("/", OpenAiDemoServer::serveMock);
      mock.start();
      Runtime.getRuntime().addShutdownHook(new Thread(() -> mock.stop(0)));
      String baseUrl = "http://127.0.0.1:" + mock.getAddress().getPort() + "/v1";
      AssistantQBitConfig config = new AssistantQBitConfig()
         .withProviderName(ScriptedModelProvider.DEFAULT_NAME)
         .withModelProvider(new ScriptedModelProvider())
         .withProviderType(new OpenAiCompatibleProviderType())
         .withAllowedBaseUrls(Set.of(baseUrl))
         .withSecretResolver(name -> null)
         .withAllowedWidgetNames(Set.of());
      AssistantProviderType anthropic = optionalAnthropicType();
      if(anthropic != null)
      {
         config.withProviderType(anthropic);
      }
      instance = AssistantTestFixture.build(config);
      Integer providerId;
      Integer modelId;
      QContext.init(instance, AssistantTestFixture.sessionFor(ADMIN));
      try
      {
         AssistantStore store = config.getRuntime().getStore();
         AssistantProvider local = store.insert(new AssistantProvider().withName("Local GPU (Ollama)")
            .withProviderType("openai-compatible").withKind("OLLAMA").withLocation("LOCAL").withHipaaEligible(true)
            .withBaseUrl(baseUrl).withModel(MODEL).withEnabled(true).withIsDefault(true));
         providerId = local.getId();
         store.insert(new AssistantProvider().withName("OpenRouter").withProviderType("openai-compatible")
            .withKind("OPENROUTER").withLocation("CLOUD").withEnabled(false).withIsDefault(false));
         if(anthropic != null)
         {
            store.insert(new AssistantProvider().withName("Claude (production)").withProviderType(anthropic.getType())
               .withModel("claude-sonnet-5-5").withEnabled(false).withIsDefault(false));
         }

         runProcess(DiscoverModelsProcess.NAME, providerId);
         AssistantModel model = store.listModels().stream().filter(m -> providerId.equals(m.getProviderId()) && MODEL.equals(m.getModelId()))
            .findFirst().orElseThrow(() -> new QException("Demo model discovery failed."));
         modelId = model.getId();
         store.update(model.withInputUsdPerMTok(new BigDecimal("0.05")).withOutputUsdPerMTok(new BigDecimal("0.10")));
         runProcess(TestConnectionProcess.NAME, providerId);
         runProcess(TestModelConnectionProcess.NAME, modelId);
         if(!"OK".equals(store.getProvider(providerId).getLastTestStatus())
            || store.listModels().stream().noneMatch(m -> modelId.equals(m.getId()) && "OK".equals(m.getLastTestStatus())))
         {
            throw (new QException("Demo connection tests failed."));
         }
      }
      finally
      {
         QContext.clear();
      }
      TurnRunner.Outcome outcome = new TurnRunner(instance, config).run(ADMIN, "Say hello from the local demo provider.");
      if(outcome.errorCode() != null || outcome.text().isBlank())
      {
         throw (new QException("Demo assistant turn failed: " + outcome.errorCode()));
      }
      TurnRunner.Outcome presentation = new TurnRunner(instance, config).run(ADMIN, IMAGE_QUESTION);
      PresentationGoldenTask.check(presentation);
      instance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("mock").withType(QAuthenticationType.MOCK)
         .withCustomizer(new QCodeReference(DemoSessionCustomizer.class)));
      instance.setHomeAppName(AssistantMetaDataBuilder.APP_NAME);
      new QApplicationJavalinServer(new OpenAiDemoServer()).withPort(port).withServeFrontendNext(true).start();
      System.out.println("Assistant home: http://localhost:" + port + "/app/" + AssistantMetaDataBuilder.APP_NAME);
      System.out.println("Local provider: http://localhost:" + port + "/app/assistant_provider/" + providerId);
      System.out.println("Local model: http://localhost:" + port + "/app/assistant_model/" + modelId);
      System.out.println("Image presentation conversation id: " + presentation.conversationId());
   }



   /*******************************************************************************
    ** Reuse the Anthropic type only when that optional module is on the classpath.
    *******************************************************************************/
   private static AssistantProviderType optionalAnthropicType() throws ReflectiveOperationException
   {
      try
      {
         return ((AssistantProviderType) Class.forName("com.kingsrook.qbits.assistant.provider.anthropic.AnthropicProviderType")
            .getDeclaredConstructor().newInstance());
      }
      catch(ClassNotFoundException e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    ** Execute the same streamed ETL process as the record action, skipping UI.
    *******************************************************************************/
   private static void runProcess(String process, Integer id) throws QException
   {
      RunProcessInput input = new RunProcessInput();
      input.setProcessName(process);
      input.setCallback(QProcessCallbackFactory.forPrimaryKey("id", id));
      input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      new RunProcessAction().execute(input);
   }



   /*******************************************************************************
    ** Repeatable localhost endpoints for startup traffic and screenshot actions.
    ** Token counts are mock API reports, never measurements of GPU inference.
    *******************************************************************************/
   private static void serveMock(HttpExchange exchange) throws IOException
   {
      try(exchange)
      {
         byte[] request = exchange.getRequestBody().readAllBytes();
         String path = exchange.getRequestURI().getPath();
         String body;
         if("GET".equals(exchange.getRequestMethod()) && "/v1/models".equals(path))
         {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            body = """
               {"object":"list","data":[{"id":"qwen3:30b-a3b","object":"model","owned_by":"local"},
               {"id":"gpt-oss:20b","object":"model","owned_by":"local"},
               {"id":"qwen3-coder:30b","object":"model","owned_by":"local"},
               {"id":"bge-m3","object":"model","owned_by":"local"}]}
               """;
         }
         else if("POST".equals(exchange.getRequestMethod()) && "/v1/chat/completions".equals(path))
         {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            body = completion(JSON.readTree(request));
         }
         else
         {
            exchange.sendResponseHeaders(404, -1);
            return;
         }
         exchange.sendResponseHeaders(200, 0);
         exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
         exchange.getResponseBody().flush();
      }
   }



   /*******************************************************************************
    ** Follow the latest user turn, so connection tests and repeated chats are
    ** independent. Counts are read from the adapter's actual tool messages.
    *******************************************************************************/
   private static String completion(JsonNode request) throws IOException
   {
      String question = "";
      Map<String, Integer> counts = new LinkedHashMap<>();
      boolean presented = false;
      for(JsonNode message : request.path("messages"))
      {
         if("user".equals(message.path("role").asText()))
         {
            question = message.path("content").asText();
            counts.clear();
            presented = false;
         }
         else if("tool".equals(message.path("role").asText()) && IMAGE_QUESTION.equals(question))
         {
            String id = message.path("tool_call_id").asText();
            if(id.startsWith("count-image/"))
            {
               counts.put(id.substring("count-".length()), ((Number) EvalSupport.unwrap(message.path("content").asText()).get("count")).intValue());
            }
            else if("present-images".equals(id))
            {
               presented = true;
            }
         }
      }

      Map<String, Object> delta = Map.of("role", "assistant", "content", "Hello from the local demo provider.");
      String finish = "stop";
      if(IMAGE_QUESTION.equals(question) && !presented)
      {
         List<Map<String, Object>> calls = new ArrayList<>();
         if(counts.isEmpty())
         {
            for(String mime : List.of("image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml"))
            {
               calls.add(toolCall(calls.size(), "count-" + mime, "count_records", Map.of("table", "media", "filter",
                  GoldenTasks.filter("AND", GoldenTasks.criterion("mimeType", "EQUALS", mime)))));
            }
         }
         else
         {
            if(counts.size() != 5 || counts.get("image/webp") != 0 || counts.get("image/gif") != 0 || counts.get("image/svg+xml") != 0)
            {
               throw (new IOException("Unexpected demo image counts."));
            }
            Map<String, Object> input = PresentationGoldenTask.input(counts.get("image/jpeg"), counts.get("image/png"));
            input.put("actions", List.of(Map.of("label", "Show the PNG files", "prompt", "Show the PNG files")));
            calls.add(toolCall(0, "present-images", "present_results", input));
         }
         delta = Map.of("role", "assistant", "tool_calls", calls);
         finish = "tool_calls";
      }
      else if(presented)
      {
         delta = Map.of("role", "assistant", "content", "You can open either image type above.");
      }
      return ("data: " + JSON.writeValueAsString(Map.of("id", "demo-completion", "object", "chat.completion.chunk",
         "choices", List.of(Map.of("index", 0, "delta", delta)))) + "\n\n"
         + "data: " + JSON.writeValueAsString(Map.of("choices", List.of(Map.of("index", 0, "delta", Map.of(), "finish_reason", finish)))) + "\n\n"
         + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":24,\"completion_tokens\":8,\"total_tokens\":32}}\n\n"
         + "data: [DONE]\n\n");
   }



   /*******************************************************************************
    ** Encode tool arguments as a JSON string, as OpenAI streaming requires.
    *******************************************************************************/
   private static Map<String, Object> toolCall(Integer index, String id, String name, Map<String, Object> arguments) throws IOException
   {
      return (Map.of("index", index, "id", id, "type", "function", "function", Map.of("name", name, "arguments", JSON.writeValueAsString(arguments))));
   }
}
