package com.njydsz.common.json.provider;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.njydsz.common.json.annotation.JsonDeserialize;
import com.njydsz.common.json.deserializer.JsonDeserializer;
import com.njydsz.common.json.exception.JsonDeserializationException;
import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.reader.JSONReader;
import com.njydsz.common.json.serializer.SerializerRegistry;
import com.njydsz.common.json.util.BoundedLruCache;

/**
 * YdszJson 反序列化提供者（零拷贝优化版）
 *
 * <p>架构层级：YdszJson => Provider => Parser
 *
 * <p><b>核心优化：</b>
 *
 * <ul>
 *   <li>零拷贝反序列化 - 直接解析 JSON 到对象字段，消除 Map 中转
 *   <li>Constructor 缓存 - 避免每次反射获取
 *   <li>HashMap 字段查找 - O(1) 替代 O(n)
 *   <li>快速路径 - 简单对象（基本类型字段）直接内联解析
 *   <li>JsonType 支持 - 泛型类型推断
 *   <li>Builder 模式支持 - 链式构建对象
 *   <li>Creator 模式支持 - 自定义构造函数反序列化
 *   <li>多态类型支持 - @JsonTypeInfo 自动识别子类型
 * </ul>
 *
 * <p><b>反序列化流程：</b>
 *
 * <ol>
 *   <li>快速路径分派 - 基本类型直接解析，其余走 BeanDeserializerEngine
 *   <li>执行解析 - BeanReader/Creator/Builder/ZeroCopy 多级降级
 *   <li>类型转换 - 处理数字、字符串、日期等类型转换
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class DeserializationProvider {

  private DeserializationProvider() {
    throw new UnsupportedOperationException();
  }

  /**
   * 从 UTF-8 字节数组反序列化（ASCII 快速路径）。
   *
   * <p>先扫描字节流判断是否为纯 ASCII：如果是，直接逐字节转 char[] 构造 String， 跳过 UTF-8 解码开销；非 ASCII 则回退 {@code new
   * String(bytes, UTF_8)}。
   *
   * <p>对标 FastJSON2 {@code JSON.parseObject(byte[], Class)} 和 Jackson {@code
   * ObjectMapper.readValue(byte[], Class)} 的 byte[] 直接入参 API。
   *
   * @param bytes UTF-8 编码的 JSON 字节数组
   * @param clazz 目标类型
   * @param <T> 类型参数
   * @return 反序列化后的对象，bytes 为空时返回 null
   * @since 26.09.01
   */
  public static <T> T deserialize(byte[] bytes, Class<T> clazz) {
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    String json = bytesToAsciiFast(bytes);
    return deserialize(json, clazz);
  }

  /**
   * 从 UTF-8 字节数组反序列化（支持泛型 Type）。
   *
   * @param bytes UTF-8 编码的 JSON 字节数组
   * @param type 目标类型
   * @return 反序列化后的对象
   * @since 26.09.01
   */
  public static Object deserializeToObject(byte[] bytes, Type type) {
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    String json = bytesToAsciiFast(bytes);
    return deserializeToObject(json, type);
  }

  /**
   * 泛型桥接：从 UTF-8 字节数组反序列化为指定 Type。
   *
   * <p>调用方用此方法可保留泛型推断 {@code <T>}，内部委托 {@link #deserializeToObject(byte[], Type)} 并作 checked
   * cast。unchecked 警告集中在桥接方法内（单处 {@code @SuppressWarnings}）。
   *
   * @param bytes UTF-8 编码的 JSON 字节数组
   * @param type 目标类型（{@link Class} 或 {@link java.lang.reflect.ParameterizedType}）
   * @param <T> 类型参数
   * @return 反序列化后的对象
   */
  @SuppressWarnings("unchecked")
  public static <T> T deserialize(byte[] bytes, Type type) {
    return (T) deserializeToObject(bytes, type);
  }

  /**
   * 泛型桥接：从 JSON 字符串反序列化为指定 Type。
   *
   * <p>调用方用此方法可保留泛型推断，内部委托 {@link #deserializeToObject(String, Type)}。
   *
   * @param json JSON 字符串
   * @param type 目标类型
   * @param <T> 类型参数
   * @return 反序列化后的对象
   */
  @SuppressWarnings("unchecked")
  public static <T> T deserialize(String json, Type type) {
    return (T) deserializeToObject(json, type);
  }

  /**
   * ASCII 快速路径：扫描字节流，若全为 ASCII（&lt; 128）则直接逐字节转 char[] 构造 String， 跳过 UTF-8 解码开销；非 ASCII 回退 {@code
   * new String(bytes, UTF_8)}。
   *
   * @param bytes UTF-8 编码的字节流
   * @return 对应的 JSON 字符串
   */
  private static String bytesToAsciiFast(byte[] bytes) {
    int len = bytes.length;
    // 快速扫描前 64 字节判断是否为纯 ASCII
    int scanLen = Math.min(len, 64);
    boolean ascii = true;
    for (int i = 0; i < scanLen; i++) {
      if (bytes[i] < 0) {
        ascii = false;
        break;
      }
    }
    // 如果前 64 字节为 ASCII，继续扫描剩余部分
    if (ascii) {
      for (int i = scanLen; i < len; i++) {
        if (bytes[i] < 0) {
          ascii = false;
          break;
        }
      }
    }
    if (ascii) {
      // 纯 ASCII：直接逐字节转 char[]，跳过 UTF-8 解码
      char[] chars = new char[len];
      for (int i = 0; i < len; i++) {
        chars[i] = (char) (bytes[i] & 0xFF);
      }
      return new String(chars);
    }
    // 非 ASCII：回退标准 UTF-8 解码
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /**
   * 泛型反序列化递归深度计数器（ThreadLocal）。
   *
   * <p>防止恶意构造的嵌套泛型 JSON（如 {@code List<List<List<...>>>}）导致 {@code deserializeToObject} 无限递归引发
   * StackOverflowError。 默认最大深度 64（对标 FastJSON2 maxTypeRecursionDepth=100），可通过 {@link
   * JSONReader#setMaxGenericDepth(int)} 调整。
   */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
  private static final ThreadLocal<Integer> DESERIALIZE_DEPTH = ThreadLocal.withInitial(() -> 0);
  // CHECKSTYLE.ON: RegexpSinglelineJava

  /**
   * @JsonDeserialize 自定义反序列化器缓存（Class -> JsonDeserializer 实例）。 有界 LRU（容量 1024），防止无界增长。
   */
  private static final BoundedLruCache<Class<?>, JsonDeserializer<?>> CUSTOM_DESERIALIZER_CACHE =
      new BoundedLruCache<>(1024);

  /**
   * 检查类是否有 @JsonDeserialize 注解并获取自定义反序列化器。
   *
   * @param clazz 要检查的类
   * @return 自定义反序列化器实例，或 null 如果没有
   */
  private static JsonDeserializer<?> getCustomDeserializer(Class<?> clazz) {
    JsonDeserialize annotation = clazz.getAnnotation(JsonDeserialize.class);
    if (annotation == null || annotation.using() == Void.class) {
      return null;
    }
    try {
      return CUSTOM_DESERIALIZER_CACHE.computeIfAbsent(
          clazz,
          c -> {
            try {
              return (JsonDeserializer<?>)
                  annotation.using().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
              throw new JsonDeserializationException(
                  "Failed to instantiate custom deserializer: " + annotation.using().getName(), e);
            }
          });
    } catch (JsonDeserializationException e) {
      throw e;
    } catch (Exception e) {
      throw new JsonDeserializationException(
          "Failed to instantiate custom deserializer: " + annotation.using().getName(), e);
    }
  }

  /**
   * 通过 {@link JSONReader} 流式调用自定义反序列化器。
   *
   * <p>自定义反序列化器统一实现 {@link JsonDeserializer}， 直接写入 {@link JSONReader}，零拷贝、避免中间 String 分配。
   *
   * @param deserializer 自定义反序列化器实例
   * @param json JSON 字符串
   * @param type 目标类型
   * @return 反序列化后的对象
   */
  private static Object invokeCustomDeserializer(Object deserializer, String json, Class<?> type) {
    JSONReader reader = new JSONReader(json);
    return ((JsonDeserializer<Object>) deserializer).deserialize(reader);
  }

  /**
   * 反序列化 JSON 字符串（零拷贝优化版）
   *
   * @param <T> 泛型类型
   * @param json JSON 字符串
   * @param clazz 目标类型
   * @return 反序列化得到的目标类型实例；{@code json} 为 {@code null} 或空串时返回 {@code null}，
   *     目标类注册了自定义反序列化器时优先由其产出（同样可能为 {@code null}）
   */
  public static <T> T deserialize(String json, Class<T> clazz) {
    if (json == null || json.isEmpty()) {
      return null;
    }

    try {
      // @JsonDeserialize 快速路径：如果类有自定义反序列化器，直接使用
      Object customDeserializer = getCustomDeserializer(clazz);
      if (customDeserializer == null) {
        // P1-6：模块与直接注册的反序列化器已统一写入 SerializerRegistry（单一事实源），
        // 直接查询全局注册中心，避免反向依赖 YdszJson（打破 YdszJson <-> DeserializationProvider 循环依赖，1.2.1）
        customDeserializer = SerializerRegistry.getInstance().getDeserializer(clazz);
      }
      if (customDeserializer != null) {
        Object result = invokeCustomDeserializer(customDeserializer, json, clazz);
        return result != null ? clazz.cast(result) : null;
      }

      Class<?> actualType = resolvePolymorphicType(json, clazz);

      // 深度限制由 JSONReader 在解析过程中通过 Feature.LimitDepth 实时维护，
      // 超阈值即抛 JsonDeserializationException，无需在此预扫描（原实现存在 O(n) 双重扫描
      // 且不区分字符串字面量中的 { } 的逻辑缺陷）
      Object result = deserializeValue(json, actualType);
      return result != null ? castResult(result, clazz) : null;
    } catch (JsonDeserializationException e) {
      // 已有上下文信息的异常直接抛出
      if (e.getContextSnippet() != null) {
        throw e;
      }
      throw JsonDeserializationException.parseError(json, e.getPosition());
    } catch (Exception e) {
      // 注入 JSON 上下文片段，帮助用户快速定位问题
      throw new JsonDeserializationException(
          JsonDeserializationException.PARSE_ERROR,
          "Failed to deserialize JSON to " + clazz.getName() + ": " + e.getMessage(),
          0,
          json);
    }
  }

  /**
   * 将反序列化结果安全转换为目标类型。
   *
   * <p>与 {@code clazz.cast(result)} 的区别：基本类型（int/long/double/float/boolean/char/byte/short） 的
   * {@code Class.cast} 无法接收装箱值（如 Integer），这里对基本类型做显式拆箱转换， 对引用类型仍走 {@code clazz.cast}。
   *
   * @param result 反序列化结果（装箱对象）
   * @param clazz 目标类型
   * @param <T> 目标类型参数
   * @return 转换后的目标类型值
   */
  @SuppressWarnings("unchecked")
  private static <T> T castResult(Object result, Class<?> clazz) {
    if (result == null) {
      return null;
    }
    // 基本类型：显式拆箱（避免 Class.cast 对 primitive 抛 ClassCastException）
    if (clazz == int.class) {
      return (T) Integer.valueOf(((Number) result).intValue());
    }
    if (clazz == long.class) {
      return (T) Long.valueOf(((Number) result).longValue());
    }
    if (clazz == double.class) {
      return (T) Double.valueOf(((Number) result).doubleValue());
    }
    if (clazz == float.class) {
      return (T) Float.valueOf(((Number) result).floatValue());
    }
    if (clazz == short.class) {
      return (T) Short.valueOf(((Number) result).shortValue());
    }
    if (clazz == byte.class) {
      return (T) Byte.valueOf(((Number) result).byteValue());
    }
    if (clazz == char.class) {
      return (T) Character.valueOf(result.toString().charAt(0));
    }
    if (clazz == boolean.class) {
      return (T) Boolean.valueOf((Boolean) result);
    }
    // 引用类型：标准 cast
    return (T) clazz.cast(result);
  }

  private static Object deserializeValue(String json, Class<?> type) {
    // 快速路径：基本类型直接判断（无需缓存查找开销）
    if (type == String.class) {
      return TypeConverter.parseStringValue(json);
    }
    if (type == Integer.class || type == int.class) {
      return TypeConverter.parseIntValue(json);
    }
    if (type == Long.class || type == long.class) {
      return TypeConverter.parseLongValue(json);
    }
    if (type == Double.class || type == double.class) {
      return TypeConverter.parseDoubleValue(json);
    }
    if (type == Float.class || type == float.class) {
      return TypeConverter.parseFloatValue(json);
    }
    if (type == Boolean.class || type == boolean.class) {
      return TypeConverter.parseBooleanValue(json);
      }
    if (type == BigDecimal.class) {
      return parseBigDecimal(json);
    }
    if (type == BigInteger.class) {
      return parseBigInteger(json);
    }
    if (type == Object.class) {
      return parseValue(json);
    }
    if (type == Map.class) {
      return JsonParserUtil.parseObject(json);
    }
    if (type == List.class) {
      return BeanDeserializerEngine.deserializeArrayZeroCopy(json, Object.class);
      }

    // Bean 类型：直接走 BeanDeserializerEngine 多级降级路径
    // （BeanReader -> Creator -> Builder -> ZeroCopy -> Map 降级）
    // 注：原 STRATEGY_CACHE 已删除——所有非简单类型统一走 BEAN 路径，
    // if-else 链已覆盖所有简单类型，缓存无策略分派价值，synchronizedMap 反而是性能瓶颈。
    return BeanDeserializerEngine.deserializeBeanZeroCopyAsObject(json, type);
  }

  /** 从 JSON 片段解析 BigDecimal（保留任意精度）。 */
  private static BigDecimal parseBigDecimal(String json) {
    if (json == null || json.isBlank() || "null".equals(json.trim())) {
      return null;
    }
    try {
      return new BigDecimal(json.trim());
    } catch (NumberFormatException e) {
      throw new JsonDeserializationException(
          JsonDeserializationException.PARSE_ERROR,
          "Failed to parse BigDecimal from: " + json,
          0,
          json);
    }
  }

  /** 从 JSON 片段解析 BigInteger。 */
  private static BigInteger parseBigInteger(String json) {
    if (json == null || json.isBlank() || "null".equals(json.trim())) {
      return null;
    }
    try {
      return new BigInteger(json.trim());
    } catch (NumberFormatException e) {
      throw new JsonDeserializationException(
          JsonDeserializationException.PARSE_ERROR,
          "Failed to parse BigInteger from: " + json,
          0,
          json);
    }
  }

  /**
   * 反序列化 JSON 字符串（带特性配置）
   *
   * <p><b>注意：</b>当前版本 {@code features} 参数仅用于 JSON 长度限制检查， 其他 Feature 配置尚未实现，保留参数位置以便后续扩展。
   *
   * @param json JSON 字符串
   * @param clazz 目标类型
   * @param features 特性标志（位运算值，当前仅用于长度限制检查）
   * @param <T> 类型参数
   * @return 反序列化后的对象
   */
  public static <T> T deserialize(String json, Class<T> clazz, long features) {
    if (json == null || json.isEmpty()) {
      return null;
    }

    // 安全检查：最大长度限制（防止 DoS 攻击）
    if (json.length() > JSONReader.DEFAULT_MAX_JSON_LENGTH) {
      throw new JsonDeserializationException(
          JsonDeserializationException.PARSE_ERROR,
          "JSON length limit exceeded: "
              + json.length()
              + " > "
              + JSONReader.DEFAULT_MAX_JSON_LENGTH);
    }

    // 深度限制由 JSONReader 在解析过程中通过 Feature.LimitDepth 实时维护
    return deserialize(json, clazz);
  }

  /**
   * 解析多态类型
   *
   * <p>如果目标类有 @JsonTypeInfo 注解，则根据 JSON 中的类型属性值 识别具体子类型并返回。
   *
   * @param json JSON 字符串
   * @param baseType 基类
   * @return 解析后的具体类型，如果不支持多态返回基类
   */
  private static Class<?> resolvePolymorphicType(String json, Class<?> baseType) {
    return PolymorphicTypeResolver.resolveType(json, baseType);
  }

  private static Object parseValue(String json) {
    json = json.trim();
    int len = json.length();

    // 快速路径：按长度和首字符分派，避免多次 equals/startsWith 调用
    if (len == 0) {
      return null;
    }
    char first = json.charAt(0);
    switch (first) {
      case 'n':
        if (len == 4 && json.equals("null")) {
          return null;
        }
        break;
      case 't':
        if (len == 4 && json.equals("true")) {
          return Boolean.TRUE;
        }
        break;
      case 'f':
        if (len == 5 && json.equals("false")) {
          return Boolean.FALSE;
        }
        break;
      case '{':
        return JsonParserUtil.parseObject(json);
      case '[':
        return JsonParserUtil.parseArray(json);
      case '"':
        return TypeConverter.parseStringValue(json);
      default:
        break;
    }

    // 数字解析
    try {
      if (json.indexOf('.') >= 0 || json.indexOf('E') >= 0 || json.indexOf('e') >= 0) {
        return Double.parseDouble(json);
      }
      return Long.parseLong(json);
    } catch (NumberFormatException e) {
      return json;
    }
  }

  /**
   * 反序列化 JSON 字符串（支持 Type）
   *
   * <p>支持 {@link Class}、{@link ParameterizedType}（List/Map/Set 泛型）等类型。 类型不匹配时立即抛出 {@link
   * JsonDeserializationException}，包含期望类型和实际类型信息。
   *
   * <p><b>返回 Object 而非泛型 T 的原因：</b>Java 泛型类型擦除导致从 {@link Type} 到 {@code T} 的转换无法在编译期验证（unchecked
   * cast）。返回 {@code Object} 后由调用方通过 {@code Class.cast()} 执行运行时检查的 checked cast， 从根源消除 unchecked 警告。
   *
   * @param json JSON 字符串
   * @param type 目标类型
   * @return 反序列化后的对象
   * @throws JsonDeserializationException 如果 JSON 结构与目标类型不匹配
   */
  public static Object deserializeToObject(String json, Type type) {
    if (json == null || json.isEmpty()) {
      return null;
    }

    // 泛型递归深度保护：仅在非 Class 类型的泛型路径（ParameterizedType/GenericArrayType/WildcardType）中递增
    boolean incrementDepth = !(type instanceof Class<?>);
    if (incrementDepth) {
      int currentDepth = DESERIALIZE_DEPTH.get();
      // P0-3：优先读取线程级调用覆盖（JsonMapper 实例隔离），未设置回退静态全局值
      int maxDepth = JSONReader.resolveCallMaxGenericDepth();
      if (currentDepth >= maxDepth) {
        throw new JsonDeserializationException(
            JsonDeserializationException.TYPE_MISMATCH,
            "Generic deserialization depth exceeded: "
                + currentDepth
                + " >= "
                + maxDepth
                + " (type: "
                + type
                + ")");
      }
      DESERIALIZE_DEPTH.set(currentDepth + 1);
    }

    try {
      return deserializeToObjectInternal(json, type);
    } finally {
      if (incrementDepth) {
        DESERIALIZE_DEPTH.set(DESERIALIZE_DEPTH.get() - 1);
      }
    }
  }

  /**
   * 内部反序列化逻辑（实际委托给各类型分派）。
   *
   * <p>与 {@link #deserializeToObject(String, Type)} 分离，方便递归深度保护在入口处统一处理。
   */
  private static Object deserializeToObjectInternal(String json, Type type) {
    if (type instanceof Class<?> clazz) {
      Object result = deserializeValue(json, clazz);
      return result;
    }

    if (type instanceof GenericArrayType gat) {
      // 泛型数组类型（如 T[]）：先反序列化为 List，再转数组
      Type componentType = gat.getGenericComponentType();
      ParameterizedType listType =
          new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
              return new Type[] {componentType};
            }

            @Override
            public Type getRawType() {
              return List.class;
            }

            @Override
            public Type getOwnerType() {
              return null;
            }
          };
      List<?> list = (List<?>) deserializeToObject(json, listType);
      if (list == null) {
        return null;
      }
      Class<?> componentClass = componentType instanceof Class<?> c ? c : Object.class;
      Object array = Array.newInstance(componentClass, list.size());
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    }

    if (type instanceof ParameterizedType pt) {
      Type rawType = pt.getRawType();

      if (rawType == List.class || rawType == ArrayList.class) {
        Type elementType = pt.getActualTypeArguments()[0];
        if (elementType instanceof Class<?> elementClass) {
          if (BeanDeserializerEngine.isSimpleType(elementClass)) {
            return BeanDeserializerEngine.deserializeArrayZeroCopy(json, elementClass);
          } else {
            return BeanDeserializerEngine.deserializeBeanListFast(json, elementClass);
          }
        }
      }

      if (rawType == Map.class
          || rawType == HashMap.class
          || rawType == LinkedHashMap.class
          || rawType == TreeMap.class) {
        Type[] typeArgs = pt.getActualTypeArguments();
        Map<String, Object> parsed = JsonParserUtil.parseObject(json);
        if (parsed == null) {
          return null;
        }
        // 当 value 类型为已知简单类型时，转换解析结果（如 Long → Integer）
        if (typeArgs.length == 2 && typeArgs[1] instanceof Class<?> valueClass) {
          if (valueClass != Object.class) {
            Map<String, Object> result = createMap(rawType);
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
              result.put(entry.getKey(), TypeConverter.convertValue(entry.getValue(), valueClass));
            }
            return result;
          }
        }
        return parsed;
      }

      if (rawType == Set.class
          || rawType == HashSet.class
          || rawType == LinkedHashSet.class
          || rawType == TreeSet.class) {
        Type elementType = pt.getActualTypeArguments()[0];
        if (elementType instanceof Class<?> elementClass) {
          if (BeanDeserializerEngine.isSimpleType(elementClass)) {
            List<?> list = BeanDeserializerEngine.deserializeArrayZeroCopy(json, elementClass);
            if (list == null) {
              return null;
            }
            return createSet(rawType, list);
          } else {
            List<?> list = BeanDeserializerEngine.deserializeBeanListFast(json, elementClass);
            if (list == null) {
              return null;
            }
            return createSet(rawType, list);
          }
        }
      }
    }

    if (type instanceof WildcardType wt) {
      // WildcardType（如 ? extends Number）：取上界进行反序列化
      Type[] upperBounds = wt.getUpperBounds();
      if (upperBounds != null && upperBounds.length > 0) {
        return deserializeToObject(json, upperBounds[0]);
      }
      // 无上界时回退到 Object
      return parseValue(json);
    }

    // 兜底路径：根据 JSON 首字符决定解析为 List 或 Map
    String trimmed = json.trim();
    if (trimmed.startsWith("[")) {
      return JsonParserUtil.parseArray(json);
    }
    return JsonParserUtil.parseObject(json);
  }

  /**
   * 根据原始类型创建对应的 Set 实例并填充元素。
   *
   * @param rawType 原始类型（TreeSet/LinkedHashSet/HashSet）
   * @param list 元素列表
   * @return 填充好的 Set 实例
   */
  private static Set<Object> createSet(Type rawType, List<?> list) {
    Set<Object> set;
    if (rawType == TreeSet.class) {
      set = new TreeSet<>();
    } else if (rawType == LinkedHashSet.class) {
      set = new LinkedHashSet<>(list.size());
    } else {
      set = new HashSet<>(list.size());
    }
    set.addAll(list);
    return set;
  }

  /** 根据 rawType 创建对应的 Map 实例。 */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
  private static Map<String, Object> createMap(Type rawType) {
  // CHECKSTYLE.ON: RegexpSinglelineJava
    if (rawType == TreeMap.class) {
      return new TreeMap<>();
    } else if (rawType == LinkedHashMap.class) {
      return new LinkedHashMap<>(0);
    }
    return new HashMap<>(0);
  }

  /**
   * 清理当前线程的 ThreadLocal 对象。
   *
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
   * <p>在线程池环境中，应在任务完成后或线程归还前调用此方法， 防止 {@link #DESERIALIZE_DEPTH} 等 ThreadLocal 值在线程池中残留。
  // CHECKSTYLE.ON: RegexpSinglelineJava
   *
   * @since 26.09.01
   */
  public static void clearThreadLocals() {
    DESERIALIZE_DEPTH.remove();
  }
}
