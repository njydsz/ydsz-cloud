package com.njydsz.common.json.provider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.exception.JsonDeserializationException;
import com.njydsz.common.json.exception.JsonException;
import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.reader.BeanReader;
import com.njydsz.common.json.reader.JSONReader;
import com.njydsz.common.json.util.JsonTypeUtils;

/**
 * Bean 反序列化策略引擎。
 *
 * <p>负责 Bean 对象的反序列化策略选择与执行，内部实现了多级降级的反序列化路径， 按性能从高到低依次尝试：
 *
 * <h3>反序列化路径（优先级从高到低）</h3>
 *
 * <ol>
 *   <li><b>BeanReader 路径</b>：针对简单 Bean（字段全为基本类型）的轻量级反射读取
 *   <li><b>@JsonCreator 路径</b>：通过注解标记的构造函数创建实例
 *   <li><b>Builder 模式路径</b>：自动检测内部 Builder
 *   <li><b>降级路径</b>：解析为 Map 或 List 返回
 * </ol>
 *
 * <h3>列表反序列化</h3>
 *
 * <p>列表场景通过预估容量和跳过异常元素保证吞吐量。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see BeanReader
 * @see CreatorResolver
 * @see BuilderResolver
 */
@SuppressWarnings("deprecation") // @SuppressWarnings 保留原因：兼容旧版 java.util.Date API，BeanReader/FieldMeta 等内部使用 Date 已废弃方法，框架需保持兼容
final class BeanDeserializerEngine {
  private static final Logger LOG = LoggerFactory.getLogger(BeanDeserializerEngine.class);

  private BeanDeserializerEngine() {
    throw new UnsupportedOperationException();
  }

  /**
   * 零拷贝 Bean 反序列化（返回 Object）。
   *
   * <p>委托给 {@link #deserializeBeanZeroCopy(String, Class)} 的便捷方法。
   *
   * @param json JSON 字符串
   * @param clazz 目标类
   * @return 反序列化后的实例
   */
  static Object deserializeBeanZeroCopyAsObject(String json, Class<?> clazz) {
    return deserializeBeanZeroCopy(json, clazz);
  }

  /**
   * 零拷贝 Bean 反序列化（泛型版）。
   *
   * <p>按多级降级策略依次尝试 BeanReader → Creator → Builder → Map 降级。 每条路径失败后自动回退到下一条，确保最终能返回结果。
   *
   * @param json JSON 字符串
   * @param clazz 目标类
   * @param <T> 目标类型
   * @return 反序列化后的实例
   */
  static <T> T deserializeBeanZeroCopy(String json, Class<T> clazz) {
    // Record 类反序列化路径：使用 canonical constructor
    if (clazz.isRecord()) {
      return deserializeRecord(json, clazz);
    }

    // BeanReader 路径：对所有非容器、非接口、非数组的 Bean 类型使用
    // BeanReader 已支持 short/byte/char/Date/LocalDateTime/LocalDate/枚举/嵌套 Bean/Collection/Map
    String trimmed = json.strip();
    if (trimmed.startsWith("{")
        && !clazz.isAssignableFrom(List.class)
        && !clazz.isAssignableFrom(Map.class)
        && !clazz.isArray()
        && !clazz.isInterface()) {
      try {
        JSONReader reader = new JSONReader(json);
        BeanReader<T> beanReader = BeanReader.getOrCreate(clazz);
        return beanReader.readObject(reader);
      } catch (Exception e) {
        LOG.warn(
            "BeanReader deserialization failed for {}, falling back to creator/builder",
            clazz.getName(),
            e);
      }
    }

    // 原有逻辑：@JsonCreator、Builder 模式支持
    Constructor<?> creatorConstructor = CreatorResolver.findCreatorConstructor(clazz);

    if (creatorConstructor != null) {
      return clazz.cast(CreatorResolver.deserializeWithCreator(json, creatorConstructor));
    }

    Class<?> innerBuilderClass = BuilderResolver.findInnerBuilderClass(clazz);
    if (innerBuilderClass != null) {
      return BuilderResolver.deserializeWithInnerBuilder(json, clazz, innerBuilderClass);
    }

    // 最终降级：解析为 Map / List 后转换
    try {
      if (trimmed.startsWith("[")) {
        return clazz.cast(JsonParserUtil.parseArray(json));
      } else {
        return clazz.cast(JsonParserUtil.parseObject(json));
      }
    } catch (ClassCastException e) {
      throw new JsonException("Failed to deserialize " + clazz.getName(), e);
    }
  }

