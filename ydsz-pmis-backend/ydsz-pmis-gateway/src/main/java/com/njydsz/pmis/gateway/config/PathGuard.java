package com.njydsz.pmis.gateway.config;

import java.util.Set;

/**
 * 路径安全工具类
 *
 * <p>提供路径规范化、白名单匹配和内部头列表功能，防止路径穿越攻击和客户端伪造内部头。
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
public final class PathGuard {

    private PathGuard() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 内部头名称列表（客户端传入时必须剥离） */
    private static final Set<String> INTERNAL_HEADERS = Set.of(
            GatewayConstants.HEADER_TRACE_ID,
            GatewayConstants.HEADER_USER_ID,
            GatewayConstants.HEADER_USERNAME,
            GatewayConstants.HEADER_USER_ROLES,
            GatewayConstants.HEADER_USER_PERMISSIONS,
            GatewayConstants.HEADER_INTERNAL_SIG,
            GatewayConstants.HEADER_INTERNAL_TS,
            GatewayConstants.HEADER_TENANT_ID,
            "X-Forwarded-For",
            "X-Real-IP"
    );

    /**
     * 创建不可修改的白名单集合
     *
     * @param paths 白名单路径
     * @return 不可修改的 Set
     */
    public static Set<String> whiteList(String... paths) {
        return Set.of(paths);
    }

    /**
     * 路径规范化，检测并拦截路径穿越攻击
     *
     * <p>检测 {@code ..}、{@code //}、{@code %2e} 等路径穿越模式。
     *
     * @param rawPath 原始路径
     * @return 规范化后的路径，如果检测到穿越攻击返回 null
     */
    public static String sanitize(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return rawPath;
        }
        // 检测路径穿越攻击
        String lowerPath = rawPath.toLowerCase();
        if (lowerPath.contains("..") ||
                lowerPath.contains("%2e") ||
                lowerPath.contains("//") ||
                lowerPath.contains("\\") ||
                lowerPath.contains("%5c") ||
                lowerPath.contains("%2f")) {
            return null;
        }
        return rawPath;
    }

    /**
     * 精确匹配白名单
     *
     * @param path      请求路径
     * @param whiteList 白名单集合
     * @return true 如果路径完全匹配白名单中的某一项
     */
    public static boolean matchWhiteList(String path, Set<String> whiteList) {
        if (path == null || whiteList == null) {
            return false;
        }
        return whiteList.contains(path);
    }

    /**
     * 返回需要剥离的内部头名称列表
     *
     * @return 内部头名称集合
     */
    public static Set<String> internalHeaders() {
        return INTERNAL_HEADERS;
    }
}
