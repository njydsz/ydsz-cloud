package com.njydsz.pmis.agent.server.engine.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型负载均衡与智能路由器（P2-1 落地）。
 *
 * <p>对标 Coze 模型负载均衡 / Dify Model Load Balancing / OpenAI Router：
 * 根据任务类型、模型能力、历史延迟和成本，智能选择最优 LLM Provider。
 *
 * <p>路由策略：
 * <ol>
 *   <li><b>任务类型路由</b>：简单问答 → 轻量模型，复杂推理 → 强力模型，代码生成 → 代码模型</li>
 *   <li><b>加权轮询</b>：同一优先级的多个 Provider 按权重分配请求</li>
 *   <li><b>延迟感知</b>：优先选择历史平均延迟更低的 Provider</li>
 *   <li><b>成本优化</b>：在能力相同的情况下，优先选择更便宜的模型</li>
 * </ol>
 *
 * <p>使用方式：
 * <pre>
 *   pmis.agent.llm.smart-routing: true
 *   pmis.agent.llm.routing-rules: |
 *     simple_qa → gpt-4o-mini,deepseek-chat
 *     complex_reasoning → gpt-4o,claude-3.5-sonnet
 *     code_generation → deepseek-coder,gpt-4o
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P2-1)
 */
@Slf4j
@Component
public class ModelLoadBalancer {

    /**
     * 任务类型枚举。
     */
    public enum TaskType {
        /** 简单问答（闲聊、FAQ） */
        SIMPLE_QA,
        /** 复杂推理（分析、推理、多步骤） */
        COMPLEX_REASONING,
        /** 代码生成 */
        CODE_GENERATION,
        /** 文档摘要 */
        SUMMARIZATION,
        /** 数据提取 */
        EXTRACTION,
        /** 默认/未知 */
        DEFAULT
    }

    /**
     * Provider 运行时统计。
     */
    private static class ProviderStats {
        final AtomicInteger totalRequests = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);
        final AtomicInteger totalLatencyMs = new AtomicInteger(0);
        volatile double lastAvgLatency = 0;

        void recordSuccess(long latencyMs) {
            totalRequests.incrementAndGet();
            successCount.incrementAndGet();
            totalLatencyMs.addAndGet((int) latencyMs);
            lastAvgLatency = (double) totalLatencyMs.get() / totalRequests.get();
        }

        void recordFailure() {
            totalRequests.incrementAndGet();
            failureCount.incrementAndGet();
        }

        double successRate() {
            int total = totalRequests.get();
            return total == 0 ? 1.0 : (double) successCount.get() / total;
        }

