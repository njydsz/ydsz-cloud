package com.njydsz.common.domain.dag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import lombok.extern.slf4j.Slf4j;

/**
 * SpEL 条件表达式评估器（DAG 条件分支节点使用）。
 *
 * <p>用于 DAG 条件分支节点（CONDITION）的表达式评估，
 * 支持从上下文中读取上游节点的执行结果进行条件判断。
 *
 * <p>表达式格式：{@code ${nodeA.result=='success'}} 或 {@code #nodeA.status!='FAILED'}
 *
 * <p><b>缓存机制：</b>
 * 表达式解析结果使用实例级 {@link ConcurrentHashMap} 缓存，避免重复解析相同表达式。
 * 缓存为实例级而非静态级，确保：
 * <ul>
 *   <li>避免 classloader 级内存泄漏（静态 Map 随 JVM 生命周期存活）</li>
 *   <li>支持 {@link #clearCache()} 在运行时清理缓存</li>
 *   <li>实例可替换，便于测试和热部署</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SpELConditionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    /** 是否启用表达式缓存 */
    private final boolean cacheEnabled;

    /** 表达式缓存最大容量（0 表示无限制） */
    private final int cacheMaxSize;

    /** 表达式解析缓存，避免重复解析相同表达式 */
    private final Map<String, Expression> exprCache;

    /**
     * 默认构造（启用缓存，容量 1024）
     */
    public SpELConditionEvaluator() {
        this(true, 1024);
    }

    /**
     * 构造 SpEL 条件评估器
     *
     * @param cacheEnabled 是否启用表达式缓存
     * @param cacheMaxSize 缓存最大容量（0 表示无限制，仅在 cacheEnabled=true 时生效）
     * @since 1.2.0
     */
    public SpELConditionEvaluator(boolean cacheEnabled, int cacheMaxSize) {
        this.cacheEnabled = cacheEnabled;
        this.cacheMaxSize = cacheMaxSize;
        this.exprCache = cacheEnabled ? createLruCache(cacheMaxSize) : null;
    }

    /**
     * 创建 LRU 缓存（有界）或 ConcurrentHashMap（无界）
     */
    private static Map<String, Expression> createLruCache(int maxSize) {
        if (maxSize <= 0) {
            return new ConcurrentHashMap<>();
        }
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Expression> eldest) {
                return size() > maxSize;
            }
        });
    }

    /**
     * 评估条件表达式。
     *
     * @param expression 条件表达式（支持 {@code ${...}} 包裹或纯 SpEL 表达式）
     * @param context    上下文变量（key=变量名, value=变量值）
     * @return 评估结果；表达式为空或解析失败时返回 false
     */
    public boolean evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            return false;
        }

        String spel = expression.trim();
        if (spel.startsWith("${") && spel.endsWith("}")) {
            spel = spel.substring(2, spel.length() - 1).trim();
        }

        try {
            EvaluationContext evalContext = buildEvaluationContext(context);
            Expression parsed = getOrParse(spel);
            Boolean result = parsed.getValue(evalContext, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("[SpELConditionEvaluator] 表达式评估失败, 返回 false: expr={} reason={}",
                    expression, e.getMessage());
            return false;
        }
    }

    /**
     * 从缓存获取或解析表达式
     */
    private Expression getOrParse(String spel) {
        if (!cacheEnabled) {
            return parser.parseExpression(spel);
        }
        synchronized (exprCache) {
            Expression parsed = exprCache.get(spel);
            if (parsed == null) {
                parsed = parser.parseExpression(spel);
                exprCache.put(spel, parsed);
            }
            return parsed;
        }
    }

    private EvaluationContext buildEvaluationContext(Map<String, Object> context) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.addPropertyAccessor(new MapAccessor());
        if (context != null) {
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                evalContext.setVariable(entry.getKey(), entry.getValue());
            }
        }
        return evalContext;
    }

    /**
     * 清除表达式解析缓存（主要用于运行时配置变更或测试场景）。
     */
    public void clearCache() {
        if (exprCache != null) {
            exprCache.clear();
        }
    }

    /**
     * 获取当前缓存中的表达式数量（主要用于监控和诊断）。
     *
     * @return 缓存的表达式数量
     */
    public int getCacheSize() {
        return exprCache != null ? exprCache.size() : 0;
    }
}
