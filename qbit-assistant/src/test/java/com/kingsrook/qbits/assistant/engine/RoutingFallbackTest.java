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

package com.kingsrook.qbits.assistant.engine;


import com.kingsrook.qbits.assistant.model.AssistantPendingAction;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantRuntime;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qbits.assistant.home.AssistantHomeData;
import com.kingsrook.qbits.assistant.home.AssistantHomeView;
import com.kingsrook.qbits.assistant.model.AssistantBudget;
import com.kingsrook.qbits.assistant.model.AssistantModel;
import com.kingsrook.qbits.assistant.model.AssistantProfile;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.model.AssistantTurn;
import com.kingsrook.qbits.assistant.model.AssistantUsage;
import com.kingsrook.qbits.assistant.persistence.AssistantStore;
import com.kingsrook.qbits.assistant.provider.AssistantModelProvider;
import com.kingsrook.qbits.assistant.provider.AssistantProviderType;
import com.kingsrook.qbits.assistant.provider.CompletableStreamHandle;
import com.kingsrook.qbits.assistant.provider.HealthResult;
import com.kingsrook.qbits.assistant.provider.ModelEventListener;
import com.kingsrook.qbits.assistant.provider.ModelPart;
import com.kingsrook.qbits.assistant.provider.ModelProviderException;
import com.kingsrook.qbits.assistant.provider.ModelRequest;
import com.kingsrook.qbits.assistant.provider.ModelResponse;
import com.kingsrook.qbits.assistant.provider.ProviderSettings;
import com.kingsrook.qbits.assistant.provider.StreamHandle;
import com.kingsrook.qbits.assistant.provider.fake.ScriptedModelProvider;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qbits.assistant.testing.BaseAssistantTest;
import com.kingsrook.qbits.assistant.testing.TurnRunner;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Multi-provider dispatch through the fixture with persisted rows and fixed time.
 *******************************************************************************/
class RoutingFallbackTest extends BaseAssistantTest
{
   private static final String USER = "editor-routing";
   private static final Instant NOW = Instant.now();
   private final Map<String, ScriptedModelProvider> scripts = new LinkedHashMap<>();
   private Integer probes = 0;
   private Boolean healthy = true;



   /*******************************************************************************
    ** Keyless adapter with distinct scripts for each endpoint.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AssistantQBitConfig value)
   {
      value.withProviderType(new RoutingType()).withAllowedBaseUrls(Set.of("https://a.invalid", "https://b.invalid", "https://c.invalid"));
   }



   /*******************************************************************************
    ** Fix time for budgets, routing and health.
    *******************************************************************************/
   @BeforeEach
   void fixedClock()
   {
      config.getRuntime().close();
      config.setRuntime(new AssistantRuntime(qInstance, config, Clock.fixed(NOW, ZoneOffset.UTC)));
   }



   /*******************************************************************************
    ** One adapter family with separate recording instances.
    *******************************************************************************/
   class RoutingType implements AssistantProviderType
   {
      /*******************************************************************************
       ** Type identifier.
       *******************************************************************************/
      public String getType()
      {
         return ("routing-test");
      }



      /*******************************************************************************
       ** Display label.
       *******************************************************************************/
      public String getLabel()
      {
         return ("Routing test");
      }



      /*******************************************************************************
       ** Registry supplies actual models.
       *******************************************************************************/
      public List<String> getModels()
      {
         return (List.of("a", "b", "c"));
      }



      /*******************************************************************************
       ** No secret needed.
       *******************************************************************************/
      public String getDefaultApiKeyEnvName()
      {
         return (null);
      }



      /*******************************************************************************
       ** No network traffic.
       *******************************************************************************/
      public String getDefaultBaseUrl()
      {
         return ("https://a.invalid");
      }



      /*******************************************************************************
       ** Resolve the endpoint's own script.
       *******************************************************************************/
      public AssistantModelProvider create(String key, String url)
      {
         return (scripts.get(url == null ? "https://a.invalid" : url));
      }



      /*******************************************************************************
       ** Count probes and assert the transport timeout.
       *******************************************************************************/
      public HealthResult checkHealth(ProviderSettings settings)
      {
         probes++;
         assertThat(settings.getRequestTimeoutSeconds()).isEqualTo(2);
         return (new HealthResult(healthy, 1L, "Test probe"));
      }
   }



