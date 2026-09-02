package com.njydsz.common.json.provider;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.json.annotation.JsonSubType;
import com.njydsz.common.json.annotation.JsonSubTypes;
import com.njydsz.common.json.annotation.JsonTypeInfo;
import com.njydsz.common.json.annotation.JsonTypeName;
import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.util.BoundedLruCache;

/**
 * 多态类型解析器
 *
 * <p>处理带 @JsonTypeInfo 注解的基类的反序列化， 根据 JSON 中的类型属性值识别具体子类型。
 *
 * <p><b>使用示例：</b>
 *
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
 * Animal animal = YdszJson.fromJson(json, Animal.class);
 * // animal 是 Dog 实例
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SuppressWarnings("deprecation")
public final class PolymorphicTypeResolver {

  /** 类型映射缓存（有界 LRU，容量 256） */
  private static final BoundedLruCache<Class<?>, TypeMapping> TYPE_MAPPING_CACHE =
      new BoundedLruCache<>(256);

  /** 默认类型属性名（预留扩展） */
  static final String DEFAULT_TYPE_PROPERTY = "type";

  private PolymorphicTypeResolver() {
    throw new UnsupportedOperationException();
  }

  /** 类型映射 */
  private static final class TypeMapping {
    final String typeProperty;
    final boolean visible;
    final JsonTypeInfo.As includeAs;
    final Map<String, Class<?>> nameToType;

    TypeMapping(
        String typeProperty,
        boolean visible,
        JsonTypeInfo.As includeAs,
        Map<String, Class<?>> nameToType) {
      this.typeProperty = typeProperty;
      this.visible = visible;
      this.includeAs = includeAs;
      this.nameToType = nameToType;
    }

    boolean isVisible() {
      return visible;
    }

    JsonTypeInfo.As getIncludeAs() {
      return includeAs;
    }

    Class<?> resolveType(String typeName) {
      return nameToType.get(typeName);
    }
  }

  /**
   * 获取或创建类型映射
   *
   * <p>支持两种多态发现机制：
   *
   * <ol>
   *   <li>注解驱动：通过 @JsonTypeInfo + @JsonSubTypes 显式声明子类型
   *   <li>密封类自动发现：Java 17+ sealed class 通过 getPermittedSubclasses() 自动注册子类型 （使用简单类名作为类型判别值）
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
        TypeMapping mapping =
            new TypeMapping(
                typeInfo.property(), typeInfo.visible(), typeInfo.include(), nameToType);
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
          String typeName =
              (jsonTypeName != null && !jsonTypeName.value().isEmpty())
                  ? jsonTypeName.value()
                  : subType.getSimpleName();
          nameToType.put(typeName, subType);
        }
        String property = typeInfo != null ? typeInfo.property() : DEFAULT_TYPE_PROPERTY;
        boolean visible = typeInfo != null && typeInfo.visible();
        JsonTypeInfo.As includeAs =
            typeInfo != null ? typeInfo.include() : JsonTypeInfo.As.PROPERTY;
        TypeMapping mapping = new TypeMapping(property, visible, includeAs, nameToType);
        TYPE_MAPPING_CACHE.put(clazz, mapping);
        return mapping;
      }
    }

    return null;
  }

  /**
   * 序列化侧类型标识（typeProperty + typeName + includeAs 三元组）。
   *
   * <p>P1 能力补齐：序列化侧输出 {@code @JsonTypeInfo} 属性，修复多态 round-trip 断裂
   * （此前仅反序列化侧识别类型属性，序列化不输出 → 生成侧无法闭环）。
   *
   * @param property 类型属性名
   * @param name 类型标识名
   * @param includeAs 包含结构
   */
  public record TypeId(String property, String name, JsonTypeInfo.As includeAs) {}

  /**
   * 解析实际类型的序列化侧类型标识。
   *
   * <p>沿类层级向上查找 {@code @JsonTypeInfo} 声明（注解通常在基类上），再从对应
   * {@link TypeMapping} 反查实际类型的标识名。仅 {@code As.PROPERTY} 结构返回非 null
   * （WRAPPER_ARRAY/WRAPPER_OBJECT 序列化输出暂不支持）。
   *
   * @param actualType 对象的实际运行时类型
   * @return 类型标识，无多态声明或结构不支持时返回 null
   */
  public static TypeId resolveTypeId(Class<?> actualType) {
    for (Class<?> c = actualType; c != null && c != Object.class; c = c.getSuperclass()) {
      JsonTypeInfo typeInfo = c.getAnnotation(JsonTypeInfo.class);
      if (typeInfo == null) {
        continue;
      }
      TypeMapping mapping = getTypeMapping(c);
      if (mapping == null) {
        return null;
      }
      String name = null;
      // Jackson 兼容：子类上的 @JsonTypeName 优先
      JsonTypeName jsonTypeName = actualType.getAnnotation(JsonTypeName.class);
      if (jsonTypeName != null && !jsonTypeName.value().isEmpty()) {
        name = jsonTypeName.value();
      }
      if (name == null) {
        for (Map.Entry<String, Class<?>> entry : mapping.nameToType.entrySet()) {
          if (entry.getValue() == actualType) {
            name = entry.getKey();
            break;
          }
        }
      }
      if (name == null) {
        return null;
      }
      return new TypeId(mapping.typeProperty, name, mapping.getIncludeAs());
    }
    return null;
  }

  /**
   * 从 JSON 中提取类型属性值
   *
   * <p>基于 JSON token 流解析（P0-3 修复）：先完整解析 JSON 为结构化对象， 再按键精确取值。替代原先的文本级 {@code indexOf} 扫描——文本扫描会被
   * 字符串值内的转义文本（如 {@code "remark":"literal \"type\":\"evil\""}） 或键名部分匹配（如 {@code user_type}）误判。
   *
   * @param json JSON 字符串
   * @param typeProperty 类型属性名
   * @return 类型值（仅接受字符串值），不存在或非字符串时返回 null
   */
  public static String extractTypeValue(String json, String typeProperty) {
    if (json == null || typeProperty == null || typeProperty.isEmpty()) {
      return null;
    }
    Object parsed = JsonParserUtil.parse(json);
    if (!(parsed instanceof Map<?, ?> map)) {
      return null;
    }
    Object value = map.get(typeProperty);
    return value instanceof String typeName ? typeName : null;
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
      return resolvedType;
    }
    return baseType;
  }

  /** 清除缓存 */
  public static void clearCache() {
    TYPE_MAPPING_CACHE.clear();
  }
}
