package com.njydsz.common.safe.sensitive;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 敏感数据统一处理器。
 *
 * <p>提供统一的敏感数据处理逻辑，可被各种 JSON 框架的序列化器复用。
 *
 * <p><b>功能特性：</b>
 *
 * <ul>
 *   <li>自动扫描 @SensitiveData 注解的字段
 *   <li>支持嵌套对象的递归处理
 *   <li>支持 Collection 和 Map 的处理
 *   <li>递归深度限制，防止栈溢出
 *   <li>循环引用检测，避免无限递归
 *   <li>异常隔离，单个字段处理失败不影响其他字段
 *   <li>框架无关，可被 Jackson、Gson、FastJson 等复用
 *   <li>兼容 Java Record 类型（通过构造器反射创建新实例）
 *   <li>兼容不可变对象（返回新实例而非修改原对象）
 * </ul>
 *
 * <p><b>安全策略（fail-closed）：</b>自 v1.0.0 起，本处理器遵循「宁可拒绝响应， 不可泄露明文」原则：
 *
 * <ul>
 *   <li>递归深度超限：抛出 {@link SensitiveDataProcessingException}，不再返回原始对象
 *   <li>对象重建失败：抛出 {@link SensitiveDataProcessingException}，不再降级返回原对象
 *   <li>角色豁免：不再信任任何客户端可注入的请求头（如 X-User-Role）， 在认证上下文未提供可信角色来源前，角色白名单一律不生效（全部脱敏）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SensitiveData
 * @see SensitiveDataProcessingException
 */
public final class SensitiveDataProcessor {

  private static final Logger logger = LoggerFactory.getLogger(SensitiveDataProcessor.class);

  /** 默认最大递归深度 */
  private static final int MAX_DEPTH = 10;

  /** 类是否包含敏感字段缓存（P2-18 性能优化：快速跳过无注解类） Key: Class对象，Value: 是否包含 @SensitiveData 注解 */
  private static final Map<Class<?>, Boolean> SENSITIVE_CLASS_CACHE = new ConcurrentHashMap<>();

  /** 简单类型缓存，避免重复判断 */
  private static final Set<Class<?>> SIMPLE_TYPES =
      Set.of(
          String.class,
          Integer.class,
          Long.class,
          Short.class,
          Byte.class,
          Double.class,
          Float.class,
          Boolean.class,
          Character.class);

  private SensitiveDataProcessor() {}

  /**
   * 处理对象中的敏感数据。
   *
   * @param obj 待处理的对象
   * @return 脱敏后的对象副本
   */
  public static <T> T process(T obj) {
    return process(obj, MAX_DEPTH);
  }

  /**
   * 处理对象中的敏感数据（可指定最大深度）。
   *
   * @param obj 待处理的对象
   * @param maxDepth 最大递归深度
   * @return 脱敏后的对象副本
   */
  public static <T> T process(T obj, int maxDepth) {
    return processInternal(obj, maxDepth, new IdentityHashMap<>());
  }

