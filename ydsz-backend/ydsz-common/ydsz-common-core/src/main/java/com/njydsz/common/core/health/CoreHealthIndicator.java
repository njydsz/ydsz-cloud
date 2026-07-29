package com.njydsz.common.core.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.core.config.CoreProperties;
import com.njydsz.common.core.config.FilterIgnoreProperties;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.core.trace.TraceIdSupplier;

/**
 * Core 模块健康指标
 *
 * <p>向 Spring Boot Actuator 暴露核心模块的运行状态信息，包括：
 * <ul>
 *   <li>TraceId 生成策略（UUID / Snowflake）及策略类名</li>
 *   <li>链路追踪是否启用</li>
 *   <li>分页配置（运行时 maxPageSize / defaultPageSize）</li>
 *   <li>国际化消息解析器是否注入</li>
 *   <li>过滤器忽略路径配置摘要</li>
 * </ul>
 *
 * <p>访问 {@code /actuator/health} 时，响应的 details 中会包含 {@code core} 节点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CoreHealthIndicator implements HealthIndicator {

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

        // TraceId 策略
        TraceIdSupplier supplier = TraceIdGenerator.getSupplier();
        String traceIdStrategy = supplier != null ? supplier.getClass().getSimpleName() : "unknown";
        details.put("traceIdStrategy", traceIdStrategy);
        details.put("traceEnabled", properties.getTrace().isEnabled());
        details.put("traceIdType", properties.getTrace().getIdType());

        // 分页配置（运行时值）
        details.put("maxPageSize", properties.getMaxPageSize());
        details.put("defaultPageSize", properties.getDefaultPageSize());

        // i18n 解析器状态
        details.put("i18nResolverRegistered", com.njydsz.common.core.response.BaseResponse.isResolverRegistered());

        // 过滤器忽略路径配置摘要
        if (filterIgnoreProperties != null) {
            details.put("filterIgnoreCommonUrls", filterIgnoreProperties.getMergedCommonIgnoreUrls().size());
            details.put("filterIgnoreSecurityExcludeUrls", filterIgnoreProperties.getMergedSecurityExcludeUrls().size());
            details.put("authFilterIgnoreServiceNames", filterIgnoreProperties.getResolvedAuthFilterIgnoreServiceNames().size());
        }

        return Health.up().withDetails(details).build();
    }
}
