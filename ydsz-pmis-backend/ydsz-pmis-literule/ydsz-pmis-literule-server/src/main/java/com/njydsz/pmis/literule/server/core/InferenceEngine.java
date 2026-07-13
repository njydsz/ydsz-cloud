package com.njydsz.pmis.literule.server.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.server.impl.ExpressionRule;

import lombok.extern.slf4j.Slf4j;

/**
 * 轻量级前向链推理引擎（2.0.0）
 *
 * <p>对标 Drools 的前向链推理（Forward Chaining）能力，支持：
 * <ul>
 *   <li><b>事实修改</b>：规则动作可以修改上下文中的事实（facts），修改后触发新一轮推理</li>
 *   <li><b>级联触发</b>：规则 A 的结果可能触发规则 B，规则 B 的结果可能触发规则 C</li>
 *   <li><b>循环防护</b>：最大推理轮次限制，防止无限循环</li>
 *   <li><b>推理追踪</b>：记录每轮触发的规则链，用于归因分析</li>
 * </ul>
 *
 * <h3>工作流程</h3>
 * <pre>
 * 初始事实 → [Round 1] 评估全部规则 → 触发规则修改事实 → [Round 2] 重新评估 → ... → 收敛
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>
 * InferenceEngine engine = new InferenceEngine(evaluator);
 * engine.register(rule1);  // 规则1：amount > 1000 → set level = "HIGH"
 * engine.register(rule2);  // 规则2：level == "HIGH" → set alert = true
 *
 * Map&lt;String, Object&gt; facts = new HashMap&lt;&gt;();
 * facts.put("amount", 1500);
 * InferenceResult result = engine.infer(facts);
 * // result.getFacts() → {amount=1500, level=HIGH, alert=true}
 * // result.getTrace() → [Round1: rule1, Round2: rule2]
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Slf4j
public class InferenceEngine {

    /** 默认最大推理轮次 */
    private static final int DEFAULT_MAX_ROUNDS = 10;

    private final ExpressionEvaluator evaluator;
    private final List<Rule> rules = new ArrayList<>();
    private int maxRounds = DEFAULT_MAX_ROUNDS;

    /**
     * 构造推理引擎
     *
     * @param evaluator 表达式求值器
     */
    public InferenceEngine(ExpressionEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator 不能为 null");
    }

    /**
     * 注册规则
     *
     * @param rule 规则
     */
    public void register(Rule rule) {
        if (rule != null) {
            rules.add(rule);
        }
    }

    /**
     * 从规则定义注册规则
     *
     * @param definition 规则定义
     */
    public void register(RuleDefinition definition) {
        if (definition != null && definition.isEnabled()) {
            register(new ExpressionRule(definition, evaluator));
        }
    }

    /**
     * 执行前向链推理
     *
     * <p>推理流程：
     * <ol>
     *   <li>使用初始事实评估全部规则</li>
     *   <li>触发的规则可以修改事实（通过 {@link RuleResult#getActions()} 或副作用）</li>
     *   <li>如果事实发生变化，重新评估全部规则</li>
     *   <li>重复直到无新规则触发或达到最大轮次</li>
     * </ol>
     *
     * @param facts 初始事实
     * @return 推理结果（含最终事实和推理轨迹）
     */
    public InferenceResult infer(Map<String, Object> facts) {
        Objects.requireNonNull(facts, "facts 不能为 null");

        // 可变事实副本
        Map<String, Object> workingFacts = new HashMap<>(facts);
        List<InferenceRound> trace = new ArrayList<>();
        int round = 0;
        boolean changed = true;

        while (changed && round < maxRounds) {
            round++;
            changed = false;
            List<String> triggeredRules = new ArrayList<>();
            List<RuleResult> roundResults = new ArrayList<>();

            RuleContext context = RuleContext.of(workingFacts, "INFERENCE", "FORWARD_CHAIN");

            for (Rule rule : rules) {
                try {
                    RuleResult result = rule.evaluate(context);
                    if (result != null && result.isTriggered()) {
                        triggeredRules.add(rule.getCode());
                        roundResults.add(result);
                    }
                } catch (Exception e) {
                    log.warn("[Inference] 规则 {} 评估异常 (round={}): {}", rule.getCode(), round, e.getMessage());
                }
            }

            // 检查事实是否被规则修改（通过副作用）
            // 规则可以通过修改 context.getFacts() 中的 Map 来修改事实
            // 由于 workingFacts 被直接传入 RuleContext，修改会反映到 workingFacts 中
            // 如果有规则触发，可能修改了事实，需要继续下一轮推理
            changed = !triggeredRules.isEmpty();

            InferenceRound roundInfo = new InferenceRound(round, triggeredRules, roundResults);
            trace.add(roundInfo);

            log.debug("[Inference] Round {}: triggered={}, changed={}",
                    round, triggeredRules, changed);

            if (triggeredRules.isEmpty()) {
                break;
            }
        }

        if (round >= maxRounds && changed) {
            log.warn("[Inference] 达到最大推理轮次 {}，可能存在循环触发", maxRounds);
        }

        return new InferenceResult(workingFacts, trace, round);
    }

    /**
     * 设置最大推理轮次
     *
     * @param maxRounds 最大轮次（建议 5-20）
     */
    public void setMaxRounds(int maxRounds) {
        this.maxRounds = Math.max(1, maxRounds);
    }

    /**
     * 获取已注册规则数量
     *
     * @return 规则数量
     */
    public int ruleCount() {
        return rules.size();
    }

    /**
     * 推理结果
     */
    public static class InferenceResult {
        private final Map<String, Object> facts;
        private final List<InferenceRound> trace;
        private final int totalRounds;

        public InferenceResult(Map<String, Object> facts, List<InferenceRound> trace, int totalRounds) {
            this.facts = facts;
            this.trace = trace;
            this.totalRounds = totalRounds;
        }

        public Map<String, Object> getFacts() {
            return facts;
        }

        public List<InferenceRound> getTrace() {
            return trace;
        }

        public int getTotalRounds() {
            return totalRounds;
        }

        /**
         * 获取所有触发的规则编码列表（按轮次顺序）
         *
         * @return 规则编码列表
         */
        public List<String> getAllTriggeredRules() {
            List<String> all = new ArrayList<>();
            for (InferenceRound round : trace) {
                all.addAll(round.triggeredRules);
            }
            return all;
        }
    }

    /**
     * 推理轮次信息
     */
    public static class InferenceRound {
        private final int round;
        private final List<String> triggeredRules;
        private final List<RuleResult> results;

        public InferenceRound(int round, List<String> triggeredRules, List<RuleResult> results) {
            this.round = round;
            this.triggeredRules = triggeredRules;
            this.results = results;
        }

        public int getRound() {
            return round;
        }

        public List<String> getTriggeredRules() {
            return triggeredRules;
        }

        public List<RuleResult> getResults() {
            return results;
        }
    }
}
