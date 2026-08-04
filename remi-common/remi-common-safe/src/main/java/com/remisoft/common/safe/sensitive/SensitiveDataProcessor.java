package com.remisoft.common.safe.sensitive;

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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.slf4j.LoggerFactory;

/**
 * 敏感数据统一处理器。
 *
 * <p>提供统一的敏感数据处理逻辑，可被各种 JSON 框架的序列化器复用。
 *
 * <p><b>功能特性：</b>
 * <ul>
 *   <li>自动扫描 @SensitiveData 注解的字段</li>
 *   <li>支持嵌套对象的递归处理</li>
 *   <li>支持 Collection 和 Map 的处理</li>
 *   <li>递归深度限制，防止栈溢出</li>
 *   <li>循环引用检测，避免无限递归</li>
 *   <li>异常隔离，单个字段处理失败不影响其他字段</li>
 *   <li>框架无关，可被 Jackson、Gson、FastJson 等复用</li>
 *   <li>兼容 Java Record 类型（通过构造器反射创建新实例）</li>
 *   <li>兼容不可变对象（返回新实例而非修改原对象）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * 
 * @see SensitiveData
 */
public final class SensitiveDataProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveDataProcessor.class);

    /**
     * 默认最大递归深度
     */
    private static final int MAX_DEPTH = 10;

    /**
     * 类是否有敏感字段缓存（P2-18 性能优化：快速跳过无注解类）
     * Key: Class对象，Value: 是否包含 @SensitiveData 注解
     */
    private static final Map<Class<?>, Boolean> SENSITIVE_CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * 简单类型缓存，避免重复判断
     */
    private static final Set<Class<?>> SIMPLE_TYPES = Set.of(
            String.class, Integer.class, Long.class, Short.class, Byte.class,
            Double.class, Float.class, Boolean.class, Character.class
    );

    private SensitiveDataProcessor() {
    }

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
     * @param obj      待处理的对象
     * @param maxDepth 最大递归深度
     * @return 脱敏后的对象副本
     */
    public static <T> T process(T obj, int maxDepth) {
        return processInternal(obj, maxDepth, new IdentityHashMap<>());
    }

    /**
     * 内部处理方法，支持深度限制和循环引用检测。
     *
     * @param obj       待处理的对象
     * @param maxDepth  最大递归深度
     * @param visited   已处理对象集合，用于循环引用检测
     * @return 脱敏后的对象副本
     */
        private static <T> T processInternal(T obj, int maxDepth, IdentityHashMap<Object, Boolean> visited) {
        if (obj == null) {
            return null;
        }

        if (maxDepth <= 0) {
            logger.debug("达到最大递归深度 {}, 跳过处理: {}", MAX_DEPTH, obj.getClass().getName());
            return obj;
        }

        if (obj instanceof String) {
            return obj;
        }

        Class<?> clazz = obj.getClass();
        if (isSimpleType(clazz)) {
            return obj;
        }

        // P2-18 性能优化：快速跳过不含 @SensitiveData 注解的类，避免不必要的反射
        if (!hasSensitiveFields(clazz)) {
            return obj;
        }

        if (visited.containsKey(obj)) {
            logger.debug("检测到循环引用，跳过处理: {}", clazz.getName());
            return obj;
        }
        visited.put(obj, Boolean.TRUE);

        try {
            if (obj instanceof Collection) {
                Collection<Object> collection = (Collection<Object>) obj;
                return (T) collection.stream()
                        .map(item -> processInternal(item, maxDepth - 1, visited))
                        .toList();
            }

            if (obj instanceof Map) {
                Map<Object, Object> map = (Map<Object, Object>) obj;
                Map<Object, Object> result = new HashMap<>();
                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                    result.put(entry.getKey(), processInternal(entry.getValue(), maxDepth - 1, visited));
                }
                return (T) result;
            }

            // Handle Java Record types via constructor reflection
            if (clazz.isRecord()) {
                return processRecord(obj, maxDepth - 1, visited);
            }

            return processBean(obj, maxDepth - 1, visited);
        } catch (Exception e) {
            logger.warn("处理对象 {} 时发生异常，返回部分脱敏对象: {}", clazz.getName(), e.getMessage());
            try {
                if (clazz.isRecord()) {
                    return processRecord(obj, maxDepth - 1, visited);
                }
                return processBean(obj, maxDepth - 1, visited);
            } catch (Exception innerEx) {
                logger.error("部分脱敏也失败: {}", innerEx.getMessage());
                return obj;
            }
        }
    }

    /**
     * 处理 Java Record 类型的敏感数据。
     *
     * <p>Record 类型是不可变的，需要通过构造器反射创建新实例。
     * 对每个 RecordComponent 对应的字段进行敏感数据处理后，通过 compact constructor 创建新实例。
     *
     * @param record   原始 Record 实例
     * @param maxDepth 剩余递归深度
     * @param visited  已处理对象集合
     * @param <T>      Record 类型
     * @return 脱敏后的 Record 新实例
     */
        private static <T> T processRecord(T record, int maxDepth, IdentityHashMap<Object, Boolean> visited) {
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

                if (annotation != null && annotation.enabled() && value != null
                        && shouldDesensitize(annotation)) {
                    String desensitized = SensitiveUtil.desensitize(
                            value.toString(),
                            annotation.value(),
                            annotation.replaceChar()
                    );
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
            Constructor<?> canonicalConstructor = clazz.getDeclaredConstructor(
                    Arrays.stream(components)
                            .map(RecordComponent::getType)
                            .toArray(Class<?>[]::new)
            );
            canonicalConstructor.setAccessible(true);
            return (T) canonicalConstructor.newInstance(componentValues);
        } catch (Exception e) {
            logger.warn("处理 Record {} 时发生异常，返回原对象: {}", clazz.getName(), e.getMessage());
            return record;
        }
    }

    /**
     * 处理 Bean 的敏感数据。
     *
     * <p>如果 Bean 没有无参构造器（如某些不可变对象），尝试使用单个全参构造器创建新实例。
     * 对于不可变对象，返回新实例而非修改原对象。
     *
     * @param bean     待处理的 Bean
     * @param maxDepth 剩余递归深度
     * @param visited  已处理对象集合
     * @param <T>      Bean 类型
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
                logger.warn("Failed to create instance of {}, using original object", clazz.getName());
                return bean;
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
                    if (annotation != null && annotation.enabled() && fieldValue != null
                            && shouldDesensitize(annotation)) {
                        String desensitized = SensitiveUtil.desensitize(
                                fieldValue.toString(),
                                annotation.value(),
                                annotation.replaceChar()
                        );
                        field.set(result, desensitized);
                    } else if (fieldValue != null) {
                        Object processedValue = processInternal(fieldValue, maxDepth, visited);
                        field.set(result, processedValue);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to process field {} in {}: {}",
                            field.getName(), currentClass.getName(), e.getMessage());
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        return (T) result;
    }

    /**
     * 尝试使用全参构造器创建实例（用于不可变对象）。
     *
     * @param bean  原始对象，用于获取字段值作为构造器参数
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

    /**
     * 获取类的所有实例字段（按声明顺序）。
     */
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

    /**
     * 尝试将值转换为目标类型。
     */
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
     * 检查类是否包含 @SensitiveData 注解字段（带缓存）
     *
     * <p>P2-18 性能优化：首次检查后缓存结果，后续直接从缓存读取，
     * 避免对不含敏感注解的类进行不必要的反射处理。
     *
     * @param clazz 待检查的类
     * @return true 表示该类（或其父类）包含 @SensitiveData 注解字段
     */
    private static boolean hasSensitiveFields(Class<?> clazz) {
        return SENSITIVE_CLASS_CACHE.computeIfAbsent(clazz, SensitiveDataProcessor::doHasSensitiveFields);
    }

    /**
     * 实际执行敏感字段检查（递归检查类及其父类）
     */
    private static boolean doHasSensitiveFields(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(SensitiveData.class)) {
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
     * <p>当 {@code @SensitiveData(roles = {"ADMIN"})} 指定了角色白名单时，
     * 如果当前用户拥有白名单中的任一角色，则跳过脱敏（返回原始值）。
     * 角色从 HTTP 请求头 {@code X-User-Role} 获取（逗号分隔）。
     *
     * @param annotation 字段上的敏感数据注解
     * @return true 表示应执行脱敏，false 表示跳过（用户有豁免角色）
     */
    private static boolean shouldDesensitize(SensitiveData annotation) {
        String[] roles = annotation.roles();
        if (roles == null || roles.length == 0) {
            return true;
        }
        String userRoles = getCurrentUserRoles();
        if (userRoles == null || userRoles.isEmpty()) {
            return true;
        }
        for (String role : roles) {
            if (userRoles.contains(role)) {
                logger.debug("用户拥有豁免角色 {}，跳过脱敏", role);
                return false;
            }
        }
        return true;
    }

    /**
     * 从当前 HTTP 请求中获取用户角色
     *
     * @return 用户角色字符串（逗号分隔），非 Web 环境返回 null
     */
    private static String getCurrentUserRoles() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            return attrs.getRequest().getHeader("X-User-Role");
        } catch (Exception e) {
            return null;
        }
    }
}
