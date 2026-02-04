# Building ARM64 Binaries

## Why ARM64 Build Is Disabled

The ARM64 build is commented out in `.github/workflows/release.yml` because:

- **GitHub Free Tier**: ARM64 runners (`ubuntu-latest-arm64`) are only available on GitHub Team or Enterprise plans
- **Cost**: Would cause the workflow to hang indefinitely waiting for a runner that doesn't exist

## Options to Enable ARM64 Builds

### Option 1: Self-Hosted ARM64 Runner (Recommended for Free Tier)

Set up your own ARM64 runner on:
- Raspberry Pi 4/5 (8GB recommended)
- Oracle Cloud ARM instance (free tier available)
- AWS Graviton instance
- Any ARM64 Linux machine

**Steps:**

1. **Set up the runner** on your ARM64 machine:
   ```bash
   # On your ARM64 machine
   mkdir actions-runner && cd actions-runner
   
   # Download the latest runner (check GitHub for current version)
   curl -o actions-runner-linux-arm64-2.311.0.tar.gz -L \
     https://github.com/actions/runner/releases/download/v2.311.0/actions-runner-linux-arm64-2.311.0.tar.gz
   
   tar xzf ./actions-runner-linux-arm64-2.311.0.tar.gz
   
   # Configure (you'll need a token from GitHub)
   ./config.sh --url https://github.com/sepgh/dnstt-client-balancer --token YOUR_TOKEN
   
   # Install as service
   sudo ./svc.sh install
   sudo ./svc.sh start
   ```

2. **Label your runner**: Add labels `self-hosted`, `linux`, `arm64`

3. **Enable in workflow**: Uncomment the ARM64 section in `release.yml`:
   ```yaml
   # Linux ARM64
   - os: linux
     arch: arm64
     runner: [self-hosted, linux, arm64]
     artifact_name: proxy-balancer-linux-arm64
   ```

### Option 2: GitHub Team/Enterprise Plan

Upgrade to GitHub Team or Enterprise to get access to ARM64 runners:
- GitHub Team: $4/user/month
- Includes ARM64 runners
- Simply change `runner: ubuntu-latest-arm64` (no self-hosting needed)

### Option 3: Cross-Compilation (Experimental)

Build ARM64 on AMD64 using QEMU:

```yaml
- name: Set up QEMU
  uses: docker/setup-qemu-action@v3
  with:
    platforms: arm64

- name: Build ARM64 with Docker
  run: |
    docker run --rm --platform linux/arm64 \
      -v $PWD:/workspace \
      -w /workspace \
      ghcr.io/graalvm/graalvm-ce:java21 \
      bash -c "mvn clean package -Pnative -DskipTests"
```

**Note**: This is slower and may have compatibility issues.

### Option 4: Build Locally and Upload

Build ARM64 binary on your local ARM64 machine and upload manually:

```bash
# On ARM64 machine
git clone https://github.com/sepgh/dnstt-client-balancer.git
cd dnstt-client-balancer

# Install GraalVM
sdk install java 21-graalvm

# Build
mvn clean package -Pnative -DskipTests

# Binary is at: target/proxy-balancer
```

Then upload to GitHub release manually.

## Recommended Solution for Free Tier

**Use Oracle Cloud Free Tier ARM Instance:**

1. Create free Oracle Cloud account
2. Provision ARM Ampere A1 instance (4 cores, 24GB RAM - FREE forever)
3. Install GitHub Actions runner
4. Enable ARM64 build in workflow

This gives you:
- ✅ Free ARM64 builds
- ✅ Fast build times (4 cores)
- ✅ Always available
- ✅ No cost

## Current Build Matrix

With ARM64 disabled, the workflow builds:
- ✅ **Linux AMD64** (x86_64) - Most users
- ✅ **Linux x86** (i686) - Legacy systems
- ❌ **Linux ARM64** (aarch64) - Disabled (requires setup above)

## Testing ARM64 Binary

If you enable ARM64 builds, test on:
- Raspberry Pi 4/5
- Oracle Cloud ARM instance
- AWS Graviton
- Any ARM64 Linux system

```bash
# On ARM64 system
chmod +x proxy-balancer-linux-arm64
./proxy-balancer-linux-arm64 config.yaml
```

## Questions?

- For self-hosted runner setup: https://docs.github.com/en/actions/hosting-your-own-runners
- For Oracle Cloud free tier: https://www.oracle.com/cloud/free/
- For ARM64 support: Open an issue in the repository
