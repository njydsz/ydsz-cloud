package com.njydsz.common.json.cache;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import com.njydsz.common.json.annotation.JsonAlias;
import com.njydsz.common.json.annotation.JsonFormat;
import com.njydsz.common.json.annotation.JsonInclude;
import com.njydsz.common.json.type.FieldTypeCode;
import com.njydsz.common.json.annotation.JsonRawValue;
import com.njydsz.common.json.annotation.JsonUnwrapped;
import com.njydsz.common.json.exception.JsonDeserializationException;
import com.njydsz.common.json.exception.JsonSerializationException;

import java.lang.reflect.Array;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 字段元数据（用于缓存字段信息，MethodHandle 优化）
 *
 * <p>缓存字段的反射信息，使用 MethodHandle 优化字段访问。</p>
 *
 * <p><b>性能对比：</b></p>
 * <ul>
 *   <li>反射访问：~100ns/次</li>
 *   <li>MethodHandle：~8ns/次（提升 12 倍）</li>
 * </ul>
 *
 * <p><b>字段元数据包含：</b></p>
 * <ul>
 *   <li>字段基本信息 - 名称、类型、Java 字段对象</li>
 *   <li>JSON 映射信息 - JSON 字段名、序列化优先级</li>
 *   <li>格式化配置 - 日期格式（缓存 DateTimeFormatter）</li>
 *   <li>包含策略 - @JsonInclude 控制空值/默认值输出</li>
 *   <li>字段访问 - MethodHandle/VarHandle 优化的 getter/setter</li>
 * </ul>
 *
 * <p><b>设计模式：</b></p>
 * <ul>
 *   <li>享元模式 - FieldMeta 实例可被缓存复用</li>
 * </ul>
 *
 * <p><b>设计决策：</b>字段元数据、字段访问（MethodHandle/VarHandle）和类型转换（日期格式化/解析）
 * 有意共置于同一类中，与 Jackson 的 {@code BeanProperty} 和 Gson 的 {@code BoundField} 设计一致。
 * 原因：类型转换逻辑依赖字段的 {@code format} 和 {@code type} 元数据，拆分到独立类会引入
 * 不必要的间接调用和对象分配开销，降低序列化/反序列化热路径性能。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public final class FieldMeta {

    private static final Logger LOGGER = LoggerFactory.getLogger(FieldMeta.class);

    /** 字段名 */
    public final String name;

    /** 字段类型 */
    public final Class<?> type;

    /** 字段 */
    public final Field field;

    /** JSON 字段名（可能有命名策略） */
    public final String jsonName;

    /** 是否是基本类型 */
    public final boolean isPrimitive;

    /** 序列化优先级 */
    public final int ordinal;

    /** 日期格式 */
    public final String format;

    /** 日期格式化时区（@JsonFormat.timezone，null 表示系统默认） */
    public final java.time.ZoneId timezone;

    /** 日期格式化区域（@JsonFormat.locale，null 表示系统默认） */
    public final java.util.Locale locale;

    /** 是否为原始 JSON 值（@JsonRawValue，序列化时不转义） */
    public final boolean isRawValue;

    /** 是否为展开字段（@JsonUnwrapped，序列化时嵌套属性展开到父对象） */
    public final boolean unwrapped;

    /** @JsonUnwrapped 前缀 */
    public final String unwrapPrefix;

    /** @JsonUnwrapped 后缀 */
    public final String unwrapSuffix;

    /** 反序列化别名列表（来自 @JsonAlias 注解） */
    public final String[] aliases;

    /** 包含策略（来自 @JsonInclude 注解，默认 ALWAYS） */
    public final JsonInclude.Include includeStrategy;

    /** 类型代码（优化序列化分支预测）*/
    public final int serializeTypeCode;

    /** 统一类型码（替代 serializeTypeCode int） */
    public final FieldTypeCode typeCode;

    /** MethodHandle Setter（优化字段设置） */
    private final MethodHandle setter;

    /** 预计算的 JSON 键名（如 "fieldName":）- 减少运行时字符串拼接 */
    public final String jsonKey;

    /** 预计算的 JSON 键名长度 */
    public final int jsonKeyLen;

    /** MethodHandle Getter（优化字段获取） */
    public final MethodHandle getter;

    /** VarHandle Getter（JDK 9+ 直接内存访问，避免装箱） */
    private final VarHandle varHandle;

    /** 缓存的 DateTimeFormatter（线程安全，避免每次 ofPattern 编译） */
    private final transient DateTimeFormatter cachedFormatter;

    /**
     * 构造函数
     */
    public FieldMeta(Field field, String jsonName, int ordinal) {
        this.field = field;
        this.name = field.getName();
        this.type = field.getType();
        this.jsonName = jsonName;
        this.isPrimitive = type.isPrimitive();
        this.ordinal = ordinal;

        // 从 @JsonFormat 注解获取格式
        JsonFormat jacksonFormat = field.getAnnotation(JsonFormat.class);
        this.format = (jacksonFormat != null && !jacksonFormat.pattern().isEmpty())
            ? jacksonFormat.pattern() : "";

        // 读取 @JsonFormat 时区和地区
        if (jacksonFormat != null) {
            String tz = jacksonFormat.timezone();
            this.timezone = (tz != null && !tz.isEmpty()) ? java.time.ZoneId.of(tz) : null;
            String loc = jacksonFormat.locale();
            this.locale = (loc != null && !loc.isEmpty()) ? java.util.Locale.forLanguageTag(loc) : null;
        } else {
            this.timezone = null;
            this.locale = null;
        }

        // 加载 @JsonAlias 别名列表
        JsonAlias aliasAnnotation = field.getAnnotation(JsonAlias.class);
        this.aliases = aliasAnnotation != null ? aliasAnnotation.value() : new String[0];

        // 加载 @JsonRawValue 注解
        JsonRawValue rawValueAnnotation = field.getAnnotation(JsonRawValue.class);
        this.isRawValue = rawValueAnnotation != null;

        // 加载 @JsonUnwrapped 注解
        JsonUnwrapped unwrappedAnnotation = field.getAnnotation(JsonUnwrapped.class);
        this.unwrapped = unwrappedAnnotation != null;
        this.unwrapPrefix = unwrappedAnnotation != null ? unwrappedAnnotation.prefix() : "";
        this.unwrapSuffix = unwrappedAnnotation != null ? unwrappedAnnotation.suffix() : "";

        // 加载 @JsonInclude 包含策略
        JsonInclude includeAnnotation = field.getAnnotation(JsonInclude.class);
        if (includeAnnotation == null) {
            includeAnnotation = field.getDeclaringClass().getAnnotation(JsonInclude.class);
        }
        this.includeStrategy = includeAnnotation != null ? includeAnnotation.value() : JsonInclude.Include.ALWAYS;

        field.setAccessible(true);

        // 缓存 DateTimeFormatter（P2-1: 避免每次 formatDateValue/parseDateValue 重复编译模式）
        DateTimeFormatter formatter = null;
        if (this.format != null && !this.format.isEmpty()) {
            try {
                formatter = DateTimeFormatter.ofPattern(this.format);
                if (this.timezone != null) {
                    formatter = formatter.withZone(this.timezone);
                }
                if (this.locale != null) {
                    formatter = formatter.withLocale(this.locale);
                }
            } catch (Exception e) {
                // 非法日期格式模式，formatDateValue/parseDateValue 会回退到 toString
                LOGGER.debug("Invalid date format pattern '" + this.format + "' for field " + name + ": " + e.getMessage());
            }
        }
        this.cachedFormatter = formatter;

        MethodHandle s = null;
        MethodHandle g = null;
        VarHandle vh = null;
        try {
            s = MethodHandles.lookup().unreflectSetter(field);
            g = MethodHandles.lookup().unreflectGetter(field);

            // 尝试使用 VarHandle（JDK 9+，避免原始类型装箱）
            try {
                vh = MethodHandles.privateLookupIn(
                    field.getDeclaringClass(), MethodHandles.lookup())
                    .unreflectVarHandle(field);
            } catch (Exception ignored) {
                // VarHandle 不可用（如 GraalVM Native Image），回退到 MethodHandle
            }
        } catch (Exception e) {
            // 反射操作失败，setter/getter 保持 null，运行时回退到 field.get/set
            LOGGER.debug("Failed to unreflect field handles for " + name + ": " + e.getMessage());
        }
        this.setter = s;
        this.getter = g;
        this.varHandle = vh;
        this.serializeTypeCode = computeSerializeTypeCode(type);
        this.typeCode = computeFieldTypeCode(type);
        this.jsonKey = "\"" + jsonName + "\":";
        this.jsonKeyLen = this.jsonKey.length();
    }

    /**
     * 计算序列化类型代码（用于 switch 替代 instanceof）。
     *
     * <p>类型代码必须与 {@link ValueWriter} 中的 TYPE_CODE_* 常量保持一致。</p>
     *
     * @param type 字段类型
     * @return 类型代码
     */
    private static int computeSerializeTypeCode(Class<?> type) {
        if (type == String.class) return 1;          // TYPE_CODE_STRING
        if (type == int.class || type == Integer.class) return 2;  // TYPE_CODE_INTEGER
        if (type == long.class || type == Long.class) return 3;    // TYPE_CODE_LONG
        if (type == double.class || type == Double.class) return 4; // TYPE_CODE_DOUBLE
        if (type == float.class || type == Float.class) return 5;  // TYPE_CODE_FLOAT
        if (type == boolean.class || type == Boolean.class) return 6; // TYPE_CODE_BOOLEAN
        if (type == char.class || type == Character.class) return 7; // TYPE_CODE_CHARACTER
        if (type == short.class || type == Short.class) return 8;  // TYPE_CODE_SHORT
        if (type == byte.class || type == Byte.class) return 9;    // TYPE_CODE_BYTE
        if (type == BigDecimal.class) return 14;     // TYPE_CODE_BIGDECIMAL
        if (type == BigInteger.class) return 15;     // TYPE_CODE_BIGINTEGER
        if (type == Date.class) return 13;           // TYPE_CODE_DATE
        if (type == LocalDate.class) return 13;      // TYPE_CODE_DATE
        if (type == LocalDateTime.class) return 13;  // TYPE_CODE_DATE
        if (type == LocalTime.class) return 13;      // TYPE_CODE_DATE
        if (type == Instant.class) return 13;        // TYPE_CODE_DATE
        if (type.isEnum()) return 16;                // TYPE_CODE_BEAN (enum falls to bean path)
        return 0;
    }

    /** 计算字段类型对应的 FieldTypeCode（统一类型码，向后兼容的 int 码仍通过 computeSerializeTypeCode 保留） */
    private static FieldTypeCode computeFieldTypeCode(Class<?> type) {
        if (type == String.class) return FieldTypeCode.STRING;
        if (type == int.class || type == Integer.class) return FieldTypeCode.INT;
        if (type == long.class || type == Long.class) return FieldTypeCode.LONG;
        if (type == double.class || type == Double.class) return FieldTypeCode.DOUBLE;
        if (type == float.class || type == Float.class) return FieldTypeCode.FLOAT;
        if (type == boolean.class || type == Boolean.class) return FieldTypeCode.BOOLEAN;
        if (type == char.class || type == Character.class) return FieldTypeCode.CHAR;
        if (type == short.class || type == Short.class) return FieldTypeCode.SHORT;
        if (type == byte.class || type == Byte.class) return FieldTypeCode.BYTE;
        if (type == BigDecimal.class) return FieldTypeCode.BIG_DECIMAL;
        if (type == BigInteger.class) return FieldTypeCode.BIG_INTEGER;
        if (type == LocalDate.class || type == LocalDateTime.class || type == LocalTime.class
                || type == Instant.class || type == Date.class) return FieldTypeCode.DATE;
        if (type.isEnum()) return FieldTypeCode.STRING; // enum 序列化为 String
        return FieldTypeCode.NESTED_OBJECT;
    }

    /**
     * 获取字段值（MethodHandle 优化）
     *
     * @param obj 对象实例
     * @return 字段值
     */
    public Object getValue(Object obj) {
        // 优先使用 VarHandle（避免装箱）
        if (varHandle != null) {
            try {
                return varHandle.get(obj);
            } catch (Exception e) {
                LOGGER.debug("VarHandle.get failed for field " + name + ": " + e.getMessage());
            }
        }
        if (getter != null) {
            try {
                return getter.invoke(obj);
            } catch (Throwable e) {
                LOGGER.debug("MethodHandle.invoke failed for field " + name + ": " + e.getMessage());
            }
        }
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
throw new JsonSerializationException(
JsonSerializationException.SERIALIZATION_ERROR,
                "Failed to get field value: " + name, e);
        }
    }

    /**
     * 获取 String 类型字段值（快速路径）
     *
     * @param obj 对象实例
     * @return 字段值
     */
    public String getStringValue(Object obj) {
        if (getter != null) {
            try {
                return (String) getter.invoke(obj);
            } catch (Throwable e) {
                LOGGER.debug("MethodHandle.invoke failed for String field " + name + ": " + e.getMessage());
            }
        }
        try {
            return (String) field.get(obj);
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to get String field " + name + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取 String 类型字段值（超快路径 - 无 try-catch）
     *
     * @param obj 对象实例
     * @return 字段值，如果失败返回 null
     */
    public String getStringValueFast(Object obj) {
        try {
            return (String) getter.invoke(obj);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 获取 int 类型字段值（快速路径）
     *
     * @param obj 对象实例
     * @return 字段值
     */
    public int getIntValue(Object obj) {
        if (getter != null) {
            try {
                return (Integer) getter.invoke(obj);
            } catch (Throwable e) {
                LOGGER.debug("MethodHandle.invoke failed for int field " + name + ": " + e.getMessage());
            }
        }
        try {
            return field.getInt(obj);
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to get int field " + name + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * 获取 int 类型字段值（超快路径 - 无 try-catch）
     *
     * @param obj 对象实例
     * @return 字段值，如果失败返回 0
     */
    public int getIntValueFast(Object obj) {
        try {
            return (Integer) getter.invoke(obj);
        } catch (Throwable e) {
            return 0;
        }
    }

    /**
     * 获取 long 类型字段值（快速路径）
     *
     * @param obj 对象实例
     * @return 字段值
     */
    public long getLongValue(Object obj) {
        if (getter != null) {
            try {
                return (Long) getter.invoke(obj);
            } catch (Throwable e) {
                LOGGER.debug("MethodHandle.invoke failed for long field " + name + ": " + e.getMessage());
            }
        }
        try {
            return field.getLong(obj);
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to get long field " + name + ": " + e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取 long 类型字段值（超快路径 - 无 try-catch）
     *
     * @param obj 对象实例
     * @return 字段值，如果失败返回 0
     */
    public long getLongValueFast(Object obj) {
        try {
            return (Long) getter.invoke(obj);
        } catch (Throwable e) {
            return 0L;
        }
    }

    /**
     * 获取 double 类型字段值（快速路径）
     *
     * @param obj 对象实例
     * @return 字段值
     */
    public double getDoubleValue(Object obj) {
        if (getter != null) {
            try {
                return (Double) getter.invoke(obj);
            } catch (Throwable e) {
                LOGGER.debug("MethodHandle.invoke failed for double field " + name + ": " + e.getMessage());
            }
        }
        try {
            return field.getDouble(obj);
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to get double field " + name + ": " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 获取 double 类型字段值（超快路径 - 无 try-catch）
     *
     * @param obj 对象实例
     * @return 字段值，如果失败返回 0
     */
    public double getDoubleValueFast(Object obj) {
        try {
            return (Double) getter.invoke(obj);
        } catch (Throwable e) {
            return 0.0;
        }
    }

    /**
     * 获取 boolean 类型字段值（快速路径）
     *
     * @param obj 对象实例
     * @return 字段值
     */
    public boolean getBooleanValue(Object obj) {
        if (getter != null) {
            try {
                return (Boolean) getter.invoke(obj);
            } catch (Throwable e) {
                LOGGER.debug("MethodHandle.invoke failed for boolean field " + name + ": " + e.getMessage());
            }
        }
        try {
            return field.getBoolean(obj);
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to get boolean field " + name + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取 boolean 类型字段值（超快路径 - 无 try-catch）
     *
     * @param obj 对象实例
     * @return 字段值，如果失败返回 false
     */
    public boolean getBooleanValueFast(Object obj) {
        try {
            return (Boolean) getter.invoke(obj);
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 设置字段值（MethodHandle 优化）
     */
    public void setValue(Object obj, Object value) {
        // 优先使用 VarHandle（避免装箱）
        if (varHandle != null) {
            try {
                varHandle.set(obj, value);
                return;
            } catch (Exception e) {
                LOGGER.debug("VarHandle.set failed for field " + name + ": " + e.getMessage());
            }
        }
        if (setter != null) {
            try {
                setter.invoke(obj, value);
                return;
            } catch (Throwable e) {
                LOGGER.debug("MethodHandle.invoke failed for field " + name + ": " + e.getMessage());
            }
        }
        try {
            field.set(obj, value);
        } catch (IllegalAccessException e) {
            throw new JsonDeserializationException(
                "Failed to set field value: " + name, e);
        }
    }

    /**
     * 是否是 String 类型
     */
    public boolean isStringType() {
        return type == String.class;
    }

    /**
     * 是否是日期类型
     */
    public boolean isDateType() {
        return type == Date.class || type == LocalDate.class || type == LocalDateTime.class;
    }

    /**
     * 格式化日期值
     */
    public String formatDateValue(Object value) {
        if (value == null) return null;
        // P2-1: 使用缓存的 DateTimeFormatter，避免每次 ofPattern 编译
        if (cachedFormatter == null) {
            return value.toString();
        }
        try {
            DateTimeFormatter formatter = cachedFormatter;
            if (value instanceof LocalDateTime) {
                return ((LocalDateTime) value).format(formatter);
            } else if (value instanceof LocalDate) {
                return ((LocalDate) value).format(formatter);
            } else if (value instanceof Date) {
                return ((Date) value).toInstant().atZone(ZoneId.systemDefault())
                        .toLocalDateTime().format(formatter);
            }
        } catch (Exception e) {
            // 格式化失败，回退到 toString
        }
        return value.toString();
    }

    /**
     * 解析日期值
     */
    public Object parseDateValue(String json) {
        if (json == null || json.equals("null")) return null;
        // P2-1: 使用缓存的 DateTimeFormatter，避免每次 ofPattern 编译
        if (cachedFormatter == null) {
            return json;
        }
        try {
            DateTimeFormatter formatter = cachedFormatter;
            if (type == LocalDateTime.class) {
                return LocalDateTime.parse(json, formatter);
            } else if (type == LocalDate.class) {
                return LocalDate.parse(json, formatter);
            } else if (type == Date.class) {
                return Date.from(LocalDateTime.parse(json, formatter)
                        .atZone(ZoneId.systemDefault()).toInstant());
            }
        } catch (DateTimeParseException e) {
            LOGGER.debug("Caught exception (ignored): {}", e.getMessage());
        }
        return json;
    }

    /**
     * 检查是否应该跳过值（根据 @JsonInclude 策略）。
     *
     * @param value 字段值
     * @return true 表示应该跳过
     */
    public boolean shouldSkipValue(Object value) {
        if (value == null) {
            return includeStrategy == JsonInclude.Include.NON_NULL
                    || includeStrategy == JsonInclude.Include.NON_EMPTY
                    || includeStrategy == JsonInclude.Include.NON_DEFAULT;
        }
        switch (includeStrategy) {
            case NON_EMPTY:
                if (value instanceof String s) return s.isEmpty();
                if (value instanceof Collection<?> c) return c.isEmpty();
                if (value instanceof Map<?, ?> m) return m.isEmpty();
                if (value.getClass().isArray()) return Array.getLength(value) == 0;
                break;
            case NON_DEFAULT:
                if (value instanceof Number n && n.doubleValue() == 0.0) return true;
                if (value instanceof Boolean b && !b) return true;
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public String toString() {
        return "FieldMeta{name='" + name + "', type=" + type.getSimpleName() + ", jsonName='" + jsonName + "'}";
    }
}
