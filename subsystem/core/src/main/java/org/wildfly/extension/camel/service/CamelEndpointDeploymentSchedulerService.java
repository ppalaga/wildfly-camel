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
import org.wildfly.extension.camel.CamelConstants;
import org.wildfly.extension.camel.CamelLogger;
import org.wildfly.extension.gravia.GraviaConstants;

/**
 * A service that either schedules HTTP endpoints for deployment once {@link #deployerService} becomes available or
 * deploys them instantly if the {@link #deployerService} is available already. The split between
 * {@link CamelEndpointDeploymentSchedulerService} and {@link CamelEndpointDeployerService} is necessary because the
 * requests to deploy HTTP endpoints may come in phases before the {@link CamelEndpointDeployerService} is available.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class CamelEndpointDeploymentSchedulerService implements Service<CamelEndpointDeploymentSchedulerService> {

    public interface EndpointHttpHandler {
        ClassLoader getClassLoader();

        void service(ServletContext context, HttpServletRequest req, HttpServletResponse resp) throws IOException;
    }

    public static ServiceController<CamelEndpointDeploymentSchedulerService> addService(
            ServiceName deploymentUnitServiceName, ServiceTarget serviceTarget) {
        final CamelEndpointDeploymentSchedulerService service = new CamelEndpointDeploymentSchedulerService();
        return serviceTarget.addService(deploymentSchedulerServiceName(deploymentUnitServiceName), service) //
                .addDependency(GraviaConstants.RUNTIME_SERVICE_NAME, Runtime.class, service.injectedRuntime) //
                .install();
    }

    public static ServiceName deploymentSchedulerServiceName(ServiceName deploymentUnitServiceName) {
        return deploymentUnitServiceName.append(SERVICE_NAME);
    }

    private volatile CamelEndpointDeployerService deployerService;
    private final InjectedValue<Runtime> injectedRuntime = new InjectedValue<>();
    private ServiceRegistration<CamelEndpointDeploymentSchedulerService> registration;
    private final Map<URI, EndpointHttpHandler> scheduledHandlers = new HashMap<>();

    /** The name for the {@link CamelEndpointDeploymentSchedulerService} */
    private static final String SERVICE_NAME = "EndpointDeploymentScheduler";

    public CamelEndpointDeploymentSchedulerService() {
        super();
    }

    @Override
    public CamelEndpointDeploymentSchedulerService getValue() throws IllegalStateException {
        return this;
    }

    /**
     * Either schedules the given HTTP endpoint for deployment once {@link #deployerService} becomes available or
     * deploys it instantly if the {@link #deployerService} is available already.
     *
     * @param uri
     *                                determines the path and protocol under which the HTTP endpoint should be exposed
     * @param endpointHttpHandler
     *                                an {@link EndpointHttpHandler} to use for handling HTTP requests sent to the given
     *                                {@link URI}'s path
     */
    public void schedule(URI uri, EndpointHttpHandler endpointHttpHandler) {
        synchronized (scheduledHandlers) {
            CamelLogger.LOGGER.debug("Scheduling a deployment of endpoint {}", uri);
            if (this.deployerService != null) {
                this.deployerService.deploy(uri, endpointHttpHandler);
            } else {
                scheduledHandlers.put(uri, endpointHttpHandler);
            }
        }
    }

    /**
     * Sets the {@link CamelEndpointDeployerService} and deploys any endpoints scheduled for deployment so far.
     *
     * @param deploymentService
     *                              the {@link CamelEndpointDeployerService}
     */
    public void setDeploymentServiceAndDeploy(CamelEndpointDeployerService deploymentService) {
        synchronized (scheduledHandlers) {
            /* Deploy the endpoints scheduled so far */
            for (Iterator<Entry<URI, EndpointHttpHandler>> it = scheduledHandlers.entrySet().iterator(); it
                    .hasNext();) {
                Entry<URI, EndpointHttpHandler> en = it.next();
                deploymentService.deploy(en.getKey(), en.getValue());
                it.remove();
            }
            this.deployerService = deploymentService;
        }
    }

    @Override
    public void start(StartContext context) throws StartException {
        Runtime runtime = injectedRuntime.getValue();
        registration = runtime.getModuleContext().registerService(CamelEndpointDeploymentSchedulerService.class, this,
                null);
        CamelLogger.LOGGER.debug("{} started", CamelEndpointDeploymentSchedulerService.class.getSimpleName());
    }

    @Override
    public void stop(StopContext context) {
        if (registration != null) {
            registration.unregister();
        }
    }

    /**
     * Either removes the given HTTP endpoint from the list of deployments scheduled for deployment or undeploys it
     * instantly if the {@link #deployerService} is available.
     *
     * @param uri
     *                determines the path and protocol under which the HTTP endpoint should be exposed
     */
    public void unschedule(URI uri) {
        synchronized (scheduledHandlers) {
            CamelLogger.LOGGER.debug("Unscheduling a deployment of endpoint {}", uri);
            if (this.deployerService != null) {
                this.deployerService.undeploy(uri);
            } else {
                scheduledHandlers.remove(uri);
            }
        }
    }
}
