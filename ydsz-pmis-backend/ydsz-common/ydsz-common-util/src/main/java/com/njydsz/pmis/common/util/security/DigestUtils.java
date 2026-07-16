package com.njydsz.common.util.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.njydsz.common.util.bytes.HexUtils;

/**
 * 不可逆加密工具类（纯 JDK 实现，零第三方依赖）
 *
 * <p>提供 MD5、SHA-1、SHA-256、SHA-512 散列、HMAC-SHA256 签名、PBKDF2 密钥派生、
 * 常量时间比较等安全能力。
 */
public class DigestUtils {

    private DigestUtils() {
        throw new UnsupportedOperationException("DigestUtils is a utility class and cannot be instantiated");
    }

    /**
     * 使用共享的线程安全 SecureRandom 实例
     *
     * <p>SecureRandom 本身是线程安全的，无需 ThreadLocal 隔离。
     * 相比 ThreadLocal 方案，避免了线程池场景下的内存泄漏风险。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 流处理缓冲区大小（8KB）
 */
    private static final int STREAM_BUFFER_SIZE = 8 * 1024;

    /**
     * PBKDF2 密钥派生算法
     */
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

    /**
     * 生成随机的 byte[] 作为 salt 密钥（线程安全）
 */
    public static byte[] genSalt(int numBytes) {
        if (numBytes <= 0) {
            throw new IllegalArgumentException("numBytes argument must be a positive integer (1 or larger)");
        }
        byte[] bytes = new byte[numBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * 生成指定长度的 Hex 格式 salt
 */
    public static String genSaltHex(int numBytes) {
        return HexUtils.bytesToHex(genSalt(numBytes));
    }

    /**
     * 优化的散列方法（支持 salt 和多次迭代）
     *
     * <p>每次迭代均混入 salt，确保 salt 对最终哈希值的充分影响。
     * 迭代公式：H_0 = H(salt || input)，H_i = H(salt || H_{i-1})
     *
     * @param input      待散列的数据
     * @param algorithm  散列算法（如 SHA-256）
     * @param salt       盐值（可为 null）
     * @param iterations 迭代次数（\u22651）
     * @return 散列结果
     */
    public static byte[] digest(byte[] input, String algorithm, byte[] salt, int iterations) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] currentHash;
            if (salt != null) {
                digest.update(salt);
                currentHash = digest.digest(input);
                for (int i = 1; i < iterations; i++) {
                    digest.update(salt);
                    digest.update(currentHash);
                    currentHash = digest.digest();
                }
            } else {
                currentHash = digest.digest(input);
                for (int i = 1; i < iterations; i++) {
                    digest.update(currentHash);
                    currentHash = digest.digest();
                }
            }
            return currentHash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithm not available: " + algorithm, e);
        }
    }

    /**
     * 优化的流处理散列
 */
    public static byte[] digest(InputStream input, String algorithm) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance(algorithm);
            final byte[] buffer = new byte[STREAM_BUFFER_SIZE];

            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithm not available: " + algorithm, e);
        }
    }

    /**
     * 计算 MD5 散列
 */
    public static byte[] md5(byte[] input) {
        return digest(input, "MD5", null, 1);
    }

    /**
     * 计算 MD5 散列（Hex 格式）
 */
    public static String md5Hex(byte[] input) {
        return HexUtils.bytesToHex(md5(input));
    }

    /**
     * 计算 MD5 散列（字符串）
 */
    public static String md5Hex(String input) {
        if (input == null) {
            return null;
        }
        return md5Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 SHA-1 散列
 */
    public static byte[] sha1(byte[] input) {
        return digest(input, "SHA-1", null, 1);
    }

    /**
     * 计算 SHA-1 散列（Hex 格式）
 */
    public static String sha1Hex(byte[] input) {
        return HexUtils.bytesToHex(sha1(input));
    }

    /**
     * 计算 SHA-1 散列（字符串）
 */
    public static String sha1Hex(String input) {
        if (input == null) {
            return null;
        }
        return sha1Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 SHA-256 散列
 */
    public static byte[] sha256(byte[] input) {
        return digest(input, "SHA-256", null, 1);
    }

    /**
     * 计算 SHA-256 散列（Hex 格式）
 */
    public static String sha256Hex(byte[] input) {
        return HexUtils.bytesToHex(sha256(input));
    }

    /**
     * 计算 SHA-256 散列（字符串）
 */
    public static String sha256Hex(String input) {
        if (input == null) {
            return null;
        }
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 SHA-512 散列
 */
    public static byte[] sha512(byte[] input) {
        return digest(input, "SHA-512", null, 1);
    }

    /**
     * 计算 SHA-512 散列（Hex 格式）
 */
    public static String sha512Hex(byte[] input) {
        return HexUtils.bytesToHex(sha512(input));
    }

    /**
     * 计算 SHA-512 散列（字符串）
 */
    public static String sha512Hex(String input) {
        if (input == null) {
            return null;
        }
        return sha512Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 HMAC-SHA256（带密钥的散列）
 */
    public static byte[] hmacSha256(byte[] input, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(input);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 algorithm not available", e);
        }
    }

    /**
     * 计算 HMAC-SHA256（Hex 格式）
 */
    public static String hmacSha256Hex(byte[] input, byte[] key) {
        return HexUtils.bytesToHex(hmacSha256(input, key));
    }

    /**
     * 计算 HMAC-SHA256（字符串）
 */
    public static String hmacSha256Hex(String input, String key) {
        if (input == null || key == null) {
            return null;
        }
        return hmacSha256Hex(
            input.getBytes(StandardCharsets.UTF_8),
            key.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * PBKDF2 密钥派生（推荐用于密码存储）
 */
    public static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 algorithm not available", e);
        }
    }

    /**
     * PBKDF2 密钥派生（Hex 格式）
 */
    public static String pbkdf2Hex(char[] password, byte[] salt, int iterations, int keyLength) {
        return HexUtils.bytesToHex(pbkdf2(password, salt, iterations, keyLength));
    }

    /**
     * 验证散列值是否匹配（时序恒定比较，防止时序攻击）
 */
    public static boolean verifyDigest(byte[] expected, byte[] actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * 验证 Hex 格式的散列值是否匹配
     */
    public static boolean verifyDigestHex(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        try {
            byte[] expected = HexUtils.hexToBytes(expectedHex);
            byte[] actual = HexUtils.hexToBytes(actualHex);
            return verifyDigest(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 常量时间字符串比较（防止计时攻击）
     *
     * <p>使用 {@link MessageDigest#isEqual(byte[], byte[])} 进行恒定时间比较，
     * 防止攻击者通过比较耗时推断字符串差异位置。
     *
     * @param a 字符串 a
     * @param b 字符串 b
     * @return 相等返回 true
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    /**
     * 计算 HMAC-SHA256 并返回 Base64 标准编码的签名
     *
     * @param data   原始数据
     * @param secret 密钥
     * @return Base64 标准编码的签名
     */
    public static String hmacSha256Base64(String data, String secret) {
        if (data == null || secret == null) {
            return null;
        }
        byte[] hmac = hmacSha256(
            data.getBytes(StandardCharsets.UTF_8),
            secret.getBytes(StandardCharsets.UTF_8)
        );
        return Base64.getEncoder().encodeToString(hmac);
    }

    /**
     * 计算 HMAC-SHA256 并返回 Base64 URL-safe 编码（无填充）的签名
     *
     * @param data 待签名数据
     * @param key  密钥字节数组
     * @return Base64 URL-safe 编码的签名
     */
    public static String hmacSha256UrlSafe(String data, byte[] key) {
        if (data == null || key == null) {
            return null;
        }
        byte[] hmac = hmacSha256(data.getBytes(StandardCharsets.UTF_8), key);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
    }

    /**
     * 签名编码格式
     */
    public enum SignatureEncoding {
        /** Base64 编码 */
        BASE64,
        /** Hex 编码 */
        HEX
    }

    /**
     * 验证 HMAC-SHA256 签名（常量时间比较，防止时序攻击）
     *
     * @param data      原始数据
     * @param secret    密钥
     * @param signature 待验证的签名
     * @param encoding  签名编码格式
     * @return 验证通过返回 true
     */
    public static boolean verifySignature(String data, String secret, String signature, SignatureEncoding encoding) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String computed = encoding == SignatureEncoding.BASE64
                ? hmacSha256Base64(data, secret)
                : hmacSha256Hex(data, secret);
        return constantTimeEquals(computed, signature);
    }
}
