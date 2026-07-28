package com.njydsz.common.util.ip;

/**
 * IP 地址工具类 - 支持 IPv4/IPv6 地址解析、校验、范围判断、本地 IP 枚举等
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import com.njydsz.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * IpAddrUtils 工具类，提供静态工具方法。
 *
 * <p>所属包：{@code com.njydsz.common.util.ip}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class IpAddrUtils {
    /** 未知 IP 标识 */
    private static final String UNKNOWN = "unknown";
    /** IPv6 本地回环地址 */
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";
    /** IPv4 本地回环地址 */
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    /** IPv4 正则校验模式 */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$");
    /** IPv6 正则校验模式（支持缩写、IPv4 映射、链路本地等格式） */
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|" +
            "^([0-9a-fA-F]{1,4}:){1,7}:$|^([0-9a-fA-F]{1,4}:){0,5}(:[0-9a-fA-F]{1,4}){1,2}$|" +
            "^([0-9a-fA-F]{1,4}:){0,4}(:[0-9a-fA-F]{1,4}){1,3}$|^([0-9a-fA-F]{1,4}:){0,3}(:[0-9a-fA-F]{1,4}){1,4}$|" +
            "^([0-9a-fA-F]{1,4}:){0,2}(:[0-9a-fA-F]{1,4}){1,5}$|^([0-9a-fA-F]{1,4}:){0,1}(:[0-9a-fA-F]{1,4}){1,6}$|" +
            "^:((:[0-9a-fA-F]{1,4}){1,7}|:)$|^fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]+$|" +
            "^::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1\\d|[1-9]?|)\\d)\\.?\\b){4}$|" +
            "^([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1\\d|[1-9]?|)\\d)\\.?\\b){4}$");

    /** IPv4 私有地址前缀（RFC 1918 + 回环） */
    private static final String[] PRIVATE_IPV4_PREFIXES = {
            "10.", "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168.", "127."
    };

    /** IPv6 私有/链路本地地址前缀 */
    private static final String[] PRIVATE_IPV6_PREFIXES = {
            "fe80:", "fc", "fd", "::1", "::ffff:"
    };

    /**
     * 从 HTTP 请求中获取客户端真实 IP 地址。
     * <p>依次检查 X-Forwarded-For、Proxy-Client-IP、WL-Proxy-Client-IP、X-Real-IP 等
     * 代理头，取第一个非 unknown 的 IP。IPv6 本地回环自动转换为 IPv4。
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址，request 为 null 时返回 "unknown"
     */
    public static String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String ip = request.getHeader("x-forwarded-for");
        if (isUnknown(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_X_CLUSTER_CLIENT_IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_FORWARDED_FOR");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_FORWARDED");
        }
        if (isUnknown(ip)) {
            ip = request.getRemoteAddr();
        }

        if (LOCALHOST_IPV6.equals(ip)) {
            ip = LOCALHOST_IPV4;
        }

        return getMultistageReverseProxyIp(ip);
    }

    /**
     * 判断 IP 是否为内网地址（委托 {@link #isInternalIp}）。
     *
     * @param ip IP 地址
     * @return true 表示为内网地址
     */
    public static boolean internalIp(String ip) {
        return isInternalIp(ip);
    }

    /**
     * 判断 IP 是否为内网地址（通过 {@link InetAddress#isSiteLocalAddress()} 判断）。
     *
     * @param ip IP 地址
     * @return true 表示为内网地址或回环地址
     */
    public static boolean isInternalIp(String ip) {
        if (isUnknown(ip) || LOCALHOST_IPV4.equals(ip)) {
            return true;
        }
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            return inetAddress.isSiteLocalAddress() || inetAddress.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * 判断 IP 是否为私有地址（前缀匹配 RFC 1918 IPv4 和 IPv6 ULA/链路本地）。
     *
     * @param ip IP 地址
     * @return true 表示为私有地址
     */
    public static boolean isPrivateIp(String ip) {
        if (isUnknown(ip)) {
            return false;
        }
        for (String prefix : PRIVATE_IPV4_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }
        String lowerIp = ip.toLowerCase();
        for (String prefix : PRIVATE_IPV6_PREFIXES) {
            if (lowerIp.startsWith(prefix)) {
                return true;
            }
        }
        return isInternalIp(ip);
    }

    /**
     * 获取本机 IP 地址。
     *
     * @return 本机 IP，获取失败时返回 127.0.0.1
     */
    public static String getHostIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return LOCALHOST_IPV4;
        }
    }

    /**
     * 获取本机主机名。
     *
     * @return 主机名，获取失败时返回 "UnknownHost"
     */
    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "UnknownHost";
        }
    }

    /**
     * 校验 IP 地址格式（IPv4 或 IPv6）。
     *
     * @param ip IP 地址字符串
     * @return true 表示格式合法
     */
    public static boolean validIp(String ip) {
        return validIpv4(ip) || validIpv6(ip);
    }

    /**
     * 校验 IPv4 地址格式。
     *
     * @param ip IP 地址字符串
     * @return true 表示为合法的 IPv4 地址
     */
    public static boolean validIpv4(String ip) {
        if (StringUtils.isEmpty(ip)) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }

    public static boolean validIpv6(String ip) {
        if (StringUtils.isEmpty(ip)) {
            return false;
        }
        return IPV6_PATTERN.matcher(ip).matches();
    }

    public static String normalizeIp(String ip) {
        if (isUnknown(ip)) {
            return UNKNOWN;
        }
        if (LOCALHOST_IPV6.equals(ip)) {
            return LOCALHOST_IPV4;
        }
        return ip.trim();
    }

    public static String normalizeIpv6(String ipv6) {
        if (StringUtils.isEmpty(ipv6)) {
            return ipv6;
        }
        return ipv6.replaceAll("(?i)::+", ":").toLowerCase();
    }

    public static boolean isInRange(String ip, String cidr) {
        if (StringUtils.isEmpty(ip) || StringUtils.isEmpty(cidr)) {
            return false;
        }
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            String networkIp = parts[0];
            int prefix = Integer.parseInt(parts[1]);
            
            if (validIpv4(ip) && validIpv4(networkIp)) {
                return isIpv4InRange(ip, networkIp, prefix);
            } else if (validIpv6(ip) && validIpv6(networkIp)) {
                return isIpv6InRange(ip, networkIp, prefix);
            }
            return false;
        } catch (Exception e) {
            log.warn("IP range check failed for ip: {}, cidr: {}", ip, cidr, e);
            return false;
        }
    }

    public static boolean isIpv4InRange(String ip, String networkIp, int prefix) {
        try {
            long ipLong = ipToLong(ip);
            long networkLong = ipToLong(networkIp);
            long mask = 0xFFFFFFFFL << (32 - prefix);
            return (ipLong & mask) == (networkLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isIpv6InRange(String ip, String networkIp, int prefix) {
        try {
            byte[] ipBytes = InetAddress.getByName(ip).getAddress();
            byte[] networkBytes = InetAddress.getByName(networkIp).getAddress();
            
            for (int i = 0; i < ipBytes.length; i++) {
                int bitsToCheck = Math.min(8, prefix - (i * 8));
                if (bitsToCheck <= 0) break;
                
                int mask = 0xFF << (8 - bitsToCheck);
                if ((ipBytes[i] & mask) != (networkBytes[i] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static long ipToLong(String ip) {
        if (!validIpv4(ip)) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }
        String[] parts = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | Integer.parseInt(parts[i]);
        }
        return result;
    }

    public static String longToIp(long ipLong) {
        return ((ipLong >> 24) & 0xFF) + "." +
               ((ipLong >> 16) & 0xFF) + "." +
               ((ipLong >> 8) & 0xFF) + "." +
               (ipLong & 0xFF);
    }

    public static int getPrefixLength(String netmask) {
        if (!validIpv4(netmask)) {
            throw new IllegalArgumentException("Invalid netmask: " + netmask);
        }
        long mask = ipToLong(netmask);
        int prefix = 0;
        while ((mask & 0xFFFFFFFFL) != 0) {
            if ((mask & 1) == 0) {
                throw new IllegalArgumentException("Invalid netmask: " + netmask);
            }
            mask >>>= 1;
            prefix++;
        }
        return prefix;
    }

    public static String getNetmaskFromPrefix(int prefix) {
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid prefix: " + prefix);
        }
        long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return longToIp(mask);
    }

    public static String getNetworkAddress(String ip, int prefix) {
        if (!validIpv4(ip)) {
            throw new IllegalArgumentException("Invalid IP: " + ip);
        }
        long ipLong = ipToLong(ip);
        long mask = 0xFFFFFFFFL << (32 - prefix);
        return longToIp(ipLong & mask);
    }

    public static String getBroadcastAddress(String ip, int prefix) {
        if (!validIpv4(ip)) {
            throw new IllegalArgumentException("Invalid IP: " + ip);
        }
        long ipLong = ipToLong(ip);
        long mask = 0xFFFFFFFFL << (32 - prefix);
        long broadcast = (ipLong & mask) | (~mask & 0xFFFFFFFFL);
        return longToIp(broadcast);
    }

    public static List<String> listLocalIps() {
        List<String> ips = new ArrayList<>();
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
                    if (!(addr instanceof Inet6Address) || !addr.isLinkLocalAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to list local IPs", e);
        }
        return ips;
    }

    public static IpType getIpType(String ip) {
        if (isUnknown(ip)) {
            return IpType.UNKNOWN;
        }
        if (LOCALHOST_IPV4.equals(ip) || LOCALHOST_IPV6.equals(ip)) {
            return IpType.LOCALHOST;
        }
        if (validIpv4(ip)) {
            if (isPrivateIp(ip)) {
                return IpType.PRIVATE_IPV4;
            }
            return IpType.PUBLIC_IPV4;
        }
        if (validIpv6(ip)) {
            if (isPrivateIp(ip)) {
                return IpType.PRIVATE_IPV6;
            }
            return IpType.PUBLIC_IPV6;
        }
        return IpType.INVALID;
    }

    public static boolean isDataCenterIp(String ip) {
        if (isUnknown(ip)) {
            return false;
        }
        String lowerIp = ip.toLowerCase();
        return lowerIp.startsWith("10.") || 
               lowerIp.startsWith("172.16.") || lowerIp.startsWith("172.17.") ||
               lowerIp.startsWith("172.18.") || lowerIp.startsWith("172.19.") ||
               lowerIp.startsWith("172.20.") || lowerIp.startsWith("172.21.") ||
               lowerIp.startsWith("172.22.") || lowerIp.startsWith("172.23.") ||
               lowerIp.startsWith("172.24.") || lowerIp.startsWith("172.25.") ||
               lowerIp.startsWith("172.26.") || lowerIp.startsWith("172.27.") ||
               lowerIp.startsWith("172.28.") || lowerIp.startsWith("172.29.") ||
               lowerIp.startsWith("172.30.") || lowerIp.startsWith("172.31.") ||
               lowerIp.startsWith("192.168.");
    }

    public static boolean isProxyIp(String ip) {
        return isDataCenterIp(ip);
    }

    private static boolean isUnknown(String ip) {
        return StringUtils.isEmpty(ip) || UNKNOWN.equalsIgnoreCase(ip);
    }

    private static String getMultistageReverseProxyIp(String ip) {
        if (ip != null && ip.indexOf(",") > 0) {
            final String[] ips = ip.split(",");
            for (String subIp : ips) {
                if (!isUnknown(subIp)) {
                    return subIp.trim();
                }
            }
        }
        return ip;
    }

    public enum IpType {
        LOCALHOST,
        PRIVATE_IPV4,
        PRIVATE_IPV6,
        PUBLIC_IPV4,
        PUBLIC_IPV6,
        UNKNOWN,
        INVALID
    }
}
