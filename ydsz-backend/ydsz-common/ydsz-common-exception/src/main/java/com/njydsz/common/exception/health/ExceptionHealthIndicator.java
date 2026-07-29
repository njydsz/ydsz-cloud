package com.njydsz.common.exception.health;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.metrics.ExceptionMetrics;
import com.njydsz.common.exception.registry.ResultCodeRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 异常模块健康检查指标
 *
 * <p>报告异常处理模块各组件的运行状态，包括：
 * <ul>
 *   <li>全局异常处理器是否启用</li>
 *   <li>异常指标统计器是否可用</li>
 *   <li>错误码注册中心已注册模块数和错误码总数</li>
 *   <li>响应格式（BaseResponse / ProblemDetail）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
public class ExceptionHealthIndicator implements HealthIndicator {

    private final ExceptionProperties properties;
    private final ObjectProvider<ExceptionMetrics> metricsProvider;
    private final ObjectProvider<ResultCodeRegistry> resultCodeRegistryProvider;

    public ExceptionHealthIndicator(ExceptionProperties properties,
                                    ObjectProvider<ExceptionMetrics> metricsProvider,
                                    ObjectProvider<ResultCodeRegistry> resultCodeRegistryProvider) {
        this.properties = properties;
        this.metricsProvider = metricsProvider;
        this.resultCodeRegistryProvider = resultCodeRegistryProvider;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        details.put("globalHandlerEnabled", properties.isGlobalHandlerEnabled());
        details.put("metricsEnabled", properties.isMetricsEnabled());
        details.put("responseFormat", properties.getResponseFormat());
        details.put("includeStackTrace", properties.isIncludeStackTrace());
        details.put("docEndpointEnabled", properties.isDocEndpointEnabled());

        ExceptionMetrics metrics = metricsProvider.getIfAvailable();
        details.put("metricsAvailable", metrics != null);
        if (metrics != null) {
            details.put("metricsEnabled", metrics.isEnabled());
            details.put("metricsIncludeCodeTag", properties.isMetricsIncludeCodeTag());
        }

        ResultCodeRegistry registry = resultCodeRegistryProvider.getIfAvailable();
        if (registry != null) {
            details.put("registeredModules", registry.getModules().size());
            int totalCodes = registry.getAllErrorCodes().values().stream()
                    .mapToInt(Set::size)
                    .sum();
            details.put("registeredErrorCodes", totalCodes);
        } else {
            details.put("registeredModules", 0);
            details.put("registeredErrorCodes", 0);
        }

        details.put("problemDetailTypeBaseUrl", properties.getProblemDetailTypeBaseUrl());

        return Health.up().withDetails(details).build();
    }
}
