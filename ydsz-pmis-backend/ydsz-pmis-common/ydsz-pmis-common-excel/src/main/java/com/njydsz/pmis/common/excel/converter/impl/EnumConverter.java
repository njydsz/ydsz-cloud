package com.njydsz.pmis.common.excel.converter.impl;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.pmis.common.excel.converter.CellValueConverter;
import com.njydsz.pmis.common.excel.converter.ConvertContext;

/**
 * Enum类型转换器
 *
 * <p>处理目标类型为Enum的转换。支持从String等原始值转换。
 * 支持通过{@link #registerMapping}注册自定义枚举映射。</p>
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
public class EnumConverter implements CellValueConverter {

    private static final Map<Class<?>, Map<String, Enum<?>>> CUSTOM_MAPPINGS = new HashMap<>();

    @Override
    public boolean supports(Class<?> targetType) {
        return targetType != null && targetType.isEnum();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
        if (rawValue == null) {
            return null;
        }

        String strValue;
        if (rawValue instanceof String) {
            strValue = (String) rawValue;
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
            @SuppressWarnings("rawtypes")
            Class<Enum> enumClass = (Class<Enum>) targetType;
            return Enum.valueOf(enumClass, strValue);
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
        CUSTOM_MAPPINGS.computeIfAbsent(enumClass, k -> new HashMap<>())
            .put(stringValue, enumValue);
    }
}
