package com.njydsz.literule.server.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.server.spi.TraceRecorder;

/**
 * 异步批量轨迹记录器
 *
 * <p>内置 {@link BlockingQueue} 缓冲轨迹事件，后台线程按批写入。 实际持久化委托给消费方提供的 {@link TraceRecorder}（通过 {@link
 * #setDelegate} 注入）； 若未提供 delegate，则仅保留在内存队列中（用于测试/默认禁用持久化的场景）。
 *
 * <p>特性：
 *
 * <ul>
 *   <li>非阻塞：{@link #record} 仅入队，主流程无 I/O 等待
 *   <li>批量：后台线程攒够 batchSize 或等待 flushIntervalMs 即刷新
 *   <li>背压：队列满时丢弃最新事件并记日志（防止拖垮主流程）
 *   <li>优雅关闭：{@link #shutdown} 等待剩余事件写入
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public class AsyncTraceRecorder implements TraceRecorder {

  private final BlockingQueue<RuleExecutionTraceVO> queue;
  private final int batchSize;
  private final long flushIntervalMs;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final ExecutorService worker;
  private final int queueCapacity;

  /** 实际持久化委托（可选） */
  private volatile TraceRecorder delegate;

  /**
   * 构造异步记录器
   *
   * @param queueCapacity 队列容量（建议 1000~10000）
   * @param batchSize 批量大小（建议 50~200）
   * @param flushIntervalMs 刷新间隔（建议 1000~5000ms）
   */
  public AsyncTraceRecorder(int queueCapacity, int batchSize, long flushIntervalMs) {
    this.queueCapacity = queueCapacity;
    this.queue = new LinkedBlockingQueue<>(queueCapacity);
    this.batchSize = batchSize;
    this.flushIntervalMs = flushIntervalMs;
    this.worker = ExecutorUtils.newSingleThreadExecutor("literule-trace-writer");
    this.worker.submit(this::flushLoop);
    log.info(
        "[LiteRule-Trace] 异步轨迹记录器已启动: queueCapacity={}, batchSize={}, flushIntervalMs={}",
        queueCapacity,
        batchSize,
        flushIntervalMs);
  }

  /**
   * 设置实际持久化委托
   *
   * <p>若不设置，{@link #flushBatch} 仅清空队列（不入库），适用于禁用 Trace 持久化的场景。
   *
   * @param delegate 持久化委托
   */
  public void setDelegate(TraceRecorder delegate) {
    this.delegate = delegate;
  }

  @Override
  public void record(RuleExecutionTraceVO trace) {
    if (!running.get()) {
      log.debug("[LiteRule-Trace] 记录器已关闭，丢弃轨迹: ruleCode={}", trace.getRuleCode());
      return;
    }
    if (!queue.offer(trace)) {
      log.warn(
          "[LiteRule-Trace] 队列已满（capacity={}），丢弃轨迹: ruleCode={}",
          queueCapacity,
          trace.getRuleCode());
    }
  }

  @Override
  public void recordBatch(List<RuleExecutionTraceVO> traces) {
    for (RuleExecutionTraceVO trace : traces) {
      record(trace);
    }
  }

  @Override
  public List<RuleExecutionTraceVO> getByTraceId(String traceId) {
    return delegate != null ? delegate.getByTraceId(traceId) : Collections.emptyList();
  }

  @Override
  public List<RuleExecutionTraceVO> getByRuleCode(String ruleCode, int limit) {
    return delegate != null ? delegate.getByRuleCode(ruleCode, limit) : Collections.emptyList();
  }

  @Override
  public List<RuleExecutionTraceVO> getRecentTraces(int limit) {
    return delegate != null ? delegate.getRecentTraces(limit) : Collections.emptyList();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  /**
   * 获取当前队列积压大小
   *
   * @return 队列中的待写入轨迹数
   * @since 26.09.01
   */
  public int getQueueSize() {
    return queue.size();
  }

  /**
   * 获取队列总容量
   *
   * @return 队列容量
   * @since 26.09.01
   */
  public int getQueueCapacity() {
    return queueCapacity;
  }

  /**
   * 检查后台线程是否仍在运行
   *
   * @return true 表示后台线程正在运行
   * @since 26.09.01
   */
  public boolean isRunning() {
    return running.get();
  }

  /** 后台刷新循环 */
  private void flushLoop() {
    List<RuleExecutionTraceVO> batch = new ArrayList<>(batchSize);
    while (running.get() || !queue.isEmpty()) {
      try {
        RuleExecutionTraceVO first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
        if (first == null) {
          continue;
        }
        batch.add(first);
        queue.drainTo(batch, batchSize - 1);
        if (!batch.isEmpty()) {
          flushBatch(batch);
          batch.clear();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.warn("[LiteRule-Trace] 批量写入失败: {}", e.getMessage());
        batch.clear();
      }
    }
  }

  /** 刷新一批到委托 */
  private void flushBatch(List<RuleExecutionTraceVO> batch) {
    if (delegate == null) {
      // 无委托：仅清空队列（不入库）
      return;
    }
    try {
      delegate.recordBatch(batch);
    } catch (Exception e) {
      log.warn("[LiteRule-Trace] 委托批量写入失败: count={}, err={}", batch.size(), e.getMessage());
    }
  }

  /**
   * 优雅关闭（等待剩余事件写入或超时）
   *
   * @param timeoutSeconds 超时秒数
   */
  public void shutdown(long timeoutSeconds) {
    running.set(false);
    try {
      worker.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    log.info("[LiteRule-Trace] 异步轨迹记录器已关闭, 剩余队列: {}", queue.size());
  }
}
