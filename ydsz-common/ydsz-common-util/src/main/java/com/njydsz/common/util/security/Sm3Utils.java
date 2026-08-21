package com.njydsz.common.util.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Base64;
import java.util.HexFormat;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * SM3 密码杂凑算法工具类
 *
 * <p>杂凑算法，输出 256 位（32 字节）摘要，安全性对标 SHA-256，符合国密标准 GM/T 0004-2012。 纯 BouncyCastle 实现，零 JDK 扩展依赖。
 *
 * <h2>核心能力</h2>
 *
 * <ul>
 *   <li>字符串/字节数组摘要：{@link #digest(String)} / {@link #digest(byte[])}
 *   <li>流式摘要：{@link #digest(InputStream)}（大文件场景，内存友好）
 *   <li>Hex/Base64 两种输出编码
 * </ul>
 *
 * <p>通过 JCA {@link MessageDigest} 委托给 BouncyCastle Provider（"BC"）， 需要 {@code bcprov-jdk18on} 在
 * classpath 上。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 字符串摘要（Hex 编码）
 * String hexHash = Sm3Utils.digestHex("Hello World");
 *
 * // 字节数组摘要（Base64 编码）
 * String base64Hash = Sm3Utils.digestBase64(data);
 *
 * // 文件摘要
 * try (InputStream is = Files.newInputStream(path)) {
 *     String fileHash = Sm3Utils.digestHex(is);
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.5.0
 */
@Slf4j
public final class Sm3Utils {

  /** SM3 摘要算法名称（JCA） */
  private static final String SM3_ALGORITHM = "SM3";

  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
  /** BouncyCastle 摘要实例 ThreadLocal 池化（MessageDigest 本身线程安全但每次 getInstance 有 Provider 查找开销） */
  // CHECKSTYLE.ON: RegexpSinglelineJava
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
  private static final ThreadLocal<MessageDigest> DIGEST_CACHE =
  // CHECKSTYLE.ON: RegexpSinglelineJava
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
      ThreadLocal.withInitial(
  // CHECKSTYLE.ON: RegexpSinglelineJava
          () -> {
            try {
              BcProvider.ensure();
              return MessageDigest.getInstance(SM3_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
              throw new IllegalStateException(
                  "SM3 algorithm not available, ensure bcprov-jdk18on is on classpath", e);
            }
          });

  /** 共享的 Hex 编码器 */
  private static final HexFormat HEX_FORMAT = HexFormat.of();

  private Sm3Utils() {
    throw new UnsupportedOperationException("Sm3Utils is a utility class");
  }

  // ==================== 字符串摘要 ====================

  /**
   * 计算 SM3 摘要（返回原始字节）
   *
   * <p>输入字符串按 UTF-8 编码转换为字节后再摘要。输入 null 时返回 null。
   *
   * @param input 待摘要字符串
   * @return 32 字节摘要
   */
  public static byte[] digest(String input) {
    if (input == null) {
      return null;
    }
    return digest(input.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * 计算 SM3 摘要（返回 Hex 编码字符串）
   *
   * <p>输入字符串按 UTF-8 编码。输入 null 时返回 null。
   *
   * @param input 待摘要字符串
   * @return 64 字符 Hex 字符串
   */
  public static String digestHex(String input) {
    byte[] digest = digest(input);
    if (digest == null) {
      return null;
    }
    return HEX_FORMAT.formatHex(digest);
  }

  /**
   * 计算 SM3 摘要（返回 Base64 编码字符串）
   *
   * <p>输入字符串按 UTF-8 编码。输入 null 时返回 null。
   *
   * @param input 待摘要字符串
   * @return Base64 编码摘要
   */
  public static String digestBase64(String input) {
    byte[] digest = digest(input);
    if (digest == null) {
      return null;
    }
    return Base64.getEncoder().encodeToString(digest);
  }

  /**
   * 计算 SM3 摘要（返回 Hex 编码字符串）
   *
   * @param input 待摘要字节数组；null 时返回 null
   * @return 64 字符 Hex 字符串
   */
  public static String digestHex(byte[] input) {
    byte[] digest = digest(input);
    if (digest == null) {
      return null;
    }
    return HEX_FORMAT.formatHex(digest);
  }

  /**
   * 计算 SM3 摘要（返回 Base64 编码字符串）
   *
   * @param input 待摘要字节数组；null 时返回 null
   * @return Base64 编码摘要
   */
  public static String digestBase64(byte[] input) {
    byte[] digest = digest(input);
    if (digest == null) {
      return null;
    }
    return Base64.getEncoder().encodeToString(digest);
  }

  /**
   * 计算 SM3 摘要（核心方法）
   *
   * <p>输入 null 时返回 null。
   *
   * @param input 输入字节数组
   * @return 32 字节摘要
   */
  public static byte[] digest(byte[] input) {
    if (input == null) {
      return null;
    }
    MessageDigest md = DIGEST_CACHE.get();
    md.reset();
    return md.digest(input);
  }

  /**
   * 清理当前线程的 SM3 摘要缓存。
   *
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
   * <p>在线程池复用场景下，建议在请求处理完成后调用此方法，避免 ThreadLocal 内存泄漏。
  // CHECKSTYLE.ON: RegexpSinglelineJava
   * 通常在 {@code finally} 块中调用：
   *
   * <pre>{@code
   * try {
   *     // 业务逻辑
   *     String hash = Sm3Utils.digestHex(data);
   * } finally {
   *     Sm3Utils.cleanup();
   * }
   * }</pre>
   */
  public static void cleanup() {
    DIGEST_CACHE.remove();
  }

  // ==================== 流式摘要 ====================

  /**
   * 计算输入流的 SM3 摘要（Hex 编码）
   *
   * <p>适用于大文件摘要场景，内部使用 8KB 缓冲区边读边算，不占用大量内存。
   *
   * @param inputStream 输入流（调用方负责关闭）
   * @return 64 字符 Hex 字符串；流为空时返回 SM3("") 的值
   * @throws IOException 读取流时出错
   */
  public static String digestHex(InputStream inputStream) throws IOException {
    byte[] digest = digest(inputStream);
    if (digest == null) {
      return null;
    }
    return HEX_FORMAT.formatHex(digest);
  }

  /**
   * 计算输入流的 SM3 摘要
   *
   * <p>适用于大文件摘要场景，内部使用 8KB 缓冲区边读边算，不占用大量内存。
   *
   * @param inputStream 输入流（调用方负责关闭）；null 时返回 null
   * @return 32 字节摘要
   * @throws IOException 读取流时出错
   */
  public static byte[] digest(InputStream inputStream) throws IOException {
    if (inputStream == null) {
      return null;
    }
    MessageDigest md = DIGEST_CACHE.get();
    md.reset();
    byte[] buffer = new byte[8 * 1024];
    int len;
    while ((len = inputStream.read(buffer)) != -1) {
      md.update(buffer, 0, len);
    }
    return md.digest();
  }
}
