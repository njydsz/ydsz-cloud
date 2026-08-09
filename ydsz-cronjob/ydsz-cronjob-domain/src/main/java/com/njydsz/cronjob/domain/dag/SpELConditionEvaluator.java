package com.njydsz.cronjob.domain.dag;

import java.util.Map;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

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
 * 表达式解析结果使用实例级 {@link Cache}（Caffeine）缓存，避免重复解析相同表达式。
 * <ul>
 *   <li>有界 LRU 淘汰（默认 1024），由 Caffeine {@code maximumSize} 实现，无全表锁，高并发下性能远优于
 *       {@code Collections.synchronizedMap(LinkedHashMap)} 方案</li>
 *   <li>缓存为实例级而非静态级，避免 classloader 级内存泄漏</li>
 *   <li>支持 {@link #clearCache()} 在运行时清理缓存</li>
 *   <li>支持 {@link #getCacheStats()} 获取命中率/淘汰数等统计指标（配合健康检查/监控）</li>
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

    /** 表达式解析缓存（Caffeine），避免重复解析相同表达式 */
    private final Cache<String, Expression> exprCache;

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
        this.exprCache = cacheEnabled ? createCache(cacheMaxSize) : null;
    }

    /**
     * 创建有界 Caffeine 缓存（开启统计，便于监控命中率）。
     */
    private static Cache<String, Expression> createCache(int maxSize) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        if (maxSize > 0) {
            builder.maximumSize(maxSize);
        }
        return builder.recordStats().build();
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
     * 从缓存获取或解析表达式。
     *
     * <p>使用 Caffeine {@code get(key, loader)} 原子化"查缓存/加载"，避免并发重复解析。
     */
    private Expression getOrParse(String spel) {
        if (!cacheEnabled) {
            return parser.parseExpression(spel);
        }
        return exprCache.get(spel, parser::parseExpression);
    }

    private EvaluationContext buildEvaluationContext(Map<String, Object> context) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext(context);
        @SuppressWarnings("removal")
        org.springframework.context.expression.MapAccessor mapAccessor = new org.springframework.context.expression.MapAccessor();
        evalContext.addPropertyAccessor(mapAccessor);
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
            exprCache.invalidateAll();
        }
    }

    /**
     * 获取当前缓存中的表达式数量（主要用于监控和诊断）。
     *
     * @return 缓存的表达式数量
     */
    public int getCacheSize() {
        return exprCache != null ? (int) exprCache.estimatedSize() : 0;
    }

    /**
     * 获取缓存统计信息（命中率、缺失率、淘汰数等）。
     *
     * <p>仅当缓存启用且 {@code recordStats()} 生效时返回有意义的数据；
     * 缓存未启用时返回空统计（全 0）。
     *
     * @return Caffeine 缓存统计快照，可用于健康检查与监控大盘
     * @since 1.4.0
     */
    public CacheStats getCacheStats() {
        return exprCache != null ? exprCache.stats() : CacheStats.empty();
    }

    /**
     * 获取缓存命中率（0.0 ~ 1.0）。
     *
     * @return 命中率；缓存未启用或无请求时返回 0.0
     * @since 1.4.0
     */
    public double getHitRate() {
        return exprCache != null ? exprCache.stats().hitRate() : 0.0;
    }
}
