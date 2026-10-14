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


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Set;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.spi.ModelProviderException;
import com.kingsrook.qbits.assistant.spi.ProviderSettings;
import com.kingsrook.qbits.assistant.testing.AssistantTestFixture;
import com.kingsrook.qqq.backend.core.context.QContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Private CA coverage against loopback HTTPS; all key material is test-only.
 *******************************************************************************/
class OpenAiTlsTest
{
   @TempDir
   Path temporary;



   /*******************************************************************************
    ** The public test root, never a real deployment CA.
    *******************************************************************************/
   private String pem() throws Exception
   {
      try(InputStream input = getClass().getResourceAsStream("/tls/ca.pem"))
      {
         return (new String(input.readAllBytes(), StandardCharsets.US_ASCII));
      }
   }



   /*******************************************************************************
    ** Leaf chain signed by the test root, valid for localhost but not its IP.
    *******************************************************************************/
   private SSLContext serverContext() throws Exception
   {
      KeyStore store = KeyStore.getInstance("PKCS12");
      try(InputStream input = getClass().getResourceAsStream("/tls/server.p12"))
      {
         store.load(input, "test-only".toCharArray());
      }
      KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keys.init(store, "test-only".toCharArray());
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(keys.getKeyManagers(), null, null);
      return (context);
   }



   /*******************************************************************************
    ** Every operation rejects the unknown CA with a safe, actionable diagnostic.
    *******************************************************************************/
   @Test
   void untrustedCertificate() throws Exception
   {
      try(MockOpenAiServer server = new MockOpenAiServer(serverContext()))
      {
         ProviderSettings settings = OpenAiContractTest.settings(server, "OLLAMA");
         var type = new OpenAiCompatibleProviderType();
         assertThatThrownBy(() -> type.discoverModels(settings)).isInstanceOfSatisfying(ModelProviderException.class, this::trustError);
         assertThat(type.checkHealth(settings).healthy()).isFalse();
         assertThat(type.checkHealth(settings).message()).contains("NETWORK", "tlsCaBundle");
         assertThatThrownBy(() -> type.create(settings).stream(OpenAiContractTest.request(), t -> {}).await(Duration.ofSeconds(5)))
            .isInstanceOfSatisfying(ModelProviderException.class, this::trustError);
         assertThat(server.requests).isEmpty();
      }
   }



   /*******************************************************************************
    ** No raw handshake details, keys, or certificate contents escape.
    *******************************************************************************/
   private void trustError(ModelProviderException error)
   {
      assertThat(error.getKind()).isEqualTo(ModelProviderException.Kind.NETWORK);
      assertThat(error.getMessage()).contains("certificate is not trusted", "tlsCaBundle").doesNotContain("synthetic-key", "TEST ONLY", "BEGIN CERTIFICATE");
      assertThat(error.getCause()).isNull();
   }



   /*******************************************************************************
    ** Shared trust enables all operations while endpoint identification stays on.
    *******************************************************************************/
   @Test
   void trustedHealthDiscoveryChatAndHostnameCheck() throws Exception
   {
      var config = new AssistantQBitConfig().withTrustedCaBundle("test-root", pem());
      try(MockOpenAiServer server = new MockOpenAiServer(serverContext()))
      {
         ProviderSettings settings = OpenAiContractTest.settings(server, "OLLAMA").withTlsCaBundle("test-root")
            .withSslContext(config.getTrustedCaBundles().get("test-root"));
         var type = new OpenAiCompatibleProviderType();
         String models = "{\"data\":[{\"id\":\"test\"}]}";
         server.enqueue(200, models);
         assertThat(type.checkHealth(settings).healthy()).isTrue();
         server.enqueue(200, models);
         assertThat(type.discoverModels(settings)).extracting(m -> m.modelId()).containsExactly("test");
         server.enqueue(200, OpenAiContractTest.TEXT);
         assertThat(type.create(settings).stream(OpenAiContractTest.request(), t -> {}).await(Duration.ofSeconds(5)).getText()).isEqualTo("OK");
         assertThat(server.requests).hasSize(3).allSatisfy(r -> assertThat(r.authorization()).isEqualTo("Bearer synthetic-key"));
         settings.withBaseUrl(server.url().replace("localhost", "127.0.0.1"));
         assertThatThrownBy(() -> type.discoverModels(settings)).isInstanceOfSatisfying(ModelProviderException.class, this::trustError);
         assertThatThrownBy(() -> type.create(settings).stream(OpenAiContractTest.request(), t -> {}).await(Duration.ofSeconds(5)))
            .isInstanceOfSatisfying(ModelProviderException.class, this::trustError);
         assertThat(server.requests).hasSize(3);
      }
   }



