package com.njydsz.common.json.provider;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.temporal.TemporalAccessor;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonView;
import com.njydsz.common.json.cache.FieldMeta;
import com.njydsz.common.json.cache.SerializerCache;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.writer.JSONWriter;

import java.sql.Date;
import java.sql.Timestamp;
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
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public final class ValueWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueWriter.class);
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

    /** 十六进制字符表（用于 \\uXXXX 转义） */
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * 追加 \\uXXXX 四位数十六进制转义（用于 U+2028/U+2029 等非 BMP 内特殊字符）。
     */
    private static void appendHex4(StringBuilder sb, char c) {
        sb.append("\\u");
        sb.append(HEX[(c >> 12) & 0xf]);
        sb.append(HEX[(c >> 8) & 0xf]);
        sb.append(HEX[(c >> 4) & 0xf]);
        sb.append(HEX[c & 0xf]);
    }

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
        TYPE_CODE_CACHE.put(Date.class, TYPE_CODE_DATE);
        TYPE_CODE_CACHE.put(Timestamp.class, TYPE_CODE_DATE);
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

        // JsonNode 树模型快速路径：直接走树模型 toString()，避免反射序列化损坏
        if (obj instanceof com.njydsz.common.json.tree.JsonNode) {
            sb.append(obj.toString());
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
            if (c < ' ' || c == '"' || c == '\\' || c == '\u2028' || c == '\u2029'
                    || Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
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
                    } else if (c == '\u2028' || c == '\u2029') {
                        // 行/段落分隔符：裸置于 <script> 中会导致 JS 语法错误，安全转义
                        appendHex4(sb, c);
                    } else if (Character.isHighSurrogate(c)) {
                        if (i + 1 < len && Character.isLowSurrogate(str.charAt(i + 1))) {
                            sb.append(c);
                            sb.append(str.charAt(i + 1));
                            i++;
                        } else {
                            // 孤立高位代理：替换为 U+FFFD
                            sb.append('\uFFFD');
                        }
                    } else if (Character.isLowSurrogate(c)) {
                        // 孤立低位代理：替换为 U+FFFD
                        sb.append('\uFFFD');
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
            if (c < ' ' || c == '"' || c == '\\' || c == '\u2028' || c == '\u2029'
                    || Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
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
                    } else if (c == '\u2028' || c == '\u2029') {
                        // 行/段落分隔符：裸置于 <script> 中会导致 JS 语法错误，安全转义
                        appendHex4(sb, c);
                    } else if (Character.isHighSurrogate(c)) {
                        if (i + 1 < len && Character.isLowSurrogate(str.charAt(i + 1))) {
                            sb.append(c);
                            sb.append(str.charAt(i + 1));
                            i++;
                        } else {
                            // 孤立高位代理：替换为 U+FFFD
                            sb.append('\uFFFD');
                        }
                    } else if (Character.isLowSurrogate(c)) {
                        // 孤立低位代理：替换为 U+FFFD
                        sb.append('\uFFFD');
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
        JSONWriter writer = SerializationProvider.getFastWriterPool();
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
        JsonClass classAnnotation = clazz.getAnnotation(JsonClass.class);

        PropertyNamingStrategy strategy = FieldMetadataLoader.NAMING_STRATEGY.get();
        FieldMeta[] fields = SerializerCache.getFieldMeta(clazz, strategy);
        if (fields == null) {
            fields = FieldMetadataLoader.loadFields(clazz);
            SerializerCache.putFieldMeta(clazz, strategy, fields);
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
            // 列权限字段排除检查
            if (SerializationProvider.isFieldExcluded(field.jsonName)) {
                continue;
            }

            Class<?> currentView = SerializationProvider.getCurrentViewClass();
            if (currentView != null) {
                JsonView viewAnnotation = field.field.getAnnotation(JsonView.class);
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

                // null 字段是否输出：@JsonClass(writeNulls=true) 类级注解 或 全局/Mapper writeNulls 配置
                // 此前仅读类注解，导致 JsonConfig/JsonMapper.Builder 的 writeNulls(true) 对 Bean 序列化无效
                boolean shouldWriteNull = classWriteNulls || SerializationProvider.isWriteNulls();
                if (value == null && !shouldWriteNull) {
                    continue;
                }

                // @JsonInclude 策略：NON_EMPTY/NON_DEFAULT 等对非 null 值的过滤
                // shouldSkipValue(null) 已由上方 shouldWriteNull 逻辑覆盖（writeNulls=true 时强制写出），
                // 此处仅对非 null 值应用 NON_EMPTY/NON_DEFAULT 过滤，避免空字符串/空集合/默认值被写出
                if (value != null && field.shouldSkipValue(value)) {
                    continue;
                }

                // @JsonUnwrapped：嵌套属性展开到父对象
                if (field.unwrapped && value != null) {
                    writeUnwrappedFields(value, field, sb, first);
                    first = false;
                    continue;
                }

                if (!first) sb.append(',');
                first = false;

                String jsonName = field.jsonName;
                sb.append('"').append(jsonName).append('"').append(':');

                if (field.isDateType() && value != null) {
                    String formattedDate = dateFormat != null && !dateFormat.isEmpty()
                            ? formatDateWithPattern(value, dateFormat)
                            : field.formatDateValue(value);
                    writeString(formattedDate, sb);
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
                LOGGER.debug("Failed to serialize field {} of {}: {}", field.name, obj.getClass().getName(), e.getMessage());
            }
        }

        // @JsonGetter 计算属性：输出没有对应字段的 @JsonGetter 方法返回值
        Method[] computedProps = FieldMetadataLoader.findComputedProperties(clazz);
        for (Method computedMethod : computedProps) {
            try {
                Object computedValue = computedMethod.invoke(obj);
                if (computedValue != null) {
                    if (!first) sb.append(',');
                    first = false;
                    String propName = FieldMetadataLoader.getComputedPropertyName(computedMethod);
                    sb.append('"').append(propName).append("\":");
                    writeValueDirect(computedValue, sb);
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to invoke @JsonGetter computed property {}: {}", computedMethod.getName(), e.getMessage());
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
        PropertyNamingStrategy strategy = FieldMetadataLoader.NAMING_STRATEGY.get();
        SerializationProvider.BeanSerializerInfo info = SerializationProvider.getOrCreateBeanSerializer(clazz, fields, strategy);

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
                        // @JsonRawValue 支持：原始 JSON 值直接写入，不转义
                        if (field.isRawValue) {
                            sb.append(strVal);
                            break;
                        }
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
            LOGGER.debug("Failed to format date with pattern '{}': {}", pattern, e.getMessage());
        }
        return value.toString();
    }

    /**
     * 日期格式化器缓存（有界 LRU 容量 128，防止动态 pattern 场景下无界增长）
     */
    private static final com.njydsz.common.json.util.BoundedLruCache<String, DateTimeFormatter> FORMATTER_CACHE =
        new com.njydsz.common.json.util.BoundedLruCache<>(128);

    /**
     * 格式化日期/时间值为字符串（统一入口，支持全局日期格式配置）。
     *
     * <p>格式化优先级：
     * <ol>
     *   <li>{@link JsonConfig#getDateFormat()} 全局日期格式（非空时优先）</li>
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

        // 优先从当前线程的 SerializationContext 读取 dateFormat（支持 JsonMapper 独立配置）
        String globalFormat = SerializationProvider.getDateFormat();
        // 回退到全局单例配置
        if (globalFormat == null || globalFormat.isEmpty()) {
            globalFormat = JsonConfig.getInstance().getDateFormat();
        }
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
        try {
            return FORMATTER_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 写入 @JsonUnwrapped 展开字段。
     *
     * <p>将嵌套对象的字段展开到父 JSON 对象中，可添加前缀/后缀。</p>
     *
     * @param nestedObj 嵌套对象
     * @param field 带有 @JsonUnwrapped 注解的字段元数据
     * @param sb JSON 字符串构建器
     * @param first 是否为第一个字段
     * @since 1.0.0
     */
    private static void writeUnwrappedFields(Object nestedObj, FieldMeta field,
                                              StringBuilder sb, boolean first) {
        Class<?> nestedClass = nestedObj.getClass();
        PropertyNamingStrategy nestedStrategy = FieldMetadataLoader.NAMING_STRATEGY.get();
        FieldMeta[] nestedFields = SerializerCache.getFieldMeta(nestedClass, nestedStrategy);
        if (nestedFields == null) {
            nestedFields = FieldMetadataLoader.loadFields(nestedClass);
            SerializerCache.putFieldMeta(nestedClass, nestedStrategy, nestedFields);
        }

        for (FieldMeta nestedField : nestedFields) {
            Object nestedValue;
            try {
                nestedValue = nestedField.getValue(nestedObj);
            } catch (Exception e) {
                continue;
            }
            if (nestedValue == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            // 应用前缀/后缀
            sb.append('"').append(field.unwrapPrefix).append(nestedField.jsonName).append(field.unwrapSuffix).append("\":");
            writeValueDirect(nestedValue, sb);
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
        Set<Object> current = SerializationProvider.getSerializingObjects();

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
