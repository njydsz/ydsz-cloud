package com.njydsz.common.util.security;

import java.util.HexFormat;

/**
 * Hex 编解码工具类（统一入口）。
 *
 * <p>提供字节数组 ↔ Hex 字符串的安全转换，替代JDK之前 AesUtils / Sm4Utils 各自实现的 {@code bytesToHex} / {@code
 * hexToBytes} 方法。所有安全模块的 Hex 编解码都应使用本类。
 *
 * <p><b>线程安全：</b>{@link HexFormat} 实例本身线程安全，本类所有方法均为无状态纯函数。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class HexUtils {

  /** 共享的 Hex 编码器（线程安全） */
  private static final HexFormat HEX_FORMAT = HexFormat.of();

  private HexUtils() {
    throw new UnsupportedOperationException("HexUtils is a utility class");
  }

  /**
   * 字节数组转 Hex 字符串（小写）。
   *
   * @param bytes 字节数组（不可为 null）
   * @return Hex 编码字符串；bytes 为空数组时返回空字符串
   * @throws NullPointerException bytes 为 null 时抛出
   */
  public static String encode(byte[] bytes) {
    return HEX_FORMAT.formatHex(bytes);
  }

  /**
   * 字节数组转 Hex 字符串（小写），带空值保护。
   *
   * <p>与 {@link #encode(byte[])} 行为一致，但输入 null 时返回 null 而非抛异常， 适用于不确定输入是否为 null 的场景。
   *
   * @param bytes 字节数组（可为 null）
   * @return Hex 编码字符串；bytes 为 null 时返回 null
   */
  public static String encodeNullable(byte[] bytes) {
    return bytes == null ? null : HEX_FORMAT.formatHex(bytes);
  }

  /**
   * Hex 字符串转字节数组。
   *
   * @param hex Hex 字符串（不可为 null，长度必须为偶数）
   * @return 字节数组
   * @throws IllegalArgumentException hex 为 null、长度为奇数或包含非 Hex 字符时抛出
   */
  public static byte[] decode(String hex) {
    if (hex == null || hex.length() % 2 != 0) {
      throw new IllegalArgumentException("Hex string must not be null and must have even length");
    }
    return HEX_FORMAT.parseHex(hex);
  }

  /**
   * Hex 字符串转字节数组，带空值保护。
   *
   * <p>与 {@link #decode(String)} 行为一致，但输入 null 时返回 null 而非抛异常。
   *
   * @param hex Hex 字符串（可为 null）
   * @return 字节数组；hex 为 null 时返回 null
   * @throws IllegalArgumentException hex 长度为奇数或包含非 Hex 字符时抛出
   */
  public static byte[] decodeNullable(String hex) {
    return hex == null ? null : decode(hex);
  }

  /**
   * 校验字符串是否为合法 Hex 格式。
   *
   * @param hex 待校验字符串（可为 null）
   * @return 合法 Hex 格式返回 true；null 或非法格式返回 false
   */
  public static boolean isValidHex(String hex) {
    if (hex == null || hex.length() % 2 != 0) {
      return false;
    }
    try {
      HEX_FORMAT.parseHex(hex);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
