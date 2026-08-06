package com.remisoft.common.util.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

/**
 * 日期时间工具类。
 *
 * <p>基于 Java 8+ {@link java.time} API，提供日期解析、格式化、运算、比较等常用操作。
 * 零第三方依赖，纯 JDK 实现。
 *
 * <p><b>线程安全说明：</b>{@link DateTimeFormatter} 实例是线程安全的（不可变），
 * 本类暴露的所有预定义 formatter 可多线程并发使用。
 *
 * @author remi-team
 * @since 1.3.0
 * @deprecated 自 2.0.0 起废弃，推荐直接使用 JDK {@code java.time} API（v3.0 移除）：
 *             <ul>
 *               <li>解析：{@code LocalDate.parse("2024-01-15")} / {@code LocalDateTime.parse("2024-01-15 14:30:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}</li>
 *               <li>格式化：{@code date.format(DateTimeFormatter.ISO_LOCAL_DATE)} / {@code dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}</li>
 *               <li>运算：{@code dateTime.plusDays(1)} / {@code date.plusMonths(3)} / {@code dateTime.minusHours(2)}</li>
 *               <li>起止：{@code date.atStartOfDay()} / {@code date.atTime(LocalTime.MAX)}</li>
 *               <li>差值：{@code ChronoUnit.DAYS.between(start, end)} / {@code ChronoUnit.HOURS.between(start, end)}</li>
 *               <li>Date互转：{@code date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()} /
 *                            {@code Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant())}</li>
 *             </ul>
 *             JDK API 已足够强大，无需额外包装工具类。
 */
@Deprecated(since = "2.0.0", forRemoval = false)
public final class DateUtils {

    /** 私有构造器，工具类不允许实例化 */
    private DateUtils() {
        throw new UnsupportedOperationException("DateUtils is a utility class and cannot be instantiated");
    }

    // ==================== 常用格式化器 ====================

    /** 标准日期格式：yyyy-MM-DD */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 标准日期时间格式：yyyy-MM-DD HH:mm:ss */
    public static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 日期时间格式（含毫秒）：yyyy-MM-DD HH:mm:ss.SSS */
    public static final DateTimeFormatter DATETIME_MS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** 紧凑日期格式：yyyyMMdd */
    public static final DateTimeFormatter DATE_COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 紧凑日期时间格式：yyyyMMddHHmmss */
    public static final DateTimeFormatter DATETIME_COMPACT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** ISO-8601 格式 */
    public static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME;

    /** 默认时区（系统默认时区） */
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    // ==================== 解析 ====================

    /**
     * 将字符串解析为 {@link LocalDate}（默认格式 yyyy-MM-dd）。
     *
     * @param text 日期字符串，如 "2024-01-15"
     * @return 解析后的 LocalDate
     * @throws DateTimeParseException 格式不匹配时抛出
     */
    public static LocalDate parseDate(String text) {
        return parseDate(text, DATE);
    }

    /**
     * 将字符串解析为 {@link LocalDate}（自定义格式）。
     *
     * @param text      日期字符串
     * @param formatter 自定义格式器
     * @return 解析后的 LocalDate
     */
    public static LocalDate parseDate(String text, DateTimeFormatter formatter) {
        Objects.requireNonNull(text, "text cannot be null");
        Objects.requireNonNull(formatter, "formatter cannot be null");
        return LocalDate.parse(text, formatter);
    }

    /**
     * 将字符串解析为 {@link LocalDateTime}（默认格式 yyyy-MM-dd HH:mm:ss）。
     *
     * @param text 日期时间字符串，如 "2024-01-15 14:30:00"
     * @return 解析后的 LocalDateTime
     * @throws DateTimeParseException 格式不匹配时抛出
     */
    public static LocalDateTime parseDateTime(String text) {
        return parseDateTime(text, DATETIME);
    }

    /**
     * 将字符串解析为 {@link LocalDateTime}（自定义格式）。
     *
     * @param text      日期时间字符串
     * @param formatter 自定义格式器
     * @return 解析后的 LocalDateTime
     */
    public static LocalDateTime parseDateTime(String text, DateTimeFormatter formatter) {
        Objects.requireNonNull(text, "text cannot be null");
        Objects.requireNonNull(formatter, "formatter cannot be null");
        return LocalDateTime.parse(text, formatter);
    }

