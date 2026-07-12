package com.njydsz.pmis.common.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * 异常上下文
 *
 * <p>为异常提供附加上下文信息，用于日志记录和错误追踪。
 * 基于 ThreadLocal 实现，自动清理。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class ExceptionContext {

    private static final ThreadLocal<Map<String, Object>> CONTEXT = ThreadLocal.withInitial(HashMap::new);

    private ExceptionContext() {
    }

    /**
     * 设置上下文值
     *
     * @param key   键
     * @param value 值
     */
    public static void set(String key, Object value) {
        CONTEXT.get().put(key, value);
    }

    /**
     * 获取上下文值
     *
     * @param key 键
     * @return 值
     */
    public static Object get(String key) {
        return CONTEXT.get().get(key);
    }

    /**
     * 获取上下文值（带类型）
     *
     * @param key 键
     * @param <T> 值类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public static <T> T getTyped(String key) {
        return (T) CONTEXT.get().get(key);
    }

    /**
     * 获取所有上下文
     *
     * @return 上下文 Map
     */
    public static Map<String, Object> getAll() {
        return new HashMap<>(CONTEXT.get());
    }

    /**
     * 移除上下文值
     *
     * @param key 键
     */
    public static void remove(String key) {
        CONTEXT.get().remove(key);
    }

    /**
     * 清理上下文
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
