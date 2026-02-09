package com.github.sepgh.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class ApplicationConfig {
    @JsonProperty("listen_host")
    private String listenHost = "127.0.0.1";

    @JsonProperty("listen_port")
    private int listenPort = 1080;

    @JsonProperty("health_check_interval_seconds")
    private int healthCheckIntervalSeconds = 30;

    @JsonProperty("current_proxy_check_interval_seconds")
    private int currentProxyCheckIntervalSeconds = 10;

    @JsonProperty("connection_timeout_ms")
    private int connectionTimeoutMs = 5000;

    @JsonProperty("test_url")
    private String testUrl = "http://www.google.com";

    @JsonProperty("log_subprocess_output")
    private boolean logSubprocessOutput = false;

    @JsonProperty("network_interface")
    private String networkInterface = null;

    @JsonProperty("switch_threshold_ms")
    private long switchThresholdMs = 250;

    @JsonProperty("proxies")
    private List<ProxyConfig> proxies = new ArrayList<>();

    public String getListenHost() {
        return listenHost;
    }

    public void setListenHost(String listenHost) {
        this.listenHost = listenHost;
    }

    public int getListenPort() {
        return listenPort;
    }

    public void setListenPort(int listenPort) {
        this.listenPort = listenPort;
    }

    public int getHealthCheckIntervalSeconds() {
        return healthCheckIntervalSeconds;
    }

    public void setHealthCheckIntervalSeconds(int healthCheckIntervalSeconds) {
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
    }

    public int getCurrentProxyCheckIntervalSeconds() {
        return currentProxyCheckIntervalSeconds;
    }

    public void setCurrentProxyCheckIntervalSeconds(int currentProxyCheckIntervalSeconds) {
        this.currentProxyCheckIntervalSeconds = currentProxyCheckIntervalSeconds;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public String getTestUrl() {
        return testUrl;
    }

    public void setTestUrl(String testUrl) {
        this.testUrl = testUrl;
    }

    public List<ProxyConfig> getProxies() {
        return proxies;
    }

    public void setProxies(List<ProxyConfig> proxies) {
        this.proxies = proxies;
    }

    public boolean isLogSubprocessOutput() {
        return logSubprocessOutput;
    }

    public void setLogSubprocessOutput(boolean logSubprocessOutput) {
        this.logSubprocessOutput = logSubprocessOutput;
    }

    public String getNetworkInterface() {
        return networkInterface;
    }

    public void setNetworkInterface(String networkInterface) {
        this.networkInterface = networkInterface;
    }

    public long getSwitchThresholdMs() {
        return switchThresholdMs;
    }

    public void setSwitchThresholdMs(long switchThresholdMs) {
        this.switchThresholdMs = switchThresholdMs;
    }
}
