package com.njydsz.message.server.template.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模板管道过滤器公共工具类。
 *
 * <p>提供模板变量渲染中的常用过滤器实现，供 {@code DefaultTemplateEngine} 和 {@code CachedTemplateEngine} 共享，
 * 避免重复代码。
 *
 * <p>支持的过滤器：
 *
 * <ul>
 *   <li>{@code date} — 日期格式化（{@link #formatDate}）
 *   <li>{@code number} — 数字格式化（{@link #formatNumber}）
 *   <li>{@code default} — 默认值（null 或空白字符串时返回指定默认值）
 *   <li>{@code upper} — 转大写
 *   <li>{@code lower} — 转小写
 *   <li>{@code truncate} — 截断到指定长度（{@link #truncate}）
 * </ul>
 *
 * <p>所有方法均为无状态纯函数，线程安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class TemplateFilterUtil {

  private static final Logger log = LoggerFactory.getLogger(TemplateFilterUtil.class);

  private TemplateFilterUtil() {
    // 工具类禁止实例化
  }

  /**
   * 应用单个管道过滤器。
   *
   * @param value 输入值
   * @param filterExpr 过滤器表达式（如 {@code date:yyyy-MM-dd} / {@code upper} / {@code truncate:50}）
   * @return 过滤后的值；未知过滤器返回原值
   */
  public static Object applyFilter(Object value, String filterExpr) {
    if (filterExpr == null || filterExpr.isEmpty()) {
      return value;
    }
    String[] fa = filterExpr.split(":", 2);
    String filterName = fa[0].trim().toLowerCase();
    String filterArg = fa.length > 1 ? fa[1] : "";
    return switch (filterName) {
      case "date" -> formatDate(value, filterArg);
      case "number" -> formatNumber(value, filterArg);
      case "default" ->
          value == null || (value instanceof String s && s.isBlank()) ? filterArg : value;
      case "upper" -> value == null ? null : String.valueOf(value).toUpperCase();
      case "lower" -> value == null ? null : String.valueOf(value).toLowerCase();
      case "truncate" -> truncate(value, filterArg);
      default -> value; // 未知过滤器不处理
    };
  }

  /**
   * 日期格式化过滤器。
   *
   * <p>支持输入类型：{@link LocalDateTime}、{@link LocalDate}、{@link Date}、{@link String}（ISO 格式）、{@link Long}（毫秒时间戳）。
   * 解析失败时降级返回原始值的字符串形式。
   *
   * @param value 输入值
   * @param pattern 日期格式模式（如 {@code yyyy-MM-dd HH:mm:ss}），为空时默认 {@code yyyy-MM-dd HH:mm:ss}
   * @return 格式化后的日期字符串；输入为 null 时返回空串
   */
  public static String formatDate(Object value, String pattern) {
    if (value == null) {
      return "";
    }
    String fmt = pattern.isEmpty() ? "yyyy-MM-dd HH:mm:ss" : pattern;
    try {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(fmt);
      if (value instanceof LocalDateTime ldt) {
        return ldt.format(formatter);
      }
      if (value instanceof LocalDate ld) {
        return ld.format(formatter);
      }
      if (value instanceof Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(formatter);
      }
      if (value instanceof String str) {
        // 尝试解析 ISO 格式
        return LocalDateTime.parse(str).format(formatter);
      }
      if (value instanceof Long ts) {
        return Instant.ofEpochMilli(ts)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(formatter);
      }
    } catch (Exception e) {
      log.debug("[TemplateFilter] 日期格式化降级为原始值, value={}, err={}", value, e.getMessage());
      return String.valueOf(value);
    }
    return String.valueOf(value);
  }

  /**
   * 数字格式化过滤器。
   *
   * <p>支持输入类型：{@link Number}、{@link String}（可解析为 {@link BigDecimal}）。
   * 解析失败时降级返回原始值的字符串形式。
   *
   * @param value 输入值
   * @param pattern 数字格式模式（如 {@code #,##0.00}），为空时默认 {@code #,##0.00}
   * @return 格式化后的数字字符串；输入为 null 时返回空串
   */
  public static String formatNumber(Object value, String pattern) {
    if (value == null) {
      return "";
    }
    String fmt = pattern.isEmpty() ? "#,##0.00" : pattern;
    try {
      DecimalFormat df = new DecimalFormat(fmt);
      df.setRoundingMode(RoundingMode.HALF_UP);
      if (value instanceof Number num) {
        return df.format(num);
      }
      if (value instanceof String str) {
        return df.format(new BigDecimal(str));
      }
    } catch (Exception e) {
      log.debug("[TemplateFilter] 数字格式化降级为原始值, value={}, err={}", value, e.getMessage());
      return String.valueOf(value);
    }
    return String.valueOf(value);
  }

  /**
   * 字符串截断过滤器。
   *
   * <p>当字符串长度超过 {@code maxLen} 时，截取前 {@code maxLen} 个字符并追加 {@code "..."}。
   *
   * @param value 输入值
   * @param lengthStr 最大长度（字符串形式，如 {@code "50"}）
   * @return 截断后的字符串；输入为 null 时返回空串；长度参数解析失败时返回原字符串
   */
  public static String truncate(Object value, String lengthStr) {
    if (value == null) {
      return "";
    }
    String str = String.valueOf(value);
    try {
      int maxLen = Integer.parseInt(lengthStr.trim());
      if (str.length() <= maxLen) {
        return str;
      }
      return str.substring(0, maxLen) + "...";
    } catch (NumberFormatException e) {
      return str;
    }
  }

  /**
   * truthy 判定：null→false / Boolean→自身 / String→非空白 / Number→非 0 / Collection→非空 / Map→非空 / 其他→true。
   *
   * @param value 值
   * @return 是否为真
   */
  public static boolean isTruthy(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof String s) {
      return !s.isBlank();
    }
    if (value instanceof Number n) {
      return n.doubleValue() != 0d;
    }
    if (value instanceof Collection<?> c) {
      return !c.isEmpty();
    }
    if (value instanceof Map<?, ?> mp) {
      return !mp.isEmpty();
    }
    return true;
  }

  /**
   * 解析占位符 key 对应的值，支持 {@code a.b.c} 形式嵌套 Map 取值。
   *
   * @param params 参数映射
   * @param key 占位符 key（如 {@code user.name} / {@code this} / {@code @index}）
   * @return 解析到的值，未命中返回 null
   */
  public static Object resolve(Map<String, Object> params, String key) {
    if (key.contains(".")) {
      String[] parts = key.split("\\.");
      Object cur = params;
      for (String p : parts) {
        if (cur instanceof Map<?, ?> map) {
          cur = map.get(p);
        } else {
          return null;
        }
      }
      return cur;
    }
    return params.get(key);
  }
}
