package com.njydsz.pmis.literule.server.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.njydsz.pmis.literule.api.CrossDecisionTableDefinition;
import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;

import lombok.extern.slf4j.Slf4j;

/**
 * 交叉决策表规则（决策矩阵，P1-6）
 *
 * <p>对标 URule Pro 的交叉决策表，支持行和列双维度交叉匹配。
 *
 * <p>执行流程：
 * <ol>
 *   <li>从 facts 中取行维度值，按 {@code rowBuckets} 顺序匹配，确定行索引</li>
 *   <li>从 facts 中取列维度值，按 {@code columnBuckets} 顺序匹配，确定列索引</li>
 *   <li>根据 "rowIndex_columnIndex" 从 {@code cells} 中取出动作</li>
 *   <li>若行或列未匹配，使用 {@code defaultActions}</li>
 * </ol>
 *
 * <p>典型场景：费率表、税率表、运费表、风险等级矩阵。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Slf4j
public class CrossDecisionTableRule implements Rule {

    private static final Pattern COMPARISON_PATTERN = Pattern.compile("^(>=|<=|>|<|!=|==)\\s*(.+)$");
    private static final Pattern INTERVAL_PATTERN = Pattern.compile("^(\\[|\\()([^,]+),([^\\]\\)]+)(\\]|\\))$");

    private final CrossDecisionTableDefinition definition;

    public CrossDecisionTableRule(CrossDecisionTableDefinition definition) {
        this.definition = definition;
    }

    @Override
    public String getCode() { return definition.getMatrixCode(); }

    @Override
    public String getName() { return definition.getMatrixName(); }

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
            // 1. 行维度匹配
            Object rowValue = context.getFacts().get(definition.getRowDimension());
            int rowIndex = matchBucket(definition.getRowBuckets(), rowValue);
            log.debug("[LiteRule-CrossMatrix] 行维度匹配: dimension={}, value={}, index={}",
                    definition.getRowDimension(), rowValue, rowIndex);

            // 2. 列维度匹配
            Object columnValue = context.getFacts().get(definition.getColumnDimension());
            int columnIndex = matchBucket(definition.getColumnBuckets(), columnValue);
            log.debug("[LiteRule-CrossMatrix] 列维度匹配: dimension={}, value={}, index={}",
                    definition.getColumnDimension(), columnValue, columnIndex);

            // 3. 查找交叉单元格
            Map<String, Object> actions = null;
            if (rowIndex >= 0 && columnIndex >= 0) {
                String cellKey = CrossDecisionTableDefinition.cellKey(rowIndex, columnIndex);
                actions = definition.getCells() != null ? definition.getCells().get(cellKey) : null;
            }

            // 4. 未命中使用默认动作
            if (actions == null || actions.isEmpty()) {
                actions = definition.getDefaultActions();
                if (actions == null || actions.isEmpty()) {
                    return RuleResult.builder()
                            .ruleCode(getCode())
                            .ruleName(getName())
                            .category(getCategory())
                            .triggered(false)
                            .triggeredAt(LocalDateTime.now())
                            .elapsedMs(elapsedMs(start))
                            .build();
                }
            }

            return buildResult(actions, start);
        } catch (Exception e) {
            log.warn("[LiteRule-CrossMatrix] 交叉决策表 {} 评估异常: {}", getCode(), e.getMessage());
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
     * 按桶顺序匹配，返回首个命中桶的索引
     *
     * @param buckets 分桶列表
     * @param value   维度值
     * @return 首个命中桶索引；全部未命中返回 -1
     */
    private int matchBucket(List<CrossDecisionTableDefinition.Bucket> buckets, Object value) {
        if (buckets == null || buckets.isEmpty()) return -1;
        for (int i = 0; i < buckets.size(); i++) {
            String condition = buckets.get(i).getCondition();
            if (matchCondition(condition, value)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 匹配条件（复用决策表的匹配逻辑）
     */
    private boolean matchCondition(String condExpr, Object factValue) {
        if (condExpr == null) return true;
        condExpr = condExpr.trim();
        if (condExpr.isEmpty() || "*".equals(condExpr)) return true;

        // null 检查
        if (factValue == null) {
            return "null".equalsIgnoreCase(condExpr) || "==null".equals(condExpr);
        }

        // 区间
        Matcher intervalMatcher = INTERVAL_PATTERN.matcher(condExpr);
        if (intervalMatcher.matches()) {
            return matchInterval(intervalMatcher, factValue);
        }

        // 枚举
        if (condExpr.contains("|")) {
            String[] parts = condExpr.split("\\|");
            for (String part : parts) {
                if (Objects.equals(toString(factValue), part.trim())) {
                    return true;
                }
            }
            return false;
        }

        // 比较表达式
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
            BigDecimal left = new BigDecimal(m.group(2).trim());
            BigDecimal right = new BigDecimal(m.group(3).trim());
            boolean leftOk = m.group(1).equals("[") ? fact.compareTo(left) >= 0 : fact.compareTo(left) > 0;
            boolean rightOk = m.group(4).equals("]") ? fact.compareTo(right) <= 0 : fact.compareTo(right) < 0;
            return leftOk && rightOk;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matchComparison(String op, String operandStr, Object factValue) {
        try {
            if ("==".equals(op)) {
                return Objects.equals(toString(factValue), operandStr) || equalsNumeric(factValue, operandStr);
            }
            if ("!=".equals(op)) {
                return !Objects.equals(toString(factValue), operandStr) && !equalsNumeric(factValue, operandStr);
            }
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
            return false;
        }
    }

    private boolean equalsNumeric(Object factValue, String operandStr) {
        try {
            BigDecimal fact = toBigDecimal(factValue);
            if (fact == null) return false;
            return fact.compareTo(new BigDecimal(operandStr.trim())) == 0;
        } catch (Exception e) {
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
            return null;
        }
    }

    private String toString(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd.toPlainString();
        return String.valueOf(value);
    }

    /**
     * 从 actions 构建 RuleResult
     */
    private RuleResult buildResult(Map<String, Object> actions, long startNano) {
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

    private long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    public CrossDecisionTableDefinition getDefinition() {
        return definition;
    }
}
