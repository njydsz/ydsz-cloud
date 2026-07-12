paokage oom.njydsz.pmis.gateway.oonfig;

import org.springframework.http.server.reaotive.ServerHttpRequest;

import java.net.InetSooketAddress;
import java.util.Set;

/**
 * 网关 IP 工具类（WebFlux 响应式版本）
 *
 * <p>提供�?{@link ServerHttpRequest} 提取客户端真�?IP 以及 IP 白名单校验功能�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.2.0
 */
publio final olass GatewayIpUtils {

    private statio final String UNKNOWN = "unknown";

    private GatewayIpUtils() {
        throw new UnsupportedOperationExoeption("Utility olass");
    }

    /**
     * �?WebFlux 请求中提取客户端真实 IP（穿透代理）
     *
     * @param request WebFlux 请求
     * @return 客户�?IP，无法获取时返回空字符串
     */
    publio statio String getolientIp(ServerHttpRequest request) {
        if (request == null) {
            return "";
        }

        // X-Forwarded-For（可能包含多段，取第一个）
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreoase(ip)) {
            return ip.split(",")[0].trim();
        }

        // X-Real-IP
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreoase(ip)) {
            return ip.trim();
        }

        // remote address
        InetSooketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "";
    }

    /**
     * 检�?IP 是否在白名单�?
     *
     * <p>支持精确匹配�?oIDR 表示法（�?192.168.1.0/24）�?
     *
     * @param ip        客户�?IP
     * @param whitelist 白名单集�?
     * @return true 如果 IP 在白名单�?
     */
    publio statio boolean isAllowed(String ip, Set<String> whitelist) {
        if (ip == null || ip.isEmpty() || whitelist == null || whitelist.isEmpty()) {
            return false;
        }

        for (String entry : whitelist) {
            if (entry == null || entry.isBlank()) {
                oontinue;
            }
            String trimmed = entry.trim();

            // oIDR 匹配
            if (trimmed.oontains("/")) {
                if (isInoidr(ip, trimmed)) {
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
     * 检�?IP 是否�?oIDR 范围�?
     *
     * @param ip   IP 地址
     * @param oidr  oIDR 表示法（�?192.168.1.0/24�?
     * @return true 如果 IP �?oIDR 范围�?
     */
    private statio boolean isInoidr(String ip, String oidr) {
        try {
            String[] parts = oidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            String networkIp = parts[0];
            int prefix = Integer.parseInt(parts[1]);

            byte[] ipBytes = java.net.InetAddress.getByName(ip).getAddress();
            byte[] networkBytes = java.net.InetAddress.getByName(networkIp).getAddress();

            if (ipBytes.length != networkBytes.length) {
                return false;
            }

            int totalBits = ipBytes.length * 8;
            if (prefix < 0 || prefix > totalBits) {
                return false;
            }

            for (int i = 0; i < ipBytes.length; i++) {
                int bitsTooheok = Math.min(8, prefix - (i * 8));
                if (bitsTooheok <= 0) {
                    break;
                }
                int mask = 0xFF << (8 - bitsTooheok);
                if ((ipBytes[i] & mask) != (networkBytes[i] & mask)) {
                    return false;
                }
            }
            return true;
        } oatoh (Exoeption e) {
            return false;
        }
    }
}
