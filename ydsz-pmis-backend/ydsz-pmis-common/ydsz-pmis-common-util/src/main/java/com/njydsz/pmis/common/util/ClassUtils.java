package com.njydsz.pmis.common.util;

/**
 * 类工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class ClassUtils {

    private ClassUtils() {
    }

    /**
     * 获取 ClassLoader
     */
    public static ClassLoader getClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassUtils.class.getClassLoader();
        }
        return cl;
    }

    /**
     * 加载类
     */
    public static Class<?> loadClass(String className) {
        try {
            return Class.forName(className, true, getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + className, e);
        }
    }

    /**
     * 判断类是否存在
     */
    public static boolean isPresent(String className) {
        try {
            Class.forName(className, false, getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 获取默认值
     */
    @SuppressWarnings("unchecked")
    public static <T> T getDefaultPrimitiveValue(Class<T> type) {
        if (type == boolean.class || type == Boolean.class) {
            return (T) Boolean.FALSE;
        }
        if (type == byte.class || type == Byte.class) {
            return (T) Byte.valueOf((byte) 0);
        }
        if (type == short.class || type == Short.class) {
            return (T) Short.valueOf((short) 0);
        }
        if (type == int.class || type == Integer.class) {
            return (T) Integer.valueOf(0);
        }
        if (type == long.class || type == Long.class) {
            return (T) Long.valueOf(0L);
        }
        if (type == float.class || type == Float.class) {
            return (T) Float.valueOf(0.0f);
        }
        if (type == double.class || type == Double.class) {
            return (T) Double.valueOf(0.0);
        }
        if (type == char.class || type == Character.class) {
            return (T) Character.valueOf('\0');
        }
        return null;
    }

    /**
     * 获取包名
     */
    public static String getPackageName(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        return pkg != null ? pkg.getName() : "";
    }

    /**
     * 获取简单类名
     */
    public static String getShortClassName(Class<?> clazz) {
        return clazz.getSimpleName();
    }

    /**
     * 获取简单类名
     */
    public static String getShortClassName(String className) {
        if (className == null || className.isEmpty()) {
            return "";
        }
        int lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? className : className.substring(lastDot + 1);
    }
}
