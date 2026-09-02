package com.njydsz.cronjob.server.core.logger;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.service.log.JobLogContentService;

/**
 * P2-3: Disruptor 日志事件发布者。
 *
 * <p>封装 Disruptor ring buffer，提供高性能无锁的日志事件发布能力。 替代 {@link JobLoggerImpl} 中的同步缓冲区，实现：
 *
 * <ul>
 *   <li>执行线程无阻塞发布（单线程写入，CAS 原子操作）
 *   <li>消费线程批量写入 DB（减少 DB 交互次数）
 *   <li>预分配事件对象，避免 GC 压力
 * </ul>
 *
 * <h3>Ring Buffer 配置</h3>
 *
 * <ul>
 *   <li>缓冲区大小：可通过 {@code ydsz.cronjob.logger.ring-buffer-size} 配置（默认 4096，必须为 2 的幂）
 *   <li>线程工厂：{@link DaemonThreadFactory}（守护线程，不阻止 JVM 退出）
 *   <li>等待策略：{@code BlockingWaitStrategy}（CPU 友好，适合日志场景）
 * </ul>
 *
 * <h3>监控指标</h3>
 *
 * <p>剩余容量通过 {@link #getRemainingCapacity()} 暴露，可用于 Prometheus 告警（缓冲区使用率 > 80% 时预警）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class DisruptorLogPublisher {
  /** Vararg 参数：行号索引 */
  private static final int ARG_LINE_NO_INDEX = 2;

  /** Vararg 参数：内容索引 */
  private static final int ARG_CONTENT_INDEX = 3;

  /** Vararg 参数：追加标记索引 */
  private static final int ARG_APPEND_INDEX = 4;

  private final ObjectProvider<JobLogContentService> jobLogContentServiceProvider;
  private final CronjobProperties cronjobProperties;

  /** Disruptor 实例 */
  private Disruptor<DisruptorLogEvent> disruptor;

  /** Ring Buffer 引用（用于发布事件） */
  private RingBuffer<DisruptorLogEvent> ringBuffer;

  public DisruptorLogPublisher(
      ObjectProvider<JobLogContentService> jobLogContentServiceProvider,
      CronjobProperties cronjobProperties) {
    this.jobLogContentServiceProvider = jobLogContentServiceProvider;
    this.cronjobProperties = cronjobProperties;
  }

  /** 初始化 Disruptor 和 Ring Buffer（P0-2: 传递配置到消费者） */
  @PostConstruct
  public void init() {
    int bufferSize = cronjobProperties.getLogger().getNormalizedRingBufferSize();
    disruptor =
        new Disruptor<>(
            new DisruptorLogEventFactory(),
            bufferSize,
            DaemonThreadFactory.INSTANCE,
            ProducerType.MULTI,
            new BlockingWaitStrategy());
    // P0-2: 传递 CronjobProperties 以支持可配置的批量写入参数
    disruptor.handleEventsWith(
        new DisruptorLogEventHandler(jobLogContentServiceProvider, cronjobProperties));
    disruptor.start();
    ringBuffer = disruptor.getRingBuffer();
    log.info(
        "[DisruptorLog] 日志发布者初始化完成: bufferSize={} batchSize={} flushIntervalMs={}",
        bufferSize,
        cronjobProperties.getLogger().getNormalizedBatchSize(),
        cronjobProperties.getLogger().getNormalizedFlushIntervalMs());
  }

  /**
   * 获取 Ring Buffer 剩余容量。
   *
   * <p>用于监控缓冲区使用率，高并发场景下剩余容量低说明消费速度跟不上生产速度。
   *
   * @return 剩余容量槽位数
   */
  public long getRemainingCapacity() {
    if (ringBuffer == null) {
      return 0;
    }
    return ringBuffer.remainingCapacity();
  }

  /**
   * 获取 Ring Buffer 总容量。
   *
   * @return 总容量槽位数
   */
  public int getBufferSize() {
    if (ringBuffer == null) {
      return cronjobProperties.getLogger().getNormalizedRingBufferSize();
    }
    return ringBuffer.getBufferSize();
  }

  /**
   * 发布日志事件到 Ring Buffer。
   *
   * <p>由 {@link JobLoggerImpl} 调用，将日志内容发布到 Disruptor。 如果 Ring Buffer 已满（极少发生），降级为丢弃并记录
   * warn 日志。
   *
   * @param logId 日志 ID
   * @param jobKey 任务 KEY
   * @param lineNo 行号
   * @param content 日志内容
   * @param level 日志级别
   */
  public void publish(String logId, String jobKey, int lineNo, String content, String level) {
    try {
      // P0-FIX: 5 个参数超出 EventTranslatorThreeArg 上限，改用 Vararg translator
      ringBuffer.publishEvent(
          (event, sequence, args) ->
              event.setInfo(
                  (String) args[0],
                  (String) args[1],
                  (Integer) args[2],
                  (String) args[ARG_CONTENT_INDEX],
                  (String) args[ARG_APPEND_INDEX]),
          logId,
          jobKey,
          lineNo,
          content,
          level);
    } catch (Exception e) {
      log.warn(
          "[DisruptorLog] 发布日志事件失败: logId={} lineNo={} reason={}",
          logId,
          lineNo,
          e.getMessage());
    }
  }

  /** 优雅关闭 Disruptor */
  @PreDestroy
  public void shutdown() {
    if (disruptor != null) {
      try {
        disruptor.shutdown();
        log.info("[DisruptorLog] Disruptor 日志发布者已关闭");
      } catch (Exception e) {
        log.warn("[DisruptorLog] Disruptor 关闭异常: {}", e.getMessage());
      }
    }
  }
}
