package org.wildfly.camel.test.cxf.ws.secure;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.camel.test.common.security.SecurityUtils;
import org.wildfly.camel.test.cxf.ws.secure.subA.Application;
import org.wildfly.camel.test.cxf.ws.secure.subA.CxfWsRouteBuilder;
import org.wildfly.camel.test.cxf.ws.secure.subA.GreetingService;
import org.wildfly.camel.test.cxf.ws.secure.subA.GreetingsProcessor;

public class CXFWSElytronUtils {

    private static final Logger LOG = LoggerFactory.getLogger(CXFWSElytronUtils.class);

    private static final String APPLICATION_PASSWORD = "testPassword1+";
    private static final String APPLICATION_ROLE = "testRole";
    private static final String APPLICATION_USER = "client";
    private static final Path BASEDIR = Paths.get(System.getProperty("project.basedir"));
    private static final String HTTPS_HOST = "https://localhost:8443";
    private static final String TRUSTSTORE_PASSWORD = "password";
    private static final Path WILDFLY_HOME = Paths.get(System.getProperty("jbossHome"));


}
