# Build Guide

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- (Optional) GraalVM 21+ with native-image for native compilation

## Building the JAR

```bash
mvn clean package
```

This will create `target/dnstt-client-balancer-1.0-SNAPSHOT.jar`

## Building Native Image

### Option 1: Using Maven Plugin

```bash
mvn clean package -Pnative
```

### Option 2: Manual Build

First build the JAR:
```bash
mvn clean package
```

Then build native image:
```bash
native-image -jar target/dnstt-client-balancer-1.0-SNAPSHOT.jar \
  --no-fallback \
  --enable-url-protocols=http,https \
  -H:+ReportExceptionStackTraces \
  -o proxy-balancer
```

## Running

### JAR Version

```bash
java -jar target/dnstt-client-balancer-1.0-SNAPSHOT.jar [config.yaml]
```

### Native Image Version

```bash
./proxy-balancer [config.yaml]
```

## Development Build

For development with auto-compilation:

```bash
mvn clean compile
```

## Troubleshooting

### Java Version Issues

Ensure you're using Java 21:
```bash
java -version
```

If using multiple Java versions, set JAVA_HOME:
```bash
export JAVA_HOME=/path/to/java21
```

### Native Image Build Fails

1. Ensure GraalVM is installed:
   ```bash
   native-image --version
   ```

2. Install native-image if missing:
   ```bash
   gu install native-image
   ```

3. Check reflection configuration in `src/main/resources/META-INF/native-image/`

### Dependency Issues

Clean Maven cache and rebuild:
```bash
mvn clean
rm -rf ~/.m2/repository/com/github/sepgh
mvn package
```

## IDE Setup

### IntelliJ IDEA

1. Open project directory
2. Maven will auto-import dependencies
3. Set Project SDK to Java 21
4. Run `ProxyBalancerApplication.main()`

### Eclipse

1. Import as Maven project
2. Update project configuration
3. Set Java compiler to 21
4. Run as Java Application

### VS Code

1. Install Java Extension Pack
2. Open project folder
3. Maven will auto-configure
4. Use Run/Debug from `ProxyBalancerApplication`

## Docker Build (Optional)

Create a `Dockerfile`:

```dockerfile
FROM ghcr.io/graalvm/graalvm-ce:latest as builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN gu install native-image
RUN mvn clean package -Pnative

FROM debian:bookworm-slim
COPY --from=builder /app/proxy-balancer /usr/local/bin/
COPY config.yaml /etc/proxy-balancer/
ENTRYPOINT ["/usr/local/bin/proxy-balancer"]
CMD ["/etc/proxy-balancer/config.yaml"]
```

Build:
```bash
docker build -t proxy-balancer .
```

Run:
```bash
docker run -p 1080:1080 -v $(pwd)/config.yaml:/etc/proxy-balancer/config.yaml proxy-balancer
```
