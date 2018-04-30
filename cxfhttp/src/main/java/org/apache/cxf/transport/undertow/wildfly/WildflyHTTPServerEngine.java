/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.transport.undertow.wildfly;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.transport.undertow.AbstractHTTPServerEngine;
import org.apache.cxf.transport.undertow.UndertowHTTPHandler;
import org.jboss.gravia.runtime.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extension.camel.service.CamelDynamicDeploymentService;

class WildflyHTTPServerEngine extends AbstractHTTPServerEngine {
    private static final Logger LOG = LoggerFactory.getLogger(WildflyHTTPServerEngine.class);
    private final CamelDynamicDeploymentService camelDynamicDeploymentService;

    WildflyHTTPServerEngine(String protocol, String host, int port) {
        super(protocol, host, port);
        camelDynamicDeploymentService = ServiceLocator.getService(CamelDynamicDeploymentService.class);
        LOG.warn("camelDynamicDeploymentService = " + camelDynamicDeploymentService);
    }

    public void addServant(URL nurl, UndertowHTTPHandler handler) {

        if (camelDynamicDeploymentService != null) {
            BiFunction<HttpServletRequest, HttpServletResponse, Void> handlerFunction = (req, res) -> {
                try {
                    handler.getHTTPDestination().doService(req, res);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return null;
            };
            Consumer<ServletContext> servletContextConsumer = (servletContext) -> {
                handler.getHTTPDestination().setServletContext(servletContext);
            };

            try {
                camelDynamicDeploymentService.deploy(nurl.toURI(), handlerFunction, servletContextConsumer,
                        WildflyHTTPServerEngine.class.getClassLoader());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public void removeServant(URL nurl) {
        try {
            camelDynamicDeploymentService.undeploy(nurl.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
