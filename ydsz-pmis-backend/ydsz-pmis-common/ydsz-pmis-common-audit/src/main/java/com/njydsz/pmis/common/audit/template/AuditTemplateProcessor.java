package com.njydsz.pmis.common.audit.template;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * SpEL 模板处理器
 * <p>
 * 负责解析和执行审计日志 content 字段中的 SpEL 表达式，支持动态内容拼接。
 * 例如 {@code @Audit(content = "'删除用户:' + #userId")} 中的 {@code #userId}
 * 会被替换为实际方法参数。
 * </p>
 *
 * <p><b>性能与安全：</b></p>
 * <ul>
 *   <li>使用 {@link SimpleEvaluationContext}（只读），禁止执行任意方法/构造器，避免安全风险</li>
 *   <li>表达式解析结果按模板字符串做 LRU 风格缓存（容量上限 256）</li>
 *   <li>SpEL 解析失败时降级返回原模板字符串，不影响审计主流程</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class AuditTemplateProcessor {

    private static final Logger log = LoggerFactory.getLogger(AuditTemplateProcessor.class);

    /** 空字符串常量，用于模板解析结果为空时的默认返回值 */
    private static final String EMPTY_STRING = "";

    /** 表达式缓存最大容量，超过时清空缓存（防止无限增长） */
    private static final int MAX_EXPRESSION_CACHE_SIZE = 256;

    /**
     * SpEL 表达式解析器（线程安全）
     */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 表达式缓存，避免重复解析（key = 模板字符串，value = 编译后的 Expression）
     */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>(128);

    /**
     * 处理 SpEL 模板表达式
     *
     * @param template 模板字符串，可包含 SpEL 表达式
     * @param method   目标方法（用于将方法参数注入到 SpEL 上下文）
     * @param args     方法参数数组
     * @return 解析后的字符串；解析失败时返回原模板
     */
    public String processTemplate(String template, Method method, Object[] args) {
        if (template == null || template.isEmpty()) {
            return EMPTY_STRING;
        }

        if (!requiresSpelEvaluation(template)) {
            return template;
        }

        return evaluateSpelExpression(template, method, args);
    }

    /**
     * 判断模板是否需要 SpEL 解析
     * <p>只有包含 {@code #}（引用变量）或 {@code T(}（类型引用）才会进行解析，
     * 其余纯文本模板直接返回。
     *
     * @param template 模板字符串
     * @return 需要解析返回 true
     */
    private boolean requiresSpelEvaluation(String template) {
        return template.contains("#") || template.contains("T(");
    }

    /**
     * 执行 SpEL 表达式求值
     *
     * @param template 模板字符串
     * @param method   目标方法
     * @param args     方法参数
     * @return 解析结果；异常时返回原模板字符串
     */
    private String evaluateSpelExpression(String template, Method method, Object[] args) {
        try {
            EvaluationContext context = createEvaluationContext(method, args);
            Expression expression = getOrParseExpression(template);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : EMPTY_STRING;
        } catch (Exception e) {
            log.warn("【审计模板】解析表达式失败, 模板={}, 错误={}", template, e.getMessage());
            return template;
        }
    }

    /**
     * 创建 SpEL 评估上下文，将方法参数按参数名注入到上下文变量中。
     * <p>使用 {@link SimpleEvaluationContext#forReadOnlyDataBinding()} 构建只读上下文，
     * 避免执行任意 Java 方法带来的安全风险。
     *
     * @param method 目标方法
     * @param args   方法参数
     * @return 评估上下文
     */
    private EvaluationContext createEvaluationContext(Method method, Object[] args) {
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();

        if (method != null && args != null) {
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < Math.min(parameters.length, args.length); i++) {
                if (parameters[i].getName() != null && args[i] != null) {
                    context.setVariable(parameters[i].getName(), args[i]);
                }
            }
        }

        return context;
    }

    /**
     * 获取或解析表达式
     * <p>优先从缓存中获取，缓存未命中时解析并缓存。缓存满时直接清空（不实现严格 LRU，
     * 简单实现对审计场景已足够）。
     *
     * @param template 模板字符串
     * @return SpEL 表达式
     */
    private Expression getOrParseExpression(String template) {
        if (expressionCache.size() >= MAX_EXPRESSION_CACHE_SIZE) {
            expressionCache.clear();
        }
        return expressionCache.computeIfAbsent(template, parser::parseExpression);
    }

    /**
     * 清理表达式缓存
     * <p>在配置热更新或测试场景下可调用，避免脏模板残留。
     */
    public void clearCache() {
        expressionCache.clear();
        log.info("【审计模板】表达式缓存已清理");
    }

    /**
     * 获取缓存条目数量
     *
     * @return 当前缓存中的 SpEL 表达式数量
     */
    public int getCacheSize() {
        return expressionCache.size();
    }
}
