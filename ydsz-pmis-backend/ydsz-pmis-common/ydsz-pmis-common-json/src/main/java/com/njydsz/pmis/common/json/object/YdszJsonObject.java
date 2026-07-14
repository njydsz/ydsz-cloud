package com.njydsz.pmis.common.json.object;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.njydsz.pmis.common.json.YdszJson;

/**
 * YdszJson 对象实现
 * 对应 fastjson2 的 JSONObject，提供动态 JSON 对象操作
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class YdszJsonObject extends LinkedHashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    /**
     * 默认构造函数
     */
    public YdszJsonObject() {
        super();
    }

    /**
     * 指定初始容量的构造函数
     *
     * @param initialCapacity 初始容量
     */
    public YdszJsonObject(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * 从 Map 创建 YdszJsonObject
     *
     * @param map 源 Map
     */
        public YdszJsonObject(Map<?, ?> map) {
        super();
        if (map != null) {
            LinkedHashMap<String, Object> filtered = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String) {
                    filtered.put((String) entry.getKey(), entry.getValue());
                }
            }
            super.putAll(filtered);
        }
    }

    // ==================== 基本类型 getter ====================

    /**
     * 获取字符串值
     *
     * @param key 键
     * @return 字符串值
     */
    public String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取整数值
     *
     * @param key 键
     * @return 整数值
     */
    public Integer getInteger(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取 int 基本类型值（为 null 时返回 0）
     *
     * @param key 键
     * @return int 值
     */
    public int getIntValue(String key) {
        Integer value = getInteger(key);
        return value != null ? value : 0;
    }

    /**
     * 获取长整数值
     *
     * @param key 键
     * @return 长整数值
     */
    public Long getLong(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取 long 基本类型值（为 null 时返回 0）
     *
     * @param key 键
     * @return long 值
     */
    public long getLongValue(String key) {
        Long value = getLong(key);
        return value != null ? value : 0L;
    }

    /**
     * 获取双精度浮点数值
     *
     * @param key 键
     * @return 双精度浮点数值
     */
    public Double getDouble(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取 double 基本类型值（为 null 时返回 0）
     *
     * @param key 键
     * @return double 值
     */
    public double getDoubleValue(String key) {
        Double value = getDouble(key);
        return value != null ? value : 0.0;
    }

    /**
     * 获取布尔值
     *
     * @param key 键
     * @return 布尔值
     */
    public Boolean getBoolean(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String str = value.toString();
        if ("true".equalsIgnoreCase(str) || "1".equals(str)) {
            return true;
        }
        if ("false".equalsIgnoreCase(str) || "0".equals(str)) {
            return false;
        }
        return null;
    }

    /**
     * 获取 boolean 基本类型值（为 null 时返回 false）
     *
     * @param key 键
     * @return boolean 值
     */
    public boolean getBooleanValue(String key) {
        Boolean value = getBoolean(key);
        return value != null ? value : false;
    }

    /**
     * 获取 byte 值
     *
     * @param key 键
     * @return byte 值
     */
    public Byte getByte(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Byte) {
            return (Byte) value;
        }
        if (value instanceof Number) {
            return ((Number) value).byteValue();
        }
        try {
            return Byte.parseByte(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取 byte 基本类型值（为 null 时返回 0）
     *
     * @param key 键
     * @return byte 值
     */
    public byte getByteValue(String key) {
        Byte value = getByte(key);
        return value != null ? value : 0;
    }

    /**
     * 获取 short 值
     *
     * @param key 键
     * @return short 值
     */
    public Short getShort(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Short) {
            return (Short) value;
        }
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }
        try {
            return Short.parseShort(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取 short 基本类型值（为 null 时返回 0）
     *
     * @param key 键
     * @return short 值
     */
    public short getShortValue(String key) {
        Short value = getShort(key);
        return value != null ? value : 0;
    }

    /**
     * 获取 float 值
     *
     * @param key 键
     * @return float 值
     */
    public Float getFloat(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Float) {
            return (Float) value;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        try {
            return Float.parseFloat(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取 float 基本类型值（为 null 时返回 0）
     *
     * @param key 键
     * @return float 值
     */
    public float getFloatValue(String key) {
        Float value = getFloat(key);
        return value != null ? value : 0.0f;
    }

    /**
     * 获取 BigDecimal 值
     *
     * @param key 键
     * @return BigDecimal 值
     */
    public BigDecimal getBigDecimal(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取 BigInteger 值
     *
     * @param key 键
     * @return BigInteger 值
     */
    public BigInteger getBigInteger(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toBigInteger();
        }
        if (value instanceof Number) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        try {
            return new BigInteger(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 日期时间类型 getter ====================

    /**
     * 获取 Date 对象
     *
     * @param key 键
     * @return Date 对象
     */
    public Date getDate(String key) {
        return getDate(key, null);
    }

    /**
     * 获取 Date 对象（带格式）
     *
     * @param key 键
     * @param pattern 日期格式
     * @return Date 对象
     */
    public Date getDate(String key, String pattern) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof Number) {
            return new Date(((Number) value).longValue());
        }
        String str = value.toString();
        try {
            if (pattern != null && !pattern.isEmpty()) {
                LocalDateTime ldt = LocalDateTime.parse(str, DateTimeFormatter.ofPattern(pattern));
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } else {
                Instant instant = Instant.parse(str);
                return Date.from(instant);
            }
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 获取 Instant 对象
     *
     * @param key 键
     * @return Instant 对象
     */
    public Instant getInstant(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant();
        }
        if (value instanceof Number) {
            return Instant.ofEpochMilli(((Number) value).longValue());
        }
        try {
            return Instant.parse(value.toString());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 获取 LocalDate 对象
     *
     * @param key 键
     * @return LocalDate 对象
     */
    public LocalDate getLocalDate(String key) {
        return getLocalDate(key, null);
    }

    /**
     * 获取 LocalDate 对象（带格式）
     *
     * @param key 键
     * @param pattern 日期格式
     * @return LocalDate 对象
     */
    public LocalDate getLocalDate(String key, String pattern) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toLocalDate();
        }
        String str = value.toString();
        try {
            if (pattern != null && !pattern.isEmpty()) {
                return LocalDate.parse(str, DateTimeFormatter.ofPattern(pattern));
            } else {
                return LocalDate.parse(str);
            }
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 获取 LocalDateTime 对象
     *
     * @param key 键
     * @return LocalDateTime 对象
     */
    public LocalDateTime getLocalDateTime(String key) {
        return getLocalDateTime(key, null);
    }

    /**
     * 获取 LocalDateTime 对象（带格式）
     *
     * @param key 键
     * @param pattern 日期格式
     * @return LocalDateTime 对象
     */
    public LocalDateTime getLocalDateTime(String key, String pattern) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }
        if (value instanceof Date) {
            return LocalDateTime.ofInstant(((Date) value).toInstant(), ZoneId.systemDefault());
        }
        if (value instanceof Instant) {
            return LocalDateTime.ofInstant((Instant) value, ZoneId.systemDefault());
        }
        String str = value.toString();
        try {
            if (pattern != null && !pattern.isEmpty()) {
                return LocalDateTime.parse(str, DateTimeFormatter.ofPattern(pattern));
            } else {
                return LocalDateTime.parse(str);
            }
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ==================== 嵌套对象 getter ====================

    /**
     * 获取 YdszJsonObject
     *
     * @param key 键
     * @return YdszJsonObject
     */
    public YdszJsonObject getJSONObject(String key) {
        Object value = get(key);
        if (value instanceof YdszJsonObject) {
            return (YdszJsonObject) value;
        }
        if (value instanceof Map) {
            return new YdszJsonObject((Map<?, ?>) value);
        }
        return null;
    }

    /**
     * 获取 YdszJsonArray
     *
     * @param key 键
     * @return YdszJsonArray
     */
    public YdszJsonArray getJSONArray(String key) {
        Object value = get(key);
        if (value instanceof YdszJsonArray) {
            return (YdszJsonArray) value;
        }
        if (value instanceof List) {
            return new YdszJsonArray((List<?>) value);
        }
        return null;
    }

    /**
     * 获取对象并转换为指定类型
     *
     * @param key 键
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 转换后的对象
     */
    public <T> T getObject(String key, Class<T> clazz) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        // 使用 YdszJson 进行转换
        String json = YdszJson.toJson(value);
        return YdszJson.toObject(json, clazz);
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取值或默认值
     *
     * @param key 键
     * @param defaultValue 默认值
     * @return 值或默认值
     */
    public Object getOrDefault(String key, Object defaultValue) {
        return super.getOrDefault(key, defaultValue);
    }

    /**
     * 获取字符串值或默认值
     *
     * @param key 键
     * @param defaultValue 默认值
     * @return 字符串值或默认值
     */
    public String getStringOrDefault(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取整数值或默认值
     *
     * @param key 键
     * @param defaultValue 默认值
     * @return 整数值或默认值
     */
    public Integer getIntegerOrDefault(String key, Integer defaultValue) {
        Integer value = getInteger(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取长整数值或默认值
     *
     * @param key 键
     * @param defaultValue 默认值
     * @return 长整数值或默认值
     */
    public Long getLongOrDefault(String key, Long defaultValue) {
        Long value = getLong(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取布尔值或默认值
     *
     * @param key 键
     * @param defaultValue 默认值
     * @return 布尔值或默认值
     */
    public Boolean getBooleanOrDefault(String key, Boolean defaultValue) {
        Boolean value = getBoolean(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 不存在时添加
     *
     * @param key 键
     * @param value 值
     * @return 旧值，如果不存在则返回 null
     */
    public Object putIfAbsent(String key, Object value) {
        return super.putIfAbsent(key, value);
    }

    /**
     * 计算值
     *
     * @param key 键
     * @param remappingFunction 重映射函数
     * @return 新值
     */
    public Object compute(String key, BiFunction<? super String, ? super Object, ?> remappingFunction) {
        return super.compute(key, (k, v) -> remappingFunction.apply(k, v));
    }

    /**
     * 不存在时计算
     *
     * @param key 键
     * @param mappingFunction 映射函数
     * @return 计算后的值
     */
    public Object computeIfAbsent(String key, Function<? super String, ?> mappingFunction) {
        return super.computeIfAbsent(key, mappingFunction);
    }

    /**
     * 存在时重新计算
     *
     * @param key 键
     * @param remappingFunction 重映射函数
     * @return 新值，如果不存在则返回 null
     */
    public Object computeIfPresent(String key, BiFunction<? super String, ? super Object, ?> remappingFunction) {
        return super.computeIfPresent(key, (k, v) -> remappingFunction.apply(k, v));
    }

    /**
     * 合并值
     *
     * @param key 键
     * @param value 值
     * @param remappingFunction 重映射函数
     * @return 合并后的值
     */
    public Object merge(String key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        return super.merge(key, value, remappingFunction);
    }

    /**
     * 批量获取多个键的值
     *
     * @param keys 键集合
     * @return Map 包含键值对
     */
    public Map<String, Object> getAll(Collection<String> keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : keys) {
            result.put(key, get(key));
        }
        return result;
    }

    // ==================== setter ====================

    /**
     * 设置值
     *
     * @param key 键
     * @param value 值
     * @return 当前对象，支持链式调用
     */
    public YdszJsonObject put(String key, Object value) {
        super.put(key, value);
        return this;
    }

    /**
     * 设置值并返回旧值
     *
     * @param key 键
     * @param value 值
     * @return 旧值
     */
    public Object set(String key, Object value) {
        return super.put(key, value);
    }

    /**
     * 批量添加
     *
     * @param m Map 对象
     */
    public void putAll(Map<? extends String, ?> m) {
        super.putAll(m);
    }

    /**
     * 移除键值对
     *
     * @param key 键
     * @return 移除的值
     */
    public Object remove(String key) {
        return super.remove(key);
    }

    // ==================== 查询方法 ====================

    /**
     * 是否包含键
     *
     * @param key 键
     * @return 如果包含返回 true
     */
    public boolean containsKey(String key) {
        return super.containsKey(key);
    }

    /**
     * 是否为空
     *
     * @return 如果为空返回 true
     */
    public boolean isEmpty() {
        return super.isEmpty();
    }

    /**
     * 获取大小
     *
     * @return 键值对数量
     */
    public int size() {
        return super.size();
    }

    /**
     * 清空
     */
    public void clear() {
        super.clear();
    }

    /**
     * 获取所有键
     *
     * @return 键集合
     */
    public Set<String> keySet() {
        return super.keySet();
    }

    /**
     * 获取所有值
     *
     * @return 值集合
     */
    public Collection<Object> values() {
        return super.values();
    }

    // ==================== 转换方法 ====================

    /**
     * 转换为 JSON 字符串
     *
     * @return JSON 字符串
     */
    public String toJsonString() {
        return YdszJson.toJson(this);
    }

    /**
     * 从 JSON 字符串解析
     *
     * @param json JSON 字符串
     * @return YdszJsonObject
     */
    public static YdszJsonObject parse(String json) {
        return YdszJson.toObject(json, YdszJsonObject.class);
    }

    /**
     * 从 Map 创建
     *
     * @param map Map 对象
     * @return YdszJsonObject
     */
    public static YdszJsonObject of(Map<?, ?> map) {
        if (map == null) {
            return new YdszJsonObject();
        }
        return new YdszJsonObject(map);
    }

    /**
     * 创建空的 YdszJsonObject
     *
     * @return YdszJsonObject
     */
    public static YdszJsonObject create() {
        return new YdszJsonObject();
    }

    @Override
    public String toString() {
        return toJsonString();
    }
}
