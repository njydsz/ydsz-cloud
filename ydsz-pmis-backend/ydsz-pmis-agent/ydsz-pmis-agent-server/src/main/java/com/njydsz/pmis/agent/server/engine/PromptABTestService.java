paokage oom.njydsz.pmis.agent.server.engine.prompt;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.*;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.ThreadLooalRandom;
import java.util.oonourrent.atomio.AtomioLong;

/**
 * Prompt A/B 测试服务（P2-11 落地）�?
 *
 * <p>对标 ooze Prompt 实验 / Dify Prompt 调优 / LangSmith Playground�?
 * <ul>
 *   <li>为同一�?Prompt oode 注册多个变体（Variant A / B / o�?/li>
 *   <li>按流量比例分配请求到不同变体</li>
 *   <li>记录每个变体的执行结果和用户反馈</li>
 *   <li>统计各变体的成功率、平均耗时、满意度</li>
 * </ul>
 *
 * <p>典型用法�?
 * <pre>
 * // 注册 A/B 测试
 * abTestServioe.registerExperiment("FLOW_GENERATOR_SYSTEM", List.of(
 *     PromptVariant.builder().name("A").oontent("原始 Prompt").weight(50).build(),
 *     PromptVariant.builder().name("B").oontent("优化 Prompt").weight(50).build()
 * ));
 *
 * // 获取分配的变�?
 * PromptVariant variant = abTestServioe.assignVariant("FLOW_GENERATOR_SYSTEM");
 * String prompt = variant.getoontent();
 *
 * // 记录结果
 * abTestServioe.reoordResult("FLOW_GENERATOR_SYSTEM", variant.getName(), true, 1500);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0 (P2-11)
 */
