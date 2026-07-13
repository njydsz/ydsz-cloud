package com.njydsz.pmis.common.excel.converter;

/**
 * 单元格值转换器SPI接口
 *
 * <p>定义单元格值的类型转换契约。每个转换器负责一种或多种目标类型的转换。
 * 通过{@link #supports(Class)}声明自己支持的目标类型，
 * 通过{@link #priority()}控制转换器在链中的优先级（数值越小优先级越高）。</p>
 *
 * <h3>扩展方式</h3>
 * <pre>{@code
 * public class MyConverter implements CellValueConverter {
 *     @Override
 *     public boolean supports(Class<?> targetType) {
 *         return MyType.class.isAssignableFrom(targetType);
 *     }
 *
 *     @Override
 *     public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
 *         // 自定义转换逻辑
 *         return new MyType(rawValue.toString());
 *     }
 *
 *     @Override
 *     public int priority() {
 *         return 50; // 高于默认优先级100
 *     }
 * }
 *
 * // 注册自定义转换器
 * ConverterRegistry.registerCustomConverter(new MyConverter());
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 * @see ConverterChain
 * @see ConverterRegistry
 */
public interface CellValueConverter {

    /**
     * 判断是否支持转换到指定目标类型
     *
     * @param targetType 目标Java类型
     * @return true表示支持该类型的转换
     */
    boolean supports(Class<?> targetType);

    /**
     * 执行类型转换
     *
     * @param rawValue 原始值（从POI Cell中提取的String、Double、Boolean、Date或null）
     * @param targetType 目标Java类型
     * @param context 转换上下文
     * @return 转换后的值，无法转换时返回null
     */
    Object convert(Object rawValue, Class<?> targetType, ConvertContext context);

    /**
     * 转换器优先级
     *
     * <p>数值越小优先级越高。默认优先级为100。
     * 自定义转换器可通过覆盖此方法提供更高优先级来替代内置转换器。</p>
     *
     * @return 优先级数值
     */
    default int priority() {
        return 100;
    }
}
