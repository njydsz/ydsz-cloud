package com.njydsz.common.exception.health;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.exception.code.ErrorCodeTable;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

/**
 * 异常模块健康检查指标
 *
 * <p>报告异常处理模块各组件的运行状态，包括：
 *
 * <ul>
 *   <li>全局异常处理器是否启用
 *   <li>异常指标统计器是否可用
 *   <li>错误码注册中心已注册模块数和错误码总数
 *   <li>响应格式（YdszResponse / ProblemDetail）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class ExceptionHealthIndicator implements HealthIndicator {

  private final ExceptionProperties properties;
  private final ObjectProvider<ExceptionMetrics> metricsProvider;
  private final ObjectProvider<ErrorCodeTable> errorCodeTableProvider;

  public ExceptionHealthIndicator(
      ExceptionProperties properties,
      ObjectProvider<ExceptionMetrics> metricsProvider,
      ObjectProvider<ErrorCodeTable> errorCodeTableProvider) {
    this.properties = properties;
    this.metricsProvider = metricsProvider;
    this.errorCodeTableProvider = errorCodeTableProvider;
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>(16);

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

    ErrorCodeTable table = errorCodeTableProvider.getIfAvailable();
    if (table != null) {
      details.put("registeredModules", table.getModules().size());
      details.put("registeredErrorCodes", table.allCodes().size());
    } else {
      details.put("registeredModules", 0);
      details.put("registeredErrorCodes", 0);
    }

    details.put("problemDetailTypeBaseUrl", properties.getProblemDetailTypeBaseUrl());

    return Health.up().withDetails(details).build();
  }
}
