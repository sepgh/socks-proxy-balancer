package com.github.sepgh.proxy;

import com.github.sepgh.config.ProxyConfig;

public interface ProxyClient {
    void start() throws Exception;

    void stop() throws Exception;

    ProxyEndpoint getEndpoint();

    boolean isRunning();
    
    boolean isHealthy();

    String getName();

    ProxyConfig getConfig();
}
