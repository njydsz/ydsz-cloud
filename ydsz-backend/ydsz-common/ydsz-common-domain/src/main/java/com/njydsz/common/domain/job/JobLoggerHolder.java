package com.njydsz.common.domain.job;

/**
 * 任务日志器持有者（ThreadLocal）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JobLoggerHolder {

    private static final ThreadLocal<JobLogger> LOGGER = new ThreadLocal<>();

    private JobLoggerHolder() {
    }

    /**
     * 设置当前线程的日志器
     *
     * @param logger 日志器
     */
    public static void setLogger(JobLogger logger) {
        LOGGER.set(logger);
    }

    /**
     * 获取当前线程的日志器
     *
     * @return 日志器（可能为 null）
     */
    public static JobLogger getLogger() {
        return LOGGER.get();
    }

    /**
     * 清除当前线程的日志器
     */
    public static void clear() {
        LOGGER.remove();
    }
}
