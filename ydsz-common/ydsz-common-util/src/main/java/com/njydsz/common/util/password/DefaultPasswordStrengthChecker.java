package com.njydsz.common.util.password;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认密码强度校验器实现。
 *
 * <p>提供基于长度、字符多样性、常见模式的密码强度评分逻辑， 并支持中英文描述消息国际化。作为 SPI 的默认实现，可被业务方自定义实现覆盖。
 *
 * <p><b>评分规则（总分 10 分）：</b>
 *
 * <ul>
 *   <li>长度 ≥ 8: +1，≥ 12: +1，≥ 16: +2
 *   <li>包含小写字母: +1
 *   <li>包含大写字母: +1
 *   <li>包含数字: +1
 *   <li>包含特殊字符: +1
 *   <li>包含连续字符（如 abc、123）: -1
 *   <li>包含重复字符（如 aaa、111）: -1
 * </ul>
 *
 * <p>等级映射：
 *
 * <ul>
 *   <li>0-2: VERY_WEAK
 *   <li>3-4: WEAK
 *   <li>5-6: MEDIUM
 *   <li>7-8: STRONG
 *   <li>9-10: VERY_STRONG
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see PasswordStrengthChecker
 */
public class DefaultPasswordStrengthChecker implements PasswordStrengthChecker {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultPasswordStrengthChecker.class);

  /** Bundle 基础名，用于国际化消息查找 */
  private static final String BUNDLE_BASE = "com.njydsz.common.util.password.messages";

  /**
   * 内置常见弱密码集合（Top 100+ 高频猜测密码）。
   *
   * <p>数据来源：RockYou 泄密库、SecLists、HaveIBeenPwned Top-1000 子集。 外部可通过 {@link
   * #loadWeakPasswordsFromClasspath(String)} 加载自定义字典覆盖或扩充。
   */
  private static final Set<String> COMMON_WEAK_PASSWORDS =
      new HashSet<>(
          Arrays.asList(
              // --- Top 50 全球最常见 ---
              "123456",
              "password",
              "12345678",
              "qwerty",
              "123456789",
              "letmein",
              "1234567",
              "football",
              "iloveyou",
              "admin",
              "welcome",
              "monkey",
              "login",
              "abc123",
              "111111",
              "123123",
              "password123",
              "1234",
              "baseball",
              "qwerty123",
              "master",
              "dragon",
              "sunshine",
              "princess",
              "shadow",
              "superman",
              "michael",
              "ashley",
              "12345",
              "charlie",
              "donald",
              "passw0rd",
              "qwerty1",
              "Mustang",
              "access",
              "loveme",
              "hello",
              "test",
              "starwars",
              "solo",
              "jesus",
              "freedom",
              "whatever",
              "trustno1",
              "hottie",
              "maverick",
              "phoenix",
              "cookie",
              "summer",
              "Batman",
              // --- 键盘模式 ---
              "qwertyuiop",
              "asdfghjkl",
              "zxcvbnm",
              "qwerty12345",
              "1q2w3e4r",
              "1qaz2wsx",
              "qazwsx",
              "qwe123",
              "1q2w3e",
              // --- 常见英文词汇 ---
              "computer",
              "internet",
              "hunter",
              "hunter2",
              "killer",
              "pepper",
              "ranger",
              "thomas",
              "robert",
              "jordan",
              "daniel",
              "jessica",
              "hannah",
              "george",
              "andrea",
              "joshua",
              "nicole",
              "robert",
              "harley",
              "samson",
              // --- 中文拼音 Top ---
              "woaini",
              "woaini1314",
              "zhangsan",
              "xiaoming",
              "qwerty123",
              "iloveu",
              "5201314",
              "888888",
              "88888888",
              "666666",
              // --- 常见后缀模式 ---
              "password1",
              "password12",
              "password!",
              "123456a",
              "abc123456",
              "a123456",
              "Aa123456",
              "123456789a",
              "qq123456",
              "aa123456",
              "Admin123",
              "Passw0rd!",
              // --- 服务相关 ---
              "root",
              "toor",
              "guest",
              "adm",
              "mysql",
              "oracle",
              "postgres",
              "nginx",
              "apache",
              "tomcat"));

  /**
   * 默认单例（无状态、线程安全，可复用）。
   *
   * <p>通过 {@code INSTANCE} 避免重复创建；由于 Score 规则是纯函数（无共享可变字段）， 所有调用方可安全共享同一实例。
   *
   * <p>首次构建时若 classpath 存在 {@code /weak-passwords.txt} 则自动合并加载。
   */
  public static final DefaultPasswordStrengthChecker INSTANCE =
      new DefaultPasswordStrengthChecker(true);

  /** 弱密码集合（实例维度，可替换扩展） */
  private final Set<String> weakPasswords;

  /**
   * 私有构造器（单例模式）。
   *
   * @param loadExtension 是否加载扩展字典文件
   */
  private DefaultPasswordStrengthChecker(boolean loadExtension) {
    Set<String> passwords = new HashSet<>(COMMON_WEAK_PASSWORDS);
    if (loadExtension) {
      Set<String> external = loadWeakPasswordsFromClasspath("/weak-passwords.txt");
      if (!external.isEmpty()) {
        passwords.addAll(external);
      }
    }
    this.weakPasswords = Collections.unmodifiableSet(passwords);
  }

  /**
   * 从 classpath 加载外部弱密码字典。
   *
   * <p>每行一条密码，忽略空行和 # 开头的注释行。 文件路径为 classpath 根路径下相对路径。
   *
   * @param classpathResource classpath 资源路径（如 {@code "/weak-passwords.txt"}）
   * @return 加载的密码集合；文件不存在时返回空 Set
   * @since 1.0.0
   */
  public static Set<String> loadWeakPasswordsFromClasspath(String classpathResource) {
    Set<String> result = new HashSet<>();
    try (InputStream is =
        DefaultPasswordStrengthChecker.class.getResourceAsStream(classpathResource)) {
      if (is == null) {
        LOG.debug("弱密码字典资源 {} 不存在，跳过加载", classpathResource);
        return result;
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
            result.add(trimmed.toLowerCase(Locale.ROOT));
          }
        }
      }
      LOG.info("从 {} 加载了 {} 条弱密码字典", classpathResource, result.size());
    } catch (IOException e) {
      LOG.warn("加载弱密码字典 {} 失败: {}", classpathResource, e.getMessage());
    }
    return result;
  }

  /**
   * 获取当前生效的弱密码集合（不可变视图）。
   *
   * @return 弱密码集合
   * @since 1.0.0
   */
  public Set<String> getWeakPasswords() {
    return weakPasswords;
  }

  @Override
  public PasswordStrengthLevel check(String password) {
    if (password == null || password.isEmpty()) {
      return PasswordStrengthLevel.VERY_WEAK;
    }

    int score = calculateScore(password);

    if (score <= 2) {
      return PasswordStrengthLevel.VERY_WEAK;
    } else if (score <= 4) {
      return PasswordStrengthLevel.WEAK;
    } else if (score <= 6) {
      return PasswordStrengthLevel.MEDIUM;
    } else if (score <= 8) {
      return PasswordStrengthLevel.STRONG;
    } else {
      return PasswordStrengthLevel.VERY_STRONG;
    }
  }

  /**
   * 计算密码强度评分。
   *
   * @param password 明文密码
   * @return 评分（可能为负数表示极弱）
   */
  private int calculateScore(String password) {
    int score = 0;
    int length = password.length();

    // 长度得分
    if (length >= 8) {
      score++;
    }
    if (length >= 12) {
      score++;
    }
    if (length >= 16) {
      score += 2;
    }

    // 字符多样性
    boolean hasLower = false;
    boolean hasUpper = false;
    boolean hasDigit = false;
    boolean hasSpecial = false;
    for (char c : password.toCharArray()) {
      if (Character.isLowerCase(c)) {
        hasLower = true;
      } else if (Character.isUpperCase(c)) {
        hasUpper = true;
      } else if (Character.isDigit(c)) {
        hasDigit = true;
      } else {
        hasSpecial = true;
      }
    }
    if (hasLower) {
      score++;
    }
    if (hasUpper) {
      score++;
    }
    if (hasDigit) {
      score++;
    }
    if (hasSpecial) {
      score++;
    }

    // 常见弱密码惩罚
    if (weakPasswords.contains(password.toLowerCase(Locale.ROOT))) {
      score -= 5;
    }

    // 连续字符惩罚（如 abc、123）
    if (hasConsecutiveChars(password)) {
      score--;
    }

    // 重复字符惩罚（如 aaa、111）
    if (hasRepeatedChars(password)) {
      score--;
    }

    return Math.max(score, 0);
  }

  /**
   * 检测连续字符（长度 >= 3，如 abc、123、xyz）。
   *
   * @param password password
   * @return 处理结果
   */
  private boolean hasConsecutiveChars(String password) {
    if (password == null || password.length() < 3) {
      return false;
    }
    for (int i = 0; i <= password.length() - 3; i++) {
      char c1 = password.charAt(i);
      char c2 = password.charAt(i + 1);
      char c3 = password.charAt(i + 2);
      if (c2 == c1 + 1 && c3 == c2 + 1) {
        return true;
      }
    }
    return false;
  }

  /** 检测重复字符（长度 >= 3，如 aaa、111）。 */
  private boolean hasRepeatedChars(String password) {
    if (password == null || password.length() < 3) {
      return false;
    }
    for (int i = 0; i <= password.length() - 3; i++) {
      char c1 = password.charAt(i);
      if (c1 == password.charAt(i + 1) && c1 == password.charAt(i + 2)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String describe(PasswordStrengthLevel level, Locale locale) {
    if (level == null) {
      return "";
    }
    Locale targetLocale = locale != null ? locale : Locale.getDefault();
    try {
      ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE, targetLocale);
      return bundle.getString("password.strength." + level.name().toLowerCase());
    } catch (Exception e) {
      // 回退默认描述
      return defaultDescribe(level, targetLocale);
    }
  }

  @Override
  public String suggest(String password, Locale locale) {
    if (password == null) {
      return getMessage("password.suggest.null", locale);
    }
    StringBuilder suggestion = new StringBuilder();
    if (password.length() < 8) {
      suggestion.append(getMessage("password.suggest.length", locale)).append(" ");
    }
    boolean hasLower = false;
    boolean hasUpper = false;
    boolean hasDigit = false;
    boolean hasSpecial = false;
    for (char c : password.toCharArray()) {
      if (Character.isLowerCase(c)) {
        hasLower = true;
      } else if (Character.isUpperCase(c)) {
        hasUpper = true;
      } else if (Character.isDigit(c)) {
        hasDigit = true;
      } else {
        hasSpecial = true;
      }
    }
    if (!hasLower || !hasUpper) {
      suggestion.append(getMessage("password.suggest.case", locale)).append(" ");
    }
    if (!hasDigit) {
      suggestion.append(getMessage("password.suggest.digit", locale)).append(" ");
    }
    if (!hasSpecial) {
      suggestion.append(getMessage("password.suggest.special", locale)).append(" ");
    }
    if (weakPasswords.contains(password.toLowerCase(Locale.ROOT))) {
      suggestion.append(getMessage("password.suggest.common", locale)).append(" ");
    }
    return suggestion.toString().trim();
  }

  /** 默认描述（ResourceBundle 缺失时的回退）。 */
  private String defaultDescribe(PasswordStrengthLevel level, Locale locale) {
    boolean isChinese = locale != null && Locale.CHINESE.getLanguage().equals(locale.getLanguage());
    switch (level) {
      case VERY_WEAK:
        return isChinese ? "极弱" : "Very Weak";
      case WEAK:
        return isChinese ? "弱" : "Weak";
      case MEDIUM:
        return isChinese ? "中等" : "Medium";
      case STRONG:
        return isChinese ? "强" : "Strong";
      case VERY_STRONG:
        return isChinese ? "极强" : "Very Strong";
      default:
        return "";
    }
  }

  /** 获取国际化消息，找不到时返回键名。 */
  private String getMessage(String key, Locale locale) {
    try {
      ResourceBundle bundle =
          ResourceBundle.getBundle(BUNDLE_BASE, locale != null ? locale : Locale.getDefault());
      return bundle.getString(key);
    } catch (Exception e) {
      // 默认英文
      switch (key) {
        case "password.suggest.length":
          return "Password should be at least 8 characters";
        case "password.suggest.case":
          return "Add both uppercase and lowercase letters";
        case "password.suggest.digit":
          return "Include at least one digit";
        case "password.suggest.special":
          return "Include at least one special character";
        case "password.suggest.common":
          return "Avoid common passwords";
        default:
          return key;
      }
    }
  }
}
