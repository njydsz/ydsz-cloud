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
 *   <li>类级别：{@code @ApiVersion("26.09.01")} 表示整个 Controller 的版本</li>
 *   <li>方法级别：覆盖类级别的版本标记（用于接口粒度的灰度）</li>
 * </ul>
 *
 * <p>默认值 {@code "26.09.01"} 为当前项目首版，符合 YY.MM.DD 版本规范。
 *
 * <p>废弃策略：接口标记为弃用时，应先标记 {@link Deprecated} + {@code @ApiVersion(deprecated = true)}，
 * 待观察期（通常 2-3 个版本）后才可物理下线。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiVersion {

  /**
   * 版本号（项目版本格式 YY.MM.DD）。
   *
   * <p>默认值为当前项目首版 {@code "26.09.01"}。
   *
   * @return 版本号
   */
  String value() default "26.09.01";

  /**
   * 是否已废弃（仍可用但不应新增依赖）。
   *
   * <p>废弃接口的返回响应中，网关会追加 {@code Warning: 299 - "API is deprecated"} 头。
   *
   * @return {@code true} 表示已废弃
   */
  boolean deprecated() default false;

  /**
   * 可选的 sunset 日期（ISO-8601），表示该版本预计下线的时间。
   *
   * <p>示例：{@code "2026-12-31"}。到期后网关会返回 410 Gone。
   *
   * @return sunset 日期
   */
  String sunset() default "";
}
