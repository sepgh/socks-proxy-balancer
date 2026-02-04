# CI/CD Documentation

This document describes the Continuous Integration and Continuous Deployment setup for the Proxy Load Balancer project.

## Overview

The project uses GitHub Actions for:
1. **Continuous Integration**: Running tests on every push/PR
2. **Continuous Deployment**: Building and releasing multi-architecture binaries

## Workflows

### Test Workflow

**File**: `.github/workflows/test.yml`

**Purpose**: Ensures code quality by running integration tests

**Triggers**:
- Push to `main` or `develop` branches
- Pull requests targeting `main`

**Steps**:
1. Checkout code
2. Set up Java 21 (Temurin distribution)
3. Build project with Maven
4. Run integration test suite
5. Upload test results as artifacts

**Duration**: ~3-5 minutes

### Release Workflow

**File**: `.github/workflows/release.yml`

**Purpose**: Build native binaries for multiple architectures and create releases

**Triggers**:
- Push to `main` branch
- Push of version tags (e.g., `v1.0.0`)

**Build Matrix**:
| Platform | Architecture | Binary Name | Type |
|----------|-------------|-------------|------|
| Linux | AMD64 (x86_64) | `proxy-balancer-linux-amd64` | Native |
| Linux | x86 (i686) | `proxy-balancer-linux-x86` | Native |
| Windows | AMD64 (x86_64) | `proxy-balancer-windows-amd64.exe` | Native |
| Universal | All | `proxy-balancer.jar` | JAR |

**Note**: ARM64 Linux build is disabled by default (requires self-hosted runner or GitHub Team/Enterprise plan).

**Steps for Native Builds**:
1. Checkout code
2. Set up GraalVM 21 with native-image
3. Build project with Maven (tests skipped)
4. Compile native image for target architecture (with `-Pnative` profile)
5. Prepare and verify binary exists
6. Upload as artifact

**Steps for JAR Build**:
1. Checkout code
2. Set up JDK 21 (Temurin)
3. Build fat JAR with all dependencies
4. Upload JAR as artifact

**Final Steps**:
1. Download all artifacts
2. Create SHA256 checksums
3. Generate release notes
4. Create draft release with all binaries and JAR

**Duration**: ~10-15 minutes per architecture (parallel execution)

**Note**: Tests are intentionally skipped in the release workflow because:
- Tests run separately in the dedicated test workflow
- Prevents duplicate test execution
- Speeds up release builds
- The test workflow must pass before merging to `main`

## Architecture Details

### AMD64 (x86_64)
- **Runner**: `ubuntu-latest`
- **Target**: Standard 64-bit Intel/AMD processors
- **Use Cases**: Most Linux servers, desktops, cloud VMs

### ARM64 (aarch64) - DISABLED
- **Runner**: Self-hosted or GitHub Enterprise ARM64 runner required
- **Target**: 64-bit ARM processors
- **Use Cases**: 
  - Raspberry Pi 4/5
  - AWS Graviton instances
  - Oracle Cloud ARM instances
  - Other ARM-based servers
- **Status**: Disabled by default (uncomment in workflow to enable with self-hosted runner)

### x86 (i686)
- **Runner**: `ubuntu-latest` (cross-compilation)
- **Target**: 32-bit x86 processors
- **Use Cases**: Legacy systems, embedded devices
- **Note**: Uses static linking with musl libc

### Windows AMD64
- **Runner**: `windows-latest`
- **Target**: 64-bit Windows systems
- **Use Cases**: Windows desktops, servers
- **Note**: Produces `.exe` executable

### Universal JAR
- **Runner**: `ubuntu-latest`
- **Target**: Any platform with Java 21+
- **Use Cases**: 
  - Platforms without native builds
  - Development and testing
  - Systems where native image isn't available
- **Note**: Requires Java 21 or higher to run

## Release Process

### Automatic (Recommended)

1. Merge changes to `main` branch
2. GitHub Actions automatically:
   - Runs tests
   - Builds all architectures
   - Creates draft release
3. Review the draft release
4. Edit release notes if needed
5. Publish the release

### Manual (Using Tags)

1. Create and push a version tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. GitHub Actions triggers release workflow
3. Review and publish draft release

## Binary Verification

Each release includes SHA256 checksums for verification:

### Linux/macOS
```bash
# Download binary and checksum
wget https://github.com/USER/REPO/releases/download/TAG/proxy-balancer-linux-amd64
wget https://github.com/USER/REPO/releases/download/TAG/proxy-balancer-linux-amd64.sha256

# Verify
sha256sum -c proxy-balancer-linux-amd64.sha256
```

