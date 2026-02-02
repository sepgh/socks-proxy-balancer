package com.github.sepgh.health;

import com.github.sepgh.proxy.ProxyEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class ProxyTester {
    private static final Logger logger = LoggerFactory.getLogger(ProxyTester.class);
    private final int timeoutMs;
    private final String testUrl;

    public ProxyTester(int timeoutMs, String testUrl) {
        this.timeoutMs = timeoutMs;
        this.testUrl = testUrl;
    }

    public ProxyTestResult test(ProxyEndpoint endpoint) {
        long startTime = System.currentTimeMillis();
        try {
            URI uri = new URI(testUrl);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 80 : uri.getPort();

            logger.debug("Testing proxy {} for {}:{} (URL: {})", endpoint, host, port, testUrl);

            try (Socket proxySocket = new Socket()) {
                proxySocket.connect(new InetSocketAddress(endpoint.getHost(), endpoint.getPort()), timeoutMs);
                proxySocket.setSoTimeout(timeoutMs);
                logger.debug("Connected to proxy {}", endpoint);

                if (!performSocks5Handshake(proxySocket)) {
                    return ProxyTestResult.failure(endpoint, "SOCKS5 handshake failed");
                }
                logger.debug("SOCKS5 handshake successful with {}", endpoint);

                if (!connectThroughSocks5(proxySocket, host, port)) {
                    return ProxyTestResult.failure(endpoint, "Failed to connect to target through SOCKS5");
                }
                logger.debug("SOCKS5 connect to {}:{} successful through {}", host, port, endpoint);

                if (!testHttpRequest(proxySocket, host)) {
                    return ProxyTestResult.failure(endpoint, "HTTP request failed");
                }

                long latency = System.currentTimeMillis() - startTime;
                logger.debug("Proxy {} test successful, latency: {}ms", endpoint, latency);
                return ProxyTestResult.success(endpoint, latency);
            }
        } catch (SocketTimeoutException e) {
            logger.debug("Timeout testing proxy {}: {}", endpoint, e.getMessage());
            return ProxyTestResult.failure(endpoint, "Timeout: " + e.getMessage());
        } catch (Exception e) {
            logger.debug("Error testing proxy {}: {}", endpoint, e.getMessage());
            return ProxyTestResult.failure(endpoint, e.getMessage());
        }
    }

    private boolean performSocks5Handshake(Socket socket) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        out.write(new byte[]{0x05, 0x01, 0x00});
        out.flush();

        byte[] response = new byte[2];
        if (in.read(response) != 2) {
            return false;
        }

        return response[0] == 0x05 && response[1] == 0x00;
    }

    private boolean connectThroughSocks5(Socket socket, String host, int port) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
        byte[] request = new byte[7 + hostBytes.length];
        request[0] = 0x05;
        request[1] = 0x01;
        request[2] = 0x00;
        request[3] = 0x03;
        request[4] = (byte) hostBytes.length;
        System.arraycopy(hostBytes, 0, request, 5, hostBytes.length);
        request[5 + hostBytes.length] = (byte) (port >> 8);
        request[6 + hostBytes.length] = (byte) (port & 0xFF);

        out.write(request);
        out.flush();

        byte[] response = new byte[10];
        int bytesRead = in.read(response);
        if (bytesRead < 2) {
            return false;
        }

        return response[0] == 0x05 && response[1] == 0x00;
    }

    private boolean testHttpRequest(Socket socket, String host) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        String httpRequest = "GET / HTTP/1.1\r\n" +
                "Host: " + host + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(httpRequest.getBytes(StandardCharsets.UTF_8));
        out.flush();

        byte[] buffer = new byte[1024];
        int bytesRead = in.read(buffer);
        if (bytesRead <= 0) {
            return false;
        }

        String response = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
        return response.startsWith("HTTP/");
    }
}
