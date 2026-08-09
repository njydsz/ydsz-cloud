package com.njydsz.common.util.ip;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import com.njydsz.common.util.string.StringUtils;

/**
 * IP 地址格式校验与内网判断工具类。
 *
 * <p>聚焦于 IP 格式校验（IPv4/IPv6）、内网/私有地址判断、IP 类型识别。
 * 自 1.4.0 起从原 {@code IpAddrUtils} 拆分为独立类。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public final class IpValidator {

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

    /** IPv4 私有地址前缀（RFC 1918 + 回环 + 链路本地 169.254.0.0/16） */
    private static final String[] PRIVATE_IPV4_PREFIXES = {
            "10.", "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168.", "127.", "169.254."
    };

    /** IPv6 私有/链路本地地址前缀 */
    private static final String[] PRIVATE_IPV6_PREFIXES = {
            "fe80:", "fc", "fd", "::1", "::ffff:"
    };

    private IpValidator() {
        throw new UnsupportedOperationException("IpValidator is a utility class and cannot be instantiated");
    }

    /**
     * 校验 IP 地址格式（IPv4 或 IPv6）。
     */
    public static boolean validIp(String ip) {
        return validIpv4(ip) || validIpv6(ip);
    }

    /**
     * 校验 IPv4 地址格式。
     */
    public static boolean validIpv4(String ip) {
        if (StringUtils.isEmpty(ip)) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * 校验 IPv6 地址格式是否合法。
     */
    public static boolean validIpv6(String ip) {
        if (StringUtils.isEmpty(ip)) {
            return false;
        }
        return IPV6_PATTERN.matcher(ip).matches();
    }

    /**
     * 判断 IP 是否为内网地址（通过 {@link InetAddress#isSiteLocalAddress()} 判断）。
     */
    public static boolean isInternalIp(String ip) {
        if (isUnknown(ip) || LOCALHOST_IPV4.equals(ip)) {
            return true;
        }
        if (!validIpv4(ip) && !validIpv6(ip)) {
            return false;
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
     * 规范化 IPv4/IPv6 混合地址，用于统一存储与比对。
     */
    public static String normalizeIp(String ip) {
        if (isUnknown(ip)) {
            return UNKNOWN;
        }
        if (LOCALHOST_IPV6.equals(ip)) {
            return LOCALHOST_IPV4;
        }
        return ip.trim();
    }

    /**
     * 规范化 IPv6 地址（折叠连续冒号并转小写）。
     */
    public static String normalizeIpv6(String ipv6) {
        if (StringUtils.isEmpty(ipv6)) {
            return ipv6;
        }
        if (!validIpv6(ipv6)) {
            return ipv6.trim().toLowerCase();
        }
        try {
            return InetAddress.getByName(ipv6).getHostAddress();
        } catch (UnknownHostException e) {
            return ipv6.trim().toLowerCase();
        }
    }

    /**
     * 识别 IP 地址的类型。
     */
    public static IpType getIpType(String ip) {
        if (isUnknown(ip)) {
            return IpType.UNKNOWN;
        }
        if (LOCALHOST_IPV4.equals(ip) || LOCALHOST_IPV6.equals(ip)) {
            return IpType.LOCALHOST;
        }
        if (validIpv4(ip)) {
            return isPrivateIp(ip) ? IpType.PRIVATE_IPV4 : IpType.PUBLIC_IPV4;
        }
        if (validIpv6(ip)) {
            return isPrivateIp(ip) ? IpType.PRIVATE_IPV6 : IpType.PUBLIC_IPV6;
        }
        return IpType.INVALID;
    }

    static boolean isUnknown(String ip) {
        return StringUtils.isEmpty(ip) || UNKNOWN.equalsIgnoreCase(ip);
    }

    /**
     * IP 地址类型枚举。
     */
    public enum IpType {
        LOCALHOST, PRIVATE_IPV4, PRIVATE_IPV6, PUBLIC_IPV4, PUBLIC_IPV6, UNKNOWN, INVALID
    }
}