  /**
   * 快速反序列化 Bean 列表。
   *
   * <p>先整体解析为原始列表，再逐个元素按 Bean 反序列化路径转换。
   *
   * @param json JSON 数组字符串
   * @param elementClass 列表元素类型
   * @param <E> 元素类型
   * @return 反序列化后的列表
   */
  static <E> List<E> deserializeBeanListFast(String json, Class<E> elementClass) {
    // Record 类列表反序列化：逐个使用 canonical constructor
    if (elementClass.isRecord()) {
      List<Object> rawList = JsonParserUtil.parseArray(json);
      List<E> result = new ArrayList<>(rawList.size());
      for (Object item : rawList) {
        if (item == null) {
          result.add(null);
        } else {
          result.add(deserializeRecord(SerializationProvider.serialize(item), elementClass));
        }
      }
      return result;
    }

    // 非容器类型的元素逐个走 Bean 反序列化路径
    List<Object> rawItems;
    try {
      rawItems = JsonParserUtil.parseArray(json);
    } catch (Exception e) {
      throw new JsonException("Failed to parse JSON array: " + e.getMessage(), e);
    }
    List<E> result = new ArrayList<>(rawItems.size());
    for (Object item : rawItems) {
      if (item == null) {
        result.add(null);
      } else {
        String elementJson = SerializationProvider.serialize(item);
        // P1 能力补齐：多态元素按 JSON 内类型属性解析具体子类型
        // （原先直接按 elementClass 反序列化，抽象基类/多态列表必然 ClassCastException）
        Class<?> effectiveType = PolymorphicTypeResolver.resolveType(elementJson, elementClass);
        @SuppressWarnings("unchecked") // 反射构建实例，泛型类型由运行时解析
        E element = (E) deserializeBeanZeroCopy(elementJson, effectiveType);
        result.add(element);
      }
    }
    return result;
  }

  /**
   * 零拷贝解析 JSON 数组为 Object 列表。
   *
   * <p>委托给 {@link JsonParserUtil#parseArray}。
   *
   * @param json JSON 数组字符串
   * @param elementClass 元素类型（保留参数以兼容调用方签名）
   * @return 解析后的列表
   */
  static List<Object> deserializeArrayZeroCopy(String json, Class<?> elementClass) {
    try {
      return JsonParserUtil.parseArray(json);
    } catch (Exception e) {
      LOG.warn(
          "Array deserialization failed for {}, falling back to JsonParserUtil",
          elementClass.getName(),
          e);
      return JsonParserUtil.parseArray(json);
    }
  }

  /**
   * 反序列化 Record 类。
   *
   * <p>Record 类不可变，使用 canonical constructor 创建实例。 先解析 JSON 为 Map，再按组件顺序提取值并调用 canonical
   * constructor。
   *
   * @param json JSON 字符串
   * @param clazz Record 类
   * @param <T> 目标类型
   * @return 反序列化后的 Record 实例
   */
  static <T> T deserializeRecord(String json, Class<T> clazz) {
    Map<String, Object> map = JsonParserUtil.parseObject(json);
    RecordComponent[] components = clazz.getRecordComponents();
    Class<?>[] paramTypes = new Class<?>[components.length];
    Object[] paramValues = new Object[components.length];

    for (int i = 0; i < components.length; i++) {
      paramTypes[i] = components[i].getType();
      String jsonName = components[i].getName();
      Object value = map.get(jsonName);
      paramValues[i] = convertRecordValue(value, paramTypes[i]);
    }

    try {
      Constructor<T> canonical = clazz.getDeclaredConstructor(paramTypes);
      canonical.setAccessible(true);
      return canonical.newInstance(paramValues);
    } catch (Exception e) {
      throw new JsonDeserializationException(
          JsonDeserializationException.NO_DEFAULT_CONSTRUCTOR,
          "Failed to deserialize Record: " + clazz.getName(), e);
    }
  }

  /**
   * 将 Map 中解析出的值转换为 Record 组件类型。
   *
   * @param value 解析值
   * @param targetType 目标类型
   * @return 转换后的值
   */
  private static Object convertRecordValue(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }
    if (targetType.isInstance(value)) {
      return value;
    }
    // 数字类型转换
    if (value instanceof Number num) {
      if (targetType == int.class || targetType == Integer.class) {
        return num.intValue();
      }
      if (targetType == long.class || targetType == Long.class) {
        return num.longValue();
      }
      if (targetType == double.class || targetType == Double.class) {
        return num.doubleValue();
      }
      if (targetType == float.class || targetType == Float.class) {
        return num.floatValue();
      }
      if (targetType == short.class || targetType == Short.class) {
        return num.shortValue();
      }
      if (targetType == byte.class || targetType == Byte.class) {
        return num.byteValue();
      }
    }
    // String → 其他类型
    if (value instanceof String str) {
      if (targetType == int.class || targetType == Integer.class) {
        return Integer.parseInt(str);
      }
      if (targetType == long.class || targetType == Long.class) {
        return Long.parseLong(str);
      }
      if (targetType == double.class || targetType == Double.class) {
        return Double.parseDouble(str);
      }
      if (targetType == float.class || targetType == Float.class) {
        return Float.parseFloat(str);
      }
      if (targetType == boolean.class || targetType == Boolean.class) {
        return Boolean.parseBoolean(str);
        }
    }
    return value;
  }

  /**
   * 判断一个类是否为「简单 Bean」。
   *
   * <p>简单 Bean 的所有非 static、非 transient 字段均为基本类型或其包装类、String。 简单 Bean 可使用高性能的 {@link BeanReader}
   * 路径，避免递归嵌套解析。
   *
   * @param clazz 待判断的类
   * @return 是简单 Bean 返回 true
   */
  static boolean isSimpleBean(Class<?> clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      int mods = field.getModifiers();
      if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
        continue;
      }
      Class<?> type = field.getType();
      if (!isSimpleType(type)) {
        return false;
      }
    }
    return true;
  }

  /**
   * 判断一个类型是否为基本类型或其包装类、String。
   *
   * <p>委托给 {@link com.njydsz.common.json.util.JsonTypeUtils} 统一实现。
   *
   * @param type 待判断的类型
   * @return 是基本类型返回 true
   */
  static boolean isSimpleType(Class<?> type) {
    return JsonTypeUtils.isSimpleType(type);
  }
}
