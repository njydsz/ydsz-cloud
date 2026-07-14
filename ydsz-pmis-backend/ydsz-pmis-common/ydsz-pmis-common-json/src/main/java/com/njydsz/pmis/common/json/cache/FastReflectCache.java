package com.njydsz.pmis.common.json.cache;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高性能反射缓存（替。ASM 字节码生成）
 * 
 * <p>预计。MethodHandle/Constructor，消除运行时反射查找开销</p>
 * 
 * <p><b>性能优势：</b></p>
 * <ul>
 *   <li>MethodHandle 缓存，避免重复查找（~1-2ns vs 反射 ~50-100ns。</li>
 *   <li>构造函数缓存，快速实例化对象</li>
 *   <li>Getter/Setter 预编译，JIT 内联优化</li>
 *   <li>。ASM 字节码生成更稳定，易于维。</li>
 * </ul>
 * 
 * @author YdszJson Team
 */
public final class FastReflectCache {

    /** Bean 缓存 */
    private static final ConcurrentHashMap<Class<?>, BeanCache<?>> CACHE = new ConcurrentHashMap<>(2048);

    /**
     * 获取或创。Bean 缓存
     */
    
    public static <T> BeanCache<T> getOrCreate(Class<T> beanType) {
        // computeIfAbsent ensures thread-safe single creation per Class
        // Type safety: cache is keyed by Class, value created with same Class
        return (BeanCache<T>) CACHE.computeIfAbsent(beanType, t -> new BeanCache<>(t));
    }

    /**
     * 清理缓存
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * Bean 缓存（预计算所。MethodHandle。
     */
    public static final class BeanCache<T> {
        /** Bean 类型 */
        public final Class<T> beanType;
        
        /** 默认构造函数*/
        public final Constructor<T> defaultConstructor;
        
        /** 字段缓存数组 */
        public final FieldCache[] fields;
        
        /** 字段名哈希数组（用于快速匹配） */
        public final int[] fieldNameHashes;
        
        /** 字段名数。*/
        public final String[] fieldNames;

        public BeanCache(Class<T> beanType) {
            this.beanType = beanType;
            
            // 缓存默认构造函数
            try {
                this.defaultConstructor = beanType.getDeclaredConstructor();
                this.defaultConstructor.setAccessible(true);
            } catch (Exception e) {
                throw new RuntimeException("No default constructor: " + beanType.getName(), e);
            }
            
            // 预计算字段信。
            Field[] declaredFields = beanType.getDeclaredFields();
            int count = 0;
            for (Field f : declaredFields) {
                int mods = f.getModifiers();
                if (!Modifier.isStatic(mods) && !Modifier.isTransient(mods)) {
                    count++;
                }
            }
            
            this.fields = new FieldCache[count];
            this.fieldNameHashes = new int[count];
            this.fieldNames = new String[count];
            
            int idx = 0;
            for (Field f : declaredFields) {
                int mods = f.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    this.fields[idx] = new FieldCache(f);
                    String name = f.getName();
                    this.fieldNames[idx] = name;
                    this.fieldNameHashes[idx] = name.hashCode();
                    idx++;
                } catch (Exception e) {
                    // skip
                }
            }
        }

        /**
         * 创建实例
         */
        public T newInstance() {
            try {
                return defaultConstructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create instance: " + beanType.getName(), e);
            }
        }
    }

    /**
     * 字段缓存（预计算 Getter/Setter。
     */
    public static final class FieldCache {
        /** 字段名*/
        public final String name;
        
        /** 字段类型 */
        public final Class<?> type;
        
        /** Getter MethodHandle */
        public final MethodHandle getter;
        
        /** Setter MethodHandle */
        public final MethodHandle setter;
        
        /** 类型代码 */
        public final int typeCode;

        public FieldCache(Field field) {
            this.name = field.getName();
            this.type = field.getType();
            this.typeCode = getTypeCode(type);
            
            MethodHandle g = null;
            MethodHandle s = null;
            
            try {
                // 查找 Getter
                Method getterMethod = findGetter(field);
                if (getterMethod != null) {
                    getterMethod.setAccessible(true);
                    g = MethodHandles.lookup().unreflect(getterMethod);
                }
            } catch (Exception e) {
                // skip
            }
            
            try {
                // 查找 Setter
                String setterName = getSetterName(field);
                Method setterMethod = findMethod(field.getDeclaringClass(), setterName, field.getType());
                if (setterMethod != null) {
                    setterMethod.setAccessible(true);
                    s = MethodHandles.lookup().unreflect(setterMethod);
                }
            } catch (Exception e) {
                // skip
            }
            
            this.getter = g;
            this.setter = s;
        }

        /**
         * 获取字段名
         */
        public Object getValue(Object obj) {
            if (getter == null) return null;
            try {
                return getter.invoke(obj);
            } catch (Throwable e) {
                return null;
            }
        }

        /**
         * 设置字段名
         */
        public void setValue(Object obj, Object value) {
            if (setter == null) return;
            try {
                setter.invoke(obj, value);
            } catch (Throwable e) {
                // ignore
            }
        }

        /**
         * 设置 int 字段名
         */
        public void setInt(Object obj, int value) {
            if (setter == null) return;
            try {
                setter.invoke(obj, value);
            } catch (Throwable e) {
                // ignore
            }
        }

        /**
         * 设置 long 字段名
         */
        public void setLong(Object obj, long value) {
            if (setter == null) return;
            try {
                setter.invoke(obj, value);
            } catch (Throwable e) {
                // ignore
            }
        }

        /**
         * 设置 double 字段名
         */
        public void setDouble(Object obj, double value) {
            if (setter == null) return;
            try {
                setter.invoke(obj, value);
            } catch (Throwable e) {
                // ignore
            }
        }

        /**
         * 设置 boolean 字段名
         */
        public void setBoolean(Object obj, boolean value) {
            if (setter == null) return;
            try {
                setter.invoke(obj, value);
            } catch (Throwable e) {
                // ignore
            }
        }

        private static Method findGetter(Field field) {
            Class<?> type = field.getType();
            String name = field.getName();
            Class<?> declaringClass = field.getDeclaringClass();
            
            if (type == boolean.class || type == Boolean.class) {
                if (name.startsWith("is")) {
                    Method m = findMethod(declaringClass, name);
                    if (m != null && m.getReturnType() == type) return m;
                }
                String getterName = "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                return findMethod(declaringClass, getterName);
            }
            
            String getterName = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return findMethod(declaringClass, getterName);
        }

        private static String getSetterName(Field field) {
            String name = field.getName();
            return "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    Class<?>[] types = m.getParameterTypes();
                    if (types.length == paramTypes.length) {
                        boolean match = true;
                        for (int i = 0; i < types.length; i++) {
                            if (types[i] != paramTypes[i]) {
                                match = false;
                                break;
                            }
                        }
                        if (match) return m;
                    }
                }
            }
            return null;
        }

        private static int getTypeCode(Class<?> type) {
            if (type == String.class) return 1;
            if (type == int.class || type == Integer.class) return 2;
            if (type == long.class || type == Long.class) return 3;
            if (type == double.class || type == Double.class) return 4;
            if (type == float.class || type == Float.class) return 5;
            if (type == boolean.class || type == Boolean.class) return 6;
            if (type == short.class || type == Short.class) return 7;
            if (type == byte.class || type == Byte.class) return 8;
            if (type == char.class || type == Character.class) return 9;
            return 10;
        }
    }
}
