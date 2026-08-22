package com.njydsz.common.json.provider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonGetter;
import com.njydsz.common.json.annotation.JsonIgnore;
import com.njydsz.common.json.annotation.JsonIgnoreProperties;
import com.njydsz.common.json.annotation.JsonInclude;
import com.njydsz.common.json.annotation.JsonNaming;
import com.njydsz.common.json.annotation.JsonProperty;
import com.njydsz.common.json.annotation.JsonPropertyOrder;
import com.njydsz.common.json.annotation.JsonSetter;
import com.njydsz.common.json.annotation.JsonValue;
import com.njydsz.common.json.cache.FieldMeta;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.util.BoundedLruCache;

/**
 * 字段元数据加载器和注解处理器
 *
 * <p>从 SerializationProvider 中提取的字段元数据加载逻辑。
 *
 * <p>负责：
 *
 * <ul>
 *   <li>加载类的字段元数据（loadFields）
 *   <li>检测字段注解（hasFieldAnnotations）
 *   <li>判断字段可见性（isFieldVisible）
 *   <li>扫描 @JsonGetter/@JsonSetter 方法级注解，覆盖字段 JSON 名称映射
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public final class FieldMetadataLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(FieldMetadataLoader.class);

  /** 当前使用的命名策略（JsonMapper.applyRuntimeConfig 会写入，故需 public） */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
  public static final ThreadLocal<PropertyNamingStrategy> NAMING_STRATEGY =
  // CHECKSTYLE.ON: RegexpSinglelineJava
      ThreadLocal.withInitial(() -> PropertyNamingStrategy.LOWER_CAMEL_CASE);

  /**
   * @JsonValue 方法缓存（Class -> 标注了 @JsonValue 的 Method，哨兵值表示无）。
   *
   * <p>避免每次序列化都反射扫描方法列表。一个类最多只能有一个 @JsonValue 方法。
   *
   * <p>P0-4：改为有界 LRU（容量 1024），防止动态类加载/热部署场景下无界增长。
   */
  private static final BoundedLruCache<Class<?>, Method> JSON_VALUE_METHOD_CACHE =
      new BoundedLruCache<>(1024);

  /**
   * 计算属性缓存（Class -> 计算属性方法列表）。
   *
   * <p>存储标注了 @JsonGetter 但没有对应字段的计算属性方法。 序列化时在字段写完后补充输出这些计算属性。
   *
   * <p>P0-4：改为有界 LRU（容量 1024），防止动态类加载/热部署场景下无界增长。
   */
  private static final BoundedLruCache<Class<?>, Method[]> COMPUTED_PROPERTIES_CACHE =
      new BoundedLruCache<>(1024);

  private FieldMetadataLoader() {
    throw new UnsupportedOperationException();
  }

  /** 加载字段元数据 */
  /**
   * 收集类自身及其所有父类（不含 {@code Object}）的 declared 字段，子类字段在前。
   *
   * <p>修复仅使用 {@code getDeclaredFields()} 导致继承字段（如 MyBatis-Plus 基类 {@code MpBaseEntity} 的 id /
   * createTime 等）被静默丢弃的问题。同名子类字段优先， 父类同名被遮蔽字段跳过，避免重复 JSON key。
   *
   * @param clazz 目标类
   * @return 合并后的字段列表（子类在前）
   * @since 1.0.0
   */
  public static List<Field> collectDeclaredAndInheritedFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Field f : current.getDeclaredFields()) {
        if (seen.add(f.getName())) {
          fields.add(f);
        }
      }
      current = current.getSuperclass();
    }
    return fields;
  }

  public static FieldMeta[] loadFields(Class<?> clazz) {
    JsonClass classAnnotation = clazz.getAnnotation(JsonClass.class);

    int annotationFieldCount =
        classAnnotation != null
            ? classAnnotation.ignores().length
                + classAnnotation.includes().length
                + classAnnotation.ordering().length
            : 0;

    Set<String> ignores = new HashSet<>(annotationFieldCount);
    Set<String> includes = null;
    Map<String, Integer> ordering = new HashMap<>(annotationFieldCount);
    PropertyNamingStrategy classNaming = NAMING_STRATEGY.get();

    if (classAnnotation != null) {
      if (classAnnotation.ignores().length > 0) {
        ignores.addAll(Arrays.asList(classAnnotation.ignores()));
      }
      if (classAnnotation.includes().length > 0) {
        includes = new HashSet<>(annotationFieldCount);
        includes.addAll(Arrays.asList(classAnnotation.includes()));
      }
      if (classAnnotation.ordering().length > 0) {
        for (int i = 0; i < classAnnotation.ordering().length; i++) {
          ordering.put(classAnnotation.ordering()[i], i);
        }
      }
      if (classAnnotation.naming() != JsonClass.NamingStrategy.CAMEL_CASE) {
        classNaming = classAnnotation.naming().toPropertyNamingStrategy();
      }
    }

    JsonPropertyOrder propertyOrder = clazz.getAnnotation(JsonPropertyOrder.class);
    Map<String, Integer> propertyOrderMapping = new HashMap<>();
    boolean alphabeticSort = false;
    if (propertyOrder != null) {
      if (propertyOrder.value().length > 0) {
        for (int i = 0; i < propertyOrder.value().length; i++) {
          propertyOrderMapping.put(propertyOrder.value()[i], i);
        }
      }
      alphabeticSort = propertyOrder.alphabetic();
    }

    // Jackson 兼容：@JsonNaming 类级命名策略
    JsonNaming jsonNaming = clazz.getAnnotation(JsonNaming.class);
    if (jsonNaming != null) {
      try {
        classNaming = jsonNaming.value().getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        LOGGER.warn("FieldMetadataLoader @JsonNaming 实例化失败，回退默认命名策略: {}", e.toString());
      }
    }

    // 处理 @JsonIgnoreProperties 注解
    JsonIgnoreProperties ignoreProperties = clazz.getAnnotation(JsonIgnoreProperties.class);
    if (ignoreProperties != null) {
      for (String name : ignoreProperties.value()) {
        ignores.add(name);
      }
    }

    // P1-8：Jackson 注解兼容桥——类级 @JsonIgnoreProperties 兜底（原生注解优先）
    if (JacksonAnnotationBridge.isAvailable()) {
      for (String name : JacksonAnnotationBridge.ignoreProperties(clazz)) {
        ignores.add(name);
      }
    }

    List<Field> declaredFields = collectDeclaredAndInheritedFields(clazz);
    List<FieldMeta> fieldList = new ArrayList<>(declaredFields.size());

    for (Field field : declaredFields) {
      int mods = field.getModifiers();
      if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
        continue;
      }

      if (!isFieldVisible(mods, field)) {
        continue;
      }

      String fieldName = field.getName();

      if (ignores.contains(fieldName)) {
        continue;
      }

      JsonProperty jsonField = field.getAnnotation(JsonProperty.class);
      JsonIgnore jacksonIgnore = field.getAnnotation(JsonIgnore.class);
      if (jacksonIgnore != null) {
        continue;
      }
      // P1-8：Jackson @JsonIgnore 兜底（原生注解优先）
      if (jsonField == null && JacksonAnnotationBridge.isIgnored(field)) {
        continue;
      }

      if (includes != null) {
        String jsonFieldName = field.getName();
        if (jsonField != null) {
          if (!jsonField.value().isEmpty()) {
            jsonFieldName = jsonField.value();
          } else if (classNaming != null) {
            jsonFieldName = classNaming.translate(field.getName());
          }
        } else if (classNaming != null) {
          jsonFieldName = classNaming.translate(field.getName());
        }
        if (!includes.contains(jsonFieldName)) {
          continue;
        }
      }

      String jsonName = fieldName;
      int ordinal = ordering.getOrDefault(fieldName, fieldList.size());

      if (propertyOrderMapping.containsKey(fieldName)) {
        ordinal = propertyOrderMapping.get(fieldName);
      }

      if (jsonField != null && !jsonField.value().isEmpty()) {
        jsonName = jsonField.value();
      } else if (classNaming != null) {
        jsonName = classNaming.translate(jsonName);
      }

      // P1-8：Jackson @JsonProperty.value 兜底重命名（原生注解未指定名称时生效）
      if (jsonField == null) {
        String bridgeName = JacksonAnnotationBridge.propertyName(field);
        if (bridgeName != null) {
          jsonName = bridgeName;
        }
      }

      try {
        field.setAccessible(true);
        fieldList.add(new FieldMeta(field, jsonName, ordinal));
      } catch (Exception e) {
        // 反射操作失败：告警而非静默丢弃，避免字段悄悄丢失（P1-⑦）
        LOGGER.warn(
            "FieldMetadataLoader 跳过字段 {}.{}: {}", clazz.getName(), field.getName(), e.toString());
      }
    }

    if (alphabeticSort && propertyOrderMapping.isEmpty()) {
      fieldList.sort((a, b) -> a.jsonName.compareTo(b.jsonName));
    } else {
      fieldList.sort((a, b) -> Integer.compare(a.ordinal, b.ordinal));
    }

    // 扫描 @JsonGetter/@JsonSetter 方法级注解，覆盖字段 JSON 名称映射
    applyMethodAnnotations(clazz, fieldList, classNaming);

    return fieldList.toArray(new FieldMeta[0]);
  }

  /**
   * 扫描 @JsonGetter/@JsonSetter 方法级注解，覆盖字段 JSON 名称映射。
   *
   * <p>当 getter/setter 方法上标注了 @JsonGetter/@JsonSetter 且指定了 value 时， 将对应字段的 JSON
   * 名称覆盖为注解指定的值。如果方法没有对应的字段 （计算属性），当前版本跳过并记录 debug 日志。
   *
   * @param clazz 被扫描的类
   * @param fieldList 已加载的字段列表
   * @param classNaming 类级命名策略
   * @since 1.0.0
   */
  private static void applyMethodAnnotations(
      Class<?> clazz, List<FieldMeta> fieldList, PropertyNamingStrategy classNaming) {
    // 构建字段名 -> 索引映射（用于替换 fieldList 中的元素）
    Map<String, Integer> fieldIndex = new HashMap<>(fieldList.size());
    for (int i = 0; i < fieldList.size(); i++) {
      fieldIndex.put(fieldList.get(i).name, i);
    }

    for (Method method : clazz.getDeclaredMethods()) {
      // @JsonGetter：覆盖序列化 JSON 名称
      JsonGetter jsonGetter = method.getAnnotation(JsonGetter.class);
      if (jsonGetter != null) {
        String fieldName = inferFieldNameFromGetter(method.getName());
        if (fieldName != null && fieldIndex.containsKey(fieldName)) {
          String newJsonName = jsonGetter.value().isEmpty() ? fieldName : jsonGetter.value();
          int idx = fieldIndex.get(fieldName);
          FieldMeta original = fieldList.get(idx);
          // 创建新的 FieldMeta，覆盖 jsonName
          FieldMeta replaced = createWithJsonName(original, newJsonName);
          if (replaced != null) {
            fieldList.set(idx, replaced);
          }
        }
      }

      // @JsonSetter：覆盖反序列化 JSON 名称
      JsonSetter jsonSetter = method.getAnnotation(JsonSetter.class);
      if (jsonSetter != null) {
        String fieldName = inferFieldNameFromSetter(method.getName());
        if (fieldName != null && fieldIndex.containsKey(fieldName)) {
          String newJsonName = jsonSetter.value().isEmpty() ? fieldName : jsonSetter.value();
          int idx = fieldIndex.get(fieldName);
          FieldMeta original = fieldList.get(idx);
          // 如果 @JsonGetter 已经覆盖过，在此基础再覆盖
          String currentName = original.jsonName;
          if (!newJsonName.equals(currentName)) {
            FieldMeta replaced = createWithJsonName(original, newJsonName);
            if (replaced != null) {
              fieldList.set(idx, replaced);
            }
          }
        }
      }
    }
  }

  /**
   * 创建一个新的 FieldMeta，使用指定的 jsonName 替代原始的 jsonName。
   *
   * <p>由于 {@link FieldMeta} 的 {@code jsonName} 是 final 字段， 需要通过反射构造一个新的实例。如果创建失败返回 null（保持原始实例）。
   *
   * @param original 原始 FieldMeta
   * @param newJsonName 新的 JSON 名称
   * @return 新的 FieldMeta 实例，或 null 如果创建失败
   */
  private static FieldMeta createWithJsonName(FieldMeta original, String newJsonName) {
    try {
      FieldMeta replaced = new FieldMeta(original.field, newJsonName, original.ordinal);
      return replaced;
    } catch (Exception e) {
      LOGGER.warn(
          "FieldMetadataLoader 重建 FieldMeta 失败，保留原实例 {}: {}",
          original.field.getName(),
          e.toString());
      return null;
    }
  }

  /**
   * 从 getter 方法名推断字段名（如 getName → name, isActive → active）。
   *
   * @param methodName 方法名
   * @return 字段名，或 null 如果无法推断
   */
  private static String inferFieldNameFromGetter(String methodName) {
    if (methodName.startsWith("get") && methodName.length() > 3) {
      return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
    }
    if (methodName.startsWith("is") && methodName.length() > 2) {
      return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
    }
    return null;
  }

  /**
   * 从 setter 方法名推断字段名（如 setName → name）。
   *
   * @param methodName 方法名
   * @return 字段名，或 null 如果无法推断
   */
  private static String inferFieldNameFromSetter(String methodName) {
    if (methodName.startsWith("set") && methodName.length() > 3) {
      return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
    }
    return null;
  }

  /** 哨兵值：表示类中无 @JsonValue 方法（ConcurrentHashMap 不允许 null value）。 */
  private static final Method NO_JSON_VALUE_SENTINEL;

  static {
    try {
      NO_JSON_VALUE_SENTINEL =
          FieldMetadataLoader.class.getDeclaredMethod("findJsonValueMethod", Class.class);
    } catch (NoSuchMethodException e) {
      throw new InternalError(e);
    }
  }

  /**
   * 查找类中标注了 {@code @JsonValue} 的方法。
   *
   * <p>对标 Jackson {@code @JsonValue}：标注在方法上时，该方法的返回值 作为整个对象的 JSON 值（而非字段级序列化）。常用于枚举自定义序列化。
   *
   * <p>一个类最多只能有一个 {@code @JsonValue} 方法。如果找到多个，使用第一个。 结果（含"无 @JsonValue 方法"的哨兵负缓存）会被缓存，后续调用直接返回缓存值。
   *
   * <p>P0-4：{@link BoundedLruCache#computeIfAbsent} 的构建函数在锁外执行， {@code computeJsonValueMethod}
   * 经父类递归调用本方法是安全的 （不存在 CHM computeIfAbsent 的 Recursive update 问题）。
   *
   * @param clazz 要扫描的类
   * @return 标注了 {@code @JsonValue} 的 Method，未找到返回 null
   * @since 1.0.0
   */
  public static Method findJsonValueMethod(Class<?> clazz) {
    Method result =
        JSON_VALUE_METHOD_CACHE.computeIfAbsent(
            clazz,
            c -> {
              Method method = computeJsonValueMethod(c);
              return method != null ? method : NO_JSON_VALUE_SENTINEL;
            });
    return result == NO_JSON_VALUE_SENTINEL ? null : result;
  }

  /**
   * 计算 @JsonValue 方法（不操作缓存，可安全递归调用）。
   *
   * <p>P1-8：原生 {@code @JsonValue} 未标注时回退扫描 Jackson {@code @JsonValue} （通过 {@link
   * JacksonAnnotationBridge} 反射读取，原生注解优先）。
   */
  private static Method computeJsonValueMethod(Class<?> clazz) {
    for (Method method : clazz.getDeclaredMethods()) {
      if (method.isAnnotationPresent(JsonValue.class)) {
        method.setAccessible(true);
        return method;
      }
    }
    if (JacksonAnnotationBridge.isAvailable()) {
      Method bridgeMethod = JacksonAnnotationBridge.findJsonValueMethod(clazz);
      if (bridgeMethod != null) {
        return bridgeMethod;
      }
    }
    Class<?> superClass = clazz.getSuperclass();
    if (superClass != null && superClass != Object.class) {
      return findJsonValueMethod(superClass);
    }
    return null;
  }

  /**
   * 检查类是否有 {@code @JsonValue} 方法。
   *
   * @param clazz 要检查的类
   * @return true 如果类中存在 {@code @JsonValue} 标注的方法
   * @since 1.0.0
   */
  public static boolean hasJsonValueMethod(Class<?> clazz) {
    return findJsonValueMethod(clazz) != null;
  }

  /**
   * 查找类中标注了 {@code @JsonAnyGetter} 的方法。
   *
   * <p><b>注意：</b> @JsonAnyGetter 注解已删除，此方法始终返回 null。 保留此方法以维持向后兼容的二进制接口。
   *
   * @param clazz 要扫描的类
   * @return 始终返回 null
   */
  public static Method findAnyGetterMethod(Class<?> clazz) {
    return null;
  }

  /**
   * 查找类中的计算属性方法（@JsonGetter 标注但没有对应字段的方法）。
   *
   * <p>对标 Jackson @JsonGetter 计算属性：标注在 getter 方法上， 方法返回值作为 JSON 属性输出，无需对应实际字段。
   *
   * @param clazz 要扫描的类
   * @return 计算属性方法数组，未找到返回空数组
   * @since 1.0.0
   */
  public static Method[] findComputedProperties(Class<?> clazz) {
    return COMPUTED_PROPERTIES_CACHE.computeIfAbsent(
        clazz,
        c -> {
          List<Method> computed = new ArrayList<>();
          // 获取已加载的字段名集合
          Set<String> fieldNames = new HashSet<>();
          Field[] fields = c.getDeclaredFields();
          for (Field f : fields) {
            int mods = f.getModifiers();
            if (!Modifier.isStatic(mods) && !Modifier.isTransient(mods)) {
              fieldNames.add(f.getName());
            }
          }

          for (Method method : c.getDeclaredMethods()) {
            JsonGetter jsonGetter = method.getAnnotation(JsonGetter.class);
            if (jsonGetter != null) {
              String inferredName = inferFieldNameFromGetter(method.getName());
              // 如果推断出的字段名不在已加载字段列表中，则为计算属性
              if (inferredName == null || !fieldNames.contains(inferredName)) {
                method.setAccessible(true);
                computed.add(method);
              }
            }
          }
          return computed.isEmpty() ? new Method[0] : computed.toArray(new Method[0]);
        });
  }

  /**
   * 检查类是否有计算属性。
   *
   * @param clazz 要检查的类
   * @return true 如果类中存在计算属性方法
   * @since 1.0.0
   */
  public static boolean hasComputedProperties(Class<?> clazz) {
    return findComputedProperties(clazz).length > 0;
  }

  /**
   * 获取计算属性的 JSON 名称。
   *
   * @param method 标注了 @JsonGetter 的方法
   * @return JSON 属性名
   * @since 1.0.0
   */
  public static String getComputedPropertyName(Method method) {
    JsonGetter jsonGetter = method.getAnnotation(JsonGetter.class);
    if (jsonGetter != null && !jsonGetter.value().isEmpty()) {
      return jsonGetter.value();
    }
    String inferred = inferFieldNameFromGetter(method.getName());
    return inferred != null ? inferred : method.getName();
  }

  /**
   * 查找类中标注了 {@code @JsonAnySetter} 的方法。
   *
   * <p><b>注意：</b> @JsonAnySetter 注解已删除，此方法始终返回 null。 保留此方法以维持向后兼容的二进制接口。
   *
   * @param clazz 要扫描的类
   * @return 始终返回 null
   */
  public static Method findAnySetterMethod(Class<?> clazz) {
    return null;
  }

  /**
   * 检查字段是否有影响序列化的注解（用于快速路径判定）。
   * <p>检测以下需要特殊处理的注解/状态：
   * <ul>
   *   <li>{@code @JsonFormat} 日期格式（{@link FieldMeta#isDateType()}）
   *   <li>{@code @JsonInclude} 非 ALWAYS 策略（{@link FieldMeta#includeStrategy}）
   * </ul>
   *
   * @param fields 字段列表
   * @return 返回值说明
   */
  public static boolean hasFieldAnnotations(FieldMeta[] fields) {
    if (fields == null) {
      return false;
    }
    for (FieldMeta field : fields) {
      if (field.isDateType() || field.includeStrategy != JsonInclude.Include.ALWAYS) {
        return true;
      }
    }
    return false;
  }

  /**
   * 判断字段是否可见。
   *
   * <p>可见性策略由 @JsonVisibility 注解控制。由于该注解已删除， 默认策略为 ANY（所有字段可见）。
   *
   * @param modifiers 字段修饰符
   * @param field 字段对象
   * @return 始终返回 true
   */
  public static boolean isFieldVisible(int modifiers, Field field) {
    return true;
  }
}