  /**
   * 内部处理方法，支持深度限制和循环引用检测。
   *
   * <p>fail-closed：深度超限或处理失败时抛出 {@link SensitiveDataProcessingException}， 禁止返回未脱敏的原始对象。
   *
   * @param obj 待处理的对象
   * @param maxDepth 最大递归深度
   * @param visited 已处理对象集合，用于循环引用检测
   * @return 脱敏后的对象副本
   */
  private static <T> T processInternal(
      T obj, int maxDepth, IdentityHashMap<Object, Boolean> visited) {
    if (obj == null) {
      return null;
    }

    if (maxDepth <= 0) {
      throw new SensitiveDataProcessingException(
          "敏感数据处理超过最大递归深度，为防深层敏感数据泄露已拒绝返回原始对象: " + obj.getClass().getName());
    }

    if (obj instanceof String) {
      return obj;
    }

    Class<?> clazz = obj.getClass();
    if (isSimpleType(clazz)) {
      return obj;
    }

    if (visited.containsKey(obj)) {
      logger.debug("检测到循环引用，跳过处理: {}", clazz.getName());
      return obj;
    }
    visited.put(obj, Boolean.TRUE);

    try {
      // 容器类型优先递归处理，确保容器内嵌套对象（即使容器类本身无注解）也能被脱敏
      if (obj instanceof Collection) {
        Collection<Object> collection = (Collection<Object>) obj;
        return (T)
            collection.stream().map(item -> processInternal(item, maxDepth - 1, visited)).toList();
      }

      if (obj instanceof Map) {
        Map<Object, Object> map = (Map<Object, Object>) obj;
        Map<Object, Object> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
          result.put(entry.getKey(), processInternal(entry.getValue(), maxDepth - 1, visited));
        }
        return (T) result;
      }

      // P2-18 性能优化：仅当类本身不含敏感字段且不含引用类型字段时才可快速跳过。
      // 含引用字段的对象仍需递归处理，防止嵌套对象中的敏感字段漏脱敏。
      if (!hasSensitiveFields(clazz)) {
        return obj;
      }

      // Handle Java Record types via constructor reflection
      if (clazz.isRecord()) {
        return processRecord(obj, maxDepth - 1, visited);
      }

      return processBean(obj, maxDepth - 1, visited);
    } catch (SensitiveDataProcessingException e) {
      // 深度超限等已由具体方法抛出的安全异常，直接透传
      throw e;
    } catch (Exception e) {
      // fail-closed：处理失败不再返回原始对象，防止未脱敏数据泄露
      throw new SensitiveDataProcessingException("敏感数据处理失败，为防数据泄露已拒绝返回原始对象: " + clazz.getName(), e);
    }
  }

  /**
   * 处理 Java Record 类型的敏感数据。
   *
   * <p>Record 类型是不可变的，需要通过构造器反射创建新实例。 对每个 RecordComponent 对应的字段进行敏感数据处理后，通过 compact constructor
   * 创建新实例。
   *
   * @param record 原始 Record 实例
   * @param maxDepth 剩余递归深度
   * @param visited 已处理对象集合
   * @param <T> Record 类型
   * @return 脱敏后的 Record 新实例
   */
  private static <T> T processRecord(
      T record, int maxDepth, IdentityHashMap<Object, Boolean> visited) {
    Class<?> clazz = record.getClass();
    RecordComponent[] components = clazz.getRecordComponents();
    if (components == null || components.length == 0) {
      return record;
    }

    // 获取所有字段的处理后的值
    Object[] componentValues = new Object[components.length];
    boolean anyChanged = false;

    try {
      // 通过 accessor 方法获取每个组件的值（等同于字段名）
      for (int i = 0; i < components.length; i++) {
        RecordComponent component = components[i];
        Method accessor = component.getAccessor();
        accessor.setAccessible(true);
        Object value = accessor.invoke(record);

        // 检查该组件对应字段是否有 @SensitiveData 注解
        SensitiveData annotation = null;
        try {
          Field field = clazz.getDeclaredField(component.getName());
          annotation = field.getAnnotation(SensitiveData.class);
        } catch (NoSuchFieldException e) {
          // Record 组件可能没有对应的声明字段，忽略
        }

        if (annotation != null
            && annotation.enabled()
            && value != null
            && shouldDesensitize(annotation)) {
          String desensitized =
              SensitiveUtil.desensitize(
                  value.toString(), annotation.value(), annotation.replaceChar());
          componentValues[i] = desensitized;
          if (!value.equals(desensitized)) {
            anyChanged = true;
          }
        } else if (value != null) {
          Object processedValue = processInternal(value, maxDepth, visited);
          componentValues[i] = processedValue;
          if (processedValue != value) {
            anyChanged = true;
          }
        } else {
          componentValues[i] = null;
        }
      }

      // 如果没有任何改变，直接返回原对象
      if (!anyChanged) {
        return record;
      }

      // 通过 canonical constructor 创建新实例
      Constructor<?> canonicalConstructor =
          clazz.getDeclaredConstructor(
              Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
      canonicalConstructor.setAccessible(true);
      return (T) canonicalConstructor.newInstance(componentValues);
    } catch (Exception e) {
      // fail-closed：Record 重建失败不返回原对象，防止深层敏感数据泄露
      throw new SensitiveDataProcessingException("敏感数据处理失败，Record 重建异常: " + clazz.getName(), e);
    }
  }

  /**
   * 处理 Bean 的敏感数据。
   *
   * <p>如果 Bean 没有无参构造器（如某些不可变对象），尝试使用单个全参构造器创建新实例。 对于不可变对象，返回新实例而非修改原对象。
   *
   * @param bean 待处理的 Bean
   * @param maxDepth 剩余递归深度
   * @param visited 已处理对象集合
   * @param <T> Bean 类型
   * @return 脱敏后的 Bean 副本
   */
  private static <T> T processBean(T bean, int maxDepth, IdentityHashMap<Object, Boolean> visited) {
    if (bean == null) {
      return null;
    }

    Class<?> clazz = bean.getClass();
    Object result;

    // 尝试无参构造器
    try {
      result = clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      // 无参构造器不可用，尝试全参构造器（支持不可变对象）
      result = tryCreateWithAllArgsConstructor(bean, clazz);
      if (result == null) {
        // fail-closed：无法创建副本时拒绝返回原始对象
        throw new SensitiveDataProcessingException("敏感数据处理失败，无法创建脱敏副本: " + clazz.getName(), e);
      }
    }

    Class<?> currentClass = clazz;
    while (currentClass != null && currentClass != Object.class) {
      for (Field field : currentClass.getDeclaredFields()) {
        // 跳过静态字段和 final 字段（已经通过构造器设置）
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers)) {
          continue;
        }
        if (result != bean && Modifier.isFinal(modifiers)) {
          // 新实例的 final 字段已通过构造器初始化，跳过
          continue;
        }

        try {
          field.setAccessible(true);
          Object fieldValue = field.get(bean);

          SensitiveData annotation = field.getAnnotation(SensitiveData.class);
          if (annotation != null
              && annotation.enabled()
              && fieldValue != null
              && shouldDesensitize(annotation)) {
            String desensitized =
                SensitiveUtil.desensitize(
                    fieldValue.toString(), annotation.value(), annotation.replaceChar());
            field.set(result, desensitized);
          } else if (fieldValue != null) {
            Object processedValue = processInternal(fieldValue, maxDepth, visited);
            field.set(result, processedValue);
          }
        } catch (SensitiveDataProcessingException e) {
          // 深度超限等安全异常直接透传，中断整个处理流程
          throw e;
        } catch (Exception e) {
          logger.warn(
              "敏感数据字段处理失败: 类={}, 字段={}, 原因={}",
              currentClass.getName(),
              field.getName(),
              e.getMessage());
        }
      }
      currentClass = currentClass.getSuperclass();
    }

    return (T) result;
  }

  /**
   * 尝试使用全参构造器创建实例（用于不可变对象）。
   *
   * @param bean 原始对象，用于获取字段值作为构造器参数
   * @param clazz 类型
   * @return 新实例，如果无法创建则返回 null
   */
  private static Object tryCreateWithAllArgsConstructor(Object bean, Class<?> clazz) {
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    for (Constructor<?> ctor : constructors) {
      Class<?>[] paramTypes = ctor.getParameterTypes();
      if (paramTypes.length == 0) {
        continue; // 跳过无参构造器（已经尝试过）
      }

      // 尝试找到一个构造器，其参数数量和字段数量匹配
      List<Field> allFields = getAllFields(clazz);
      if (paramTypes.length != allFields.size()) {
        continue;
      }

      try {
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
          Field field = allFields.get(i);
          field.setAccessible(true);
          Object value = field.get(bean);
          args[i] = convertValueIfNeeded(value, paramTypes[i]);
        }

        ctor.setAccessible(true);
        return ctor.newInstance(args);
      } catch (Exception e) {
        logger.debug("使用构造器 {} 创建实例失败: {}", ctor, e.getMessage());
      }
    }
    return null;
  }

  /** 获取类的所有实例字段（按声明顺序）。 */
  private static List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers())) {
          fields.add(field);
        }
      }
      current = current.getSuperclass();
    }
    return fields;
  }

  /** 尝试将值转换为目标类型。 */
  private static Object convertValueIfNeeded(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }
    if (targetType.isAssignableFrom(value.getClass())) {
      return value;
    }
    // 处理基本类型与其包装类之间的转换
    if (targetType == int.class && value instanceof Integer) return value;
    if (targetType == long.class && value instanceof Long) return value;
    if (targetType == boolean.class && value instanceof Boolean) return value;
    if (targetType == double.class && value instanceof Double) return value;
    if (targetType == float.class && value instanceof Float) return value;
    if (targetType == short.class && value instanceof Short) return value;
    if (targetType == byte.class && value instanceof Byte) return value;
    if (targetType == char.class && value instanceof Character) return value;
    return value;
  }

  /**
   * 判断是否为简单类型。
   *
   * <p>简单类型包括：基本类型、包装类型、日期时间类型等，无需递归处理。
   *
   * @param clazz 类型
   * @return 是否为简单类型
   */
  private static boolean isSimpleType(Class<?> clazz) {
    return clazz.isPrimitive()
        || SIMPLE_TYPES.contains(clazz)
        || Number.class.isAssignableFrom(clazz)
        || Temporal.class.isAssignableFrom(clazz)
        || Date.class.isAssignableFrom(clazz);
  }

  /**
   * 检查类是否包含 @SensitiveData 注解字段或引用类型字段（带缓存）
   *
   * <p>P2-18 性能优化：首次检查后缓存结果，后续直接从缓存读取， 避免对不含敏感注解的类进行不必要的反射处理。
   *
   * <p><b>安全说明：</b>除注解字段外，引用类型字段（嵌套对象/集合/Map）也必须返回 true，
   * 以确保嵌套对象内部的敏感字段能被递归处理，防止「外层类无注解、内层对象有敏感字段」时漏脱敏。
   *
   * @param clazz 待检查的类
   * @return true 表示该类（或其父类）包含 @SensitiveData 注解字段或引用类型字段
   */
  private static boolean hasSensitiveFields(Class<?> clazz) {
    return SENSITIVE_CLASS_CACHE.computeIfAbsent(
        clazz, SensitiveDataProcessor::doHasSensitiveFields);
  }

  /** 实际执行敏感字段检查（递归检查类及其父类） */
  private static boolean doHasSensitiveFields(Class<?> clazz) {
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        if (field.isAnnotationPresent(SensitiveData.class)) {
          return true;
        }
        // 引用类型字段（非简单类型）可能承载嵌套敏感数据，必须递归处理
        if (!isSimpleType(field.getType())) {
          return true;
        }
      }
      current = current.getSuperclass();
    }
    return false;
  }

  /**
   * 检查当前字段是否应该执行脱敏（基于角色白名单）
   *
   * <p>当 {@code @SensitiveData(roles = {"ADMIN"})} 指定了角色白名单时， 如果当前用户拥有白名单中的任一角色，则跳过脱敏（返回原始值）。
   *
   * <p><b>安全说明（fail-closed）：</b>角色信息必须来自认证后的可信上下文， 绝不信任客户端可注入的请求头（如 X-User-Role）。 当前认证上下文契约（{@code
   * CurrentUser}）尚未提供角色字段， 因此角色白名单暂不生效——无任何角色来源时一律执行脱敏， 防止伪造请求头绕过脱敏导致敏感数据泄露。
   *
   * @param annotation 字段上的敏感数据注解
   * @return true 表示应执行脱敏，false 表示跳过（用户有豁免角色）
   */
  private static boolean shouldDesensitize(SensitiveData annotation) {
    String[] roles = annotation.roles();
    if (roles == null || roles.length == 0) {
      return true;
    }
    // fail-closed：无可信角色来源（认证上下文未提供角色字段），一律脱敏
    // TODO: 2026-08-20 待认证上下文扩展角色字段后，改为从 RequestContext 的
    //       AuthInfo 读取当前用户角色集合，再做精确等值匹配（@ydsz-team）
    logger.debug("角色白名单暂不生效（认证上下文未提供角色来源），按 fail-closed 执行脱敏: {}", String.join(",", roles));
    return true;
  }
}
