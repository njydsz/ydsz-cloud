package com.njydsz.cronjob.domain.job;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 任务执行上下文持有者（ThreadLocal 合并版）。
 *
 * <p>统一管理任务执行期间的分片上下文（{@link ShardingContext}）与日志器（{@link JobLogger}）， 取代历史拆分的 JobContextHolder /
 * JobLoggerHolder 两个独立 ThreadLocal， 避免"上下文分两处、清理时漏清一个"的问题。
 *
 * <p>使用 {@link TransmittableThreadLocal}（TTL），在线程池场景下配合 {@code
 * com.alibaba.ttl.TtlRunnable#get(Runnable)} 包装任务，即可实现 父线程上下文自动传播到线程池工作线程，无需 TaskDecorator。
 *
 * <p><b>线程池使用约束：</b>使用 {@link #setLogger(JobLogger)} / {@link #setShardingContext(ShardingContext)}
 * 后，若需提交到线程池， 必须在提交前通过 {@code TtlRunnable.get(task)} 包装任务；否则线程池内将无法读取上下文。 推荐在任务入口处使用 {@link
 * ExecutionContextScope#of(JobLogger, ShardingContext)} 结构化生命周期管理。
 *
 * @author ydsz-team
 * @since 1.0.0 合并 JobContextHolder 与 JobLoggerHolder；1.5.x 起使用 TransmittableThreadLocal
 */
public final class JobExecutionContext {

  // CHECKSTYLE.OFF: RegexpSinglelineJava - ThreadLocal 持有者类，已提供 clear() 统一清理入口（编码规范 15.1 节）
  private static final TransmittableThreadLocal<ShardingContext> SHARDING_CONTEXT =
      new TransmittableThreadLocal<>();

  private static final TransmittableThreadLocal<JobLogger> LOGGER =
      new TransmittableThreadLocal<>();
  // CHECKSTYLE.ON: RegexpSinglelineJava

  private JobExecutionContext() {}

  /**
   * 设置当前线程的分片上下文
   *
   * @param ctx 分片上下文
   */
  public static void setShardingContext(ShardingContext ctx) {
    SHARDING_CONTEXT.set(ctx);
  }

  /**
   * 获取当前线程的分片上下文
   *
   * @return 分片上下文（可能为 null）
   */
  public static ShardingContext getShardingContext() {
    return SHARDING_CONTEXT.get();
  }

  /**
   * 设置当前线程的任务日志器
   *
   * @param logger 日志器
   */
  public static void setLogger(JobLogger logger) {
    LOGGER.set(logger);
  }

  /**
   * 获取当前线程的任务日志器
   *
   * @return 日志器（可能为 null）
   */
  public static JobLogger getLogger() {
    return LOGGER.get();
  }

  /** 清除当前线程的分片上下文与日志器 */
  public static void clear() {
    SHARDING_CONTEXT.remove();
    LOGGER.remove();
  }
}