### Windows (PowerShell)
```powershell
# Download and verify
$hash = Get-FileHash proxy-balancer-windows-amd64.exe -Algorithm SHA256
$expectedHash = Get-Content proxy-balancer-windows-amd64.exe.sha256
if ($hash.Hash -eq $expectedHash.Split()[0]) {
    Write-Host "Verification successful"
} else {
    Write-Host "Verification failed"
}
```

## Local Testing

Before pushing, test the build locally:

### Native Image Build
```bash
# Install GraalVM
sdk install java 21-graalvm

# Build native image
mvn clean package -Pnative -DskipTests

# Test (Linux/macOS)
./target/proxy-balancer config.yaml

# Test (Windows)
target\proxy-balancer.exe config.yaml
```

### JAR Build
```bash
# Use any JDK 21+
sdk install java 21-tem

# Build JAR
mvn clean package -DskipTests

# Test
java -jar target/proxy-balancer.jar config.yaml
```

## Troubleshooting

### Tests Fail in CI

1. Check test logs in GitHub Actions
2. Run tests locally:
   ```bash
   ./run-integration-test.sh
   ```
3. Fix issues and push again

### ARM64 Build Fails

**Issue**: No ARM64 runner available

**Solutions**:
1. Use GitHub's ARM64 runners (if available for your plan)
2. Set up self-hosted ARM64 runner
3. Remove ARM64 from build matrix temporarily

### x86 Build Fails

**Issue**: Cross-compilation issues

**Solutions**:
1. Remove x86 from matrix if not needed
2. Use Docker-based cross-compilation
3. Set up dedicated x86 runner

### Native Image Out of Memory

**Issue**: Build fails with OOM error

**Solution**: Increase heap size in workflow:
```yaml
-Dgraalvm.native-image.buildArgs="-J-Xmx4g"
```

## Monitoring

### View Workflow Runs
```
https://github.com/YOUR_USERNAME/dnstt-client-balancer/actions
```

### Add Status Badges

Add to `README.md`:

```markdown
![Tests](https://github.com/YOUR_USERNAME/dnstt-client-balancer/workflows/Run%20Tests/badge.svg)
![Release](https://github.com/YOUR_USERNAME/dnstt-client-balancer/workflows/Build%20and%20Release/badge.svg)
```

## Customization

### Add More Architectures

Edit `.github/workflows/release.yml` matrix:

```yaml
# macOS ARM64 (Apple Silicon)
- os: macos
  arch: arm64
  runner: macos-14
  artifact_name: proxy-balancer-macos-arm64

# macOS AMD64 (Intel)
- os: macos
  arch: amd64
  runner: macos-13
  artifact_name: proxy-balancer-macos-amd64

# Enable ARM64 Linux (requires self-hosted runner)
- os: linux
  arch: arm64
  runner: [self-hosted, linux, arm64]
  artifact_name: proxy-balancer-linux-arm64
```

### Change Test Frequency

Modify triggers in `test.yml`:

```yaml
on:
  push:
    branches: [ main, develop, feature/* ]
  pull_request:
    branches: [ main, develop ]
  schedule:
    - cron: '0 0 * * *'  # Daily at midnight
```

### Auto-Publish Releases

Change in `release.yml`:

```yaml
with:
  draft: false  # Publish immediately
  prerelease: false
```

## Best Practices

1. **Always run tests locally** before pushing
2. **Review draft releases** before publishing
3. **Test binaries** from artifacts before release
4. **Use semantic versioning** for tags (v1.0.0, v1.1.0, etc.)
5. **Keep release notes updated** with changes
6. **Monitor workflow runs** for failures
7. **Update dependencies** regularly

## Security

- Workflows use `GITHUB_TOKEN` (automatically provided)
- No additional secrets required
- Binaries are built in isolated runners
- SHA256 checksums provided for verification
- All builds are reproducible

## Performance

- **Test workflow**: ~3-5 minutes
- **Release workflow**: ~15-25 minutes per architecture
- **Parallel builds**: All architectures build simultaneously
- **Caching**: Maven dependencies cached between runs

## Support

For issues with workflows:
1. Check workflow logs in GitHub Actions
2. Review `.github/workflows/README.md`
3. Test locally with same Java/GraalVM version
4. Open an issue with workflow logs
