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
      * @param ip ip
      * @return 处理后的结果
     */
    public static boolean validIp(String ip) {
        return validIpv4(ip) || validIpv6(ip);
    }

    /**
     * 校验 IPv4 地址格式。
     * @param ip ip
     * @return 处理后的结果
     */
    public static boolean validIpv4(String ip) {
        if (StringUtils.isEmpty(ip)) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * 校验 IPv6 地址格式是否合法。
     * @param ip ip
     * @return 处理后的结果
     */
    public static boolean validIpv6(String ip) {
        if (StringUtils.isEmpty(ip)) {
            return false;
        }
        return IPV6_PATTERN.matcher(ip).matches();
    }

    /**
     * 判断 IP 是否为内网地址（通过 {@link InetAddress#isSiteLocalAddress()} 判断）。
     * @param ip ip
     * @return 处理后的结果
     */
    public static boolean isInternalIp(String ip) {
        if (isUnknown(ip) || LOCALHOST_IPV4.equals(ip)) {
            return true;
        }
        if (!validIpv4(ip) && !validIpv6(ip)) {
            return false;
        }
        try {
            InetAddress inetAddress = toInetAddress(ip);
            return inetAddress.isSiteLocalAddress() || inetAddress.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * 判断 IP 是否为私有地址（前缀匹配 RFC 1918 IPv4 和 IPv6 ULA/链路本地）。
     * @param ip ip
     * @return 处理后的结果
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
     * @param ip ip
     * @return 处理后的结果
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
     * 规范化 IPv6 地址（折叠连续冒号并转小写，符合 RFC 5952）。
     *
     * <p>JDK 的 {@link InetAddress#getHostAddress()} 返回全 8 组形式（不压缩），
     * 本方法在此基础上折叠最长的连续全零组为 {@code ::}。
     * @param ipv6 ipv6
     * @return 处理后的结果
     */
    public static String normalizeIpv6(String ipv6) {
        if (StringUtils.isEmpty(ipv6)) {
            return ipv6;
        }
        if (!validIpv6(ipv6)) {
            return ipv6.trim().toLowerCase();
        }
        try {
            String canonical = toInetAddress(ipv6).getHostAddress();
            return compressIpv6(canonical);
        } catch (UnknownHostException e) {
            return ipv6.trim().toLowerCase();
        }
    }

    /**
     * 将全 8 组形式的 IPv6 地址折叠最长的连续零组为 {@code ::}（RFC 5952）。
     *
     * <p>仅当连续零组长度 ≥ 2 时才折叠；无符合条件的零组时保持原样。
     *
     * @param canonical 规范形式 IPv6（形如 {@code 2001:db8:0:0:0:0:0:1}）
     * @return 折叠后的 IPv6 字符串
     */
    private static String compressIpv6(String canonical) {
        String[] groups = canonical.split(":", -1);
        int bestStart = -1;
        int bestLen = 0;
        for (int i = 0; i < groups.length; i++) {
            if (!"0".equals(groups[i])) {
                continue;
            }
            int j = i;
            while (j < groups.length && "0".equals(groups[j])) {
                j++;
            }
            int len = j - i;
            if (len >= 2 && len > bestLen) {
                bestStart = i;
                bestLen = len;
            }
            i = j - 1;
        }
        if (bestStart < 0) {
            return canonical;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bestStart; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(groups[i]);
        }
        sb.append("::");
        for (int i = bestStart + bestLen; i < groups.length; i++) {
            if (i > bestStart + bestLen) {
                sb.append(':');
            }
            sb.append(groups[i]);
        }
        return sb.toString();
    }

    /**
     * 将 IP 字面量转为 {@link InetAddress}，全程不产生任何网络 IO。
     *
     * <p>IPv4 字面量直接解析为 4 字节后通过 {@link InetAddress#getByAddress(byte[])} 构造，
     * 彻底规避 DNS 解析与反向查询；IPv6 字面量经 {@link InetAddress#getByName(String)} 时，
     * JDK 会先尝试按字面量解析，仅对主机名才触发 DNS，故合法 IPv6 字面量同样不会发起网络请求。
     *
     * @param ip 已通过 {@link #validIpv4} / {@link #validIpv6} 校验的 IP 字面量
     * @return 对应的 InetAddress
     * @throws UnknownHostException 字面量无法解析时（理论上不应发生于已校验输入）
     */
    private static InetAddress toInetAddress(String ip) throws UnknownHostException {
        if (validIpv4(ip)) {
            String[] parts = ip.split("\\.");
            byte[] addr = new byte[4];
            for (int i = 0; i < 4; i++) {
                addr[i] = (byte) Integer.parseInt(parts[i]);
            }
            return InetAddress.getByAddress(addr);
        }
        return InetAddress.getByName(ip);
    }

    /**
     * 识别 IP 地址的类型。
     * @param ip ip
     * @return 处理后的结果
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











