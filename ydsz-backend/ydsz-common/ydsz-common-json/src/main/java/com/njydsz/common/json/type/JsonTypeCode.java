package com.njydsz.common.json.type;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JSON 类型代码枚举
 *
 * <p>为每种 JSON 数据类型定义唯一的整数代码，用于 ASM 字节码生成时的类型判断优化。
 * 通过类型代码可以快速进行 instanceof 链的替代，提升序列化/反序列化性能。</p>
 *
 * <p><b>类型代码分配：</b></p>
 * <ul>
 *   <li>1 - String 字符串</li>
 *   <li>2 - Integer 整数</li>
 *   <li>3 - Long 长整数</li>
 *   <li>4 - Double 双精度浮点数</li>
 *   <li>5 - Float 单精度浮点数</li>
 *   <li>6 - Boolean 布尔值</li>
 *   <li>7 - Character 字符</li>
 *   <li>8 - Short 短整数</li>
 *   <li>9 - Byte 字节</li>
 *   <li>10 - Array 数组</li>
 *   <li>11 - List 列表</li>
 *   <li>12 - Map 映射</li>
 *   <li>13 - Date 日期</li>
 *   <li>14 - BigDecimal 大精度数字</li>
 *   <li>15 - BigInteger 大整数</li>
 *   <li>16 - Bean 普通对象</li>
 * </ul>
 *
 * @deprecated 此类型码与 AsmBeanCodecGenerator/FieldMeta/ValueWriter 不一致，已死代码。
 *             统一类型码后（P1-A4）将被删除。
 */
@Deprecated
public enum JsonTypeCode {

    STRING(1, String.class),
    INTEGER(2, Integer.class),
    LONG(3, Long.class),
    DOUBLE(4, Double.class),
    FLOAT(5, Float.class),
    BOOLEAN(6, Boolean.class),
    CHARACTER(7, Character.class),
    SHORT(8, Short.class),
    BYTE(9, Byte.class),
    ARRAY(10, Object[].class),
    LIST(11, List.class),
    MAP(12, Map.class),
    DATE(13, Date.class),
    BIGDECIMAL(14, BigDecimal.class),
    BIGINTEGER(15, BigInteger.class),
    BEAN(16, Object.class);

    private final int code;
    private final Class<?> defaultType;

    JsonTypeCode(int code, Class<?> defaultType) {
        this.code = code;
        this.defaultType = defaultType;
    }

    /**
     * 获取类型代码
     *
     * @return 类型的整数代码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取默认类型
     *
     * @return 对应的 Java 类型
     */
    public Class<?> getDefaultType() {
        return defaultType;
    }

    /**
     * 根据类型代码查找对应的 JsonTypeCode
     *
     * @param code 类型代码
     * @return 对应的 JsonTypeCode
     * @throws IllegalArgumentException 如果代码无效
     */
    public static JsonTypeCode fromCode(int code) {
        for (JsonTypeCode typeCode : values()) {
            if (typeCode.code == code) {
                return typeCode;
            }
        }
        throw new IllegalArgumentException("Unknown type code: " + code);
    }
}
