# SOCKS Proxy Load Balancer

A high-performance SOCKS proxy load balancer written in Java that automatically selects and forwards traffic to the best available proxy backend. Supports multiple proxy types including direct SOCKS proxies, Xray, Sing-box, DNSTT, Slipstream, and custom process-based proxies.

## Features

- **Automatic Proxy Selection**: Continuously monitors proxy health and automatically switches to the best performing proxy
- **Multiple Proxy Types**: Support for direct SOCKS proxies and process-based proxies (Xray, Sing-box, DNSTT, Slipstream, etc.)
- **Health Checking**: Periodic health checks with configurable intervals and automatic failover
- **Thread-Safe Configuration**: Dynamic configuration management with thread-safe access
- **GraalVM Native Image**: Build native executables for fast startup and low memory footprint
- **Extensible Architecture**: Clean interfaces for adding custom proxy client implementations

## Architecture

The application consists of several key components:

1. **Configuration Manager**: Thread-safe configuration management with support for dynamic updates
2. **Proxy Clients**: Abstract interface for different proxy types (direct, process-based)
3. **Health Checker**: Monitors proxy health and selects the best available proxy
4. **Proxy Tester**: Tests SOCKS proxies using actual connections
5. **SOCKS Proxy Server**: Forwards incoming SOCKS connections to the selected backend proxy

## Building

### Standard JAR

```bash
mvn clean package
```

### GraalVM Native Image

Requires GraalVM with native-image installed:

```bash
mvn clean package -Pnative
```

Or manually:

```bash
mvn clean package
native-image -jar target/dnstt-client-balancer-1.0-SNAPSHOT.jar
```

## Configuration

Create a `config.yaml` file with your proxy settings:

```yaml
listen_host: "127.0.0.1"
listen_port: 1080

health_check_interval_seconds: 30
current_proxy_check_interval_seconds: 10
connection_timeout_ms: 5000
test_url: "http://www.google.com"

proxies:
  - type: "direct"
    name: "public-proxy-1"
    enabled: true
    config:
      host: "proxy.example.com"
      port: 1080

  - type: "xray"
    name: "xray-vless-1"
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
```

### Configuration Options

#### Main Configuration

- `listen_host`: IP address to bind the SOCKS server (default: 127.0.0.1)
- `listen_port`: Port to bind the SOCKS server (default: 1080)
- `health_check_interval_seconds`: How often to check all proxies (default: 30)
- `current_proxy_check_interval_seconds`: How often to check the current proxy (default: 10)
- `connection_timeout_ms`: Connection timeout for proxy tests (default: 5000)
- `test_url`: URL to test proxy connectivity (default: http://www.google.com)

#### Proxy Types

##### Direct Proxy

For existing SOCKS proxies:

```yaml
- type: "direct"
  name: "my-proxy"
  enabled: true
  config:
    host: "proxy.example.com"
    port: 1080
```

##### Process-Based Proxy (Xray, Sing-box, DNSTT, Slipstream, etc.)

For proxies that need to be started as subprocesses:

```yaml
- type: "xray"  # or "singbox", "dnstt", "slipstream"
  name: "my-xray-proxy"
  enabled: true
  config:
    command: "/usr/local/bin/xray"
    args:
      - "run"
      - "-c"
      - "/path/to/config.json"
    host: "127.0.0.1"
    port: 10808
    startup_delay_ms: 3000
    working_dir: "/path/to/working/dir"  # optional
    env:  # optional environment variables
      LOG_LEVEL: "info"
```

## Running

### With JAR

```bash
java -jar target/dnstt-client-balancer-1.0-SNAPSHOT.jar [config-path]
```

If no config path is provided, it looks for `config.yaml` in the current directory.

### With Native Image

```bash
./proxy-balancer [config-path]
```

## Usage

Once running, configure your applications to use the SOCKS proxy at the configured `listen_host:listen_port` (default: `127.0.0.1:1080`).

The application will:
1. Start all enabled proxy clients
2. Test each proxy to find the best one
3. Forward all SOCKS traffic to the selected proxy
4. Continuously monitor proxy health
5. Automatically switch to a better proxy if the current one fails

## Extending

### Adding Custom Proxy Types

1. Create a new class extending `AbstractProxyClient`:

```java
public class MyCustomProxyClient extends AbstractProxyClient {
    public MyCustomProxyClient(ProxyConfig config) {
        super(config);
    }

    @Override
    public void start() throws Exception {
        // Initialize your proxy
        // Set this.endpoint to the SOCKS proxy endpoint
        setRunning(true);
    }

    @Override
    public void stop() throws Exception {
        // Clean up resources
        setRunning(false);
    }
}
```

2. Register it in `ProxyClientFactory`:

```java
case "mycustom" -> new MyCustomProxyClient(config);
```

### Dynamic Configuration Updates

The `ConfigurationManager` supports thread-safe dynamic updates:

```java
// Add a new proxy
configManager.addProxy(new ProxyConfig("direct", "new-proxy", config));

// Remove a proxy
configManager.removeProxy("proxy-name");

// Enable/disable a proxy
configManager.updateProxyEnabled("proxy-name", false);
```

## Logging

The application uses SLF4J with a simple logger. Configure logging by setting system properties:

```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -jar target/dnstt-client-balancer-1.0-SNAPSHOT.jar
```

Log levels: `trace`, `debug`, `info`, `warn`, `error`

## License

See LICENSE file for details.

## Contributing

Contributions are welcome! Please ensure your code follows the existing architecture and includes appropriate documentation.
