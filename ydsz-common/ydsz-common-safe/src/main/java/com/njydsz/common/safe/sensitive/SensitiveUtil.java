package com.njydsz.common.safe.sensitive;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 敏感数据脱敏工具类
 *
 * <p>提供各种敏感数据类型的脱敏处理方法。
 *
 * <p><b>支持的脱敏类型：</b>
 *
 * <ul>
 *   <li>中文姓名、身份证、手机号、邮箱
 *   <li>银行卡号、固定电话、密码
 *   <li>地址、护照、军官证等
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SensitiveType
 */
public final class SensitiveUtil {

  private static final Logger log = LoggerFactory.getLogger(SensitiveUtil.class);

  private static final char ASTERISK = '*';

  /** 自定义脱敏 handler 注册表（name → masker） */
  private static final Map<String, Function<String, String>> CUSTOM_HANDLERS =
      new ConcurrentHashMap<>();

  // ============================== 标准 PII 扫描正则（单一来源，全系统共享） ==============================

  /**
   * 标准 PII 扫描模式表。
   *
   * <p>所有需要从自由文本中自动发现 PII 的场景应使用此处统一维护的正则， 避免各模块自行维护导致升级遗漏（如手机号号段扩展需同步修改多处）。
   *
   * <p>顺序影响扫描优先级：先匹配长模式（身份证 18 位）再匹配短模式（手机号 11 位）， 防止身份证前 17 位被手机号模式截断。
   */
  private static final Map<Pattern, SensitiveType> PII_SCAN_PATTERNS;

  static {
    Map<Pattern, SensitiveType> map = new LinkedHashMap<>();
    // 身份证号：18 位（前 17 位数字 + 末位数字或 X），严格匹配
    map.put(
        Pattern.compile(
            "(?<![0-9])([1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx])(?![0-9])"),
        SensitiveType.ID_CARD);
    // 手机号：11 位数字，1 开头，第二位 3-9
    map.put(Pattern.compile("(?<![0-9])(1[3-9]\\d{9})(?![0-9])"), SensitiveType.PHONE);
    // 银行卡号：16-19 位连续数字，62 开头
    map.put(Pattern.compile("(?<![0-9])(6[2-9]\\d{14,17})(?![0-9])"), SensitiveType.BANK_CARD);
    // 邮箱地址
    map.put(
        Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"), SensitiveType.EMAIL);
    // 护照号：G/E/K/S 开头 + 8 位数字
    map.put(Pattern.compile("(?<![A-Z])([GEKS][1-9]\\d{7})(?!\\d)"), SensitiveType.PASSPORT);
    PII_SCAN_PATTERNS = map;
  }

  private SensitiveUtil() {}

  /**
   * 注册自定义脱敏 handler。
   *
   * @param name handler 名称（如 "default"）
   * @param masker 脱敏函数（输入原始值，返回脱敏后的值）
   */
  public static void register(String name, Function<String, String> masker) {
    if (name != null && masker != null) {
      CUSTOM_HANDLERS.put(name, masker);
    }
  }

  /**
   * 根据已注册的 handler 名称执行自定义脱敏。
   *
   * @param name handler 名称
   * @param value 原始值
   * @return 脱敏后的值，未注册 handler 时返回原值
   */
  public static String maskCustom(String name, String value) {
    if (name == null || value == null || value.isEmpty()) {
      return value;
    }
    Function<String, String> handler = CUSTOM_HANDLERS.get(name);
    return handler != null ? handler.apply(value) : value;
  }

  /**
   * 手机号脱敏便捷方法（使用默认替换字符 *）。
   *
   * @param value 原手机号
   * @return 脱敏后的手机号
   */
  public static String maskPhone(String value) {
    return phone(value, ASTERISK);
  }

  /**
   * 电子邮箱脱敏便捷方法（使用默认替换字符 *）。
   *
   * @param value 原邮箱
   * @return 脱敏后的邮箱
   */
  public static String maskEmail(String value) {
    return email(value, ASTERISK);
  }

  /**
   * 根据脱敏类型对数据进行脱敏
   *
   * @param value 原数据
   * @param type 脱敏类型
   * @param replaceChar 替换字符
   * @return 脱敏后的数据
   */
  public static String desensitize(String value, SensitiveType type, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    if (type == null) {
      return value;
    }
    return switch (type) {
      case DEFAULT -> defaultDesensitize(value, replaceChar);
      case CHINESE_NAME -> chineseName(value, replaceChar);
      case ID_CARD -> idCard(value, replaceChar);
      case PHONE -> phone(value, replaceChar);
      case EMAIL -> email(value, replaceChar);
      case BANK_CARD -> bankCard(value, replaceChar);
      case ADDRESS -> address(value, replaceChar);
      case PASSWORD -> password(value);
      case FIXED_PHONE -> fixedPhone(value, replaceChar);
      case CVV -> cvv(value);
      case MILITARY_ID -> militaryId(value, replaceChar);
      case PASSPORT -> passport(value, replaceChar);
      case BUSINESS_LICENSE -> businessLicense(value, replaceChar);
      case CAR_LICENSE -> carLicense(value, replaceChar);
      case SOCIAL_SECURITY -> socialSecurity(value, replaceChar);
      case BIRTH_DATE -> birthDate(value);
      case NAME -> name(value, replaceChar);
      case CUSTOM -> custom(value, replaceChar);
    };
  }

  /**
   * 根据脱敏类型对数据进行脱敏（使用默认替换字符 *）
   *
   * @param value 原数据
   * @param type 脱敏类型
   * @return 脱敏后的数据
   */
  public static String desensitize(String value, SensitiveType type) {
    return desensitize(value, type, ASTERISK);
  }

  /**
   * 默认脱敏
   *
   * <p>脱敏规则：保留前2后2位，中间用替换字符替换
   *
   * @param value 原数据
   * @param replaceChar 替换字符
   * @return 脱敏后的数据
   */
  public static String defaultDesensitize(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length <= 4) {
      return repeat(replaceChar, length);
    }
    String prefix = value.substring(0, 2);
    String suffix = value.substring(length - 2);
    return prefix + repeat(replaceChar, length - 4) + suffix;
  }

