package com.github.sepgh.proxy;

import com.github.sepgh.config.ProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractProxyClient implements ProxyClient {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ProxyConfig config;
    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected ProxyEndpoint endpoint;

    protected AbstractProxyClient(ProxyConfig config) {
        this.config = config;
    }

    @Override
    public ProxyEndpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public ProxyConfig getConfig() {
        return config;
    }

    protected void setRunning(boolean value) {
        running.set(value);
    }

    protected Object getConfigValue(String key) {
        return config.getConfig().get(key);
    }

    protected String getConfigString(String key, String defaultValue) {
        Object value = getConfigValue(key);
        return value != null ? value.toString() : defaultValue;
    }

    protected int getConfigInt(String key, int defaultValue) {
        Object value = getConfigValue(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for key {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    protected boolean getConfigBoolean(String key, boolean defaultValue) {
        Object value = getConfigValue(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
}
