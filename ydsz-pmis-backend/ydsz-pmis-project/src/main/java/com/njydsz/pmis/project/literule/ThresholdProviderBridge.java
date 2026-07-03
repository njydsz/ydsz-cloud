package com.njydsz.pmis.project.literule;

import com.njydsz.pmis.literule.spi.ThresholdProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 规则阈值提供者桥接实现（execution 模块）
 *
 * <p>实现 literule 模块的 {@link ThresholdProvider} SPI 接口，
 * 将调用桥接到 common 模块的 {@code com.njydsz.pmis.common.config.ThresholdProvider}（统一从配置中心读取 alert 分组阈值）。
 *
 * <p>说明：
 * <ul>
 *   <li>调用方传入的 key 带 "alert." 前缀（如 alert.cpi.yellow），
 *       common 模块内部已自带 "alert." 前缀，此处去掉前缀后再委托。</li>
 *   <li>字符串与布尔阈值暂不通过此桥接，直接返回默认值。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Component
@RequiredArgsConstructor
public class ThresholdProviderBridge implements ThresholdProvider {

    /** common 模块阈值提供器（委托目标） */
    @Qualifier("thresholdProvider")
    private final com.njydsz.pmis.common.config.ThresholdProvider delegate;

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
            case "evm.red.count" -> delegate.evmRedCount();
            default -> defaultValue;
        };
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        String shortKey = key.startsWith("alert.") ? key.substring(6) : key;
        return switch (shortKey) {
            case "cpi.yellow" -> delegate.cpiYellow();
            case "cpi.red" -> delegate.cpiRed();
            case "spi.yellow" -> delegate.spiYellow();
            case "spi.red" -> delegate.spiRed();
            case "margin.yellow" -> delegate.marginYellow();
            case "margin.red" -> delegate.marginRed();
            case "utilization.yellow" -> delegate.utilizationYellow();
            case "utilization.red" -> delegate.utilizationRed();
            case "budget.yellow" -> delegate.budgetYellow();
            case "budget.red" -> delegate.budgetRed();
            default -> defaultValue;
        };
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return defaultValue;
    }
}
