package com.njydsz.common.lock.metrics;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 分布式锁指标采集配置。
 *
 * <p>采集 Redisson 分布式锁的获取/释放/等待/超时指标，输出到 Micrometer，
 *
 * <p>便于在 Grafana 中监控锁竞争热点与潜在的死锁风险。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
public class LockMetricsConfiguration {

  /**
   * 将 MeterRegistry 绑定到 LockMetrics，启用 Prometheus 指标采集
   *
   * @param lockMetrics 锁指标收集器
   * @param meterRegistry Micrometer 指标注册表
   */
  public LockMetricsConfiguration(LockMetrics lockMetrics, MeterRegistry meterRegistry) {
    lockMetrics.bindMeterRegistry(meterRegistry);
  }
}
