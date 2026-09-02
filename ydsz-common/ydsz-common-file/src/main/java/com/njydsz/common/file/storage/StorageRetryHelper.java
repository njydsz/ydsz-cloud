package com.njydsz.common.file.storage;

import java.util.function.Predicate;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.exception.FileExceptionCode;

/**
 * 存储操作重试助手（带随机抖动的指数退避策略）
 *
 * <p>对存储操作（上传/下载等）提供自动重试能力，采用指数退避算法并附加随机抖动， 避免在云存储服务短暂不可用时直接失败，同时减少并发重试时的惊群效应。
 *
 * <p><b>重试策略：</b>
 *
 * <ul>
 *   <li>首次失败后等待约 {@code initialBackoffMillis} 毫秒
 *   <li>每次重试等待时间翻倍（指数退避）并附加 0~50% 随机抖动
 *   <li>退避上限为 {@code maxBackoffMillis}（默认 30 秒）
 *   <li>达到最大重试次数后抛出 {@link BusinessException}
 * </ul>
 *
 * <p><b>异常过滤：</b>
 *
 * <ul>
 *   <li>业务异常（{@link BusinessException}）不会重试，直接抛出
 *   <li>可通过 {@link #setRetryablePredicate(Predicate)} 自定义可重试异常类型， 默认所有非业务异常均可重试
 *   <li>线程中断时立即退出重试循环
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class StorageRetryHelper {

  /** 最大退避时间（毫秒） */
  private static final long DEFAULT_MAX_BACKOFF_MILLIS = 30_000L;

  /** 最大重试次数 */
  private final int maxRetries;

  /** 初始退避时间（毫秒） */
  private final long initialBackoffMillis;

  /** 最大退避时间（毫秒） */
  private final long maxBackoffMillis;

  /** 可重试异常判断函数（为 null 时默认所有非业务异常均可重试） */
  private Predicate<Exception> retryablePredicate;

  /**
   * 构造存储重试助手
   *
   * @param maxRetries 最大重试次数（最小为 0，表示不重试）
   * @param initialBackoffMillis 初始退避时间（毫秒，最小为 100）
   */
  public StorageRetryHelper(int maxRetries, long initialBackoffMillis) {
    this(maxRetries, initialBackoffMillis, DEFAULT_MAX_BACKOFF_MILLIS, null);
  }

  /**
   * 构造存储重试助手（带完整参数）
   *
   * @param maxRetries 最大重试次数（最小为 0，表示不重试）
   * @param initialBackoffMillis 初始退避时间（毫秒，最小为 100）
   * @param maxBackoffMillis 最大退避时间（毫秒，最小为 1000）
   * @param retryablePredicate 可重试异常判断函数（可为 null）
   */
  public StorageRetryHelper(
      int maxRetries,
      long initialBackoffMillis,
      long maxBackoffMillis,
      Predicate<Exception> retryablePredicate) {
    this.maxRetries = Math.max(0, maxRetries);
    this.initialBackoffMillis = Math.max(100, initialBackoffMillis);
    this.maxBackoffMillis = Math.max(1000, maxBackoffMillis);
    this.retryablePredicate = retryablePredicate;
  }

  /**
   * 设置可重试异常判断函数
   *
   * @param predicate 异常判断函数，返回 true 表示该异常可重试
   */
  public void setRetryablePredicate(Predicate<Exception> predicate) {
    this.retryablePredicate = predicate;
  }

  /**
   * 带重试执行操作（有返回值）
   *
   * <p>若操作成功则直接返回结果；若操作失败且为可重试异常则自动重试； 若为业务异常或不可重试异常则直接抛出。
   *
   * @param <T> 返回值类型
   * @param action 待执行的操作
   * @param operationName 操作名称（用于日志记录）
   * @return 操作结果
   * @throws BusinessException 达到最大重试次数或业务异常时抛出
   */
  public <T> T executeWithRetry(Supplier<T> action, String operationName) {
    int attempts = 0;
    while (attempts <= maxRetries) {
      try {
        return action.get();
      } catch (BusinessException e) {
        throw e;
      } catch (Exception e) {
        if (!isRetryable(e)) {
          log.warn(
              "[StorageRetry] {} non-retryable exception, abort: {}",
              operationName,
              e.getMessage());
          throw new BusinessException(FileExceptionCode.UNKNOWN);
        }
        attempts++;
        if (attempts > maxRetries) {
          break;
        }
        long backoff = calculateBackoff(attempts);
        log.warn(
            "[StorageRetry] {} attempt {} failed, retrying in {}ms: {}",
            operationName,
            attempts,
            backoff,
            e.getMessage());
        try {
          Thread.sleep(backoff);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    log.error("[StorageRetry] {} failed after {} attempts", operationName, attempts);
    throw new BusinessException(FileExceptionCode.UNKNOWN);
  }

  /**
   * 带重试执行操作（无返回值）
   *
   * @param action 待执行的操作
   * @param operationName 操作名称（用于日志记录）
   */
  public void executeRunnableWithRetry(Runnable action, String operationName) {
    executeWithRetry(
        () -> {
          action.run();
          return null;
        },
        operationName);
  }

  /**
   * 计算退避时间（指数退避 + 随机抖动）
   *
   * <p>退避时间 = min(initialBackoff * 2^(attempt-1), maxBackoff) + random(0, backoff/2)
   *
   * @param attempts 当前已尝试次数（从 1 开始）
   * @return 退避时间（毫秒）
   */
  private long calculateBackoff(int attempts) {
    long exponential = initialBackoffMillis * (1L << Math.min(attempts - 1, 10));
    long capped = Math.min(exponential, maxBackoffMillis);
    long jitter = (long) (Math.random() * (capped / 2 + 1));
    return capped + jitter;
  }

  /**
   * 判断异常是否可重试
   *
   * @param e 异常实例
   * @return true 表示可重试
   */
  private boolean isRetryable(Exception e) {
    if (retryablePredicate != null) {
      return retryablePredicate.test(e);
    }
    return true;
  }

  /**
   * 获取最大重试次数
   *
   * @return 最大重试次数
   */
  public int getMaxRetries() {
    return maxRetries;
  }
}
