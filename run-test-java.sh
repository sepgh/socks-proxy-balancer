#!/bin/bash

echo "=== Building with Java Directly ==="

# Create target directories
mkdir -p target/classes target/test-classes

# Find all dependency JARs (assuming they're in ~/.m2)
find ~/.m2/repository -name "*.jar" 2>/dev/null | grep -E "(jackson|slf4j)" > /tmp/deps.txt 2>/dev/null || echo "" > /tmp/deps.txt

# Build classpath
CLASSPATH="target/classes:target/test-classes"
while read -r jar; do
    if [ -n "$jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done < /tmp/deps.txt

echo "Classpath: $CLASSPATH"

# Compile main classes
echo "Compiling main classes..."
find src/main/java -name "*.java" > /tmp/sources.txt
javac -cp "$CLASSPATH" -d target/classes @/tmp/sources.txt

if [ $? -ne 0 ]; then
    echo "Main compilation failed!"
    exit 1
fi

# Compile test classes
echo "Compiling test classes..."
find src/test/java -name "*.java" > /tmp/test-sources.txt
javac -cp "$CLASSPATH" -d target/test-classes @/tmp/test-sources.txt

if [ $? -ne 0 ]; then
    echo "Test compilation failed!"
    exit 1
fi

echo ""
echo "=== Running Integration Test ==="

# Run the test
java -cp "$CLASSPATH" \
    -Dorg.slf4j.simpleLogger.defaultLogLevel=debug \
    com.github.sepgh.integration.ProxyBalancerIntegrationTest

exit $?
