package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.ruleengine.DecisionTableDO;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DMN 决策表评估引擎
 *
 * <p>基于 OMG DMN 标准的决策表求值器，支持六种命中策略（hit policy）：
 * <ul>
 *   <li><b>UNIQUE</b>：唯一命中，匹配到多行时抛出 {@link IllegalStateException}</li>
 *   <li><b>FIRST</b>：首次命中，返回第一条匹配行（默认策略）</li>
 *   <li><b>PRIORITY</b>：优先级命中，返回优先级最高的匹配行（数值越小优先级越高）</li>
 *   <li><b>RULE_ORDER</b>：规则顺序命中，返回所有匹配行（按行在表中的出现顺序）</li>
 *   <li><b>COLLECT</b>：收集命中，返回所有匹配行</li>
 *   <li><b>ANY</b>：任意命中，返回任意一条匹配行（多条时取首条并告警）</li>
 * </ul>
 *
 * <p>命中策略存储在 {@link DecisionTableDO#getHitPolicy()}，为空时默认 FIRST。
 *
 * <h3>条件求值</h3>
 * <p>条件列通过 literule 模块的 Aviator 表达式引擎求值。每行的条件单元支持三种写法：
 * <ul>
 *   <li><b>比较片段</b>（如 {@code > 100000}、{@code == 'CAPEX'}）：自动拼接为 {@code 字段 > 100000}</li>
 *   <li><b>完整表达式</b>（如 {@code amount > 100000 && type == 'CAPEX'}）：直接求值</li>
 *   <li><b>字面值</b>（如 {@code 100000}、{@code CAPEX}）：按列 type 自动转换为等值比较</li>
 *   <li><b>通配符</b>（{@code -}、{@code *}、{@code any} 或空）：该条件恒成立</li>
 * </ul>
 *
 * <p>行结构遵循决策表 DDL 约定：
 * <pre>{@code
 *   {"conditions": {"字段名": "条件值"}, "actions": {"动作名": "动作值"}, "priority": 10}
 * }</pre>
 *
 * <p>表达式求值器通过 {@link AviatorExpressionEvaluator} 提供；若 Spring 容器中未注册该 Bean，
 * 则兜底创建默认沙箱实例，确保引擎在脱离 Spring 配置时仍可用。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Component
public class DecisionTableEvaluator {

    /** 默认命中策略 */
    private static final String DEFAULT_HIT_POLICY = "FIRST";

    /**
     * Aviator 表达式求值器（可选注入）。
     *
     * <p>当 ydsz-pmis-literule 模块启用时自动注入；未启用时为 null，回退到默认沙箱实例。
     */
    private final ExpressionEvaluator expressionEvaluator;

    /** Bean 未注入时的兜底求值器（懒加载，避免污染全局 Aviator 实例） */
    private volatile ExpressionEvaluator fallbackEvaluator;

    /**
     * 构造注入：使用 {@link ObjectProvider} 支持可选依赖。
     *
     * @param evaluatorProvider 表达式求值器提供者（可选）
     */
    public DecisionTableEvaluator(ObjectProvider<ExpressionEvaluator> evaluatorProvider) {
        this.expressionEvaluator = evaluatorProvider.getIfAvailable();
    }

    /**
     * 评估决策表
     *
     * @param table 决策表实体
     * @param facts 事实数据（变量名 -> 值），为 null 时按空上下文处理
     * @return 命中行的动作值列表；无匹配时返回默认动作（单元素列表）或空列表
     */
    public List<Map<String, Object>> evaluate(DecisionTableDO table, Map<String, Object> facts) {
        if (table == null) {
            log.warn("[DMN] 决策表为 null，返回空结果");
            return Collections.emptyList();
        }

        List<Map<String, Object>> rows = table.getRows();
        if (rows == null || rows.isEmpty()) {
            log.debug("[DMN] 决策表无行数据，返回默认动作: tableCode={}", table.getTableCode());
            return defaultResult(table);
        }

        String hitPolicy = resolveHitPolicy(table);
        List<Map<String, Object>> conditionColumns = table.getConditionColumns();
        List<Map<String, Object>> effectiveConditions =
                conditionColumns != null ? conditionColumns : Collections.emptyList();

        Map<String, Object> evalFacts = facts != null ? facts : Collections.emptyMap();
        RuleContext context = RuleContext.of(evalFacts, "DMN_EVAL", "DecisionTableEvaluator");
        ExpressionEvaluator evaluator = getEvaluator();

        // 收集所有命中行（保留行顺序）
        List<MatchedRow> matched = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            if (isRowDisabled(row)) {
                continue;
            }
            try {
                if (rowMatches(row, effectiveConditions, context, evaluator)) {
                    matched.add(new MatchedRow(i, row, resolveRowPriority(row, table)));
                }
            } catch (Exception e) {
                // 防御性编码：单行评估异常不影响其它行
                log.warn("[DMN] 行评估异常，跳过: tableCode={}, rowIndex={}, err={}",
                        table.getTableCode(), i, e.getMessage());
            }
        }

        if (matched.isEmpty()) {
            log.debug("[DMN] 无命中行，返回默认动作: tableCode={}", table.getTableCode());
            return defaultResult(table);
        }

        List<Map<String, Object>> actions = applyHitPolicy(hitPolicy, matched, table);
        log.info("[DMN] 决策表评估完成: tableCode={}, hitPolicy={}, matched={}, returned={}",
                table.getTableCode(), hitPolicy, matched.size(), actions.size());
        return actions;
    }

    // ============================== 命中策略 ==============================

    /**
     * 根据命中策略从命中行中提取动作结果
     */
    private List<Map<String, Object>> applyHitPolicy(String hitPolicy,
                                                      List<MatchedRow> matched,
                                                      DecisionTableDO table) {
        String policy = hitPolicy.toUpperCase();
        switch (policy) {
            case "UNIQUE": {
                if (matched.size() > 1) {
                    throw new IllegalStateException("DMN UNIQUE 命中策略下匹配到 " + matched.size()
                            + " 行，期望唯一命中: tableCode=" + table.getTableCode());
                }
                return singleResult(extractActions(matched.get(0).row, table));
            }
            case "FIRST":
            case "ANY": {
                if ("ANY".equals(policy) && matched.size() > 1) {
                    log.warn("[DMN] ANY 命中策略下匹配到 {} 行，取首条: tableCode={}",
                            matched.size(), table.getTableCode());
                }
                return singleResult(extractActions(matched.get(0).row, table));
            }
            case "PRIORITY": {
                MatchedRow best = matched.stream()
                        .min((a, b) -> Integer.compare(a.priority, b.priority))
                        .orElse(matched.get(0));
                log.debug("[DMN] PRIORITY 命中: rowIndex={}, priority={}", best.rowIndex, best.priority);
                return singleResult(extractActions(best.row, table));
            }
            case "RULE_ORDER": {
                // DMN 1.4 RULE ORDER：返回全部匹配行，按行在表中的出现顺序排列
                List<Map<String, Object>> all = new ArrayList<>(matched.size());
                for (MatchedRow m : matched) {
                    all.add(extractActions(m.row, table));
                }
                return all;
            }
            case "COLLECT": {
                List<Map<String, Object>> all = new ArrayList<>(matched.size());
                for (MatchedRow m : matched) {
                    all.add(extractActions(m.row, table));
                }
                return all;
            }
            default: {
                log.warn("[DMN] 未知命中策略 '{}'，按 FIRST 处理: tableCode={}", hitPolicy, table.getTableCode());
                return singleResult(extractActions(matched.get(0).row, table));
            }
        }
    }

    // ============================== 行匹配 ==============================

    /**
     * 评估单行是否全部条件命中
     */
    private boolean rowMatches(Map<String, Object> row,
                               List<Map<String, Object>> conditionColumns,
                               RuleContext context,
                               ExpressionEvaluator evaluator) {
        Map<String, Object> conditions = asMap(row.get("conditions"));
        // 若行未声明 conditions 子映射，则按行本身作为条件映射（兼容扁平结构）
        Map<String, Object> condMap = conditions != null ? conditions : row;

        for (Map<String, Object> column : conditionColumns) {
            String name = resolveColumnName(column);
            Object cell = condMap.get(name);
            if (!matchCondition(column, cell, context, evaluator)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 评估单个条件单元
     *
     * @param column    条件列定义（含 name/type/expression 等）
     * @param cellValue 该行对应条件列的单元格值
     * @param context   规则上下文
     * @param evaluator 表达式求值器
     * @return true=条件成立
     */
    private boolean matchCondition(Map<String, Object> column,
                                   Object cellValue,
                                   RuleContext context,
                                   ExpressionEvaluator evaluator) {
        String cellStr = stringify(cellValue);
        if (isWildcard(cellStr)) {
            return true;
        }
        String lhs = resolveInputExpr(column);
        String type = stringify(column.get("type"));
        String expr = buildConditionExpr(lhs, cellStr, type);
        if (expr == null) {
            log.warn("[DMN] 无法构建条件表达式，该条件视为通过: lhs={}, cell={}", lhs, cellStr);
            return true;
        }
        try {
            return evaluator.evalBoolean(expr, context);
        } catch (Exception e) {
            log.warn("[DMN] 条件求值异常，视为不通过: expr={}, err={}", expr, e.getMessage());
            return false;
        }
    }

    /**
     * 构建条件表达式
     *
     * <p>根据单元格内容形态选择拼接方式：
     * <ol>
     *   <li>比较片段（以 {@code > < >= <= == !=} 开头）→ {@code lhs + " " + cell}</li>
     *   <li>完整表达式（含 {@code && ||} 或比较运算符，或以 {@code ! (} 开头）→ 直接使用 cell</li>
     *   <li>字面值 → {@code lhs + " == " + quote(cell, type)}</li>
     * </ol>
     *
     * @param lhs     左侧输入表达式（字段名或计算表达式），为 null 时无法拼接片段/字面值
     * @param cellStr 单元格字符串
     * @param type    列类型（number/string/boolean 等）
     * @return 条件表达式；无法构建时返回 null
     */
    private String buildConditionExpr(String lhs, String cellStr, String type) {
        if (isFragment(cellStr)) {
            if (isBlank(lhs)) return null;
            return lhs + " " + cellStr;
        }
        if (isCompleteExpr(cellStr)) {
            return cellStr;
        }
        if (isBlank(lhs)) return null;
        return lhs + " == " + quoteLiteral(cellStr, type);
    }

    // ============================== 动作提取 ==============================

    /**
     * 从命中行中提取动作值
     *
     * <p>按 actionColumns 定义顺序构建 LinkedHashMap，键为动作列名，值为行内对应动作值。
     * 若未定义 actionColumns，则原样返回行内 actions 映射。
     */
    private Map<String, Object> extractActions(Map<String, Object> row, DecisionTableDO table) {
        Map<String, Object> actions = asMap(row.get("actions"));
        Map<String, Object> actionMap = actions != null ? actions : row;
        List<Map<String, Object>> actionColumns = table.getActionColumns();

        Map<String, Object> result = new LinkedHashMap<>();
        if (actionColumns != null) {
            for (Map<String, Object> column : actionColumns) {
                String name = resolveColumnName(column);
                if (isBlank(name)) {
                    continue;
                }
                result.put(name, actionMap.get(name));
            }
        } else {
            // 未定义动作列时，原样返回动作映射
            result.putAll(actionMap);
        }
        return result;
    }

    // ============================== 辅助方法 ==============================

    /**
     * 解析命中策略，为空时回退默认 FIRST
     */
    private String resolveHitPolicy(DecisionTableDO table) {
        String hitPolicy = table.getHitPolicy();
        return isBlank(hitPolicy) ? DEFAULT_HIT_POLICY : hitPolicy.trim();
    }

    /**
     * 解析行优先级：行内 priority 优先，其次决策表 priority，最后 0
     */
    private int resolveRowPriority(Map<String, Object> row, DecisionTableDO table) {
        Object rowPriority = row.get("priority");
        if (rowPriority instanceof Number n) {
            return n.intValue();
        }
        if (rowPriority != null) {
            try {
                return Integer.parseInt(rowPriority.toString());
            } catch (NumberFormatException ignored) {
                // 忽略非法优先级
            }
        }
        return table.getPriority() != null ? table.getPriority() : 0;
    }

    /**
     * 判断行是否被显式禁用
     */
    private boolean isRowDisabled(Map<String, Object> row) {
        Object enabled = row.get("enabled");
        if (enabled instanceof Boolean b && !b) {
            return true;
        }
        Object disabled = row.get("disabled");
        return disabled instanceof Boolean b && b;
    }

    /**
     * 构建默认动作结果（无命中行时使用）
     */
    private List<Map<String, Object>> defaultResult(DecisionTableDO table) {
        Map<String, Object> defaults = table.getDefaultActions();
        if (defaults == null || defaults.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new LinkedHashMap<>(defaults));
    }

    private List<Map<String, Object>> singleResult(Map<String, Object> actions) {
        return Collections.singletonList(actions);
    }

    /**
     * 获取表达式求值器：优先使用注入的 Bean，未注入时兜底创建默认沙箱实例
     */
    private ExpressionEvaluator getEvaluator() {
        if (expressionEvaluator != null) {
            return expressionEvaluator;
        }
        if (fallbackEvaluator == null) {
            synchronized (this) {
                if (fallbackEvaluator == null) {
                    log.warn("[DMN] ExpressionEvaluator Bean 未注入，使用默认沙箱 Aviator 实例兜底");
                    fallbackEvaluator = new AviatorExpressionEvaluator(true);
                }
            }
        }
        return fallbackEvaluator;
    }

    // ---------------------- 表达式构建辅助 ----------------------

    /**
     * 解析条件列左侧输入表达式
     *
     * <p>优先级：expression（计算输入，如 amount + tax）> inputExpression > field > name
     */
    private String resolveInputExpr(Map<String, Object> column) {
        for (String key : new String[]{"expression", "inputExpression", "field", "name"}) {
            Object val = column.get(key);
            if (val != null) {
                String s = val.toString().trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * 解析列名（用于条件/动作单元定位）
     *
     * <p>优先级：name > field > key > id
     */
    private String resolveColumnName(Map<String, Object> column) {
        for (String key : new String[]{"name", "field", "key", "id"}) {
            Object val = column.get(key);
            if (val != null) {
                String s = val.toString().trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * 判断单元格是否为通配符（恒成立）
     */
    private boolean isWildcard(String s) {
        if (isBlank(s)) {
            return true;
        }
        String t = s.trim();
        return "-".equals(t) || "*".equals(t) || "any".equalsIgnoreCase(t);
    }

    /**
     * 判断是否为比较片段（以比较运算符开头，需拼接 lhs）
     */
    private boolean isFragment(String s) {
        String t = s.trim();
        return t.startsWith(">") || t.startsWith("<")
                || t.startsWith("==") || t.startsWith("!=");
    }

    /**
     * 判断是否为完整表达式（含布尔/比较运算符，或以 ! ( 开头）
     */
    private boolean isCompleteExpr(String s) {
        String t = s.trim();
        return t.contains("&&") || t.contains("||")
                || t.indexOf('>') >= 0 || t.indexOf('<') >= 0
                || t.contains("==") || t.contains("!=")
                || t.startsWith("!") || t.startsWith("(");
    }

    /**
     * 将字面值按列类型转换为 Aviator 字面量
     *
     * <ul>
     *   <li>number/int/long/double/decimal → 原样返回（裸数字）</li>
     *   <li>boolean/bool → 原样返回（true/false）</li>
     *   <li>其它（含 string）→ 单引号包裹并转义内部单引号</li>
     * </ul>
     *
     * @param val  字面值字符串
     * @param type 列类型
     * @return Aviator 字面量
     */
    private String quoteLiteral(String val, String type) {
        if (val.isEmpty()) {
            return "''";
        }
        char c = val.charAt(0);
        // 已带引号，原样返回
        if (c == '\'' || c == '"') {
            return val;
        }
        if ("number".equalsIgnoreCase(type) || "numeric".equalsIgnoreCase(type)
                || "decimal".equalsIgnoreCase(type) || "int".equalsIgnoreCase(type)
                || "integer".equalsIgnoreCase(type) || "long".equalsIgnoreCase(type)
                || "double".equalsIgnoreCase(type) || "float".equalsIgnoreCase(type)) {
            return val;
        }
        if ("boolean".equalsIgnoreCase(type) || "bool".equalsIgnoreCase(type)) {
            return val;
        }
        return "'" + val.replace("'", "\\'") + "'";
    }

    private String stringify(Object val) {
        return val == null ? "" : String.valueOf(val).trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return null;
    }

    /**
     * 命中行内部载体
     */
    private static final class MatchedRow {
        final int rowIndex;
        final Map<String, Object> row;
        final int priority;

        MatchedRow(int rowIndex, Map<String, Object> row, int priority) {
            this.rowIndex = rowIndex;
            this.row = row;
            this.priority = priority;
        }
    }
}
