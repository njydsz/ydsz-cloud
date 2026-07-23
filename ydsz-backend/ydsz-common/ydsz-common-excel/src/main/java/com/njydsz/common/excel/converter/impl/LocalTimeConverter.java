package com.njydsz.common.excel.converter.impl;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.excel.converter.CellValueConverter;
import com.njydsz.common.excel.converter.ConvertContext;

/**
 * LocalTime类型转换器
 *
 * <p>处理目标类型为LocalTime的转换。支持从String等原始值转换。</p>
 *
 * @author ydsz-team
 * @email ydsz-dev@njydsz.com
 * @version 1.0.0
 */
public class LocalTimeConverter implements CellValueConverter {

    private static final Map<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean supports(Class<?> targetType) {
        return targetType == LocalTime.class;
    }

    @Override
    public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof LocalTime) {
            return rawValue;
        }

        if (rawValue instanceof String) {
            return parseLocalTimeString((String) rawValue);
        }

        return null;
    }

    private LocalTime parseLocalTimeString(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(timeStr);
        } catch (Exception e) {
            try {
                DateTimeFormatter formatter = FORMATTER_CACHE.computeIfAbsent("HH:mm:ss",
                    DateTimeFormatter::ofPattern);
                return LocalTime.parse(timeStr, formatter);
            } catch (Exception e2) {
                try {
                    DateTimeFormatter formatter = FORMATTER_CACHE.computeIfAbsent("HH:mm",
                        DateTimeFormatter::ofPattern);
                    return LocalTime.parse(timeStr, formatter);
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }

    @Override
    public int priority() {
        return 80;
    }
}
