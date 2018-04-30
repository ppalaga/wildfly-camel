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
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.HttpHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.AuthMethodConfig;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.SecurityInfo.EmptyRoleSemantic;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.core.ManagedServlet;

import org.jboss.as.web.common.WarMetaData;
import org.jboss.gravia.runtime.Runtime;
import org.jboss.gravia.runtime.ServiceRegistration;
import org.jboss.metadata.javaee.jboss.RunAsIdentityMetaData;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.metadata.web.spec.LoginConfigMetaData;
import org.jboss.metadata.web.spec.SecurityConstraintMetaData;
import org.jboss.metadata.web.spec.WebResourceCollectionMetaData;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
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
import org.wildfly.extension.undertow.ApplicationSecurityDomainDefinition.Registration;
import org.wildfly.extension.undertow.Host;
import org.wildfly.extension.undertow.UndertowService;
import org.wildfly.extension.undertow.deployment.AuthMethodParser;
import org.wildfly.extension.undertow.security.jacc.JACCContextIdHandler;

public class CamelDynamicDeploymentService implements Service<CamelDynamicDeploymentService> {

    private static final Logger LOG = LoggerFactory.getLogger(CamelDynamicDeploymentService.class);

    class DynamicDeployment {
        private final DeploymentManager manager;

        public DynamicDeployment(DeploymentManager manager) {
            super();
            this.manager = manager;
        }

        public void dispose() {
            if (manager != null) {
                injectedDefaultHost.getValue().unregisterDeployment(manager.getDeployment());
            }
        }
    }

    @SuppressWarnings("serial")
    static class DefaultServlet extends HttpServlet {

        private BiFunction<HttpServletRequest, HttpServletResponse, Void> handlerFunction;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            LOG.error("*** DefaultServlet", new RuntimeException());
            handlerFunction.apply(req, res);
        }

