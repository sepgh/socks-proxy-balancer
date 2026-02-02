package com.github.sepgh.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TestSocksServer {
    private static final Logger logger = LoggerFactory.getLogger(TestSocksServer.class);
    
    private static class SocksConnectInfo {
        String host;
        int port;
        
        SocksConnectInfo(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
    
    private final String host;
    private final int port;
    private final long artificialLatencyMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public TestSocksServer(String host, int port, long artificialLatencyMs) {
        this.host = host;
        this.port = port;
        this.artificialLatencyMs = artificialLatencyMs;
    }

    public void start() throws IOException {
        if (running.get()) {
            throw new IllegalStateException("Server already running");
        }

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(host, port));
        running.set(true);

        logger.info("Test SOCKS server started on {}:{} with {}ms artificial latency", host, port, artificialLatencyMs);

        acceptThread = new Thread(this::acceptConnections, "test-socks-accept-" + port);
        acceptThread.start();
    }

    public void stop() {
        if (!running.get()) {
            return;
        }

        logger.info("Stopping test SOCKS server on port {}", port);
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

        logger.info("Test SOCKS server on port {} stopped", port);
    }

    private void acceptConnections() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                connectionCount.incrementAndGet();
                logger.debug("Test SOCKS server on port {} accepted connection #{}", port, connectionCount.get());
                executor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("Error accepting connection", e);
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            if (artificialLatencyMs > 0) {
                Thread.sleep(artificialLatencyMs);
            }

            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            if (!handleSocks5Handshake(in, out)) {
                logger.warn("SOCKS5 handshake failed on port {}", port);
                clientSocket.close();
                return;
            }

            SocksConnectInfo connectInfo = handleSocks5Connect(in, out);
            if (connectInfo == null) {
                logger.warn("SOCKS5 connect failed on port {}", port);
                clientSocket.close();
                return;
            }

            logger.debug("Test SOCKS server on port {} connecting to {}:{}", port, connectInfo.host, connectInfo.port);

            Socket targetSocket = new Socket();
            try {
                targetSocket.connect(new InetSocketAddress(connectInfo.host, connectInfo.port), 5000);
                logger.debug("Test SOCKS server on port {} connected to {}:{}", port, connectInfo.host, connectInfo.port);
            } catch (IOException e) {
                logger.warn("Failed to connect to target {}:{} from port {}: {}", connectInfo.host, connectInfo.port, port, e.getMessage());
                clientSocket.close();
                return;
            }

            Thread clientToTarget = new Thread(
                new SocketForwarder(clientSocket, targetSocket),
                "forward-c2t-" + port
            );
            Thread targetToClient = new Thread(
                new SocketForwarder(targetSocket, clientSocket),
                "forward-t2c-" + port
            );

            clientToTarget.start();
            targetToClient.start();

            clientToTarget.join();
            targetToClient.join();

            targetSocket.close();

        } catch (Exception e) {
            logger.debug("Error handling client on port {}: {}", port, e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.debug("Error closing client socket", e);
            }
        }
    }

    private boolean handleSocks5Handshake(InputStream in, OutputStream out) throws IOException {
        byte[] request = new byte[2];
        if (in.read(request) != 2) {
            return false;
        }

        if (request[0] != 0x05) {
            return false;
        }

        int nmethods = request[1] & 0xFF;
        byte[] methods = new byte[nmethods];
        if (in.read(methods) != nmethods) {
            return false;
        }

        out.write(new byte[]{0x05, 0x00});
        out.flush();
        return true;
    }

    private SocksConnectInfo handleSocks5Connect(InputStream in, OutputStream out) throws IOException {
        byte[] header = new byte[4];
        if (in.read(header) != 4) {
            return null;
        }

        if (header[0] != 0x05 || header[1] != 0x01) {
            return null;
        }

        int addrType = header[3] & 0xFF;
        String targetHost;
        int targetPort;

        if (addrType == 0x01) {
            byte[] addr = new byte[4];
            if (in.read(addr) != 4) {
                return null;
            }
            targetHost = String.format("%d.%d.%d.%d",
                addr[0] & 0xFF, addr[1] & 0xFF, addr[2] & 0xFF, addr[3] & 0xFF);
        } else if (addrType == 0x03) {
            int len = in.read();
            if (len < 0) {
                return null;
            }
            byte[] addr = new byte[len];
            if (in.read(addr) != len) {
                return null;
            }
            targetHost = new String(addr, StandardCharsets.UTF_8);
        } else {
            return null;
        }

        byte[] portBytes = new byte[2];
        if (in.read(portBytes) != 2) {
            return null;
        }
        targetPort = ((portBytes[0] & 0xFF) << 8) | (portBytes[1] & 0xFF);

        logger.debug("Test SOCKS server on port {} received connect request for {}:{}", port, targetHost, targetPort);

        byte[] response = new byte[10];
        response[0] = 0x05;
        response[1] = 0x00;
        response[2] = 0x00;
        response[3] = 0x01;
        response[4] = 127;
        response[5] = 0;
        response[6] = 0;
        response[7] = 1;
        response[8] = (byte) ((port >> 8) & 0xFF);
        response[9] = (byte) (port & 0xFF);

        out.write(response);
        out.flush();
        
        return new SocksConnectInfo(targetHost, targetPort);
    }

    public int getConnectionCount() {
        return connectionCount.get();
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running.get();
    }

    private static class SocketForwarder implements Runnable {
        private final Socket source;
        private final Socket destination;

        public SocketForwarder(Socket source, Socket destination) {
            this.source = source;
            this.destination = destination;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[8192];
            try {
                InputStream in = source.getInputStream();
                OutputStream out = destination.getOutputStream();
                
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            } catch (IOException e) {
            } finally {
                try {
                    destination.shutdownOutput();
                } catch (IOException e) {
                }
            }
        }
    }
}
