package com.remisoft.common.sentry.sla;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SLA 步骤注解
 *
 * <p>标注在方法上，作为 SLA 的一个步骤进行采集。
 * 需配合 {@link SlaMetric} 使用。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SlaStep {

    /** 步骤名 */
    String name();

    /** 超时阈值（毫秒） */
    long timeoutMillis() default 200;

    /** 是否关键步骤（失败则整体 SLA 违反） */
    boolean critical() default true;
}
