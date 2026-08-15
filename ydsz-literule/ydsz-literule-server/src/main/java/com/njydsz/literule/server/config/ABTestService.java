package com.njydsz.literule.server.config;

import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.expr.ExpressionEvaluator;
import com.njydsz.literule.server.impl.ExpressionRule;

import lombok.extern.slf4j.Slf4j;

/**
 * 规则 A/B 测试服务
 *
 * <p>对同一事实数据并行评估当前规则版本和候选规则版本，对比差异，
 * 用于规则变更前的安全验证。
 *
 * <p>对比维度：
 * <ul>
 *   <li>触发状态（triggered）差异</li>
 *   <li>严重度（severity）差异</li>
 *   <li>标题（title）差异</li>
 *   <li>描述（description）差异</li>
 * </ul>
 *
 * <p>典型场景：
 * <ul>
 *   <li>规则条件表达式调整后，验证是否产生意外触发/未触发</li>
 *   <li>严重度表达式调整后，验证严重度变化范围</li>
 *   <li>模板调整后，验证标题/描述输出是否符合预期</li>
 * </ul>
 *
 * @author ydsz-team
 *
 * @since 1.0.0
 */
@Slf4j
public class ABTestService {

    private final ExpressionEvaluator evaluator;

    /**
     * 构造 A/B 测试服务
     *
     * @param evaluator 表达式求值器
     */
    public ABTestService(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * 执行 A/B 测试
     *
     * <p>对同一 facts 分别用当前规则定义和候选规则定义评估，返回对比报告。
     *
     * @param currentDef   当前规则定义
     * @param candidateDef 候选规则定义
     * @param facts        事实数据
     * @return A/B 测试报告
     */
    public ABTestReport test(RuleDefinition currentDef, RuleDefinition candidateDef,
                              Map<String, Object> facts) {
        RuleContext context = RuleContext.of(facts, "AB_TEST", "MANUAL");

        // 评估当前版本
        ExpressionRule currentRule = new ExpressionRule(currentDef, evaluator);
        RuleResult currentResult = currentRule.evaluate(context);

        // 评估候选版本
        ExpressionRule candidateRule = new ExpressionRule(candidateDef, evaluator);
        RuleResult candidateResult = candidateRule.evaluate(context);

        // 对比分析
        boolean triggeredChanged = currentResult.isTriggered() != candidateResult.isTriggered();
        boolean severityChanged = currentResult.getSeverity() != candidateResult.getSeverity();
        boolean titleChanged = !safeEquals(currentResult.getTitle(), candidateResult.getTitle());
        boolean descriptionChanged = !safeEquals(currentResult.getDescription(), candidateResult.getDescription());

        boolean hasDiff = triggeredChanged || severityChanged || titleChanged || descriptionChanged;

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("triggeredChanged", triggeredChanged);
        diff.put("severityChanged", severityChanged);
        diff.put("titleChanged", titleChanged);
        diff.put("descriptionChanged", descriptionChanged);
        diff.put("hasDiff", hasDiff);

        if (triggeredChanged) {
            diff.put("triggeredBefore", currentResult.isTriggered());
            diff.put("triggeredAfter", candidateResult.isTriggered());
        }
        if (severityChanged) {
            diff.put("severityBefore", currentResult.getSeverity());
            diff.put("severityAfter", candidateResult.getSeverity());
        }

        String summary = String.format("A/B 测试完成：%s",
                hasDiff ? "检测到差异" : "无差异");

        log.info("[LiteRule] A/B 测试: ruleCode={}, hasDiff={}, triggeredChanged={}, severityChanged={}",
                currentDef.getCode(), hasDiff, triggeredChanged, severityChanged);

        return new ABTestReport(
                currentDef.getCode(),
                currentDef.getVersion(),
                candidateDef.getVersion(),
                currentResult,
                candidateResult,
                diff,
                summary
        );
    }

    /**
     * 安全字符串比较（null-safe）
     *
     * @param a 字符串 a
     * @param b 字符串 b
     * @return true=相等
     */
    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * A/B 测试报告
     *
     * @param ruleCode       规则编码
     * @param currentVersion 当前版本号
     * @param candidateVersion 候选版本号
     * @param currentResult  当前版本评估结果
     * @param candidateResult 候选版本评估结果
     * @param diff           差异详情
     * @param summary        摘要
     */
    public record ABTestReport(
            String ruleCode,
            int currentVersion,
            int candidateVersion,
            RuleResult currentResult,
            RuleResult candidateResult,
            Map<String, Object> diff,
            String summary
    ) {}
}
