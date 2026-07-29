package com.njydsz.common.domain.job;

/**
 * 任务日志器持有者（ThreadLocal）
 *
 * <p>使用 {@link InheritableThreadLocal} 替代普通 ThreadLocal，
 * 使子线程能自动继承父线程的日志器。
 *
 * <p><b>注意：</b>对于线程池场景（线程复用），InheritableThreadLocal 仅在线程首次创建时
 * 继承父线程的值，不会在线程复用时更新。如果需要在线程池中正确传播上下文，
 * 请使用 TaskDecorator 或在提交任务前显式调用 {@link #setLogger(JobLogger)} / {@link #clear()}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JobLoggerHolder {

    private static final InheritableThreadLocal<JobLogger> LOGGER = new InheritableThreadLocal<>();

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
