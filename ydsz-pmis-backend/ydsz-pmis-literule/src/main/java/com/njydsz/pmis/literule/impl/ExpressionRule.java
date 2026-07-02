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
import java.util.Map;

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
     * 渲染模板（支持 ${var} 占位符）
     *
     * @param template 模板字符串
     * @param context  规则上下文
     * @return 渲染后的字符串
     */
    private String renderTemplate(String template, RuleContext context) {
        if (StrUtil.isBlank(template)) {
            return getName();
        }
        Map<String, Object> facts = context.getFacts();
        String result = template;
        for (Map.Entry<String, Object> entry : facts.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
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
