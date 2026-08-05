package com.remisoft.common.json.provider;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.remisoft.common.json.annotation.JsonTypeName;
import com.remisoft.common.json.annotation.JsonSubType;
import com.remisoft.common.json.annotation.JsonSubTypes;
import com.remisoft.common.json.annotation.JsonTypeInfo;
import com.remisoft.common.json.autotype.AutoTypeChecker;

/**
 * 多态类型解析器
 *
 * <p>处理带 @JsonTypeInfo 注解的基类的反序列化，
 * 根据 JSON 中的类型属性值识别具体子类型。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 基类定义
 * &#064;JsonTypeInfo(property = "type")
 * &#064;JsonSubTypes({
 *     &#064;JsonSubType(value = Dog.class, name = "dog"),
 *     &#064;JsonSubType(value = Cat.class, name = "cat")
 * })
 * public abstract class Animal { }
 *
 * // 反序列化
 * String json = "{\"type\":\"dog\",\"name\":\"Buddy\"}";
 * Animal animal = RemiJson.toObject(json, Animal.class);
 * // animal 是 Dog 实例
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public final class PolymorphicTypeResolver {

    /** 类型映射缓存 */
    private static final ConcurrentHashMap<Class<?>, TypeMapping> TYPE_MAPPING_CACHE = new ConcurrentHashMap<>();

    /** 默认类型属性名（预留扩展） */
    static final String DEFAULT_TYPE_PROPERTY = "type";

    private PolymorphicTypeResolver() {
        throw new UnsupportedOperationException();
    }

    /**
     * 类型映射
     */
    private static final class TypeMapping {
        final String typeProperty;
        final boolean visible;
        final JsonTypeInfo.As includeAs;
        final Map<String, Class<?>> nameToType;

        TypeMapping(String typeProperty, boolean visible, JsonTypeInfo.As includeAs, Map<String, Class<?>> nameToType) {
            this.typeProperty = typeProperty;
            this.visible = visible;
            this.includeAs = includeAs;
            this.nameToType = nameToType;
        }

        boolean isVisible() { return visible; }
        JsonTypeInfo.As getIncludeAs() { return includeAs; }
        Class<?> resolveType(String typeName) { return nameToType.get(typeName); }
    }

    /**
     * 获取或创建类型映射
     *
     * <p>支持两种多态发现机制：</p>
     * <ol>
     *   <li>注解驱动：通过 @JsonTypeInfo + @JsonSubTypes 显式声明子类型</li>
     *   <li>密封类自动发现：Java 17+ sealed class 通过 getPermittedSubclasses() 自动注册子类型
     *       （使用简单类名作为类型判别值）</li>
     * </ol>
     *
     * @param clazz 基类
     * @return 类型映射，如果不支持多态返回 null
     */
    public static TypeMapping getTypeMapping(Class<?> clazz) {
        TypeMapping cached = TYPE_MAPPING_CACHE.get(clazz);
        if (cached != null) {
            return cached;
        }

        JsonTypeInfo typeInfo = clazz.getAnnotation(JsonTypeInfo.class);

        // 路径 1：注解驱动多态
        if (typeInfo != null) {
            JsonSubTypes subTypes = clazz.getAnnotation(JsonSubTypes.class);
            if (subTypes != null) {
                Map<String, Class<?>> nameToType = new HashMap<>(subTypes.value().length * 2);
                for (JsonSubType subType : subTypes.value()) {
                    // Jackson 兼容：子类上的 @JsonTypeName 优先于 @JsonSubType.name()
                    String typeName = subType.name();
                    JsonTypeName jsonTypeName = subType.value().getAnnotation(JsonTypeName.class);
                    if (jsonTypeName != null && !jsonTypeName.value().isEmpty()) {
                        typeName = jsonTypeName.value();
                    }
                    nameToType.put(typeName, subType.value());
                }
                TypeMapping mapping = new TypeMapping(typeInfo.property(), typeInfo.visible(),
                    typeInfo.include(), nameToType);
                TYPE_MAPPING_CACHE.put(clazz, mapping);
                return mapping;
            }
        }

        // 路径 2：密封类自动发现（Java 17+）
        if (clazz.isSealed()) {
            Class<?>[] permitted = clazz.getPermittedSubclasses();
            if (permitted != null && permitted.length > 0) {
                Map<String, Class<?>> nameToType = new HashMap<>(permitted.length * 2);
                for (Class<?> subType : permitted) {
                    // Jackson 兼容：优先使用 @JsonTypeName，回退到简单类名
                    JsonTypeName jsonTypeName = subType.getAnnotation(JsonTypeName.class);
                    String typeName = (jsonTypeName != null && !jsonTypeName.value().isEmpty())
                            ? jsonTypeName.value() : subType.getSimpleName();
                    nameToType.put(typeName, subType);
                }
                String property = typeInfo != null ? typeInfo.property() : DEFAULT_TYPE_PROPERTY;
                boolean visible = typeInfo != null && typeInfo.visible();
                JsonTypeInfo.As includeAs = typeInfo != null ? typeInfo.include() : JsonTypeInfo.As.PROPERTY;
                TypeMapping mapping = new TypeMapping(property, visible, includeAs, nameToType);
                TYPE_MAPPING_CACHE.put(clazz, mapping);
                return mapping;
            }
        }

        return null;
    }

    /**
     * 从 JSON 中提取类型属性值
     *
     * @param json JSON 字符串
     * @param typeProperty 类型属性名
     * @return 类型值，如果不存在返回 null
     */
    public static String extractTypeValue(String json, String typeProperty) {
        int propStart = json.indexOf("\"" + typeProperty + "\"");
        if (propStart < 0) {
            return null;
        }

        int colonPos = json.indexOf(':', propStart + typeProperty.length() + 1);
        if (colonPos < 0) {
            return null;
        }

        int valueStart = colonPos + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
            return null;
        }

        int valueEnd = json.indexOf('"', valueStart + 1);
        if (valueEnd < 0) {
            return null;
        }

        return json.substring(valueStart + 1, valueEnd);
    }

    /**
     * 解析具体子类型
     *
     * @param json JSON 字符串
     * @param baseType 基类
     * @return 具体子类型，如果无法解析返回基类
     */
    public static Class<?> resolveType(String json, Class<?> baseType) {
        TypeMapping mapping = getTypeMapping(baseType);
        if (mapping == null) {
            return baseType;
        }

        String typeName = extractTypeValue(json, mapping.typeProperty);
        if (typeName == null) {
            return baseType;
        }

        Class<?> resolvedType = mapping.resolveType(typeName);
        if (resolvedType != null) {
            AutoTypeChecker.checkType(resolvedType);
            return resolvedType;
        }
        return baseType;
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        TYPE_MAPPING_CACHE.clear();
    }
}
