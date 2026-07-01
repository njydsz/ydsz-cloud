package com.njydsz.pmis.workflow.flow.engine.impl;

import com.njydsz.pmis.workflow.flow.engine.FlowVariableStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认 SpEL 解析器
 *
 * <p>支持两种语法：
 * <ul>
 *   <li>${var} - 简单占位符替换</li>
 *   <li>${var > 100} - 简单比较表达式</li>
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

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern COMPARE = Pattern.compile("^\\s*([a-zA-Z_][a-zA-Z0-9_\\.]*)\\s*(>=|<=|==|!=|>|<)\\s*(.+?)\\s*$");

    @Override
    public boolean evaluate(String condition, Map<String, Object> variables) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        String expr = condition.trim();
        try {
            // 1. 先做变量替换
            String resolved = replacePlaceholders(expr, variables);
            // 2. 尝试解析比较表达式
            Matcher m = COMPARE.matcher(resolved);
            if (m.matches()) {
                String key = m.group(1);
                String op = m.group(2);
                String rawValue = m.group(3).trim();
                Object actual = lookupValue(key, variables);
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
            log.warn("[Flow] 条件表达式无法识别: expr={} resolved={}", condition, resolved);
            return false;
        } catch (Exception e) {
            log.error("[Flow] 条件解析异常: expr={} err={}", condition, e.getMessage());
            return false;
        }
    }

    @Override
    public String resolveAssignee(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        return replacePlaceholders(expression.trim(), variables);
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
        if (s.startsWith("\"") && s.endsWith("\"")) {
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

    @SuppressWarnings({"rawtypes", "unchecked"})
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
}
