package com.njydsz.literule.server.expr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.expr.ExpressionEvaluator;
import com.njydsz.literule.api.expr.ExpressionValidationResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 表达式校验服务
 *
 * <p>面向前端表达式编辑器的高层校验 API，封装 {@link ExpressionEvaluator#validateDetailed(String)}
 * 并叠加业务语义校验（条件表达式必须返回 boolean、严重度表达式取值合法、模板占位符闭合等）。
 *
 * <p>1.4.0 起支持 VariableRegistry（P2-4）：
 * <ul>
 *   <li>当注入非空 {@link VariableRegistry} 时，启用 UNDEFINED_VARIABLE 校验</li>
 *   <li>对条件/严重度表达式中引用的变量，逐一查询 registry，未注册的变量收集为 UNDEFINED_VARIABLE 错误</li>
 *   <li>对模板表达式中的 ${var} 占位符，同样查询 registry</li>
 *   <li>当 registry 为 {@link EmptyVariableRegistry} 时（默认），跳过 UNDEFINED_VARIABLE 校验，保持向后兼容</li>
 * </ul>
 *
 * <p>Bean 装配见 {@link com.njydsz.literule.server.config.LiteRuleAutoConfiguration#expressionValidationService}。
 *
 * @author ydsz-team
 *
 * @since 1.0.0
 */
@Slf4j
public class ExpressionValidationService {

    /** 表达式求值器，执行底层 LiteExpr 表达式编译与求值 */
    private final ExpressionEvaluator evaluator;
    /** 变量注册表，用于校验表达式中引用的变量是否已声明（为 EmptyVariableRegistry 时跳过该校验） */
    private final VariableRegistry variableRegistry;

    /** 模板占位符正则：${var} 或 ${ a.b.c } */
    private static final Pattern TEMPLATE_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]*)\\}");

    /** 不平衡的 ${ 开占位符（用于检测未闭合） */
    private static final Pattern UNCLOSED_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{[^}]*$");

    /**
     * 构造表达式校验服务（不启用 UNDEFINED_VARIABLE 校验）
     *
     * @param evaluator 表达式求值器
     */
    public ExpressionValidationService(ExpressionEvaluator evaluator) {
        this(evaluator, new EmptyVariableRegistry());
    }

    /**
     * 构造表达式校验服务
     *
     * @param evaluator 表达式求值器
     * @param variableRegistry 变量注册表（null 时使用 EmptyVariableRegistry，跳过 UNDEFINED_VARIABLE 校验）
     */
    public ExpressionValidationService(ExpressionEvaluator evaluator, VariableRegistry variableRegistry) {
        this.evaluator = evaluator;
        this.variableRegistry = variableRegistry != null ? variableRegistry : new EmptyVariableRegistry();
        if (!this.variableRegistry.isEmpty()) {
            log.info("[LiteRule-Expr] 变量空间校验已启用（已注册 {} 个变量）", this.variableRegistry.listAll().size());
        }
    }

    /**
     * 校验条件表达式（必须返回 boolean）
     *
     * <p>当注入非空 {@link VariableRegistry} 时，叠加 UNDEFINED_VARIABLE 校验。
     *
     * @param expression 条件表达式
     * @return 校验结果
     */
    public ExpressionValidationResult validateCondition(String expression) {
        ExpressionValidationResult base = evaluator.validateDetailed(expression);
        // 语法不通过时直接返回，不进入变量校验阶段
        if (!base.isValid()) {
            return base;
        }
        return checkUndefinedVariables(base);
    }

    /**
     * 校验严重度表达式（可选，返回值应为 RED/YELLOW/INFO）
     *
     * <p>仅校验语法合法性，不强制返回值约束（因为严重度表达式可以返回任意字符串，
     * 由 {@link com.njydsz.literule.api.RuleSeverity#fromCode(String)} 解析）。
     *
     * @param expression 严重度表达式
     * @return 校验结果
     */
    public ExpressionValidationResult validateSeverity(String expression) {
        if (expression == null || expression.isBlank()) {
            // 严重度表达式可选，为空时使用 defaultSeverity
            return ExpressionValidationResult.ok(expression, 0L, List.of());
        }
        ExpressionValidationResult base = evaluator.validateDetailed(expression);
        if (!base.isValid()) {
            return base;
        }
        return checkUndefinedVariables(base);
    }

    /**
     * 校验模板表达式（支持 ${var} 占位符）
     *
     * <p>当注入非空 {@link VariableRegistry} 时，对 ${var} 中的变量做 UNDEFINED_VARIABLE 校验。
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
        ExpressionValidationResult result = ExpressionValidationResult.ok(template, elapsed, referencedVars);

        // 叠加变量存在性校验（仅当 registry 非空时）
        return checkUndefinedVariables(result);
    }

    /**
     * 校验表达式中的变量是否已注册
     *
     * <p>当 {@link #variableRegistry} 为空（{@link EmptyVariableRegistry}）时，跳过校验。
     * 当 registry 非空时，遍历 referencedVariables，收集未注册的变量。
     *
     * @param base 基础校验结果（已通过语法校验）
     * @return 叠加 UNDEFINED_VARIABLE 校验后的结果
     */
    private ExpressionValidationResult checkUndefinedVariables(ExpressionValidationResult base) {
        if (variableRegistry == null || variableRegistry.isEmpty()) {
            return base;
        }
        List<String> referenced = base.getReferencedVariables();
        if (referenced == null || referenced.isEmpty()) {
            return base;
        }
        List<String> undefined = new ArrayList<>();
        for (String var : referenced) {
            if (!variableRegistry.contains(var)) {
                undefined.add(var);
            }
        }
        if (undefined.isEmpty()) {
            return base;
        }
        String msg = String.format("表达式引用了未注册的变量: %s（共 %d 个，请检查拼写或联系管理员注册变量）",
                String.join(", ", undefined), undefined.size());
        return ExpressionValidationResult.builder()
                .valid(false)
                .errorType(ExpressionValidationResult.ErrorType.UNDEFINED_VARIABLE)
                .errorMessage(msg)
                .errorLine(-1)
                .errorColumn(-1)
                .expression(base.getExpression())
                .parseTimeMs(base.getParseTimeMs())
                .referencedVariables(referenced)
                .build();
    }

    /**
     * 批量校验一组表达式
     *
     * @param expressions 表达式列表（key=标签，value=表达式文本）
     * @return 校验结果列表（与输入顺序一致）
     */
    public Map<String, ExpressionValidationResult> validateBatch(Map<String, String> expressions) {
        Map<String, ExpressionValidationResult> results = new LinkedHashMap<>();
        if (expressions == null) {
            return results;
        }
        expressions.forEach((label, expr) -> {
            ExpressionValidationResult result;
            try {
                result = evaluator.validateDetailed(expr);
                if (result.isValid()) {
                    result = checkUndefinedVariables(result);
                }
            } catch (Exception e) {
                result = ExpressionValidationResult.fail(expr,
                        ExpressionValidationResult.ErrorType.UNKNOWN,
                        "校验异常: " + e.getMessage(), 0L);
            }
            results.put(label, result);
        });
        return results;
    }

    /**
     * 获取已注册的变量列表（供前端编辑器自动补全）
     *
     * @return 变量定义列表
     */
    public List<VariableDefinition> listAvailableVariables() {
        if (variableRegistry == null) {
            return List.of();
        }
        return variableRegistry.listAll();
    }

    /**
     * 按类别查询已注册变量
     *
     * @param category 变量类别
     * @return 变量定义列表
     */
    public List<VariableDefinition> listVariablesByCategory(String category) {
        if (variableRegistry == null) {
            return List.of();
        }
        return variableRegistry.listByCategory(category);
    }

    /**
     * 获取变量注册表（暴露给外部用于注册变量）
     *
     * @return 变量注册表
     */
    public VariableRegistry getVariableRegistry() {
        return variableRegistry;
    }

    /**
     * 表达式求值预览（P2-8）
     *
     * <p>给定表达式与样例事实数据，返回求值结果，供前端表达式编辑器实时预览。
     * 语法错误或求值异常时返回结构化的错误信息，不抛异常。
     *
     * @param expression 表达式
     * @param facts      样例事实数据
     * @return 求值结果（含 value / type / error）
     * @since 1.0.0
     */
    public ExpressionPreviewResult previewEvaluate(String expression, Map<String, Object> facts) {
        long start = System.nanoTime();
        ExpressionPreviewResult result = new ExpressionPreviewResult();
        result.setExpression(expression);
        if (expression == null || expression.isBlank()) {
            result.setError("表达式为空");
            return result;
        }
        // 先校验语法
        ExpressionValidationResult validation = evaluator.validateDetailed(expression);
        if (!validation.isValid()) {
            result.setError("语法错误: " + validation.getErrorMessage());
            return result;
        }
        // 求值
        try {
            RuleContext ctx =
                    RuleContext.of(facts != null ? facts : Map.of());
            Object value = evaluator.eval(expression, ctx);
            result.setValue(value == null ? "null" : String.valueOf(value));
            result.setJavaType(value == null ? "null" : value.getClass().getSimpleName());
            result.setBooleanValue(value instanceof Boolean b ? b : null);
        } catch (Exception e) {
            result.setError("求值失败: " + e.getMessage());
        }
        result.setElapsedMs((System.nanoTime() - start) / 1_000_000L);
        return result;
    }
}
