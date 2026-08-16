package com.njydsz.literule.server.impl;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.util.string.StringUtils;
import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleEnvironment;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.RuleSeverity;
import com.njydsz.literule.api.expression.ExpressionEngine;

/**
 * 表达式规则：基于 LiteExpr 表达式动态评估
 *
 * <p>从 {@link RuleDefinition} 构建，条件表达式返回 boolean 决定是否触发，
 * 严重度表达式可动态决定严重等级。支持 ${var} 模板渲染标题和描述。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class ExpressionRule implements Rule {

    private final RuleDefinition definition;
    private final ExpressionEngine evaluator;

    /**
     * 构造表达式规则
     *
     * @param definition 规则定义
     * @param evaluator  表达式求值器
     */
    public ExpressionRule(RuleDefinition definition, ExpressionEngine evaluator) {
        this.definition = definition;
        this.evaluator = evaluator;
    }

    /**
     * 获取规则编码（来自规则定义）
     *
     * @return 规则编码
     */
    @Override
    public String getCode() { return definition.getCode(); }

    /**
     * 获取规则名称（来自规则定义）
     *
     * @return 规则名称
     */
    @Override
    public String getName() { return definition.getName(); }

    /**
     * 获取规则分类（来自规则定义）
     *
     * @return 规则分类
     */
    @Override
    public String getCategory() { return definition.getCategory(); }

    /**
     * 获取规则优先级（来自规则定义）
     *
     * <p>priority 数值越小越先执行。
     *
     * @return 优先级数值
     */
    @Override
    public int getPriority() { return definition.getPriority(); }

    /**
     * 获取规则场景范围（来自规则定义）
     *
     * @return 场景标识；null 或 "ALL" 表示适用全部场景
     */
    @Override
    public String getScope() { return definition.getScope(); }

    /**
     * 获取互斥组标识（来自规则定义）
     *
     * <p>同一互斥组内，首个命中的规则触发后，同组后续规则将跳过评估。
     *
     * @return 互斥组标识；null 或空表示不参与互斥
     */
    @Override
    public String getMutexGroup() { return definition.getMutexGroup(); }

    /**
     * 暴露规则定义（用于灰度路由 / Trace 记录 / 监控指标）
     *
     * @return 原始规则定义
     * @since 1.0.0
     */
    @Override
    public RuleDefinition getRuleDefinition() { return definition; }

    /**
     * 租户 ID（来自规则定义）
     *
     * <p>1.5.0 起启用运行时租户过滤：{@link com.njydsz.literule.server.core.DefaultRuleEngine}
     * 在评估前会比较本方法返回值与 {@link RuleContext#getTenantId()}，仅当两者匹配时才评估该规则。
     *
     * @return 规则定义中的租户 ID；默认 "1"
     * @since 1.0.0
     */
    @Override
    public String getTenantId() { return definition.getTenantId(); }

    /**
     * 环境标识（来自规则定义，P1-5 多环境隔离）
     *
     * <p>1.6.0 起启用运行时环境过滤：{@link com.njydsz.literule.server.core.DefaultRuleEngine}
     * 在评估前会比较本方法返回值与 {@link RuleContext#getEnvironment()}：
     * 规则 environment 为 {@link RuleEnvironment#DEFAULT "default"} 时匹配任何上下文环境；
     * 非 "default" 时必须完全匹配。
     *
     * @return 规则定义中的环境标识；默认 "default"
     * @since 1.0.0
     */
    @Override
    public String getEnvironment() {
        String env = definition.getEnvironment();
        return env != null ? env : RuleEnvironment.DEFAULT;
    }

    /**
     * 评估规则条件并返回结果
     *
     * <p>执行流程：
     * <ol>
     *   <li>求值条件表达式（带缓存，同 context 内仅求值一次）</li>
     *   <li>条件不满足：返回 triggered=false 的结果</li>
     *   <li>条件满足：解析动态严重度、渲染标题/描述模板，返回完整触发结果</li>
     * </ol>
     *
     * <p>异常处理：评估过程中任意异常均被捕获，返回 triggered=false 的结果，
     * 异常信息记录到日志，不向上传播（异常隔离）。
     *
     * @param context 规则上下文（包含 facts、场景、租户等）
     * @return 规则评估结果（包含触发状态、严重度、标题、描述等）
     */
    @Override
    public RuleResult evaluate(RuleContext context) {
        long start = System.nanoTime();
        try {
            boolean triggered = Boolean.TRUE.equals(evalBooleanCached(definition.getConditionExpression(), context));
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
        if (StringUtils.isNotBlank(expr)) {
            Object code = evalCached(expr, context);
            RuleSeverity dynamic = RuleSeverity.fromCode(code == null ? null : String.valueOf(code));
            if (dynamic != null) return dynamic;
        }
        return definition.getDefaultSeverity() != null ? definition.getDefaultSeverity() : RuleSeverity.INFO;
    }

    /**
     * 渲染模板（支持 ${var} 占位符 + ${expression} LiteExpr 表达式 + 格式化）
     *
     * <p>支持的模板语法：
     * <ul>
     *   <li>{@code ${var}} — 简单变量替换（向后兼容）</li>
     *   <li>{@code ${amount * 0.1}} — LiteExpr 表达式求值</li>
     *   <li>{@code ${amount | #,##0.00}} — 数字格式化（| 后为格式模式）</li>
     *   <li>{@code ${amount | %.2f}} — printf 风格格式化</li>
     * </ul>
     *
     * @param template 模板字符串
     * @param context  规则上下文
     * @return 渲染后的字符串
     */
    private String renderTemplate(String template, RuleContext context) {
        if (StringUtils.isBlank(template)) {
            return getName();
        }
        String result = template;
        // 匹配 ${...} 模式，支持嵌套表达式和格式化
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)}");
        Matcher matcher = pattern.matcher(result);
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
                Object value = evalCached(expr, context);
                if (value == null) {
                    replacement = "";
                } else if (formatPattern != null) {
                    replacement = formatValue(value, formatPattern);
                } else if (value instanceof BigDecimal bd) {
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
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
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
                DecimalFormat df = new DecimalFormat(formatPattern);
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

    /**
     * 带缓存的布尔表达式求值（P2-9 条件冗余计算缓存）
     *
     * <p>同一 {@link RuleContext} 内，相同条件表达式仅求值一次；命中缓存直接返回，
     * 避免多条规则或同规则内（条件+严重度+模板）重复表达式的冗余计算。
     * 缓存随 {@code context} 生命周期自动失效，无需额外清理。
     *
     * @param expr    条件表达式
     * @param context 评估上下文
     * @return 布尔结果；expr 为 null/空返回 null
     */
    private Boolean evalBooleanCached(String expr, RuleContext context) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        Map<String, Object> cache = context.getExpressionCache();
        String key = "B:" + expr;
        Object cached = cache.get(key);
        if (cached != null) {
            return cached instanceof Boolean ? (Boolean) cached : Boolean.valueOf(String.valueOf(cached));
        }
        Boolean result = evaluator.evalBoolean(expr, context);
        cache.put(key, result);
        return result;
    }

    /**
     * 带缓存的对象表达式求值（P2-9 条件冗余计算缓存）
     *
     * <p>与 {@link #evalBooleanCached(String, RuleContext)} 同理，用于严重度/模板渲染表达式。
     *
     * @param expr    表达式
     * @param context 评估上下文
     * @return 求值结果；expr 为 null/空返回 null
     */
    private Object evalCached(String expr, RuleContext context) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        Map<String, Object> cache = context.getExpressionCache();
        String key = "O:" + expr;
        Object cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Object result = evaluator.eval(expr, context);
        cache.put(key, result);
        return result;
    }
}
