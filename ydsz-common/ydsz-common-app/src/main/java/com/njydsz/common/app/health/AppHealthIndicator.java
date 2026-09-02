package com.njydsz.common.app.health;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.app.metrics.AppMetrics;
import com.njydsz.common.safe.config.ApiSignatureProperties;

/**
 * App 模块健康检查指示器
 *
 * <p>检测 App 端各子系统的健康状态，暴露 {@code /actuator/health/app} 端点。
 *
 * <p><b>检测项：</b>
 *
 * <ul>
 *   <li>API 签名验证：启用状态、密钥配置、排除路径数量、时间戳容差
 *   <li>指标采集：Micrometer 是否可用
 * </ul>
 *
 * <p><b>注意：</b>Redis 连通性和安全能力清单由 {@code SafeHealthIndicator} 统一报告， 本指示器仅关注 App 模块特有状态，避免重复检测。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class AppHealthIndicator implements HealthIndicator {

  private final ApiSignatureProperties signatureProperties;
  private final ObjectProvider<AppMetrics> appMetricsProvider;

  /**
   * 构造方法
   *
   * @param signatureProperties safe 模块的 API 签名配置属性
   * @param appMetricsProvider App 指标采集器（可选依赖）
   */
  public AppHealthIndicator(
      ApiSignatureProperties signatureProperties, ObjectProvider<AppMetrics> appMetricsProvider) {
    this.signatureProperties = signatureProperties;
    this.appMetricsProvider = appMetricsProvider;
  }

  /**
   * 上报 App 模块健康状态。
   *
   * <p>汇总 API 签名验证配置摘要与指标采集可用性；当签名验证启用但密钥缺失时 标记 DOWN（此时所有 App 请求都会因签名校验失败而拒绝，属功能性故障）。 details
   * 中仅暴露配置摘要与计数，不包含密钥本身。
   */
  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>(16);
    details.put("module", "app");

    // API 签名验证状态（由 safe 模块提供，app 模块报告配置摘要）
    Map<String, Object> signatureStatus = new LinkedHashMap<>(16);
    signatureStatus.put("enabled", signatureProperties.isEnabled());
    signatureStatus.put(
        "hasSecret",
        signatureProperties.getAppSecret() != null
            && !signatureProperties.getAppSecret().isBlank());
    signatureStatus.put(
        "timestampToleranceSeconds", signatureProperties.getTimestampToleranceSeconds());
    signatureStatus.put("nonceExpireSeconds", signatureProperties.getNonceExpireSeconds());
    signatureStatus.put("excludesCount", signatureProperties.getExcludes().size());
    details.put("signature", signatureStatus);

    // 指标采集状态
    AppMetrics metrics = appMetricsProvider.getIfAvailable();
    details.put("metrics", metrics != null ? "enabled" : "disabled");

    // 如果签名验证启用但密钥未配置，标记为 DOWN
    if (signatureProperties.isEnabled()
        && (signatureProperties.getAppSecret() == null
            || signatureProperties.getAppSecret().isBlank())) {
      return Health.down()
          .withDetail("module", "app")
          .withDetail("error", "API 签名验证已启用但未配置密钥")
          .withDetails(details)
          .build();
    }

    return Health.up().withDetails(details).build();
  }
}
