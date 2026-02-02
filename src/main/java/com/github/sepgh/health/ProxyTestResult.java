package com.github.sepgh.health;

import com.github.sepgh.proxy.ProxyEndpoint;

public class ProxyTestResult {
    private final ProxyEndpoint endpoint;
    private final boolean success;
    private final long latencyMs;
    private final String errorMessage;

    private ProxyTestResult(ProxyEndpoint endpoint, boolean success, long latencyMs, String errorMessage) {
        this.endpoint = endpoint;
        this.success = success;
        this.latencyMs = latencyMs;
        this.errorMessage = errorMessage;
    }

    public static ProxyTestResult success(ProxyEndpoint endpoint, long latencyMs) {
        return new ProxyTestResult(endpoint, true, latencyMs, null);
    }

    public static ProxyTestResult failure(ProxyEndpoint endpoint, String errorMessage) {
        return new ProxyTestResult(endpoint, false, -1, errorMessage);
    }

    public ProxyEndpoint getEndpoint() {
        return endpoint;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return "ProxyTestResult{endpoint=" + endpoint + ", success=true, latency=" + latencyMs + "ms}";
        } else {
            return "ProxyTestResult{endpoint=" + endpoint + ", success=false, error='" + errorMessage + "'}";
        }
    }
}
