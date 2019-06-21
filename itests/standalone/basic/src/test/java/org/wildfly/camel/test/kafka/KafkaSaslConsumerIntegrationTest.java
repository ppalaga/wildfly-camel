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

package org.wildfly.camel.test.kafka;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.kafka.EmbeddedKafkaBroker;
import org.wildfly.camel.test.common.utils.TestUtils;
import org.wildfly.camel.test.common.zookeeper.EmbeddedZookeeper;
import org.wildfly.extension.camel.CamelAware;

@CamelAware
@RunWith(Arquillian.class)
public class KafkaSaslConsumerIntegrationTest {

    private static final String JAVA_SECURITY_AUTH_LOGIN_CONFIG = "java.security.auth.login.config";
    private static final int KAFKA_PORT = 9092;
    public static final String TOPIC = "test";
    private static final String SERVER_JAAS_TEMPLATE = "KafkaServer {\n" //
            + "   org.apache.kafka.common.security.plain.PlainLoginModule required\n" //
            + "   username=\"admin\"\n" //
            + "   password=\"admin-secret\"\n" //
            + "   user_admin=\"admin-secret\"\n" //
            + "   user_kafkabroker1=\"kafkabroker1-secret\"\n" //
            + "   user_%s=\"%s\"\n" //
            + "   user_%s=\"%s\";\n" //
            + "};";
    private static final String CONSUMER_SECRET = "consumer1-secret";
    private static final String CONSUMER_USER = "consumer1";
    private static final String PRODUCER_SECRET = "producer1-secret";
    private static final String PRODUCER_USER = "producer1";

    static EmbeddedZookeeper embeddedZookeeper;
    static EmbeddedKafkaBroker embeddedKafkaBroker;
    private static String javaSecurityLoginConfig;

    @Deployment
    public static JavaArchive deployment() {
        return ShrinkWrap.create(JavaArchive.class, "kafka-consumer-tests.jar").addClasses(TestUtils.class,
                EmbeddedZookeeper.class, EmbeddedKafkaBroker.class);
    }

    @BeforeClass
    public static void before() throws Exception {
        final Path serverJaasPath = Paths.get("target/kafka_server_jaas.conf");
        Files.write(serverJaasPath,
                String.format(SERVER_JAAS_TEMPLATE, CONSUMER_USER, CONSUMER_SECRET, PRODUCER_USER, PRODUCER_SECRET)
                        .getBytes(StandardCharsets.UTF_8));

        javaSecurityLoginConfig = System.getProperty(JAVA_SECURITY_AUTH_LOGIN_CONFIG);
        System.setProperty(JAVA_SECURITY_AUTH_LOGIN_CONFIG, serverJaasPath.toString());

        embeddedZookeeper = new EmbeddedZookeeper();
        List<Integer> kafkaPorts = Collections.singletonList(KAFKA_PORT);
        final Properties props = new Properties();
        props.put("sasl.enabled.mechanisms", "PLAIN");
        props.put("sasl.mechanism.inter.broker.protocol", "PLAIN");
        props.put("security.inter.broker.protocol", "SASL_PLAINTEXT");
        props.put("listeners", "SASL_PLAINTEXT://localhost:" + KAFKA_PORT);
        props.put("advertised.listeners", "SASL_PLAINTEXT://localhost:" + KAFKA_PORT);

        embeddedKafkaBroker = new EmbeddedKafkaBroker(embeddedZookeeper.getConnection(), props, kafkaPorts);

        embeddedZookeeper.startup(1, TimeUnit.SECONDS);
        System.out.println("### Embedded Zookeeper connection: " + embeddedZookeeper.getConnection());

        embeddedKafkaBroker.startup();
        System.out.println("### Embedded Kafka cluster broker list: " + embeddedKafkaBroker.getBrokerList());
    }

    @AfterClass
    public static void after() throws Exception {
        embeddedKafkaBroker.shutdown();
        embeddedZookeeper.shutdown();
        if (javaSecurityLoginConfig != null) {
            System.setProperty(JAVA_SECURITY_AUTH_LOGIN_CONFIG, javaSecurityLoginConfig);
        } else {
            System.getProperties().remove(JAVA_SECURITY_AUTH_LOGIN_CONFIG);
        }
    }

    @Test
    public void kaftMessageIsConsumedByCamel() throws Exception {

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("kafka:" + TOPIC + "?groupId=group1&autoOffsetReset=earliest&autoCommitEnable=true"
                        + "&securityProtocol=SASL_PLAINTEXT"
                        + "&saslMechanism=PLAIN"
                        + "&saslJaasConfig=org.apache.kafka.common.security.plain.PlainLoginModule required username=\""
                        + CONSUMER_USER + "\" password=\"" + CONSUMER_SECRET + "\";")
                .to("mock:result");
            }
        });

        KafkaComponent kafka = new KafkaComponent();
        kafka.setBrokers("localhost:" + KAFKA_PORT);
        camelctx.addComponent("kafka", kafka);

        MockEndpoint to = camelctx.getEndpoint("mock:result", MockEndpoint.class);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        to.expectedMessageCount(5);

        camelctx.start();
        try (KafkaProducer<String, String> producer = createKafkaProducer()) {
            for (int k = 0; k < 5; k++) {
                final String msg = "message-" + k;
                final ProducerRecord<String, String> data = new ProducerRecord<String, String>(TOPIC, "1", msg);
                producer.send(data);
            }
            to.assertIsSatisfied(3000);
        } finally {
            camelctx.stop();
        }
    }

    private KafkaProducer<String, String> createKafkaProducer() {
        Properties props = new Properties();
        props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\""
                + PRODUCER_USER + "\" password=\"" + PRODUCER_SECRET + "\";");
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + KAFKA_PORT);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_PARTITIONER);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread()
                    .setContextClassLoader(org.apache.kafka.clients.producer.KafkaProducer.class.getClassLoader());
            return new KafkaProducer<>(props);
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }
}
