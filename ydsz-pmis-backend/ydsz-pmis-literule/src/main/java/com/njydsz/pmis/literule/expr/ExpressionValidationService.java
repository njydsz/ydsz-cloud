package com.njydsz.pmis.literule.expr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表达式校验服务
 *
 * <p>面向前端表达式编辑器的高层校验 API，封装 {@link ExpressionEvaluator#validateDetailed(String)}
 * 并叠加业务语义校验（条件表达式必须返回 boolean、严重度表达式取值合法、模板占位符闭合等）。
 *
 * <p>当前实现的特点：
 * <ul>
 *   <li>不依赖 VariableRegistry（P2-4 尚未落地），所以 UNDEFINED_VARIABLE 类型暂时不会触发</li>
 *   <li>从表达式文本中提取引用变量，用于前端"已使用变量"提示</li>
 *   <li>模板表达式校验仅做 {@code ${var}} 占位符闭合检查，不做变量存在性校验</li>
 * </ul>
 *
 * <p>Bean 装配见 {@link com.njydsz.pmis.literule.config.LiteRuleAutoConfiguration#expressionValidationService}。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RequiredArgsConstructor
public class ExpressionValidationService {

    private final ExpressionEvaluator evaluator;

    /** 模板占位符正则：${var} 或 ${ a.b.c } */
    private static final Pattern TEMPLATE_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]*)\\}");

    /** 不平衡的 ${ 开占位符（用于检测未闭合） */
    private static final Pattern UNCLOSED_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{[^}]*$");

    /**
     * 校验条件表达式（必须返回 boolean）
     *
     * <p>当前实现只校验语法合法性，不强制运行时类型检查（避免误报）。
     * VariableRegistry（P2-4）落地后，可在此叠加变量存在性校验。
     *
     * @param expression 条件表达式
     * @return 校验结果
     */
    public ExpressionValidationResult validateCondition(String expression) {
        return evaluator.validateDetailed(expression);
    }

    /**
     * 校验严重度表达式（可选，返回值应为 RED/YELLOW/INFO）
     *
     * <p>仅校验语法合法性，不强制返回值约束（因为严重度表达式可以返回任意字符串，
     * 由 {@link com.njydsz.pmis.literule.api.RuleSeverity#fromCode(String)} 解析）。
     *
     * @param expression 严重度表达式
     * @return 校验结果
     */
    public ExpressionValidationResult validateSeverity(String expression) {
        if (expression == null || expression.isBlank()) {
            // 严重度表达式可选，为空时使用 defaultSeverity
            return ExpressionValidationResult.ok(expression, 0L, List.of());
        }
        return evaluator.validateDetailed(expression);
    }

    /**
     * 校验模板表达式（支持 ${var} 占位符）
     *
     * <p>仅校验占位符是否闭合，不校验变量是否存在。
     * 普通文本（不含占位符）视为合法。
     *
     * @param template 模板字符串
     * @return 校验结果
     */
    public ExpressionValidationResult validateTemplate(String template) {
        long start = System.nanoTime();
        long elapsed;

        if (template == null || template.isBlank()) {
            elapsed = (System.nanoTime() - start) / 1_000_000L;
            // 模板可选，为空视为合法
            return ExpressionValidationResult.ok(template, elapsed, List.of());
        }

        // 检测未闭合的占位符
        if (UNCLOSED_PLACEHOLDER_PATTERN.matcher(template).find()) {
            elapsed = (System.nanoTime() - start) / 1_000_000L;
            return ExpressionValidationResult.fail(template,
                    ExpressionValidationResult.ErrorType.TEMPLATE_FORMAT_ERROR,
                    "模板存在未闭合的占位符 ${ ... }，缺少 }", elapsed);
        }

        // 提取占位符中引用的变量
        List<String> referencedVars = new ArrayList<>();
        Matcher m = TEMPLATE_PLACEHOLDER_PATTERN.matcher(template);
        while (m.find()) {
            String var = m.group(1).trim();
            if (!var.isEmpty()) {
                referencedVars.add(var);
            }
        }

        elapsed = (System.nanoTime() - start) / 1_000_000L;
        return ExpressionValidationResult.ok(template, elapsed, referencedVars);
    }

    /**
     * 批量校验一组表达式
     *
     * @param expressions 表达式列表（key=标签，value=表达式文本）
     * @return 校验结果列表（与输入顺序一致）
     */
    public java.util.Map<String, ExpressionValidationResult> validateBatch(java.util.Map<String, String> expressions) {
        java.util.Map<String, ExpressionValidationResult> results = new java.util.LinkedHashMap<>();
        if (expressions == null) {
            return results;
        }
        expressions.forEach((label, expr) -> {
            ExpressionValidationResult result;
            try {
                result = evaluator.validateDetailed(expr);
            } catch (Exception e) {
                result = ExpressionValidationResult.fail(expr,
                        ExpressionValidationResult.ErrorType.UNKNOWN,
                        "校验异常: " + e.getMessage(), 0L);
            }
            results.put(label, result);
        });
        return results;
    }
}