  /**
   * 中文姓名脱敏
   *
   * <p>脱敏规则：保留姓氏，隐藏名字
   *
   * @param value 原姓名
   * @param replaceChar 替换字符
   * @return 脱敏后的姓名
   */
  public static String chineseName(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length == 1) {
      return String.valueOf(replaceChar);
    }
    if (length == 2) {
      return value.charAt(0) + String.valueOf(replaceChar);
    }
    return value.charAt(0) + String.valueOf(replaceChar) + value.substring(2);
  }

  /**
   * 身份证号脱敏
   *
   * <p>脱敏规则：中间8位脱敏
   *
   * @param value 原身份证号
   * @param replaceChar 替换字符
   * @return 脱敏后的身份证号
   */
  public static String idCard(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length <= 8) {
      return repeat(replaceChar, length);
    }
    String prefix = value.substring(0, 3);
    String suffix = value.substring(11);
    return prefix + repeat(replaceChar, 8) + suffix;
  }

  /**
   * 手机号脱敏
   *
   * <p>脱敏规则：显示前3位和后4位，中间隐藏
   *
   * @param value 原手机号
   * @param replaceChar 替换字符
   * @return 脱敏后的手机号
   */
  public static String phone(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length <= 7) {
      return repeat(replaceChar, length);
    }
    String prefix = value.substring(0, 3);
    String suffix = value.substring(length - 4);
    return prefix + repeat(replaceChar, length - 7) + suffix;
  }

  /**
   * 电子邮箱脱敏
   *
   * <p>脱敏规则：保留首尾字符，中间隐藏
   *
   * @param value 原邮箱
   * @param replaceChar 替换字符
   * @return 脱敏后的邮箱
   */
  public static String email(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int atIndex = value.indexOf('@');
    if (atIndex <= 1) {
      return repeat(replaceChar, value.length());
    }
    String username = value.substring(0, atIndex);
    String domain = value.substring(atIndex);

    int usernameLength = username.length();
    if (usernameLength == 2) {
      return username.charAt(0) + String.valueOf(replaceChar) + domain;
    }
    if (usernameLength == 3) {
      return username.charAt(0) + String.valueOf(replaceChar) + username.charAt(2) + domain;
    }
    return username.charAt(0)
        + repeat(replaceChar, 2)
        + username.charAt(usernameLength - 1)
        + domain;
  }

  /**
   * 银行卡号脱敏
   *
   * <p>脱敏规则：后4位保留，其余脱敏
   *
   * @param value 原银行卡号
   * @param replaceChar 替换字符
   * @return 脱敏后的银行卡号
   */
  public static String bankCard(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length <= 4) {
      return repeat(replaceChar, length);
    }
    String suffix = value.substring(length - 4);
    return repeat(replaceChar, length - 4) + suffix;
  }

  /**
   * 家庭住址脱敏
   *
   * <p>脱敏规则：保留省市区，隐藏详细地址
   *
   * @param value 原地址
   * @param replaceChar 替换字符
   * @return 脱敏后的地址
   */
  public static String address(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length <= 4) {
      return repeat(replaceChar, length);
    }
    return value.substring(0, 4) + repeat(replaceChar, Math.min(6, length - 4));
  }

  /**
   * 密码脱敏
   *
   * <p>脱敏规则：不返回实际密码
   *
   * @param value 原密码
   * @return 脱敏后的密码
   */
  public static String password(String value) {
    return "******";
  }

  /**
   * 固定电话脱敏
   *
   * <p>脱敏规则：显示区号和后4位，中间隐藏
   *
   * @param value 原固定电话
   * @param replaceChar 替换字符
   * @return 脱敏后的固定电话
   */
  public static String fixedPhone(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length <= 4) {
      return repeat(replaceChar, length);
    }
    int dashIndex = value.lastIndexOf('-');
    if (dashIndex > 0) {
      String areaCode = value.substring(0, dashIndex);
      String number = value.substring(dashIndex + 1);
      if (number.length() <= 4) {
        return areaCode + "-" + repeat(replaceChar, number.length());
      }
      return areaCode
          + "-"
          + repeat(replaceChar, number.length() - 4)
          + number.substring(number.length() - 4);
    }
    return phone(value, replaceChar);
  }

  /**
   * CVV 脱敏
   *
   * <p>脱敏规则：不返回
   *
   * @param value 原 CVV
   * @return 脱敏后的 CVV
   */
  public static String cvv(String value) {
    return "***";
  }

  /**
   * 军官证脱敏
   *
   * @param value 原军官证号
   * @param replaceChar 替换字符
   * @return 脱敏后的军官证号
   */
  public static String militaryId(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length <= 4) {
      return repeat(replaceChar, length);
    }
    return value.substring(0, 2) + repeat(replaceChar, length - 4) + value.substring(length - 2);
  }

  /**
   * 护照脱敏
   *
   * @param value 原护照号
   * @param replaceChar 替换字符
   * @return 脱敏后的护照号
   */
  public static String passport(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length <= 4) {
      return repeat(replaceChar, length);
    }
    return value.substring(0, 2) + repeat(replaceChar, length - 4) + value.substring(length - 2);
  }

  /**
   * 营业执照注册号脱敏
   *
   * @param value 原注册号
   * @param replaceChar 替换字符
   * @return 脱敏后的注册号
   */
  public static String businessLicense(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length <= 6) {
      return repeat(replaceChar, length);
    }
    return value.substring(0, 3) + repeat(replaceChar, length - 6) + value.substring(length - 3);
  }

  /**
   * 车牌号脱敏
   *
   * <p>脱敏规则：保留首字符和最后一位
   *
   * @param value 原车牌号
   * @param replaceChar 替换字符
   * @return 脱敏后的车牌号
   */
  public static String carLicense(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length <= 2) {
      return repeat(replaceChar, length);
    }
    return value.charAt(0) + repeat(replaceChar, length - 2) + value.charAt(length - 1);
  }

  /**
   * 社保卡号脱敏
   *
   * @param value 原社保卡号
   * @param replaceChar 替换字符
   * @return 脱敏后的社保卡号
   */
  public static String socialSecurity(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length <= 6) {
      return repeat(replaceChar, length);
    }
    return value.substring(0, 3) + repeat(replaceChar, length - 6) + value.substring(length - 3);
  }

  /**
   * 出生日期脱敏
   *
   * <p>脱敏规则：只显示年月
   *
   * @param value 原出生日期
   * @return 脱敏后的出生日期
   */
  public static String birthDate(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    if (value.length() >= 6) {
      return value.substring(0, 4) + "-**-**";
    }
    return repeat(ASTERISK, value.length());
  }

  /**
   * 姓名脱敏
   *
   * <p>脱敏规则：仅保留首字
   *
   * @param value 原姓名
   * @param replaceChar 替换字符
   * @return 脱敏后的姓名
   */
  public static String name(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int length = value.length();
    if (length == 1) {
      return value;
    }
    if (length == 2) {
      return value.charAt(0) + String.valueOf(replaceChar);
    }
    if (length == 3) {
      return value.charAt(0) + String.valueOf(replaceChar).repeat(2);
    }
    return value.charAt(0)
        + String.valueOf(replaceChar).repeat(length - 2)
        + value.charAt(length - 1);
  }

  /**
   * 自定义脱敏
   *
   * <p>脱敏规则：默认将字符串全部替换为指定字符
   *
   * @param value 原始数据
   * @param replaceChar 替换字符
   * @return 脱敏后的数据
   */
  public static String custom(String value, char replaceChar) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    return repeat(replaceChar, value.length());
  }

  private static String repeat(char c, int count) {
    return String.valueOf(c).repeat(count);
  }

  // ============================== PII 自由文本扫描 + 脱敏（消除各模块重复正则） ==============================

  /**
   * PII 匹配位置信息。
   *
   * <p>记录单次正则命中的位置、原始值与类型，供需要定位的场景（如文档高亮、 结构化 PII 发现结果）使用。不可变对象，线程安全。
   *
   * @param startIndex 匹配起始下标（含）
   * @param endIndex 匹配结束下标（不含）
   * @param rawValue 匹配到的原始文本
   * @param type 对应的 PII 类型
   * @author ydsz-team
   * @since 2.1.0
   */
  public record PiiMatch(int startIndex, int endIndex, String rawValue, SensitiveType type) {

    /**
     * 返回此匹配的脱敏结果（使用默认替换字符 *）。
     *
     * <p>委托 {@link SensitiveUtil#desensitize} 执行，确保与本工具类其他 脱敏路径结果一致。
     *
     * @return 脱敏后的文本；{@code rawValue} 为 {@code null} 或空时返回原值
     */
    public String masked() {
      return desensitize(rawValue, type);
    }
  }

  /**
   * 对自由文本执行 PII 扫描，返回带位置信息的匹配列表（不脱敏）。
   *
   * <p>扫描文本中的所有预定义 PII 模式（身份证、手机号、银行卡、邮箱、护照）， 返回每次命中的位置、原始值与类型。各模式独立扫描原文，结果按下标升序排列。
   *
   * <p><b>设计目的：</b>
   *
   * <ul>
   *   <li>作为全系统 PII 扫描的唯一正则来源，消除各模块重复正则
   *   <li>供需要定位的场景使用：文档 PII 高亮、结构化发现结果等
   *   <li>与 {@link #scanAndMask} 共享同一套正则，保证结果一致
   * </ul>
   *
   * <p><b>使用示例：</b>
   *
   * <pre>{@code
   * List<PiiMatch> matches = SensitiveUtil.scanWithPositions("联系人：张三，手机：13800138000");
   * for (PiiMatch m : matches) {
   *     System.out.println(m.type() + " at [" + m.startIndex() + "," + m.endIndex() + "): " + m.masked());
   * }
   * }</pre>
   *
   * @param text 原始文本（可为 null）
   * @return 匹配列表（按下标升序）；输入为 null 或空时返回空列表，不返回 {@code null}
   * @author ydsz-team
   * @since 2.1.0
   */
  public static List<PiiMatch> scanWithPositions(String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }
    List<PiiMatch> matches = new java.util.ArrayList<>();
    for (Map.Entry<Pattern, SensitiveType> entry : PII_SCAN_PATTERNS.entrySet()) {
      Pattern pattern = entry.getKey();
      SensitiveType type = entry.getValue();
      Matcher matcher = pattern.matcher(text);
      while (matcher.find()) {
        // 优先捕获 group(1)，不存在则取 group(0)
        String raw = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
        matches.add(new PiiMatch(matcher.start(), matcher.end(), raw, type));
      }
    }
    // 按下标升序排列，便于下游按序处理
    matches.sort(java.util.Comparator.comparingInt(PiiMatch::startIndex));
    return matches;
  }

  /**
   * 对自由文本执行 PII 扫描并脱敏（使用默认替换字符 *）。
   *
   * <p>扫描文本中的所有预定义 PII 模式（身份证、手机号、银行卡、邮箱、护照）， 对匹配到的片段调用对应的脱敏方法。
   *
   * <p><b>设计目的（P1-3）：</b>
   *
   * <ul>
   *   <li>消除 {@code FlowSensitiveMasker}、{@code PiiMaskingGuardrail} 等类中重复的正则定义
   *   <li>PII 扫描正则在此处统一维护，升级时只需修改一处
   *   <li>脱敏算法委托本类的标准方法，确保与 {@code @SensitiveData} 注解脱敏结果一致
   * </ul>
   *
   * <p><b>使用示例：</b>
   *
   * <pre>{@code
   * // 在业务模块中使用（替代自建 PII 正则扫描）
   * String safe = SensitiveUtil.scanAndMask("联系人：张三，手机：13800138000");
   * // 结果："联系人：张三，手机：138****8000"
   * }</pre>
   *
   * @param text 原始文本（可为 null）
   * @return 脱敏后文本；输入为 null 时返回 null
   */
  public static String scanAndMask(String text) {
    return scanAndMaskWith(text, ASTERISK);
  }

  /**
   * 对自由文本执行 PII 扫描并脱敏（自定义替换字符）。
   *
   * @param text 原始文本（可为 null）
   * @param replaceChar 替换字符（如 '*' 或 '#'）
   * @return 脱敏后文本；输入为 null 时返回 null
   * @see #scanAndMask(String)
   */
  public static String scanAndMaskWith(String text, char replaceChar) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String result = text;
    try {
      for (Map.Entry<Pattern, SensitiveType> entry : PII_SCAN_PATTERNS.entrySet()) {
        Pattern pattern = entry.getKey();
        SensitiveType type = entry.getValue();
        Matcher matcher = pattern.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
          String raw = matcher.group(1);
          String masked = desensitize(raw, type, replaceChar);
          matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(sb);
        result = sb.toString();
      }
    } catch (Exception e) {
      log.warn("[SensitiveUtil] PII 扫描脱敏异常，返回原文: err={}", e.getMessage());
      return text;
    }
    return result;
  }
}
