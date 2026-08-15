package com.njydsz.common.excel.columnar;

import java.util.Locale;
import java.util.Optional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
/**
 * 列式存储字段类型枚举。
 *
 * <p>屏蔽 Parquet 与 ORC 底层类型差异，提供统一的 Java 类型抽象。
 * 用于 {@link ColumnarField}、{@link ColumnarSchema} 等列式存储元数据定义。
 *
 * <h2>Parquet/ORC 类型映射</h2>
 * <table border="1">
 *   <caption>类型映射表</caption>
 *   <tr><th>本枚举</th><th>Parquet 类型</th><th>ORC 类型</th><th>Java 类型</th></tr>
 *   <tr><td>BOOLEAN</td><td>BOOLEAN</td><td>BOOLEAN</td><td>Boolean</td></tr>
 *   <tr><td>INT32</td><td>INT32</td><td>INT (int)</td><td>Integer</td></tr>
 *   <tr><td>INT64</td><td>INT64</td><td>LONG (bigint)</td><td>Long</td></tr>
 *   <tr><td>FLOAT</td><td>FLOAT</td><td>FLOAT</td><td>Float</td></tr>
 *   <tr><td>DOUBLE</td><td>DOUBLE</td><td>DOUBLE</td><td>Double</td></tr>
 *   <tr><td>STRING</td><td>BYTE_ARRAY (UTF8)</td><td>STRING (varchar)</td><td>String</td></tr>
 *   <tr><td>BINARY</td><td>BYTE_ARRAY</td><td>BINARY</td><td>byte[]</td></tr>
 *   <tr><td>DATE</td><td>DATE (INT32)</td><td>DATE</td><td>LocalDate</td></tr>
 *   <tr><td>TIMESTAMP</td><td>INT96 / TIMESTAMP_MILLIS</td><td>TIMESTAMP</td><td>LocalDateTime</td></tr>
 *   <tr><td>DECIMAL</td><td>DECIMAL (Binary)</td><td>DECIMAL</td><td>BigDecimal</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum ColumnarType {

    /** 布尔类型 */
    BOOLEAN,
    /** 32 位整数 */
    INT32,
    /** 64 位整数 */
    INT64,
    /** 单精度浮点 */
    FLOAT,
    /** 双精度浮点 */
    DOUBLE,
    /** UTF-8 字符串 */
    STRING,
    /** 字节数组 */
    BINARY,
    /** 日期（年-月-日，无时区） */
    DATE,
    /** 时间戳（含时区） */
    TIMESTAMP,
    /** 任意精度小数（precision/scale 见 {@link ColumnarField}） */
    DECIMAL;

    /**
     * 推断字段的列式类型（基于 Java 类型）。
     *
     * @param javaType Java 类型（String/Integer/Long/...）
     * @return 推断的列式类型，无法推断时返回 {@link Optional#empty()}
     */
    public static Optional<ColumnarType> fromJavaType(Class<?> javaType) {
        if (javaType == null) {
            return Optional.empty();
        }
        if (javaType == Boolean.class || javaType == boolean.class) {
            return Optional.of(BOOLEAN);
        }
        if (javaType == Integer.class || javaType == int.class
                || javaType == Short.class || javaType == short.class
                || javaType == Byte.class || javaType == byte.class) {
            return Optional.of(INT32);
        }
        if (javaType == Long.class || javaType == long.class) {
            return Optional.of(INT64);
        }
        if (javaType == Float.class || javaType == float.class) {
            return Optional.of(FLOAT);
        }
        if (javaType == Double.class || javaType == double.class) {
            return Optional.of(DOUBLE);
        }
        if (javaType == String.class || javaType == CharSequence.class) {
            return Optional.of(STRING);
        }
        if (javaType == byte[].class) {
            return Optional.of(BINARY);
        }
        if (javaType == LocalDate.class) {
            return Optional.of(DATE);
        }
        if (javaType == LocalDateTime.class
                || javaType == Date.class
                || javaType == Instant.class) {
            return Optional.of(TIMESTAMP);
        }
        if (javaType == BigDecimal.class) {
            return Optional.of(DECIMAL);
        }
        return Optional.empty();
    }

    /**
     * 解析字符串名称（不区分大小写）。
     *
     * @param name 类型名（如 "string" / "BIGINT" / "Decimal"）
     * @return 匹配的类型
     */
    public static Optional<ColumnarType> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ColumnarType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * 是否为数值类型（INT32/INT64/FLOAT/DOUBLE/DECIMAL）。
     */
    public boolean isNumeric() {
        return this == INT32 || this == INT64 || this == FLOAT || this == DOUBLE || this == DECIMAL;
    }

    /**
     * 是否为整型（INT32/INT64）。
     */
    public boolean isIntegral() {
        return this == INT32 || this == INT64;
    }
}
