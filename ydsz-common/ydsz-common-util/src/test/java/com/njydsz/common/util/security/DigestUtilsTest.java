package com.njydsz.common.util.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link DigestUtils} 单元测试。
 *
 * <p>覆盖：MD5、SHA-256、SHA-512、HMAC、PBKDF2、常量时间比较等密码学路径。
 *
 * @since 26.09.19
 */
@DisplayName("DigestUtils 测试")
class DigestUtilsTest {

  private static final String TEST_INPUT = "Hello, World!";

  @Nested
  @DisplayName("MD5 散列")
  class Md5Test {

    @Test
    @DisplayName("md5Hex: 标准输入返回固定长度 hex")
    void md5Hex_standardInput_returnsFixedLengthHex() {
      String result = DigestUtils.md5Hex(TEST_INPUT);
      assertThat(result).hasSize(32).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("md5Hex: null 返回 null")
    void md5Hex_null_returnsNull() {
      assertThat(DigestUtils.md5Hex((String) null)).isNull();
    }
  }

  @Nested
  @DisplayName("SHA-256 散列")
  class Sha256Test {

    @Test
    @DisplayName("sha256Hex: 标准输入返回固定长度 hex")
    void sha256Hex_standardInput_returnsFixedLengthHex() {
      String result = DigestUtils.sha256Hex(TEST_INPUT);
      assertThat(result).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("sha256Hex: 相同输入产生相同输出（确定性）")
    void sha256Hex_deterministic() {
      assertThat(DigestUtils.sha256Hex(TEST_INPUT))
          .isEqualTo(DigestUtils.sha256Hex(TEST_INPUT));
    }

    @Test
    @DisplayName("sha256Hex: 不同输入产生不同输出")
    void sha256Hex_differentInput_differentOutput() {
      String hash1 = DigestUtils.sha256Hex("input-a");
      String hash2 = DigestUtils.sha256Hex("input-b");
      assertThat(hash1).isNotEqualTo(hash2);
    }
  }

  @Nested
  @DisplayName("SHA-512 散列")
  class Sha512Test {

    @Test
    @DisplayName("sha512Hex: 标准输入返回固定长度 hex")
    void sha512Hex_standardInput_returnsFixedLengthHex() {
      String result = DigestUtils.sha512Hex(TEST_INPUT);
      assertThat(result).hasSize(128).matches("[0-9a-f]+");
    }
  }

  @Nested
  @DisplayName("HMAC 签名")
  class HmacTest {

    @Test
    @DisplayName("hmacSha256Hex: 签名可重复验证")
    void hmacSha256Hex_deterministic() {
      byte[] key = "secret".getBytes(StandardCharsets.UTF_8);
      String hmac1 = DigestUtils.hmacSha256Hex(TEST_INPUT, "secret");
      String hmac2 = DigestUtils.hmacSha256Hex(TEST_INPUT, "secret");
      assertThat(hmac1).isEqualTo(hmac2);
    }

    @Test
    @DisplayName("hmacSha256Hex: 不同密钥签名不同")
    void hmacSha256Hex_differentKey_differentSignature() {
      String sig1 = DigestUtils.hmacSha256Hex(TEST_INPUT, "key1");
      String sig2 = DigestUtils.hmacSha256Hex(TEST_INPUT, "key2");
      assertThat(sig1).isNotEqualTo(sig2);
    }
  }

  @Nested
  @DisplayName("PBKDF2 密钥派生")
  class Pbkdf2Test {

    @Test
    @DisplayName("pbkdf2Hex: 相同输入 + 盐 + 迭代 = 相同输出")
    void pbkdf2Hex_deterministic() {
      byte[] salt = DigestUtils.genSalt(16);
      String result1 = DigestUtils.pbkdf2Hex("password".toCharArray(), salt, 10000, 256);
      String result2 = DigestUtils.pbkdf2Hex("password".toCharArray(), salt, 10000, 256);
      assertThat(result1).isEqualTo(result2);
    }

    @Test
    @DisplayName("genSaltHex: 指定长度输出正确 hex 字符串")
    void genSaltHex_returnsCorrectLengthHex() {
      String saltHex = DigestUtils.genSaltHex(16);
      assertThat(saltHex).hasSize(32).matches("[0-9a-f]+");
    }
  }

  @Nested
  @DisplayName("常量时间比较")
  class ConstantTimeTest {

    @Test
    @DisplayName("constantTimeEquals: 相同字符串返回 true")
    void constantTimeEquals_sameStrings_returnsTrue() {
      assertThat(DigestUtils.constantTimeEquals("hello", "hello")).isTrue();
    }

    @Test
    @DisplayName("constantTimeEquals: 不同字符串返回 false")
    void constantTimeEquals_differentStrings_returnsFalse() {
      assertThat(DigestUtils.constantTimeEquals("hello", "world")).isFalse();
    }

    @Test
    @DisplayName("constantTimeEquals: 都为 null 返回 true")
    void constantTimeEquals_bothNull_returnsTrue() {
      assertThat(DigestUtils.constantTimeEquals(null, null)).isTrue();
    }

    @Test
    @DisplayName("verifyDigestHex: 匹配时返回 true")
    void verifyDigestHex_matching_returnsTrue() {
      String hash = DigestUtils.sha256Hex(TEST_INPUT);
      assertThat(DigestUtils.verifyDigestHex(hash, hash)).isTrue();
    }

    @Test
    @DisplayName("verifyDigestHex: 不匹配时返回 false")
    void verifyDigestHex_notMatching_returnsFalse() {
      String hash1 = DigestUtils.sha256Hex("input1");
      String hash2 = DigestUtils.sha256Hex("input2");
      assertThat(DigestUtils.verifyDigestHex(hash1, hash2)).isFalse();
    }
  }
}
