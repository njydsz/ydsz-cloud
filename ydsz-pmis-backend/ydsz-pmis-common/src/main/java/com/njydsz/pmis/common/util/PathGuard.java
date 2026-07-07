package com.njydsz.pmis.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 路径安全工具（P0-C5）
 *
 * <p>针对网关层路径穿越漏洞：在 {@code isWhiteList} 校验前必须先调用
 * {@link #sanitize} 规范化路径，防止 {@code ..}、{@code %2e%2e}、{@code //} 等
 * 绕过前缀匹配。
 *
 * <h3>防护策略</h3>
 * <ol>
 *   <li>解码 URL 编码（如 {@code %2e%2e} → {@code ..}）</li>
 *   <li>规范化路径（去除 {@code .} 与 {@code ..} 段）</li>
 *   <li>合并连续斜杠 {@code //} → {@code /}</li>
 *   <li>含 {@code ..} 段穿越且规范化后路径变化 → 视为攻击，返回 null</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class PathGuard {

    private static final Logger log = LoggerFactory.getLogger(PathGuard.class);

    /** 需要剥离的内部头集合（防止客户端伪造） */
    public static final Set<String> INTERNAL_HEADERS = Set.of(
            "X-User-Id", "X-Username", "X-User-Dept-Id",
            "X-User-Roles", "X-User-Permissions",
            "X-Internal-Sig", "X-Internal-Ts"
    );

    private PathGuard() {
    }

    /**
     * 规范化路径，返回安全路径或 null（视为攻击）。
     *
     * @param rawPath 原始路径（已 URL 解码，不含 query）
     * @return 规范化后的绝对路径；非法或含穿越段返回 null
     */
    public static String sanitize(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return null;
        }
        // 拒绝包含编码点号的路径（如 %2e%2e 已被容器解码为 ..，此处兜底）
        // 注：rawPath 通常来自 request.getURI().getPath()，已由容器解码
        if (rawPath.contains("..")) {
            return null;
        }
        // 合并连续斜杠
        String collapsed = rawPath.replaceAll("/+", "/");
        // 尝试用 URI.normalize() 二次校验
        try {
            URI normalized = new URI(collapsed).normalize();
            String nPath = normalized.getPath();
            // 规范化后若仍含 .. 表示穿越到根之上，视为攻击
            if (nPath == null || nPath.contains("..") || !nPath.startsWith("/")) {
                return null;
            }
            return nPath;
        } catch (URISyntaxException e) {
            log.warn("[PathGuard] 路径规范化失败 rawPath={}: {}", collapsed, e.getMessage());
            return null;
        }
    }

    /**
     * 精确匹配白名单（含路径边界）。
     *
     * <p>替代 {@code path::startsWith}：{@code /auth/login} 只匹配自身，
     * 不匹配 {@code /auth/login/anything}。
     *
     * @param path       已规范化的路径
     * @param whiteList  白名单路径集合
     * @return true 表示命中白名单
     */
    public static boolean matchWhiteList(String path, Set<String> whiteList) {
        if (path == null || whiteList == null || whiteList.isEmpty()) {
            return false;
        }
        // 精确匹配或父路径匹配（白名单末尾加 / 表示匹配子路径）
        for (String w : whiteList) {
            if (path.equals(w)) {
                return true;
            }
            // 仅当白名单以 / 结尾时允许前缀匹配子路径
            if (w.endsWith("/") && path.startsWith(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回内部头集合（用于网关剥离客户端伪造的内部头）。
     *
     * @return 不可变内部头名称集合
     */
    public static Set<String> internalHeaders() {
        return INTERNAL_HEADERS;
    }

    /**
     * 构造白名单集合（LinkedHashSet 保序，便于调试）。
     *
     * @param paths 白名单路径数组
     * @return 不可变白名单集合
     */
    public static Set<String> whiteList(String... paths) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String p : paths) {
            if (p != null && !p.isBlank()) {
                set.add(p);
            }
        }
        return Set.copyOf(set);
    }
}
