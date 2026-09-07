package com.njydsz.common.excel.converter.impl;

import java.time.Instant;
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

    if (rawValue instanceof Date d) {
      return d;
    }

    if (rawValue instanceof Double d) {
      return ldtToDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(d.longValue()), ZoneId.systemDefault()));
    }

    if (rawValue instanceof Long l) {
      return ldtToDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(l), ZoneId.systemDefault()));
    }

    if (rawValue instanceof String s) {
      return parseDateString(s);
    }

    if (rawValue instanceof LocalDateTime ldt) {
      return ldtToDate(ldt);
    }

    return null;
  }

  /**
   * 将 LocalDateTime 桥接为 Date（POI 写入/旧字段赋值兼容）。
   *
   * @param ldt 本地日期时间
   * @return 对应 Date 实例
   */
  private static Date ldtToDate(LocalDateTime ldt) {
    return ldt == null ? null : Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
  }

  /**
   * 将日期字符串解析为 Date。
   */
  private Date parseDateString(String dateStr) {
    if (dateStr == null || dateStr.isEmpty()) {
      return null;
    }
    for (String pattern : DATE_PATTERNS) {
      try {
        DateTimeFormatter formatter =
            FORMATTER_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
        LocalDateTime ldt = LocalDateTime.parse(dateStr, formatter);
        return ldtToDate(ldt);
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
