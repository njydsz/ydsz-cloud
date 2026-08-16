package com.njydsz.common.excel.converter.impl;

import com.njydsz.common.excel.converter.CellValueConverter;
import com.njydsz.common.excel.converter.ConvertContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LocalDate类型转换器
 *
 * <p>处理目标类型为LocalDate的转换。支持从String、Date等原始值转换。
 *
 * @author ydsz-team
 * @email ydsz-dev@ydszsoft.com
 * @version 1.0.0
 * @since 1.0.0
 */
public class LocalDateConverter implements CellValueConverter {

  private static final Logger log = LoggerFactory.getLogger(LocalDateConverter.class);

  private static final Map<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

  @Override
  public boolean supports(Class<?> targetType) {
    return targetType == LocalDate.class;
  }

  @Override
  public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
    if (rawValue == null) {
      return null;
    }

    if (rawValue instanceof LocalDate) {
      return rawValue;
    }

    if (rawValue instanceof Date) {
      return ((Date) rawValue).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    if (rawValue instanceof String) {
      return parseLocalDateString((String) rawValue, context);
    }

    return null;
  }

  private LocalDate parseLocalDateString(String dateStr, ConvertContext context) {
    if (dateStr == null || dateStr.isEmpty()) {
      return null;
    }

    String dateFormat = context.getDateFormat();
    if (dateFormat != null && !dateFormat.isEmpty()) {
      try {
        DateTimeFormatter formatter =
            FORMATTER_CACHE.computeIfAbsent(dateFormat, DateTimeFormatter::ofPattern);
        return LocalDate.parse(dateStr, formatter);
      } catch (Exception ignored) {
        log.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
    }

    try {
      return LocalDate.parse(dateStr);
    } catch (Exception e) {
      try {
        DateTimeFormatter formatter =
            FORMATTER_CACHE.computeIfAbsent("yyyy-MM-dd", DateTimeFormatter::ofPattern);
        return LocalDate.parse(dateStr, formatter);
      } catch (Exception e2) {
        return null;
      }
    }
  }

  @Override
  public int priority() {
    return 70;
  }
}
