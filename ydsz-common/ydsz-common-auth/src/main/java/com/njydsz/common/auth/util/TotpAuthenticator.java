package com.njydsz.common.auth.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP（RFC 6238）双因素认证工具。
 *
 * <p>基于时间的一次性密码（Time-based One-Time Password），与 Google Authenticator /
 * Microsoft Authenticator 等主流 TOTP 应用兼容。 零第三方依赖，Base32 编解码与 HMAC-SHA1 动态截断均基于 JDK 实现。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>{@link #generateSecret()} - 生成 Base32 编码的随机密钥（160 bit，SHA1 标准）
 *   <li>{@link #generateCode(String)} - 生成当前时间步的动态码（默认 6 位、30s 步长）
 *   <li>{@link #verify(String, String)} - 校验用户输入动态码（支持前后各 1 个时间步的时钟偏移容忍）
 *   <li>{@link #buildOtpAuthUri(String, String, String)} - 构建 otpauth:// URI（供二维码绑定）
 * </ul>
 *
 * <p><b>安全说明：</b>密钥必须与服务端保存的 Base32 密钥一致；建议绑定后由业务侧使用
 * {@link com.njydsz.common.util.security.crypto.CryptoUtils} 加密存储。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TotpAuthenticator {

  private TotpAuthenticator() {}

  /** Base32 字母表（RFC 4648，无填充） */
  private static final char[] BASE32_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

  /** Base32 字符到值的映射（-1 表示非法字符） */
  private static final int[] BASE32_LOOKUP = new int[128];

  /** 默认动态码位数 */
  public static final int DEFAULT_CODE_LENGTH = 6;

  /** 默认时间步长（秒） */
  public static final int DEFAULT_TIME_STEP_SECONDS = 30;

  /** 校验时允许的时钟偏移步数（前后各 1 步，容忍 ±30s 偏差） */
  public static final int CLOCK_SKEW_STEPS = 1;

  /** HMAC-SHA1 算法名 */
  private static final String HMAC_SHA1 = "HmacSHA1";

  /** 密钥位数（160 bit，RFC 4226 建议至少 128 bit） */
  private static final int SECRET_BITS = 160;

  /** 加密强随机数生成器 */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  static {
    for (int i = 0; i < BASE32_LOOKUP.length; i++) {
      BASE32_LOOKUP[i] = -1;
    }
    for (int i = 0; i < BASE32_ALPHABET.length; i++) {
      BASE32_LOOKUP[BASE32_ALPHABET[i]] = i;
    }
  }

  /**
   * 生成 Base32 编码的随机 TOTP 密钥。
   *
   * @return Base32 字符串（160 bit，不含填充符）
   */
  public static String generateSecret() {
    byte[] bytes = new byte[SECRET_BITS / 8];
    SECURE_RANDOM.nextBytes(bytes);
    return encodeBase32(bytes);
  }

  /**
   * 生成当前时间步的动态码。
   *
   * @param secret Base32 编码的密钥
   * @return 6 位数字动态码（字符串形式，保留前导零）
   */
  public static String generateCode(String secret) {
    return generateCode(secret, System.currentTimeMillis(), DEFAULT_CODE_LENGTH);
  }

  /**
   * 生成指定时刻的动态码。
   *
   * @param secret Base32 编码的密钥
   * @param timestampMs 时刻（毫秒）
   * @param codeLength 动态码位数
   * @return 指定长度的数字动态码
   */
  public static String generateCode(String secret, long timestampMs, int codeLength) {
    byte[] key = decodeBase32(secret);
    long counter = timestampMs / TimeUnit.SECONDS.toMillis(DEFAULT_TIME_STEP_SECONDS);
    byte[] hash = hmacSha1(key, ByteBuffer.allocate(8).putLong(counter).array());
    return truncate(hash, codeLength);
  }

  /**
   * 校验用户输入的动态码。
   *
   * <p>基于当前时间步，并容忍前后 {@link #CLOCK_SKEW_STEPS} 步的时钟偏移（合计最多 ±60s）。
   *
   * @param secret Base32 编码的密钥
   * @param userCode 用户输入的动态码
   * @return 校验通过返回 true；密钥/动态码非法或不在有效窗口内返回 false
   */
  public static boolean verify(String secret, String userCode) {
    if (secret == null || secret.isBlank() || userCode == null || userCode.isBlank()) {
      return false;
    }
    long currentCounter = System.currentTimeMillis() / TimeUnit.SECONDS.toMillis(DEFAULT_TIME_STEP_SECONDS);
    byte[] key = decodeBase32(secret);
    for (int step = -CLOCK_SKEW_STEPS; step <= CLOCK_SKEW_STEPS; step++) {
      byte[] hash = hmacSha1(key, ByteBuffer.allocate(8).putLong(currentCounter + step).array());
      if (constantTimeEquals(truncate(hash, DEFAULT_CODE_LENGTH), userCode)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 构建 otpauth:// URI（供二维码绑定，兼容 Google Authenticator）。
   *
   * <p>格式：{@code otpauth://totp/{issuer}:{account}?secret={secret}&issuer={issuer}}
   *
   * @param issuer 发行方名称（如 "Ydsz Cloud"）
   * @param account 账户标识（如用户名或邮箱）
   * @param secret Base32 编码的密钥
   * @return otpauth URI 字符串
   */
  public static String buildOtpAuthUri(String issuer, String account, String secret) {
    String safeIssuer = encodeUriComponent(issuer);
    String safeAccount = encodeUriComponent(account);
    return "otpauth://totp/"
        + safeIssuer
        + ":"
        + safeAccount
        + "?secret="
        + secret
        + "&issuer="
        + safeIssuer;
  }

  /**
   * HMAC-SHA1 计算。
   *
   * @param key 密钥字节
   * @param data 待签名数据
   * @return 20 字节 HMAC 摘要
   */
  private static byte[] hmacSha1(byte[] key, byte[] data) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA1);
      mac.init(new SecretKeySpec(key, HMAC_SHA1));
      return mac.doFinal(data);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      // HmacSHA1 是 JDK 强制支持的算法，密钥由 Base32 解码而来必然合法
      throw new IllegalStateException("TOTP HMAC-SHA1 计算失败", e);
    }
  }

  /**
   * RFC 4226 动态截断（Dynamic Truncation）。
   *
   * @param hash HMAC-SHA1 摘要（20 字节）
   * @param codeLength 输出位数（6~8）
   * @return 数字字符串（保留前导零）
   */
  private static String truncate(byte[] hash, int codeLength) {
    int offset = hash[hash.length - 1] & 0x0f;
    int binary =
        ((hash[offset] & 0x7f) << 24)
            | ((hash[offset + 1] & 0xff) << 16)
            | ((hash[offset + 2] & 0xff) << 8)
            | (hash[offset + 3] & 0xff);
    int modulo = (int) Math.pow(10, codeLength);
    int code = binary % modulo;
    return String.format("%0" + codeLength + "d", code);
  }

  /**
   * 常量时间比较，避免时序侧信道。
   *
   * @param expected 期望值
   * @param actual 实际值
   * @return 相等返回 true
   */
  private static boolean constantTimeEquals(String expected, String actual) {
    if (expected == null || actual == null || expected.length() != actual.length()) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < expected.length(); i++) {
      result |= expected.charAt(i) ^ actual.charAt(i);
    }
    return result == 0;
  }

  /**
   * Base32 编码（RFC 4648，无填充）。
   *
   * @param data 原始字节
   * @return Base32 字符串
   */
  private static String encodeBase32(byte[] data) {
    StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
    int buffer = 0;
    int bitsLeft = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xff);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        sb.append(BASE32_ALPHABET[(buffer >>> (bitsLeft - 5)) & 0x1f]);
        bitsLeft -= 5;
      }
    }
    if (bitsLeft > 0) {
      sb.append(BASE32_ALPHABET[(buffer << (5 - bitsLeft)) & 0x1f]);
    }
    return sb.toString();
  }

  /**
   * Base32 解码（RFC 4648，容忍大小写与填充符）。
   *
   * @param base32 Base32 字符串
   * @return 解码后的字节数组
   */
  private static byte[] decodeBase32(String base32) {
    String normalized = base32.toUpperCase().replace("=", "");
    int byteCount = normalized.length() * 5 / 8;
    byte[] out = new byte[byteCount];
    int buffer = 0;
    int bitsLeft = 0;
    int outIndex = 0;
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if (c >= BASE32_LOOKUP.length || BASE32_LOOKUP[c] < 0) {
        throw new IllegalArgumentException("非法 Base32 字符: " + c);
      }
      buffer = (buffer << 5) | BASE32_LOOKUP[c];
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        out[outIndex++] = (byte) ((buffer >>> (bitsLeft - 8)) & 0xff);
        bitsLeft -= 8;
      }
    }
    return out;
  }

  /**
   * URI 组件编码（RFC 3986），用于 otpauth URI 中 issuer/account 的安全转义。
   *
   * @param value 原始字符串
   * @return 编码后的字符串
   */
  private static String encodeUriComponent(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder(value.length());
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    for (byte b : bytes) {
      char c = (char) (b & 0xff);
      if ((c >= 'a' && c <= 'z')
          || (c >= 'A' && c <= 'Z')
          || (c >= '0' && c <= '9')
          || c == '-' || c == '_' || c == '.' || c == '~') {
        sb.append(c);
      } else {
        sb.append('%').append(String.format("%02X", b & 0xff));
      }
    }
    return sb.toString();
  }
}
