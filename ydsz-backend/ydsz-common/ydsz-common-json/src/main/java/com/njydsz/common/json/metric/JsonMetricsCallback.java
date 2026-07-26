package com.njydsz.common.json.metric;

/**
 * JSON 处理指标回调接口（SPI）
 *
 * <p>定义 {@link com.njydsz.common.json.YdszJson} 引擎在序列化/反序列化时
 * 的指标采集回调点。实现方可以对接 Micrometer、OpenTelemetry 等监控体系。
 *
 * <p>此接口位于 common-json 模块，零外部依赖，确保引擎层不耦合具体监控框架。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JsonMetricsCallback {

    /**
     * 记录序列化成功
     *
     * @param durationNanos 序列化耗时（纳秒）
     */
    void onSerializeSuccess(long durationNanos);

    /**
     * 记录序列化失败
     */
    void onSerializeFailure();

    /**
     * 记录反序列化成功
     *
     * @param durationNanos 反序列化耗时（纳秒）
     */
    void onDeserializeSuccess(long durationNanos);

    /**
     * 记录反序列化失败
     */
    void onDeserializeFailure();
}
