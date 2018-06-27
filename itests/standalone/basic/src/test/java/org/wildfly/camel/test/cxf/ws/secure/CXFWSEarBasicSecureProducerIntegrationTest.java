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
package org.wildfly.camel.test.cxf.ws.secure;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.security.BasicSecurityDomainSetup;
import org.wildfly.camel.test.common.security.ClientCertSecurityDomainSetup;
import org.wildfly.camel.test.common.security.SecurityUtils;
import org.wildfly.camel.test.cxf.ws.secure.subA.Application;
import org.wildfly.camel.test.cxf.ws.secure.subB.ApplicationB;
import org.wildfly.extension.camel.CamelAware;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@CamelAware
@RunAsClient
@RunWith(Arquillian.class)
@ServerSetup({BasicSecurityDomainSetup.class, ClientCertSecurityDomainSetup.class})
public class CXFWSEarBasicSecureProducerIntegrationTest {


    private static final String WS_MESSAGE_TEMPLATE_B = "<Envelope xmlns=\"http://schemas.xmlsoap.org/soap/envelope/\">" //
            + "<Body>" //
            + "<greet xmlns=\"http://subB.secure.ws.cxf.test.camel.wildfly.org/\">" //
            + "<message xmlns=\"\">%s</message>" //
            + "<name xmlns=\"\">%s</name>" //
            + "</greet>" //
            + "</Body>" //
            + "</Envelope>";

