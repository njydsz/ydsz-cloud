package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.config.LiteRuleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则健康度评分服务（P2-15 AI 增强）
 *
 * <p>为单条规则生成健康度评分，支持两种使用方式：
 * <ul>
 *   <li>{@link #score} - 给定 {@link RuleDefinition} + 执行统计生成评分</li>
 *   <li>{@link #scoreBatch} - 批量评估多条规则</li>
 * </ul>
 *
 * <p>评分模型各分项为 0~100：
 * <ul>
 *   <li>hitRateScore：命中率越高越好；样本不足 30 次时不评估该维度（按 100 算）</li>
 *   <li>errorRateScore：错误率越低越好；0% → 100，50%+ → 0</li>
 *   <li>complexityScore：token 数越少越好；≤ 阈值的 30% → 100，超过阈值 → 0</li>
 *   <li>coverageScore：表达式引用的变量在 declaredVariables 集合中的占比</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class RuleHealthScoreService {

    private static final Logger log = LoggerFactory.getLogger(RuleHealthScoreService.class);

    /** 变量名提取正则（Aviator 标识符，但排除常见关键字） */
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]{0,63})\\b");

    /** 常见关键字（不应作为变量名计算覆盖率） */
    private static final Set<String> KEYWORDS;
    static {
        Set<String> kw = new HashSet<>();
        for (String s : new String[]{
                "true", "false", "null", "nil",
                "and", "or", "not",
                "if", "else", "for", "while",
                "return", "function",
                "let", "const", "var",
                "new", "this", "self"
        }) {
            kw.add(s);
        }
        KEYWORDS = Collections.unmodifiableSet(kw);
    }

    /** 样本量不足以评估命中率的阈值 */
    private static final long MIN_EVAL_FOR_HIT = 30L;

    private final LiteRuleProperties.Ai aiConfig;

    public RuleHealthScoreService(LiteRuleProperties.Ai aiConfig) {
        this.aiConfig = aiConfig;
    }

    /**
     * 为单条规则生成健康度评分
     *
     * @param rule   规则定义
     * @param stats  执行统计（可为 null，表示无数据）
     * @return 健康度评分结果
     */
    public RuleHealthScore score(RuleDefinition rule, RuleEngineStats stats) {
        if (rule == null) {
            throw new IllegalArgumentException("rule 不能为空");
        }
        RuleHealthScore result = new RuleHealthScore();
        result.setRuleCode(rule.getCode());
        result.setRuleName(rule.getName());

        // 1. 提取表达式 token 与变量
        String expr = rule.getConditionExpression() == null ? "" : rule.getConditionExpression();
        int tokenCount = countExpressionTokens(expr);
        result.setExpressionTokenCount(tokenCount);

        // 2. 统计信息（取自 perRuleStats 中按规则编码的明细）
        long total = 0L;
        long hits = 0L;
        long errors = 0L;
        if (stats != null && stats.getPerRuleStats() != null) {
            RuleEngineStats.RuleStat perRule = stats.getPerRuleStats().get(rule.getCode());
            if (perRule != null) {
                total = perRule.getExecutions();
                hits = perRule.getTriggered();
                errors = perRule.getErrors();
            } else {
                total = stats.getTotalEvaluations();
                hits = stats.getTotalTriggered();
                errors = stats.getTotalErrors();
            }
        }
        result.setTotalEvaluations(total);
        result.setHitCount(hits);
        double hitRate = total > 0 ? (double) hits / total : 0.0;
        double errorRate = total > 0 ? (double) errors / total : 0.0;
        result.setHitRate(hitRate);
        result.setErrorRate(errorRate);

        // 3. 各分项评分
        result.setHitRateScore(scoreHitRate(total, hitRate));
        result.setErrorRateScore(scoreErrorRate(errorRate));
        result.setComplexityScore(scoreComplexity(tokenCount));
        double coverage = computeCoverage(rule);
        result.setVariableCoverage(coverage);
        result.setCoverageScore(scoreCoverage(coverage));

        // 4. 加权总分
        double total0 =
                result.getHitRateScore() * aiConfig.getHealthHitRateWeight()
                        + result.getErrorRateScore() * aiConfig.getHealthErrorRateWeight()
                        + result.getComplexityScore() * aiConfig.getHealthComplexityWeight()
                        + result.getCoverageScore() * aiConfig.getHealthCoverageWeight();
        double sumWeights = aiConfig.getHealthHitRateWeight()
                + aiConfig.getHealthErrorRateWeight()
                + aiConfig.getHealthComplexityWeight()
                + aiConfig.getHealthCoverageWeight();
        double finalScore = sumWeights > 0 ? total0 / sumWeights : 0.0;
        if (finalScore < 0) finalScore = 0;
        if (finalScore > 100) finalScore = 100;
        result.setScore(round2(finalScore));
        result.setLevel(RuleHealthScore.HealthLevel.of(finalScore));

        // 5. 改进建议
        result.getSuggestions().addAll(buildSuggestions(result, rule));

        return result;
    }

    /**
     * 批量评分
     *
     * @param rules  规则列表
     * @param stats  规则编码 → 执行统计
     * @return 评分结果列表（与输入顺序一致）
     */
    public List<RuleHealthScore> scoreBatch(List<RuleDefinition> rules,
                                            java.util.Map<String, RuleEngineStats> stats) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }
        List<RuleHealthScore> result = new ArrayList<>(rules.size());
        for (RuleDefinition r : rules) {
            RuleEngineStats s = stats == null ? null : stats.get(r.getCode());
            result.add(score(r, s));
        }
        return result;
    }

    /**
     * 计算表达式 token 数（按非空白字符分隔的粗略估算）
     */
    int countExpressionTokens(String expression) {
        if (expression == null || expression.isEmpty()) {
            return 0;
        }
        return expression.trim().split("\\s+").length;
    }

    /**
     * 命中率分项：样本不足按 100；0%→0；100%→100；5%~30% 视为正常
     */
    double scoreHitRate(long total, double hitRate) {
        if (total < MIN_EVAL_FOR_HIT) {
            return 100.0;
        }
        // 假设健康命中率为 5%~30%，该区间映射到 100 分
        if (hitRate >= 0.05 && hitRate <= 0.30) {
            return 100.0;
        }
        if (hitRate < 0.05) {
            // 命中率过低：0.05 → 100，0 → 60
            return round2(60.0 + (hitRate / 0.05) * 40.0);
        }
        // 命中率过高：可能是误报
        return round2(100.0 - Math.min(1.0, (hitRate - 0.30) / 0.70) * 30.0);
    }

    /**
     * 错误率分项：0%→100；50%+→0
     */
    double scoreErrorRate(double errorRate) {
        if (errorRate <= 0.0) return 100.0;
        if (errorRate >= 0.5) return 0.0;
        return round2(100.0 * (1.0 - errorRate / 0.5));
    }

    /**
     * 复杂度分项：token 数 / 阈值 → 0~1
     */
    double scoreComplexity(int tokenCount) {
        int threshold = aiConfig.getHealthComplexityThreshold();
        if (threshold <= 0) {
            return 100.0;
        }
        double ratio = (double) tokenCount / threshold;
        if (ratio <= 0.3) return 100.0;
        if (ratio >= 1.0) return 0.0;
        return round2(100.0 * (1.0 - (ratio - 0.3) / 0.7));
    }

    /**
     * 覆盖率分项
     */
    double scoreCoverage(double coverage) {
        return round2(Math.max(0.0, Math.min(1.0, coverage)) * 100.0);
    }

    /**
     * 提取表达式引用的变量名（排除关键字和数字字面量）
     */
    Set<String> extractReferencedVariables(String expression) {
        Set<String> vars = new HashSet<>();
        if (expression == null || expression.isEmpty()) {
            return vars;
        }
        Matcher m = IDENTIFIER_PATTERN.matcher(expression);
        while (m.find()) {
            String token = m.group(1);
            if (KEYWORDS.contains(token.toLowerCase())) {
                continue;
            }
            // 排除纯数字
            if (token.matches("\\d+")) {
                continue;
            }
            vars.add(token);
        }
        return vars;
    }

    /**
     * 计算变量覆盖率：引用变量命中已声明变量的比例
     */
    double computeCoverage(RuleDefinition rule) {
        Set<String> referenced = extractReferencedVariables(rule.getConditionExpression());
        if (referenced.isEmpty()) {
            return 1.0;
        }
        Set<String> declared = collectDeclaredVariables(rule);
        if (declared == null || declared.isEmpty()) {
            // 没有声明变量信息时按 1.0 计算（不扣分）
            return 1.0;
        }
        int matched = 0;
        for (String v : referenced) {
            if (declared.contains(v)) {
                matched++;
            }
        }
        return (double) matched / referenced.size();
    }

    private Set<String> collectDeclaredVariables(RuleDefinition rule) {
        Set<String> declared = new HashSet<>();
        if (rule.getCanaryConditions() != null) {
            for (String cond : rule.getCanaryConditions()) {
                declared.addAll(extractReferencedVariables(cond));
            }
        }
        if (rule.getSeverityExpression() != null) {
            declared.addAll(extractReferencedVariables(rule.getSeverityExpression()));
        }
        return declared;
    }

    private List<String> buildSuggestions(RuleHealthScore score, RuleDefinition rule) {
        List<String> list = new ArrayList<>();
        if (score.getErrorRate() >= 0.2) {
            list.add("规则执行错误率偏高（" + formatPct(score.getErrorRate())
                    + "），建议排查表达式或样本数据。");
        }
        if (score.getTotalEvaluations() >= MIN_EVAL_FOR_HIT
                && score.getHitRate() < 0.01) {
            list.add("规则命中率长期低于 1%，建议下线或重新评估规则条件。");
        }
        if (score.getTotalEvaluations() >= MIN_EVAL_FOR_HIT
                && score.getHitRate() > 0.6) {
            list.add("规则命中率超过 60%，请确认是否为预期行为，过高可能引发告警风暴。");
        }
        if (score.getExpressionTokenCount() > aiConfig.getHealthComplexityThreshold()) {
            list.add("表达式偏长（" + score.getExpressionTokenCount()
                    + " tokens），建议拆分为子规则或抽取公共变量。");
        }
        if (score.getCoverageScore() < 80) {
            list.add("变量覆盖率较低（" + formatPct(score.getVariableCoverage())
                    + "），建议补充 severityExpression 或 canaryConditions 声明变量。");
        }
        if (rule.getOwner() == null || rule.getOwner().isEmpty()) {
            list.add("未配置责任人 Owner，建议补充以便异常时通知。");
        }
        if (list.isEmpty()) {
            list.add("规则健康度良好，暂无改进建议。");
        }
        return list;
    }

    private static String formatPct(double v) {
        return String.format("%.1f%%", v * 100);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