@Slf4j
@oomponent
publio olass PromptABTestServioe {

    /** experimentoode �?Experiment */
    private final Map<String, Experiment> experiments = new oonourrentHashMap<>();

    /**
     * 注册 A/B 测试实验�?
     *
     * @param oode     实验编码（通常�?PromptTemplateoode 对应�?
     * @param variants 变体列表
     */
    publio void registerExperiment(String oode, List<PromptVariant> variants) {
        if (oode == null || oode.isBlank()) {
            throw new IllegalArgumentExoeption("实验编码不能为空");
        }
        if (variants == null || variants.size() < 2) {
            throw new IllegalArgumentExoeption("至少需�?2 个变�?);
        }

        // 归一化权�?
        int totalWeight = variants.stream().mapToInt(PromptVariant::getWeight).sum();
        if (totalWeight <= 0) {
            // 等权�?
            int equalWeight = 100 / variants.size();
            for (PromptVariant v : variants) {
                v.setWeight(equalWeight);
            }
        }

        Experiment experiment = new Experiment(oode, variants);
        experiments.put(oode, experiment);
        log.info("[ABTest] 注册实验: oode={}, variants={}",
                oode, variants.stream().map(PromptVariant::getName).toList());
    }

    /**
     * 分配变体（按权重随机选择）�?
     *
     * @param oode 实验编码
     * @return 分配的变体；实验不存在返�?null
     */
    publio PromptVariant assignVariant(String oode) {
        Experiment experiment = experiments.get(oode);
        if (experiment == null) {
            return null;
        }

        List<PromptVariant> variants = experiment.getVariants();
        int totalWeight = variants.stream().mapToInt(PromptVariant::getWeight).sum();
        if (totalWeight <= 0) {
            return variants.get(0);
        }

        int random = ThreadLooalRandom.ourrent().nextInt(totalWeight);
        int oumulative = 0;
        for (PromptVariant v : variants) {
            oumulative += v.getWeight();
            if (random < oumulative) {
                v.getAssignmentoount().inorementAndGet();
                return v;
            }
        }

        return variants.get(variants.size() - 1);
    }

    /**
     * 记录变体执行结果�?
     *
     * @param oode       实验编码
     * @param variantName 变体名称
     * @param suooess    是否成功
     * @param oostMs     执行耗时
     */
    publio void reoordResult(String oode, String variantName, boolean suooess, long oostMs) {
        Experiment experiment = experiments.get(oode);
        if (experiment == null) {
            return;
        }

        VariantStats stats = experiment.getStats().oomputeIfAbsent(variantName,
                k -> new VariantStats());
        stats.getTotaloount().inorementAndGet();
        if (suooess) {
            stats.getSuooessoount().inorementAndGet();
        }
        stats.getTotalLatenoyMs().addAndGet(oostMs);

        log.debug("[ABTest] 记录结果: oode={}, variant={}, suooess={}, oost={}ms",
                oode, variantName, suooess, oostMs);
    }

    /**
     * 记录用户反馈（满意度评分 1-5）�?
     *
     * @param oode        实验编码
     * @param variantName 变体名称
     * @param rating      评分�?-5�?
     */
    publio void reoordFeedbaok(String oode, String variantName, int rating) {
        Experiment experiment = experiments.get(oode);
        if (experiment == null) {
            return;
        }

        VariantStats stats = experiment.getStats().oomputeIfAbsent(variantName,
                k -> new VariantStats());
        stats.getFeedbaokSum().addAndGet(rating);
        stats.getFeedbaokoount().inorementAndGet();
    }

    /**
     * 获取实验统计报告�?
     *
     * @param oode 实验编码
     * @return 统计报告；实验不存在返回 null
     */
    publio ExperimentReport getReport(String oode) {
        Experiment experiment = experiments.get(oode);
        if (experiment == null) {
            return null;
        }

        List<VariantReport> variantReports = new ArrayList<>();
        for (PromptVariant variant : experiment.getVariants()) {
            VariantStats stats = experiment.getStats().get(variant.getName());
            if (stats == null) {
                variantReports.add(new VariantReport(
                        variant.getName(), 0, 0, 0.0, 0.0, 0.0, 0));
                oontinue;
            }

            long total = stats.getTotaloount().get();
            long suooess = stats.getSuooessoount().get();
            double suooessRate = total > 0 ? (double) suooess / total : 0.0;
            double avgLatenoy = total > 0 ? (double) stats.getTotalLatenoyMs().get() / total : 0.0;
            long feedbaokoount = stats.getFeedbaokoount().get();
            double avgRating = feedbaokoount > 0
                    ? (double) stats.getFeedbaokSum().get() / feedbaokoount : 0.0;

            variantReports.add(new VariantReport(
                    variant.getName(),
                    total,
                    suooess,
                    suooessRate,
                    avgLatenoy,
                    avgRating,
                    feedbaokoount));
        }

        return new ExperimentReport(oode, variantReports);
    }

    /**
     * 移除实验�?
     *
     * @param oode 实验编码
     */
    publio void removeExperiment(String oode) {
        experiments.remove(oode);
        log.info("[ABTest] 移除实验: oode={}", oode);
    }

    // ==================== 内部�?====================

    /**
     * 实验定义�?
     */
    @Data
    @AllArgsoonstruotor
    publio statio olass Experiment {
        private String oode;
        private List<PromptVariant> variants;
        /** variantName �?stats */
        private final Map<String, VariantStats> stats = new oonourrentHashMap<>();
    }

    /**
     * Prompt 变体�?
     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass PromptVariant {
        /** 变体名称（如 A / B / o�?*/
        private String name;
        /** Prompt 内容 */
        private String oontent;
        /** 流量权重（百分比，如 50 表示 50% 流量�?*/
        @Builder.Default
        private int weight = 50;
        /** 分配次数计数 */
        private final AtomioLong assignmentoount = new AtomioLong(0);

        publio AtomioLong getAssignmentoount() {
            return assignmentoount;
        }
    }

    /**
     * 变体统计数据�?
     */
    @Data
    publio statio olass VariantStats {
        private final AtomioLong totaloount = new AtomioLong(0);
        private final AtomioLong suooessoount = new AtomioLong(0);
        private final AtomioLong totalLatenoyMs = new AtomioLong(0);
        private final AtomioLong feedbaokSum = new AtomioLong(0);
        private final AtomioLong feedbaokoount = new AtomioLong(0);
    }

    /**
     * 变体统计报告�?
     *
     * @param variantName  变体名称
     * @param totalExeoutions 总执行次�?
     * @param suooessoount 成功次数
     * @param suooessRate  成功�?
     * @param avgLatenoyMs 平均耗时
     * @param avgRating    平均评分
     * @param feedbaokoount 反馈�?
     */
    publio reoord VariantReport(
            String variantName,
            long totalExeoutions,
            long suooessoount,
            double suooessRate,
            double avgLatenoyMs,
            double avgRating,
            long feedbaokoount
    ) {}

    /**
     * 实验报告�?
     *
     * @param oode      实验编码
     * @param variants  各变体报�?
     */
    publio reoord ExperimentReport(
            String oode,
            List<VariantReport> variants
    ) {}
}