    /**
     * 将字符串解析为 {@link LocalTime}（格式 HH:mm:ss）。
     *
     * @param text 时间字符串，如 "14:30:00"
     * @return 解析后的 LocalTime
     */
    public static LocalTime parseTime(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        return LocalTime.parse(text);
    }

    /**
     * 尝试解析字符串为 LocalDateTime，解析失败时返回 null 而非抛异常。
     *
     * @param text 日期时间字符串（默认 yyyy-MM-dd HH:mm:ss 格式）
     * @return 解析结果的 LocalDateTime，解析失败返回 null
     */
    public static LocalDateTime parseDateTimeQuietly(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DATETIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ==================== 格式化 ====================

    /**
     * 将 {@link LocalDate} 格式化为字符串（默认 yyyy-MM-dd 格式）。
     *
     * @param date 日期对象
     * @return 格式化字符串，date 为 null 时返回 null
     */
    public static String format(LocalDate date) {
        return format(date, DATE);
    }

    /**
     * 将 {@link LocalDate} 格式化为字符串（自定义格式）。
     *
     * @param date      日期对象
     * @param formatter 自定义格式器
     * @return 格式化字符串
     */
    public static String format(LocalDate date, DateTimeFormatter formatter) {
        if (date == null) {
            return null;
        }
        return date.format(formatter);
    }

    /**
     * 将 {@link LocalDateTime} 格式化为字符串（默认 yyyy-MM-dd HH:mm:ss 格式）。
     *
     * @param dateTime 日期时间对象
     * @return 格式化字符串，dateTime 为 null 时返回 null
     */
    public static String format(LocalDateTime dateTime) {
        return format(dateTime, DATETIME);
    }

    /**
     * 将 {@link LocalDateTime} 格式化为字符串（自定义格式）。
     *
     * @param dateTime  日期时间对象
     * @param formatter 自定义格式器
     * @return 格式化字符串
     */
    public static String format(LocalDateTime dateTime, DateTimeFormatter formatter) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(formatter);
    }

