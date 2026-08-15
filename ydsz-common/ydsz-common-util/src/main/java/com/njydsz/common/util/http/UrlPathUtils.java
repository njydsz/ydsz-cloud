package com.njydsz.common.util.http;

import java.util.Collection;
import java.util.Objects;

import org.springframework.util.AntPathMatcher;

/**
 * URL 路径匹配工具类
 *
 * <p>提供 Ant 风格路径模式匹配能力，供安全过滤器（XSS / CSRF / SQL 注入 / IP 访问控制 /
 * 安全响应头 / API 签名等）与认证过滤器判断请求路径是否命中白名单或排除清单。
 *
 * <p>Ant 风格规则示例：
 * <ul>
 *   <li>{@code /api/**} — 匹配 {@code /api/} 下所有路径</li>
 *   <li>{@code /actuator/*} — 匹配 {@code /actuator/} 下一级路径</li>
 *   <li>{@code /login} — 精确匹配</li>
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class UrlPathUtils {

    /** Ant 路径匹配器（线程安全，无状态可共享） */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private UrlPathUtils() {
    }

    /**
     * 判断路径是否匹配任意一个 Ant 风格模式
     *
     * @param patterns Ant 风格模式集合（null 或空集合返回 false）
     * @param path     请求路径（null 返回 false）
     * @return 任一模式匹配返回 true；全部不匹配或入参为空返回 false
     */
    public static boolean matchAny(Collection<String> patterns, String path) {
        if (patterns == null || patterns.isEmpty() || path == null || path.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern != null && PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 URL 是否在忽略清单中
     *
     * <p>语义同 {@link #matchAny(Collection, String)}，用于认证过滤器的忽略路径判断。
     *
     * @param ignoreUrls 忽略 URL 模式集合（null 或空集合返回 false）
     * @param url        请求 URL 路径（null 返回 false）
     * @return 命中忽略清单返回 true
     */
    public static boolean isIgnoreUrl(Collection<String> ignoreUrls, String url) {
        if (ignoreUrls == null || ignoreUrls.isEmpty() || Objects.isNull(url)) {
            return false;
        }
        return matchAny(ignoreUrls, url);
    }
}
