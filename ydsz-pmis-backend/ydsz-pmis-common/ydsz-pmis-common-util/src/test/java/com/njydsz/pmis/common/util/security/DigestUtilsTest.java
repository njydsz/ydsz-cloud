package com.njydsz.pmis.common.util.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DigestUtils 单元测试
 *
 * <p>覆盖 MD5 / SHA-256 / SHA-512 / HMAC-SHA256 / PBKDF2 / salt 生成 / 时序恒定比较等核心能力。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@DisplayName("DigestUtils - 不可逆加密工具类测试")
class DigestUtilsTest {

    // ==================== 已知向量（来自 RFC / 公开标准） ====================

    /** MD5("abc") = 900150983cd24fb0d6963f7d28e17f72 */
    private static final String MD5_ABC_HEX = "900150983cd24fb0d6963f7d28e17f72";

    /** MD5("") = d41d8cd98f00b204e9800998ecf8427e */
    private static final String MD5_EMPTY_HEX = "d41d8cd98f00b204e9800998ecf8427e";

    /** SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad */
    private static final String SHA256_ABC_HEX = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 */
    private static final String SHA256_EMPTY_HEX = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** SHA-512("abc") 标准向量 */
    private static final String SHA512_ABC_HEX =
        "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f";

    /** SHA-512("") 标准向量 */
    private static final String SHA512_EMPTY_HEX =
        "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e";

    /** RFC 4231 Test Case 2: HMAC-SHA256(key="Jefe", data="what do ya want for nothing?") */
    private static final String HMAC_SHA256_RFC_HEX =
        "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";

    // ==================== MD5 ====================

    @Nested
    @DisplayName("MD5 摘要")
    class Md5Test {

        @Test
        @DisplayName("md5(byte[]) 返回 16 字节摘要")
        void shouldReturn16BytesForMd5() {
            byte[] hash = DigestUtils.md5("abc".getBytes(StandardCharsets.UTF_8));
            assertNotNull(hash);
            assertEquals(16, hash.length);
        }

        @Test
        @DisplayName("md5(byte[]) 与 MessageDigest 基准一致")
        void shouldMatchJdkMessageDigestForMd5() throws Exception {
            byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
            byte[] expected = MessageDigest.getInstance("MD5").digest(input);
            assertArrayEquals(expected, DigestUtils.md5(input));
        }

