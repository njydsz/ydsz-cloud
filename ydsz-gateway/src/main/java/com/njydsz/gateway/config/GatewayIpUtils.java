package com.njydsz.gateway.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

import org.springframework.http.server.reactive.ServerHttpRequest;

import com.njydsz.common.safe.util.ClientIpResolver;

/**
 * 网关 IP 工具类（WebFlux 响应式版本）
 *
 * <p>提供从 {@link ServerHttpRequest} 提取客户端真实 IP 以及 IP 白名单校验功能。
 *
 * <h3>P0-3 增强：可信代理链校验</h3>
 * <p>复用 {@link ClientIpResolver#isTrustedProxy(String)} 进行可信代理校验，
 * 仅当直连 IP 是可信代理（本地回环或内网私有地址）时才信任 {@code X-Forwarded-For} /
 * {@code X-Real-IP}。这样可以防止外部客户端伪造 X-Forwarded-For 绕过 IP 限流或 IP 黑白名单。
 *
 * <p><b>注意：</b>WebFlux 栈不能直接复用 {@link ClientIpResolver#getClientIp(HttpServletRequest)}，
 * 因为后者依赖 Servlet API。本类对应 WebFlux 的 {@link ServerHttpRequest} 做了等价实现，
 * 但可信代理判定逻辑完全复用 ydsz-common-safe 中的 {@link ClientIpResolver#isTrustedProxy}，
 * 保持单一来源一致。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class GatewayIpUtils {

    private static final String UNKNOWN = "unknown";
    private static final String DEFAULT_IP = "0.0.0.0";

    /** 可信代理头：X-Forwarded-For */
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    /** 可信代理头：X-Real-IP */
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private GatewayIpUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 从 WebFlux 请求中提取客户端真实 IP（含可信代理链校验）
     *
     * <p>判断逻辑：
     * <ol>
     *   <li>获取直连 IP（request.getRemoteAddress()，不可伪造）</li>
     *   <li>如果直连 IP 是可信代理（{@link ClientIpResolver#isTrustedProxy}），
     *       才信任 {@code X-Forwarded-For} / {@code X-Real-IP}</li>
     *   <li>否则直接使用直连 IP</li>
     * </ol>
     * 这样可以防止外部客户端伪造 X-Forwarded-For 绕过 IP 限流或 IP 黑白名单。
     *
     * @param request WebFlux 请求
     * @return 客户端 IP，无法获取时返回 {@link #DEFAULT_IP}
     */
    public static String getClientIp(ServerHttpRequest request) {
        if (request == null) {
            return DEFAULT_IP;
        }
        String directIp = resolveDirectIp(request);
        return ClientIpResolver.resolveFromHeaders(
                directIp,
                request.getHeaders().getFirst(HEADER_X_FORWARDED_FOR),
                request.getHeaders().getFirst(HEADER_X_REAL_IP));
    }

    /**
     * 从 WebFlux 请求的 RemoteAddress 中解析直连 IP
     *
     * @param request WebFlux 请求
     * @return 直连 IP，无法获取时返回 null
     */
    private static String resolveDirectIp(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return null;
    }

    /**
     * 检查 IP 是否在白名单中
     *
     * <p>支持精确匹配和 CIDR 表示法（如 192.168.1.0/24）。
     *
     * @param ip        客户端 IP
     * @param whitelist 白名单集合
     * @return true 如果 IP 在白名单中
     */
    public static boolean isAllowed(String ip, Set<String> whitelist) {
        if (ip == null || ip.isEmpty() || whitelist == null || whitelist.isEmpty()) {
            return false;
        }

        for (String entry : whitelist) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();

            // CIDR 匹配
            if (trimmed.contains("/")) {
                if (isInCidr(ip, trimmed)) {
                    return true;
                }
            } else if (trimmed.equals(ip)) {
                // 精确匹配
                return true;
            }
        }
        return false;
    }

    /**
     * 检查 IP 是否在 CIDR 范围内
     *
     * @param ip   IP 地址
     * @param cidr  CIDR 表示法（如 192.168.1.0/24）
     * @return true 如果 IP 在 CIDR 范围内
     */
    private static boolean isInCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            String networkIp = parts[0];
            int prefix = Integer.parseInt(parts[1]);

            byte[] ipBytes = InetAddress.getByName(ip).getAddress();
            byte[] networkBytes = InetAddress.getByName(networkIp).getAddress();

            if (ipBytes.length != networkBytes.length) {
                return false;
            }

            int totalBits = ipBytes.length * 8;
            if (prefix < 0 || prefix > totalBits) {
                return false;
            }

            for (int i = 0; i < ipBytes.length; i++) {
                int bitsToCheck = Math.min(8, prefix - (i * 8));
                if (bitsToCheck <= 0) {
                    break;
                }
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
}
