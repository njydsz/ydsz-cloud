package com.njydsz.pmis.common.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 反射工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class ReflectUtils {

    private ReflectUtils() {
    }

    /**
     * 获取类所有字段（包括父类）
     */
    public static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    /**
     * 获取指定字段（包括父类）
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 获取字段值
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object obj, String fieldName) {
        Field field = getField(obj.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        field.setAccessible(true);
        try {
            return (T) field.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get field value: " + fieldName, e);
        }
    }

    /**
     * 设置字段值
     */
    public static void setFieldValue(Object obj, String fieldName, Object value) {
        Field field = getField(obj.getClass(), fieldName);
        if (field == null) {
            throw new RuntimeException("Field not found: " + fieldName);
        }
        field.setAccessible(true);
        try {
            field.set(obj, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field value: " + fieldName, e);
        }
    }

    /**
     * 调用方法
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(Object obj, String methodName, Class<?>[] paramTypes, Object[] args) {
        try {
            Method method = obj.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return (T) method.invoke(obj, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke method: " + methodName, e);
        }
    }

    /**
     * 创建实例
     */
    public static <T> T newInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance: " + clazz.getName(), e);
        }
    }

    /**
     * 判断是否为基本类型或包装类型
     */
    public static boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() || clazz == Integer.class || clazz == Long.class
                || clazz == Double.class || clazz == Float.class || clazz == Boolean.class
                || clazz == Character.class || clazz == Byte.class || clazz == Short.class
                || clazz == String.class;
    }
}