    /**
     * 将 {@link LocalTime} 格式化为字符串（HH:mm:ss 格式）。
     *
     * @param time 时间对象
     * @return 格式化字符串
     */
    public static String format(LocalTime time) {
        if (time == null) {
            return null;
        }
        return time.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    // ==================== 日期运算 ====================

    /**
     * 返回指定日期加上指定天数后的 LocalDateTime。
     *
     * @param dateTime 起始日期时间
     * @param days     要增加的天数（可为负数）
     * @return 运算结果
     */
    public static LocalDateTime plusDays(LocalDateTime dateTime, long days) {
        Objects.requireNonNull(dateTime, "dateTime cannot be null");
        return dateTime.plusDays(days);
    }

    /**
     * 返回指定日期加上指定小时后的 LocalDateTime。
     *
     * @param dateTime 起始日期时间
     * @param hours    要增加的小时数（可为负数）
     * @return 运算结果
     */
    public static LocalDateTime plusHours(LocalDateTime dateTime, long hours) {
        Objects.requireNonNull(dateTime, "dateTime cannot be null");
        return dateTime.plusHours(hours);
    }

    /**
     * 返回指定日期加上指定分钟后的 LocalDateTime。
     *
     * @param dateTime 起始日期时间
     * @param minutes  要增加的分钟数（可为负数）
     * @return 运算结果
     */
    public static LocalDateTime plusMinutes(LocalDateTime dateTime, long minutes) {
        Objects.requireNonNull(dateTime, "dateTime cannot be null");
        return dateTime.plusMinutes(minutes);
    }

    /**
     * 返回指定日期加上指定月数后的 LocalDateTime。
     *
     * @param dateTime 起始日期时间
     * @param months   要增加的月数（可为负数）
     * @return 运算结果
     */
    public static LocalDateTime plusMonths(LocalDateTime dateTime, long months) {
        Objects.requireNonNull(dateTime, "dateTime cannot be null");
        return dateTime.plusMonths(months);
    }

    /**
     * 返回指定日期加上指定年数后的 LocalDateTime。
     *
     * @param dateTime 起始日期时间
     * @param years    要增加的年数（可为负数）
     * @return 运算结果
     */
    public static LocalDateTime plusYears(LocalDateTime dateTime, long years) {
        Objects.requireNonNull(dateTime, "dateTime cannot be null");
        return dateTime.plusYears(years);
    }

    /**
     * 返回指定 LocalDate 加上指定天数后的结果。
     *
     * @param date 起始日期
     * @param days 要增加的天数（可为负数）
     * @return 运算结果
     */
    public static LocalDate plusDays(LocalDate date, long days) {
        Objects.requireNonNull(date, "date cannot be null");
        return date.plusDays(days);
    }

    /**
     * 返回指定 LocalDate 加上指定月数后的结果。
     *
     * @param date   起始日期
     * @param months 要增加的月数（可为负数）
     * @return 运算结果
     */
    public static LocalDate plusMonths(LocalDate date, long months) {
        Objects.requireNonNull(date, "date cannot be null");
        return date.plusMonths(months);
    }

    // ==================== 起止时刻 ====================

    /**
     * 返回指定日期的起始时刻（00:00:00）。
     *
     * @param date 日期对象
     * @return 当日 00:00:00 的 LocalDateTime
     */
    public static LocalDateTime beginOfDay(LocalDate date) {
        Objects.requireNonNull(date, "date cannot be null");
        return date.atStartOfDay();
    }

    /**
     * 返回指定日期的结束时刻（23:59:59.999999999）。
     *
     * <p>注意：纳秒级精度，实际使用时如需毫秒精度请配合格式化截断。
     *
     * @param date 日期对象
     * @return 当日最后一纳秒的 LocalDateTime
     */
    public static LocalDateTime endOfDay(LocalDate date) {
        Objects.requireNonNull(date, "date cannot be null");
        return date.atTime(LocalTime.MAX);
    }

    /**
     * 返回指定日期当天的 00:00:00（基于系统默认时区）。
     *
     * @param dateTime 日期时间对象
     * @return 当日 00:00:00 的 LocalDateTime
     */
    public static LocalDateTime beginOfDay(LocalDateTime dateTime) {
        Objects.requireNonNull(dateTime, "dateTime cannot be null");
        return dateTime.toLocalDate().atStartOfDay();
    }

    /**
     * 返回指定日期当天的 23:59:59.999999999（基于系统默认时区）。
     *
     * @param dateTime 日期时间对象
     * @return 当日最后一纳秒的 LocalDateTime
     */
    public static LocalDateTime endOfDay(LocalDateTime dateTime) {
        Objects.requireNonNull(dateTime, "dateTime cannot be null");
        return dateTime.toLocalDate().atTime(LocalTime.MAX);
    }

    // ==================== 比较与差值 ====================

    /**
     * 计算两个日期之间的天数差（endDate - startDate）。
     *
     * <p>不足一天按整数截断。
     *
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @return 天数差（可为负数）
     */
    public static long daysBetween(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(endDate, "endDate cannot be null");
        return ChronoUnit.DAYS.between(startDate, endDate);
    }

    /**
     * 计算两个日期时间之间的小时差（end - start）。
     *
     * @param start 起始时间
     * @param end   结束时间
     * @return 小时差（可为负数）
     */
    public static long hoursBetween(LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(start, "start cannot be null");
        Objects.requireNonNull(end, "end cannot be null");
        return ChronoUnit.HOURS.between(start, end);
    }

    /**
     * 计算两个日期时间之间的分钟差（end - start）。
     *
     * @param start 起始时间
     * @param end   结束时间
     * @return 分钟差（可为负数）
     */
    public static long minutesBetween(LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(start, "start cannot be null");
        Objects.requireNonNull(end, "end cannot be null");
        return ChronoUnit.MINUTES.between(start, end);
    }

    /**
     * 计算两个日期时间之间的秒差（end - start）。
     *
     * @param start 起始时间
     * @param end   结束时间
     * @return 秒差（可为负数）
     */
    public static long secondsBetween(LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(start, "start cannot be null");
        Objects.requireNonNull(end, "end cannot be null");
        return ChronoUnit.SECONDS.between(start, end);
    }

    /**
     * 计算两个 Date 之间的时间差（毫秒，end - start）。
     *
     * @param start 起始时间
     * @param end   结束时间
     * @return 毫秒差（可为负数）
     */
    public static long millisBetween(Date start, Date end) {
        Objects.requireNonNull(start, "start cannot be null");
        Objects.requireNonNull(end, "end cannot be null");
        return end.getTime() - start.getTime();
    }

    /**
     * 判断指定日期是否为今天（基于系统默认时区）。
     *
     * @param date 日期对象
     * @return 是否为今天
     */
    public static boolean isToday(LocalDate date) {
        if (date == null) {
            return false;
        }
        return date.equals(LocalDate.now());
    }

    /**
     * 判断指定日期是否为过去日期（早于今天，不包含今天）。
     *
     * @param date 日期对象
     * @return 是否早于今天
     */
    public static boolean isBeforeToday(LocalDate date) {
        if (date == null) {
            return false;
        }
        return date.isBefore(LocalDate.now());
    }

    /**
     * 判断指定日期是否为未来日期（晚于今天，不包含今天）。
     *
     * @param date 日期对象
     * @return 是否晚于今天
     */
    public static boolean isAfterToday(LocalDate date) {
        if (date == null) {
            return false;
        }
        return date.isAfter(LocalDate.now());
    }

    /**
     * 判断第一个日期是否在第二个日期之前。
     *
     * @param d1 第一个日期
     * @param d2 第二个日期
     * @return d1 在 d2 之前返回 true
     */
    public static boolean isBefore(LocalDateTime d1, LocalDateTime d2) {
        Objects.requireNonNull(d1, "d1 cannot be null");
        Objects.requireNonNull(d2, "d2 cannot be null");
        return d1.isBefore(d2);
    }

    /**
     * 判断第一个日期是否在第二个日期之后。
     *
     * @param d1 第一个日期
     * @param d2 第二个日期
     * @return d1 在 d2 之后返回 true
     */
    public static boolean isAfter(LocalDateTime d1, LocalDateTime d2) {
        Objects.requireNonNull(d1, "d1 cannot be null");
        Objects.requireNonNull(d2, "d2 cannot be null");
        return d1.isAfter(d2);
    }

    /**
     * 判断两个日期是否为同一天。
     *
     * @param d1 第一个日期
     * @param d2 第二个日期
     * @return 同一天返回 true
     */
    public static boolean isSameDay(LocalDate d1, LocalDate d2) {
        if (d1 == null || d2 == null) {
            return false;
        }
        return d1.isEqual(d2);
    }

    // ==================== Date ↔ LocalDateTime 互转 ====================

    /**
     * 将 {@link java.util.Date} 转换为 {@link LocalDateTime}（基于系统默认时区）。
     *
     * @param date 旧式 Date 对象
     * @return LocalDateTime，date 为 null 时返回 null
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(DEFAULT_ZONE).toLocalDateTime();
    }

    /**
     * 将 {@link LocalDateTime} 转换为 {@link java.util.Date}（基于系统默认时区）。
     *
     * @param dateTime LocalDateTime 对象
     * @return Date，dateTime 为 null 时返回 null
     */
    public static Date toDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.atZone(DEFAULT_ZONE).toInstant());
    }

    /**
     * 将 {@link LocalDate} 转换为 {@link java.util.Date}（基于系统默认时区，时间为 00:00:00）。
     *
     * @param date LocalDate 对象
     * @return Date，date 为 null 时返回 null
     */
    public static Date toDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return Date.from(date.atStartOfDay(DEFAULT_ZONE).toInstant());
    }

    /**
     * 将 {@link java.util.Date} 转换为 {@link LocalDate}（基于系统默认时区）。
     *
     * @param date 旧式 Date 对象
     * @return LocalDate，date 为 null 时返回 null
     */
    public static LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(DEFAULT_ZONE).toLocalDate();
    }

    // ==================== 时间戳互转 ====================

    /**
     * 将毫秒时间戳转换为 LocalDateTime（基于系统默认时区）。
     *
     * @param epochMilli 毫秒时间戳
     * @return LocalDateTime
     */
    public static LocalDateTime ofEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), DEFAULT_ZONE);
    }

    /**
     * 将 LocalDateTime 转换为毫秒时间戳（基于系统默认时区）。
     *
     * @param dateTime LocalDateTime 对象
     * @return 毫秒时间戳，dateTime 为 null 时返回 0
     */
    public static long toEpochMilli(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0L;
        }
        return dateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
    }

    /**
     * 将 LocalDateTime 转换为秒时间戳（基于系统默认时区）。
     *
     * @param dateTime LocalDateTime 对象
     * @return 秒时间戳，dateTime 为 null 时返回 0
     */
    public static long toEpochSecond(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0L;
        }
        return dateTime.atZone(DEFAULT_ZONE).toInstant().getEpochSecond();
    }

    /**
     * 将秒时间戳转换为 LocalDateTime（基于系统默认时区）。
     *
     * @param epochSecond 秒时间戳
     * @return LocalDateTime
     */
    public static LocalDateTime ofEpochSecond(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), DEFAULT_ZONE);
    }

    // ==================== 相对日期便捷方法 ====================

    /**
     * 返回昨天的 LocalDate（基于系统默认时区）。
     *
     * @return 昨天
     */
    public static LocalDate yesterday() {
        return LocalDate.now().minusDays(1);
    }

    /**
     * 返回明天的 LocalDate（基于系统默认时区）。
     *
     * @return 明天
     */
    public static LocalDate tomorrow() {
        return LocalDate.now().plusDays(1);
    }

    /**
     * 返回当前 00:00:00 的 LocalDateTime（基于系统默认时区）。
     *
     * @return 今日凌晨
     */
    public static LocalDateTime todayBegin() {
        return LocalDate.now().atStartOfDay();
    }

    /**
     * 返回当前 23:59:59.999999999 的 LocalDateTime（基于系统默认时区）。
     *
     * @return 今日结束
     */
    public static LocalDateTime todayEnd() {
        return LocalDate.now().atTime(LocalTime.MAX);
    }

    /**
     * 获取当前时间戳毫秒数（同 {@link System#currentTimeMillis()}）。
     *
     * @return 当前毫秒时间戳
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 获取当前时间戳秒数。
     *
     * @return 当前秒时间戳
     */
    public static long currentTimeSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * 计算指定时间到当前时间的差值（毫秒，当前时间 - 指定时间）。
     *
     * @param time 指定时间
     * @return 毫秒差，time 为 null 时返回 0
     */
    public static long elapsedMillis(LocalDateTime time) {
        if (time == null) {
            return 0L;
        }
        return Duration.between(time, LocalDateTime.now()).toMillis();
    }

    // ==================== Duration 友好输出 ====================

    /**
     * 将毫秒数格式化为人类可读的时长字符串（如 "1d 2h 3m 4s"）。
     *
     * @param millis 毫秒数
     * @return 格式化字符串，如 "2h 30m"、"45s"、"0s"
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        Duration duration = Duration.ofMillis(millis);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("s");
        }
        return sb.toString().trim();
    }

    /**
     * 将两个 LocalDateTime 之间的差值格式化为友好字符串。
     *
     * @param start 起始时间
     * @param end   结束时间
     * @return 格式化字符串如 "2h 30m"
     */
    public static String formatBetween(LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(start, "start cannot be null");
        Objects.requireNonNull(end, "end cannot be null");
        return formatDuration(Duration.between(start, end).toMillis());
    }

    // ==================== ZonedDateTime 辅助 ====================

    /**
     * 将 LocalDateTime 转为 {@link ZonedDateTime}（基于系统默认时区）。
     *
     * @param dateTime LocalDateTime 对象
     * @return ZonedDateTime
     */
    public static ZonedDateTime atZone(LocalDateTime dateTime) {
        Objects.requireNonNull(dateTime, "dateTime cannot be null");
        return dateTime.atZone(DEFAULT_ZONE);
    }

    /**
     * 将 LocalDateTime 格式化为指定时区的 ZonedDateTime 字符串。
     *
     * @param dateTime LocalDateTime 对象
     * @param zoneId   目标时区，如 "Asia/Shanghai"
     * @return ISO-8601 格式字符串
     */
    public static String formatAtZone(LocalDateTime dateTime, String zoneId) {
        Objects.requireNonNull(dateTime, "dateTime cannot be null");
        Objects.requireNonNull(zoneId, "zoneId cannot be null");
        ZoneId zone = ZoneId.of(zoneId);
        return dateTime.atZone(zone).format(ISO_DATE_TIME);
    }
}