   /*******************************************************************************
    ** Parse at startup, accept multiple PEMs, omit contexts from serialized beans.
    *******************************************************************************/
   @Test
   void eagerConfigAndSerialization() throws Exception
   {
      String pem = pem();
      Path file = temporary.resolve("bundle.pem");
      Files.writeString(file, pem + pem);
      var config = new AssistantQBitConfig().withTrustedCaBundleFile("test-root", file);
      Files.delete(file);
      assertThat(config.getTrustedCaBundles()).containsKey("test-root");
      assertThatThrownBy(() -> config.getTrustedCaBundles().clear()).isInstanceOf(UnsupportedOperationException.class);
      assertThat(OpenAiHttp.JSON.writeValueAsString(config)).doesNotContain("trustedCaBundles", "BEGIN CERTIFICATE", "test-root");
      var settings = new ProviderSettings().withSslContext(config.getTrustedCaBundles().get("test-root"));
      assertThat(OpenAiHttp.JSON.writeValueAsString(settings)).doesNotContain("sslContext", "BEGIN CERTIFICATE");
      for(String invalid : new String[] { null, "", "private-content", pem + "private-content", "-----BEGIN CERTIFICATE-----bad-----END CERTIFICATE-----" })
      {
         assertThatThrownBy(() -> config.withTrustedCaBundle("bad", invalid)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid CA bundle").hasMessageNotContaining("private-content").hasNoCause();
      }
      assertThatThrownBy(() -> config.withTrustedCaBundle(" ", pem)).hasMessageContaining("CA bundle name");
      assertThatThrownBy(() -> config.withTrustedCaBundleFile("missing", file)).hasMessage("Could not read the configured CA bundle file.").hasNoCause();
   }



   /*******************************************************************************
    ** Settings saves reject unknown names and HTTP; changing names rebuilds clients.
    *******************************************************************************/
   @Test
   void registryValidationAndClientCache() throws Exception
   {
      var config = new AssistantQBitConfig().withProviderType(new OpenAiCompatibleProviderType())
         .withAllowedBaseUrls(Set.of("https://localhost:8443/v1"))
         .withTrustedCaBundle("first", pem()).withTrustedCaBundle("second", pem());
      try
      {
         var instance = AssistantTestFixture.build(config);
         QContext.init(instance, AssistantTestFixture.sessionFor(AssistantTestFixture.ADMIN));
         var service = config.getRuntime().getProviderService();
         var row = new AssistantProvider().withName("Test TLS").withProviderType("openai-compatible").withKind("OLLAMA")
            .withBaseUrl("https://localhost:8443/v1").withTlsCaBundle("unknown");
         assertThat(service.validate(row)).anyMatch(e -> e.contains("configured on this server"));
         assertThatThrownBy(() -> service.settings(row)).hasMessageContaining("TLS CA bundle");
         assertThatThrownBy(() -> config.getRuntime().getStore().insert(row)).hasMessageContaining("TLS CA bundle");
         row.withTlsCaBundle("first").withBaseUrl(null);
         assertThat(service.validate(row)).anyMatch(e -> e.contains("HTTPS"));
         row.withBaseUrl("https://localhost:8443/v1");
         assertThat(service.validate(row)).isEmpty();
         row.withId(config.getRuntime().getStore().insert(row).getId());
         assertThat(row.getTlsCaBundle()).isEqualTo("first");
         assertThat(service.settings(row).getSslContext()).isSameAs(config.getTrustedCaBundles().get("first"));
         var first = service.forRow(row, "test").provider();
         assertThat(service.forRow(row, "test").provider()).isSameAs(first);
         row.withTlsCaBundle("second");
         assertThat(service.forRow(row, "test").provider()).isNotSameAs(first);
         row.withTlsCaBundle(null);
         assertThat(service.settings(row).getSslContext()).isNull();
         assertThat(instance.getTable(AssistantProvider.TABLE_NAME).getField("tlsCaBundle").getMaxLength()).isEqualTo(100);
      }
      finally
      {
         if(config.getRuntime() != null)
         {
            config.getRuntime().close();
         }
         QContext.clear();
      }
   }
}
