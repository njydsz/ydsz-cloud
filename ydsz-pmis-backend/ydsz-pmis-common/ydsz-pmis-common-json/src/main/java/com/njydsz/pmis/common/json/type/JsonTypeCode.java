package com.njydsz.pmis.common.json.type;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Map;

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

    public int getCode() {
        return code;
    }

    public Class<?> getDefaultType() {
        return defaultType;
    }

    public static JsonTypeCode fromCode(int code) {
        for (JsonTypeCode typeCode : values()) {
            if (typeCode.code == code) {
                return typeCode;
            }
        }
        throw new IllegalArgumentException("Unknown type code: " + code);
    }
}
