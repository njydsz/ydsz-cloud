package com.njydsz.common.excel.converter.impl;

import java.util.Date;
import com.njydsz.common.excel.converter.CellValueConverter;
import com.njydsz.common.excel.converter.ConvertContext;

/**
 * String类型转换器
 *
 * <p>处理目标类型为String的转换。支持从String、Double、Boolean、Date等原始值转换。
 * 当automaticTrim为true时，自动去除字符串首尾空格。</p>
 *
 * @author ydsz-team
 * @email ydsz-dev@ydszsoft.com
 * @version 1.0.0
 * @since 1.0.0
 */
public class StringConverter implements CellValueConverter {

    @Override
    public boolean supports(Class<?> targetType) {
        return targetType == String.class;
    }

    @Override
    public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof String) {
            String str = (String) rawValue;
            return context.isAutomaticTrim() ? str.trim() : str;
        }
        if (rawValue instanceof Double) {
            return String.valueOf(rawValue);
        }
        if (rawValue instanceof Boolean) {
            return String.valueOf(rawValue);
        }
        if (rawValue instanceof Date) {
            return rawValue.toString();
        }
        return rawValue.toString();
    }

    @Override
    public int priority() {
        return 10;
    }
}
