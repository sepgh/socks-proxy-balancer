# Quick Start Guide

Get up and running with the SOCKS Proxy Load Balancer in 5 minutes.

## Step 1: Build the Project

```bash
mvn clean package
```

## Step 2: Create Configuration

Create a `config.yaml` file (or use the provided example):

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"

proxies:
  - type: "direct"
    name: "my-proxy"
    enabled: true
    config:
      host: "your-proxy-server.com"
      port: 1080
```

## Step 3: Run the Application

```bash
java -jar target/dnstt-client-balancer-1.0-SNAPSHOT.jar config.yaml
```

You should see:
```
[main] INFO ProxyBalancerApplication - Initializing Proxy Balancer Application
[main] INFO ProxyBalancerApplication - Starting Proxy Balancer Application
[main] INFO HealthChecker - Starting HealthChecker
[main] INFO HealthChecker - Selecting initial proxy
[main] INFO HealthChecker - Selected proxy: my-proxy
[main] INFO SocksProxyServer - SOCKS proxy server started on 127.0.0.1:1080
[main] INFO ProxyBalancerApplication - Proxy Balancer Application started successfully
[main] INFO ProxyBalancerApplication - Listening on 127.0.0.1:1080
```

## Step 4: Test the Proxy

Configure your application to use SOCKS proxy at `127.0.0.1:1080`.

### Test with curl

```bash
curl -x socks5://127.0.0.1:1080 http://ifconfig.me
```

### Test with Firefox

1. Open Settings → Network Settings
2. Select "Manual proxy configuration"
3. SOCKS Host: `127.0.0.1`, Port: `1080`
4. Select "SOCKS v5"
5. Click OK

### Test with Chrome

```bash
google-chrome --proxy-server="socks5://127.0.0.1:1080"
```

## Common Scenarios

### Scenario 1: Multiple Backup Proxies

```yaml
proxies:
  - type: "direct"
    name: "primary"
    enabled: true
    config:
      host: "primary.proxy.com"
      port: 1080

  - type: "direct"
    name: "backup"
    enabled: true
    config:
      host: "backup.proxy.com"
      port: 1080
```

The application will automatically use the fastest working proxy and failover if one goes down.

### Scenario 2: Xray/V2Ray Client

```yaml
proxies:
  - type: "xray"
    name: "xray-client"
    enabled: true
    config:
      command: "/usr/local/bin/xray"
      args:
        - "run"
        - "-c"
        - "/path/to/xray-config.json"
      host: "127.0.0.1"
      port: 10808
      startup_delay_ms: 3000
```

The application will start Xray, wait for it to be ready, and forward traffic to it.

### Scenario 3: DNSTT Client

```yaml
proxies:
  - type: "dnstt"
    name: "dnstt-tunnel"
    enabled: true
    config:
      command: "/usr/local/bin/dnstt-client"
      args:
        - "-udp"
        - "8.8.8.8:53"
        - "-pubkey"
        - "your-public-key"
        - "tunnel.example.com"
        - "127.0.0.1:7300"
      host: "127.0.0.1"
      port: 7300
      startup_delay_ms: 2000
```

## Monitoring

Watch the logs to see proxy selection and health checks:

```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug \
  -jar target/dnstt-client-balancer-1.0-SNAPSHOT.jar config.yaml
```

You'll see:
- Initial proxy selection
- Periodic health checks
- Proxy switches when failures occur
- Connection forwarding details

## Stopping the Application

Press `Ctrl+C` to gracefully shutdown. The application will:
1. Stop accepting new connections
2. Close the SOCKS server
3. Stop health checker
4. Terminate all proxy clients
5. Exit cleanly

## Next Steps

- Read [README.md](README.md) for detailed documentation
- Check [EXAMPLES.md](EXAMPLES.md) for more configuration examples
- Review [ARCHITECTURE.md](ARCHITECTURE.md) to understand the internals
- See [BUILD.md](BUILD.md) for native image compilation

## Troubleshooting

### "No proxies configured"

Ensure your `config.yaml` has at least one proxy with `enabled: true`.

### "No working proxy found"

Check that:
1. Your proxy servers are accessible
2. The proxy credentials are correct (if required)
3. The `test_url` is reachable through the proxies
4. Firewall rules allow connections

### "Address already in use"

Another application is using port 1080. Either:
1. Stop the other application
2. Change `listen_port` in config.yaml

### Process proxy won't start

Check:
1. The command path is correct
2. The executable has execute permissions
3. All required arguments are provided
4. The working directory exists
5. Check logs for process output

## Getting Help

- Check logs with debug level enabled
- Review configuration examples
- Ensure all prerequisites are met
- Verify network connectivity to proxies
