package com.njydsz.common.excel.converter.impl;

import java.sql.Timestamp;
import java.util.Date;

import com.njydsz.common.excel.converter.CellValueConverter;
import com.njydsz.common.excel.converter.ConvertContext;

/**
 * java.sql.Timestamp类型转换器
 *
 * <p>处理目标类型为java.sql.Timestamp的转换。支持从Date、Double等原始值转换。</p>
 *
 * @author ydsz-team
 * @email ydsz-dev@njydsz.com
 * @version 1.0.0
 */
public class TimestampConverter implements CellValueConverter {

    @Override
    public boolean supports(Class<?> targetType) {
        return targetType == Timestamp.class;
    }

    @Override
    public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof Timestamp) {
            return rawValue;
        }

        if (rawValue instanceof Date) {
            return new Timestamp(((Date) rawValue).getTime());
        }

        if (rawValue instanceof Double) {
            return new Timestamp(((Double) rawValue).longValue());
        }

        if (rawValue instanceof Long) {
            return new Timestamp((Long) rawValue);
        }

        return null;
    }

    @Override
    public int priority() {
        return 100;
    }
}
