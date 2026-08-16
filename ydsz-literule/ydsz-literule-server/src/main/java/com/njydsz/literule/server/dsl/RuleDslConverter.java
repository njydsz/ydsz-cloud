package com.njydsz.literule.server.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.literule.api.DecisionTableDefinition;
import com.njydsz.literule.api.HitPolicy;
import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleSeverity;
import com.njydsz.literule.api.ScorecardDefinition;
import com.njydsz.literule.api.expression.ExpressionEngine;
import com.njydsz.literule.server.impl.DecisionTableRule;
import com.njydsz.literule.server.impl.ExpressionRule;
import com.njydsz.literule.server.impl.ScorecardRule;
import com.njydsz.literule.server.impl.ScriptRule;
import com.njydsz.literule.server.orchestrator.RuleChain;

/**
 * DSL 模型到引擎可执行对象的转换器
 *
 * <p>将 {@link RuleDsl} 转换为引擎可执行的 {@link Rule} 列表与 {@link RuleChain} 列表。
 * 转换过程会根据规则类型（type 字段）分派到对应的 Definition + Rule 实现类。
 *
 * <p><b>使用示例</b>：
 * <pre>
 * RuleDsl dsl = RuleDslParser.parse(yamlContent);
 * RuleDslParser.validate(dsl);
 *
 * // 转换为规则列表
 * List&lt;Rule&gt; rules = RuleDslConverter.toRules(dsl, evaluator);
 *
 * // 转换为链列表（需要规则字典解析步骤引用）
 * Map&lt;String, Rule&gt; ruleMap = RuleDslConverter.toRuleMap(rules);
 * List&lt;RuleChain&gt; chains = RuleDslConverter.toChains(dsl, ruleMap, evaluator);
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public final class RuleDslConverter {

    private RuleDslConverter() {
    }

    /**
     * 将 DSL 规则列表转换为引擎可执行的 Rule 列表
     *
     * @param dsl       DSL 模型
     * @param evaluator 表达式求值器
     * @return Rule 列表（按 DSL 中的顺序）
     */
    public static List<Rule> toRules(RuleDsl dsl, ExpressionEngine evaluator) {
        if (dsl == null || dsl.getRules() == null || dsl.getRules().isEmpty()) {
            return Collections.emptyList();
        }
        List<Rule> rules = new ArrayList<>(dsl.getRules().size());
        for (RuleDslEntry entry : dsl.getRules()) {
            try {
                Rule rule = toRule(entry, evaluator);
                if (rule != null) {
                    rules.add(rule);
                }
            } catch (Exception e) {
                log.warn("[LiteRule-DSL] 规则 {} 转换失败: {}", entry.getCode(), e.getMessage());
            }
        }
        return rules;
    }

    /**
     * 将 Rule 列表转换为 code -> Rule 的 Map（便于链编排查找）
     *
     * @param rules Rule 列表
     * @return code -> Rule 映射
     */
    public static Map<String, Rule> toRuleMap(List<Rule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Rule> map = new LinkedHashMap<>(rules.size());
        for (Rule r : rules) {
            if (r.getCode() != null) {
                map.put(r.getCode(), r);
            }
        }
        return map;
    }

    /**
     * 将 DSL 链列表转换为 RuleChain 列表
     *
     * @param dsl       DSL 模型
     * @param ruleMap   规则字典（code -> Rule），用于解析链步骤引用
     * @param evaluator 表达式求值器
     * @return RuleChain 列表
     */
    public static List<RuleChain> toChains(RuleDsl dsl, Map<String, Rule> ruleMap, ExpressionEngine evaluator) {
        if (dsl == null || dsl.getChains() == null || dsl.getChains().isEmpty()) {
            return Collections.emptyList();
        }
        List<RuleChain> chains = new ArrayList<>(dsl.getChains().size());
        for (ChainDslEntry entry : dsl.getChains()) {
            try {
                RuleChain chain = toChain(entry, ruleMap, evaluator);
                if (chain != null) {
                    chains.add(chain);
                }
            } catch (Exception e) {
                log.warn("[LiteRule-DSL] 链 {} 转换失败: {}", entry.getName(), e.getMessage());
            }
        }
        return chains;
    }

    /**
     * 将单条 DSL 规则转换为 Rule 实例
     *
     * @param entry     DSL 规则条目
     * @param evaluator 表达式求值器
     * @return Rule 实例
     * @throws IllegalArgumentException 类型不支持或必填字段缺失
     */
    public static Rule toRule(RuleDslEntry entry, ExpressionEngine evaluator) {
        String type = entry.getType() == null ? "expression" : entry.getType().toLowerCase();
        return switch (type) {
            case "expression" -> new ExpressionRule(toRuleDefinition(entry), evaluator);
            case "scorecard" -> ScorecardRule.from(toScorecardDefinition(entry), evaluator);
            case "decision_table" -> new DecisionTableRule(toDecisionTableDefinition(entry), evaluator);
            case "script" -> toScriptRule(entry);
            case "static_rule" -> throw new IllegalArgumentException(
                    "static_rule 类型需通过编程式注册，DSL 不支持直接声明");
            case "decision_tree" -> throw new IllegalArgumentException(
                    "decision_tree 类型暂未支持 DSL 声明，请使用编程式 API");
            default -> throw new IllegalArgumentException("未知规则类型: " + type);
        };
    }

    /**
     * 将单条 DSL 链转换为 RuleChain 实例
     *
     * @param entry     DSL 链条目
     * @param ruleMap   规则字典
     * @param evaluator 表达式求值器
     * @return RuleChain 实例
     */
    public static RuleChain toChain(ChainDslEntry entry, Map<String, Rule> ruleMap, ExpressionEngine evaluator) {
        String type = entry.getType() == null ? "THEN" : entry.getType().toUpperCase();
        return switch (type) {
            case "THEN" -> RuleChain.then(resolveRules(entry.getSteps(), ruleMap, entry.getName()));
            case "WHEN" -> RuleChain.when(resolveRules(entry.getSteps(), ruleMap, entry.getName()));
            case "IF" -> RuleChain.ifThen(entry.getCondition(),
                    resolveRule(entry.getStep(), ruleMap, entry.getName()));
            case "ELIF" -> buildElifChain(entry, ruleMap);
            case "SWITCH" -> buildSwitchChain(entry, ruleMap);
            case "FOR" -> RuleChain.forEach(entry.getIterable(), entry.getVar(),
                    resolveRule(entry.getStep(), ruleMap, entry.getName()));
            case "WHILE" -> RuleChain.whileDo(entry.getCondition(),
                    resolveRule(entry.getStep(), ruleMap, entry.getName()),
                    entry.getMaxIterations() != null ? entry.getMaxIterations() : 100);
            default -> throw new IllegalArgumentException("未知链类型: " + type);
        };
    }

    // ============ Definition 转换 ============

    /**
     * 将 DSL 规则条目转换为 RuleDefinition（expression 类型）
     *
     * @param entry DSL 规则条目
     * @return RuleDefinition
     * @since 1.0.0
     */
    public static RuleDefinition toRuleDefinition(RuleDslEntry entry) {
        RuleSeverity defaultSeverity = parseSeverity(entry.getSeverity(), RuleSeverity.INFO);
        return RuleDefinition.builder()
                .code(entry.getCode())
                .name(entry.getName())
                .category(entry.getCategory())
                .categoryPath(entry.getCategoryPath())
                .owner(entry.getOwner())
                .description(entry.getDescription())
                .conditionExpression(entry.getCondition())
                .severityExpression(entry.getSeverityExpression())
                .defaultSeverity(defaultSeverity)
                .titleTemplate(entry.getTitle())
                .descriptionTemplate(entry.getDescriptionTemplate())
                .priority(entry.getPriority())
                .enabled(entry.isEnabled())
                .scope(entry.getScope())
                .mutexGroup(entry.getMutexGroup())
                .version(entry.getVersion())
                .canaryRatio(entry.getCanaryRatio() != null ? entry.getCanaryRatio() : 0.0)
                .canaryConditions(entry.getCanaryConditions())
                .canaryConditionExpression(entry.getCanaryConditionExpression())
                .canarySeverityExpression(entry.getCanarySeverityExpression())
                .effectiveFrom(entry.getEffectiveFrom())
                .effectiveTo(entry.getEffectiveTo())
                .status("PUBLISHED")
                .build();
    }

    /**
     * 将 DSL 规则条目转换为 ScorecardDefinition（scorecard 类型）
     */
    private static ScorecardDefinition toScorecardDefinition(RuleDslEntry entry) {
        ScorecardDefinition.ScorecardDefinitionBuilder b = ScorecardDefinition.builder()
                .ruleCode(entry.getCode())
                .ruleName(entry.getName())
                .category(entry.getCategory())
                .description(entry.getDescription())
                .baseScore(entry.getBaseScore() != null ? entry.getBaseScore() : 100.0)
                .redThreshold(entry.getRedThreshold() != null ? entry.getRedThreshold() : 0.0)
                .yellowThreshold(entry.getYellowThreshold() != null ? entry.getYellowThreshold() : 0.0)
                .minScore(entry.getMinScore() != null ? entry.getMinScore() : 0.0)
                .maxScore(entry.getMaxScore() != null ? entry.getMaxScore() : 100.0)
                .scoreDirection(parseDirection(entry.getDirection()))
                .priority(entry.getPriority())
                .enabled(entry.isEnabled())
                .scope(entry.getScope())
                .version(entry.getVersion());
        // 因子列表
        if (entry.getFactors() != null) {
            List<ScorecardDefinition.ScoreFactor> factors = new ArrayList<>(entry.getFactors().size());
            for (RuleDslEntry.FactorDsl f : entry.getFactors()) {
                factors.add(ScorecardDefinition.ScoreFactor.builder()
                        .conditionExpression(f.getWhen())
                        .score(f.getScore() != null ? f.getScore() : 0.0)
                        .scoreExpression(f.getScoreExpr())
                        .weight(f.getWeight() != null ? f.getWeight() : 1.0)
                        .description(f.getDesc())
                        .build());
            }
            b.factors(factors);
        }
        // 评级映射
        if (entry.getGrades() != null) {
            List<ScorecardDefinition.ScoreGrade> grades = new ArrayList<>(entry.getGrades().size());
            for (RuleDslEntry.GradeDsl g : entry.getGrades()) {
                double min = (g.getRange() != null && g.getRange().size() >= 1) ? g.getRange().get(0) : 0.0;
                double max = (g.getRange() != null && g.getRange().size() >= 2) ? g.getRange().get(1) : Double.MAX_VALUE;
                grades.add(ScorecardDefinition.ScoreGrade.builder()
                        .label(g.getLabel())
                        .minScore(min)
                        .maxScore(max)
                        .severity(g.getSeverity())
                        .build());
            }
            b.grades(grades);
        }
        return b.build();
    }

    /**
     * 将 DSL 规则条目转换为 DecisionTableDefinition（decision_table 类型）
     */
    private static DecisionTableDefinition toDecisionTableDefinition(RuleDslEntry entry) {
        DecisionTableDefinition.DecisionTableDefinitionBuilder b = DecisionTableDefinition.builder()
                .tableCode(entry.getCode())
                .tableName(entry.getName())
                .category(entry.getCategory())
                .description(entry.getDescription())
                .hitPolicy(parseHitPolicy(entry.getHitPolicy()))
                .priority(entry.getPriority())
                .enabled(entry.isEnabled())
                .scope(entry.getScope())
                .version(entry.getVersion());
        // 条件列
        if (entry.getConditionColumns() != null) {
            List<DecisionTableDefinition.Column> cols = new ArrayList<>(entry.getConditionColumns().size());
            for (Map<String, Object> cm : entry.getConditionColumns()) {
                cols.add(DecisionTableDefinition.Column.builder()
                        .name(asString(cm.get("name")))
                        .label(asString(cm.get("label")))
                        .type(asString(cm.get("type")))
                        .build());
            }
            b.conditionColumns(cols);
        }
        // 动作列
        if (entry.getActionColumns() != null) {
            List<DecisionTableDefinition.Column> cols = new ArrayList<>(entry.getActionColumns().size());
            for (Map<String, Object> cm : entry.getActionColumns()) {
                cols.add(DecisionTableDefinition.Column.builder()
                        .name(asString(cm.get("name")))
                        .label(asString(cm.get("label")))
                        .type(asString(cm.get("type")))
                        .build());
            }
            b.actionColumns(cols);
        }
        // 决策行
        if (entry.getRows() != null) {
            List<DecisionTableDefinition.Row> rows = new ArrayList<>(entry.getRows().size());
            for (Map<String, Object> rm : entry.getRows()) {
                Map<String, String> conditions = new LinkedHashMap<>();
                Object condObj = rm.get("conditions");
                if (condObj instanceof Map<?, ?> cm) {
                    for (Map.Entry<?, ?> e : cm.entrySet()) {
                        if (e.getKey() != null && e.getValue() != null) {
                            conditions.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                        }
                    }
                }
                Map<String, Object> actions = new LinkedHashMap<>();
                Object actObj = rm.get("actions");
                if (actObj instanceof Map<?, ?> am) {
                    for (Map.Entry<?, ?> e : am.entrySet()) {
                        if (e.getKey() != null) {
                            actions.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    }
                }
                Object prioObj = rm.get("priority");
                int prio = 100;
                if (prioObj instanceof Number n) prio = n.intValue();
                rows.add(DecisionTableDefinition.Row.builder()
                        .conditions(conditions)
                        .actions(actions)
                        .priority(prio)
                        .build());
            }
            b.rows(rows);
        }
        // 默认动作
        if (entry.getDefaultActions() != null) {
            Map<String, Object> def = new LinkedHashMap<>(entry.getDefaultActions());
            b.defaultActions(def);
        }
        return b.build();
    }

    /**
     * 将 DSL 规则条目转换为 ScriptRule（script 类型）
     *
     * <p>1.5.0 起支持多语言：groovy / javascript / python（需对应 JSR-223 引擎在 classpath）。
     */
    private static Rule toScriptRule(RuleDslEntry entry) {
        String language = entry.getScriptLanguage() == null ? "groovy" : entry.getScriptLanguage().toLowerCase();
        RuleSeverity defaultSeverity = parseSeverity(entry.getSeverity(), RuleSeverity.INFO);
        return new ScriptRule(
                entry.getCode(),
                entry.getName(),
                entry.getCategory(),
                entry.getPriority(),
                entry.getScope(),
                defaultSeverity,
                entry.getScriptBody(),
                language,
                true);
    }

    // ============ 链构建 ============

    /**
     * 构建 ELIF 链
     */
    private static RuleChain buildElifChain(ChainDslEntry entry, Map<String, Rule> ruleMap) {
        Map<String, Rule> branches = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entry.getBranches().entrySet()) {
            Rule r = resolveRule(e.getValue(), ruleMap, entry.getName());
            branches.put(e.getKey(), r);
        }
        Rule elseRule = entry.getDefaultRule() != null
                ? resolveRule(entry.getDefaultRule(), ruleMap, entry.getName())
                : null;
        return RuleChain.elif(branches, elseRule);
    }

    /**
     * 构建 SWITCH 链
     */
    private static RuleChain buildSwitchChain(ChainDslEntry entry, Map<String, Rule> ruleMap) {
        Map<String, Rule> branches = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entry.getBranches().entrySet()) {
            Rule r = resolveRule(e.getValue(), ruleMap, entry.getName());
            branches.put(e.getKey(), r);
        }
        Rule defaultRule = entry.getDefaultRule() != null
                ? resolveRule(entry.getDefaultRule(), ruleMap, entry.getName())
                : null;
        return RuleChain.switchOn(entry.getBranchKey(), branches, defaultRule);
    }

    /**
     * 解析步骤列表中的规则引用
     */
    private static Rule[] resolveRules(List<String> steps, Map<String, Rule> ruleMap, String chainName) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("链 " + chainName + " 的 steps 为空");
        }
        Rule[] rules = new Rule[steps.size()];
        for (int i = 0; i < steps.size(); i++) {
            rules[i] = resolveRule(steps.get(i), ruleMap, chainName);
        }
        return rules;
    }

    /**
     * 解析单个规则引用
     */
    private static Rule resolveRule(String code, Map<String, Rule> ruleMap, String chainName) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("链 " + chainName + " 引用了空的规则编码");
        }
        Rule r = ruleMap.get(code);
        if (r == null) {
            throw new IllegalArgumentException("链 " + chainName + " 引用了不存在的规则: " + code);
        }
        return r;
    }

    // ============ 枚举解析 ============

    private static RuleSeverity parseSeverity(String code, RuleSeverity fallback) {
        if (code == null || code.isBlank()) return fallback;
        try {
            return RuleSeverity.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static ScorecardDefinition.ScoreDirection parseDirection(String dir) {
        if (dir == null || dir.isBlank()) {
            return ScorecardDefinition.ScoreDirection.DESCENDING;
        }
        try {
            return ScorecardDefinition.ScoreDirection.valueOf(dir.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ScorecardDefinition.ScoreDirection.DESCENDING;
        }
    }

    private static HitPolicy parseHitPolicy(String policy) {
        if (policy == null || policy.isBlank()) {
            return HitPolicy.FIRST;
        }
        try {
            return HitPolicy.valueOf(policy.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return HitPolicy.FIRST;
        }
    }

    private static String asString(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }
}
