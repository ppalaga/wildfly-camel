/*
 * #%L
 * Wildfly Camel :: Subsystem
 * %%
 * Copyright (C) 2013 - 2017 RedHat
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

import org.apache.camel.CamelContext;

/**
 * CamelContextActivator starts a SpringCamelContext via a dynamically created class associated with the
 * current deployment ClassLoader.
 *
 * This ensures that SLF4J Logger instances created by Camel are associated with the correct
 * JBoss Logging LogProfile, when custom user log profiles are in operation.
 *
 * See:
 *
 * https://issues.jboss.org/browse/ENTESB-7117
 * https://github.com/wildfly-extras/wildfly-camel/issues/1919
 */
public interface CamelContextActivator {

    public void activate(CamelContext camelctx, ClassLoader classLoader) throws Exception;

}
