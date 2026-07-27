package com.njydsz.common.util.date;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LocalDateTime 工具类。
 *
 * <p>提供全面的日期时间操作能力，基于 Java 8+ java.time API，所有方法 null 安全。
 *
 * <h2>核心能力</h2>
 * <ul>
 *   <li>格式化与解析：支持多种常用格式 + 自定义格式 + 中文日期格式</li>
 *   <li>时间计算与调整：加减天数/小时/分钟/秒、获取月初/月末/周一/周日</li>
 *   <li>时间比较与判断：是否同天/同月/同周、是否在时间区间内</li>
 *   <li>时间转换：LocalDateTime ↔ Date ↔ Instant ↔ 时间戳</li>
 *   <li>业务场景：工作日计算、季度获取、年龄计算、时效性判断</li>
 * </ul>
 *
 * <h2>线程安全</h2>
 * <p>{@link DateTimeFormatter} 是线程安全的，所有格式化器常量可直接在多线程环境中使用。
 * 自定义格式的解析通过 {@link ConcurrentHashMap} 缓存 Formatter 实例，避免重复创建。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class LocalDateTimeUtils {

    // ==================== 常用格式化字符串 ====================

    /** 标准日期时间格式：{@code yyyy-MM-dd HH:mm:ss} */
    public static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";
    /** 标准日期格式：{@code yyyy-MM-dd} */
    public static final String YYYY_MM_DD = "yyyy-MM-dd";
    /** 标准时间格式：{@code HH:mm:ss} */
    public static final String HH_MM_SS = "HH:mm:ss";
    /** 紧凑日期时间格式：{@code yyyyMMddHHmmss} */
    public static final String YYYYMMDDHHMMSS = "yyyyMMddHHmmss";
    /** 紧凑日期格式：{@code yyyyMMdd} */
    public static final String YYYYMMDD = "yyyyMMdd";
    /** 精确到分钟的格式：{@code yyyy-MM-dd HH:mm} */
    public static final String YYYY_MM_DD_HH_MM = "yyyy-MM-dd HH:mm";
    /** 精确到小时的格式：{@code yyyy-MM-dd HH} */
    public static final String YYYY_MM_DD_HH = "yyyy-MM-dd HH";
    /** 年月格式：{@code yyyy-MM} */
    public static final String YYYY_MM = "yyyy-MM";
    /** 月日格式：{@code MM-dd} */
    public static final String MM_DD = "MM-dd";
    /** 时分格式：{@code HH:mm} */
    public static final String HH_MM = "HH:mm";
    /** 中文日期格式：{@code yyyy 年 MM 月 dd 日} */
    public static final String CHINESE_DATE = "yyyy 年 MM 月 dd 日";
    /** 中文日期时间格式：{@code yyyy 年 MM 月 dd 日 HH 时 mm 分} */
    public static final String CHINESE_DATETIME = "yyyy 年 MM 月 dd 日 HH 时 mm 分";
    /** 中文完整日期时间格式：{@code yyyy 年 MM 月 dd 日 HH 时 mm 分 ss 秒} */
    public static final String CHINESE_FULL_DATETIME = "yyyy 年 MM 月 dd 日 HH 时 mm 分 ss 秒";

    // ==================== 线程安全的格式化器 ====================
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS);
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(YYYY_MM_DD);
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(HH_MM_SS);
    public static final DateTimeFormatter FULL_DATETIME_FORMATTER = DateTimeFormatter.ofPattern(YYYYMMDDHHMMSS);
    public static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern(YYYYMMDD);
    public static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM);
    public static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern(YYYY_MM_DD_HH);
    public static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern(YYYY_MM);
    public static final DateTimeFormatter CHINESE_DATE_FORMATTER = DateTimeFormatter.ofPattern(CHINESE_DATE);
    public static final DateTimeFormatter CHINESE_DATETIME_FORMATTER = DateTimeFormatter.ofPattern(CHINESE_DATETIME);
    public static final DateTimeFormatter CHINESE_FULL_DATETIME_FORMATTER = DateTimeFormatter.ofPattern(CHINESE_FULL_DATETIME);

    // ==================== 默认时区 ====================
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();

    private static final ConcurrentHashMap<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

    // ==================== 格式化与解析 ====================

    /**
     * LocalDateTime 转换为格式化字符串 (yyyy-MM-dd HH:mm:ss)
     *
     * @param dateTime 日期时间
     * @return 格式化后的字符串
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATETIME_FORMATTER);
    }

    /**
     * LocalDateTime 转换为自定义格式字符串
     *
     * @param dateTime 日期时间
     * @param pattern  格式
     * @return 格式化后的字符串
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return null;
        }
        DateTimeFormatter formatter = FORMATTER_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
        return dateTime.format(formatter);
    }

    /**
     * 使用预定义格式化器格式化日期
     *
     * @param dateTime  日期时间
     * @param formatter 格式化器
     * @return 格式化后的字符串
     */
    public static String format(LocalDateTime dateTime, DateTimeFormatter formatter) {
        return dateTime == null ? null : dateTime.format(formatter);
    }

    /**
     * 格式化字符串转 LocalDateTime (yyyy-MM-dd HH:mm:ss)
     *
     * @param dateTimeStr 日期时间字符串
     * @return LocalDateTime
     */
    public static LocalDateTime parse(String dateTimeStr) {
        return parse(dateTimeStr, DATETIME_FORMATTER);
    }

    /**
     * 格式化字符串转 LocalDateTime
     *
     * @param dateTimeStr 日期时间字符串
     * @param pattern     格式
     * @return LocalDateTime
     */
    public static LocalDateTime parse(String dateTimeStr, String pattern) {
        if (dateTimeStr == null) {
            return null;
        }
        DateTimeFormatter formatter = FORMATTER_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
        return LocalDateTime.parse(dateTimeStr, formatter);
    }

    /**
     * 使用预定义格式化器解析字符串
     *
     * @param dateTimeStr 日期时间字符串
     * @param formatter   格式化器
     * @return LocalDateTime
     */
    public static LocalDateTime parse(String dateTimeStr, DateTimeFormatter formatter) {
        return dateTimeStr == null ? null : LocalDateTime.parse(dateTimeStr, formatter);
    }

    /**
     * 安全解析，解析失败返回 null
     *
     * @param dateTimeStr 日期时间字符串
     * @param pattern     格式
     * @return LocalDateTime，解析失败返回 null
     */
    public static LocalDateTime parseSafely(String dateTimeStr, String pattern) {
        if (dateTimeStr == null) {
            return null;
        }
        try {
            DateTimeFormatter formatter = FORMATTER_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
            return LocalDateTime.parse(dateTimeStr, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 获取当前时间 ====================

    /**
     * 获取当前日期时间
     *
     * @return 当前日期时间
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(DEFAULT_ZONE_ID);
    }

    /**
     * 获取今天的开始时间 (00:00:00)
     *
     * @return 今天的开始时间
     */
    public static LocalDateTime todayStart() {
        return getDayStart(now());
    }

    /**
     * 获取今天的结束时间 (23:59:59.999999999)
     *
     * @return 今天的结束时间
     */
    public static LocalDateTime todayEnd() {
        return getDayEnd(now());
    }

    /**
     * 获取本周的开始时间（周一 00:00:00）
     *
     * @return 本周的开始时间
     */
    public static LocalDateTime weekStart() {
        return getWeekStart(now());
    }

    /**
     * 获取本周的结束时间（周日 23:59:59.999999999）
     *
     * @return 本周的结束时间
     */
    public static LocalDateTime weekEnd() {
        return getWeekEnd(now());
    }

    /**
     * 获取本月的开始时间
     *
     * @return 本月的开始时间
     */
    public static LocalDateTime monthStart() {
        return getFirstDayOfMonth(now());
    }

    /**
     * 获取本月的结束时间
     *
     * @return 本月的结束时间
     */
    public static LocalDateTime monthEnd() {
        return getLastDayOfMonth(now());
    }

    /**
     * 获取本年的开始时间
     *
     * @return 本年的开始时间
     */
    public static LocalDateTime yearStart() {
        return getFirstDayOfYear(now());
    }

    /**
     * 获取本年的结束时间
     *
     * @return 本年的结束时间
     */
    public static LocalDateTime yearEnd() {
        return getLastDayOfYear(now());
    }

    // ==================== 时间调整 ====================

    /**
     * 获取当天的开始时间 (00:00:00)
     *
     * @param dateTime 日期时间
     * @return 当天的开始时间
     */
    public static LocalDateTime getDayStart(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.with(LocalTime.MIN);
    }

    /**
     * 获取当天的结束时间 (23:59:59.999999999)
     *
     * @param dateTime 日期时间
     * @return 当天的结束时间
     */
    public static LocalDateTime getDayEnd(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.with(LocalTime.MAX);
    }

    /**
     * 获取本周的开始时间（周一）
     *
     * @param dateTime 日期时间
     * @return 本周的开始时间
     */
    public static LocalDateTime getWeekStart(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).with(LocalTime.MIN);
    }

    /**
     * 获取本周的结束时间（周日）
     *
     * @param dateTime 日期时间
     * @return 本周的结束时间
     */
    public static LocalDateTime getWeekEnd(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(LocalTime.MAX);
    }

    /**
     * 获取本月第一天
     *
     * @param dateTime 日期时间
     * @return 本月第一天
     */
    public static LocalDateTime getFirstDayOfMonth(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.with(TemporalAdjusters.firstDayOfMonth()).with(LocalTime.MIN);
    }

    /**
     * 获取本月最后一天
     *
     * @param dateTime 日期时间
     * @return 本月最后一天
     */
    public static LocalDateTime getLastDayOfMonth(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX);
    }

    /**
     * 获取本月第二天
     *
     * @param dateTime 日期时间
     * @return 本月第二天
     */
    public static LocalDateTime getSecondDayOfMonth(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return getFirstDayOfMonth(dateTime).plusDays(1);
    }

    /**
     * 获取下月第一天
     *
     * @param dateTime 日期时间
     * @return 下月第一天
     */
    public static LocalDateTime getFirstDayOfNextMonth(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.with(TemporalAdjusters.firstDayOfNextMonth()).with(LocalTime.MIN);
    }

    /**
     * 获取本年第一天
     *
     * @param dateTime 日期时间
     * @return 本年第一天
     */
    public static LocalDateTime getFirstDayOfYear(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.with(TemporalAdjusters.firstDayOfYear()).with(LocalTime.MIN);
    }

    /**
     * 获取本年最后一天
     *
     * @param dateTime 日期时间
     * @return 本年最后一天
     */
    public static LocalDateTime getLastDayOfYear(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.with(TemporalAdjusters.lastDayOfYear()).with(LocalTime.MAX);
    }

    /**
     * 获取下年第一天
     *
     * @param dateTime 日期时间
     * @return 下年第一天
     */
    public static LocalDateTime getFirstDayOfNextYear(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.with(TemporalAdjusters.firstDayOfNextYear()).with(LocalTime.MIN);
    }

    /**
     * 获取指定季度第一天
     *
     * @param dateTime 日期时间
     * @param quarter  季度 (1-4)
     * @return 季度第一天
     */
    public static LocalDateTime getFirstDayOfQuarter(LocalDateTime dateTime, int quarter) {
        if (dateTime == null || quarter < 1 || quarter > 4) {
            return null;
        }
        int month = (quarter - 1) * 3 + 1;
        return dateTime.withMonth(month).with(TemporalAdjusters.firstDayOfMonth()).with(LocalTime.MIN);
    }

    /**
     * 获取指定季度最后一天
     *
     * @param dateTime 日期时间
     * @param quarter  季度 (1-4)
     * @return 季度最后一天
     */
    public static LocalDateTime getLastDayOfQuarter(LocalDateTime dateTime, int quarter) {
        if (dateTime == null || quarter < 1 || quarter > 4) {
            return null;
        }
        int month = quarter * 3;
        return dateTime.withMonth(month).with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX);
    }

    /**
     * 获取当前季度第一天
     *
     * @param dateTime 日期时间
     * @return 当前季度第一天
     */
    public static LocalDateTime getCurrentQuarterStart(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        int quarter = getQuarter(dateTime);
        return getFirstDayOfQuarter(dateTime, quarter);
    }

    /**
     * 获取当前季度最后一天
     *
     * @param dateTime 日期时间
     * @return 当前季度最后一天
     */
    public static LocalDateTime getCurrentQuarterEnd(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        int quarter = getQuarter(dateTime);
        return getLastDayOfQuarter(dateTime, quarter);
    }

    // ==================== 时间计算 ====================

    /**
     * 增加年数
     *
     * @param dateTime 日期时间
     * @param years    年数（可为负数）
     * @return 计算后的日期时间
     */
    public static LocalDateTime plusYears(LocalDateTime dateTime, long years) {
        return dateTime == null ? null : dateTime.plusYears(years);
    }

    /**
     * 增加月数
     *
     * @param dateTime 日期时间
     * @param months   月数（可为负数）
     * @return 计算后的日期时间
     */
    public static LocalDateTime plusMonths(LocalDateTime dateTime, long months) {
        return dateTime == null ? null : dateTime.plusMonths(months);
    }

    /**
     * 增加周数
     *
     * @param dateTime 日期时间
     * @param weeks    周数（可为负数）
     * @return 计算后的日期时间
     */
    public static LocalDateTime plusWeeks(LocalDateTime dateTime, long weeks) {
        return dateTime == null ? null : dateTime.plusWeeks(weeks);
    }

    /**
     * 增加天数
     *
     * @param dateTime 日期时间
     * @param days     天数（可为负数）
     * @return 计算后的日期时间
     */
    public static LocalDateTime plusDays(LocalDateTime dateTime, long days) {
        return dateTime == null ? null : dateTime.plusDays(days);
    }

    /**
     * 增加小时数
     *
     * @param dateTime 日期时间
     * @param hours    小时数（可为负数）
     * @return 计算后的日期时间
     */
    public static LocalDateTime plusHours(LocalDateTime dateTime, long hours) {
        return dateTime == null ? null : dateTime.plusHours(hours);
    }

    /**
     * 增加分钟数
     *
     * @param dateTime 日期时间
     * @param minutes  分钟数（可为负数）
     * @return 计算后的日期时间
     */
    public static LocalDateTime plusMinutes(LocalDateTime dateTime, long minutes) {
        return dateTime == null ? null : dateTime.plusMinutes(minutes);
    }

    /**
     * 增加秒数
     *
     * @param dateTime 日期时间
     * @param seconds  秒数（可为负数）
     * @return 计算后的日期时间
     */
    public static LocalDateTime plusSeconds(LocalDateTime dateTime, long seconds) {
        return dateTime == null ? null : dateTime.plusSeconds(seconds);
    }

    /**
     * 减少年数
     *
     * @param dateTime 日期时间
     * @param years    年数
     * @return 计算后的日期时间
     */
    public static LocalDateTime minusYears(LocalDateTime dateTime, long years) {
        return dateTime == null ? null : dateTime.minusYears(years);
    }

    /**
     * 减少月数
     *
     * @param dateTime 日期时间
     * @param months   月数
     * @return 计算后的日期时间
     */
    public static LocalDateTime minusMonths(LocalDateTime dateTime, long months) {
        return dateTime == null ? null : dateTime.minusMonths(months);
    }

    /**
     * 减少天数
     *
     * @param dateTime 日期时间
     * @param days     天数
     * @return 计算后的日期时间
     */
    public static LocalDateTime minusDays(LocalDateTime dateTime, long days) {
        return dateTime == null ? null : dateTime.minusDays(days);
    }

    // ==================== 时间间隔计算 ====================

    /**
     * 计算两个日期之间相差的天数
     *
     * @param start 开始日期
     * @param end   结束日期
     * @return 相差的天数
     */
    public static long betweenDays(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? 0 : ChronoUnit.DAYS.between(start, end);
    }

    /**
     * 计算两个日期之间相差的秒数
     *
     * @param start 开始日期
     * @param end   结束日期
     * @return 相差的秒数
     */
    public static long betweenSeconds(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? 0 : ChronoUnit.SECONDS.between(start, end);
    }

    /**
     * 计算两个日期之间相差的分钟数
     *
     * @param start 开始日期
     * @param end   结束日期
     * @return 相差的分钟数
     */
    public static long betweenMinutes(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? 0 : ChronoUnit.MINUTES.between(start, end);
    }

    /**
     * 计算两个日期之间相差的小时数
     *
     * @param start 开始日期
     * @param end   结束日期
     * @return 相差的小时数
     */
    public static long betweenHours(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? 0 : ChronoUnit.HOURS.between(start, end);
    }

    /**
     * 计算两个日期之间相差的周数
     *
     * @param start 开始日期
     * @param end   结束日期
     * @return 相差的周数
     */
    public static long betweenWeeks(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? 0 : ChronoUnit.WEEKS.between(start, end);
    }

    /**
     * 计算两个日期之间相差的月数
     *
     * @param start 开始日期
     * @param end   结束日期
     * @return 相差的月数
     */
    public static long betweenMonths(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? 0 : ChronoUnit.MONTHS.between(start, end);
    }

    /**
     * 计算两个日期之间相差的年数
     *
     * @param start 开始日期
     * @param end   结束日期
     * @return 相差的年数
     */
    public static long betweenYears(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? 0 : ChronoUnit.YEARS.between(start, end);
    }

    /**
     * 计算两个日期之间相差的毫秒数
     *
     * @param start 开始日期
     * @param end   结束日期
     * @return 相差的毫秒数
     */
    public static long betweenMillis(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? 0 : ChronoUnit.MILLIS.between(start, end);
    }

    // ==================== 时间比较与判断 ====================

    /**
     * 判断是否是闰年
     *
     * @param dateTime 日期时间
     * @return 是否是闰年
     */
    public static boolean isLeapYear(LocalDateTime dateTime) {
        return dateTime != null && dateTime.toLocalDate().isLeapYear();
    }

    /**
     * 判断是否在指定日期之前
     *
     * @param dateTime  日期时间
     * @param otherDate 比较的日期
     * @return 是否在之前
     */
    public static boolean isBefore(LocalDateTime dateTime, LocalDateTime otherDate) {
        return dateTime != null && dateTime.isBefore(otherDate);
    }

    /**
     * 判断是否在指定日期之后
     *
     * @param dateTime  日期时间
     * @param otherDate 比较的日期
     * @return 是否在之后
     */
    public static boolean isAfter(LocalDateTime dateTime, LocalDateTime otherDate) {
        return dateTime != null && dateTime.isAfter(otherDate);
    }

    /**
     * 判断是否在两个日期之间（不包含边界）
     *
     * @param dateTime 日期时间
     * @param start    开始日期
     * @param end      结束日期
     * @return 是否在之间
     */
    public static boolean isBetween(LocalDateTime dateTime, LocalDateTime start, LocalDateTime end) {
        return dateTime != null && dateTime.isAfter(start) && dateTime.isBefore(end);
    }

    /**
     * 判断是否在两个日期之间（包含边界）
     *
     * @param dateTime 日期时间
     * @param start    开始日期
     * @param end      结束日期
     * @return 是否在之间
     */
    public static boolean isBetweenInclusive(LocalDateTime dateTime, LocalDateTime start, LocalDateTime end) {
        return dateTime != null && !dateTime.isBefore(start) && !dateTime.isAfter(end);
    }

    /**
     * 判断是否是今天
     *
     * @param dateTime 日期时间
     * @return 是否是今天
     */
    public static boolean isToday(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        LocalDate today = LocalDate.now(DEFAULT_ZONE_ID);
        return dateTime.toLocalDate().equals(today);
    }

    /**
     * 判断是否是昨天
     *
     * @param dateTime 日期时间
     * @return 是否是昨天
     */
    public static boolean isYesterday(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        LocalDate yesterday = LocalDate.now(DEFAULT_ZONE_ID).minusDays(1);
        return dateTime.toLocalDate().equals(yesterday);
    }

    /**
     * 判断是否是本周
     *
     * @param dateTime 日期时间
     * @return 是否是本周
     */
    public static boolean isThisWeek(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        LocalDateTime weekStart = getWeekStart(now());
        LocalDateTime weekEnd = getWeekEnd(now());
        return isBetweenInclusive(dateTime, weekStart, weekEnd);
    }

    /**
     * 判断是否是本月
     *
     * @param dateTime 日期时间
     * @return 是否是本月
     */
    public static boolean isThisMonth(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        LocalDateTime monthStart = getFirstDayOfMonth(now());
        LocalDateTime monthEnd = getLastDayOfMonth(now());
        return isBetweenInclusive(dateTime, monthStart, monthEnd);
    }

    /**
     * 判断是否是本年
     *
     * @param dateTime 日期时间
     * @return 是否是本年
     */
    public static boolean isThisYear(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        LocalDateTime yearStart = getFirstDayOfYear(now());
        LocalDateTime yearEnd = getLastDayOfYear(now());
        return isBetweenInclusive(dateTime, yearStart, yearEnd);
    }

    /**
     * 判断是否是周末
     *
     * @param dateTime 日期时间
     * @return 是否是周末
     */
    public static boolean isWeekend(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        DayOfWeek dayOfWeek = dateTime.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    /**
     * 判断是否是工作日
     *
     * @param dateTime 日期时间
     * @return 是否是工作日
     */
    public static boolean isWeekday(LocalDateTime dateTime) {
        return !isWeekend(dateTime);
    }

    // ==================== 获取时间信息 ====================

    /**
     * 获取年份
     *
     * @param dateTime 日期时间
     * @return 年份
     */
    public static int getYear(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.getYear();
    }

    /**
     * 获取月份（1-12）
     *
     * @param dateTime 日期时间
     * @return 月份
     */
    public static int getMonth(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.getMonthValue();
    }

    /**
     * 获取日（1-31）
     *
     * @param dateTime 日期时间
     * @return 日
     */
    public static int getDayOfMonth(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.getDayOfMonth();
    }

    /**
     * 获取小时（0-23）
     *
     * @param dateTime 日期时间
     * @return 小时
     */
    public static int getHour(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.getHour();
    }

    /**
     * 获取分钟（0-59）
     *
     * @param dateTime 日期时间
     * @return 分钟
     */
    public static int getMinute(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.getMinute();
    }

    /**
     * 获取秒（0-59）
     *
     * @param dateTime 日期时间
     * @return 秒
     */
    public static int getSecond(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.getSecond();
    }

    /**
     * 获取星期几（1-7，1 代表周一，7 代表周日）
     *
     * @param dateTime 日期时间
     * @return 星期几
     */
    public static int getDayOfWeek(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.getDayOfWeek().getValue();
    }

    /**
     * 获取季度（1-4）
     *
     * @param dateTime 日期时间
     * @return 季度
     */
    public static int getQuarter(LocalDateTime dateTime) {
        if (dateTime == null) {
            return -1;
        }
        int month = getMonth(dateTime);
        return (month - 1) / 3 + 1;
    }

    /**
     * 获取一年中的第几天（1-366）
     *
     * @param dateTime 日期时间
     * @return 一年中的第几天
     */
    public static int getDayOfYear(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.getDayOfYear();
    }

    /**
     * 获取一个月的第几周
     *
     * @param dateTime 日期时间
     * @return 一个月的第几周
     */
    public static int getWeekOfMonth(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.get(WeekFields.of(Locale.CHINA).weekOfMonth());
    }

    /**
     * 获取一年中的第几周
     *
     * @param dateTime 日期时间
     * @return 一年中的第几周
     */
    public static int getWeekOfYear(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.get(WeekFields.of(Locale.CHINA).weekOfWeekBasedYear());
    }

    // ==================== 类型转换 ====================

    /**
     * Date 转换为 LocalDateTime
     *
     * @param date 日期
     * @return LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : date.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDateTime();
    }

    /**
     * LocalDateTime 转换为 Date
     *
     * @param dateTime 日期时间
     * @return Date
     */
    public static Date toDate(LocalDateTime dateTime) {
        return dateTime == null ? null : Date.from(dateTime.atZone(DEFAULT_ZONE_ID).toInstant());
    }

    /**
     * LocalDate 转换为 LocalDateTime（当天开始时间）
     *
     * @param localDate 日期
     * @return LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(LocalDate localDate) {
        return localDate == null ? null : localDate.atStartOfDay();
    }

    /**
     * LocalDateTime 转换为 LocalDate
     *
     * @param dateTime 日期时间
     * @return LocalDate
     */
    public static LocalDate toLocalDate(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    /**
     * 获取指定日期的毫秒数（时间戳）
     *
     * @param dateTime 日期时间
     * @return 毫秒数
     */
    public static Long toEpochMilli(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(DEFAULT_ZONE_ID).toInstant().toEpochMilli();
    }

    /**
     * 时间戳转换为 LocalDateTime
     *
     * @param timestamp 时间戳（毫秒）
     * @return LocalDateTime
     */
    public static LocalDateTime fromEpochMilli(Long timestamp) {
        return timestamp == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), DEFAULT_ZONE_ID);
    }

    /**
     * LocalDateTime 转换为 Unix 时间戳（秒）
     *
     * @param dateTime 日期时间
     * @return Unix 时间戳（秒）
     */
    public static Long toEpochSecond(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(DEFAULT_ZONE_ID).toInstant().getEpochSecond();
    }

    /**
     * Unix 时间戳（秒）转换为 LocalDateTime
     *
     * @param timestamp Unix 时间戳（秒）
     * @return LocalDateTime
     */
    public static LocalDateTime fromEpochSecond(Long timestamp) {
        return timestamp == null ? null : LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), DEFAULT_ZONE_ID);
    }

    // ==================== 业务场景方法 ====================

    /**
     * 计算年龄
     *
     * @param birthDate 出生日期
     * @return 年龄
     */
    public static int calculateAge(LocalDateTime birthDate) {
        if (birthDate == null) {
            return -1;
        }
        LocalDate today = LocalDate.now(DEFAULT_ZONE_ID);
        LocalDate birth = birthDate.toLocalDate();
        int age = today.getYear() - birth.getYear();
        if (today.getMonthValue() < birth.getMonthValue() ||
                (today.getMonthValue() == birth.getMonthValue() && today.getDayOfMonth() < birth.getDayOfMonth())) {
            age--;
        }
        return age;
    }

    /**
     * 计算两个日期之间的工作日天数（排除周末）
     *
     * <p>使用 O(1) 算法：计算完整周数 × 5 + 剩余天数中的工作日。
     * 相比 O(n) 循环方式，大时间跨度时性能提升显著。</p>
     *
     * @param start 开始日期
     * @param end   结束日期
     * @return 工作日天数
     */
    public static long betweenWeekdays(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        if (start.isAfter(end)) {
            return 0;
        }

        LocalDate startDate = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        long totalWeeks = totalDays / 7;
        long remainingDays = totalDays % 7;

        long weekdays = totalWeeks * 5;

        DayOfWeek startDayOfWeek = startDate.getDayOfWeek();
        for (long i = 0; i < remainingDays; i++) {
            DayOfWeek currentDay = startDayOfWeek.plus((int) i);
            if (currentDay != DayOfWeek.SATURDAY && currentDay != DayOfWeek.SUNDAY) {
                weekdays++;
            }
        }

        return weekdays;
    }

    /**
     * 获取指定年份的总天数
     *
     * @param dateTime 日期时间
     * @return 年份的总天数
     */
    public static int getDaysOfYear(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.with(TemporalAdjusters.lastDayOfYear()).getDayOfYear();
    }

    /**
     * 获取指定月份的总天数
     *
     * @param dateTime 日期时间
     * @return 月份的总天数
     */
    public static int getDaysOfMonth(LocalDateTime dateTime) {
        return dateTime == null ? -1 : dateTime.with(TemporalAdjusters.lastDayOfMonth()).getDayOfMonth();
    }

    /**
     * 获取指定季度的月份数（固定为 3）
     *
     * @param dateTime 日期时间
     * @return 季度的月份数
     */
    public static int getMonthsOfQuarter(LocalDateTime dateTime) {
        return dateTime == null ? -1 : 3;
    }

    /**
     * 获取下一个工作日（跳过周末）
     *
     * @param dateTime 日期时间
     * @return 下一个工作日
     */
    public static LocalDateTime nextWeekday(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        LocalDateTime next = dateTime.plusDays(1);
        while (isWeekend(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * 获取上一个工作日（跳过周末）
     *
     * @param dateTime 日期时间
     * @return 上一个工作日
     */
    public static LocalDateTime previousWeekday(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        LocalDateTime previous = dateTime.minusDays(1);
        while (isWeekend(previous)) {
            previous = previous.minusDays(1);
        }
        return previous;
    }

    /**
     * 获取指定日期所在季度的所有月份
     *
     * @param dateTime 日期时间
     * @return 季度月份数组 [3, 4, 5]
     */
    public static int[] getQuarterMonths(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        int quarter = getQuarter(dateTime);
        return new int[]{(quarter - 1) * 3 + 1, (quarter - 1) * 3 + 2, quarter * 3};
    }

    /**
     * 判断两个日期是否在同一年
     *
     * @param dateTime1 日期 1
     * @param dateTime2 日期 2
     * @return 是否在同一年
     */
    public static boolean isSameYear(LocalDateTime dateTime1, LocalDateTime dateTime2) {
        if (dateTime1 == null || dateTime2 == null) {
            return false;
        }
        return dateTime1.getYear() == dateTime2.getYear();
    }

    /**
     * 判断两个日期是否在同一月
     *
     * @param dateTime1 日期 1
     * @param dateTime2 日期 2
     * @return 是否在同一月
     */
    public static boolean isSameMonth(LocalDateTime dateTime1, LocalDateTime dateTime2) {
        if (dateTime1 == null || dateTime2 == null) {
            return false;
        }
        return dateTime1.getYear() == dateTime2.getYear() &&
                dateTime1.getMonthValue() == dateTime2.getMonthValue();
    }

    /**
     * 判断两个日期是否在同一天
     *
     * @param dateTime1 日期 1
     * @param dateTime2 日期 2
     * @return 是否在同一天
     */
    public static boolean isSameDay(LocalDateTime dateTime1, LocalDateTime dateTime2) {
        if (dateTime1 == null || dateTime2 == null) {
            return false;
        }
        return dateTime1.toLocalDate().equals(dateTime2.toLocalDate());
    }

    /**
     * 判断两个日期是否在同一季度
     *
     * @param dateTime1 日期 1
     * @param dateTime2 日期 2
     * @return 是否在同一季度
     */
    public static boolean isSameQuarter(LocalDateTime dateTime1, LocalDateTime dateTime2) {
        if (dateTime1 == null || dateTime2 == null) {
            return false;
        }
        return isSameYear(dateTime1, dateTime2) && getQuarter(dateTime1) == getQuarter(dateTime2);
    }

    /**
     * 判断两个日期是否在同一周
     *
     * @param dateTime1 日期 1
     * @param dateTime2 日期 2
     * @return 是否在同一周
     */
    public static boolean isSameWeek(LocalDateTime dateTime1, LocalDateTime dateTime2) {
        if (dateTime1 == null || dateTime2 == null) {
            return false;
        }
        return isSameYear(dateTime1, dateTime2) &&
                getWeekOfYear(dateTime1) == getWeekOfYear(dateTime2);
    }

    /**
     * 获取相对时间描述（如：刚刚、5 分钟前、1 小时前、3 天前等）
     *
     * @param dateTime 日期时间
     * @return 相对时间描述
     */
    public static String getRelativeTimeDescription(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        long seconds = betweenSeconds(dateTime, now());
        if (seconds < 60) {
            return "刚刚";
        } else if (seconds < 3600) {
            return seconds / 60 + "分钟前";
        } else if (seconds < 86400) {
            return seconds / 3600 + "小时前";
        } else if (seconds < 86400 * 2) {
            return "昨天";
        } else if (seconds < 86400 * 3) {
            return "前天";
        } else if (seconds < 86400 * 7) {
            return seconds / 86400 + "天前";
        } else if (seconds < 86400 * 30) {
            return seconds / (86400 * 7) + "周前";
        } else if (seconds < 86400 * 365) {
            return seconds / (86400 * 30) + "个月前";
        } else {
            return seconds / (86400 * 365) + "年前";
        }
    }
}
