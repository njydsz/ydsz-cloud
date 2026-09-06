package com.njydsz.common.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SkyWalking 分布式链路追踪配置属性（P2-1 集成）。
 *
 * <p>绑定 {@code ydsz.skywalking.*} 前缀的配置项， 覆盖 SkyWalking Agent 部分运行时行为（采样率、服务名称等）。
 *
 * <p>当 Agent 未挂载（本地开发环境）时，这些属性仍可用于本地 trace 标识注入，但与 Agent 的
 * 拦截增强能力解耦：Agent 相关的运行时配置（如 agent.service_name）仍需通过 JVM 参数或
 * agent.config 文件设置。
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   skywalking:
 *     enabled: true
 *     service-name: ydsz-system
 *     logging-pattern-enabled: true        # 自动注入 traceId/spanId 到日志 MDC
 *     sampling-rate: 1.0                   # 采样率（0.0~1.0，1.0=全量采样）
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.skywalking")
public class SkyWalkingProperties {

  /** 是否启用 SkyWalking 集成（默认 true）。禁用后所有 SkyWalking 能力不生效。 */
  private boolean enabled = true;

  /**
   * 服务名称（映射到 Agent 的 agent.service_name）。
   *
   * <p>建议与 {@code spring.application.name} 保持一致， 不建议每个环境使用不同的名称（会导致 UI 中服务视图分散）。
   * 若未设置，回退到 {@code spring.application.name}。
   */
  private String serviceName = "";

  /**
   * 采样率（0.0 ~ 1.0）。
   *
   * <p>控制 trace 采样的比例。默认 1.0（全量采样）。
   * 高并发生产环境建议设置为 0.1 ~ 0.5（10%~50% 采样），减少性能开销。
   *
   * <p>注意：此配置由 Agent 运行时生效， 不直接修改 Agent 的 agent.sample_n_per_3_secs 参数。
   */
  private double samplingRate = 1.0;

  /**
   * 是否启用 SkyWalking 日志 MDC traceId/spanId 自动注入。
   *
   * <p>开启后，logback pattern 中的 {@code %X{traceId:-}} 和 {@code %X{spanId:-}} 将自动填充，
   * 前提是 {@code apm-toolkit-logback-1.x} 已在 classpath 中且 Agent 已挂载。
   */
  private boolean loggingPatternEnabled = true;

  // ==================== getter / setter ====================

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public double getSamplingRate() {
    return samplingRate;
  }

  public void setSamplingRate(double samplingRate) {
    this.samplingRate = samplingRate;
  }

  public boolean isLoggingPatternEnabled() {
    return loggingPatternEnabled;
  }

  public void setLoggingPatternEnabled(boolean loggingPatternEnabled) {
    this.loggingPatternEnabled = loggingPatternEnabled;
  }

  @Override
  public String toString() {
    return String.format(
        "SkyWalkingProperties{enabled=%s, serviceName='%s', samplingRate=%.2f, loggingPatternEnabled=%s}",
        enabled, serviceName, samplingRate, loggingPatternEnabled);
  }
}
