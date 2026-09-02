package com.njydsz.common.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 版本注解 —— 标记 Controller 或方法所属的语义化版本。
 *
 * <p>用法（path-level 版本控制）：
 * <ul>
 *   <li>类级别：{@code @ApiVersion("v2")} 表示整个 Controller 已升级到 v2</li>
 *   <li>方法级别：覆盖类级别的版本标记（用于接口粒度的灰度）</li>
 * </ul>
 *
 * <p>配套路径改造示例：
 * <pre>
 * +-- /api/v1/user  (旧版，稳定)
 * +-- /api/v2/user  (新版，享新能力)
 * </pre>
 *
 * <p>请求既可通过 path（首选）也可通过 Header（{@code X-Api-Version: v2}）指定。
 * 网关 {@code ydsz-gateway} 按 header 重写 path，将 version 信息路由到对应服务实例。
 *
 * <p>废弃策略：接口标记为弃用时，应先标记 {@link Deprecated} + {@code @ApiVersion(deprecated = true)}，
 * 待观察期（通常 2-3 个版本）后才可物理下线。此信息会通过 OpenAPI springdoc 输出至
 * {@code x-api-version} / {@code deprecated} 字段，前端 CI 自动生成 SDK 时检测并告警。
 *
 * @path ydsz-common/ydsz-common-api/src/main/java/com/njydsz/common/api/annotation/ApiVersion.java
 * @author ydsz-team
 * @since 4.1.0 (P2-11)
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiVersion {

  /**
   * 语义化版本号（如 "v1"、"v2"）。
   *
   * <p>未指定时由网关 fallback 为 {@code v1}（项目默认版本）。
   */
  String value() default "v1";

  /**
   * 是否已废弃（仍可用但不应新增依赖）。
   *
   * <p>废弃接口的返回响应中，网关会追加 {@code Warning: 299 - "API is deprecated"} 头，
   * 前端 SDK 生成时也会输出 ESLint 弃用标注，编译期即被识别。
   */
  boolean deprecated() default false;

  /**
   * 可选的 sunset 日期（ISO-8601），表示该版本预计下线的时间。
   *
   * <p>示例：{@code "2026-12-31"}。到期后网关会返回 410 Gone。
   */
  String sunset() default "";
}
