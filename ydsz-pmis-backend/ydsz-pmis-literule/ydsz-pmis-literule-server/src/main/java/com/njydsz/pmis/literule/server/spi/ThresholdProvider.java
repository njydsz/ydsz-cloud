package com.njydsz.pmis.literule.server.spi;

import java.math.BigDecimal;

/**
 * 规则阈值提供者接口（SPI）
 *
 * <p>由消费方实现，从配置中心（如 pmis_config 表）读取规则阈值。
 * 修复原 AlertRuleEngine 与 pmis_config 表脱节的问题：
 * 引擎不再使用硬编码常量，而是通过此接口获取可配置阈值。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ThresholdProvider {

    /**
     * 获取字符串阈值
     *
     * @param key          配置键（如 alert.cpi.yellow）
     * @param defaultValue 默认值
     * @return 配置值
     */
    String getString(String key, String defaultValue);

    /**
     * 获取数值阈值
     *
     * @param key          配置键（如 alert.bench.red.cost）
     * @param defaultValue 默认值
     * @return 配置值
     */
    BigDecimal getDecimal(String key, BigDecimal defaultValue);

    /**
     * 获取整数阈值
     *
     * @param key          配置键（如 alert.evm.red.count）
     * @param defaultValue 默认值
     * @return 配置值
     */
    int getInt(String key, int defaultValue);

    /**
     * 获取双精度阈值
     *
     * @param key          配置键（如 alert.margin.red）
     * @param defaultValue 默认值
     * @return 配置值
     */
    double getDouble(String key, double defaultValue);

    /**
     * 获取布尔阈值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    boolean getBoolean(String key, boolean defaultValue);

    /** EVM 红色项目数阈值 */
    String KEY_EVM_RED_COUNT = "alert.evm.red.count";
    /** CPI 黄色阈值 */
    String KEY_CPI_YELLOW = "alert.cpi.yellow";
    /** CPI 红色阈值 */
    String KEY_CPI_RED = "alert.cpi.red";
    /** SPI 黄色阈值 */
    String KEY_SPI_YELLOW = "alert.spi.yellow";
    /** SPI 红色阈值 */
    String KEY_SPI_RED = "alert.spi.red";
    /** 毛利黄色阈值 */
    String KEY_MARGIN_YELLOW = "alert.margin.yellow";
    /** 毛利红色阈值 */
    String KEY_MARGIN_RED = "alert.margin.red";
    /** Bench 闲置成本黄色阈值 */
    String KEY_BENCH_YELLOW_COST = "alert.bench.yellow.cost";
    /** Bench 闲置成本红色阈值 */
    String KEY_BENCH_RED_COST = "alert.bench.red.cost";
    /** 利用率黄色阈值 */
    String KEY_UTILIZATION_YELLOW = "alert.utilization.yellow";
    /** 利用率红色阈值 */
    String KEY_UTILIZATION_RED = "alert.utilization.red";
    /** 预算黄色阈值 */
    String KEY_BUDGET_YELLOW = "alert.budget.yellow";
    /** 预算红色阈值 */
    String KEY_BUDGET_RED = "alert.budget.red";
}
