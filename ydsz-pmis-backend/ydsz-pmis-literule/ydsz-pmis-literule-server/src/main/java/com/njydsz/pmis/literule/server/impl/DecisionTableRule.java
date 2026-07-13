package com.njydsz.pmis.literule.server.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.njydsz.pmis.literule.api.DecisionTableDefinition;
import com.njydsz.pmis.literule.api.HitPolicy;
import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.server.expr.ExpressionEvaluator;

import lombok.extern.slf4j.Slf4j;

/**
 * 决策表规则：基于 DMN 风格的表格进行多条件匹配
 *
 * <p>执行流程：
 * <ol>
 *   <li>遍历所有 Row，对每行的 conditions 进行匹配（条件 AND 关系）</li>
 *   <li>按 {@link HitPolicy} 收集命中结果</li>
 *   <li>UNIQUE 多行命中时记录异常（不抛出，仅返回未触发 + 错误描述）</li>
 *   <li>COLLECT/RULE_ORDER 返回所有命中行：主结果取首条，其余存入 {@code collectedResults}</li>
 *   <li>FIRST/ANY/PRIORITY 仅返回首条/优先级最高的命中行</li>
 * </ol>
 *
 * <p>条件表达式解析由 {@link #matchCondition} 实现，支持字面值、比较、区间、枚举、LiteExpr 表达式。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
public class DecisionTableRule implements Rule {

    private static final Pattern COMPARISON_PATTERN = Pattern.compile("^(>=|<=|>|<|!=|==)\\s*(.+)$");
    private static final Pattern INTERVAL_PATTERN = Pattern.compile("^(\\[|\\()([^,]+),([^\\]\\)]+)(\\]|\\))$");
    private static final Pattern ENUM_PATTERN = Pattern.compile("^([^|]+(?:\\|[^|]+)+)$");
    private static final String EXPR_PREFIX = "expr:";

    private final DecisionTableDefinition definition;
    private final ExpressionEvaluator evaluator;

    public DecisionTableRule(DecisionTableDefinition definition, ExpressionEvaluator evaluator) {
        this.definition = definition;
        this.evaluator = evaluator;
    }

    @Override
    public String getCode() { return definition.getTableCode(); }

    @Override
    public String getName() { return definition.getTableName(); }

    @Override
    public String getCategory() { return definition.getCategory(); }

    @Override
    public int getPriority() { return definition.getPriority(); }

    @Override
    public String getScope() { return definition.getScope(); }

    @Override
    public RuleResult evaluate(RuleContext context) {
        long start = System.nanoTime();
        try {
            List<DecisionTableDefinition.Row> matchedRows = new ArrayList<>();
            for (DecisionTableDefinition.Row row : definition.getRows()) {
                if (row.getConditions() == null || row.getConditions().isEmpty()) {
                    matchedRows.add(row);
                    continue;
                }
                boolean allMatch = true;
                for (Map.Entry<String, String> entry : row.getConditions().entrySet()) {
                    String column = entry.getKey();
                    String condExpr = entry.getValue();
                    Object factValue = context.getFacts().get(column);
                    if (!matchCondition(column, condExpr, factValue, context)) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    matchedRows.add(row);
                }
            }

            // 无命中：使用默认动作；若无默认动作则返回未触发
            if (matchedRows.isEmpty()) {
                if (definition.getDefaultActions() == null || definition.getDefaultActions().isEmpty()) {
                    return RuleResult.builder()
                            .ruleCode(getCode())
                            .ruleName(getName())
                            .category(getCategory())
                            .triggered(false)
                            .triggeredAt(LocalDateTime.now())
                            .elapsedMs(elapsedMs(start))
                            .build();
                }
                return buildResultFromActions(definition.getDefaultActions(), start);
            }

            HitPolicy policy = definition.getHitPolicy() == null ? HitPolicy.FIRST : definition.getHitPolicy();

            // UNIQUE 多命中 → 报错
            if (policy == HitPolicy.UNIQUE && matchedRows.size() > 1) {
                log.warn("[LiteRule-DecisionTable] 决策表 {} UNIQUE 策略命中多行: count={}",
                        getCode(), matchedRows.size());
                return RuleResult.builder()
                        .ruleCode(getCode())
                        .ruleName(getName())
                        .category(getCategory())
                        .triggered(false)
                        .description("决策表 UNIQUE 策略命中多行: " + matchedRows.size())
                        .triggeredAt(LocalDateTime.now())
                        .elapsedMs(elapsedMs(start))
                        .build();
            }

            // 按策略挑选
            DecisionTableDefinition.Row chosen;
            if (policy == HitPolicy.PRIORITY) {
                chosen = matchedRows.stream()
                        .min(Comparator.comparingInt(DecisionTableDefinition.Row::getPriority))
                        .orElse(matchedRows.get(0));
            } else if (policy == HitPolicy.COLLECT) {
                // COLLECT 策略：按优先级升序排序，主结果取首条，
                // 其余匹配行作为独立 RuleResult 收集到 collectedResults
                List<DecisionTableDefinition.Row> sorted = new ArrayList<>(matchedRows);
                sorted.sort(Comparator.comparingInt(DecisionTableDefinition.Row::getPriority));
                chosen = sorted.get(0);
                RuleResult mainResult = buildResultFromActions(chosen.getActions(), start);
                mainResult.setCollectedResults(buildCollectedResults(sorted, start));
                // 兼容下游：actions 中保留 _matchedCount 供旧消费者使用
                mainResult.setDescription(appendCollectInfo(mainResult.getDescription(), sorted.size()));
                return mainResult;
            } else if (policy == HitPolicy.RULE_ORDER) {
                // RULE_ORDER 策略：按行在表中的出现顺序，主结果取首条，
                // 其余匹配行作为独立 RuleResult 收集到 collectedResults
                chosen = matchedRows.get(0);
                RuleResult mainResult = buildResultFromActions(chosen.getActions(), start);
                mainResult.setCollectedResults(buildCollectedResults(matchedRows, start));
                mainResult.setDescription(appendCollectInfo(mainResult.getDescription(), matchedRows.size()));
                return mainResult;
            } else {
                // FIRST / ANY → 首条
                chosen = matchedRows.get(0);
            }

            Map<String, Object> actions = new LinkedHashMap<>(chosen.getActions());
            actions.put("_matchedCount", matchedRows.size());

            return buildResultFromActions(actions, start);
        } catch (Exception e) {
            log.warn("[LiteRule-DecisionTable] 决策表 {} 评估异常: {}", getCode(), e.getMessage());
            return RuleResult.builder()
                    .ruleCode(getCode())
                    .triggered(false)
                    .description("评估异常: " + e.getMessage())
                    .triggeredAt(LocalDateTime.now())
                    .elapsedMs(elapsedMs(start))
                    .build();
        }
    }

    /**
     * 根据 actions 构建规则结果
     *
     * <p>actions 中约定键：
     * <ul>
     *   <li>{@code severity} — 严重度编码（INFO/YELLOW/RED），缺省 INFO</li>
     *   <li>{@code title} — 标题</li>
     *   <li>{@code description} — 详细描述</li>
     *   <li>{@code currentValue} — 当前值（参考）</li>
     * </ul>
     */
    private RuleResult buildResultFromActions(Map<String, Object> actions, long startNano) {
        String severityCode = actions.get("severity") == null ? "INFO" : String.valueOf(actions.get("severity"));
        RuleSeverity severity = RuleSeverity.fromCode(severityCode);
        if (severity == null) severity = RuleSeverity.INFO;

        String title = actions.get("title") == null ? getName() : String.valueOf(actions.get("title"));
        String description = actions.get("description") == null ? "" : String.valueOf(actions.get("description"));
        String currentValue = actions.get("currentValue") == null ? null : String.valueOf(actions.get("currentValue"));

        return RuleResult.builder()
                .ruleCode(getCode())
                .ruleName(getName())
                .category(getCategory())
                .triggered(true)
                .severity(severity)
                .title(title)
                .description(description)
                .currentValue(currentValue)
                .scope(definition.getScope())
                .triggeredAt(LocalDateTime.now())
                .drilldownAvailable(true)
                .elapsedMs(elapsedMs(startNano))
                .build();
    }

    /**
     * 条件匹配（支持字面值 / 比较表达式 / 区间 / 枚举 / LiteExpr 表达式）
     */
    private boolean matchCondition(String column, String condExpr, Object factValue, RuleContext context) {
        if (condExpr == null) return true;
        condExpr = condExpr.trim();
        if (condExpr.isEmpty() || "*".equals(condExpr)) return true;

        // LiteExpr 表达式：expr:>amount*0.1
        if (condExpr.startsWith(EXPR_PREFIX)) {
            String expr = condExpr.substring(EXPR_PREFIX.length());
            try {
                return evaluator.evalBoolean(expr, context);
            } catch (Exception e) {
                log.debug("[LiteRule-DecisionTable] 表达式条件求值失败 column={} expr={}: {}", column, expr, e.getMessage());
                return false;
            }
        }

        // null 检查：支持 "null"、"==null" 匹配 null；"!=null" 匹配非 null（此处 factValue 为 null 所以返回 false）
        if (factValue == null) {
            return "null".equalsIgnoreCase(condExpr) || "==null".equals(condExpr);
        }

        // 区间：[0.05,0.15)
        Matcher intervalMatcher = INTERVAL_PATTERN.matcher(condExpr);
        if (intervalMatcher.matches()) {
            return matchInterval(intervalMatcher, factValue);
        }

        // 枚举：RED|YELLOW
        Matcher enumMatcher = ENUM_PATTERN.matcher(condExpr);
        if (enumMatcher.matches() && condExpr.contains("|")) {
            String[] parts = condExpr.split("\\|");
            for (String part : parts) {
                if (Objects.equals(toString(factValue), part.trim())) {
                    return true;
                }
            }
            return false;
        }

        // 比较表达式：>=3 / <0.05 / !=null
        Matcher comparisonMatcher = COMPARISON_PATTERN.matcher(condExpr);
        if (comparisonMatcher.matches()) {
            String op = comparisonMatcher.group(1);
            String operandStr = comparisonMatcher.group(2).trim();
            if ("null".equalsIgnoreCase(operandStr)) {
                return (op.equals("==") && factValue == null) || (op.equals("!=") && factValue != null);
            }
            return matchComparison(op, operandStr, factValue);
        }

        // 字面值相等
        return Objects.equals(toString(factValue), condExpr) || equalsNumeric(factValue, condExpr);
    }

    private boolean matchInterval(Matcher m, Object factValue) {
        try {
            BigDecimal fact = toBigDecimal(factValue);
            if (fact == null) return false;
            String leftBracket = m.group(1);
            BigDecimal left = new BigDecimal(m.group(2).trim());
            String rightStr = m.group(3).trim();
            String rightBracket = m.group(4);
            BigDecimal right = new BigDecimal(rightStr);

            boolean leftOk = leftBracket.equals("[") ? fact.compareTo(left) >= 0 : fact.compareTo(left) > 0;
            boolean rightOk = rightBracket.equals("]") ? fact.compareTo(right) <= 0 : fact.compareTo(right) < 0;
            return leftOk && rightOk;
        } catch (Exception e) {
            log.warn("[DecisionTableRule] 区间匹配异常 factValue={}: {}", factValue, e.getMessage());
            return false;
        }
    }

    private boolean matchComparison(String op, String operandStr, Object factValue) {
        try {
            // 字符串比较
            if ("==".equals(op)) {
                return Objects.equals(toString(factValue), operandStr) || equalsNumeric(factValue, operandStr);
            }
            if ("!=".equals(op)) {
                return !Objects.equals(toString(factValue), operandStr) && !equalsNumeric(factValue, operandStr);
            }
            // 数值比较
            BigDecimal fact = toBigDecimal(factValue);
            BigDecimal operand = new BigDecimal(operandStr);
            if (fact == null) return false;
            int cmp = fact.compareTo(operand);
            return switch (op) {
                case ">" -> cmp > 0;
                case ">=" -> cmp >= 0;
                case "<" -> cmp < 0;
                case "<=" -> cmp <= 0;
                default -> false;
            };
        } catch (Exception e) {
            log.warn("[DecisionTableRule] 比较匹配异常 op={} operandStr={} factValue={}: {}",
                    op, operandStr, factValue, e.getMessage());
            return false;
        }
    }

    private boolean equalsNumeric(Object factValue, String operandStr) {
        try {
            BigDecimal fact = toBigDecimal(factValue);
            if (fact == null) return false;
            BigDecimal operand = new BigDecimal(operandStr.trim());
            return fact.compareTo(operand) == 0;
        } catch (Exception e) {
            log.warn("[DecisionTableRule] 数值相等比较异常 factValue={} operandStr={}: {}",
                    factValue, operandStr, e.getMessage());
            return false;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(value.toString().trim());
        } catch (Exception e) {
            log.warn("[DecisionTableRule] BigDecimal 转换失败 value={}: {}", value, e.getMessage());
            return null;
        }
    }

    private String toString(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd.toPlainString();
        return String.valueOf(value);
    }

    private long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    /**
     * 构建 COLLECT/RULE_ORDER 策略的全部匹配行结果列表
     *
     * <p>每行独立构建一个 {@link RuleResult}，保留行优先级与动作信息，
     * 主结果（列表首项）与外层返回的主结果内容一致。
     *
     * @param matchedRows 已按策略排序的匹配行
     * @param startNano   评估起始纳秒时间
     * @return 匹配行结果列表（至少 1 项）
     */
    private List<RuleResult> buildCollectedResults(List<DecisionTableDefinition.Row> matchedRows, long startNano) {
        List<RuleResult> results = new ArrayList<>(matchedRows.size());
        for (DecisionTableDefinition.Row row : matchedRows) {
            results.add(buildResultFromActions(row.getActions(), startNano));
        }
        return results;
    }

    /**
     * 在描述末尾追加 COLLECT/RULE_ORDER 命中计数信息
     *
     * @param description 原始描述
     * @param count       匹配行数
     * @return 拼接后的描述；原始描述为空时仅返回计数信息
     */
    private String appendCollectInfo(String description, int count) {
        String info = "[matchedCount=" + count + "]";
        return (description == null || description.isEmpty()) ? info : description + " " + info;
    }

    public DecisionTableDefinition getDefinition() {
        return definition;
    }
}
