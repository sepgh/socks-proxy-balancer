package com.github.sepgh.proxy;

public class ProxyEndpoint {
    private final String host;
    private final int port;

    public ProxyEndpoint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProxyEndpoint that = (ProxyEndpoint) o;
        return port == that.port && host.equals(that.host);
    }

    @Override
    public int hashCode() {
        return 31 * host.hashCode() + port;
    }
}
