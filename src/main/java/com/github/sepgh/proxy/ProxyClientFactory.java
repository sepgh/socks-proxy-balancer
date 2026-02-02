package com.github.sepgh.proxy;

import com.github.sepgh.config.ProxyConfig;
import com.github.sepgh.proxy.impl.DirectProxyClient;
import com.github.sepgh.proxy.impl.ProcessProxyClient;

public class ProxyClientFactory {
    public static ProxyClient createClient(ProxyConfig config) {
        return switch (config.getType().toLowerCase()) {
            case "direct" -> new DirectProxyClient(config);
            case "xray", "singbox", "dnstt", "slipstream" -> new ProcessProxyClient(config);
            default -> throw new IllegalArgumentException("Unknown proxy type: " + config.getType());
        };
    }
}
