package com.njydsz.common.util.string;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.slf4j.helpers.MessageFormatter;

/**
 * 字符串工具类
 *
 * <p>提供项目高频使用的字符串处理方法，聚焦于 JDK 未覆盖的能力：
 *
 * <ul>
 *   <li>对象判空：isEmpty / isNotEmpty / isBlank / isNotBlank / hasText（支持 CharSequence 与 Object 多类型）
 *   <li>默认值：defaultIfBlank
 *   <li>前缀匹配：startsWithIgnoreCase（忽略大小写）
 *   <li>命名转换：toCamelCase / toUnderScoreCase
 *   <li>格式化：format（使用 {} 占位符，类似 SLF4J 风格）
 *   <li>截断/缩写：truncate / abbreviate（带省略号）
 *   <li>空白规范化：normalizeSpace
 * </ul>
 *
 * <p><b>不提供的能力（直接使用 JDK）：</b>
 *
 * <ul>
 *   <li>equals / contains / indexOf / startsWith / endsWith → {@link String}
 *   <li>trim / strip / replace / split → {@link String}
 *   <li>toUpperCase / toLowerCase / repeat / reverse → {@link String} / {@link StringBuilder}
 *   <li>join → {@link java.util.stream.Collectors#joining(CharSequence)}
 *   <li>正则匹配 → {@link java.util.regex.Pattern} / {@link java.util.regex.Matcher}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class StringUtils {

  private StringUtils() {
    throw new UnsupportedOperationException(
        "StringUtils is a utility class and cannot be instantiated");
  }

  /** 空字符串常量 */
  public static final String EMPTY = "";

  /** 下划线字符常量 */
  private static final char SEPARATOR = '_';

  // ==================== 判空方法 ====================

  /**
   * 判断字符串是否为 null 或空字符串（""）
   *
   * @param input 待判断的字符序列
   * @return 是否为 null 或空字符串
   */
  public static boolean isEmpty(CharSequence input) {
    return input == null || input.length() == 0;
  }

  /**
   * 判断字符串是否不为 null 且不为空字符串
   *
   * @param input 待判断的字符序列
   * @return 是否不为 null 且不为空字符串
   */
  public static boolean isNotEmpty(CharSequence input) {
    return !isEmpty(input);
  }

  /**
   * 判断字符串是否为 null、空字符串或只包含空白字符
   *
   * @param input 待判断的字符序列
   * @return 是否为 null、空字符串或只包含空白字符
   */
  public static boolean isBlank(CharSequence input) {
    if (input == null || input.length() == 0) {
      return true;
    }
    for (int i = 0; i < input.length(); i++) {
      if (!Character.isWhitespace(input.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * 判断字符串是否不为 null、不为空字符串且包含非空白字符
   *
   * @param input 待判断的字符序列
   * @return 是否包含非空白字符
   */
  public static boolean isNotBlank(CharSequence input) {
    return !isBlank(input);
  }

  /**
   * 判断所有字符串是否都不为 null、都不为空且都包含非空白字符。
   *
   * <p>等价于 Spring {@code StringUtils.hasText} 的多参扩展。
   *
   * <p>示例：
   *
   * <pre>{@code
   * isNoneBlank("a", "b")      // true
   * isNoneBlank("a", null)     // false
   * isNoneBlank("a", "")       // false
   * isNoneBlank("a", " ")      // false
   * isNoneBlank()              // true（空参返回 true）
   * }</pre>
   *
   * @param inputs 待判断的字符序列数组
   * @return 全部包含非空白字符时返回 true
   * @since 26.09.19
   */
  public static boolean isNoneBlank(CharSequence... inputs) {
    if (inputs == null || inputs.length == 0) {
      return true;
    }
    for (CharSequence cs : inputs) {
      if (isBlank(cs)) {
        return false;
      }
    }
    return true;
  }

  /**
   * 判断任意一个字符串是否为 null、空字符串或只包含空白字符。
   *
   * <p>是 {@link #isNoneBlank(CharSequence...)} 的逻辑取反。存在任一空白时返回 true。
   *
   * @param inputs 待判断的字符序列数组
   * @return 任一为空白时返回 true；全部非空白时返回 false
   * @since 26.09.19
   */
  public static boolean isAnyBlank(CharSequence... inputs) {
    if (inputs == null || inputs.length == 0) {
      return false;
    }
    for (CharSequence cs : inputs) {
      if (isBlank(cs)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 判断字符串是否包含实际文本内容（不为 null、不为空且包含非空白字符）
   *
   * <p>hasText("hello") -> true
   *
   * <p>hasText(" ") -> false
   *
   * <p>hasText(null) -> false
   *
   * @param input 待判断的字符序列
   * @return 是否包含实际文本内容
   */
  public static boolean hasText(CharSequence input) {
    return isNotBlank(input);
  }

  /**
   * 判断对象是否为空（支持 CharSequence / Collection / Map / Array / Iterator / Iterable）
   *
   * <p>注意：对 CharSequence 判断长度是否为 0（与 {@link #isEmpty(CharSequence)} 语义一致）， 不按空白字符判空。如需按空白判空请使用
   * {@link #isBlank(CharSequence)}。
   *
   * @param obj 待判断对象
   * @return 判断结果
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
   *
   * @param obj 待判断对象
   * @return 判断结果
   */
  public static boolean isNotEmpty(Object obj) {
    return !isEmpty(obj);
  }

  // ==================== 默认值方法 ====================

  /**
   * 如果字符串为 null 或空白，返回默认值
   *
   * @param input 待判断的字符序列
   * @param defaultValue 空白时的默认返回值
   * @return 原字符串或默认值
   */
  public static String defaultIfBlank(CharSequence input, String defaultValue) {
    return isBlank(input) ? defaultValue : input.toString();
  }

  /**
   * 判断字符串是否以指定前缀开头（忽略大小写）
   *
   * @param input 待检查字符串
   * @param prefix 前缀
   * @return 如果 input 以 prefix 开头（忽略大小写）返回 true；str 或 prefix 为 null 返回 false
   */
  public static boolean startsWithIgnoreCase(String input, String prefix) {
    if (input == null || prefix == null) {
      return false;
    }
    return input.regionMatches(true, 0, prefix, 0, prefix.length());
  }

  /**
   * 从字符串开头移除指定的前缀。
   *
   * <p>如果字符串以指定前缀开头，则移除该前缀并返回剩余部分； 如果字符串为 null 或不以前缀开头，则返回原字符串。
   *
   * <p>示例：
   *
   * <pre>
   * removeStart("/path/to/file", "/")   -> "path/to/file"
   * removeStart("hello", "xyz")        -> "hello"
   * removeStart(null, "/")             -> null
   * </pre>
   *
   * @param input 待处理字符串（可为 null）
   * @param prefix 要移除的前缀（可为 null）
   * @return 移除前缀后的字符串；如果输入为 null 或不以前缀开头，返回原字符串
   */
  public static String removeStart(String input, String prefix) {
    if (input == null || prefix == null || prefix.isEmpty()) {
      return input;
    }
    if (input.startsWith(prefix)) {
      return input.substring(prefix.length());
    }
    return input;
  }

  // ==================== 命名转换方法 ====================

  /**
   * 下划线命名转驼峰命名。
   *
   * <p>规则：
   *
   * <ul>
   *   <li>不含下划线的字符串（如已是驼峰的 {@code userName}）保持原样返回，避免误处理
   *   <li>含下划线的字符串：下划线后的首字母大写，其余字母小写化，首字段首字母小写
   * </ul>
   *
   * <p>示例：
   *
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
   *
   * <p>userName -> user_name
   *
   * @param s s
   * @return 处理后的字符串
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

  /**
   * 格式化字符串（使用 {} 作为占位符，支持 {@code \{} } 转义）
   *
   * <p>format("Hello, {}! You are {} years old.", "Alice", 25)
   *
   * <p>内部委托 {@link org.slf4j.helpers.MessageFormatter}（slf4j-api 自带）， 与日志占位符语义完全一致：支持 {@code \{} }
   * 转义、null 渲染为 "null"、 参数不足时保留占位符、参数多余时忽略。
   *
   * @since 26.09.01
   * @param pattern 格式模式
   * @param arguments 格式化参数（可为 null 或空）
   * @return 处理后的字符串
   */
  public static String format(String pattern, Object... arguments) {
    if (pattern == null) {
      return null;
    }
    return MessageFormatter.arrayFormat(pattern, arguments).getMessage();
  }

  // ======================== 截断与缩写 ========================

  /**
   * 截断字符串到指定最大长度。
   *
   * <p>如果字符串为 {@code null} 直接返回 {@code null}； 如果字符串长度不超过 {@code maxLength}，返回原字符串。
   *
   * @param text 待截断字符串
   * @param maxLength 最大允许长度（≥ 0），超过将被截断
   * @return 不超过 maxLength 的字符串，或原字符串
   * @throws IllegalArgumentException 如果 maxLength 为负数
   * @since 26.09.01
   */
  public static String truncate(String text, int maxLength) {
    if (maxLength < MIN_INDEX) {
      throw new IllegalArgumentException("maxLength 不能为负数: " + maxLength);
    }
    if (Objects.isNull(text) || text.length() <= maxLength) {
      return text;
    }
    return text.substring(MIN_INDEX, maxLength);
  }

  /**
   * 缩写字符串：截断到指定长度并在末尾追加省略号 "..."。
   *
   * <p>当文本长度不超过 {@code maxLength} 时返回原字符串不追加省略号； 截断后的文本长度 + "..." 长度等于 maxLength。
   *
   * @param text 待缩写字符串
   * @param maxLength 缩写后最大长度（≥ 4，因为至少要保留 1 字符 + "..."）
   * @return 缩写后的字符串
   * @throws IllegalArgumentException 如果 maxLength 小于 4
   * @since 26.09.01
   */
  public static String abbreviate(String text, int maxLength) {
    if (maxLength < MIN_ABBREVIATION_LENGTH) {
      throw new IllegalArgumentException("maxLength 不能小于 " + MIN_ABBREVIATION_LENGTH);
    }
    if (Objects.isNull(text) || text.length() <= maxLength) {
      return text;
    }
    return text.substring(MIN_INDEX, maxLength - ELLIPSIS_LENGTH) + ELLIPSIS;
  }

  /**
   * 规范化空白字符：将所有空白序列（空格、制表符、换行等）替换为单个空格， 并移除首尾空白。
   *
   * <p>等价于 Apache Commons Lang 中 {@code StringUtils.normalizeSpace}。
   *
   * @param text 待处理字符串
   * @return 处理后的字符串，输入为 {@code null} 时返回 {@code null}
   * @since 26.09.01
   */
  public static String normalizeSpace(String text) {
    if (Objects.isNull(text)) {
      return null;
    }
    return Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replaceAll(WHITESPACE_REGEX, BYTE_SPACE)
        .trim();
  }

  private static final int MIN_ABBREVIATION_LENGTH = 4;
  private static final int ELLIPSIS_LENGTH = 3;
  private static final String ELLIPSIS = "...";
  private static final int MIN_INDEX = 0;
  private static final String BYTE_SPACE = " ";
  private static final String WHITESPACE_REGEX = "\\s+";
}
