package com.github.sepgh.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TestHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(TestHttpServer.class);
    
    private final String host;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public TestHttpServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        if (running.get()) {
            throw new IllegalStateException("Server already running");
        }

        serverSocket = new ServerSocket(port);
        running.set(true);

        logger.info("Test HTTP server started on {}:{}", host, port);

        acceptThread = new Thread(this::acceptConnections, "test-http-accept");
        acceptThread.start();
    }

    public void stop() {
        if (!running.get()) {
            return;
        }

        logger.info("Stopping test HTTP server");
        running.set(false);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }

        if (acceptThread != null) {
            acceptThread.interrupt();
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void acceptConnections() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleRequest(clientSocket));
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("Error accepting connection", e);
                }
            }
        }
    }

    private void handleRequest(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)
            );
            
            String requestLine = reader.readLine();
            if (requestLine == null) {
                clientSocket.close();
                return;
            }

            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Skip headers
            }

            int count = requestCount.incrementAndGet();
            logger.debug("Test HTTP server received request #{}: {}", count, requestLine);

            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: 7\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    "SUCCESS";

            OutputStream out = clientSocket.getOutputStream();
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();

            clientSocket.close();
        } catch (IOException e) {
            logger.debug("Error handling request: {}", e.getMessage());
        }
    }

    public int getRequestCount() {
        return requestCount.get();
    }

    public int getPort() {
        return port;
    }

    public String getUrl() {
        return "http://" + host + ":" + port;
    }
}
