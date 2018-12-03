/*
 * #%L
 * Wildfly Camel :: Testsuite
 * %%
 * Copyright (C) 2013 - 2018 RedHat
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
package org.wildfly.camel.test.rest.dsl;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.rest.dsl.secure.TestClient;
import org.wildfly.camel.test.rest.dsl.subB.Routes1;
import org.wildfly.camel.test.rest.dsl.subB.Routes2;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@RunAsClient
@RunWith(Arquillian.class)
public class UndertowRestDslMultiWarIntegrationTest {
    private static final String APP_1 = "UndertowRestDslMultiWarIntegrationTest1.war";
    private static final String APP_2 = "UndertowRestDslMultiWarIntegrationTest2.war";

    @ArquillianResource
    Deployer deployer;

    private static WebArchive app(String war, Class<?> routeBuilder) {
        final WebArchive archive = ShrinkWrap.create(WebArchive.class, war)
                .addClasses(TestClient.class, routeBuilder)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        return archive;
    }

    @Deployment(name = APP_1, managed = false)
    public static WebArchive app1() {
        return app(APP_1, Routes1.class);
    }

    @Deployment(name = APP_2, managed = false)
    public static WebArchive app2() {
        return app(APP_2, Routes2.class);
    }

    @Deployment
    public static WebArchive dummy() {
        return ShrinkWrap.create(WebArchive.class, "UndertowRestDslMultiWarIntegrationTest.war");
    }

    @Test
    public void multiWar() throws Exception {
        try (TestClient c = new TestClient()) {
            c.assertResponse("/test1", "GET", 404);
            c.assertResponse("/test2", "GET", 404);
            deployer.deploy(APP_1);
            c.assertResponse("/test1", "GET", 200);
            c.assertResponse("/test2", "GET", 404);
            try {
                deployer.deploy(APP_2);
                try {
                    c.assertResponse("/test1", "GET", 200);
                    c.assertResponse("/test2", "GET", 200);
                } finally {
                    deployer.undeploy(APP_2);
                }
                c.assertResponse("/test1", "GET", 200);
                c.assertResponse("/test2", "GET", 404);
            } finally {
                deployer.undeploy(APP_1);
            }
            c.assertResponse("/test1", "GET", 404);
            c.assertResponse("/test2", "GET", 404);
        }
    }

}
