package com.njydsz.pmis.common.util.ip;

/**
 * MAC 地址获取工具类
 *
 * <p>获取本机所有网络接口的 MAC 地址，支持 Windows/Linux/Mac 跨平台。
 * 内置缓存机制，首次获取后缓存结果，后续调用直接返回缓存值。
 *
 * <p>使用方式：
 * <pre>{@code
 * List<String> macList = MacAddressUtils.getAllHostMacAddress();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MacAddressUtils {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final Map<String, String> MAC_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean CACHE_ENABLED = true;
    private static String cachedAllMacAddress = null;

    public static String getAllHostMacAddress() {
        if (CACHE_ENABLED && cachedAllMacAddress != null) {
            return cachedAllMacAddress;
        }

        StringJoiner joiner = new StringJoiner(",");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String mac = getMac(addr);
                    if (mac != null) {
                        joiner.add(mac);
                    }
                }
            }
            
            String result = joiner.toString();
            if (CACHE_ENABLED) {
                cachedAllMacAddress = result;
            }
            return result;
        } catch (SocketException e) {
            log.error("Failed to get MAC address: {}", e.getMessage());
            return "";
        }
    }

    public static List<NetworkInfo> getAllNetworkInterfaces() {
        List<NetworkInfo> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                NetworkInfo info = new NetworkInfo();
                info.setName(networkInterface.getName());
                info.setDisplayName(networkInterface.getDisplayName());
                info.setUp(networkInterface.isUp());
                info.setLoopback(networkInterface.isLoopback());
                info.setVirtual(networkInterface.isVirtual());
                info.setPointToPoint(networkInterface.isPointToPoint());
                info.setMulticast(networkInterface.supportsMulticast());
                
                byte[] macBytes = networkInterface.getHardwareAddress();
                if (macBytes != null && macBytes.length > 0) {
                    info.setMac(formatMac(macBytes));
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                List<String> ips = new ArrayList<>();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    ips.add(addr.getHostAddress());
                }
                info.setIps(ips);
                
                result.add(info);
            }
        } catch (SocketException e) {
            log.error("Failed to list network interfaces", e);
        }
        return result;
    }

    public static String getMacByInterfaceName(String interfaceName) {
        if (interfaceName == null || interfaceName.isEmpty()) {
            return null;
        }
        
        String cacheKey = "iface:" + interfaceName;
        if (CACHE_ENABLED) {
            String cached = MAC_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        
        try {
            NetworkInterface networkInterface = NetworkInterface.getByName(interfaceName);
            if (networkInterface == null || !networkInterface.isUp() || networkInterface.isLoopback()) {
                return null;
            }
            
            byte[] mac = networkInterface.getHardwareAddress();
            if (mac == null) {
                return null;
            }
            
            String macStr = formatMac(mac);
            if (CACHE_ENABLED) {
                MAC_CACHE.put(cacheKey, macStr);
            }
            return macStr;
        } catch (SocketException e) {
            log.error("Failed to get MAC for interface: {}", interfaceName, e);
            return null;
        }
    }

    public static String getMacByIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        
        String cacheKey = "ip:" + ip;
        if (CACHE_ENABLED) {
            String cached = MAC_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            String mac = getMac(inetAddress);
            if (CACHE_ENABLED && mac != null) {
                MAC_CACHE.put(cacheKey, mac);
            }
            return mac;
        } catch (Exception e) {
            log.error("Failed to get MAC for IP: {}", ip, e);
            return null;
        }
    }

    public static String getMac(InetAddress inetAddress) {
        try {
            NetworkInterface ni = NetworkInterface.getByInetAddress(inetAddress);
            if (ni == null) {
                return null;
            }
            
            byte[] mac = ni.getHardwareAddress();
            if (mac == null) {
                return null;
            }

            return formatMac(mac);
        } catch (SocketException e) {
            return null;
        }
    }

    public static String formatMac(byte[] mac) {
        if (mac == null || mac.length == 0) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            if (i > 0) {
                sb.append(OS_NAME.contains("win") ? "-" : ":");
            }
            sb.append(String.format("%02X", mac[i]));
        }
        return sb.toString();
    }

    public static String formatMac(byte[] mac, String separator) {
        if (mac == null || mac.length == 0) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(String.format("%02X", mac[i]));
        }
        return sb.toString();
    }

    public static String normalizeMac(String mac) {
        if (mac == null || mac.isEmpty()) {
            return mac;
        }
        return mac.replaceAll("[-:]", ":").toUpperCase();
    }

    public static boolean isValidMac(String mac) {
        if (mac == null || mac.isEmpty()) {
            return false;
        }
        String normalized = mac.replaceAll("[-:]", "");
        return normalized.matches("^[0-9A-Fa-f]{12}$");
    }

    public static void clearCache() {
        MAC_CACHE.clear();
        cachedAllMacAddress = null;
        log.info("MAC address cache cleared");
    }

    public static void setCacheEnabled(boolean enabled) {
        CACHE_ENABLED = enabled;
        log.info("MAC address cache {}", enabled ? "enabled" : "disabled");
    }

    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    public static boolean isLinux() {
        return OS_NAME.contains("linux");
    }

    public static boolean isMac() {
        return OS_NAME.contains("mac");
    }

    public static String getOsName() {
        return OS_NAME;
    }

    @Getter
    @ToString
    @Setter
    public static class NetworkInfo {
        private String name;
        private String displayName;
        private boolean up;
        private boolean loopback;
        private boolean virtual;
        private boolean pointToPoint;
        private boolean multicast;
        private String mac;
        private List<String> ips;
    }

}
