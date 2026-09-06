package com.njydsz.gateway.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 弃用标记注解。
 *
 * <p>用于在 Controller 方法或类上标记某个 API 已被弃用。网关运行时结合 {@code
 * com.njydsz.gateway.config.ApiDeprecationManager} 自动在响应中注入 Deprecation 警告头。
 *
 * <p>标注后，客户端将收到以下响应头：
 *
 * <pre>
 *   Deprecation: @v1
 *   Sunset: &lt;移除日期（RFC 1123）&gt;
 *   Link: &lt;replacement&gt;; rel="successor-version"
 *   X-API-Deprecation-Message: &lt;自定义说明&gt;
 * </pre>
 *
 * <p>使用示例：
 *
 * <pre>
 * &#64;DeprecatedApi(since = "v1", replacement = "v2", removalDate = "2026-12-31",
 *     message = "请使用 /api/v2/message/send 替代")
 * &#64;GetMapping("/api/v1/message/send")
 * public Mono&lt;Void&gt; sendMessage() { ... }
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.06
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeprecatedApi {
  /**
   * 弃用版本号（如 "v1"）。
   *
   * @return 弃用版本号
   */
  String since() default "";

  /**
   * 替代版本（如 "v2" 或完整路径 "/api/v2/message/send"）。
   *
   * @return 替代版本
   */
  String replacement() default "";

  /**
   * 移除时间（预计），格式同 HTTP-Date（如 "2026-12-31"）。
   *
   * @return 移除时间
   */
  String removalDate() default "";

  /**
   * 自定义弃用说明。
   *
   * @return 弃用说明
   */
  String message() default "";
}
