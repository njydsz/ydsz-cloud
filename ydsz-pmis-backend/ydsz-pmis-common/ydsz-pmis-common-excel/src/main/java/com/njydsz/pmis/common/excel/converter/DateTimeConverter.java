package com.njydsz.pmis.common.excel.converter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日期时间转换器 - 处理Java 8日期时间类型
 *
 * <p>支持LocalDateTime、LocalDate等Java 8新增的日期时间类型与字符串之间的转换。
 * 使用DateTimeFormatter进行格式化和解析,并通过缓存提升性能。</p>
 *
 * @see Converter
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
public abstract class DateTimeConverter {

    private static final Map<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

    protected DateTimeFormatter getFormatter(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            pattern = "yyyy-MM-dd HH:mm:ss";
        }
        return FORMATTER_CACHE.computeIfAbsent(pattern, p -> DateTimeFormatter.ofPattern(p));
    }

    public abstract Object convert(Object value);

    public abstract Object toExcelValue(Object value);

    public static class LocalDateTimeConverter extends DateTimeConverter {
        private final String pattern;

        public LocalDateTimeConverter() {
            this("yyyy-MM-dd HH:mm:ss");
        }

        public LocalDateTimeConverter(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public LocalDateTime convert(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof LocalDateTime) {
                return (LocalDateTime) value;
            }
            if (value instanceof Date) {
                return ((Date) value).toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            }
            return LocalDateTime.parse(value.toString(), getFormatter(pattern));
        }

        @Override
        public Object toExcelValue(Object value) {
            if (value instanceof LocalDateTime) {
                return ((LocalDateTime) value).format(getFormatter(pattern));
            }
            return value;
        }
    }
}
