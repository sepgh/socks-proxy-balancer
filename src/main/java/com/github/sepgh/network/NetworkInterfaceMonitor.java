package com.github.sepgh.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class NetworkInterfaceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(NetworkInterfaceMonitor.class);
    
    private final String interfaceName;
    private volatile boolean lastKnownState = true;

    public NetworkInterfaceMonitor(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public boolean isNetworkAvailable() {
        try {
            if (interfaceName != null && !interfaceName.isEmpty()) {
                return isSpecificInterfaceUp(interfaceName);
            } else {
                return isAnyInterfaceUp();
            }
        } catch (Exception e) {
            logger.error("Error checking network interface availability", e);
            return lastKnownState;
        }
    }

    private boolean isSpecificInterfaceUp(String name) throws SocketException {
        NetworkInterface networkInterface = NetworkInterface.getByName(name);
        if (networkInterface == null) {
            logger.warn("Network interface {} not found", name);
            return false;
        }
        
        boolean isUp = networkInterface.isUp() && !networkInterface.isLoopback();
        updateState(isUp, name);
        return isUp;
    }

    private boolean isAnyInterfaceUp() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            
            if (networkInterface.isUp() && !networkInterface.isLoopback() && !networkInterface.isVirtual()) {
                if (hasIpAddress(networkInterface)) {
                    updateState(true, networkInterface.getName());
                    return true;
                }
            }
        }
        
        updateState(false, "none");
        return false;
    }

    private boolean hasIpAddress(NetworkInterface networkInterface) {
        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                return true;
            }
        }
        return false;
    }

    private void updateState(boolean isUp, String name) {
        if (isUp != lastKnownState) {
            if (isUp) {
                logger.info("Network interface {} is now UP", name);
            } else {
                logger.warn("Network interface {} is now DOWN", name);
            }
            lastKnownState = isUp;
        }
    }

    public String getInterfaceName() {
        return interfaceName;
    }
}
