package com.njydsz.common.core.health;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.core.config.CoreProperties;
import com.njydsz.common.core.config.FilterIgnoreProperties;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.core.trace.TraceIdSupplier;
import com.njydsz.common.core.trace.SnowflakeTraceIdSupplier;

/**
 * Core 模块健康指标
 *
 * <p>向 Spring Boot Actuator 暴露核心模块的运行状态信息，遵循 Spring Boot
 * {@link HealthIndicator} 标准规范，在 {@code /actuator/health} 端点返回 details。
 *
 * <p><b>检查项：</b>
 * <ul>
 *   <li>TraceId 生成策略（UUID / Snowflake）及策略类名</li>
 *   <li>TraceId 生成探针（实际生成一个 ID 验证可用性）</li>
 *   <li>链路追踪是否启用、TraceId 类型配置校验</li>
 *   <li>分页配置（运行时 maxPageSize / defaultPageSize）及合法性校验</li>
 *   <li>国际化消息解析器是否注入</li>
 *   <li>Snowflake workerId / datacenterId（当策略为 Snowflake 时）</li>
 *   <li>过滤器忽略路径配置摘要</li>
 * </ul>
 *
 * <p><b>健康状态规则：</b>
 * <ul>
 *   <li>TraceId 生成失败 → DOWN</li>
 *   <li>配置项非法（如 id-type 不在允许值范围内） → DOWN</li>
 *   <li>所有检查通过 → UP</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CoreHealthIndicator implements HealthIndicator {

    /** 合法的 TraceId 类型值 */
    private static final Set<String> VALID_ID_TYPES = Set.of("uuid", "snowflake");

    private final CoreProperties properties;
    private final FilterIgnoreProperties filterIgnoreProperties;

    /**
     * 创建 CoreHealthIndicator 实例
     *
     * @param properties 核心配置属性
     */
    public CoreHealthIndicator(CoreProperties properties) {
        this(properties, null);
    }

    /**
     * 创建 CoreHealthIndicator 实例（增强版）
     *
     * @param properties             核心配置属性
     * @param filterIgnoreProperties 过滤器忽略路径配置（可为 null）
     */
    public CoreHealthIndicator(CoreProperties properties, FilterIgnoreProperties filterIgnoreProperties) {
        this.properties = properties;
        this.filterIgnoreProperties = filterIgnoreProperties;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean hasCritical = false;

        // ── TraceId 策略 ──
        TraceIdSupplier supplier = TraceIdGenerator.getSupplier();
        String traceIdStrategy = supplier != null ? supplier.getClass().getSimpleName() : "unknown";
        details.put("traceIdStrategy", traceIdStrategy);
        details.put("traceEnabled", properties.getTrace().isEnabled());
        details.put("traceIdType", properties.getTrace().getIdType());

        // 配置校验：id-type 合法性
        String idType = properties.getTrace().getIdType();
        if (!VALID_ID_TYPES.contains(idType)) {
            details.put("configValidation", "FAIL: trace.id-type must be 'uuid' or 'snowflake', got: " + idType);
            hasCritical = true;
        } else {
            details.put("configValidation", "PASS");
        }

        // ── TraceId 生成探针 ──
        try {
            String probeTraceId = TraceIdGenerator.generate();
            boolean probeOk = probeTraceId != null && !probeTraceId.isBlank();
            details.put("traceIdProbe", probeOk ? "pass" : "fail");
            if (!probeOk) {
                hasCritical = true;
            }
        } catch (Exception e) {
            details.put("traceIdProbe", "fail: " + e.getMessage());
            hasCritical = true;
        }

        // ── Snowflake 专属信息 ──
        if (supplier instanceof SnowflakeTraceIdSupplier snowflake) {
            details.put("snowflakeWorkerId", extractWorkerId(snowflake));
        }

        // ── 分页配置 ──
        details.put("maxPageSize", properties.getMaxPageSize());
        details.put("defaultPageSize", properties.getDefaultPageSize());

        // 分页合法性校验
        if (properties.getDefaultPageSize() > properties.getMaxPageSize()) {
            details.put("pageSizeValidation", "WARN: default-page-size > max-page-size");
        }

        // ── i18n 解析器状态 ──
        details.put("i18nResolverRegistered", BaseResponse.isResolverRegistered());

        // ── 过滤器忽略路径配置摘要 ──
        if (filterIgnoreProperties != null) {
            details.put("filterIgnoreCommonUrls",
                    filterIgnoreProperties.getMergedCommonIgnoreUrls().size());
            details.put("filterIgnoreSecurityExcludeUrls",
                    filterIgnoreProperties.getMergedSecurityExcludeUrls().size());
            details.put("authFilterIgnoreServiceNames",
                    filterIgnoreProperties.getResolvedAuthFilterIgnoreServiceNames().size());
        }

        if (hasCritical) {
            return Health.down().withDetails(details).build();
        }
        return Health.up().withDetails(details).build();
    }

    /**
     * 从 SnowflakeTraceIdSupplier 提取 workerId
     *
     * <p>通过 toString() 提取，避免反射访问私有字段。
     */
    private String extractWorkerId(SnowflakeTraceIdSupplier snowflake) {
        try {
            return snowflake.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
