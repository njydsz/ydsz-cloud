package com.njydsz.pmis.common.util.json;

import java.util.concurrent.atomic.LongAdder;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * JSON 处理指标收集器
 *
 * <p>提供两层指标采集：
 * <ul>
 *   <li>内部计数器：基于 LongAdder，始终可用，零依赖</li>
 *   <li>Micrometer 指标：条件化注册，当 classpath 存在 MeterRegistry 时自动启用</li>
 * </ul>
 *
 * <p><b>内部计数器指标：</b>
 * <ul>
 *   <li>serializeSuccessCount - JSON 序列化成功次数</li>
 *   <li>serializeFailCount - JSON 序列化失败次数</li>
 *   <li>deserializeSuccessCount - JSON 反序列化成功次数</li>
 *   <li>deserializeFailCount - JSON 反序列化失败次数</li>
 *   <li>totalSerializeTimeNanos - 序列化成功累计耗时（纳秒）</li>
 *   <li>totalDeserializeTimeNanos - 反序列化成功累计耗时（纳秒）</li>
 * </ul>
 *
 * <p><b>Micrometer/Prometheus 指标：</b>
 * <ul>
 *   <li>json.serialize.total - JSON 序列化成功总数（Counter）</li>
 *   <li>json.serialize.failed.total - JSON 序列化失败总数（Counter）</li>
 *   <li>json.serialize.duration - JSON 序列化耗时（Timer，单位毫秒）</li>
 *   <li>json.deserialize.total - JSON 反序列化成功总数（Counter）</li>
 *   <li>json.deserialize.failed.total - JSON 反序列化失败总数（Counter）</li>
 *   <li>json.deserialize.duration - JSON 反序列化耗时（Timer，单位毫秒）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @version 4.0.0
 */
public class JsonMetrics {

    /**
     * JSON 序列化成功次数
     */
    private final LongAdder serializeSuccessCount = new LongAdder();
    /**
     * JSON 序列化失败次数
     */
    private final LongAdder serializeFailCount = new LongAdder();
    /**
     * JSON 反序列化成功次数
     */
    private final LongAdder deserializeSuccessCount = new LongAdder();
    /**
     * JSON 反序列化失败次数
     */
    private final LongAdder deserializeFailCount = new LongAdder();

    /**
     * 序列化成功累计耗时（纳秒）
     */
    private final LongAdder totalSerializeTimeNanos = new LongAdder();
    /**
     * 反序列化成功累计耗时（纳秒）
     */
    private final LongAdder totalDeserializeTimeNanos = new LongAdder();

    /**
     * Micrometer 指标收集器（可选，当 classpath 存在 MeterRegistry 时设置）
     */
    private volatile JsonMicrometerCollector micrometerCollector;

    /**
     * 记录序列化成功
     *
     * @param timeNanos 序列化耗时（纳秒）
     */
    void recordSerializeSuccess(long timeNanos) {
        serializeSuccessCount.increment();
        totalSerializeTimeNanos.add(timeNanos);
        if (micrometerCollector != null) {
            micrometerCollector.recordSerializeSuccess(timeNanos);
        }
    }

    /**
     * 记录序列化失败
     */
    void recordSerializeFail() {
        serializeFailCount.increment();
        if (micrometerCollector != null) {
            micrometerCollector.recordSerializeFail();
        }
    }

    /**
     * 记录反序列化成功
     *
     * @param timeNanos 反序列化耗时（纳秒）
     */
    void recordDeserializeSuccess(long timeNanos) {
        deserializeSuccessCount.increment();
        totalDeserializeTimeNanos.add(timeNanos);
        if (micrometerCollector != null) {
            micrometerCollector.recordDeserializeSuccess(timeNanos);
        }
    }

    /**
     * 记录反序列化失败
     */
    void recordDeserializeFail() {
        deserializeFailCount.increment();
        if (micrometerCollector != null) {
            micrometerCollector.recordDeserializeFail();
        }
    }

    /**
     * 绑定 Micrometer MeterRegistry，启用 Prometheus 指标采集
     *
     * @param meterRegistry Micrometer MeterRegistry 实例
     */
    public void bindMeterRegistry(Object meterRegistry) {
        this.micrometerCollector = new JsonMicrometerCollector(
                (MeterRegistry) meterRegistry);
    }

    /**
     * 获取序列化成功次数
     *
     * @return 成功次数
     */
    public long getSerializeSuccessCount() {
        return serializeSuccessCount.sum();
    }

    /**
     * 获取序列化失败次数
     *
     * @return 失败次数
     */
    public long getSerializeFailCount() {
        return serializeFailCount.sum();
    }

    /**
     * 获取反序列化成功次数
     *
     * @return 成功次数
     */
    public long getDeserializeSuccessCount() {
        return deserializeSuccessCount.sum();
    }

    /**
     * 获取反序列化失败次数
     *
     * @return 失败次数
     */
    public long getDeserializeFailCount() {
        return deserializeFailCount.sum();
    }

    /**
     * 获取序列化成功累计耗时（纳秒）
     *
     * @return 累计耗时（纳秒）
     */
    public long getTotalSerializeTimeNanos() {
        return totalSerializeTimeNanos.sum();
    }

    /**
     * 获取反序列化成功累计耗时（纳秒）
     *
     * @return 累计耗时（纳秒）
     */
    public long getTotalDeserializeTimeNanos() {
        return totalDeserializeTimeNanos.sum();
    }

    /**
     * 获取平均序列化耗时（毫秒）
     *
     * @return 平均耗时（毫秒），无成功记录时返回 0
     */
    public double getAverageSerializeTimeMillis() {
        long count = serializeSuccessCount.sum();
        return count == 0 ? 0 : totalSerializeTimeNanos.sum() / (count * 1_000_000.0);
    }

    /**
     * 获取平均反序列化耗时（毫秒）
     *
     * @return 平均耗时（毫秒），无成功记录时返回 0
     */
    public double getAverageDeserializeTimeMillis() {
        long count = deserializeSuccessCount.sum();
        return count == 0 ? 0 : totalDeserializeTimeNanos.sum() / (count * 1_000_000.0);
    }

    @Override
    public String toString() {
        return String.format("JsonMetrics{serializeSuccess=%d, serializeFail=%d, deserializeSuccess=%d, deserializeFail=%d, avgSerialize=%.3fms, avgDeserialize=%.3fms}",
                getSerializeSuccessCount(), getSerializeFailCount(),
                getDeserializeSuccessCount(), getDeserializeFailCount(),
                getAverageSerializeTimeMillis(), getAverageDeserializeTimeMillis());
    }
}
