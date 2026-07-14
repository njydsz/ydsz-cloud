package com.njydsz.pmis.common.json.reader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高性能 Bean 反序列化器（FastJSON2 ObjectReader 移植版）
 * 
 * <p>预计算字段元数据，消除运行时反射，支持复杂类型（嵌套对象、集合、Map。/p>
 * 
 * <p><b>性能优化：</b></p>
 * <ul>
 *   <li>构造函数缓存+ 直接 MethodHandle 调用</li>
 *   <li>字段 setter 预计算，避免运行时查。</li>
 *   <li>类型代码分类，快速分发解析逻辑</li>
 *   <li>嵌套对象递归解析，支持任意深。</li>
 *   <li>集合/Map 完整支持，自动类型转。</li>
 * </ul>
 * 
 * @author Json Team
 */
public final class ObjectReader<T> {
    
    public final Class<T> objectType;
    public final Constructor<T> constructor;
    public final FieldReader[] fieldReaders;
    public final int[] fieldNameHashes;
    public final String[] fieldNames;
    public final int fieldCount;
    
    public ObjectReader(Class<T> objectType) {
        this.objectType = objectType;
        
        try {
            this.constructor = objectType.getDeclaredConstructor();
            this.constructor.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("No default constructor: " + objectType.getName(), e);
        }
        
        Field[] fields = getSerializableFields(objectType);
        this.fieldCount = fields.length;
        this.fieldReaders = new FieldReader[fieldCount];
        this.fieldNameHashes = new int[fieldCount];
        this.fieldNames = new String[fieldCount];
        
        for (int i = 0; i < fieldCount; i++) {
            Field field = fields[i];
            field.setAccessible(true);
            this.fieldReaders[i] = new FieldReader(field);
            String name = field.getName();
            this.fieldNames[i] = name;
            this.fieldNameHashes[i] = name.hashCode();
        }
    }
    
    /**
     * 获取可序列化字段（排。static/transient。
     */
    private Field[] getSerializableFields(Class<?> clazz) {
        Field[] allFields = clazz.getDeclaredFields();
        int count = 0;
        for (Field f : allFields) {
            int mods = f.getModifiers();
            if (!Modifier.isStatic(mods) && 
                !Modifier.isTransient(mods)) {
                count++;
            }
        }
        
        Field[] result = new Field[count];
        int idx = 0;
        for (Field f : allFields) {
            int mods = f.getModifiers();
            if (!Modifier.isStatic(mods) && 
                !Modifier.isTransient(mods)) {
                result[idx++] = f;
            }
        }
        return result;
    }
    
    /**
     * 。JSONReader 反序列化对象
     */
    public T readObject(JSONReader reader) {
        reader.skipWhitespace();
        
        // 跳过 {
        if (reader.pos >= reader.len || reader.buf[reader.pos] != '{') {
            throw new RuntimeException("Expected { at position " + reader.pos);
        }
        reader.pos++;
        
        // 创建实例
        T object;
        try {
            object = constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance: " + objectType.getName(), e);
        }
        
        char[] buf = reader.buf;
        int len = reader.len;
        
        // 解析字段
        while (reader.pos < len) {
            // 跳过空白
            while (reader.pos < len && buf[reader.pos] <= ' ') {
                reader.pos++;
            }
            
            // 检查对象结。
            if (reader.pos >= len) break;
            if (buf[reader.pos] == '}') {
                reader.pos++;
                return object;
            }
            
            // 跳过逗号
            if (buf[reader.pos] == ',') {
                reader.pos++;
                continue;
            }
            
            // 读取字段名
            if (buf[reader.pos] != '"') {
                reader.pos++;
                continue;
            }
            
            String fieldName = reader.readFieldNameFast();
            if (fieldName == null) break;
            
            // 读取冒号
            reader.skipTo(':');
            if (reader.pos < len) reader.pos++;
            
            // 匹配字段
            int hash = fieldName.hashCode();
            boolean matched = false;
            for (int i = 0; i < fieldCount; i++) {
                if (fieldNameHashes[i] == hash) {
                    if (fieldNames[i].equals(fieldName)) {
                        fieldReaders[i].readValue(reader, object);
                        matched = true;
                        break;
                    }
                }
            }
            
            // 未匹配，跳过。
            if (!matched) {
                reader.skipValue();
            }
        }
        
        return object;
    }
    
    /**
     * 字段读取。
     */
    public static final class FieldReader {
        public final String fieldName;
        public final Class<?> fieldType;
        public final MethodHandle setter;
        public final int typeCode;
        
        // 复杂类型支持
        public final boolean isCollection;
        public final boolean isMap;
        public final boolean isBean;
        public final Class<?> componentType; // List/Map 的元素类型
        
        public FieldReader(Field field) {
            this.fieldName = field.getName();
            this.fieldType = field.getType();
            this.typeCode = getTypeCode(fieldType);
            this.isCollection = isCollectionType(fieldType);
            this.isMap = isMapType(fieldType);
            this.isBean = !isSimpleType(fieldType) && !isCollection && !isMap;
            this.componentType = getComponentType(field);
            
            try {
                this.setter = MethodHandles.lookup().unreflectSetter(field);
            } catch (Throwable e) {
                throw new RuntimeException("Failed to create setter: " + fieldName, e);
            }
        }
        
