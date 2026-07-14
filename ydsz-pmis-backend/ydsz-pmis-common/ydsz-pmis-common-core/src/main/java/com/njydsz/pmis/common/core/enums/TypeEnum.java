package com.njydsz.pmis.common.core.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用枚举接口
 *
 * <p>定义了系统中所有枚举类型应实现的接口规范。
 * 所有实现此接口的枚举必须提供类型编码和描述信息。
 *
 * @param <T> 编码类型，支持 String、Integer 等类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface TypeEnum<T> {

    /**
     * 获取类型编码
     *
     * @return 类型编码
     */
    T getCode();

    /**
     * 获取类型描述
     *
     * @return 类型描述
     */
    String getDesc();

    /**
     * 构建枚举码到枚举实例的映射
     * <p>消除各枚举类中重复的 CODE_MAP 初始化代码
     *
     * @param enumClass 枚举类
     * @param <T> 码类型
     * @param <E> 枚举类型
     * @return 不可变的码到枚举映射
     */
    static <T, E extends Enum<E> & TypeEnum<T>> Map<T, E> buildCodeMap(Class<E> enumClass) {
        return Collections.unmodifiableMap(
            Arrays.stream(enumClass.getEnumConstants())
                .collect(Collectors.toMap(TypeEnum::getCode, Function.identity()))
        );
    }

    /**
     * 根据码查找枚举实例
     *
     * @param codeMap 码映射
     * @param code 码值
     * @param <T> 码类型
     * @param <E> 枚举类型
     * @return 枚举实例
     * @throws IllegalArgumentException 当码不存在时
     */
    static <T, E extends Enum<E> & TypeEnum<T>> E codeOf(Map<T, E> codeMap, T code) {
        E value = codeMap.get(code);
        if (value == null) {
            throw new IllegalArgumentException("No enum constant with code: " + code);
        }
        return value;
    }
}