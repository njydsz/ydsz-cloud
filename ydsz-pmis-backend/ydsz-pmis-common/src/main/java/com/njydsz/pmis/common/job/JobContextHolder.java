package com.njydsz.pmis.common.job;

/**
 * 任务执行上下文 ThreadLocal 持有者（P1-2 GLUE 在线编码）。
 *
 * <p>{@code DefaultTaskDispatcher} 在任务执行前调用 {@link #set(String, String)} 绑定
 * 当前任务的 jobId / jobKey，执行后调用 {@link #clear()} 释放，
 * 避免线程池复用导致上下文串任务。
 *
 * <p>业务侧（如 {@code GlueJobHandler}）通过 {@link #getJobId()} 获取当前任务 ID，
 * 用于加载该任务对应的 GLUE 代码等场景；非任务线程内调用返回 {@code null}（静默降级）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class JobContextHolder {

    private static final ThreadLocal<String> JOB_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> JOB_KEY = new ThreadLocal<>();

    private JobContextHolder() {
    }

    /**
     * 绑定当前任务 ID 与 KEY 到当前线程。
     *
     * @param jobId  任务 ID；null 等同于 {@link #clear()}
     * @param jobKey 任务 KEY（可空）
     */
    public static void set(String jobId, String jobKey) {
        if (jobId == null) {
            clear();
            return;
        }
        JOB_ID.set(jobId);
        JOB_KEY.set(jobKey);
    }

    /**
     * 获取当前线程绑定的任务 ID。
     *
     * @return 任务 ID；非任务线程返回 null
     */
    public static String getJobId() {
        return JOB_ID.get();
    }

    /**
     * 获取当前线程绑定的任务 KEY。
     *
     * @return 任务 KEY；非任务线程返回 null
     */
    public static String getJobKey() {
        return JOB_KEY.get();
    }

    /**
     * 清除当前线程绑定的任务上下文。
     *
     * <p>必须在任务执行完成后调用，避免线程池复用时上下文串任务。
     */
    public static void clear() {
        JOB_ID.remove();
        JOB_KEY.remove();
    }
}
