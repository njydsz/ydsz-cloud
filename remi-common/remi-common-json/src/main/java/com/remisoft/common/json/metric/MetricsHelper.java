package com.remisoft.common.json.metric;

import com.remisoft.common.json.exception.JsonException;

/**
 * 指标监控包装工具（统一序列化/反序列化的指标记录逻辑）。
 *
 * <p>提取自 {@link com.remisoft.common.json.RemiJson} 和
 * {@link com.remisoft.common.json.JsonMapper} 中重复的 recordSerialize/recordDeserialize 逻辑，
 * 消除约 100 行重复代码。</p>
 *
 * <p><b>性能优化：</b>当 {@code callback} 为 null 时（未启用监控），直接执行操作，
 * 跳过 {@code System.nanoTime()} 和 lambda 捕获开销。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class MetricsHelper {

    private MetricsHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * 可抛出受检异常的供应商接口。
     *
     * <p>与 {@code java.util.function.Supplier} 的区别在于允许 {@code get()}
     * 抛出 {@link Exception}，用于指标包装层在回调失败时向上传播异常，
     * 最终由 {@link #recordOperation} 统一转换为 {@link JsonException}。</p>
     *
     * @param <T> 生产值的类型
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        /**
         * 获取结果。
         *
         * @return 生产的值
         * @throws Exception 获取过程中可能抛出的任意异常
         */
        T get() throws Exception;
    }

    /**
     * 序列化操作的指标监控包装。
     *
     * @param supplier 序列化操作
     * @param callback 指标回调（null 时跳过监控）
     * @param <T> 返回类型
     * @return 序列化结果
     */
    public static <T> T recordSerialize(ThrowingSupplier<T> supplier, JsonMetricsCallback callback) {
        return recordOperation(supplier, callback, true);
    }

    /**
     * 反序列化操作的指标监控包装。
     *
     * @param supplier 反序列化操作
     * @param callback 指标回调（null 时跳过监控）
     * @param <T> 返回类型
     * @return 反序列化结果
     */
    public static <T> T recordDeserialize(ThrowingSupplier<T> supplier, JsonMetricsCallback callback) {
        return recordOperation(supplier, callback, false);
    }

    /**
     * 统一操作包装（序列化/反序列化共享逻辑）。
     *
     * @param supplier 操作供应商
     * @param callback 指标回调
     * @param isSerialize 是否为序列化操作
     * @return 操作结果
     */
    private static <T> T recordOperation(ThrowingSupplier<T> supplier, JsonMetricsCallback callback, boolean isSerialize) {
        if (callback == null) {
            // 无监控回调时短路，避免 System.nanoTime() 和 lambda 捕获开销
            try {
                return supplier.get();
            } catch (Exception e) {
                if (e instanceof JsonException) {
                    throw (JsonException) e;
                }
                throw new JsonException(
                    (isSerialize ? "JSON serialize failed: " : "JSON deserialize failed: ")
                    + e.getMessage(), e);
            }
        }
        long start = System.nanoTime();
        try {
            T result = supplier.get();
            if (isSerialize) {
                callback.onSerializeSuccess(System.nanoTime() - start);
            } else {
                callback.onDeserializeSuccess(System.nanoTime() - start);
            }
            return result;
        } catch (Exception e) {
            if (isSerialize) {
                callback.onSerializeFailure();
            } else {
                callback.onDeserializeFailure();
            }
            if (e instanceof JsonException) {
                throw (JsonException) e;
            }
            throw new JsonException(
                (isSerialize ? "JSON serialize failed: " : "JSON deserialize failed: ")
                + e.getMessage(), e);
        }
    }
}
