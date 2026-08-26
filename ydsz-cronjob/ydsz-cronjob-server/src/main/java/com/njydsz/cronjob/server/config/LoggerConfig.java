package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * Disruptor 日志发布者配置（P0-5 优化：缓冲区大小可配置化 + 监控指标）。
 *
 * <p>控制 DisruptorLogPublisher 的 Ring Buffer 大小，适应不同吞吐量场景。
 *
 * <p>对应配置前缀 {@code ydsz.cronjob.logger.*}。
 *
 * <h3>配置项说明</h3>
 *
 * <ul>
 *   <li>{@link #ringBufferSize} Ring Buffer 大小（必须为 2 的幂，默认 4096）
 * </ul>
 *
 * <p>依据《云顶编码规范》§24 配置管理规范：所有可调整参数必须通过配置项暴露，禁止硬编码。
 *
 * @author ydsz-team
 * @since 1.0.2
 */
@Data
public class LoggerConfig {

  /** 默认 Ring Buffer 大小（2 的幂）：4096 */
  private static final int DEFAULT_RING_BUFFER_SIZE = 4096;

  /** 最小 Ring Buffer 大小（2 的幂）：256 */
  private static final int MIN_RING_BUFFER_SIZE = 256;

  /** 最大 Ring Buffer 大小（2 的幂）：65536 */
  private static final int MAX_RING_BUFFER_SIZE = 65536;

  /**
   * Ring Buffer 大小（必须为 2 的幂，默认 4096）。
   *
   * <p>高并发日志场景下，较大的缓冲区可减少事件丢弃概率，但会增加内存占用。
   * 每槽位约 64 字节，4096 槽位约占用 256KB 堆内存。
   */
  private int ringBufferSize = DEFAULT_RING_BUFFER_SIZE;

  /**
   * 获取规整化后的 Ring Buffer 大小（确保为 2 的幂）。
   *
   * <p>若配置值不是 2 的幂，自动向下取最近的 2 的幂；若超出 [256, 65536] 区间，自动收敛到边界值。
   *
   * @return 规整化后的 Ring Buffer 大小
   */
  public int getNormalizedRingBufferSize() {
    int size = ringBufferSize;
    if (size < MIN_RING_BUFFER_SIZE) {
      return MIN_RING_BUFFER_SIZE;
    }
    if (size > MAX_RING_BUFFER_SIZE) {
      return MAX_RING_BUFFER_SIZE;
    }
    // 向下取最近的 2 的幂
    if ((size & (size - 1)) != 0) {
      size = Integer.highestOneBit(size);
    }
    return size;
  }
}
