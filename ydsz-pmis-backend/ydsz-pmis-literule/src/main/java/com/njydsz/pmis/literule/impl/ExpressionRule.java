package com.njydsz.pmis.literule.impl;

import cn.hutool.core.util.StrUtil;
import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 表达式规则：基于 Aviator 表达式动态评估
 *
 * <p>从 {@link RuleDefinition} 构建，条件表达式返回 boolean 决定是否触发，
 * 严重度表达式可动态决定严重等级。支持 ${var} 模板渲染标题和描述。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
public class ExpressionRule implements Rule {

    private final RuleDefinition definition;
    private final ExpressionEvaluator evaluator;

    /**
     * 构造表达式规则
     *
     * @param definition 规则定义
     * @param evaluator  表达式求值器
     */
    public ExpressionRule(RuleDefinition definition, ExpressionEvaluator evaluator) {
        this.definition = definition;
        this.evaluator = evaluator;
    }

    @Override
    public String getCode() { return definition.getCode(); }

    @Override
    public String getName() { return definition.getName(); }

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
            boolean triggered = evaluator.evalBoolean(definition.getConditionExpression(), context);
            if (!triggered) {
                return RuleResult.builder()
                        .ruleCode(getCode())
                        .ruleName(getName())
                        .category(getCategory())
                        .triggered(false)
                        .triggeredAt(LocalDateTime.now())
                        .elapsedMs(elapsedMs(start))
                        .build();
            }

            // 解析严重度
            RuleSeverity severity = resolveSeverity(context);

            // 渲染标题和描述
            String title = renderTemplate(definition.getTitleTemplate(), context);
            String description = renderTemplate(definition.getDescriptionTemplate(), context);

            return RuleResult.builder()
                    .ruleCode(getCode())
                    .ruleName(getName())
                    .category(getCategory())
                    .triggered(true)
                    .severity(severity)
                    .title(title)
                    .description(description)
                    .scope(definition.getScope())
                    .threshold(definition.getConditionExpression())
                    .triggeredAt(LocalDateTime.now())
                    .drilldownAvailable(definition.isDrilldownAvailable())
                    .elapsedMs(elapsedMs(start))
                    .build();
        } catch (Exception e) {
            log.warn("[LiteRule] 表达式规则 {} 评估异常: {}", getCode(), e.getMessage());
            return RuleResult.builder()
                    .ruleCode(getCode())
                    .triggered(false)
                    .triggeredAt(LocalDateTime.now())
                    .elapsedMs(elapsedMs(start))
                    .build();
        }
    }

    /**
     * 解析严重度（支持动态表达式）
     *
     * @param context 规则上下文
     * @return 严重度
     */
    private RuleSeverity resolveSeverity(RuleContext context) {
        String expr = definition.getSeverityExpression();
        if (StrUtil.isNotBlank(expr)) {
            String code = evaluator.eval(expr, context);
            RuleSeverity dynamic = RuleSeverity.fromCode(code);
            if (dynamic != null) return dynamic;
        }
        return definition.getDefaultSeverity() != null ? definition.getDefaultSeverity() : RuleSeverity.INFO;
    }

    /**
     * 渲染模板（支持 ${var} 占位符 + ${expression} Aviator 表达式 + 格式化）
     *
     * <p>支持的模板语法：
     * <ul>
     *   <li>{@code ${var}} — 简单变量替换（向后兼容）</li>
     *   <li>{@code ${amount * 0.1}} — Aviator 表达式求值</li>
     *   <li>{@code ${amount | #,##0.00}} — 数字格式化（| 后为格式模式）</li>
     *   <li>{@code ${amount | %.2f}} — printf 风格格式化</li>
     * </ul>
     *
     * @param template 模板字符串
     * @param context  规则上下文
     * @return 渲染后的字符串
     */
    private String renderTemplate(String template, RuleContext context) {
        if (StrUtil.isBlank(template)) {
            return getName();
        }
        String result = template;
        // 匹配 ${...} 模式，支持嵌套表达式和格式化
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^}]+)}");
        java.util.regex.Matcher matcher = pattern.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            String replacement;
            // 检查是否有格式化指令（| 分隔）
            String formatPattern = null;
            int pipeIdx = expr.indexOf('|');
            if (pipeIdx > 0) {
                formatPattern = expr.substring(pipeIdx + 1).trim();
                expr = expr.substring(0, pipeIdx).trim();
            }
            try {
                Object value = evaluator.eval(expr, context);
                if (value == null) {
                    replacement = "";
                } else if (formatPattern != null) {
                    replacement = formatValue(value, formatPattern);
                } else if (value instanceof java.math.BigDecimal bd) {
                    // 整数去除小数点（100.0 → 100），非整数保留原值
                    double d = bd.doubleValue();
                    if (d == Math.floor(d) && !Double.isInfinite(d)) {
                        replacement = String.valueOf((long) d);
                    } else {
                        replacement = bd.toPlainString();
                    }
                } else {
                    replacement = String.valueOf(value);
                }
            } catch (Exception e) {
                // 表达式求值失败，尝试简单变量替换（向后兼容）
                Object factValue = context.getFacts().get(expr);
                replacement = factValue != null ? String.valueOf(factValue) : "${" + expr + "}";
            }
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 格式化数值
     *
     * @param value         值
     * @param formatPattern 格式模式（支持 DecimalFormat 模式或 printf 风格）
     * @return 格式化后的字符串
     */
    private String formatValue(Object value, String formatPattern) {
        try {
            if (formatPattern.startsWith("%")) {
                // printf 风格格式化
                return String.format(formatPattern, value);
            } else {
                // DecimalFormat 模式
                java.text.DecimalFormat df = new java.text.DecimalFormat(formatPattern);
                return df.format(value);
            }
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 计算耗时（毫秒）
     *
     * @param startNano 开始纳秒
     * @return 耗时毫秒
     */
    private long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    /**
     * 获取规则定义
     *
     * @return 规则定义
     */
    public RuleDefinition getDefinition() {
        return definition;
    }
}
