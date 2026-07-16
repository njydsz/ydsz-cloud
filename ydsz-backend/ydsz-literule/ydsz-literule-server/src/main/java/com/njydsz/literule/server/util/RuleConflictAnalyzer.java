package com.njydsz.literule.server.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则冲突分析工具（P1-4 架构优化）。
 *
 * <p>提取 literule 模块的 {@code RuleConflictDetector} 和 project 模块的
 * {@code RuleConflictDetector} 中重复的变量提取和重叠分析逻辑。
 *
 * <h3>核心方法</h3>
 * <ul>
 *   <li>{@link #extractVariables(String)} — 从表达式中提取变量名集合</li>
 *   <li>{@link #calculateOverlapRatio(Set, Set)} — 计算两个变量集合的重叠比例</li>
 *   <li>{@link #determineSeverity(double)} — 根据重叠比例判定严重等级</li>
 * </ul>
 *
 * @since 1.6.0 (P1-4)
 */
public final class RuleConflictAnalyzer {

    /** 提取变量名的正则 */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\b([a-zA-Z_]\\w*)\\b");

    /** 关键字/函数名，非变量 */
    private static final Set<String> KEYWORDS = Set.of(
            "true", "false", "nil", "null",
            "RED", "YELLOW", "INFO", "GREEN", "BLUE",
            "if", "else", "return", "seq", "lambda",
            "println", "print", "p", "string", "long", "double",
            "boolean", "int", "math", "Math", "max", "min", "abs",
            "round", "floor", "ceil", "sqrt", "pow", "log",
            "contains", "startsWith", "endsWith", "length",
            "count", "sum", "avg", "rand", "now", "date",
            "and", "or", "not"
    );

    private RuleConflictAnalyzer() {
    }

    /**
     * 从表达式文本中提取变量名集合。
     *
     * <p>过滤关键字、数字、单字符标识符。保留首字母小写的标识符（驼峰变量名）
     * 和含下划线的标识符。
     *
     * @param expression 条件表达式
     * @return 变量名集合；空表达式返回空集合
     */
    public static Set<String> extractVariables(String expression) {
        if (expression == null || expression.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> vars = new HashSet<>();
        Matcher matcher = VAR_PATTERN.matcher(expression);
        while (matcher.find()) {
            String word = matcher.group(1);
            if (KEYWORDS.contains(word)) continue;
            if (word.matches("\\d+")) continue;
            if (word.length() <= 1) continue;
            // 保留首字母小写的标识符（驼峰变量名）或含下划线的标识符
            if (Character.isLowerCase(word.charAt(0)) || word.contains("_")) {
                vars.add(word);
            }
        }
        return vars;
    }

    /**
     * 计算两个变量集合的重叠比例（Jaccard 系数）。
     *
     * @param varsA 变量集合 A
     * @param varsB 变量集合 B
     * @return 重叠比例 [0, 1]；任一为空返回 0
     */
    public static double calculateOverlapRatio(Set<String> varsA, Set<String> varsB) {
        if (varsA == null || varsB == null || varsA.isEmpty() || varsB.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(varsA);
        intersection.retainAll(varsB);
        Set<String> union = new HashSet<>(varsA);
        union.addAll(varsB);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    /**
     * 根据重叠比例判定严重等级。
     *
     * @param overlapRatio 重叠比例 [0, 1]
     * @return "high" / "medium" / "low"
     */
    public static String determineSeverity(double overlapRatio) {
        if (overlapRatio >= 0.8) {
            return "high";
        } else if (overlapRatio >= 0.4) {
            return "medium";
        } else {
            return "low";
        }
    }

    /**
     * 计算两个变量集合的交集。
     *
     * @param varsA 变量集合 A
     * @param varsB 变量集合 B
     * @return 交集集合；任一为空返回空集合
     */
    public static Set<String> intersection(Set<String> varsA, Set<String> varsB) {
        if (varsA == null || varsB == null) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>(varsA);
        result.retainAll(varsB);
        return result;
    }
}
