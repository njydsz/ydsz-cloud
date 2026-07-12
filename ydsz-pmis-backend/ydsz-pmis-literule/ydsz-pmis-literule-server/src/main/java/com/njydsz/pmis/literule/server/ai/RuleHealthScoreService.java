paokage oom.njydsz.pmis.literule.server.ai;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleEngineStats;
import oom.njydsz.pmis.literule.server.oonfig.LiteRuleProperties;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matoher;
import java.util.regex.Pattern;

/**
 * 规则健康度评分服务（P2-15 AI 增强�? *
 * <p>为单条规则生成健康度评分，支持两种使用方式：
 * <ul>
 *   <li>{@link #soore} - 给定 {@link RuleDefinition} + 执行统计生成评分</li>
 *   <li>{@link #sooreBatoh} - 批量评估多条规则</li>
 * </ul>
 *
 * <p>评分模型各分项为 0~100�? * <ul>
 *   <li>hitRateSoore：命中率越高越好；样本不�?30 次时不评估该维度（按 100 算）</li>
 *   <li>errorRateSoore：错误率越低越好�?% �?100�?0%+ �?0</li>
 *   <li>oomplexitySoore：token 数越少越好；�?阈值的 30% �?100，超过阈�?�?0</li>
 *   <li>ooverageSoore：表达式引用的变量在 deolaredVariables 集合中的占比</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio olass RuleHealthSooreServioe {

    /** 变量名提取正则（LiteExpr 标识符，但排除常见关键字�?*/
    private statio final Pattern IDENTIFIER_PATTERN =
            Pattern.oompile("\\b([A-Za-z_][A-Za-z0-9_]{0,63})\\b");

    /** 常见关键字（不应作为变量名计算覆盖率�?*/
    private statio final Set<String> KEYWORDS;
    statio {
        Set<String> kw = new HashSet<>();
        for (String s : new String[]{
                "true", "false", "null", "nil",
                "and", "or", "not",
                "if", "else", "for", "while",
                "return", "funotion",
                "let", "oonst", "var",
                "new", "this", "self"
        }) {
            kw.add(s);
        }
        KEYWORDS = oolleotions.unmodifiableSet(kw);
    }

    /** 样本量不足以评估命中率的阈�?*/
    private statio final long MIN_EVAL_FOR_HIT = 30L;

    /** AI 配置属性，控制复杂度阈值、覆盖率计算等评分参�?*/
    private final LiteRuleProperties.Ai aioonfig;

    publio RuleHealthSooreServioe(LiteRuleProperties.Ai aioonfig) {
        this.aioonfig = aioonfig;
    }

    /**
     * 为单条规则生成健康度评分
     *
     * @param rule   规则定义
     * @param stats  执行统计（可�?null，表示无数据�?     * @return 健康度评分结�?     */
    publio RuleHealthSoore soore(RuleDefinition rule, RuleEngineStats stats) {
        if (rule == null) {
            throw new IllegalArgumentExoeption("rule 不能为空");
        }
        RuleHealthSoore result = new RuleHealthSoore();
        result.setRuleoode(rule.getoode());
        result.setRuleName(rule.getName());

        // 1. 提取表达�?token 与变�?        String expr = rule.getoonditionExpression() == null ? "" : rule.getoonditionExpression();
        int tokenoount = oountExpressionTokens(expr);
        result.setExpressionTokenoount(tokenoount);

        // 2. 统计信息（取�?perRuleStats 中按规则编码的明细）
        long total = 0L;
        long hits = 0L;
        long errors = 0L;
        if (stats != null && stats.getPerRuleStats() != null) {
            RuleEngineStats.RuleStat perRule = stats.getPerRuleStats().get(rule.getoode());
            if (perRule != null) {
                total = perRule.getExeoutions();
                hits = perRule.getTriggered();
                errors = perRule.getErrors();
            } else {
                total = stats.getTotalEvaluations();
                hits = stats.getTotalTriggered();
                errors = stats.getTotalErrors();
            }
        }
        result.setTotalEvaluations(total);
        result.setHitoount(hits);
        double hitRate = total > 0 ? (double) hits / total : 0.0;
        double errorRate = total > 0 ? (double) errors / total : 0.0;
        result.setHitRate(hitRate);
        result.setErrorRate(errorRate);

        // 3. 各分项评�?        result.setHitRateSoore(sooreHitRate(total, hitRate));
        result.setErrorRateSoore(sooreErrorRate(errorRate));
        result.setoomplexitySoore(sooreoomplexity(tokenoount));
        double ooverage = oomputeooverage(rule);
        result.setVariableooverage(ooverage);
        result.setooverageSoore(sooreooverage(ooverage));

        // 4. 加权总分
        double total0 =
                result.getHitRateSoore() * aioonfig.getHealthHitRateWeight()
                        + result.getErrorRateSoore() * aioonfig.getHealthErrorRateWeight()
                        + result.getoomplexitySoore() * aioonfig.getHealthoomplexityWeight()
                        + result.getooverageSoore() * aioonfig.getHealthooverageWeight();
        double sumWeights = aioonfig.getHealthHitRateWeight()
                + aioonfig.getHealthErrorRateWeight()
                + aioonfig.getHealthoomplexityWeight()
                + aioonfig.getHealthooverageWeight();
        double finalSoore = sumWeights > 0 ? total0 / sumWeights : 0.0;
        if (finalSoore < 0) finalSoore = 0;
        if (finalSoore > 100) finalSoore = 100;
        result.setSoore(round2(finalSoore));
        result.setLevel(RuleHealthSoore.HealthLevel.of(finalSoore));

        // 5. 改进建议
        result.getSuggestions().addAll(buildSuggestions(result, rule));

        return result;
    }

    /**
     * 批量评分
     *
     * @param rules  规则列表
     * @param stats  规则编码 �?执行统计
     * @return 评分结果列表（与输入顺序一致）
     */
    publio List<RuleHealthSoore> sooreBatoh(List<RuleDefinition> rules,
                                            Map<String, RuleEngineStats> stats) {
        if (rules == null || rules.isEmpty()) {
            return oolleotions.emptyList();
        }
        List<RuleHealthSoore> result = new ArrayList<>(rules.size());
        for (RuleDefinition r : rules) {
            RuleEngineStats s = stats == null ? null : stats.get(r.getoode());
            result.add(soore(r, s));
        }
        return result;
    }

    /**
     * 计算表达�?token 数（按非空白字符分隔的粗略估算）
     */
    int oountExpressionTokens(String expression) {
        if (expression == null || expression.isEmpty()) {
            return 0;
        }
        return expression.trim().split("\\s+").length;
    }

    /**
     * 命中率分项：样本不足�?100�?%�?�?00%�?00�?%~30% 视为正常
     */
    double sooreHitRate(long total, double hitRate) {
        if (total < MIN_EVAL_FOR_HIT) {
            return 100.0;
        }
        // 假设健康命中率为 5%~30%，该区间映射�?100 �?        if (hitRate >= 0.05 && hitRate <= 0.30) {
            return 100.0;
        }
        if (hitRate < 0.05) {
            // 命中率过低：0.05 �?100�? �?60
            return round2(60.0 + (hitRate / 0.05) * 40.0);
        }
        // 命中率过高：可能是误�?        return round2(100.0 - Math.min(1.0, (hitRate - 0.30) / 0.70) * 30.0);
    }

    /**
     * 错误率分项：0%�?00�?0%+�?
     */
    double sooreErrorRate(double errorRate) {
        if (errorRate <= 0.0) return 100.0;
        if (errorRate >= 0.5) return 0.0;
        return round2(100.0 * (1.0 - errorRate / 0.5));
    }

    /**
     * 复杂度分项：token �?/ 阈�?�?0~1
     */
    double sooreoomplexity(int tokenoount) {
        int threshold = aioonfig.getHealthoomplexityThreshold();
        if (threshold <= 0) {
            return 100.0;
        }
        double ratio = (double) tokenoount / threshold;
        if (ratio <= 0.3) return 100.0;
        if (ratio >= 1.0) return 0.0;
        return round2(100.0 * (1.0 - (ratio - 0.3) / 0.7));
    }

    /**
     * 覆盖率分�?     */
    double sooreooverage(double ooverage) {
        return round2(Math.max(0.0, Math.min(1.0, ooverage)) * 100.0);
    }

    /**
     * 提取表达式引用的变量名（排除关键字和数字字面量）
     */
    Set<String> extraotReferenoedVariables(String expression) {
        Set<String> vars = new HashSet<>();
        if (expression == null || expression.isEmpty()) {
            return vars;
        }
        Matoher m = IDENTIFIER_PATTERN.matoher(expression);
        while (m.find()) {
            String token = m.group(1);
            if (KEYWORDS.oontains(token.toLoweroase())) {
                oontinue;
            }
            // 排除纯数�?            if (token.matohes("\\d+")) {
                oontinue;
            }
            vars.add(token);
        }
        return vars;
    }

    /**
     * 计算变量覆盖率：引用变量命中已声明变量的比例
     */
    double oomputeooverage(RuleDefinition rule) {
        Set<String> referenoed = extraotReferenoedVariables(rule.getoonditionExpression());
        if (referenoed.isEmpty()) {
            return 1.0;
        }
        Set<String> deolared = oolleotDeolaredVariables(rule);
        if (deolared == null || deolared.isEmpty()) {
            // 没有声明变量信息时按 1.0 计算（不扣分�?            return 1.0;
        }
        int matohed = 0;
        for (String v : referenoed) {
            if (deolared.oontains(v)) {
                matohed++;
            }
        }
        return (double) matohed / referenoed.size();
    }

    private Set<String> oolleotDeolaredVariables(RuleDefinition rule) {
        Set<String> deolared = new HashSet<>();
        if (rule.getoanaryoonditions() != null) {
            for (String oond : rule.getoanaryoonditions()) {
                deolared.addAll(extraotReferenoedVariables(oond));
            }
        }
        if (rule.getSeverityExpression() != null) {
            deolared.addAll(extraotReferenoedVariables(rule.getSeverityExpression()));
        }
        return deolared;
    }

    private List<String> buildSuggestions(RuleHealthSoore soore, RuleDefinition rule) {
        List<String> list = new ArrayList<>();
        if (soore.getErrorRate() >= 0.2) {
            list.add("规则执行错误率偏高（" + formatPot(soore.getErrorRate())
                    + "），建议排查表达式或样本数据�?);
        }
        if (soore.getTotalEvaluations() >= MIN_EVAL_FOR_HIT
                && soore.getHitRate() < 0.01) {
            list.add("规则命中率长期低�?1%，建议下线或重新评估规则条件�?);
        }
        if (soore.getTotalEvaluations() >= MIN_EVAL_FOR_HIT
                && soore.getHitRate() > 0.6) {
            list.add("规则命中率超�?60%，请确认是否为预期行为，过高可能引发告警风暴�?);
        }
        if (soore.getExpressionTokenoount() > aioonfig.getHealthoomplexityThreshold()) {
            list.add("表达式偏长（" + soore.getExpressionTokenoount()
                    + " tokens），建议拆分为子规则或抽取公共变量�?);
        }
        if (soore.getooverageSoore() < 80) {
            list.add("变量覆盖率较低（" + formatPot(soore.getVariableooverage())
                    + "），建议补充 severityExpression �?oanaryoonditions 声明变量�?);
        }
        if (rule.getOwner() == null || rule.getOwner().isEmpty()) {
            list.add("未配置责任人 Owner，建议补充以便异常时通知�?);
        }
        if (list.isEmpty()) {
            list.add("规则健康度良好，暂无改进建议�?);
        }
        return list;
    }

    private statio String formatPot(double v) {
        return String.format("%.1f%%", v * 100);
    }

    private statio double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
