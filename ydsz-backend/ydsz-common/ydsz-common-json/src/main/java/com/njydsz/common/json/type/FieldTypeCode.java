package com.njydsz.common.json.type;

/**
 * 统一的字段类型码枚举（P1-A4 类型码统一方案）。
 *
 * <p>取代本模块中 6 套不兼容的 int 类型码（ValueWriter.TYPE_CODE_* / FieldMeta.computeSerializeTypeCode /
 * AsmBeanCodecGenerator.getTypeCode / JsonTypeCode / ZeroCopyDeserializer.computeTypeCode /
 * ObjectReader.getTypeCode / BeanReader.getTypeCode），后续版本将所有组件逐步迁移至此枚举。
 *
 * <p>当前编码表以 {@link com.njydsz.common.json.provider.ValueWriter} 的 TYPE_CODE_* 为准，
 * 各组件通过 {@link #fromLegacy(int, String)} 按来源兼容转换。
 *
 * @since 1.0.0
 */
public enum FieldTypeCode {
    STRING(1), INT(2), LONG(3), DOUBLE(4), FLOAT(5), BOOLEAN(6),
    CHAR(7), SHORT(8), BYTE(9),
    LOCAL_DATE_TIME(10), LOCAL_DATE(11), DATE(12),
    COLLECTION(13), MAP(14), NESTED_OBJECT(15),
    BIG_DECIMAL(16), BIG_INTEGER(17), UUID(18);

    private final int code;

    FieldTypeCode(int code) {
        this.code = code;
    }

    /**
     * 返回该字段类型对应的整型类型码。
     *
     * <p>类型码用于序列化/反序列化路径上以 int 快速分支（取代反射或 instanceof 链），
     * 编码表见类级文档。该值与旧系统类型码不完全兼容，跨系统转换请走
     * {@link #fromLegacy(int, String)}。</p>
     *
     * @return 整型类型码（编码含义见枚举声明顺序）
     */
    public int code() {
        return code;
    }

    /**
     * 按当前编码方案获取枚举（直接匹配）。
     */
    public static FieldTypeCode of(int code) {
        for (FieldTypeCode tc : values()) {
            if (tc.code == code) return tc;
        }
        return NESTED_OBJECT;
    }

    /**
     * 从各旧系统的 int 类型码按来源转换。
     * @param code 旧系统的 int 类型码
     * @param source 来源标识（"FieldMeta"/"Asm"/"ZeroCopy"/"BeanReader"）
     */
    public static FieldTypeCode fromLegacy(int code, String source) {
        return switch (source) {
            case "Asm" -> fromAsmCode(code);
            case "ZeroCopy" -> fromZeroCopyCode(code);
            case "FieldMeta" -> of(code);
            default -> of(code);
        };
    }

    private static FieldTypeCode fromAsmCode(int c) {
        return switch (c) {
            case 1 -> STRING; case 2 -> INT; case 3 -> LONG; case 4 -> DOUBLE;
            case 5 -> FLOAT; case 6 -> BOOLEAN;
            case 7 -> SHORT; case 8 -> BYTE; case 9 -> CHAR;
            case 10 -> LOCAL_DATE_TIME; case 11 -> LOCAL_DATE; case 12 -> DATE;
            case 13 -> COLLECTION; case 14 -> MAP; case 15 -> NESTED_OBJECT;
            default -> NESTED_OBJECT;
        };
    }

    private static FieldTypeCode fromZeroCopyCode(int c) {
        return switch (c) {
            case 1 -> INT; case 2 -> LONG; case 3 -> DOUBLE; case 4 -> FLOAT;
            case 5 -> BOOLEAN; case 6 -> STRING;
            default -> NESTED_OBJECT;
        };
    }
}
