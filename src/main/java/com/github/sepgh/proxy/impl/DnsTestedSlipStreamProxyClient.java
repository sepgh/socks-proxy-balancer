package com.github.sepgh.proxy.impl;

import com.github.sepgh.config.ProxyConfig;
import com.github.sepgh.dns.DnsEndpoint;
import com.github.sepgh.dns.DnsTestResult;
import com.github.sepgh.dns.DnsTester;
import com.github.sepgh.proxy.AbstractProxyClient;
import com.github.sepgh.proxy.ProxyEndpoint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DnsTestedSlipStreamProxyClient extends AbstractProxyClient {
    private SlipStreamProxyClient slipStreamClient;
    private DnsEndpoint selectedDnsEndpoint;
    private final int dnsTestTimeoutMs;
    private final String dnsTestDomain;

    public DnsTestedSlipStreamProxyClient(ProxyConfig config) {
        super(config);
        this.dnsTestTimeoutMs = getConfigInt("dns_test_timeout_ms", 3000);
        this.dnsTestDomain = getConfigString("dns_test_domain", "www.google.com");
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
        DnsEndpoint bestDnsEndpoint = selectBestDnsEndpoint(dnsEndpoints);
        
        if (bestDnsEndpoint == null) {
            throw new RuntimeException("No working DNS endpoint found for " + getName());
        }

        logger.info("Selected DNS endpoint {} for {}", bestDnsEndpoint, getName());
        this.selectedDnsEndpoint = bestDnsEndpoint;

        ProxyConfig slipStreamConfig = createSlipStreamConfig(bestDnsEndpoint);
        this.slipStreamClient = new SlipStreamProxyClient(slipStreamConfig);
        
        try {
            slipStreamClient.start();
            this.endpoint = slipStreamClient.getEndpoint();
            setRunning(true);
            logger.info("DNS-tested SlipStream proxy client {} started successfully", getName());
        } catch (Exception e) {
            logger.error("Failed to start SlipStream client for {}", getName(), e);
            throw new RuntimeException("SlipStream client failed to start: " + e.getMessage(), e);
        }
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

    private DnsEndpoint selectBestDnsEndpoint(List<DnsEndpoint> endpoints) {
        DnsTester dnsTester = new DnsTester(dnsTestTimeoutMs, dnsTestDomain);
        
        Map<DnsEndpoint, DnsTestResult> results = new HashMap<>();
        for (DnsEndpoint endpoint : endpoints) {
            DnsTestResult result = dnsTester.test(endpoint);
            results.put(endpoint, result);
            logger.debug("DNS test result for {}: {}", endpoint, result);
        }

        List<Map.Entry<DnsEndpoint, DnsTestResult>> sortedResults = results.entrySet().stream()
            .filter(entry -> entry.getValue().isSuccess())
            .sorted(Comparator.comparingLong(entry -> entry.getValue().getLatencyMs()))
            .collect(Collectors.toList());

        if (sortedResults.isEmpty()) {
            logger.error("No DNS endpoints passed the test");
            return null;
        }

        logger.info("DNS endpoints sorted by latency:");
        for (Map.Entry<DnsEndpoint, DnsTestResult> entry : sortedResults) {
            logger.info("  {} - {}ms", entry.getKey(), entry.getValue().getLatencyMs());
        }

        return sortedResults.get(0).getKey();
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
}
