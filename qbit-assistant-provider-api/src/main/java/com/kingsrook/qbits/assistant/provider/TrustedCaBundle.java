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

package com.kingsrook.qbits.assistant.provider;


import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;


/*******************************************************************************
 ** Builds additive trust: JVM roots OR explicitly configured certificates.
 ** Endpoint identification remains the HTTP client's responsibility and is enabled.
 *******************************************************************************/
public final class TrustedCaBundle implements X509TrustManager
{
   private final X509TrustManager defaults;
   private final X509TrustManager additional;



   /*******************************************************************************
    ** Two independently validating trust managers, never a trust-all fallback.
    *******************************************************************************/
   TrustedCaBundle(X509TrustManager defaults, X509TrustManager additional)
   {
      this.defaults = defaults;
      this.additional = additional;
   }



   /*******************************************************************************
    ** Accept only one or more complete PEM certificates; reject trailing garbage.
    *******************************************************************************/
   public static SSLContext parse(String pem)
   {
      try
      {
         KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
         store.load(null, null);
         CertificateFactory factory = CertificateFactory.getInstance("X.509");
         Matcher matcher = Pattern.compile("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----").matcher(pem == null ? "" : pem);
         int end = 0;
         int count = 0;
         while(matcher.find())
         {
            if(!pem.substring(end, matcher.start()).isBlank())
            {
               throw (new CertificateException());
            }
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(matcher.group().getBytes(StandardCharsets.US_ASCII)));
            store.setCertificateEntry("ca-" + count++, certificate);
            end = matcher.end();
         }
         if(count == 0 || !pem.substring(end).isBlank())
         {
            throw (new CertificateException());
         }
         SSLContext context = SSLContext.getInstance("TLS");
         context.init(null, new TrustManager[] { new TrustedCaBundle(manager(null), manager(store)) }, null);
         return (context);
      }
      catch(Exception e)
      {
         throw (new IllegalArgumentException("Invalid CA bundle: expected one or more PEM X.509 certificates."));
      }
   }



   /*******************************************************************************
    ** A null store asks JSSE to load the JVM's configured default trust roots.
    *******************************************************************************/
   private static X509TrustManager manager(KeyStore store) throws Exception
   {
      TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      factory.init(store);
      return (Arrays.stream(factory.getTrustManagers()).filter(X509TrustManager.class::isInstance).map(X509TrustManager.class::cast).findFirst().orElseThrow());
   }



   /*******************************************************************************
    ** Client validation uses the same additive trust rule.
    *******************************************************************************/
   @Override
   public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
   {
      try
      {
         defaults.checkClientTrusted(chain, authType);
      }
      catch(CertificateException e)
      {
         additional.checkClientTrusted(chain, authType);
      }
   }



   /*******************************************************************************
    ** Try JVM roots first, then the named bundle, preserving chain validation.
    *******************************************************************************/
   @Override
   public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
   {
      try
      {
         defaults.checkServerTrusted(chain, authType);
      }
      catch(CertificateException e)
      {
         additional.checkServerTrusted(chain, authType);
      }
   }



   /*******************************************************************************
    ** Both sets of issuers remain accepted.
    *******************************************************************************/
   @Override
   public X509Certificate[] getAcceptedIssuers()
   {
      return (Stream.concat(Arrays.stream(defaults.getAcceptedIssuers()), Arrays.stream(additional.getAcceptedIssuers())).toArray(X509Certificate[]::new));
   }
}
