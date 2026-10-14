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

package com.kingsrook.qbits.assistant.provider.openai;


import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.spi.ModelMessage;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ModelRequest;
import com.kingsrook.qbits.assistant.spi.ProviderSettings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Opt-in smoke test; never contacts an endpoint in the default build.
 *******************************************************************************/
@Tag("live")
class LiveOpenAiCompatibleTest
{
   /*******************************************************************************
    ** All connection details come from environment references.
    *******************************************************************************/
   @Test
   void live() throws Exception
   {
      String url = System.getenv("ASSISTANT_LIVE_OPENAI_BASE_URL");
      String model = System.getenv("ASSISTANT_LIVE_OPENAI_MODEL");
      Assumptions.assumeTrue(url != null && !url.isBlank() && model != null && !model.isBlank(), "Live URL and model must be set");
      String kind = System.getenv("ASSISTANT_LIVE_OPENAI_KIND");
      String keyEnv = System.getenv("ASSISTANT_LIVE_OPENAI_KEY_ENV");
      ProviderSettings settings = new ProviderSettings().withKind(kind == null || kind.isBlank() ? "OLLAMA" : kind)
         .withBaseUrl(url).withApiKey(keyEnv == null || keyEnv.isBlank() ? null : System.getenv(keyEnv));
      String caFile = System.getenv("ASSISTANT_LIVE_OPENAI_CA_FILE");
      if(caFile != null && !caFile.isBlank())
      {
         settings.withTlsCaBundle("live").withSslContext(new AssistantQBitConfig().withTrustedCaBundleFile("live", Path.of(caFile)).getTrustedCaBundles().get("live"));
      }
      try
      {
         OpenAiCompatibleProviderType type = new OpenAiCompatibleProviderType();
         Assumptions.assumeTrue(Boolean.TRUE.equals(type.checkHealth(settings).healthy()), "Live health probe must pass");
         assertThat(type.discoverModels(settings)).isNotEmpty();
         var provider = type.create(settings);
         StringBuilder text = new StringBuilder();
         provider.stream(new ModelRequest().withModel(model).withMaxTokens(128).withMessages(List.of(ModelMessage.user("Reply with OK."))), text::append).await(Duration.ofSeconds(120));
         assertThat(text).isNotEmpty();
         var response = provider.stream(new ModelRequest().withModel(model).withMaxTokens(256).withTools(List.of(OpenAiContractTest.tool()))
            .withMessages(List.of(ModelMessage.user("Call lookup with q set to hello. Do not answer without calling the tool."))), t -> {}).await(Duration.ofSeconds(120));
         assertThat(response.getToolUses()).anyMatch(t -> "lookup".equals(t.getToolName()));
      }
      catch(ModelProviderException e)
      {
         Assumptions.assumeFalse(e.getKind() == ModelProviderException.Kind.NETWORK || e.getKind() == ModelProviderException.Kind.TIMEOUT, "Live endpoint unreachable (including TLS)");
         throw (e);
      }
   }
}
