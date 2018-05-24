package org.wildfly.camel.test.common.security;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.wildfly.camel.test.common.utils.UserManager;
import org.wildfly.camel.test.common.utils.WildFlyCli;

public class ClientCertSecurityDomainSetup implements ServerSetupTask {
    static final Path WILDFLY_HOME = Paths.get(System.getProperty("jbossHome"));
    public static final String APPLICATION_ROLE = "testRole";
    public static final String SECURITY_DOMAIN = "client-cert-security-domain";
    public static final String AUTH_METHOD = "CLIENT-CERT";
    public static final String CLIENT_ALIAS = "client";
    public static final String TRUSTSTORE_PASSWORD = "123456";

    private final WildFlyCli wildFlyCli = new WildFlyCli(WILDFLY_HOME);

    @Override
    public void setup(ManagementClient managementClient, String containerId) throws Exception {
        SecurityUtils.copyKeyMaterial(WILDFLY_HOME);
        UserManager.addRoleToApplicationUser(CLIENT_ALIAS, APPLICATION_ROLE, WILDFLY_HOME);
        URL cliUrl = this.getClass().getClassLoader().getResource("security/cli/client-cert/setup.cli");
        wildFlyCli.run(cliUrl).assertSuccess();
    }

    @Override
    public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
        UserManager.revokeRoleFromApplicationUser(CLIENT_ALIAS, APPLICATION_ROLE, WILDFLY_HOME);
        URL cliUrl = this.getClass().getClassLoader().getResource("security/cli/client-cert/tear-down.cli");
        wildFlyCli.run(cliUrl).assertSuccess();
    }
}
