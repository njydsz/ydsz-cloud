package com.njydsz.common.tenant.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多租户 Micrometer 指标。
 *
 * <p>按租户维度上报以下指标：
 *
 * <ul>
 *   <li>{@code tenant.sql.intercept.total} — SQL 拦截次数（Counter，tag: result=pass/blocked/skipped）
 *   <li>{@code tenant.failclosed.total} — fail-closed 拒绝次数（Counter）
 *   <li>{@code tenant.context.skip.total} — 跳过隔离次数（Counter）
 *   <li>{@code tenant.superadmin.total} — 超级管理员绕过次数（Counter）
 *   <li>{@code tenant.datasource.switch.total} — 数据源切换次数（Counter，ISOLATE_DB 模式）
 *   <li>{@code tenant.active} — 当前活跃租户上下文数（Gauge）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TenantMetrics {

  private static final String METRIC_PREFIX = "tenant.";

  private final MeterRegistry meterRegistry;
  private final AtomicLong activeContexts = new AtomicLong(0);

  private final AtomicLong interceptPassCount = new AtomicLong(0);
  private final AtomicLong interceptBlockedCount = new AtomicLong(0);
  private final AtomicLong interceptSkippedCount = new AtomicLong(0);
  private final AtomicLong failClosedCount = new AtomicLong(0);
  private final AtomicLong contextSkipCount = new AtomicLong(0);
  private final AtomicLong superAdminCount = new AtomicLong(0);
  private final AtomicLong datasourceSwitchCount = new AtomicLong(0);
  private final AtomicLong sqlCacheHitCount = new AtomicLong(0);
  private final AtomicLong sqlCacheMissCount = new AtomicLong(0);

  public TenantMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    registerMetrics();
  }

  private void registerMetrics() {
    if (meterRegistry == null) {
      return;
    }
    meterRegistry.gauge(METRIC_PREFIX + "active", activeContexts);
  }

  /** 记录 SQL 拦截通过。 */
  public void recordInterceptPass() {
    interceptPassCount.incrementAndGet();
    incrementCounter("sql.intercept.total", "result", "pass");
  }

  /** 记录 SQL 拦截跳过（匿名 URL / 超级管理员）。 */
  public void recordInterceptSkipped() {
    interceptSkippedCount.incrementAndGet();
    incrementCounter("sql.intercept.total", "result", "skipped");
  }

  /** 记录 fail-closed 拒绝。 */
  public void recordFailClosed() {
    failClosedCount.incrementAndGet();
    incrementCounter("failclosed.total");
    incrementCounter("sql.intercept.total", "result", "blocked");
  }

  /** 记录上下文跳过隔离。 */
  public void recordContextSkip() {
    contextSkipCount.incrementAndGet();
    incrementCounter("context.skip.total");
  }

  /** 记录超级管理员绕过。 */
  public void recordSuperAdminBypass() {
    superAdminCount.incrementAndGet();
    incrementCounter("superadmin.total");
  }

  /** 记录数据源切换（ISOLATE_DB 模式）。 */
  public void recordDatasourceSwitch() {
    datasourceSwitchCount.incrementAndGet();
    incrementCounter("datasource.switch.total");
  }

  /** 记录 SQL 缓存命中。 */
  public void recordSqlCacheHit() {
    sqlCacheHitCount.incrementAndGet();
    incrementCounter("sql.cache.total", "result", "hit");
  }

  /** 记录 SQL 缓存未命中（实际解析）。 */
  public void recordSqlCacheMiss() {
    sqlCacheMissCount.incrementAndGet();
    incrementCounter("sql.cache.total", "result", "miss");
  }

  /** 上下文设置时调用（活跃计数+1）。 */
  public void incrementActiveContext() {
    activeContexts.incrementAndGet();
  }

  /** 上下文清除时调用（活跃计数-1）。 */
  public void decrementActiveContext() {
    activeContexts.decrementAndGet();
  }

  private void incrementCounter(String name, String... tagKeyValues) {
    if (meterRegistry == null) {
      return;
    }
    Tags tags = Tags.empty();
    for (int i = 0; i + 1 < tagKeyValues.length; i += 2) {
      tags = tags.and(Tag.of(tagKeyValues[i], tagKeyValues[i + 1]));
    }
    meterRegistry.counter(METRIC_PREFIX + name, tags).increment();
  }

  // --- Getter for HealthIndicator ---

  public long getInterceptPassCount() {
    return interceptPassCount.get();
  }

  /**
   * 获取 SQL 拦截被阻断（fail-closed 拒绝）的次数。
   *
   * @return 被阻断次数
   */
  public long getInterceptBlockedCount() {
    return interceptBlockedCount.get();
  }

  /**
   * 获取 SQL 拦截被跳过（匿名 URL / 超级管理员）的次数。
   *
   * @return 被跳过次数
   */
  public long getInterceptSkippedCount() {
    return interceptSkippedCount.get();
  }

  /**
   * 获取 fail-closed 拒绝执行 SQL 的次数。
   *
   * @return fail-closed 拒绝次数
   */
  public long getFailClosedCount() {
    return failClosedCount.get();
  }

  /**
   * 获取超级管理员绕过隔离的次数。
   *
   * @return 超级管理员绕过次数
   */
  public long getSuperAdminCount() {
    return superAdminCount.get();
  }

  /**
   * 获取数据源切换（ISOLATE_DB 模式）次数。
   *
   * @return 数据源切换次数
   */
  public long getDatasourceSwitchCount() {
    return datasourceSwitchCount.get();
  }

  /**
   * 获取当前活跃租户上下文数。
   *
   * @return 活跃上下文数
   */
  public long getActiveContexts() {
    return activeContexts.get();
  }

  /**
   * 获取 SQL 缓存命中次数。
   *
   * @return 缓存命中次数
   */
  public long getSqlCacheHitCount() {
    return sqlCacheHitCount.get();
  }

  /**
   * 获取 SQL 缓存未命中的次数。
   *
   * @return 缓存未命中次数
   */
  public long getSqlCacheMissCount() {
    return sqlCacheMissCount.get();
  }
}
