package com.njydsz.pmis.common.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Set;

/**
 * IP 地址工具（P2-8 安全加固）
 *
 * <p>核心职责:
 * <ol>
 *   <li>IPv4 格式校验</li>
 *   <li>CIDR 表示法匹配（自研轻量实现，不引入 commons-net 等第三方依赖）</li>
 *   <li>从请求头解析客户端真实 IP（X-Forwarded-For → X-Real-IP → RemoteAddr）</li>
 *   <li>统一白名单判定：支持单个 IP 精确匹配与 CIDR 范围匹配</li>
 * </ol>
 *
 * <h3>CIDR 匹配算法</h3>
 * <pre>
 *   1. 将点分十进制 IPv4 转为 32 位无符号整数（用 long 存储）
 *   2. 根据 CIDR 前缀长度计算掩码：mask = 0xFFFFFFFFL &lt;&lt; (32 - prefix)
 *   3. 比较网络部分：(ip &amp; mask) == (network &amp; mask)
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class IpUtils {

    /** X-Forwarded-For 请求头名称 */
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    /** X-Real-IP 请求头名称 */
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    /** IPv4 段数 */
    private static final int IPV4_SEGMENTS = 4;

    /** IPv4 单段最大值 */
    private static final int IPV4_SEGMENT_MAX = 255;

    /** IPv4 最大前缀长度 */
    private static final int IPV4_MAX_PREFIX = 32;

    /** 32 位无符号掩码 */
    private static final long IPV4_MASK = 0xFFFFFFFFL;

    private IpUtils() {
    }

    /**
     * 校验 IPv4 地址格式（点分十进制，4 段，每段 0-255）
     *
     * @param ip 待校验的 IP 字符串
     * @return true 表示合法 IPv4
     */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        String[] segments = ip.split("\\.");
        if (segments.length != IPV4_SEGMENTS) {
            return false;
        }
        for (String seg : segments) {
            if (seg.isEmpty()) {
                return false;
            }
            // 拒绝前导零（如 01、001），避免与八进制歧义
            if (seg.length() > 1 && seg.charAt(0) == '0') {
                return false;
            }
            for (int i = 0; i < seg.length(); i++) {
                char c = seg.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
            }
            int val;
            try {
                val = Integer.parseInt(seg);
            } catch (NumberFormatException e) {
                return false;
            }
            if (val < 0 || val > IPV4_SEGMENT_MAX) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断 IP 是否在 CIDR 范围内
     *
     * <p>支持的格式:
     * <ul>
     *   <li>{@code 192.168.1.0/24} — 标准 CIDR</li>
     *   <li>{@code 192.168.1.1/32} — 等价于单个 IP 精确匹配</li>
     *   <li>{@code 0.0.0.0/0} — 匹配所有 IPv4</li>
     * </ul>
     *
     * @param ip  待判定的 IPv4 地址
     * @param cidr CIDR 表示法（network/prefix）
     * @return true 表示 IP 在 CIDR 范围内；IP 或 CIDR 非法时返回 false
     */
    public static boolean isInRange(String ip, String cidr) {
        if (!isValidIp(ip) || cidr == null || cidr.isEmpty()) {
            return false;
        }
        int slashIdx = cidr.indexOf('/');
        String networkStr;
        int prefix;
        if (slashIdx < 0) {
            // 未带前缀长度，视为单个 IP 精确匹配
            networkStr = cidr;
            prefix = IPV4_MAX_PREFIX;
        } else {
            networkStr = cidr.substring(0, slashIdx);
            String prefixStr = cidr.substring(slashIdx + 1);
            try {
                prefix = Integer.parseInt(prefixStr);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (!isValidIp(networkStr) || prefix < 0 || prefix > IPV4_MAX_PREFIX) {
            return false;
        }
        long ipLong = ipToLong(ip);
        long networkLong = ipToLong(networkStr);
        // /0 时 mask 计算需特殊处理：0xFFFFFFFFL << 32 在 Java 中会溢出
        long mask = prefix == 0 ? 0L : (IPV4_MASK << (IPV4_MAX_PREFIX - prefix)) & IPV4_MASK;
        return (ipLong & mask) == (networkLong & mask);
    }

    /**
     * 统一白名单判定：检查 IP 是否命中白名单
     *
     * <p>白名单条目支持两种格式（自动识别）:
     * <ul>
     *   <li>单个 IP（如 {@code 10.0.0.1}）— 精确匹配</li>
     *   <li>CIDR（如 {@code 192.168.1.0/24}）— 范围匹配</li>
     * </ul>
     *
     * <p>白名单为空或 null 时返回 false（由调用方决定放行策略）。
     *
     * @param ip        待判定的 IPv4 地址
     * @param whitelist 白名单集合
     * @return true 表示 IP 命中白名单
     */
    public static boolean isAllowed(String ip, Set<String> whitelist) {
        if (!isValidIp(ip) || whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        for (String entry : whitelist) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            // 含 / 视为 CIDR，否则视为单个 IP（isInRange 内部兼容无 / 的格式）
            if (isInRange(ip, trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从请求头解析客户端真实 IP
     *
     * <p>优先级:
     * <ol>
     *   <li>{@code X-Forwarded-For} 第一个 IP（取逗号分隔的左起第一段）</li>
     *   <li>{@code X-Real-IP}</li>
     *   <li>{@code request.getRemoteAddress()} 的 host 部分</li>
     * </ol>
     *
     * <p>说明：X-Forwarded-For 可能被客户端伪造，生产环境应在反向代理层
     * 覆盖该头，仅信任最后一跳代理注入的值。
     *
     * @param request 服务器 HTTP 请求（WebFlux reactive）
     * @return 客户端 IP；无法获取时返回空字符串
     */
    public static String getClientIp(ServerHttpRequest request) {
        if (request == null) {
            return "";
        }
        // 1) X-Forwarded-For 第一个 IP
        String xff = request.getHeaders().getFirst(HEADER_X_FORWARDED_FOR);
        if (xff != null && !xff.isEmpty()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return normalize(first);
            }
        }
        // 2) X-Real-IP
        String xRealIp = request.getHeaders().getFirst(HEADER_X_REAL_IP);
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return normalize(xRealIp.trim());
        }
        // 3) RemoteAddr
        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return normalize(remoteAddress.getAddress().getHostAddress());
        }
        return "";
    }

    /**
     * 将 IPv4 点分十进制转为 32 位无符号 long
     *
     * @param ip 合法的 IPv4 地址
     * @return 32 位无符号整数（long 存储）
     */
    private static long ipToLong(String ip) {
        String[] segments = ip.split("\\.");
        long result = 0L;
        for (String seg : segments) {
            result = (result << 8) | (Integer.parseInt(seg) & 0xFFL);
        }
        return result;
    }

    /**
     * 规范化 IP 字符串：去除 IPv6 环绕（如 ::ffff:）与方括号
     *
     * @param ip 原始 IP 字符串
     * @return 规范化后的 IPv4 字符串
     */
    private static String normalize(String ip) {
        if (ip == null) {
            return "";
        }
        String s = ip.trim();
        // 去除 IPv6 方括号
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        // 去除 IPv4-mapped IPv6 前缀（如 ::ffff:192.168.1.1）
        int colonIdx = s.lastIndexOf(':');
        if (colonIdx >= 0 && s.indexOf('.') > colonIdx) {
            s = s.substring(colonIdx + 1);
        }
        return s;
    }
}
