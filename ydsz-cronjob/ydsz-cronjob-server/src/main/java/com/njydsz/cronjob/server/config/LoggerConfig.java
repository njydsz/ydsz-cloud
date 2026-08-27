package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * Disruptor 日志发布者配置（P0-2 优化：批量写入参数可配置化 + 时间驱动刷新）。
 *
 * <p>控制 DisruptorLogPublisher 的 Ring Buffer 大小和批量写入行为，适应不同吞吐量场景。
 *
 * <p>对应配置前缀 {@code ydsz.cronjob.logger.*}。
 *
 * <h3>配置项说明</h3>
 *
 * <ul>
 *   <li>{@link #ringBufferSize} Ring Buffer 大小（必须为 2 的幂，默认 4096）
 *   <li>{@link #batchSize} 批量写入阈值（条数，默认 50）
 *   <li>{@link #flushIntervalMs} 强制刷新间隔（毫秒，默认 1000ms，避免日志延迟过大）
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

  /** 默认批量写入阈值：50 条 */
  private static final int DEFAULT_BATCH_SIZE = 50;

  /** 最小批量写入阈值：10 条 */
  private static final int MIN_BATCH_SIZE = 10;

  /** 最大批量写入阈值：500 条 */
  private static final int MAX_BATCH_SIZE = 500;

  /** 默认强制刷新间隔：1000ms */
  private static final long DEFAULT_FLUSH_INTERVAL_MS = 1000L;

  /** 最小强制刷新间隔：100ms */
  private static final long MIN_FLUSH_INTERVAL_MS = 100L;

  /** 最大强制刷新间隔：10000ms */
  private static final long MAX_FLUSH_INTERVAL_MS = 10000L;

  /**
   * Ring Buffer 大小（必须为 2 的幂，默认 4096）。
   *
   * <p>高并发日志场景下，较大的缓冲区可减少事件丢弃概率，但会增加内存占用。
   * 每槽位约 64 字节，4096 槽位约占用 256KB 堆内存。
   */
  private int ringBufferSize = DEFAULT_RING_BUFFER_SIZE;

  /**
   * 批量写入阈值（条数，默认 50）。
   *
   * <p>当缓冲区累积到此数量时触发批量写入 DB。
   * 较大的值减少 DB 交互次数，但增加单条日志的写入延迟。
   */
  private int batchSize = DEFAULT_BATCH_SIZE;

  /**
   * 强制刷新间隔（毫秒，默认 1000ms）。
   *
   * <p>即使未达到批量写入阈值，超过此间隔后也会强制刷新缓冲区，
   * 避免低频任务的日志长时间停留在内存中。
   */
  private long flushIntervalMs = DEFAULT_FLUSH_INTERVAL_MS;

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

  /**
   * 获取规整化后的批量写入阈值。
   *
   * <p>若配置值超出 [10, 500] 区间，自动收敛到边界值。
   *
   * @return 规整化后的批量写入阈值
   */
  public int getNormalizedBatchSize() {
    if (batchSize < MIN_BATCH_SIZE) {
      return MIN_BATCH_SIZE;
    }
    if (batchSize > MAX_BATCH_SIZE) {
      return MAX_BATCH_SIZE;
    }
    return batchSize;
  }

  /**
   * 获取规整化后的强制刷新间隔。
   *
   * <p>若配置值超出 [100, 10000] 区间，自动收敛到边界值。
   *
   * @return 规整化后的强制刷新间隔（毫秒）
   */
  public long getNormalizedFlushIntervalMs() {
    if (flushIntervalMs < MIN_FLUSH_INTERVAL_MS) {
      return MIN_FLUSH_INTERVAL_MS;
    }
    if (flushIntervalMs > MAX_FLUSH_INTERVAL_MS) {
      return MAX_FLUSH_INTERVAL_MS;
    }
    return flushIntervalMs;
  }
}
