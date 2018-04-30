/*
 * #%L
 * Wildfly Camel :: Testsuite
 * %%
 * Copyright (C) 2013 - 2016 RedHat
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
package org.wildfly.camel.test.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public final class UserManager {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private UserManager() {
        // Hide constructor for utility class
    }

    public static void addApplicationUser(String userName, String password, Path jbossHome) {
        addUser(userName, "ApplicationRealm", password, jbossHome.resolve("standalone/configuration/application-users.properties"));
    }

    public static void addManagementUser(String userName, String password, Path jbossHome) {
        addUser(userName, "ManagementRealm", password, jbossHome.resolve("standalone/configuration/mgmt-users.properties"));
    }

    private static void addUser(String userName, String realm, String password, Path propertiesFile) {
        Properties properties = readPropertiesFile(propertiesFile);
        properties.put(userName, encryptPassword(userName, password, realm));
        writePropertiesFile(properties, propertiesFile);
    }

    public static void addRoleToApplicationUser(String userName, String role, Path jbossHome) {
        addRoleToUser(userName, role, jbossHome.resolve("standalone/configuration/application-roles.properties"));
    }

    public static void addRoleToManagementUser(String userName, String role, Path jbossHome) {
        addRoleToUser(userName, role, jbossHome.resolve("standalone/configuration/mgmt-roles.properties"));
    }

    private static void addRoleToUser(String userName, String role, Path propertiesFile) {
        Properties properties = readPropertiesFile(propertiesFile);
        properties.put(userName, role);
        writePropertiesFile(properties, propertiesFile);
    }

    public static void removeApplicationUser(String userName, Path jbossHome) {
        removeUser(userName, jbossHome.resolve("standalone/configuration/application-users.properties"));
    }

    public static void removeManagementUser(String userName, Path jbossHome) {
        removeUser(userName, jbossHome.resolve("standalone/configuration/mgmt-users.properties"));
    }

    private static void removeUser(String userName, Path propertiesFile) {
        Properties properties = readPropertiesFile(propertiesFile);
        properties.remove(userName);
        writePropertiesFile(properties, propertiesFile);
    }

    public static void revokeRoleFromApplicationUser(String userName, String role, Path jbossHome) {
        revokeRoleFromUser(userName, role, jbossHome.resolve("standalone/configuration/application-roles.properties"));
    }

    public static void revokeRoleFromManagementUser(String userName, String role, Path jbossHome) {
        revokeRoleFromUser(userName, role, jbossHome.resolve("standalone/configuration/mgmt-roles.properties"));
    }

    private static void revokeRoleFromUser(String userName, String role, Path propertiesFile) {
        Properties properties = readPropertiesFile(propertiesFile);
        String roles = properties.getProperty(userName);
        if (roles != null && ! roles.isEmpty()) {
            List<String> roleList = new ArrayList<>(Arrays.asList(roles.split(",")));
            roleList.remove(role);
            if (roleList.isEmpty()) {
                properties.remove(userName);
            } else {
                String updatedRoles = roleList.stream().collect(Collectors.joining(","));
                properties.put(userName, updatedRoles);
            }
            properties.put(userName, role);
            writePropertiesFile(properties, propertiesFile);
        }
    }

    private static Properties readPropertiesFile(Path file) {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return properties;
    }

    private static void writePropertiesFile(Properties properties, Path file) {
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, null);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encryptPassword(String userName, String password, String realm) {
        try {
            String stringToEncrypt = String.format("%s:%s:%s", userName, realm, password);

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashedPassword = md.digest(stringToEncrypt.getBytes(StandardCharsets.UTF_8));

            char[] converted = new char[hashedPassword.length * 2];
            for (int i = 0; i < hashedPassword.length; i++) {
                byte b = hashedPassword[i];
                converted[i * 2] = HEX_CHARS[b >> 4 & 0x0F];
                converted[i * 2 + 1] = HEX_CHARS[b & 0x0F];
            }
            return String.valueOf(converted);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
