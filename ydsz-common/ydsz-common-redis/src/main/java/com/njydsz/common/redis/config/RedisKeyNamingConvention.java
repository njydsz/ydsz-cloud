package com.njydsz.common.redis.config;

import java.util.regex.Pattern;

/**
 * Redis Key 命名规范校验工具
 *
 * <p>统一约束全项目 Redis Key 的命名格式，避免 Key 命名混乱导致的可维护性问题。
 *
 * <p><b>命名规范：</b>
 *
 * <ul>
 *   <li>必须使用小写字母、数字、下划线、连字符和冒号
 *   <li>推荐结构：{@code {业务域}:{子域}:{标识符}}，如 {@code user:info:10086}、{@code order:status:20240101}
 *   <li>禁止空格、特殊字符和中文
 *   <li>Key 长度建议控制在 64 字符以内
 *   <li>临时性数据（锁、限流、验证码）应使用独立业务域前缀（{@code lock:}、{@code ratelimit:}、{@code captcha:}）
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>单元测试中校验 Key 命名合规性
 *   <li>Code Review 时人工对照检查
 *   <li>配合 Redis MONITOR / SCAN 做命名规范巡检
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 校验 Key 是否合规
 * boolean valid = RedisKeyNamingConvention.isValid("user:info:10086");   // true
 * boolean invalid = RedisKeyNamingConvention.isValid("User_Info_10086"); // false
 *
 * // 批量扫描并找出不合规的 Key
 * List<String> violations = RedisKeyNamingConvention.filterInvalidKeys(allKeys);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RedisKeysEnum
 * @see RedisKeyFormatter
 */
public final class RedisKeyNamingConvention {

  /** Key 最大长度限制 */
  public static final int MAX_KEY_LENGTH = 64;

  /**
   * 合法 Key 正则表达式
   *
   * <p>允许多个小写段，用冒号 ":" 分隔，每段由小写字母、数字、下划线、连字符组成。
   *
   * <p>示例匹配：{@code user:info:10086}、{@code ratelimit:api:login:10086}、{@code
   * captcha:sms:13800138000}
   */
  private static final Pattern VALID_KEY_PATTERN =
      Pattern.compile("^[a-z0-9_-]+(?::[a-z0-9_-]+)*$");

  /** 推荐的业务域前缀（与 RedisKeysEnum 保持一致） */
  public static final String[] RECOMMENDED_DOMAINS = {
    "user", "login", "captcha", "oauth", "sys", "org",
    "wf", "msg", "log", "audit", "lock", "ratelimit",
    "idempotent", "blacklist", "doc", "seq", "cache"
  };

  /** 私有构造，禁止实例化 */
  private RedisKeyNamingConvention() {
    throw new AssertionError("工具类禁止实例化");
  }

  /**
   * 校验 Key 是否符合命名规范
   *
   * @param key 原始 Key（不含业务前缀如 "ydsz:"）
   * @return true 表示符合规范
   */
  public static boolean isValid(String key) {
    if (key == null || key.isEmpty()) {
      return false;
    }
    if (key.length() > MAX_KEY_LENGTH) {
      return false;
    }
    return VALID_KEY_PATTERN.matcher(key).matches();
  }

  /**
   * 校验 Key 并返回违规原因
   *
   * @param key 原始 Key
   * @return 合规时返回 null；不合规时返回具体原因描述
   */
  public static String validateWithReason(String key) {
    if (key == null || key.isEmpty()) {
      return "Key 不能为空";
    }
    if (key.length() > MAX_KEY_LENGTH) {
      return String.format("Key 长度超过 %d 字符限制：当前长度 %d", MAX_KEY_LENGTH, key.length());
    }
    if (!VALID_KEY_PATTERN.matcher(key).matches()) {
      return "Key 只能包含小写字母、数字、下划线、连字符和冒号，且冒号不能出现在首尾或连续出现";
    }
    return null;
  }

  /**
   * 判断 Key 是否使用了推荐的业务域前缀
   *
   * @param key 原始 Key
   * @return true 表示使用了推荐的业务域前缀
   */
  public static boolean hasRecommendedDomain(String key) {
    if (key == null || key.isEmpty()) {
      return false;
    }
    for (String domain : RECOMMENDED_DOMAINS) {
      if (key.startsWith(domain + ":")) {
        return true;
      }
    }
    return false;
  }

  /**
   * 计算 Key 的层级深度（按冒号分隔）
   *
   * @param key 原始 Key
   * @return 层级深度；key 非法时返回 0
   */
  public static int depth(String key) {
    if (key == null || key.isEmpty()) {
      return 0;
    }
    int depth = 1;
    for (int i = 0; i < key.length(); i++) {
      if (key.charAt(i) == ':') {
        depth++;
      }
    }
    return depth;
  }
}
