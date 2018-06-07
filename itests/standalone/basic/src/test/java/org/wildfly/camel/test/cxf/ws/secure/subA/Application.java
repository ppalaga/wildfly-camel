/*
 * #%L
 * Wildfly Camel :: Example :: Camel CXF JAX-WS CDI Secure
 * %%
 * Copyright (C) 2013 - 2017 RedHat
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
package org.wildfly.camel.test.cxf.ws.secure.subA;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.component.cxf.CxfComponent;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named("cxf_cdi_security_app")
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    private static final String CXF_PRODUCER_ENDPOINT_ADDRESS = "https://localhost:8443/webservices/greeting-secure-cdi";
    private static final String CXF_CONSUMER_ENDPOINT_ADDRESS = "https://localhost:8443/webservices/greeting-secure-cdi";

    @Inject
    @ContextName("cxfws-secure-cdi-camel-context")
    CamelContext camelContext;

    @Named("greetingsProcessor")
    @Produces
    public Processor produceGreetingsProcessor() {
        return new GreetingsProcessor();
    }

    @Named("cxfProducerEndpoint")
    @Produces
    public CxfEndpoint createCxfProducerEndpoint() {
        CxfComponent cxfProducerComponent = new CxfComponent(this.camelContext);
        CxfEndpoint cxfProducerEndpoint = new CxfEndpoint(CXF_PRODUCER_ENDPOINT_ADDRESS, cxfProducerComponent);
        cxfProducerEndpoint.setBeanId("cxfProducerEndpoint");
        cxfProducerEndpoint.setServiceClass(GreetingService.class);

        // Not for use in production
        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                log.warn("=== hostname = "+ hostname);
                return true;
            }
        };
        cxfProducerEndpoint.setHostnameVerifier(hostnameVerifier);

        return cxfProducerEndpoint;
    }

    @Named("cxfConsumerEndpoint")
    @Produces
    public CxfEndpoint createCxfConsumerEndpoint() {
        CxfComponent cxfConsumerComponent = new CxfComponent(this.camelContext);
        CxfEndpoint cxfConsumerEndpoint = new CxfEndpoint(CXF_CONSUMER_ENDPOINT_ADDRESS, cxfConsumerComponent);
        cxfConsumerEndpoint.setBeanId("cxfConsumerEndpoint");
        cxfConsumerEndpoint.setServiceClass(GreetingService.class);
        return cxfConsumerEndpoint;
    }

}
