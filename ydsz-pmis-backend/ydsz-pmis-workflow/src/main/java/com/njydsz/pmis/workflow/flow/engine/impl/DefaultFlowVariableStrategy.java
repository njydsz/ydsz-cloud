package com.njydsz.pmis.workflow.flow.engine.impl;

import com.njydsz.pmis.workflow.flow.engine.FlowVariableStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认 SpEL 解析器
 *
 * <p>支持多种语法：
 * <ul>
 *   <li>${var} - 简单占位符替换</li>
 *   <li>${var > 100} - 简单比较表达式</li>
 *   <li>${a > 100} && ${b < 50} - 逻辑与（P2-14）</li>
 *   <li>${a > 100} || ${b < 50} - 逻辑或（P2-14）</li>
 *   <li>!${flag} - 逻辑非（P2-14）</li>
 *   <li>${cond ? 'A' : 'B'} - 三元运算符（P2-14）</li>
 *   <li>固定字符串：role:hr / dept:10 / user:1001</li>
 * </ul>
 *
 * <p>复杂 SpEL（如方法调用、对象属性）可通过替换本组件扩展。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DefaultFlowVariableStrategy implements FlowVariableStrategy {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_\\.]*)}");
    /** 字面量比较：lhs (op) rhs  -- lhs 可为标识符、数字、字符串 */
    private static final Pattern COMPARE_LITERAL = Pattern.compile(
            "^\\s*(.+?)\\s*(>=|<=|==|!=|>|<)\\s*(.+?)\\s*$");
    /** ${var} (op) 字面量  -- 主要使用模式（保留供未来扩展） */
    @SuppressWarnings("unused")
    private static final Pattern COMPARE_PLACEHOLDER = Pattern.compile(
            "^\\s*\\$\\{([^}]+)}\\s*(>=|<=|==|!=|>|<)\\s*(.+?)\\s*$");
    /** ${var op value} 内部比较模式  -- 即整体被 ${} 包裹且内部含运算符 */
    private static final Pattern COMPARE_INNER = Pattern.compile(
            "^\\s*([a-zA-Z_][a-zA-Z0-9_\\.]*)\\s*(>=|<=|==|!=|>|<)\\s*(.+?)\\s*$");
    /** 三元表达式：${cond ? trueVal : falseVal}  -- 整体被 ${} 包裹 */
    private static final Pattern TERNARY_INNER = Pattern.compile(
            "^\\s*(.+?)\\s*\\?\\s*(.+?)\\s*:\\s*(.+?)\\s*$");

    @Override
    public boolean evaluate(String condition, Map<String, Object> variables) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        String expr = condition.trim();
        try {
            return evaluateOr(expr, variables);
        } catch (Exception e) {
            log.error("[Flow] 条件解析异常: expr={} err={}", condition, e.getMessage());
            return false;
        }
    }

    /**
     * 顶层 || 逻辑或：任一子表达式为 true 即为 true。
     * 例如：${a > 100} || ${b < 50}
     */
    private boolean evaluateOr(String expr, Map<String, Object> variables) {
        String[] parts = splitTopLevel(expr, "||");
        for (String part : parts) {
            if (evaluateAnd(part.trim(), variables)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 顶层 && 逻辑与：所有子表达式为 true 才为 true。
     * 例如：${a > 100} && ${b < 50}
     */
    private boolean evaluateAnd(String expr, Map<String, Object> variables) {
        String[] parts = splitTopLevel(expr, "&&");
        for (String part : parts) {
            if (!evaluateNot(part.trim(), variables)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 逻辑非：!expr 形式，支持嵌套（如 !!flag）。
     */
    private boolean evaluateNot(String expr, Map<String, Object> variables) {
        String trimmed = expr.trim();
        if (trimmed.startsWith("!")) {
            return !evaluateNot(trimmed.substring(1).trim(), variables);
        }
        return evaluateSingle(trimmed, variables);
    }

    /**
     * 单一原子表达式求值（原有 evaluate 主体逻辑）：
     * - ${var op value} 比较表达式
     * - ${var} 非空判断
     * - true/false 字面量
     */
    private boolean evaluateSingle(String expr, Map<String, Object> variables) {
        if (expr.isEmpty()) {
            return true;
        }
        // 0. 如果整个表达式是单一 ${...} 占位符（允许非标识符内容如 "${var op value}"）
        Matcher fullPh = Pattern.compile("^\\$\\{(.+)}\\s*$").matcher(expr);
        if (fullPh.matches()) {
            String inner = fullPh.group(1).trim();
            // 先尝试 ${var op value} 格式：内部含运算符
            Matcher innerCmp = COMPARE_INNER.matcher(inner);
            if (innerCmp.matches()) {
                String varName = innerCmp.group(1).trim();
                String op = innerCmp.group(2);
                String rawValue = innerCmp.group(3).trim();
                Object actual = lookupValue(varName, variables);
                Object expected = parseLiteral(rawValue);
                return compare(actual, op, expected);
            }
            // 单一 ${var}：非空 + 非 false 即视为 true
            Object v = lookupValue(inner, variables);
            if (v == null) {
                return false;
            }
            if (v instanceof Boolean) {
                return (Boolean) v;
            }
            if (v instanceof String) {
                String s = ((String) v).trim();
                return !s.isEmpty() && !"false".equalsIgnoreCase(s);
            }
            return true;
        }
        // 1. 先做变量替换（${var} -> 实际值）
        String resolved = replacePlaceholders(expr, variables);
        // 2. 解析比较表达式 lhs op rhs
        Matcher m = COMPARE_LITERAL.matcher(resolved);
        if (m.matches() && isComparisonOperator(m.group(2))) {
            String rawLhs = m.group(1).trim();
            String op = m.group(2);
            String rawValue = m.group(3).trim();
            Object actual = parseLiteral(rawLhs);
            Object expected = parseLiteral(rawValue);
            return compare(actual, op, expected);
        }
        // 3. 布尔字面量
        if ("true".equalsIgnoreCase(resolved)) {
            return true;
        }
        if ("false".equalsIgnoreCase(resolved)) {
            return false;
        }
        log.warn("[Flow] 条件表达式无法识别: expr={} resolved={}", expr, resolved);
        return false;
    }

    private static boolean isComparisonOperator(String s) {
        return ">=".equals(s) || "<=".equals(s) || "==".equals(s)
                || "!=".equals(s) || ">".equals(s) || "<".equals(s);
    }

    @Override
    public String resolveAssignee(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String trimmed = expression.trim();
        // P2-14: 支持三元运算符 ${cond ? trueVal : falseVal}
        Matcher ternary = TERNARY_INNER.matcher(trimmed);
        if (ternary.matches() && trimmed.startsWith("${") && trimmed.endsWith("}")) {
            String cond = ternary.group(1).trim();
            String trueVal = ternary.group(2).trim();
            String falseVal = ternary.group(3).trim();
            boolean condResult = evaluate(cond, variables);
            String chosen = condResult ? trueVal : falseVal;
            return resolveLiteral(chosen, variables);
        }
        return replacePlaceholders(trimmed, variables);
    }

    /**
     * 解析三元分支的值：支持字符串字面量、${var} 引用、裸标识符。
     */
    private String resolveLiteral(String raw, Map<String, Object> variables) {
        String s = raw.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) ||
                (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        if (s.startsWith("${") && s.endsWith("}")) {
            String key = s.substring(2, s.length() - 1).trim();
            Object v = lookupValue(key, variables);
            return v == null ? "" : v.toString();
        }
        return s;
    }

    private String replacePlaceholders(String input, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return input;
        }
        Matcher m = PLACEHOLDER.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).trim();
            Object value = lookupValue(key, variables);
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Object lookupValue(String key, Map<String, Object> variables) {
        if (variables == null) {
            return null;
        }
        // 支持点路径：user.deptId
        if (key.contains(".")) {
            String[] parts = key.split("\\.");
            Object cursor = variables.get(parts[0]);
            for (int i = 1; i < parts.length && cursor != null; i++) {
                if (cursor instanceof Map<?, ?> map) {
                    cursor = map.get(parts[i]);
                } else {
                    try {
                        var field = cursor.getClass().getDeclaredField(parts[i]);
                        field.setAccessible(true);
                        cursor = field.get(cursor);
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
            return cursor;
        }
        return variables.get(key);
    }

    private Object parseLiteral(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
        if ((s.startsWith("\"") && s.endsWith("\"")) ||
                (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        try {
            if (s.contains(".")) {
                return Double.parseDouble(s);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException nfe) {
            return s;
        }
    }

    private boolean compare(Object actual, String op, Object expected) {
        if (actual == null && expected == null) {
            return "==".equals(op) || "!=".equals(op) ? "==".equals(op) : false;
        }
        if (actual == null || expected == null) {
            return false;
        }
        if (actual instanceof Number && expected instanceof Number) {
            double a = ((Number) actual).doubleValue();
            double b = ((Number) expected).doubleValue();
            return switch (op) {
                case ">" -> a > b;
                case ">=" -> a >= b;
                case "<" -> a < b;
                case "<=" -> a <= b;
                case "==" -> Double.compare(a, b) == 0;
                case "!=" -> Double.compare(a, b) != 0;
                default -> false;
            };
        }
        int cmp = String.valueOf(actual).compareTo(String.valueOf(expected));
        return switch (op) {
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            default -> false;
        };
    }

    /**
     * 在顶层分割字符串，不进入 ${} 块和 '...' / "..." 字面量内部。
     * 例如："${a > 1} && ${b < 2} || ${c == 3}" 按 "||" 分割得到 ["${a > 1} && ${b < 2}", " ${c == 3}"]
     *
     * @param expr      待分割的表达式
     * @param delimiter 顶层分隔符（如 "||" 或 "&&"）
     * @return 分割后的子表达式数组
     */
    private String[] splitTopLevel(String expr, String delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;          // ${} 嵌套深度
        boolean inSingle = false;
        boolean inDouble = false;
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            // 字符串字面量内部不解析
            if (inSingle) {
                current.append(c);
                if (c == '\'') inSingle = false;
                i++;
                continue;
            }
            if (inDouble) {
                current.append(c);
                if (c == '"') inDouble = false;
                i++;
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                current.append(c);
                i++;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                current.append(c);
                i++;
                continue;
            }
            // ${ 块开始：depth++
            if (c == '$' && i + 1 < expr.length() && expr.charAt(i + 1) == '{') {
                depth++;
                current.append("${");
                i += 2;
                continue;
            }
            // ${ 块结束：depth--
            if (c == '}' && depth > 0) {
                depth--;
                current.append(c);
                i++;
                continue;
            }
            // 顶层匹配分隔符
            if (depth == 0 && i + delimiter.length() <= expr.length()
                    && expr.substring(i, i + delimiter.length()).equals(delimiter)) {
                result.add(current.toString());
                current.setLength(0);
                i += delimiter.length();
                continue;
            }
            current.append(c);
            i++;
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }
}
