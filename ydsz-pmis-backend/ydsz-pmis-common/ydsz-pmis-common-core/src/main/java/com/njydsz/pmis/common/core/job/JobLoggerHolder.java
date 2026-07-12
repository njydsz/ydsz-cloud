package com.njydsz.pmis.common.core.job;

/**
 * 任务日志记录器 ThreadLocal 持有者。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class JobLoggerHolder {

    private static final ThreadLocal<JobLogger> HOLDER = new ThreadLocal<>();

    private JobLoggerHolder() {
    }

    public static void set(JobLogger logger) {
        HOLDER.set(logger);
    }

    public static JobLogger get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
