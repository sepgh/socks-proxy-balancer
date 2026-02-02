package com.github.sepgh.health;

import com.github.sepgh.config.ConfigurationManager;
import com.github.sepgh.config.ProxyConfig;
import com.github.sepgh.proxy.ProxyClient;
import com.github.sepgh.proxy.ProxyClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class HealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(HealthChecker.class);
    
    private final ConfigurationManager configManager;
    private final ProxyTester proxyTester;
    private final Map<String, ProxyClient> activeClients = new ConcurrentHashMap<>();
    private final AtomicReference<ProxyClient> selectedProxy = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService testExecutor = Executors.newFixedThreadPool(5);
    
    private volatile boolean running = false;
    private final int healthCheckIntervalSeconds;
    private final int currentProxyCheckIntervalSeconds;

    public HealthChecker(ConfigurationManager configManager, ProxyTester proxyTester) {
        this.configManager = configManager;
        this.proxyTester = proxyTester;
        this.healthCheckIntervalSeconds = configManager.getConfig().getHealthCheckIntervalSeconds();
        this.currentProxyCheckIntervalSeconds = configManager.getConfig().getCurrentProxyCheckIntervalSeconds();
    }

    public void start() {
        if (running) {
            logger.warn("HealthChecker is already running");
            return;
        }
        
        running = true;
        logger.info("Starting HealthChecker");
        
        selectInitialProxy();
        
        scheduler.scheduleWithFixedDelay(
            this::checkAllProxies,
            healthCheckIntervalSeconds,
            healthCheckIntervalSeconds,
            TimeUnit.SECONDS
        );
        
        scheduler.scheduleWithFixedDelay(
            this::checkCurrentProxy,
            currentProxyCheckIntervalSeconds,
            currentProxyCheckIntervalSeconds,
            TimeUnit.SECONDS
        );
    }

    public void stop() {
        if (!running) {
            return;
        }
        
        logger.info("Stopping HealthChecker");
        running = false;
        
        scheduler.shutdown();
        testExecutor.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!testExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                testExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            testExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        stopAllClients();
    }

    private void selectInitialProxy() {
        logger.info("Selecting initial proxy");
        List<ProxyConfig> proxies = configManager.getProxies();
        
        if (proxies.isEmpty()) {
            logger.error("No proxies configured");
            return;
        }
        
        Map<ProxyClient, ProxyTestResult> results = testProxies(proxies);
        ProxyClient bestProxy = selectBestProxy(results);
        
        if (bestProxy != null) {
            switchToProxy(bestProxy);
        } else {
            logger.error("No working proxy found during initialization");
        }
    }

    private void checkAllProxies() {
        if (!running) return;
        
        logger.debug("Running health check on all proxies");
        List<ProxyConfig> proxies = configManager.getProxies();
        Map<ProxyClient, ProxyTestResult> results = testProxies(proxies);
        
        ProxyClient current = selectedProxy.get();
        ProxyClient bestProxy = selectBestProxy(results);
        
        if (bestProxy == null) {
            logger.warn("No working proxy found during health check");
            return;
        }
        
        if (bestProxy != current) {
            logger.info("Switching to better proxy: {}", bestProxy.getName());
            switchToProxy(bestProxy);
        } else {
            logger.debug("Current proxy {} is still the best option", current.getName());
        }
    }

    private void checkCurrentProxy() {
        if (!running) return;
        
        ProxyClient current = selectedProxy.get();
        if (current == null) {
            logger.warn("No current proxy selected, attempting to select one");
            selectInitialProxy();
            return;
        }
        
        logger.debug("Checking current proxy: {}", current.getName());
        ProxyTestResult result = proxyTester.test(current.getEndpoint());
        
        if (!result.isSuccess()) {
            logger.warn("Current proxy {} failed health check: {}", current.getName(), result.getErrorMessage());
            selectInitialProxy();
        } else {
            logger.debug("Current proxy {} is healthy (latency: {}ms)", current.getName(), result.getLatencyMs());
        }
    }

    private Map<ProxyClient, ProxyTestResult> testProxies(List<ProxyConfig> proxies) {
        Map<ProxyClient, ProxyTestResult> results = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        
        for (ProxyConfig config : proxies) {
            if (!config.isEnabled()) {
                continue;
            }
            
            futures.add(testExecutor.submit(() -> {
                try {
                    ProxyClient client = activeClients.computeIfAbsent(config.getName(), name -> {
                        ProxyClient newClient = ProxyClientFactory.createClient(config);
                        try {
                            newClient.start();
                            return newClient;
                        } catch (Exception e) {
                            logger.error("Failed to start proxy client {}", name, e);
                            return null;
                        }
                    });
                    
                    if (client != null && client.isRunning()) {
                        ProxyTestResult result = proxyTester.test(client.getEndpoint());
                        results.put(client, result);
                        logger.debug("Test result for {}: {}", client.getName(), result);
                    }
                } catch (Exception e) {
                    logger.error("Error testing proxy {}", config.getName(), e);
                }
            }));
        }
        
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.error("Error waiting for proxy test", e);
            }
        }
        
        return results;
    }

    private ProxyClient selectBestProxy(Map<ProxyClient, ProxyTestResult> results) {
        return results.entrySet().stream()
            .filter(entry -> entry.getValue().isSuccess())
            .min(Comparator.comparingLong(entry -> entry.getValue().getLatencyMs()))
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private void switchToProxy(ProxyClient newProxy) {
        ProxyClient oldProxy = selectedProxy.getAndSet(newProxy);
        if (oldProxy != null && oldProxy != newProxy) {
            logger.info("Switched from proxy {} to {}", oldProxy.getName(), newProxy.getName());
        } else {
            logger.info("Selected proxy: {}", newProxy.getName());
        }
    }

    private void stopAllClients() {
        for (ProxyClient client : activeClients.values()) {
            try {
                client.stop();
            } catch (Exception e) {
                logger.error("Error stopping proxy client {}", client.getName(), e);
            }
        }
        activeClients.clear();
    }

    public ProxyClient getSelectedProxy() {
        return selectedProxy.get();
    }
}
