package com.njydsz.pmis.common.core.config;

/**
 * 阈值配置提供者接口。
 *
 * <p>统一从配置中心读取预警阈值，供 EVM 挣值分析、利用率监控、利润预警等场景使用。
 * 实现类可从数据库、Nacos 或本地配置读取阈值。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ThresholdProvider {

    /**
     * CPI 黄灯阈值（低于此值触发黄灯预警）。
     *
     * @return CPI 黄灯阈值
     */
    default double cpiYellow() {
        return 0.9;
    }

    /**
     * CPI 红灯阈值（低于此值触发红灯预警）。
     *
     * @return CPI 红灯阈值
     */
    default double cpiRed() {
        return 0.8;
    }

    /**
     * SPI 黄灯阈值。
     *
     * @return SPI 黄灯阈值
     */
    default double spiYellow() {
        return 0.9;
    }

    /**
     * SPI 红灯阈值。
     *
     * @return SPI 红灯阈值
     */
    default double spiRed() {
        return 0.8;
    }

    /**
     * 利润率黄灯阈值。
     *
     * @return 利润率黄灯阈值
     */
    default double marginYellow() {
        return 0.15;
    }

    /**
     * 利润率红灯阈值。
     *
     * @return 利润率红灯阈值
     */
    default double marginRed() {
        return 0.05;
    }

    /**
     * 利用率黄灯阈值。
     *
     * @return 利用率黄灯阈值
     */
    default double utilizationYellow() {
        return 0.7;
    }

    /**
     * 利用率红灯阈值。
     *
     * @return 利用率红灯阈值
     */
    default double utilizationRed() {
        return 0.5;
    }

    /**
     * 预算消耗黄灯阈值。
     *
     * @return 预算消耗黄灯阈值
     */
    default double budgetYellow() {
        return 0.8;
    }

    /**
     * 预算消耗红灯阈值。
     *
     * @return 预算消耗红灯阈值
     */
    default double budgetRed() {
        return 0.9;
    }

    /**
     * 闲置黄灯天数阈值。
     *
     * @return 闲置黄灯天数
     */
    default int benchYellowDays() {
        return 5;
    }

    /**
     * 闲置红灯天数阈值。
     *
     * @return 闲置红灯天数
     */
    default int benchRedDays() {
        return 10;
    }

    /**
     * EVM 红灯项目数阈值（超过此值触发全局预警）。
     *
     * @return EVM 红灯项目数阈值
     */
    default int evmRedCount() {
        return 3;
    }
}
