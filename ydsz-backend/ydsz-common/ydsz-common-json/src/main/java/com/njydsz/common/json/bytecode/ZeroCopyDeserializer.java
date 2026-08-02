package com.njydsz.common.json.bytecode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.json.annotation.JsonAlias;
import com.njydsz.common.json.annotation.JsonProperty;
import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.exception.JsonDeserializationException;
import com.njydsz.common.json.util.JsonTypeUtils;
import com.njydsz.common.json.util.StringInterner;

/**
 * 零拷贝反序列化生成器（深度优化版 v3.5.0）
 *
 * <p>核心优化：</p>
 * <ul>
 *   <li>char[] 数组访问 - 避免 String.charAt() 开销</li>
 *   <li>直接字段设置 - 避免 Map 中转</li>
 *   <li>hashCode 快速匹配 - 先比哈希再比字符</li>
 *   <li>基本类型优化 - 避免装箱/拆箱</li>
 *   <li>零拷贝数字解析 - JIT 自动向量化加速</li>
 *   <li>对象池复用 - ThreadLocal 缓存 ArrayList/LinkedHashMap</li>
 *   <li>String 池化 - 复用常用字段名和短字符串</li>
 *   <li>Constructor 缓存 - 避免反射开销</li>
 *   <li>分级反序列化 - 根据字段数选择最优策略</li>
 *   <li>字段访问缓存 - 预计算类型码与 setter 策略</li>
 *   <li>集合预分配优化 - 基于字段数的容量估算</li>
 * </ul>
 *
 * <p><b>反序列化器分级：</b></p>
 * <ul>
 *   <li>SingleFieldDeserializer - 1 个简单字段，性能最高</li>
 *   <li>TwoFieldDeserializer - 2 个简单字段</li>
 *   <li>UltraFastDeserializer - 3 个简单字段，hashCode 优化</li>
 *   <li>FastDeserializer - 4+ 个字段，HashMap 查找</li>
 *   <li>StandardDeserializer - 任意字段数，完整功能</li>
 * </ul>
 *
 * <p><b>设计模式：</b></p>
 * <ul>
 *   <li>策略模式 - 根据字段数量选择最优反序列化策略</li>
 *   <li>享元模式 - 反序列化器缓存、字符串驻留</li>
 *   <li>对象池 - ThreadLocal 复用集合对象</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ZeroCopyDeserializer {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ZeroCopyDeserializer.class);

    /** 反序列化器缓存*/
    private static final ConcurrentHashMap<Class<?>, BeanDeserializer> CACHE = new ConcurrentHashMap<>();

    /** Constructor 缓存 */
    private static final ConcurrentHashMap<Class<?>, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    /** 字符串驻留器（减少重复字符串分配。 */
    private static final StringInterner STRING_INTERNER = new StringInterner(4096);

    /** ArrayList 对象。*/
    private static final ThreadLocal<ArrayList<Object>> ARRAY_LIST_POOL = ThreadLocal.withInitial(() -> new ArrayList<>(64));

    /** LinkedHashMap 对象。*/
    private static final ThreadLocal<LinkedHashMap<String, Object>> LINKED_HASH_MAP_POOL = ThreadLocal.withInitial(() -> new LinkedHashMap<>(64));

    /**
     * 从池中获取 ArrayList
     */
    private static ArrayList<Object> borrowArrayList() {
        ArrayList<Object> list = ARRAY_LIST_POOL.get();
        list.clear();
        return list;
    }

    /**
     * 归还 ArrayList 到池。
     */
    private static void returnArrayList(ArrayList<Object> list) {
        list.clear();
    }

    /**
     * 从池中获取 LinkedHashMap
     */
    private static LinkedHashMap<String, Object> borrowLinkedHashMap() {
        LinkedHashMap<String, Object> map = LINKED_HASH_MAP_POOL.get();
        map.clear();
        return map;
    }

    /**
     * 归还 LinkedHashMap 到池。
     */
    private static void returnLinkedHashMap(LinkedHashMap<String, Object> map) {
        map.clear();
    }

    /**
     * 驻留字符串（短字符串复用。
     *
     * @param str 待驻留的字符。
     * @return 驻留后的字符串实体
     */
private static String internString(String str) {
return STRING_INTERNER.intern(str);
}

/**
 * 获取字段的 JSON 名称（优先从 @JsonProperty 注解获取，回退到 Java 字段名）。
 *
 * @param field Java 字段
 * @return JSON 名称
 */
