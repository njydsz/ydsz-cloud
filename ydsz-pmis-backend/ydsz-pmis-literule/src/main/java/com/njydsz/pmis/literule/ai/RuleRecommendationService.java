package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.config.LiteRuleProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则推荐服务（P2-15 AI 增强）
 *
 * <p>基于现有规则 + 历史执行数据，使用以下启发式算法生成推荐：
 * <ol>
 *   <li>字段补全：提取所有规则表达式中的高频变量，对"出现次数少但被多规则引用"的字段推荐新规则</li>
 *   <li>模式重复：条件高度相似（共用 ≥3 变量）但严重度不同的规则提示合并</li>
 *   <li>变体衍生：对低命中率规则按其类别衍生阈值变体规则</li>
 *   <li>健康度拆分：对错误率高的规则建议拆分为子规则</li>
 * </ol>
 *
 * <p>所有推荐结果按 score 降序返回，截断至配置 topN。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class RuleRecommendationService {

    /** 变量名提取正则 */
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]{0,63})\\b");

    /** 关键字白名单（不参与变量统计） */
    private static final Set<String> KEYWORDS;
    static {
        Set<String> kw = new HashSet<>();
        for (String s : new String[]{
                "true", "false", "null", "nil",
                "and", "or", "not",
                "if", "else",
                "return"
        }) {
            kw.add(s);
        }
        KEYWORDS = Collections.unmodifiableSet(kw);
    }

    /** AI 配置属性，控制推荐结果 topN、相似度阈值等参数 */
    private final LiteRuleProperties.Ai aiConfig;

    public RuleRecommendationService(LiteRuleProperties.Ai aiConfig) {
        this.aiConfig = aiConfig;
    }

    /**
     * 为指定规则生成推荐
     *
     * @param source  源规则
     * @param all     候选上下文（同一业务域内的所有规则，用于共现/重复检测）
     * @param stats   规则编码 → 执行统计
     * @return 推荐结果（按 score 降序）
     */
    public List<RuleRecommendation> recommend(RuleDefinition source,
                                              List<RuleDefinition> all,
                                              Map<String, RuleEngineStats> stats) {
        if (source == null) {
            return Collections.emptyList();
        }
        List<RuleDefinition> context = all == null ? Collections.emptyList() : all;
        Map<String, RuleEngineStats> statsMap = stats == null ? Collections.emptyMap() : stats;

        List<RuleRecommendation> result = new ArrayList<>();
        result.addAll(fieldCompletion(source, context));
        result.addAll(duplicationDetection(source, context));
        result.addAll(variantSuggestion(source, statsMap.get(source.getCode())));
        result.addAll(splitSuggestion(source, statsMap.get(source.getCode())));

        // 按 score 降序
        result.sort(Comparator.comparingDouble(RuleRecommendation::getScore).reversed());
        int topN = aiConfig.getRecommendTopN();
        if (result.size() > topN) {
            return new ArrayList<>(result.subList(0, topN));
        }
        return result;
    }

    /**
     * 字段补全：基于同一类别下的高频变量推荐"该字段触发的独立规则"
     */
    List<RuleRecommendation> fieldCompletion(RuleDefinition source, List<RuleDefinition> context) {
        if (context.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Integer> varCount = new HashMap<>();
        for (RuleDefinition r : context) {
            if (r == null || r.getConditionExpression() == null) {
                continue;
            }
            Set<String> vars = extractVars(r.getConditionExpression());
            for (String v : vars) {
                varCount.merge(v, 1, (a, b) -> a + b);
            }
        }
        if (varCount.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> sourceVars = extractVars(source.getConditionExpression());
        List<RuleRecommendation> recs = new ArrayList<>();
        // 在 source 中未出现、但被 ≥3 条规则共用的字段
        for (Map.Entry<String, Integer> e : varCount.entrySet()) {
            String var = e.getKey();
            int count = e.getValue();
            if (sourceVars.contains(var)) {
                continue;
            }
            if (count < 3) {
                continue;
            }
            RuleRecommendation rec = new RuleRecommendation();
            rec.setSuggestedCode(source.getCode() + "-rec-" + rec.hashCode() % 1000);
            rec.setSuggestedName("新增 " + var + " 监控规则");
            rec.setSuggestedExpression(var + " != null");
            rec.setSuggestedSeverity("YELLOW");
            rec.setRationale("字段 [" + var + "] 在 " + count + " 条规则中被引用，建议建立独立监控。");
            rec.setScore(Math.min(1.0, count / 10.0));
            rec.setType(RuleRecommendation.RecommendationType.FIELD_COMPLETION);
            recs.add(rec);
        }
        return recs;
    }

    /**
     * 重复检测：与 source 共享 ≥3 变量的规则提示存在逻辑重叠
     */
    List<RuleRecommendation> duplicationDetection(RuleDefinition source, List<RuleDefinition> context) {
        List<RuleRecommendation> recs = new ArrayList<>();
        Set<String> sourceVars = extractVars(source.getConditionExpression());
        if (sourceVars.size() < 3) {
            return recs;
        }
        for (RuleDefinition r : context) {
            if (r == null || r.getCode() == null || r.getCode().equals(source.getCode())) {
                continue;
            }
            Set<String> overlap = new HashSet<>(extractVars(r.getConditionExpression()));
            overlap.retainAll(sourceVars);
            if (overlap.size() >= 3) {
                RuleRecommendation rec = new RuleRecommendation();
                rec.setSuggestedCode(source.getCode() + "-dup-" + r.getCode());
                rec.setSuggestedName("规则 " + r.getName() + " 存在重叠");
                rec.setSuggestedExpression(source.getConditionExpression());
                rec.setSuggestedSeverity("INFO");
                rec.setRationale("与规则 [" + r.getCode() + "] 共享 " + overlap.size()
                        + " 个变量，建议评估是否合并或拆分。");
                rec.setScore(Math.min(1.0, overlap.size() / 5.0));
                rec.setType(RuleRecommendation.RecommendationType.PATTERN_DUPLICATION);
                recs.add(rec);
            }
        }
        return recs;
    }

    /**
     * 变体衍生：低命中率规则按类别生成阈值变体
     */
    List<RuleRecommendation> variantSuggestion(RuleDefinition source, RuleEngineStats stats) {
        if (stats == null) {
            return Collections.emptyList();
        }
        long evaluations = getStatExecutions(stats, source.getCode());
        long triggered = getStatTriggered(stats, source.getCode());
        if (evaluations < 100) {
            return Collections.emptyList();
        }
        double hitRate = evaluations == 0 ? 0.0 : (double) triggered / evaluations;
        if (hitRate >= 0.01) {
            return Collections.emptyList();
        }
        RuleRecommendation rec = new RuleRecommendation();
        rec.setSuggestedCode(source.getCode() + "-var-loose");
        rec.setSuggestedName(source.getName() + " - 宽松阈值变体");
        rec.setSuggestedExpression(loosenExpression(source.getConditionExpression()));
        rec.setSuggestedSeverity("GREEN");
        rec.setRationale("源规则命中率仅 " + String.format("%.2f%%", hitRate * 100)
                + "，建议尝试放宽阈值以观察是否能产生有效命中。");
        rec.setScore(0.5);
        rec.setType(RuleRecommendation.RecommendationType.VARIANT);
        return Collections.singletonList(rec);
    }

    /**
     * 拆分建议：错误率高的规则按 AND 拆分为子规则
     */
    List<RuleRecommendation> splitSuggestion(RuleDefinition source, RuleEngineStats stats) {
        if (stats == null) {
            return Collections.emptyList();
        }
        long evaluations = getStatExecutions(stats, source.getCode());
        long errors = getStatErrors(stats, source.getCode());
        if (evaluations < 30) {
            return Collections.emptyList();
        }
        double errorRate = (double) errors / evaluations;
        if (errorRate < 0.1) {
            return Collections.emptyList();
        }
        String expr = source.getConditionExpression() == null ? "" : source.getConditionExpression();
        if (expr.isEmpty() || !expr.contains("&&")) {
            return Collections.emptyList();
        }
        String[] parts = expr.split("&&");
        if (parts.length < 2) {
            return Collections.emptyList();
        }
        List<RuleRecommendation> recs = new ArrayList<>();
        int idx = 0;
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            RuleRecommendation rec = new RuleRecommendation();
            rec.setSuggestedCode(source.getCode() + "-split-" + (idx++));
            rec.setSuggestedName(source.getName() + " 子规则 " + (idx));
            rec.setSuggestedExpression(trimmed);
            rec.setSuggestedSeverity("YELLOW");
            rec.setRationale("源规则错误率 " + String.format("%.1f%%", errorRate * 100)
                    + "，建议按 && 拆分为子规则独立评估，便于定位失败原因。");
            rec.setScore(0.7);
            rec.setType(RuleRecommendation.RecommendationType.SPLIT_SUGGESTION);
            recs.add(rec);
        }
        return recs;
    }

    private long getStatExecutions(RuleEngineStats stats, String code) {
        if (stats.getPerRuleStats() != null) {
            RuleEngineStats.RuleStat perRule = stats.getPerRuleStats().get(code);
            if (perRule != null) {
                return perRule.getExecutions();
            }
        }
        return stats.getTotalEvaluations();
    }

    private long getStatTriggered(RuleEngineStats stats, String code) {
        if (stats.getPerRuleStats() != null) {
            RuleEngineStats.RuleStat perRule = stats.getPerRuleStats().get(code);
            if (perRule != null) {
                return perRule.getTriggered();
            }
        }
        return stats.getTotalTriggered();
    }

    private long getStatErrors(RuleEngineStats stats, String code) {
        if (stats.getPerRuleStats() != null) {
            RuleEngineStats.RuleStat perRule = stats.getPerRuleStats().get(code);
            if (perRule != null) {
                return perRule.getErrors();
            }
        }
        return stats.getTotalErrors();
    }

    /**
     * 提取表达式变量名（排除关键字和数字）
     */
    Set<String> extractVars(String expression) {
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
            if (token.matches("\\d+")) {
                continue;
            }
            vars.add(token);
        }
        return vars;
    }

    /**
     * 宽松化表达式：将所有 {@code >=} 替换为 {@code >}，{@code <=} 替换为 {@code <}，
     * 常量阈值乘 0.8。
     */
    private String loosenExpression(String expression) {
        if (expression == null || expression.isEmpty()) {
            return expression;
        }
        String result = expression.replace(">=", ">").replace("<=", "<");
        // 简单的数字字面量放宽（不处理完整表达式解析，仅适合作为提示）
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(result);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            double v = Double.parseDouble(m.group(1));
            double loosened = v * 0.8;
            m.appendReplacement(sb, String.valueOf(loosened));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
