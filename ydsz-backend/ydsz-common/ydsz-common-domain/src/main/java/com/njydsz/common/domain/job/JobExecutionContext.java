package com.njydsz.common.domain.job;

/**
 * 任务执行上下文持有者（统一 ThreadLocal）。
 *
 * <p>合并原 {@code JobContextHolder}（分片上下文）与 {@code JobLoggerHolder}（任务日志器）
 * 两个独立 ThreadLocal 为一个统一上下文，避免业务方在多处切换与遗漏清理：
 * <ul>
 *   <li>{@link #setShardingContext(ShardingContext)} — 写入分片上下文</li>
 *   <li>{@link #setLogger(JobLogger)} — 写入任务日志器</li>
 *   <li>{@link #getShardingContext()} / {@link #getLogger()} — 读取当前线程上下文</li>
 *   <li>{@link #clear()} — 一次性清空全部上下文（任务结束时必须调用）</li>
 * </ul>
 *
 * <p>使用 {@link InheritableThreadLocal}，使子线程能自动继承父线程的任务上下文。
 *
 * <p><b>注意：</b>对于线程池场景（线程复用），InheritableThreadLocal 仅在线程首次创建时
 * 继承父线程的值，不会在线程复用时更新。如果需要在线程池中正确传播上下文，
 * 请使用 TaskDecorator 或在提交任务前显式调用 {@link #setShardingContext(ShardingContext)} /
 * {@link #setLogger(JobLogger)}，并在任务结束 finally 中调用 {@link #clear()}，
 * 避免上下文泄漏到下一个任务。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public final class JobExecutionContext {

    private static final InheritableThreadLocal<JobExecutionContext> CTX =
            new InheritableThreadLocal<>();

    /** 分片上下文（可能为 null） */
    private ShardingContext shardingContext;

    /** 任务日志器（可能为 null） */
    private JobLogger logger;

    private JobExecutionContext() {
    }

    private static JobExecutionContext getOrCreate() {
        JobExecutionContext ctx = CTX.get();
        if (ctx == null) {
            ctx = new JobExecutionContext();
            CTX.set(ctx);
        }
        return ctx;
    }

    /**
     * 设置当前线程的分片上下文。
     *
     * @param ctx 分片上下文
     */
    public static void setShardingContext(ShardingContext ctx) {
        getOrCreate().shardingContext = ctx;
    }

    /**
     * 获取当前线程的分片上下文。
     *
     * @return 分片上下文（可能为 null）
     */
    public static ShardingContext getShardingContext() {
        JobExecutionContext ctx = CTX.get();
        return ctx != null ? ctx.shardingContext : null;
    }

    /**
     * 设置当前线程的任务日志器。
     *
     * @param logger 任务日志器
     */
    public static void setLogger(JobLogger logger) {
        getOrCreate().logger = logger;
    }

    /**
     * 获取当前线程的任务日志器。
     *
     * @return 任务日志器（可能为 null）
     */
    public static JobLogger getLogger() {
        JobExecutionContext ctx = CTX.get();
        return ctx != null ? ctx.logger : null;
    }

    /**
     * 清空当前线程的全部任务上下文。
     *
     * <p>任务执行结束（finally 块）时必须调用，防止 ThreadLocal 泄漏到线程池中的下一个任务。
     */
    public static void clear() {
        CTX.remove();
    }
}
