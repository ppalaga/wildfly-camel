package org.wildfly.camel.test.common.security;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.wildfly.camel.test.common.http.HttpRequest;
import org.wildfly.camel.test.common.utils.DMRUtils;
import org.wildfly.camel.test.common.utils.DMRUtils.BatchNodeBuilder;
import org.wildfly.camel.test.common.utils.UserManager;

public class BasicSecurityDomainSetup implements ServerSetupTask {
    static final Path WILDFLY_HOME = Paths.get(System.getProperty("jbossHome"));
    public static final String APPLICATION_ROLE = "testRole";
    public static final String SECURITY_DOMAIN = "basic-application-security-domain";
    public static final String AUTH_METHOD = "BASIC";
    public static final String APPLICATION_USER = "testUser";
    public static final String APPLICATION_PASSWORD = "password+";

    private static final String HTTPS_HOST = "https://localhost:8443";

    private static final PathAddress DOMAIN_ADDRESS;

    static {
        DOMAIN_ADDRESS = PathAddress
                .parseCLIStyleAddress("/subsystem=undertow/application-security-domain=" + SECURITY_DOMAIN);
    }

    @Override
    public void setup(ManagementClient managementClient, String containerId) throws Exception {
        // Force WildFly to create the default application.keystore
        HttpRequest.post(HTTPS_HOST).getResponse();
        final ModelControllerClient client = managementClient.getControllerClient();

        UserManager.addApplicationUser(APPLICATION_USER, APPLICATION_PASSWORD, WILDFLY_HOME);
        UserManager.addRoleToApplicationUser(APPLICATION_USER, APPLICATION_ROLE, WILDFLY_HOME);

        final BatchNodeBuilder batch = DMRUtils.batchNode() //
                .addStep(DMRUtils.createOpNode("subsystem=elytron/properties-realm=ApplicationRealm", "load"));

        final ModelNode addDomain = Util.createAddOperation(DOMAIN_ADDRESS);
        addDomain.get("http-authentication-factory").set("application-http-authentication");

        batch.addStep(addDomain) //
                .execute(client) //
                .assertSuccess();
    }

    @Override
    public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
        UserManager.removeApplicationUser(APPLICATION_USER, WILDFLY_HOME);
        UserManager.revokeRoleFromApplicationUser(APPLICATION_USER, APPLICATION_ROLE, WILDFLY_HOME);

        DMRUtils.batchNode() //
                .addStep(DMRUtils.createOpNode("subsystem=elytron/properties-realm=ApplicationRealm", "load")) //
                .addStep(Util.createRemoveOperation(DOMAIN_ADDRESS)) //
                .execute(managementClient.getControllerClient()) //
                .assertSuccess();

    }
}
