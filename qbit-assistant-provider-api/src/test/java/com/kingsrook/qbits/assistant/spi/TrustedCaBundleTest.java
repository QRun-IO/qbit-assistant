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

package com.kingsrook.qbits.assistant.spi;


import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Offline proof that adding a bundle does not replace the JVM's trust roots.
 *******************************************************************************/
class TrustedCaBundleTest
{
   /*******************************************************************************
    ** Use existing public JVM roots in memory; no network or fixture signing.
    *******************************************************************************/
   @Test
   void keepsDefaultRoots() throws Exception
   {
      X509TrustManager defaults = manager(null);
      X509Certificate[] roots = defaults.getAcceptedIssuers();
      assertThat(roots.length).isGreaterThan(1);
      KeyStore extra = KeyStore.getInstance(KeyStore.getDefaultType());
      extra.load(null, null);
      extra.setCertificateEntry("one-root", roots[0]);
      TrustedCaBundle combined = new TrustedCaBundle(defaults, manager(extra));
      assertThat(combined.getAcceptedIssuers()).contains(roots);
      assertThat(combined.getAcceptedIssuers()).hasSize(roots.length + 1);
      combined.checkServerTrusted(new X509Certificate[] { roots[1] }, roots[1].getPublicKey().getAlgorithm());
      combined.checkClientTrusted(new X509Certificate[] { roots[1] }, roots[1].getPublicKey().getAlgorithm());
      String pem = "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(roots[0].getEncoded()) + "\n-----END CERTIFICATE-----\n";
      assertThat(TrustedCaBundle.parse(pem).getProtocol()).isEqualTo("TLS");
   }



   /*******************************************************************************
    ** Resolve a real PKIX manager from either the JVM or the small extra store.
    *******************************************************************************/
   private X509TrustManager manager(KeyStore store) throws Exception
   {
      TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      factory.init(store);
      return (Arrays.stream(factory.getTrustManagers()).filter(X509TrustManager.class::isInstance).map(X509TrustManager.class::cast).findFirst().orElseThrow());
   }
}
