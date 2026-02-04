package com.github.sepgh.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Random;

public class DnsTester {
    private static final Logger logger = LoggerFactory.getLogger(DnsTester.class);
    private final int timeoutMs;
    private final String testDomain;

    public DnsTester(int timeoutMs, String testDomain) {
        this.timeoutMs = timeoutMs;
        this.testDomain = testDomain;
    }

    public DnsTestResult test(DnsEndpoint endpoint) {
        long startTime = System.currentTimeMillis();
        
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            
            byte[] queryData = buildDnsQuery(testDomain);
            
            InetAddress dnsServer = InetAddress.getByName(endpoint.getIp());
            DatagramPacket queryPacket = new DatagramPacket(
                queryData, 
                queryData.length, 
                dnsServer, 
                endpoint.getPort()
            );
            
            logger.debug("Sending DNS query for {} to {}", testDomain, endpoint);
            socket.send(queryPacket);
            
            byte[] responseData = new byte[512];
            DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length);
            socket.receive(responsePacket);
            
            if (!isValidDnsResponse(responseData, responsePacket.getLength())) {
                return DnsTestResult.failure(endpoint, "Invalid DNS response");
            }
            
            long latency = System.currentTimeMillis() - startTime;
            logger.debug("DNS query to {} successful, latency: {}ms", endpoint, latency);
            return DnsTestResult.success(endpoint, latency);
            
        } catch (SocketTimeoutException e) {
            logger.debug("DNS query timeout for {}: {}", endpoint, e.getMessage());
            return DnsTestResult.failure(endpoint, "Timeout: " + e.getMessage());
        } catch (IOException e) {
            logger.debug("DNS query error for {}: {}", endpoint, e.getMessage());
            return DnsTestResult.failure(endpoint, e.getMessage());
        }
    }

    private byte[] buildDnsQuery(String domain) {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        
        short transactionId = (short) new Random().nextInt(65536);
        buffer.putShort(transactionId);
        
        short flags = 0x0100;
        buffer.putShort(flags);
        
        buffer.putShort((short) 1);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        
        String[] labels = domain.split("\\.");
        for (String label : labels) {
            buffer.put((byte) label.length());
            buffer.put(label.getBytes());
        }
        buffer.put((byte) 0);
        
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        
        byte[] query = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(query);
        return query;
    }

    private boolean isValidDnsResponse(byte[] data, int length) {
        if (length < 12) {
            return false;
        }
        
        byte flags1 = data[2];
        boolean isResponse = (flags1 & 0x80) != 0;
        
        return isResponse;
    }
}
