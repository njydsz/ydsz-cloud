package com.njydsz.pmis.common.job;

/**
 * JobLogger ThreadLocal 持有者（P0-2 在线日志白屏化）。
 *
 * <p>{@code DefaultTaskDispatcher} 在任务执行前调用 {@link #set(JobLogger)} 绑定日志器，
 * 执行后调用 {@link #clear()} 释放，避免线程池复用导致日志串任务。
 *
 * <p>业务侧通过 {@link #get()} 获取当前日志器；非任务线程内调用返回 {@code null}（静默降级）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class JobLoggerHolder {

    private static final ThreadLocal<JobLogger> HOLDER = new ThreadLocal<>();

    private JobLoggerHolder() {
    }

    /**
     * 绑定日志器到当前线程。
     *
     * @param logger 日志器实例；null 等同于 {@link #clear()}
     */
    public static void set(JobLogger logger) {
        if (logger == null) {
            clear();
            return;
        }
        HOLDER.set(logger);
    }

    /**
     * 获取当前线程绑定的日志器。
     *
     * @return 日志器实例；非任务线程返回 null
     */
    public static JobLogger get() {
        return HOLDER.get();
    }

    /**
     * 清除当前线程绑定的日志器。
     *
     * <p>必须在任务执行完成后调用，避免线程池复用时日志串任务。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
