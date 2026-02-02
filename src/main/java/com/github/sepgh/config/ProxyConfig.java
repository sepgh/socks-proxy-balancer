package com.github.sepgh.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ProxyConfig {
    @JsonProperty("type")
    private String type;

    @JsonProperty("name")
    private String name;

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("config")
    private Map<String, Object> config;

    public ProxyConfig() {
    }

    public ProxyConfig(String type, String name, Map<String, Object> config) {
        this.type = type;
        this.name = name;
        this.config = config;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    @Override
    public String toString() {
        return "ProxyConfig{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
