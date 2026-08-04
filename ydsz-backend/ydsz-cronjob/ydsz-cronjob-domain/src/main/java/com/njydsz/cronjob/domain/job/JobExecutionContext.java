package com.njydsz.cronjob.domain.job;

import com.njydsz.common.domain.job.JobLogger;

/**
 * 任务执行上下文持有者（ThreadLocal 合并版）。
 *
 * <p>统一管理任务执行期间的分片上下文（{@link ShardingContext}）与日志器（{@link JobLogger}），
 * 取代历史拆分的 JobContextHolder / JobLoggerHolder 两个独立 ThreadLocal，
 * 避免"上下文分两处、清理时漏清一个"的问题。
 *
 * <p><b>注意：</b>使用 {@link InheritableThreadLocal}，仅在线程首次创建时继承父线程的值，
 * 不会在线程复用时更新。线程池场景请使用 TaskDecorator 或在提交任务前
 * 显式调用 {@link #setLogger(JobLogger)} / {@link #setShardingContext(ShardingContext)}，
 * 并在 finally 中 {@link #clear()}，避免上下文泄漏到下一个任务。
 *
 * @author ydsz-team
 * @since 1.4.0 合并 JobContextHolder 与 JobLoggerHolder
 */
public final class JobExecutionContext {

    private static final InheritableThreadLocal<ShardingContext> SHARDING_CONTEXT = new InheritableThreadLocal<>();

    private static final InheritableThreadLocal<JobLogger> LOGGER = new InheritableThreadLocal<>();

    private JobExecutionContext() {
    }

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

    /**
     * 清除当前线程的分片上下文与日志器
     */
    public static void clear() {
        SHARDING_CONTEXT.remove();
        LOGGER.remove();
    }
}
