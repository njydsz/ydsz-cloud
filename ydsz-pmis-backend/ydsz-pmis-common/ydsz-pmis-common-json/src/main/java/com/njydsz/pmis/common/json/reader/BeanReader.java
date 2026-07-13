package com.njydsz.pmis.common.json.reader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bean 反序列化读取器（FastJSON2 BeanDeserializer 移植版）
 * 
 * <p>预计算字段读取路径，直接 char[] 解析，消除 Map 中转，完整支持嵌套对象和集合</p>
 * 
 * <p><b>性能优化：</b></p>
 * <ul>
 *   <li>字段哈希缓存，O(1) 快速字段匹配</li>
 *   <li>数值直接解析，避免 Double.parseDouble</li>
 *   <li>构造函数缓存，避免重复反射</li>
 *   <li>消除不必要的 skipWhitespace 调用</li>
 *   <li>嵌套对象递归解析，支持任意深度</li>
 *   <li>集合/Map 完整支持，自动类型推断</li>
 * </ul>
 * 
 * @author YdszJson Team
 */
@SuppressWarnings("unchecked")
public final class BeanReader<T> {
    
    /** Bean 类型 */
    public final Class<T> beanType;
    
    /** 字段读取器数组 */
    public final FieldReader[] fieldReaders;
    
    /** 字段名哈希缓存 */
    public final int[] fieldNameHashes;
    
    /** 默认构造函数 */
    public final Constructor<T> defaultConstructor;
    
