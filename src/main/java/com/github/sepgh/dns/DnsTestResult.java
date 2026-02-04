package com.github.sepgh.dns;

public class DnsTestResult {
    private final DnsEndpoint endpoint;
    private final boolean success;
    private final long latencyMs;
    private final String errorMessage;

    private DnsTestResult(DnsEndpoint endpoint, boolean success, long latencyMs, String errorMessage) {
        this.endpoint = endpoint;
        this.success = success;
        this.latencyMs = latencyMs;
        this.errorMessage = errorMessage;
    }

    public static DnsTestResult success(DnsEndpoint endpoint, long latencyMs) {
        return new DnsTestResult(endpoint, true, latencyMs, null);
    }

    public static DnsTestResult failure(DnsEndpoint endpoint, String errorMessage) {
        return new DnsTestResult(endpoint, false, -1, errorMessage);
    }

    public DnsEndpoint getEndpoint() {
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
            return "DnsTestResult{endpoint=" + endpoint + ", success=true, latency=" + latencyMs + "ms}";
        } else {
            return "DnsTestResult{endpoint=" + endpoint + ", success=false, error='" + errorMessage + "'}";
        }
    }
}
