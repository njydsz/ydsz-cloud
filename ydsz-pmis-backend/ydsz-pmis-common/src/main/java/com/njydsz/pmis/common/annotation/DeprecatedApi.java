package com.njydsz.pmis.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 版本废弃标记（P2-5：API 版本废弃策略）
 *
 * <p>标注在 Controller 方法上，自动在响应中添加 {@code Deprecation} 和 {@code Sunset} HTTP 头。
 * 配合 {@link com.njydsz.pmis.common.web.ApiVersionController} 使用。
 *
 * <p>使用示例：
 * <pre>
 * &#64;DeprecatedApi(since = "v2", sunset = "2027-01-01", alternative = "/api/v2/users")
 * public Result&lt;UserVO&gt; getUserV1(Long id) { ... }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeprecatedApi {

    /**
     * 标记为废弃的版本号
     */
    String since() default "";

    /**
     * 计划移除日期（ISO 8601，如 "2027-01-01"）
     */
    String sunset() default "";

    /**
     * 替代 API 路径
     */
    String alternative() default "";

    /**
     * 废弃原因
     */
    String reason() default "";
}