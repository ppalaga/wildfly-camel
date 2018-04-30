/*
 * #%L
 * Wildfly Camel :: Testsuite
 * %%
 * Copyright (C) 2013 - 2014 RedHat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wildfly.camel.test.cxf.ws;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Base64;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.http.HttpRequest;
import org.wildfly.camel.test.common.utils.UserManager;
import org.wildfly.camel.test.common.utils.WildFlyCli;
import org.wildfly.camel.test.cxf.ws.subB.Application;
import org.wildfly.camel.test.cxf.ws.subB.CxfWsRouteBuilder;
import org.wildfly.camel.test.cxf.ws.subB.GreetingService;
import org.wildfly.camel.test.cxf.ws.subB.GreetingsProcessor;
import org.wildfly.extension.camel.CamelAware;

@CamelAware
@RunAsClient
@RunWith(Arquillian.class)
@ServerSetup(CXFWSElytronSecureProducerIntegrationTest.ServerSecuritySetup.class)
public class CXFWSElytronSecureProducerIntegrationTest {
    private static final Path WILDFLY_HOME = Paths.get(System.getProperty("jbossHome"));
    private static final Path BASEDIR = Paths.get(System.getProperty("project.basedir"));

    private static final String HTTPS_HOST = "https://localhost:8443";
    private static final String WS_ENDPOINT_ADDRESS = "https://localhost:8443/webservices/greeting-secure-cdi";
    private static final String APPLICATION_USER = "CN=localhost";
    private static final String APPLICATION_PASSWORD = "testPassword1+";
    private static final String APPLICATION_ROLE = "testRole";
    private static final String TRUSTSTORE_PASSWORD = "password";

    private static final String WS_MESSAGE_TEMPLATE = "<Envelope xmlns=\"http://schemas.xmlsoap.org/soap/envelope/\">" //
            + "<Body>" //
            + "<greet xmlns=\"http://subB.ws.cxf.test.camel.wildfly.org/\">" //
            + "<message xmlns=\"\">%s</message>" //
            + "<name xmlns=\"\">%s</name>" //
            + "</greet>" //
            + "</Body>" //
            + "</Envelope>";

    static final String WAR_NAME = "CXFWSElytronSecureProducerIntegrationTest.war";

    @Deployment
    public static Archive<?> deployment() {
        final WebArchive archive = ShrinkWrap.create(WebArchive.class, WAR_NAME) //
                .addClasses(CxfWsRouteBuilder.class, Application.class, GreetingService.class, GreetingsProcessor.class) //
                .addAsWebInfResource(
                        new StringAsset("<jboss-web><security-domain>client-cert</security-domain></jboss-web>"),
                        "jboss-web.xml")//
                .addAsWebInfResource(new StringAsset(""), "beans.xml")//
                .addAsWebInfResource(BASEDIR.resolve("src/test/resources/cxf/secure-elytron/web.xml").toFile(), "web.xml")//
        ;
        archive.as(ZipExporter.class).exportTo(new File(
                "/home/ppalaga/orgs/camel/wildfly-camel/itests/standalone/basic/target/cxfws-elytron-secure-producer-tests.war"));
        return archive;
    }

    static class ServerSecuritySetup implements ServerSetupTask {

        private final WildFlyCli wildFlyCli = new WildFlyCli(WILDFLY_HOME);

        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {
            // Make WildFly generate a keystore
            HttpRequest.post(HTTPS_HOST).getResponse();

            UserManager.addApplicationUser(APPLICATION_USER, APPLICATION_PASSWORD, WILDFLY_HOME);
            UserManager.addRoleToApplicationUser(APPLICATION_USER, APPLICATION_ROLE, WILDFLY_HOME);

            wildFlyCli.run(BASEDIR.resolve("src/test/resources/cxf/secure-elytron/add-elytron-security-domain.cli"))
                    .assertSuccess();
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            wildFlyCli.run(BASEDIR.resolve("src/test/resources/cxf/secure-elytron/remove-elytron-security-domain.cli"))
                    .assertSuccess();

            UserManager.removeApplicationUser(APPLICATION_USER, WILDFLY_HOME);
            UserManager.revokeRoleFromApplicationUser(APPLICATION_USER, APPLICATION_ROLE, WILDFLY_HOME);
        }

    }

    private static SSLConnectionSocketFactory createSocketFactory() throws KeyManagementException,
            NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        SSLContext sslcontext = SSLContexts.custom()//
                .loadTrustMaterial(WILDFLY_HOME.resolve("standalone/configuration/application.keystore").toFile(),
                        TRUSTSTORE_PASSWORD.toCharArray(), //
                        TrustSelfSignedStrategy.INSTANCE)//
                .build();
        return new SSLConnectionSocketFactory(sslcontext, new HostnameVerifier() {
            @Override
            public boolean verify(final String s, final SSLSession sslSession) {
                return "localhost".equals(s);
            }
        });
    }

    @Test
    public void greetBasic() throws Exception {
        try (CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(createSocketFactory()).build()) {
            HttpPost request = new HttpPost(WS_ENDPOINT_ADDRESS);
            request.setHeader("Content-Type", "text/xml");
            request.setHeader("soapaction", "\"urn:greet\"");

            String auth = APPLICATION_USER + ":" + APPLICATION_PASSWORD;
            String authHeader = "Basic "
                    + Base64.getEncoder().encodeToString(auth.getBytes(Charset.forName("ISO-8859-1")));
            request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);

            request.setEntity(
                    new StringEntity(String.format(WS_MESSAGE_TEMPLATE, "Hi", "Joe"), StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = httpclient.execute(request)) {
                Assert.assertEquals(200, response.getStatusLine().getStatusCode());

                HttpEntity entity = response.getEntity();
                String body = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                Assert.assertTrue(body.contains("Hi Joe"));
            }
        }

    }

    @Test
    public void greetAnonymous() throws Exception {
        try (CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(createSocketFactory()).build()) {
            HttpPost request = new HttpPost(WS_ENDPOINT_ADDRESS);
            request.setHeader("Content-Type", "text/xml");
            request.setHeader("soapaction", "\"urn:greet\"");

            request.setEntity(
                    new StringEntity(String.format(WS_MESSAGE_TEMPLATE, "Hi", "Joe"), StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = httpclient.execute(request)) {
                Assert.assertEquals(401, response.getStatusLine().getStatusCode());
            }
        }
    }
}
