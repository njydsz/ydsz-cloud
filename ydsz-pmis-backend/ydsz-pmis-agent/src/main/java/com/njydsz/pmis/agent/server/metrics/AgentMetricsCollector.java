package com.njydsz.pmis.agent.server.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 指标采集器（P1-8 落地）。
 *
 * <p>对标 Coze 数据看板 / Dify 监控面板 / LangSmith Tracing Dashboard：
 * <ul>
 *   <li>Agent 执行次数（按 agentType 维度）</li>
 *   <li>Agent 执行成功率 / 失败率</li>
 *   <li>LLM 调用次数与 Token 消耗</li>
 *   <li>工具调用次数与成功率</li>
 *   <li>平均执行耗时 / P95 / P99</li>
 *   <li>RAG 检索次数与命中率</li>
 * </ul>
 *
 * <p>采集方式：内存滑动窗口统计，定期快照到 DB。
 * 后续可对接 Micrometer / Prometheus 实现时序指标导出。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P1-8)
 */
@Slf4j
@Component
public class AgentMetricsCollector {

    /** agentType → 指标数据 */
    private final Map<String, AgentMetrics> metricsMap = new ConcurrentHashMap<>();

    /** LLM Provider → 调用次数 */
    private final Map<String, AtomicLong> llmCallCounts = new ConcurrentHashMap<>();

    /** LLM Provider → Token 消耗 */
    private final Map<String, AtomicLong> llmTokenCounts = new ConcurrentHashMap<>();

    /** 工具名 → 调用次数 */
    private final Map<String, AtomicLong> toolCallCounts = new ConcurrentHashMap<>();

    /** 工具名 → 失败次数 */
    private final Map<String, AtomicLong> toolFailureCounts = new ConcurrentHashMap<>();

    /** RAG 检索次数 */
    private final AtomicLong ragQueryCount = new AtomicLong(0);

    /** RAG 检索命中次数（返回结果非空） */
    private final AtomicLong ragHitCount = new AtomicLong(0);

    /**
     * 记录 Agent 执行开始。
     *
     * @param agentType Agent 类型
     */
    public void recordAgentStart(String agentType) {
        getOrCreate(agentType).totalExecutions.incrementAndGet();
    }

    /**
     * 记录 Agent 执行成功。
     *
     * @param agentType Agent 类型
     * @param costMs    执行耗时（毫秒）
     */
    public void recordAgentSuccess(String agentType, long costMs) {
        AgentMetrics m = getOrCreate(agentType);
        m.successCount.incrementAndGet();
        recordLatency(m, costMs);
    }

    /**
     * 记录 Agent 执行失败。
     *
     * @param agentType Agent 类型
     * @param costMs    执行耗时（毫秒）
     * @param error     错误信息
     */
    public void recordAgentFailure(String agentType, long costMs, String error) {
        AgentMetrics m = getOrCreate(agentType);
        m.failureCount.incrementAndGet();
        recordLatency(m, costMs);
        log.debug("[Metrics] Agent 失败: type={}, cost={}ms, error={}", agentType, costMs, error);
    }

    /**
     * 记录 LLM 调用。
     *
     * @param providerName LLM Provider 名称
     * @param tokenCount   本次调用消耗的 Token 数
     */
    public void recordLlmCall(String providerName, long tokenCount) {
        llmCallCounts.computeIfAbsent(providerName, k -> new AtomicLong(0)).incrementAndGet();
        llmTokenCounts.computeIfAbsent(providerName, k -> new AtomicLong(0)).addAndGet(tokenCount);
    }

