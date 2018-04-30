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
package org.wildfly.extension.camel.deployment;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.msc.service.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extension.camel.service.CamelEndpointDeployerService;
import org.wildfly.extension.undertow.UndertowService;
import org.wildfly.extension.undertow.deployment.DefaultDeploymentMappingProvider;
import org.wildfly.extension.undertow.deployment.UndertowDeploymentInfoService;

public class CamelEndpointDeployerProcessor implements DeploymentUnitProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CamelEndpointDeployerProcessor.class);

    private String hostNameOfDeployment(final WarMetaData metaData, String defaultHost) {
        Collection<String> hostNames = null;
        if (metaData.getMergedJBossWebMetaData() != null) {
            hostNames = metaData.getMergedJBossWebMetaData().getVirtualHosts();
        }
        if (hostNames == null || hostNames.isEmpty()) {
            hostNames = Collections.singleton(defaultHost);
        }
        String hostName = hostNames.iterator().next();
        if (hostName == null) {
            throw new IllegalStateException("Null host name");
        }
        return hostName;
    }

    static String pathNameOfDeployment(final DeploymentUnit deploymentUnit, final JBossWebMetaData metaData) {
        String pathName;
        if (metaData.getContextRoot() == null) {
            final EEModuleDescription description = deploymentUnit
                    .getAttachment(org.jboss.as.ee.component.Attachments.EE_MODULE_DESCRIPTION);
            if (description != null) {
                // if there is an EEModuleDescription we need to take into account that the module name may have been
                // overridden
                pathName = "/" + description.getModuleName();
            } else {
                pathName = "/" + deploymentUnit.getName().substring(0, deploymentUnit.getName().length() - 4);
            }
        } else {
            pathName = metaData.getContextRoot();
            if (pathName.length() > 0 && pathName.charAt(0) != '/') {
                pathName = "/" + pathName;
            }
        }
        return pathName;
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        CamelDeploymentSettings depSettings = deploymentUnit.getAttachment(CamelDeploymentSettings.ATTACHMENT_KEY);

        if (!depSettings.isEnabled()) {
            return;
        }
        final WarMetaData warMetaData = deploymentUnit.getAttachment(WarMetaData.ATTACHMENT_KEY);
        if (warMetaData == null) {
            LOG.warn("WarMetaData not ready");
            return;
        }
        LOG.warn("*** installing deployer");
        final String deploymentName;
        if (deploymentUnit.getParent() == null) {
            deploymentName = deploymentUnit.getName();
        } else {
            deploymentName = deploymentUnit.getParent().getName() + "." + deploymentUnit.getName();
        }
        final Map.Entry<String, String> severHost = DefaultDeploymentMappingProvider.instance()
                .getMapping(deploymentName);
        String defaultHostForDeployment;
        String defaultServerForDeployment;
        if (severHost != null) {
            defaultServerForDeployment = severHost.getKey();
            defaultHostForDeployment = severHost.getValue();
        } else {
            defaultServerForDeployment = "default-server";
            defaultHostForDeployment = "default-host";
        }
        final String serverInstanceName = warMetaData.getMergedJBossWebMetaData().getServerInstanceName() == null
                ? defaultServerForDeployment
                : warMetaData.getMergedJBossWebMetaData().getServerInstanceName();
        final String hostName = hostNameOfDeployment(warMetaData, defaultHostForDeployment);
        final JBossWebMetaData metaData = warMetaData.getMergedJBossWebMetaData();
        final String pathName = pathNameOfDeployment(deploymentUnit, metaData);
        final ServiceName deploymentServiceName = UndertowService.deploymentServiceName(serverInstanceName, hostName,
                pathName);
        final ServiceName deploymentInfoServiceName = deploymentServiceName
                .append(UndertowDeploymentInfoService.SERVICE_NAME);
        ServiceName hostServiceName = UndertowService.virtualHostName(serverInstanceName, hostName);
        CamelEndpointDeployerService.addService(phaseContext.getServiceTarget(), deploymentInfoServiceName,
                hostServiceName);
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }
}
