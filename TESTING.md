# Testing Guide

## Integration Tests

The project includes comprehensive integration tests that verify the complete proxy load balancing functionality.

## Test Architecture

The integration test creates a complete testing environment:

1. **Test HTTP Server**: A simple HTTP server that responds with "SUCCESS" to verify end-to-end connectivity
2. **Test SOCKS Servers**: Three SOCKS5 proxy servers with different artificial latencies:
   - **Fast Proxy** (10ms latency) - Port 12081
   - **Medium Proxy** (100ms latency) - Port 12082
   - **Slow Proxy** (300ms latency) - Port 12083
3. **Proxy Balancer**: The actual application being tested, listening on port 11080
4. **Test Client**: Makes requests through the balancer to verify functionality

## Test Scenarios

### Test 1: Initial Proxy Selection
Verifies that the balancer correctly selects the fastest proxy (lowest latency) during startup.

**Expected Result**: Fast proxy (10ms) should be selected.

### Test 2: Proxy Forwarding
Verifies that requests are correctly forwarded through the entire chain:
Client → Balancer → Selected Proxy → HTTP Server

**Expected Result**: HTTP request succeeds and returns "SUCCESS".

### Test 3: Latency-Based Selection
Verifies that the balancer consistently prefers the lowest latency proxy.

**Expected Result**: Fast proxy remains selected, receives most connections.

### Test 4: Proxy Failover
Tests automatic failover when the current proxy fails:
1. Stops the fast proxy
2. Verifies balancer switches to medium proxy (next best)
3. Restarts fast proxy
4. Verifies balancer switches back to fast proxy

**Expected Result**: Seamless failover and recovery without connection failures.

### Test 5: Multiple Concurrent Connections
Tests handling of 10 simultaneous connections through the balancer.

**Expected Result**: All connections succeed without errors.

## Running the Tests

### Option 1: Using the Simple Script (Recommended)

```bash
chmod +x run-test-simple.sh
./run-test-simple.sh
```

### Option 2: Using the Advanced Script

```bash
chmod +x run-integration-test.sh
./run-integration-test.sh
```

### Option 3: Using Maven Directly

```bash
mvn clean compile test-compile
mvn exec:java -Dexec.mainClass="com.github.sepgh.integration.ProxyBalancerIntegrationTest" \
    -Dexec.classpathScope="test"
```

### Option 4: With Debug Logging

```bash
mvn exec:java -Dexec.mainClass="com.github.sepgh.integration.ProxyBalancerIntegrationTest" \
    -Dexec.classpathScope="test" \
    -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

### Option 5: From IDE

Run `ProxyBalancerIntegrationTest.main()` as a Java application. Make sure to set the classpath to include `target/test-classes`.

## Test Output

Successful test run will show:

```
=== Starting Integration Tests ===
Setting up test environment...
Test HTTP server started on port 18080
Fast proxy started on port 12081 (10ms latency)
Medium proxy started on port 12082 (100ms latency)
Slow proxy started on port 12083 (300ms latency)
Proxy balancer started on port 11080

=== Test 1: Initial Proxy Selection ===
Selected proxy: fast-proxy
✓ Test 1 PASSED: Fast proxy was correctly selected

=== Test 2: Proxy Forwarding ===
✓ Test 2 PASSED: Request successfully forwarded through balancer -> fast proxy -> HTTP server

=== Test 3: Latency-Based Selection ===
Currently selected proxy: fast-proxy
Connection counts - Fast: 2, Medium: 0, Slow: 0
✓ Test 3 PASSED: Balancer correctly prefers lowest latency proxy

=== Test 4: Proxy Failover ===
Stopping fast proxy to trigger failover...
New selected proxy after failover: medium-proxy
Restarting fast proxy...
Selected proxy after fast proxy recovery: fast-proxy
✓ Test 4 PASSED: Failover and recovery work correctly

=== Test 5: Multiple Concurrent Connections ===
✓ Test 5 PASSED: Successfully handled 10 concurrent connections

=== All Tests Completed Successfully ===
=== ALL TESTS PASSED ===
```

## Test Components

### TestSocksServer
A fully functional SOCKS5 proxy server implementation for testing:
- Implements SOCKS5 handshake and connect protocols
- Supports artificial latency injection
- Tracks connection counts
- Forwards traffic to real destinations

### TestHttpServer
A simple HTTP server that:
- Responds to all requests with "SUCCESS"
- Tracks request counts
- Used as the final destination for testing

### ProxyBalancerIntegrationTest
The main test class that:
- Sets up the complete test environment
- Runs all test scenarios
- Performs assertions
- Cleans up resources

## Troubleshooting

### Port Already in Use
If you see "Address already in use" errors:
```bash
# Check what's using the ports
lsof -i :11080
lsof -i :12081
lsof -i :12082
lsof -i :12083
lsof -i :18080

# Kill the processes if needed
kill -9 <PID>
```

### Tests Timeout
If tests hang or timeout:
- Check that no firewall is blocking localhost connections
- Verify Java version is 21+
- Try running with debug logging to see where it hangs

### Assertion Failures
If specific tests fail:
- Check the logs to see which proxy was selected
- Verify timing - health checks may need more time
- Increase sleep durations in test if running on slow hardware

## Adding New Tests

To add new test scenarios:

1. Add a new test method in `ProxyBalancerIntegrationTest`:
```java
private void testNewScenario() throws Exception {
    logger.info("\n=== Test N: New Scenario ===");
    
    // Your test logic here
    
    logger.info("✓ Test N PASSED: Description");
}
```

2. Call it from `runAllTests()`:
```java
public void runAllTests() throws Exception {
    // ... existing tests ...
    testNewScenario();
}
```

## Performance Testing

For performance testing, modify the test to:
- Increase concurrent connection count
- Add timing measurements
- Test with higher latency differences
- Simulate network issues

Example:
```java
long startTime = System.currentTimeMillis();
// ... make requests ...
long duration = System.currentTimeMillis() - startTime;
logger.info("Processed {} requests in {}ms", count, duration);
```

## CI/CD Integration

To integrate with CI/CD pipelines:

```yaml
# GitHub Actions example
- name: Run Integration Tests
  run: |
    chmod +x run-integration-test.sh
    ./run-integration-test.sh
```

```groovy
// Jenkins example
stage('Integration Tests') {
    steps {
        sh './run-integration-test.sh'
    }
}
```

## Test Coverage

The integration tests cover:
- ✅ Initial proxy selection based on latency
- ✅ Request forwarding through the proxy chain
- ✅ Latency-based proxy preference
- ✅ Automatic failover on proxy failure
- ✅ Automatic recovery when failed proxy comes back
- ✅ Concurrent connection handling
- ✅ SOCKS5 protocol implementation
- ✅ Health checking system
- ✅ Configuration management

## Manual Testing

For manual testing of the actual application:

1. Start the application with test config
2. Use curl to test through the proxy:
```bash
curl -x socks5://127.0.0.1:1080 http://example.com
```

3. Monitor logs to see proxy selection
4. Stop/start backend proxies to test failover
