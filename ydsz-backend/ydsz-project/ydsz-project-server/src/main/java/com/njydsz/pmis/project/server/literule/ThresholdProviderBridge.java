package com.njydsz.project.server.literule;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.njydsz.literule.server.spi.ThresholdProvider;

import lombok.RequiredArgsConstructor;

/**
 * 规则阈值提供者桥接实现（execution 模块）
 *
 * <p>实现 literule 模块的 {@link ThresholdProvider} SPI 接口，
 * 将调用桥接到 common 模块的阈值提供器（统一从配置中心读取 alert 分组阈值）。
 *
 * <p>说明：
 * <ul>
 *   <li>调用方传入的 key 带 "alert." 前缀（如 alert.cpi.yellow），
 *       common 模块内部已自带 "alert." 前缀，此处去掉前缀后再委托。</li>
 *   <li>字符串与布尔阈值暂不通过此桥接，直接返回默认值。</li>
 *   <li>common 模块的 ThresholdProvider 通过 {@link CommonThresholdHolder} 注入，
 *       以避免与 literule 的 ThresholdProvider 同名冲突。</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class ThresholdProviderBridge implements ThresholdProvider {

    /** common 模块阈值提供器持有器（委托目标） */
    private final CommonThresholdHolder thresholdHolder;

    @Override
    public String getString(String key, String defaultValue) {
        // common ThresholdProvider 没有公开的 getString，字符串阈值暂不通过此桥接
        return defaultValue;
    }

    @Override
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        return BigDecimal.valueOf(getDouble(key, defaultValue.doubleValue()));
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String shortKey = key.startsWith("alert.") ? key.substring(6) : key;
        return switch (shortKey) {
            case "evm.red.count" -> thresholdHolder.getDelegate().evmRedCount();
            default -> defaultValue;
        };
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        String shortKey = key.startsWith("alert.") ? key.substring(6) : key;
        return switch (shortKey) {
            case "cpi.yellow" -> thresholdHolder.getDelegate().cpiYellow();
            case "cpi.red" -> thresholdHolder.getDelegate().cpiRed();
            case "spi.yellow" -> thresholdHolder.getDelegate().spiYellow();
            case "spi.red" -> thresholdHolder.getDelegate().spiRed();
            case "margin.yellow" -> thresholdHolder.getDelegate().marginYellow();
            case "margin.red" -> thresholdHolder.getDelegate().marginRed();
            case "utilization.yellow" -> thresholdHolder.getDelegate().utilizationYellow();
            case "utilization.red" -> thresholdHolder.getDelegate().utilizationRed();
            case "budget.yellow" -> thresholdHolder.getDelegate().budgetYellow();
            case "budget.red" -> thresholdHolder.getDelegate().budgetRed();
            default -> defaultValue;
        };
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return defaultValue;
    }
}
