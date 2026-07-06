package com.njydsz.pmis.workflow.dmn;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DMN 决策表执行引擎
 *
 * <p>P0-4: DMN 决策表引擎（对标 Camunda/Flowable DMN）。
 *
 * <p>执行流程：
 * <ol>
 *   <li>遍历所有规则行</li>
 *   <li>对每个规则，检查所有输入条件是否匹配</li>
 *   <li>根据命中策略返回结果</li>
 * </ol>
 *
 * <p>条件匹配使用简单解析，不依赖 SpEL：
 * <ul>
 *   <li>{@code >100} → 数值大于</li>
 *   <li>{@code <100} → 数值小于</li>
 *   <li>{@code >=100} → 数值大于等于</li>
 *   <li>{@code <=100} → 数值小于等于</li>
 *   <li>{@code ==100} 或 {@code 100} → 等于</li>
 *   <li>{@code !=100} → 不等于</li>
 *   <li>{@code '紧急'} 或 {@code ==紧急} → 字符串等于</li>
 *   <li>{@code in(1,2,3)} → 在列表中</li>
 *   <li>{@code between(1,100)} → 范围内</li>
 *   <li>{@code contains(x)} → 包含子串</li>
 *   <li>{@code -} 或空 → 任意匹配（always true）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Component
public class DmnEngine {

    /**
     * 执行 DMN 决策表
     *
     * @param table   决策表定义
     * @param context 输入上下文（变量名 → 值）
     * @return 输出结果列表（每个匹配规则产生一组输出）
     */
    public List<Map<String, Object>> execute(DmnDecisionTable table, Map<String, Object> context) {
        if (table == null) {
            return List.of();
        }
        if (table.getRules() == null || table.getRules().isEmpty()) {
            log.warn("[DmnEngine] 决策表无规则行: tableKey={}", table.getTableKey());
            return List.of();
        }
        Map<String, Object> ctx = context == null ? Map.of() : context;

        DmnHitPolicy policy = table.getHitPolicy() == null ? DmnHitPolicy.UNIQUE : table.getHitPolicy();
        List<Map<String, Object>> matched = new ArrayList<>();

        for (DmnRule rule : table.getRules()) {
            if (matchRule(table, rule, ctx)) {
                matched.add(buildOutput(table, rule));
                // 单结果策略：命中即返回
                if (policy == DmnHitPolicy.FIRST
                        || policy == DmnHitPolicy.ANY
                        || policy == DmnHitPolicy.PRIORITY) {
                    break;
                }
            }
        }

        // UNIQUE: 多条命中报错
        if (policy == DmnHitPolicy.UNIQUE && matched.size() > 1) {
            throw new IllegalStateException(
                    "[DmnEngine] UNIQUE 命中策略下匹配到多条规则: tableKey=" + table.getTableKey()
                            + " count=" + matched.size());
        }

        // COLLECT: 按聚合运算符聚合
        if (policy == DmnHitPolicy.COLLECT) {
            return aggregate(matched, table);
        }

        return matched;
    }

    // ============================== 规则匹配 ==============================

