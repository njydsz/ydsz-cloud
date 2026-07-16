package com.njydsz.common.json.engine;

import com.njydsz.common.json.cache.SerializerCache;
import com.njydsz.common.json.provider.SerializationProvider;

/**
 * Json 序列化引擎（Facade + 缓存管理）
 *
 * <p>架构层级：Json => Engine => Provider => Parser</p>
 *
 * <p><b>Engine 层职责：</b></p>
 * <ul>
 *   <li>缓存管理 - 提供缓存统计和清理接口</li>
 *   <li>统一异常处理 - 包装 Provider 异常</li>
 *   <li>ThreadLocal 清理 - 提供清理入口</li>
 * </ul>
 *
 * <p><b>性能监控说明：</b></p>
 * <p>默认关闭内置性能监控（消除 System.nanoTime + volatile 写入开销，约 60-120ns/次）。
 * 可通过 {@code -Dydsz.json.monitoring=true} 系统属性启用。</p>
 *
 * @since 1.0.0
 */
public final class SerializerEngine {

    /** 性能监控开关（默认关闭，消除热路径中 System.nanoTime + volatile 写入开销） */
    private static final boolean MONITORING_ENABLED =
        Boolean.getBoolean("ydsz.json.monitoring");

    /** 序列化次数（仅监控开启时使用） */
    private static volatile long serializeCount = 0;

    /** 序列化总耗时纳秒（仅监控开启时使用） */
    private static volatile long serializeTotalNanos = 0;

    private SerializerEngine() {
        throw new UnsupportedOperationException();
    }

    /**
     * 序列化对象
     */
    public static String serialize(Object obj) {
        if (MONITORING_ENABLED) {
            long start = System.nanoTime();
            try {
                return SerializationProvider.serialize(obj);
            } finally {
                long elapsed = System.nanoTime() - start;
                serializeCount++;
                serializeTotalNanos += elapsed;
            }
        }
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
        if (MONITORING_ENABLED) {
            long start = System.nanoTime();
            try {
                return SerializationProvider.serialize(obj, features);
            } finally {
                long elapsed = System.nanoTime() - start;
                serializeCount++;
                serializeTotalNanos += elapsed;
            }
        }
        return SerializationProvider.serialize(obj, features);
    }

    /**
     * 格式化序列化（带缩进）
     */
    public static String format(Object obj) {
        if (MONITORING_ENABLED) {
            long start = System.nanoTime();
            try {
                return SerializationProvider.format(obj);
            } finally {
                long elapsed = System.nanoTime() - start;
                serializeCount++;
                serializeTotalNanos += elapsed;
            }
        }
        return SerializationProvider.format(obj);
    }

    /**
     * 序列化对象（带视图过滤）
     */
    public static String serialize(Object obj, Class<?> viewClass) {
        if (MONITORING_ENABLED) {
            long start = System.nanoTime();
            try {
                return SerializationProvider.serializeWithView(obj, viewClass);
            } finally {
                long elapsed = System.nanoTime() - start;
                serializeCount++;
                serializeTotalNanos += elapsed;
            }
        }
        return SerializationProvider.serializeWithView(obj, viewClass);
    }

    /**
     * 序列化对象（带视图过滤和配置）
     */
    public static String serialize(Object obj, Class<?> viewClass, boolean pretty) {
        if (MONITORING_ENABLED) {
            long start = System.nanoTime();
            try {
                return SerializationProvider.serializeWithView(obj, viewClass, pretty);
            } finally {
                long elapsed = System.nanoTime() - start;
                serializeCount++;
                serializeTotalNanos += elapsed;
            }
        }
        return SerializationProvider.serializeWithView(obj, viewClass, pretty);
    }

    /**
     * 获取序列化次数（需开启监控）
     */
    public static long getSerializeCount() {
        return serializeCount;
    }

    /**
     * 获取序列化平均耗时（纳秒，需开启监控）
     */
    public static double getAvgSerializeNanos() {
        return serializeCount == 0 ? 0.0 : (double) serializeTotalNanos / serializeCount;
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
     * 重置性能统计
     */
    public static void resetStats() {
        serializeCount = 0;
        serializeTotalNanos = 0;
    }

    /**
     * 序列化器接口（ASM 使用）
     */
    public interface ObjectSerializer {
        void serialize(Object obj, StringBuilder sb);
    }
}