private static String getJsonName(Field field) {
JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
if (jsonProperty != null && !jsonProperty.value().isEmpty()) {
    return jsonProperty.value();
}
return field.getName();
}

    /**
     * 获取反序列化。
     */
    public static <T> BeanDeserializer getDeserializer(Class<T> clazz) {
        AutoTypeChecker.checkType(clazz);
        return CACHE.computeIfAbsent(clazz, c -> createDeserializer(c));
    }

    /**
     * 创建反序列化。
     */
    private static <T> BeanDeserializer createDeserializer(Class<T> clazz) {
        FieldInfo[] fields = loadFields(clazz);

        if (fields.length == 0) {
            return new BeanDeserializer() {
                @Override
                public Object deserialize(String json) throws Exception {
                    return clazz.getDeclaredConstructor().newInstance();
                }
                @Override
                public Object deserialize(char[] chars, int offset, int len) throws Exception {
                    return clazz.getDeclaredConstructor().newInstance();
                }
            };
        }

        Constructor<T> constructor = getConstructor(clazz);

        if (fields.length == 1 && isSimpleField(fields[0])) {
            return new SingleFieldDeserializer<>(clazz, constructor, fields[0]);
        } else if (fields.length == 2 && isSimpleFields(fields)) {
            return new TwoFieldDeserializer<>(clazz, constructor, fields);
        } else if (fields.length <= 4 && isSimpleFields(fields)) {
            return new UltraFastDeserializer<>(clazz, constructor, fields);
        } else if (fields.length <= 8) {
            return new FastDeserializer<>(clazz, constructor, fields);
        } else {
            return new StandardDeserializer<>(clazz, constructor, fields);
        }
    }

    private static boolean isSimpleField(FieldInfo f) {
        return JsonTypeUtils.isSimpleType(f.type);
    }

    private static boolean isSimpleFields(FieldInfo[] fields) {
        for (FieldInfo f : fields) {
            if (!isSimpleField(f)) {
                return false;
            }
        }
        return true;
    }

    
    private static <T> Constructor<T> getConstructor(Class<T> clazz) {
        Constructor<?> cached = CONSTRUCTOR_CACHE.get(clazz);
        if (cached != null) {
            return captureConstructor(cached);
        }
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            CONSTRUCTOR_CACHE.put(clazz, ctor);
            return captureConstructor(ctor);
        } catch (NoSuchMethodException e) {
            throw new JsonDeserializationException("No default constructor for " + clazz.getName(), e);
        }
    }

    private static <T> Constructor<T> captureConstructor(Constructor<?> ctor) {
        return (Constructor<T>) ctor;
    }

    private static FieldInfo[] loadFields(Class<?> clazz) {
        Field[] declaredFields = clazz.getDeclaredFields();
        List<FieldInfo> fieldList = new ArrayList<>(declaredFields.length);

        for (Field field : declaredFields) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                continue;
            }
            fieldList.add(new FieldInfo(field));
        }

        return fieldList.toArray(new FieldInfo[0]);
    }

private static class FieldInfo {
final String name;
final String[] aliases;
final Class<?> type;
final Field field;
final MethodHandle setter;
final int nameHashCode;
final int typeCode;
final Class<?> elementType;

FieldInfo(Field field) {
this.field = field;
this.name = internString(getJsonName(field));
this.type = field.getType();
this.nameHashCode = name.hashCode();
this.typeCode = computeTypeCode(this.type);
this.elementType = extractElementType(field.getGenericType());

// 加载 @JsonAlias 别名列表
JsonAlias aliasAnnotation = field.getAnnotation(JsonAlias.class);
this.aliases = aliasAnnotation != null ? aliasAnnotation.value() : new String[0];

field.setAccessible(true);

            MethodHandle s = null;
            try {
                s = MethodHandles.lookup().unreflectSetter(field);
            } catch (Exception e) {
                // 反射操作失败，忽略此路径，回退到默认行为
            }
            this.setter = s;
        }

