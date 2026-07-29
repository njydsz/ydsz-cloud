package com.njydsz.common.core.metrics;

/**
 * 核心模块指标回调 SPI
 *
 * <p>定义核心模块关键操作的指标采集接口，遵循与 {@link com.njydsz.common.core.response.BaseResponse.MessageResolver}
 * 相同的静态 holder 模式，使 core 模块在<b>不引入 Micrometer 依赖</b>的前提下，
 * 允许上层模块（如 {@code ydsz-common-base}）注入指标采集实现。
 *
 * <p><b>设计理由：</b>
 * <ul>
 *   <li>core 模块定位为最小核心，不含 Micrometer / AOP 依赖</li>
 *   <li>通过 SPI 回调解耦，上层模块可桥接到 Micrometer / Prometheus / 自定义监控</li>
 *   <li>当无实现注册时，所有方法空操作，零性能开销</li>
 * </ul>
 *
 * <p><b>使用示例（上层模块实现）：</b>
 * <pre>{@code
 * @Bean
 * public CoreMetricsCallback coreMetricsCallback(MeterRegistry registry) {
 *     return new CoreMetricsCallback() {
 *         @Override
 *         public void onTraceIdGenerated(String strategy) {
 *             registry.counter("ydsz.core.traceid.generated", "strategy", strategy).increment();
 *         }
 *         @Override
 *         public void onResponseCreated(boolean success, String code) {
 *             registry.counter("ydsz.core.response.created",
 *                     "success", String.valueOf(success), "code", code).increment();
 *         }
 *     };
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public interface CoreMetricsCallback {

    /**
     * TraceId 生成事件
     *
     * @param strategy 生成策略名称（如 "UuidTraceIdSupplier" / "SnowflakeTraceIdSupplier"）
     */
    void onTraceIdGenerated(String strategy);

    /**
     * 响应创建事件
     *
     * @param success 是否成功（code == "A00000"）
     * @param code    响应码
     */
    void onResponseCreated(boolean success, String code);

    /**
     * 空操作实现（默认注册时使用，零开销）
     */
    CoreMetricsCallback NOOP = new CoreMetricsCallback() {
        @Override
        public void onTraceIdGenerated(String strategy) {
            // no-op
        }

        @Override
        public void onResponseCreated(boolean success, String code) {
            // no-op
        }
    };
}
