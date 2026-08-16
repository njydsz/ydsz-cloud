package com.njydsz.common.json.reader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.njydsz.common.json.annotation.JsonAlias;
import com.njydsz.common.json.annotation.JsonCreator;
import com.njydsz.common.json.annotation.JsonIgnore;
import com.njydsz.common.json.annotation.JsonIgnoreProperties;
import com.njydsz.common.json.annotation.JsonProperty;
import com.njydsz.common.json.exception.JsonDeserializationException;
import com.njydsz.common.json.provider.FieldMetadataLoader;
import com.njydsz.common.json.provider.JacksonAnnotationBridge;
import com.njydsz.common.json.util.BoundedLruCache;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(BeanReader.class);

    /** Bean 类型 */
    public final Class<T> beanType;

    /** 字段读取器数组*/
    public final FieldReader[] fieldReaders;

    /** 字段名哈希缓存*/
    public final int[] fieldNameHashes;

    /** 默认构造函数（@JsonCreator 模式下为 null）*/
    public Constructor<T> defaultConstructor;

    /** @JsonAnySetter 方法（null 表示无）*/
    public final Method anySetterMethod;

    /** @JsonCreator 标注的构造函数（null 表示使用默认构造函数） */
    private final Constructor<?> creatorConstructor;

    /** @JsonCreator 构造函数参数名映射（对应 JSON 字段名，null 表示未解析） */
    private final String[] creatorParameterNames;

    /**
     * 构造函数
     */
    public BeanReader(Class<T> beanType) {
        this.beanType = beanType;

        // 检测 @JsonAnySetter 方法
        this.anySetterMethod = FieldMetadataLoader.findAnySetterMethod(beanType);

        // 解析构造函数：优先默认无参构造；否则查找 @JsonCreator 标注的构造
        Constructor<?> creator = null;
        String[] paramNames = null;
        try {
            this.defaultConstructor = beanType.getDeclaredConstructor();
            this.defaultConstructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            // 无默认构造：尝试 @JsonCreator
            Constructor<?>[] ctors = beanType.getDeclaredConstructors();
            for (Constructor<?> ctor : ctors) {
                if (ctor.getAnnotation(JsonCreator.class) != null) {
                    creator = ctor;
                    creator.setAccessible(true);
                    // 解析参数名：优先 @JsonCreator(parameterNames=...)，否则参数上的 @JsonProperty
                    JsonCreator ann = ctor.getAnnotation(JsonCreator.class);
                    if (ann.parameterNames().length == ctor.getParameterCount()) {
                        paramNames = ann.parameterNames();
                    } else {
                        java.lang.reflect.Parameter[] params = ctor.getParameters();
                        paramNames = new String[params.length];
                        for (int i = 0; i < params.length; i++) {
                            JsonProperty jp = params[i].getAnnotation(JsonProperty.class);
                            paramNames[i] = (jp != null && !jp.value().isEmpty()) ? jp.value() : params[i].getName();
                        }
                    }
                    break;
                }
            }
            if (creator == null) {
                // 既无默认构造也无 @JsonCreator：抛出明确异常
                throw new RuntimeException("No default constructor or @JsonCreator for " + beanType.getName(), e);
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("No default constructor for " + beanType.getName(), e);
        }
        this.creatorConstructor = creator;
        this.creatorParameterNames = paramNames;

        // 预计算字段读取器：遍历自身及所有父类字段（修复继承字段静默丢失，P0-②）
        List<Field> fields = FieldMetadataLoader.collectDeclaredAndInheritedFields(beanType);
        int count = 0;
        for (Field field : fields) {
            int mods = field.getModifiers();
            if (!Modifier.isStatic(mods) && !Modifier.isTransient(mods) && !isIgnoredField(field)) {
                count++;
            }
        }

        this.fieldReaders = new FieldReader[count];
        this.fieldNameHashes = new int[count];
        int idx = 0;
        for (Field field : fields) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || isIgnoredField(field)) {
                continue;
            }
            try {
                field.setAccessible(true);
                FieldReader fr = new FieldReader(field);
                this.fieldReaders[idx] = fr;
                this.fieldNameHashes[idx] = fr.jsonNameHash;
                idx++;
            } catch (Exception e) {
                // 反射操作失败：记录告警而非静默丢弃，避免字段悄悄丢失（P1-⑦）
                LOGGER.warn("BeanReader 跳过字段 {}.{}: {}", beanType.getName(), field.getName(), e.toString());
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
        return readObject(reader, 0);
    }

    private T readObject(JSONReader reader, int depth) {
        if (depth > JSONReader.DEFAULT_MAX_DEPTH) {
            throw new JsonDeserializationException(
                "JSON nesting depth exceeds limit: " + depth, reader.pos);
        }

        T obj;
        // @JsonCreator 路径：先解析全部字段到临时 Map，读完再调用构造函数
        if (creatorConstructor != null) {
            Map<String, Object> pending = new HashMap<>();
            Object parsed = readObjectFields(reader, depth, pending);
            if (parsed != null) {
                // 已通过默认构造 + 字段赋值完成（参数名未解析成功时降级）
                return (T) parsed;
            }
            try {
                Object[] args = new Object[creatorParameterNames.length];
                for (int i = 0; i < creatorParameterNames.length; i++) {
                    args[i] = pending.get(creatorParameterNames[i]);
                }
                return (T) creatorConstructor.newInstance(args);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create via @JsonCreator for " + beanType.getName(), e);
            }
        }
        try {
            obj = defaultConstructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create " + beanType.getName(), e);
        }

        readObjectFieldsInto(reader, depth, obj, null);
        return obj;
    }

    /**
     * 通用字段扫描循环：将 JSON 字段写入目标对象，或（creator 模式）收集到 pending Map。
     *
     * @return creator 模式下：若参数名解析失败并降级为默认构造赋值则返回实例，否则 null
     */
    @SuppressWarnings("unchecked")
    private T readObjectFields(JSONReader reader, int depth, Map<String, Object> pending) {
        // 尝试用默认构造 + 字段赋值（若类实际存在默认构造则走此路径）
        try {
            T inst = (T) beanType.getDeclaredConstructor().newInstance();
            readObjectFieldsInto(reader, depth, inst, pending);
            return inst;
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException e) {
            // 无默认构造：仅收集字段到 pending，不创建实例
            readObjectFieldsInto(reader, depth, null, pending);
            return null;
        } catch (Exception e) {
            // 其他异常（如无默认构造）→ 仅收集
            readObjectFieldsInto(reader, depth, null, pending);
            return null;
        }
    }

    /**
     * 逐字段读取 JSON 对象内容。
     *
     * @param target  字段写入目标（null 时仅解析值放入 pending）
     * @param pending 收集的字段值（JSON 字段名 → 解析值），可为 null
     */
    private void readObjectFieldsInto(JSONReader reader, int depth, T target, Map<String, Object> pending) {
        reader.skipTo('{');
        if (reader.pos >= reader.len) {
            throw new RuntimeException("Unexpected end of JSON");
        }
        reader.pos++;

        char[] buf = reader.buf;
        int len = reader.len;

        while (reader.pos < len) {
            char ch = buf[reader.pos];
            while (ch <= ' ') {
                reader.pos++;
                if (reader.pos >= len) return;
                ch = buf[reader.pos];
            }

            if (ch == '}') {
                reader.pos++;
                return;
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
            if (fieldName == null) return;

            reader.skipTo(':');
            if (reader.pos < len) reader.pos++;

            int hash = fieldName.hashCode();
            boolean matched = false;
            for (int i = 0; i < fieldReaders.length; i++) {
                FieldReader fr = fieldReaders[i];
                // 主匹配：jsonName（@JsonProperty 值或字段名）
                if (fieldNameHashes[i] == hash && fr.jsonName.equals(fieldName)) {
                    if (target != null) {
                        fr.readValue(reader, target, depth);
                    } else {
                        pending.put(fr.fieldName, fr.readRawValue(reader));
                    }
                    matched = true;
                    break;
                }
                // 回退匹配：原始 Java 字段名（当 @JsonProperty 设置但 JSON 仍用字段名时）
                if (!matched && fr.fieldName.equals(fieldName)) {
                    if (target != null) {
                        fr.readValue(reader, target, depth);
                    } else {
                        pending.put(fr.fieldName, fr.readRawValue(reader));
                    }
                    matched = true;
                    break;
                }
                // @JsonAlias 别名字段匹配（F-3 恢复）：按 aliasHashes 二次比对，避免 hash 碰撞误命中
                if (!matched && fr.aliasHashes.length > 0) {
                    for (int a = 0; a < fr.aliasHashes.length; a++) {
                        if (fr.aliasHashes[a] == hash && fr.aliasNames[a].equals(fieldName)) {
                            if (target != null) {
                                fr.readValue(reader, target, depth);
                            } else {
                                pending.put(fr.fieldName, fr.readRawValue(reader));
                            }
                            matched = true;
                            break;
                        }
                    }
                }
            }

            if (!matched) {
                if (anySetterMethod != null) {
                    // @JsonAnySetter：将未匹配的属性通过方法写入
                    String rawValue = reader.readRawValue().trim();
                    try {
                        Object parsedValue = parseValue(rawValue);
                        if (target != null) {
                            anySetterMethod.invoke(target, fieldName, parsedValue);
                        } else {
                            pending.put(fieldName, parsedValue);
                        }
                    } catch (Exception e) {
                        // 调用失败时跳过该字段
                    }
                } else {
                    reader.skipValue();
                }
            }
        }
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
     *
     * <p>支持 @JsonProperty 重命名、@JsonAlias 别名匹配，以及 short/byte/char/
     * Date/LocalDateTime/LocalDate/枚举/嵌套 Bean/Collection/Map 的完整反序列化。</p>
     */
    public static final class FieldReader {

        /** Java 字段名（原始） */
        public final String fieldName;
        /** JSON 匹配名（@JsonProperty 值或字段名） */
        public final String jsonName;
        /** JSON 匹配名哈希 */
        public final int jsonNameHash;
        /** @JsonAlias 备用名称（不含主名称；空数组表示无别名） */
        public final String[] aliasNames;
        /** @JsonAlias 备用名称哈希（与 aliasNames 一一对应） */
        public final int[] aliasHashes;
        public final Class<?> fieldType;
        public final Field field;
        public final int typeCode;

        /** @JsonFormat(pattern=...) 指定的日期格式（null 表示使用默认格式列表） */
        public final String datePattern;

        public FieldReader(Field field) {
            this.field = field;
            this.fieldName = field.getName();
            this.fieldType = field.getType();
            this.field.setAccessible(true);
            this.typeCode = getTypeCode(fieldType);

            // 加载 @JsonFormat：日期格式模式（序列化/反序列化共用）
            com.njydsz.common.json.annotation.JsonFormat jsonFormat =
                    field.getAnnotation(com.njydsz.common.json.annotation.JsonFormat.class);
            this.datePattern = (jsonFormat != null && !jsonFormat.pattern().isEmpty())
                    ? jsonFormat.pattern() : null;

            // 加载 @JsonProperty：如果标注了且 value 非空，用 value 作为 JSON 匹配名；
            // P1-8：未标注时回退 Jackson @JsonProperty.value（兼容桥，原生优先）
            JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
            if (jsonProperty != null && !jsonProperty.value().isEmpty()) {
                this.jsonName = jsonProperty.value();
            } else {
                String bridgeName = JacksonAnnotationBridge.propertyName(field);
                this.jsonName = (bridgeName != null) ? bridgeName : this.fieldName;
            }
            this.jsonNameHash = this.jsonName.hashCode();

            // F-3 恢复：加载 @JsonAlias 备用名称（仅反序列化匹配，序列化仍输出主名称）；
            // P1-8：未标注时回退 Jackson @JsonAlias.value
            com.njydsz.common.json.annotation.JsonAlias jsonAlias =
                    field.getAnnotation(com.njydsz.common.json.annotation.JsonAlias.class);
            String[] aliasValues = null;
            if (jsonAlias != null && jsonAlias.value().length > 0) {
                aliasValues = jsonAlias.value();
            } else {
                String[] bridgeAliases = JacksonAnnotationBridge.aliases(field);
                if (bridgeAliases.length > 0) {
                    aliasValues = bridgeAliases;
                }
            }
            if (aliasValues != null) {
                List<String> aliases = new ArrayList<>(aliasValues.length);
                for (String alias : aliasValues) {
                    if (alias != null && !alias.isEmpty() && !alias.equals(this.jsonName)) {
                        aliases.add(alias);
                    }
                }
                this.aliasNames = aliases.toArray(new String[0]);
                this.aliasHashes = new int[this.aliasNames.length];
                for (int i = 0; i < this.aliasNames.length; i++) {
                    this.aliasHashes[i] = this.aliasNames[i].hashCode();
                }
            } else {
                this.aliasNames = new String[0];
                this.aliasHashes = new int[0];
            }

        }

        /**
         * 将 reader 当前位置的值按字段类型写入目标对象。
         *
         * <p>依据 {@code typeCode} 分发：基础类型直接读取并赋值；复杂类型（嵌套对象、
         * List/Collection、Map、Date 等）递归委托或按类型转换。null 值统一处理。</p>
         *
         * @param reader 已定位到值起始的 JSONReader
         * @param obj    目标 Bean 实例，字段值写入其中
         */
        @SuppressWarnings("deprecation")
        public void readValue(JSONReader reader, Object obj) {
            readValue(reader, obj, 0);
        }

        @SuppressWarnings("deprecation")
        public void readValue(JSONReader reader, Object obj, int depth) {
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
                    case 2: // int / Integer
                        if (reader.isNull()) {
                            reader.readNull();
                            if (fieldType == Integer.class) field.set(obj, null);
                        } else {
                            int val = reader.readInt();
                            if (fieldType == int.class) {
                                field.setInt(obj, val);
                            } else {
                                field.set(obj, Integer.valueOf(val));
                            }
                        }
                        break;
                    case 3: // long / Long
                        if (reader.isNull()) {
                            reader.readNull();
                            if (fieldType == Long.class) field.set(obj, null);
                        } else {
                            long val = reader.readLong();
                            if (fieldType == long.class) {
                                field.setLong(obj, val);
                            } else {
                                field.set(obj, Long.valueOf(val));
                            }
                        }
                        break;
                    case 4: // double / Double
                        if (reader.isNull()) {
                            reader.readNull();
                            if (fieldType == Double.class) field.set(obj, null);
                        } else {
                            double val = reader.readDouble();
                            if (fieldType == double.class) {
                                field.setDouble(obj, val);
                            } else {
                                field.set(obj, Double.valueOf(val));
                            }
                        }
                        break;
                    case 5: // float / Float
                        if (reader.isNull()) {
                            reader.readNull();
                            if (fieldType == Float.class) field.set(obj, null);
                        } else {
                            float val = reader.readFloat();
                            if (fieldType == float.class) {
                                field.setFloat(obj, val);
                            } else {
                                field.set(obj, Float.valueOf(val));
                            }
                        }
                        break;
                    case 6: // boolean / Boolean
                        if (reader.isNull()) {
                            reader.readNull();
                            if (fieldType == Boolean.class) field.set(obj, null);
                        } else {
                            boolean val = reader.readBoolean();
                            if (fieldType == boolean.class) {
                                field.setBoolean(obj, val);
                            } else {
                                field.set(obj, Boolean.valueOf(val));
                            }
                        }
                        break;
                    case 7: // short
                        if (reader.isNull()) {
                            reader.readNull();
                            if (fieldType == Short.class) field.set(obj, null);
                        } else {
                            short val = (short) reader.readInt();
                            if (fieldType == short.class) {
                                field.setShort(obj, val);
                            } else {
                                field.set(obj, Short.valueOf(val));
                            }
                        }
                        break;
                    case 8: // byte
                        if (reader.isNull()) {
                            reader.readNull();
                            if (fieldType == Byte.class) field.set(obj, null);
                        } else {
                            byte val = (byte) reader.readInt();
                            if (fieldType == byte.class) {
                                field.setByte(obj, val);
                            } else {
                                field.set(obj, Byte.valueOf(val));
                            }
                        }
                        break;
                    case 9: // char
                        if (reader.isNull()) {
                            reader.readNull();
                            if (fieldType == Character.class) field.set(obj, null);
                        } else {
                            String s = reader.readString();
                            if (s != null && s.length() > 0) {
                                char val = s.charAt(0);
                                if (fieldType == char.class) {
                                    field.setChar(obj, val);
                                } else {
                                    field.set(obj, Character.valueOf(val));
                                }
                            }
                        }
                        break;
                    default:
                        // 复杂类型（嵌套对象、集合、Date 等）
                        if (reader.isNull()) {
                            reader.readNull();
                            field.set(obj, null);
                        } else if (fieldType == List.class || fieldType == ArrayList.class || Collection.class.isAssignableFrom(fieldType)) {
                            List<Object> listValue = reader.readArray(Object.class);
                            field.set(obj, listValue);
                        } else if (fieldType == Map.class || fieldType == HashMap.class || Map.class.isAssignableFrom(fieldType)) {
                            field.set(obj, reader.readObjectMap());
                        } else if (fieldType == LocalDateTime.class) {
                            String s = reader.readString();
                            field.set(obj, parseLocalDateTime(s, datePattern));
                        } else if (fieldType == LocalDate.class) {
                            String s = reader.readString();
                            field.set(obj, parseLocalDate(s, datePattern));
                        } else if (fieldType == Date.class) {
                            String s = reader.readString();
                            field.set(obj, parseDate(s, datePattern));
                        } else if (fieldType.isEnum()) {
                            String s = reader.readString();
                            field.set(obj, parseEnum(fieldType, s));
                        } else {
                            BeanReader<?> nestedReader = getOrCreateForType(fieldType);
                            field.set(obj, nestedReader.readObject(reader, depth + 1));
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
            if (type == short.class || type == Short.class) return 7;
            if (type == byte.class || type == Byte.class) return 8;
            if (type == char.class || type == Character.class) return 9;
            return 10;
        }

        /**
         * 读取当前值并转换为适合 @JsonCreator 参数的类型。
         *
         * <p>不写入目标对象，仅按字段类型解析并返回原始值（字符串/数字/布尔），
         * 供 {@link #readObjectFieldsInto} 的 creator 模式收集参数使用。</p>
         *
         * @param reader 已定位到值起始的 JSONReader
         * @return 解析后的值
         */
        public Object readRawValue(JSONReader reader) {
            try {
                switch (typeCode) {
                    case 1: // String
                        return reader.isNull() ? null : reader.readString();
                    case 2: // int
                        return reader.isNull() ? null : reader.readInt();
                    case 3: // long
                        return reader.isNull() ? null : reader.readLong();
                    case 4: // double
                        return reader.isNull() ? null : reader.readDouble();
                    case 5: // float
                        return reader.isNull() ? null : reader.readFloat();
                    case 6: // boolean
                        return reader.isNull() ? null : reader.readBoolean();
                    case 7: // short
                        return reader.isNull() ? null : (short) reader.readInt();
                    case 8: // byte
                        return reader.isNull() ? null : (byte) reader.readInt();
                    case 9: // char
                        return reader.isNull() ? null : reader.readString();
                    default:
                        // 复杂类型：读取原始字符串，由 CreatorResolver/TypeConverter 转换
                        return reader.isNull() ? null : reader.readRawValue().trim();
                }
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** BeanReader 缓存（有界 LRU，容量 1024，防止动态类加载场景下无界增长） */
    private static final BoundedLruCache<Class<?>, BeanReader<?>> CACHE = new BoundedLruCache<>(1024);


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
     */
    public static void clearCache() {
        CACHE.clear();
    }

    // ==================== 日期/枚举解析辅助方法 ====================

    /**
     * 判断字段是否应在反序列化时被忽略。
     *
     * <p>规则（与序列化侧 {@code FieldMetadataLoader.loadFields} 对齐）：</p>
     * <ol>
     *   <li>字段级 {@code @JsonIgnore} → 忽略</li>
     *   <li>类级 {@code @JsonIgnoreProperties} 中列出的字段名 → 忽略</li>
     *   <li>P1-8：Jackson {@code @JsonIgnore} / {@code @JsonIgnoreProperties} 兜底
     *       （兼容桥，原生注解优先）</li>
     * </ol>
     *
     * @param field 目标字段
     * @return true 表示忽略
     */
    private static boolean isIgnoredField(Field field) {
        if (field.getAnnotation(JsonIgnore.class) != null) {
            return true;
        }
        // P1-8：Jackson @JsonIgnore 兜底
        if (JacksonAnnotationBridge.isIgnored(field)) {
            return true;
        }
        // 类级 @JsonIgnoreProperties：忽略列表中包含字段名（含别名匹配）
        JsonIgnoreProperties ignoreProps = field.getDeclaringClass().getAnnotation(JsonIgnoreProperties.class);
        String[] ignoreNames = (ignoreProps != null && ignoreProps.value().length > 0)
                ? ignoreProps.value() : JacksonAnnotationBridge.ignoreProperties(field.getDeclaringClass());
        if (ignoreNames.length > 0) {
            for (String name : ignoreNames) {
                if (name.equals(field.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final DateTimeFormatter[] DATE_TIME_FORMATS = {
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    };

    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    };

    @SuppressWarnings("deprecation")
    private static LocalDateTime parseLocalDateTime(String s) {
        return parseLocalDateTime(s, null);
    }

    @SuppressWarnings("deprecation")
    private static LocalDateTime parseLocalDateTime(String s, String pattern) {
        if (s == null || s.isEmpty()) return null;
        // @JsonFormat 指定格式优先
        if (pattern != null) {
            try {
                return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        for (DateTimeFormatter fmt : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(s, fmt);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static LocalDate parseLocalDate(String s) {
        return parseLocalDate(s, null);
    }

    @SuppressWarnings("deprecation")
    private static LocalDate parseLocalDate(String s, String pattern) {
        if (s == null || s.isEmpty()) return null;
        if (pattern != null) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static Date parseDate(String s) {
        return parseDate(s, null);
    }

    @SuppressWarnings("deprecation")
    private static Date parseDate(String s, String pattern) {
        if (s == null || s.isEmpty()) return null;
        try {
            return new Date(Long.parseLong(s));
        } catch (NumberFormatException ignored) {
        }
        List<DateTimeFormatter> candidates = new ArrayList<>();
        if (pattern != null) {
            try {
                candidates.add(DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        candidates.add(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        candidates.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        candidates.add(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        for (DateTimeFormatter fmt : candidates) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(s, fmt);
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseEnum(Class<?> enumType, String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Enum.valueOf((Class<Enum>) enumType, s);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
