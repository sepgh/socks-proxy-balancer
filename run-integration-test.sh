#!/bin/bash

echo "=== Building Project ==="
mvn clean compile test-compile

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo ""
echo "=== Running Integration Test ==="

# Get the classpath
CLASSPATH=$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)

# Add compiled classes to classpath
CLASSPATH="target/classes:target/test-classes:$CLASSPATH"

# Run the test with java directly
java -cp "$CLASSPATH" \
    -Dorg.slf4j.simpleLogger.defaultLogLevel=debug \
    com.github.sepgh.integration.ProxyBalancerIntegrationTest

exit $?
