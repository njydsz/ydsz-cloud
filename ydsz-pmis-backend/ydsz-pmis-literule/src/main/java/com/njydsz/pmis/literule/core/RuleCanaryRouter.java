package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 规则灰度路由器
 *
 * <p>当 {@link RuleDefinition#getCanaryRatio()} > 0 时，按比例将流量分到候选版本。
 *
 * <p>分流策略（双重过滤）：
 * <ol>
 *   <li>条件过滤：若 {@link RuleDefinition#getCanaryConditions()} 非空，
 *       则需全部条件表达式求值为 true 才进入候选桶</li>
 *   <li>比例分桶：通过 traceId 哈希 + 随机数，按 canaryRatio 比例决定是否进入候选桶</li>
 * </ol>
 *
 * <p>设计原则：
 * <ul>
 *   <li>同一 traceId 的分桶结果稳定（避免同一上下文在不同规则上分流不一致）</li>
 *   <li>当 canaryConditionExpression 为空时仅做条件过滤，不做版本对比</li>
 *   <li>分桶统计通过 {@link #getCanaryBucketStats} 暴露给运营监控</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
public class RuleCanaryRouter {

    private final ExpressionEvaluator evaluator;

    /** 灰度桶计数器：ruleCode -> {PRIMARY: count, CANARY: count} */
    private final ConcurrentMap<String, long[]> bucketCounts =
            new ConcurrentHashMap<>();

    public RuleCanaryRouter(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * 判断当前流量是否应进入灰度候选桶
     *
     * @param definition 规则定义
     * @param context    上下文
     * @return true=进入候选桶；false=走主版本
     */
    public boolean shouldRouteToCanary(RuleDefinition definition, RuleContext context) {
        if (definition == null || definition.getCanaryRatio() <= 0) {
            return false;
        }

        // 1. 条件过滤
        List<String> conditions = definition.getCanaryConditions();
        if (conditions != null && !conditions.isEmpty()) {
            for (String cond : conditions) {
                if (cond == null || cond.isBlank()) continue;
                try {
                    if (!evaluator.evalBoolean(cond, context)) {
                        return false;
                    }
                } catch (Exception e) {
                    log.debug("[LiteRule-Canary] 灰度条件求值失败 cond={}: {}", cond, e.getMessage());
                    return false;
                }
            }
        }

        // 2. 比例分桶：基于 traceId 哈希 + 随机扰动，保证稳定且均匀
        double ratio = Math.min(1.0, Math.max(0.0, definition.getCanaryRatio()));
        String traceId = context.getTraceId();
        int hash = traceId == null ? ThreadLocalRandom.current().nextInt() : traceId.hashCode();
        double bucket = ((hash & 0x7FFFFFFF) % 10000) / 10000.0;
        return bucket < ratio;
    }

    /**
     * 构建候选版本的临时规则定义
     *
     * <p>复制主版本定义，但用 canaryConditionExpression / canarySeverityExpression 覆盖。
     *
     * @param original 原始规则定义
     * @return 候选版本定义
     */
    public RuleDefinition buildCanaryDefinition(RuleDefinition original) {
        return RuleDefinition.builder()
                .code(original.getCode())
                .name(original.getName() + " [CANARY]")
                .category(original.getCategory())
                .description(original.getDescription())
                .conditionExpression(original.getCanaryConditionExpression() != null
                        ? original.getCanaryConditionExpression()
                        : original.getConditionExpression())
                .severityExpression(original.getCanarySeverityExpression() != null
                        ? original.getCanarySeverityExpression()
                        : original.getSeverityExpression())
                .defaultSeverity(original.getDefaultSeverity())
                .titleTemplate(original.getTitleTemplate())
                .descriptionTemplate(original.getDescriptionTemplate())
                .priority(original.getPriority())
                .enabled(true)
                .scope(original.getScope())
                .drilldownAvailable(original.isDrilldownAvailable())
                .version(original.getVersion())
                .status("PUBLISHED")
                .build();
    }

    /**
     * 评估候选版本（构造临时 ExpressionRule 并执行）
     *
     * <p>结果会被标记 {@link RuleResult#isCanary()} = true，canaryBucket = "CANARY"。
     * 由 {@link DefaultRuleEngine} 在确定进入灰度桶后调用。
     *
     * @param original 原始规则定义
     * @param context  规则上下文
     * @return 候选版本评估结果（不会返回 null）
     */
    public RuleResult evaluateCanary(RuleDefinition original, RuleContext context) {
        RuleDefinition canaryDef = buildCanaryDefinition(original);
        ExpressionRule canaryRule = new ExpressionRule(canaryDef, evaluator);
        RuleResult result;
        try {
            result = canaryRule.evaluate(context);
        } catch (Exception e) {
            log.warn("[LiteRule-Canary] 候选版本评估异常 ruleCode={}: {}", original.getCode(), e.getMessage());
            result = RuleResult.builder()
                    .ruleCode(original.getCode())
                    .triggered(false)
                    .description("灰度候选版本评估异常: " + e.getMessage())
                    .build();
        }
        if (result == null) {
            result = RuleResult.notTriggered(original.getCode());
        }
        result.setCanary(true);
        result.setCanaryBucket("CANARY");
        return result;
    }

    /**
     * 构建候选版本 Rule 实例（用于让 DefaultRuleEngine 统一走 timeoutExecutor 通道）
     *
     * @param original 原始规则定义
     * @return 候选版本 Rule
     * @since 1.4.0
     */
    public Rule buildCanaryRule(RuleDefinition original) {
        return new ExpressionRule(buildCanaryDefinition(original), evaluator);
    }

    /**
     * 给候选版本结果打上灰度标记
     *
     * @param result 候选版本结果
     */
    public void markCanary(RuleResult result) {
        if (result != null) {
            result.setCanary(true);
            result.setCanaryBucket("CANARY");
        }
    }

    /**
     * 记录分桶结果
     *
     * @param ruleCode 规则编码
     * @param canary   是否进入候选桶
     */
    public void recordBucket(String ruleCode, boolean canary) {
        long[] counts = bucketCounts.computeIfAbsent(ruleCode, k -> new long[2]);
        synchronized (counts) {
            counts[canary ? 1 : 0]++;
        }
    }

    /**
     * 获取分桶统计
     *
     * @return ruleCode -> [primaryCount, canaryCount]
     */
    public Map<String, long[]> getCanaryBucketStats() {
        return new HashMap<>(bucketCounts);
    }

    /**
     * 重置分桶统计
     */
    public void resetStats() {
        bucketCounts.clear();
    }
}
