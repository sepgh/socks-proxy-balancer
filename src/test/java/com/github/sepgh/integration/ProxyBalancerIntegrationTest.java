package com.github.sepgh.integration;

import com.github.sepgh.ProxyBalancerApplication;
import com.github.sepgh.config.ApplicationConfig;
import com.github.sepgh.config.ConfigurationManager;
import com.github.sepgh.config.ProxyConfig;
import com.github.sepgh.health.HealthChecker;
import com.github.sepgh.health.ProxyTester;
import com.github.sepgh.proxy.ProxyClient;
import com.github.sepgh.server.SocksProxyServer;
import com.github.sepgh.test.TestHttpServer;
import com.github.sepgh.test.TestSocksServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ProxyBalancerIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(ProxyBalancerIntegrationTest.class);
    
    private static final String TEST_HOST = "127.0.0.1";
    private static final int BALANCER_PORT = 11080;
    private static final int HTTP_SERVER_PORT = 18080;
    private static final int FAST_PROXY_PORT = 12081;
    private static final int MEDIUM_PROXY_PORT = 12082;
    private static final int SLOW_PROXY_PORT = 12083;
    
    private TestHttpServer httpServer;
    private TestSocksServer fastProxy;
    private TestSocksServer mediumProxy;
    private TestSocksServer slowProxy;
    private ConfigurationManager configManager;
    private HealthChecker healthChecker;
    private SocksProxyServer balancerServer;

    public static void main(String[] args) {
        ProxyBalancerIntegrationTest test = new ProxyBalancerIntegrationTest();
        try {
            test.runAllTests();
            logger.info("=== ALL TESTS PASSED ===");
            System.exit(0);
        } catch (Exception e) {
            logger.error("=== TEST FAILED ===", e);
            System.exit(1);
        }
    }

    public void runAllTests() throws Exception {
        logger.info("=== Starting Integration Tests ===");
        
        try {
            setupTestEnvironment();
            
            testInitialProxySelection();
            testProxyForwarding();
            testLatencyBasedSelection();
            testProxyFailover();
            testMultipleConnections();
            
            logger.info("=== All Tests Completed Successfully ===");
        } finally {
            tearDown();
        }
    }

    private void setupTestEnvironment() throws Exception {
        logger.info("Setting up test environment...");
        
        httpServer = new TestHttpServer(TEST_HOST, HTTP_SERVER_PORT);
        httpServer.start();
        logger.info("Test HTTP server started on port {}", HTTP_SERVER_PORT);
        
        fastProxy = new TestSocksServer(TEST_HOST, FAST_PROXY_PORT, 10);
        fastProxy.start();
        logger.info("Fast proxy started on port {} (10ms latency)", FAST_PROXY_PORT);
        
        mediumProxy = new TestSocksServer(TEST_HOST, MEDIUM_PROXY_PORT, 100);
        mediumProxy.start();
        logger.info("Medium proxy started on port {} (100ms latency)", MEDIUM_PROXY_PORT);
        
        slowProxy = new TestSocksServer(TEST_HOST, SLOW_PROXY_PORT, 300);
        slowProxy.start();
        logger.info("Slow proxy started on port {} (300ms latency)", SLOW_PROXY_PORT);
        
        Thread.sleep(500);
        
        Path configPath = createTestConfig();
        configManager = new ConfigurationManager(configPath.toString());
        
        ProxyTester proxyTester = new ProxyTester(
            configManager.getConfig().getConnectionTimeoutMs(),
            "http://" + TEST_HOST + ":" + HTTP_SERVER_PORT
        );
        
        healthChecker = new HealthChecker(configManager, proxyTester);
        healthChecker.start();
        
        Thread.sleep(3000);
        
        balancerServer = new SocksProxyServer(TEST_HOST, BALANCER_PORT, healthChecker);
        balancerServer.start();
        
        logger.info("Proxy balancer started on port {}", BALANCER_PORT);
        Thread.sleep(1000);
    }

    private Path createTestConfig() throws IOException {
        ApplicationConfig config = new ApplicationConfig();
        config.setListenHost(TEST_HOST);
        config.setListenPort(BALANCER_PORT);
        config.setHealthCheckIntervalSeconds(5);
        config.setCurrentProxyCheckIntervalSeconds(2);
        config.setConnectionTimeoutMs(3000);
        config.setTestUrl("http://" + TEST_HOST + ":" + HTTP_SERVER_PORT);
        
        ProxyConfig fastProxyConfig = new ProxyConfig();
        fastProxyConfig.setType("direct");
        fastProxyConfig.setName("fast-proxy");
        fastProxyConfig.setEnabled(true);
        Map<String, Object> fastConfig = new HashMap<>();
        fastConfig.put("host", TEST_HOST);
        fastConfig.put("port", FAST_PROXY_PORT);
        fastProxyConfig.setConfig(fastConfig);
        
        ProxyConfig mediumProxyConfig = new ProxyConfig();
        mediumProxyConfig.setType("direct");
        mediumProxyConfig.setName("medium-proxy");
        mediumProxyConfig.setEnabled(true);
        Map<String, Object> mediumConfig = new HashMap<>();
        mediumConfig.put("host", TEST_HOST);
        mediumConfig.put("port", MEDIUM_PROXY_PORT);
        mediumProxyConfig.setConfig(mediumConfig);
        
        ProxyConfig slowProxyConfig = new ProxyConfig();
        slowProxyConfig.setType("direct");
        slowProxyConfig.setName("slow-proxy");
        slowProxyConfig.setEnabled(true);
        Map<String, Object> slowConfig = new HashMap<>();
        slowConfig.put("host", TEST_HOST);
        slowConfig.put("port", SLOW_PROXY_PORT);
        slowProxyConfig.setConfig(slowConfig);
        
        config.getProxies().add(fastProxyConfig);
        config.getProxies().add(mediumProxyConfig);
        config.getProxies().add(slowProxyConfig);
        
        Path tempConfig = Files.createTempFile("test-config", ".yaml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.writerWithDefaultPrettyPrinter().writeValue(tempConfig.toFile(), config);
        
        logger.info("Test configuration created at {}", tempConfig);
        return tempConfig;
    }

    private void testInitialProxySelection() throws Exception {
        logger.info("\n=== Test 1: Initial Proxy Selection ===");
        
        ProxyClient selectedProxy = healthChecker.getSelectedProxy();
        assertNotNull(selectedProxy, "A proxy should be selected");
        
        String selectedName = selectedProxy.getName();
        logger.info("Selected proxy: {}", selectedName);
        
        assertEquals("fast-proxy", selectedName, 
            "Fast proxy should be selected initially (lowest latency)");
        
        logger.info("✓ Test 1 PASSED: Fast proxy was correctly selected");
    }

    private void testProxyForwarding() throws Exception {
        logger.info("\n=== Test 2: Proxy Forwarding ===");
        
        int initialHttpRequests = httpServer.getRequestCount();
        int initialFastProxyConnections = fastProxy.getConnectionCount();
        
        String response = makeRequestThroughBalancer();
        
        assertEquals("SUCCESS", response, "Should receive SUCCESS response from HTTP server");
        
        int finalHttpRequests = httpServer.getRequestCount();
        int finalFastProxyConnections = fastProxy.getConnectionCount();
        
        assertTrue(finalHttpRequests > initialHttpRequests, 
            "HTTP server should have received a request");
        assertTrue(finalFastProxyConnections > initialFastProxyConnections, 
            "Fast proxy should have forwarded the connection");
        
        logger.info("✓ Test 2 PASSED: Request successfully forwarded through balancer -> fast proxy -> HTTP server");
    }

    private void testLatencyBasedSelection() throws Exception {
        logger.info("\n=== Test 3: Latency-Based Selection ===");
        
        ProxyClient selectedProxy = healthChecker.getSelectedProxy();
        String selectedName = selectedProxy.getName();
        
        logger.info("Currently selected proxy: {}", selectedName);
        
        assertEquals("fast-proxy", selectedName, 
            "Fast proxy should remain selected (lowest latency: 10ms vs 100ms vs 300ms)");
        
        int fastConnections = fastProxy.getConnectionCount();
        int mediumConnections = mediumProxy.getConnectionCount();
        int slowConnections = slowProxy.getConnectionCount();
        
        logger.info("Connection counts - Fast: {}, Medium: {}, Slow: {}", 
            fastConnections, mediumConnections, slowConnections);
        
        assertTrue(fastConnections > 0, "Fast proxy should have connections");
        
        logger.info("✓ Test 3 PASSED: Balancer correctly prefers lowest latency proxy");
    }

    private void testProxyFailover() throws Exception {
        logger.info("\n=== Test 4: Proxy Failover ===");
        
        ProxyClient initialProxy = healthChecker.getSelectedProxy();
        assertEquals("fast-proxy", initialProxy.getName(), "Fast proxy should be selected initially");
        
        logger.info("Stopping fast proxy to trigger failover...");
        fastProxy.stop();
        
        Thread.sleep(4000);
        
        ProxyClient newProxy = healthChecker.getSelectedProxy();
        assertNotNull(newProxy, "A proxy should still be selected after failover");
        
        String newProxyName = newProxy.getName();
        logger.info("New selected proxy after failover: {}", newProxyName);
        
        assertEquals("medium-proxy", newProxyName, 
            "Medium proxy should be selected after fast proxy fails (100ms < 300ms)");
        
        String response = makeRequestThroughBalancer();
        assertEquals("SUCCESS", response, "Requests should still work through new proxy");
        
        logger.info("Restarting fast proxy...");
        fastProxy = new TestSocksServer(TEST_HOST, FAST_PROXY_PORT, 10);
        fastProxy.start();
        
        Thread.sleep(6000);
        
        ProxyClient finalProxy = healthChecker.getSelectedProxy();
        logger.info("Selected proxy after fast proxy recovery: {}", finalProxy.getName());
        
        assertEquals("fast-proxy", finalProxy.getName(), 
            "Should switch back to fast proxy after it recovers");
        
        logger.info("✓ Test 4 PASSED: Failover and recovery work correctly");
    }

    private void testMultipleConnections() throws Exception {
        logger.info("\n=== Test 5: Multiple Concurrent Connections ===");
        
        int initialRequests = httpServer.getRequestCount();
        
        Thread[] threads = new Thread[10];
        final String[] responses = new String[10];
        final Exception[] exceptions = new Exception[10];
        
        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    responses[index] = makeRequestThroughBalancer();
                } catch (Exception e) {
                    exceptions[index] = e;
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        for (int i = 0; i < 10; i++) {
            assertNull(exceptions[i], "Request " + i + " should not throw exception");
            assertEquals("SUCCESS", responses[i], "Request " + i + " should receive SUCCESS");
        }
        
        int finalRequests = httpServer.getRequestCount();
        int processedRequests = finalRequests - initialRequests;
        
        assertTrue(processedRequests >= 10, 
            "HTTP server should have processed at least 10 requests, got: " + processedRequests);
        
        logger.info("✓ Test 5 PASSED: Successfully handled {} concurrent connections", processedRequests);
    }

    private String makeRequestThroughBalancer() throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(TEST_HOST, BALANCER_PORT), 5000);
        socket.setSoTimeout(5000);
        
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        
        out.write(new byte[]{0x05, 0x01, 0x00});
        out.flush();
        
        byte[] handshakeResponse = new byte[2];
        if (in.read(handshakeResponse) != 2) {
            throw new IOException("Failed to read handshake response");
        }
        
        byte[] hostBytes = TEST_HOST.getBytes(StandardCharsets.UTF_8);
        byte[] connectRequest = new byte[7 + hostBytes.length];
        connectRequest[0] = 0x05;
        connectRequest[1] = 0x01;
        connectRequest[2] = 0x00;
        connectRequest[3] = 0x03;
        connectRequest[4] = (byte) hostBytes.length;
        System.arraycopy(hostBytes, 0, connectRequest, 5, hostBytes.length);
        connectRequest[5 + hostBytes.length] = (byte) ((HTTP_SERVER_PORT >> 8) & 0xFF);
        connectRequest[6 + hostBytes.length] = (byte) (HTTP_SERVER_PORT & 0xFF);
        
        out.write(connectRequest);
        out.flush();
        
        byte[] connectResponse = new byte[10];
        int bytesRead = in.read(connectResponse);
        if (bytesRead < 2) {
            throw new IOException("Failed to read connect response");
        }
        
        String httpRequest = "GET / HTTP/1.1\r\n" +
                "Host: " + TEST_HOST + ":" + HTTP_SERVER_PORT + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(httpRequest.getBytes(StandardCharsets.UTF_8));
        out.flush();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
        }
        
        StringBuilder body = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        
        socket.close();
        return body.toString();
    }

    private void tearDown() {
        logger.info("Tearing down test environment...");
        
        if (balancerServer != null) {
            balancerServer.stop();
        }
        
        if (healthChecker != null) {
            healthChecker.stop();
        }
        
        if (fastProxy != null && fastProxy.isRunning()) {
            fastProxy.stop();
        }
        
        if (mediumProxy != null && mediumProxy.isRunning()) {
            mediumProxy.stop();
        }
        
        if (slowProxy != null && slowProxy.isRunning()) {
            slowProxy.stop();
        }
        
        if (httpServer != null) {
            httpServer.stop();
        }
        
        logger.info("Test environment cleaned up");
    }

    private void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError(message);
        }
    }

    private void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) {
            return;
        }
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private void assertNull(Object obj, String message) {
        if (obj != null) {
            throw new AssertionError(message + " - Expected null but got: " + obj);
        }
    }
}
