package com.njydsz.common.tenant.interceptor;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;

import com.njydsz.common.jdbc.spi.InnerInterceptorProvider;
import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.common.tenant.metrics.TenantMetrics;

/**
 * 租户隔离拦截器 SPI 提供者。
 *
 * <p>当 {@code common-tenant} 在 classpath 且 {@code ydsz.tenant.enabled=true} 时， 通过 {@link
 * InnerInterceptorProvider} SPI 接口自动注册到 {@code MybatisPlusInterceptor} 链中（order=400）。
 *
 * <p>order=400 确保租户隔离在字段填充（300）之后、数据权限（500）之前执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see InnerInterceptorProvider
 * @see TenantIsolationInterceptor
 */
public class TenantInterceptorProvider implements InnerInterceptorProvider {

  private final TenantProperties properties;
  private final TenantMetrics metrics;

  public TenantInterceptorProvider(TenantProperties properties, TenantMetrics metrics) {
    this.properties = properties;
    this.metrics = metrics;
  }

  public TenantInterceptorProvider(TenantProperties properties) {
    this(properties, null);
  }

  @Override
  public InnerInterceptor createInterceptor() {
    return new TenantIsolationInterceptor(properties, metrics);
  }

  @Override
  public int getOrder() {
    return 400;
  }
}
