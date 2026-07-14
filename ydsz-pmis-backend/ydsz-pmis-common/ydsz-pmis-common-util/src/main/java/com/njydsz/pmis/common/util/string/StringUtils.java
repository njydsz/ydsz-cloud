package com.njydsz.pmis.common.util.string;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pattern 缓存 - 基于 ConcurrentHashMap 的线程安全缓存，用于缓存编译后的正则表达式
 *
 * <p>使用 ConcurrentHashMap 替代 synchronized LinkedHashMap，
 * 利用 computeIfAbsent 的原子性实现无锁并发读、细粒度并发写。
 * 采用简单的大小限制策略：超过上限时清空全量缓存（适用于 pattern 种类有限的场景）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
final class PatternCache {

    private static final int MAX_CACHE_SIZE = 256;

    private static final ConcurrentHashMap<String, Pattern> CACHE = new ConcurrentHashMap<>(MAX_CACHE_SIZE);

    private PatternCache() {
        throw new UnsupportedOperationException("PatternCache is a utility class and cannot be instantiated");
    }

    /**
     * 获取编译后的 Pattern，优先从缓存中获取
     *
     * @param regex 正则表达式
     * @return 编译后的 Pattern
     */
    static Pattern compile(String regex) {
        Pattern cached = CACHE.get(regex);
        if (cached != null) {
            return cached;
        }
        if (CACHE.size() >= MAX_CACHE_SIZE) {
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(regex, Pattern::compile);
    }
}

/**
 * 字符串工具类
 *
 * <p>提供全面的字符串处理方法，功能对标 Apache Commons Lang3 StringUtils 和 Spring StringUtils，
 * 并进行了增强和优化。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>对象判空：isNull、isNotNull、isEmpty、isNotEmpty、isBlank、isNotBlank、hasText、hasNoText</li>
 *   <li>默认值：defaultIfBlank、defaultIfEmpty、nullIfBlank、nullIfEmpty</li>
 *   <li>命名转换：toCamelCase（下划线转驼峰）、toUnderScoreCase（驼峰转下划线）</li>
 *   <li>字符串截取：substring、left、right、substringBefore、substringAfter 等</li>
 *   <li>字符串连接：join（集合/数组转字符串）</li>
 *   <li>字符串分割：split、splitByWhitespace</li>
 *   <li>字符串替换：replace、replaceFirst、replaceEach、replaceChars</li>
 *   <li>字符串清理：trim、strip、removeStart、removeEnd、compressWhitespace</li>
 *   <li>大小写转换：capitalize、uncapitalize、swapCase、toUpper、toLower</li>
 *   <li>字符串比较：equals、equalsIgnoreCase、compare、equalsAny</li>
 *   <li>字符串查找：contains、containsAny、indexOf、lastIndexOf</li>
 *   <li>字符串填充：padLeft、padRight</li>
 *   <li>字符串缩略：abbreviate</li>
 *   <li>字符串反转：reverse、reverseWords</li>
 *   <li>数字判断：isNumeric、isAlpha、isAlphaNumeric</li>
 *   <li>特殊判断：isWhitespace、isAllUpperCase、isAllLowerCase</li>
 *   <li>重复操作：repeat</li>
 *   <li>字符串提取：substringBetween、substringsBetween</li>
 *   <li>字符串计数：countMatches</li>
 *   <li>字符串格式化：format（使用 {} 占位符）</li>
 *   <li>编码转换：codePoints、getBytes、fromBytes</li>
 *   <li>正则表达式：isMatch、extract、replaceAllRegex</li>
 *   <li>字符串模板：renderTemplate</li>
 *   <li>敏感词过滤：maskSensitive</li>
 *   <li>字符串相似度：levenshteinDistance</li>
 * </ul>
 *
 * <p><b>迁移说明：</b>
 * <ul>
 *   <li>md5/sha1/sha256 已迁移至 {@code com.njydsz.pmis.common.util.digest.DigestUtils}</li>
 *   <li>base64Encode/base64Decode 已迁移至 {@code com.njydsz.pmis.common.util.codec.Base64Utils}</li>
 *   <li>urlEncode/urlDecode 已迁移至 {@code com.njydsz.pmis.common.util.net.ServletUtils}</li>
 *   <li>escapeHtml/unescapeHtml 已迁移至 {@code com.njydsz.pmis.common.safe.xss.EscapeUtils}</li>
 *   <li>getLocalhostIp 已迁移至 {@code com.njydsz.pmis.common.util.net.NetUtils}</li>
 * </ul>
 *
 * <p><b>相比 Apache/Spring 的增强：</b>
 * <ul>
 *   <li>支持更多类型的 isEmpty 判断（Collection、Map、Array、Iterator、Iterable）</li>
 *   <li>substring 支持负索引</li>
 *   <li>提供 format 方法，使用 {} 占位符</li>
 *   <li>提供 UTF-8 编码的 getBytes/fromBytes 方法</li>
 *   <li>所有方法 null 安全处理</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class StringUtils {

    private StringUtils() {
        throw new UnsupportedOperationException("StringUtils is a utility class and cannot be instantiated");
    }

    /**
     * 空字符串常量
     */
    public static final String EMPTY = "";