        public void setHandlerFunction(BiFunction<HttpServletRequest, HttpServletResponse, Void> handlerFunction) {
            this.handlerFunction = handlerFunction;
        }
    }

    private final InjectedValue<Runtime> injectedRuntime = new InjectedValue<>();
    private final InjectedValue<BiFunction> injectedSecurityFunction = new InjectedValue<>();
    private ServiceRegistration<CamelDynamicDeploymentService> registration;
    private final Map<URI, DynamicDeployment> deployments = new HashMap<>();
    private final WarMetaData warMetaData;
    private final InjectedValue<Host> injectedDefaultHost = new InjectedValue<>();
    private org.wildfly.extension.undertow.ApplicationSecurityDomainDefinition.Registration securityDomainRegistration;

    public CamelDynamicDeploymentService(WarMetaData warMetaData) {
        super();
        this.warMetaData = warMetaData;
    }

    public static ServiceController<CamelDynamicDeploymentService> addService(ServiceTarget serviceTarget,
            ServiceName securityDomainServiceName, WarMetaData warMetaData) {
        CamelDynamicDeploymentService service = new CamelDynamicDeploymentService(warMetaData);
        ServiceBuilder<CamelDynamicDeploymentService> builder = serviceTarget
                .addService(CamelConstants.CAMEL_DYNAMIC_DEPLOYMENT_SERVICE_NAME, service) //
                .addDependency(GraviaConstants.RUNTIME_SERVICE_NAME, Runtime.class, service.injectedRuntime) //
                .addDependency(UndertowService.virtualHostName("default-server", "default-host"), Host.class,
                        service.injectedDefaultHost) //
                .addDependency(securityDomainServiceName, BiFunction.class, service.injectedSecurityFunction);
        return builder.install();
    }

    @Override
    public void start(StartContext context) throws StartException {
        Runtime runtime = injectedRuntime.getValue();
        registration = runtime.getModuleContext().registerService(CamelDynamicDeploymentService.class, this, null);
    }

    @Override
    public void stop(StopContext context) {
        if (registration != null) {
            registration.unregister();
        }
    }

    @Override
    public CamelDynamicDeploymentService getValue() throws IllegalStateException {
        return this;
    }

    private static io.undertow.servlet.api.TransportGuaranteeType transportGuaranteeType(URI uri, final org.jboss.metadata.web.spec.TransportGuaranteeType type) {
        if (type != null) {
            switch (type) {
                case CONFIDENTIAL:
                    return io.undertow.servlet.api.TransportGuaranteeType.CONFIDENTIAL;
                case INTEGRAL:
                    return io.undertow.servlet.api.TransportGuaranteeType.INTEGRAL;
                case NONE:
                    return io.undertow.servlet.api.TransportGuaranteeType.NONE;
            }
        } else if (uri.getScheme().equals("https")) {
            return io.undertow.servlet.api.TransportGuaranteeType.CONFIDENTIAL;
        } else {
            return io.undertow.servlet.api.TransportGuaranteeType.NONE;
        }
        throw new RuntimeException("UNREACHABLE");
    }

    public void deploy(URI uri, BiFunction<HttpServletRequest, HttpServletResponse, Void> handlerFunction,
            Consumer<ServletContext> servletContextConsumer, ClassLoader cl) {

        ServletInfo servletInfo = Servlets.servlet("DefaultServlet", DefaultServlet.class)
                .addMapping("/*").setAsyncSupported(true);

        DeploymentInfo servletBuilder = Servlets.deployment() //
                .setClassLoader(cl) //
                .setContextPath(uri.getPath()) //
                .setDeploymentName("cxfdestination.war") //
                .addServlets(servletInfo);

        final JBossWebMetaData metaData = warMetaData.getMergedJBossWebMetaData();
        servletBuilder.setDenyUncoveredHttpMethods(metaData.getDenyUncoveredHttpMethods() != null);
        Set<String> securityRoleNames = metaData.getSecurityRoleNames();
        LOG.warn("securityRoleNames = "+ securityRoleNames);
        if (metaData.getSecurityConstraints() != null) {
            for (SecurityConstraintMetaData constraint : metaData.getSecurityConstraints()) {
                SecurityConstraint securityConstraint = new SecurityConstraint()
                        .setTransportGuaranteeType(transportGuaranteeType(uri, constraint.getTransportGuarantee()));

                List<String> roleNames = constraint.getRoleNames();
                LOG.warn("roleNames = "+ roleNames);
                if (constraint.getAuthConstraint() == null) {
                    // no auth constraint means we permit the empty roles
                    securityConstraint.setEmptyRoleSemantic(EmptyRoleSemantic.PERMIT);
                } else if (roleNames.size() == 1 && roleNames.contains("*") && securityRoleNames.contains("*")) {
                    // AS7-6932 - Trying to do a * to * mapping which JBossWeb passed through, for Undertow enable
                    // authentication only mode.
                    // TODO - AS7-6933 - Revisit workaround added to allow switching between JBoss Web and Undertow.
                    securityConstraint.setEmptyRoleSemantic(EmptyRoleSemantic.AUTHENTICATE);
                } else {
                    securityConstraint.addRolesAllowed(roleNames);
                }

                LOG.warn("constraint.getResourceCollections() = "+ constraint.getResourceCollections());
                if (constraint.getResourceCollections() != null) {
                    for (final WebResourceCollectionMetaData resourceCollection : constraint.getResourceCollections()) {
                        LOG.warn("resourceCollection.getName() = "+ resourceCollection.getName());
                        LOG.warn("resourceCollection.getUrlPatterns() = "+ resourceCollection.getUrlPatterns());
                        securityConstraint.addWebResourceCollection(new WebResourceCollection()
                                .addHttpMethods(resourceCollection.getHttpMethods())
                                .addHttpMethodOmissions(resourceCollection.getHttpMethodOmissions())
                                .addUrlPatterns(resourceCollection.getUrlPatterns()));
                    }
                }
                servletBuilder.addSecurityConstraint(securityConstraint);
            }
        } else if (uri.getScheme().equals("https")) {
            SecurityConstraint securityConstraint = new SecurityConstraint();
            WebResourceCollection webResourceCollection = new WebResourceCollection();
            webResourceCollection.addUrlPattern("/*");

            securityConstraint.addWebResourceCollection(webResourceCollection);
            securityConstraint.setTransportGuaranteeType(TransportGuaranteeType.CONFIDENTIAL);
            securityConstraint.setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.PERMIT);

            servletBuilder.addSecurityConstraint(securityConstraint);
        }
        if (uri.getScheme().equals("https")) {
            servletBuilder.setConfidentialPortManager(exchange -> {
                int port = exchange.getConnection().getLocalAddress(InetSocketAddress.class).getPort();
                return injectedDefaultHost.getValue().getServer().getValue().lookupSecurePort(port);
            });
        }
        final LoginConfigMetaData loginConfig = metaData.getLoginConfig();
        if (loginConfig != null) {
            LOG.warn("*** loginConfig = "+ loginConfig);
            List<AuthMethodConfig> authMethods = authMethod(loginConfig.getAuthMethod());
            if (loginConfig.getFormLoginConfig() != null) {
                servletBuilder.setLoginConfig(new LoginConfig(loginConfig.getRealmName(), loginConfig.getFormLoginConfig().getLoginPage(), loginConfig.getFormLoginConfig().getErrorPage()));
            } else {
                servletBuilder.setLoginConfig(new LoginConfig(loginConfig.getRealmName()));
            }
            for (AuthMethodConfig method : authMethods) {
                LOG.warn("*** authMethod  = "+ method);
                servletBuilder.getLoginConfig().addLastAuthMethod(method);
            }
        }

        final Set<String> roleNames = metaData.getSecurityRoleNames();
        LOG.warn("*** roleNames  = "+ roleNames);
        servletBuilder.addSecurityRoles(roleNames);

        final BiFunction<DeploymentInfo, Function<String, RunAsIdentityMetaData>, Registration> securityFunction = injectedSecurityFunction
                .getOptionalValue();
        final JBossWebMetaData mergedMetaData = warMetaData.getMergedJBossWebMetaData();
        if (securityFunction != null) {
            Map<String, RunAsIdentityMetaData> runAsIdentityMap = mergedMetaData.getRunAsIdentity();
            securityDomainRegistration = securityFunction.apply(servletBuilder, runAsIdentityMap::get);
            servletBuilder.addOuterHandlerChainWrapper(JACCContextIdHandler.wrapper(mergedMetaData.getJaccContextID()));
        }

        final DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
        manager.deploy();

        try {
            HttpHandler servletHandler = manager.start();
            injectedDefaultHost.getValue().registerDeployment(manager.getDeployment(), servletHandler);

            servletContextConsumer.accept(manager.getDeployment().getServletContext());

            ManagedServlet managedServlet = manager.getDeployment().getServlets().getManagedServlet("DefaultServlet");
            DefaultServlet servletInstance = (DefaultServlet) managedServlet.getServlet().getInstance();
            servletInstance.setHandlerFunction(handlerFunction);
        } catch (ServletException ex) {
            throw new IllegalStateException(ex);
        }
        final DynamicDeployment deployment = new DynamicDeployment(manager);
        synchronized (deployments) {
            deployments.put(uri, deployment);
        }
    }

    public void undeploy(URI uri) {
        synchronized (deployments) {
            deployments.remove(uri);
        }
    }

    /**
     * Convert the authentication method name from the format specified in the web.xml to the format used by
     * {@link javax.servlet.http.HttpServletRequest}.
     * <p/>
     * If the auth method is not recognised then it is returned as-is.
     *
     * @return The converted auth method.
     * @throws NullPointerException if no configuredMethod is supplied.
     */
    private static List<AuthMethodConfig> authMethod(String configuredMethod) {
        if (configuredMethod == null) {
            return Collections.singletonList(new AuthMethodConfig(HttpServletRequest.BASIC_AUTH));
        }
        return AuthMethodParser.parse(configuredMethod, Collections.singletonMap("CLIENT-CERT", HttpServletRequest.CLIENT_CERT_AUTH));
    }
}
