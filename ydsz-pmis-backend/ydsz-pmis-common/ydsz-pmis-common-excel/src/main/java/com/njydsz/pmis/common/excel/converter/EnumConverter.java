package com.njydsz.pmis.common.excel.converter;

import java.util.HashMap;
import java.util.Map;

/**
 * 枚举转换�?
 *
 * <p>用于将枚举值与字符串之间的转换�?
 * 支持自定义映射关系�?/p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public enum Gender {
 *     MALE, FEMALE
 * }
 *
 * // 使用默认转换
 * @ExcelProperty(value = "性别", converterClass = EnumConverter.class)
 * private Gender gender;
 *
 * // 使用自定义映�?
 * EnumConverter.registerMapping(Gender.class, "�?, Gender.MALE);
 * EnumConverter.registerMapping(Gender.class, "�?, Gender.FEMALE);
 * }</pre>
 *
 * @param <E> 枚举类型
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
public class EnumConverter<E extends Enum<E>> implements Converter<E, String> {

    private final Class<E> enumClass;
    private static final Map<Class<?>, Map<String, Enum<?>>> CUSTOM_MAPPINGS = new HashMap<>();
    private static final Map<Class<?>, Map<Enum<?>, String>> REVERSE_MAPPINGS = new HashMap<>();

    public EnumConverter(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public String convertToExcel(E source) {
        if (source == null) {
            return null;
        }

        Map<Enum<?>, String> reverseMap = REVERSE_MAPPINGS.get(enumClass);
        if (reverseMap != null && reverseMap.containsKey(source)) {
            return reverseMap.get(source);
        }

        return source.name();
    }

    @Override
    public E convertFromSource(String source) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        Map<String, Enum<?>> map = CUSTOM_MAPPINGS.get(enumClass);
        if (map != null && map.containsKey(source)) {
            return (E) map.get(source);
        }

        try {
            return Enum.valueOf(enumClass, source);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 注册自定义枚举映�?
     *
     * @param enumClass 枚举类型
     * @param stringValue 字符串�?
     * @param enumValue 枚举�?
     */
    public static void registerMapping(Class<?> enumClass, String stringValue, Enum<?> enumValue) {
        CUSTOM_MAPPINGS.computeIfAbsent(enumClass, k -> new HashMap<>())
                .put(stringValue, enumValue);
        REVERSE_MAPPINGS.computeIfAbsent(enumClass, k -> new HashMap<>())
                .put(enumValue, stringValue);
    }
}