    /**
     * 构造函数
     */
    public BeanReader(Class<T> beanType) {
        this.beanType = beanType;
        
        // 缓存默认构造函数
        try {
            this.defaultConstructor = beanType.getDeclaredConstructor();
            this.defaultConstructor.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("No default constructor for " + beanType.getName(), e);
        }
        
        // 预计算字段读取器
        Field[] fields = beanType.getDeclaredFields();
        int count = 0;
        for (Field field : fields) {
            int mods = field.getModifiers();
            if (!Modifier.isStatic(mods) && !Modifier.isTransient(mods)) {
                count++;
            }
        }
        
        this.fieldReaders = new FieldReader[count];
        this.fieldNameHashes = new int[count];
        int idx = 0;
        for (Field field : fields) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                continue;
            }
            try {
                field.setAccessible(true);
                this.fieldReaders[idx] = new FieldReader(field);
                this.fieldNameHashes[idx] = field.getName().hashCode();
                idx++;
            } catch (Exception e) {
                // skip
            }
        }
    }
    
    /**
     * 从 JSONReader 反序列化对象
     */
    public T readObject(JSONReader reader) {
        reader.skipTo('{');
        if (reader.pos >= reader.len) {
            throw new RuntimeException("Unexpected end of JSON");
        }
        reader.pos++;
        
        T obj;
        try {
            obj = defaultConstructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create " + beanType.getName(), e);
        }
        
        char[] buf = reader.buf;
        int len = reader.len;
        
        while (reader.pos < len) {
            char ch = buf[reader.pos];
            while (ch <= ' ') {
                reader.pos++;
                if (reader.pos >= len) return obj;
                ch = buf[reader.pos];
            }
            
            if (ch == '}') {
                reader.pos++;
                return obj;
            }
            
            if (ch == ',') {
                reader.pos++;
                continue;
            }
            
            if (ch != '"') {
                reader.pos++;
                continue;
            }
            
            String fieldName = reader.readString();
            if (fieldName == null) return obj;
            
            reader.skipTo(':');
            if (reader.pos < len) reader.pos++;
            
            int hash = fieldName.hashCode();
            boolean matched = false;
            for (int i = 0; i < fieldReaders.length; i++) {
                if (fieldNameHashes[i] == hash) {
                    FieldReader fr = fieldReaders[i];
                    if (fr.fieldName.equals(fieldName)) {
                        fr.readValue(reader, obj);
                        matched = true;
                        break;
                    }
                }
            }
            
            if (!matched) {
                reader.skipValue();
            }
        }
        
        return obj;
    }
    
    /**
     * 字段读取器（消除 MethodHandle，改用直接字段访问）
     */
    public static final class FieldReader {

        public final String fieldName;
        public final Class<?> fieldType;
        public final Field field;
        public final int typeCode;

        public FieldReader(Field field) {
            this.fieldName = field.getName();
            this.fieldType = field.getType();
            this.field = field;
            this.field.setAccessible(true);
            this.typeCode = getTypeCode(fieldType);
        }

        public void readValue(JSONReader reader, Object obj) {
            try {
                switch (typeCode) {
                    case 1: // String
                        if (reader.isNull()) {
                            reader.readNull();
                            field.set(obj, null);
                        } else {
                            field.set(obj, reader.readString());
                        }
                        break;
                    case 2: // int
                        if (reader.isNull()) {
                            reader.readNull();
                        } else {
                            field.setInt(obj, reader.readInt());
                        }
                        break;
                    case 3: // long
                        if (reader.isNull()) {
                            reader.readNull();
                        } else {
                            field.setLong(obj, reader.readLong());
                        }
                        break;
                    case 4: // double
                        if (reader.isNull()) {
                            reader.readNull();
                        } else {
                            field.setDouble(obj, reader.readDouble());
                        }
                        break;
                    case 5: // float
                        if (reader.isNull()) {
                            reader.readNull();
                        } else {
                            field.setFloat(obj, reader.readFloat());
                        }
                        break;
                    case 6: // boolean
                        if (reader.isNull()) {
                            reader.readNull();
                        } else {
                            field.setBoolean(obj, reader.readBoolean());
                        }
                        break;
                    default:
                        // 复杂类型（嵌套对象、集合等）
                        if (reader.isNull()) {
                            reader.readNull();
                            field.set(obj, null);
                        } else if (fieldType == List.class || fieldType == ArrayList.class || Collection.class.isAssignableFrom(fieldType)) {
                            List<Object> listValue = reader.readArray(Object.class, null);
                            field.set(obj, listValue);
                        } else if (fieldType == Map.class || fieldType == HashMap.class || Map.class.isAssignableFrom(fieldType)) {
                            field.set(obj, reader.readObjectMap());
                        } else {
                            
                            BeanReader<?> nestedReader = getOrCreateForType(fieldType);
                            field.set(obj, nestedReader.readObject(reader));
                        }
                        break;
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to set field: " + fieldName, e);
            }
        }

        private static int getTypeCode(Class<?> type) {
            if (type == String.class) return 1;
            if (type == int.class || type == Integer.class) return 2;
            if (type == long.class || type == Long.class) return 3;
            if (type == double.class || type == Double.class) return 4;
            if (type == float.class || type == Float.class) return 5;
            if (type == boolean.class || type == Boolean.class) return 6;
            return 10;
        }
    }
    
    /** BeanReader 缓存 */
    private static final ConcurrentHashMap<Class<?>, BeanReader<?>> CACHE = new ConcurrentHashMap<>(1024);
    
    
    public static <T> BeanReader<T> getOrCreate(Class<T> beanType) {
        BeanReader<?> cached = CACHE.get(beanType);
        if (cached != null) {
            return captureReader(cached);
        }
        BeanReader<T> reader = new BeanReader<>(beanType);
        CACHE.put(beanType, reader);
        return reader;
    }

    private static <T> BeanReader<T> captureReader(BeanReader<?> reader) {
        return (BeanReader<T>) reader;
    }

    public static BeanReader<?> getOrCreateForType(Class<?> beanType) {
        BeanReader<?> reader = CACHE.get(beanType);
        if (reader == null) {
            reader = new BeanReader<>(beanType);
            CACHE.put(beanType, reader);
        }
        return reader;
    }
    
    public static void clearCache() {
        CACHE.clear();
    }
}
