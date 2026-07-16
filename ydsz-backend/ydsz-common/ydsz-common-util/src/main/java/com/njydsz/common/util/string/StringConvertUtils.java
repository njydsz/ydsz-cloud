package com.njydsz.common.util.string;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 通用类型转换工具类
 *
 * <p>提供全面的类型转换方法，功能对标 Hutool Convert 和 Apache Commons BeanUtils。
 *
 * <p><b>注意：</b>数字相关转换方法（toInt/toLong/toDouble/toBigDecimal 等）与
 * {@link com.njydsz.common.util.number.NumberUtils} 存在重叠。
 * 数字运算、比较、格式化请优先使用 {@link com.njydsz.common.util.number.NumberUtils}。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>基础类型转换：toStr、toInt、toLong、toByte、toChar、toBool、toBigDecimal</li>
 *   <li>浮点数转换：toFloat、toDouble</li>
 *   <li>日期时间转换：toDate、toLocalDate、toLocalDateTime、toInstant</li>
 *   <li>大数转换：toBigInteger</li>
 *   <li>短整型转换：toShort</li>
 *   <li>枚举转换：toEnum</li>
 *   <li>UUID 生成：toUUID</li>
 *   <li>十六进制转换：hexToString、stringToHex</li>
 *   <li>集合转换：toList、toSet、toArray</li>
 *   <li>数字格式化转换：toNumber</li>
 *   <li>金额转换：toMoney（分转元）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class StringConvertUtils {

    /**
     * 转换为字符串 (支持默认值)
     */
    public static String toStr(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value instanceof String ? (String) value : value.toString();
    }

    public static String toStr(Object value) {
        return toStr(value, null);
    }

    /**
     * 转换为 Character
     */
    public static Character toChar(Object value, Character defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Character) {
            return (Character) value;
        }
        String valueStr = toStr(value, "");
        return valueStr.isEmpty() ? defaultValue : valueStr.charAt(0);
    }

    public static Character toChar(Object value) {
        return toChar(value, null);
    }

    /**
     * 转换为 Byte
     */
    public static Byte toByte(Object value, Byte defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Byte) {
            return (Byte) value;
        }
        if (value instanceof Number) {
            return ((Number) value).byteValue();
        }
        try {
            return Byte.parseByte(toStr(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Byte toByte(Object value) {
        return toByte(value, null);
    }

    /**
     * 转换为 Integer
     */
    public static Integer toInt(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(toStr(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Integer toInt(Object value) {
        return toInt(value, null);
    }

    /**
     * 转换为 Long
     */
    public static Long toLong(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(toStr(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Long toLong(Object value) {
        return toLong(value, null);
    }

    /**
     * 转换为 BigDecimal
     */
    public static BigDecimal toBigDecimal(Object value, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Long) {
            return new BigDecimal((Long) value);
        }
        if (value instanceof Integer) {
            return new BigDecimal((Integer) value);
        }
        if (value instanceof Double) {
            return BigDecimal.valueOf((Double) value);
        }
        try {
            return new BigDecimal(toStr(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static BigDecimal toBigDecimal(Object value) {
        return toBigDecimal(value, BigDecimal.ZERO);
    }

    /**
     * 转换为 Boolean
     */
    public static Boolean toBool(Object value, Boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String valueStr = toStr(value);
        return "true".equalsIgnoreCase(valueStr) || "1".equals(valueStr) || "yes".equalsIgnoreCase(valueStr);
    }

    public static Boolean toBool(Object value) {
        return toBool(value, false);
    }

    /**
     * 转换为 Short
     */
    public static Short toShort(Object value, Short defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Short) {
            return (Short) value;
        }
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }
        try {
            return Short.parseShort(toStr(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Short toShort(Object value) {
        return toShort(value, null);
    }

    /**
     * 转换为 Float
     */
    public static Float toFloat(Object value, Float defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Float) {
            return (Float) value;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        try {
            return Float.parseFloat(toStr(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Float toFloat(Object value) {
        return toFloat(value, null);
    }

    /**
     * 转换为 Double
     */
    public static Double toDouble(Object value, Double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(toStr(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Double toDouble(Object value) {
        return toDouble(value, null);
    }

    /**
     * 转换为 BigInteger
     */
    public static BigInteger toBigInteger(Object value, BigInteger defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }
        if (value instanceof Long) {
            return BigInteger.valueOf((Long) value);
        }
        if (value instanceof Integer) {
            return BigInteger.valueOf((Integer) value);
        }
        try {
            return new BigInteger(toStr(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static BigInteger toBigInteger(Object value) {
        return toBigInteger(value, BigInteger.ZERO);
    }

    /**
     * 转换为 LocalDate
     */
    public static LocalDate toDate(Object value, String pattern, LocalDate defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toLocalDate();
        }
        
        String dateStr = toStr(value);
        if (dateStr.isEmpty()) {
            return defaultValue;
        }
        
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            if (pattern.contains("HH") || pattern.contains("mm") || pattern.contains("ss")) {
                LocalDateTime ldt = LocalDateTime.parse(dateStr, formatter);
                return ldt.toLocalDate();
            }
            return LocalDate.parse(dateStr, formatter);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static LocalDate toDate(Object value, String pattern) {
        return toDate(value, pattern, null);
    }

    public static LocalDate toDate(Object value) {
        return toDate(value, "yyyy-MM-dd HH:mm:ss", null);
    }

    /**
     * 转换为 LocalDate
     */
    public static LocalDate toLocalDate(Object value, String pattern, LocalDate defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toLocalDate();
        }
        
        String dateStr = toStr(value);
        if (dateStr.isEmpty()) {
            return defaultValue;
        }
        
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDate.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            return defaultValue;
        }
    }

    public static LocalDate toLocalDate(Object value, String pattern) {
        return toLocalDate(value, pattern, null);
    }

    public static LocalDate toLocalDate(Object value) {
        return toLocalDate(value, "yyyy-MM-dd", null);
    }

    /**
     * 转换为 LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(Object value, String pattern, LocalDateTime defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }
        
        String dateStr = toStr(value);
        if (dateStr.isEmpty()) {
            return defaultValue;
        }
        
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDateTime.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            return defaultValue;
        }
    }

    public static LocalDateTime toLocalDateTime(Object value, String pattern) {
        return toLocalDateTime(value, pattern, null);
    }

    public static LocalDateTime toLocalDateTime(Object value) {
        return toLocalDateTime(value, "yyyy-MM-dd HH:mm:ss", null);
    }

    /**
     * 转换为 Instant
     */
    public static Instant toInstant(Object value, Instant defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant();
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
        if (value instanceof Long) {
            return Instant.ofEpochMilli((Long) value);
        }
        
        String dateStr = toStr(value);
        if (dateStr.isEmpty()) {
            return defaultValue;
        }
        
        try {
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e) {
            return defaultValue;
        }
    }

    public static Instant toInstant(Object value) {
        return toInstant(value, null);
    }

    /**
     * 转换为枚举
     */
    public static <T extends Enum<T>> T toEnum(Class<T> enumClass, Object value, T defaultValue) {
        if (enumClass == null || value == null) {
            return defaultValue;
        }
        if (enumClass.isInstance(value)) {
            return enumClass.cast(value);
        }
        
        String valueStr = toStr(value);
        if (valueStr.isEmpty()) {
            return defaultValue;
        }
        
        try {
            return Enum.valueOf(enumClass, valueStr);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    public static <T extends Enum<T>> T toEnum(Class<T> enumClass, Object value) {
        return toEnum(enumClass, value, null);
    }

    /**
     * 转换为 UUID
     */
    public static UUID toUUID(Object value, UUID defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof UUID) {
            return (UUID) value;
        }
        
        String valueStr = toStr(value);
        if (valueStr.isEmpty()) {
            return defaultValue;
        }
        
        try {
            return UUID.fromString(valueStr);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    public static UUID toUUID(Object value) {
        return toUUID(value, null);
    }

    /**
     * 生成随机 UUID
     */
    public static String randomUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * 十六进制字符串转字符串
     */
    public static String hexToString(String hexStr) {
        if (StringUtils.isEmpty(hexStr)) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hexStr.length(); i += 2) {
            String hex = hexStr.substring(i, i + 2);
            sb.append((char) Integer.parseInt(hex, 16));
        }
        return sb.toString();
    }

    /**
     * 字符串转十六进制
     */
    public static String stringToHex(String str) {
        if (StringUtils.isEmpty(str)) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            sb.append(String.format("%02x", (int) c));
        }
        return sb.toString();
    }

    /**
     * 转换为 List
     */
    
    public static <T> List<T> toList(Object value, Class<T> clazz) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List) {
            return ((List<?>) value).stream().map(clazz::cast).collect(Collectors.toList());
        }
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            List<T> list = new ArrayList<>();
            for (Object obj : collection) {
                list.add(clazz.cast(obj));
            }
            return list;
        }
        if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            List<T> list = new ArrayList<>();
            for (Object obj : array) {
                list.add(clazz.cast(obj));
            }
            return list;
        }
        
        List<T> list = new ArrayList<>();
        list.add(clazz.cast(value));
        return list;
    }

    /**
     * 转换为 Set
     */
    
    public static <T> Set<T> toSet(Object value, Class<T> clazz) {
        if (value == null) {
            return new HashSet<>();
        }
        if (value instanceof Set) {
            return ((Set<?>) value).stream().map(clazz::cast).collect(Collectors.toSet());
        }
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            Set<T> set = new HashSet<>();
            for (Object obj : collection) {
                set.add(clazz.cast(obj));
            }
            return set;
        }
        
        Set<T> set = new HashSet<>();
        set.add(clazz.cast(value));
        return set;
    }

    /**
     * 转换为数组
     */
    
    public static <T> T[] toArray(Object value, Class<T> clazz, T[] defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object[] source = new Object[length];
            for (int i = 0; i < length; i++) {
                source[i] = clazz.cast(Array.get(value, i));
            }
            return Arrays.copyOf(source, length, getArrayClass(clazz));
        }
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            Object[] source = new Object[collection.size()];
            int i = 0;
            for (Object obj : collection) {
                source[i++] = clazz.cast(obj);
            }
            return Arrays.copyOf(source, collection.size(), getArrayClass(clazz));
        }
        
        Object[] source = new Object[]{clazz.cast(value)};
        return Arrays.copyOf(source, 1, getArrayClass(clazz));
    }

    private static <T> Class<? extends T[]> getArrayClass(Class<T> componentType) {
        return (Class<? extends T[]>) componentType.arrayType();
    }

    /**
     * 转换为数字（支持格式化字符串）
     */
    public static Number toNumber(Object value, Number defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return (Number) value;
        }
        
        String valueStr = toStr(value);
        if (valueStr.isEmpty()) {
            return defaultValue;
        }
        
        try {
            if (valueStr.contains(".")) {
                return Double.parseDouble(valueStr);
            } else {
                return Long.parseLong(valueStr);
            }
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 金额转换（分转元）
     */
    public static BigDecimal toMoney(Object value, BigDecimal defaultValue) {
        BigDecimal amount = toBigDecimal(value, defaultValue);
        if (amount == null) {
            return defaultValue;
        }
        return amount.divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal toMoney(Object value) {
        return toMoney(value, BigDecimal.ZERO);
    }

    /**
     * 金额转换（元转分）
     */
    public static BigDecimal toMoneyInCents(Object value, BigDecimal defaultValue) {
        BigDecimal amount = toBigDecimal(value, defaultValue);
        if (amount == null) {
            return defaultValue;
        }
        return amount.multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * 布尔值转整数
     */
    public static Integer boolToInt(Boolean value) {
        return value == null ? 0 : (value ? 1 : 0);
    }

    /**
     * 字符串转布尔（支持更多格式）
     */
    public static Boolean strToBool(String str, Boolean defaultValue) {
        if (StringUtils.isEmpty(str)) {
            return defaultValue;
        }
        String lower = str.toLowerCase().trim();
        if ("true".equals(lower) || "1".equals(lower) || "yes".equals(lower) || "y".equals(lower) || "是".equals(lower)) {
            return true;
        }
        if ("false".equals(lower) || "0".equals(lower) || "no".equals(lower) || "n".equals(lower) || "否".equals(lower)) {
            return false;
        }
        return defaultValue;
    }

    public static Boolean strToBool(String str) {
        return strToBool(str, false);
    }

    /**
     * 驼峰转下划线后转小写
     */
    public static String camelToUnderline(String str) {
        if (StringUtils.isEmpty(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 下划线转驼峰
     */
    public static String underlineToCamel(String str) {
        if (StringUtils.isEmpty(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 中横线转驼峰
     */
    public static String kebabToCamel(String str) {
        if (StringUtils.isEmpty(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '-') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