        @Test
        @DisplayName("md5Hex(byte[]) 返回标准 MD5 十六进制串")
        void shouldReturnStandardMd5HexForBytes() {
            assertEquals(MD5_ABC_HEX, DigestUtils.md5Hex("abc".getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("md5Hex(String) 返回标准 MD5 十六进制串")
        void shouldReturnStandardMd5HexForString() {
            assertEquals(MD5_ABC_HEX, DigestUtils.md5Hex("abc"));
        }

        @Test
        @DisplayName("md5Hex(\"\") 返回空字符串的标准摘要")
        void shouldReturnEmptyStringMd5Hex() {
            assertEquals(MD5_EMPTY_HEX, DigestUtils.md5Hex(""));
        }

        @Test
        @DisplayName("md5Hex(null) 返回 null")
        void shouldReturnNullWhenMd5HexInputIsNull() {
            assertNull(DigestUtils.md5Hex((String) null));
        }
    }

    // ==================== SHA-256 ====================

    @Nested
    @DisplayName("SHA-256 摘要")
    class Sha256Test {

        @Test
        @DisplayName("sha256(byte[]) 返回 32 字节摘要")
        void shouldReturn32BytesForSha256() {
            byte[] hash = DigestUtils.sha256("abc".getBytes(StandardCharsets.UTF_8));
            assertNotNull(hash);
            assertEquals(32, hash.length);
        }

        @Test
        @DisplayName("sha256(byte[]) 与 MessageDigest 基准一致")
        void shouldMatchJdkMessageDigestForSha256() throws Exception {
            byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
            byte[] expected = MessageDigest.getInstance("SHA-256").digest(input);
            assertArrayEquals(expected, DigestUtils.sha256(input));
        }

        @Test
        @DisplayName("sha256Hex(byte[]) 返回标准 SHA-256 十六进制串")
        void shouldReturnStandardSha256HexForBytes() {
            assertEquals(SHA256_ABC_HEX, DigestUtils.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("sha256Hex(String) 返回标准 SHA-256 十六进制串")
        void shouldReturnStandardSha256HexForString() {
            assertEquals(SHA256_ABC_HEX, DigestUtils.sha256Hex("abc"));
        }

        @Test
        @DisplayName("sha256Hex(\"\") 返回空字符串的标准摘要")
        void shouldReturnEmptyStringSha256Hex() {
            assertEquals(SHA256_EMPTY_HEX, DigestUtils.sha256Hex(""));
        }

        @Test
        @DisplayName("sha256Hex(null) 返回 null")
        void shouldReturnNullWhenSha256HexInputIsNull() {
            assertNull(DigestUtils.sha256Hex((String) null));
        }
    }

    // ==================== SHA-512 ====================

    @Nested
    @DisplayName("SHA-512 摘要")
    class Sha512Test {

        @Test
        @DisplayName("sha512(byte[]) 返回 64 字节摘要")
        void shouldReturn64BytesForSha512() {
            byte[] hash = DigestUtils.sha512("abc".getBytes(StandardCharsets.UTF_8));
            assertNotNull(hash);
            assertEquals(64, hash.length);
        }

        @Test
        @DisplayName("sha512(byte[]) 与 MessageDigest 基准一致")
        void shouldMatchJdkMessageDigestForSha512() throws Exception {
            byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
            byte[] expected = MessageDigest.getInstance("SHA-512").digest(input);
            assertArrayEquals(expected, DigestUtils.sha512(input));
        }

        @Test
        @DisplayName("sha512Hex(byte[]) 返回标准 SHA-512 十六进制串")
        void shouldReturnStandardSha512HexForBytes() {
            assertEquals(SHA512_ABC_HEX, DigestUtils.sha512Hex("abc".getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("sha512Hex(String) 返回标准 SHA-512 十六进制串")
        void shouldReturnStandardSha512HexForString() {
            assertEquals(SHA512_ABC_HEX, DigestUtils.sha512Hex("abc"));
        }

        @Test
        @DisplayName("sha512Hex(\"\") 返回空字符串的标准摘要")
        void shouldReturnEmptyStringSha512Hex() {
            assertEquals(SHA512_EMPTY_HEX, DigestUtils.sha512Hex(""));
        }

        @Test
        @DisplayName("sha512Hex(null) 返回 null")
        void shouldReturnNullWhenSha512HexInputIsNull() {
            assertNull(DigestUtils.sha512Hex((String) null));
        }
    }

    // ==================== HMAC-SHA256 ====================

    @Nested
    @DisplayName("HMAC-SHA256 散列")
    class HmacSha256Test {

        @Test
        @DisplayName("hmacSha256(byte[], byte[]) 返回 32 字节摘要")
        void shouldReturn32BytesForHmacSha256() {
            byte[] hash = DigestUtils.hmacSha256(
                "what do ya want for nothing?".getBytes(StandardCharsets.UTF_8),
                "Jefe".getBytes(StandardCharsets.UTF_8)
            );
            assertNotNull(hash);
            assertEquals(32, hash.length);
        }

        @Test
        @DisplayName("hmacSha256Hex(byte[], byte[]) 命中 RFC 4231 测试向量")
        void shouldMatchRfc4231VectorForBytes() {
            String hex = DigestUtils.hmacSha256Hex(
                "what do ya want for nothing?".getBytes(StandardCharsets.UTF_8),
                "Jefe".getBytes(StandardCharsets.UTF_8)
            );
            assertEquals(HMAC_SHA256_RFC_HEX, hex);
        }

        @Test
        @DisplayName("hmacSha256Hex(String, String) 与字节数组版本结果一致")
        void shouldMatchStringVersionWithBytesVersion() {
            String input = "what do ya want for nothing?";
            String key = "Jefe";

            String fromString = DigestUtils.hmacSha256Hex(input, key);
            String fromBytes = DigestUtils.hmacSha256Hex(
                input.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8)
            );

            assertEquals(fromBytes, fromString);
            assertEquals(HMAC_SHA256_RFC_HEX, fromString);
        }

        @Test
        @DisplayName("hmacSha256Hex(null, key) 返回 null")
        void shouldReturnNullWhenInputIsNull() {
            assertNull(DigestUtils.hmacSha256Hex(null, "key"));
        }

        @Test
        @DisplayName("hmacSha256Hex(input, null) 返回 null")
        void shouldReturnNullWhenKeyIsNull() {
            assertNull(DigestUtils.hmacSha256Hex("input", null));
        }

        @Test
        @DisplayName("相同输入产生相同 HMAC，不同密钥产生不同 HMAC")
        void shouldProduceSameHashForSameInputAndDifferentForDifferentKey() {
            byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
            byte[] key1 = "key1".getBytes(StandardCharsets.UTF_8);
            byte[] key2 = "key2".getBytes(StandardCharsets.UTF_8);

            byte[] h1 = DigestUtils.hmacSha256(input, key1);
            byte[] h2 = DigestUtils.hmacSha256(input, key1);
            byte[] h3 = DigestUtils.hmacSha256(input, key2);

            assertArrayEquals(h1, h2);
            assertFalse(MessageDigest.isEqual(h1, h3));
        }
    }

    // ==================== PBKDF2 ====================

    @Nested
    @DisplayName("PBKDF2 密钥派生")
    class Pbkdf2Test {

        @Test
        @DisplayName("pbkdf2 返回指定长度的派生密钥")
        void shouldReturnDerivedKeyOfRequestedLength() {
            byte[] salt = DigestUtils.genSalt(16);
            byte[] derived = DigestUtils.pbkdf2("password".toCharArray(), salt, 1000, 256);
            assertNotNull(derived);
            assertEquals(32, derived.length);
        }

        @Test
        @DisplayName("相同输入（密码 + salt + 迭代次数）产生相同派生密钥")
        void shouldProduceSameKeyForSameInput() {
            char[] password = "mySecretPassword".toCharArray();
            byte[] salt = DigestUtils.genSalt(16);

            byte[] k1 = DigestUtils.pbkdf2(password, salt, 1000, 256);
            byte[] k2 = DigestUtils.pbkdf2(password, salt, 1000, 256);

            assertArrayEquals(k1, k2);
        }

        @Test
        @DisplayName("不同 salt 产生不同派生密钥")
        void shouldProduceDifferentKeyForDifferentSalt() {
            char[] password = "mySecretPassword".toCharArray();
            byte[] salt1 = DigestUtils.genSalt(16);
            byte[] salt2 = DigestUtils.genSalt(16);

            byte[] k1 = DigestUtils.pbkdf2(password, salt1, 1000, 256);
            byte[] k2 = DigestUtils.pbkdf2(password, salt2, 1000, 256);

            assertNotNull(k1);
            assertNotNull(k2);
            assertNotEquals(k1, k2);
            assertFalse(MessageDigest.isEqual(k1, k2));
        }

        @Test
        @DisplayName("不同迭代次数产生不同派生密钥")
        void shouldProduceDifferentKeyForDifferentIterations() {
            char[] password = "mySecretPassword".toCharArray();
            byte[] salt = DigestUtils.genSalt(16);

            byte[] k1 = DigestUtils.pbkdf2(password, salt, 1000, 256);
            byte[] k2 = DigestUtils.pbkdf2(password, salt, 2000, 256);

            assertFalse(MessageDigest.isEqual(k1, k2));
        }

        @Test
        @DisplayName("pbkdf2Hex 返回与 pbkdf2 一致的十六进制串")
        void shouldReturnHexMatchingBytesVersion() {
            char[] password = "password".toCharArray();
            byte[] salt = "1234567890123456".getBytes(StandardCharsets.UTF_8);

            byte[] derived = DigestUtils.pbkdf2(password, salt, 1000, 256);
            String hex = DigestUtils.pbkdf2Hex(password, salt, 1000, 256);

            assertNotNull(hex);
            assertEquals(64, hex.length());
            assertEquals(bytesToHexInline(derived), hex);
        }
    }

    // ==================== Salt 生成 ====================

    @Nested
    @DisplayName("随机 salt 生成")
    class SaltTest {

        @Test
        @DisplayName("genSalt 返回指定长度的字节数组")
        void shouldReturnByteArrayOfRequestedLength() {
            byte[] salt = DigestUtils.genSalt(16);
            assertNotNull(salt);
            assertEquals(16, salt.length);
        }

        @Test
        @DisplayName("genSalt 每次调用返回不同的随机值")
        void shouldReturnDifferentSaltEachCall() {
            byte[] s1 = DigestUtils.genSalt(32);
            byte[] s2 = DigestUtils.genSalt(32);
            assertEquals(32, s1.length);
            assertEquals(32, s2.length);
            assertFalse(MessageDigest.isEqual(s1, s2), "两次生成的 salt 不应相同");
        }

        @Test
        @DisplayName("genSalt(0) 抛出 IllegalArgumentException")
        void shouldThrowWhenGenSaltWithZero() {
            assertThrows(IllegalArgumentException.class, () -> DigestUtils.genSalt(0));
        }

        @Test
        @DisplayName("genSalt 负数抛出 IllegalArgumentException")
        void shouldThrowWhenGenSaltWithNegative() {
            assertThrows(IllegalArgumentException.class, () -> DigestUtils.genSalt(-1));
        }

        @Test
        @DisplayName("genSaltHex 返回 2 * numBytes 长度的十六进制串")
        void shouldReturnHexSaltOfCorrectLength() {
            String hex = DigestUtils.genSaltHex(16);
            assertNotNull(hex);
            assertEquals(32, hex.length());
        }
    }

    // ==================== 时序恒定比较 verifyDigest ====================

    @Nested
    @DisplayName("verifyDigest 时序恒定比较")
    class VerifyDigestTest {

        @Test
        @DisplayName("相同的字节数组返回 true")
        void shouldReturnTrueWhenArraysMatch() {
            byte[] a = DigestUtils.sha256("hello".getBytes(StandardCharsets.UTF_8));
            byte[] b = DigestUtils.sha256("hello".getBytes(StandardCharsets.UTF_8));
            assertTrue(DigestUtils.verifyDigest(a, b));
        }

        @Test
        @DisplayName("不同的字节数组返回 false")
        void shouldReturnFalseWhenArraysDoNotMatch() {
            byte[] a = DigestUtils.sha256("hello".getBytes(StandardCharsets.UTF_8));
            byte[] b = DigestUtils.sha256("world".getBytes(StandardCharsets.UTF_8));
            assertFalse(DigestUtils.verifyDigest(a, b));
        }

        @Test
        @DisplayName("expected 为 null 返回 false")
        void shouldReturnFalseWhenExpectedIsNull() {
            byte[] a = DigestUtils.sha256("hello".getBytes(StandardCharsets.UTF_8));
            assertFalse(DigestUtils.verifyDigest(null, a));
        }

        @Test
        @DisplayName("actual 为 null 返回 false")
        void shouldReturnFalseWhenActualIsNull() {
            byte[] a = DigestUtils.sha256("hello".getBytes(StandardCharsets.UTF_8));
            assertFalse(DigestUtils.verifyDigest(a, null));
        }

        @Test
        @DisplayName("两个 null 返回 false")
        void shouldReturnFalseWhenBothNull() {
            assertFalse(DigestUtils.verifyDigest(null, null));
        }

        @Test
        @DisplayName("长度不同的数组返回 false")
        void shouldReturnFalseWhenLengthDiffers() {
            byte[] a = DigestUtils.md5("hello".getBytes(StandardCharsets.UTF_8));        // 16 字节
            byte[] b = DigestUtils.sha256("hello".getBytes(StandardCharsets.UTF_8));     // 32 字节
            assertFalse(DigestUtils.verifyDigest(a, b));
        }

        @Test
        @DisplayName("verifyDigestHex 相同十六进制串返回 true")
        void shouldReturnTrueWhenHexStringsMatch() {
            String hex = DigestUtils.sha256Hex("hello");
            assertTrue(DigestUtils.verifyDigestHex(hex, hex));
        }

        @Test
        @DisplayName("verifyDigestHex 不同十六进制串返回 false")
        void shouldReturnFalseWhenHexStringsDoNotMatch() {
            String h1 = DigestUtils.sha256Hex("hello");
            String h2 = DigestUtils.sha256Hex("world");
            assertFalse(DigestUtils.verifyDigestHex(h1, h2));
        }

        @Test
        @DisplayName("verifyDigestHex expected 为 null 返回 false")
        void shouldReturnFalseWhenHexExpectedIsNull() {
            assertFalse(DigestUtils.verifyDigestHex(null, "abcd"));
        }

        @Test
        @DisplayName("verifyDigestHex actual 为 null 返回 false")
        void shouldReturnFalseWhenHexActualIsNull() {
            assertFalse(DigestUtils.verifyDigestHex("abcd", null));
        }

        @Test
        @DisplayName("verifyDigestHex 非法十六进制串返回 false")
        void shouldReturnFalseWhenHexInvalid() {
            // 长度为奇数，无法解码
            assertFalse(DigestUtils.verifyDigestHex("abc", "abc"));
            // 非法字符
            assertFalse(DigestUtils.verifyDigestHex("zzzz", "zzzz"));
        }
    }

    // ==================== 辅助方法 ====================

    /** 内联十六进制编码（避免依赖 HexUtils 公开 API） */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private String bytesToHexInline(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_CHARS[(b >> 4) & 0x0F]);
            sb.append(HEX_CHARS[b & 0x0F]);
        }
        return sb.toString();
    }
}
