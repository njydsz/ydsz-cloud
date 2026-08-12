package com.njydsz.common.util.string;

import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * 字符串工具类
 *
 * <p>提供项目高频使用的字符串处理方法，聚焦于 JDK 未覆盖的能力：
 * <ul>
 *   <li>对象判空：isEmpty / isNotEmpty / isBlank / isNotBlank / hasText（支持 CharSequence 与 Object 多类型）</li>
 *   <li>默认值：defaultIfBlank</li>
 *   <li>前缀匹配：startsWithIgnoreCase（忽略大小写）</li>
 *   <li>命名转换：toCamelCase / toUnderScoreCase</li>
 *   <li>格式化：format（使用 {} 占位符，类似 SLF4J 风格）</li>
 * </ul>
 *
 * <p><b>不提供的能力（直接使用 JDK）：</b>
 * <ul>
 *   <li>equals / contains / indexOf / startsWith / endsWith → {@link String}</li>
 *   <li>trim / strip / replace / split → {@link String}</li>
 *   <li>toUpperCase / toLowerCase / repeat / reverse → {@link String} / {@link StringBuilder}</li>
 *   <li>join → {@link java.util.stream.Collectors#joining(CharSequence)}</li>
 *   <li>正则匹配 → {@link java.util.regex.Pattern} / {@link java.util.regex.Matcher}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class StringUtils {

    private StringUtils() {
        throw new UnsupportedOperationException("StringUtils is a utility class and cannot be instantiated");
    }

    /** 空字符串常量 */
    public static final String EMPTY = "";

    /** 下划线字符常量 */
    private static final char SEPARATOR = '_';

    // ==================== DateTimeFormatter 常量池 ====================

    /**
     * 日期格式化器常量池。
     *
     * <p>{@link DateTimeFormatter} 是线程安全的，全局共享实例不会导致并发问题。
     * 统一的格式常量可避免业务代码各自声明导致格式不一致（如 {@code yyyy-MM-dd} vs {@code yyyy/MM/dd}）。
     *
     * <p>包含以下常用格式：
     * <ul>
     *   <li>{@link #LOCAL_DATE} — {@code yyyy-MM-dd} (ISO)</li>
     *   <li>{@link #LOCAL_TIME} — {@code HH:mm:ss} (ISO)</li>
     *   <li>{@link #LOCAL_TIME_WITH_MILLIS} — {@code HH:mm:ss.SSS}</li>
     *   <li>{@link #LOCAL_DATE_TIME} — {@code yyyy-MM-dd HH:mm:ss}（与数据库 DATETIME 兼容）</li>
     *   <li>{@link #LOCAL_DATE_TIME_WITH_MILLIS} — {@code yyyy-MM-dd HH:mm:ss.SSS}</li>
     *   <li>{@link #COMPACT_DATE} — {@code yyyyMMdd}（紧凑格式，用于文件名等）</li>
     *   <li>{@link #COMPACT_DATE_TIME} — {@code yyyyMMddHHmmss}（紧凑格式）</li>
     *   <li>{@link #ISO_LOCAL_DATE_TIME} — ISO-8601 格式（ LocalDateTime.parse 直接支持）</li>
     *   <li>{@link #SLASH_DATE} — {@code yyyy/MM/dd}（部分接口兼容）</li>
     * </ul>
     *
     * @since 2.2.0
     */
    public static final class DateFormats {

        private DateFormats() {
            throw new UnsupportedOperationException("DateFormats is a constant holder");
        }

        /** 日期格式：yyyy-MM-dd (ISO-8601) */
        public static final DateTimeFormatter LOCAL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        /** 时间格式：HH:mm:ss (ISO-8601) */
        public static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

        /** 时间格式（含毫秒）：HH:mm:ss.SSS */
        public static final DateTimeFormatter LOCAL_TIME_WITH_MILLIS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

        /** 日期时间格式：yyyy-MM-dd HH:mm:ss（兼容 MySQL DATETIME / TIMESTAMP） */
        public static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        /** 日期时间格式（含毫秒）：yyyy-MM-dd HH:mm:ss.SSS */
        public static final DateTimeFormatter LOCAL_DATE_TIME_WITH_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        /** 紧凑日期格式：yyyyMMdd（文件名、报表编号等场景） */
        public static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

        /** 紧凑日期时间格式：yyyyMMddHHmmss */
        public static final DateTimeFormatter COMPACT_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        /** ISO-8601 格式（含秒）：yyyy-MM-dd'T'HH:mm:ss */
        public static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        /** ISO-8601 完整格式（含毫秒）：yyyy-MM-dd'T'HH:mm:ss.SSS */
        public static final DateTimeFormatter ISO_LOCAL_DATE_TIME_WITH_MILLIS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        /** 斜线日期格式：yyyy/MM-dd（部分旧接口兼容） */
        public static final DateTimeFormatter SLASH_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

        /** 斜线日期时间格式：yyyy/MM/dd HH:mm:ss */
        public static final DateTimeFormatter SLASH_DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    }

    /**
     * 日期时间格式化工具方法（{@link java.time.LocalDateTime} → String）。
     *
     * <p>对业务代码中高频使用的 {@code formatter.format(ldt)} 做 null 安全包装。
     *
     * @param dateTime 待格式化的 LocalDateTime（可为 null）
     * @param formatter 格式化器（通常为 {@link DateFormats} 中的常量）
     * @return 格式化后的字符串；入参为 null 时返回 null
     * @since 2.2.0
     */
    public static String formatDateTime(java.time.LocalDateTime dateTime, DateTimeFormatter formatter) {
        if (dateTime == null) {
            return null;
        }
        return formatter != null ? formatter.format(dateTime) : dateTime.toString();
    }

    /**
     * 日期格式化工具方法（{@link java.time.LocalDate} → String）。
     *
     * @param date 待格式化的 LocalDate（可为 null）
     * @param formatter 格式化器
     * @return 格式化后的字符串；入参为 null 时返回 null
     * @since 2.2.0
     */
    public static String formatDate(java.time.LocalDate date, DateTimeFormatter formatter) {
        if (date == null) {
            return null;
        }
        return formatter != null ? formatter.format(date) : date.toString();
    }

    /**
     * 字符串解析为 {@link java.time.LocalDateTime}（null/空串安全）。
     *
     * @param text 待解析字符串（可为 null）
     * @param formatter 格式化器
     * @return 解析后的 LocalDateTime；入参为 null/空串时返回 null
     * @since 2.2.0
     */
    public static java.time.LocalDateTime parseDateTime(String text, DateTimeFormatter formatter) {
        if (isBlank(text)) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(text, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 字符串解析为 {@link java.time.LocalDate}（null/空串安全）。
     *
     * @param text 待解析字符串（可为 null）
     * @param formatter 格式化器
     * @return 解析后的 LocalDate；入参为 null/空串时返回 null
     * @since 2.2.0
     */
    public static java.time.LocalDate parseDate(String text, DateTimeFormatter formatter) {
        if (isBlank(text)) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(text, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 判空方法 ====================

    /**
     * 判断字符串是否为 null 或空字符串（""）
     */
    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    /**
     * 判断字符串是否不为 null 且不为空字符串
     */
    public static boolean isNotEmpty(CharSequence cs) {
        return !isEmpty(cs);
    }

    /**
     * 判断字符串是否为 null、空字符串或只包含空白字符
     */
    public static boolean isBlank(CharSequence cs) {
        if (cs == null || cs.length() == 0) {
            return true;
        }
        for (int i = 0; i < cs.length(); i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字符串是否不为 null、不为空字符串且包含非空白字符
     */
    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

    /**
     * 判断字符串是否包含实际文本内容（不为 null、不为空且包含非空白字符）
     * <p>hasText("hello") -> true</p>
     * <p>hasText("   ") -> false</p>
     * <p>hasText(null) -> false</p>
     */
    public static boolean hasText(CharSequence cs) {
        return isNotBlank(cs);
    }

    /**
     * 判断对象是否为空（支持 CharSequence / Collection / Map / Array / Iterator / Iterable）
     *
     * <p>注意：对 CharSequence 判断长度是否为 0（与 {@link #isEmpty(CharSequence)} 语义一致），
     * 不按空白字符判空。如需按空白判空请使用 {@link #isBlank(CharSequence)}。
     */
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof CharSequence) {
            return ((CharSequence) obj).length() == 0;
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).isEmpty();
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).isEmpty();
        }
        if (obj instanceof Object[]) {
            return ((Object[]) obj).length == 0;
        }
        if (obj instanceof Iterator) {
            return !((Iterator<?>) obj).hasNext();
        }
        if (obj instanceof Iterable) {
            return !((Iterable<?>) obj).iterator().hasNext();
        }
        return false;
    }

    /**
     * 判断对象是否不为空
     */
    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }

    // ==================== 默认值方法 ====================

    /**
     * 如果字符串为 null 或空白，返回默认值
     */
    public static String defaultIfBlank(CharSequence str, String defaultStr) {
        return isBlank(str) ? defaultStr : str.toString();
    }

    /**
     * 判断字符串是否以指定前缀开头（忽略大小写）
     *
     * @param str    待检查字符串
     * @param prefix 前缀
     * @return 如果 str 以 prefix 开头（忽略大小写）返回 true；str 或 prefix 为 null 返回 false
     */
    public static boolean startsWithIgnoreCase(String str, String prefix) {
        if (str == null || prefix == null) {
            return false;
        }
        return str.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * 从字符串开头移除指定的前缀。
     *
     * <p>如果字符串以指定前缀开头，则移除该前缀并返回剩余部分；
     * 如果字符串为 null 或不以前缀开头，则返回原字符串。
     *
     * <p>示例：
     * <pre>
     * removeStart("/path/to/file", "/")   -> "path/to/file"
     * removeStart("hello", "xyz")        -> "hello"
     * removeStart(null, "/")             -> null
     * </pre>
     *
     * @param str    待处理字符串（可为 null）
     * @param remove 要移除的前缀（可为 null）
     * @return 移除前缀后的字符串；如果输入为 null 或不以前缀开头，返回原字符串
     */
    public static String removeStart(String str, String remove) {
        if (str == null || remove == null || remove.isEmpty()) {
            return str;
        }
        if (str.startsWith(remove)) {
            return str.substring(remove.length());
        }
        return str;
    }

    // ==================== 命名转换方法 ====================

    /**
     * 下划线命名转驼峰命名。
     *
     * <p>规则：
     * <ul>
     *   <li>不含下划线的字符串（如已是驼峰的 {@code userName}）保持原样返回，避免误处理</li>
     *   <li>含下划线的字符串：下划线后的首字母大写，其余字母小写化，首字段首字母小写</li>
     * </ul>
     *
     * <p>示例：
     * <pre>
     * toCamelCase("user_name")   -> "userName"
     * toCamelCase("USER_NAME")   -> "userName"
     * toCamelCase("userName")    -> "userName"   （已驼峰，保持原样）
     * toCamelCase("User_Name")   -> "userName"
     * </pre>
     *
     * @param s 输入字符串
     * @return 驼峰命名字符串；null 返回 null
     */
    public static String toCamelCase(String s) {
        if (s == null) {
            return null;
        }
        if (s.indexOf(SEPARATOR) < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean upperNext = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == SEPARATOR) {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * 驼峰命名转下划线命名
     * <p>userName -> user_name</p>
     */
    public static String toUnderScoreCase(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean upperCase = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean nextUpperCase = true;
            if (i < (s.length() - 1)) {
                nextUpperCase = Character.isUpperCase(s.charAt(i + 1));
            }
            if ((i > 0) && Character.isUpperCase(c)) {
                if (!upperCase || !nextUpperCase) {
                    sb.append(SEPARATOR);
                }
                upperCase = true;
            } else {
                upperCase = false;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    // ==================== 字符串格式化方法 ====================

    /**
     * 格式化字符串（使用 {} 作为占位符）
     * <p>format("Hello, {}! You are {} years old.", "Alice", 25)</p>
     */
    public static String format(String pattern, Object... arguments) {
        if (pattern == null) {
            return null;
        }
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        int lastIndex = 0;
        while (lastIndex < pattern.length()) {
            int placeholderIndex = pattern.indexOf("{}", lastIndex);
            if (placeholderIndex == -1) {
                result.append(pattern, lastIndex, pattern.length());
                break;
            }
            result.append(pattern, lastIndex, placeholderIndex);
            if (argIndex < arguments.length) {
                result.append(arguments[argIndex] == null ? "null" : arguments[argIndex].toString());
                argIndex++;
            } else {
                result.append("{}");
            }
            lastIndex = placeholderIndex + 2;
        }
        return result.toString();
    }
}
