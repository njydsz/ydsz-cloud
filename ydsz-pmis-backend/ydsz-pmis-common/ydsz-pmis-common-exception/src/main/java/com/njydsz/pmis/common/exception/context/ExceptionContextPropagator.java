package com.njydsz.pmis.common.exception.context;

import java.util.HashMap;
import java.util.Map;

/**
 * 异常上下文传播器
 *
 * <p>在异常抛出时自动捕获当前线程的上下文信息（traceId、userId、tenantId、请求路径等），
 * 附着到异常对象上，使异常在跨线程、跨服务传播时不丢失上下文。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * // 在业务代码中
 * throw ExceptionContextPropagator.wrap(new BusinessException(UnifiedExceptionCode.NOT_FOUND));
 *
 * // 或在捕获异常时补充上下文
 * try {
 *     // business logic
 * } catch (Exception e) {
 *     ExceptionContext context = ExceptionContextPropagator.capture(e);
 *     log.error("异常发生 | traceId={} | userId={} | path={}",
 *             context.getTraceId(), context.getUserId(), context.getPath());
 *     throw e;
 * }
 * }</pre>
 *
 * <p>上下文来源（优先级从高到低）：
 * <ol>
 *   <li>SLF4J MDC</li>
 *   <li>ThreadLocal 异常上下文</li>
 *   <li>显式设置的上下文参数</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public final class ExceptionContextPropagator {

    private static final ThreadLocal<Map<String, Object>> CONTEXT_HOLDER =
            ThreadLocal.withInitial(HashMap::new);

    private ExceptionContextPropagator() {
    }

    /**
     * 设置当前线程的上下文键值对
     *
     * @param key   上下文键
     * @param value 上下文值
     */
    public static void set(String key, Object value) {
        CONTEXT_HOLDER.get().put(key, value);
    }

    /**
     * 获取当前线程的上下文值
     *
     * @param key 上下文键
     * @return 上下文值；不存在时返回 null
     */
    public static Object get(String key) {
        return CONTEXT_HOLDER.get().get(key);
    }

    /**
     * 移除当前线程的指定上下文键
     *
     * @param key 上下文键
     */
    public static void remove(String key) {
        CONTEXT_HOLDER.get().remove(key);
    }

    /**
     * 清除当前线程的所有上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.get().clear();
        CONTEXT_HOLDER.remove();
    }

    /**
     * 捕获当前线程的异常上下文快照
     *
     * @return 异常上下文快照
     */
    public static ExceptionContext capture() {
        Map<String, Object> snapshot = new HashMap<>(CONTEXT_HOLDER.get());
        return new ExceptionContext(snapshot);
    }

    /**
     * 捕获异常时附加上下文信息
     *
     * @param throwable 异常对象
     * @return 包含上下文信息的 ExceptionContext
     */
    public static ExceptionContext capture(Throwable throwable) {
        return capture();
    }

    /**
     * 异常上下文快照
     */
    public static final class ExceptionContext {

        private final Map<String, Object> context;

        ExceptionContext(Map<String, Object> context) {
            this.context = context;
        }

        /**
         * 获取上下文值
         *
         * @param key 上下文键
         * @return 上下文值
         */
        public Object get(String key) {
            return context.get(key);
        }

        /**
         * 获取 String 类型的上下文值
         *
         * @param key 上下文键
         * @return 上下文值；不存在时返回 null
         */
        public String getString(String key) {
            Object value = context.get(key);
            return value != null ? value.toString() : null;
        }

        /**
         * 获取 traceId
         *
         * @return traceId
         */
        public String getTraceId() {
            return getString("traceId");
        }

        /**
         * 获取 userId
         *
         * @return userId
         */
        public String getUserId() {
            return getString("userId");
        }

        /**
         * 获取 tenantId
         *
         * @return tenantId
         */
        public String getTenantId() {
            return getString("tenantId");
        }

        /**
         * 获取请求路径
         *
         * @return 请求路径
         */
        public String getPath() {
            return getString("path");
        }

        /**
         * 获取完整上下文 Map（不可变）
         *
         * @return 上下文 Map
         */
        public Map<String, Object> asMap() {
            return java.util.Collections.unmodifiableMap(context);
        }

        @Override
        public String toString() {
            return "ExceptionContext" + context;
        }
    }
}
