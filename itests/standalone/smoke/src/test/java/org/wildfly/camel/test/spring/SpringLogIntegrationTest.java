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

package org.wildfly.camel.test.spring;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.dmr.ModelNode;
import org.jboss.gravia.resource.ManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.utils.DMRUtils;
import org.wildfly.camel.test.common.utils.LogUtils;
import org.wildfly.extension.camel.CamelAware;
import org.wildfly.extension.camel.CamelContextRegistry;

@CamelAware
@RunWith(Arquillian.class)
@ServerSetup({SpringLogIntegrationTest.LogSetupTask.class})
public class SpringLogIntegrationTest {

    private static final String LOGGING_CONTEXT_XML_TEMPLATE = "<beans xmlns=\"http://www.springframework.org/schema/beans\"" //
+ "       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" //
+ "       xsi:schemaLocation=\"http://www.springframework.org/schema/beans" //
+ "                           http://www.springframework.org/schema/beans/spring-beans.xsd" //
+ "                           http://camel.apache.org/schema/spring http://camel.apache.org/schema/spring/camel-spring.xsd\">" //
+ "    <camelContext id=\"%s\" xmlns=\"http://camel.apache.org/schema/spring\">" //
+ "        <route>" //
+ "            <from uri=\"direct:start\"/>" //
+ "            <log message=\"${body}\" loggingLevel=\"INFO\" logName=\"%s\" />" //
+ "        </route>" //
+ "    </camelContext>" //
+ "</beans>";

    private static final String APP1 = "app1";
    private static final String APP2 = "app2";

    private static final String LOG1 = "log1";
    private static final String LOG2 = "log2";

    private static final String CONTEXT1 = "spring-context-1";
    private static final String CONTEXT2 = "spring-context-2";

    private static final Path LOG1_PATH = Paths.get(System.getProperty("jboss.server.log.dir"), LOG1 + ".log");
    private static final Path LOG2_PATH = Paths.get(System.getProperty("jboss.server.log.dir"), LOG2 + ".log");

    private static final String CATEGORY1 = "my.custom.log.category1";
    private static final String CATEGORY2 = "my.custom.log.category2";

    static class LogSetupTask implements ServerSetupTask {

        public static final String LOG1_PROFILE_PREFIX = "subsystem=logging/logging-profile="+LOG1;
        public static final String LOG2_PROFILE_PREFIX = "subsystem=logging/logging-profile="+LOG2;

        @Override
        public void setup(ManagementClient managementClient, String s) throws Exception {
            ModelNode batchNode = DMRUtils.batchNode()
                .addStep(LOG1_PROFILE_PREFIX, "add")
                .addStep(LOG1_PROFILE_PREFIX + "/file-handler="+ LOG1, "add(file={path=>camel-log-test.log,relative-to=>jboss.server.log.dir})")
                .addStep(LOG1_PROFILE_PREFIX + "/file-handler="+ LOG1, "change-log-level(level=INFO))")
                .addStep(LOG1_PROFILE_PREFIX + "/logger="+ CATEGORY1, "add(level=INFO,handlers=[handler="+ LOG1 +"])")
                .addStep(LOG2_PROFILE_PREFIX, "add")
                .addStep(LOG2_PROFILE_PREFIX + "/file-handler="+ LOG2, "add(file={path=>camel-log-test.log,relative-to=>jboss.server.log.dir})")
                .addStep(LOG2_PROFILE_PREFIX + "/file-handler="+ LOG2, "change-log-level(level=INFO))")
                .addStep(LOG2_PROFILE_PREFIX + "/logger="+ CATEGORY2, "add(level=INFO,handlers=[handler="+ LOG2 +"])")
                .build();

            ModelNode result = managementClient.getControllerClient().execute(batchNode);
            Assert.assertEquals("success", result.get("outcome").asString());
        }

        @Override
        public void tearDown(ManagementClient managementClient, String s) throws Exception {
            ModelNode batchNode = DMRUtils.batchNode() //
                    .addStep(LOG1_PROFILE_PREFIX, "remove") //
                    .addStep(LOG2_PROFILE_PREFIX, "remove") //
                    .build();
            managementClient.getControllerClient().execute(batchNode);
        }
    }

    @ArquillianResource
    CamelContextRegistry contextRegistry;

    @ArquillianResource
    public Deployer deployer;

    @Deployment(name = APP1, managed = false)
    public static JavaArchive app1() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "spring-log-tests"+APP1)
            .addClasses(LogUtils.class)
            .setManifest(() -> {
                ManifestBuilder builder = new ManifestBuilder();
                builder.addManifestHeader("Logging-Profile", LOG1);
                return builder.openStream();
            })
            .addAsResource(new StringAsset(String.format(LOGGING_CONTEXT_XML_TEMPLATE, CONTEXT1, CATEGORY1)), "logging-camel-context.xml");
        return archive;
    }

    @Deployment(name = APP2, managed = false)
    public static JavaArchive app2() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "spring-log-tests"+APP2)
            .addClasses(LogUtils.class)
            .setManifest(() -> {
                ManifestBuilder builder = new ManifestBuilder();
                builder.addManifestHeader("Logging-Profile", LOG2);
                return builder.openStream();
            })
            .addAsResource(new StringAsset(String.format(LOGGING_CONTEXT_XML_TEMPLATE, CONTEXT2, CATEGORY2)), "logging-camel-context.xml");
        return archive;
    }

    /**
     * @return an empty jar without which Arquillian won't inject the {@link #contextRegistry}
     */
    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class);
    }

    @Test
    public void concurrentDeployments() throws Exception {

        deployer.deploy(APP1);
        deployer.deploy(APP2);

        CamelContext camelctx1 = contextRegistry.getCamelContext(CONTEXT1);
        ProducerTemplate producer1 = camelctx1.createProducerTemplate();
        producer1.requestBody("direct:start", "Hello 1");


        CamelContext camelctx2 = contextRegistry.getCamelContext(CONTEXT2);
        ProducerTemplate producer2 = camelctx2.createProducerTemplate();
        producer2.requestBody("direct:start", "Hello 2");

        assertLogFileContent(".*"+ CATEGORY1 +".*Hello 1$", LOG1_PATH, true);
        assertLogFileContent(".*"+ CATEGORY2 +".*Hello 2$", LOG2_PATH, true);

        assertLogFileContent(".*"+ CATEGORY2 +".*Hello 2$", LOG1_PATH, false);
        assertLogFileContent(".*"+ CATEGORY1 +".*Hello 1$", LOG2_PATH, false);

        deployer.undeploy(APP2);
        deployer.undeploy(APP1);

    }

    private static void assertLogFileContent(String pattern, Path file, boolean expected) throws IOException {
        if (expected) {
            boolean logMessagePresent = LogUtils.awaitLogMessage(pattern, 5000, file);
            Assert.assertTrue("Gave up waiting for message "+ pattern + " in file "+ file.toString(), logMessagePresent);
        } else {
            boolean logMessagePresent = LogUtils.awaitLogMessage(pattern, 0, file);
            Assert.assertFalse("Message "+ pattern + " not found in file "+ file.toString(), logMessagePresent);
        }
    }
}
