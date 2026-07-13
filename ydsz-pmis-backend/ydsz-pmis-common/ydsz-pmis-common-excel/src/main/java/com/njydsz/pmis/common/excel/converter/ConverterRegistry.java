package com.njydsz.pmis.common.excel.converter;

import java.util.ArrayList;
import java.util.List;

/**
 * 转换器注册中心 - SPI注册入口
 *
 * <p>管理默认转换器链和自定义转换器的注册。
 * 默认转换器链在首次访问时懒初始化，包含所有内置转换器。</p>
 *
 * <h3>注册自定义转换器</h3>
 * <pre>{@code
 * // 注册自定义转换器（会添加到默认链中）
 * ConverterRegistry.registerCustomConverter(new MyConverter());
 * }</pre>
 *
 * <h3>内置转换器（按优先级排序）</h3>
 * <ul>
 *   <li>StringConverter (priority=10)</li>
 *   <li>NumberConverter (priority=20)</li>
 *   <li>BooleanConverter (priority=30)</li>
 *   <li>BigDecimalConverter (priority=40)</li>
 *   <li>DateConverter (priority=50)</li>
 *   <li>LocalDateTimeConverter (priority=60)</li>
 *   <li>LocalDateConverter (priority=70)</li>
 *   <li>LocalTimeConverter (priority=80)</li>
 *   <li>YearMonthConverter (priority=90)</li>
 *   <li>TimestampConverter (priority=100)</li>
 *   <li>EnumConverter (priority=110)</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
public class ConverterRegistry {

    private static volatile ConverterChain defaultChain;

    private ConverterRegistry() {
    }

    /**
     * 获取默认转换器链
     *
     * <p>采用双重检查锁定模式，保证线程安全且延迟初始化。</p>
     *
     * @return 默认转换器链
     */
    public static ConverterChain getDefaultChain() {
        if (defaultChain == null) {
            synchronized (ConverterRegistry.class) {
                if (defaultChain == null) {
                    defaultChain = createDefaultChain();
                }
            }
        }
        return defaultChain;
    }

    /**
     * 注册自定义转换器
     *
     * <p>将自定义转换器添加到默认链中。
     * 自定义转换器可通过实现{@link CellValueConverter#priority()}来控制优先级。</p>
     *
     * @param converter 自定义转换器实例
     */
    public static void registerCustomConverter(CellValueConverter converter) {
        getDefaultChain().register(converter);
    }

    /**
     * 重置默认转换器链
     *
     * <p>主要用于测试场景，恢复默认的内置转换器链。</p>
     */
    public static void reset() {
        synchronized (ConverterRegistry.class) {
            defaultChain = null;
        }
    }

    private static ConverterChain createDefaultChain() {
        List<CellValueConverter> converters = new ArrayList<>();
        converters.add(new com.njydsz.pmis.common.excel.converter.impl.StringConverter());
        converters.add(new com.njydsz.pmis.common.excel.converter.impl.NumberConverter());
        converters.add(new com.njydsz.pmis.common.excel.converter.impl.BooleanConverter());
        converters.add(new com.njydsz.pmis.common.excel.converter.impl.BigDecimalConverter());
        converters.add(new com.njydsz.pmis.common.excel.converter.impl.DateConverter());
        converters.add(new com.njydsz.pmis.common.excel.converter.impl.LocalDateTimeConverter());
        converters.add(new com.njydsz.pmis.common.excel.converter.impl.LocalDateConverter());
        converters.add(new com.njydsz.pmis.common.excel.converter.impl.LocalTimeConverter());
        converters.add(new com.njydsz.pmis.common.excel.converter.impl.YearMonthConverter());
        converters.add(new com.njydsz.pmis.common.excel.converter.impl.TimestampConverter());
        converters.add(new com.njydsz.pmis.common.excel.converter.impl.EnumConverter());
        return new ConverterChain(converters);
    }
}
