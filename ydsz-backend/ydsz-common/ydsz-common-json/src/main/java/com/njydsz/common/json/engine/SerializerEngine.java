package com.njydsz.common.json.engine;

import com.njydsz.common.json.cache.SerializerCache;
import com.njydsz.common.json.provider.SerializationProvider;

/**
 * YdszJson 序列化引擎（Facade + 缓存管理）
 *
 * <p>架构层级：YdszJson => Engine => Provider => Parser</p>
 *
 * <p><b>Engine 层职责：</b></p>
 * <ul>
 *   <li>缓存管理 - 提供缓存统计和清理接口</li>
 *   <li>统一异常处理 - 包装 Provider 异常</li>
 *   <li>ThreadLocal 清理 - 提供清理入口</li>
 * </ul>
 *
 * <p><b>性能监控说明：</b></p>
 * <p>Engine 层不再内置独立的监控计数器（避免与 {@code YdszJson.metricsCallback}
 * 重复计数）。性能监控统一由 {@code YdszJson.recordSerialize()} 通过
 * {@link com.njydsz.common.json.metric.JsonMetricsCallback} 回调上报，
 * 默认 callback 为 null（短路返回，零开销）。如需对接 Micrometer 等监控系统，
 * 通过 {@code YdszJson.setMetricsCallback(...)} 注入实现即可。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SerializerEngine {

    private SerializerEngine() {
        throw new UnsupportedOperationException();
    }

    /**
     * 序列化对象
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    public static String serialize(Object obj) {
        return SerializationProvider.serialize(obj);
    }

    /**
     * 序列化对象（带特性配置）
     *
     * @param obj 对象
     * @param features 特性标志（位运算值）
     * @return JSON 字符串
     */
    public static String serialize(Object obj, long features) {
        return SerializationProvider.serialize(obj, features);
    }

    /**
     * 格式化序列化（带缩进）
     *
     * @param obj 对象
     * @return 格式化的 JSON 字符串
     */
    public static String format(Object obj) {
        return SerializationProvider.format(obj);
    }

    /**
     * 序列化对象（带视图过滤）
     *
     * @param obj 对象
     * @param viewClass 视图类
     * @return JSON 字符串
     */
    public static String serialize(Object obj, Class<?> viewClass) {
        return SerializationProvider.serializeWithView(obj, viewClass);
    }

    /**
     * 序列化对象（带视图过滤和配置）
     *
     * @param obj 对象
     * @param viewClass 视图类
     * @param pretty 是否格式化输出
     * @return JSON 字符串
     */
    public static String serialize(Object obj, Class<?> viewClass, boolean pretty) {
        return SerializationProvider.serializeWithView(obj, viewClass, pretty);
    }

    /**
     * 清理所有缓存（包括 ThreadLocal）
     *
     * <p>在线程池环境中，应在适当时机调用此方法清理 ThreadLocal 对象</p>
     */
    public static void clearAllCaches() {
        SerializerCache.clear();
        SerializationProvider.clearThreadLocals();
    }

    /**
     * 序列化器接口（ASM 使用）
     */
    public interface ObjectSerializer {
        void serialize(Object obj, StringBuilder sb);
    }
}
