package com.vaadin.starter.skeleton;

import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.startup.ServletContextListeners;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jetbrains.annotations.NotNull;

/**
 * Run {@link #main(String[])} to launch your app in Embedded Jetty.
 * @author mavi
 */
public final class Main {

    private static Server server;

    public static void main(@NotNull String[] args) throws Exception {
        start(args);
        server.join();
    }

    public static void start(@NotNull String[] args) throws Exception {
        // use pnpm instead of npm
        System.setProperty("vaadin.pnpm.enable", "true");

        // detect&enable production mode
        if (isProductionMode()) {
            // fixes https://github.com/mvysny/vaadin14-embedded-jetty/issues/1
            System.out.println("Production mode detected, enforcing");
            System.setProperty("vaadin.productionMode", "true");
        }

        final WebAppContext context = new WebAppContext();
        context.setBaseResource(findWebRoot());
        context.setContextPath("/");
        context.addServlet(VaadinServlet.class, "/*");
        context.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*\\.jar|.*/classes/.*");
        context.setConfigurationDiscovered(true);
        context.getServletContext().setExtendedListenerTypes(true);
        context.addEventListener(new ServletContextListeners());

        int port = 8080;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }

        server = new Server();
        server.addConnector(httpConnector(port));
        server.addConnector(httpsConnector());
        server.setHandler(context);
        server.start();

        System.out.println("\n\n=================================================\n\n" +
        "Please open http://localhost:" + port + " in your browser\n\n" +
        "If you see the 'Unable to determine mode of operation' exception, just kill me and run `mvn -C clean package`\n\n" +
        "=================================================\n\n");
    }

    private static ServerConnector httpConnector(int port) {
        var httpConfig = new HttpConfiguration();
        var httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfig), new HTTP2CServerConnectionFactory(httpConfig));
        httpConnector.setPort(port);
        return httpConnector;
    }

    private static ServerConnector httpsConnector() {
        var httpsConfig = new HttpConfiguration();
        httpsConfig.setSecureScheme("https");
        httpsConfig.setSecurePort(8443);
        httpsConfig.addCustomizer(new SecureRequestCustomizer(false));
        var h2 = new HTTP2ServerConnectionFactory(httpsConfig);

        var alpn = new ALPNServerConnectionFactory();
        alpn.setDefaultProtocol(HttpVersion.HTTP_1_1.asString());

        var ssl = new SslConnectionFactory(serverSslContextFactory(), alpn.getProtocol());

        var httpsConnector = new ServerConnector(server, ssl, alpn, h2, new HttpConnectionFactory(httpsConfig));
        httpsConnector.setPort(8443);
        return httpsConnector;
    }

    private static SslContextFactory.Server serverSslContextFactory() {
        var factory = new SslContextFactory.Server();
        factory.setKeyStorePath("./keystore.pfx");
        factory.setKeyStorePassword("OBF:1z0f1vu91vv11z0f");
        factory.setKeyStoreType("PKCS12");
        factory.setCipherComparator(HTTP2Cipher.COMPARATOR);
        factory.setProvider("Conscrypt");
        return factory;
    }

    public static void stop() throws Exception {
        server.stop();
        server = null;
    }

    private static boolean isProductionMode() {
        final String probe = "META-INF/maven/com.vaadin/flow-server-production-mode/pom.xml";
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader.getResource(probe) != null;
    }

    @NotNull
    private static Resource findWebRoot() throws MalformedURLException {
        // don't look up directory as a resource, it's unreliable: https://github.com/eclipse/jetty.project/issues/4173#issuecomment-539769734
        // instead we'll look up the /webapp/ROOT and retrieve the parent folder from that.
        final URL f = Main.class.getResource("/webapp/ROOT");
        if (f == null) {
            throw new IllegalStateException("Invalid state: the resource /webapp/ROOT doesn't exist, has webapp been packaged in as a resource?");
        }
        final String url = f.toString();
        if (!url.endsWith("/ROOT")) {
            throw new RuntimeException("Parameter url: invalid value " + url + ": doesn't end with /ROOT");
        }
        System.err.println("/webapp/ROOT is " + f);

        // Resolve file to directory
        URL webRoot = new URL(url.substring(0, url.length() - 5));
        System.err.println("WebRoot is " + webRoot);
        return Resource.newResource(webRoot);
    }
}