   /*******************************************************************************
    ** Admin setup; actual turns use the editor session.
    *******************************************************************************/
   private AssistantStore store()
   {
      QContext.init(qInstance, AssistantTestFixture.ownerSession("admin-routing", config));
      return (config.getRuntime().getStore());
   }



   /*******************************************************************************
    ** One enabled registry model and endpoint.
    *******************************************************************************/
   private AssistantModel model(String name, String location, Boolean eligible) throws Exception
   {
      scripts.put("https://" + name + ".invalid", new ScriptedModelProvider(name));
      AssistantProvider row = store().insert(new AssistantProvider().withName(name.toUpperCase()).withProviderType("routing-test")
         .withBaseUrl("https://" + name + ".invalid").withModel(name).withEnabled(true).withIsDefault("a".equals(name)).withLocation(location).withHipaaEligible(eligible));
      return (store().insert(new AssistantModel().withProviderId(row.getId()).withModelId(name).withEnabled(true)));
   }



   /*******************************************************************************
    ** Retrieve a recording endpoint.
    *******************************************************************************/
   private ScriptedModelProvider script(String name)
   {
      return (scripts.get("https://" + name + ".invalid"));
   }



   /*******************************************************************************
    ** Save through the real validation path.
    *******************************************************************************/
   private void link(AssistantModel from, AssistantModel to) throws Exception
   {
      store().updateFields(AssistantModel.TABLE_NAME, new QRecord().withValue("id", from.getId()).withValue("fallbackModelId", to.getId()));
   }



   /*******************************************************************************
    ** Default profile for this fixture.
    *******************************************************************************/
   private void profile(String mode, AssistantModel simple, AssistantModel complex, AssistantModel classifier) throws Exception
   {
      store().insert(new AssistantProfile().withName("routing").withDisplayName("Assistant").withIsDefault(true).withEnabled(true).withRoutingMode(mode)
         .withSimpleModelId(simple.getId()).withComplexModelId(complex.getId()).withClassifierModelId(classifier == null ? null : classifier.getId()));
   }



   /*******************************************************************************
    ** Read all terminal fields.
    *******************************************************************************/
   private AssistantTurn turn(TurnRunner.Outcome outcome) throws Exception
   {
      return (store().getTurn(USER, outcome.turnId()));
   }



   /*******************************************************************************
    ** Each keyword and negative word-boundary case.
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource({"analyze sales,COMPLEX", "compare months,COMPLEX", "why,COMPLEX", "trend,COMPLEX", "explain this,COMPLEX", "summarize across stores,COMPLEX", "plan work,COMPLEX", "planet,SIMPLE", "hello,SIMPLE", "blog_post page,COMPLEX", "blog_post blog_post,SIMPLE", "blog_posts,SIMPLE"})
   void heuristicWords(String question, String expected)
   {
      assertThat(ModelRouter.heuristic(question, Set.of("blog_post", "page"), false, 8, false)).isEqualTo(expected);
   }



   /*******************************************************************************
    ** Exact length/turn boundaries, pending writes, and tool exhaustion.
    *******************************************************************************/
   @Test
   void heuristicBoundaries()
   {
      assertThat(ModelRouter.heuristic("x".repeat(600), Set.of(), false, 8, false)).isEqualTo("SIMPLE");
      assertThat(ModelRouter.heuristic("x".repeat(601), Set.of(), false, 8, false)).isEqualTo("COMPLEX");
      assertThat(ModelRouter.heuristic("hi", Set.of(), true, 0, false)).isEqualTo("COMPLEX");
      assertThat(ModelRouter.heuristic("hi", Set.of(), false, 9, false)).isEqualTo("COMPLEX");
      assertThat(ModelRouter.heuristic("hi", Set.of(), false, 0, true)).isEqualTo("COMPLEX");
      assertThat(ModelRouter.heuristic(null, Set.of(), false, 0, false)).isEqualTo("SIMPLE");
   }



