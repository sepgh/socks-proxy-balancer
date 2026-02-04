package com.github.sepgh.proxy;

import com.github.sepgh.config.ProxyConfig;
import com.github.sepgh.proxy.impl.DirectProxyClient;
import com.github.sepgh.proxy.impl.DnsTestedSlipStreamProxyClient;
import com.github.sepgh.proxy.impl.ProcessProxyClient;
import com.github.sepgh.proxy.impl.SlipStreamProxyClient;

public class ProxyClientFactory {
    public static ProxyClient createClient(ProxyConfig config) {
        return switch (config.getType().toLowerCase()) {
            case "direct" -> new DirectProxyClient(config);
            case "process" -> new ProcessProxyClient(config);
            case "slipstream" -> new SlipStreamProxyClient(config);
            case "dns-tested-slipstream" -> new DnsTestedSlipStreamProxyClient(config);
            default -> throw new IllegalArgumentException("Unknown proxy type: " + config.getType());
        };
    }
}
