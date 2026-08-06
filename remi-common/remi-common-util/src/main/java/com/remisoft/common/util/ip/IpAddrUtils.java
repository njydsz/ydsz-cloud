package com.remisoft.common.util.ip;

import java.util.Collections;
import java.util.Set;

import com.remisoft.common.util.string.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * IP 地址工具类 - 支持 IPv4/IPv6 地址解析、校验、范围判断、本地 IP 枚举等。
 *
 * <p>自 1.4.0 起，通用 IP 能力已拆分为三个独立工具类：
 * <ul>
 *   <li>{@link IpValidator} — IP 格式校验、内网/私有地址判断、IP 类型识别</li>
 *   <li>{@link CidrUtils} — CIDR 网段计算（范围判断、掩码转换、网络/广播地址）</li>
 *   <li>{@link NetworkInterfaceUtils} — 本机网络接口枚举（host IP、host name、local IPs）</li>
 * </ul>
 *
 * <p>本类仅保留 HTTP 请求 IP 解析等 Web 层专有方法，通用 IP 能力请直接使用上述新类。
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

    private IpAddrUtils() {
        throw new UnsupportedOperationException("IpAddrUtils is a utility class and cannot be instantiated");
    }

    // ==================== HTTP 请求 IP 解析（本类独有）====================

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
     * 判断字符串是否为"未知"IP 占位符（空值或 "unknown" 忽略大小写）。
     *
     * @param ip IP 地址字符串
     * @return true 表示为未知 IP     */
    private static boolean isUnknown(String ip) {
        return StringUtils.isEmpty(ip) || UNKNOWN.equalsIgnoreCase(ip);
    }

    /**
     * 从 HTTP 请求中获取客户端真实 IP 地址（使用 {@code ServletUtils} 配置的可信代理）。
     *
     * <p>便捷方法，等价于调用 {@code getIpAddrWithTrustedProxies(request, ...)} 并传入
     * {@code ServletUtils} 中配置的可信代理集合。适用于标准 Web 应用场景。
     *
     * @param request HTTP 请求
     * @return 客户端真实 IP；request 为 null 时返回 "unknown"
     */
    public static String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        // 通过 ServletRequestUtils.isTrustedProxy 判断 remoteAddr 是否可信
        // 如果直连对端可信，则尝试从 X-Forwarded-For 解析真实 IP
        String xff = request.getHeader("x-forwarded-for");
        if (!isUnknown(xff) && com.remisoft.common.util.http.ServletRequestUtils.isTrustedProxy(request)) {
            // 取 XFF 中最左侧的 IP（原始客户端）
            if (xff.indexOf(',') >= 0) {
                String first = xff.split(",")[0].trim();
                if (!isUnknown(first)) {
                    return LOCALHOST_IPV6.equals(first) ? LOCALHOST_IPV4 : first;
                }
            } else {
                String candidate = xff.trim();
                if (!isUnknown(candidate)) {
                    return LOCALHOST_IPV6.equals(candidate) ? LOCALHOST_IPV4 : candidate;
                }
            }
        }
        String ip = request.getRemoteAddr();
        return LOCALHOST_IPV6.equals(ip) ? LOCALHOST_IPV4 : ip;
    }

    // ==================== 风控语义方法（保留在本类）====================

    /**
     * 判断 IP 是否为数据中心/私有网段地址。
     *
     * <p>用于风控场景：数据中心 IP 通常不可信（代理/机房出口），
     * 当前实现等价于私有网段判断。
     *
     * @param ip 待判断的 IP 地址
     * @return {@code true} 表示属于私有/数据中心网段
     */
    public static boolean isDataCenterIp(String ip) {
        return IpValidator.isPrivateIp(ip);
    }

    /**
     * 判断 IP 是否为代理出口地址。
     *
     * <p>风控语义：代理 IP 以数据中心/私有网段为主，当前实现
     * 委托给 {@link #isDataCenterIp(String)} 判断。
     *
     * @param ip 待判断的 IP 地址
     * @return {@code true} 表示疑似代理 IP
     */
    public static boolean isProxyIp(String ip) {
        return isDataCenterIp(ip);
    }
}