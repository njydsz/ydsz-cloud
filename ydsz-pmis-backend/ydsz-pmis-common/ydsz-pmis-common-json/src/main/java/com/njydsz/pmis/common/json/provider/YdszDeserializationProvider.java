package com.njydsz.pmis.common.json.provider;

import com.njydsz.pmis.common.json.autotype.AutoTypeChecker;
import com.njydsz.pmis.common.json.config.DeserializationConfig;
import com.njydsz.pmis.common.json.exception.JsonDeserializationException;
import com.njydsz.pmis.common.json.reader.JSONReader;
import com.njydsz.pmis.common.json.parser.YdszJsonParser;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Remi 反序列化提供者（零拷贝优化版）
 *
 * <p>架构层级：YdszJson => Engine => Provider => Parser</p>
 *
 * <p><b>核心优化：</b></p>
 * <ul>
 *   <li>零拷贝反序列化 - 直接解析 JSON 到对象字段，消除 Map 中转</li>
 *   <li>Constructor 缓存 - 避免每次反射获取</li>
 *   <li>HashMap 字段查找 - O(1) 替代 O(n)</li>
 *   <li>快速路径 - 简单对象（≤4 字段）直接内联解析</li>
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
 *   <li>执行解析 - 调用 ZeroCopyDeserializer 或 YdszJsonParser</li>
 *   <li>类型转换 - 处理数字、字符串、日期等类型转换</li>
 * </ol>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@SuppressWarnings("unchecked")
public final class YdszDeserializationProvider {

    /**
     * 反序列化策略缓存（线程安全，避免每次反序列化都重新查找策略链）
     *
     * <p>缓存 Class -> DeserializationStrategy 的映射，类似于序列化端的 ASM 序列化器缓存。
     * 首次反序列化某类型时，会遍历策略链（ASM -> BeanReader -> Creator -> Builder -> ZeroCopy），
     * 找到可用策略后缓存，后续直接使用缓存策略，跳过策略选择开销。</p>
     */
    private static final ConcurrentHashMap<Class<?>, DeserializationStrategy> STRATEGY_CACHE =
        new ConcurrentHashMap<>(256);

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
        /** Bean 类型 - 走 BeanDeserializerEngine */
        BEAN
    }

    private YdszDeserializationProvider() {
        throw new UnsupportedOperationException();
    }

    /**
     * 反序列化 JSON 字符串（零拷贝优化版）
     */
    
    public static <T> T deserialize(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        AutoTypeChecker.checkType(clazz);

        DeserializationConfig config = DeserializationConfig.getInstance();
        if (config.isWhitelistEnabled() && !config.isTypeAllowed(clazz.getName())) {
            throw new JsonDeserializationException(
                JsonDeserializationException.PARSE_ERROR,
                "Deserialization type not allowed: " + clazz.getName()
            );
        }

        Class<?> actualType = resolvePolymorphicType(json, clazz);

        if (config.isWhitelistEnabled() && !config.isTypeAllowed(actualType.getName())) {
            throw new JsonDeserializationException(
                JsonDeserializationException.PARSE_ERROR,
                "Resolved polymorphic type not allowed: " + actualType.getName()
            );
        }

        if (config.getMaxDepth() < DeserializationConfig.DEFAULT_MAX_DEPTH) {
            validateDepth(json, config.getMaxDepth());
        }

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
            strategy = DeserializationStrategy.BEAN; // 非 PRIMITIVE/OBJECT/MAP/LIST 的都是 BEAN
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

        if (JSONReader.Feature.LimitDepth.isEnabled(features)) {
            validateDepth(json, JSONReader.DEFAULT_MAX_DEPTH);
        }

        return deserialize(json, clazz);
    }

    /**
     * 验证 JSON 深度（防止栈溢出攻击）
     */
    private static void validateDepth(String json, int maxDepth) {
        int depth = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{' || c == '[') {
                depth++;
                if (depth > maxDepth) {
                    throw new JsonDeserializationException(
                        JsonDeserializationException.PARSE_ERROR,
                        "JSON depth limit exceeded: " + depth + " > " + maxDepth
                    );
                }
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
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
                    if (BeanDeserializerEngine.isSimpleType(elementClass)) {
                        return captureType(BeanDeserializerEngine.deserializeArrayZeroCopy(json, elementClass));
                    } else {
                        return captureType(BeanDeserializerEngine.deserializeBeanListFast(json, elementClass));
                    }
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
