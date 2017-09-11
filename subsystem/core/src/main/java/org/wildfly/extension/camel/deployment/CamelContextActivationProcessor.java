/*
 * #%L
 * Wildfly Camel :: Subsystem
 * %%
 * Copyright (C) 2013 - 2014 RedHat
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

import static org.wildfly.extension.camel.CamelLogger.LOGGER;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.Module;
import org.wildfly.extension.camel.CamelConstants;

/**
 * Start/Stop the {@link CamelContext}
 *
 * @author Thomas.Diesler@jboss.com
 * @since 22-Apr-2013
 */
public class CamelContextActivationProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        DeploymentUnit depUnit = phaseContext.getDeploymentUnit();
        Module module = depUnit.getAttachment(Attachments.MODULE);

        // Start the camel contexts
        for (CamelContext camelctx : depUnit.getAttachmentList(CamelConstants.CAMEL_CONTEXT_KEY)) {
            try {
                CamelContextActivator camelContextActivator = (CamelContextActivator) Proxy.newProxyInstance(module.getClassLoader(), new Class<?>[]{CamelContextActivator.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        camelctx.start();
                        return null;
                    }
                });
                camelContextActivator.activate(camelctx, module.getClassLoader());
            } catch (Exception ex) {
                LOGGER.error("Cannot start camel context: " + camelctx.getName(), ex);
            }
        }
    }

    @Override
    public void undeploy(final DeploymentUnit depUnit) {

        List<CamelContext> ctxlist = new ArrayList<>(depUnit.getAttachmentList(CamelConstants.CAMEL_CONTEXT_KEY));
        Collections.reverse(ctxlist);

        // Stop the camel contexts
        for (CamelContext camelctx : ctxlist) {
            try {
                camelctx.stop();
            } catch (Exception ex) {
                LOGGER.warn("Cannot stop camel context: " + camelctx.getName(), ex);
            }
        }
    }
}
