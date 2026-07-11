package com.njydsz.pmis.literule.server.adaptive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条件表达式阈值提取器（P3-4 自适应智能风控）
 *
 * <p>从 LiteExpr 条件表达式中提取变量与阈值的比较关系，用于自适应阈值调整。
 *
 * <p>支持的表达式形态：
 * <ul>
 *   <li>简单比较：{@code amount > 1000} → {variable:"amount", operator:"&gt;", threshold:1000}</li>
 *   <li>组合表达式：{@code amount > 1000 && score < 800} → 两条 ThresholdInfo</li>
 *   <li>带空格：{@code amount   &gt;=   1000} → 自动归一化</li>
 *   <li>变量在右：{@code 1000 &lt; amount} → 自动翻转运算符为 amount &gt; 1000</li>
 *   <li>小数阈值：{@code ratio &gt; 0.5}</li>
 *   <li>负数阈值：{@code balance &lt; -100}</li>
 * </ul>
 *
 * <p>不支持的表达式（返回空列表）：
 * <ul>
 *   <li>纯函数调用：{@code fn(x) &gt; 1}</li>
 *   <li>嵌套表达式：{@code (a + b) &gt; c}</li>
 *   <li>字符串比较：{@code name == "abc"}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public final class ThresholdExtractor {

    private ThresholdExtractor() {
    }

    /**
     * 比较运算符正则：变量 + 运算符 + 数字（变量在左）
     *
     * <p>分组说明：
     * <ul>
     *   <li>group(1)=变量名</li>
     *   <li>group(2)=运算符</li>
     *   <li>group(3)=数字（含小数与负号）</li>
     * </ul>
     */
    private static final Pattern VAR_LEFT_PATTERN = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_]*)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)");

    /**
     * 比较运算符正则：数字 + 运算符 + 变量（变量在右）
     *
     * <p>用于识别 {@code 1000 < amount} 形式，提取后翻转运算符为 {@code amount > 1000}。
     */
    private static final Pattern VAR_RIGHT_PATTERN = Pattern.compile(
            "(-?\\d+(?:\\.\\d+)?)\\s*(>=|<=|==|!=|>|<)\\s*([A-Za-z_][A-Za-z0-9_]*)");

    /**
     * 从表达式中提取阈值信息
     *
     * @param conditionExpression 条件表达式（LiteExpr 语法）
     * @return 阈值信息列表；表达式为空或不包含可识别的阈值比较时返回空列表
     */
    public static List<ThresholdInfo> extract(String conditionExpression) {
        List<ThresholdInfo> result = new ArrayList<>();
        if (conditionExpression == null || conditionExpression.isBlank()) {
            return result;
        }

        // 先匹配变量在左的形式
        Matcher leftMatcher = VAR_LEFT_PATTERN.matcher(conditionExpression);
        while (leftMatcher.find()) {
            String variable = leftMatcher.group(1);
            String operator = leftMatcher.group(2);
            double threshold = Double.parseDouble(leftMatcher.group(3));
            result.add(ThresholdInfo.builder()
                    .variable(variable)
                    .operator(operator)
                    .threshold(threshold)
                    .build());
        }

        // 再匹配变量在右的形式（翻转运算符）
        Matcher rightMatcher = VAR_RIGHT_PATTERN.matcher(conditionExpression);
        while (rightMatcher.find()) {
            String numberStr = rightMatcher.group(1);
            String operator = rightMatcher.group(2);
            String variable = rightMatcher.group(3);
            double threshold = Double.parseDouble(numberStr);
            String flipped = flipOperator(operator);
            // 跳过已被 VAR_LEFT_PATTERN 匹配过的相同位置（避免重复）
            // 通过判断 variable 是否已经在结果中且 threshold 相同来去重
            if (alreadyContains(result, variable, flipped, threshold)) {
                continue;
            }
            result.add(ThresholdInfo.builder()
                    .variable(variable)
                    .operator(flipped)
                    .threshold(threshold)
                    .build());
        }

        return result;
    }

    /**
     * 翻转运算符（变量在右 → 变量在左）
     *
     * @param op 原运算符
     * @return 翻转后的运算符
     */
    private static String flipOperator(String op) {
        return switch (op) {
            case ">" -> "<";
            case "<" -> ">";
            case ">=" -> "<=";
            case "<=" -> ">=";
            default -> op; // == 和 != 不需要翻转
        };
    }

    /**
     * 判断结果集中是否已包含相同的阈值信息（去重）
     */
    private static boolean alreadyContains(List<ThresholdInfo> result, String variable,
                                            String operator, double threshold) {
        for (ThresholdInfo info : result) {
            if (info.getVariable().equals(variable)
                    && info.getOperator().equals(operator)
                    && Double.compare(info.getThreshold(), threshold) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 阈值信息（变量 + 运算符 + 阈值）
     *
     * @author ydsz-pmis-team
     * @since 1.8.0
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThresholdInfo {
        /** 变量名 */
        private String variable;

        /** 运算符（&gt;、&gt;=、&lt;、&lt;=、==、!=） */
        private String operator;

        /** 阈值 */
        private double threshold;
    }
}
