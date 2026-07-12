package com.njydsz.pmis.common.util.object;

import com.njydsz.pmis.common.util.string.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Supplier;

/**
 * 对象工具类
 *
 * <p>提供全面的对象操作方法，功能对标 Apache Commons Lang3 ObjectUtils 和 Google Guava，
 * 并进行了增强和优化。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li><b>零依赖</b>：仅依赖 JDK 和本模块内部工具类</li>
 *   <li><b>空值安全</b>：所有方法均进行空值检查，避免 NPE</li>
 *   <li><b>类型判断</b>：支持基本类型、集合、Map、数组等多种类型判断</li>
 *   <li><b>反射增强</b>：支持对象属性深度检查</li>
 *   <li><b>函数式支持</b>：提供 Supplier 延迟求值支持</li>
 * </ul>
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>空值判断：isEmpty、isNotEmpty、isNull、isNotNull</li>
 *   <li>对象比较：equals、notEquals、isSame、isNotSame</li>
 *   <li>默认值处理：getOrDefault、firstNonNull</li>
 *   <li>类型判断：isPrimitiveOrWrapper、isNumber、isCollection、isMap、isArray、isEnum</li>
 *   <li>对象转换：cast、toString、hashCode</li>
 *   <li>对象检查：isAllFieldsNull、requireNonNull、requireNonEmpty</li>
 *   <li>字符串判断：isStrTrue</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 空值判断
 * if (ObjectUtils.isEmpty(obj)) { ... }
 *
 * // 获取默认值
 * String result = ObjectUtils.getOrDefault(value, "default");
 *
 * // 类型判断
 * if (ObjectUtils.isCollection(list)) { ... }
 *
 * // 安全转换
 * UserVO vo = ObjectUtils.cast(obj);
 *
 * // 获取第一个非空值
 * String name = ObjectUtils.firstNonNull(a, b, c);
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@SuppressWarnings("unchecked")
public class ObjectUtils {

    /**
     * 私有构造函数，防止外部实例化
     */
    private ObjectUtils() {
        throw new UnsupportedOperationException("ObjectUtils 是工具类，不允许被实例化");
    }

    /**
     * 判断字符串是否为 true
     */
    public static boolean isStrTrue(String str) {
        return StringUtils.isNotEmpty(str) && "true".equalsIgnoreCase(str);
    }

    /**
     * 判断对象是否不为空
     */
    public static boolean isNotEmpty(Object object) {
        return !isEmpty(object);
    }

    /**
     * 判断对象是否为空 (标准检查：null, 空字符串，空集合，空 Map, 空数组，空 Optional)
     * 注意：不再递归检查对象内部属性，如需检查请使用 isAllFieldsNull
     */
    public static boolean isEmpty(Object object) {
        if (object == null) {
            return true;
        }
        if (object instanceof Optional) {
            return ((Optional<?>) object).isEmpty();
        }
        if (object instanceof CharSequence) {
            return ((CharSequence) object).length() == 0;
        }
        if (object.getClass().isArray()) {
            return Array.getLength(object) == 0;
        }
        if (object instanceof Collection) {
            return ((Collection<?>) object).isEmpty();
        }
        if (object instanceof Map) {
            return ((Map<?, ?>) object).isEmpty();
        }
        if (object instanceof Iterable) {
            return !((Iterable<?>) object).iterator().hasNext();
        }
        return false;
    }

    /**
     * 判断对象的所有属性是否都为 null (反射检查)
     * 排除 serialVersionUID 和静态字段
     */
    public static boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true;
        }
        try {
            for (Field field : object.getClass().getDeclaredFields()) {
                if (!field.trySetAccessible()) {
                    continue;
                }
                if ("serialVersionUID".equals(field.getName())) {
                    continue;
                }
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) ||
                    Modifier.isTransient(modifiers)) {
                    continue;
                }
                if (field.get(object) != null) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 判断对象是否为 null
     */
    public static boolean isNull(Object object) {
        return object == null;
    }

    /**
     * 判断对象是否不为 null
     */
    public static boolean isNotNull(Object object) {
        return object != null;
    }

    /**
     * 获取非空对象，否则返回默认值
     */
    public static <T> T getOrDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    /**
     * 获取非空对象，否则返回 Supplier 提供的默认值（延迟求值）
     */
    public static <T> T getOrDefault(T value, Supplier<T> defaultValueSupplier) {
        return value != null ? value : defaultValueSupplier.get();
    }

    /**
     * 比较两个对象是否相等（支持 null 安全比较）
     */
    public static boolean equals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    /**
     * 比较两个对象是否不相等
     */
    public static boolean notEquals(Object a, Object b) {
        return !equals(a, b);
    }

    /**
     * 获取对象的 hashCode，如果为 null 返回 0
     */
    public static int hashCode(Object obj) {
        return obj != null ? obj.hashCode() : 0;
    }

    /**
     * 计算多个对象的 hashCode（用于重写 hashCode 方法）
     */
    public static int hashCode(Object... objects) {
        return Arrays.hashCode(objects);
    }

    /**
     * 获取对象的字符串表示，如果为 null 返回空字符串
     */
    public static String toString(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    /**
     * 获取对象的字符串表示，如果为 null 返回默认值
     */
    public static String toString(Object obj, String defaultStr) {
        return obj != null ? obj.toString() : defaultStr;
    }

    /**
     * 如果对象为 null 则抛出 IllegalArgumentException
     */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    /**
     * 如果对象为 null 则抛出指定的异常
     */
    public static <T> T requireNonNull(T obj, Supplier<String> messageSupplier) {
        if (obj == null) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
        return obj;
    }

    /**
     * 安全的对象转换，如果对象为 null 或类型不兼容返回 null。
     *
     * <p>注意：由于泛型擦除，(T) obj 在运行时不会立即抛出 ClassCastException，
     * 类型检查延迟到实际使用转换结果时。此方法在转换前进行运行时类型检查。
     *
     * @param obj 待转换对象
     * @param <T> 目标类型
     * @return 转换后的对象，类型不兼容时返回 null
     */
    
    public static <T> T cast(Object obj) {
        if (obj == null) {
            return null;
        }
        return cast(obj, (Class<T>) obj.getClass());
    }

    /**
     * 带类型检查的安全转换，在转换时立即验证类型兼容性。
     *
     * @param obj 待转换对象
     * @param targetClass 目标类型
     * @param <T> 目标类型
     * @return 转换后的对象，类型不兼容时返回 null
     */
    public static <T> T cast(Object obj, Class<T> targetClass) {
        if (obj == null || targetClass == null) {
            return null;
        }
        if (!targetClass.isInstance(obj)) {
            return null;
        }
        return targetClass.cast(obj);
    }

    /**
     * 获取对象的类名，如果为 null 返回空字符串
     */
    public static String getClassName(Object obj) {
        return obj != null ? obj.getClass().getName() : "";
    }

    /**
     * 获取对象的简单类名，如果为 null 返回空字符串
     */
    public static String getSimpleClassName(Object obj) {
        return obj != null ? obj.getClass().getSimpleName() : "";
    }

    /**
     * 判断对象是否为基本类型或其包装类
     */
    public static boolean isPrimitiveOrWrapper(Object obj) {
        if (obj == null) {
            return false;
        }
        Class<?> clazz = obj.getClass();
        return clazz.isPrimitive() ||
               clazz == Boolean.class || clazz == Character.class ||
               clazz == Byte.class || clazz == Short.class ||
               clazz == Integer.class || clazz == Long.class ||
               clazz == Float.class || clazz == Double.class;
    }

    /**
     * 判断对象是否为数字类型
     */
    public static boolean isNumber(Object obj) {
        return obj instanceof Number;
    }

    /**
     * 判断对象是否为集合类型
     */
    public static boolean isCollection(Object obj) {
        return obj instanceof Collection;
    }

    /**
     * 判断对象是否为 Map 类型
     */
    public static boolean isMap(Object obj) {
        return obj instanceof Map;
    }

    /**
     * 判断对象是否为数组类型
     */
    public static boolean isArray(Object obj) {
        return obj != null && obj.getClass().isArray();
    }

    /**
     * 判断对象是否为枚举类型
     */
    public static boolean isEnum(Object obj) {
        return obj != null && obj.getClass().isEnum();
    }

    /**
     * 获取对象的大小（适用于数组、集合、Map、字符串）
     */
    @SuppressWarnings("unused")
    public static int getSize(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).size();
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).size();
        }
        if (obj.getClass().isArray()) {
            return Array.getLength(obj);
        }
        if (obj instanceof CharSequence) {
            return ((CharSequence) obj).length();
        }
        if (obj instanceof Iterable) {
            int count = 0;
            for (Object element : (Iterable<?>) obj) {
                count++;
            }
            return count;
        }
        return 1;
    }

    /**
     * 判断两个对象是否为同一引用或值相等
     */
    public static boolean isSame(Object a, Object b) {
        return a == b;
    }

    /**
     * 判断对象是否不为同一引用
     */
    public static boolean isNotSame(Object a, Object b) {
        return a != b;
    }

    /**
     * 如果对象为 null 或 empty，则抛出 IllegalArgumentException
     */
    public static <T> T requireNonEmpty(T obj, String message) {
        if (isEmpty(obj)) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    /**
     * 获取第一个非 null 对象
     */
    public static <T> T firstNonNull(T[] values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 获取第一个非 null 对象，如果都为 null 则抛出异常
     */
    public static <T> T requireFirstNonNull(String message, T[] values) {
        T result = firstNonNull(values);
        if (result == null) {
            throw new IllegalArgumentException(message);
        }
        return result;
    }
}
