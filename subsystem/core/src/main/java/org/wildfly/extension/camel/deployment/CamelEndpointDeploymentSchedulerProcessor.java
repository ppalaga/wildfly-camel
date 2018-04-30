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

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extension.camel.CamelConstants;
import org.wildfly.extension.camel.service.CamelEndpointDeploymentSchedulerService;

public class CamelEndpointDeploymentSchedulerProcessor implements DeploymentUnitProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CamelEndpointDeploymentSchedulerProcessor.class);

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        CamelDeploymentSettings depSettings = deploymentUnit.getAttachment(CamelDeploymentSettings.ATTACHMENT_KEY);

        if (!depSettings.isEnabled()) {
            return;
        }
        LOG.warn("*** installing scheduler");
        final ServiceTarget serviceTarget = phaseContext.getServiceTarget();
        final ServiceController<CamelEndpointDeploymentSchedulerService> serviceController = CamelEndpointDeploymentSchedulerService.addService(serviceTarget);
        phaseContext.addDeploymentDependency(serviceController.getName(), CamelConstants.CAMEL_ENDPOINT_DEPLOYMENT_SCHEDULER_REGISTRY_KEY);
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }
}
