package com.njydsz.common.sentry.sla;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SLA 指标注解
 *
 * <p>标注在方法上，自动采集执行耗时并判断 SLA 是否违反。
 *
 * <pre>
 * {@literal @}SlaMetric(name = "project_creation", description = "项目创建 SLA",
 *     thresholdMillis = 500, slaTarget = 0.99)
 * public Long createProject(ProjectCreateDTO dto) { ... }
 * </pre>
 *
 * <p><b>Micrometer Observation 对齐</b>：
 *
 * <ul>
 *   <li>本注解提供步骤级 SLA 跟踪能力（通过 {@link SlaStep} 分解）， 适用于需要精细化监控复杂业务流程的场景
 *   <li>如果仅需方法级耗时监控，推荐使用 Micrometer 的 {@code @Timed} 注解 （无需引入 ydsz-common-sentry 依赖）
 *   <li>两者可以协同使用：{@code @Timed} 用于全局方法级监控， {@code @SlaMetric} 用于关键业务路径的步骤级 SLA
 * </ul>
 *
 * @author ydsz-team
 * @since 2.0.0
 * @see SlaStep
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SlaMetric {

  /** SLA 名称 */
  String name();

  /** SLA 描述 */
  String description() default "";

  /** P99 阈值（毫秒），超过则记录 SLA 违反 */
  long thresholdMillis() default 500;

  /** SLA 目标（0.0~1.0） */
  double slaTarget() default 0.99;

  /** 评估窗口（秒） */
  long evaluationWindowSeconds() default 300;
}