    /**
     * 判断单条规则是否匹配（所有输入条件都满足才命中）
     */
    private boolean matchRule(DmnDecisionTable table, DmnRule rule, Map<String, Object> context) {
        if (rule == null || rule.getInputEntries() == null) {
            return false;
        }
        List<DmnInput> inputs = table.getInputs();
        if (inputs == null || inputs.isEmpty()) {
            return true;
        }
        List<String> entries = rule.getInputEntries();
        for (int i = 0; i < inputs.size(); i++) {
            DmnInput input = inputs.get(i);
            String condition = i < entries.size() ? entries.get(i) : null;
            if (!matchInput(input, condition, context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断单个输入条件是否匹配
     */
    private boolean matchInput(DmnInput input, String condition, Map<String, Object> context) {
        // 空条件或 "-" → 任意匹配
        if (condition == null || condition.trim().isEmpty() || "-".equals(condition.trim())) {
            return true;
        }
        Object actual = getValue(context, input);
        if (actual == null) {
            return false;
        }
        String cond = condition.trim();

        // 函数式条件：in(...)/between(...)/contains(...)
        String lower = cond.toLowerCase();
        if (lower.startsWith("in(") && cond.endsWith(")")) {
            return matchIn(actual, cond.substring(3, cond.length() - 1));
        }
        if (lower.startsWith("between(") && cond.endsWith(")")) {
            return matchBetween(actual, cond.substring(8, cond.length() - 1));
        }
        if (lower.startsWith("contains(") && cond.endsWith(")")) {
            return matchContains(actual, cond.substring(9, cond.length() - 1));
        }

        // 比较运算符
        if (cond.startsWith(">=")) return compare(actual, cond.substring(2)) >= 0;
        if (cond.startsWith("<=")) return compare(actual, cond.substring(2)) <= 0;
        if (cond.startsWith("!=")) return compare(actual, cond.substring(2)) != 0;
        if (cond.startsWith("==")) return compare(actual, cond.substring(2)) == 0;
        if (cond.startsWith(">")) return compare(actual, cond.substring(1)) > 0;
        if (cond.startsWith("<")) return compare(actual, cond.substring(1)) < 0;

        // 无运算符 → 等于
        return compare(actual, cond) == 0;
    }

    /**
     * 从上下文中取输入值
     *
     * <p>取值 key 优先使用 input.expression，为空则使用 input.name。
     */
    private Object getValue(Map<String, Object> context, DmnInput input) {
        if (input == null) {
            return null;
        }
        String key = (input.getExpression() != null && !input.getExpression().isBlank())
                ? input.getExpression() : input.getName();
        if (key == null || key.isBlank()) {
            return null;
        }
        return context.get(key);
    }

    // ============================== 条件运算 ==============================

    /**
     * 比较实际值与条件值
     *
     * @return 负数=实际小于条件，0=相等，正数=实际大于条件
     */
    private int compare(Object actual, String condStr) {
        String expected = stripQuotes(condStr.trim());
        Double actualNum = toDouble(actual);
        Double expectedNum = toDouble(expected);
        // 双方均可解析为数值 → 数值比较
        if (actualNum != null && expectedNum != null) {
            return Double.compare(actualNum, expectedNum);
        }
        // 布尔比较
        Boolean actualBool = toBoolean(actual);
        Boolean expectedBool = toBoolean(expected);
        if (actualBool != null && expectedBool != null) {
            return Boolean.compare(actualBool, expectedBool);
        }
        // 字符串比较
        return String.valueOf(actual).compareTo(expected);
    }

    /**
     * in(...) 列表匹配
     */
    private boolean matchIn(Object actual, String args) {
        if (args == null || args.isBlank()) {
            return false;
        }
        for (String item : args.split(",")) {
            if (compare(actual, item.trim()) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * between(min,max) 范围匹配（闭区间）
     */
    private boolean matchBetween(Object actual, String args) {
        if (args == null || args.isBlank()) {
            return false;
        }
        String[] parts = args.split(",");
        if (parts.length < 2) {
            return false;
        }
        Double actualNum = toDouble(actual);
        Double min = toDouble(stripQuotes(parts[0].trim()));
        Double max = toDouble(stripQuotes(parts[1].trim()));
        if (actualNum == null || min == null || max == null) {
            return false;
        }
        return actualNum >= min && actualNum <= max;
    }

    /**
     * contains(x) 子串匹配
     */
    private boolean matchContains(Object actual, String args) {
        if (args == null) {
            return false;
        }
        String expected = stripQuotes(args.trim());
        return String.valueOf(actual).contains(expected);
    }

    // ============================== 输出构建 ==============================

    /**
     * 构建单条规则的输出 Map（output.name → 解析后的值）
     */
    private Map<String, Object> buildOutput(DmnDecisionTable table, DmnRule rule) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<DmnOutput> outputs = table.getOutputs();
        List<String> outputEntries = rule.getOutputEntries();
        if (outputs == null || outputEntries == null) {
            return out;
        }
        for (int i = 0; i < outputs.size() && i < outputEntries.size(); i++) {
            DmnOutput output = outputs.get(i);
            out.put(output.getName(), parseOutputValue(outputEntries.get(i)));
        }
        return out;
    }

    /**
     * 解析输出值字符串为具体类型
     *
     * <p>支持：单引号/双引号字符串、整数、小数、布尔。
     */
    private Object parseOutputValue(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || "-".equals(s)) {
            return null;
        }
        // 引号字符串
        if ((s.startsWith("'") && s.endsWith("'"))
                || (s.startsWith("\"") && s.endsWith("\""))) {
            return s.substring(1, s.length() - 1);
        }
        // 布尔
        if ("true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        // 整数
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
            // 继续尝试小数
        }
        // 小数
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            // 回退为字符串
        }
        return s;
    }

    // ============================== COLLECT 聚合 ==============================

    /**
     * COLLECT 命中策略聚合
     *
     * <ul>
     *   <li>{@code LIST}（默认）— 返回所有命中行</li>
     *   <li>{@code COUNT} — 返回命中数量</li>
     *   <li>{@code SUM/MIN/MAX} — 对每个输出列做数值聚合，返回单条结果</li>
     * </ul>
     */
    private List<Map<String, Object>> aggregate(List<Map<String, Object>> matched, DmnDecisionTable table) {
        String op = table.getCollectOperator();
        if (op == null || op.isBlank() || "LIST".equalsIgnoreCase(op)) {
            return matched;
        }
        if (matched.isEmpty()) {
            return matched;
        }
        if ("COUNT".equalsIgnoreCase(op)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", matched.size());
            return List.of(result);
        }
        // SUM/MIN/MAX 逐列聚合
        Map<String, Object> result = new LinkedHashMap<>();
        List<DmnOutput> outputs = table.getOutputs();
        if (outputs == null) {
            return List.of(result);
        }
        for (DmnOutput output : outputs) {
            String name = output.getName();
            List<Double> nums = new ArrayList<>();
            for (Map<String, Object> row : matched) {
                Double d = toDouble(row.get(name));
                if (d != null) {
                    nums.add(d);
                }
            }
            if (nums.isEmpty()) {
                result.put(name, null);
                continue;
            }
            switch (op.toUpperCase()) {
                case "SUM" -> result.put(name, nums.stream().mapToDouble(d -> d).sum());
                case "MIN" -> result.put(name, nums.stream().mapToDouble(d -> d).min().orElse(0));
                case "MAX" -> result.put(name, nums.stream().mapToDouble(d -> d).max().orElse(0));
                default -> result.put(name, nums);
            }
        }
        return List.of(result);
    }

    // ============================== 工具方法 ==============================

    /**
     * 去除字符串首尾引号（单引号或双引号）
     */
    private String stripQuotes(String s) {
        if (s == null || s.length() < 2) {
            return s;
        }
        if ((s.startsWith("'") && s.endsWith("'"))
                || (s.startsWith("\"") && s.endsWith("\""))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * 安全转换为 Double，无法转换返回 null
     */
    private Double toDouble(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        String s = obj.toString().trim();
        // 去除引号后尝试解析
        String stripped = stripQuotes(s);
        try {
            return Double.parseDouble(stripped);
        } catch (NumberFormatException e) {
            log.warn("[DmnEngine] Double 解析失败 s={}: {}", s, e.getMessage());
            return null;
        }
    }

    /**
     * 安全转换为 Boolean，无法转换返回 null
     */
    private Boolean toBoolean(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        String s = obj.toString().trim();
        if ("true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
