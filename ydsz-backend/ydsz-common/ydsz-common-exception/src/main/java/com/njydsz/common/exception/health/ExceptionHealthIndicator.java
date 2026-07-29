package com.njydsz.common.exception.health;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import com.njydsz.common.exception.alert.ExceptionAlertPublisher;
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
 *   <li>异常告警发布器是否可用、监听器数量</li>
 *   <li>错误码注册中心已注册模块数和错误码总数</li>
 *   <li>响应格式（BaseResponse / ProblemDetail）</li>
 *   <li>堆栈脱敏是否启用</li>
 *   <li>异步告警是否启用</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
public class ExceptionHealthIndicator implements HealthIndicator {

    private final ExceptionProperties properties;
    private final ObjectProvider<ExceptionMetrics> metricsProvider;
    private final ObjectProvider<ExceptionAlertPublisher> alertPublisherProvider;
    private final ObjectProvider<ResultCodeRegistry> resultCodeRegistryProvider;

    /**
     * 构造异常模块健康检查指标
     *
     * @param properties             异常模块配置属性
     * @param metricsProvider        异常指标统计器（可选）
     * @param alertPublisherProvider  异常告警发布器（可选）
     * @param resultCodeRegistryProvider 错误码注册中心（可选）
     */
    public ExceptionHealthIndicator(ExceptionProperties properties,
                                    ObjectProvider<ExceptionMetrics> metricsProvider,
                                    ObjectProvider<ExceptionAlertPublisher> alertPublisherProvider,
                                    ObjectProvider<ResultCodeRegistry> resultCodeRegistryProvider) {
        this.properties = properties;
        this.metricsProvider = metricsProvider;
        this.alertPublisherProvider = alertPublisherProvider;
        this.resultCodeRegistryProvider = resultCodeRegistryProvider;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // 基础配置状态
        details.put("globalHandlerEnabled", properties.isGlobalHandlerEnabled());
        details.put("metricsEnabled", properties.isMetricsEnabled());
        details.put("alertEnabled", properties.isAlertEnabled());
        details.put("traceEnabled", properties.isTraceEnabled());
        details.put("responseFormat", properties.getResponseFormat());
        details.put("includeStackTrace", properties.isIncludeStackTrace());
        details.put("docEndpointEnabled", properties.isDocEndpointEnabled());

        // 指标统计器状态
        ExceptionMetrics metrics = metricsProvider.getIfAvailable();
        details.put("metricsAvailable", metrics != null);
        if (metrics != null) {
            details.put("metricsEnabled", metrics.isEnabled());
            details.put("metricsIncludeCodeTag", properties.isMetricsIncludeCodeTag());
        }

        // 告警发布器状态
        ExceptionAlertPublisher alertPublisher = alertPublisherProvider.getIfAvailable();
        details.put("alertPublisherAvailable", alertPublisher != null);
        if (alertPublisher != null) {
            details.put("asyncAlertEnabled", properties.isAsyncAlertEnabled());
            details.put("alertDedupWindowSeconds", properties.getAlertDedupWindowSeconds());
            details.put("alertSilencePeriodSeconds", properties.getAlertSilencePeriodSeconds());
        }

        // 错误码注册中心状态
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

        // ProblemDetail 配置
        details.put("problemDetailTypeBaseUrl", properties.getProblemDetailTypeBaseUrl());

        return Health.up().withDetails(details).build();
    }
}
