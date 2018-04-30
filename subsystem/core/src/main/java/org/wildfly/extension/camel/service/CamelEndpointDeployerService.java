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
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.HttpHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.core.ManagedServlet;

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
import org.wildfly.extension.camel.service.CamelEndpointDeploymentSchedulerService.EndpointHttpHandler;
import org.wildfly.extension.undertow.Host;

public class CamelEndpointDeployerService implements Service<CamelEndpointDeployerService> {

    private static final Logger LOG = LoggerFactory.getLogger(CamelEndpointDeployerService.class);

    @SuppressWarnings("serial")
    static class EndpointServlet extends HttpServlet {

        public static final String NAME = "EndpointServlet";
        private EndpointHttpHandler endpointHttpHandler;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            LOG.error("*** DefaultServlet", new RuntimeException());
            endpointHttpHandler.service(getServletContext(), req, res);
        }

        public void setEndpointHttpHandler(EndpointHttpHandler endpointHttpHandler) {
            this.endpointHttpHandler = endpointHttpHandler;
        }

    }

    private final Map<URI, Deployment> deployments = new HashMap<>();
    private final InjectedValue<Host> injectedDefaultHost = new InjectedValue<>();
    private final InjectedValue<DeploymentInfo> injectedMainDeploymentInfo = new InjectedValue<>();
    private final InjectedValue<CamelEndpointDeploymentSchedulerService> injectedCamelEndpointDeploymentSchedulerService = new InjectedValue<>();
    // private org.wildfly.extension.undertow.ApplicationSecurityDomainDefinition.Registration
    // securityDomainRegistration;

    public static ServiceController<CamelEndpointDeployerService> addService(ServiceTarget serviceTarget,
            ServiceName deploymentInfoServiceName, ServiceName hostServiceName) {
        final CamelEndpointDeployerService service = new CamelEndpointDeployerService();
        return serviceTarget.addService(CamelConstants.CAMEL_ENDPOINT_DEPLOYER_SERVICE_NAME, service) //
                .addDependency(hostServiceName, Host.class, service.injectedDefaultHost) //
                .addDependency(deploymentInfoServiceName, DeploymentInfo.class, service.injectedMainDeploymentInfo) //
                .addDependency(CamelConstants.CAMEL_ENDPOINT_DEPLOYMENT_SCHEDULER_SERVICE_NAME, CamelEndpointDeploymentSchedulerService.class, service.injectedCamelEndpointDeploymentSchedulerService) //
                .install();
    }

    @Override
    public void start(StartContext context) throws StartException {
        /*
         * Now that the injectedMainDeploymentInfo is ready, we can link this to CamelEndpointDeploymentSchedulerService
         */
        injectedCamelEndpointDeploymentSchedulerService.getValue().setDeploymentService(this);
        LOG.warn("*** deployer started");
    }

    @Override
    public void stop(StopContext context) {
    }

    @Override
    public CamelEndpointDeployerService getValue() throws IllegalStateException {
        return this;
    }
    //
    // private static io.undertow.servlet.api.TransportGuaranteeType transportGuaranteeType(URI uri,
    // final org.jboss.metadata.web.spec.TransportGuaranteeType type) {
    // if (type != null) {
    // switch (type) {
    // case CONFIDENTIAL:
    // return io.undertow.servlet.api.TransportGuaranteeType.CONFIDENTIAL;
    // case INTEGRAL:
    // return io.undertow.servlet.api.TransportGuaranteeType.INTEGRAL;
    // case NONE:
    // return io.undertow.servlet.api.TransportGuaranteeType.NONE;
    // }
    // } else if (uri.getScheme().equals("https")) {
    // return io.undertow.servlet.api.TransportGuaranteeType.CONFIDENTIAL;
    // } else {
    // return io.undertow.servlet.api.TransportGuaranteeType.NONE;
    // }
    // throw new RuntimeException("UNREACHABLE");
    // }

    public void deploy(URI uri, EndpointHttpHandler endpointHttpHandler) {

        final ServletInfo servletInfo = Servlets.servlet(EndpointServlet.NAME, EndpointServlet.class).addMapping("/*")
                .setAsyncSupported(true);

        final DeploymentInfo mainDeploymentInfo = injectedMainDeploymentInfo.getValue();
        DeploymentInfo endPointDeplyomentInfo = mainDeploymentInfo.clone();

        try {
            Field servletsField = DeploymentInfo.class.getDeclaredField("servlets");
            servletsField.setAccessible(true);
            Map<?, ?> servlets = (Map<?, ?>) servletsField.get(endPointDeplyomentInfo);
            servlets.clear();
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        endPointDeplyomentInfo //
                .setContextPath(uri.getPath()) //
                .setDeploymentName(uri.getPath()) //
                .addServlets(servletInfo);

        Set<String> securityRoleNames = new HashSet<>(Arrays.asList("testRole"));
        LOG.warn("securityRoleNames = " + securityRoleNames);
        // if (metaData.getSecurityConstraints() != null) {
        // for (SecurityConstraintMetaData constraint : metaData.getSecurityConstraints()) {
        // SecurityConstraint securityConstraint = new SecurityConstraint()
        // .setTransportGuaranteeType(transportGuaranteeType(uri, constraint.getTransportGuarantee()));
        //
        // List<String> roleNames = constraint.getRoleNames();
        // LOG.warn("roleNames = " + roleNames);
        // if (constraint.getAuthConstraint() == null) {
        // // no auth constraint means we permit the empty roles
        // securityConstraint.setEmptyRoleSemantic(EmptyRoleSemantic.PERMIT);
        // } else if (roleNames.size() == 1 && roleNames.contains("*") && securityRoleNames.contains("*")) {
        // // AS7-6932 - Trying to do a * to * mapping which JBossWeb passed through, for Undertow enable
        // // authentication only mode.
        // // TODO - AS7-6933 - Revisit workaround added to allow switching between JBoss Web and Undertow.
        // securityConstraint.setEmptyRoleSemantic(EmptyRoleSemantic.AUTHENTICATE);
        // } else {
        // securityConstraint.addRolesAllowed(roleNames);
        // }
        //
        // LOG.warn("constraint.getResourceCollections() = " + constraint.getResourceCollections());
        // if (constraint.getResourceCollections() != null) {
        // for (final WebResourceCollectionMetaData resourceCollection : constraint.getResourceCollections()) {
        // LOG.warn("resourceCollection.getName() = " + resourceCollection.getName());
        // LOG.warn("resourceCollection.getUrlPatterns() = " + resourceCollection.getUrlPatterns());
        // securityConstraint.addWebResourceCollection(
        // new WebResourceCollection().addHttpMethods(resourceCollection.getHttpMethods())
        // .addHttpMethodOmissions(resourceCollection.getHttpMethodOmissions())
        // .addUrlPatterns(resourceCollection.getUrlPatterns()));
        // }
        // }
        // servletBuilder.addSecurityConstraint(securityConstraint);
        // }
        // } else if (uri.getScheme().equals("https")) {
        // SecurityConstraint securityConstraint = new SecurityConstraint();
        // WebResourceCollection webResourceCollection = new WebResourceCollection();
        // webResourceCollection.addUrlPattern("/*");
        //
        // securityConstraint.addWebResourceCollection(webResourceCollection);
        // securityConstraint.setTransportGuaranteeType(TransportGuaranteeType.CONFIDENTIAL);
        // securityConstraint.setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.PERMIT);
        //
        // servletBuilder.addSecurityConstraint(securityConstraint);
        // }
        // if (uri.getScheme().equals("https")) {
        // servletBuilder.setConfidentialPortManager(exchange -> {
        // int port = exchange.getConnection().getLocalAddress(InetSocketAddress.class).getPort();
        // return injectedDefaultHost.getValue().getServer().getValue().lookupSecurePort(port);
        // });
        // }
        // final LoginConfigMetaData loginConfig = metaData.getLoginConfig();
        // if (loginConfig != null) {
        // LOG.warn("*** loginConfig = " + loginConfig);
        // List<AuthMethodConfig> authMethods = authMethod(loginConfig.getAuthMethod());
        // if (loginConfig.getFormLoginConfig() != null) {
        // servletBuilder.setLoginConfig(
        // new LoginConfig(loginConfig.getRealmName(), loginConfig.getFormLoginConfig().getLoginPage(),
        // loginConfig.getFormLoginConfig().getErrorPage()));
        // } else {
        // servletBuilder.setLoginConfig(new LoginConfig(loginConfig.getRealmName()));
        // }
        // for (AuthMethodConfig method : authMethods) {
        // LOG.warn("*** authMethod = " + method);
        // servletBuilder.getLoginConfig().addLastAuthMethod(method);
        // }
        // }
        //
        // final Set<String> roleNames = metaData.getSecurityRoleNames();
        // LOG.warn("*** roleNames = " + roleNames);
        // servletBuilder.addSecurityRoles(roleNames);

        final DeploymentManager manager = Servlets.defaultContainer().addDeployment(endPointDeplyomentInfo);
        manager.deploy();
        final Deployment deployment = manager.getDeployment();
        try {
            HttpHandler servletHandler = manager.start();
            injectedDefaultHost.getValue().registerDeployment(deployment, servletHandler);

            ManagedServlet managedServlet = deployment.getServlets().getManagedServlet(EndpointServlet.NAME);
            EndpointServlet servletInstance = (EndpointServlet) managedServlet.getServlet().getInstance();
            servletInstance.setEndpointHttpHandler(endpointHttpHandler);
        } catch (ServletException ex) {
            throw new IllegalStateException(ex);
        }
        synchronized (deployments) {
            deployments.put(uri, deployment);
        }
    }

    public void undeploy(URI uri) {
        synchronized (deployments) {
            Deployment removedDeployment = deployments.remove(uri);
            if (removedDeployment != null) {
                injectedDefaultHost.getValue().unregisterDeployment(removedDeployment);
            }
        }
    }
    //
    // /**
    // * Convert the authentication method name from the format specified in the web.xml to the format used by
    // * {@link javax.servlet.http.HttpServletRequest}.
    // * <p/>
    // * If the auth method is not recognised then it is returned as-is.
    // *
    // * @return The converted auth method.
    // * @throws NullPointerException
    // * if no configuredMethod is supplied.
    // */
    // private static List<AuthMethodConfig> authMethod(String configuredMethod) {
    // if (configuredMethod == null) {
    // return Collections.singletonList(new AuthMethodConfig(HttpServletRequest.BASIC_AUTH));
    // }
    // return AuthMethodParser.parse(configuredMethod,
    // Collections.singletonMap("CLIENT-CERT", HttpServletRequest.CLIENT_CERT_AUTH));
    // }
}
