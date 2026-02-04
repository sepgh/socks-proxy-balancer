# Configuration Examples

This document provides various configuration examples for different use cases.

## Basic Setup with Direct Proxy

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"
test_rounds: 1

proxies:
  - type: "direct"
    name: "primary-proxy"
    enabled: true
    config:
      host: "proxy.example.com"
      port: 1080
```

## Multiple Direct Proxies with Failover

```yaml
listen_host: "0.0.0.0"
listen_port: 1080

health_check_interval_seconds: 20
current_proxy_check_interval_seconds: 5
connection_timeout_ms: 3000
test_url: "http://www.google.com"

proxies:
  - type: "direct"
    name: "proxy-us-1"
    enabled: true
    config:
      host: "us1.proxy.example.com"
      port: 1080

  - type: "direct"
    name: "proxy-us-2"
    enabled: true
    config:
      host: "us2.proxy.example.com"
      port: 1080

  - type: "direct"
    name: "proxy-eu-1"
    enabled: true
    config:
      host: "eu1.proxy.example.com"
      port: 1080
```

## Xray VLESS Configuration

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"
test_rounds: 1

proxies:
  - type: "process"
    name: "xray-vless-server1"
    enabled: true
    config:
      command: "/usr/local/bin/xray"
      args:
        - "run"
        - "-c"
        - "/etc/xray/config-server1.json"
      host: "127.0.0.1"
      port: 10808
      startup_delay_ms: 3000
      working_dir: "/etc/xray"

  - type: "process"
    name: "xray-vless-server2"
    enabled: true
    config:
      command: "/usr/local/bin/xray"
      args:
        - "run"
        - "-c"
        - "/etc/xray/config-server2.json"
      host: "127.0.0.1"
      port: 10809
      startup_delay_ms: 3000
      working_dir: "/etc/xray"
```

## Sing-box Configuration

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"
test_rounds: 1

proxies:
  - type: "process"
    name: "singbox-vmess"
    enabled: true
    config:
      command: "/usr/local/bin/sing-box"
      args:
        - "run"
        - "-c"
        - "/etc/sing-box/vmess-config.json"
      host: "127.0.0.1"
      port: 10810
      startup_delay_ms: 2000

  - type: "singbox"
    name: "singbox-trojan"
    enabled: true
    config:
      command: "/usr/local/bin/sing-box"
      args:
        - "run"
        - "-c"
        - "/etc/sing-box/trojan-config.json"
      host: "127.0.0.1"
      port: 10811
      startup_delay_ms: 2000
```

## DNSTT Configuration with Port Placeholder

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"
test_rounds: 1

proxies:
  - type: "process"
    name: "dnstt-cloudflare"
    enabled: true
    config:
      command: "/usr/local/bin/dnstt-client"
      args:
        - "-udp"
        - "1.1.1.1:53"
        - "-pubkey"
        - "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        - "tunnel.example.com"
        - "127.0.0.1:{PORT}"  # {PORT} will be replaced with 7300
      host: "127.0.0.1"
      port: 7300
      startup_delay_ms: 2000

  - type: "process"
    name: "dnstt-google"
    enabled: true
    config:
      command: "/usr/local/bin/dnstt-client"
      args:
        - "-udp"
        - "8.8.8.8:53"
        - "-pubkey"
        - "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        - "tunnel.example.com"
        - "127.0.0.1:{PORT}"  # {PORT} will be replaced with 7301
      host: "127.0.0.1"
      port: 7301
      startup_delay_ms: 2000
```

## SlipStream Configuration

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"
test_rounds: 1

proxies:
  - type: "slipstream"
    name: "slipstream-manual-dns"
    enabled: true
    config:
      binary_path: "/usr/local/bin/slipstream"
      resolver_ip: "8.8.8.8"
      resolver_port: 53
      domain: "tunnel.example.com"
      cert_path: "/etc/slipstream/cert.pem"
      host: "127.0.0.1"
      port: 8080
      startup_delay_ms: 2000
