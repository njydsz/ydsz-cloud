package com.njydsz.common.util.validate;

import java.util.regex.Pattern;

import jakarta.annotation.Nullable;

import com.njydsz.common.util.api.Experimental;

/**
 * 业务校验工具类
 *
 * <p>提供中国常用业务数据的格式校验能力，所有方法均为 null 安全。 校验规则遵循国家标准（GB 11643-1999 身份证、GB 32100-2015 统一社会信用代码等）。
 *
 * <p><b>规则说明：</b>
 *
 * <ul>
 *   <li>手机号：1开头，第二位3-9，共11位
 *   <li>身份证：18位，末位可为 X，含校验码验证
 *   <li>统一社会信用代码：18位，含校验码验证（GB 32100-2015）
 *   <li>银行卡：13-19位数字，Luhn 算法校验
 * </ul>
 *
 * <p>所有正则表达式已预编译为 {@link Pattern} 静态字段，避免重复编译。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Experimental("能力储备：常用格式校验工具，当前平台内暂无消费方；覆盖面待扩充（护照、港澳台证件等）")
public final class ValidationUtils {

  /** 中国大陆手机号正则：1开头，第二位3-9，共11位 */
  private static final Pattern MOBILE_PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

  /** 邮箱正则 */
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  /** 中国车牌号正则（含新能源） */
  private static final Pattern PLATE_NUMBER_PATTERN =
      Pattern.compile(
          "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z][A-HJ-NP-Z0-9]{4,5}" + "[A-HJ-NP-Z0-9挂学警港澳]$");

  /** 十六进制颜色值正则（#RGB 或 #RRGGBB） */
  private static final Pattern HEX_COLOR_PATTERN =
      Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");

  /** IPv4 地址正则（四组 0-255，点分十进制） */
  private static final Pattern IPV4_PATTERN =
      Pattern.compile(
          "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$");

  /** 统一社会信用代码字符集（不含 I/O/S/V/Z） */
  private static final String SOCIAL_CREDIT_CODE_CHARSET = "0123456789ABCDEFGHJKLMNPQRTUWXY";

  /** 统一社会信用代码加权因子 */
  private static final int[] SOCIAL_CREDIT_WEIGHTS = {
    1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28
  };

  /** 18 位身份证号码的加权因子 */
  private static final int[] ID_CARD_WEIGHTS = {
    7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2
  };

  /** 18 位身份证号码校验码对照表 */
  private static final char[] ID_CARD_CHECK_CODES = {
    '1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'
  };

  /** 统一社会信用代码标准长度 */
  private static final int SOCIAL_CREDIT_CODE_LENGTH = 18;

  /** 统一社会信用代码校验码索引 */
  private static final int CHECK_CODE_INDEX = 17;

  /** 身份证号码标准长度 */
  private static final int ID_CARD_LENGTH = 18;

  /** 身份证校验码索引 */
  private static final int ID_CARD_CHECK_INDEX = 17;

  /** 身份证前 17 位长度 */
  private static final int ID_CARD_BODY_LENGTH = 17;

  /** 银行卡号最小长度 */
  private static final int BANK_CARD_MIN_LENGTH = 13;

  /** 银行卡号最大长度 */
  private static final int BANK_CARD_MAX_LENGTH = 19;

  /** Luhn 算法模数 */
  private static final int LUHN_MOD = 10;

  /** 校验码计算模数 */
  private static final int CHECK_CODE_MOD = 11;

  private ValidationUtils() {
    throw new UnsupportedOperationException(
        "ValidationUtils is a utility class and cannot be instantiated");
  }

  // ==================== 手机号校验 ====================

  /**
   * 校验中国大陆手机号格式。
   *
   * <p>正则：1开头，第二位3-9，共 11 位数字。
   *
   * @param str 待校验字符串
   * @return 是否符合手机号格式，null 或空串返回 false
   */
  public static boolean isMobilePhone(@Nullable String str) {
    if (str == null || str.isEmpty()) {
      return false;
    }
    return MOBILE_PHONE_PATTERN.matcher(str).matches();
  }

  // ==================== 邮箱校验 ====================

  /**
   * 校验邮箱格式。
   *
   * @param str 待校验字符串
   * @return 是否符合邮箱格式，null 或空串返回 false
   */
  public static boolean isEmail(@Nullable String str) {
    if (str == null || str.isEmpty()) {
      return false;
    }
    return EMAIL_PATTERN.matcher(str).matches();
  }

  // ==================== 身份证号校验 ====================

  /**
   * 校验 18 位身份证号码（含校验码验证）。
   *
   * <p>遵循 GB 11643-1999 标准，使用 ISO 7064:1983.MOD 11-2 校验码算法。
   *
   * @param str 待校验字符串
   * @return 是否为合法身份证号，null 返回 false
   */
  public static boolean isIdCard18(@Nullable String str) {
    if (str == null || str.length() != ID_CARD_LENGTH) {
      return false;
    }
    String body = str.substring(0, ID_CARD_BODY_LENGTH);
    for (int i = 0; i < ID_CARD_BODY_LENGTH; i++) {
      char c = body.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    char expectedCheckCode = calculateIdCardCheckCode(body);
    char actualCheckCode = Character.toUpperCase(str.charAt(ID_CARD_CHECK_INDEX));
    return expectedCheckCode == actualCheckCode;
  }

  // ==================== 统一社会信用代码校验 ====================

  /**
   * 校验统一社会信用代码（GB 32100-2015）。
   *
   * <p>18 位字符，由数字和字母（不含 I/O/S/V/Z）组成，含校验码验证。
   *
   * @param str 待校验字符串
   * @return 是否为合法的统一社会信用代码，null 返回 false
   */
  public static boolean isSocialCreditCode(@Nullable String str) {
    if (str == null || str.length() != SOCIAL_CREDIT_CODE_LENGTH) {
      return false;
    }
    String code = str.toUpperCase();
    for (int i = 0; i < CHECK_CODE_INDEX; i++) {
      char c = code.charAt(i);
      if (SOCIAL_CREDIT_CODE_CHARSET.indexOf(c) == -1) {
        return false;
      }
    }
    char expectedCheckCode = calculateSocialCreditCheckCode(code);
    return expectedCheckCode == code.charAt(CHECK_CODE_INDEX);
  }

  // ==================== 车牌号校验 ====================

  /**
   * 校验中国车牌号格式（含新能源）。
   *
   * <p>支持普通车牌（如 京A12345）和新能源车牌（如 京AD12345）。
   *
   * @param str 待校验字符串
   * @return 是否符合车牌号格式，null 或空串返回 false
   */
  public static boolean isPlateNumber(@Nullable String str) {
    if (str == null || str.isEmpty()) {
      return false;
    }
    return PLATE_NUMBER_PATTERN.matcher(str).matches();
  }

  // ==================== 银行卡号校验 ====================

  /**
   * 校验银行卡号格式（Luhn 算法）。
   *
   * <p>卡号为 13-19 位纯数字，通过 Luhn 算法验证校验位。
   *
   * @param str 待校验字符串
   * @return 是否为合法银行卡号，null 返回 false
   */
  public static boolean isBankCard(@Nullable String str) {
    if (str == null || str.length() < BANK_CARD_MIN_LENGTH || str.length() > BANK_CARD_MAX_LENGTH) {
      return false;
    }
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return isValidLuhn(str);
  }

  // ==================== IPv4 校验 ====================

  /**
   * 校验 IPv4 地址格式。
   *
   * <p>四组 0-255 数字，点分十进制（如 192.168.1.1）。
   *
   * @param str 待校验字符串
   * @return 是否为合法 IPv4 地址，null 或空串返回 false
   */
  public static boolean isIPv4(@Nullable String str) {
    if (str == null || str.isEmpty()) {
      return false;
    }
    return IPV4_PATTERN.matcher(str).matches();
  }

  // ==================== 颜色值校验 ====================

  /**
   * 校验十六进制颜色值格式。
   *
   * <p>支持 #RGB 或 #RRGGBB 格式（如 #fff 或 #FF5733）。
   *
   * @param str 待校验字符串
   * @return 是否为合法十六进制颜色值，null 或空串返回 false
   */
  public static boolean isHexColor(@Nullable String str) {
    if (str == null || str.isEmpty()) {
      return false;
    }
    return HEX_COLOR_PATTERN.matcher(str).matches();
  }

  // ==================== 内部算法实现 ====================

  /**
   * 计算 18 位身份证号码的校验码（ISO 7064:1983.MOD 11-2）。
   *
   * @param body 身份证号前 17 位（必须全为数字）
   * @return 期望的校验码字符
   */
  private static char calculateIdCardCheckCode(String body) {
    int sum = 0;
    for (int i = 0; i < ID_CARD_BODY_LENGTH; i++) {
      int digit = body.charAt(i) - '0';
      sum += digit * ID_CARD_WEIGHTS[i];
    }
    int remainder = sum % CHECK_CODE_MOD;
    return ID_CARD_CHECK_CODES[remainder];
  }

  /**
   * 计算统一社会信用代码的校验码（GB 32100-2015）。
   *
   * @param code 18 位统一社会信用代码（大写，校验码位不参与计算）
   * @return 期望的校验码字符
   */
  private static char calculateSocialCreditCheckCode(String code) {
    int sum = 0;
    for (int i = 0; i < CHECK_CODE_INDEX; i++) {
      char c = code.charAt(i);
      int value = SOCIAL_CREDIT_CODE_CHARSET.indexOf(c);
      sum += value * SOCIAL_CREDIT_WEIGHTS[i];
    }
    int remainder = sum % CHECK_CODE_MOD;
    int checkValue = (CHECK_CODE_MOD - remainder) % CHECK_CODE_MOD;
    return SOCIAL_CREDIT_CODE_CHARSET.charAt(checkValue);
  }

  /**
   * 使用 Luhn 算法验证银行卡号校验位。
   *
   * @param cardNumber 纯数字银行卡号
   * @return 是否通过 Luhn 校验
   */
  private static boolean isValidLuhn(String cardNumber) {
    int sum = 0;
    boolean alternate = false;
    for (int i = cardNumber.length() - 1; i >= 0; i--) {
      int digit = cardNumber.charAt(i) - '0';
      if (alternate) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      alternate = !alternate;
    }
    return sum % LUHN_MOD == 0;
  }
}
