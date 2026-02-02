package com.github.sepgh.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private ApplicationConfig config;
    private final ObjectMapper objectMapper;

    public ConfigurationManager(String configPath) throws IOException {
        this.objectMapper = new ObjectMapper(new YAMLFactory());
        this.config = loadConfig(configPath);
    }

    private ApplicationConfig loadConfig(String configPath) throws IOException {
        File configFile = new File(configPath);
        if (!configFile.exists()) {
            logger.warn("Configuration file not found at {}, using defaults", configPath);
            return new ApplicationConfig();
        }
        return objectMapper.readValue(configFile, ApplicationConfig.class);
    }

    public ApplicationConfig getConfig() {
        lock.readLock().lock();
        try {
            return config;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ProxyConfig> getProxies() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(config.getProxies());
        } finally {
            lock.readLock().unlock();
        }
    }

    public void addProxy(ProxyConfig proxyConfig) {
        lock.writeLock().lock();
        try {
            config.getProxies().add(proxyConfig);
            logger.info("Added new proxy configuration: {}", proxyConfig);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeProxy(String proxyName) {
        lock.writeLock().lock();
        try {
            config.getProxies().removeIf(p -> p.getName().equals(proxyName));
            logger.info("Removed proxy configuration: {}", proxyName);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateProxyEnabled(String proxyName, boolean enabled) {
        lock.writeLock().lock();
        try {
            config.getProxies().stream()
                    .filter(p -> p.getName().equals(proxyName))
                    .findFirst()
                    .ifPresent(p -> {
                        p.setEnabled(enabled);
                        logger.info("Updated proxy {} enabled status to {}", proxyName, enabled);
                    });
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void saveConfig(String configPath) throws IOException {
        lock.readLock().lock();
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(configPath), config);
            logger.info("Configuration saved to {}", configPath);
        } finally {
            lock.readLock().unlock();
        }
    }
}
