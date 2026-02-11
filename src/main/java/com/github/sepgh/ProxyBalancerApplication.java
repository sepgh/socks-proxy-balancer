package com.github.sepgh;

import com.github.sepgh.config.ConfigurationManager;
import com.github.sepgh.config.LoggingConfigurator;
import com.github.sepgh.health.HealthChecker;
import com.github.sepgh.health.ProxyTester;
import com.github.sepgh.server.SocksProxyServer;
import com.github.sepgh.server.StatusHttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ProxyBalancerApplication {
    private static final Logger logger = LoggerFactory.getLogger(ProxyBalancerApplication.class);
    private static final String DEFAULT_CONFIG_PATH = "config.yaml";

    private final ConfigurationManager configManager;
    private final HealthChecker healthChecker;
    private final SocksProxyServer proxyServer;
    private final StatusHttpServer statusServer;

    public ProxyBalancerApplication(String configPath) throws IOException {
        logger.info("Initializing Proxy Balancer Application");
        
        this.configManager = new ConfigurationManager(configPath);
        
        // Configure file logging if enabled (must happen early)
        LoggingConfigurator.configure(configManager.getConfig());
        
        ProxyTester proxyTester = new ProxyTester(
            configManager.getConfig().getConnectionTimeoutMs(),
            configManager.getConfig().getTestUrl(),
            configManager.getConfig().getTestRounds()
        );
        
        this.healthChecker = new HealthChecker(configManager, proxyTester);
        
        this.proxyServer = new SocksProxyServer(
            configManager.getConfig().getListenHost(),
            configManager.getConfig().getListenPort(),
            healthChecker,
            configManager.getConfig().getSoRcvBuf(),
            configManager.getConfig().getSoSndBuf()
        );
        
        if (configManager.getConfig().isStatusEnabled()) {
            this.statusServer = new StatusHttpServer(
                configManager.getConfig().getStatusHost(),
                configManager.getConfig().getStatusPort(),
                configManager.getConfig().getListenHost(),
                configManager.getConfig().getListenPort(),
                healthChecker
            );
        } else {
            this.statusServer = null;
        }
    }

    public void start() throws IOException {
        logger.info("Starting Proxy Balancer Application");
        
        healthChecker.start();
        
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during startup", e);
        }
        
        proxyServer.start();
        
        if (statusServer != null) {
            statusServer.start();
        }
        
        logger.info("Proxy Balancer Application started successfully");
        logger.info("Listening on {}:{}", 
            configManager.getConfig().getListenHost(), 
            configManager.getConfig().getListenPort());
    }

    public void stop() {
        logger.info("Stopping Proxy Balancer Application");
        
        if (statusServer != null) {
            statusServer.stop();
        }
        proxyServer.stop();
        healthChecker.stop();
        
        logger.info("Proxy Balancer Application stopped");
    }

    public void waitForShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            stop();
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Application interrupted");
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : DEFAULT_CONFIG_PATH;
        
        try {
            ProxyBalancerApplication app = new ProxyBalancerApplication(configPath);
            app.start();
            app.waitForShutdown();
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            System.exit(1);
        }
    }
}
