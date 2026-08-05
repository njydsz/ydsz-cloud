package com.remisoft.common.json.provider;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
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
import java.util.concurrent.ConcurrentHashMap;

import com.remisoft.common.json.autotype.AutoTypeChecker;
import com.remisoft.common.json.annotation.JsonDeserialize;
import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.deserializer.JsonDeserializer;
import com.remisoft.common.json.exception.JsonDeserializationException;
import com.remisoft.common.json.parser.JsonParserUtil;
import com.remisoft.common.json.reader.JSONReader;

/**
 * RemiJson 反序列化提供者（零拷贝优化版）
 *
 * <p>架构层级：RemiJson => Provider => Parser</p>
 *
 * <p><b>核心优化：</b></p>
 * <ul>
 *   <li>零拷贝反序列化 - 直接解析 JSON 到对象字段，消除 Map 中转</li>
 *   <li>Constructor 缓存 - 避免每次反射获取</li>
 *   <li>HashMap 字段查找 - O(1) 替代 O(n)</li>
 *   <li>快速路径 - 简单对象（基本类型字段）直接内联解析</li>
 *   <li>JsonType 支持 - 泛型类型推断</li>
 *   <li>Builder 模式支持 - 链式构建对象</li>
 *   <li>Creator 模式支持 - 自定义构造函数反序列化</li>
 *   <li>多态类型支持 - @JsonTypeInfo 自动识别子类型</li>
 * </ul>
 *
 * <p><b>反序列化流程：</b></p>
 * <ol>
 *   <li>类型安全检查 - AutoTypeChecker 白名单/黑名单校验</li>
 *   <li>快速路径分派 - 基本类型直接解析，其余走 BeanDeserializerEngine</li>
 *   <li>执行解析 - ASM/BeanReader/Creator/Builder/ZeroCopy 多级降级</li>
 *   <li>类型转换 - 处理数字、字符串、日期等类型转换</li>
 * </ol>
 *
 * @author remi-team
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public final class DeserializationProvider {

    private DeserializationProvider() {
        throw new UnsupportedOperationException();
    }

    /**
     * 从 UTF-8 字节数组反序列化（ASCII 快速路径）。
     *
     * <p>先扫描字节流判断是否为纯 ASCII：如果是，直接逐字节转 char[] 构造 String，
     * 跳过 UTF-8 解码开销；非 ASCII 则回退 {@code new String(bytes, UTF_8)}。</p>
     *
     * <p>对标 FastJSON2 {@code JSON.parseObject(byte[], Class)} 和 Jackson
     * {@code ObjectMapper.readValue(byte[], Class)} 的 byte[] 直接入参 API。</p>
     *
     * @param bytes UTF-8 编码的 JSON 字节数组
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 反序列化后的对象，bytes 为空时返回 null
     * @since 1.0.0
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
     * @since 1.0.0
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
     * <p>调用方用此方法可保留泛型推断 {@code <T>}，内部委托 {@link #deserializeToObject(byte[], Type)}
     * 并作 checked cast。unchecked 警告集中在桥接方法内（单处 {@code @SuppressWarnings}）。</p>
     *
     * @param bytes UTF-8 编码的 JSON 字节数组
     * @param type  目标类型（{@link Class} 或 {@link java.lang.reflect.ParameterizedType}）
     * @param <T>   类型参数
     * @return 反序列化后的对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(byte[] bytes, Type type) {
        return (T) deserializeToObject(bytes, type);
    }

    /**
     * 泛型桥接：从 JSON 字符串反序列化为指定 Type。
     *
     * <p>调用方用此方法可保留泛型推断，内部委托 {@link #deserializeToObject(String, Type)}。</p>
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T>  类型参数
     * @return 反序列化后的对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(String json, Type type) {
        return (T) deserializeToObject(json, type);
    }

    /**
     * ASCII 快速路径：扫描字节流，若全为 ASCII（&lt; 128）则直接逐字节转 char[] 构造 String，
     * 跳过 UTF-8 解码开销；非 ASCII 回退 {@code new String(bytes, UTF_8)}。
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
            if (bytes[i] < 0) { ascii = false; break; }
        }
        // 如果前 64 字节为 ASCII，继续扫描剩余部分
        if (ascii) {
            for (int i = scanLen; i < len; i++) {
                if (bytes[i] < 0) { ascii = false; break; }
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
     * <p>防止恶意构造的嵌套泛型 JSON（如 {@code List<List<List<...>>>}）导致
     * {@code deserializeToObject} 无限递归引发 StackOverflowError。
     * 默认最大深度 64（对标 FastJSON2 maxTypeRecursionDepth=100），可通过
     * {@link JSONReader#setMaxGenericDepth(int)} 调整。
     */
    private static final ThreadLocal<Integer> DESERIALIZE_DEPTH =
        ThreadLocal.withInitial(() -> 0);

    /**
     * @JsonDeserialize 自定义反序列化器缓存（Class -> JsonDeserializer 实例）。
     */
    private static final ConcurrentHashMap<Class<?>, JsonDeserializer<?>> CUSTOM_DESERIALIZER_CACHE =
        new ConcurrentHashMap<>();

    /**
     * 自定义反序列化器 deserialize 方法缓存（Class -> Method）。
     *
     * <p>由于 {@code JsonDeserializer<?>} 的 {@code deserialize(String, Class<?>)} 方法
     * 无法直接以 {@code Class<T>} 参数调用（泛型类型参数 ? 未知），
     * 使用 {@link Method#invoke(Object, Object...)} 进行反射调用，
     * 避免 unchecked cast。{@code Method.invoke} 返回 {@code Object}，
     * 调用方通过 {@code clazz.cast()} 执行 checked cast。</p>
     */
    private static final ConcurrentHashMap<Class<?>, Method> DESERIALIZE_METHOD_CACHE =
        new ConcurrentHashMap<>();

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
        JsonDeserializer<?> cached = CUSTOM_DESERIALIZER_CACHE.get(clazz);
        if (cached != null) {
            return cached;
        }
        try {
            JsonDeserializer<?> instance = (JsonDeserializer<?>) annotation.using().getDeclaredConstructor().newInstance();
            CUSTOM_DESERIALIZER_CACHE.putIfAbsent(clazz, instance);
            return instance;
        } catch (Exception e) {
            throw new JsonDeserializationException(
                "Failed to instantiate custom deserializer: " + annotation.using().getName(),
                e
            );
        }
    }

    /**
     * 通过反射调用自定义反序列化器的 deserialize 方法。
     *
     * <p>使用 {@link Method#invoke(Object, Object...)} 避免泛型 unchecked cast。
     * {@code Method.invoke} 返回 {@code Object}，调用方通过 {@code Class.cast()} 执行 checked cast。</p>
     *
     * @param deserializer 自定义反序列化器实例
     * @param json JSON 字符串
     * @param type 目标类型
     * @return 反序列化后的对象
     */
    private static Object invokeCustomDeserializer(Object deserializer, String json, Class<?> type) {
        // 新版接口（deserializer.JsonDeserializer）：直接使用 JSONReader 流式解析，零拷贝
        if (deserializer instanceof com.remisoft.common.json.deserializer.JsonDeserializer) {
            JSONReader reader = new JSONReader(json);
            return ((com.remisoft.common.json.deserializer.JsonDeserializer<Object>) deserializer)
                .deserialize(reader);
        }
        // 旧版接口（api.JsonDeserializer，deprecated）：接收完整 JSON 字符串
        Method method = DESERIALIZE_METHOD_CACHE.computeIfAbsent(deserializer.getClass(), cls -> {
            try {
                Method m = cls.getMethod("deserialize", String.class, Class.class);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                throw new JsonDeserializationException(
                    "deserialize method not found on " + cls.getName()
                        + " (neither new deserializer.JsonDeserializer nor old api.JsonDeserializer)",
                    e
                );
            }
        });
        try {
            return method.invoke(deserializer, json, type);
        } catch (Exception e) {
            throw new JsonDeserializationException(
                "Custom deserializer failed: " + deserializer.getClass().getName(),
                e
            );
        }
    }

    /**
     * 反序列化 JSON 字符串（零拷贝优化版）
     */
    public static <T> T deserialize(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        try {
            // @JsonDeserialize 快速路径：如果类有自定义反序列化器，直接使用
            Object customDeserializer = getCustomDeserializer(clazz);
            if (customDeserializer == null) {
                // 模块注册 / 全局注册 快速路径：JsonModule.SpringFactory 或 RemiJson.register(...) 注册的反序列化器
                customDeserializer = RemiJson.getRegisteredDeserializer(clazz);
            }
            if (customDeserializer != null) {
                Object result = invokeCustomDeserializer(customDeserializer, json, clazz);
                return result != null ? clazz.cast(result) : null;
            }

            // 统一安全门控：AutoTypeChecker 作为唯一的类型安全检查入口
            AutoTypeChecker.checkType(clazz);

            Class<?> actualType = resolvePolymorphicType(json, clazz);
            if (actualType != clazz) {
                AutoTypeChecker.checkType(actualType);
            }

            // 深度限制由 JSONReader 在解析过程中通过 Feature.LimitDepth 实时维护，
            // 超阈值即抛 JsonDeserializationException，无需在此预扫描（原实现存在 O(n) 双重扫描
            // 且不区分字符串字面量中的 { } 的逻辑缺陷）
            Object result = deserializeValue(json, actualType);
            return result != null ? clazz.cast(result) : null;
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
                0, json);
        }
    }

    private static Object deserializeValue(String json, Class<?> type) {
        // 快速路径：基本类型直接判断（无需缓存查找开销）
        if (type == String.class) return TypeConverter.parseStringValue(json);
        if (type == Integer.class || type == int.class) return TypeConverter.parseIntValue(json);
        if (type == Long.class || type == long.class) return TypeConverter.parseLongValue(json);
        if (type == Double.class || type == double.class) return TypeConverter.parseDoubleValue(json);
        if (type == Float.class || type == float.class) return TypeConverter.parseFloatValue(json);
        if (type == Boolean.class || type == boolean.class) return TypeConverter.parseBooleanValue(json);
        if (type == Object.class) return parseValue(json);
        if (type == Map.class) return JsonParserUtil.parseObject(json);
        if (type == List.class) return BeanDeserializerEngine.deserializeArrayZeroCopy(json, Object.class);

        // Bean 类型：直接走 BeanDeserializerEngine 多级降级路径
        // （ASM -> BeanReader -> Creator -> Builder -> ZeroCopy -> Map 降级）
        // 注：原 STRATEGY_CACHE 已删除——所有非简单类型统一走 BEAN 路径，
        // if-else 链已覆盖所有简单类型，缓存无策略分派价值，synchronizedMap 反而是性能瓶颈。
        return BeanDeserializerEngine.deserializeBeanZeroCopyAsObject(json, type);
    }

    /**
     * 反序列化 JSON 字符串（带特性配置）
     *
     * <p><b>注意：</b>当前版本 {@code features} 参数仅用于 JSON 长度限制检查，
     * 其他 Feature 配置尚未实现，保留参数位置以便后续扩展。
     * 如需 AutoType 安全检查，请通过 {@link AutoTypeChecker#setSafeMode(boolean)} 全局配置。</p>
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
                "JSON length limit exceeded: " + json.length() + " > " + JSONReader.DEFAULT_MAX_JSON_LENGTH
            );
        }

        // 深度限制由 JSONReader 在解析过程中通过 Feature.LimitDepth 实时维护
        return deserialize(json, clazz);
    }

    /**
     * 解析多态类型
     *
     * <p>如果目标类有 @JsonTypeInfo 注解，则根据 JSON 中的类型属性值
     * 识别具体子类型并返回。</p>
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
     * <p>支持 {@link Class}、{@link ParameterizedType}（List/Map/Set 泛型）等类型。
     * 类型不匹配时立即抛出 {@link JsonDeserializationException}，包含期望类型和实际类型信息。</p>
     *
     * <p><b>返回 Object 而非泛型 T 的原因：</b>Java 泛型类型擦除导致从 {@link Type}
     * 到 {@code T} 的转换无法在编译期验证（unchecked cast）。返回 {@code Object}
     * 后由调用方通过 {@code Class.cast()} 执行运行时检查的 checked cast，
     * 从根源消除 unchecked 警告。</p>
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
            int maxDepth = JSONReader.getMaxGenericDepth();
            if (currentDepth >= maxDepth) {
                throw new JsonDeserializationException(
                    JsonDeserializationException.TYPE_MISMATCH,
                    "Generic deserialization depth exceeded: " + currentDepth + " >= " + maxDepth
                        + " (type: " + type + ")");
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
            AutoTypeChecker.checkType(clazz);
            Object result = deserializeValue(json, clazz);
            return result;
        }

        if (type instanceof GenericArrayType gat) {
            // 泛型数组类型（如 T[]）：先反序列化为 List，再转数组
            Type componentType = gat.getGenericComponentType();
            ParameterizedType listType = new ParameterizedType() {
                @Override public Type[] getActualTypeArguments() { return new Type[]{componentType}; }
                @Override public Type getRawType() { return List.class; }
                @Override public Type getOwnerType() { return null; }
            };
            List<?> list = (List<?>) deserializeToObject(json, listType);
            if (list == null) return null;
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
                    // 安全检查：校验容器元素类型，防止泛型路径绕过 AutoType 白名单
                    AutoTypeChecker.checkType(elementClass);
                    if (BeanDeserializerEngine.isSimpleType(elementClass)) {
                        return BeanDeserializerEngine.deserializeArrayZeroCopy(json, elementClass);
                    } else {
                        return BeanDeserializerEngine.deserializeBeanListFast(json, elementClass);
                    }
                }
            }

            if (rawType == Map.class || rawType == HashMap.class
                    || rawType == LinkedHashMap.class
                    || rawType == TreeMap.class) {
                Type[] typeArgs = pt.getActualTypeArguments();
                Map<String, Object> parsed = JsonParserUtil.parseObject(json);
                if (parsed == null) return null;
                // 当 value 类型为已知简单类型时，转换解析结果（如 Long → Integer）
                if (typeArgs.length == 2 && typeArgs[1] instanceof Class<?> valueClass) {
                    AutoTypeChecker.checkType(valueClass);
                    if (valueClass != Object.class) {
                        Map<String, Object> result = createMap(rawType);
                        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                            result.put(entry.getKey(),
                                TypeConverter.convertValue(entry.getValue(), valueClass));
                        }
                        return result;
                    }
                }
                return parsed;
            }

            if (rawType == Set.class || rawType == HashSet.class
                    || rawType == LinkedHashSet.class
                    || rawType == TreeSet.class) {
                Type elementType = pt.getActualTypeArguments()[0];
                if (elementType instanceof Class<?> elementClass) {
                    // 安全检查：校验 Set 元素类型
                    AutoTypeChecker.checkType(elementClass);
                    if (BeanDeserializerEngine.isSimpleType(elementClass)) {
                        List<?> list = BeanDeserializerEngine.deserializeArrayZeroCopy(json, elementClass);
                        if (list == null) return null;
                        return createSet(rawType, list);
                    } else {
                        List<?> list = BeanDeserializerEngine.deserializeBeanListFast(json, elementClass);
                        if (list == null) return null;
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

    /**
     * 根据 rawType 创建对应的 Map 实例。
     */
    private static Map<String, Object> createMap(Type rawType) {
        if (rawType == TreeMap.class) {
            return new TreeMap<>();
        } else if (rawType == LinkedHashMap.class) {
            return new LinkedHashMap<>();
        }
        return new HashMap<>();
    }
}
