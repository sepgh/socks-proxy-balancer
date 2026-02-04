package com.github.sepgh.proxy.impl;

import com.github.sepgh.config.ProxyConfig;
import com.github.sepgh.dns.DnsEndpoint;
import com.github.sepgh.dns.DnsTestResult;
import com.github.sepgh.dns.DnsTester;
import com.github.sepgh.network.NetworkInterfaceMonitor;
import com.github.sepgh.proxy.AbstractProxyClient;
import com.github.sepgh.proxy.ProxyEndpoint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DnsTestedSlipStreamProxyClient extends AbstractProxyClient {
    private SlipStreamProxyClient slipStreamClient;
    private DnsEndpoint selectedDnsEndpoint;
    private List<DnsEndpoint> sortedDnsEndpoints;
    private int currentDnsEndpointIndex = 0;
    private final int dnsTestTimeoutMs;
    private final String dnsTestDomain;
    private final int maxRetries;
    private final NetworkInterfaceMonitor networkMonitor;

    public DnsTestedSlipStreamProxyClient(ProxyConfig config) {
        super(config);
        this.dnsTestTimeoutMs = getConfigInt("dns_test_timeout_ms", 3000);
        this.dnsTestDomain = getConfigString("dns_test_domain", "www.google.com");
        this.maxRetries = getConfigInt("max_dns_retries", 5);
        
        String networkInterface = getConfigString("network_interface", null);
        this.networkMonitor = new NetworkInterfaceMonitor(networkInterface);
    }

    @Override
    public void start() throws Exception {
        if (isRunning()) {
            logger.warn("DNS-tested SlipStream proxy client {} is already running", getName());
            return;
        }

        List<DnsEndpoint> dnsEndpoints = loadDnsEndpoints();
        if (dnsEndpoints.isEmpty()) {
            throw new IllegalArgumentException("No DNS endpoints configured for " + getName());
        }

        logger.info("Testing {} DNS endpoints for {}", dnsEndpoints.size(), getName());
        this.sortedDnsEndpoints = selectAndSortDnsEndpoints(dnsEndpoints);
        
        if (sortedDnsEndpoints.isEmpty()) {
            throw new RuntimeException("No working DNS endpoint found for " + getName());
        }

        // Try to start with the best DNS endpoints, rotating through them if one fails
        Exception lastException = null;
        int attempts = Math.min(maxRetries, sortedDnsEndpoints.size());
        
        for (int i = 0; i < attempts; i++) {
            DnsEndpoint dnsEndpoint = sortedDnsEndpoints.get(i);
            logger.info("Attempting to start SlipStream with DNS endpoint {} ({}/{})", dnsEndpoint, i + 1, attempts);
            
            try {
                if (startWithDnsEndpoint(dnsEndpoint)) {
                    this.currentDnsEndpointIndex = i;
                    this.selectedDnsEndpoint = dnsEndpoint;
                    logger.info("DNS-tested SlipStream proxy client {} started successfully with DNS endpoint {}", getName(), dnsEndpoint);
                    return;
                }
            } catch (Exception e) {
                logger.warn("Failed to start SlipStream with DNS endpoint {}: {}", dnsEndpoint, e.getMessage());
                lastException = e;
                // Clean up failed attempt
                if (slipStreamClient != null) {
                    try {
                        slipStreamClient.stop();
                    } catch (Exception stopEx) {
                        logger.debug("Error stopping failed SlipStream client", stopEx);
                    }
                    slipStreamClient = null;
                }
            }
        }
        
        throw new RuntimeException("Failed to start SlipStream with any of the " + attempts + " DNS endpoints tried", lastException);
    }
    
    private boolean startWithDnsEndpoint(DnsEndpoint dnsEndpoint) throws Exception {
        ProxyConfig slipStreamConfig = createSlipStreamConfig(dnsEndpoint);
        this.slipStreamClient = new SlipStreamProxyClient(slipStreamConfig);
        
        slipStreamClient.start();
        this.endpoint = slipStreamClient.getEndpoint();
        setRunning(true);
        return true;
    }
    
    public boolean rotateToNextDnsEndpoint() {
        if (!networkMonitor.isNetworkAvailable()) {
            logger.warn("Network interface is down, skipping DNS rotation for {}", getName());
            return false;
        }
        
        if (sortedDnsEndpoints == null || sortedDnsEndpoints.isEmpty()) {
            logger.error("No DNS endpoints available for rotation");
            return false;
        }
        
        int nextIndex = currentDnsEndpointIndex + 1;
        int attempts = 0;
        int maxAttempts = Math.min(maxRetries, sortedDnsEndpoints.size() - nextIndex);
        
        while (attempts < maxAttempts) {
            int tryIndex = (nextIndex + attempts) % sortedDnsEndpoints.size();
            DnsEndpoint dnsEndpoint = sortedDnsEndpoints.get(tryIndex);
            
            logger.info("Rotating to next DNS endpoint: {} (attempt {}/{})", dnsEndpoint, attempts + 1, maxAttempts);
            
            // Stop current client
            if (slipStreamClient != null) {
                try {
                    slipStreamClient.stop();
                } catch (Exception e) {
                    logger.debug("Error stopping SlipStream client during rotation", e);
                }
            }
            
            try {
                if (startWithDnsEndpoint(dnsEndpoint)) {
                    this.currentDnsEndpointIndex = tryIndex;
                    this.selectedDnsEndpoint = dnsEndpoint;
                    // Reset health status for clean slate
                    if (slipStreamClient != null) {
                        slipStreamClient.resetHealthStatus();
                    }
                    logger.info("Successfully rotated to DNS endpoint {}", dnsEndpoint);
                    return true;
                }
            } catch (Exception e) {
                logger.warn("Failed to rotate to DNS endpoint {}: {}", dnsEndpoint, e.getMessage());
                if (slipStreamClient != null) {
                    try {
                        slipStreamClient.stop();
                    } catch (Exception stopEx) {
                        logger.debug("Error stopping failed SlipStream client", stopEx);
                    }
                    slipStreamClient = null;
                }
            }
            
            attempts++;
        }
        
        logger.error("Failed to rotate to any available DNS endpoint after {} attempts", attempts);
        setRunning(false);
        return false;
    }

    @Override
    public void stop() throws Exception {
        if (!isRunning()) {
            return;
        }

        logger.info("Stopping DNS-tested SlipStream proxy client {}", getName());
        setRunning(false);

        if (slipStreamClient != null) {
            slipStreamClient.stop();
        }

        logger.info("DNS-tested SlipStream proxy client {} stopped", getName());
    }

    private List<DnsEndpoint> loadDnsEndpoints() {
        List<DnsEndpoint> endpoints = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<String> dnsEndpointsList = (List<String>) config.getConfig().get("dns_endpoints");
        if (dnsEndpointsList != null && !dnsEndpointsList.isEmpty()) {
            for (String endpointStr : dnsEndpointsList) {
                try {
                    endpoints.add(new DnsEndpoint(endpointStr));
                } catch (Exception e) {
                    logger.warn("Invalid DNS endpoint format: {}", endpointStr, e);
                }
            }
        }

        String dnsEndpointsFile = getConfigString("dns_endpoints_file", null);
        if (dnsEndpointsFile != null && !dnsEndpointsFile.isEmpty()) {
            File file = new File(dnsEndpointsFile);
            if (file.exists() && file.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            try {
                                endpoints.add(new DnsEndpoint(line));
                            } catch (Exception e) {
                                logger.warn("Invalid DNS endpoint in file: {}", line, e);
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error reading DNS endpoints file: {}", dnsEndpointsFile, e);
                }
            } else {
                logger.warn("DNS endpoints file not found or not readable: {}", dnsEndpointsFile);
            }
        }

        return endpoints;
    }

    private List<DnsEndpoint> selectAndSortDnsEndpoints(List<DnsEndpoint> endpoints) {
        DnsTester dnsTester = new DnsTester(dnsTestTimeoutMs, dnsTestDomain);
        
        Map<DnsEndpoint, DnsTestResult> results = new HashMap<>();
        int total = endpoints.size();
        int tested = 0;
        
        for (DnsEndpoint endpoint : endpoints) {
            tested++;
            DnsTestResult result = dnsTester.test(endpoint);
            results.put(endpoint, result);
            
            if (tested % 10 == 0 || tested == total) {
                logger.info("DNS testing progress: {}/{} endpoints tested", tested, total);
            }
            logger.debug("DNS test result for {}: {}", endpoint, result);
        }

        List<Map.Entry<DnsEndpoint, DnsTestResult>> sortedResults = results.entrySet().stream()
            .filter(entry -> entry.getValue().isSuccess())
            .sorted(Comparator.comparingLong(entry -> entry.getValue().getLatencyMs()))
            .collect(Collectors.toList());

        if (sortedResults.isEmpty()) {
            logger.error("No DNS endpoints passed the test");
            return new ArrayList<>();
        }

        logger.info("DNS endpoints sorted by latency:");
        List<DnsEndpoint> sortedEndpoints = new ArrayList<>();
        for (Map.Entry<DnsEndpoint, DnsTestResult> entry : sortedResults) {
            logger.info("  {} - {}ms", entry.getKey(), entry.getValue().getLatencyMs());
            sortedEndpoints.add(entry.getKey());
        }

        return sortedEndpoints;
    }

    private ProxyConfig createSlipStreamConfig(DnsEndpoint dnsEndpoint) {
        Map<String, Object> slipStreamConfigMap = new HashMap<>(config.getConfig());
        
        slipStreamConfigMap.put("resolver_ip", dnsEndpoint.getIp());
        slipStreamConfigMap.put("resolver_port", dnsEndpoint.getPort());

        ProxyConfig slipStreamConfig = new ProxyConfig();
        slipStreamConfig.setType("slipstream");
        slipStreamConfig.setName(config.getName() + "-slipstream");
        slipStreamConfig.setEnabled(true);
        slipStreamConfig.setConfig(slipStreamConfigMap);

        return slipStreamConfig;
    }

    public DnsEndpoint getSelectedDnsEndpoint() {
        return selectedDnsEndpoint;
    }
    
    @Override
    public boolean isHealthy() {
        if (!isRunning()) {
            return false;
        }
        
        // Delegate to underlying SlipStream client's health status
        if (slipStreamClient != null) {
            return slipStreamClient.isHealthy();
        }
        
        return false;
    }
}
