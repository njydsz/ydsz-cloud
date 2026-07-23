package com.njydsz.common.excel.converter.impl;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.excel.converter.CellValueConverter;
import com.njydsz.common.excel.converter.ConvertContext;

/**
 * YearMonth类型转换器
 *
 * <p>处理目标类型为YearMonth的转换。支持从String等原始值转换。</p>
 *
 * @author ydsz-team
 * @email ydsz-dev@njydsz.com
 * @version 1.0.0
 */
public class YearMonthConverter implements CellValueConverter {

    private static final Map<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean supports(Class<?> targetType) {
        return targetType == YearMonth.class;
    }

    @Override
    public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof YearMonth) {
            return rawValue;
        }

        if (rawValue instanceof String) {
            return parseYearMonthString((String) rawValue);
        }

        return null;
    }

    private YearMonth parseYearMonthString(String yearMonthStr) {
        if (yearMonthStr == null || yearMonthStr.isEmpty()) {
            return null;
        }
        try {
            return YearMonth.parse(yearMonthStr);
        } catch (Exception e) {
            try {
                DateTimeFormatter formatter = FORMATTER_CACHE.computeIfAbsent("yyyy-MM",
                    DateTimeFormatter::ofPattern);
                return YearMonth.parse(yearMonthStr, formatter);
            } catch (Exception e2) {
                try {
                    DateTimeFormatter formatter = FORMATTER_CACHE.computeIfAbsent("yyyy/MM",
                        DateTimeFormatter::ofPattern);
                    return YearMonth.parse(yearMonthStr, formatter);
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }

    @Override
    public int priority() {
        return 90;
    }
}
