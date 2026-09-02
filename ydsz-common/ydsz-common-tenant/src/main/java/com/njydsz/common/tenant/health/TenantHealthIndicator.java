package com.njydsz.common.tenant.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.common.tenant.datasource.TenantDataSourceRouter;
import com.njydsz.common.tenant.metrics.TenantMetrics;

/**
 * 多租户健康检查。
 *
 * <p>暴露 {@code /actuator/health/tenant} 端点，报告：
 *
 * <ul>
 *   <li>多租户是否启用
 *   <li>当前隔离模式（SINGLE / MULTI / ISOLATE_DB）
 *   <li>租户列名
 *   <li>系统租户 ID / 超级管理员租户 ID
 *   <li>SQL 拦截指标（通过 / 拒绝 / 跳过）
 *   <li>fail-closed 拒绝次数
 *   <li>ISOLATE_DB 模式数据源路由器状态（可选）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class TenantHealthIndicator implements HealthIndicator {

  private final TenantProperties properties;
  private final ObjectProvider<TenantMetrics> metricsProvider;
  private final ObjectProvider<TenantDataSourceRouter> dataSourceRouterProvider;

  public TenantHealthIndicator(
      TenantProperties properties,
      ObjectProvider<TenantMetrics> metricsProvider,
      ObjectProvider<TenantDataSourceRouter> dataSourceRouterProvider) {
    this.properties = properties;
    this.metricsProvider = metricsProvider;
    this.dataSourceRouterProvider = dataSourceRouterProvider;
  }

  @Override
  public Health health() {
    Health.Builder builder = Health.up();

    builder.withDetail("enabled", properties.isEnabled());
    builder.withDetail("mode", properties.getMode().name());
    builder.withDetail("tenantColumn", properties.getTenantColumn());
    builder.withDetail("superTenantId", properties.getSuperTenantId());
    builder.withDetail("systemTenantId", properties.getSystemTenantId());
    builder.withDetail("ignoreTables", properties.getNormalizedIgnoreTables());
    builder.withDetail("anonUrls", properties.getNormalizedAnonUrls());

    // 指标
    TenantMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null) {
      builder.withDetail("interceptPassCount", metrics.getInterceptPassCount());
      builder.withDetail("interceptBlockedCount", metrics.getInterceptBlockedCount());
      builder.withDetail("interceptSkippedCount", metrics.getInterceptSkippedCount());
      builder.withDetail("failClosedCount", metrics.getFailClosedCount());
      builder.withDetail("superAdminBypassCount", metrics.getSuperAdminCount());
      builder.withDetail("activeContexts", metrics.getActiveContexts());
    } else {
      builder.withDetail("metrics", "not configured");
    }

    // ISOLATE_DB 数据源路由器状态
    TenantDataSourceRouter router = dataSourceRouterProvider.getIfAvailable();
    if (router != null) {
      builder.withDetail("isolateDbMode", router.isIsolateDbMode());
      builder.withDetail(
          "datasourceSwitchCount", metrics != null ? metrics.getDatasourceSwitchCount() : 0);
    }

    return builder.build();
  }
}
