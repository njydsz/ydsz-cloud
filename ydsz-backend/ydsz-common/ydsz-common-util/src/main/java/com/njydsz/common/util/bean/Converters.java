package com.njydsz.common.util.bean;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 内置类型转换器
 * <p>
 * 提供常见类型之间的转换实现，包括：
 * 1. 基本类型转换（String、Integer、Long、Double 等）
 * 2. 日期类型转换（Date、String、Long）
 * 3. 数字格式化转换
 * </p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class Converters {

    /**
     * 日期格式
     */
    private static final ThreadLocal<Map<String, SimpleDateFormat>> DATE_FORMAT_CACHE =
            ThreadLocal.withInitial(HashMap::new);

    /**
     * 私有构造函数，防止实例化
     */
    private Converters() {
        throw new UnsupportedOperationException("Converters is a utility class and cannot be instantiated");
    }

    /**
     * String 转 Integer
     */
    public static final PropertyConverter<String, Integer> STRING_TO_INTEGER = source -> {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(source.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert String to Integer: " + source, e);
        }
    };

    /**
     * String 转 Long
     */
    public static final PropertyConverter<String, Long> STRING_TO_LONG = source -> {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(source.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert String to Long: " + source, e);
        }
    };

    /**
     * String 转 Double
     */
    public static final PropertyConverter<String, Double> STRING_TO_DOUBLE = source -> {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(source.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert String to Double: " + source, e);
        }
    };

    /**
     * Integer 转 String
     */
    public static final PropertyConverter<Integer, String> INTEGER_TO_STRING = source ->
            source == null ? null : String.valueOf(source);

    /**
     * Long 转 String
     */
    public static final PropertyConverter<Long, String> LONG_TO_STRING = source ->
            source == null ? null : String.valueOf(source);

    /**
     * Double 转 String
     */
    public static final PropertyConverter<Double, String> DOUBLE_TO_STRING = source ->
            source == null ? null : String.valueOf(source);

    /**
     * Number 转 Integer
     */
    public static final PropertyConverter<Number, Integer> NUMBER_TO_INTEGER = source ->
            source == null ? null : source.intValue();

    /**
     * Number 转 Long
     */
    public static final PropertyConverter<Number, Long> NUMBER_TO_LONG = source ->
            source == null ? null : source.longValue();

    /**
     * Number 转 Double
     */
    public static final PropertyConverter<Number, Double> NUMBER_TO_DOUBLE = source ->
            source == null ? null : source.doubleValue();

    /**
     * LocalDateTime 转 String（使用指定格式）
     *
     * @param pattern 日期格式
     * @return PropertyConverter
     * @since 1.0.0
     */
    public static PropertyConverter<LocalDateTime, String> localDateTimeToString(String pattern) {
        return source -> {
            if (source == null) {
                return null;
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return source.format(formatter);
        };
    }

    /**
     * String 转 LocalDateTime（使用指定格式）
     *
     * @param pattern 日期格式
     * @return PropertyConverter
     * @since 1.0.0
     */
    public static PropertyConverter<String, LocalDateTime> stringToLocalDateTime(String pattern) {
        return source -> {
            if (source == null || source.trim().isEmpty()) {
                return null;
            }
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                return LocalDateTime.parse(source.trim(), formatter);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Cannot convert String to LocalDateTime: " + source, e);
            }
        };
    }

    /**
     * LocalDateTime 转 Long（毫秒时间戳，使用系统默认时区）
     *
     * @since 1.0.0
     */
    public static final PropertyConverter<LocalDateTime, Long> LOCAL_DATE_TIME_TO_TIMESTAMP = source ->
            source == null ? null : source.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

    /**
     * Long（毫秒时间戳）转 LocalDateTime（使用系统默认时区）
     *
     * @since 1.0.0
     */
    public static final PropertyConverter<Long, LocalDateTime> TIMESTAMP_TO_LOCAL_DATE_TIME = source ->
            source == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(source), ZoneId.systemDefault());

    /**
     * Object 转 String
     */
    public static final PropertyConverter<Object, String> OBJECT_TO_STRING = source ->
            source == null ? null : String.valueOf(source);

    /**
     * 通用转换器（使用 Function）
     *
     * @param function 转换函数
     * @param <S>      源类型
     * @param <T>      目标类型
     * @return PropertyConverter
     */
    public static <S, T> PropertyConverter<S, T> of(Function<S, T> function) {
        return function::apply;
    }

    /**
     * 条件转换器
     * 只有当条件满足时才进行转换，否则返回原值
     *
     * @param converter 转换器
     * @param condition 条件判断
     * @param <S>       源类型
     * @param <T>       目标类型
     * @return PropertyConverter
     */
    
    public static <S, T> PropertyConverter<S, T> conditional(PropertyConverter<S, T> converter,
                                                              Function<S, Boolean> condition) {
        return source -> {
            if (condition.apply(source)) {
                return converter.convert(source);
            }
            return (T) source;
        };
    }

    /**
     * 空值安全转换器
     * 当源值为 null 时返回默认值
     *
     * @param converter    转换器
     * @param defaultValue 默认值
     * @param <S>          源类型
     * @param <T>          目标类型
     * @return PropertyConverter
     */
    public static <S, T> PropertyConverter<S, T> nullSafe(PropertyConverter<S, T> converter, T defaultValue) {
        return source -> {
            if (source == null) {
                return defaultValue;
            }
            return converter.convert(source);
        };
    }

    /**
     * 清除当前线程的日期格式缓存
     */
    public static void clearDateFormatCache() {
        DATE_FORMAT_CACHE.remove();
    }
}
