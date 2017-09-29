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

package org.wildfly.camel.test.stomp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.utils.JMSUtils;
import org.wildfly.extension.camel.CamelAware;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@CamelAware
@RunWith(Arquillian.class)
@ServerSetup({ StompIntegrationTest.JmsQueueSetup.class })
public class StompIntegrationTest {

    private static final int STOMP_PORT = 61613;
    static final String QUEUE_NAME = "stompq";
    static final String QUEUE_JNDI_NAME = "java:/" + QUEUE_NAME;
    static final String ACTIVEMQ_USER = "user1";
    static final String ACTIVEMQ_PASSWORD = "appl-pa$$wrd1";

    @ArquillianResource
    InitialContext initialctx;

    static class JmsQueueSetup implements ServerSetupTask {

        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {
            JMSUtils.createJmsQueue(QUEUE_NAME, QUEUE_JNDI_NAME, managementClient.getControllerClient());
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            JMSUtils.removeJmsQueue(QUEUE_NAME, managementClient.getControllerClient());
        }
    }

    @Deployment
    public static JavaArchive createdeployment() {
        return ShrinkWrap.create(JavaArchive.class, "camel-stomp-tests");
    }

    @Test
    public void testMessageConsumerRoute() throws Exception {

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("stomp:jms.queue.%s?brokerURL=tcp://127.0.0.1:%d&login=%s&passcode=%s", QUEUE_NAME, STOMP_PORT,
                        ACTIVEMQ_USER, ACTIVEMQ_PASSWORD) //
                                .transform(body().method("utf8").prepend("Hello "))//
                                .to("mock:result");
            }
        });

        camelctx.start();

        MockEndpoint mockEndpoint = camelctx.getEndpoint("mock:result", MockEndpoint.class);
        mockEndpoint.setExpectedCount(1);
        mockEndpoint.expectedBodiesReceived("Hello Kermit");
        final ConnectionFactory cfactory = (ConnectionFactory) initialctx.lookup("java:/ConnectionFactory");
        Connection connection = null;

        try {

            connection = cfactory.createConnection();
            sendMessage(connection, QUEUE_JNDI_NAME, "Kermit");

            mockEndpoint.assertIsSatisfied();
        } finally {
            if (connection != null) {
                connection.close();
            }
            camelctx.stop();
        }
    }

    @Test
    public void testMessageProviderRoute() throws Exception {

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").
                transform(body().prepend("Hello ")).
                toF("stomp:jms.queue.%s?brokerURL=tcp://127.0.0.1:%d&login=%s&passcode=%s", QUEUE_NAME, STOMP_PORT,
                        ACTIVEMQ_USER, ACTIVEMQ_PASSWORD);
            }
        });

        final List<String> result = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        camelctx.start();

        ConnectionFactory cfactory = (ConnectionFactory) initialctx.lookup("java:/ConnectionFactory");
        Connection connection = null;
        try {

            connection = cfactory.createConnection();
            try {
                receiveMessage(connection, QUEUE_JNDI_NAME, new MessageListener() {
                    @Override
                    public void onMessage(Message message) {
                        BytesMessage text = (BytesMessage) message;
                        try {
                            int len = (int) text.getBodyLength();
                            byte[] bytes = new byte[len];
                            if (len != text.readBytes(bytes)) {
                                result.add("Unexpected read length");
                            }
                            result.add(new String(bytes, StandardCharsets.UTF_8));
                        } catch (JMSException ex) {
                            result.add(ex.getMessage());
                        }
                        latch.countDown();
                    }
                });

                ProducerTemplate producer = camelctx.createProducerTemplate();
                producer.asyncSendBody("direct:start", "Kermit");

                Assert.assertTrue(latch.await(20, TimeUnit.SECONDS));
                Assert.assertEquals("One message", 1, result.size());
                Assert.assertEquals("Hello Kermit", result.get(0));
            } finally {
                connection.close();
            }
        } finally {
            camelctx.stop();
        }
    }

    private void sendMessage(Connection connection, String jndiName, String message) throws Exception {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = (Destination) initialctx.lookup(jndiName);
        MessageProducer producer = session.createProducer(destination);
        TextMessage msg = session.createTextMessage(message);
        producer.send(msg);
        connection.start();
    }

    private Session receiveMessage(Connection connection, String jndiName, MessageListener listener) throws Exception {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = (Destination) initialctx.lookup(jndiName);
        MessageConsumer consumer = session.createConsumer(destination);
        consumer.setMessageListener(listener);
        connection.start();
        return session;
    }
}
