package com.github.sepgh.dns;

public class DnsEndpoint {
    private final String ip;
    private final int port;

    public DnsEndpoint(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public DnsEndpoint(String ipPort) {
        String[] parts = ipPort.split(":");
        this.ip = parts[0];
        this.port = parts.length > 1 ? Integer.parseInt(parts[1]) : 53;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return ip + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DnsEndpoint that = (DnsEndpoint) o;
        return port == that.port && ip.equals(that.ip);
    }

    @Override
    public int hashCode() {
        return 31 * ip.hashCode() + port;
    }
}