   /*******************************************************************************
    ** Exact classifier replies select a route; garbage uses the heuristic.
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource({"SIMPLE,a,CLASSIFIER", "COMPLEX,b,CLASSIFIER", "garbage,b,HEURISTIC"})
   void classifierAnswers(String answer, String selected, String source) throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      AssistantModel c = model("c", "CLOUD", true);
      store().updateFields(AssistantModel.TABLE_NAME, new QRecord().withValue("id", c.getId()).withValue("supportsTools", false));
      profile("CLASSIFIER", a, b, c);
      script("c").thenText(answer);
      script(selected).thenText("Answer");
      TurnRunner.Outcome result = runner.run(USER, "explain this");
      assertThat(result.text()).isEqualTo("Answer");
      assertThat(turn(result).getModel()).isEqualTo(selected);
      assertThat(turn(result).getRouteSource()).isEqualTo(source);
      ModelRequest request = script("c").getRequests().get(0);
      assertThat(request.getMaxTokens()).isEqualTo(4);
      assertThat(request.getStreaming()).isFalse();
      assertThat(request.getTools()).isEmpty();
      assertThat(request.getSystem()).hasSize(1);
      assertThat(request.getSystem().get(0).getText()).isEqualTo(ModelRouter.CLASSIFIER_PROMPT);
      assertThat(request.getMessages()).hasSize(1);
      assertThat(request.getMessages().get(0).getParts()).hasSize(1);
      assertThat(request.getMessages().get(0).getParts().get(0).getText()).isEqualTo("explain this");
      List<AssistantUsage> rows = store().query(AssistantUsage.TABLE_NAME, new QQueryFilter(), AssistantUsage.class);
      assertThat(rows).extracting(AssistantUsage::getPurpose).containsExactly("ROUTE", "TURN");
      assertThat(turn(result).getInputTokens()).isEqualTo(2400L);
   }



   /*******************************************************************************
    ** Error categories leave the heuristic in charge and record ROUTE failures.
    *******************************************************************************/
   @ParameterizedTest
   @EnumSource(value = ModelProviderException.Kind.class, names = {"TIMEOUT", "SERVER"})
   void classifierErrors(ModelProviderException.Kind kind) throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      AssistantModel c = model("c", "CLOUD", true);
      profile("CLASSIFIER", a, b, c);
      script("c").thenError(kind, "unavailable");
      script("b").thenText("Answer");
      TurnRunner.Outcome result = runner.run(USER, "why");
      assertThat(turn(result).getRouteSource()).isEqualTo("HEURISTIC");
      assertThat(result.text()).isEqualTo("Answer");
      assertThat(store().query(AssistantUsage.TABLE_NAME, new QQueryFilter(), AssistantUsage.class)).anyMatch(row -> "ROUTE".equals(row.getPurpose()) && "ERROR".equals(row.getStatus()));
   }



   /*******************************************************************************
    ** Real wait timeout cancels the classifier and opens its circuit.
    *******************************************************************************/
   @Test
   void classifierDeadline() throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      AssistantModel c = model("c", "CLOUD", true);
      profile("CLASSIFIER", a, b, c);
      script("c").withDelay(Duration.ofSeconds(10)).thenText("SIMPLE");
      script("b").thenText("Answer");
      Long start = System.nanoTime();
      TurnRunner.Outcome result = runner.run(USER, "why");
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(8));
      assertThat(turn(result).getRouteSource()).isEqualTo("HEURISTIC");
      assertThat(store().getProvider(c.getProviderId()).getHealthState()).isEqualTo("DOWN");
   }



   /*******************************************************************************
    ** PHI cannot reach an ineligible classifier.
    *******************************************************************************/
   @Test
   void phiSkipsClassifier() throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      AssistantModel c = model("c", "CLOUD", false);
      profile("CLASSIFIER", a, b, c);
      config.withDataClassification("blog_post", DataClassification.PHI);
      script("b").thenText("Answer");
      TurnRunner.Outcome result = runner.run(USER, null, "why", Map.of("tableName", "blog_post"));
      assertThat(script("c").getRequests()).isEmpty();
      assertThat(turn(result).getRouteSource()).isEqualTo("HEURISTIC");
   }



   /*******************************************************************************
    ** OFF preserves row selection even for a complex question.
    *******************************************************************************/
   @Test
   void offKeepsDefault() throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      profile("OFF", b, b, null);
      script("a").thenText("Default");
      TurnRunner.Outcome result = runner.run(USER, "why");
      assertThat(result.text()).isEqualTo("Default");
      assertThat(turn(result).getRouteDecision()).isEqualTo("OFF");
      assertThat(turn(result).getRouteSource()).isNull();
   }



   /*******************************************************************************
    ** Every retryable category records both attempts and the final destination.
    *******************************************************************************/
   @ParameterizedTest
   @EnumSource(value = ModelProviderException.Kind.class, names = {"NETWORK", "TIMEOUT", "OVERLOADED", "SERVER", "RATE_LIMITED"})
   void fallbackBeforeOutput(ModelProviderException.Kind kind) throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      link(a, b);
      script("a").thenError(kind, "unavailable");
      script("b").thenText("Recovered");
      TurnRunner.Outcome result = runner.run(USER, "hello");
      assertThat(result.text()).isEqualTo("Recovered");
      assertThat(turn(result).getFallbackCount()).isEqualTo(1);
      assertThat(turn(result).getProviderId()).isEqualTo(b.getProviderId());
      assertThat(turn(result).getModel()).isEqualTo("b");
      assertThat(result.events(TurnEvent.NOTICE).toString()).contains("Provider A is unavailable; answered with B.");
      List<AssistantUsage> rows = store().query(AssistantUsage.TABLE_NAME, new QQueryFilter(), AssistantUsage.class);
      assertThat(rows).extracting(AssistantUsage::getStatus).containsExactly("ERROR", "OK");
      assertThat(rows.get(0).getErrorKind()).isEqualTo(kind.name());
      assertThat(turn(result).getInputTokens()).isEqualTo(2400L);
   }



   /*******************************************************************************
    ** Permanent errors cannot use another provider.
    *******************************************************************************/
   @ParameterizedTest
   @EnumSource(value = ModelProviderException.Kind.class, names = {"INVALID_REQUEST", "AUTHENTICATION", "OTHER"})
   void noFallbackForPermanentError(ModelProviderException.Kind kind) throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      link(a, b);
      script("a").thenError(kind, "bad request");
      TurnRunner.Outcome result = runner.run(USER, "hello");
      assertThat(result.end()).containsEntry("status", "ERROR");
      assertThat(script("b").getRequests()).isEmpty();
   }



   /*******************************************************************************
    ** Three models, two failures, one final answer.
    *******************************************************************************/
   @Test
   void threeModelChain() throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      AssistantModel c = model("c", "CLOUD", true);
      link(a, b);
      link(b, c);
      script("a").thenError(ModelProviderException.Kind.OVERLOADED, "busy");
      script("b").thenError(ModelProviderException.Kind.SERVER, "busy");
      script("c").thenText("Recovered");
      TurnRunner.Outcome result = runner.run(USER, "hello");
      assertThat(result.text()).isEqualTo("Recovered");
      assertThat(turn(result).getFallbackCount()).isEqualTo(2);
   }



   /*******************************************************************************
    ** Real save validation rejects cycles and disabled destinations.
    *******************************************************************************/
   @Test
   void rejectsInvalidChains() throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      link(a, b);
      assertThatThrownBy(() -> link(b, a)).hasMessageContaining("cycle");
      assertThatThrownBy(() -> link(a, a)).hasMessageContaining("cycle");
      AssistantModel c = model("c", "CLOUD", true);
      store().updateFields(AssistantModel.TABLE_NAME, new QRecord().withValue("id", c.getId()).withValue("enabled", false));
      assertThatThrownBy(() -> link(a, c)).hasMessageContaining("enabled");
      Map<Integer, AssistantModel> graph = new LinkedHashMap<>();
      for(Integer i = 1; i <= 6; i++)
      {
         graph.put(i, new AssistantModel().withId(i).withEnabled(true).withFallbackModelId(i == 6 ? null : i + 1));
      }
      assertThat(FallbackChain.validate(graph.get(1), graph)).contains("four hops");
      assertThat(FallbackChain.validate(graph.get(2), graph)).isNull();
   }



   /*******************************************************************************
    ** Skip unavailable intermediate endpoints but follow their next edge.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"disabled", "down", "disabledModel"})
   void skipsUnavailableCandidate(String state) throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      AssistantModel c = model("c", "CLOUD", true);
      link(a, b);
      link(b, c);
      if("disabledModel".equals(state))
      {
         store().updateFields(AssistantModel.TABLE_NAME, new QRecord().withValue("id", b.getId()).withValue("enabled", false));
      }
      else
      {
         store().updateFields(AssistantProvider.TABLE_NAME, "disabled".equals(state)
            ? new QRecord().withValue("id", b.getProviderId()).withValue("enabled", false)
            : new QRecord().withValue("id", b.getProviderId()).withValue("healthState", "DOWN").withValue("downUntil", NOW.plusSeconds(60)));
      }
      script("a").thenError(ModelProviderException.Kind.SERVER, "busy");
      script("c").thenText("Recovered");
      assertThat(runner.run(USER, "hello").text()).isEqualTo("Recovered");
      assertThat(script("b").getRequests()).isEmpty();
   }



   /*******************************************************************************
    ** Explicit links never override PHI eligibility.
    *******************************************************************************/
   @Test
   void phiNeverFallsToIneligible() throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", false);
      link(a, b);
      config.withDataClassification("blog_post", DataClassification.PHI);
      script("a").thenError(ModelProviderException.Kind.OVERLOADED, "busy");
      TurnRunner.Outcome result = runner.run(USER, null, "hello", Map.of("tableName", "blog_post"));
      assertThat(result.end()).containsEntry("status", "ERROR");
      assertThat(script("b").getRequests()).isEmpty();
   }



   /*******************************************************************************
    ** Automatic alternatives stay local, even when cloud is available.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"LOCAL", "CLOUD"})
   void implicitLocalOnly(String destination) throws Exception
   {
      model("a", "LOCAL", true);
      model("b", destination, true);
      script("a").thenError(ModelProviderException.Kind.OVERLOADED, "busy");
      script("b").thenText("Recovered");
      TurnRunner.Outcome result = runner.run(USER, "hello");
      assertThat(result.end()).containsEntry("status", "LOCAL".equals(destination) ? "COMPLETE" : "ERROR");
      assertThat(script("b").getRequests()).hasSize("LOCAL".equals(destination) ? 1 : 0);
   }



   /*******************************************************************************
    ** Destination STOP skips a candidate and continues to its successor.
    *******************************************************************************/
   @Test
   void budgetSkipsCandidate() throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      AssistantModel c = model("c", "CLOUD", true);
      link(a, b);
      link(b, c);
      store().insert(new AssistantBudget().withName("Stop B").withScopeType("MODEL").withScopeValue(String.valueOf(b.getId()))
         .withPeriod("DAY").withMetric("TOKENS").withLimitValue(BigDecimal.ONE).withAtLimitAction("STOP"));
      store().insert(new AssistantUsage().withUserId(USER).withProviderId(b.getProviderId()).withModel("b").withPurpose("TURN").withStatus("OK").withInputTokens(100L).withOutputTokens(0L).withCreateDate(NOW));
      script("a").thenError(ModelProviderException.Kind.SERVER, "busy");
      script("c").thenText("Recovered");
      assertThat(runner.run(USER, "hello").text()).isEqualTo("Recovered");
      assertThat(script("b").getRequests()).isEmpty();
   }



   /*******************************************************************************
    ** Circuit expiry, cached probes and successful recovery.
    *******************************************************************************/
   @Test
   void healthCircuitAndProbeCache() throws Exception
   {
      AssistantModel a = model("a", "LOCAL", true);
      ProviderService.Active active = config.getRuntime().getProviderService().resolveModel(a.getId(), false);
      ProviderHealth health = new ProviderHealth(config.getRuntime());
      health.beforeCall(active);
      health.beforeCall(active);
      assertThat(probes).isEqualTo(1);
      health.failed(active, new ModelProviderException(ModelProviderException.Kind.NETWORK, null, "offline"));
      assertThat(store().getProvider(a.getProviderId()).getDownUntil()).isEqualTo(NOW.plusSeconds(60));
      assertThatThrownBy(() -> health.beforeCall(active)).isInstanceOf(ModelProviderException.class);
      store().updateFields(AssistantProvider.TABLE_NAME, new QRecord().withValue("id", a.getProviderId()).withValue("downUntil", NOW).withValue("healthCheckedAt", NOW.minusSeconds(61)));
      health.beforeCall(active);
      assertThat(probes).isEqualTo(2);
      assertThat(store().getProvider(a.getProviderId()).getHealthState()).isEqualTo("UP");
      health.failed(active, new ModelProviderException(ModelProviderException.Kind.TIMEOUT, null, "offline"));
      health.succeeded(active);
      assertThat(store().getProvider(a.getProviderId()).getDownUntil()).isNull();
   }



   /*******************************************************************************
    ** Provider-private signatures from a successful tool round cannot reach B.
    *******************************************************************************/
   @Test
   void midTurnSwitchRebuildsNeutralHistory() throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      link(a, b);
      script("a").thenRespond(request -> new ModelResponse().withParts(new ArrayList<>(List.of(ModelPart.toolUse("tool-1", "count_records", input("table", "blog_post")))))
         .withProviderState("private-signature").withStopReason(ModelResponse.STOP_TOOL_USE))
         .thenError(ModelProviderException.Kind.OVERLOADED, "busy");
      script("b").thenRespond(request ->
      {
         assertThat(request.getMessages()).allMatch(message -> message.getProviderState() == null);
         assertThat(request.getMessages()).anyMatch(message -> message.getParts().stream().anyMatch(part -> part.getType() == ModelPart.Type.TOOL_RESULT));
         return (new ModelResponse().withParts(new ArrayList<>(List.of(ModelPart.text("Recovered after tool")))).withStopReason(ModelResponse.STOP_END_TURN));
      });
      TurnRunner.Outcome result = runner.run(USER, "count posts");
      assertThat(result.text()).isEqualTo("Recovered after tool");
      assertThat(result.toolCalls()).hasSize(1);
      assertThat(turn(result).getFallbackCount()).isEqualTo(1);
      assertThat(script("a").getRequests().get(1).getMessages()).anyMatch(message -> "private-signature".equals(message.getProviderState()));
   }



   /*******************************************************************************
    ** Even buffered text or a tool start makes the failure terminal.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"text", "tool"})
   void partialOutputNeverFallsBack(String output) throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      link(a, b);
      scripts.put("https://a.invalid", new ScriptedModelProvider("a")
      {
         /*******************************************************************************
          ** Emit a partial event, then fail the same call.
          *******************************************************************************/
         @Override
         public StreamHandle stream(ModelRequest request, ModelEventListener listener)
         {
            if("text".equals(output))
            {
               listener.onTextDelta("partial");
            }
            else
            {
               listener.onToolUseStart("tool-1", "count_records");
            }
            CompletableStreamHandle handle = new CompletableStreamHandle();
            handle.fail(new ModelProviderException(ModelProviderException.Kind.NETWORK, null, "offline"));
            return (handle);
         }
      });
      TurnRunner.Outcome result = runner.run(USER, "hello");
      assertThat(result.end()).containsEntry("status", "ERROR");
      assertThat(script("b").getRequests()).isEmpty();
      assertThat(turn(result).getFallbackCount()).isZero();
      assertThat(store().getProvider(a.getProviderId()).getHealthState()).isEqualTo("DOWN");
      if("text".equals(output))
      {
         assertThat(result.text()).isEqualTo("partial");
      }
   }



   /*******************************************************************************
    ** Failed local probes open the circuit without sending model requests.
    *******************************************************************************/
   @Test
   void failedProbeAndHomeHealth() throws Exception
   {
      AssistantModel a = model("a", "LOCAL", true);
      healthy = false;
      TurnRunner.Outcome result = runner.run(USER, "hello");
      assertThat(result.end()).containsEntry("status", "ERROR");
      assertThat(script("a").getRequests()).isEmpty();
      assertThat(store().getProvider(a.getProviderId()).getHealthState()).isEqualTo("DOWN");
      String html = AssistantHomeView.render(AssistantHomeData.load(config.getRuntime()), "");
      assertThat(html).contains("DOWN", "Down until", "0 fallbacks");
   }



   /*******************************************************************************
    ** Classifier tokens count before the answer call can dispatch.
    *******************************************************************************/
   @Test
   void classifierConsumesBudget() throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      AssistantModel c = model("c", "CLOUD", true);
      profile("CLASSIFIER", a, b, c);
      store().insert(new AssistantBudget().withName("Small allowance").withScopeType("USER").withScopeValue(USER)
         .withPeriod("DAY").withMetric("TOKENS").withLimitValue(BigDecimal.valueOf(100)).withAtLimitAction("STOP"));
      script("c").thenText("SIMPLE");
      TurnRunner.Outcome result = runner.run(USER, "hello");
      assertThat(result.end()).containsEntry("status", "ERROR");
      assertThat(script("a").getRequests()).isEmpty();
      assertThat(store().query(AssistantUsage.TABLE_NAME, new QQueryFilter(), AssistantUsage.class)).extracting(AssistantUsage::getPurpose).containsExactly("ROUTE");
   }



   /*******************************************************************************
    ** Conversation context supplies tool exhaustion, pending writes and history.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"tool_limit", "pending", "history"})
   void heuristicConversationContext(String context) throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      profile("HEURISTIC", a, b, null);
      script("a").thenText("Simple");
      TurnRunner.Outcome first = runner.run(USER, "hello");
      assertThat(turn(first).getRouteDecision()).isEqualTo("SIMPLE");
      if("tool_limit".equals(context))
      {
         store().updateFields(AssistantTurn.TABLE_NAME, new QRecord().withValue("id", first.turnId()).withValue("stopReason", "tool_limit"));
      }
      else if("pending".equals(context))
      {
         store().insert(new AssistantPendingAction().withUserId(USER).withConversationId(first.conversationId()).withStatus("PENDING"));
      }
      else
      {
         for(Integer i = 0; i < 8; i++)
         {
            store().insert(new AssistantTurn().withUserId(USER).withConversationId(first.conversationId()).withStatus("COMPLETE"));
         }
      }
      script("b").thenText("Complex");
      TurnRunner.Outcome result = runner.run(USER, first.conversationId(), "hello", null);
      assertThat(result.text()).isEqualTo("Complex");
      assertThat(turn(result).getRouteDecision()).isEqualTo("COMPLEX");
   }



   /*******************************************************************************
    ** Without a profile, escalation supplies the complex model and effort.
    *******************************************************************************/
   @Test
   void codeLevelEscalation() throws Exception
   {
      config.getModels().withEscalationEnabled(true).withEscalationModel("complex-default").withEscalationEffort("high");
      provider.thenText("Complex");
      TurnRunner.Outcome result = runner.run(USER, "why");
      assertThat(result.text()).isEqualTo("Complex");
      assertThat(provider.getRequests().get(0).getModel()).isEqualTo("complex-default");
      assertThat(provider.getRequests().get(0).getEffort()).isEqualTo("high");
   }



   /*******************************************************************************
    ** Four edges are allowed; a fifth is rejected even when added at the tail.
    *******************************************************************************/
   @Test
   void fourHopBoundary() throws Exception
   {
      AssistantModel a = model("a", "CLOUD", true);
      AssistantModel b = model("b", "CLOUD", true);
      AssistantModel c = model("c", "CLOUD", true);
      AssistantModel d = store().insert(new AssistantModel().withProviderId(c.getProviderId()).withModelId("d").withEnabled(true));
      AssistantModel e = store().insert(new AssistantModel().withProviderId(c.getProviderId()).withModelId("e").withEnabled(true));
      AssistantModel f = store().insert(new AssistantModel().withProviderId(c.getProviderId()).withModelId("f").withEnabled(true));
      link(a, b);
      link(b, c);
      link(c, d);
      link(d, e);
      assertThatThrownBy(() -> link(e, f)).hasMessageContaining("four hops");
      script("a").thenError(ModelProviderException.Kind.OVERLOADED, "busy");
      script("b").thenError(ModelProviderException.Kind.OVERLOADED, "busy");
      script("c").thenError(ModelProviderException.Kind.OVERLOADED, "busy").thenError(ModelProviderException.Kind.OVERLOADED, "busy").thenText("Recovered");
      TurnRunner.Outcome result = runner.run(USER, "hello");
      assertThat(result.text()).isEqualTo("Recovered");
      assertThat(turn(result).getModel()).isEqualTo("e");
      assertThat(turn(result).getFallbackCount()).isEqualTo(4);
      String html = AssistantHomeView.render(AssistantHomeData.load(config.getRuntime()), "");
      assertThat(html).contains("4 fallbacks");
   }
}