    private static final Map<String, String> PATH_ROLE_MAP = new LinkedHashMap<String, String>() {
        private static final long serialVersionUID = 1L;
        {
            try {
                put(new URI(Application.CXF_CONSUMER_ENDPOINT_ADDRESS).getPath(),
                        BasicSecurityDomainSetup.APPLICATION_ROLE);
                put(new URI(Application.CXF_CONSUMER_ENDPOINT_ADDRESS_SUB).getPath(),
                        BasicSecurityDomainSetup.APPLICATION_ROLE_SUB);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    };

    private static final Map<String, String> PATH_ROLE_MAP_B = new LinkedHashMap<String, String>() {
        private static final long serialVersionUID = 1L;
        {
            try {
                put(new URI(ApplicationB.CXF_CONSUMER_ENDPOINT_ADDRESS).getPath(),
                        ClientCertSecurityDomainSetup.APPLICATION_ROLE);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    };
    static final Path WILDFLY_HOME = Paths.get(System.getProperty("jbossHome"));

    @Deployment
    public static Archive<?> deployment() {
        final JavaArchive jar = ShrinkWrap
                .create(JavaArchive.class, CXFWSEarBasicSecureProducerIntegrationTest.class.getSimpleName() + ".jar") //
                .addClasses(BasicSecurityDomainSetup.class, CXFWSSecureUtils.class) //
        ;
        final WebArchive warA = ShrinkWrap
                .create(WebArchive.class, CXFWSEarBasicSecureProducerIntegrationTest.class.getSimpleName() + "-a.war") //
                .addPackage(Application.class.getPackage()) //
                .addAsWebInfResource(new StringAsset(""), "beans.xml") //
        ;
        SecurityUtils.enhanceArchive(warA, BasicSecurityDomainSetup.SECURITY_DOMAIN,
                BasicSecurityDomainSetup.AUTH_METHOD, PATH_ROLE_MAP);

        final WebArchive warB = ShrinkWrap
                .create(WebArchive.class, CXFWSEarBasicSecureProducerIntegrationTest.class.getSimpleName() + "-b.war") //
                .addPackage(ApplicationB.class.getPackage()) //
                .addAsWebInfResource(new StringAsset(""), "beans.xml") //
        ;
        SecurityUtils.enhanceArchive(warB, ClientCertSecurityDomainSetup.SECURITY_DOMAIN,
                ClientCertSecurityDomainSetup.AUTH_METHOD, PATH_ROLE_MAP_B);

        final EnterpriseArchive ear = ShrinkWrap
                .create(EnterpriseArchive.class, CXFWSEarBasicSecureProducerIntegrationTest.class.getSimpleName() + ".ear") //
                .addAsLibrary(jar)
                .addAsModule(warA)
                .addAsModule(warB)
        ;

        ear.as(ZipExporter.class).exportTo(new File("/home/ppalaga/zzz/ear.zip"), true);

        return ear;
    }
//
//    @Test
//    public void greetAnonymous() throws Exception {
//        CXFWSSecureUtils.assertGreet(WILDFLY_HOME, Application.CXF_CONSUMER_ENDPOINT_ADDRESS, null, null, 401, null);
//    }
//
//    @Test
//    public void greetAnonymousSub() throws Exception {
//        CXFWSSecureUtils.assertGreet(WILDFLY_HOME, Application.CXF_CONSUMER_ENDPOINT_ADDRESS_SUB, null, null, 401,
//                null);
//    }
//
//    @Test
//    public void greetBasicBadUser() throws Exception {
//        CXFWSSecureUtils.assertGreet(WILDFLY_HOME, Application.CXF_CONSUMER_ENDPOINT_ADDRESS,
//                BasicSecurityDomainSetup.APPLICATION_USER_SUB, BasicSecurityDomainSetup.APPLICATION_PASSWORD_SUB, 403,
//                null);
//    }
//
//    @Test
//    public void greetBasicGoodUser() throws Exception {
//        CXFWSSecureUtils.assertGreet(WILDFLY_HOME, Application.CXF_CONSUMER_ENDPOINT_ADDRESS,
//                BasicSecurityDomainSetup.APPLICATION_USER, BasicSecurityDomainSetup.APPLICATION_PASSWORD, 200,
//                "Hi Joe");
//    }
//
//    @Test
//    public void greetBasicSubBadUser() throws Exception {
//        CXFWSSecureUtils.assertGreet(WILDFLY_HOME, Application.CXF_CONSUMER_ENDPOINT_ADDRESS_SUB,
//                BasicSecurityDomainSetup.APPLICATION_USER, BasicSecurityDomainSetup.APPLICATION_PASSWORD, 403, null);
//    }
//
//    @Test
//    public void greetBasicSubGoodUser() throws Exception {
//        CXFWSSecureUtils.assertGreet(WILDFLY_HOME, Application.CXF_CONSUMER_ENDPOINT_ADDRESS_SUB,
//                BasicSecurityDomainSetup.APPLICATION_USER_SUB, BasicSecurityDomainSetup.APPLICATION_PASSWORD_SUB, 200,
//                "Hi Joe");
//    }


    @Test
    public void greetUntrustedClientCert() throws Exception {
        try (CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(SecurityUtils.createUntrustedClientCertSocketFactory(WILDFLY_HOME)).build()) {
            HttpPost request = new HttpPost(ApplicationB.CXF_CONSUMER_ENDPOINT_ADDRESS);
            request.setHeader("Content-Type", "text/xml");
            request.setHeader("soapaction", "\"urn:greet\"");

            request.setEntity(
                    new StringEntity(String.format(WS_MESSAGE_TEMPLATE_B, "Hi", "Joe"), StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = httpclient.execute(request)) {
                HttpEntity entity = response.getEntity();
                String body = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                Assert.assertTrue("expected to contain 'Hi Joe', found "+ body, body.contains("Hi Joe"));
                Assert.assertEquals(403, response.getStatusLine().getStatusCode());
            }
        }
    }

    @Test
    public void greetClientCert() throws Exception {
        try (CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(SecurityUtils.createTrustedClientCertSocketFactory(WILDFLY_HOME)).build()) {
            HttpPost request = new HttpPost(ApplicationB.CXF_CONSUMER_ENDPOINT_ADDRESS);
            request.setHeader("Content-Type", "text/xml");
            request.setHeader("soapaction", "\"urn:greet\"");

            request.setEntity(
                    new StringEntity(String.format(WS_MESSAGE_TEMPLATE_B, "Hi", "Joe"), StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = httpclient.execute(request)) {
                Assert.assertEquals(200, response.getStatusLine().getStatusCode());

                HttpEntity entity = response.getEntity();
                String body = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                Assert.assertTrue(body.contains("Hi Joe"));
            }
        }
    }
}
