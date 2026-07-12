package com.njydsz.pmis.common.util.bean;

/**
 * 属性转换器接口
 * <p>
 * 用于在 Bean 拷贝过程中对属性值进行自定义转换
 * 支持不同类型之间的转换，如 String 转 Integer、Date 转 String 等
 * </p>
 *
 * @param <S> 源类型
 * @param <T> 目标类型
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@FunctionalInterface
public interface PropertyConverter<S, T> {

    /**
     * 转换属性值
     *
     * @param source 源值
     * @return 转换后的值
     */
    T convert(S source);

    /**
     * 链式组合另一个转换器
     *
     * @param after 另一个转换器
     * @param <R>   最终目标类型
     * @return 组合后的转换器
     */
    default <R> PropertyConverter<S, R> andThen(PropertyConverter<? super T, ? extends R> after) {
        return (S source) -> after.convert(convert(source));
    }

    /**
     * 恒等转换器（不做任何转换）
     *
     * @param <T> 类型
     * @return 恒等转换器
     */
    static <T> PropertyConverter<T, T> identity() {
        return t -> t;
    }
}
