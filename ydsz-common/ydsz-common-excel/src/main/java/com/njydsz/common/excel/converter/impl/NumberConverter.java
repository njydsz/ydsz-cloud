package com.njydsz.common.excel.converter.impl;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.converter.CellValueConverter;
import com.njydsz.common.excel.converter.ConvertContext;

/**
 * 数值类型转换器
 *
 * <p>处理目标类型为Integer、Long、Double、Float、Short、Byte、BigInteger的转换。
 * 支持从String、Double、Boolean等原始值转换。
 * 当strictNumberConversion为true时，转换失败抛出异常；否则仅记录日志并返回null。</p>
 *
 * @author ydsz-team
 * @email ydsz-dev@njydsz.com
 * @version 1.0.0
 * @since 1.0.0
 */
public class NumberConverter implements CellValueConverter {

    private static final Logger log = LoggerFactory.getLogger(NumberConverter.class);

    @Override
    public boolean supports(Class<?> targetType) {
        return targetType == Integer.class || targetType == int.class
            || targetType == Long.class || targetType == long.class
            || targetType == Double.class || targetType == double.class
            || targetType == Float.class || targetType == float.class
            || targetType == Short.class || targetType == short.class
            || targetType == Byte.class || targetType == byte.class
            || targetType == BigInteger.class;
    }

    @Override
    public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof Double) {
            return convertFromNumeric((Double) rawValue, targetType);
        }

        if (rawValue instanceof Long) {
            return convertFromNumeric(((Long) rawValue).doubleValue(), targetType);
        }

        if (rawValue instanceof String) {
            return convertFromString((String) rawValue, targetType, context);
        }

        if (rawValue instanceof Boolean) {
            boolean boolValue = (Boolean) rawValue;
            if (targetType == Integer.class || targetType == int.class) {
                return boolValue ? 1 : 0;
            }
            if (targetType == Long.class || targetType == long.class) {
                return boolValue ? 1L : 0L;
            }
            if (targetType == Double.class || targetType == double.class) {
                return boolValue ? 1.0 : 0.0;
            }
            if (targetType == Float.class || targetType == float.class) {
                return boolValue ? 1.0f : 0.0f;
            }
            if (targetType == Short.class || targetType == short.class) {
                return (short) (boolValue ? 1 : 0);
            }
            if (targetType == Byte.class || targetType == byte.class) {
                return (byte) (boolValue ? 1 : 0);
            }
            if (targetType == BigInteger.class) {
                return boolValue ? BigInteger.ONE : BigInteger.ZERO;
            }
            return null;
        }

        return null;
    }

    private Object convertFromNumeric(double numValue, Class<?> targetType) {
        if (targetType == Double.class || targetType == double.class) {
            return numValue;
        }
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf((int) Math.round(numValue));
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(Math.round(numValue));
        }
        if (targetType == Float.class || targetType == float.class) {
            return (float) numValue;
        }
        if (targetType == Short.class || targetType == short.class) {
            return (short) numValue;
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return (byte) numValue;
        }
        if (targetType == BigInteger.class) {
            return BigInteger.valueOf(Math.round(numValue));
        }
        return numValue;
    }

    private Object convertFromString(String str, Class<?> targetType, ConvertContext context) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        try {
            if (targetType == Integer.class || targetType == int.class) {
                return Integer.valueOf(str);
            }
            if (targetType == Long.class || targetType == long.class) {
                return Long.valueOf(str);
            }
            if (targetType == Double.class || targetType == double.class) {
                return Double.valueOf(str);
            }
            if (targetType == Float.class || targetType == float.class) {
                return Float.valueOf(str);
            }
            if (targetType == Short.class || targetType == short.class) {
                return Short.valueOf(str);
            }
            if (targetType == Byte.class || targetType == byte.class) {
                return Byte.valueOf(str);
            }
            if (targetType == BigInteger.class) {
                return new BigInteger(str);
            }
        } catch (NumberFormatException e) {
            logConversionError(targetType.getSimpleName(), str, context);
            if (context.isStrictNumberConversion()) {
                throw new IllegalArgumentException(
                    String.format("Excel单元格值 '%s' 无法转换为%s类型，行号:%d，列名:%s",
                        str, targetType.getSimpleName(), context.getRowIndex(), context.getColumnName()), e);
            }
            return null;
        }
        return null;
    }

    private void logConversionError(String targetType, String value, ConvertContext context) {
        if (log.isDebugEnabled()) {
            log.debug("Excel单元格值 '{}' 无法转换为 {} 类型，行号:{}，列名:{}",
                value, targetType, context.getRowIndex(), context.getColumnName());
        }
    }

    @Override
    public int priority() {
        return 20;
    }
}
