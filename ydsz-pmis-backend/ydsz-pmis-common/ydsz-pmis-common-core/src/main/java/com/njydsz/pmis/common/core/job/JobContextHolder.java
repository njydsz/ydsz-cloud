package com.njydsz.pmis.common.core.job;

/**
 * 任务执行上下文持有者（ThreadLocal）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class JobContextHolder {

    private static final ThreadLocal<JobContext> HOLDER = new ThreadLocal<>();

    private JobContextHolder() {
    }

    public static void set(JobContext context) {
        HOLDER.set(context);
    }

    /**
     * 设置任务上下文（便捷方法）。
     *
     * @param jobId  任务 ID
     * @param jobKey 任务 KEY
     */
    public static void set(String jobId, String jobKey) {
        HOLDER.set(new JobContext(jobId, jobKey, null, null, null));
    }

    /**
     * 设置任务上下文（便捷方法）。
     *
     * @param jobId       任务 ID
     * @param jobKey      任务 KEY
     * @param logId       日志 ID
     * @param tenantId    租户 ID
     * @param paramsJson  参数 JSON
     */
    public static void set(String jobId, String jobKey, String logId, String tenantId, String paramsJson) {
        HOLDER.set(new JobContext(jobId, jobKey, logId, tenantId, paramsJson));
    }

    public static JobContext get() {
        return HOLDER.get();
    }

    /**
     * 获取当前任务 ID（便捷方法）。
     *
     * @return 任务 ID，未设置时返回 null
     */
    public static String getJobId() {
        JobContext ctx = HOLDER.get();
        return ctx != null ? ctx.getJobId() : null;
    }

    /**
     * 获取当前任务 KEY（便捷方法）。
     *
     * @return 任务 KEY，未设置时返回 null
     */
    public static String getJobKey() {
        JobContext ctx = HOLDER.get();
        return ctx != null ? ctx.getJobKey() : null;
    }

    /**
     * 获取当前日志 ID（便捷方法）。
     *
     * @return 日志 ID，未设置时返回 null
     */
    public static String getLogId() {
        JobContext ctx = HOLDER.get();
        return ctx != null ? ctx.getLogId() : null;
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 任务执行上下文。
     */
    public static class JobContext {
        private final String jobId;
        private final String jobKey;
        private final String logId;
        private final String tenantId;
        private final String paramsJson;

        public JobContext(String jobId, String jobKey, String logId, String tenantId, String paramsJson) {
            this.jobId = jobId;
            this.jobKey = jobKey;
            this.logId = logId;
            this.tenantId = tenantId;
            this.paramsJson = paramsJson;
        }

        public String getJobId() { return jobId; }
        public String getJobKey() { return jobKey; }
        public String getLogId() { return logId; }
        public String getTenantId() { return tenantId; }
        public String getParamsJson() { return paramsJson; }
    }
}
