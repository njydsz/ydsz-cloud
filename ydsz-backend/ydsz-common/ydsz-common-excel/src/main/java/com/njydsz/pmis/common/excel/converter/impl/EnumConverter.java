package com.njydsz.common.excel.converter.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import com.njydsz.common.excel.converter.CellValueConverter;
import com.njydsz.common.excel.converter.ConvertContext;

/**
 * Enum类型转换器
 *
 * <p>处理目标类型为Enum的转换。支持从String等原始值转换。
 * 支持通过{@link #registerMapping}注册自定义枚举映射。</p>
 *
 * @author ydsz-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
public class EnumConverter implements CellValueConverter {

    private static final Map<Class<?>, Map<String, Enum<?>>> CUSTOM_MAPPINGS = new ConcurrentHashMap<>();

    @Override
    public boolean supports(Class<?> targetType) {
        return targetType != null && targetType.isEnum();
    }

    @Override
    public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
        if (rawValue == null) {
            return null;
        }

        String strValue;
        if (rawValue instanceof String s) {
            strValue = s;
        } else {
            strValue = rawValue.toString();
        }

        if (strValue.isEmpty()) {
            return null;
        }

        Map<String, Enum<?>> map = CUSTOM_MAPPINGS.get(targetType);
        if (map != null && map.containsKey(strValue)) {
            return map.get(strValue);
        }

        try {
            return Enum.valueOf(targetType.asSubclass(Enum.class), strValue);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public int priority() {
        return 110;
    }

    /**
     * 注册自定义枚举映射
     *
     * @param enumClass 枚举类型
     * @param stringValue 字符串值
     * @param enumValue 枚举值
     */
    public static void registerMapping(Class<?> enumClass, String stringValue, Enum<?> enumValue) {
        CUSTOM_MAPPINGS.computeIfAbsent(enumClass, k -> new ConcurrentHashMap<>())
            .put(stringValue, enumValue);
    }
}
