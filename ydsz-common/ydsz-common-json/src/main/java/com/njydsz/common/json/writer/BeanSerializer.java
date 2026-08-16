package com.njydsz.common.json.writer;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Map;
import com.njydsz.common.json.cache.FieldMeta;
import com.njydsz.common.json.number.NumberUtils;
import com.njydsz.common.json.provider.FieldMetadataLoader;
import com.njydsz.common.json.provider.SerializationProvider;

/**
 * Bean 专用序列化器
 *
 * <p>为每个 Bean 类预计算字段元数据，使用 char[] 直接写入缓冲区，
 * 消除运行时类型检查开销，提供高性能的 Bean 序列化能力。</p>
 *
 * <p><b>优化策略：</b></p>
 * <ul>
 *   <li>预计算字段元数据 - 避免运行时反射</li>
 *   <li>char[] 直接写入 - 避免 StringBuilder 开销</li>
 *   <li>类型代码快速路径 - String/int/long 直接写入</li>
 *   <li>列权限字段排除 - 支持字段级权限控制</li>
 * </ul>
 *
 * <p><b>使用场景：</b></p>
 * <ul>
 *   <li>高性能 Bean 序列化</li>
 *   <li>需要字段级权限控制的场景</li>
 *   <li>高频调用的序列化热点</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public final class BeanSerializer {

    /** Bean 类 */
    public final Class<?> clazz;

    /** 字段数量 */
    public final int fieldCount;

    /** 字段序列化信息 */
    public final FieldWriter[] fields;

    /** 预估 JSON 大小 */
    public final int estimatedSize;

    /** @JsonAnyGetter 方法（null 表示无） */
    public final Method anyGetterMethod;

    /**
     * 是否为纯原始类型 Bean（所有字段均为 String/int/long/double/float/boolean/
     * short/byte/char/BigInteger/BigDecimal/Date/LocalDate/LocalDateTime 等，
     * 不含嵌套 Bean/Collection/Map 引用类型）。
     *
     * <p>当此标志为 true 时，{@link #write(Object, JSONWriter)} 永远不会递归进入
     * {@link SerializationProvider#serialize(Object)}，因此上层调用方可以安全地跳过
     * {@code serializingObjects} 的 add/remove 操作，避免 IdentityHashMap 的查询开销。</p>
     *
     * @since 1.2.0
     */
    public final boolean primitiveOnly;

    /**
     * 构造 Bean 序列化器
     *
     * <p>预计算字段元数据，过滤需要跳过的字段，计算预估 JSON 大小。</p>
     *
     * @param clazz Bean 类型
     * @param fieldMetas 字段元数据数组
     */
    public BeanSerializer(Class<?> clazz, FieldMeta[] fieldMetas) {
        this.clazz = clazz;

        // 检测 @JsonAnyGetter 方法
        this.anyGetterMethod = FieldMetadataLoader.findAnyGetterMethod(clazz);

        // 所有字段均为有效字段（@JsonInclude 策略在写入时由 shouldSkipValue 判定）
        this.fieldCount = fieldMetas.length;
        this.fields = new FieldWriter[fieldCount];
        int estimatedSize = 2; // {}

        int idx = 0;
        boolean allPrimitive = true;
        for (FieldMeta meta : fieldMetas) {
            this.fields[idx++] = new FieldWriter(meta);
            estimatedSize += meta.jsonKeyLen + 16; // 键名 + 平均字段值
            // serializeTypeCode == 0 表示嵌套对象/引用类型（非原始类型）
            if (allPrimitive && meta.serializeTypeCode == 0) {
                allPrimitive = false;
            }
        }

        this.estimatedSize = estimatedSize;
        this.primitiveOnly = allPrimitive;
    }

    /**
     * 字段写入器
     */
    public static final class FieldWriter {

        /** 字段访问器 */
        public final MethodHandle getter;

        /** Java 字段名（用于异常路径追踪） */
        public final String fieldName;

        /** JSON 键名（含引号和冒号） */
        public final String jsonKey;

        /** JSON 键名长度 */
        public final int jsonKeyLen;

        /** 字段类型 */
        public final Class<?> type;

        /** 类型代码 */
        public final int typeCode;

        /**
         * 构造字段写入器
         *
         * @param meta 字段元数据
         */
        public FieldWriter(FieldMeta meta) {
            this.getter = meta.getter;
            this.fieldName = meta.name;
            this.jsonKey = meta.jsonKey;
            this.jsonKeyLen = meta.jsonKey.length();
            this.type = meta.type;
            this.typeCode = meta.serializeTypeCode;
        }
    }

    /**
     * 序列化对象到 JSONWriter
     *
     * <p>将 Bean 对象序列化为 JSON 格式，直接写入缓冲区，
     * 支持列权限字段排除。</p>
     *
     * @param obj 要序列化的 Bean 对象
     * @param writer JSON 写入器
     */
    public void write(Object obj, JSONWriter writer) {
        writer.ensureCapacity(estimatedSize);

        char[] buf = writer.buf;
        int pos = writer.pos;

        // 写入 {
        buf[pos++] = '{';

        boolean first = true;

        for (int i = 0; i < fieldCount; i++) {
            FieldWriter field = fields[i];

            // 列权限字段排除检查
            if (SerializationProvider.isFieldExcluded(field.jsonKey)) {
                continue;
            }

            switch (field.typeCode) {
                case 1: // String
                    String strVal;
                    try {
                        strVal = (String) field.getter.invoke(obj);
                    } catch (Throwable e) {
                        strVal = null;
                    }
                    if (strVal != null) {
                        if (!first) {
                            buf[pos++] = ',';
                        }
                        first = false;

                        // 写入键名
                        int keyLen = field.jsonKeyLen;
                        field.jsonKey.getChars(0, keyLen, buf, pos);
                        pos += keyLen;

                        // 写入字符串值
                        int len = strVal.length();
                        buf[pos++] = '"';

                        // 快速路径：检查转义
                        boolean needsEscape = false;
                        for (int j = 0; j < len; j++) {
                            char c = strVal.charAt(j);
                            if (c < ' ' || c == '"' || c == '\\') {
                                needsEscape = true;
                                break;
                            }
                        }

                        if (!needsEscape) {
                            strVal.getChars(0, len, buf, pos);
                            pos += len;
                        } else {
                            // 需要转义，写入并更新 pos
                            pos = writeStringWithEscape(strVal, buf, pos);
                        }

                        buf[pos++] = '"';
                    }
                    break;

                case 2: // int/Integer
                    int intVal;
                    try {
                        Integer val = (Integer) field.getter.invoke(obj);
                        intVal = val == null ? 0 : val;
                    } catch (Throwable e) {
                        intVal = 0;
                    }
                    if (intVal != 0 || field.type == int.class) {
                        if (!first) {
                            buf[pos++] = ',';
                        }
                        first = false;

                        int keyLen = field.jsonKeyLen;
                        field.jsonKey.getChars(0, keyLen, buf, pos);
                        pos += keyLen;

                        pos += NumberUtils.writeInt(intVal, buf, pos);
                    }
                    break;

                case 3: // long/Long
                    long longVal;
                    try {
                        Long val = (Long) field.getter.invoke(obj);
                        longVal = val == null ? 0L : val;
                    } catch (Throwable e) {
                        longVal = 0L;
                    }
                    if (longVal != 0L || field.type == long.class) {
                        if (!first) {
                            buf[pos++] = ',';
                        }
                        first = false;

                        int keyLen = field.jsonKeyLen;
                        field.jsonKey.getChars(0, keyLen, buf, pos);
                        pos += keyLen;

                        pos += NumberUtils.writeLong(longVal, buf, pos);
                    }
                    break;

                case 4: // double/Double
                    double doubleVal;
                    try {
                        Double val = (Double) field.getter.invoke(obj);
                        doubleVal = val == null ? 0.0 : val;
                    } catch (Throwable e) {
                        doubleVal = 0.0;
                    }
                    if (doubleVal != 0.0 || field.type == double.class) {
                        if (!first) {
                            buf[pos++] = ',';
                        }
                        first = false;

                        int keyLen = field.jsonKeyLen;
                        field.jsonKey.getChars(0, keyLen, buf, pos);
                        pos += keyLen;

                        pos = writer.writeDoubleToBuf(doubleVal, pos);
                    }
                    break;

                case 5: // float/Float
                    float floatVal;
                    try {
                        Float val = (Float) field.getter.invoke(obj);
                        floatVal = val == null ? 0.0f : val;
                    } catch (Throwable e) {
                        floatVal = 0.0f;
                    }
                    if (floatVal != 0.0f || field.type == float.class) {
                        if (!first) {
                            buf[pos++] = ',';
                        }
                        first = false;

                        int keyLen = field.jsonKeyLen;
                        field.jsonKey.getChars(0, keyLen, buf, pos);
                        pos += keyLen;

                        pos = writer.writeFloatToBuf(floatVal, pos);
                    }
                    break;

                case 6: // boolean/Boolean
                    boolean boolVal;
                    try {
                        Boolean val = (Boolean) field.getter.invoke(obj);
                        boolVal = val != null && val;
                    } catch (Throwable e) {
                        boolVal = false;
                    }
                    if (boolVal || field.type == boolean.class) {
                        if (!first) {
                            buf[pos++] = ',';
                        }
                        first = false;

                        int keyLen = field.jsonKeyLen;
                        field.jsonKey.getChars(0, keyLen, buf, pos);
                        pos += keyLen;

                        if (boolVal) {
                            buf[pos++] = 't';
                            buf[pos++] = 'r';
                            buf[pos++] = 'u';
                            buf[pos++] = 'e';
                        } else {
                            buf[pos++] = 'f';
                            buf[pos++] = 'a';
                            buf[pos++] = 'l';
                            buf[pos++] = 's';
                            buf[pos++] = 'e';
                        }
                    }
                    break;

                case 13: // Date / LocalDate / LocalDateTime / LocalTime / Instant
                case 14: // BigDecimal
                case 15: // BigInteger
                    Object dateOrNumVal;
                    try {
                        dateOrNumVal = field.getter.invoke(obj);
                    } catch (Throwable e) {
                        dateOrNumVal = null;
                    }
                    if (dateOrNumVal == null) {
                        break;
                    }
                    if (!first) {
                        buf[pos++] = ',';
                    }
                    first = false;

                    int keyLen = field.jsonKeyLen;
                    field.jsonKey.getChars(0, keyLen, buf, pos);
                    pos += keyLen;

                    // BigDecimal / BigInteger / Date 直接调用 JSONWriter 的写入方法，
                    // 这些类型不涉及循环引用检测，无需递归进入 SerializationProvider
                    writer.pos = pos;
                    writer.writeValueInline(dateOrNumVal);
                    pos = writer.pos;
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
                    if (!first) {
                        buf[pos++] = ',';
                    }
                    first = false;

                    int defaultKeyLen = field.jsonKeyLen;
                    field.jsonKey.getChars(0, defaultKeyLen, buf, pos);
                    pos += defaultKeyLen;

                    writer.pos = pos;
                    // 字段路径追踪：writeValueInline 可能递归进入子 Bean
                    SerializationProvider.pushFieldPath(field.fieldName);
                    try {
                        writer.writeValueInline(value);
                    } finally {
                        SerializationProvider.popFieldPath();
                    }
                    pos = writer.pos;
                    break;
            }
        }

        // 写入 }
        buf[pos++] = '}';
        writer.pos = pos;

        // @JsonAnyGetter：将 Map 中的键值对展开为顶层 JSON 属性
        if (anyGetterMethod != null) {
            writeAnyGetterProperties(obj, writer);
        }
    }

    /**
     * 写入 @JsonAnyGetter 返回的 Map 中的键值对作为顶层 JSON 属性。
     *
     * <p>在 } 之前插入逗号和新属性。需要回退 pos 以在 } 前插入内容。</p>
     *
     * @param obj 要序列化的 Bean 对象
     * @param writer JSON 写入器
     */
    private void writeAnyGetterProperties(Object obj, JSONWriter writer) {
        Map<?, ?> map;
        try {
            map = (Map<?, ?>) anyGetterMethod.invoke(obj);
        } catch (Exception e) {
            return; // 调用失败时静默跳过
        }
        if (map == null || map.isEmpty()) {
            return;
        }

        // 回退 pos 以在 } 前插入内容
        int pos = writer.pos - 1; // 回退到 } 的位置
        char[] buf = writer.buf;

        boolean firstAny = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;

            String key = String.valueOf(entry.getKey());
            writer.ensureCapacity(32 + key.length() * 2);
            buf = writer.buf; // ensureCapacity 可能重新分配

            if (!firstAny) {
                buf[pos++] = ',';
            }
            firstAny = false;
            buf[pos++] = '"';
            key.getChars(0, key.length(), buf, pos);
            pos += key.length();
            buf[pos++] = '"';
            buf[pos++] = ':';

            writer.pos = pos;
            writer.writeValueInline(value);
            pos = writer.pos;
        }

        buf[pos++] = '}';
        writer.pos = pos;
    }

    /**
     * 写入带转义的字符串到缓冲区
     *
     * <p>处理特殊字符的转义，生成合法的 JSON 字符串。</p>
     *
     * @param str 原始字符串
     * @param buf 目标缓冲区
     * @param pos 当前写入位置
     * @return 写入后的新位置
     */
    private static int writeStringWithEscape(String str, char[] buf, int pos) {
        int len = str.length();

        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    buf[pos++] = '\\';
                    buf[pos++] = '"';
                    break;
                case '\\':
                    buf[pos++] = '\\';
                    buf[pos++] = '\\';
                    break;
                case '\n':
                    buf[pos++] = '\\';
                    buf[pos++] = 'n';
                    break;
                case '\r':
                    buf[pos++] = '\\';
                    buf[pos++] = 'r';
                    break;
                case '\t':
                    buf[pos++] = '\\';
                    buf[pos++] = 't';
                    break;
                default:
                    if (c < ' ') {
                        buf[pos++] = '\\';
                        buf[pos++] = 'u';
                        buf[pos++] = '0';
                        buf[pos++] = '0';
                        char h = (char) (c >> 4);
                        char l = (char) (c & 0xf);
                        buf[pos++] = (char) (h < 10 ? h + '0' : h - 10 + 'a');
                        buf[pos++] = (char) (l < 10 ? l + '0' : l - 10 + 'a');
                    } else {
                        buf[pos++] = c;
                    }
                    break;
            }
        }

        return pos;
    }
}
