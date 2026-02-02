#!/bin/bash

echo "=== Building and Running Integration Test ==="

# Compile and run the test
mvn clean compile test-compile exec:java \
    -Dexec.mainClass="com.github.sepgh.integration.ProxyBalancerIntegrationTest" \
    -Dexec.classpathScope="test" \
    -Dorg.slf4j.simpleLogger.defaultLogLevel=info

exit $?