    /**
     * 下划线字符常量
     */
    private static final char SEPARATOR = '_';

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?|ftp)://[^\\s/$.?#].[^\\s]*$");
    private static final Pattern IPV4_PATTERN = Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$");

    // ==================== 判空方法 ====================

    /**
     * 判断对象是否为 null
     */
    public static boolean isNull(Object o) {
        return o == null;
    }

    /**
     * 判断对象是否不为 null
     */
    public static boolean isNotNull(Object o) {
        return o != null;
    }

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
     * 判断字符串是否不包含实际文本内容（为 null、空或只包含空白字符）
     * <p>hasNoText("hello") -> false</p>
     * <p>hasNoText("   ") -> true</p>
     * <p>hasNoText(null) -> true</p>
     */
    public static boolean hasNoText(CharSequence cs) {
        return isBlank(cs);
    }

    /**
     * 判断对象是否为空（支持多种类型）
     */
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof CharSequence) {
            return isBlank((CharSequence) obj);
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
     * 如果字符串为 null 或空，返回默认值
     */
    public static String defaultIfEmpty(CharSequence str, String defaultStr) {
        return isEmpty(str) ? defaultStr : str.toString();
    }

    // ==================== 命名转换方法 ====================

    /**
     * 下划线命名转驼峰命名
     * <p>user_name -> userName</p>
     */
    public static String toCamelCase(String s) {
        if (s == null) {
            return null;
        }
        s = s.toLowerCase();
        StringBuilder sb = new StringBuilder(s.length());
        boolean upperCase = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == SEPARATOR) {
                upperCase = true;
            } else if (upperCase) {
                sb.append(Character.toUpperCase(c));
                upperCase = false;
            } else {
                sb.append(c);
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

    // ==================== 字符串截取方法 ====================

    /**
     * 安全截取字符串（支持负索引）
     * <p>substring("hello", 1, -1) -> "ell"</p>
     */
    public static String substring(String str, int start, int end) {
        if (str == null) {
            return EMPTY;
        }
        if (end < 0) {
            end = str.length() + end;
        }
        if (start < 0) {
            start = str.length() + start;
        }
        if (end > str.length()) {
            end = str.length();
        }
        if (start > end) {
            return EMPTY;
        }
        return str.substring(start, end);
    }

    /**
     * 从指定位置截取到末尾
     */
    public static String substring(String str, int start) {
        if (str == null) {
            return EMPTY;
        }
        if (start < 0) {
            start = str.length() + start;
        }
        if (start > str.length()) {
            return EMPTY;
        }
        return str.substring(start);
    }

    /**
     * 截取左侧指定长度的字符串
     */
    public static String left(String str, int len) {
        if (str == null || len <= 0) {
            return EMPTY;
        }
        if (len >= str.length()) {
            return str;
        }
        return str.substring(0, len);
    }

    /**
     * 截取右侧指定长度的字符串
     */
    public static String right(String str, int len) {
        if (str == null || len <= 0) {
            return EMPTY;
        }
        if (len >= str.length()) {
            return str;
        }
        return str.substring(str.length() - len);
    }

    // ==================== 字符串连接方法 ====================

    /**
     * 使用指定分隔符连接集合元素
     */
    public static String join(Collection<?> collection, String separator) {
        if (collection == null || collection.isEmpty()) {
            return EMPTY;
        }
        return collection.stream()
                .map(Objects::toString)
                .collect(Collectors.joining(separator));
    }

    /**
     * 使用指定分隔符连接数组元素
     */
    public static String join(Object[] array, String separator) {
        if (array == null || array.length == 0) {
            return EMPTY;
        }
        return Arrays.stream(array)
                .map(Objects::toString)
                .collect(Collectors.joining(separator));
    }

    /**
     * 连接字符串（无分隔符）
     */
    public static String join(Object... elements) {
        return join(Arrays.asList(elements), EMPTY);
    }

    // ==================== 字符串分割方法 ====================

    /**
     * 按指定分隔符分割字符串
     */
    public static String[] split(String str, String separator) {
        if (str == null || str.isEmpty()) {
            return new String[0];
        }
        if (separator == null || separator.isEmpty()) {
            return new String[]{str};
        }
        return str.split(Pattern.quote(separator));
    }

    /**
     * 按空白字符分割字符串
     */
    public static String[] splitByWhitespace(String str) {
        if (str == null || str.isEmpty()) {
            return new String[0];
        }
        return str.trim().split("\\s+");
    }

    // ==================== 字符串替换方法 ====================

    /**
     * 替换字符串中所有的目标子串
     */
    public static String replace(String text, String searchString, String replacement) {
        if (text == null || text.isEmpty() || searchString == null || searchString.isEmpty() || replacement == null) {
            return text;
        }
        return text.replace(searchString, replacement);
    }

    /**
     * 替换字符串中第一个匹配的目标子串
     */
    public static String replaceFirst(String text, String searchString, String replacement) {
        if (text == null || text.isEmpty() || searchString == null || searchString.isEmpty() || replacement == null) {
            return text;
        }
        int idx = text.indexOf(searchString);
        if (idx == -1) {
            return text;
        }
        return text.substring(0, idx) + replacement + text.substring(idx + searchString.length());
    }

    /**
     * 替换多个字符
     */
    public static String replaceChars(String str, String searchChars, String replaceChars) {
        if (str == null || str.isEmpty() || searchChars == null || searchChars.isEmpty()) {
            return str;
        }
        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            int idx = searchChars.indexOf(chars[i]);
            if (idx >= 0 && replaceChars != null && idx < replaceChars.length()) {
                chars[i] = replaceChars.charAt(idx);
            }
        }
        return new String(chars);
    }

    // ==================== 字符串清理方法 ====================

    /**
     * 去除字符串两端的空白字符
     */
    public static String trim(String str) {
        return str == null ? null : str.trim();
    }

    /**
     * 去除字符串两端的空白字符，null 返回空字符串
     */
    public static String trimToEmpty(String str) {
        return str == null ? EMPTY : str.trim();
    }

    /**
     * 去除字符串两端的空白字符，null 返回 null
     */
    public static String strip(String str) {
        return trim(str);
    }

    /**
     * 去除字符串开头的指定字符
     */
    public static String removeStart(String str, String remove) {
        if (str == null || str.isEmpty() || remove == null || remove.isEmpty()) {
            return str;
        }
        if (str.startsWith(remove)) {
            return str.substring(remove.length());
        }
        return str;
    }

    /**
     * 去除字符串末尾的指定字符
     */
    public static String removeEnd(String str, String remove) {
        if (str == null || str.isEmpty() || remove == null || remove.isEmpty()) {
            return str;
        }
        if (str.endsWith(remove)) {
            return str.substring(0, str.length() - remove.length());
        }
        return str;
    }

    /**
     * 移除字符串中的所有空格
     */
    public static String deleteWhitespace(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.replaceAll("\\s+", EMPTY);
    }

    // ==================== 大小写转换方法 ====================

    /**
     * 首字母大写
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * 首字母小写
     */
    public static String uncapitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * 大小写互换
     */
    public static String swapCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (Character.isUpperCase(chars[i])) {
                chars[i] = Character.toLowerCase(chars[i]);
            } else if (Character.isLowerCase(chars[i])) {
                chars[i] = Character.toUpperCase(chars[i]);
            }
        }
        return new String(chars);
    }

    /**
     * 转大写
     */
    public static String toUpper(String str) {
        return str == null ? null : str.toUpperCase();
    }

    /**
     * 转小写
     */
    public static String toLower(String str) {
        return str == null ? null : str.toLowerCase();
    }

    /**
     * 转小写（别名方法，兼容 Apache Commons Lang3）
     */
    public static String lowerCase(String str) {
        return toLower(str);
    }

    /**
     * 转大写（别名方法，兼容 Apache Commons Lang3）
     */
    public static String upperCase(String str) {
        return toUpper(str);
    }

    // ==================== 字符串比较方法 ====================

    /**
     * 比较两个字符串是否相等（支持 null）
     */
    public static boolean equals(CharSequence cs1, CharSequence cs2) {
        if (cs1 == cs2) {
            return true;
        }
        if (cs1 == null || cs2 == null) {
            return false;
        }
        return cs1.toString().equals(cs2.toString());
    }

    /**
     * 比较两个字符串是否相等（忽略大小写，支持 null）
     */
    public static boolean equalsIgnoreCase(CharSequence cs1, CharSequence cs2) {
        if (cs1 == cs2) {
            return true;
        }
        if (cs1 == null || cs2 == null) {
            return false;
        }
        return cs1.toString().equalsIgnoreCase(cs2.toString());
    }

    /**
     * 比较两个字符串的字典序
     */
    public static int compare(String str1, String str2) {
        if (str1 == str2) {
            return 0;
        }
        if (str1 == null) {
            return -1;
        }
        if (str2 == null) {
            return 1;
        }
        return str1.compareTo(str2);
    }

    // ==================== 字符串查找方法 ====================

    /**
     * 判断是否包含子串
     */
    public static boolean contains(CharSequence seq, CharSequence searchSeq) {
        if (seq == null || searchSeq == null) {
            return false;
        }
        return seq.toString().contains(searchSeq);
    }

    /**
     * 判断是否包含任意一个目标子串
     */
    public static boolean containsAny(String str, String... searchStrings) {
        if (str == null || searchStrings == null || searchStrings.length == 0) {
            return false;
        }
        for (String searchStr : searchStrings) {
            if (searchStr != null && str.contains(searchStr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查找子串第一次出现的位置
     */
    public static int indexOf(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return -1;
        }
        return str.indexOf(searchStr);
    }

    /**
     * 查找子串最后一次出现的位置
     */
    public static int lastIndexOf(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return -1;
        }
        return str.lastIndexOf(searchStr);
    }

    /**
     * 判断是否以指定前缀开头
     */
    public static boolean startsWith(String str, String prefix) {
        if (str == null || prefix == null) {
            return false;
        }
        return str.startsWith(prefix);
    }

    /**
     * 判断是否以指定前缀开头（忽略大小写）
     */
    public static boolean startsWithIgnoreCase(String str, String prefix) {
        if (str == null || prefix == null) {
            return false;
        }
        if (prefix.isEmpty()) {
            return true;
        }
        if (str.length() < prefix.length()) {
            return false;
        }
        return str.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * 判断是否以指定后缀结尾
     */
    public static boolean endsWith(String str, String suffix) {
        if (str == null || suffix == null) {
            return false;
        }
        return str.endsWith(suffix);
    }

    /**
     * 判断是否以指定后缀结尾（忽略大小写）
     */
    public static boolean endsWithIgnoreCase(String str, String suffix) {
        if (str == null || suffix == null) {
            return false;
        }
        if (suffix.isEmpty()) {
            return true;
        }
        if (str.length() < suffix.length()) {
            return false;
        }
        return str.regionMatches(true, str.length() - suffix.length(), suffix, 0, suffix.length());
    }

    // ==================== 数字和字母判断方法 ====================

    /**
     * 判断是否为数字字符串
     */
    public static boolean isNumeric(CharSequence cs) {
        if (cs == null || cs.length() == 0) {
            return false;
        }
        for (int i = 0; i < cs.length(); i++) {
            if (!Character.isDigit(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是否为纯字母字符串
     */
    public static boolean isAlpha(CharSequence cs) {
        if (cs == null || cs.length() == 0) {
            return false;
        }
        for (int i = 0; i < cs.length(); i++) {
            if (!Character.isLetter(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是否为字母或数字字符串
     */
    public static boolean isAlphaNumeric(CharSequence cs) {
        if (cs == null || cs.length() == 0) {
            return false;
        }
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if (!Character.isLetter(c) && !Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    // ==================== 重复操作方法 ====================

    /**
     * 重复字符串指定次数
     */
    public static String repeat(String str, int repeat) {
        if (str == null || repeat <= 0) {
            return EMPTY;
        }
        return str.repeat(repeat);
    }

    // ==================== 编码转换方法 ====================

    /**
     * 将字符串转换为 code point 数组
     */
    public static int[] toCodePoints(String str) {
        if (str == null) {
            return new int[0];
        }
        return str.codePoints().toArray();
    }

    // ==================== 字符串填充和对齐方法 ====================

    /**
     * 左侧填充字符
     * <p>padLeft("42", 5, '0") -> "00042"</p>
     */
    public static String padLeft(String str, int size, char padChar) {
        if (str == null) {
            return null;
        }
        if (size <= str.length()) {
            return str;
        }
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size - str.length(); i++) {
            sb.append(padChar);
        }
        sb.append(str);
        return sb.toString();
    }

    /**
     * 右侧填充字符
     * <p>padRight("hello", 10, ' ') -> "hello     "</p>
     */
    public static String padRight(String str, int size, char padChar) {
        if (str == null) {
            return null;
        }
        if (size <= str.length()) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        for (int i = 0; i < size - str.length(); i++) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    /**
     * 左侧填充空格
     */
    public static String padLeft(String str, int size) {
        return padLeft(str, size, ' ');
    }

    /**
     * 右侧填充空格
     */
    public static String padRight(String str, int size) {
        return padRight(str, size, ' ');
    }

    // ==================== 字符串缩略方法 ====================

    /**
     * 缩略字符串到指定长度，超出部分用省略号代替
     * <p>abbreviate("abcdefg", 5) -> "ab..."</p>
     */
    public static String abbreviate(String str, int maxWidth) {
        return abbreviate(str, 0, maxWidth);
    }

    /**
     * 缩略字符串到指定范围
     * <p>abbreviate("abcdefg", 2, 5) -> "...ef..."</p>
     */
    public static String abbreviate(String str, int offset, int maxWidth) {
        if (str == null) {
            return null;
        }
        if (maxWidth <= 0) {
            return EMPTY;
        }
        if (str.length() <= maxWidth) {
            return str;
        }
        if (maxWidth <= 3) {
            return "...";
        }
        if (offset < 0) {
            offset = 0;
        }
        if (offset >= str.length()) {
            return "...";
        }
        if (str.length() - offset <= maxWidth - 3) {
            return "..." + str.substring(str.length() - (maxWidth - 3));
        }
        return "..." + str.substring(offset, offset + maxWidth - 3) + "...";
    }

    // ==================== 字符串反转方法 ====================

    /**
     * 反转字符串
     */
    public static String reverse(String str) {
        if (str == null) {
            return null;
        }
        return new StringBuilder(str).reverse().toString();
    }

    /**
     * 反转字符串中的单词顺序
     * <p>reverseWords("hello world") -> "world hello"</p>
     */
    public static String reverseWords(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        String[] words = str.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = words.length - 1; i >= 0; i--) {
            sb.append(words[i]);
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    // ==================== 字符串提取方法 ====================

    /**
     * 提取两个分隔符之间的字符串
     * <p>substringBetween("tag:hello:end", "tag:", ":end") -> "hello"</p>
     */
    public static String substringBetween(String str, String open, String close) {
        if (str == null || open == null || close == null) {
            return null;
        }
        int start = str.indexOf(open);
        if (start == -1) {
            return null;
        }
        int end = str.indexOf(close, start + open.length());
        if (end == -1) {
            return null;
        }
        return str.substring(start + open.length(), end);
    }

    /**
     * 提取所有两个分隔符之间的字符串
     */
    public static String[] substringsBetween(String str, String open, String close) {
        if (str == null || str.isEmpty() || open == null || close == null) {
            return new String[0];
        }
        List<String> results = new ArrayList<>();
        int pos = 0;
        while (pos < str.length()) {
            int start = str.indexOf(open, pos);
            if (start == -1) {
                break;
            }
            int end = str.indexOf(close, start + open.length());
            if (end == -1) {
                break;
            }
            results.add(str.substring(start + open.length(), end));
            pos = end + close.length();
        }
        return results.toArray(new String[0]);
    }

    // ==================== 字符串连接增强方法 ====================

    /**
     * 使用默认分隔符连接字符串数组
     */
    public static String joinArray(Object[] array) {
        return join(array, ",");
    }

    /**
     * 使用默认分隔符连接集合
     */
    public static String joinCollection(Collection<?> collection) {
        return join(collection, ",");
    }

    // ==================== 字符串删除方法 ====================

    /**
     * 删除字符串中的所有空格
     */
    public static String deleteSpaces(String str) {
        return str == null ? null : str.replace(" ", EMPTY);
    }

    /**
     * 删除字符串中的指定字符
     */
    public static String deleteChar(String str, char c) {
        return str == null ? null : str.replace(String.valueOf(c), EMPTY);
    }

    // ==================== 字符串计数方法 ====================

    /**
     * 统计子串出现的次数
     */
    public static int countMatches(String str, String sub) {
        if (str == null || str.isEmpty() || sub == null || sub.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    /**
     * 统计指定字符出现的次数
     */
    public static int countMatches(String str, char c) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    // ==================== 字符串判断增强方法 ====================

    /**
     * 判断字符串是否只包含空白字符
     */
    public static boolean isWhitespace(CharSequence cs) {
        if (cs == null || cs.length() == 0) {
            return false;
        }
        for (int i = 0; i < cs.length(); i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字符串是否全部大写
     */
    public static boolean isAllUpperCase(CharSequence cs) {
        if (cs == null || cs.length() == 0) {
            return false;
        }
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if (Character.isLetter(c) && !Character.isUpperCase(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字符串是否全部小写
     */
    public static boolean isAllLowerCase(CharSequence cs) {
        if (cs == null || cs.length() == 0) {
            return false;
        }
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if (Character.isLetter(c) && !Character.isLowerCase(c)) {
                return false;
            }
        }
        return true;
    }

    // ==================== 字符串截取增强方法 ====================

    /**
     * 截取字符串到指定分隔符之前
     */
    public static String substringBefore(String str, String separator) {
        if (str == null || str.isEmpty() || separator == null) {
            return str;
        }
        if (separator.isEmpty()) {
            return EMPTY;
        }
        int pos = str.indexOf(separator);
        return pos == -1 ? str : str.substring(0, pos);
    }

    /**
     * 截取字符串到指定分隔符之后
     */
    public static String substringAfter(String str, String separator) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        if (separator == null || separator.isEmpty()) {
            return EMPTY;
        }
        int pos = str.indexOf(separator);
        return pos == -1 ? EMPTY : str.substring(pos + separator.length());
    }

    /**
     * 截取最后一个分隔符之前的字符串
     */
    public static String substringBeforeLast(String str, String separator) {
        if (str == null || str.isEmpty() || separator == null) {
            return str;
        }
        if (separator.isEmpty()) {
            return EMPTY;
        }
        int pos = str.lastIndexOf(separator);
        return pos == -1 ? str : str.substring(0, pos);
    }

    /**
     * 截取最后一个分隔符之后的字符串
     */
    public static String substringAfterLast(String str, String separator) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        if (separator == null || separator.isEmpty()) {
            return EMPTY;
        }
        int pos = str.lastIndexOf(separator);
        return pos == -1 ? EMPTY : str.substring(pos + separator.length());
    }

    // ==================== 字符串替换增强方法 ====================

    /**
     * 替换指定次数的子串
     */
    public static String replace(String text, String searchString, String replacement, int max) {
        if (text == null || text.isEmpty() || searchString == null || searchString.isEmpty() || replacement == null || max <= 0) {
            return text;
        }
        int start = 0;
        int end = text.indexOf(searchString, start);
        if (end == -1) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + (replacement.length() - searchString.length()) * max);
        while (end != -1 && max > 0) {
            sb.append(text, start, end);
            sb.append(replacement);
            start = end + searchString.length();
            max--;
            end = text.indexOf(searchString, start);
        }
        sb.append(text, start, text.length());
        return sb.toString();
    }

    /**
     * 批量替换
     */
    public static String replaceEach(String text, String[] searchList, String[] replacementList) {
        if (text == null || text.isEmpty() || searchList == null || searchList.length == 0 
                || replacementList == null || replacementList.length == 0) {
            return text;
        }
        String result = text;
        for (int i = 0; i < searchList.length && i < replacementList.length; i++) {
            result = replace(result, searchList[i], replacementList[i]);
        }
        return result;
    }

    // ==================== 字符串比较增强方法 ====================

    /**
     * 比较两个字符串是否不相等
     */
    public static boolean notEquals(CharSequence cs1, CharSequence cs2) {
        return !equals(cs1, cs2);
    }

    /**
     * 比较两个字符串是否不相等（忽略大小写）
     */
    public static boolean notEqualsIgnoreCase(CharSequence cs1, CharSequence cs2) {
        return !equalsIgnoreCase(cs1, cs2);
    }

    /**
     * 判断字符串是否与任意一个目标字符串相等
     */
    public static boolean equalsAny(CharSequence string, CharSequence... searchStrings) {
        if (searchStrings == null || searchStrings.length == 0) {
            return false;
        }
        for (CharSequence searchStr : searchStrings) {
            if (equals(string, searchStr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断字符串是否与任意一个目标字符串相等（忽略大小写）
     */
    public static boolean equalsAnyIgnoreCase(CharSequence string, CharSequence... searchStrings) {
        if (searchStrings == null || searchStrings.length == 0) {
            return false;
        }
        for (CharSequence searchStr : searchStrings) {
            if (equalsIgnoreCase(string, searchStr)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 字符串清理增强方法 ====================

    /**
     * 压缩多个连续空格为单个空格
     */
    public static String compressWhitespace(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.replaceAll("\\s+", " ").trim();
    }

    /**
     * 移除字符串中的所有空白字符（包括空格、制表符、换行等）
     */
    public static String removeWhitespace(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
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

    // ==================== 字符串重复增强方法 ====================

    /**
     * 重复字符串指定次数，使用分隔符连接
     */
    public static String repeat(String str, int repeat, String separator) {
        if (str == null || repeat <= 0) {
            return EMPTY;
        }
        if (separator == null) {
            separator = EMPTY;
        }
        return Collections.nCopies(repeat, str).stream()
                .collect(Collectors.joining(separator));
    }

    // ==================== 其他实用方法 ====================

    /**
     * 如果字符串为 null 或空白，返回 null，否则返回原字符串
     */
    public static String nullIfBlank(String str) {
        return isBlank(str) ? null : str;
    }

    /**
     * 如果字符串为 null 或空，返回 null，否则返回原字符串
     */
    public static String nullIfEmpty(String str) {
        return isEmpty(str) ? null : str;
    }

    /**
     * 获取字符串的字节数组（使用 UTF-8 编码）
     */
    public static byte[] getBytes(String str) {
        return str == null ? null : str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 从字节数组创建字符串（使用 UTF-8 编码）
     */
    public static String fromBytes(byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    // ==================== 正则表达式方法 ====================

    /**
     * 判断字符串是否匹配指定正则表达式
     */
    public static boolean isMatch(CharSequence str, String regex) {
        if (str == null || regex == null) {
            return false;
        }
        return Pattern.matches(regex, str);
    }

    /**
     * 提取第一个匹配的子串
     */
    public static String extract(CharSequence str, String regex) {
        if (str == null || regex == null) {
            return null;
        }
        Matcher matcher = PatternCache.compile(regex).matcher(str);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * 提取所有匹配的子串
     */
    public static List<String> extractAll(CharSequence str, String regex) {
        if (str == null || regex == null) {
            return new ArrayList<>();
        }
        List<String> results = new ArrayList<>();
        Matcher matcher = PatternCache.compile(regex).matcher(str);
        while (matcher.find()) {
            results.add(matcher.group());
        }
        return results;
    }

    /**
     * 使用正则表达式替换所有匹配的子串
     */
    public static String replaceAllRegex(String str, String regex, String replacement) {
        if (str == null || regex == null) {
            return str;
        }
        return PatternCache.compile(regex).matcher(str).replaceAll(replacement);
    }

    /**
     * 使用正则表达式替换第一个匹配的子串
     */
    public static String replaceFirstRegex(String str, String regex, String replacement) {
        if (str == null || regex == null) {
            return str;
        }
        return PatternCache.compile(regex).matcher(str).replaceFirst(replacement);
    }

    /**
     * 判断是否为有效的邮箱地址
     */
    public static boolean isEmail(CharSequence str) {
        if (str == null) {
            return false;
        }
        return EMAIL_PATTERN.matcher(str).matches();
    }

    /**
     * 判断是否为有效的手机号码（中国大陆）
     */
    public static boolean isMobile(CharSequence str) {
        if (str == null) {
            return false;
        }
        return MOBILE_PATTERN.matcher(str).matches();
    }

    /**
     * 判断是否为有效的身份证号（中国大陆）
     */
    public static boolean isIdCard(CharSequence str) {
        if (str == null) {
            return false;
        }
        return ID_CARD_PATTERN.matcher(str).matches();
    }

    /**
     * 判断是否为有效的 URL
     */
    public static boolean isUrl(CharSequence str) {
        if (str == null) {
            return false;
        }
        return URL_PATTERN.matcher(str).matches();
    }

    /**
     * 判断是否为有效的 IPv4 地址
     */
    public static boolean isIpv4(CharSequence str) {
        if (str == null) {
            return false;
        }
        return IPV4_PATTERN.matcher(str).matches();
    }

    // ==================== 字符串模板方法 ====================

    /**
     * 渲染模板（使用 ${key} 占位符）
     */
    public static String renderTemplate(String template, Map<String, Object> params) {
        if (template == null || params == null) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() == null ? "" : entry.getValue().toString();
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * 渲染模板（使用自定义占位符）
     */
    public static String renderTemplate(String template, Map<String, Object> params, String prefix, String suffix) {
        if (template == null || params == null) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = prefix + entry.getKey() + suffix;
            String value = entry.getValue() == null ? "" : entry.getValue().toString();
            result = result.replace(placeholder, value);
        }
        return result;
    }

    // ==================== 敏感词过滤方法 ====================

    /**
     * 脱敏处理（保留前后缀）
     */
    public static String maskSensitive(String str, int keepPrefix, int keepSuffix) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        int len = str.length();
        if (len <= keepPrefix + keepSuffix) {
            return repeat("*", len);
        }
        return str.substring(0, keepPrefix) + repeat("*", len - keepPrefix - keepSuffix) + str.substring(len - keepSuffix);
    }

    /**
     * 手机号脱敏
     */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() != 11) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    /**
     * 身份证号脱敏
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 18) {
            return idCard;
        }
        return idCard.substring(0, 6) + repeat("*", 8) + idCard.substring(14);
    }

    /**
     * 邮箱脱敏
     */
    public static String maskEmail(String email) {
        if (email == null || !isEmail(email)) {
            return email;
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return email;
        }
        return email.substring(0, 2) + repeat("*", atIndex - 2) + email.substring(atIndex);
    }

    // ==================== 字符串相似度方法 ====================

    /**
     * 计算编辑距离（Levenshtein Distance）
     */
    public static int levenshteinDistance(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return -1;
        }
        int[][] dp = new int[str1.length() + 1][str2.length() + 1];
        for (int i = 0; i <= str1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= str2.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                int cost = str1.charAt(i - 1) == str2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[str1.length()][str2.length()];
    }

    /**
     * 计算相似度（0-1 之间）
     */
    public static double similarity(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return 0.0;
        }
        int maxLen = Math.max(str1.length(), str2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = levenshteinDistance(str1, str2);
        return 1.0 - (double) distance / maxLen;
    }

    // ==================== 字符串比较增强方法 ====================

    /**
     * 判断字符串是否包含空白字符
     */
    public static boolean containsWhitespace(CharSequence cs) {
        if (cs == null || cs.length() == 0) {
            return false;
        }
        for (int i = 0; i < cs.length(); i++) {
            if (Character.isWhitespace(cs.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断字符串是否以指定字符开头
     */
    public static boolean startsWith(CharSequence str, char prefix) {
        return str != null && str.length() > 0 && str.charAt(0) == prefix;
    }

    /**
     * 判断字符串是否以指定字符结尾
     */
    public static boolean endsWith(CharSequence str, char suffix) {
        return str != null && str.length() > 0 && str.charAt(str.length() - 1) == suffix;
    }

    /**
     * 获取字符串的字节数组（使用指定字符集）
     */
    public static byte[] getBytes(String str, Charset charset) {
        Objects.requireNonNull(charset, "charset must not be null");
        return str == null ? null : str.getBytes(charset);
    }

    /**
     * 从字节数组创建字符串（使用指定字符集）
     */
    public static String fromBytes(byte[] bytes, Charset charset) {
        Objects.requireNonNull(charset, "charset must not be null");
        return bytes == null ? null : new String(bytes, charset);
    }

    /**
     * 判断字符串是否只包含数字和空格
     */
    public static boolean isNumericOrSpace(CharSequence cs) {
        if (cs == null || cs.length() == 0) {
            return false;
        }
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if (!Character.isDigit(c) && !Character.isSpaceChar(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取字符串的 Unicode 编码
     */
    public static String toUnicode(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (c > 255) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 从 Unicode 编码还原字符串
     */
    public static String fromUnicode(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < str.length()) {
            if (str.charAt(i) == '\\' && i + 5 < str.length() && str.charAt(i + 1) == 'u') {
                String hex = str.substring(i + 2, i + 6);
                char c = (char) Integer.parseInt(hex, 16);
                sb.append(c);
                i += 6;
            } else {
                sb.append(str.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
