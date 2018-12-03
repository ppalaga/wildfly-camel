package org.wildfly.camel.test.rest.dsl.subB;

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;

@ApplicationScoped
@ContextName("UndertowRestDslMultiWarIntegrationTest2-camel-context")
public class Routes2 extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        restConfiguration()
        .host("localhost")
        .port(8080)
        .component("undertow")
    ;

    rest()
        .get("/test2")
            .route()
               .setBody(constant("GET: /test2"))
            .endRest()
    ;
    }

}