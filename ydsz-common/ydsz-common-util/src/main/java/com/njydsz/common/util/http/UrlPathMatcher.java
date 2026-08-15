package com.njydsz.common.util.http;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.util.AntPathMatcher;

/**
 * 可复用的高性能 URL 路径匹配器。
 *
 * <p>相比 {@link UrlPathUtils#matchAny(Collection, String)} 的每次线性扫描，本类在构建时：
 * <ul>
 *   <li>将精确匹配模式（如 {@code /login}）提取到 {@link HashSet}，查找 O(1)</li>
 *   <li>仅对非精确模式保留 {@link AntPathMatcher} 列表，避免精确路径走正则解析</li>
 * </ul>
 *
 * <p>适用场景：白名单路径频繁匹配（如安全过滤器、认证拦截器），patterns 数量 10~100+。
 *
 * <p><b>线程安全：</b>构建后不可变（immutable），可安全用于多线程环境。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 构建一次，复用多次（推荐在 @Bean 或 static 字段中构建）
 * UrlPathMatcher matcher = UrlPathMatcher.of(List.of(
 *     "/api/public/**", "/login", "/health", "/swagger-ui/**"
 * ));
 *
 * // 匹配判断
 * boolean allowed = matcher.matches(requestPath);
 * boolean exactExact = matcher.matchesExact(requestPath);
 * }</pre>
 *
 * @since 2.2.0
 * @author ydsz-team
 */
public final class UrlPathMatcher {

    /** Ant 通配符模式匹配器（线程安全，仅用于含通配符的模式） */
    private static final AntPathMatcher ANT_MATCHER = new AntPathMatcher();

    /** 精确匹配模式集合（查找 O(1)，无通配符） */
    private final Set<String> exactPatterns;

    /** 含通配符的模式列表（顺序匹配，命中即返回） */
    private final List<String> wildcardPatterns;

    /**
     * 私有构造器，通过 {@link #of(Collection)} 工厂方法创建。
     *
     * @param exactPatterns    精确匹配模式集合
     * @param wildcardPatterns 含通配符的模式列表
     * @return 处理后的结果
     */
    private UrlPathMatcher(Set<String> exactPatterns, List<String> wildcardPatterns) {
        this.exactPatterns = Collections.unmodifiableSet(exactPatterns);
        this.wildcardPatterns = Collections.unmodifiableList(wildcardPatterns);
    }

    /**
     * 根据模式集合构建匹配器。
     *
     * <p>构造时将模式分为两类：
     * <ul>
     *   <li>精确模式（不含 * ? **）：放入 HashSet，查找 O(1)</li>
     *   <li>通配符模式：保留在 List，匹配时顺序遍历</li>
     * </ul>
     *
     * @param patterns Ant 风格模式集合（可为 null 或空）
     * @return 构建后的 UrlPathMatcher 实例（永不为 null）
     */
    public static UrlPathMatcher of(Collection<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return new UrlPathMatcher(Collections.emptySet(), Collections.emptyList());
        }

        Set<String> exactSet = new HashSet<>();
        List<String> wildcardList = new ArrayList<>();
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            if (isWildcardPattern(pattern)) {
                wildcardList.add(pattern);
            } else {
                exactSet.add(pattern);
            }
        }
        return new UrlPathMatcher(exactSet, wildcardList);
    }

    /**
     * 匹配路径（先精确查找，后通配符匹配）。
     *
     * @param path 请求路径（null 返回 false）
     * @return 命中任一模式返回 true
     */
    public boolean matches(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // O(1) 精确匹配
        if (exactPatterns.contains(path)) {
            return true;
        }
        // 通配符匹配（顺序命中即返）
        for (String pattern : wildcardPatterns) {
            if (ANT_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否精确匹配（不含通配符模式命中）。
     *
     * @param path 请求路径（null 返回 false）
     * @return 精确模式命中返回 true
     */
    public boolean matchesExact(String path) {
        return path != null && !path.isEmpty() && exactPatterns.contains(path);
    }

    /**
     * 是否含有通配符模式。
     *
     * @return 包含通配符模式返回 true
     */
    public boolean hasWildcardPatterns() {
        return !wildcardPatterns.isEmpty();
    }

    /**
     * 获取精确匹配模式集合（不可变视图）。
     *
     * @return 精确模式集合
     */
    public Set<String> getExactPatterns() {
        return exactPatterns;
    }

    /**
     * 获取通配符模式列表（不可变视图）。
     *
     * @return 通配符模式列表
     */
    public List<String> getWildcardPatterns() {
        return wildcardPatterns;
    }

    /**
     * 判断模式是否包含通配符。
     *
     * <p>AntPathMatcher 的通配符包括 {@code *}、{@code ?}、{@code **}。
     *
     * @param pattern 模式字符串
     * @return 包含通配符返回 true
     */
    private static boolean isWildcardPattern(String pattern) {
        return pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0;
    }
}






















