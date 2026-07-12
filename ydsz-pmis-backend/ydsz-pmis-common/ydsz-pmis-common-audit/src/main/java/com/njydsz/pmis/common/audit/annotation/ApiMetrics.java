package com.njydsz.pmis.common.audit.annotation;

import java.lang.annotation.*;

/**
 * API 指标监控注解。
 *
 * <p>标注在 Controller 方法上，声明该接口需要纳入指标采集。
 * 切面会自动记录调用次数、耗时、成功率等指标数据，
 * 并暴露到 Micrometer / Prometheus 端点。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @ApiMetrics("opportunity:create")
 * @PostMapping
 * public Result<String> create(@RequestBody OpportunityCreateDTO dto) { ... }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
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
