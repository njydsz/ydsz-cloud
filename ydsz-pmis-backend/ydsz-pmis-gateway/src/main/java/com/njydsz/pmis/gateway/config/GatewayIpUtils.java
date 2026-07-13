package com.njydsz.pmis.gateway.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 网关 IP 工具类（WebFlux 响应式版本）
 *
 * <p>提供从 {@link ServerHttpRequest} 提取客户端真实 IP 以及 IP 白名单校验功能。
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
public final class GatewayIpUtils {

    private static final String UNKNOWN = "unknown";

    private GatewayIpUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 从 WebFlux 请求中提取客户端真实 IP（穿透代理）
     *
     * @param request WebFlux 请求
     * @return 客户端 IP，无法获取时返回空字符串
     */
    public static String getClientIp(ServerHttpRequest request) {
        if (request == null) {
            return "";
        }

        // X-Forwarded-For（可能包含多段，取第一个）
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }

        // X-Real-IP
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        // remote address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "";
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
