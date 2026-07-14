package com.njydsz.pmis.common.json.provider;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.pmis.common.json.annotation.YdszJsonSubType;
import com.njydsz.pmis.common.json.annotation.YdszJsonSubTypes;
import com.njydsz.pmis.common.json.annotation.YdszJsonTypeInfo;
import com.njydsz.pmis.common.json.autotype.AutoTypeChecker;

/**
 * 多态类型解析器
 *
 * <p>处理带 @YdszJsonTypeInfo 注解的基类的反序列化，
 * 根据 JSON 中的类型属性值识别具体子类型。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 基类定义
 * &#064;YdszJsonTypeInfo(property = "type")
 * &#064;YdszJsonSubTypes({
 *     &#064;YdszJsonSubType(value = Dog.class, name = "dog"),
 *     &#064;YdszJsonSubType(value = Cat.class, name = "cat")
 * })
 * public abstract class Animal { }
 *
 * // 反序列化
 * String json = "{\"type\":\"dog\",\"name\":\"Buddy\"}";
 * Animal animal = YdszJson.toObject(json, Animal.class);
 * // animal 是 Dog 实例
 * </pre>
 *
 * @author ydsz-pmis-team
 * @email limw1888@126.com
 * @since 1.3.0
 */
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
        final Map<String, Class<?>> nameToType;

        TypeMapping(String typeProperty, boolean visible, Map<String, Class<?>> nameToType) {
            this.typeProperty = typeProperty;
            this.visible = visible;
            this.nameToType = nameToType;
        }

        boolean isVisible() {
            return visible;
        }

        Class<?> resolveType(String typeName) {
            return nameToType.get(typeName);
        }
    }

    /**
     * 获取或创建类型映射
     *
     * @param clazz 基类
     * @return 类型映射，如果不支持多态返回 null
     */
    public static TypeMapping getTypeMapping(Class<?> clazz) {
        TypeMapping cached = TYPE_MAPPING_CACHE.get(clazz);
        if (cached != null) {
            return cached;
        }

        YdszJsonTypeInfo typeInfo = clazz.getAnnotation(YdszJsonTypeInfo.class);
        if (typeInfo == null) {
            return null;
        }

        YdszJsonSubTypes subTypes = clazz.getAnnotation(YdszJsonSubTypes.class);
        if (subTypes == null) {
            return null;
        }

        Map<String, Class<?>> nameToType = new HashMap<>(subTypes.value().length * 2);
        for (YdszJsonSubType subType : subTypes.value()) {
            nameToType.put(subType.name(), subType.value());
        }

        TypeMapping mapping = new TypeMapping(typeInfo.property(), typeInfo.visible(), nameToType);
        TYPE_MAPPING_CACHE.put(clazz, mapping);
        return mapping;
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
