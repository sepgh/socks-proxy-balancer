package com.github.sepgh.test;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class SimpleProxyTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Simple SOCKS Proxy Test ===");
        
        // Start a simple HTTP server
        TestHttpServer httpServer = new TestHttpServer("127.0.0.1", 18080);
        httpServer.start();
        
        // Start a simple SOCKS proxy
        TestSocksServer socksServer = new TestSocksServer("127.0.0.1", 12081, 10);
        socksServer.start();
        
        Thread.sleep(1000);
        
        try {
            // Test direct connection to HTTP server
            System.out.println("Testing direct HTTP connection...");
            String directResponse = makeDirectRequest("127.0.0.1", 18080);
            System.out.println("Direct response: " + directResponse);
            
            // Test connection through SOCKS proxy
            System.out.println("Testing SOCKS proxy connection...");
            String proxyResponse = makeSocksRequest("127.0.0.1", 12081, "127.0.0.1", 18080);
            System.out.println("Proxy response: " + proxyResponse);
            
            if ("SUCCESS".equals(directResponse) && "SUCCESS".equals(proxyResponse)) {
                System.out.println("✓ All tests passed!");
            } else {
                System.out.println("✗ Tests failed!");
            }
            
        } finally {
            socksServer.stop();
            httpServer.stop();
        }
    }
    
    private static String makeDirectRequest(String host, int port) throws Exception {
        try (Socket socket = new Socket(host, port)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            
            String request = "GET / HTTP/1.1\r\n" +
                           "Host: " + host + ":" + port + "\r\n" +
                           "Connection: close\r\n" +
                           "\r\n";
            
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            
            // Skip headers
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
            }
            
            // Read body
            StringBuilder body = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            
            return body.toString();
        }
    }
    
    private static String makeSocksRequest(String proxyHost, int proxyPort, String targetHost, int targetPort) throws Exception {
        try (Socket proxySocket = new Socket()) {
            proxySocket.connect(new InetSocketAddress(proxyHost, proxyPort), 5000);
            proxySocket.setSoTimeout(5000);
            
            OutputStream out = proxySocket.getOutputStream();
            InputStream in = proxySocket.getInputStream();
            
            // SOCKS5 handshake
            out.write(new byte[]{0x05, 0x01, 0x00});
            out.flush();
            
            byte[] handshakeResponse = new byte[2];
            if (in.read(handshakeResponse) != 2) {
                return "HANDSHAKE_FAILED";
            }
            
            if (handshakeResponse[0] != 0x05 || handshakeResponse[1] != 0x00) {
                return "HANDSHAKE_REJECTED";
            }
            
            // SOCKS5 connect
            byte[] hostBytes = targetHost.getBytes(StandardCharsets.UTF_8);
            byte[] connectRequest = new byte[7 + hostBytes.length];
            connectRequest[0] = 0x05;
            connectRequest[1] = 0x01;
            connectRequest[2] = 0x00;
            connectRequest[3] = 0x03;
            connectRequest[4] = (byte) hostBytes.length;
            System.arraycopy(hostBytes, 0, connectRequest, 5, hostBytes.length);
            connectRequest[5 + hostBytes.length] = (byte) ((targetPort >> 8) & 0xFF);
            connectRequest[6 + hostBytes.length] = (byte) (targetPort & 0xFF);
            
            out.write(connectRequest);
            out.flush();
            
            byte[] connectResponse = new byte[10];
            int bytesRead = in.read(connectResponse);
            if (bytesRead < 2) {
                return "CONNECT_FAILED";
            }
            
            if (connectResponse[0] != 0x05 || connectResponse[1] != 0x00) {
                return "CONNECT_REJECTED";
            }
            
            // HTTP request through proxy
            String httpRequest = "GET / HTTP/1.1\r\n" +
                               "Host: " + targetHost + ":" + targetPort + "\r\n" +
                               "Connection: close\r\n" +
                               "\r\n";
            
            out.write(httpRequest.getBytes(StandardCharsets.UTF_8));
            out.flush();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            
            // Skip headers
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
            }
            
            // Read body
            StringBuilder body = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            
            return body.toString();
        }
    }
}