        public void readValue(JSONReader reader, Object target) {
            try {
                switch (typeCode) {
                    case 1: // String
                        if (reader.isNull()) {
                            reader.readNull();
                            setter.invoke(target, null);
                        } else {
                            setter.invoke(target, reader.readString());
                        }
                        break;
                    case 2: // int/Integer
                        if (reader.isNull()) {
                            reader.readNull();
                            setter.invoke(target, fieldType == int.class ? 0 : null);
                        } else {
                            setter.invoke(target, reader.readInt());
                        }
                        break;
                    case 3: // long/Long
                        if (reader.isNull()) {
                            reader.readNull();
                            setter.invoke(target, fieldType == long.class ? 0L : null);
                        } else {
                            setter.invoke(target, reader.readLong());
                        }
                        break;
                    case 4: // double/Double
                        if (reader.isNull()) {
                            reader.readNull();
                            setter.invoke(target, fieldType == double.class ? 0.0 : null);
                        } else {
                            setter.invoke(target, reader.readDouble());
                        }
                        break;
                    case 5: // float/Float
                        if (reader.isNull()) {
                            reader.readNull();
                            setter.invoke(target, fieldType == float.class ? 0.0f : null);
                        } else {
                            setter.invoke(target, (float) reader.readDouble());
                        }
                        break;
                    case 6: // boolean/Boolean
                        if (reader.isNull()) {
                            reader.readNull();
                            setter.invoke(target, fieldType == boolean.class ? false : null);
                        } else {
                            setter.invoke(target, reader.readBoolean());
                        }
                        break;
                    case 7: // short/Short
                        if (reader.isNull()) {
                            reader.readNull();
                            setter.invoke(target, fieldType == short.class ? (short)0 : null);
                        } else {
                            setter.invoke(target, (short) reader.readInt());
                        }
                        break;
                    case 8: // byte/Byte
                        if (reader.isNull()) {
                            reader.readNull();
                            setter.invoke(target, fieldType == byte.class ? (byte)0 : null);
                        } else {
                            setter.invoke(target, (byte) reader.readInt());
                        }
                        break;
                    case 9: // char/Character
                        if (reader.isNull()) {
                            reader.readNull();
                            setter.invoke(target, fieldType == char.class ? '\0' : null);
                        } else {
                            String s = reader.readString();
                            setter.invoke(target, s == null || s.isEmpty() ? '\0' : s.charAt(0));
                        }
                        break;
                    default:
                        // 复杂类型
                        if (reader.isNull()) {
                            reader.readNull();
                            setter.invoke(target, null);
                        } else if (isCollection) {
                            
                            ObjectReader<?> componentReader = componentType != null && !isSimpleType(componentType) ? 
                                getOrCreateForType(componentType) : null;
                            
                            List<Object> arrayValue = reader.readArray(componentType, componentReader);
                            setter.invoke(target, arrayValue);
                        } else if (isMap) {
                            setter.invoke(target, reader.readObjectMap());
                        } else if (isBean) {
                            Class<?> targetType = componentType != null ? componentType : fieldType;
                            
                            ObjectReader<?> nestedReader = getOrCreateForType(targetType);
                            setter.invoke(target, nestedReader.readObject(reader));
                        } else {
                            // 未知类型，跳。
                            reader.skipValue();
                        }
                        break;
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed to set field " + fieldName + ": " + e.getMessage(), e);
            }
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
            return 10; // 复杂类型
        }
        
        private static boolean isSimpleType(Class<?> type) {
            return type == String.class || type == int.class || type == Integer.class ||
                   type == long.class || type == Long.class || type == double.class || 
                   type == Double.class || type == float.class || type == Float.class ||
                   type == boolean.class || type == Boolean.class || type == short.class ||
                   type == Short.class || type == byte.class || type == Byte.class ||
                   type == char.class || type == Character.class;
        }
        
        private static boolean isCollectionType(Class<?> type) {
            return type == List.class || type == ArrayList.class || 
                   Collection.class.isAssignableFrom(type);
        }
        
        private static boolean isMapType(Class<?> type) {
            return type == Map.class || type == HashMap.class || 
                   Map.class.isAssignableFrom(type);
        }
        
        private static Class<?> getComponentType(Field field) {
            // 尝试从泛型获取元素类型
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericType;
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    return (Class<?>) typeArgs[0];
                }
            }
            return Object.class;
        }
    }
    
    /** Cache */
    private static final ConcurrentHashMap<Class<?>, ObjectReader<?>> CACHE = new ConcurrentHashMap<>(2048);

    public static ObjectReader<?> getOrCreateForType(Class<?> type) {
        return CACHE.computeIfAbsent(type, t -> new ObjectReader<>(t));
    }

    public static void clearCache() {
        CACHE.clear();
    }
}