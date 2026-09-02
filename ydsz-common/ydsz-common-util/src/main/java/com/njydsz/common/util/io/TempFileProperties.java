package com.njydsz.common.util.io;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 临时文件管理器配置属性。
 *
 * <p>配置前缀：{@code ydsz.util.tempfile}
 *
 * <p><b>配置示例（application.yml）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   util:
 *     tempfile:
 *       retention: 24h        # 临时文件保留时长（超龄自动清理）
 *       cleanup-interval: 10m # 清理任务执行间隔
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.util.tempfile")
public class TempFileProperties {

  /** 临时文件保留时长（创建后超过该时长仍未删除的文件将被兜底清理）。 */
  private Duration retention = Duration.ofHours(24);

  /** 兜底清理任务的执行间隔。 */
  private Duration cleanupInterval = Duration.ofMinutes(10);

  /**
   * 获取临时文件保留时长。
   *
   * @return 保留时长
   */
  public Duration getRetention() {
    return retention;
  }

  /**
   * 设置临时文件保留时长。
   *
   * @param retention 保留时长（正数）
   */
  public void setRetention(Duration retention) {
    this.retention = retention;
  }

  /**
   * 获取兜底清理任务的执行间隔。
   *
   * @return 清理间隔
   */
  public Duration getCleanupInterval() {
    return cleanupInterval;
  }

  /**
   * 设置兜底清理任务的执行间隔。
   *
   * @param cleanupInterval 清理间隔（正数）
   */
  public void setCleanupInterval(Duration cleanupInterval) {
    this.cleanupInterval = cleanupInterval;
  }
}
