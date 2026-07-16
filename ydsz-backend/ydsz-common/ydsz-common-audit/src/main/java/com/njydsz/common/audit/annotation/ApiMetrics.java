package com.njydsz.common.audit.annotation;

import java.lang.annotation.*;

/**
 * API 指标监控注解。
 *
 * <p>标注在 Controller 方法上，声明该接口需要纳入指标采集。
 *
 * <p><b>注意：</b>该注解当前未实现对应的 AOP 切面，
 * 指标采集请直接使用 Spring Boot Actuator + Micrometer 的 {@code @Timed} 注解替代。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @ApiMetrics("opportunity:create")
 * @PostMapping
 * public Result<String> create(@RequestBody OpportunityCreateDTO dto) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 该注解当前未实现对应的 AOP 切面，
 *             请使用 Spring Boot Actuator + Micrometer 的 {@code @Timed} 注解替代。
 */
@Deprecated
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiMetrics {

    /**
     * 指标名称，用作 Micrometer tag。
     *
     * @return 指标名称
     */
    String value();
}
