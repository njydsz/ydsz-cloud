package com.njydsz.workflow.server.metrics;

import com.njydsz.common.base.metrics.AbstractMetricsHolder;

/**
 * 流程引擎运行态 Metrics 静态持有者。
 *
 * <p>为流程引擎核心路径提供 Micrometer 指标注册与累加能力，
 * 通过静态方法方便业务代码（如 {@code DefaultFlowAdvancer}、{@code FlowTaskService}）埋点。
 *
 * <p>继承 {@link AbstractMetricsHolder}，仅保留本模块的业务语义方法，
 * 注册表绑定与缓存去重由父类统一处理。
 *
 * <p>暴露的 Prometheus 指标：
 * <ul>
 *   <li>{@code workflow.start_total{process_def_key}} — 流程启动计数</li>
 *   <li>{@code workflow.task_complete_total{process_def_key}} — 任务完成计数</li>
 *   <li>{@code workflow.execution_duration{process_def_key}} — 流程平均执行耗时分布</li>
 *   <li>{@code workflow.task_timeout_total{process_def_key}} — 流程卡住/超时计数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class WorkflowMetricsHolder extends AbstractMetricsHolder {

    /** 模块指标前缀 */
    private static final String METRIC_PREFIX = "workflow.";

    private WorkflowMetricsHolder() {
        throw new UnsupportedOperationException("utility class");
    }

    // ======================== 流程启动计数 ========================

    /**
     * 递增流程启动计数（{@code workflow.start_total}）。
     *
     * @param processDefKey 流程定义 KEY
     */
    public static void incrementStart(String processDefKey) {
        registerCounter(METRIC_PREFIX, "start_total",
                "process_def_key", safe(processDefKey)).increment();
    }

    // ======================== 任务完成计数 ========================

    /**
     * 递增任务完成计数（{@code workflow.task_complete_total}）。
     *
     * @param processDefKey 流程定义 KEY
     */
    public static void incrementTaskComplete(String processDefKey) {
        registerCounter(METRIC_PREFIX, "task_complete_total",
                "process_def_key", safe(processDefKey)).increment();
    }

    // ======================== 流程平均执行耗时 ========================

    /**
     * 记录流程执行耗时（{@code workflow.execution_duration}）。
     *
     * @param processDefKey 流程定义 KEY
     * @param millis        执行耗时（毫秒）
     */
    public static void recordExecutionDuration(String processDefKey, long millis) {
        recordDuration(METRIC_PREFIX, "execution_duration", millis,
                "process_def_key", safe(processDefKey));
    }

    // ======================== 流程卡住/超时计数 ========================

    /**
     * 递增流程卡住/超时计数（{@code workflow.task_timeout_total}）。
     *
     * @param processDefKey 流程定义 KEY
     */
    public static void incrementTaskTimeout(String processDefKey) {
        registerCounter(METRIC_PREFIX, "task_timeout_total",
                "process_def_key", safe(processDefKey)).increment();
    }
}
