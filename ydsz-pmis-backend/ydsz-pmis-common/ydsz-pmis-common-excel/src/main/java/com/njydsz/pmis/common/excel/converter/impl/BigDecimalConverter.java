package com.njydsz.pmis.common.excel.converter.impl;

import com.njydsz.pmis.common.excel.converter.CellValueConverter;
import com.njydsz.pmis.common.excel.converter.ConvertContext;

import java.math.BigDecimal;

/**
 * BigDecimal类型转换器
 *
 * <p>处理目标类型为BigDecimal的转换。支持从String、Double等原始值转换。</p>
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
public class BigDecimalConverter implements CellValueConverter {

    @Override
    public boolean supports(Class<?> targetType) {
        return targetType == BigDecimal.class;
    }

    @Override
    public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof BigDecimal) {
            return rawValue;
        }

        if (rawValue instanceof Double) {
            return BigDecimal.valueOf((Double) rawValue);
        }

        if (rawValue instanceof Long) {
            return BigDecimal.valueOf((Long) rawValue);
        }

        if (rawValue instanceof String) {
            String str = (String) rawValue;
            if (str.isEmpty()) {
                return null;
            }
            return new BigDecimal(str);
        }

        return null;
    }

    @Override
    public int priority() {
        return 40;
    }
}
