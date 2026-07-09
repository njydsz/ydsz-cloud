package com.njydsz.pmis.agent.engine.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prompt A/B 测试服务（P2-11 落地）。
 *
 * <p>对标 Coze Prompt 实验 / Dify Prompt 调优 / LangSmith Playground：
 * <ul>
 *   <li>为同一个 Prompt Code 注册多个变体（Variant A / B / C）</li>
 *   <li>按流量比例分配请求到不同变体</li>
 *   <li>记录每个变体的执行结果和用户反馈</li>
 *   <li>统计各变体的成功率、平均耗时、满意度</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>
 * // 注册 A/B 测试
 * abTestService.registerExperiment("FLOW_GENERATOR_SYSTEM", List.of(
 *     PromptVariant.builder().name("A").content("原始 Prompt").weight(50).build(),
 *     PromptVariant.builder().name("B").content("优化 Prompt").weight(50).build()
 * ));
 *
 * // 获取分配的变体
 * PromptVariant variant = abTestService.assignVariant("FLOW_GENERATOR_SYSTEM");
 * String prompt = variant.getContent();
 *
 * // 记录结果
 * abTestService.recordResult("FLOW_GENERATOR_SYSTEM", variant.getName(), true, 1500);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0 (P2-11)
 */
@Slf4j
@Component
public class PromptABTestService {

    /** experimentCode → Experiment */
    private final Map<String, Experiment> experiments = new ConcurrentHashMap<>();

