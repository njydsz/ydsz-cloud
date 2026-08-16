package com.njydsz.common.json.writer;

import com.njydsz.common.json.cache.FieldMeta;
import com.njydsz.common.json.number.NumberUtils;
import com.njydsz.common.json.provider.FieldMetadataLoader;
import com.njydsz.common.json.provider.SerializationProvider;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Map;

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
                        buf[pos++] = '"';\n\n                        // 快速路径：检查转义\n                        boolean needsEscape = false;\n                        for (int j = 0; j < len; j++) {\n                            char c = strVal.charAt(j);\n                            if (c < ' ' || c == '"' || c == '\\') {
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

                        buf[pos++] = '"';\n                    }\n                    break;\n\n                case 2: // int/Integer\n                    int intVal;\n                    try {\n                        Integer val = (Integer) field.getter.invoke(obj);\n                        intVal = val == null ? 0 : val;\n                    } catch (Throwable e) {\n                        intVal = 0;\n                    }\n                    if (intVal != 0 || field.type == int.class) {\n                        if (!first) {\n                            buf[pos++] = ',';\n                        }\n                        first = false;\n\n                        int keyLen = field.jsonKeyLen;\n                        field.jsonKey.getChars(0, keyLen, buf, pos);\n                        pos += keyLen;\n\n                        pos += NumberUtils.writeInt(intVal, buf, pos);\n                    }\n                    break;\n\n                case 3: // long/Long\n                    long longVal;\n                    try {\n                        Long val = (Long) field.getter.invoke(obj);\n                        longVal = val == null ? 0L : val;\n                    } catch (Throwable e) {\n                        longVal = 0L;\n                    }\n                    if (longVal != 0L || field.type == long.class) {\n                        if (!first) {\n                            buf[pos++] = ',';\n                        }\n                        first = false;\n\n                        int keyLen = field.jsonKeyLen;\n                        field.jsonKey.getChars(0, keyLen, buf, pos);\n                        pos += keyLen;\n\n                        pos += NumberUtils.writeLong(longVal, buf, pos);\n                    }\n                    break;\n\n                case 4: // double/Double\n                    double doubleVal;\n                    try {\n                        Double val = (Double) field.getter.invoke(obj);\n                        doubleVal = val == null ? 0.0 : val;\n                    } catch (Throwable e) {\n                        doubleVal = 0.0;\n                    }\n                    if (doubleVal != 0.0 || field.type == double.class) {\n                        if (!first) {\n                            buf[pos++] = ',';\n                        }\n                        first = false;\n\n                        int keyLen = field.jsonKeyLen;\n                        field.jsonKey.getChars(0, keyLen, buf, pos);\n                        pos += keyLen;\n\n                        pos = writer.writeDoubleToBuf(doubleVal, pos);\n                    }\n                    break;\n\n                case 5: // float/Float\n                    float floatVal;\n                    try {\n                        Float val = (Float) field.getter.invoke(obj);\n                        floatVal = val == null ? 0.0f : val;\n                    } catch (Throwable e) {\n                        floatVal = 0.0f;\n                    }\n                    if (floatVal != 0.0f || field.type == float.class) {\n                        if (!first) {\n                            buf[pos++] = ',';\n                        }\n                        first = false;\n\n                        int keyLen = field.jsonKeyLen;\n                        field.jsonKey.getChars(0, keyLen, buf, pos);\n                        pos += keyLen;\n\n                        pos = writer.writeFloatToBuf(floatVal, pos);\n                    }\n                    break;\n\n                case 6: // boolean/Boolean\n                    boolean boolVal;\n                    try {\n                        Boolean val = (Boolean) field.getter.invoke(obj);\n                        boolVal = val != null && val;\n                    } catch (Throwable e) {\n                        boolVal = false;\n                    }\n                    if (boolVal || field.type == boolean.class) {\n                        if (!first) {\n                            buf[pos++] = ',';\n                        }\n                        first = false;\n\n                        int keyLen = field.jsonKeyLen;\n                        field.jsonKey.getChars(0, keyLen, buf, pos);\n                        pos += keyLen;\n\n                        if (boolVal) {\n                            buf[pos++] = 't';\n                            buf[pos++] = 'r';\n                            buf[pos++] = 'u';\n                            buf[pos++] = 'e';\n                        } else {\n                            buf[pos++] = 'f';\n                            buf[pos++] = 'a';\n                            buf[pos++] = 'l';\n                            buf[pos++] = 's';\n                            buf[pos++] = 'e';\n                        }\n                    }\n                    break;\n\n                case 13: // Date / LocalDate / LocalDateTime / LocalTime / Instant\n                case 14: // BigDecimal\n                case 15: // BigInteger\n                    Object dateOrNumVal;\n                    try {\n                        dateOrNumVal = field.getter.invoke(obj);\n                    } catch (Throwable e) {\n                        dateOrNumVal = null;\n                    }\n                    if (dateOrNumVal == null) {\n                        break;\n                    }\n                    if (!first) {\n                        buf[pos++] = ',';\n                    }\n                    first = false;\n\n                    int keyLen = field.jsonKeyLen;\n                    field.jsonKey.getChars(0, keyLen, buf, pos);\n                    pos += keyLen;\n\n                    // BigDecimal / BigInteger / Date 直接调用 JSONWriter 的写入方法，\n                    // 这些类型不涉及循环引用检测，无需递归进入 SerializationProvider\n                    writer.pos = pos;\n                    writer.writeValueInline(dateOrNumVal);\n                    pos = writer.pos;\n                    break;\n\n                default:\n                    Object value;\n                    try {\n                        value = field.getter.invoke(obj);\n                    } catch (Throwable e) {\n                        value = null;\n                    }\n                    if (value == null) {\n                        break;\n                    }\n                    if (!first) {\n                        buf[pos++] = ',';\n                    }\n                    first = false;\n\n                    int defaultKeyLen = field.jsonKeyLen;\n                    field.jsonKey.getChars(0, defaultKeyLen, buf, pos);\n                    pos += defaultKeyLen;\n\n                    writer.pos = pos;\n                    // 字段路径追踪：writeValueInline 可能递归进入子 Bean\n                    SerializationProvider.pushFieldPath(field.fieldName);\n                    try {\n                        writer.writeValueInline(value);\n                    } finally {\n                        SerializationProvider.popFieldPath();\n                    }\n                    pos = writer.pos;\n                    break;\n            }\n        }\n\n        // 写入 }\n        buf[pos++] = '}';\n        writer.pos = pos;\n\n        // @JsonAnyGetter：将 Map 中的键值对展开为顶层 JSON 属性\n        if (anyGetterMethod != null) {\n            writeAnyGetterProperties(obj, writer);\n        }\n    }\n\n    /**\n     * 写入 @JsonAnyGetter 返回的 Map 中的键值对作为顶层 JSON 属性。\n     *\n     * <p>在 } 之前插入逗号和新属性。需要回退 pos 以在 } 前插入内容。</p>\n     *\n     * @param obj 要序列化的 Bean 对象\n     * @param writer JSON 写入器\n     */\n    private void writeAnyGetterProperties(Object obj, JSONWriter writer) {\n        Map<?, ?> map;\n        try {\n            map = (Map<?, ?>) anyGetterMethod.invoke(obj);\n        } catch (Exception e) {\n            return; // 调用失败时静默跳过\n        }\n        if (map == null || map.isEmpty()) {\n            return;\n        }\n\n        // 回退 pos 以在 } 前插入内容\n        int pos = writer.pos - 1; // 回退到 } 的位置\n        char[] buf = writer.buf;\n\n        boolean firstAny = true;\n        for (Map.Entry<?, ?> entry : map.entrySet()) {\n            Object value = entry.getValue();\n            if (value == null) continue;\n\n            String key = String.valueOf(entry.getKey());\n            writer.ensureCapacity(32 + key.length() * 2);\n            buf = writer.buf; // ensureCapacity 可能重新分配\n\n            if (!firstAny) {\n                buf[pos++] = ',';\n            }\n            firstAny = false;\n            buf[pos++] = '"';
            key.getChars(0, key.length(), buf, pos);
            pos += key.length();
            buf[pos++] = '"';\n            buf[pos++] = ':';\n\n            writer.pos = pos;\n            writer.writeValueInline(value);\n            pos = writer.pos;\n        }\n\n        buf[pos++] = '}';\n        writer.pos = pos;\n    }\n\n    /**\n     * 写入带转义的字符串到缓冲区\n     *\n     * <p>处理特殊字符的转义，生成合法的 JSON 字符串。</p>\n     *\n     * @param str 原始字符串\n     * @param buf 目标缓冲区\n     * @param pos 当前写入位置\n     * @return 写入后的新位置\n     */\n    private static int writeStringWithEscape(String str, char[] buf, int pos) {\n        int len = str.length();\n\n        for (int i = 0; i < len; i++) {\n            char c = str.charAt(i);\n            switch (c) {\n                case '"':
                    buf[pos++] = '\\';
                    buf[pos++] = '"';\n                    break;\n                case '\\':\n                    buf[pos++] = '\\';\n                    buf[pos++] = '\\';\n                    break;\n                case '\n':\n                    buf[pos++] = '\\';\n                    buf[pos++] = 'n';\n                    break;\n                case '\r':\n                    buf[pos++] = '\\';\n                    buf[pos++] = 'r';\n                    break;\n                case '\t':\n                    buf[pos++] = '\\';\n                    buf[pos++] = 't';\n                    break;\n                default:\n                    if (c < ' ') {\n                        buf[pos++] = '\\';\n                        buf[pos++] = 'u';\n                        buf[pos++] = '0';\n                        buf[pos++] = '0';\n                        char h = (char) (c >> 4);\n                        char l = (char) (c & 0xf);\n                        buf[pos++] = (char) (h < 10 ? h + '0' : h - 10 + 'a');\n                        buf[pos++] = (char) (l < 10 ? l + '0' : l - 10 + 'a');\n                    } else {\n                        buf[pos++] = c;\n                    }\n                    break;\n            }\n        }\n\n        return pos;\n    }\n}\n