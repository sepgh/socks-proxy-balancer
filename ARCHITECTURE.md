# Architecture Documentation

## Overview

The SOCKS Proxy Load Balancer is designed with a modular architecture that separates concerns and allows for easy extension. The system continuously monitors multiple proxy backends and automatically routes traffic through the best performing one.

## Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                   ProxyBalancerApplication                   │
│                     (Main Orchestrator)                      │
└───────────┬─────────────────────────────────┬───────────────┘
            │                                 │
            │                                 │
    ┌───────▼────────┐              ┌────────▼────────┐
    │ SocksProxyServer│              │  HealthChecker  │
    │   (Forwarder)   │              │  (Monitoring)   │
    └───────┬─────────┘              └────────┬────────┘
            │                                 │
            │                          ┌──────▼──────┐
            │                          │ ProxyTester │
            │                          └─────────────┘
            │                                 │
            │                                 │
    ┌───────▼─────────────────────────────────▼───────┐
    │          ConfigurationManager                    │
    │         (Thread-Safe Config)                     │
    └──────────────────┬───────────────────────────────┘
                       │
         ┌─────────────┴─────────────┐
         │                           │
    ┌────▼─────┐              ┌──────▼──────┐
    │  Proxy   │              │   Proxy     │
    │ Clients  │◄─────────────┤  Factory    │
    └──────────┘              └─────────────┘
         │
    ┌────┴────────────────────┐
    │                         │
┌───▼──────────┐    ┌────────▼─────────┐
│DirectProxy   │    │ ProcessProxy     │
│   Client     │    │    Client        │
└──────────────┘    └──────────────────┘
```

## Core Components

### 1. ProxyBalancerApplication

**Responsibility**: Main entry point and lifecycle manager

**Key Features**:
- Initializes all components
- Manages application lifecycle (start/stop)
- Handles shutdown hooks for graceful termination

**Thread Model**: Main thread + delegates to other components

### 2. ConfigurationManager

**Responsibility**: Thread-safe configuration management

**Key Features**:
- Loads configuration from YAML files
- Provides thread-safe read/write access using ReadWriteLock
- Supports dynamic configuration updates
- Allows adding/removing proxies at runtime

**Thread Safety**: Uses `ReentrantReadWriteLock` for concurrent access

**API**:
```java
ApplicationConfig getConfig()
List<ProxyConfig> getProxies()
void addProxy(ProxyConfig proxyConfig)
void removeProxy(String proxyName)
void updateProxyEnabled(String proxyName, boolean enabled)
```

### 3. Proxy Client System

#### ProxyClient Interface

Defines the contract for all proxy implementations:
```java
void start() throws Exception
void stop() throws Exception
ProxyEndpoint getEndpoint()
boolean isRunning()
String getName()
ProxyConfig getConfig()
```

#### AbstractProxyClient

Base implementation providing:
- Common state management (running flag)
- Configuration value extraction helpers
- Logging infrastructure

#### DirectProxyClient

For existing SOCKS proxies that don't need to be started:
- Simply wraps an existing proxy endpoint
- No process management needed

#### ProcessProxyClient

For proxies that run as subprocesses:
- Manages process lifecycle
- Captures stdout/stderr for logging
- Handles graceful and forced termination
- Configurable startup delay
- Environment variable support

### 4. Health Checking System

#### ProxyTester

**Responsibility**: Tests individual proxy endpoints

**Testing Process**:
1. Connect to proxy endpoint
2. Perform SOCKS5 handshake
3. Connect to test URL through proxy
4. Send HTTP request
5. Verify response
6. Measure latency

**Returns**: `ProxyTestResult` with success/failure and latency

#### HealthChecker

**Responsibility**: Continuous monitoring and proxy selection

**Key Features**:
- Two separate monitoring threads:
  - All proxies health check (configurable interval)
  - Current proxy health check (more frequent)
- Automatic proxy client lifecycle management
- Best proxy selection based on latency
- Automatic failover on proxy failure

**Thread Model**:
- `ScheduledExecutorService` with 2 threads for scheduling
- `ExecutorService` with 5 threads for parallel proxy testing

**Selection Algorithm**:
1. Test all enabled proxies in parallel
2. Filter successful results
3. Select proxy with lowest latency
4. Switch if current proxy fails or better option available

### 5. SocksProxyServer

**Responsibility**: Accept SOCKS connections and forward to backend

**Key Features**:
- Binds to configured host:port
- Accepts incoming SOCKS connections
- Forwards all traffic to currently selected proxy
- Bidirectional data forwarding using separate threads
- Graceful connection handling

**Thread Model**:
- Main accept thread
- Cached thread pool for connection handlers
- Two threads per connection (bidirectional forwarding)

**Forwarding Process**:
1. Accept client connection
2. Get current selected proxy from HealthChecker
3. Connect to backend proxy
4. Create two forwarding threads:
   - Client → Backend
   - Backend → Client
5. Forward data until connection closes

## Data Flow

### Startup Flow

```
1. Load Configuration
   ↓
