package com.remisoft.common.util.ip;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import com.remisoft.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * IP 地址工具类 - 支持 IPv4/IPv6 地址解析、校验、范围判断、本地 IP 枚举等
 *
 * @author remi-team
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

    /**
     * 从 HTTP 请求中获取客户端真实 IP 地址（基于可信代理白名单）。
     *
     * <p>从 X-Forwarded-For 头部最右侧（最可信的代理）向左遍历，跳过所有属于
     * {@code trustedProxies} 的 IP，返回第一个非可信 IP 作为真实客户端 IP。
     * 这是 Spring Security {@code ForwardedHeaderFilter} 推荐的安全做法，
     * 可防止攻击者伪造 X-Forwarded-For 头绕过 IP 限制。
     *
     * <p>典型用法：
     * <pre>{@code
     * Set<String> trusted = Set.of("10.0.0.1", "10.0.0.2"); // 反向代理 IP
     * String clientIp = IpAddrUtils.getIpAddrWithTrustedProxies(request, trusted);
     * }</pre>
     *
     * @param request         HTTP 请求
     * @param trustedProxies  可信代理 IP 集合（不可为 null）
     * @return 客户端真实 IP；无 X-Forwarded-For 头或全部为可信代理时返回 remoteAddr
     * @since 1.0.0
     */
    public static String getIpAddrWithTrustedProxies(HttpServletRequest request, Set<String> trustedProxies) {
        if (request == null) {
            return UNKNOWN;
        }
        Set<String> trusted = trustedProxies == null ? Collections.emptySet() : trustedProxies;
        String xff = request.getHeader("x-forwarded-for");
        if (!isUnknown(xff)) {
            if (xff.indexOf(',') >= 0) {
                String[] parts = xff.split(",");
                // 从右向左遍历：跳过可信代理，取第一个非可信 IP
                for (int i = parts.length - 1; i >= 0; i--) {
                    String candidate = parts[i].trim();
                    if (!isUnknown(candidate) && !trusted.contains(candidate)) {
                        return LOCALHOST_IPV6.equals(candidate) ? LOCALHOST_IPV4 : candidate;
                    }
                }
                // 全部为可信代理，取最左侧（原始客户端，可能被伪造，但已是最佳推断）
                String first = parts[0].trim();
                if (!isUnknown(first)) {
                    return LOCALHOST_IPV6.equals(first) ? LOCALHOST_IPV4 : first;
                }
            } else {
                // 单 IP 的 XFF（无逗号）：非空且非可信代理 IP 时直接用作客户端 IP
                String candidate = xff.trim();
                if (!isUnknown(candidate) && !trusted.contains(candidate)) {
                    return LOCALHOST_IPV6.equals(candidate) ? LOCALHOST_IPV4 : candidate;
                }
            }
        }
        String ip = request.getRemoteAddr();
        return LOCALHOST_IPV6.equals(ip) ? LOCALHOST_IPV4 : ip;
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
        // 前置格式校验，避免 InetAddress.getByName 触发 DNS 解析（SSRF / DNS rebinding 风险）
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

    /**
     * 校验 IPv6 地址格式是否合法。
     *
     * <p>通过预编译正则覆盖缩写、IPv4 映射（如 {@code ::ffff:ipv4}）、链路本地等常见格式；
     * 空串直接返回 {@code false}。注意本方法仅做格式校验，不校验语义前缀合法性。
     *
     * @param ip IPv6 地址字符串
     * @return true 表示格式合法
     */
    public static boolean validIpv6(String ip) {
        if (StringUtils.isEmpty(ip)) {
            return false;
        }
        return IPV6_PATTERN.matcher(ip).matches();
    }

    /**
     * 规范化 IPv4/IPv6 混合地址，用于统一存储与比对。
     *
     * <p>空/unknown 原样返回占位符；IPv6 本地回环 {@code ::1} 归一为 {@code 127.0.0.1}，其余去除首尾空白。
     * 目的是消除代理头中携带的多余空格与回环表示差异，避免去重/判等失真。
     *
     * @param ip 原始 IP 字符串
     * @return 规范化后的 IP，或 {@code unknown} 占位符
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
     *
     * <p>将多个连续 {@code :} 折叠为单个并统一小写，便于作为缓存 key 或比对。
     * 空串直接原样返回；格式非法（未通过 {@link #validIpv6(String)}）时返回原值的小写化，
     * 不调用 {@link InetAddress#getByName} 以避免触发 DNS 解析（SSRF / DNS rebinding 风险）。
     *
     * @param ipv6 原始 IPv6 字符串
     * @return 折叠小写后的 IPv6 字符串
     */
    public static String normalizeIpv6(String ipv6) {
        if (StringUtils.isEmpty(ipv6)) {
            return ipv6;
        }
        // 前置格式校验，避免 InetAddress.getByName 触发 DNS 解析（SSRF / DNS rebinding 风险）
        if (!validIpv6(ipv6)) {
            return ipv6.trim().toLowerCase();
        }
        try {
            return InetAddress.getByName(ipv6).getHostAddress();
        } catch (UnknownHostException e) {
            // 非法 IPv6 降级：仅做小写化与空白裁剪，保留原语义
            return ipv6.trim().toLowerCase();
        }
    }

    /**
     * 判断 IP 是否在 CIDR 网段内。
     *
     * <p>自动识别 IPv4 / IPv6 并分派到对应实现；参数为空、CIDR 格式非法
     * 或解析异常时统一返回 {@code false}（宽松失败，不影响调用方主流程）。</p>
     *
     * @param ip   待判断的 IP 地址
     * @param cidr CIDR 网段（如 {@code 192.168.1.0/24} 或 {@code 2001:db8::/32}）
     * @return {@code true} 表示 IP 在网段内；非法输入返回 {@code false}
     */
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

    /**
     * 判断 IPv4 是否在网段内（基于整型掩码比较）。
     *
     * @param ip        待判断的 IPv4 地址
     * @param networkIp 网段起始地址（网络地址）
     * @param prefix    前缀长度 [0, 32]
     * @return {@code true} 表示在网段内；解析异常返回 {@code false}
     */
    public static boolean isIpv4InRange(String ip, String networkIp, int prefix) {
        if (prefix < 0 || prefix > 32) {
            return false;
        }
        try {
            long ipLong = ipToLong(ip);
            long networkLong = ipToLong(networkIp);
            // prefix=0 时掩码应为 0，所有 IPv4 均在网段内
            if (prefix == 0) {
                return true;
            }
            long mask = 0xFFFFFFFFL << (32 - prefix);
            return (ipLong & mask) == (networkLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断 IPv6 是否在网段内（基于字节级掩码比较）。
     *
     * <p>调用 {@link InetAddress#getByName} 前先用 {@link #validIpv6(String)} 校验格式，
     * 避免非法输入触发 DNS 解析（SSRF / DNS rebinding 风险）。
     *
     * @param ip        待判断的 IPv6 地址
     * @param networkIp 网段起始地址（网络地址）
     * @param prefix    前缀长度 [0, 128]
     * @return {@code true} 表示在网段内；解析异常返回 {@code false}
     */
    public static boolean isIpv6InRange(String ip, String networkIp, int prefix) {
        // 前置格式校验，避免 InetAddress.getByName 触发 DNS 解析（SSRF / DNS rebinding 风险）
        if (!validIpv6(ip) || !validIpv6(networkIp)) {
            return false;
        }
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

    /**
     * 将 IPv4 地址转换为 32 位无符号长整型。
     *
     * <p>便于做网段掩码运算，与 {@link #longToIp(long)} 互逆。
     * 非合法 IPv4 会抛出 {@link IllegalArgumentException}，调用方应预先用 {@link #validIpv4(String)} 校验。
     *
     * @param ip 点分十进制 IPv4 地址，非空且合法
     * @return 对应的长整型值
     * @throws IllegalArgumentException 当 IP 格式非法时
     */
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

    /**
     * 将 32 位长整型还原为点分十进制 IPv4 地址。
     *
     * <p>与 {@link #ipToLong(String)} 互逆，用于网段计算结果的展示。
     * 不校验入参范围，调用方应保证其为合法 IPv4 整数表示。
     *
     * @param ipLong IPv4 对应的长整型值
     * @return 点分十进制地址字符串
     */
    public static String longToIp(long ipLong) {
        return ((ipLong >> 24) & 0xFF) + "." +
               ((ipLong >> 16) & 0xFF) + "." +
               ((ipLong >> 8) & 0xFF) + "." +
               (ipLong & 0xFF);
    }

    /**
     * 从子网掩码反推前缀长度（CIDR prefix）。
     *
     * <p>例如掩码 {@code 255.255.255.0} 返回 24。掩码必须为高位连续 1、低位连续 0，
     * 否则抛出 {@link IllegalArgumentException}（非法掩码）。
     *
     * @param netmask 点分十进制子网掩码，非空且合法
     * @return 前缀长度（0~32）
     * @throws IllegalArgumentException 当掩码格式非法或不连续时
     */
    public static int getPrefixLength(String netmask) {
        if (!validIpv4(netmask)) {
            throw new IllegalArgumentException("Invalid netmask: " + netmask);
        }
        long mask = ipToLong(netmask) & 0xFFFFFFFFL;
        int prefix = 0;
        boolean foundZero = false;
        // 从高位向低位扫描：1 必须连续，遇到 0 后不能再出现 1
        for (int i = 31; i >= 0; i--) {
            if ((mask & (1L << i)) != 0) {
                if (foundZero) {
                    throw new IllegalArgumentException("Invalid netmask: " + netmask);
                }
                prefix++;
            } else {
                foundZero = true;
            }
        }
        return prefix;
    }

    /**
     * 由前缀长度生成对应的子网掩码。
     *
     * <p>与 {@link #getPrefixLength(String)} 互逆，例如 24 返回 {@code 255.255.255.0}。
     * 前缀越界（&lt;0 或 &gt;32）抛出 {@link IllegalArgumentException}。
     *
     * @param prefix CIDR 前缀长度（0~32）
     * @return 点分十进制子网掩码
     * @throws IllegalArgumentException 当前缀越界时
     */
    public static String getNetmaskFromPrefix(int prefix) {
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid prefix: " + prefix);
        }
        // prefix=0 时掩码为 0，特殊处理避免 long 移位 32 位无效的问题
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return longToIp(mask);
    }

    /**
     * 计算 IP 所在子网的网络地址。
     *
     * <p>对 IP 与掩码按位与，得到网段起始地址（用于路由、ACL 判定）。
     * 要求 IP 为合法 IPv4，否则抛出 {@link IllegalArgumentException}。
     *
     * @param ip     IPv4 地址，非空且合法
     * @param prefix CIDR 前缀长度
     * @return 网络地址（点分十进制）
     * @throws IllegalArgumentException 当 IP 非法时
     */
    public static String getNetworkAddress(String ip, int prefix) {
        if (!validIpv4(ip)) {
            throw new IllegalArgumentException("Invalid IP: " + ip);
        }
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid prefix: " + prefix);
        }
        long ipLong = ipToLong(ip);
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return longToIp(ipLong & mask);
    }

    /**
     * 计算 IP 所在子网的广播地址。
     *
     * <p>在网络地址基础上对主机位取全 1，用于子网内广播寻址。
     * 要求 IP 为合法 IPv4，否则抛出 {@link IllegalArgumentException}。
     *
     * @param ip     IPv4 地址，非空且合法
     * @param prefix CIDR 前缀长度
     * @return 广播地址（点分十进制）
     * @throws IllegalArgumentException 当 IP 非法时
     */
    public static String getBroadcastAddress(String ip, int prefix) {
        if (!validIpv4(ip)) {
            throw new IllegalArgumentException("Invalid IP: " + ip);
        }
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid prefix: " + prefix);
        }
        long ipLong = ipToLong(ip);
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        long broadcast = (ipLong & mask) | (~mask & 0xFFFFFFFFL);
        return longToIp(broadcast);
    }

    /**
     * 枚举本机所有非回环、非虚拟且在线的网络接口 IP。
     *
     * <p>遍历 {@link NetworkInterface}，跳过 loopback/virtual/未启用接口；
     * 对 IPv6 仅收集非链路本地地址（避免 {@code fe80} 噪声）。
     * 枚举异常时记录日志并返回已收集的部分结果（不抛异常，保证可用性）。
     *
     * @return 本机 IP 地址列表，可能为空
     */
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
                    // 统一排除链路本地（IPv4 169.154.x.x / IPv6 fe80::）和回环地址
                    if (!addr.isLinkLocalAddress() && !addr.isLoopbackAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to list local IPs", e);
        }
        return ips;
    }

    /**
     * 识别 IP 地址的类型。
     *
     * <p>判定顺序：未知 → 本机回环 → 合法 IPv4（私有/公网）→ 合法 IPv6（私有/公网）→ 非法。</p>
     *
     * @param ip 待识别的 IP 地址
     * @return 对应的 {@link IpType} 枚举
     */
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

    /**
     * 判断 IP 是否为数据中心/私有网段地址。
     *
     * <p>用于风控场景：数据中心 IP 通常不可信（代理/机房出口），
     * 当前实现等价于私有网段判断。</p>
     *
     * @param ip 待判断的 IP 地址
     * @return {@code true} 表示属于私有/数据中心网段
     */
    public static boolean isDataCenterIp(String ip) {
        return isPrivateIp(ip);
    }

    /**
     * 判断 IP 是否为代理出口地址。
     *
     * <p>风控语义：代理 IP 以数据中心/私有网段为主，当前实现
     * 委托给 {@link #isDataCenterIp(String)} 判断。</p>
     *
     * @param ip 待判断的 IP 地址
     * @return {@code true} 表示疑似代理 IP
     */
    public static boolean isProxyIp(String ip) {
        return isDataCenterIp(ip);
    }

    private static boolean isUnknown(String ip) {
        return StringUtils.isEmpty(ip) || UNKNOWN.equalsIgnoreCase(ip);
    }

    /**
     * IP 地址类型枚举。
     */
    public enum IpType {
        /** 本机回环地址（127.0.0.1 / ::1） */
        LOCALHOST,
        /** 私有 IPv4 地址（RFC1918） */
        PRIVATE_IPV4,
        /** 私有 IPv6 地址（ULA 等） */
        PRIVATE_IPV6,
        /** 公网 IPv4 地址 */
        PUBLIC_IPV4,
        /** 公网 IPv6 地址 */
        PUBLIC_IPV6,
        /** 无法判定的地址（如空值/特殊地址） */
        UNKNOWN,
        /** 非法地址格式 */
        INVALID
    }
}
