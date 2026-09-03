package com.njydsz.common.json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.deserializer.JsonDeserializer;
import com.njydsz.common.json.exception.JsonException;
import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.reader.JSONReader;
import com.njydsz.common.json.serializer.JsonSerializer;
import com.njydsz.common.json.serializer.SerializerRegistry;
import com.njydsz.common.json.tree.ArrayNode;
import com.njydsz.common.json.tree.JsonMergePatch;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.JsonPatch;
import com.njydsz.common.json.tree.JsonPatch.PatchOp;
import com.njydsz.common.json.tree.NullNode;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.json.tree.TreeConverter;
import com.njydsz.common.json.type.JsonType;
import com.njydsz.common.json.type.TypeFactory;

/**
 * YdszJson - 高性能 JSON 工具类
 *
 * <p>提供高性能、功能丰富的 JSON 序列化和反序列化功能，纯 Java 实现，零外部 JSON 库依赖。
 *
 * <p><b>核心特性：</b>
 *
 * <ul>
 *   <li><b>零依赖</b>：纯 Java 实现，无需 Jackson / FastJSON / Gson 等第三方库
 *   <li><b>高性能</b>：char[] 直操作、递归下降解析、ThreadLocal 对象池、MethodHandle 反射加速
 *   <li><b>递归下降解析</b>：直接解析 JSON 到 Bean，无需 Map 中转
 *   <li><b>字段哈希匹配</b>：FNV-1a 哈希 O(1) 字段匹配，优于传统 O(n) 扫描
 *   <li><b>循环引用检测</b>：自动检测并处理循环引用（REF / IGNORE / ERROR 策略）
 *   <li><b>泛型支持</b>：完整的泛型反序列化支持（TypeRef 工厂方法）
 *   <li><b>Java 8+ 日期时间</b>：完整支持 LocalDateTime 等新 API
 *   <li><b>JSON Patch (RFC 6902)</b>：支持 add/remove/replace/move/copy/test 六种操作
 *   <li><b>JSON Merge Patch (RFC 7396)</b>：简化合并语义
 *   <li><b>注解支持</b>：80%+ Jackson 兼容注解（@JsonProperty、@JsonIgnore 等）
 *   <li><b>自定义序列化器</b>：JsonModule SPI + @JsonSerialize/@JsonDeserialize 注解
 *   <li><b>命名策略</b>：支持 SNAKE_CASE、KEBAB_CASE、LOWER_CASE 等多种策略
 * </ul>
 *
 * <p><b>核心优化技术：</b>
 *
 * <ul>
 *   <li>递归下降解析器 - 直接解析 JSON 到对象字段，零拷贝
 *   <li>FNV-1a 字段哈希 - O(1) 字段匹配，提升反序列化性能
 *   <li>ThreadLocal 对象池 - StringBuilder / JSONWriter 复用，减少 GC
 *   <li>ASCII 快速路径 - byte[] → char[] 跳过 UTF-8 解码
 *   <li>分级 StringBuilder - 根据 JSON 大小预分配合适容量
 *   <li>不可变配置 + 原子替换 - 线程安全的配置管理
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class YdszJson {

  private static final Logger LOGGER = LoggerFactory.getLogger(YdszJson.class);

  private YdszJson() {
    throw new UnsupportedOperationException(
        "YdszJson is a utility class and cannot be instantiated");
  }

  /**
   * 默认 JsonMapper 实例（持有全局配置）。
   *
   * <p>YdszJson 静态方法委托给此实例，确保：
   *
   * <ul>
   *   <li>配置变更（通过 {@link JsonConfig#install(JsonConfig)}）自动生效
   *   <li>与实例化 {@code JsonMapper} API 共享相同代码路径（消除代码路径分叉）
   * </ul>
   *
   * <p>volatile 保证 {@link #reloadDefaultMapper()} 后的可见性。
   *
   * @since 26.09.01
   */
  private static volatile JsonMapper defaultMapper = new JsonMapper();

  /**
   * 重新构建默认 JsonMapper 实例。
   *
   * <p>当通过 {@link JsonConfig#install(JsonConfig)} 变更全局配置后调用此方法， 使 YdszJson 静态方法立即使用新配置。
   * 正常情况下无需显式调用——{@link JsonConfig#install(JsonConfig)} 内部会自动触发。
   *
   * @since 26.09.01
   */
  public static void reloadDefaultMapper() {
    defaultMapper = new JsonMapper(JsonConfig.copyOf(null));
  }

  /**
   * 获取当前生效的默认 Mapper 实例。
   *
   * <p>与 {@link JsonMapper#getDefault()} 同源（单一事实来源），保证配置热更新后 两个入口看到的默认配置一致（P0-2 修复：原先 {@code
   * JsonMapper.DEFAULT} 是 类加载时的 static final 快照，{@link JsonConfig#install(JsonConfig)} 后不会刷新）。
   *
   * @return 当前默认 Mapper 实例（volatile 保证可见性，永不为 null）
   * @since 26.09.01
   */
  public static JsonMapper getDefaultMapper() {
    return defaultMapper;
  }

  // ==================== 序列化入口方法 ====================

  /**
   * 对象转 JSON 字符串
   *
   * @param obj 要序列化的对象
   * @return JSON 字符串
   */
  public static String toJson(Object obj) {
    if (obj == null) {
      return "null";
    }
    return defaultMapper.toJson(obj);
  }

  /**
   * 格式化 JSON（带缩进）
   *
   * @param obj 要序列化的对象
   * @return 格式化的 JSON 字符串
   */
  public static String format(Object obj) {
    if (obj == null) {
      return "null";
    }
    return defaultMapper.toJson(obj, true);
  }

  /**
   * 美化一个已序列化的 JSON 字符串（格式化 + 缩进）。
   *
   * <p>对标 Jackson 的"二次格式化"场景：输入一个紧凑 JSON 字符串，输出带缩进的格式化字符串。 内部通过 parseTree 后 toJson(pretty) 实现。
   *
   * <p>如果输入为 null/空/非法 JSON，降级返回原始字符串（不抛异常）， 避免在日志、调试等场景引发二次异常。
   *
   * @param json 紧凑 JSON 字符串
   * @return 格式化后的 JSON 字符串；解析失败时返回原始字符串
   * @since 26.09.01
   */
  public static String format(String json) {
    if (json == null || json.isEmpty()) {
      return json;
    }
    try {
      JsonNode tree = defaultMapper.readTree(json);
      return defaultMapper.toJson(tree, true);
    } catch (Exception e) {
      // 格式化失败（非法 JSON 等）时降级返回原始输入，避免二次异常
      return json;
    }
  }

  // ==================== JSON Patch (RFC 6902 / RFC 7396) ====================

  /**
   * 应用 JSON Patch（RFC 6902）到目标 JSON 字符串。
   *
   * <p>JSON Patch 是一个操作序列，支持 add/remove/replace/move/copy/test 六种操作， 用于对 JSON 文档做局部更新。
   *
   * <p><b>示例：</b>
   *
   * <pre>{@code
   * String result = YdszJson.patch(
   *     "{\"name\":\"old\",\"age\":25}",
   *     "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"new\"}]"
   * );
   * // result: {"name":"new","age":25}
   * }</pre>
   *
   * @param targetJson 目标 JSON 字符串
   * @param patchJson Patch JSON 数组字符串
   * @return 应用 Patch 后的 JSON 字符串
   * @throws com.njydsz.common.json.exception.JsonException 当 Patch 操作失败时（如路径不存在、TEST 失败等）
   * @since 26.09.01
   */
  public static String patch(String targetJson, String patchJson) {
    // P-2 优化：统一走 JsonNode 树路径（readTree → applyToTree → toJson），
    // 去掉原先 parseMap → Map 中转的两次结构转换（Map → tree → Map）。
    // P1 修复：使用 applyToTree 返回值——整文档路径操作（path=""）会替换根节点。
    ObjectNode tree = (ObjectNode) readTree(targetJson);
    return toJson(JsonPatch.applyToTree(patchJson, tree));
  }

  /**
   * 应用 JSON Merge Patch（RFC 7396）到目标 JSON 字符串。
   *
   * <p>JSON Merge Patch 是一种简化的 patch 格式：直接用 patch 中的字段覆盖目标中的同名字段， null 值表示删除字段。
   *
   * <p><b>示例：</b>
   *
   * <pre>{@code
   * String result = YdszJson.mergePatch(
   *     "{\"a\":1,\"b\":{\"c\":2}}",
   *     "{\"b\":{\"c\":null,\"d\":3}}"
   * );
   * // result: {"a":1,"b":{"d":3}}
   * }</pre>
   *
   * @param targetJson 目标 JSON 字符串
   * @param patchJson Merge Patch JSON 字符串
   * @return 应用 Patch 后的 JSON 字符串
   * @throws JsonException 如果 patch 操作失败
   * @since 26.09.01
   */
  public static String mergePatch(String targetJson, String patchJson) {
    JsonNode target = readTree(targetJson);
    JsonNode patch = readTree(patchJson);
    JsonNode result = JsonMergePatch.apply(target, patch);
    return toJson(result);
  }

  /**
   * 对象转 JSON 字节数组（UTF-8 编码）
   *
   * <p>适用于网络传输、文件写入等需要字节数组的场景，避免额外的 String.getBytes() 调用。
   *
   * @param obj 要序列化的对象
   * @return UTF-8 编码的 JSON 字节数组
   */
  public static byte[] toJsonBytes(Object obj) {
    if (obj == null) {
      return new byte[] {'n', 'u', 'l', 'l'};
    }
    return defaultMapper.toJsonBytes(obj);
  }

  // ==================== 反序列化入口方法 ====================

  /**
   * JSON 字符串转对象
   *
   * @param json JSON 字符串
   * @param clazz 目标类型
   * @param <T> 类型参数
   * @return 反序列化后的对象
   */
  public static <T> T fromJson(String json, Class<T> clazz) {
    if (json == null || json.isBlank()) {
      return null;
    }
    validateJsonSize(json);
    return defaultMapper.toObject(json, clazz);
  }

  /**
   * JSON 字符串转对象（支持 JsonType）
   *
   * @param json JSON 字符串
   * @param typeRef 类型引用
   * @param <T> 类型参数
   * @return 反序列化后的对象
   */
  public static <T> T fromJson(String json, JsonType<T> typeRef) {
    if (json == null || json.isBlank()) {
      return null;
    }
    validateJsonSize(json);
    return defaultMapper.toObject(json, typeRef.getType());
  }

  /**
   * JSON 字符串转对象（支持 {@link Type}，如泛型 {@code List<User>}）。
   *
   * <p>供持有 {@code Type} 的框架适配场景使用（如 Feign Decoder 传入的 泛型反序列化目标类型），避免调用方自行将 Type
   * 包装为 {@link JsonType}。
   *
   * @param json JSON 字符串
   * @param type 目标类型（可为泛型，如 {@code List<User>}）
   * @return 反序列化后的对象
   */
  public static Object fromJson(String json, Type type) {
    if (json == null || json.isBlank()) {
      return null;
    }
    validateJsonSize(json);
    return defaultMapper.toObject(json, type);
  }

  /**
   * 反序列化 JSON 字符串为泛型集合（便捷重载，无需显式构造 {@link Type}）。
   * <p>内部通过 {@link com.njydsz.common.json.type.TypeFactory} 构造参数化类型， 再委托 {@link #fromJson(String,
   * Type)} 完成反序列化。
   * <p><b>使用示例：</b>
   * <pre>{@code
   * // 反序列化为 List<User>
   * List<User> users = YdszJson.fromJson(json, List.class, User.class);
   * // 反序列化为 Set<String>
   * Set<String> ids = YdszJson.fromJson(json, Set.class, String.class);
   * }</pre>
   * @param json JSON 字符串
   * @param collectionClass 集合类型（如 {@code List.class}、{@code Set.class}、{@code ArrayList.class}）
   * @param elementClass 集合元素类型
   * @return 反序列化后的集合对象，json 为空时返回 null
   * @throws IllegalArgumentException 如果 collectionClass 是 Map 类型（应使用 fromJsonToMap）
   * @since 26.09.01
   *
   * @param <T> 泛型类型
   */
  public static <T> T fromJson(String json, Class<?> collectionClass, Class<?> elementClass) {
    if (json == null || json.isBlank()) {
      return null;
    }
    if (Map.class.isAssignableFrom(collectionClass)) {
      throw new IllegalArgumentException("Map 类型反序列化请使用 fromJsonToMap(json, keyClass, valueClass)");
    }
    Type type =
        TypeFactory.getInstance()
            .constructCollectionType(collectionClass, elementClass);
    T result = (T) fromJson(json, type);
    return result;
  }

  // ==================== 自定义序列化器注册 ====================

  // 注意：历史版本提供 YdszJson.register(Class, JsonSerializer/JsonDeserializer) 静态注册方法，
  // 自 1.1.0 起废弃并由 SerializerRegistry / JsonModule 替代，本方法已移除。
  // 自定义序列化器/反序列化器请实现 JsonModule 接口并标注 @Component，由 SPI 自动发现注册。

  static <T> JsonSerializer<T> getCustomSerializer(Class<T> clazz) {
    // P1-6：模块序列化器已统一写入 SerializerRegistry（单一事实源），无需再兜底查询模块注册中心
    return SerializerRegistry.getInstance().get(clazz);
  }

  static <T> JsonDeserializer<T> getCustomDeserializer(Class<T> clazz) {
    // P1-6：模块反序列化器已统一写入 SerializerRegistry（单一事实源），无需再兜底查询模块注册中心
    return SerializerRegistry.getInstance().getDeserializer(clazz);
  }

  /**
   * 获取已注册的自定义序列化器（来自 {@code SerializerRegistry} 或 {@code JsonModule} 模块）。
   *
   * <p>供序列化 Provider 在 {@code @JsonSerialize} 注解快速路径之后回退查询。 历史实现中该方法虽存在但未被 Provider
   * 实际调用，导致模块注册机制形同虚设； 现已在 {@code SerializationProvider}/{@code DeserializationProvider} 接入，使
   * {@code JsonModule.SpringFactory} 注册的序列化器/反序列化器在全局 {@code toJson/toObject} 路径中真正生效。
   *
   * @param clazz 目标类型
   * @param <T> 类型参数
   * @return 序列化器，未注册返回 null
   */
  public static <T> JsonSerializer<T> getRegisteredSerializer(Class<T> clazz) {
    return getCustomSerializer(clazz);
  }

  /**
   * 获取已注册的自定义反序列化器（来自 {@code SerializerRegistry} 或 {@code JsonModule} 模块）。
   *
   * @param clazz 目标类型
   * @param <T> 类型参数
   * @return 反序列化器，未注册返回 null
   */
  public static <T> JsonDeserializer<T> getRegisteredDeserializer(Class<T> clazz) {
    return getCustomDeserializer(clazz);
  }

  static boolean hasCustomSerializer(Class<?> clazz) {
    // P1-6：单一事实源，直接查询全局注册中心
    return SerializerRegistry.getInstance().hasSerializer(clazz);
  }

  static boolean hasCustomDeserializer(Class<?> clazz) {
    // P1-6：单一事实源，直接查询全局注册中心
    return SerializerRegistry.getInstance().hasDeserializer(clazz);
  }

  // ==================== 类型转换 ====================

  /**
   * 将一个对象转换为指定类型的实例。
   *
   * <p>内部通过 JSON 序列化/反序列化实现，适用于 Map&lt;?, ?&gt; 到 POJO、对象深拷贝等场景。 推荐替代 {@code
   * YdszJson.fromJson(YdszJson.toJson(obj), TargetClass.class)} 这种链式调用。
   *
   * <p><b>典型用法：</b>
   *
   * <pre>{@code
   * // Map 转 POJO
   * User user = YdszJson.convertValue(userMap, User.class);
   *
   * // 泛型类型转换
   * List<Order> orders = YdszJson.convertValue(rawData,
   *         new JsonType<List<Order>>() {});
   *
   * // 对象深拷贝
   * User copy = YdszJson.convertValue(original, User.class);
   * }</pre>
   *
   * @param fromValue 源对象（可为 Map、POJO、基本类型等）
   * @param toValueType 目标类型
   * @param <T> 目标类型泛型
   * @return 转换后的对象实例
   * @throws JsonException 如果转换失败
   * @since 26.09.01
   */
  public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
    return defaultMapper.convertValue(fromValue, toValueType);
  }

  /**
   * 将一个对象转换为指定泛型类型的实例。
   *
   * <p>适用于带泛型参数的目标类型（如 {@code List<User>}、{@code Map<String, Order>}）， 推荐使用 {@link JsonType}
   * 匿名内部类传递完整的泛型信息。
   *
   * @param fromValue 源对象
   * @param toValueTypeRef 目标泛型类型引用
   * @param <T> 目标类型泛型
   * @return 转换后的对象实例
   * @throws JsonException 如果转换失败
   * @since 26.09.01
   */
  public static <T> T convertValue(Object fromValue, JsonType<T> toValueTypeRef) {
    return defaultMapper.convertValue(fromValue, toValueTypeRef);
  }

  // ==================== Tree Model API ====================

  /**
   * 将 JSON 字符串解析为 JsonNode 树
   *
   * @param json JSON 字符串
   * @return JsonNode 树
   */
  public static JsonNode readTree(String json) {
    Object parsed = JsonParserUtil.parse(json);
    return TreeConverter.convertToJsonNode(parsed);
  }

  /**
   * 将 JSON 字符串解析为 ObjectNode 对象节点。
   *
   * <p>对标 FastJSON2 {@code JSON.parseObject(json)}，解析 JSON 对象字符串为 ObjectNode， 提供
   * getString/getInteger/getLong/getBoolean 等便捷访问方法。
   *
   * @param json JSON 字符串
   * @return ObjectNode 实例，json 为 null/blank 返回 null
   * @throws JsonException 如果 JSON 不是对象（如为数组或标量）
   * @since 26.09.01
   */
  public static ObjectNode parseObject(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    JsonNode tree = readTree(json);
    if (tree instanceof ObjectNode objNode) {
      return objNode;
    }
    throw new JsonException("JSON is not an object: " + tree.getClass().getSimpleName());
  }

  /**
   * 将 JSON 字符串解析为 ArrayNode 数组节点。
   *
   * <p>对标 FastJSON2 {@code JSON.parseArray(json)}，解析 JSON 数组字符串为 ArrayNode， 提供
   * getString/getInteger/getLong/getBoolean 等便捷访问方法。
   *
   * @param json JSON 字符串
   * @return ArrayNode 实例，json 为 null/blank 返回 null
   * @throws JsonException 如果 JSON 不是数组
   * @since 26.09.01
   */
  public static ArrayNode parseArrayNode(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    JsonNode tree = readTree(json);
    if (tree instanceof ArrayNode arrNode) {
      return arrNode;
    }
    throw new JsonException("JSON is not an array: " + tree.getClass().getSimpleName());
  }

  /**
   * 将对象序列化为 JsonNode 树。
   *
   * <p>与 {@link JsonMapper#valueToTree(Object)} 语义一致，可直接使用 YdszJson 静态入口调用。
   *
   * @param obj 要序列化的对象
   * @return JsonNode 树
   */
  public static JsonNode valueToTree(Object obj) {
    if (obj == null) {
      return NullNode.getInstance();
    }
    if (obj instanceof JsonNode) {
      return (JsonNode) obj;
    }
    if (obj instanceof Map
        || obj instanceof List
        || obj instanceof String
        || obj instanceof Number
        || obj instanceof Boolean) {
      return TreeConverter.convertToJsonNode(obj);
    }
    String json = toJson(obj);
    return readTree(json);
  }

  // ==================== 类型安全 Map / List 便捷 API ====================

  /**
   * JSON 字符串转 Map（类型安全，key 为 String，value 为 Object）。
   *
   * @param json JSON 字符串
   * @return Map 对象，json 为 null/blank 时返回 null
   */
  public static Map<String, Object> parseMap(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    validateJsonSize(json);
    // P-1 优化：底层 toObject(json, Map.class) 直接产出 Map<String,Object>（键恒为 String，
    // 嵌套 Map/List 结构正确），原先的 toStringObjectMap 递归重建整棵 Map 为纯冗余分配，已移除。
    return defaultMapper.toObject(json, Map.class);
  }

  /**
   * JSON 字符串转 List（指定元素类型）。
   *
   * @param json JSON 字符串
   * @param clazz 元素类型
   * @param <T> 元素类型泛型
   * @return List 对象，json 为 null/blank 时返回 null
   */
  public static <T> List<T> parseArray(String json, Class<T> clazz) {
    if (json == null || json.isBlank()) {
      return null;
    }
    validateJsonSize(json);
    // 复用 TypeFactory 缓存的参数化类型，避免每次调用新建匿名 ParameterizedType
    Type type = TypeFactory.getInstance().constructCollectionType(List.class, clazz);
    List<T> result = defaultMapper.toObject(json, type);
    return result != null ? result : new ArrayList<>(16);
  }

  /**
   * JSON 字符串转 List（元素类型为 Object）。
   *
   * @param json JSON 字符串
   * @return List 对象，json 为 null/blank 时返回 null
   */
  public static List<Object> parseArray(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    validateJsonSize(json);
    // P-1 优化：底层 toObject(json, List.class) 直接产出 List<Object>（元素为 Map/List/标量，
    // 结构正确），原先的 toObjectList 递归重建整棵 List 为纯冗余分配，已移除。
    return defaultMapper.toObject(json, List.class);
  }

  /**
   * 从 JSON 字符串反序列化为指定 key/value 类型的 Map。
   *
   * @param json JSON 字符串
   * @param keyClass Map key 类型
   * @param valueClass Map value 类型
   * @param <K> key 类型泛型
   * @param <V> value 类型泛型
   * @return 反序列化 Map，json 为空时返回 null
   */
  public static <K, V> Map<K, V> fromJsonToMap(
      String json, Class<K> keyClass, Class<V> valueClass) {
    if (json == null || json.isBlank()) {
      return null;
    }
    validateJsonSize(json);
    // 复用 TypeFactory 缓存的参数化类型，避免每次调用新建匿名 ParameterizedType
    Type mapType = TypeFactory.getInstance().constructMapType(Map.class, keyClass, valueClass);
    Map<K, V> result = defaultMapper.toObject(json, mapType);
    if (result != null) {
      return result;
    }
    return new LinkedHashMap<>(0);
  }

  // ==================== 便捷方法（字节数组 / 类型安全 Map） ====================

  /**
   * 字节数组转对象（UTF-8 编码）
   *
   * @param bytes JSON 字节数组
   * @param clazz 目标类型
   * @param <T> 目标类型泛型
   * @return 反序列化对象，bytes 为空时返回 null
   */
  public static <T> T fromJsonBytes(byte[] bytes, Class<T> clazz) {
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    validateJsonSizeBytes(bytes.length);
    return defaultMapper.toObject(bytes, clazz);
  }

  /**
   * 字节数组转泛型对象（UTF-8 编码）
   *
   * @param bytes JSON 字节数组
   * @param typeRef 类型引用
   * @param <T> 目标类型泛型
   * @return 反序列化对象，bytes 为空时返回 null
   */
  public static <T> T fromJsonBytes(byte[] bytes, JsonType<T> typeRef) {
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    validateJsonSizeBytes(bytes.length);
    return defaultMapper.toObject(bytes, typeRef.getType());
  }

  // ==================== 流式 API ====================

  /**
   * 将对象序列化为 JSON 并直接写入 OutputStream（UTF-8 编码）。
   *
   * <p>避免中间 String 分配，适用于大 JSON 输出场景。
   *
   * @param obj 要序列化的对象
   * @param out 输出流
   * @since 26.09.01
   */
  public static void toJson(Object obj, OutputStream out) {
    SerializationProvider.serializeToStream(obj, out);
  }

  /**
   * 将对象序列化为 JSON 并直接写入 Writer。
   *
   * <p>适用于字符流输出场景（如 FileWriter、StringWriter、BufferedWriter）。 内部使用 {@link
   * SerializationProvider#serialize(Object)} 生成 JSON 字符串后写入 Writer， 避免额外的 byte/char 转换。
   *
   * @param obj 要序列化的对象
   * @param writer 字符输出流
   * @throws JsonException 如果写入失败
   * @since 26.09.01
   */
  public static void toJson(Object obj, Writer writer) {
    SerializationProvider.serializeToWriter(obj, writer);
  }

  /** 默认 InputStream 读取上限（10MB），防止无界流导致 OOM */
  private static final long DEFAULT_MAX_INPUT_STREAM_SIZE = 10L * 1024 * 1024;

  /**
   * 从 InputStream 读取 JSON 并反序列化为指定类型。
   *
   * <p>使用有界读取（默认 10MB 上限），防止恶意或无界 InputStream 导致 OOM。 超过上限时会抛出 {@link JsonException}。
   *
   * @param in 输入流
   * @param clazz 目标类型
   * @param <T> 类型参数
   * @return 反序列化后的对象
   * @since 26.09.01
   */
  public static <T> T toObject(InputStream in, Class<T> clazz) {
    return toObject(in, DEFAULT_MAX_INPUT_STREAM_SIZE, clazz);
  }

  /**
   * 从 InputStream 读取 JSON 并反序列化为指定类型（自定义上限）。
   *
   * <p>P1-P2 流式安全保护：替代 {@code InputStream.readAllBytes()} 无界读取， 超限时立即抛异常，防止大输入导致 OOM。
   *
   * @param in 输入流，不允许 null
   * @param maxBytes 最大允许读取字节数
   * @param clazz 目标类型
   * @param <T> 类型参数
   * @return 反序列化后的对象；若输入流为空返回 null
   * @throws JsonException 读取超限或 IO 错误
   * @since 26.09.01
   */
  public static <T> T toObject(InputStream in, long maxBytes, Class<T> clazz) {
    if (in == null) {
      return null;
    }
    try {
      byte[] bytes = readBoundedBytes(in, maxBytes);
      if (bytes.length == 0) {
        return null;
      }
      return fromJsonBytes(bytes, clazz);
    } catch (IOException e) {
      throw new JsonException("Failed to read from InputStream", e);
    }
  }

  /**
   * 从 InputStream 读取 JSON 并反序列化为指定泛型类型。
   *
   * @param in 输入流
   * @param typeRef 类型引用
   * @param <T> 类型参数
   * @return 反序列化后的对象
   * @since 26.09.01
   */
  public static <T> T toObject(InputStream in, JsonType<T> typeRef) {
    if (in == null) {
      return null;
    }
    try {
      byte[] bytes = readBoundedBytes(in, DEFAULT_MAX_INPUT_STREAM_SIZE);
      if (bytes.length == 0) {
        return null;
      }
      return fromJsonBytes(bytes, typeRef);
    } catch (IOException e) {
      throw new JsonException("Failed to read from InputStream", e);
    }
  }

  /**
   * 从输入流中读取最多 maxBytes 字节，超限时立即抛 IOException。
   *
   * <p>对标 JsonHttpMessageConverter.readBoundedBytes()——有界读取防御 OOM， 与 HTTP 层的安全策略一致，适用于任意
   * InputStream 场景。
   *
   * @param input 输入流
   * @param maxBytes 最大允许读取字节数
   * @return 读取的字节数组（长度不超过 maxBytes）
   * @throws IOException 读取失败或超过大小限制
   * @since 26.09.01
   */
  private static byte[] readBoundedBytes(InputStream input, long maxBytes) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
    byte[] chunk = new byte[8192];
    long totalRead = 0;
    int n;
    while ((n = input.read(chunk)) != -1) {
      totalRead += n;
      if (totalRead > maxBytes) {
        throw new IOException(
            "InputStream exceeds maximum size: "
                + maxBytes
                + " (read "
                + totalRead
                + " bytes so far)");
      }
      buffer.write(chunk, 0, n);
    }
    return buffer.toByteArray();
  }

  // ==================== 预热 ====================

  /**
   * 预热指定类型的序列化/反序列化缓存。
   *
   * <p>在应用启动时调用，提前为指定类型构建字段元数据、序列化器与反序列化器缓存， 避免首次请求时的延迟尖峰。预热失败不影响启动（内部吞掉异常）。
   *
   * <p>注：原 字节码生成（AsmCodecCache）已随 {@code AsmCodecCache} 移除， 现改为通过一次真实的序列化/反序列化触发 {@code
   * SerializationProvider} / {@code DeserializationProvider} 的元数据缓存构建，语义保持一致。
   *
   * @param classes 需要预热的类型列表
   * @since 26.09.01
   */
  public static void warmup(Class<?>... classes) {
    if (classes == null || classes.length == 0) {
      return;
    }
    for (Class<?> clazz : classes) {
      if (clazz == null) {
        continue;
      }
      try {
        // 触发序列化侧缓存构建（字段元数据 / BeanSerializerInfo / BeanSerializer）
        Object instance = createInstanceForWarmup(clazz);
        if (instance != null) {
          defaultMapper.toJson(instance);
        }
      } catch (Exception e) {
        // 预热失败不影响启动，但记录 WARN 便于诊断（如缺无参构造的类）
        LOGGER.warn("YdszJson warmup serialize failed for class: {}", clazz.getName(), e);
      }
      try {
        // 触发反序列化侧缓存构建（BeanReader / Creator 解析）
        defaultMapper.toObject("{}", clazz);
      } catch (Exception e) {
        // 预热失败不影响启动，但记录 WARN 便于诊断
        LOGGER.warn("YdszJson warmup deserialize failed for class: {}", clazz.getName(), e);
      }
    }
  }

  /** 为预热创建实例（仅支持无参构造的可实例化类型，否则返回 null）。 */
  private static Object createInstanceForWarmup(Class<?> clazz) {
    if (clazz.isInterface()
        || clazz.isEnum()
        || clazz.isArray()
        || clazz.isPrimitive()
        || Modifier.isAbstract(clazz.getModifiers())) {
      return null;
    }
    try {
      Constructor<?> constructor = clazz.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (Exception e) {
      return null;
    }
  }

  // ==================== 安全检查 ====================

  /**
   * 校验 JSON 字符串大小是否超过全局限制。
   *
   * @param json JSON 字符串
   * @throws JsonException 如果超过最大 JSON 大小限制
   */
  static void validateJsonSize(String json) {
    if (json == null) {
      return;
    }
    // 使用预计算运行时配置避免每次查询全局 JsonConfig
    long maxSize = defaultMapper.getRuntimeConfig().maxJsonSize();
    if (json.length() > maxSize) {
      throw new JsonException("JSON size exceeds limit: " + json.length() + " > " + maxSize);
    }
  }

  /**
   * 校验 JSON 字节数组大小是否超过全局限制。
   *
   * @param byteLength JSON 字节数组长度
   * @throws JsonException 如果超过最大 JSON 大小限制
   */
  static void validateJsonSizeBytes(int byteLength) {
    // 使用预计算运行时配置避免每次查询全局 JsonConfig
    long maxSize = defaultMapper.getRuntimeConfig().maxJsonSize();
    if (byteLength > maxSize) {
      throw new JsonException("JSON size exceeds limit: " + byteLength + " > " + maxSize);
    }
  }

  /**
   * 校验字符串是否为合法 JSON（解析成功即为合法）。
   *
   * <p>内部通过尝试解析实现，适合在校验场景使用，不抛出异常。
   *
   * @param json 待校验字符串
   * @return true 如果字符串为合法 JSON
   * @since 26.09.01
   */
  public static boolean isValidJson(String json) {
    if (json == null || json.isEmpty()) {
      return false;
    }
    try {
      JSONReader reader = new JSONReader(json);
      reader.skipValue();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 清理当前线程的全部 JSON 相关 ThreadLocal 状态（P1-7）。
   *
   * <p><b>使用场景：</b>Web 请求线程由 {@code JsonHttpMessageConverter} 自动清理； 但 MQ 消费者、定时任务、RPC 工作线程等非 Web
   * 线程池调用 {@code YdszJson} 静态方法后，池化缓冲区（char[]、StringBuilder、JSONReader） 与配置覆盖会常驻每条线程（约数十
   * KB/线程）。此类线程应在任务边界 （任务 finally 或线程归还前）调用本方法主动回收：
   *
   * <pre>{@code
   * executor.execute(() -> {
   *     try {
   *         handleMessage(YdszJson.fromJson(payload, Message.class));
   *     } finally {
   *         YdszJson.cleanupThread();  // 归还线程前回收常驻缓冲
   *     }
   * });
   * }</pre>
   *
   * <p>清理内容包括：序列化/反序列化上下文、字段命名策略、解析缓冲池、 读取器池、深度覆盖与精度模式覆盖。调用后本线程后续 JSON 调用行为不变 （均会按默认值重新初始化）。正在进行的嵌套
   * JSON 调用栈内禁止调用。
   *
   * @since 26.09.01
   */
  public static void cleanupThread() {
    SerializationProvider.clearThreadLocals();
  }

  // ==================== JSON Patch (RFC 6902) ====================

  /**
   * 解析 JSON Patch 操作列表。
   *
   * <p>JSON Patch 格式示例：
   *
   * <pre>
   * [
   *   {"op": "replace", "path": "/name", "value": "newName"},
   *   {"op": "remove", "path": "/age"},
   *   {"op": "add", "path": "/email", "value": "test@example.com"}
   * ]
   * </pre>
   *
   * @param patchJson Patch JSON 数组字符串
   * @return Patch 操作列表
   * @since 26.09.01
   * @see com.njydsz.common.json.tree.JsonPatch#parse(String)
   */
  public static List<PatchOp> parsePatch(String patchJson) {
    return JsonPatch.parse(patchJson);
  }

  /**
   * 应用 JSON Patch (RFC 6902) 到目标对象，返回新对象。
   *
   * @param patchJson Patch JSON 数组字符串
   * @param target 目标对象（不会被修改）
   * @param clazz 目标类型
   * @param <T> 目标类型参数
   * @return Patch 后的新对象
   * @since 26.09.01
   */
  public static <T> T applyPatch(String patchJson, T target, Class<T> clazz) {
    return JsonPatch.apply(patchJson, target, clazz);
  }

  /**
   * 应用 JSON Merge Patch (RFC 7396) 到目标对象，返回新对象。
   *
   * <p>Merge Patch 更简单的语义：null 值表示删除字段，其他值替换或添加。
   *
   * @param mergeJson Merge Patch JSON 字符串，如 {"name":"new","age":null}
   * @param target 目标对象（不会被修改）
   * @param clazz 目标类型
   * @param <T> 目标类型参数
   * @return Patch 后的新对象
   * @since 26.09.01
   */
  public static <T> T applyMergePatch(String mergeJson, T target, Class<T> clazz) {
    return JsonPatch.applyMerge(mergeJson, target, clazz);
  }
}
