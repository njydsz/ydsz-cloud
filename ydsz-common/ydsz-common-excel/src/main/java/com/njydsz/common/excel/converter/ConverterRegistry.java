package com.njydsz.common.excel.converter;

import java.util.ArrayList;
import java.util.List;

import com.njydsz.common.excel.converter.impl.BigDecimalConverter;
import com.njydsz.common.excel.converter.impl.BooleanConverter;
import com.njydsz.common.excel.converter.impl.DateConverter;
import com.njydsz.common.excel.converter.impl.EnumConverter;
import com.njydsz.common.excel.converter.impl.LocalDateConverter;
import com.njydsz.common.excel.converter.impl.LocalDateTimeConverter;
import com.njydsz.common.excel.converter.impl.LocalTimeConverter;
import com.njydsz.common.excel.converter.impl.NumberConverter;
import com.njydsz.common.excel.converter.impl.StringConverter;
import com.njydsz.common.excel.converter.impl.TimestampConverter;
import com.njydsz.common.excel.converter.impl.YearMonthConverter;

/**
 * 转换器注册中心 - SPI注册入口
 *
 * <p>管理默认转换器链和自定义转换器的注册。 默认转换器链在首次访问时懒初始化，包含所有内置转换器。
 *
 * <h3>注册自定义转换器</h3>
 *
 * <pre>{@code
 * // 注册自定义转换器（会添加到默认链中）
 * ConverterRegistry.registerCustomConverter(new MyConverter());
 * }</pre>
 *
 * <h3>内置转换器（按优先级排序）</h3>
 *
 * <ul>
 *   <li>StringConverter (priority=10)
 *   <li>NumberConverter (priority=20)
 *   <li>BooleanConverter (priority=30)
 *   <li>BigDecimalConverter (priority=40)
 *   <li>DateConverter (priority=50)
 *   <li>LocalDateTimeConverter (priority=60)
 *   <li>LocalDateConverter (priority=70)
 *   <li>LocalTimeConverter (priority=80)
 *   <li>YearMonthConverter (priority=90)
 *   <li>TimestampConverter (priority=100)
 *   <li>EnumConverter (priority=110)
 * </ul>
 *
 * @author ydsz-team
 * @email ydsz-dev@ydszsoft.com
 * @version 1.0.0
 * @since 1.0.0
 */
public class ConverterRegistry {

  private static volatile ConverterChain defaultChain;

  private ConverterRegistry() {}

  /**
   * 获取默认转换器链
   *
   * <p>采用双重检查锁定模式，保证线程安全且延迟初始化。
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
   * <p>将自定义转换器添加到默认链中。 自定义转换器可通过实现{@link CellValueConverter#priority()}来控制优先级。
   *
   * @param converter 自定义转换器实例
   */
  public static void registerCustomConverter(CellValueConverter converter) {
    getDefaultChain().register(converter);
  }

  /**
   * 重置默认转换器链
   *
   * <p>主要用于测试场景，恢复默认的内置转换器链。
   */
  public static void reset() {
    synchronized (ConverterRegistry.class) {
      defaultChain = null;
    }
  }

  private static ConverterChain createDefaultChain() {
    List<CellValueConverter> converters = new ArrayList<>();
    converters.add(new StringConverter());
    converters.add(new NumberConverter());
    converters.add(new BooleanConverter());
    converters.add(new BigDecimalConverter());
    converters.add(new DateConverter());
    converters.add(new LocalDateTimeConverter());
    converters.add(new LocalDateConverter());
    converters.add(new LocalTimeConverter());
    converters.add(new YearMonthConverter());
    converters.add(new TimestampConverter());
    converters.add(new EnumConverter());
    return new ConverterChain(converters);
  }
}
