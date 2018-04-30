/*
 * #%L
 * Wildfly Camel :: Subsystem
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
package org.wildfly.extension.camel.service;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.gravia.runtime.Runtime;
import org.jboss.gravia.runtime.ServiceRegistration;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extension.camel.CamelConstants;
import org.wildfly.extension.gravia.GraviaConstants;

public class CamelEndpointDeploymentSchedulerService implements Service<CamelEndpointDeploymentSchedulerService> {

    private static final Logger LOG = LoggerFactory.getLogger(CamelEndpointDeploymentSchedulerService.class);

    public interface EndpointHttpHandler {
        ClassLoader getClassLoader();

        void service(ServletContext context, HttpServletRequest req, HttpServletResponse resp) throws IOException;
    }

    private final InjectedValue<Runtime> injectedRuntime = new InjectedValue<>();
    private ServiceRegistration<CamelEndpointDeploymentSchedulerService> registration;
    private final Map<URI, EndpointHttpHandler> scheduledHandlers = new HashMap<>();
    private volatile CamelEndpointDeployerService deploymentService;

    public CamelEndpointDeploymentSchedulerService() {
        super();
    }

    public static ServiceController<CamelEndpointDeploymentSchedulerService> addService(ServiceTarget serviceTarget) {
        final CamelEndpointDeploymentSchedulerService service = new CamelEndpointDeploymentSchedulerService();
        return serviceTarget.addService(CamelConstants.CAMEL_ENDPOINT_DEPLOYMENT_SCHEDULER_SERVICE_NAME, service) //
                .addDependency(GraviaConstants.RUNTIME_SERVICE_NAME, Runtime.class, service.injectedRuntime) //
                .install();
    }

    @Override
    public void start(StartContext context) throws StartException {
        Runtime runtime = injectedRuntime.getValue();
        registration = runtime.getModuleContext().registerService(CamelEndpointDeploymentSchedulerService.class, this,
                null);
        LOG.warn("*** scheduler started");
    }

    @Override
    public void stop(StopContext context) {
        if (registration != null) {
            registration.unregister();
        }
    }

    @Override
    public CamelEndpointDeploymentSchedulerService getValue() throws IllegalStateException {
        return this;
    }

    public void schedule(URI uri, EndpointHttpHandler endpointHttpHandler) {
        synchronized (scheduledHandlers) {
            if (this.deploymentService != null) {
                this.deploymentService.deploy(uri, endpointHttpHandler);
            } else {
                scheduledHandlers.put(uri, endpointHttpHandler);
            }
        }
    }

    public void unschedule(URI uri) {
        synchronized (scheduledHandlers) {
            if (this.deploymentService != null) {
                this.deploymentService.undeploy(uri);
            } else {
                scheduledHandlers.remove(uri);
            }
        }
    }

    public void setDeploymentService(CamelEndpointDeployerService deploymentService) {
        synchronized (scheduledHandlers) {
            /* Deploy the endpoints scheduled so far */
            for (Iterator<Entry<URI, EndpointHttpHandler>> it = scheduledHandlers.entrySet().iterator(); it
                    .hasNext();) {
                Entry<URI, EndpointHttpHandler> en = it.next();
                deploymentService.deploy(en.getKey(), en.getValue());
                it.remove();
            }
            this.deploymentService = deploymentService;
        }
    }
}
