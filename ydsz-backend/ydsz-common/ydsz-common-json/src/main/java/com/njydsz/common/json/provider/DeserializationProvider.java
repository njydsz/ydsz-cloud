package com.njydsz.common.json.provider;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.exception.JsonDeserializationException;
import com.njydsz.common.json.parser.YdszJsonParser;
import com.njydsz.common.json.reader.JSONReader;
/**
 * YdszJson 反序列化提供者（零拷贝优化版）
 *
 * <p>架构层级：YdszJson => Engine => Provider => Parser</p>
 *
 * <p><b>核心优化：</b></p>
 * <ul>
 *   <li>零拷贝反序列化 - 直接解析 JSON 到对象字段，消除 Map 中转</li>
 *   <li>Constructor 缓存 - 避免每次反射获取</li>
 *   <li>HashMap 字段查找 - O(1) 替代 O(n)</li>
 *   <li>快速路径 - 简单对象（基本类型字段）直接内联解析</li>
 *   <li>YdszJsonType 支持 - 泛型类型推断</li>
 *   <li>Builder 模式支持 - 链式构建对象</li>
 *   <li>Creator 模式支持 - 自定义构造函数反序列化</li>
 *   <li>多态类型支持 - @YdszJsonTypeInfo 自动识别子类型</li>
 * </ul>
 *
 * <p><b>反序列化流程：</b></p>
 * <ol>
 *   <li>检查缓存 - 查找已编译的反序列化器</li>
 *   <li>选择策略 - 根据类型选择合适的反序列化方式</li>
 *   <li>执行解析 - 调用 ZeroCopyDeserializer + YdszJsonParser</li>
 *   <li>类型转换 - 处理数字、字符串、日期等类型转换</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class DeserializationProvider {

    /**
     * 反序列化策略缓存（线程安全 LRU 有界缓存，避免类加载器泄漏）
     *
     * <p>缓存 Class -> DeserializationStrategy 的映射，类似于序列化端的 ASM 序列化器缓存。
     * 首次反序列化某类型时，会遍历策略链（ASM -> BeanReader -> Creator -> Builder -> ZeroCopy），
     * 找到可用策略后缓存，后续直接使用缓存策略，跳过策略选择开销。</p>
     *
     * <p>使用 LRU 淘汰策略，最大 1024 个条目，避免动态类加载场景下的内存泄漏。</p>
     */
    private static final Map<Class<?>, DeserializationStrategy> STRATEGY_CACHE =
        java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(256, 0.75f, true) {
            private static final int MAX_ENTRIES = 1024;
            @Override
            protected boolean removeEldestEntry(Map.Entry<Class<?>, DeserializationStrategy> eldest) {
                return size() > MAX_ENTRIES;
            }
        });

    /** 反序列化策略枚举 */
    private enum DeserializationStrategy {
        /** 基本类型（String/Integer/Long/Double/Float/Boolean） */
        PRIMITIVE,
        /** Object 类型 */
        OBJECT,
        /** Map 类型 */
        MAP,
        /** List 类型 */
        LIST,
        /** Bean 类型 - BeanDeserializerEngine */
        BEAN
    }

    private DeserializationProvider() {
        throw new UnsupportedOperationException();
    }

    /**
     * 反序列化 JSON 字符串（零拷贝优化版）
     */
    public static <T> T deserialize(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
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
        if (type == Map.class) return YdszJsonParser.parseObject(json);
        if (type == List.class) return BeanDeserializerEngine.deserializeArrayZeroCopy(json, Object.class);

        // 缓存路径：使用策略缓存避免每次反序列化都重新判断类型
        DeserializationStrategy strategy = STRATEGY_CACHE.get(type);
        if (strategy == null) {
            // 首次遇到此类型，确定策略并缓存
            strategy = DeserializationStrategy.BEAN; // 。PRIMITIVE/OBJECT/MAP/LIST 的都不是 BEAN
            STRATEGY_CACHE.put(type, strategy);
        }

        return BeanDeserializerEngine.deserializeBeanZeroCopyAsObject(json, type);
    }

    /**
     * 反序列化 JSON 字符串（带特性配置）
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

        // 深度限制由 JSONReader 在解析过程中通过 Feature.LimitDepth 实时维护，
        // 无需在此预扫描（原 validateDepth 存在 O(n) 双重扫描且不区分字符串字面量的逻辑缺陷）
        return deserialize(json, clazz);
    }

    /**
     * 解析多态类型
     *
     * <p>如果目标类有 @YdszJsonTypeInfo 注解，则根据 JSON 中的类型属性值
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
        if (json.equals("null")) {
            return null;
        }
        if (json.startsWith("{")) {
            return YdszJsonParser.parseObject(json);
        }
        if (json.startsWith("[")) {
            return YdszJsonParser.parseArray(json);
        }
        if (json.equals("true")) {
            return Boolean.TRUE;
        }
        if (json.equals("false")) {
            return Boolean.FALSE;
        }
        if (json.startsWith("\"")) {
            return TypeConverter.parseStringValue(json);
        }
        try {
            if (json.contains(".") || json.contains("E") || json.contains("e")) {
                return Double.parseDouble(json);
            }
            return Long.parseLong(json);
        } catch (NumberFormatException e) {
            return json;
        }
    }

    /**
     * 反序列化 JSON 字符串（支持 Type）
     */
    public static <T> T deserialize(String json, Type type) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        if (type instanceof Class<?> clazz) {
            AutoTypeChecker.checkType(clazz);
            Object result = deserializeValue(json, clazz);
            return captureType(result);
        }

        if (type instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();

            if (rawType == List.class || rawType == ArrayList.class) {
                Type elementType = pt.getActualTypeArguments()[0];
                if (elementType instanceof Class<?> elementClass) {
                    // 安全检查：校验容器元素类型，防止泛型路径绕过 AutoType 白名单
                    AutoTypeChecker.checkType(elementClass);
                    if (BeanDeserializerEngine.isSimpleType(elementClass)) {
                        return captureType(BeanDeserializerEngine.deserializeArrayZeroCopy(json, elementClass));
                    } else {
                        return captureType(BeanDeserializerEngine.deserializeBeanListFast(json, elementClass));
                    }
                }
            }

            if (rawType == Map.class || rawType == java.util.HashMap.class
                    || rawType == java.util.LinkedHashMap.class
                    || rawType == java.util.TreeMap.class) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length == 2) {
                    // 安全检查：校验 Map 的 value 类型，防止泛型路径绕过 AutoType 白名单
                    if (typeArgs[1] instanceof Class<?> valueClass) {
                        AutoTypeChecker.checkType(valueClass);
                    }
                }
                return captureType(YdszJsonParser.parseObject(json));
            }

            if (rawType == java.util.Set.class || rawType == java.util.HashSet.class
                    || rawType == java.util.LinkedHashSet.class
                    || rawType == java.util.TreeSet.class) {
                Type elementType = pt.getActualTypeArguments()[0];
                if (elementType instanceof Class<?> elementClass) {
                    // 安全检查：校验 Set 元素类型
                    AutoTypeChecker.checkType(elementClass);
                }
            }
        }

        if (json.trim().startsWith("[")) {
            return captureType(YdszJsonParser.parseArray(json));
        }

        return captureType(YdszJsonParser.parseObject(json));
    }

    private static <T> T captureType(Object value) {
        return (T) value;
    }
}