```

## DNS-Tested SlipStream Configuration

Automatically tests multiple DNS resolvers and selects the fastest:

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"
test_rounds: 3  # Use 3 rounds for more accurate latency

proxies:
  - type: "dns-tested-slipstream"
    name: "slipstream-auto-dns"
    enabled: true
    config:
      binary_path: "/usr/local/bin/slipstream"
      domain: "tunnel.example.com"
      cert_path: "/etc/slipstream/cert.pem"
      host: "127.0.0.1"
      port: 8080
      startup_delay_ms: 2000
      
      # List of DNS endpoints to test
      dns_endpoints:
        - "8.8.8.8:53"          # Google DNS
        - "1.1.1.1:53"          # Cloudflare DNS
        - "9.9.9.9:53"          # Quad9 DNS
        - "208.67.222.222:53"   # OpenDNS
      
      dns_test_timeout_ms: 3000
      dns_test_domain: "www.google.com"
```

## Mixed Configuration (Multiple Proxy Types)

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"
test_rounds: 1

proxies:
  - type: "direct"
    name: "public-proxy"
    enabled: true
    config:
      host: "proxy.example.com"
      port: 1080

  - type: "process"
    name: "xray-vless"
    enabled: true
    config:
      command: "/usr/local/bin/xray"
      args:
        - "run"
        - "-c"
        - "/etc/xray/config.json"
      host: "127.0.0.1"
      port: 10808
      startup_delay_ms: 3000

  - type: "process"
    name: "singbox-vmess"
    enabled: true
    config:
      command: "/usr/local/bin/sing-box"
      args:
        - "run"
        - "-c"
        - "/etc/sing-box/config.json"
      host: "127.0.0.1"
      port: 10809
      startup_delay_ms: 2000

  - type: "process"
    name: "dnstt-client"
    enabled: true
    config:
      command: "/usr/local/bin/dnstt-client"
      args:
        - "-udp"
        - "8.8.8.8:53"
        - "-pubkey"
        - "your-public-key-here"
        - "tunnel.example.com"
        - "127.0.0.1:{PORT}"  # Port placeholder
      host: "127.0.0.1"
      port: 7300
      startup_delay_ms: 2000

  - type: "slipstream"
    name: "slipstream-proxy"
    enabled: true
    config:
      binary_path: "/usr/local/bin/slipstream"
      resolver_ip: "1.1.1.1"
      resolver_port: 53
      domain: "tunnel.example.com"
      cert_path: "/etc/slipstream/cert.pem"
      host: "127.0.0.1"
      port: 8080
      startup_delay_ms: 2000
```

## High-Frequency Monitoring

For critical applications requiring fast failover:

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 10
current_proxy_check_interval_seconds: 3
connection_timeout_ms: 2000
test_url: "http://www.google.com"

proxies:
  - type: "direct"
    name: "proxy-1"
    enabled: true
    config:
      host: "proxy1.example.com"
      port: 1080

  - type: "direct"
    name: "proxy-2"
    enabled: true
    config:
      host: "proxy2.example.com"
      port: 1080
```

## Custom Test URL

For testing against specific services:

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://example.com/health"

proxies:
  - type: "direct"
    name: "proxy-1"
    enabled: true
    config:
      host: "proxy.example.com"
      port: 1080
```

## Public Listening (Use with Caution)

For accepting connections from other machines:

```yaml
listen_host: "0.0.0.0"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"
test_rounds: 1

proxies:
  - type: "direct"
    name: "backend-proxy"
    enabled: true
    config:
      host: "internal-proxy.example.com"
      port: 1080
```

**Warning**: Binding to `0.0.0.0` makes the proxy accessible from any network interface. Ensure proper firewall rules are in place.

## Disabled Proxy Example

Temporarily disable a proxy without removing it:

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"
test_rounds: 1

proxies:
  - type: "direct"
    name: "proxy-1"
    enabled: true
    config:
      host: "proxy1.example.com"
      port: 1080

  - type: "direct"
    name: "proxy-2"
    enabled: false
    config:
      host: "proxy2.example.com"
      port: 1080
```

## Environment Variables in Process Proxies

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"
test_rounds: 1

proxies:
  - type: "process"
    name: "xray-with-env"
    enabled: true
    config:
      command: "/usr/local/bin/xray"
      args:
        - "run"
        - "-c"
        - "/etc/xray/config.json"
      host: "127.0.0.1"
      port: 10808
      startup_delay_ms: 3000
      working_dir: "/etc/xray"
      env:
        XRAY_LOCATION_ASSET: "/usr/local/share/xray"
        XRAY_LOCATION_CONFIG: "/etc/xray"
```
