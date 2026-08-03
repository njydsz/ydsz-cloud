package com.njydsz.common.core.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.trace.TraceIdGenerator;

/**
 * Core 模块健康指标。
 *
 * <p>向 Spring Boot Actuator 暴露核心模块的运行状态，遵循 Spring Boot
 * {@link HealthIndicator} 标准规范，在 {@code /actuator/health} 端点返回 details。
 *
 * <p><b>检查项：</b>
 * <ul>
 *   <li>TraceId 生成探针（实际生成一个 ID 验证可用性）</li>
 *   <li>国际化消息解析器是否注入</li>
 * </ul>
 *
 * <p><b>健康状态规则：</b>
 * <ul>
 *   <li>TraceId 生成失败 → DOWN</li>
 *   <li>所有检查通过 → UP</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CoreHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // ── TraceId 生成探针 ──
        try {
            String probeTraceId = TraceIdGenerator.generate();
            details.put("traceIdProbe", probeTraceId != null && !probeTraceId.isBlank() ? "pass" : "fail");
        } catch (Exception e) {
            details.put("traceIdProbe", "fail: " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // ── i18n 解析器状态 ──
        details.put("i18nResolverRegistered", BaseResponse.isResolverRegistered());

        return Health.up().withDetails(details).build();
    }
}