        double avgLatencyMs() {
            int total = totalRequests.get();
            return total == 0 ? 0 : (double) totalLatencyMs.get() / total;
        }
    }

    /** Provider 名称 → 运行时统计 */
    private final Map<String, ProviderStats> statsMap = new ConcurrentHashMap<>();

    /** 轮询计数器（按任务类型） */
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    /**
     * 任务类型 → Provider 优先级列表 的路由规则。
     *
     * <p>格式：TaskType → ["provider1", "provider2", ...]
     * 按列表顺序尝试，第一个可用的 Provider 被选中。
     */
    private final Map<TaskType, List<String>> routingRules = new EnumMap<>(TaskType.class);

    public ModelLoadBalancer() {
        // 默认路由规则：所有任务类型使用同一个 Provider
        log.info("[ModelLoadBalancer] 初始化, smartRouting=false (使用默认路由)");
    }

    /**
     * 配置路由规则。
     *
     * @param taskType    任务类型
     * @param providers   按优先级排列的 Provider 名称列表
     */
    public void configureRule(TaskType taskType, List<String> providers) {
        if (taskType != null && providers != null && !providers.isEmpty()) {
            routingRules.put(taskType, new ArrayList<>(providers));
            log.info("[ModelLoadBalancer] 路由规则: {} → {}", taskType, providers);
        }
    }

    /**
     * 根据任务类型选择最优 Provider。
     *
     * <p>选择逻辑：
     * <ol>
     *   <li>查找该任务类型的路由规则</li>
     *   <li>在规则中的 Provider 列表里，按加权轮询选择一个</li>
     *   <li>加权因素：成功率（权重 ↑）、平均延迟（权重 ↓）</li>
     *   <li>如果该任务类型没有规则，返回 null（由调用方使用默认 Provider）</li>
     * </ol>
     *
     * @param taskType 任务类型
     * @return 选中的 Provider 名称；null 表示使用默认 Provider
     */
    public String selectProvider(TaskType taskType) {
        if (taskType == null) {
            return null;
        }
        List<String> candidates = routingRules.get(taskType);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        // 单个候选直接返回
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // 加权轮询：根据成功率和延迟计算权重
        String selected = selectByWeightedRoundRobin(taskType, candidates);
        log.debug("[ModelLoadBalancer] 任务 {} 选择 Provider: {}", taskType, selected);
        return selected;
    }

    /**
     * 加权轮询选择。
     *
     * <p>权重 = 成功率 × 100 / (1 + 平均延迟 / 1000)
     * 即成功率越高、延迟越低的 Provider 权重越大。
     */
    private String selectByWeightedRoundRobin(TaskType taskType, List<String> candidates) {
        // 计算每个 Provider 的权重
        List<Double> weights = new ArrayList<>();
        double totalWeight = 0;
        for (String provider : candidates) {
            ProviderStats stats = statsMap.computeIfAbsent(provider, k -> new ProviderStats());
            double successRate = stats.successRate();
            double avgLatency = stats.avgLatencyMs();
            // 权重 = 成功率 / (1 + 延迟秒数)
            double weight = successRate * 100 / (1 + avgLatency / 1000);
            // 保证最低权重
            weight = Math.max(weight, 1);
            weights.add(weight);
            totalWeight += weight;
        }

        // 轮询计数器递增
        String counterKey = taskType.name();
        int counter = roundRobinCounters
                .computeIfAbsent(counterKey, k -> new AtomicInteger(0))
                .getAndIncrement();

        // 加权选择
        double target = (counter % 100) / 100.0 * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights.get(i);
            if (cumulative >= target) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    /**
     * 记录 Provider 调用成功。
     *
     * @param providerName Provider 名称
     * @param latencyMs    调用延迟（毫秒）
     */
    public void recordSuccess(String providerName, long latencyMs) {
        statsMap.computeIfAbsent(providerName, k -> new ProviderStats())
                .recordSuccess(latencyMs);
    }

    /**
     * 记录 Provider 调用失败。
     *
     * @param providerName Provider 名称
     */
    public void recordFailure(String providerName) {
        statsMap.computeIfAbsent(providerName, k -> new ProviderStats())
                .recordFailure();
    }

    /**
     * 获取所有 Provider 的运行时统计。
     *
     * @return Provider 名称 → 统计信息 Map
     */
    public Map<String, Object> getStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ProviderStats> entry : statsMap.entrySet()) {
            Map<String, Object> stat = new LinkedHashMap<>();
            ProviderStats s = entry.getValue();
            stat.put("totalRequests", s.totalRequests.get());
            stat.put("successCount", s.successCount.get());
            stat.put("failureCount", s.failureCount.get());
            stat.put("successRate", String.format("%.2f", s.successRate()));
            stat.put("avgLatencyMs", String.format("%.0f", s.avgLatencyMs()));
            result.put(entry.getKey(), stat);
        }
        return result;
    }

    /**
     * 推断任务类型。
     *
     * <p>基于用户输入的文本特征，简单推断任务类型。
     * 可后续替换为 LLM 分类或更复杂的规则。
     *
     * @param userPrompt 用户输入
     * @return 推断的任务类型
     */
    public static TaskType inferTaskType(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return TaskType.DEFAULT;
        }
        String lower = userPrompt.toLowerCase();

        // 代码生成
        if (lower.contains("代码") || lower.contains("code")
                || lower.contains("函数") || lower.contains("function")
                || lower.contains("编程") || lower.contains("program")) {
            return TaskType.CODE_GENERATION;
        }

        // 文档摘要
        if (lower.contains("摘要") || lower.contains("总结") || lower.contains("summar")
                || lower.contains("概括") || lower.contains("归纳")) {
            return TaskType.SUMMARIZATION;
        }

        // 数据提取
        if (lower.contains("提取") || lower.contains("extract")
                || lower.contains("解析") || lower.contains("识别")) {
            return TaskType.EXTRACTION;
        }

        // 复杂推理
        if (lower.contains("分析") || lower.contains("推理") || lower.contains("reason")
                || lower.contains("比较") || lower.contains("评估") || lower.contains("plan")
                || lower.contains("计划") || lower.contains("方案")) {
            return TaskType.COMPLEX_REASONING;
        }

        // 简单问答
        if (userPrompt.length() < 50) {
            return TaskType.SIMPLE_QA;
        }

        return TaskType.DEFAULT;
    }
}
