package com.github.sepgh.proxy.impl;

import com.github.sepgh.config.ProxyConfig;
import com.github.sepgh.proxy.AbstractProxyClient;
import com.github.sepgh.proxy.ProxyEndpoint;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SlipStreamProxyClient extends AbstractProxyClient {
    private Process process;
    private Thread outputReaderThread;
    private Thread errorReaderThread;
    private volatile long lastConnectionWarningTime = 0;
    private volatile int consecutiveWarnings = 0;
    private static final long WARNING_WINDOW_MS = 10000; // 10 seconds
    private static final int MAX_WARNINGS_THRESHOLD = 2;

    public SlipStreamProxyClient(ProxyConfig config) {
        super(config);
        validateConfig();
    }

    private void validateConfig() {
        String binaryPath = getConfigString("binary_path", null);
        if (binaryPath == null || binaryPath.isEmpty()) {
            throw new IllegalArgumentException("binary_path is required for SlipStream proxy client");
        }

        File binaryFile = new File(binaryPath);
        if (!binaryFile.exists()) {
            throw new IllegalArgumentException("Binary file does not exist: " + binaryPath);
        }
        
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (!isWindows && !binaryFile.canExecute()) {
            throw new IllegalArgumentException("Binary file is not executable: " + binaryPath);
        }

        String certPath = getConfigString("cert_path", null);
        if (certPath == null || certPath.isEmpty()) {
            throw new IllegalArgumentException("cert_path is required for SlipStream proxy client");
        }

        File certFile = new File(certPath);
        if (!certFile.exists()) {
            throw new IllegalArgumentException("Certificate file does not exist: " + certPath);
        }
        if (!certFile.canRead()) {
            throw new IllegalArgumentException("Certificate file is not readable: " + certPath);
        }

        String domain = getConfigString("domain", null);
        if (domain == null || domain.isEmpty()) {
            throw new IllegalArgumentException("domain is required for SlipStream proxy client");
        }
    }

    @Override
    public void start() throws Exception {
        if (isRunning()) {
            logger.warn("SlipStream proxy client {} is already running", getName());
            return;
        }

        String binaryPath = getConfigString("binary_path", null);
        String resolverIp = getConfigString("resolver_ip", "127.0.0.1");
        int resolverPort = getConfigInt("resolver_port", 53);
        String domain = getConfigString("domain", null);
        String certPath = getConfigString("cert_path", null);
        
        String host = getConfigString("host", "127.0.0.1");
        int port = getConfigInt("port", 0);
        if (port == 0) {
            throw new IllegalArgumentException("Port is required for SlipStream proxy client");
        }
        this.endpoint = new ProxyEndpoint(host, port);

        List<String> commandList = new ArrayList<>();
        commandList.add(binaryPath);
        commandList.add("--resolver");
        commandList.add(resolverIp + ":" + resolverPort);
        commandList.add("--domain");
        commandList.add(domain);
        commandList.add("-l");
        commandList.add(String.valueOf(port));
        commandList.add("--cert");
        commandList.add(certPath);

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);

        logger.info("Starting SlipStream proxy client {}: {}", getName(), String.join(" ", commandList));
        process = processBuilder.start();

        outputReaderThread = new Thread(() -> readStream(process.getInputStream(), "STDOUT"), getName() + "-stdout");
        errorReaderThread = new Thread(() -> readStream(process.getErrorStream(), "STDERR"), getName() + "-stderr");
        outputReaderThread.setDaemon(true);
        errorReaderThread.setDaemon(true);
        outputReaderThread.start();
        errorReaderThread.start();

        int startupDelayMs = getConfigInt("startup_delay_ms", 2000);
        Thread.sleep(startupDelayMs);

        if (!process.isAlive()) {
            throw new RuntimeException("SlipStream process for proxy client " + getName() + " terminated unexpectedly");
        }

        setRunning(true);
        logger.info("SlipStream proxy client {} started successfully on {}", getName(), endpoint);
    }

    @Override
    public void stop() throws Exception {
        if (!isRunning()) {
            return;
        }

        logger.info("Stopping SlipStream proxy client {}", getName());
        setRunning(false);

        if (process != null && process.isAlive()) {
            process.destroy();
            boolean terminated = process.waitFor(5, TimeUnit.SECONDS);
            if (!terminated) {
                logger.warn("SlipStream process did not terminate gracefully, forcing termination");
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        }

        if (outputReaderThread != null) {
            outputReaderThread.interrupt();
        }
        if (errorReaderThread != null) {
            errorReaderThread.interrupt();
        }

        logger.info("SlipStream proxy client {} stopped", getName());
    }

    private void readStream(java.io.InputStream inputStream, String streamName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            boolean logOutput = getConfigBoolean("log_subprocess_output", false);
            
            while ((line = reader.readLine()) != null) {
                if (logOutput) {
                    logger.info("[{}][{}] {}", getName(), streamName, line);
                } else {
                    logger.debug("[{}][{}] {}", getName(), streamName, line);
                }
                
                // Detect connection issues from SlipStream output
                if (line.contains("WARN") && 
                    (line.contains("Connection closed") || 
                     line.contains("reconnecting") ||
                     line.contains("Path for resolver") ||
                     line.contains("became unavailable"))) {
                    
                    long now = System.currentTimeMillis();
                    if (now - lastConnectionWarningTime < WARNING_WINDOW_MS) {
                        consecutiveWarnings++;
                        logger.warn("SlipStream connection warning detected ({} consecutive warnings in {}ms)", 
                                  consecutiveWarnings, now - lastConnectionWarningTime);
                        
                        if (consecutiveWarnings >= MAX_WARNINGS_THRESHOLD) {
                            logger.error("SlipStream proxy {} has become UNHEALTHY after {} consecutive connection warnings", 
                                       getName(), consecutiveWarnings);
                        }
                    } else {
                        consecutiveWarnings = 1;
                    }
                    lastConnectionWarningTime = now;
                }
            }
        } catch (IOException e) {
            if (isRunning()) {
                logger.error("Error reading {} for {}", streamName, getName(), e);
            }
        }
    }
    
    @Override
    public boolean isHealthy() {
        if (!isRunning()) {
            return false;
        }
        
        // Check if we're in a reconnection loop
        long timeSinceLastWarning = System.currentTimeMillis() - lastConnectionWarningTime;
        if (consecutiveWarnings >= MAX_WARNINGS_THRESHOLD && timeSinceLastWarning < WARNING_WINDOW_MS) {
            logger.warn("SlipStream proxy {} is unhealthy: {} consecutive connection warnings", 
                      getName(), consecutiveWarnings);
            return false;
        }
        
        return true;
    }
    
    public void resetHealthStatus() {
        logger.info("Resetting health status for SlipStream proxy {}", getName());
        consecutiveWarnings = 0;
        lastConnectionWarningTime = 0;
    }
}
