package com.njydsz.common.core.job;

/**
 * 任务执行上下文持有者。
 *
 * <p>基于 {@link ThreadLocal} 绑定当前线程正在执行的任务 ID 和 jobKey，
 * 供任务执行链路上的各组件（如 GLUE 处理器）获取当前任务信息。
 *
 * <p>调度器在任务执行前调用 {@link #set(String, String)}，
 * 执行完成后在 finally 块中调用 {@link #clear()} 清理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JobContextHolder {

    private static final ThreadLocal<String> JOB_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> JOB_KEY_HOLDER = new ThreadLocal<>();

    private JobContextHolder() {
    }

    /**
     * 设置当前线程的任务上下文。
     *
     * @param jobId   任务 ID
     * @param jobKey  任务标识
     */
    public static void set(String jobId, String jobKey) {
        JOB_ID_HOLDER.set(jobId);
        JOB_KEY_HOLDER.set(jobKey);
    }

    /**
     * 获取当前线程正在执行的任务 ID。
     *
     * @return 任务 ID，未设置时返回 null
     */
    public static String getJobId() {
        return JOB_ID_HOLDER.get();
    }

    /**
     * 获取当前线程正在执行的任务标识。
     *
     * @return 任务标识，未设置时返回 null
     */
    public static String getJobKey() {
        return JOB_KEY_HOLDER.get();
    }

    /**
     * 清除当前线程的任务上下文。
     */
    public static void clear() {
        JOB_ID_HOLDER.remove();
        JOB_KEY_HOLDER.remove();
    }
}
