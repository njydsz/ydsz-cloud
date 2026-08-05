package com.remisoft.common.core.constant;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 过滤器忽略常量类（通用 URL 模式）
 *
 * <p>定义了过滤器需要忽略的 URL 模式，用于：
 * <ul>
 *   <li>公共资源过滤：CSS、JS、图片、字体等静态资源</li>
 *   <li>API 文档过滤：Swagger、OpenAPI 等文档页面</li>
 *   <li>安全相关的排除 URL（登录、认证、验证码等）</li>
 * </ul>
 *
 * <p><b>注意：</b>此常量类仅包含通用 URL 模式，不包含业务特定的服务名列表。
 * 服务名白名单等各模块自定义业务配置请在对应模块（如 remi-common-auth / remi-common-web）中定义，
 * 通过配置文件注入，避免 core 模块承载业务特定信息。</p>
 *
 * <p><b>线程安全性：</b>所有常量集合均为不可变 Set，多线程并发访问安全。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class FilterIgnoreConstant {

    private FilterIgnoreConstant() {
        throw new UnsupportedOperationException("FilterIgnoreConstant is a utility class and cannot be instantiated");
    }

    /** 默认全部忽略的 URL 模式（静态资源 + 文档 + 系统端点） */
    private static final Set<String> COMMON_IGNORE_URL = Collections.unmodifiableSet(Set.of(
            "/**/css/**",
            "/**/js/**",
            "/**/images/**",
            "/**/fonts/**",
            "/**/swagger**/**",
            "/**/webjars/**",
            "/**/v2/api-docs/**",
            "/**/v3/api-docs/**",
            "/**/v3/api-docs.yaml",
            "/**/error",
            "/**/doc.html",
            "/doc.html",
            "/**/swagger-ui/**",
            "/**/swagger-ui.html",
            "/**/favicon.ico",
            "/**/health",
            "/**/actuator/**"
    ));

    /** 安全相关的排除URL模式（登录、认证、验证码等） */
    private static final Set<String> SECURITY_EXCLUDE_URL = Collections.unmodifiableSet(Set.of(
            "/login",
            "/auth/**",
            "/captcha/**"
    ));

    /** 全部排除路径（预计算，避免每次调用 Stream.concat 重建） */
    private static final Set<String> ALL_EXCLUDE_URLS = Stream.concat(
            COMMON_IGNORE_URL.stream(),
            SECURITY_EXCLUDE_URL.stream()
    ).collect(Collectors.toUnmodifiableSet());

    /**
     * 获取过滤器忽略的 URL 模式集合
     *
     * @return URL 模式集合（不可变）
     */
    public static Set<String> getCommonIgnoreUrls() {
        return COMMON_IGNORE_URL;
    }

    /**
     * 获取安全相关的排除 URL 模式集合
     *
     * @return 安全排除 URL 模式集合（不可变）
     */
    public static Set<String> getSecurityExcludeUrls() {
        return SECURITY_EXCLUDE_URL;
    }

    /**
     * 获取全部排除路径（公共静态资源 + 安全排除路径）
     *
     * @return 全部排除路径集合（不可变）
     */
    public static Set<String> getAllExcludeUrls() {
        return ALL_EXCLUDE_URLS;
    }
}
