package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认规则引擎实现
 *
 * <p>核心能力：
 * <ul>
 *   <li>规则注册/注销（线程安全 CopyOnWriteArrayList）</li>
 *   <li>按优先级编排执行（priority 数值越小越先执行）</li>
 *   <li>单规则异常隔离（不影响其他规则）</li>
 *   <li>结果按严重度倒序排列（RED → YELLOW → INFO）</li>
 *   <li>执行统计（执行次数/触发次数/异常次数/耗时）</li>
 *   <li>Dry-run 仿真（返回全部结果含未触发，不记录统计）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
public class DefaultRuleEngine implements RuleEngine {

    /** 已注册规则列表（按优先级排序） */
    private final CopyOnWriteArrayList<Rule> rules = new CopyOnWriteArrayList<>();

    /** 统计计数器 */
    private final AtomicLong totalEvaluations = new AtomicLong(0);
    private final AtomicLong totalTriggered = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalElapsedMs = new AtomicLong(0);

    /** 按规则编码的统计明细 */
    private final ConcurrentHashMap<String, RuleEngineStats.RuleStat> perRuleStats = new ConcurrentHashMap<>();

    @Override
    public void register(Rule rule) {
        if (rule == null || rule.getCode() == null) {
            return;
        }
        // 先移除同编码旧规则（支持热更新覆盖）
        unregister(rule.getCode());
        rules.add(rule);
        // 按优先级排序
        rules.sort(Comparator.comparingInt(Rule::getPriority));
        log.info("[LiteRule] 规则已注册: code={}, name={}, priority={}", rule.getCode(), rule.getName(), rule.getPriority());
    }

    @Override
    public void unregister(String ruleCode) {
        if (ruleCode == null) return;
        rules.removeIf(r -> ruleCode.equals(r.getCode()));
    }

    @Override
    public List<RuleResult> evaluate(RuleContext context) {
        List<RuleResult> triggered = new ArrayList<>();
        for (Rule rule : rules) {
            long start = System.nanoTime();
            try {
                RuleResult result = rule.evaluate(context);
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                recordStat(rule.getCode(), result != null && result.isTriggered(), false, elapsed);
                if (result != null && result.isTriggered()) {
                    triggered.add(result);
                }
            } catch (Exception e) {
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                recordStat(rule.getCode(), false, true, elapsed);
                log.warn("[LiteRule] 规则 {} 评估异常: {}", rule.getCode(), e.getMessage());
            }
        }
        // 按严重度倒序
        triggered.sort(Comparator.comparingInt((RuleResult r) -> severityWeight(r)).reversed());
        return triggered;
    }

    @Override
    public RuleResult topResult(RuleContext context) {
        List<RuleResult> all = evaluate(context);
        return all.isEmpty() ? null : all.get(0);
    }

    @Override
    public List<RuleResult> dryRun(RuleContext context) {
        List<RuleResult> all = new ArrayList<>();
        for (Rule rule : rules) {
            try {
                RuleResult result = rule.evaluate(context);
                if (result == null) {
                    result = RuleResult.notTriggered(rule.getCode());
                }
                all.add(result);
            } catch (Exception e) {
                all.add(RuleResult.builder()
                        .ruleCode(rule.getCode())
                        .triggered(false)
                        .description("评估异常: " + e.getMessage())
                        .build());
            }
        }
        return all;
    }

    @Override
    public List<Rule> getRules() {
        return List.copyOf(rules);
    }

    @Override
    public RuleEngineStats getStats() {
        Map<String, RuleEngineStats.RuleStat> snapshot = new ConcurrentHashMap<>();
        perRuleStats.forEach((k, v) -> snapshot.put(k, RuleEngineStats.RuleStat.builder()
                .executions(v.getExecutions())
                .triggered(v.getTriggered())
                .errors(v.getErrors())
                .totalElapsedMs(v.getTotalElapsedMs())
                .build()));
        return RuleEngineStats.builder()
                .totalEvaluations(totalEvaluations.get())
                .totalTriggered(totalTriggered.get())
                .totalErrors(totalErrors.get())
                .totalElapsedMs(totalElapsedMs.get())
                .perRuleStats(snapshot)
                .build();
    }

    /**
     * 重置统计
     */
    public void resetStats() {
        totalEvaluations.set(0);
        totalTriggered.set(0);
        totalErrors.set(0);
        totalElapsedMs.set(0);
        perRuleStats.clear();
    }

    /**
     * 记录统计
     *
     * @param ruleCode   规则编码
     * @param triggered  是否触发
     * @param error      是否异常
     * @param elapsedMs  耗时
     */
    private void recordStat(String ruleCode, boolean triggered, boolean error, long elapsedMs) {
        totalEvaluations.incrementAndGet();
        totalElapsedMs.addAndGet(elapsedMs);
        if (triggered) totalTriggered.incrementAndGet();
        if (error) totalErrors.incrementAndGet();
        perRuleStats.compute(ruleCode, (k, v) -> {
            if (v == null) v = RuleEngineStats.RuleStat.builder().build();
            v.setExecutions(v.getExecutions() + 1);
            if (triggered) v.setTriggered(v.getTriggered() + 1);
            if (error) v.setErrors(v.getErrors() + 1);
            v.setTotalElapsedMs(v.getTotalElapsedMs() + elapsedMs);
            return v;
        });
    }

    /**
     * 严重度权重
     *
     * @param result 规则结果
     * @return 权重值
     */
    private int severityWeight(RuleResult result) {
        if (result == null || result.getSeverity() == null) return 0;
        return result.getSeverity().getWeight();
    }
}