    /**
     * 记录工具调用。
     *
     * @param toolName 工具名
     * @param success  是否成功
     */
    public void recordToolCall(String toolName, boolean success) {
        toolCallCounts.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        if (!success) {
            toolFailureCounts.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    /**
     * 记录 RAG 检索。
     *
     * @param hit 是否命中（返回结果非空）
     */
    public void recordRagQuery(boolean hit) {
        ragQueryCount.incrementAndGet();
        if (hit) {
            ragHitCount.incrementAndGet();
        }
    }

    /**
     * 获取指定 Agent 类型的指标快照。
     *
     * @param agentType Agent 类型
     * @return 指标快照；不存在返回 null
     */
    public AgentMetrics getMetrics(String agentType) {
        return metricsMap.get(agentType);
    }

    /**
     * 获取所有 Agent 指标快照。
     *
     * @return agentType → 指标快照
     */
    public Map<String, AgentMetrics> getAllMetrics() {
        return Map.copyOf(metricsMap);
    }

    /**
     * 获取 LLM 调用统计。
     *
     * @return provider → [调用次数, Token 消耗]
     */
    public Map<String, long[]> getLlmStats() {
        Map<String, long[]> stats = new ConcurrentHashMap<>();
        for (String provider : llmCallCounts.keySet()) {
            long calls = llmCallCounts.getOrDefault(provider, new AtomicLong(0)).get();
            long tokens = llmTokenCounts.getOrDefault(provider, new AtomicLong(0)).get();
            stats.put(provider, new long[]{calls, tokens});
        }
        return stats;
    }

    /**
     * 获取工具调用统计。
     *
     * @return toolName → [调用次数, 失败次数]
     */
    public Map<String, long[]> getToolStats() {
        Map<String, long[]> stats = new ConcurrentHashMap<>();
        for (String tool : toolCallCounts.keySet()) {
            long calls = toolCallCounts.getOrDefault(tool, new AtomicLong(0)).get();
            long failures = toolFailureCounts.getOrDefault(tool, new AtomicLong(0)).get();
            stats.put(tool, new long[]{calls, failures});
        }
        return stats;
    }

    /**
     * 获取 RAG 统计。
     *
     * @return [检索次数, 命中次数]
     */
    public long[] getRagStats() {
        return new long[]{ragQueryCount.get(), ragHitCount.get()};
    }

    /**
     * 重置所有指标（用于测试）。
     */
    public void reset() {
        metricsMap.clear();
        llmCallCounts.clear();
        llmTokenCounts.clear();
        toolCallCounts.clear();
        toolFailureCounts.clear();
        ragQueryCount.set(0);
        ragHitCount.set(0);
        log.info("[Metrics] 所有指标已重置");
    }

    // ==================== 内部方法 ====================

    private AgentMetrics getOrCreate(String agentType) {
        return metricsMap.computeIfAbsent(agentType, k -> new AgentMetrics());
    }

    private void recordLatency(AgentMetrics m, long costMs) {
        m.totalLatencyMs.addAndGet(costMs);
        // 简化 P95/P99：使用滚动最大值和平均值
        // 生产环境应使用滑动窗口或 HDR Histogram
        if (costMs > m.maxLatencyMs.get()) {
            m.maxLatencyMs.set(costMs);
        }
    }

    /**
     * Agent 指标数据。
     */
    public static class AgentMetrics {
        public final AtomicLong totalExecutions = new AtomicLong(0);
        public final AtomicLong successCount = new AtomicLong(0);
        public final AtomicLong failureCount = new AtomicLong(0);
        public final AtomicLong totalLatencyMs = new AtomicLong(0);
        public final AtomicLong maxLatencyMs = new AtomicLong(0);

        public long getTotalExecutions() { return totalExecutions.get(); }
        public long getSuccessCount() { return successCount.get(); }
        public long getFailureCount() { return failureCount.get(); }
        public long getTotalLatencyMs() { return totalLatencyMs.get(); }
        public long getMaxLatencyMs() { return maxLatencyMs.get(); }

        public double getSuccessRate() {
            long total = totalExecutions.get();
            return total > 0 ? (double) successCount.get() / total : 0.0;
        }

        public double getAvgLatencyMs() {
            long total = totalExecutions.get();
            return total > 0 ? (double) totalLatencyMs.get() / total : 0.0;
        }

        @Override
        public String toString() {
            return String.format("AgentMetrics{total=%d, success=%d, fail=%d, rate=%.1f%%, avgLatency=%.0fms, maxLatency=%dms}",
                    getTotalExecutions(), getSuccessCount(), getFailureCount(),
                    getSuccessRate() * 100, getAvgLatencyMs(), getMaxLatencyMs());
        }
    }
}
