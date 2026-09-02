package com.njydsz.common.config.cli;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

import com.njydsz.common.json.YdszJson;

/**
 * Jasypt 配置加密 CLI 工具
 *
 * <p>提供命令行方式加密 / 解密配置值，生成 {@code ENC(密文)} 格式串，便于在 Nacos 配置中心中填写加密属性。
 *
 * <p>本工具为 CLI 入口（{@code main} 方法），{@link System#out} / {@link System#err} 是 CLI 标准输出通道，
 * 不属于运行时代码的日志场景，因此豁免 checkstyle 日志规范要求。
 *
 * // CHECKSTYLE.OFF: RegexpSinglelineJava - CLI 工具类，System.out/err 为标准输出通道，非日志场景
 *
 * <h3>用法</h3>
 *
 * <pre>{@code
 * # 加密
 * java -cp ydsz-common-config.jar com.njydsz.common.config.cli.ConfigCliTool encrypt "my-db-password" "master-password"
 *
 * # 输出: ENC(G8NkR6qVw2J3FpY0bXxC7A==)
 *
 * # 解密
 * java -cp ydsz-common-config.jar com.njydsz.common.config.cli.ConfigCliTool
 *     decrypt "G8NkR6qVw2J3FpY0bXxC7A==" "master-password"
 *
 * # 输出: my-db-password
 *
 * # 使用环境变量提供主密码（推荐）
 * export JASYPT_ENCRYPTOR_PASSWORD="master-password"
 * java -cp ydsz-common-config.jar com.njydsz.common.config.cli.ConfigCliTool encrypt "my-db-password"
 * }</pre>
 *
 * <h3>算法默认值</h3>
 *
 * <p>默认使用 {@code PBEWithHMACSHA512AndAES_256}（需 JCE unlimited strength， JDK 8u161+ 已内置），与项目 Nacos
 * 共享配置 {@code jasypt.encryptor.algorithm} 对齐。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ConfigCliTool {

  private static final String ENC_PREFIX = "ENC(";
  private static final String ENC_SUFFIX = ")";
  private static final String DEFAULT_ALGORITHM = "PBEWithHMACSHA512AndAES_256";
  private static final int DEFAULT_ITERATIONS = 1000;
  private static final int DEFAULT_POOL_SIZE = 4;

  /**
   * CLI 入口
   *
   * <p>参数格式：
   *
   * <ul>
   *   <li>{@code encrypt <plaintext> [masterPassword] [--format json]}
   *   <li>{@code decrypt <ciphertext> [masterPassword] [--format json]}
   * </ul>
   *
   * <p>如未提供 masterPassword，从环境变量 {@code JASYPT_ENCRYPTOR_PASSWORD} 读取。
   *
   * @param args 命令参数
   */
  public static void main(String[] args) {
    if (args.length < 2) {
      printUsage();
      System.exit(1);
    }

    String command = args[0].toLowerCase();
    String value = args[1];
    String masterPassword = null;
    OutputFormat format = OutputFormat.TEXT;

    // 解析可选参数
    for (int i = 2; i < args.length; i++) {
      if ("--format".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
        // CHECKSTYLE.OFF: ModifiedControlVariable — 命令行参数解析需跳过 format 值，语义必要
        format = OutputFormat.fromValue(args[++i]);
        // CHECKSTYLE.ON: ModifiedControlVariable
      } else if (masterPassword == null) {
        masterPassword = args[i];
      }
    }

    if (masterPassword == null || masterPassword.isBlank()) {
      masterPassword = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
    }

    if (masterPassword == null || masterPassword.isBlank()) {
      String errorMsg =
          "ERROR: Master password is required. "
              + "Provide as 3rd argument or set JASYPT_ENCRYPTOR_PASSWORD env var.";
      if (format == OutputFormat.JSON) {
        printJsonError(errorMsg);
      } else {
        System.err.println(errorMsg);
      }
      System.exit(2);
    }

    PooledPBEStringEncryptor encryptor = createEncryptor(masterPassword);

    switch (command) {
      case "encrypt" -> {
        String encrypted = encryptor.encrypt(value);
        String result = ENC_PREFIX + encrypted + ENC_SUFFIX;
        if (format == OutputFormat.JSON) {
          printJsonResult(command, DEFAULT_ALGORITHM, result);
        } else {
          System.out.println(result);
        }
      }
      case "decrypt" -> {
        String cipherText = stripEncWrapper(value);
        String decrypted = encryptor.decrypt(cipherText);
        if (format == OutputFormat.JSON) {
          printJsonResult(command, DEFAULT_ALGORITHM, decrypted);
        } else {
          System.out.println(decrypted);
        }
      }
      case "re-encrypt" -> {
        // 需要旧密码和新密码
        if (args.length < 3) {
          String errorMsg = "re-encrypt requires old-master-password and new-master-password";
          if (format == OutputFormat.JSON) {
            printJsonError(errorMsg);
          } else {
            System.err.println(errorMsg);
          }
          System.exit(2);
        }
        String oldPassword = null;
        String newPassword = null;
        for (int i = 2; i < args.length; i++) {
          if ("--format".equalsIgnoreCase(args[i])) {
            // CHECKSTYLE.OFF: ModifiedControlVariable — 命令行参数解析需跳过 format 值，语义必要
            i++; // skip format value
            // CHECKSTYLE.ON: ModifiedControlVariable
            continue;
          }
          if (oldPassword == null) {
            oldPassword = args[i];
          } else if (newPassword == null) {
            newPassword = args[i];
          }
        }
        if (newPassword == null) {
          String errorMsg = "re-encrypt requires both old-master-password and new-master-password";
          if (format == OutputFormat.JSON) {
            printJsonError(errorMsg);
          } else {
            System.err.println(errorMsg);
          }
          System.exit(2);
        }
        String cipherText = stripEncWrapper(value);
        String reEncrypted = reEncrypt(cipherText, oldPassword, newPassword);
        if (format == OutputFormat.JSON) {
          printJsonResult(command, DEFAULT_ALGORITHM, reEncrypted);
        } else {
          System.out.println(reEncrypted);
        }
      }
      default -> {
        String errorMsg = "Unknown command: " + command;
        if (format == OutputFormat.JSON) {
          printJsonError(errorMsg);
        } else {
          System.err.println(errorMsg);
          printUsage();
        }
        System.exit(1);
      }
    }
  }

  /** 输出 JSON 格式结果 */
  private static void printJsonResult(String operation, String algorithm, String result) {
    Map<String, Object> output = new LinkedHashMap<>(4);
    output.put("operation", operation);
    output.put("algorithm", algorithm);
    output.put("result", result);
    output.put("timestamp", Instant.now().toString());
    System.out.println(YdszJson.toJson(output));
  }

  /** 输出 JSON 格式错误 */
  private static void printJsonError(String message) {
    Map<String, Object> output = new LinkedHashMap<>(2);
    output.put("error", message);
    output.put("timestamp", Instant.now().toString());
    System.err.println(YdszJson.toJson(output));
  }

  /**
   * 创建 Jasypt 加密器
   *
   * @param masterPassword 主密码
   * @return 配置好的 PooledPBEStringEncryptor 实例
   */
  public static PooledPBEStringEncryptor createEncryptor(String masterPassword) {
    return createEncryptor(
        masterPassword, DEFAULT_ALGORITHM, DEFAULT_ITERATIONS, DEFAULT_POOL_SIZE);
  }

  /**
   * 创建 Jasypt 加密器（自定义参数）
   *
   * @param masterPassword 主密码
   * @param algorithm 加密算法
   * @param keyObtentionIterations 密钥派生迭代次数
   * @param poolSize 加密器池大小
   * @return 配置好的 PooledPBEStringEncryptor 实例
   */
  public static PooledPBEStringEncryptor createEncryptor(
      String masterPassword, String algorithm, int keyObtentionIterations, int poolSize) {
    PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
    SimpleStringPBEConfig config = new SimpleStringPBEConfig();
    config.setPassword(masterPassword);
    config.setAlgorithm(algorithm);
    config.setKeyObtentionIterations(String.valueOf(keyObtentionIterations));
    config.setPoolSize(String.valueOf(poolSize));
    config.setProviderName("SunJCE");
    config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
    config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
    config.setStringOutputType("base64");
    encryptor.setConfig(config);
    return encryptor;
  }

  private static String stripEncWrapper(String value) {
    if (value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX)) {
      return value.substring(ENC_PREFIX.length(), value.length() - ENC_SUFFIX.length());
    }
    return value;
  }

  /**
   * 使用新主密码重新加密
   *
   * @param cipherText 密文（可带 ENC() 包裹）
   * @param oldPassword 旧主密码
   * @param newPassword 新主密码
   * @return 重新加密后的 ENC(...) 格式串
   */
  public static String reEncrypt(String cipherText, String oldPassword, String newPassword) {
    PooledPBEStringEncryptor oldEncryptor = createEncryptor(oldPassword);
    PooledPBEStringEncryptor newEncryptor = createEncryptor(newPassword);
    String plaintext = oldEncryptor.decrypt(stripEncWrapper(cipherText));
    return ENC_PREFIX + newEncryptor.encrypt(plaintext) + ENC_SUFFIX;
  }

  private static void printUsage() {
    System.out.println("YDSZ Config Encrypt CLI Tool");
    System.out.println();
    System.out.println("Usage:");
    System.out.println("  encrypt <plaintext> [masterPassword] [--format json|text]");
    System.out.println("  decrypt <ciphertext> [masterPassword] [--format json|text]");
    System.out.println(
        "  re-encrypt <ciphertext> <oldPassword> <newPassword> [--format json|text]");
    System.out.println();
    System.out.println("Options:");
    System.out.println("  --format json|text    Output format (default: text)");
    System.out.println();
    System.out.println("Commands:");
    System.out.println("  encrypt       Encrypt plaintext, output ENC(ciphertext)");
    System.out.println("  decrypt       Decrypt ENC(ciphertext) or raw ciphertext");
    System.out.println(
        "  re-encrypt    Re-encrypt ciphertext with new master password (key rotation)");
    System.out.println();
    System.out.println(
        "If masterPassword is omitted, reads from JASYPT_ENCRYPTOR_PASSWORD env var.");
    System.out.println("Default algorithm: " + DEFAULT_ALGORITHM);
  }

  /** 输出格式枚举 */
  private enum OutputFormat {
    TEXT("text"),
    JSON("json");

    private final String value;

    OutputFormat(String value) {
      this.value = value;
    }

    static OutputFormat fromValue(String value) {
      for (OutputFormat format : values()) {
        if (format.value.equalsIgnoreCase(value)) {
          return format;
        }
      }
      return TEXT;
    }
  }
}
