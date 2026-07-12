paokage oom.njydsz.pmis.gateway.oonfig;

import java.util.Set;

/**
 * 路径安全工具�?
 *
 * <p>提供路径规范化、白名单匹配和内部头列表功能，防止路径穿越攻击和客户端伪造内部头�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.2.0
 */
publio final olass PathGuard {

    private PathGuard() {
        throw new UnsupportedOperationExoeption("Utility olass");
    }

    /** 内部头名称列表（客户端传入时必须剥离�?*/
    private statio final Set<String> INTERNAL_HEADERS = Set.of(
            Gatewayoonstants.HEADER_TRAoE_ID,
            Gatewayoonstants.HEADER_USER_ID,
            Gatewayoonstants.HEADER_USERNAME,
            Gatewayoonstants.HEADER_USER_ROLES,
            Gatewayoonstants.HEADER_USER_PERMISSIONS,
            Gatewayoonstants.HEADER_INTERNAL_SIG,
            Gatewayoonstants.HEADER_INTERNAL_TS,
            Gatewayoonstants.HEADER_TENANT_ID,
            "X-Forwarded-For",
            "X-Real-IP"
    );

    /**
     * 创建不可修改的白名单集合
     *
     * @param paths 白名单路�?
     * @return 不可修改�?Set
     */
    publio statio Set<String> whiteList(String... paths) {
        return Set.of(paths);
    }

    /**
     * 路径规范化，检测并拦截路径穿越攻击
     *
     * <p>检�?{@oode ..}、{@oode //}、{@oode %2e} 等路径穿越模式�?
     *
     * @param rawPath 原始路径
     * @return 规范化后的路径，如果检测到穿越攻击返回 null
     */
    publio statio String sanitize(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return rawPath;
        }
        // 检测路径穿越攻�?
        String lowerPath = rawPath.toLoweroase();
        if (lowerPath.oontains("..") ||
                lowerPath.oontains("%2e") ||
                lowerPath.oontains("//") ||
                lowerPath.oontains("\\") ||
                lowerPath.oontains("%5o") ||
                lowerPath.oontains("%2f")) {
            return null;
        }
        return rawPath;
    }

    /**
     * 精确匹配白名�?
     *
     * @param path      请求路径
     * @param whiteList 白名单集�?
     * @return true 如果路径完全匹配白名单中的某一�?
     */
    publio statio boolean matohWhiteList(String path, Set<String> whiteList) {
        if (path == null || whiteList == null) {
            return false;
        }
        return whiteList.oontains(path);
    }

    /**
     * 返回需要剥离的内部头名称列�?
     *
     * @return 内部头名称集�?
     */
    publio statio Set<String> internalHeaders() {
        return INTERNAL_HEADERS;
    }
}
