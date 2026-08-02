package com.njydsz.common.json.reader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.json.annotation.JsonAlias;
import com.njydsz.common.json.provider.FieldMetadataLoader;

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
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public final class BeanReader<T> {
    
    /** Bean 类型 */
    public final Class<T> beanType;
    
    /** 字段读取器数组*/
    public final FieldReader[] fieldReaders;
    
    /** 字段名哈希缓存*/
    public final int[] fieldNameHashes;
    
    /** 默认构造函数*/
    public final Constructor<T> defaultConstructor;
    
    /** @JsonAnySetter 方法（null 表示无）*/
    public final Method anySetterMethod;
    
    /**
     * 构造函数
     */
    public BeanReader(Class<T> beanType) {
        this.beanType = beanType;
        
        // 检测 @JsonAnySetter 方法
        this.anySetterMethod = FieldMetadataLoader.findAnySetterMethod(beanType);
        
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
     * 从 JSONReader 反序列化出当前 Bean 类型的实例（反序列化主路径）。
     *
     * <p>定位到 {@code '{'} 后通过默认构造函数创建空对象，再逐字段按 hash 匹配
     * {@link FieldReader} 并写入字段值。支持：
     * <ul>
     *   <li>{@code @JsonAlias} 别名字段匹配（按 aliasHashes 二次比对，避免 hash 碰撞误命中）；</li>
     *   <li>{@code @JsonAnySetter}：未匹配字段经 {@link #parseValue} 解析后通过 anySetter 方法写入；</li>
     *   <li>无 anySetter 时，未匹配字段调用 {@code reader.skipValue()} 跳过，保持容错。</li>
     * </ul>
     * </p>
     *
     * @param reader 已初始化的 JSONReader，调用结束后其读取位置推进到对象末尾
     * @return 反序列化得到的 Bean 实例，非 null
     * @throws RuntimeException 当 JSON 意外终止、目标类缺少默认构造函数或字段赋值失败
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
                // @JsonAlias 别名匹配（非 ASM 路径联动）
                if (!matched && fieldReaders[i].aliasHashes.length > 0) {
                    FieldReader fr = fieldReaders[i];
                    for (int j = 0; j < fr.aliasHashes.length; j++) {
                        if (fr.aliasHashes[j] == hash && fr.aliases[j].equals(fieldName)) {
                            fr.readValue(reader, obj);
                            matched = true;
                            break;
                        }
                    }
                    if (matched) break;
                }
            }
            
            if (!matched) {
                if (anySetterMethod != null) {
                    // @JsonAnySetter：将未匹配的属性通过方法写入
                    String rawValue = reader.readRawValue().trim();
                    try {
                        Object parsedValue = parseValue(rawValue);
                        anySetterMethod.invoke(obj, fieldName, parsedValue);
                    } catch (Exception e) {
                        // 调用失败时跳过该字段
                    }
                } else {
                    reader.skipValue();
                }
            }
        }
        
        return obj;
    }
    
    /**
     * 将原始 JSON 值字符串解析为 Java 对象。
     * 
     * <p>用于 @JsonAnySetter 路径，将未匹配字段的值解析为简单类型。</p>
     * 
     * @param rawValue 原始 JSON 值字符串（如 {@code "hello"}, {@code 123}, {@code true}, {@code null}, {@code {...}}）
     * @return 解析后的 Java 对象
     */
    private static Object parseValue(String rawValue) {
        if (rawValue == null || rawValue.isEmpty() || "null".equals(rawValue)) {
            return null;
        }
        char first = rawValue.charAt(0);
        if (first == '"') {
            // 去除首尾引号
            return rawValue.substring(1, rawValue.length() - 1);
        }
        if (first == '{' || first == '[') {
            // 复杂对象/数组，返回原始字符串
            return rawValue;
        }
        if ("true".equals(rawValue) || "false".equals(rawValue)) {
            return Boolean.parseBoolean(rawValue);
        }
        // 尝试数值解析
        try {
            if (rawValue.contains(".") || rawValue.contains("e") || rawValue.contains("E")) {
                return Double.parseDouble(rawValue);
            }
            return Long.parseLong(rawValue);
        } catch (NumberFormatException e) {
            return rawValue;
        }
    }

    /**
     * 字段读取器（消除 MethodHandle，改用直接字段访问）
     */
    public static final class FieldReader {

        public final String fieldName;
        public final Class<?> fieldType;
        public final Field field;
        public final int typeCode;

        /** 反序列化别名列表（来自 @JsonAlias 注解） */
        public final String[] aliases;
        /** 别名哈希缓存（与 fieldNameHashes 配合使用） */
        public final int[] aliasHashes;

        public FieldReader(Field field) {
            this.fieldName = field.getName();
            this.fieldType = field.getType();
            this.field = field;
            this.field.setAccessible(true);
            this.typeCode = getTypeCode(fieldType);

            // 加载 @JsonAlias 别名列表
            JsonAlias aliasAnnotation = field.getAnnotation(JsonAlias.class);
            if (aliasAnnotation != null && aliasAnnotation.value().length > 0) {
                this.aliases = aliasAnnotation.value();
                this.aliasHashes = new int[aliases.length];
                for (int i = 0; i < aliases.length; i++) {
                    aliasHashes[i] = aliases[i].hashCode();
                }
            } else {
                this.aliases = new String[0];
                this.aliasHashes = new int[0];
            }
        }

        /**
         * 将 reader 当前位置的值按字段类型写入目标对象。
         *
         * <p>依据 {@code typeCode} 分发：基础类型（String/int/long/double/float/boolean）直接读取并
         * 通过 {@link Field#set} 赋值（读取到 null 时置为 null）；复杂类型（嵌套对象、List/Collection、
         * Map）递归委托 {@link #getOrCreateForType} 或 reader 内建方法解析。字段写入失败（如类型不兼容）
         * 包装为 {@link RuntimeException} 抛出。</p>
         *
         * @param reader 已定位到值起始的 JSONReader
         * @param obj    目标 Bean 实例，字段值写入其中
         */
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
                            List<Object> listValue = reader.readArray(Object.class);
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
    
    
    /**
     * 获取或创建指定 Bean 类型的读取器（全局缓存）。
     *
     * <p>{@link ConcurrentHashMap#computeIfAbsent} 保证每个 Class 仅被构造一次，
     * 多线程下不会出现重复创建。BeanReader 的构建涉及反射扫描字段、缓存构造函数与
     * 探测 {@code @JsonAnySetter}，开销较大，因此复用缓存对反序列化性能至关重要。</p>
     *
     * @param beanType 目标 Bean 类型，不可为 null（否则抛出 NPE）
     * @param <T>      Bean 类型
     * @return 对应类型的 BeanReader，非 null
     */
    public static <T> BeanReader<T> getOrCreate(Class<T> beanType) {
        // computeIfAbsent ensures thread-safe single creation per Class
        // Type safety: cache is keyed by Class, value created with same Class
        return (BeanReader<T>) CACHE.computeIfAbsent(beanType, t -> new BeanReader<>(t));
    }

    /**
     * 获取或创建指定 Bean 类型的读取器（非泛型入口）。
     *
     * <p>与 {@link #getOrCreate(Class)} 等价，但返回原始 {@code BeanReader<?>}，
     * 适用于调用方仅持有运行时 {@link Class}（无具体泛型参数）的场景，例如
     * {@link FieldReader#readValue} 解析嵌套对象时按字段类型递归获取读取器。
     * 同样基于进程级 {@link ConcurrentHashMap} 缓存，保证每类型仅构建一次。</p>
     *
     * @param beanType 目标 Bean 类型，不可为 null（否则抛出 NPE）
     * @return 对应类型的 BeanReader，非 null
     */
    public static BeanReader<?> getOrCreateForType(Class<?> beanType) {
        return CACHE.computeIfAbsent(beanType, t -> new BeanReader<>(t));
    }
    
    /**
     * 清空全局 BeanReader 缓存。
     *
     * <p>仅在类结构热更新或测试隔离场景下使用；清空后下次访问将重新反射构建读取器。
     * 注意该缓存为进程级 {@link ConcurrentHashMap}，操作影响所有线程，
     * 生产运行期慎用，避免并发反序列化瞬间因重建产生性能抖动。</p>
     */
    public static void clearCache() {
        CACHE.clear();
    }
}
