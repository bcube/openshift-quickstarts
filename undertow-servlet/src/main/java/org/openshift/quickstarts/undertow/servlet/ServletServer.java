/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openshift.quickstarts.undertow.servlet;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;

import static io.undertow.servlet.Servlets.defaultContainer;
import static io.undertow.servlet.Servlets.deployment;
import static io.undertow.servlet.Servlets.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * @author Stuart Douglas
 */
public class ServletServer {

    public static final String MYAPP = "/";

    /** The driver class provided by NuoDB. */
    public static final String DRIVER_CLASS =
            "com.nuodb.jdbc.Driver";
    /** The base URL for connecting to a local database server. */
    public static final String DATABASE_URL =
            "jdbc:com.nuodb://east48004-nuodb.dev-oc1.welabvb.com/";
    // the established connection to a local server
    private final Connection dbConnection;

    public ServletServer(String user, String password, String dbName)
            throws SQLException
    {
        Properties properties = new Properties();
        properties.put("user", user);
        properties.put("password", password);
        properties.put("schema", "hello");
        dbConnection =
                DriverManager.getConnection(DATABASE_URL + dbName, properties);
    }
    /** Closes the connection to the server. */
    public void close() throws SQLException {
        dbConnection.close();
    }

    public static void main(final String[] args) {
        try {


            Class.forName(DRIVER_CLASS);

            ServletServer helloDB = new ServletServer("dba", "secret", "demo");

            helloDB.close();


            DeploymentInfo servletBuilder = deployment()
                    .setClassLoader(ServletServer.class.getClassLoader())
                    .setContextPath(MYAPP)
                    .setDeploymentName("test.war")
                    .addServlets(
                            servlet("MessageServlet", MessageServlet.class)
                                    .addInitParam("message", "Hello World")
                                    .addMapping("/*"),
                            servlet("MyServlet", MessageServlet.class)
                                    .addInitParam("message", "MyServlet")
                                    .addMapping("/myservlet"));

            DeploymentManager manager = defaultContainer().addDeployment(servletBuilder);
            manager.deploy();

            SSLContext sslContext = null;
            String filename = System.getenv("HTTPS_KEYSTORE");
            if (filename != null) {
                String directory = System.getenv("HTTPS_KEYSTORE_DIR");
                char[] password = System.getenv("HTTPS_PASSWORD").toCharArray();
                File keystore = new File(directory, filename);
            
                sslContext = createSSLContext(loadKeyStore(keystore, password), password);
            }
            
            HttpHandler servletHandler = manager.start();
            PathHandler path = Handlers.path(Handlers.redirect(MYAPP))
                    .addPrefixPath(MYAPP, servletHandler);
            Undertow server = Undertow.builder()
                    .addHttpListener(8080, "0.0.0.0")
                    .addHttpsListener(8443, "0.0.0.0", sslContext)
                    .setHandler(path)
                    .build();
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static KeyStore loadKeyStore(File file, char[] password) throws Exception {
        final InputStream stream = new FileInputStream(file);
        try(InputStream is = stream) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(is, password);
            return loadedKeystore;
        }
    }

    private static SSLContext createSSLContext(final KeyStore keyStore, final char[] password) throws Exception {
        KeyManager[] keyManagers;
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        keyManagers = keyManagerFactory.getKeyManagers();

        SSLContext sslContext;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, null, null);

        return sslContext;
    }
}
