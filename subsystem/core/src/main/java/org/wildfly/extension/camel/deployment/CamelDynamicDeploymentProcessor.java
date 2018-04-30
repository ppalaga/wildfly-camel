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

import org.jboss.as.security.deployment.SecurityAttachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.metadata.ear.jboss.JBossAppMetaData;
import org.jboss.metadata.ear.spec.EarMetaData;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extension.camel.service.CamelDynamicDeploymentService;

public class CamelDynamicDeploymentProcessor implements DeploymentUnitProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CamelDynamicDeploymentProcessor.class);

    private static final String defaultSecurityDomain = "other";

    /**
     * Try to obtain the security domain configured in jboss-app.xml at the ear level if available
     */
    private String getJBossAppSecurityDomain(final DeploymentUnit deploymentUnit) {
        String securityDomain = null;
        DeploymentUnit parent = deploymentUnit.getParent();
        if (parent != null) {
            final EarMetaData jbossAppMetaData = parent.getAttachment(org.jboss.as.ee.structure.Attachments.EAR_METADATA);
            if (jbossAppMetaData instanceof JBossAppMetaData) {
                securityDomain = ((JBossAppMetaData) jbossAppMetaData).getSecurityDomain();
            }
        }
        return securityDomain;
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
        if (deploymentUnit.getName().equals("example-camel-cxf-jaxws-cdi-secure.war")) {
            LOG.warn("yes this is example-camel-cxf-jaxws-cdi-secure.war");

            boolean securityEnabled = deploymentUnit.hasAttachment(SecurityAttachments.SECURITY_ENABLED);
            final JBossWebMetaData metaData = warMetaData.getMergedJBossWebMetaData();

            String metaDataSecurityDomain = metaData.getSecurityDomain();
            if (metaDataSecurityDomain == null) {
                metaDataSecurityDomain = getJBossAppSecurityDomain(deploymentUnit);
            }
            if (metaDataSecurityDomain != null) {
                metaDataSecurityDomain = metaDataSecurityDomain.trim();
            }

            final String securityDomain;
            if(securityEnabled) {
                securityDomain = metaDataSecurityDomain == null ? defaultSecurityDomain : unprefixSecurityDomain(metaDataSecurityDomain);
            } else {
                securityDomain = null;
            }

            LOG.warn("*** securityDomain = "+ securityDomain);

            final ServiceTarget serviceTarget = phaseContext.getServiceTarget();
            final ServiceName securityDomainServiceName = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.CAPABILITY_SERVICE_SUPPORT)
            .getCapabilityServiceName(
                    org.wildfly.extension.undertow.Capabilities.CAPABILITY_APPLICATION_SECURITY_DOMAIN,
                    securityDomain);
            CamelDynamicDeploymentService.addService(serviceTarget, securityDomainServiceName, warMetaData);
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }

    /**
     * Default Application Policy
     */
    private static final String DEFAULT_APPLICATION_POLICY = "other";

    /**
     * Default JAAS based Security Domain Context
     */
    private static final String JAAS_CONTEXT_ROOT = "java:jboss/jaas/";

    /**
     * Default JASPI based Security Domain Context
     */
    private static final String JASPI_CONTEXT_ROOT = "java:jboss/jbsx/";

    private static String LEGACY_JAAS_CONTEXT_ROOT = "java:/jaas/";

    /**
     * Strip the security domain of prefix (java:jaas or java:jbsx)
     *
     * @param securityDomain
     * @return
     */
    public static String unprefixSecurityDomain(String securityDomain)
    {
       String result = null;
       if (securityDomain != null)
       {
          if (securityDomain.startsWith(JAAS_CONTEXT_ROOT))
             result = securityDomain.substring(JAAS_CONTEXT_ROOT.length());
          else if (securityDomain.startsWith(JASPI_CONTEXT_ROOT))
             result = securityDomain.substring(JASPI_CONTEXT_ROOT.length());
          else if (securityDomain.startsWith(LEGACY_JAAS_CONTEXT_ROOT))
             result = securityDomain.substring(LEGACY_JAAS_CONTEXT_ROOT.length());
          else
             result = securityDomain;
       }
       return result;

    }
}
