package com.njydsz.common.json.cache;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.annotation.JsonFormat;
import com.njydsz.common.json.annotation.JsonInclude;
import com.njydsz.common.json.annotation.JsonProperty;
import com.njydsz.common.json.annotation.JsonView;
import com.njydsz.common.json.exception.JsonDeserializationException;
import com.njydsz.common.json.exception.JsonSerializationException;
import com.njydsz.common.json.type.FieldTypeCode;

/**
 * 字段元数据（用于缓存字段信息，MethodHandle 优化）
 *
 * <p>缓存字段的反射信息，使用 MethodHandle 优化字段访问。
 *
 * <p><b>性能对比：</b>
 *
 * <ul>
 *   <li>反射访问：~100ns/次
 *   <li>MethodHandle：~8ns/次（提升 12 倍）
 * </ul>
 *
 * <p><b>字段元数据包含：</b>
 *
 * <ul>
 *   <li>字段基本信息 - 名称、类型、Java 字段对象
 *   <li>JSON 映射信息 - JSON 字段名、序列化优先级
 *   <li>格式化配置 - 日期格式（缓存 DateTimeFormatter）
 *   <li>包含策略 - @JsonInclude 控制空值/默认值输出
 *   <li>字段访问 - MethodHandle/VarHandle 优化的 getter/setter
 * </ul>
 *
 * <p><b>设计模式：</b>
 *
 * <ul>
 *   <li>享元模式 - FieldMeta 实例可被缓存复用
 * </ul>
 *
 * <p><b>设计决策：</b>字段元数据、字段访问（MethodHandle/VarHandle）和类型转换（日期格式化/解析） 有意共置于同一类中，与 Jackson 的 {@code
 * BeanProperty} 和 Gson 的 {@code BoundField} 设计一致。 原因：类型转换逻辑依赖字段的 {@code format} 和 {@code type}
 * 元数据，拆分到独立类会引入 不必要的间接调用和对象分配开销，降低序列化/反序列化热路径性能。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SuppressWarnings("deprecation")
public final class FieldMeta {

  private static final Logger LOGGER = LoggerFactory.getLogger(FieldMeta.class);

  /** 序列化类型码：默认/未知类型（与 ValueWriter 保持一致） */
  private static final int TYPE_CODE_DEFAULT = 0;

  /** 序列化类型码：String（与 ValueWriter.TYPE_CODE_STRING 保持一致） */
  private static final int TYPE_CODE_STRING = 1;

  /** 序列化类型码：Integer/int（与 ValueWriter.TYPE_CODE_INTEGER 保持一致） */
  private static final int TYPE_CODE_INTEGER = 2;

  /** 序列化类型码：Long/long（与 ValueWriter.TYPE_CODE_LONG 保持一致） */
  private static final int TYPE_CODE_LONG = 3;

  /** 序列化类型码：Double/double（与 ValueWriter.TYPE_CODE_DOUBLE 保持一致） */
  private static final int TYPE_CODE_DOUBLE = 4;

  /** 序列化类型码：Float/float（与 ValueWriter.TYPE_CODE_FLOAT 保持一致） */
  private static final int TYPE_CODE_FLOAT = 5;

  /** 序列化类型码：Boolean/boolean（与 ValueWriter.TYPE_CODE_BOOLEAN 保持一致） */
  private static final int TYPE_CODE_BOOLEAN = 6;

  /** 序列化类型码：Character/char（与 ValueWriter.TYPE_CODE_CHARACTER 保持一致） */
  private static final int TYPE_CODE_CHARACTER = 7;

  /** 序列化类型码：Short/short（与 ValueWriter.TYPE_CODE_SHORT 保持一致） */
  private static final int TYPE_CODE_SHORT = 8;

  /** 序列化类型码：Byte/byte（与 ValueWriter.TYPE_CODE_BYTE 保持一致） */
  private static final int TYPE_CODE_BYTE = 9;

  /** 序列化类型码：日期类型（与 ValueWriter.TYPE_CODE_DATE 保持一致） */
  private static final int TYPE_CODE_DATE = 13;

  /** 序列化类型码：BigDecimal（与 ValueWriter.TYPE_CODE_BIGDECIMAL 保持一致） */
  private static final int TYPE_CODE_BIGDECIMAL = 14;

  /** 序列化类型码：BigInteger（与 ValueWriter.TYPE_CODE_BIGINTEGER 保持一致） */
  private static final int TYPE_CODE_BIGINTEGER = 15;

  /** 序列化类型码：Bean/Enum（与 ValueWriter.TYPE_CODE_BEAN 保持一致） */
  private static final int TYPE_CODE_BEAN = 16;

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
  public final ZoneId timezone;

  /** 日期格式化区域（@JsonFormat.locale，null 表示系统默认） */
  public final Locale locale;

  /** 包含策略（来自 @JsonInclude 注解，默认 ALWAYS） */
  public final JsonInclude.Include includeStrategy;

  /**
   * 是否参与序列化（来自 @JsonProperty.access，P0 修复）。
   *
   * <p>{@code Access.WRITE_ONLY}（如密码字段）时为 false——该字段只接受反序列化写入，
   * 序列化时不输出，对标 Jackson 语义。此前注解声明了 access 但全模块零消费，
   * WRITE_ONLY 标注的敏感字段会被照常序列化输出（安全缺陷）。
   */
  public final boolean serializable;

  /**
   * 字段视图（P1.5 方法级注解支持）。
   *
   * <p>来自字段级或 getter 方法级 {@code @JsonView} 注解（字段级优先），空数组表示无视图标注。
   * 对齐 Jackson 的注解放置习惯：{@code @JsonView} 既可标在字段上也可标在 getter 上。
   */
  public final Class<?>[] views;

  /** 类型代码（优化序列化分支预测） */
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

  /** 序列化类型码查找表（避免 computeSerializeTypeCode 的 if-else 链） */
  private static final Map<Class<?>, Integer> SERIALIZE_TYPE_CODE_MAP = new HashMap<>();

  /** 字段类型码查找表（避免 computeFieldTypeCode 的 if-else 链） */
  private static final Map<Class<?>, FieldTypeCode> FIELD_TYPE_CODE_MAP = new HashMap<>(16);

  static {
    SERIALIZE_TYPE_CODE_MAP.put(String.class, TYPE_CODE_STRING);
    SERIALIZE_TYPE_CODE_MAP.put(int.class, TYPE_CODE_INTEGER);
    SERIALIZE_TYPE_CODE_MAP.put(Integer.class, TYPE_CODE_INTEGER);
    SERIALIZE_TYPE_CODE_MAP.put(long.class, TYPE_CODE_LONG);
    SERIALIZE_TYPE_CODE_MAP.put(Long.class, TYPE_CODE_LONG);
    SERIALIZE_TYPE_CODE_MAP.put(double.class, TYPE_CODE_DOUBLE);
    SERIALIZE_TYPE_CODE_MAP.put(Double.class, TYPE_CODE_DOUBLE);
    SERIALIZE_TYPE_CODE_MAP.put(float.class, TYPE_CODE_FLOAT);
    SERIALIZE_TYPE_CODE_MAP.put(Float.class, TYPE_CODE_FLOAT);
    SERIALIZE_TYPE_CODE_MAP.put(boolean.class, TYPE_CODE_BOOLEAN);
    SERIALIZE_TYPE_CODE_MAP.put(Boolean.class, TYPE_CODE_BOOLEAN);
    SERIALIZE_TYPE_CODE_MAP.put(char.class, TYPE_CODE_CHARACTER);
    SERIALIZE_TYPE_CODE_MAP.put(Character.class, TYPE_CODE_CHARACTER);
    SERIALIZE_TYPE_CODE_MAP.put(short.class, TYPE_CODE_SHORT);
    SERIALIZE_TYPE_CODE_MAP.put(Short.class, TYPE_CODE_SHORT);
    SERIALIZE_TYPE_CODE_MAP.put(byte.class, TYPE_CODE_BYTE);
    SERIALIZE_TYPE_CODE_MAP.put(Byte.class, TYPE_CODE_BYTE);
    SERIALIZE_TYPE_CODE_MAP.put(BigDecimal.class, TYPE_CODE_BIGDECIMAL);
    SERIALIZE_TYPE_CODE_MAP.put(BigInteger.class, TYPE_CODE_BIGINTEGER);
    SERIALIZE_TYPE_CODE_MAP.put(Date.class, TYPE_CODE_DATE);
    SERIALIZE_TYPE_CODE_MAP.put(LocalDate.class, TYPE_CODE_DATE);
    SERIALIZE_TYPE_CODE_MAP.put(LocalDateTime.class, TYPE_CODE_DATE);
    SERIALIZE_TYPE_CODE_MAP.put(LocalTime.class, TYPE_CODE_DATE);
    SERIALIZE_TYPE_CODE_MAP.put(Instant.class, TYPE_CODE_DATE);

    FIELD_TYPE_CODE_MAP.put(String.class, FieldTypeCode.STRING);
    FIELD_TYPE_CODE_MAP.put(int.class, FieldTypeCode.INT);
    FIELD_TYPE_CODE_MAP.put(Integer.class, FieldTypeCode.INT);
    FIELD_TYPE_CODE_MAP.put(long.class, FieldTypeCode.LONG);
    FIELD_TYPE_CODE_MAP.put(Long.class, FieldTypeCode.LONG);
    FIELD_TYPE_CODE_MAP.put(double.class, FieldTypeCode.DOUBLE);
    FIELD_TYPE_CODE_MAP.put(Double.class, FieldTypeCode.DOUBLE);
    FIELD_TYPE_CODE_MAP.put(float.class, FieldTypeCode.FLOAT);
    FIELD_TYPE_CODE_MAP.put(Float.class, FieldTypeCode.FLOAT);
    FIELD_TYPE_CODE_MAP.put(boolean.class, FieldTypeCode.BOOLEAN);
    FIELD_TYPE_CODE_MAP.put(Boolean.class, FieldTypeCode.BOOLEAN);
    FIELD_TYPE_CODE_MAP.put(char.class, FieldTypeCode.CHAR);
    FIELD_TYPE_CODE_MAP.put(Character.class, FieldTypeCode.CHAR);
    FIELD_TYPE_CODE_MAP.put(short.class, FieldTypeCode.SHORT);
    FIELD_TYPE_CODE_MAP.put(Short.class, FieldTypeCode.SHORT);
    FIELD_TYPE_CODE_MAP.put(byte.class, FieldTypeCode.BYTE);
    FIELD_TYPE_CODE_MAP.put(Byte.class, FieldTypeCode.BYTE);
    FIELD_TYPE_CODE_MAP.put(BigDecimal.class, FieldTypeCode.BIG_DECIMAL);
    FIELD_TYPE_CODE_MAP.put(BigInteger.class, FieldTypeCode.BIG_INTEGER);
    FIELD_TYPE_CODE_MAP.put(LocalDate.class, FieldTypeCode.DATE);
    FIELD_TYPE_CODE_MAP.put(LocalDateTime.class, FieldTypeCode.DATE);
    FIELD_TYPE_CODE_MAP.put(LocalTime.class, FieldTypeCode.DATE);
    FIELD_TYPE_CODE_MAP.put(Instant.class, FieldTypeCode.DATE);
    FIELD_TYPE_CODE_MAP.put(Date.class, FieldTypeCode.DATE);
  }

  /**
   * 构造函数
   *
   * @param field Java 反射字段对象
   * @param jsonName JSON 字段名（经命名策略处理后的名称）
   * @param ordinal 序列化优先级
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
    this.format =
        (jacksonFormat != null && !jacksonFormat.pattern().isEmpty())
            ? jacksonFormat.pattern()
            : "";

    // 读取 @JsonFormat 时区和地区
    if (jacksonFormat != null) {
      String tz = jacksonFormat.timezone();
      this.timezone = (tz != null && !tz.isEmpty()) ? ZoneId.of(tz) : null;
      String loc = jacksonFormat.locale();
      this.locale = (loc != null && !loc.isEmpty()) ? Locale.forLanguageTag(loc) : null;
    } else {
      this.timezone = null;
      this.locale = null;
    }

    // 加载 @JsonInclude 包含策略
    this.includeStrategy = resolveIncludeStrategy(field);

    // 加载 @JsonProperty.access 访问模式（P0 修复：WRITE_ONLY 字段不参与序列化）
    JsonProperty accessProperty = field.getAnnotation(JsonProperty.class);
    this.serializable =
        !(accessProperty != null
            && accessProperty.access() == JsonProperty.Access.WRITE_ONLY);

    // 加载 @JsonView（P1.5 方法级支持：字段级优先，回退 getter 方法级）
    this.views = resolveViews(field);

    field.setAccessible(true);

    // 缓存 DateTimeFormatter（P2-1: 避免每次 formatDateValue/parseDateValue 重复编译模式）
    this.cachedFormatter = createCachedFormatter(this.format, this.timezone, this.locale, name);

    // 初始化 MethodHandle / VarHandle
    MethodHandleBundle bundle = createMethodHandleBundle(field, name);
    this.setter = bundle.setter;
    this.getter = bundle.getter;
    this.varHandle = bundle.varHandle;
    this.serializeTypeCode = computeSerializeTypeCode(type);
    this.typeCode = computeFieldTypeCode(type);
    this.jsonKey = "\"" + jsonName + "\":";
    this.jsonKeyLen = this.jsonKey.length();
  }

  /**
   * 解析 @JsonInclude 包含策略。
   *
   * <p>优先使用字段级注解，若不存在则回退到类级注解，均未标注时默认 ALWAYS。
   *
   * @param field Java 反射字段对象
   * @return 包含策略枚举值
   */
  private static JsonInclude.Include resolveIncludeStrategy(Field field) {
    JsonInclude includeAnnotation = field.getAnnotation(JsonInclude.class);
    if (includeAnnotation == null) {
      includeAnnotation = field.getDeclaringClass().getAnnotation(JsonInclude.class);
    }
    return includeAnnotation != null ? includeAnnotation.value() : JsonInclude.Include.ALWAYS;
  }

  /**
   * 解析字段视图（P1.5 方法级注解支持）。
   *
   * <p>字段级 {@code @JsonView} 优先；字段未标注时回退到 getter 方法级标注
   * （命名规则：非 boolean 字段 {@code getX}，boolean 字段 {@code isX}，另尝试 {@code isX}）。
   * 两处均未标注返回空数组。
   *
   * @param field Java 反射字段对象
   * @return 视图类数组（无标注时为空数组，非 null）
   */
  private static Class<?>[] resolveViews(Field field) {
    JsonView annotation = field.getAnnotation(JsonView.class);
    if (annotation != null) {
      return annotation.value();
    }
    // 回退 getter 方法级标注
    Method getter = findGetterMethod(field);
    if (getter != null) {
      JsonView methodAnnotation = getter.getAnnotation(JsonView.class);
      if (methodAnnotation != null) {
        return methodAnnotation.value();
      }
    }
    return new Class<?>[0];
  }

  /**
   * 按字段名推断并查找 getter 方法（{@code getX} / boolean 字段 {@code isX}）。
   *
   * @param field Java 反射字段对象
   * @return getter 方法，不存在返回 null
   */
  private static Method findGetterMethod(Field field) {
    String fieldName = field.getName();
    String capitalized =
        Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    Class<?> declaringClass = field.getDeclaringClass();
    String[] candidates =
        field.getType() == boolean.class
            ? new String[] {"is" + capitalized, "get" + capitalized}
            : new String[] {"get" + capitalized};
    for (String candidate : candidates) {
      try {
        return declaringClass.getMethod(candidate);
      } catch (NoSuchMethodException e) {
        // 继续尝试下一个候选名
      }
    }
    return null;
  }

  /**
   * 创建缓存的 DateTimeFormatter。
   *
   * <p>根据格式模式、时区和区域配置创建线程安全的 DateTimeFormatter 实例。 若格式模式为空或非法则返回 null，运行时回退到 toString。
   *
   * @param format 日期格式模式（为空时返回 null）
   * @param timezone 时区（可为 null）
   * @param locale 区域（可为 null）
   * @param fieldName 字段名（仅用于日志）
   * @return 配置好的 DateTimeFormatter，或 null
   */
  private static DateTimeFormatter createCachedFormatter(
      String format, ZoneId timezone, Locale locale, String fieldName) {
    if (format == null || format.isEmpty()) {
      return null;
    }
    try {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
      if (timezone != null) {
        formatter = formatter.withZone(timezone);
      }
      if (locale != null) {
        formatter = formatter.withLocale(locale);
      }
      return formatter;
    } catch (Exception e) {
      // 非法日期格式模式，formatDateValue/parseDateValue 会回退到 toString
      LOGGER.debug(
          "Invalid date format pattern '{}' for field {}: {}", format, fieldName, e.getMessage());
      return null;
    }
  }

  /**
   * 创建 MethodHandle/VarHandle 访问器。
   *
   * <p>优先使用 VarHandle（JDK 9+，避免原始类型装箱）， VarHandle 不可用时回退到 MethodHandle，均失败时返回 null。
   *
   * @param field Java 反射字段对象
   * @param fieldName 字段名（仅用于日志）
   * @return 包含 setter、getter、varHandle 的不可变载体
   */
  private static MethodHandleBundle createMethodHandleBundle(Field field, String fieldName) {
    MethodHandle s = null;
    MethodHandle g = null;
    VarHandle vh = null;
    try {
      s = MethodHandles.lookup().unreflectSetter(field);
      g = MethodHandles.lookup().unreflectGetter(field);

      // 尝试使用 VarHandle（JDK 9+，避免原始类型装箱）
      try {
        vh =
            MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup())
                .unreflectVarHandle(field);
      } catch (Exception ignored) {
        // VarHandle 不可用（如 GraalVM Native Image），回退到 MethodHandle
      }
    } catch (Exception e) {
      // 反射操作失败，setter/getter 保持 null，运行时回退到 field.get/set
      LOGGER.debug("Failed to unreflect field handles for {}: {}", fieldName, e.getMessage());
    }
    return new MethodHandleBundle(s, g, vh);
  }

  /** MethodHandle/VarHandle 的不可变载体（用于从 createMethodHandleBundle 返回三个句柄）。 */
  private static final class MethodHandleBundle {
    final MethodHandle setter;
    final MethodHandle getter;
    final VarHandle varHandle;

    MethodHandleBundle(MethodHandle setter, MethodHandle getter, VarHandle varHandle) {
      this.setter = setter;
      this.getter = getter;
      this.varHandle = varHandle;
    }
  }

  /**
   * 计算序列化类型代码（用于 switch 替代 instanceof）。
   *
   * <p>类型代码必须与 {@link ValueWriter} 中的 TYPE_CODE_* 常量保持一致。
   *
   * @param type 字段类型
   * @return 类型代码
   */
  private static int computeSerializeTypeCode(Class<?> type) {
    Integer code = SERIALIZE_TYPE_CODE_MAP.get(type);
    if (code != null) {
      return code;
    }
    if (type.isEnum()) {
      return TYPE_CODE_BEAN;
    }
    return TYPE_CODE_DEFAULT;
  }

  /**
   * 计算字段类型对应的 FieldTypeCode（统一类型码，向后兼容的 int 码仍通过 computeSerializeTypeCode 保留）
   *
   * @param type 字段类型
   * @return 对应的 FieldTypeCode 枚举值
   */
  private static FieldTypeCode computeFieldTypeCode(Class<?> type) {
    FieldTypeCode code = FIELD_TYPE_CODE_MAP.get(type);
    if (code != null) {
      return code;
    }
    if (type.isEnum()) {
      return FieldTypeCode.STRING;
    }
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
        LOGGER.debug("VarHandle.get failed for field {}: {}", name, e.getMessage());
      }
    }
    if (getter != null) {
      try {
        return getter.invoke(obj);
      } catch (Throwable e) {
        LOGGER.debug("MethodHandle.invoke failed for field {}: {}", name, e.getMessage());
      }
    }
    try {
      return field.get(obj);
    } catch (IllegalAccessException e) {
      throw new JsonSerializationException(
          JsonSerializationException.SERIALIZATION_ERROR, "Failed to get field value: " + name, e);
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
        LOGGER.debug("MethodHandle.invoke failed for String field {}: {}", name, e.getMessage());
      }
    }
    try {
      return (String) field.get(obj);
    } catch (IllegalAccessException e) {
      LOGGER.warn("Failed to get String field {}: {}", name, e.getMessage());
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
        LOGGER.debug("MethodHandle.invoke failed for int field {}: {}", name, e.getMessage());
      }
    }
    try {
      return field.getInt(obj);
    } catch (IllegalAccessException e) {
      LOGGER.warn("Failed to get int field {}: {}", name, e.getMessage());
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
        LOGGER.debug("MethodHandle.invoke failed for long field {}: {}", name, e.getMessage());
      }
    }
    try {
      return field.getLong(obj);
    } catch (IllegalAccessException e) {
      LOGGER.warn("Failed to get long field {}: {}", name, e.getMessage());
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
        LOGGER.debug("MethodHandle.invoke failed for double field {}: {}", name, e.getMessage());
      }
    }
    try {
      return field.getDouble(obj);
    } catch (IllegalAccessException e) {
      LOGGER.warn("Failed to get double field {}: {}", name, e.getMessage());
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
        LOGGER.debug("MethodHandle.invoke failed for boolean field {}: {}", name, e.getMessage());
      }
    }
    try {
      return field.getBoolean(obj);
    } catch (IllegalAccessException e) {
      LOGGER.warn("Failed to get boolean field {}: {}", name, e.getMessage());
      return false;
    }
  }

  /**
   * 获取 boolean 类型字段值（超快路径 - 无 try-catch）
   *
   * @param obj 目标对象实例
   * @return 字段值，若获取失败返回 false
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
   *
   * @param obj 目标对象实例
   * @param value 要设置的字段值
   */
  public void setValue(Object obj, Object value) {
    // 优先使用 VarHandle（避免装箱）
    if (varHandle != null) {
      try {
        varHandle.set(obj, value);
        return;
      } catch (Exception e) {
        LOGGER.debug("VarHandle.set failed for field {}: {}", name, e.getMessage());
      }
    }
    if (setter != null) {
      try {
        setter.invoke(obj, value);
        return;
      } catch (Throwable e) {
        LOGGER.debug("MethodHandle.invoke failed for field {}: {}", name, e.getMessage());
      }
    }
    try {
      field.set(obj, value);
    } catch (IllegalAccessException e) {
      throw new JsonDeserializationException("Failed to set field value: " + name, e);
    }
  }

  /**
   * 是否是 String 类型
   *
   * @return 若字段类型为 String 返回 true，否则返回 false
   */
  public boolean isStringType() {
    return type == String.class;
  }

  /**
   * 是否是日期类型
   *
   * @return 若字段类型为 Date、LocalDate 或 LocalDateTime 返回 true，否则返回 false
   */
  public boolean isDateType() {
    return type == Date.class || type == LocalDate.class || type == LocalDateTime.class;
  }

  /**
   * 格式化日期值
   *
   * @param value 日期字段值
   * @return 格式化后的日期字符串，若值为 null 返回 null
   */
  public String formatDateValue(Object value) {
    if (value == null) {
      return null;
    }
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
        return ((Date) value)
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(formatter);
      }
    } catch (Exception e) {
      // 格式化失败，回退到 toString
    }
    return value.toString();
  }

  /**
   * 解析日期值
   *
   * @param json JSON 字符串
   * @return 解析后的日期对象，若解析失败返回原始字符串
   */
  public Object parseDateValue(String json) {
    if (json == null || json.equals("null")) {
      return null;
    }
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
        return Date.from(
            LocalDateTime.parse(json, formatter).atZone(ZoneId.systemDefault()).toInstant());
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
      return isNullSkippingStrategy();
    }
    if (includeStrategy == JsonInclude.Include.NON_EMPTY) {
      return isEmptyValue(value);
    }
    if (includeStrategy == JsonInclude.Include.NON_DEFAULT) {
      return isDefaultValue(value);
    }
    return false;
  }

  /**
   * 判断当前策略是否在值为 null 时跳过。
   *
   * @return 若 null 值应被跳过返回 true
   */
  private boolean isNullSkippingStrategy() {
    return includeStrategy == JsonInclude.Include.NON_NULL
        || includeStrategy == JsonInclude.Include.NON_EMPTY
        || includeStrategy == JsonInclude.Include.NON_DEFAULT;
  }

  /**
   * 判断值是否为空（String/Collection/Map/Array 的空判断）。
   *
   * @param value 字段值（非 null）
   * @return 若值为空返回 true
   */
  private boolean isEmptyValue(Object value) {
    if (value instanceof String s) {
      return s.isEmpty();
    }
    if (value instanceof Collection<?> c) {
      return c.isEmpty();
    }
    if (value instanceof Map<?, ?> m) {
      return m.isEmpty();
    }
    if (value.getClass().isArray()) {
      return Array.getLength(value) == 0;
    }
    return false;
  }

  /**
   * 判断值是否为默认值（Number 为 0，Boolean 为 false）。
   *
   * @param value 字段值（非 null）
   * @return 若值为默认值返回 true
   */
  private boolean isDefaultValue(Object value) {
    if (value instanceof Number n && n.doubleValue() == 0.0) {
      return true;
    }
    if (value instanceof Boolean b && !b) {
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return "FieldMeta{name='"
        + name
        + "', type="
        + type.getSimpleName()
        + ", jsonName='"
        + jsonName
        + "'}";
  }
}