        /**
         * 提取泛型元素类型（如 List<User> -> User.class。
         */
        private static Class<?> extractElementType(Type genericType) {
            if (genericType instanceof ParameterizedType pt) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> clazz) {
                    return clazz;
                }
            }
            return Object.class;
        }

        static int computeTypeCode(Class<?> type) {
            if (type == int.class || type == Integer.class) return 1;
            if (type == long.class || type == Long.class) return 2;
            if (type == double.class || type == Double.class) return 3;
            if (type == float.class || type == Float.class) return 4;
            if (type == boolean.class || type == Boolean.class) return 5;
            if (type == String.class) return 6;
            return 0;
        }

        void setValue(Object obj, Object value) {
            if (setter != null) {
                try {
                    setter.invoke(obj, value);
                    return;
                } catch (Throwable e) {
                log.debug("Caught exception (ignored): {}", e.getMessage());
                } }
            }
            try {
                field.set(obj, value);
            } catch (IllegalAccessException e) {
            log.debug("Caught exception (ignored): {}", e.getMessage());
            } }
        }

        void setIntValue(Object obj, int value) {
            try {
                field.setInt(obj, value);
            } catch (IllegalAccessException e) {
            log.debug("Caught exception (ignored): {}", e.getMessage());
            } }
        }

        void setLongValue(Object obj, long value) {
            try {
                field.setLong(obj, value);
            } catch (IllegalAccessException e) {
            log.debug("Caught exception (ignored): {}", e.getMessage());
            } }
        }

        void setDoubleValue(Object obj, double value) {
            try {
                field.setDouble(obj, value);
            } catch (IllegalAccessException e) {
            log.debug("Caught exception (ignored): {}", e.getMessage());
            } }
        }

        void setBooleanValue(Object obj, boolean value) {
            try {
                field.setBoolean(obj, value);
            } catch (IllegalAccessException e) {
            log.debug("Caught exception (ignored): {}", e.getMessage());
            } }
        }

        /** package-private */ boolean matchesHashCode(int hash) {
            return nameHashCode == hash;
        }
    }

    public interface BeanDeserializer {
        Object deserialize(String json) throws Exception;
        Object deserialize(char[] chars, int offset, int len) throws Exception;
    }

    private static class SingleFieldDeserializer<T> implements BeanDeserializer {
        private final Constructor<T> constructor;
        private final FieldInfo field;
        private final String fieldName;
        private final char[] fieldNameChars;

        SingleFieldDeserializer(Class<T> clazz, Constructor<T> constructor, FieldInfo field) {
            this.constructor = constructor;
            this.field = field;
            this.fieldName = field.name;
            this.fieldNameChars = field.name.toCharArray();
        }

        @Override
        public Object deserialize(String json) throws Exception {
            char[] chars = json.toCharArray();
            return deserialize(chars, 0, chars.length);
        }

        @Override
        public Object deserialize(char[] chars, int offset, int len) throws Exception {
            int pos = offset;
            T instance = constructor.newInstance();

            while (pos < len && chars[pos] <= ' ') pos++;
            if (pos >= len || chars[pos] != '{') return instance;
            pos++;

            while (pos < len) {
                while (pos < len && chars[pos] <= ' ') pos++;
                if (pos >= len) break;
                char c = chars[pos];
                if (c == '}') break;
                if (c == ',') { pos++; continue; }
                if (c != '"') { pos++; continue; }

                int nameStart = pos + 1;
                int nameEnd = nameStart;
                while (nameEnd < len && chars[nameEnd] != '"') nameEnd++;
                pos = nameEnd + 1;

                while (pos < len && chars[pos] <= ' ') pos++;
                if (pos < len && chars[pos] == ':') pos++;

                int nameLen = nameEnd - nameStart;
                if (nameLen == fieldName.length() && BytesUtil.equals(chars, nameStart, fieldNameChars, 0, nameLen)) {
                    Object value = parseValue(chars, pos, field.type, len);
                    if (value != null) field.setValue(instance, value);
                    return instance;
                }

                pos = skipValue(chars, pos, len);
                if (pos >= len) break;
            }

            return instance;
        }
    }

    private static class TwoFieldDeserializer<T> implements BeanDeserializer {
        private final Constructor<T> constructor;
        private final FieldInfo[] fields;
        private final String[] fieldNames;
        private final char[][] fieldNameCharsArray;

        TwoFieldDeserializer(Class<T> clazz, Constructor<T> constructor, FieldInfo[] fields) {
            this.constructor = constructor;
            this.fields = fields;
            this.fieldNames = new String[fields.length];
            this.fieldNameCharsArray = new char[fields.length][];
            for (int i = 0; i < fields.length; i++) {
                fieldNames[i] = fields[i].name;
                fieldNameCharsArray[i] = fields[i].name.toCharArray();
            }
        }

        @Override
        public Object deserialize(String json) throws Exception {
            char[] chars = json.toCharArray();
            return deserialize(chars, 0, chars.length);
        }

        @Override
        public Object deserialize(char[] chars, int offset, int len) throws Exception {
            int pos = offset;
            T instance = constructor.newInstance();

            while (pos < len && chars[pos] <= ' ') pos++;
            if (pos >= len || chars[pos] != '{') return instance;
            pos++;

            while (pos < len) {
                while (pos < len && chars[pos] <= ' ') pos++;
                if (pos >= len) break;
                char c = chars[pos];
                if (c == '}') break;
                if (c == ',') { pos++; continue; }
                if (c != '"') { pos++; continue; }

                int nameStart = pos + 1;
                int nameEnd = nameStart;
                while (nameEnd < len && chars[nameEnd] != '"') nameEnd++;
                pos = nameEnd + 1;

                while (pos < len && chars[pos] <= ' ') pos++;
                if (pos < len && chars[pos] == ':') pos++;

                int fieldIdx = -1;
                int nameLen = nameEnd - nameStart;
                for (int i = 0; i < fieldNames.length; i++) {
                    if (nameLen == fieldNames[i].length() && BytesUtil.equals(chars, nameStart, fieldNameCharsArray[i], 0, nameLen)) {
                        fieldIdx = i;
                        break;
                    }
                }

                if (fieldIdx >= 0) {
                    FieldInfo f = fields[fieldIdx];
                    Object value = parseValue(chars, pos, f.type, len);
                    if (value != null) f.setValue(instance, value);
                } else {
                    pos = skipValue(chars, pos, len);
                }
                pos = skipToNext(chars, pos, len);

                if (pos >= len) break;
            }

            return instance;
        }
    }

    private static class UltraFastDeserializer<T> implements BeanDeserializer {
        private final Constructor<T> constructor;
        private final FieldInfo[] fields;
        private final int[] fieldNameHashes;
        private final int[] fieldTypeCodes;
        private final char[][] fieldNameCharsArray;

        UltraFastDeserializer(Class<T> clazz, Constructor<T> constructor, FieldInfo[] fields) {
            this.constructor = constructor;
            this.fields = fields;
            this.fieldNameHashes = new int[fields.length];
            this.fieldTypeCodes = new int[fields.length];
            this.fieldNameCharsArray = new char[fields.length][];
            for (int i = 0; i < fields.length; i++) {
                fieldNameHashes[i] = fields[i].nameHashCode;
                fieldTypeCodes[i] = fields[i].typeCode;
                fieldNameCharsArray[i] = fields[i].name.toCharArray();
            }
        }

        @Override
        public Object deserialize(String json) throws Exception {
            char[] chars = json.toCharArray();
            return deserialize(chars, 0, chars.length);
        }

        @Override
        public Object deserialize(char[] chars, int offset, int len) throws Exception {
            int pos = offset;
            T instance = constructor.newInstance();

            while (pos < len && chars[pos] <= ' ') pos++;
            if (pos >= len || chars[pos] != '{') return instance;
            pos++;

            while (pos < len) {
                while (pos < len && chars[pos] <= ' ') pos++;
                if (pos >= len) break;
                char c = chars[pos];
                if (c == '}') break;
                if (c == ',') { pos++; continue; }
                if (c != '"') { pos++; continue; }

                int nameStart = pos + 1;
                int nameEnd = nameStart;
                while (nameEnd < len && chars[nameEnd] != '"') nameEnd++;
                pos = nameEnd + 1;

                while (pos < len && chars[pos] <= ' ') pos++;
                if (pos < len && chars[pos] == ':') pos++;

                int nameLen = nameEnd - nameStart;
                int fieldHash = computeHashCode(chars, nameStart, nameLen);
                
                int fieldIdx = findFieldIndexByHash(fieldHash, nameLen, nameStart, chars);
                if (fieldIdx < 0) {
                    pos = skipValue(chars, pos, len);
                    continue;
                }

                FieldInfo field = fields[fieldIdx];
                int typeCode = fieldTypeCodes[fieldIdx];

                switch (typeCode) {
                    case 1:
                        field.setIntValue(instance, parseIntDirect(chars, pos, len));
                        break;
                    case 2:
                        field.setLongValue(instance, parseLongDirect(chars, pos, len));
                        break;
                    case 3:
                        field.setDoubleValue(instance, parseDoubleDirect(chars, pos, len));
                        break;
                    case 4:
                        field.setDoubleValue(instance, (float) parseDoubleDirect(chars, pos, len));
                        break;
                    case 5:
                        field.setBooleanValue(instance, parseBooleanFast(chars, pos, len));
                        break;
                    case 6:
                        String strVal = parseStringFast(chars, pos, len);
                        field.setValue(instance, internString(strVal));
                        break;
                    default:
                        Object value = parseValue(chars, pos, field.type, len);
                        field.setValue(instance, value);
                }

                pos = skipToNext(chars, pos, len);
                if (pos >= len) break;
            }

            return instance;
        }

        private int findFieldIndexByHash(int fieldHash, int nameLen, int nameStart, char[] chars) {
            for (int i = 0; i < fieldNameHashes.length; i++) {
                if (fieldNameHashes[i] == fieldHash && fieldNameCharsArray[i].length == nameLen) {
                    if (BytesUtil.equals(chars, nameStart, fieldNameCharsArray[i], 0, nameLen)) {
                        return i;
                    }
                }
            }
            return -1;
        }

        private int computeHashCode(char[] chars, int start, int len) {
            int hash = 0;
            for (int i = 0; i < len; i++) {
                hash = 31 * hash + chars[start + i];
            }
            return hash;
        }

        private boolean parseBooleanFast(char[] chars, int pos, int len) {
            while (pos < len && chars[pos] <= ' ') pos++;
            if (pos + 4 <= len) {
                if (chars[pos] == 't' && chars[pos+1] == 'r' && chars[pos+2] == 'u' && chars[pos+3] == 'e') {
                    return true;
                }
            }
            return false;
        }

        private String parseStringFast(char[] chars, int pos, int len) {
            while (pos < len && chars[pos] <= ' ') pos++;
            if (pos >= len || chars[pos] != '"') return null;
            int start = pos + 1;
            int end = start;
            while (end < len) {
                if (chars[end] == '\\') {
                    return parseString(chars, pos, len);
                }
                if (chars[end] == '"') break;
                end++;
            }
            String result = new String(chars, start, end - start);
            return internString(result);
        }

        private int parseIntDirect(char[] chars, int pos, int len) {
            while (pos < len && chars[pos] <= ' ') pos++;
            int sign = 1;
            if (chars[pos] == '-') { sign = -1; pos++; }
            int value = 0;
            while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
                value = value * 10 + (chars[pos] - '0');
                pos++;
            }
            return value * sign;
        }

        private long parseLongDirect(char[] chars, int pos, int len) {
            while (pos < len && chars[pos] <= ' ') pos++;
            int sign = 1;
            if (chars[pos] == '-') { sign = -1; pos++; }
            long value = 0;
            while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
                value = value * 10 + (chars[pos] - '0');
                pos++;
            }
            return value * sign;
        }

        private double parseDoubleDirect(char[] chars, int pos, int len) {
            while (pos < len && chars[pos] <= ' ') pos++;
            int start = pos;
            while (pos < len && chars[pos] != ',' && chars[pos] != '}' && chars[pos] != ']') pos++;
            String numStr = new String(chars, start, pos - start);
            return Double.parseDouble(numStr);
        }
    }

    private static class FastDeserializer<T> implements BeanDeserializer {
        private final Constructor<T> constructor;
        private final HashMap<String, FieldInfo> fieldMap;
        private final String[] fieldNames;
        private final char[][] fieldNameCharsArray;
        private final FieldInfo[] fieldArray;
        private final int[] fieldNameHashes;

        FastDeserializer(Class<T> clazz, Constructor<T> constructor, FieldInfo[] fields) {
            this.constructor = constructor;
            this.fieldMap = new HashMap<>(fields.length * 2);
            this.fieldArray = fields;
            this.fieldNames = new String[fields.length];
            this.fieldNameCharsArray = new char[fields.length][];
            this.fieldNameHashes = new int[fields.length];
for (int i = 0; i < fields.length; i++) {
fieldMap.put(fields[i].name, fields[i]);
for (String alias : fields[i].aliases) {
    fieldMap.putIfAbsent(alias, fields[i]);
}
fieldNames[i] = fields[i].name;
fieldNameCharsArray[i] = fields[i].name.toCharArray();
fieldNameHashes[i] = fields[i].nameHashCode;
}
}

@Override
public Object deserialize(String json) throws Exception {
char[] chars = json.toCharArray();
return deserialize(chars, 0, chars.length);
        }

        @Override
        public Object deserialize(char[] chars, int offset, int len) throws Exception {
            int pos = offset;
            T instance = constructor.newInstance();

            while (pos < len && chars[pos] <= ' ') pos++;
            if (pos >= len || chars[pos] != '{') return instance;
            pos++;

            while (pos < len) {
                while (pos < len && chars[pos] <= ' ') pos++;
                if (pos >= len) break;
                char c = chars[pos];
                if (c == '}') break;
                if (c == ',') { pos++; continue; }
                if (c != '"') { pos++; continue; }

                int nameStart = pos + 1;
                int nameEnd = nameStart;
                while (nameEnd < len && chars[nameEnd] != '"') nameEnd++;
                pos = nameEnd + 1;

                while (pos < len && chars[pos] <= ' ') pos++;
                if (pos < len && chars[pos] == ':') pos++;

                int nameLen = nameEnd - nameStart;
                int fieldHash = computeHashCode(chars, nameStart, nameLen);
                FieldInfo field = findFieldByHash(fieldHash, nameLen, nameStart, chars);

                if (field == null) {
                    pos = skipValue(chars, pos, len);
                    continue;
                }

                Object value = parseValueWithFieldInfo(chars, pos, field, len);
                if (value != null) field.setValue(instance, value);
                pos = skipToNext(chars, pos, len);

                if (pos >= len) break;
            }

            return instance;
        }

        /**
         * 使用字段完整信息解析值（支持泛型类型。
         */
        private static Object parseValueWithFieldInfo(char[] chars, int start, FieldInfo field, int len) {
            if (start >= len) return null;

            int pos = start;
            while (pos < len && chars[pos] <= ' ') pos++;
            if (pos >= len) return null;

            char c = chars[pos];

            if (c == 'n' && pos + 4 <= len && chars[pos+1] == 'u' && chars[pos+2] == 'l' && chars[pos+3] == 'l') {
                return null;
            }
            if (c == 't' && pos + 4 <= len && chars[pos+1] == 'r' && chars[pos+2] == 'u' && chars[pos+3] == 'e') {
                return true;
            }
            if (c == 'f' && pos + 5 <= len && chars[pos+1] == 'a' && chars[pos+2] == 'l' && chars[pos+3] == 's' && chars[pos+4] == 'e') {
                return false;
            }

            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber(chars, pos, field.type, len);
            }

            if (c == '"') {
                return parseStringWithFieldType(chars, pos, field, len);
            }

            if (c == '[') {
                return parseArray(chars, pos, len, field.elementType);
            }

            if (c == '{') {
                if (field.type != Object.class && field.type != Map.class && field.type != List.class && field.type != ArrayList.class
                    && !field.type.isInterface() && !field.type.isPrimitive() && !field.type.isArray()) {
                    try {
                        BeanDeserializer deserializer = ZeroCopyDeserializer.getDeserializer(field.type);
                        return deserializer.deserialize(chars, pos, len - pos);
                    } catch (Exception e) {
                        return parseObject(chars, pos, len);
                    }
                } else {
                    return parseObject(chars, pos, len);
                }
            }

            return null;
        }

        private int computeHashCode(char[] chars, int start, int len) {
            int hash = 0;
            for (int i = 0; i < len; i++) {
                hash = 31 * hash + chars[start + i];
            }
            return hash;
        }

        private FieldInfo findFieldByHash(int fieldHash, int nameLen, int nameStart, char[] chars) {
            for (int i = 0; i < fieldNameHashes.length; i++) {
                if (fieldNameHashes[i] == fieldHash && fieldNameCharsArray[i].length == nameLen) {
                    if (BytesUtil.equals(chars, nameStart, fieldNameCharsArray[i], 0, nameLen)) {
                        return fieldArray[i];
                    }
                }
            }
            return null;
        }
    }

    private static class StandardDeserializer<T> implements BeanDeserializer {
        private final Constructor<T> constructor;
        private final HashMap<String, FieldInfo> fieldMap;
        private final String[] fieldNames;
        private final char[][] fieldNameCharsArray;
        private final FieldInfo[] fieldArray;
        private final int[] fieldNameHashes;

        StandardDeserializer(Class<T> clazz, Constructor<T> constructor, FieldInfo[] fields) {
            this.constructor = constructor;
            this.fieldMap = new HashMap<>(fields.length * 2);
            this.fieldArray = fields;
            this.fieldNames = new String[fields.length];
            this.fieldNameCharsArray = new char[fields.length][];
            this.fieldNameHashes = new int[fields.length];
for (int i = 0; i < fields.length; i++) {
String name = fields[i].name;
fieldMap.put(name, fields[i]);
for (String alias : fields[i].aliases) {
    fieldMap.putIfAbsent(alias, fields[i]);
}
fieldNames[i] = name;
fieldNameCharsArray[i] = name.toCharArray();
fieldNameHashes[i] = fields[i].nameHashCode;
}
}

        @Override
        public Object deserialize(String json) throws Exception {
            char[] chars = json.toCharArray();
            return deserialize(chars, 0, chars.length);
        }

        @Override
        public Object deserialize(char[] chars, int offset, int len) throws Exception {
            int pos = offset;
            T instance = constructor.newInstance();

            while (pos < len && chars[pos] <= ' ') pos++;
            if (pos >= len || chars[pos] != '{') return instance;
            pos++;

            while (pos < len) {
                while (pos < len && chars[pos] <= ' ') pos++;
                if (pos >= len) break;
                char c = chars[pos];
                if (c == '}') break;
                if (c == ',') { pos++; continue; }
                if (c != '"') { pos++; continue; }

                int nameStart = pos + 1;
                int nameEnd = nameStart;
                while (nameEnd < len && chars[nameEnd] != '"') nameEnd++;
                pos = nameEnd + 1;

                while (pos < len && chars[pos] <= ' ') pos++;
                if (pos < len && chars[pos] == ':') pos++;

                int nameLen = nameEnd - nameStart;
                int fieldHash = computeHashCodeStandard(chars, nameStart, nameLen);
                FieldInfo field = findFieldByHashStandard(fieldHash, nameLen, nameStart, chars);

                if (field == null) {
                    field = fieldMap.get(new String(chars, nameStart, nameLen));
                }

                if (field == null) {
                    pos = skipValue(chars, pos, len);
                    continue;
                }

                Object value = parseValue(chars, pos, field.type, len);
                if (value != null) field.setValue(instance, value);
                pos = skipToNext(chars, pos, len);

                if (pos >= len) break;
            }

            return instance;
        }

        private int computeHashCodeStandard(char[] chars, int start, int len) {
            int hash = 0;
            for (int i = 0; i < len; i++) {
                hash = 31 * hash + chars[start + i];
            }
            return hash;
        }

        private FieldInfo findFieldByHashStandard(int fieldHash, int nameLen, int nameStart, char[] chars) {
            for (int i = 0; i < fieldNameHashes.length; i++) {
                if (fieldNameHashes[i] == fieldHash && fieldNameCharsArray[i].length == nameLen) {
                    if (BytesUtil.equals(chars, nameStart, fieldNameCharsArray[i], 0, nameLen)) {
                        return fieldArray[i];
                    }
                }
            }
            return null;
        }
    }

    private static Object parseValue(char[] chars, int start, Class<?> type, int len) {
        if (start >= len) return null;

        int pos = start;
        while (pos < len && chars[pos] <= ' ') pos++;
        if (pos >= len) return null;

        char c = chars[pos];

        if (c == 'n' && pos + 4 <= len && chars[pos+1] == 'u' && chars[pos+2] == 'l' && chars[pos+3] == 'l') {
            return null;
        }
        if (c == 't' && pos + 4 <= len && chars[pos+1] == 'r' && chars[pos+2] == 'u' && chars[pos+3] == 'e') {
            return true;
        }
        if (c == 'f' && pos + 5 <= len && chars[pos+1] == 'a' && chars[pos+2] == 'l' && chars[pos+3] == 's' && chars[pos+4] == 'e') {
            return false;
        }

        if (c == '-' || (c >= '0' && c <= '9')) {
            return parseNumber(chars, pos, type, len);
        }

        if (c == '"') {
            return parseString(chars, pos, len);
        }

        if (c == '[') {
            return parseArray(chars, pos, len);
        }

        if (c == '{') {
            if (type != Object.class && type != Map.class && type != List.class && type != ArrayList.class
                && !type.isInterface() && !type.isPrimitive() && !type.isArray()) {
                try {
                    BeanDeserializer deserializer = ZeroCopyDeserializer.getDeserializer(type);
                    return deserializer.deserialize(chars, pos, len - pos);
                } catch (Exception e) {
                    return parseObject(chars, pos, len);
                }
            } else {
                return parseObject(chars, pos, len);
            }
        }

        return null;
    }

    private static Number parseNumber(char[] chars, int start, Class<?> type, int len) {
        int end = start;
        boolean hasDot = false;
        boolean hasExp = false;

        if (end < len && chars[end] == '-') end++;

        while (end < len) {
            char c = chars[end];
            if (c >= '0' && c <= '9') {
                end++;
            } else if (c == '.' && !hasDot) {
                hasDot = true;
                end++;
            } else if ((c == 'e' || c == 'E') && !hasExp) {
                hasExp = true;
                end++;
                if (end < len && (chars[end] == '+' || chars[end] == '-')) {
                    end++;
                }
            } else {
                break;
            }
        }

        if (type == int.class || type == Integer.class) {
            return parseIntDirect(chars, start, end);
        } else if (type == long.class || type == Long.class) {
            return parseLongDirect(chars, start, end);
        } else if (type == double.class || type == Double.class) {
            return hasDot || hasExp ? parseDoubleDirect(chars, start, end) : (long) parseDoubleDirect(chars, start, end);
        } else if (type == float.class || type == Float.class) {
            return (float) parseDoubleDirect(chars, start, end);
        }

        return hasDot || hasExp ? parseDoubleDirect(chars, start, end) : (long) parseDoubleDirect(chars, start, end);
    }

    /**
     * 根据字段类型解析字符串值（支持 LocalDateTime 等特殊类型）
     */
    private static Object parseStringWithFieldType(char[] chars, int start, FieldInfo field, int len) {
        String str = parseString(chars, start, len);
        Class<?> type = field.type;

        if (type == LocalDateTime.class) {
            return LocalDateTime.parse(str);
        } else if (type == LocalDate.class) {
            return LocalDate.parse(str);
        } else if (type == LocalTime.class) {
            return LocalTime.parse(str);
        } else if (type == Instant.class) {
            return Instant.parse(str);
        } else if (type == ZonedDateTime.class) {
            return ZonedDateTime.parse(str);
        } else if (type == Date.class) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(str);
            } catch (ParseException e) {
                return str;
            }
        } else if (type == BigDecimal.class) {
            return new BigDecimal(str);
        } else if (type == BigInteger.class) {
            return new BigInteger(str);
        }

        return str;
    }

    private static int parseIntDirect(char[] chars, int start, int end) {
        int result = 0;
        int sign = 1;
        int i = start;

        if (chars[i] == '-') {
            sign = -1;
            i++;
        }

        while (i < end && chars[i] >= '0' && chars[i] <= '9') {
            result = result * 10 + (chars[i] - '0');
            i++;
        }

        while (i < end && chars[i] != '.' && chars[i] != 'e' && chars[i] != 'E') {
            i++;
        }

        return result * sign;
    }

    private static long parseLongDirect(char[] chars, int start, int end) {
        long result = 0;
        long sign = 1;
        int i = start;

        if (chars[i] == '-') {
            sign = -1;
            i++;
        }

        while (i < end && chars[i] >= '0' && chars[i] <= '9') {
            result = result * 10 + (chars[i] - '0');
            i++;
        }

        while (i < end && chars[i] != '.' && chars[i] != 'e' && chars[i] != 'E') {
            i++;
        }

        return result * sign;
    }

    private static double parseDoubleDirect(char[] chars, int start, int end) {
        int i = start;
        int sign = 1;

        if (chars[i] == '-') {
            sign = -1;
            i++;
        }

        long intPart = 0;
        while (i < end && chars[i] >= '0' && chars[i] <= '9') {
            intPart = intPart * 10 + (chars[i] - '0');
            i++;
        }

        double result = intPart;

        if (i < end && chars[i] == '.') {
            i++;
            long decPart = 0;
            int decDigits = 0;
            while (i < end && chars[i] >= '0' && chars[i] <= '9') {
                decPart = decPart * 10 + (chars[i] - '0');
                decDigits++;
                i++;
            }
            result = intPart + decPart / Math.pow(10, decDigits);
        }

        if (i < end && (chars[i] == 'e' || chars[i] == 'E')) {
            i++;
            int expSign = 1;
            if (i < end && chars[i] == '-') {
                expSign = -1;
                i++;
            } else if (i < end && chars[i] == '+') {
                i++;
            }
            int exp = 0;
            while (i < end && chars[i] >= '0' && chars[i] <= '9') {
                exp = exp * 10 + (chars[i] - '0');
                i++;
            }
            result *= Math.pow(10, expSign * exp);
        }

        return result * sign;
    }

    private static String parseString(char[] chars, int start, int len) {
        if (start >= len || chars[start] != '"') {
            return new String(chars, start, len - start);
        }

        // 快速路径：先扫描是否有转义字符，无转义则直接 new String
        // 对标 Jackson 的 fast string parse 优化
        int scanEnd = start + 1;
        while (scanEnd < len) {
            char c = chars[scanEnd];
            if (c == '\\') break;          // 有转义，走慢速路径
            if (c == '"') {
                // 无转义字符的快速路径：直接 substring
                return new String(chars, start + 1, scanEnd - start - 1);
            }
            scanEnd++;
        }

        // 慢速路径：有转义字符，使用 StringBuilder 逐字符处理
        int end = start + 1;
        StringBuilder sb = new StringBuilder();

        while (end < len) {
            char c = chars[end];
            if (c == '\\' && end + 1 < len) {
                end++;
                switch (chars[end]) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (end + 4 < len) {
                            String hex = new String(chars, end + 1, 4);
                            char ch = (char) Integer.parseInt(hex, 16);
                            if (Character.isHighSurrogate(ch) && end + 10 < len && chars[end + 5] == '\\' && chars[end + 6] == 'u') {
                                String hex2 = new String(chars, end + 7, 4);
                                char low = (char) Integer.parseInt(hex2, 16);
                                if (Character.isLowSurrogate(low)) {
                                    sb.append(Character.toChars(Character.toCodePoint(ch, low)));
                                    end += 6;
                                    break;
                                }
                            }
                            sb.append(ch);
                            end += 4;
                        }
                        break;
                    default: sb.append(chars[end]); break;
                }
            } else if (c == '"') {
                end++;
                break;
            } else {
                sb.append(c);
            }
            end++;
        }

        return sb.toString();
    }

    /**
     * 解析 JSON 数组
     */
    public static List<Object> parseArray(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        char[] chars = json.toCharArray();
        return parseArray(chars, 0, chars.length);
    }

    /**
     * 解析 JSON 数组（带元素类型。
     */
    public static List<Object> parseArray(String json, Class<?> elementClass) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        char[] chars = json.toCharArray();
        return parseArrayChars(chars, 0, chars.length, elementClass);
    }

    /**
     * 解析 JSON 数组（内部使用）
     */
    private static List<Object> parseArray(char[] chars, int start, int len) {
        return parseArray(chars, start, len, Object.class);
    }

    /**
     * 解析 JSON 数组（带元素类型，内部使用）
     */
    private static List<Object> parseArray(char[] chars, int start, int len, Class<?> elementClass) {
        ArrayList<Object> list = borrowArrayList();
        try {
            parseArrayInner(list, chars, start, len, elementClass);
            return new ArrayList<>(list);
        } catch (Exception e) {
            returnArrayList(list);
            throw e;
        }
    }

    /**
     * 公共解析 JSON 数组（带元素类型。
     */
    public static List<Object> parseArrayChars(char[] chars, int start, int len, Class<?> elementClass) {
        ArrayList<Object> list = borrowArrayList();
        try {
            parseArrayInner(list, chars, start, len, elementClass);
            return new ArrayList<>(list);
        } catch (Exception e) {
            returnArrayList(list);
            throw e;
        }
    }

    private static List<Object> parseArrayInner(ArrayList<Object> list, char[] chars, int start, int len, Class<?> elementClass) {

        if (start >= len || chars[start] != '[') return list;
        int pos = start + 1;

        while (pos < len) {
            while (pos < len && chars[pos] <= ' ') pos++;
            if (pos >= len) break;

            char c = chars[pos];
            if (c == ']') break;
            if (c == ',') { pos++; continue; }

            Object value = parseValue(chars, pos, elementClass, len);
            list.add(value);
            pos = skipToNext(chars, pos, len);

            if (pos >= len) break;
        }

        return list;
    }

    private static Map<String, Object> parseObject(char[] chars, int start, int len) {
        LinkedHashMap<String, Object> map = borrowLinkedHashMap();

        if (start >= len || chars[start] != '{') {
            returnLinkedHashMap(map);
            return new LinkedHashMap<>();
        }
        int pos = start + 1;

        try {
            while (pos < len) {
                while (pos < len && chars[pos] <= ' ') pos++;
                if (pos >= len) break;

                char c = chars[pos];
                if (c == '}') break;
                if (c == ',') { pos++; continue; }

                if (c == '"') {
                    int nameStart = pos + 1;
                    int nameEnd = nameStart;
                    while (nameEnd < len && chars[nameEnd] != '"') nameEnd++;
                    String key = new String(chars, nameStart, nameEnd - nameStart);
                    pos = nameEnd + 1;

                    while (pos < len && chars[pos] <= ' ') pos++;
                    if (pos < len && chars[pos] == ':') pos++;

                    Object value = parseValue(chars, pos, Object.class, len);
                    map.put(key, value);
                    pos = skipToNext(chars, pos, len);
                } else {
                    pos++;
                }

                if (pos >= len) break;
            }
        } catch (Exception e) {
            returnLinkedHashMap(map);
            throw e;
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>(map);
        returnLinkedHashMap(map);
        return result;
    }

    private static int skipToNext(char[] chars, int pos, int len) {
        int depth = 0;
        boolean inString = false;

        while (pos < len) {
            char c = chars[pos];

            if (c == '\\' && inString) {
                pos += 2;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                pos++;
                continue;
            }

            if (!inString) {
                if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    if (depth == 0) return pos;
                    depth--;
                } else if (c == ',' && depth == 0) {
                    return pos + 1;
                }
            }

            pos++;
        }

        return pos;
    }

    private static int skipValue(char[] chars, int pos, int len) {
        while (pos < len && chars[pos] <= ' ') pos++;
        if (pos >= len) return pos;

        char c = chars[pos];

        if (c == '"') {
            int end = pos + 1;
            while (end < len) {
                if (chars[end] == '\\') { end += 2; continue; }
                if (chars[end] == '"') { end++; break; }
                end++;
            }
            return end;
        } else if (c == '{' || c == '[') {
            return skipToNext(chars, pos, len);
        } else if (c == 't' && pos + 4 <= len && chars[pos+1] == 'r' && chars[pos+2] == 'u' && chars[pos+3] == 'e') {
            return pos + 4;
        } else if (c == 'f' && pos + 5 <= len && chars[pos+1] == 'a' && chars[pos+2] == 'l' && chars[pos+3] == 's' && chars[pos+4] == 'e') {
            return pos + 5;
        } else if (c == 'n' && pos + 4 <= len && chars[pos+1] == 'u' && chars[pos+2] == 'l' && chars[pos+3] == 'l') {
            return pos + 4;
        } else if (c == '-' || (c >= '0' && c <= '9')) {
            while (pos < len) {
                char ch = chars[pos];
                if ((ch >= '0' && ch <= '9') || ch == '.' || ch == 'e' || ch == 'E' || ch == '+' || ch == '-') {
                    pos++;
                } else {
                    break;
                }
            }
            return pos;
        }

        return pos + 1;
    }
}
