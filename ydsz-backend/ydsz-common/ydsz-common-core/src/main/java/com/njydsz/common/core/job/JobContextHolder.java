package com.njydsz.common.core.job;

/**
 * 任务执行上下文持有者
 *
 * <p>基于 {@link ThreadLocal} 绑定当前线程正在执行的任务 ID 和 jobKey，
 * 供任务执行链路上的各组件（如 GLUE 处理器、动态编译脚本）获取当前任务信息。
 *
 * <p><b>使用规范：</b>
 * <ul>
 *   <li>调度器在任务执行前调用 {@link #set(String, String)}</li>
 *   <li>执行完成后在 finally 块中调用 {@link #clear()} 清理，避免 ThreadLocal 内存泄漏</li>
 *   <li>在异步线程中需手动重新 {@link #set} 当前上下文，否则 {@link #getJobId()} 返回 null</li>
 * </ul>
 *
 * <p><b>典型使用场景：</b>
 * <ul>
 *   <li>动态编译的 Groovy/Java 脚本（GLUE 模式）通过 {@link #getJobId()} 输出关联日志</li>
 *   <li>业务操作日志通过当前任务 ID 关联到 ydsz_job_log</li>
 *   <li>告警消息中嵌入当前任务标识便于排查</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JobLoggerHolder
 */
public final class JobContextHolder {

    private static final ThreadLocal<String> JOB_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> JOB_KEY_HOLDER = new ThreadLocal<>();

    private JobContextHolder() {
    }

    /**
     * 设置当前线程的任务上下文
     *
     * <p>同一线程同一时刻只能执行一个任务；多次调用后只有最后一次生效。
     *
     * @param jobId   任务 ID
     * @param jobKey  任务标识
     */
    public static void set(String jobId, String jobKey) {
        JOB_ID_HOLDER.set(jobId);
        JOB_KEY_HOLDER.set(jobKey);
    }

    /**
     * 获取当前线程正在执行的任务 ID
     *
     * @return 任务 ID，未设置时返回 null
     */
    public static String getJobId() {
        return JOB_ID_HOLDER.get();
    }

    /**
     * 获取当前线程正在执行的任务标识
     *
     * @return 任务标识，未设置时返回 null
     */
    public static String getJobKey() {
        return JOB_KEY_HOLDER.get();
    }

    /**
     * 清除当前线程的任务上下文
     *
     * <p>必须在线程执行结束前调用，避免 ThreadLocal 内存泄漏（特别是在线程池场景）。
     */
    public static void clear() {
        JOB_ID_HOLDER.remove();
        JOB_KEY_HOLDER.remove();
    }
}
