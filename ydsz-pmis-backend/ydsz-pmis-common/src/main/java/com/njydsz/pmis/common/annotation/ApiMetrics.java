package com.njydsz.pmis.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 耗时监控注解（P2-2：API 响应时间 P99 监控）
 *
 * <p>标注在 Controller 方法上，自动记录方法执行耗时到日志和 Prometheus Metrics。
 * 配合 {@link com.njydsz.pmis.common.aspect.ApiMetricsAspect} 使用。
 *
 * <p>使用示例：
 * <pre>
 * &#64;ApiMetrics("contract:page")
 * public PageResult&lt;ContractVO&gt; page(PageQuery query) { ... }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiMetrics {

    /**
     * 指标名称（用于 Prometheus metric name）
     */
    String value() default "";
}