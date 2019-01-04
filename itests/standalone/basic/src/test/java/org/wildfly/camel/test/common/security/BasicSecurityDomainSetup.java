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
import org.wildfly.camel.test.common.utils.UserManager;

/**
 * Creates an Undertow {@code application-security-domain} called {@value #SECURITY_DOMAIN} and links it with the
 * default Elytron {@code ApplicationDomain} which in turn uses {@code application-[users|roles].properties}. Also adds
 * some users and roles therein.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class BasicSecurityDomainSetup implements ServerSetupTask {
    public static final String APPLICATION_PASSWORD = "password+";
    public static final String APPLICATION_PASSWORD_SUB = "password+Sub";
    public static final String APPLICATION_ROLE = "testRole";
    public static final String APPLICATION_ROLE_SUB = "testRoleSub";
    public static final String APPLICATION_USER = "testUser";
    public static final String APPLICATION_USER_SUB = "testUserSub";
    public static final String AUTH_METHOD = "BASIC";
    private static final PathAddress DOMAIN_ADDRESS;
    private static final String HTTPS_HOST = "https://localhost:8443";

    public static final String SECURITY_DOMAIN = "basic-application-security-domain";

    static final Path WILDFLY_HOME = Paths.get(System.getProperty("jbossHome"));

    static {
        DOMAIN_ADDRESS = PathAddress
                .parseCLIStyleAddress("/subsystem=undertow/application-security-domain=" + SECURITY_DOMAIN);
    }

    @Override
    public void setup(ManagementClient managementClient, String containerId) throws Exception {
        // Force WildFly to create the default application.keystore
        HttpRequest.post(HTTPS_HOST).getResponse();
        final ModelControllerClient client = managementClient.getControllerClient();

        try (UserManager um = UserManager.forStandaloneApplicationRealm(WILDFLY_HOME)) {
            um //
                    .addUser(APPLICATION_USER, APPLICATION_PASSWORD) //
                    .addRole(APPLICATION_USER, APPLICATION_ROLE) //
                    .addUser(APPLICATION_USER_SUB, APPLICATION_PASSWORD_SUB) //
                    .addRole(APPLICATION_USER_SUB, APPLICATION_ROLE_SUB) //
            ;
        }

        final ModelNode addDomain = Util.createAddOperation(DOMAIN_ADDRESS);
        addDomain.get("http-authentication-factory").set("application-http-authentication");

        DMRUtils.batchNode() //
                // reload the realm so that the changes in the property files are visible
                .addStep(DMRUtils.createOpNode("subsystem=elytron/properties-realm=ApplicationRealm", "load")) //
                .addStep(addDomain) //
                .execute(client) //
                .assertSuccess();
    }

    @Override
    public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
        try (UserManager um = UserManager.forStandaloneApplicationRealm(WILDFLY_HOME)) {
            um //
                    .removeUser(APPLICATION_USER) //
                    .removeRole(APPLICATION_USER, APPLICATION_ROLE) //
                    .removeUser(APPLICATION_USER_SUB) //
                    .removeRole(APPLICATION_USER_SUB, APPLICATION_ROLE_SUB) //
            ;
        }

        DMRUtils.batchNode() //
                // reload the realm so that the changes in the property files are visible
                .addStep(DMRUtils.createOpNode("subsystem=elytron/properties-realm=ApplicationRealm", "load")) //
                .addStep(Util.createRemoveOperation(DOMAIN_ADDRESS)) //
                .execute(managementClient.getControllerClient()) //
                .assertSuccess();

    }
}
