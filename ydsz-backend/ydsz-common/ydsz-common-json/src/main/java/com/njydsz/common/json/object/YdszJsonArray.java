package com.njydsz.common.json.object;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import com.njydsz.common.json.YdszJson;

/**
 * YdszJson 数组实现
 * 对应 fastjson2 的 JSONArray，提供动态 JSON 数组操作
 *
 * @since 1.0.0
 */
public class YdszJsonArray extends ArrayList<Object> {

    private static final long serialVersionUID = 1L;

    /**
     * 默认构造函数
     */
    public YdszJsonArray() {
        super();
    }

    /**
     * 指定初始容量的构造函数
     *
     * @param initialCapacity 初始容量
     */
    public YdszJsonArray(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * 从 List 创建 YdszJsonArray
     *
     * @param list 源 List
     */
    public YdszJsonArray(Collection<?> list) {
        super(list != null ? new ArrayList<>(list) : new ArrayList<>());
    }

    // ==================== 基本类型 getter ====================

    /**
     * 获取字符串值
     *
     * @param index 索引
     * @return 字符串值
     */
    public String getString(int index) {
        Object value = get(index);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取整数值
     *
     * @param index 索引
     * @return 整数值
     */
    public Integer getInteger(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @return int 值
     */
    public int getIntValue(int index) {
        Integer value = getInteger(index);
        return value != null ? value : 0;
    }

    /**
     * 获取长整数值
     *
     * @param index 索引
     * @return 长整数值
     */
    public Long getLong(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @return long 值
     */
    public long getLongValue(int index) {
        Long value = getLong(index);
        return value != null ? value : 0L;
    }

    /**
     * 获取双精度浮点数值
     *
     * @param index 索引
     * @return 双精度浮点数值
     */
    public Double getDouble(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @return double 值
     */
    public double getDoubleValue(int index) {
        Double value = getDouble(index);
        return value != null ? value : 0.0;
    }

    /**
     * 获取布尔值
     *
     * @param index 索引
     * @return 布尔值
     */
    public Boolean getBoolean(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @return boolean 值
     */
    public boolean getBooleanValue(int index) {
        Boolean value = getBoolean(index);
        return value != null ? value : false;
    }

    /**
     * 获取 byte 值
     *
     * @param index 索引
     * @return byte 值
     */
    public Byte getByte(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @return byte 值
     */
    public byte getByteValue(int index) {
        Byte value = getByte(index);
        return value != null ? value : 0;
    }

    /**
     * 获取 short 值
     *
     * @param index 索引
     * @return short 值
     */
    public Short getShort(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @return short 值
     */
    public short getShortValue(int index) {
        Short value = getShort(index);
        return value != null ? value : 0;
    }

    /**
     * 获取 float 值
     *
     * @param index 索引
     * @return float 值
     */
    public Float getFloat(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @return float 值
     */
    public float getFloatValue(int index) {
        Float value = getFloat(index);
        return value != null ? value : 0.0f;
    }

    /**
     * 获取 BigDecimal 值
     *
     * @param index 索引
     * @return BigDecimal 值
     */
    public BigDecimal getBigDecimal(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @return BigInteger 值
     */
    public BigInteger getBigInteger(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @return Date 对象
     */
    public Date getDate(int index) {
        return getDate(index, null);
    }

    /**
     * 获取 Date 对象（带格式）
     *
     * @param index 索引
     * @param pattern 日期格式
     * @return Date 对象
     */
    public Date getDate(int index, String pattern) {
        Object value = get(index);
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
     * @param index 索引
     * @return Instant 对象
     */
    public Instant getInstant(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @return LocalDate 对象
     */
    public LocalDate getLocalDate(int index) {
        return getLocalDate(index, null);
    }

    /**
     * 获取 LocalDate 对象（带格式）
     *
     * @param index 索引
     * @param pattern 日期格式
     * @return LocalDate 对象
     */
    public LocalDate getLocalDate(int index, String pattern) {
        Object value = get(index);
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
     * @param index 索引
     * @return LocalDateTime 对象
     */
    public LocalDateTime getLocalDateTime(int index) {
        return getLocalDateTime(index, null);
    }

    /**
     * 获取 LocalDateTime 对象（带格式）
     *
     * @param index 索引
     * @param pattern 日期格式
     * @return LocalDateTime 对象
     */
    public LocalDateTime getLocalDateTime(int index, String pattern) {
        Object value = get(index);
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
     * @param index 索引
     * @return YdszJsonObject
     */
    public YdszJsonObject getJSONObject(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @return YdszJsonArray
     */
    public YdszJsonArray getJSONArray(int index) {
        Object value = get(index);
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
     * @param index 索引
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 转换后的对象
     */
    public <T> T getObject(int index, Class<T> clazz) {
        Object value = get(index);
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

    // ==================== setter ====================

    /**
     * 添加元素
     *
     * @param element 元素
     * @return true
     */
    @Override
    public boolean add(Object element) {
        return super.add(element);
    }

    /**
     * 在指定位置添加元素
     *
     * @param index 索引
     * @param element 元素
     */
    public void add(int index, Object element) {
        super.add(index, element);
    }

    /**
     * 添加所有元素
     *
     * @param collection 集合
     * @return true
     */
    @Override
    public boolean addAll(Collection<?> collection) {
        return super.addAll(collection);
    }

    /**
     * 在指定位置添加所有元素
     *
     * @param index 索引
     * @param collection 集合
     * @return true
     */
    public boolean addAll(int index, Collection<?> collection) {
        return super.addAll(index, collection);
    }

    /**
     * 移除元素
     *
     * @param index 索引
     * @return 移除的元素
     */
    public Object remove(int index) {
        return super.remove(index);
    }

    /**
     * 设置元素
     *
     * @param index 索引
     * @param element 元素
     * @return 旧元素
     */
    public Object set(int index, Object element) {
        return super.set(index, element);
    }

    // ==================== 查询方法 ====================

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
     * @return 元素数量
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
     * 是否包含元素
     *
     * @param o 元素
     * @return 如果包含返回 true
     */
    public boolean contains(Object o) {
        return super.contains(o);
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
     * @return YdszJsonArray
     */
    public static YdszJsonArray parse(String json) {
        return YdszJson.toObject(json, YdszJsonArray.class);
    }

    /**
     * 从 List 创建
     *
     * @param list List 对象
     * @return YdszJsonArray
     */
    public static YdszJsonArray of(List<?> list) {
        if (list == null) {
            return new YdszJsonArray();
        }
        return new YdszJsonArray(list);
    }

    /**
     * 创建空的 YdszJsonArray
     *
     * @return YdszJsonArray
     */
    public static YdszJsonArray create() {
        return new YdszJsonArray();
    }

    @Override
    public String toString() {
        return toJsonString();
    }
}
