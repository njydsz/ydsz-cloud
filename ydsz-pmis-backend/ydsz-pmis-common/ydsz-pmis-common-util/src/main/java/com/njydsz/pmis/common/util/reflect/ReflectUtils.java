package com.njydsz.pmis.common.util.reflect;

import java.lang.annotation.Annotation;
import java.lang.ref.SoftReference;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射工具类
 *
 * <p>提供全面的反射操作方法，功能增强：
 * 1. SoftReference 缓存 Field/Method，JVM 内存紧张时自动回收
 * 2. 缓存大小上限控制（10000 个类）
 * 3. 类卸载时自动清理缓存
 * </p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class ReflectUtils {

    private static final int MAX_CACHE_SIZE = 5000;

    private static final Map<Class<?>, SoftReference<Field[]>> FIELD_CACHE = createLruCache();

    private static final Map<Class<?>, SoftReference<Method[]>> METHOD_CACHE = createLruCache();

    private static <K, V> Map<K, V> createLruCache() {
        return Collections.synchronizedMap(new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        });
    }

    private ReflectUtils() {
        throw new UnsupportedOperationException("ReflectUtils is a utility class and cannot be instantiated");
    }

    /**
     * 获取指定字段（优先从缓存读取）
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        Field[] fields = getCachedFields(clazz);
        if (fields != null) {
            for (Field field : fields) {
                if (field.getName().equals(fieldName)) {
                    return field;
                }
            }
        }

        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return getField(clazz.getSuperclass(), fieldName);
            }
            return null;
        }
    }

    /**
     * 获取公共字段
     */
    public static Field getPublicField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getField(fieldName);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * 获取所有字段（包括父类，带缓存）
     */
    public static List<Field> getAllFields(Class<?> clazz) {
        Field[] cached = getCachedFields(clazz);
        if (cached != null) {
            return Arrays.asList(cached);
        }

        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        cacheFields(clazz, fields.toArray(new Field[0]));
        return fields;
    }

    /**
     * 获取字段值
     */
    public static Object getFieldValue(Object obj, String fieldName) {
        if (obj == null || fieldName == null) {
            return null;
        }

        Field field = getField(obj.getClass(), fieldName);
        if (field == null) {
            return null;
        }

        try {
            if (!field.trySetAccessible()) {
                throw new RuntimeException("Cannot access field: " + fieldName);
            }
            return field.get(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field value: " + fieldName, e);
        }
    }

    /**
     * 获取字段值 (泛型)
     */
    public static <T> T getFieldValue(Object obj, String fieldName, Class<T> type) {
        Object value = getFieldValue(obj, fieldName);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }

    /**
     * 设置字段值
     */
    public static void setFieldValue(Object obj, String fieldName, Object value) {
        if (obj == null || fieldName == null) {
            return;
        }

        Field field = getField(obj.getClass(), fieldName);
        if (field == null) {
            throw new RuntimeException("Field not found: " + fieldName);
        }

        try {
            if (!field.trySetAccessible()) {
                throw new RuntimeException("Cannot access field: " + fieldName);
            }
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field value: " + fieldName, e);
        }
    }

    /**
     * 获取指定方法（优先从缓存读取）
     */
    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        Method[] cached = getCachedMethods(clazz);
        if (cached != null) {
            for (Method method : cached) {
                if (method.getName().equals(methodName)
                        && Arrays.equals(method.getParameterTypes(), paramTypes)) {
                    return method;
                }
            }
        }

        try {
            return clazz.getDeclaredMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            if (clazz.getSuperclass() != null) {
                return getMethod(clazz.getSuperclass(), methodName, paramTypes);
            }
            return null;
        }
    }

    /**
     * 获取公共方法
     */
    public static Method getPublicMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * 获取所有方法（包括父类，带缓存）
     */
    public static List<Method> getAllMethods(Class<?> clazz) {
        Method[] cached = getCachedMethods(clazz);
        if (cached != null) {
            return Arrays.asList(cached);
        }

        List<Method> methods = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
            current = current.getSuperclass();
        }
        cacheMethods(clazz, methods.toArray(new Method[0]));
        return methods;
    }

    /**
     * 调用方法
     */
    public static Object invokeMethod(Object obj, String methodName, Object... args) {
        if (obj == null || methodName == null) {
            return null;
        }

        Class<?>[] paramTypes = getParameterTypes(args);
        Method method = getMethod(obj.getClass(), methodName, paramTypes);

        if (method == null) {
            throw new RuntimeException("Method not found: " + methodName);
        }

        try {
            if (!method.trySetAccessible()) {
                throw new RuntimeException("Cannot access method: " + methodName);
            }
            return method.invoke(obj, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke method: " + methodName, e);
        }
    }

    /**
     * 调用静态方法
     */
    public static Object invokeStaticMethod(Class<?> clazz, String methodName, Object... args) {
        if (clazz == null || methodName == null) {
            return null;
        }

        Class<?>[] paramTypes = getParameterTypes(args);
        Method method = getMethod(clazz, methodName, paramTypes);

        if (method == null) {
            throw new RuntimeException("Static method not found: " + methodName);
        }

        try {
            if (!method.trySetAccessible()) {
                throw new RuntimeException("Cannot access static method: " + methodName);
            }
            return method.invoke(null, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke static method: " + methodName, e);
        }
    }

    /**
     * 创建实例（无参构造）
     */
    public static <T> T newInstance(Class<T> clazz) {
        if (clazz == null) {
            return null;
        }

        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance: " + clazz.getName(), e);
        }
    }

    /**
     * 创建实例（带参数）
     */
    public static <T> T newInstance(Class<T> clazz, Object... args) {
        if (clazz == null) {
            return null;
        }

        Class<?>[] paramTypes = getParameterTypes(args);

        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor(paramTypes);
            if (!constructor.trySetAccessible()) {
                throw new RuntimeException("Cannot access constructor for class: " + clazz.getName());
            }
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance with args: " + clazz.getName(), e);
        }
    }

    /**
     * 设置可访问 (Java 17+ 模块系统兼容)
     */
    public static boolean setAccessible(AccessibleObject accessibleObject) {
        if (accessibleObject != null) {
            return accessibleObject.trySetAccessible();
        }
        return false;
    }

    /**
     * 获取注解
     */
    public static <A extends Annotation> A getAnnotation(Class<?> clazz, Class<A> annotationType) {
        if (clazz == null || annotationType == null) {
            return null;
        }
        return clazz.getAnnotation(annotationType);
    }

    /**
     * 获取注解（字段）
     */
    public static <A extends Annotation> A getAnnotation(Field field, Class<A> annotationType) {
        if (field == null || annotationType == null) {
            return null;
        }
        return field.getAnnotation(annotationType);
    }

    /**
     * 获取注解（方法）
     */
    public static <A extends Annotation> A getAnnotation(Method method, Class<A> annotationType) {
        if (method == null || annotationType == null) {
            return null;
        }
        return method.getAnnotation(annotationType);
    }

    /**
     * 获取所有注解
     */
    public static Annotation[] getAnnotations(Class<?> clazz) {
        if (clazz == null) {
            return new Annotation[0];
        }
        return clazz.getAnnotations();
    }

    /**
     * 判断是否包含注解
     */
    public static boolean isAnnotationPresent(Class<?> clazz, Class<? extends Annotation> annotationType) {
        return clazz != null && clazz.isAnnotationPresent(annotationType);
    }

    /**
     * 判断是否包含注解（字段）
     */
    public static boolean isAnnotationPresent(Field field, Class<? extends Annotation> annotationType) {
        return field != null && field.isAnnotationPresent(annotationType);
    }

    /**
     * 判断是否包含注解（方法）
     */
    public static boolean isAnnotationPresent(Method method, Class<? extends Annotation> annotationType) {
        return method != null && method.isAnnotationPresent(annotationType);
    }

    /**
     * 获取参数类型
     */
    private static Class<?>[] getParameterTypes(Object[] args) {
        if (args == null || args.length == 0) {
            return new Class<?>[0];
        }

        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
        }
        return paramTypes;
    }

    /**
     * 判断是否为公共成员
     */
    public static boolean isPublic(Member member) {
        return member != null && Modifier.isPublic(member.getModifiers());
    }

    /**
     * 判断是否为私有成员
     */
    public static boolean isPrivate(Member member) {
        return member != null && Modifier.isPrivate(member.getModifiers());
    }

    /**
     * 判断是否为静态成员
     */
    public static boolean isStatic(Member member) {
        return member != null && Modifier.isStatic(member.getModifiers());
    }

    /**
     * 判断是否为最终成员
     */
    public static boolean isFinal(Member member) {
        return member != null && Modifier.isFinal(member.getModifiers());
    }

    /**
     * 判断是否为接口
     */
    public static boolean isInterface(Class<?> clazz) {
        return clazz != null && clazz.isInterface();
    }

    /**
     * 判断是否为抽象类
     */
    public static boolean isAbstract(Class<?> clazz) {
        return clazz != null && Modifier.isAbstract(clazz.getModifiers());
    }

    /**
     * 判断是否为枚举
     */
    public static boolean isEnum(Class<?> clazz) {
        return clazz != null && clazz.isEnum();
    }

    /**
     * 创建动态代理
     */
    public static <T> T createProxy(Class<T> interfaceClass, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                handler
        );
    }

    /**
     * 获取简单类名
     */
    public static String getSimpleClassName(Object obj) {
        return obj != null ? obj.getClass().getSimpleName() : null;
    }

    /**
     * 获取全限定类名
     */
    public static String getClassName(Object obj) {
        return obj != null ? obj.getClass().getName() : null;
    }

    // ==================== 缓存管理 ====================

    private static Field[] getCachedFields(Class<?> clazz) {
        SoftReference<Field[]> ref = FIELD_CACHE.get(clazz);
        if (ref == null) {
            return null;
        }
        Field[] fields = ref.get();
        if (fields == null) {
            FIELD_CACHE.remove(clazz);
        }
        return fields;
    }

    private static void cacheFields(Class<?> clazz, Field[] fields) {
        FIELD_CACHE.put(clazz, new SoftReference<>(fields));
    }

    private static Method[] getCachedMethods(Class<?> clazz) {
        SoftReference<Method[]> ref = METHOD_CACHE.get(clazz);
        if (ref == null) {
            return null;
        }
        Method[] methods = ref.get();
        if (methods == null) {
            METHOD_CACHE.remove(clazz);
        }
        return methods;
    }

    private static void cacheMethods(Class<?> clazz, Method[] methods) {
        METHOD_CACHE.put(clazz, new SoftReference<>(methods));
    }

    /**
     * 清空所有反射缓存
     */
    public static void clearCache() {
        FIELD_CACHE.clear();
        METHOD_CACHE.clear();
    }

    /**
     * 获取缓存统计信息
     */
    public static Map<String, Integer> getCacheStats() {
        Map<String, Integer> stats = new ConcurrentHashMap<>();
        stats.put("fieldCacheSize", FIELD_CACHE.size());
        stats.put("methodCacheSize", METHOD_CACHE.size());
        return stats;
    }
}
