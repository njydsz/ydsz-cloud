package com.njydsz.pmis.literule.expr;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import com.njydsz.pmis.literule.api.RuleContext;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aviator 表达式求值器实现
 *
 * <p>使用 Aviator 编译缓存提升性能，线程安全。
 * 支持自定义函数注入（通过 {@link #addFunction} 扩展）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
public class AviatorExpressionEvaluator implements ExpressionEvaluator {

    /** Aviator 实例（独立实例，避免污染全局） */
    private final AviatorEvaluatorInstance instance;

    /** 表达式编译缓存（表达式文本 -> 编译后的 Expression） */
    private final ConcurrentHashMap<String, Expression> cache = new ConcurrentHashMap<>();

    public AviatorExpressionEvaluator() {
        this.instance = AviatorEvaluator.newInstance();
        // 浮点数解析为 Decimal 类型，避免精度丢失
        this.instance.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, true);
    }

    @Override
    public boolean evalBoolean(String expression, RuleContext context) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        try {
            Expression compiled = compile(expression);
            Object result = compiled.execute(context.getFacts());
            if (result instanceof Boolean b) return b;
            if (result instanceof Number n) return n.doubleValue() != 0;
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (Exception e) {
            log.warn("[LiteRule-Aviator] 布尔表达式求值失败: expr='{}', error={}", expression, e.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T eval(String expression, RuleContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            Expression compiled = compile(expression);
            return (T) compiled.execute(context.getFacts());
        } catch (Exception e) {
            log.warn("[LiteRule-Aviator] 表达式求值失败: expr='{}', error={}", expression, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean validate(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        try {
            compile(expression);
            return true;
        } catch (Exception e) {
            log.debug("[LiteRule-Aviator] 表达式校验失败: expr='{}', error={}", expression, e.getMessage());
            return false;
        }
    }

    /**
     * 编译表达式（带缓存）
     *
     * @param expression 表达式文本
     * @return 编译后的 Expression
     */
    private Expression compile(String expression) {
        return cache.computeIfAbsent(expression, key -> {
            try {
                return instance.compile(key, true);
            } catch (Exception e) {
                throw new IllegalArgumentException("表达式编译失败: " + key + " (" + e.getMessage() + ")", e);
            }
        });
    }

    /**
     * 清除编译缓存
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * 获取缓存大小
     *
     * @return 缓存的表达式数量
     */
    public int cacheSize() {
        return cache.size();
    }
}