    /**
     * 注册 A/B 测试实验。
     *
     * @param code     实验编码（通常与 PromptTemplateCode 对应）
     * @param variants 变体列表
     */
    public void registerExperiment(String code, List<PromptVariant> variants) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("实验编码不能为空");
        }
        if (variants == null || variants.size() < 2) {
            throw new IllegalArgumentException("至少需要 2 个变体");
        }

        // 归一化权重
        int totalWeight = variants.stream().mapToInt(PromptVariant::getWeight).sum();
        if (totalWeight <= 0) {
            // 等权重
            int equalWeight = 100 / variants.size();
            for (PromptVariant v : variants) {
                v.setWeight(equalWeight);
            }
        }

        Experiment experiment = new Experiment(code, variants);
        experiments.put(code, experiment);
        log.info("[ABTest] 注册实验: code={}, variants={}",
                code, variants.stream().map(PromptVariant::getName).toList());
    }

    /**
     * 分配变体（按权重随机选择）。
     *
     * @param code 实验编码
     * @return 分配的变体；实验不存在返回 null
     */
    public PromptVariant assignVariant(String code) {
        Experiment experiment = experiments.get(code);
        if (experiment == null) {
            return null;
        }

        List<PromptVariant> variants = experiment.getVariants();
        int totalWeight = variants.stream().mapToInt(PromptVariant::getWeight).sum();
        if (totalWeight <= 0) {
            return variants.get(0);
        }

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (PromptVariant v : variants) {
            cumulative += v.getWeight();
            if (random < cumulative) {
                v.getAssignmentCount().incrementAndGet();
                return v;
            }
        }

        return variants.get(variants.size() - 1);
    }

    /**
     * 记录变体执行结果。
     *
     * @param code       实验编码
     * @param variantName 变体名称
     * @param success    是否成功
     * @param costMs     执行耗时
     */
    public void recordResult(String code, String variantName, boolean success, long costMs) {
        Experiment experiment = experiments.get(code);
        if (experiment == null) {
            return;
        }

        VariantStats stats = experiment.getStats().computeIfAbsent(variantName,
                k -> new VariantStats());
        stats.getTotalCount().incrementAndGet();
        if (success) {
            stats.getSuccessCount().incrementAndGet();
        }
        stats.getTotalLatencyMs().addAndGet(costMs);

        log.debug("[ABTest] 记录结果: code={}, variant={}, success={}, cost={}ms",
                code, variantName, success, costMs);
    }

    /**
     * 记录用户反馈（满意度评分 1-5）。
     *
     * @param code        实验编码
     * @param variantName 变体名称
     * @param rating      评分（1-5）
     */
    public void recordFeedback(String code, String variantName, int rating) {
        Experiment experiment = experiments.get(code);
        if (experiment == null) {
            return;
        }

        VariantStats stats = experiment.getStats().computeIfAbsent(variantName,
                k -> new VariantStats());
        stats.getFeedbackSum().addAndGet(rating);
        stats.getFeedbackCount().incrementAndGet();
    }

    /**
     * 获取实验统计报告。
     *
     * @param code 实验编码
     * @return 统计报告；实验不存在返回 null
     */
    public ExperimentReport getReport(String code) {
        Experiment experiment = experiments.get(code);
        if (experiment == null) {
            return null;
        }

        List<VariantReport> variantReports = new ArrayList<>();
        for (PromptVariant variant : experiment.getVariants()) {
            VariantStats stats = experiment.getStats().get(variant.getName());
            if (stats == null) {
                variantReports.add(new VariantReport(
                        variant.getName(), 0, 0, 0.0, 0.0, 0.0, 0));
                continue;
            }

            long total = stats.getTotalCount().get();
            long success = stats.getSuccessCount().get();
            double successRate = total > 0 ? (double) success / total : 0.0;
            double avgLatency = total > 0 ? (double) stats.getTotalLatencyMs().get() / total : 0.0;
            long feedbackCount = stats.getFeedbackCount().get();
            double avgRating = feedbackCount > 0
                    ? (double) stats.getFeedbackSum().get() / feedbackCount : 0.0;

            variantReports.add(new VariantReport(
                    variant.getName(),
                    total,
                    success,
                    successRate,
                    avgLatency,
                    avgRating,
                    feedbackCount));
        }

        return new ExperimentReport(code, variantReports);
    }

    /**
     * 移除实验。
     *
     * @param code 实验编码
     */
    public void removeExperiment(String code) {
        experiments.remove(code);
        log.info("[ABTest] 移除实验: code={}", code);
    }

    // ==================== 内部类 ====================

    /**
     * 实验定义。
     */
    @Data
    @AllArgsConstructor
    public static class Experiment {
        private String code;
        private List<PromptVariant> variants;
        /** variantName → stats */
        private final Map<String, VariantStats> stats = new ConcurrentHashMap<>();
    }

    /**
     * Prompt 变体。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptVariant {
        /** 变体名称（如 A / B / C） */
        private String name;
        /** Prompt 内容 */
        private String content;
        /** 流量权重（百分比，如 50 表示 50% 流量） */
        @Builder.Default
        private int weight = 50;
        /** 分配次数计数 */
        private final AtomicLong assignmentCount = new AtomicLong(0);

        public AtomicLong getAssignmentCount() {
            return assignmentCount;
        }
    }

    /**
     * 变体统计数据。
     */
    @Data
    public static class VariantStats {
        private final AtomicLong totalCount = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong totalLatencyMs = new AtomicLong(0);
        private final AtomicLong feedbackSum = new AtomicLong(0);
        private final AtomicLong feedbackCount = new AtomicLong(0);
    }

    /**
     * 变体统计报告。
     *
     * @param variantName  变体名称
     * @param totalExecutions 总执行次数
     * @param successCount 成功次数
     * @param successRate  成功率
     * @param avgLatencyMs 平均耗时
     * @param avgRating    平均评分
     * @param feedbackCount 反馈数
     */
    public record VariantReport(
            String variantName,
            long totalExecutions,
            long successCount,
            double successRate,
            double avgLatencyMs,
            double avgRating,
            long feedbackCount
    ) {}

    /**
     * 实验报告。
     *
     * @param code      实验编码
     * @param variants  各变体报告
     */
    public record ExperimentReport(
            String code,
            List<VariantReport> variants
    ) {}
}
