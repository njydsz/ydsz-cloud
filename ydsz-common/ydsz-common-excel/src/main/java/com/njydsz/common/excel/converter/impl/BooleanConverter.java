package com.njydsz.common.excel.converter.impl;

import com.njydsz.common.excel.converter.CellValueConverter;
import com.njydsz.common.excel.converter.ConvertContext;

/**
 * Boolean类型转换器
 *
 * <p>处理目标类型为Boolean的转换。支持从String、Double、Boolean等原始值转换。
 *
 * @author ydsz-team

 * @version 26.09.01
 * @since 26.09.01
 */
public class BooleanConverter implements CellValueConverter {

  @Override
  public boolean supports(Class<?> targetType) {
    return targetType == Boolean.class || targetType == boolean.class;
  }

  @Override
  public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
    if (rawValue == null) {
      return null;
    }

    if (rawValue instanceof Boolean) {
      return rawValue;
    }

    if (rawValue instanceof String) {
      return Boolean.valueOf((String) rawValue);
    }

    if (rawValue instanceof Double) {
      return ((Double) rawValue) != 0;
    }

    if (rawValue instanceof Long) {
      return ((Long) rawValue) != 0;
    }

    return null;
  }

  @Override
  public int priority() {
    return 30;
  }
}
