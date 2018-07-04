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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.modules.ModuleClassLoader;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.extension.camel.CamelLogger;

/**
 * A service that either schedules HTTP endpoints for deployment once {@link #deployerService} becomes available or
 * deploys them instantly if the {@link #deployerService} is available already. The split between
 * {@link CamelEndpointDeploymentSchedulerService} and {@link CamelEndpointDeployerService} is necessary because the
 * requests to deploy HTTP endpoints may come in phases before the {@link CamelEndpointDeployerService} is available.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class CamelEndpointDeploymentSchedulerService implements Service<CamelEndpointDeploymentSchedulerService> {

    static class ContextPath implements Comparable<ContextPath> {
        private final String source;
        private final String[] segments;

        public ContextPath(String contextPath) {
            this.source = contextPath;
            this.segments = contextPath.split("/");
        }

        /** {@link ContextPath}s with more segments go first, then alphabetically per segment */
        @Override
        public int compareTo(ContextPath other) {
            if (this.source.equals(other.source)) {
                return 0;
            } else if (this.segments.length > other.segments.length) {
                return -1;
            } else if (this.segments.length < other.segments.length) {
                return -1;
            } else {
                /* equal number of segments */
                for (int i = 0; i < this.segments.length; i++) {
                    final int comp = this.segments[i].compareTo(other.segments[i]);
                    if (comp != 0) {
                        return comp;
                    }
                }
                return 0;
            }
        }

    }

    public interface EndpointHttpHandler {
        ClassLoader getClassLoader();

        void service(ServletContext context, HttpServletRequest req, HttpServletResponse resp) throws IOException;
    }

    /** The name for the {@link CamelEndpointDeploymentSchedulerService} */
    private static final String SERVICE_NAME = "EndpointDeploymentScheduler";

    public static ServiceController<CamelEndpointDeploymentSchedulerService> addService(
            ServiceName deploymentUnitServiceName, String deploymentName, int subDeploymentsCount, ServiceTarget serviceTarget) {
        CamelLogger.LOGGER.warn("deploymentUnitServiceName of {} = {}", deploymentName, deploymentUnitServiceName);
        final CamelEndpointDeploymentSchedulerService service = new CamelEndpointDeploymentSchedulerService(deploymentName, subDeploymentsCount);
        return serviceTarget.addService(deploymentSchedulerServiceName(deploymentUnitServiceName), service) //
                .install();
    }

    public static ServiceName deploymentSchedulerServiceName(ClassLoader deploymentClassLoader) {
        if (deploymentClassLoader == null) {
            deploymentClassLoader = SecurityActions.getContextClassLoader();
        }
        if (deploymentClassLoader instanceof ModuleClassLoader) {
            ModuleClassLoader moduleClassLoader = (ModuleClassLoader) deploymentClassLoader;
            final String DEPLOYMEND_CL_NAME_PREFIX = "deployment.";
            final String clName = moduleClassLoader.getName();
            if (clName.startsWith(DEPLOYMEND_CL_NAME_PREFIX)) {
                final String deploymentName = clName.substring(DEPLOYMEND_CL_NAME_PREFIX.length());
                return ServiceName.of("jboss", "deployment", "unit", deploymentName, SERVICE_NAME);
            } else {
                throw new IllegalStateException(String.format("Expected a %s name starting with '%s'; found %s", ModuleClassLoader.class.getName(), DEPLOYMEND_CL_NAME_PREFIX, clName));
            }
        } else {
            throw new IllegalStateException(String.format("Expected a %s; found %s", ModuleClassLoader.class.getName(), deploymentClassLoader));
        }
    }
    public static ServiceName deploymentSchedulerServiceName(ServiceName deploymentUnitServiceName) {
        return deploymentUnitServiceName.append(SERVICE_NAME);
    }
    private final String deploymentName;

    private final Map<URI, EndpointHttpHandler> scheduledHandlers = new LinkedHashMap<>();
    private final Map<ContextPath, CamelEndpointDeployerService> deployers = new TreeMap<>();

    private final int subDeploymentsCount;

    /** Used to lock both {@link #deployers} an {@link #scheduledHandlers} */
    private final Object lock = new Object();

    CamelEndpointDeploymentSchedulerService(String deploymentName, int subDeploymentsCount) {
        super();
        this.deploymentName = deploymentName;
        this.subDeploymentsCount = subDeploymentsCount;
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
        synchronized (lock) {
            CamelLogger.LOGGER.warn("Scheduling a deployment of endpoint {} from {}", uri, deploymentName);
            if (deployers.size() >= subDeploymentsCount) {
                /* Deploy immediately */
                final CamelEndpointDeployerService matchingDeploymentService = findDeployer(uri);
                matchingDeploymentService.deploy(uri, endpointHttpHandler);
            } else {
                /* Schedule the deployment */
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
    public void registerDeployer(String contextPath, CamelEndpointDeployerService registeringDeploymentService) {
        synchronized (lock) {
            deployers.put(new ContextPath(contextPath), registeringDeploymentService);
            if (deployers.size() >= subDeploymentsCount) {
                /* Deploy the endpoints scheduled so far */
                for (Iterator<Entry<URI, EndpointHttpHandler>> it = scheduledHandlers.entrySet().iterator(); it
                        .hasNext();) {
                    final Entry<URI, EndpointHttpHandler> en = it.next();
                    final URI uri = en.getKey();
                    final CamelEndpointDeployerService matchingDeploymentService = findDeployer(uri);
                    matchingDeploymentService.deploy(uri, en.getValue());
                    it.remove();
                }
            }
        }
    }

    @Override
    public void start(StartContext context) throws StartException {
        CamelLogger.LOGGER.warn("{} started for deployment {}", CamelEndpointDeploymentSchedulerService.class.getSimpleName(), deploymentName);
    }

    @Override
    public void stop(StopContext context) {
    }

    /**
     * Either removes the given HTTP endpoint from the list of deployments scheduled for deployment or undeploys it
     * instantly if the {@link #deployerService} is available.
     *
     * @param uri
     *                determines the path and protocol under which the HTTP endpoint should be exposed
     */
    public void unschedule(URI uri) {
        synchronized (lock) {
            CamelLogger.LOGGER.warn("Unscheduling a deployment of endpoint {} from {}", uri, deploymentName);
            final CamelEndpointDeployerService deploymentService = findDeployer(uri);
            if (deploymentService != null) {
                deploymentService.undeploy(uri);
            }
            scheduledHandlers.remove(uri);
        }
    }

    private CamelEndpointDeployerService findDeployer(URI uri) {
        synchronized (lock) {
            if (deployers.size() == 1) {
                return deployers.values().iterator().next();
            } else {
                String path = uri.getPath();
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                for (Entry<ContextPath, CamelEndpointDeployerService> en : deployers.entrySet()) {
                    if (en.getKey().source.startsWith(path)) {
                        return en.getValue();
                    }
                }
                return null;
            }
        }
    }
}