2. Initialize ProxyTester
   ↓
3. Initialize HealthChecker
   ↓
4. Start HealthChecker
   ├─→ Test all proxies
   ├─→ Select best proxy
   └─→ Start monitoring threads
   ↓
5. Start SocksProxyServer
   └─→ Begin accepting connections
```

### Connection Flow

```
1. Client connects to SocksProxyServer
   ↓
2. Server gets selected proxy from HealthChecker
   ↓
3. Server connects to backend proxy
   ↓
4. Bidirectional forwarding begins
   ├─→ Client → Backend thread
   └─→ Backend → Client thread
   ↓
5. Forward until connection closes
```

### Health Check Flow

```
┌─→ Periodic Timer
│   ↓
│   Test All Proxies (parallel)
│   ↓
│   Collect Results
│   ↓
│   Select Best Proxy
│   ↓
│   Switch if needed
└───┘

┌─→ Frequent Timer
│   ↓
│   Test Current Proxy
│   ↓
│   If Failed → Trigger Full Check
└───┘
```

## Thread Safety

### ConfigurationManager
- Uses `ReentrantReadWriteLock`
- Multiple readers, single writer
- Read operations: `getConfig()`, `getProxies()`
- Write operations: `addProxy()`, `removeProxy()`, `updateProxyEnabled()`

### HealthChecker
- Uses `AtomicReference` for selected proxy
- `ConcurrentHashMap` for active clients
- Thread-safe proxy selection and switching

### ProxyClient Implementations
- Uses `AtomicBoolean` for running state
- Thread-safe state transitions

## Extension Points

### Adding New Proxy Types

1. **Create Client Implementation**:
   ```java
   public class MyProxyClient extends AbstractProxyClient {
       // Implement start() and stop()
   }
   ```

2. **Register in Factory**:
   ```java
   // In ProxyClientFactory.createClient()
   case "mytype" -> new MyProxyClient(config);
   ```

3. **Configure in YAML**:
   ```yaml
   - type: "mytype"
     name: "my-proxy"
     config:
       # Your configuration
   ```

### Custom Health Checks

Extend `ProxyTester` to implement custom testing logic:
```java
public class CustomProxyTester extends ProxyTester {
    @Override
    public ProxyTestResult test(ProxyEndpoint endpoint) {
        // Custom testing logic
    }
}
```

### Dynamic Configuration Updates

Use `ConfigurationManager` API to modify configuration at runtime:
```java
// Example: Add proxy discovered dynamically
ProxyConfig newProxy = new ProxyConfig("direct", "discovered-proxy", config);
configManager.addProxy(newProxy);
```

## Performance Considerations

### Connection Handling
- Uses cached thread pool for connection handlers
- Scales automatically with connection load
- Each connection uses 2 threads for bidirectional forwarding

### Health Checking
- Parallel proxy testing (5 concurrent tests)
- Configurable check intervals
- Separate intervals for all proxies vs current proxy

### Memory
- Minimal memory footprint
- GraalVM native image support for even lower memory usage
- No connection pooling (stateless forwarding)

### Latency
- Direct forwarding (no buffering beyond socket buffers)
- Automatic selection of lowest latency proxy
- Fast failover on proxy failure

## Error Handling

### Proxy Client Failures
- Logged but don't crash application
- Failed clients excluded from selection
- Automatic retry on next health check

### Connection Failures
- Graceful connection cleanup
- Client connections closed on backend failure
- No impact on other connections

### Configuration Errors
- Invalid configuration logged
- Application continues with valid proxies
- Missing config file uses defaults

## Logging

Uses SLF4J with configurable levels:
- **ERROR**: Critical failures
- **WARN**: Proxy failures, configuration issues
- **INFO**: Lifecycle events, proxy switches
- **DEBUG**: Connection details, test results
- **TRACE**: Detailed debugging

## GraalVM Native Image

### Reflection Configuration
- Configuration classes registered for reflection
- Jackson serialization support

### Resource Configuration
- YAML files included in native image
- Configuration files accessible at runtime

### Build Optimization
- No fallback mode
- HTTP/HTTPS protocols enabled
- Exception stack traces enabled for debugging
