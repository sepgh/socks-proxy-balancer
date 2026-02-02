package com.github.sepgh.proxy.impl;

import com.github.sepgh.config.ProxyConfig;
import com.github.sepgh.proxy.AbstractProxyClient;
import com.github.sepgh.proxy.ProxyEndpoint;

public class DirectProxyClient extends AbstractProxyClient {

    public DirectProxyClient(ProxyConfig config) {
        super(config);
        String host = getConfigString("host", "127.0.0.1");
        int port = getConfigInt("port", 1080);
        this.endpoint = new ProxyEndpoint(host, port);
    }

    @Override
    public void start() throws Exception {
        logger.info("Direct proxy client {} does not require starting, using endpoint: {}", getName(), endpoint);
        setRunning(true);
    }

    @Override
    public void stop() throws Exception {
        logger.info("Direct proxy client {} stopped", getName());
        setRunning(false);
    }
}
