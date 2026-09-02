package com.njydsz.common.excel.converter.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.converter.CellValueConverter;
import com.njydsz.common.excel.converter.ConvertContext;

/**
 * java.util.Date类型转换器
 *
 * <p>处理目标类型为java.util.Date的转换。支持从String、Double、Date等原始值转换。 从String转换时尝试多种日期格式解析。
 *
 * @author ydsz-team

 * @version 26.09.01
 * @since 26.09.01
 */
public class DateConverter implements CellValueConverter {

  private static final Logger LOG = LoggerFactory.getLogger(DateConverter.class);

  private static final Map<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

  private static final String[] DATE_PATTERNS = {
    "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd"
  };

  @Override
  public boolean supports(Class<?> targetType) {
    return targetType == Date.class;
  }

  @Override
  public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
    if (rawValue == null) {
      return null;
    }

    if (rawValue instanceof Date) {
      return rawValue;
    }

    if (rawValue instanceof Double) {
      return new Date(((Double) rawValue).longValue());
    }

    if (rawValue instanceof Long) {
      return new Date((Long) rawValue);
    }

    if (rawValue instanceof String) {
      return parseDateString((String) rawValue);
    }

    if (rawValue instanceof LocalDateTime) {
      return new Date(
          ((LocalDateTime) rawValue).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    return null;
  }

  private Date parseDateString(String dateStr) {
    if (dateStr == null || dateStr.isEmpty()) {
      return null;
    }
    for (String pattern : DATE_PATTERNS) {
      try {
        DateTimeFormatter formatter =
            FORMATTER_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
        LocalDateTime ldt = LocalDateTime.parse(dateStr, formatter);
        return new Date(ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
      } catch (Exception ignored) {
        LOG.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
    }
    return null;
  }

  @Override
  public int priority() {
    return 50;
  }
}
