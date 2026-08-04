package com.remisoft.common.sentry.sla;

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
 * @author remi-team
 * @since 1.0.0
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
