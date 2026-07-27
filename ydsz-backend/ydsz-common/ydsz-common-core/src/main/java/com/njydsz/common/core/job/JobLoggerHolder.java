package com.njydsz.common.core.job;

/**
 * 任务执行日志器持有者。
 *
 * <p>基于 {@link ThreadLocal} 绑定当前线程正在使用的 {@link JobLogger}，
 * 供任务执行链路上的各组件（如 MapReduce Processor）写入在线日志。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JobLoggerHolder {

    private static final ThreadLocal<JobLogger> HOLDER = new ThreadLocal<>();

    private JobLoggerHolder() {
    }

    /**
     * 设置当前线程的日志器。
     *
     * @param logger 日志器
     */
    public static void set(JobLogger logger) {
        HOLDER.set(logger);
    }

    /**
     * 获取当前线程的日志器。
     *
     * @return 日志器，未设置时返回 null
     */
    public static JobLogger get() {
        return HOLDER.get();
    }

    /**
     * 清除当前线程的日志器。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
