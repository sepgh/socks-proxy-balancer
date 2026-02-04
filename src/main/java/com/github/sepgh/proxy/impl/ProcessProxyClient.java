package com.github.sepgh.proxy.impl;

import com.github.sepgh.config.ProxyConfig;
import com.github.sepgh.proxy.AbstractProxyClient;
import com.github.sepgh.proxy.ProxyEndpoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProcessProxyClient extends AbstractProxyClient {
    private Process process;
    private Thread outputReaderThread;
    private Thread errorReaderThread;

    public ProcessProxyClient(ProxyConfig config) {
        super(config);
    }

    @Override
    public void start() throws Exception {
        if (isRunning()) {
            logger.warn("Proxy client {} is already running", getName());
            return;
        }

        String command = getConfigString("command", null);
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Command is required for process proxy client");
        }

        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) config.getConfig().get("args");
        if (args == null) {
            args = new ArrayList<>();
        }

        String workingDir = getConfigString("working_dir", null);
        @SuppressWarnings("unchecked")
        Map<String, String> env = (Map<String, String>) config.getConfig().get("env");

        String host = getConfigString("host", "127.0.0.1");
        int port = getConfigInt("port", 0);
        if (port == 0) {
            throw new IllegalArgumentException("Port is required for process proxy client");
        }
        this.endpoint = new ProxyEndpoint(host, port);

        String portPlaceholder = "{PORT}";
        String portValue = String.valueOf(port);
        
        List<String> commandList = new ArrayList<>();
        commandList.add(command.replace(portPlaceholder, portValue));
        for (String arg : args) {
            commandList.add(arg.replace(portPlaceholder, portValue));
        }

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);
        if (workingDir != null) {
            processBuilder.directory(new java.io.File(workingDir));
        }
        if (env != null) {
            processBuilder.environment().putAll(env);
        }

        logger.info("Starting process proxy client {}: {}", getName(), String.join(" ", commandList));
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
            throw new RuntimeException("Process for proxy client " + getName() + " terminated unexpectedly");
        }

        setRunning(true);
        logger.info("Process proxy client {} started successfully on {}", getName(), endpoint);
    }

    @Override
    public void stop() throws Exception {
        if (!isRunning()) {
            return;
        }

        logger.info("Stopping process proxy client {}", getName());
        setRunning(false);

        if (process != null && process.isAlive()) {
            process.destroy();
            boolean terminated = process.waitFor(5, TimeUnit.SECONDS);
            if (!terminated) {
                logger.warn("Process did not terminate gracefully, forcing termination");
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

        logger.info("Process proxy client {} stopped", getName());
    }

    private void readStream(java.io.InputStream inputStream, String streamName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[{}][{}] {}", getName(), streamName, line);
            }
        } catch (IOException e) {
            if (isRunning()) {
                logger.error("Error reading {} for {}", streamName, getName(), e);
            }
        }
    }
}
