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
 * LocalDateTime类型转换器
 *
 * <p>处理目标类型为LocalDateTime的转换。支持从String、Date、Double等原始值转换。
 *
 * @author ydsz-team

 * @version 1.0.0
 * @since 1.0.0
 */
public class LocalDateTimeConverter implements CellValueConverter {

  private static final Logger LOG = LoggerFactory.getLogger(LocalDateTimeConverter.class);

  private static final Map<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

  @Override
  public boolean supports(Class<?> targetType) {
    return targetType == LocalDateTime.class;
  }

  @Override
  public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
    if (rawValue == null) {
      return null;
    }

    if (rawValue instanceof LocalDateTime) {
      return rawValue;
    }

    if (rawValue instanceof Date) {
      return ((Date) rawValue).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    if (rawValue instanceof String) {
      return parseLocalDateTimeString((String) rawValue, context);
    }

    return null;
  }

  private LocalDateTime parseLocalDateTimeString(String dateStr, ConvertContext context) {
    if (dateStr == null || dateStr.isEmpty()) {
      return null;
    }

    String dateFormat = context.getDateFormat();
    if (dateFormat != null && !dateFormat.isEmpty()) {
      try {
        DateTimeFormatter formatter =
            FORMATTER_CACHE.computeIfAbsent(dateFormat, DateTimeFormatter::ofPattern);
        return LocalDateTime.parse(dateStr, formatter);
      } catch (Exception ignored) {
        LOG.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
    }

    try {
      return LocalDateTime.parse(dateStr);
    } catch (Exception e) {
      try {
        DateTimeFormatter formatter =
            FORMATTER_CACHE.computeIfAbsent("yyyy-MM-dd HH:mm:ss", DateTimeFormatter::ofPattern);
        return LocalDateTime.parse(dateStr, formatter);
      } catch (Exception e2) {
        return null;
      }
    }
  }

  @Override
  public int priority() {
    return 60;
  }
}
