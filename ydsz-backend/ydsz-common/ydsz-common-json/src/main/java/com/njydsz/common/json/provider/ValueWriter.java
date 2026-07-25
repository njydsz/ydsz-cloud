package com.njydsz.common.json.provider;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.temporal.TemporalAccessor;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.UUID;

import com.njydsz.common.json.annotation.YdszJsonClass;
import com.njydsz.common.json.annotation.YdszJsonView;
import com.njydsz.common.json.cache.AsmCodecCache;
import com.njydsz.common.json.cache.FieldMeta;
import com.njydsz.common.json.cache.SerializerCache;
import com.njydsz.common.json.config.YdszJsonConfig;
import com.njydsz.common.json.writer.JSONWriter;

/**
 * 类型特定的值写入器
 *
 * <p>从 SerializationProvider 中提取的类型特定值写入逻辑。</p>
 *
 * <p><b>优化技术：</b></p>
 * <ul>
 *   <li>类型代码缓存 - 使用 ConcurrentHashMap 缓存类型代码，替代 instanceof 链</li>
 *   <li>小整数缓存 - 0-9999 的整数直接查表，避免 String.valueOf 开销</li>
 *   <li>快速字符串编码 - 两次遍历快速路径，减少转义判断开销</li>
 *   <li>循环引用检测 - 使用 IdentityHashMap 保证引用比较</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class ValueWriter {

    private static final Logger LOGGER = Logger.getLogger(ValueWriter.class.getName());
    /** 小整数缓存（0-9999） */
    static final String[] SMALL_INTS = new String[10000];

    /** 类型代码枚举 */
    static final byte TYPE_CODE_STRING = 1;
    static final byte TYPE_CODE_INTEGER = 2;
    static final byte TYPE_CODE_LONG = 3;
    static final byte TYPE_CODE_DOUBLE = 4;
    static final byte TYPE_CODE_FLOAT = 5;
    static final byte TYPE_CODE_BOOLEAN = 6;
    static final byte TYPE_CODE_CHARACTER = 7;
    static final byte TYPE_CODE_SHORT = 8;
    static final byte TYPE_CODE_BYTE = 9;
    static final byte TYPE_CODE_ARRAY = 10;
    static final byte TYPE_CODE_LIST = 11;
    static final byte TYPE_CODE_MAP = 12;
    static final byte TYPE_CODE_DATE = 13;
    static final byte TYPE_CODE_BIGDECIMAL = 14;
    static final byte TYPE_CODE_BIGINTEGER = 15;
    static final byte TYPE_CODE_BEAN = 16;
    static final byte TYPE_CODE_OPTIONAL = 17;
    static final byte TYPE_CODE_UUID = 18;

    /** 类型代码缓存（Class -> 类型代码） */
    static final ConcurrentHashMap<Class<?>, Byte> TYPE_CODE_CACHE = new ConcurrentHashMap<>(256);

    static {
        for (int i = 0; i < 10000; i++) {
            SMALL_INTS[i] = String.valueOf(i);
        }

        // 预填充常见类型
        TYPE_CODE_CACHE.put(String.class, TYPE_CODE_STRING);
        TYPE_CODE_CACHE.put(Integer.class, TYPE_CODE_INTEGER);
        TYPE_CODE_CACHE.put(Long.class, TYPE_CODE_LONG);
        TYPE_CODE_CACHE.put(Double.class, TYPE_CODE_DOUBLE);
        TYPE_CODE_CACHE.put(Float.class, TYPE_CODE_FLOAT);
        TYPE_CODE_CACHE.put(Boolean.class, TYPE_CODE_BOOLEAN);
        TYPE_CODE_CACHE.put(Character.class, TYPE_CODE_CHARACTER);
        TYPE_CODE_CACHE.put(Short.class, TYPE_CODE_SHORT);
        TYPE_CODE_CACHE.put(Byte.class, TYPE_CODE_BYTE);
        TYPE_CODE_CACHE.put(LocalDateTime.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(LocalDate.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(LocalTime.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(ZonedDateTime.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(OffsetDateTime.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(OffsetTime.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(Instant.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(Year.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(YearMonth.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(MonthDay.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(Date.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(java.sql.Date.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(java.sql.Timestamp.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(BigDecimal.class, TYPE_CODE_BIGDECIMAL);
        TYPE_CODE_CACHE.put(BigInteger.class, TYPE_CODE_BIGINTEGER);
        TYPE_CODE_CACHE.put(UUID.class, TYPE_CODE_UUID);
    }

    private ValueWriter() {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取类型代码（带缓存）
     */
    static byte getTypeCode(Object obj) {
        Class<?> clazz = obj.getClass();
        Byte cached = TYPE_CODE_CACHE.get(clazz);
        if (cached != null) {
            return cached;
        }

        // 未命中缓存，计算类型代码
        byte typeCode;
        if (clazz.isArray()) {
            typeCode = TYPE_CODE_ARRAY;
        } else if (obj instanceof List) {
            typeCode = TYPE_CODE_LIST;
        } else if (obj instanceof Map) {
            typeCode = TYPE_CODE_MAP;
        } else {
            typeCode = TYPE_CODE_BEAN;
        }

        TYPE_CODE_CACHE.put(clazz, typeCode);
        return typeCode;
    }

    /**
     * 写入值（类型代码优化版）
     *
     * <p>使用类型代码替代 instanceof 链，提高分支预测准确率。</p>
     */
    public static void writeValue(Object obj, StringBuilder sb) {
        if (obj == null) {
            sb.append("null");
            return;
        }

        byte typeCode = getTypeCode(obj);
        switch (typeCode) {
            case TYPE_CODE_STRING:
                writeString((String) obj, sb);
                break;
            case TYPE_CODE_INTEGER:
                writeInt((Integer) obj, sb);
                break;
            case TYPE_CODE_LONG:
                writeLong((Long) obj, sb);
                break;
            case TYPE_CODE_DOUBLE:
                writeDouble((Double) obj, sb);
                break;
            case TYPE_CODE_FLOAT:
                writeFloat((Float) obj, sb);
                break;
            case TYPE_CODE_BOOLEAN:
                writeBoolean((Boolean) obj, sb);
                break;
            case TYPE_CODE_CHARACTER:
                writeChar((Character) obj, sb);
                break;
            case TYPE_CODE_SHORT:
            case TYPE_CODE_BYTE:
                sb.append(obj);
                break;
            case TYPE_CODE_ARRAY:
                writeArray(obj, sb);
                break;
            case TYPE_CODE_LIST:
                writeListOptimized((List<?>) obj, sb);
                break;
            case TYPE_CODE_MAP:
                writeMapOptimized((Map<?, ?>) obj, sb);
                break;
            case TYPE_CODE_DATE:
                writeString(formatDateValue(obj), sb);
                break;
            case TYPE_CODE_BIGDECIMAL:
                sb.append(((BigDecimal) obj).toPlainString());
                break;
            case TYPE_CODE_BIGINTEGER:
                sb.append(((BigInteger) obj).toString());
                break;
            case TYPE_CODE_UUID:
                writeString(obj.toString(), sb);
                break;
            default:
                if (obj instanceof Optional<?> optional) {
                    if (optional.isPresent()) {
                        writeValue(optional.get(), sb);
                    } else {
                        sb.append("null");
                    }
                } else {
                    writeBeanWithCycleDetection(obj, sb);
                }
        }
    }

    /**
     * 写入字符串
     */
    public static void writeString(String str, StringBuilder sb) {
        int len = str.length();
        if (len == 0) {
            sb.append("\"\"");
            return;
        }

        int firstSpecial = -1;
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c < ' ' || c == '"' || c == '\\') {
                firstSpecial = i;
                break;
            }
        }

        if (firstSpecial == -1) {
            sb.append('"');
            sb.append(str);
            sb.append('"');
            return;
        }

        sb.append('"');
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        // 快速 Unicode 转义（避免 String.format 开销）
                        sb.append("\\u00");
                        char h = (char)(c >> 4);
                        char l = (char)(c & 0xf);
                        sb.append((char)(h < 10 ? h + '0' : h - 10 + 'a'));
                        sb.append((char)(l < 10 ? l + '0' : l - 10 + 'a'));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /**
     * 写入字符串（FastJSON2 架构优化 - 两次遍历快速路径）
     */
    public static void writeStringInline(String str, StringBuilder sb) {
        int len = str.length();

        // 快速路径：检查是否需要转义
        boolean needsEscape = false;
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c < ' ' || c == '"' || c == '\\') {
                needsEscape = true;
                break;
            }
        }

        if (!needsEscape) {
            // 无转义，直接写入（最优路径）
            sb.ensureCapacity(sb.length() + len + 2);
            sb.append('"');
            sb.append(str);
            sb.append('"');
            return;
        }

        // 慢速路径：需要转义（优化版 - 避免 String.format）
        sb.append('"');
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < ' ') {
                        // 快速 Unicode 转义（避免 String.format）
                        sb.append("\\u00");
                        char h = (char)(c >> 4);
                        char l = (char)(c & 0xf);
                        sb.append((char)(h < 10 ? h + '0' : h - 10 + 'a'));
                        sb.append((char)(l < 10 ? l + '0' : l - 10 + 'a'));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
    }

    /**
     * 写入整数（FastJSON2 快速路径 - 直接写入字符数组）
     */
    public static void writeInt(int value, StringBuilder sb) {
        if (value >= 0 && value < 10000) {
            sb.append(SMALL_INTS[value]);
            return;
        }

        sb.append(value);
    }

    /**
     * 写入长整数（优化版）
     */
    public static void writeLong(long value, StringBuilder sb) {
        if (value >= 0 && value < 10000) {
            sb.append(SMALL_INTS[(int) value]);
            return;
        }

        sb.append(value);
    }

    /**
     * 写入双精度数（FastJSON2 架构优化）
     */
    public static void writeDouble(double value, StringBuilder sb) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            sb.append("null");
        } else {
            // 快速路径：整数值直接输出
            if (value == (long) value && Math.abs(value) < 1e15) {
                sb.append((long) value).append(".0");
            } else {
                sb.append(Double.toString(value));
            }
        }
    }

    /**
     * 直接写入 double（避免方法调用开销）
     */
    public static void writeDoubleDirect(double value, StringBuilder sb) {
        if (value == Double.POSITIVE_INFINITY) {
            sb.append("1.7976931348623157E308");
        } else if (value == Double.NEGATIVE_INFINITY) {
            sb.append("-1.7976931348623157E308");
        } else if (Double.isNaN(value)) {
            sb.append("0");
        } else {
            sb.append(value);
        }
    }

    /**
     * 写入浮点数（FastJSON2 架构优化）
     */
    public static void writeFloat(float value, StringBuilder sb) {
        // 快速路径：整数值直接输出
        if (value == (int) value && Math.abs(value) < 1e7) {
            sb.append((int) value).append(".0");
        } else {
            sb.append(Float.toString(value));
        }
    }

    /**
     * 写入布尔值
     */
    public static void writeBoolean(boolean value, StringBuilder sb) {
        sb.append(value ? "true" : "false");
    }

    /**
     * 写入字符
     */
    public static void writeChar(char value, StringBuilder sb) {
        sb.append('"').append(value).append('"');
    }

    /**
     * 写入数组
     */
    public static void writeArray(Object array, StringBuilder sb) {
        sb.append('[');
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(',');
            writeValueDirect(Array.get(array, i), sb);
        }
        sb.append(']');
    }

    /**
     * 写入 List（FastJSON2 架构优化 - 类型代码替代 instanceof 链）
     */
    public static void writeList(List<?> list, StringBuilder sb) {
        int size = list.size();
        sb.append('[');
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(',');
            Object item = list.get(i);
            if (item == null) {
                sb.append("null");
            } else {
                writeValueByTypeCodeFast(item, sb, getTypeCode(item));
            }
        }
        sb.append(']');
    }

    /**
     * 优化列表序列化（使用 JSONWriter 直接写入，避免创建中间 String）
     */
    public static void writeListOptimized(List<?> list, StringBuilder sb) {
        JSONWriter writer = SerializationProvider.FAST_WRITER_POOL.get();
        writer.reset();
        writer.writeCollection(list);
        sb.append(writer.toString());
    }

    /**
     * 写入 Map（FastJSON2 架构优化 - 类型代码替代 instanceof 链）
     */
    public static void writeMapOptimized(Map<?, ?> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;

            Object key = entry.getKey();
            if (key instanceof String) {
                sb.append('"').append((String) key).append('"');
            } else {
                sb.append('"').append(String.valueOf(key)).append('"');
            }

            sb.append(':');

            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else {
                writeValueByTypeCodeFast(value, sb, getTypeCode(value));
            }
        }
        sb.append('}');
    }

    /**
     * 写入 Bean 对象（ASM 优化版本 - FastJSON2 核心架构）
     */
    public static void writeBean(Object obj, StringBuilder sb) {
        Class<?> clazz = obj.getClass();
        YdszJsonClass classAnnotation = clazz.getAnnotation(YdszJsonClass.class);

        FieldMeta[] fields = SerializerCache.getFieldMeta(clazz);
        if (fields == null) {
            fields = FieldMetadataLoader.loadFields(clazz);
            SerializerCache.putFieldMeta(clazz, fields);
        }

        boolean hasFieldAnnotations = FieldMetadataLoader.hasFieldAnnotations(fields);

        // 如果没有注解，使用优化方式
        if (classAnnotation == null && !hasFieldAnnotations) {
            writeBeanNoAnnotationOptimized(obj, sb, clazz, fields);
            return;
        }

        boolean writeClassName = classAnnotation != null && classAnnotation.writeClassName();
        String dateFormat = classAnnotation != null && !classAnnotation.dateFormat().isEmpty()
                ? classAnnotation.dateFormat() : null;
        boolean classWriteNulls = classAnnotation != null && classAnnotation.writeNulls();
        boolean serializeEnumUsingOrdinal = classAnnotation != null && classAnnotation.serializeEnumUsingOrdinal();

        sb.append('{');
        boolean first = true;

        if (writeClassName) {
            sb.append("\"@class\":\"").append(clazz.getName()).append("\"");
            first = false;
        }

        for (FieldMeta field : fields) {
            if (field.shouldSkip()) {
                continue;
            }

            // 列权限字段排除检查
            if (SerializationProvider.isFieldExcluded(field.jsonName)) {
                continue;
            }

            Class<?> currentView = SerializationProvider.CURRENT_VIEW_CLASS.get();
            if (currentView != null) {
                YdszJsonView viewAnnotation = field.field.getAnnotation(YdszJsonView.class);
                if (viewAnnotation == null) {
                    continue;
                }
                Class<?>[] viewClasses = viewAnnotation.value();
                boolean visible = false;
                for (Class<?> vc : viewClasses) {
                    if (vc == currentView || vc.isAssignableFrom(currentView)) {
                        visible = true;
                        break;
                    }
                }
                if (!visible) {
                    continue;
                }
            }

            try {
                Object value = field.getValue(obj);

                boolean shouldWriteNull = classWriteNulls || field.writeNull;
                if (value == null && !shouldWriteNull) {
                    continue;
                }

                if (!first) sb.append(',');
                first = false;

                String jsonName = field.jsonName;
                sb.append('"').append(jsonName).append('"').append(':');

                if (field.hasCustomSerializer()) {
                    Object serialized = field.invokeCustomSerializer(value);
                    writeValueDirect(serialized, sb);
                } else if (field.isDateType() && value != null) {
                    String formattedDate = dateFormat != null && !dateFormat.isEmpty()
                            ? formatDateWithPattern(value, dateFormat)
                            : field.formatDateValue(value);
                    writeString(formattedDate, sb);
                } else if (value instanceof Number && !field.numberFormat.isEmpty()) {
                    writeFormattedNumber((Number) value, field.numberFormat, sb);
                } else if (field.htmlSafe && value instanceof String) {
                    writeHtmlSafeString((String) value, sb);
                } else if (value instanceof Enum) {
                    if (serializeEnumUsingOrdinal) {
                        sb.append(((Enum<?>) value).ordinal());
                    } else {
                        writeString(((Enum<?>) value).name(), sb);
                    }
                } else {
                    writeValueDirect(value, sb);
                }
            } catch (Exception e) {
                LOGGER.fine("Failed to serialize field " + field.name + " of " + obj.getClass().getName() + ": " + e.getMessage());
            }
        }

        sb.append('}');
    }

    /**
     * Bean 无注解快速路径（FastJSON2 架构极致优化）
     *
     * <p>FastJSON2 核心优化技术：</p>
     * <ul>
     *   <li>预计算有效字段数组 - 避免运行时 shouldSkip 判断</li>
     *   <li>类型代码直接索引 - 消除 switch 分支开销</li>
     *   <li>StringBuilder 预分配 - 基于精确容量计算</li>
     *   <li>方法调用最小化 - 减少间接方法调用</li>
     *   <li>ASM 字节码生成 - 彻底消除反射开销</li>
     * </ul>
     */
    public static void writeBeanNoAnnotationOptimized(Object obj, StringBuilder sb, Class<?> clazz, FieldMeta[] fields) {
        try {
            JSONWriter writer = SerializationProvider.FAST_WRITER_POOL.get();
            writer.reset();
            if (AsmCodecCache.trySerialize(obj, writer)) {
                sb.append(writer.toString());
                return;
            }
        } catch (Exception e) {
            LOGGER.fine("ASM serialization failed in fast path for " + clazz.getName() + ": " + e.getMessage());
        }
        SerializationProvider.BeanSerializerInfo info = SerializationProvider.getOrCreateBeanSerializer(clazz, fields);

        // 精确容量预分配
        sb.ensureCapacity(info.estimatedSize);

        sb.append('{');
        boolean first = true;

        // 直接遍历预计算的有效字段
        for (FieldMeta field : info.validFields) {
            int typeCode = field.serializeTypeCode;

            switch (typeCode) {
                case 1:  // String
                    String strVal;
                    try {
                        strVal = (String) field.getter.invoke(obj);
                    } catch (Throwable e) {
                        strVal = null;
                    }
                    if (strVal != null) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append(field.jsonKey);
                        // 快速路径：内联字符串检查
                        int len = strVal.length();
                        boolean needsEscape = false;
                        for (int i = 0; i < len; i++) {
                            char c = strVal.charAt(i);
                            if (c < ' ' || c == '"' || c == '\\') {
                                needsEscape = true;
                                break;
                            }
                        }
                        if (!needsEscape) {
                            sb.append('"');
                            sb.append(strVal);
                            sb.append('"');
                        } else {
                            writeStringInline(strVal, sb);
                        }
                    }
                    break;
                case 2:  // int/Integer
                    int intVal;
                    try {
                        Integer val = (Integer) field.getter.invoke(obj);
                        intVal = val == null ? 0 : val;
                    } catch (Throwable e) {
                        intVal = 0;
                    }
                    if (intVal != 0 || field.type == int.class) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append(field.jsonKey);
                        // 小整数快速路径
                        if (intVal >= 0 && intVal < 10000) {
                            sb.append(SMALL_INTS[intVal]);
                        } else {
                            sb.append(intVal);
                        }
                    }
                    break;
                case 3:  // long/Long
                    long longVal;
                    try {
                        Long val = (Long) field.getter.invoke(obj);
                        longVal = val == null ? 0L : val;
                    } catch (Throwable e) {
                        longVal = 0L;
                    }
                    if (longVal != 0L || field.type == long.class) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append(field.jsonKey);
                        // 小长整数快速路径
                        if (longVal >= 0 && longVal < 10000) {
                            sb.append(SMALL_INTS[(int) longVal]);
                        } else {
                            sb.append(longVal);
                        }
                    }
                    break;
                case 4:  // double/Double
                    double doubleVal;
                    try {
                        Double val = (Double) field.getter.invoke(obj);
                        doubleVal = val == null ? 0.0 : val;
                    } catch (Throwable e) {
                        doubleVal = 0.0;
                    }
                    if (doubleVal != 0.0 || field.type == double.class) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append(field.jsonKey);
                        sb.append(doubleVal);
                    }
                    break;
                case 6:  // boolean/Boolean
                    boolean boolVal;
                    try {
                        Boolean val = (Boolean) field.getter.invoke(obj);
                        boolVal = val == null ? false : val;
                    } catch (Throwable e) {
                        boolVal = false;
                    }
                    if (!first) sb.append(',');
                    first = false;
                    sb.append(field.jsonKey);
                    sb.append(boolVal ? "true" : "false");
                    break;
                default:
                    Object value;
                    try {
                        value = field.getter.invoke(obj);
                    } catch (Throwable e) {
                        value = null;
                    }
                    if (value == null) {
                        break;
                    }
                    if (!first) sb.append(',');
                    first = false;
                    sb.append(field.jsonKey);
                    writeValueByTypeCodeFast(value, sb, (byte) typeCode);
                    break;
            }
        }

        sb.append('}');
    }

    /**
     * 根据类型代码快速写入值（FastJSON2 架构 - 处理所有类型包括 Bean/List/Map）
     *
     * @param value 值
     * @param sb StringBuilder
     * @param typeCode 类型代码
     */
    public static void writeValueByTypeCodeFast(Object value, StringBuilder sb, byte typeCode) {
        switch (typeCode) {
            case TYPE_CODE_STRING:
                writeString((String) value, sb);
                break;
            case TYPE_CODE_INTEGER:
                writeInt((Integer) value, sb);
                break;
            case TYPE_CODE_LONG:
                writeLong((Long) value, sb);
                break;
            case TYPE_CODE_DOUBLE:
                writeDouble((Double) value, sb);
                break;
            case TYPE_CODE_FLOAT:
                writeFloat((Float) value, sb);
                break;
            case TYPE_CODE_BOOLEAN:
                writeBoolean((Boolean) value, sb);
                break;
            case TYPE_CODE_CHARACTER:
                writeChar((Character) value, sb);
                break;
            case TYPE_CODE_SHORT:
            case TYPE_CODE_BYTE:
                sb.append(value);
                break;
            case TYPE_CODE_ARRAY:
                writeArray(value, sb);
                break;
            case TYPE_CODE_LIST:
                writeList((List<?>) value, sb);
                break;
            case TYPE_CODE_MAP:
                writeMapOptimized((Map<?, ?>) value, sb);
                break;
            case TYPE_CODE_DATE:
                writeString(formatDateValue(value), sb);
                break;
            case TYPE_CODE_BIGDECIMAL:
                sb.append(((BigDecimal) value).toPlainString());
                break;
            case TYPE_CODE_BIGINTEGER:
                sb.append(((BigInteger) value).toString());
                break;
            case TYPE_CODE_UUID:
                writeString(value.toString(), sb);
                break;
            default:
                if (value instanceof Optional<?> optional) {
                    if (optional.isPresent()) {
                        writeValueByTypeCodeFast(optional.get(), sb, getTypeCode(optional.get()));
                    } else {
                        sb.append("null");
                    }
                } else {
                    writeBeanWithCycleDetection(value, sb);
                }
                break;
        }
    }

    /**
     * 格式化日期（带模式）
     */
    public static String formatDateWithPattern(Object value, String pattern) {
        if (value == null) return null;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            if (value instanceof TemporalAccessor temporal) {
                return formatter.format(temporal);
            } else if (value instanceof Date date) {
                return date.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime().format(formatter);
            }
        } catch (Exception e) {
            LOGGER.fine("Failed to format date with pattern '" + pattern + "': " + e.getMessage());
        }
        return value.toString();
    }

    /**
     * 日期格式化器缓存（避免每次调用 DateTimeFormatter.ofPattern）
     */
    private static volatile String cachedDateFormat = null;
    private static volatile DateTimeFormatter cachedFormatter = null;

    /**
     * 格式化日期/时间值为字符串（统一入口，支持全局日期格式配置）。
     *
     * <p>格式化优先级：
     * <ol>
     *   <li>{@link YdszJsonConfig#getDateFormat()} 全局日期格式（非空时优先）</li>
     *   <li>ISO 默认格式（toString）</li>
     * </ol>
     * 支持所有 java.time.* 和 java.util.Date 类型。</p>
     *
     * @param value 日期/时间值
     * @return 格式化后的字符串
     * @since 1.0.0
     */
    public static String formatDateValue(Object value) {
        if (value == null) return null;

        String globalFormat = YdszJsonConfig.getInstance().getDateFormat();
        if (globalFormat != null && !globalFormat.isEmpty()) {
            DateTimeFormatter formatter = getCachedFormatter(globalFormat);
            if (formatter != null) {
                try {
                    if (value instanceof TemporalAccessor temporal) {
                        return formatter.format(temporal);
                    } else if (value instanceof Date date) {
                        return date.toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                                .format(formatter);
                    }
                } catch (Exception e) {
                    // 格式化失败，回退到 toString
                }
            }
        }

        if (value instanceof TemporalAccessor temporal) {
            return temporal.toString();
        } else if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        return value.toString();
    }

    /**
     * 获取缓存的 DateTimeFormatter（避免重复 ofPattern 调用）
     */
    private static DateTimeFormatter getCachedFormatter(String pattern) {
        if (pattern.equals(cachedDateFormat)) {
            return cachedFormatter;
        }
        try {
            DateTimeFormatter newFormatter = DateTimeFormatter.ofPattern(pattern);
            cachedDateFormat = pattern;
            cachedFormatter = newFormatter;
            return newFormatter;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 写入格式化数字
     */
    public static void writeFormattedNumber(Number value, String format, StringBuilder sb) {
        if (value instanceof Double || value instanceof Float) {
            double d = value.doubleValue();
            if (!format.isEmpty()) {
                sb.append(String.format(format, d));
            } else {
                sb.append(d);
            }
        } else if (value instanceof Long) {
            long l = value.longValue();
            if (!format.isEmpty()) {
                sb.append(String.format(format, l));
            } else {
                sb.append(l);
            }
        } else {
            sb.append(value);
        }
    }

    /**
     * 写入 HTML 安全字符串
     */
    public static void writeHtmlSafeString(String value, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '<': sb.append("\\u003c"); break;
                case '>': sb.append("\\u003e"); break;
                case '&': sb.append("\\u0026"); break;
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                default: sb.append(c); break;
            }
        }
        sb.append('"');
    }

    /**
     * 写入 Bean 对象（带循环引用检测）
     */
    public static void writeBeanWithCycleDetection(Object obj, StringBuilder sb) {
        Set<Object> current = SerializationProvider.SERIALIZING_OBJECTS.get();

        if (current.contains(obj)) {
            sb.append("{\"$ref\":\"cycle\"}");
            return;
        }

        current.add(obj);
        try {
            writeBean(obj, sb);
        } finally {
            current.remove(obj);
        }
    }

    /**
     * 直接写入值（优化版，避免重复类型检查）
     */
    public static void writeValueDirect(Object obj, StringBuilder sb) {
        if (obj == null) {
            sb.append("null");
            return;
        }

        Class<?> clazz = obj.getClass();

        if (clazz == String.class) {
            writeString((String) obj, sb);
        } else if (clazz == Integer.class) {
            writeInt((Integer) obj, sb);
        } else if (clazz == Long.class) {
            writeLong((Long) obj, sb);
        } else if (clazz == Double.class) {
            writeDouble((Double) obj, sb);
        } else if (clazz == Float.class) {
            writeFloat((Float) obj, sb);
        } else if (clazz == Boolean.class) {
            writeBoolean((Boolean) obj, sb);
        } else if (clazz == Character.class) {
            writeChar((Character) obj, sb);
        } else if (clazz == UUID.class) {
            writeString(obj.toString(), sb);
        } else if (obj instanceof Optional<?> optional) {
            if (optional.isPresent()) {
                writeValueDirect(optional.get(), sb);
            } else {
                sb.append("null");
            }
        } else if (obj instanceof List) {
            writeList((List<?>) obj, sb);
        } else if (obj instanceof Map) {
            writeMapOptimized((Map<?, ?>) obj, sb);
        } else if (clazz.isArray()) {
            writeArray(obj, sb);
        } else if (obj instanceof TemporalAccessor || obj instanceof Date) {
            writeString(formatDateValue(obj), sb);
        } else {
            writeBeanWithCycleDetection(obj, sb);
        }
    }
}
