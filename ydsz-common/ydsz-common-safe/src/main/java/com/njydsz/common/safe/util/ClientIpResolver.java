package com.njydsz.common.safe.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.util.ip.CidrUtils;

/**
 * 客户端 IP 解析工具
 *
 * <p>统一从 HTTP 请求中解析客户端真实 IP，支持多级反向代理场景。
 * 所有安全 Filter 统一使用此类获取客户端 IP，避免逻辑不一致导致的安全漏洞。
 *
 * <p><b>安全策略（可信代理校验）：</b>
 * <ol>
 *   <li>获取直连 IP（{@code request.getRemoteAddr()}，不可伪造）</li>
 *   <li>如果直连 IP 是可信代理（本地回环或内网私有地址），才信任 {@code X-Forwarded-For} / {@code X-Real-IP}</li>
 *   <li>否则直接使用直连 IP</li>
 * </ol>
 * 这样可以防止外部客户端伪造 {@code X-Forwarded-For} 绕过 IP 限流或 IP 黑白名单。
 *
 * <p><b>可信代理范围：</b>
 * <ul>
 *   <li>本地回环：127.0.0.0/8, ::1</li>
 *   <li>内网私有：10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16</li>
 *   <li>Docker 默认网段：172.17.0.0/16</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);
    private static final String UNKNOWN = "unknown";
    private static final String DEFAULT_IP = "0.0.0.0";

    /**
     * 可信代理的 CIDR 网段定义（P2-4：与 ydsz-common-util 的 CidrUtils 对齐）。
     *
     * <p>包含：
     * <ul>
     *   <li>IPv4 回环：127.0.0.0/8</li>
     *   <li>IPv4 私有地址（RFC 1918）：10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16</li>
     *   <li>IPv6 回环：::1/128</li>
     * </ul>
     *
     * <p>注意：Docker 默认网段 172.17.0.0/16 已被 172.16.0.0/12 覆盖。
     */
    private static final String[] TRUSTED_PROXY_CIDRS = {
            "127.0.0.0/8",
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "::1/128"
    };

    /** 可信代理判断缓存（IP → isTrusted），减少高频调用时的 CIDR 计算开销 */
    private static final ConcurrentMap<String, Boolean> TRUSTED_PROXY_CACHE = new ConcurrentHashMap<>(128);

    private ClientIpResolver() {
    }

    /**
     * 从 HTTP 请求中解析客户端真实 IP（含可信代理校验）
     *
     * <p>判断逻辑：
     * <ol>
     *   <li>获取直连 IP（request.getRemoteAddr()，不可伪造）</li>
     *   <li>如果直连 IP 是可信代理（本地回环或内网私有地址），才信任 X-Forwarded-For / X-Real-IP</li>
     *   <li>否则直接使用直连 IP</li>
     * </ol>
     * 这样可以防止外部客户端伪造 X-Forwarded-For 绕过 IP 限流。
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        return resolveFromHeaders(
                request.getRemoteAddr(),
                request.getHeader(HeaderConstants.X_FORWARDED_FOR),
                request.getHeader("X-Real-IP"));
    }

    /**
     * 从直连 IP 和代理头中解析客户端真实 IP（框架无关版本）。
     *
     * <p>本方法供 Servlet 和 WebFlux 两种栈共用，消除重复的 X-Forwarded-For 解析逻辑。
     * 判断逻辑与 {@link #getClientIp(HttpServletRequest)} 完全一致。
     *
     * @param directIp       直连 IP（不可伪造，由 TCP 连接获得）
     * @param xForwardedFor  X-Forwarded-For 头值（可为 null）
     * @param xRealIp        X-Real-IP 头值（可为 null）
     * @return 客户端真实 IP
     * @since 1.1.0
     */
    public static String resolveFromHeaders(String directIp, String xForwardedFor, String xRealIp) {
        if (directIp != null && isTrustedProxy(directIp)) {
            if (StringUtils.hasText(xForwardedFor) && !UNKNOWN.equalsIgnoreCase(xForwardedFor)) {
                int index = xForwardedFor.indexOf(',');
                if (index != -1) {
                    return xForwardedFor.substring(0, index).trim();
                }
                return xForwardedFor.trim();
            }
            if (StringUtils.hasText(xRealIp) && !UNKNOWN.equalsIgnoreCase(xRealIp)) {
                return xRealIp.trim();
            }
        }
        return directIp != null && !directIp.isEmpty() ? directIp : DEFAULT_IP;
    }

    /**
     * 判断 IP 是否为可信代理（P2-4：使用 CIDR 网段匹配替代手工前缀判断）。
     *
     * <p>可信代理包括：
     * <ul>
     *   <li>本地回环：127.0.0.0/8, ::1/128</li>
     *   <li>内网私有（RFC 1918）：10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16</li>
     * </ul>
     *
     * <p><b>P2-4 设计说明：</b>
     * <ul>
     *   <li>原实现使用手工字符串前缀匹配（startsWith "10." / "192.168." / "172." + 解析第二字节），
     *       与 {@link com.njydsz.common.util.ip.CidrUtils} 的 CIDR 能力重叠</li>
     *   <li>现统一委托 {@link CidrUtils#isInRange(String, String)}，获得标准 CIDR 计算能力</li>
     *   <li>加入 {@link ConcurrentHashMap} 缓存（key=IP），减少高频调用时的重复计算</li>
     * </ul>
     *
     * @param ip IP 地址
     * @return true 为可信代理
     */
    public static boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        Boolean cached = TRUSTED_PROXY_CACHE.get(ip);
        if (cached != null) {
            return cached;
        }
        boolean result = computeIsTrustedProxy(ip);
        TRUSTED_PROXY_CACHE.putIfAbsent(ip, result);
        return result;
    }

    /**
     * 实际计算是否为可信代理（CIDR 网段匹配）。
     */
    private static boolean computeIsTrustedProxy(String ip) {
        // IPv6 特殊形式（"0:0:0:0:0:0:0:1"）需归一化为 "::1"
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            return true;
        }
        for (String cidr : TRUSTED_PROXY_CIDRS) {
            if (CidrUtils.isInRange(ip, cidr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 IP 是否为内网地址
     *
     * @param ip IP 地址
     * @return true 为内网地址
     */
    public static boolean isInternalIp(String ip) {
        return isTrustedProxy(ip);
    }
}
