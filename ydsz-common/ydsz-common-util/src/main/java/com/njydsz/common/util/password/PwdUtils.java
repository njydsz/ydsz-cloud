package com.njydsz.common.util.password;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
  // CHECKSTYLE.ON: RegexpSinglelineJava

import com.njydsz.common.util.api.Experimental;
import com.njydsz.common.util.security.DigestUtils;

/**
 * 用户密码安全工具类（纯 JDK 实现 + Spring Security BCrypt）
 *
 * <p>支持多种密码加密方式：BCrypt（推荐）、PBKDF2（推荐）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Experimental("零采用；密码策略 SPI 略复杂，待简化")
@Slf4j
public final class PwdUtils {

  /** BCrypt 格式正则 */
  private static final Pattern BCRYPT_PATTERN =
      Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

  /**
   * Spring Security BCrypt 编码器（线程安全）
   *
   * <p>强度 12 对应 2^12 = 4096 轮哈希计算，OWASP 推荐至少 10。 如需调整强度，请通过配置注入新的 BCryptPasswordEncoder 实例。
   */
  private static final BCryptPasswordEncoder BCRYPT_ENCODER = new BCryptPasswordEncoder(12);

  /** 私有构造器，工具类不允许实例化。 */
  private PwdUtils() {
    throw new UnsupportedOperationException(
        "PwdUtils is a utility class and cannot be instantiated");
  }

  /**
   * PBKDF2 默认迭代次数的配置键。
   *
   * <p>优先级：系统属性 > 环境变量 > 默认值（600000）。
   *
   * <ul>
   *   <li>系统属性：{@code ydsz.util.password.pbkdf2.iterations}
   *   <li>环境变量：{@code YDSZ_UTIL_PASSWORD_PBKDF2_ITERATIONS}
   * </ul>
   *
   * <p>迭代次数存储在编码密码中（salt:iterations:hash）， 验证旧密码时使用存储的迭代次数，不受默认值变化影响。
   *
   * @since 26.09.01
   */
  private static final String ITERATIONS_CONFIG_KEY = "ydsz.util.password.pbkdf2.iterations";

  private static final String ITERATIONS_ENV_KEY = "YDSZ_UTIL_PASSWORD_PBKDF2_ITERATIONS";

  /**
   * PBKDF2 默认迭代次数。
   *
   * <p>OWASP 2023 推荐 PBKDF2-SHA256 至少 600000 次迭代。 支持通过系统属性或环境变量覆盖（详见 {@link
   * #ITERATIONS_CONFIG_KEY}）。 迭代次数存储在编码密码中（salt:iterations:hash）， 验证旧密码时使用存储的迭代次数，不受此值变化影响。
   */
  private static final int DEFAULT_ITERATIONS = resolveDefaultIterations();

  /**
   * 解析默认迭代次数。
   *
   * <p>优先级：系统属性 > 环境变量 > 硬编码默认 600000。 解析失败（非数字、超出范围）时回退到 OWASP 推荐值 600000。
   *
   * @return 有效的迭代次数（≥ 1000）
   */
  private static int resolveDefaultIterations() {
    String value = System.getProperty(ITERATIONS_CONFIG_KEY);
    if (value == null || value.isEmpty()) {
      value = System.getenv(ITERATIONS_ENV_KEY);
    }
    if (value != null && !value.isEmpty()) {
      try {
        int parsed = Integer.parseInt(value);
        if (parsed >= 1000) {
          return parsed;
        }
        log.warn("配置的 PBKDF2 迭代次数 {} 过小（最低 1000），使用默认值 600000", parsed);
      } catch (NumberFormatException e) {
        log.warn("PBKDF2 迭代次数配置 '{}' 格式无效，使用默认值 600000", value);
      }
    }
    return 600000;
  }

  /**
   * 获取当前生效的 PBKDF2 默认迭代次数。
   *
   * @return 当前默认迭代次数（≥ 1000）
   * @since 26.09.01
   */
  public static int getDefaultIterations() {
    return DEFAULT_ITERATIONS;
  }

  /** 默认盐值长度（16 字节） */
  private static final int DEFAULT_SALT_LENGTH = 16;

  /**
   * 检查 BCrypt 依赖是否可用。
   *
   * <p>BCrypt 依赖 spring-security-crypto，未引入时返回 false。 调用 {@link #hashPasswordBCrypt(String)} /
   * {@link #verifyPasswordBCrypt(String, String)} 前可先检查，避免 {@link NoClassDefFoundError}。
   *
   * @return BCrypt 可用返回 true
   * @since 26.09.01
   */
  public static boolean isBcryptAvailable() {
    try {
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
      Class.forName("org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder");
  // CHECKSTYLE.ON: RegexpSinglelineJava
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * 使用 BCrypt 哈希密码。
   *
   * <p><b>依赖要求</b>：classpath 需包含 spring-security-crypto。 未引入时抛出 {@link IllegalStateException}
   * 并附引入指引。
   *
   * @param rawPassword 原始密码（不为 null）
   * @return BCrypt 哈希值
   * @throws IllegalStateException spring-security-crypto 未引入时抛出
   */
  public static String hashPasswordBCrypt(String rawPassword) {
    if (!isBcryptAvailable()) {
      throw new IllegalStateException(
          "BCrypt 需要 spring-security-crypto 依赖。请在 pom.xml 中添加：\n"
              + "<dependency>\n"
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
              + "  <groupId>org.springframework.security</groupId>\n"
  // CHECKSTYLE.ON: RegexpSinglelineJava
              + "  <artifactId>spring-security-crypto</artifactId>\n"
              + "</dependency>");
    }
    return BCRYPT_ENCODER.encode(rawPassword);
  }

  /**
   * 验证 BCrypt 密码。
   *
   * <p><b>依赖要求</b>：classpath 需包含 spring-security-crypto。
   *
   * @param rawPassword 原始密码
   * @param hashedPassword BCrypt 哈希值
   * @return 匹配返回 true
   * @throws IllegalStateException spring-security-crypto 未引入时抛出
   */
  public static boolean verifyPasswordBCrypt(String rawPassword, String hashedPassword) {
    if (!isBcryptAvailable()) {
      throw new IllegalStateException(
          "BCrypt 需要 spring-security-crypto 依赖。请在 pom.xml 中添加：\n"
              + "<dependency>\n"
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
              + "  <groupId>org.springframework.security</groupId>\n"
  // CHECKSTYLE.ON: RegexpSinglelineJava
              + "  <artifactId>spring-security-crypto</artifactId>\n"
              + "</dependency>");
    }
    return BCRYPT_ENCODER.matches(rawPassword, hashedPassword);
  }

  /**
   * 判断密码是否为 BCrypt 格式。
   *
   * <p>纯 JDK 正则判断，无需 spring-security-crypto 依赖。
   *
   * @param password 密码字符串
   * @return 是 BCrypt 格式返回 true
   */
  public static boolean isBCryptFormat(String password) {
    return password != null && BCRYPT_PATTERN.matcher(password).matches();
  }

  /**
   * 使用 PBKDF2 加密密码（推荐用于生产环境）
   *
   * @param password 密码字符数组
   * @param saltHex 十六进制盐值
   * @return 加密结果（salt:iterations:hash 格式）
   */
  public static String encodePBKDF2(char[] password, String saltHex) {
    return encodePBKDF2(password, saltHex, DEFAULT_ITERATIONS);
  }

  /**
   * 使用 PBKDF2 加密密码（可指定迭代次数）
   *
   * @param password 密码字符数组
   * @param saltHex 十六进制盐值
   * @param iterations 迭代次数
   * @return 加密结果（salt:iterations:hash 格式）
   */
  public static String encodePBKDF2(char[] password, String saltHex, int iterations) {
    if (saltHex == null || saltHex.isEmpty()) {
      throw new IllegalArgumentException("Salt must not be empty");
    }
    if (password == null || password.length == 0) {
      throw new IllegalArgumentException("Password must not be empty");
    }

    byte[] salt = HexFormat.of().parseHex(saltHex);
    byte[] hash = DigestUtils.pbkdf2(password, salt, iterations, 256);
    return saltHex + ":" + iterations + ":" + HexFormat.of().formatHex(hash);
  }

  /**
   * 使用 PBKDF2 加密密码（自动生成盐值）
   *
   * @param password 密码字符数组
   * @return 加密结果（salt:iterations:hash 格式）
   */
  public static String encodePBKDF2WithAutoSalt(char[] password) {
    return encodePBKDF2WithAutoSalt(password, DEFAULT_ITERATIONS);
  }

  /**
   * 使用 PBKDF2 加密密码（自动生成盐值和指定迭代次数）
   *
   * @param password 密码字符数组
   * @param iterations 迭代次数
   * @return 加密结果（salt:iterations:hash 格式）
   */
  public static String encodePBKDF2WithAutoSalt(char[] password, int iterations) {
    if (password == null || password.length == 0) {
      throw new IllegalArgumentException("Password must not be empty");
    }

    String saltHex = DigestUtils.genSaltHex(DEFAULT_SALT_LENGTH);
    return encodePBKDF2(password, saltHex, iterations);
  }

  /**
   * 验证 PBKDF2 加密的密码
   *
   * @param password 明文密码
   * @param encodedPassword 加密后的密码（格式：salt:iterations:hash）
   * @return 是否匹配
   */
  public static boolean verifyPBKDF2(String password, String encodedPassword) {
    if (password == null
        || password.isEmpty()
        || encodedPassword == null
        || encodedPassword.isEmpty()) {
      return false;
    }

    String[] parts = encodedPassword.split(":");
    if (parts.length != 3) {
      return false;
    }

    try {
      String saltHex = parts[0];
      int iterations = Integer.parseInt(parts[1]);
      // 防御恶意高迭代次数导致 CPU DoS（如 Integer.MAX_VALUE）。
      // 上限 10_000_000 远高于 OWASP 推荐 600000，兼顾合法旧数据与安全性。
      if (iterations < 1 || iterations > 10_000_000) {
        throw new IllegalArgumentException("iterations 超出允许范围 [1, 10000000]");
      }
      String expectedHash = parts[2];

      byte[] salt = HexFormat.of().parseHex(saltHex);
      byte[] actualHash = DigestUtils.pbkdf2(password.toCharArray(), salt, iterations, 256);
      String actualHashHex = HexFormat.of().formatHex(actualHash);

      return MessageDigest.isEqual(
          expectedHash.getBytes(StandardCharsets.UTF_8),
          actualHashHex.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 生成随机盐值
   *
   * @param length 盐值长度（字节）
   * @return 十六进制盐值字符串
   */
  public static String generateSalt(int length) {
    return DigestUtils.genSaltHex(length);
  }

  /**
   * 生成默认长度的随机盐值
   *
   * @return 十六进制盐值字符串
   */
  public static String generateSalt() {
    return generateSalt(DEFAULT_SALT_LENGTH);
  }

  /**
   * 密码强度检查器（ServiceLoader SPI 懒加载）。
   *
   * <p>通过 {@code META-INF/services/com.njydsz.common.util.password.PasswordStrengthChecker}
   * 注册的自定义实现，可被第三方覆盖以适配企业密码策略。
   */
  private static final AtomicReference<PasswordStrengthChecker> STRENGTH_CHECKER =
      new AtomicReference<>();

  /**
   * 获取密码强度检查器实例。
   *
   * <p>优先通过 {@link ServiceLoader} 发现自定义注册实现； 若未注册则返回 {@link DefaultPasswordStrengthChecker} 单例。
   * 结果被缓存为 volatile 字段，ServiceLoader 开销仅首次加载发生。
   *
   * @return 密码强度检查器（不为 null）
   */
  public static PasswordStrengthChecker getPasswordStrengthChecker() {
    PasswordStrengthChecker checker = STRENGTH_CHECKER.get();
    if (checker != null) {
      return checker;
    }
    ServiceLoader<PasswordStrengthChecker> loader =
        ServiceLoader.load(PasswordStrengthChecker.class);
    PasswordStrengthChecker found = null;
    for (PasswordStrengthChecker impl : loader) {
      found = impl;
      break; // 取第一个注册实现
    }
    PasswordStrengthChecker created =
        (found != null) ? found : DefaultPasswordStrengthChecker.INSTANCE;
    return STRENGTH_CHECKER.compareAndSet(null, created) ? created : STRENGTH_CHECKER.get();
  }

  /**
   * 检查密码强度（五档精细评分，返回新 API Level 枚举）。
   *
   * <p>内部委托给 SPI {@link #getPasswordStrengthChecker()}。
   *
   * @param password 密码（可为 null）
   * @return 密码强度级别；null 或空串返回 VERY_WEAK
   * @since 26.09.01
   */
  public static PasswordStrengthChecker.PasswordStrengthLevel checkPasswordStrengthLevel(
      String password) {
    return getPasswordStrengthChecker().check(password);
  }

  /**
   * 获取密码强度描述（国际化支持）。
   *
   * @param password 密码
   * @param locale 语言区域（{@link Locale#CHINESE} / {@link Locale#ENGLISH} 等）
   * @return 本地化描述字符串（弱/中等/强 等）
   * @since 26.09.01
   */
  public static String describePasswordStrength(String password, Locale locale) {
    PasswordStrengthChecker.PasswordStrengthLevel level =
        getPasswordStrengthChecker().check(password);
    return getPasswordStrengthChecker().describe(level, locale);
  }

  /**
   * 获取密码改进建议（国际化支持）。
   *
   * @param password 当前密码（可为 null）
   * @param locale 语言区域
   * @return 建议文本（可能为空；不会返回 null）
   * @since 26.09.01
   */
  public static String suggestPasswordImprovement(String password, Locale locale) {
    return getPasswordStrengthChecker().suggest(password, locale);
  }
}
