package com.njydsz.common.util.mask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.api.Experimental;

/**
 * 数据脱敏工具类
 *
 * <p>提供个人隐私数据的掩码/脱敏操作，用于日志打印、前端展示等场景。 涵盖手机号、身份证号、银行卡、邮箱、姓名等常见敏感信息的脱敏规则。
 *
 * <p>所有方法均为 null 安全：输入 null 时返回 null。
 *
 * <p>脱敏规则遵循等保 2.0 要求：保留必要信息以便识别，隐藏关键位数。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Experimental(value = "能力储备：数据脱敏工具；后续规划 @Mask 注解化与脱敏位可配置化", since = "26.10.01")
public final class MaskUtils {

  /** 标准脱敏占位符（全掩码场景：如 URL 参数值、Token 完整替换） */
  public static final String PLACEHOLDER = "***";

  /** 默认掩码字符 */
  private static final char MASK_CHAR = '*';

  /** 手机号保留前位数 */
  private static final int PHONE_PREFIX_LEN = 3;

  /** 手机号保留后位数 */
  private static final int PHONE_SUFFIX_LEN = 4;

  /** 身份证号保留前位数 */
  private static final int ID_CARD_PREFIX_LEN = 3;

  /** 身份证号保留后位数 */
  private static final int ID_CARD_SUFFIX_LEN = 4;

  /** 银行卡号保留前位数 */
  private static final int BANK_CARD_PREFIX_LEN = 4;

  /** 银行卡号保留后位数 */
  private static final int BANK_CARD_SUFFIX_LEN = 4;

  /** 邮箱前缀最少保留位数 */
  private static final int EMAIL_PREFIX_KEEP = 1;

  /** 姓名保留前位数 */
  private static final int NAME_PREFIX_LEN = 1;

  /** 默认掩码字符重复次数 */
  private static final int DEFAULT_MASK_REPEAT = 4;

  /** 最小可脱敏文本长度（至少保留前缀+后缀能覆盖的长度） */
  private static final int MIN_MASKABLE_LENGTH = 0;

  private MaskUtils() {
    throw new UnsupportedOperationException(
        "MaskUtils is a utility class and cannot be instantiated");
  }

  // ==================== 特定类型脱敏 ====================

  /**
   * 手机号脱敏，保留前 3 后 4 位，中间用 **** 替代。
   *
   * <p>如 "13812345678" → "138****5678"。不足 7 位时尽可能保留前后部分。
   *
   * @param phone 手机号
   * @return 脱敏后手机号，输入为 null 返回 null
   */
  public static String maskPhone(String phone) {
    if (phone == null) {
      return null;
    }
    return mask(phone, PHONE_PREFIX_LEN, PHONE_SUFFIX_LEN);
  }

  /**
   * 身份证号脱敏，保留前 3 后 4 位，中间用 * 替代。
   *
   * <p>如 "320102199001011234" → "320***********1234"。
   *
   * @param idCard 身份证号
   * @return 脱敏后身份证号，输入为 null 返回 null
   */
  public static String maskIdCard(String idCard) {
    if (idCard == null) {
      return null;
    }
    return mask(idCard, ID_CARD_PREFIX_LEN, ID_CARD_SUFFIX_LEN);
  }

  /**
   * 银行卡号脱敏，保留前 4 后 4 位，中间用 **** 替代。
   *
   * <p>如 "6222021234567890" → "6222****7890"。
   *
   * @param bankCard 银行卡号
   * @return 脱敏后银行卡号，输入为 null 返回 null
   */
  public static String maskBankCard(String bankCard) {
    if (bankCard == null) {
      return null;
    }
    return mask(bankCard, BANK_CARD_PREFIX_LEN, BANK_CARD_SUFFIX_LEN);
  }

  /**
   * 邮箱脱敏，保留第 1 字符和 @ 及域名部分。
   *
   * <p>如 "abc@qq.com" → "a****@qq.com"。如果没有 @ 符号，按通用脱敏处理。
   *
   * @param email 邮箱地址
   * @return 脱敏后邮箱，输入为 null 返回 null
   */
  public static String maskEmail(String email) {
    if (email == null) {
      return null;
    }
    int atIndex = email.indexOf('@');
    if (atIndex <= EMAIL_PREFIX_KEEP) {
      return mask(email, EMAIL_PREFIX_KEEP, email.length() - EMAIL_PREFIX_KEEP);
    }
    String prefix = email.substring(0, EMAIL_PREFIX_KEEP);
    String suffix = email.substring(atIndex);
    int maskLength = atIndex - EMAIL_PREFIX_KEEP;
    return prefix + repeatMask(maskLength) + suffix;
  }

  /**
   * 姓名脱敏，保留首字，其余用 * 替代。
   *
   * <p>如 "张三" → "张*"，"张三丰" → "张**"。单字姓名不脱敏。
   *
   * @param name 姓名
   * @return 脱敏后姓名，输入为 null 返回 null
   */
  public static String maskName(String name) {
    if (name == null) {
      return null;
    }
    if (name.length() <= NAME_PREFIX_LEN) {
      return name;
    }
    return name.substring(0, NAME_PREFIX_LEN) + repeatMask(name.length() - NAME_PREFIX_LEN);
  }

  // ==================== 通用脱敏 ====================

  /**
   * 通用脱敏方法：保留前 keepPrefix 和后 keepSuffix 字符，中间用 * 替代。
   *
   * <p>边界处理规则：
   *
   * <ul>
   *   <li>如果 keepPrefix + keepSuffix >= 文本长度，尽可能保留前后部分，不追加掩码
   *   <li>如果文本为 null，返回 null
   *   <li>长度不足以保留时，前缀尽量保留，剩余给后缀
   * </ul>
   *
   * @param text 待脱敏文本
   * @param keepPrefix 保留的前缀字符数（应 >= 0）
   * @param keepSuffix 保留的后缀字符数（应 >= 0）
   * @return 脱敏后文本，输入为 null 返回 null
   */
  public static String mask(String text, int keepPrefix, int keepSuffix) {
    if (text == null) {
      return null;
    }
    int length = text.length();
    if (length == 0) {
      return text;
    }
    int prefix = Math.max(0, keepPrefix);
    int suffix = Math.max(0, keepSuffix);
    if (prefix + suffix >= length) {
      return text;
    }
    String prefixStr = text.substring(0, prefix);
    String suffixStr = text.substring(length - suffix);
    int maskLength = length - prefix - suffix;
    return prefixStr + repeatMask(maskLength) + suffixStr;
  }

  /**
   * 根据字段名自动选择脱敏策略。
   *
   * <p>YDIZ-COMMON-029: 统一脱敏策略 —— 手机号前3后4、身份证前3后4。
   *
   * <p>支持的字段名（不区分大小写子串匹配）：
   *
   * <ul>
   *   <li>手机号：字段名包含 "mobile" 或 "phone" → {@link #maskPhone(String)}
   *   <li>身份证：字段名包含 "idcard" 或 "idnumber" → {@link #maskIdCard(String)}
   *   <li>银行卡：字段名包含 "bankcard" / "cardno" / "cardnumber" → {@link #maskBankCard(String)}
   *   <li>邮箱：字段名包含 "email" → {@link #maskEmail(String)}
   *   <li>姓名：字段名包含 "name" 或 "username" → {@link #maskName(String)}
   * </ul>
   *
   * <p>未匹配到任何策略时原样返回。
   *
   * @param fieldName 字段名（如 idCard、mobile、phone、bankCard、email、name）
   * @param value 字段值
   * @return 脱敏后的值（未匹配则原样返回）
   * @since 26.09.28
   */
  public static String maskByFieldName(String fieldName, String value) {
    if (fieldName == null || fieldName.isEmpty() || value == null || value.isEmpty()) {
      return value;
    }
    String lower = fieldName.toLowerCase();
    if (lower.contains("idcard") || lower.contains("idnumber")) {
      return maskIdCard(value);
    }
    if (lower.contains("mobile") || lower.contains("phone")) {
      return maskPhone(value);
    }
    if (lower.contains("bankcard") || lower.contains("cardno") || lower.contains("cardnumber")) {
      return maskBankCard(value);
    }
    if (lower.contains("email")) {
      return maskEmail(value);
    }
    if (lower.contains("name") || lower.contains("username")) {
      return maskName(value);
    }
    // 未匹配 → 原样返回
    return value;
  }

  // ==================== JSON 级脱敏 ====================

  /** 默认敏感字段名称匹配模式（不区分大小写、子串匹配） */
  private static final Set<String> DEFAULT_SENSITIVE_PATTERNS =
      Set.of(
          // 认证凭据类
          "password", "secret", "token", "credential", "apikey", "apisecret",
          "privatekey", "publickey", "salt", "auth", "sessionid", "refreshtoken",
          // 个人信息类
          "creditcard", "cardno", "cardnumber", "bankcard", "cvv", "pin",
          "idcard", "idnumber", "mobile", "phone", "email", "address",
          // 其他敏感信息
          "passport", "license", "accountno", "accountnumber");

  /**
   * 对 JSON 字符串中的敏感字段进行脱敏处理。
   *
   * <p>解析 JSON 为 Map 结构后递归遍历，命中敏感词列表的字段值将根据字段名类型自动选择脱敏策略
   * （手机号前3后4、身份证前3后4、银行卡前4后4、邮箱保留首字符、其他保留前后2位）。
   * 解析失败时降级返回原 JSON。
   *
   * <p>性能优化：先通过字符串子串预检快速判断 JSON 是否包含任何敏感词，
   * 若不含则直接跳过解析-修改-重序列化流程。
   *
   * @param json JSON 字符串
   * @param extraPatterns 额外敏感字段名称集合（与默认模式合并生效）；传入 null 则仅使用默认模式
   * @return 脱敏后的 JSON 字符串；解析失败或无需脱敏时返回原 JSON
   * @since 26.10.01
   */
  public static String maskJson(String json, Set<String> extraPatterns) {
    if (json == null || json.isEmpty()) {
      return json;
    }
    Set<String> combined = new HashSet<>(DEFAULT_SENSITIVE_PATTERNS);
    if (extraPatterns != null) {
      combined.addAll(extraPatterns);
    }
    // 快速路径：不包含任何敏感词则直接跳过
    if (!jsonContainsSensitiveKey(json, combined)) {
      return json;
    }
    try {
      Object parsed = YdszJson.fromJson(json, Object.class);
      Object masked = maskJsonValue(parsed, combined);
      return YdszJson.toJson(masked);
    } catch (Exception e) {
      return json;
    }
  }

  /**
   * 对 JSON 字符串中的敏感字段进行脱敏处理（仅使用默认敏感词）。
   *
   * @param json JSON 字符串
   * @return 脱敏后的 JSON 字符串
   * @since 26.10.01
   */
  public static String maskJson(String json) {
    return maskJson(json, null);
  }

  /**
   * 快速检查 JSON 字符串是否可能包含敏感字段（不解析 JSON）。
   *
   * @param json 待检查的 JSON 字符串
   * @param patterns 敏感词集合
   * @return 包含敏感词返回 true
   */
  private static boolean jsonContainsSensitiveKey(String json, Set<String> patterns) {
    String lowerJson = json.toLowerCase();
    for (String pattern : patterns) {
      if (lowerJson.contains(pattern.toLowerCase())) {
        return true;
      }
    }
    return false;
  }

  /**
   * 递归脱敏处理：根据字段名或 Map key 匹配敏感词。
   *
   * @param value 待处理值
   * @param patterns 敏感词集合
   * @return 脱敏后的值
   */
  private static Object maskJsonValue(Object value, Set<String> patterns) {
    if (value == null) {
      return null;
    }
    if (value instanceof String str) {
      return mask(str, 2, 2);
    }
    if (value instanceof Map<?, ?> mapObj) {
      Map<String, Object> result = new HashMap<>(mapObj.size());
      for (Map.Entry<?, ?> entry : mapObj.entrySet()) {
        String key = String.valueOf(entry.getKey());
        Object val = entry.getValue();
        if (isJsonSensitiveKey(key, patterns)) {
          result.put(key, maskByFieldName(key, String.valueOf(val)));
        } else {
          result.put(key, maskJsonValue(val, patterns));
        }
      }
      return result;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(item -> maskJsonValue(item, patterns)).toList();
    }
    return value;
  }

  /**
   * 判断字段名称是否为敏感字段（大小写不敏感、子串匹配）。
   *
   * @param key 字段名称
   * @param patterns 敏感词集合
   * @return 命中返回 true
   */
  private static boolean isJsonSensitiveKey(String key, Set<String> patterns) {
    if (key == null) {
      return false;
    }
    String lower = key.toLowerCase();
    for (String pattern : patterns) {
      if (lower.contains(pattern.toLowerCase())) {
        return true;
      }
    }
    return false;
  }

  // ==================== 内部方法 ====================

  /**
   * 生成指定重复次数的掩码字符串。
   *
   * @param count 重复次数
   * @return 重复掩码字符串，count <= 0 时返回空字符串
   */
  private static String repeatMask(int count) {
    if (count <= MIN_MASKABLE_LENGTH) {
      return "";
    }
    StringBuilder sb = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      sb.append(MASK_CHAR);
    }
    return sb.toString();
  }
}
