package com.njydsz.pmis.common.sentry.spi;

import java.time.Duration;
import java.util.Map;

import com.njydsz.pmis.common.sentry.domain.MetricType;

/**
 * 指标采集器 SPI
 *
 * <p>统一指标采集抽象，底层可切换 Micrometer / 内存计数器 / 其他实现。
 * 业务模块通过此接口上报指标，无需关心底层监控方案。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public interface MetricsCollector {

    /**
     * 注册/获取 Counter 指标并递增
     *
     * @param name        指标名称
     * @param description 指标描述
     * @param tags        标签
     * @param amount      递增量
     */
    void incrementCounter(String name, String description, Map<String, String> tags, double amount);

    /**
     * 注册/获取 Counter 指标并递增 1
     */
    default void incrementCounter(String name, String description, Map<String, String> tags) {
        incrementCounter(name, description, tags, 1);
    }

    /**
     * 设置 Gauge 指标值
     *
     * @param name        指标名称
     * @param description 指标描述
     * @param tags        标签
     * @param value       值
     */
    void setGauge(String name, String description, Map<String, String> tags, double value);

    /**
     * 记录 Timer 指标耗时
     *
     * @param name        指标名称
     * @param description 指标描述
     * @param tags        标签
     * @param duration    耗时
     */
    void recordTimer(String name, String description, Map<String, String> tags, Duration duration);

    /**
     * 记录 Histogram 值分布
     *
     * @param name        指标名称
     * @param description 指标描述
     * @param tags        标签
     * @param value       值
     */
    void recordHistogram(String name, String description, Map<String, String> tags, double value);

    /**
     * 判断采集器是否可用
     */
    boolean isAvailable();

    /**
     * 获取采集器名称
     */
    String getName();

    /**
     * 获取采集器类型
     */
    default MetricType getCollectorType() {
        return null;
    }
}
